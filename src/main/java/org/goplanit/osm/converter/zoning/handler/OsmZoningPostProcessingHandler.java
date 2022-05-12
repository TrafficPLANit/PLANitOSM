package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.network.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmZoningReaderOsmData;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.goplanit.osm.tags.*;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.locale.DrivingDirectionDefaultByCountry;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneGroup;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.linearref.LinearLocation;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that conducts final parsing round where all stop_positions in relations are mapped to the now parsed transfer zones.
 * This is done separately because transfer zones are sometimes also part of relations and it is not guaranteed that all transfer zones
 * are available when encountering a stop_position in a relation. So we parse them in another pass.
 * <p>
 * Also, all unprocessed stations that are not part of any relation are converted into transfer zones and connectoids here since
 * we can now guarantee they are not part of a relation, i.e., stop_area 
 * 
 * @author markr
 * 
 *
 */
public class OsmZoningPostProcessingHandler extends OsmZoningHandlerBase {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningPostProcessingHandler.class.getCanonicalName());
        
  /** to be able to find OSM nodes internal to parsed PLANit links that we want to break to for example create stop locations for stand alone station, 
   * we must be able to spatially find those nodes spatially because they are not referenced by the station or PLANit link explicitly, this is what we do here. 
   * It is not placed in the zoning data as it is only utilised in post-processing */
  private Map<MacroscopicNetworkLayer, Quadtree> spatiallyIndexedOsmNodesInternalToPlanitLinks = null;
  
  /** A stand alone station can either support a single platform when it is road based or two stop_locations for rail (on either side). This is 
   * reflected in the returned max matches. The search distance is based on the settings where a road based station utilises the stop to waiting
   * area search distance whereas a rail based one uses the station to waiting area search distance
   * 
   * @param osmStationId to use
   * @param settings to obtain search distance for
   * @param osmStationMode station modes supported
   * @return search distance and max stop_location matches pair, null if problem occurred
   */
  private static Pair<Double, Integer> determineSearchDistanceAndMaxStopLocationMatchesForStandAloneStation(
      long osmStationId, String osmStationMode, OsmPublicTransportReaderSettings settings) {
    
    Double searchDistance = null;
    Integer maxMatches = null;
    if(OsmRailModeTags.isRailModeTag(osmStationMode)) {
      /* rail based station -> match to nearby train tracks 
       * assumptions: small station would at most have two tracks with platforms and station might be a bit further away 
       * from tracks than a regular bus stop pole, so cast wider net */
      searchDistance = settings.getStationToWaitingAreaSearchRadiusMeters();
      maxMatches = 2;
    }else if(OsmRoadModeTags.isRoadModeTag(osmStationMode)) {
      /* road based station -> match to nearest road link 
       * likely bus stop, so only match to closest by road link which should be very close, so use
       * at most single match and small search radius, same as used for pole->stop_position search */
      searchDistance = settings.getStopToWaitingAreaSearchRadiusMeters();
      maxMatches = 1;
    }else if(OsmWaterModeTags.isWaterModeTag(osmStationMode)) {
      /* water based -> not supported yet */
      LOGGER.warning(String.format("DISCARD: water based stand-alone station detected %d, not supported yet, skip", osmStationId));
      return null;
    }
    
    return Pair.of(searchDistance, maxMatches);
  }     
  
  /**
   * created spatially indexed OSM nodes internal to existing PLANit links container
   */
  private void initialiseSpatiallyIndexedOsmNodesInternalToPlanitLinks() {
    
    double envelopeMinExtentAbsolute = Double.POSITIVE_INFINITY;
    for(MacroscopicNetworkLayer layer : getSettings().getReferenceNetwork().getTransportLayers()) {
      OsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(layer);
      spatiallyIndexedOsmNodesInternalToPlanitLinks.put(layer, new Quadtree());
      Quadtree spatialcontainer = spatiallyIndexedOsmNodesInternalToPlanitLinks.get(layer);            
            
      Set<Point> registeredInternalLinkLocations = layerData.getRegisteredLocationsInternalToAnyPlanitLink();
      for( Point location: registeredInternalLinkLocations) {
        OsmNode osmNodeAtLocation = layerData.getOsmNodeInternalToLinkByLocation(location);
        Envelope pointEnvelope = new Envelope(location.getCoordinate());
        getGeoUtils().createBoundingBox(pointEnvelope, 5); // buffer around bounding box of point to avoid issues with JTS quadtree minimumExtent anomalies
        /* pad envelope with minimum extent computed */
        spatialcontainer.insert(Quadtree.ensureExtent(pointEnvelope, envelopeMinExtentAbsolute), osmNodeAtLocation);
      }
    }
  } 
        
  /** for the passed in links collection determine the subset that is compatible with the driving direction information on the link given the
   * placement of the waiting area and the mode of transport, e.g. a bus waiting area must reside on the side of the road compatible with the driving
   * direction since a bus only has doors on one side. If it is on the wrong side, it is removed from the link collection that is returned.
   * 
   * @param waitingAreaGeometry the links appear accessible from/to
   * @param eligibleOsmModes of the waiting area
   * @param linksToVerify the links to verify
   * @return links compatible with the driving direction
   * @throws PlanItException thrown if error
   */
  private Collection<Link> filterDrivingDirectionCompatibleLinks(Geometry waitingAreaGeometry, Collection<String> eligibleOsmModes, Collection<Link> linksToVerify) throws PlanItException {
    boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(getZoningReaderData().getCountryName());    
    Collection<Mode> accessModes = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModes(OsmModeUtils.extractPublicTransportModesFrom(eligibleOsmModes));
    return PlanitLinkUtils.excludeLinksOnWrongSideOf(waitingAreaGeometry, linksToVerify, isLeftHandDrive, accessModes, getGeoUtils());  
  }


  /** From the provided options, select the most appropriate based on proximity, mode compatibility, relative location to transfer zone, and importance of the osm way type
   *  
   * @param transferZone under consideration
   * @param osmAccessMode access mode to use
   * @param eligibleLinks for connectoids
   * @return most appropriate link that is found
   * @throws PlanItException thrown if error
   */
  private Link findMostAppropriateStopLocationLinkForWaitingArea(TransferZone transferZone, String osmAccessMode, Collection<Link> eligibleLinks) throws PlanItException {
       
    /* Preprocessing only for user warning:
     * check if closest road is compatible regarding driving direction (relative location of waiting area versus road) 
     * if not, a user warning is needed in case of possible tagging error regarding closest road (not being valid) for transfer zone, then try to salvage */
    boolean salvaging = false;
    do{
      Link closestLink = (Link) PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), eligibleLinks, getGeoUtils());
      Collection<Link> result = filterDrivingDirectionCompatibleLinks(transferZone.getGeometry(), Collections.singleton(osmAccessMode), Collections.singleton(closestLink));
      if(result!=null && !result.isEmpty()){
        /* closest is also viable, continue */
        break;        
      }else {
        /* closest link is on the wrong side of the waiting area, let user know, possibly tagging error */
        LOGGER.fine(String.format("Waiting area (osm id %s) for mode %s is situated on the wrong side of closest eligible road %s, attempting to salvage",
            transferZone.getExternalId(),osmAccessMode,closestLink.getExternalId()));
        eligibleLinks.remove(closestLink);
        salvaging = true;
      }
      
      if(eligibleLinks.isEmpty()){
        break;
      }
    }while(true);      
   
    if(eligibleLinks.isEmpty()) {
      logWarningIfNotNearBoundingBox(
          String.format("DISCARD: No suitable stop_location on correct side of osm way candidates available for transfer zone %s and mode %s", transferZone.getExternalId(), osmAccessMode), transferZone.getGeometry());
      return null;
    }
    
    /* reduce options based on proximity to closest viable link, without ruling out other options that might also be valid*/
    Link selectedAccessLink = null;
    Pair<? extends Edge, Set<? extends Edge>> candidatesForStopLocation = PlanitGraphGeoUtils.findEdgesClosest(
        transferZone.getGeometry(), eligibleLinks, OsmPublicTransportReaderSettings.DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M, getGeoUtils());        
    if(candidatesForStopLocation==null) {
      throw new PlanItException("No closest link could be found from selection of eligible closeby links when finding stop locations for transfer zone (osm entity id %s), this should not happen", transferZone.getExternalId());
    }
        
    if(candidatesForStopLocation.second() == null || candidatesForStopLocation.second().isEmpty() ) {
      /* only one option */
      selectedAccessLink = (Link) candidatesForStopLocation.first();
    }else {      
      
      /* multiple candidates still, filter candidates based on availability of valid stop location checking (mode support, correct location compared to zone etc.) */      
      Mode accessMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmAccessMode);
      MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(accessMode);
      
      @SuppressWarnings("unchecked")
      Set<Link> candidatesToFilter = (Set<Link>) candidatesForStopLocation.second();
      candidatesToFilter.add((Link)candidatesForStopLocation.first());
      
      /* 1) reduce options by removing all compatible links within proximity of the closest link that are on the wrong side of the road infrastructure */
      candidatesToFilter = (Set<Link>) filterDrivingDirectionCompatibleLinks(transferZone.getGeometry(), Collections.singleton(osmAccessMode), candidatesToFilter);      

      /* 2) make sure a valid stop_location on each remaining link can be created (for example if stop_location would be on an extreme node, it is possible no access link segment upstream of that node remains 
       *    which would render an otherwise valid position invalid */
      Iterator<? extends Edge> iterator = candidatesToFilter.iterator();
      while(iterator.hasNext()) {      
        Edge candidateLink = iterator.next();
        Point connectoidLocation = getConnectoidHelper().findConnectoidLocationForstandAloneTransferZoneOnLink(
            transferZone, (Link)candidateLink, accessMode, getSettings().getStopToWaitingAreaSearchRadiusMeters(), networkLayer);
        if(connectoidLocation == null) {
          iterator.remove();
        }          
      }
      
      if(candidatesToFilter == null || candidatesToFilter.isEmpty() ) {
        logWarningIfNotNearBoundingBox(String.format("DISCARD: No suitable stop_location on potential osm way candidates found for transfer zone %s and mode %s", transferZone.getExternalId(), accessMode.getName()), transferZone.getGeometry());
        return null;
      }
      
      /* 3) filter based on link hierarchy using osm way types, the premise being that bus services tend to be located on main roads, rather than smaller roads 
       * there is no hierarchy for rail, so we only do this for road modes. This could allow slightly misplaced waiting areas with multiple options near small and big roads
       * to be salvaged in favour of the larger road */
      if(OsmRoadModeTags.isRoadModeTag(osmAccessMode)){
        OsmWayUtils.removeEdgesWithOsmHighwayTypesLessImportantThan(OsmWayUtils.findMostProminentOsmHighWayType(candidatesToFilter), candidatesToFilter);
      }
        
      if(candidatesToFilter.size()==1) {
        selectedAccessLink = (Link) candidatesToFilter.iterator().next();
      }else {
        /* 4) still multiple options, now select closest from the remaining candidates */
        selectedAccessLink = (Link)PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), candidatesToFilter, getGeoUtils());
      }
  
    }
    
    if(salvaging == true) {
      LOGGER.info(String.format("SALVAGED: Used non-closest osm way to %s %s to ensure waiting area %s is on correct side of road for mode %s", 
          selectedAccessLink.getExternalId(), selectedAccessLink.getName() != null ?  selectedAccessLink.getName() : "" , transferZone.getExternalId(), osmAccessMode));  
    }    
    
    return selectedAccessLink;    
  }


  /** Find all links that are within the given search bounding box and are mode compatible with the given mode. 
   * 
   * @param osmEntityId the osm id of the waiting area
   * @param waitingAreaGeometry to collect accessible links for 
   * @param eligibleOsmMode mode supported by the waiting area
   * @param searchBoundingBox to use
   * @return all links that are deemed accessible for this waiting area
   * @throws PlanItException thrown if error
   */
  private Collection<Link> findModeBBoxCompatibleLinksForOsmGeometry(Long osmEntityId, Geometry waitingAreaGeometry, String eligibleOsmMode, Envelope searchBoundingBox) throws PlanItException {
        
    Collection<String> eligibleOsmModes = Collections.singleton(eligibleOsmMode);
    /* match links spatially */
    Collection<Link> spatiallyMatchedLinks = getZoningReaderData().getPlanitData().findLinksSpatially(searchBoundingBox);    
    if(spatiallyMatchedLinks == null || spatiallyMatchedLinks.isEmpty()) {
      return null;
    }    
  
    /* filter based on mode compatibility */
    Collection<Link> modeAndSpatiallyCompatibleLinks = getPtModeHelper().filterModeCompatibleLinks(eligibleOsmModes, spatiallyMatchedLinks, false /*only exact matches allowed */);    
    if(modeAndSpatiallyCompatibleLinks == null || modeAndSpatiallyCompatibleLinks.isEmpty()) {
      return null;
    }
    
    return modeAndSpatiallyCompatibleLinks;
  }

  /** find links that are within the given search bounding box and are mode compatible with the given reference modes. If more links are found
   * than maxMatches, reduce the results to the closest maxMatches. We also make sure that in case multiple matches are allowed we only select
   * multiple links if they represent parallel train lines.
   * 
   * @param stationEntity osm station entity
   * @param transferZone to find accessible links for
   * @param referenceOsmMode station mode that should be supported by the links
   * @param searchBoundingBox search area to identify links spatially
   * @param maxMatches number of matches at most that is allowed (typically only higher than 1 for train stations)
   * @return found links most likely to be accessible by the station
   * @throws PlanItException thrown if error
   */
  private Collection<Link> findStopLocationLinksForStation(OsmEntity stationEntity, TransferZone transferZone, String referenceOsmMode, Envelope searchBoundingBox, Integer maxMatches) throws PlanItException {
        
    Collection<Link> directionModeSpatiallyCompatibleLinks = findModeBBoxCompatibleLinksForOsmGeometry(stationEntity.getId(), transferZone.getGeometry(), referenceOsmMode, searchBoundingBox);
    if(directionModeSpatiallyCompatibleLinks==null || directionModeSpatiallyCompatibleLinks.isEmpty()) {
      return null;
    }
    
    /* #matches compatibility */
    Collection<Link> chosenLinksForStopLocations = null;    
    {
      Link idealAccessLink = findMostAppropriateStopLocationLinkForWaitingArea(transferZone, referenceOsmMode, directionModeSpatiallyCompatibleLinks);            
      
      if(idealAccessLink==null) {
        throw new PlanItException("No appropriate link could be found from selection of eligible closeby links when finding stop locations for station %s, this should not happen", transferZone.getExternalId());
      }
      
      if(maxMatches==1) {
        
        /* road based station would require single match to link, e.g. bus station */
        chosenLinksForStopLocations = Collections.singleton(idealAccessLink);
        
      }else if(maxMatches>1) {

        /* multiple matches allowed, indicating we are searching for parallel platforms -> use closest match to establish a virtual line from station to this link's closest intersection point
         * and use it to identify other links that intersect with this virtual line, these are our parallel platforms */
        chosenLinksForStopLocations = new HashSet<Link>();      
        LineSegment stationToClosestPointOnClosestLinkSegment = null;
       
        /* create virtual line passing through station and closest link's geometry, extended to eligible distance */          
        if(Osm4JUtils.getEntityType(stationEntity) == EntityType.Node) {
          Coordinate closestCoordinate = OsmNodeUtils.findClosestProjectedCoordinateTo((OsmNode)stationEntity, idealAccessLink.getGeometry(), getGeoUtils());
          Point osmStationLocation = PlanitJtsUtils.createPoint(OsmNodeUtils.createCoordinate((OsmNode)stationEntity));
          stationToClosestPointOnClosestLinkSegment = PlanitJtsUtils.createLineSegment(osmStationLocation.getCoordinate(),closestCoordinate);
        }else if(Osm4JUtils.getEntityType(stationEntity) == EntityType.Way) {
          stationToClosestPointOnClosestLinkSegment = OsmWayUtils.findMinimumLineSegmentBetween((OsmWay)stationEntity, idealAccessLink.getGeometry(), getNetworkToZoningData().getOsmNodes(), getGeoUtils());
        }else {
          throw new PlanItException("unknown entity type %s for osm station encountered, this should not happen", Osm4JUtils.getEntityType(stationEntity).toString());
        }      
        final LineSegment interSectionLineSegment = getGeoUtils().createExtendedLineSegment(stationToClosestPointOnClosestLinkSegment, getSettings().getStationToParallelTracksSearchRadiusMeters(), true, true);
        final Geometry virtualInterSectionGeometryForParallelTracks = PlanitJtsUtils.createLineString(interSectionLineSegment.getCoordinate(0),interSectionLineSegment.getCoordinate(1));
        
        /* find all links of compatible modes that intersect with virtual line reflecting parallel accessible (train) tracks eligible to create a platform for */
        for(Link link : directionModeSpatiallyCompatibleLinks) {
          if( link.getGeometry().intersects(virtualInterSectionGeometryForParallelTracks)) {
            /* intersect so still possible */
            final LinearLocation closestLinkLinearLocation = getGeoUtils().getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(transferZone.getGeometry(), link.getGeometry());
            final Point closestLinkLocation = PlanitJtsUtils.createPoint(closestLinkLinearLocation.getCoordinate(link.getGeometry()));            
            final double distanceStationToPotentialAccessLink = getGeoUtils().getClosestDistanceInMeters(closestLinkLocation, transferZone.getGeometry());
            if(distanceStationToPotentialAccessLink < getSettings().getStationToWaitingAreaSearchRadiusMeters()) {
              /* within distance set, so valid */
              chosenLinksForStopLocations.add(link);  
            }
          }
        }
      }else if(maxMatches<1) {
        LOGGER.severe(String.format("Invalid number of maximum matches %d provided when finding stop location links for station %d",maxMatches, stationEntity.getId()));
        return null;
      }        
    }  
    
    if(chosenLinksForStopLocations== null || chosenLinksForStopLocations.isEmpty()) {
      /* cannot happen because at least the closestLinkForStopLocation we know exists should be found here */
      throw new PlanItException("No links could be identified from virtual line connecting station to closest by point on closest link for osm station %d, this should not happen", stationEntity.getId());
    }
    
    return chosenLinksForStopLocations;
  }  
        

  /**
   * Try to extract a station from the entity. In case no existing platforms/stop_positions can be found nearby we create them fro this station because the station
   * represents both platform(s), station, and potentially stop_positions. In case existing platforms/stop_positinos can be found, it is likely the station is a legacy
   * tag that is not yet properly added to an existing stop_area, or the tagging itself only provides platforms without a stop_area. Either way, in this case the station is
   * to be discarded since appropriate infrastructure is already available.
   * 
   * @param osmStation to identify non stop_area station for
   * @param eligibleSearchBoundingBox the search area to see if more detailed and related existing infrastructure can be found that is expected to be conected to the station
   * @throws PlanItException thrown if error
   */
  private void processStationNotPartOfStopArea(OsmEntity osmStation, Envelope eligibleSearchBoundingBox) throws PlanItException {    
    /* mark as processed */
    Map<String,String> tags = OsmModelUtil.getTagsAsMap(osmStation);    
    OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(tags);
    getZoningReaderData().getOsmData().removeUnproccessedStation(ptVersion, osmStation);
                
    /* eligible modes for station, must at least support one or more mapped modes */
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmStation.getId(), tags, OsmModeUtils.identifyPtv1DefaultMode(tags));
    Collection<String> eligibleOsmModes = modeResult!= null ? modeResult.first() : null;
    Set<TransferZone> matchedTransferZones = new HashSet<TransferZone>();
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getPlanitData().getTransferZonesSpatially(eligibleSearchBoundingBox);
    if(potentialTransferZones != null && !potentialTransferZones.isEmpty()) {          
            
      /* find potential matched transfer zones based on mode compatibility while tracking group memberships, for groups with multiple members
       * we enforce exact mode compatibility and do not allow for pseudo compatibility (yet) */
      Set<TransferZoneGroup> potentialTransferZoneGroups = getTransferZoneGroupHelper().findModeCompatibleTransferZoneGroups(eligibleOsmModes, potentialTransferZones, false /* exact mode compatibility */);
      if(potentialTransferZoneGroups!=null && !potentialTransferZoneGroups.isEmpty()) {
        
        /* find transfer group and zone match(es) based on proximity -> then process accordingly */
        if(!potentialTransferZoneGroups.isEmpty()) {
          /* when part of one or more transfer zone groups -> find transfer zone group with closest by transfer zone
           * then update all transfer zones within that group with this station information...
           * (in case multiple stations are close together we only want to update the right one) 
           */      
          TransferZone closestZone = PlanitTransferZoneUtils.findTransferZoneClosestByTransferGroup(osmStation, potentialTransferZoneGroups, getNetworkToZoningData().getOsmNodes(), getGeoUtils());
          Set<TransferZoneGroup> groups = closestZone.getTransferZoneGroups();
          for(TransferZoneGroup group : groups) {
            TransferZoneGroupHelper.updateTransferZoneGroupStationName(group, osmStation, tags);
            for(TransferZone zone : group.getTransferZones()) {
              PlanitTransferZoneUtils.updateTransferZoneStationName(zone, tags);
              matchedTransferZones.add(zone);          
            }
          }
        }        
          
      }else {
        
        /* try finding stand-alone transfer zones that are pseudo mode compatible instead, i.e., we are less strict at this point */
        Set<TransferZone> modeCompatibleTransferZones = getTransferZoneHelper().filterModeCompatibleTransferZones(eligibleOsmModes, potentialTransferZones, true /* allow pseudo mode compatibility*/);
        if(modeCompatibleTransferZones != null && !modeCompatibleTransferZones.isEmpty()){
          for(TransferZone zone : modeCompatibleTransferZones) {
            PlanitTransferZoneUtils.updateTransferZoneStationName(zone, tags);
            matchedTransferZones.add(zone);
          }        
        }    
      }
    }
                
    if(matchedTransferZones.isEmpty()) {
      /* create a new station with transfer zones and connectoids based on the stations eligible modes (if any) 
       * it is however possible that we found no matches because the station represents only unmapped modes, e.g. ferry 
       * in which case we can safely skip*/
      if(OsmModeUtils.hasMappedPlanitMode(modeResult)) {
                
        /* * 
         * station with mapped modes 
         * --> extract a new station including dummy transfer zones and connectoids 
         * */
        extractStandAloneStation(osmStation, tags, getGeoUtils());
      }             
    }else if(LOGGER.getLevel() == Level.FINE){            
      String transferZonesExternalId = matchedTransferZones.stream().map( z -> z.getExternalId()).collect(Collectors.toSet()).toString();
      LOGGER.fine(String.format("Station %d mapped to platform/pole(s) %s",osmStation.getId(), transferZonesExternalId));
    }        
  }

  /**
   * process remaining unprocessed stations of a particular type that are not part of any stop_area. This means the station reflects both a transfer zone and an
   * implicit stop_position at the nearest viable node if it cannot be mapped to a nearby platform/stop_area
   * @param unprocessedStations stations to process
   * @param type of the stations
   * @throws PlanItException  thrown if error
   */  
  private void processStationsNotPartOfStopArea(Set<OsmEntity> unprocessedStations, EntityType type, OsmPtVersionScheme ptVersion) throws PlanItException {    
    if(unprocessedStations != null) {
      for(OsmEntity osmStation : unprocessedStations){
        
        Envelope boundingBox = OsmBoundingAreaUtils.createBoundingBoxForOsmWay(osmStation, getSettings().getStationToWaitingAreaSearchRadiusMeters(), getNetworkToZoningData().getOsmNodes(), getGeoUtils());        
        if(boundingBox!= null) {
          
          /* process based on bounding box */
          processStationNotPartOfStopArea(osmStation, boundingBox);
          
          /* profile */
          switch (ptVersion) {
          case VERSION_1:
            getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.STATION);  
            break;
          case VERSION_2:
            getProfiler().incrementOsmPtv2TagCounter(OsmPtv1Tags.STATION);  
            break;
          default:
            LOGGER.severe(String.format("unknown Pt version found %s when processing station %s not part of a stop_area",osmStation.getId(),ptVersion.toString()));
            break;
          }
          
        }
      }
    }
  }

  /**
   * process any remaining unprocessed stations that are not part of any stop_area. This means the station reflects both a transfer zone and an
   * implicit stop_position at the nearest viable node
   *  
   * @throws PlanItException thrown if error
   */
  private void processStationsNotPartOfStopArea() throws PlanItException {
    OsmZoningReaderOsmData osmData = getZoningReaderData().getOsmData();
    /* Ptv1 node station */
    if(!osmData.getUnprocessedPtv1Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(osmData.getUnprocessedPtv1Stations(EntityType.Node).values()); 
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Node, OsmPtVersionScheme.VERSION_1);
    }
    /* Ptv1 way station */
    if(!osmData.getUnprocessedPtv1Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(osmData.getUnprocessedPtv1Stations(EntityType.Way).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Way, OsmPtVersionScheme.VERSION_1);                       
    }
    /* Ptv2 node station */    
    if(!osmData.getUnprocessedPtv2Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(osmData.getUnprocessedPtv2Stations(EntityType.Node).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Node, OsmPtVersionScheme.VERSION_2);            
    }
    /* Ptv2 way station */    
    if(!osmData.getUnprocessedPtv2Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(osmData.getUnprocessedPtv2Stations(EntityType.Way).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Way, OsmPtVersionScheme.VERSION_2);                
    }       
  }

  /** process the stop_position represented by the provided osm node that is nto part of any stop_area and therefore has not been matched
   * to any platform/pole yet, i.e., transfer zone. It is our task to do that now (if possible).
   * @param osmNode to process as stop_position if possible
   * @param tags of the node
   * @throws PlanItException thrown if error
   */
  private void processStopPositionNotPartOfStopArea(OsmNode osmNode, Map<String, String> tags) throws PlanItException {   
    getZoningReaderData().getOsmData().removeUnprocessedStopPosition(osmNode.getId());
        
    /* modes for stop_position */
    String defaultOsmMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, defaultOsmMode);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      /* no eligible modes mapped to planit mode, ignore stop_position */
      return;
    }
    
    Collection<String> eligibleOsmModes = modeResult.first();
    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    for(String osmMode: eligibleOsmModes) {
      Mode planitMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmMode);
      MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(planitMode);
    
      /* a stop position should be part of an already parsed planit link, if not it is incorrectly tagged */
      if(!getNetworkToZoningData().getNetworkLayerData(networkLayer).isLocationPresentInLayer(osmNodeLocation)) {
        LOGGER.fine(String.format("DISCARD: stop_location %d is not part of any parsed link in the network, likely incorrectly tagged", osmNode.getId()));
        continue;
      }
  
      /* locate transfer zone(s) for stop_location/mode combination */
      Collection<TransferZone> matchedTransferZones = getTransferZoneHelper().findTransferZonesForStopPosition(osmNode, tags, Collections.singleton(osmMode));      
      if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
        logWarningIfNotNearBoundingBox(String.format("DISCARD: stop_position %d has no valid pole, platform, station reference, nor closeby infrastructure that qualifies as such for mode %s",osmNode.getId(), osmMode),osmNodeLocation);
        return;
      }
      
      /* create connectoid(s) for stop_location/transferzone/mode combination */
      for(TransferZone transferZone : matchedTransferZones) {
        getConnectoidHelper().extractDirectedConnectoidsForMode(osmNode, transferZone, planitMode, getGeoUtils());          
      }           
    }
         
  }

  /**
   * process any remaining unprocessed stop_positions that are not part of any stop_area. This means the stop_position has not yet been matched
   * to any platform/pole, i.e., transfer zone. It is our task to do that now (if possible).
   *  
   * @throws PlanItException thrown if error
   */
  private void processStopPositionsNotPartOfStopArea() throws PlanItException {
    Set<Long> unprocessedStopPositions = new TreeSet<Long>(getZoningReaderData().getOsmData().getUnprocessedStopPositions());
    if(!unprocessedStopPositions.isEmpty()) {
      for(Long osmNodeId : unprocessedStopPositions) {
        OsmNode osmNode =getNetworkToZoningData().getOsmNodes().get(osmNodeId);
        processStopPositionNotPartOfStopArea(osmNode, OsmModelUtil.getTagsAsMap(osmNode));  
      }       
    }
  }

  /**
   * process a remaining transfer zones without any connectoids that is not part of any stop_area. This means that it has no stop_position or the stop_position 
   * has not yet been matched to any platform/pole, i.e., transferzone. It is our task to do that now (if possible).
   * 
   * @param transferZone remaining unprocessed transfer zone (without connectoids)
   * @throws PlanItException thrown if error
   */  
  private void processIncompleteTransferZone(TransferZone transferZone) throws PlanItException {
    
    EntityType osmEntityType = PlanitTransferZoneUtils.extractOsmEntityType(transferZone);
    long osmEntityId = Long.valueOf(transferZone.getExternalId());     
        
    /* validate mode support */
    Collection<String> accessOsmModes = 
        OsmModeUtils.extractPublicTransportModesFrom(PlanitTransferZoneUtils.getRegisteredOsmModesForTransferZone(transferZone));
    if(!getNetworkToZoningData().getNetworkSettings().hasAnyMappedPlanitMode(accessOsmModes)) {
      LOGGER.warning(String.format("DISCARD: waiting area (osm id %d) has no supported public transport planit modes present", osmEntityId));
      return;             
    }
        
    /* validate location is separated from infrastructure */
    boolean transferZoneOnInfrastructure = osmEntityType.equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmEntityId);
    if(transferZoneOnInfrastructure) {
      /* all waiting areas that are on road/rail infrastructure should immediately have been processed including their connectoid creation, so if we still find one here
       * something went wrong that is worth logging */
      LOGGER.severe(String.format("DISCARD: waiting area (osm id %d) identified to be placed on road/rail infrastructure, this shouldn't happen", osmEntityId));
      return;
    }     
    
    /* per mode - find links, because if the transfer zone supports both a rail and road mode, we require different links for our connectoids */
    for(String osmAccessMode : accessOsmModes) {      
      Mode accessMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmAccessMode);
      MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(accessMode);
    
      Link selectedAccessLink = null;
      if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmEntityId, osmEntityType)) {
        
        /* user override for what link to use for connectoids */        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmEntityId, osmEntityType);
        selectedAccessLink = PlanitLinkUtils.getClosestLinkWithOsmWayIdToGeometry( osmWayId, transferZone.getGeometry(), networkLayer, getGeoUtils());
        if(selectedAccessLink == null) {
          LOGGER.warning(String.format("DISCARD: User nominated osm way %d not available for waiting area %s",osmWayId, transferZone.getExternalId()));
          return;
        }
        
      }else {
        
        /* regular approach */                     
        Envelope searchBoundingBox = getGeoUtils().createBoundingBox(transferZone.getEnvelope(), getSettings().getStopToWaitingAreaSearchRadiusMeters());
        
        /* collect spatially, mode, direction compatible links */
        Collection<Link> directionModeSpatiallyCompatibleLinks = findModeBBoxCompatibleLinksForOsmGeometry(osmEntityId, transferZone.getGeometry(), osmAccessMode, searchBoundingBox);        
        if(directionModeSpatiallyCompatibleLinks == null || directionModeSpatiallyCompatibleLinks.isEmpty()) {
          logWarningIfNotNearBoundingBox(String.format("DISCARD: No accessible links (max distance %.2fm) for waiting area %s, mode %s (tag error or consider activating more road types)", getSettings().getStopToWaitingAreaSearchRadiusMeters(), transferZone.getExternalId(), osmAccessMode), transferZone.getGeometry());
          return;
        }
        
        /* based on candidates, now select the most appropriate option based on a multitude of criteria */
        selectedAccessLink = findMostAppropriateStopLocationLinkForWaitingArea(transferZone, osmAccessMode, directionModeSpatiallyCompatibleLinks);        
      }
       
      /* create connectoids */    
      if(selectedAccessLink != null) {
        getConnectoidHelper().extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(
            Long.valueOf(transferZone.getExternalId()),transferZone.getGeometry(),selectedAccessLink, transferZone, accessMode, getSettings().getStopToWaitingAreaSearchRadiusMeters(), networkLayer);
      }
    }
    
  }

  /**
   * process remaining transfer zones without any connectoids yet that are not part of any stop_area. This means that it has no stop_position or the stop_position 
   * has not yet been matched to any platform/pole, i.e., transferzone. It is our task to do that now (if possible).
   * 
   * @param transferZones remaining unprocessed transfer zones (without connectoids)
   * @param osmEntityType of the unprocessed transfer zones
   *  
   * @throws PlanItException thrown if error
   */
  private void processIncompleteTransferZones(Collection<TransferZone> transferZones) throws PlanItException {
    Set<TransferZone> unprocessedTransferZones = new TreeSet<TransferZone>(transferZones);
    for(TransferZone transferZone : unprocessedTransferZones) {
      /* only process incomplete zones (without connectoids) */
      if(!getZoningReaderData().getPlanitData().hasConnectoids(transferZone)) {
        processIncompleteTransferZone(transferZone);
      }
    }                
  }

  /**
   * All transfer zones that were created without connectoids AND were found to not be linked to any stop_positions in a stop_area will still have no connectoids.
   * Connectoids need to be created based on implicit stop_position of vehicles which by OSM standards is defined as based on the nearest node. This is what we will do here.
   * 
   * @throws PlanItException thrown if error
   */
  private void processIncompleteTransferZones() throws PlanItException {
    processIncompleteTransferZones(getZoningReaderData().getPlanitData().getTransferZonesByOsmId(EntityType.Node)); 
    processIncompleteTransferZones(getZoningReaderData().getPlanitData().getTransferZonesByOsmId(EntityType.Way));    
  }
  

   /** Once a station is identified as stand-alone during processing and the transfer zone is created/identified we create its connectoids here.
   * 
   * @param osmStation to extract from
   * @param tags of the station
   * @param stationTransferZone transfer zone of the station
   * @param osmAccessModes the eligible modes
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  private void extractStandAloneStationConnectoids(OsmEntity osmStation, Map<String, String> tags, TransferZone stationTransferZone, Collection<String> osmAccessModes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    EntityType osmStationEntityType = Osm4JUtils.getEntityType(osmStation);
        
    /* modes */
    for(String osmAccessMode : osmAccessModes) {
      Mode planitMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmAccessMode);
      MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(planitMode);
      
      
      /* station mode determines where to create stop_locations and how many. It is special in this regard and different from a regular transfer zone (platform/pole) */
      Pair<Double, Integer> result = determineSearchDistanceAndMaxStopLocationMatchesForStandAloneStation(osmStation.getId(), osmAccessMode, getSettings());
      if(result == null) {
        LOGGER.warning(String.format("DISCARD: unable to process stand-alone station %d supported mode %s, skip", osmStation.getId(),osmAccessMode));
        continue;
      }
      double maxSearchDistance =result.first();
      int maxStopLocations = result.second(); 
      
      /* links per mode*/
      Collection<Link> accessLinks = null;
      if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmStation.getId(), osmStationEntityType)) {
        
        /* user override for what link to use for connectoids */        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmStation.getId(), osmStationEntityType);
        Link nominatedLink = PlanitLinkUtils.getClosestLinkWithOsmWayIdToGeometry( osmWayId, stationTransferZone.getGeometry(), networkLayer, geoUtils);
        if(nominatedLink != null) {
          accessLinks = Collections.singleton(nominatedLink); 
        }else {
          LOGGER.severe(String.format("User nominated osm way not available for station %d",osmWayId));
        }                    
        
      }else {      
      
        /* regular approach */
        /* accessible links for station conditioned on found modes, proximity, relative location to transfer zone and importance of osm way type (if applicable) */
        Envelope searchBoundingBox = OsmBoundingAreaUtils.createBoundingBoxForOsmWay(osmStation, maxSearchDistance , getNetworkToZoningData().getOsmNodes(), geoUtils);
        accessLinks = findStopLocationLinksForStation(osmStation, stationTransferZone, osmAccessMode, searchBoundingBox, maxStopLocations);
      }
      
      if(accessLinks == null) {
        logWarningIfNotNearBoundingBox(String.format("DISCARD: station %d has no eligible accessible links to qualify for pt vehicles as stop locations",osmStation.getId()),stationTransferZone.getGeometry() );
        return;
      }                   
      
      /* connectoids per mode per link */
      for(Link accessLink : accessLinks) {
      
        /* identify locations based on links and spatial restrictions, because no stop_location is known in osm, nearest osm node might be to
         * far away and we must break the link and insert a planit node without an osm node counterpart present, this is what the below method does */
       getConnectoidHelper(). extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(
            osmStation.getId(), stationTransferZone.getGeometry() , accessLink, stationTransferZone, planitMode, getSettings().getStationToWaitingAreaSearchRadiusMeters(), networkLayer);            
      }
    }
  }


  /** Once a station is identified as stand-alone during processing, i.e., no platforms, poles nearby, we must create the appropriate
   * transfer zones (without connectoids). Then afterwards we create the connectoids as well
   * 
   * @param osmStation to extract from
   * @param tags of the station
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  private void extractStandAloneStation(OsmEntity osmStation, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {   
        
    /* modes */
    String defaultMode = OsmModeUtils.identifyPtv1DefaultMode(tags, OsmRailModeTags.TRAIN);
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmStation.getId(), tags, defaultMode);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      return;
    }
    Collection<String> osmAccessModes = modeResult.first();
        
    TransferZone stationTransferZone = null;
    EntityType osmStationEntityType = Osm4JUtils.getEntityType(osmStation);
    boolean stationOnTrack = osmStationEntityType.equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmStation.getId()); 
    if(stationOnTrack && !getSettings().isOverwriteStopLocationWaitingArea(osmStation.getId())) {              
      /* transfer zone + connectoids */
            
      /* station is stop_location as well as transfer zone, create both transfer zone and connectoids based on this location */      
      OsmNode osmStationNode = getNetworkToZoningData().getOsmNodes().get(osmStation.getId());
      TransferZoneType ptv1TransferZoneType = PlanitTransferZoneUtils.extractTransferZoneTypeFromPtv1Tags(osmStationNode, tags);
      getTransferZoneHelper().createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmStationNode, tags, defaultMode, ptv1TransferZoneType, geoUtils);      
      
    }else{
      /* either station is not on track, or it is, but a different transfer zone is user mandated, either way, we must obtain the transfer zone
       * separately from the connectoids */        
            
      /* transfer zone*/
      {        
        if(getSettings().isOverwriteStopLocationWaitingArea(osmStation.getId())) {
          
          /* transfer zone to use is user replaced, so immediately adopt this transfer zone  */
          Pair<EntityType, Long> result = getSettings().getOverwrittenStopLocationWaitingArea(osmStation.getId());
          stationTransferZone = getZoningReaderData().getPlanitData().getTransferZoneByOsmId(result.first(), result.second());
          LOGGER.fine(String.format("Mapped station stop_position %d to overwritten waiting area %d", osmStation.getId(), result.second()));
          
        }else {
          
          /* transfer zone not on track, so create separately from connectoids */ 
          stationTransferZone = getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmStation, tags, TransferZoneType.SMALL_STATION, defaultMode, geoUtils);          
        }
            
        if(stationTransferZone == null) {
          LOGGER.warning(String.format("DISCARD: Unable to create transfer zone for osm station %d", osmStation.getId()));
          return;
        }
        PlanitTransferZoneUtils.updateTransferZoneStationName(stationTransferZone, tags);
      }        
      
      /* if station is assigned a particular pre-existing stop_location, no need to find stop_locations (create connectoids) as part of processing the station, this
       * will be taken care of when processing the stop_positions */
      if(getSettings().isWaitingAreaStopLocationOverwritten(osmStationEntityType, osmStation.getId())){         
         return;
      }else {
      
        /* connectoids */
        extractStandAloneStationConnectoids(osmStation, tags, stationTransferZone, osmAccessModes, geoUtils);
      }
      
    }
  }


  /**
   * Process the remaining unprocessed Osm entities that were marked for processing, or were identified as not having explicit stop_position, i.e., no connectoids have yet been created. 
   * Now that we have successfully parsed all relations (stop_areas) and removed Osm entities part of relations, the remaining entities are guaranteed to not be part of any relation and require
   * stand-alone treatment to finalise them. 
   * 
   * @throws PlanItException thrown if error
   */
  private void extractRemainingOsmEntitiesNotPartOfStopArea() throws PlanItException {
    
    /* unproccessed stations -> create transfer zone and connectoids (implicit stop_positions). 
     * - Only create connectoids when station is on track/road, 
     * - otherwise create incomplete transfer zone to be dealt with in processing incomplete transfer zones */
    processStationsNotPartOfStopArea();       
    
    /* unprocessed stop_positions -> do first because ALL transfer zones (without connectoids) are now available and we cant try to  match them
     * to these already existing transfer zones if possible */
    processStopPositionsNotPartOfStopArea();    
        
    /* transfer zones without connectoids, i.e., implicit stop_positions not part of any stop_area, --> create connectoids for remaining transfer zones without connectoids*/
    processIncompleteTransferZones();                  
    
  }  
    
  /** extract a regular Ptv2 stop position that is part of a stop_area relation and is registered before as a stop_position in the main processing phase. 
   * Extract based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param osmNode node that is the stop_position in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractKnownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {    
          
    /* supported modes */
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, null);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      return;
    }
          
    /* find the transfer zones this stop position is eligible for */
    Collection<TransferZone> matchedTransferZones = getTransferZoneHelper().findTransferZonesForStopPosition(osmNode, tags, modeResult.first(), transferZoneGroup);
          
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* still no match, issue warning */
      logWarningIfNotNearBoundingBox(
          String.format("DISCARD: Stop position %d in stop_area %s has no valid pole, platform, station reference, nor closeby infrastructure that qualifies", osmNode.getId(), transferZoneGroup.getExternalId()), OsmNodeUtils.createPoint(osmNode));
      return;
    }             
    
    /* connectoids */
    getConnectoidHelper().extractDirectedConnectoids(osmNode, tags, matchedTransferZones, modeResult.second(), transferZoneGroup);
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation but not yet identified in the regular phase of parsing as a stop_position. Hence it is not properly
   * tagged. In this method we try to salvage it by inferring its properties (eligible modes). We do so, by mapping it to the nearest transfer zone available on the stop_area (if any)
   *  as a last resort attempt
   * 
   * @param osmNode node of unknown stop position
   * @param tags of the node
   * @param transferZoneGroup the group this stop position is part of
   * @throws PlanItException thrown if error
   */
  private void extractUnknownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {
    
    /* not a proper stop_position, so we must infer its properties (eligible modes, transfer zone) */
    Collection<TransferZone> matchedTransferZones = getTransferZoneHelper().findTransferZonesForStopPosition(osmNode, tags, null, transferZoneGroup);
            
    Set<Mode> accessModes;    
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      
      /* log warning unless it relates to stop_position without any activate modes and/or near bounding box */
      if(OsmModeUtils.hasMappedPlanitMode(getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, OsmModeUtils.identifyPtv1DefaultMode(tags)))) {      
        logWarningIfNotNearBoundingBox(
            String.format("DISCARD: stop_position %d without proper tagging on OSM network could not be mapped to closeby transfer zone in stop_area", osmNode.getId()), OsmNodeUtils.createPoint(osmNode));
      }      
      return;
    }else if(matchedTransferZones.size()>1){
      throw new PlanItException("Identified more than one spatially closest transfer zone for stop_position %d that was not tagged as such in stop_area %s, this should nto happen",osmNode.getId(), transferZoneGroup.getExternalId());
    }
    
    TransferZone foundZone = matchedTransferZones.iterator().next();  
    accessModes = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModes(PlanitTransferZoneUtils.getRegisteredOsmModesForTransferZone(foundZone));
    if(accessModes == null) {
      LOGGER.warning(String.format("DISCARD: stop_position %d without proper tagging on OSM network, unable to identify access modes from closest transfer zone in stop_area", osmNode.getId()));
      return;             
    }
             
    /* connectoids */
    getConnectoidHelper().extractDirectedConnectoids(osmNode, tags, Collections.singleton(foundZone), accessModes, transferZoneGroup);   
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param member member in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaStopPosition(final OsmRelationMember member, final TransferZoneGroup transferZoneGroup) throws PlanItException {
    PlanItException.throwIfNull(member, "Stop_area stop_position member null");
    
    /* only proceed when not marked as invalid earlier */
    if(getZoningReaderData().getOsmData().isIgnoreStopAreaStopPosition(member.getType(), member.getId())) {
      return;
    }          
    
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_POSITION);
    
    /* validate state and input */
    if(member.getType() != EntityType.Node) {
      throw new PlanItException("Stop_position %d encountered that it not an OSM node, this is not permitted",member.getId());
    }      
        
    OsmNode stopPositionNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
    if(stopPositionNode==null) {
      /* likely missing because it falls outside bounding box, ignore */
      if(!getSettings().hasBoundingPolygon()) {
        LOGGER.warning(String.format("DISCARD:Unable to extract ptv2 stop position %d in stop area %s, osm node missing", member.getId(), transferZoneGroup.getExternalId()));
      }
      return;
    }
    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(stopPositionNode);            
    Boolean isKnownPtv2StopPosition = null;
    
    /* regular stop_position or special cases due to tagging errors */
    {
      if(getZoningReaderData().getOsmData().hasUnprocessedStopPosition(member.getId())){
      
        /* registered as unprocessed --> known and available for processing */      
        isKnownPtv2StopPosition = true;      
        
      }else {
        
        /* check if connectoids already exist, if not (due to being part of multiple stop_areas or because it is a Ptv1 stop that has already been processed) */
        boolean isPtv2NodeOnly = !OsmPtVersionSchemeUtils.isPtv2StopPositionPtv1Stop(stopPositionNode, tags);
        boolean alreadyProcessed = getZoningReaderData().getPlanitData().hasAnyDirectedConnectoidsForLocation(OsmNodeUtils.createPoint(stopPositionNode)); 
        if(isPtv2NodeOnly && alreadyProcessed) {
          /* stop_position resides in multiple stop_areas, this is strongly discouraged by OSM, but does occur still so we identify and skip (no need to process twice anyway)*/
          LOGGER.fine(String.format("Stop_position %d present in multiple stop_areas, discouraged tagging behaviour, consider retagging",member.getId(), transferZoneGroup.getExternalId()));
        }
        if(alreadyProcessed) {
          /* already matched to at least one transfer zone (platform/pole), no need to attempt and create connectoid again. In case it serves more than one transferzone
           * these other transfer zones remain incomplete and will be supplemented with the connectoid at a later stage */
          return;
        }
        
        /* stop-position not processed and not tagged as stop_position known, so it is not properly tagged and we must infer mode access from infrastructure it resides on to salvage it */      
        LOGGER.fine(String.format("Stop_position %d in stop_area not marked as such on OSM node, inferring transfer zone and access modes by geographically closest transfer zone in stop_area instead ",member.getId()));
        isKnownPtv2StopPosition = false;         
      }
    }
        
    
    /* stop location via OSM node */    
    if(isKnownPtv2StopPosition) {
      
      /* process as regular stop_position */
      extractKnownPtv2StopAreaStopPosition(stopPositionNode, tags, transferZoneGroup);
      /* mark as processed */
      getZoningReaderData().getOsmData().removeUnprocessedStopPosition(stopPositionNode.getId());
      
    }else {
      
      /* unknown, so node is not tagged properly, try to salvage */
      extractUnknownPtv2StopAreaStopPosition(stopPositionNode, tags, transferZoneGroup);
            
    }                             
  }  
  
  /** extract stop area relation of Ptv2 scheme. We create connectoids for all now already present transfer zones.
   * 
   * @param osmRelation to extract stop_area for
   * @param tags of the stop_area relation
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaPostProcessingEntities(OsmRelation osmRelation, Map<String, String> tags) throws PlanItException{
  
    /* transfer zone group */
    TransferZoneGroup transferZoneGroup = getZoningReaderData().getPlanitData().getTransferZoneGroupByOsmId(osmRelation.getId());
    if(transferZoneGroup == null) {
      LOGGER.severe(String.format("found stop_area %d in post-processing for which not PLANit transfer zone group has been created, this should not happen",osmRelation.getId()));
    }
        
    /* process only stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      
      if( skipOsmPtEntity(member)) {
        continue;
      }
                  
      /* stop_position */
      if(member.getRole().equals(OsmPtv2Tags.STOP_ROLE)) {        
        
        extractPtv2StopAreaStopPosition(member, transferZoneGroup);       
        
      }

    }    
       
  }  
  

  /**
   * Constructor
   * 
   * @param transferSettings for the handler
   * @param handlerData the handler data gathered by preceding handlers for zoning parsing
   * @param zoningToPopulate to populate
   * @param profiler to use 
   */
  public OsmZoningPostProcessingHandler(
      final OsmPublicTransportReaderSettings transferSettings, 
      final OsmZoningReaderData handlerData,  
      final Zoning zoningToPopulate,
      final OsmZoningHandlerProfiler profiler) {
    super(transferSettings, handlerData, zoningToPopulate, profiler);        
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * 
   * @throws PlanItException thrown if error
   */
  public void initialiseBeforeParsing(){
    reset();
    
    PlanItRunTimeException.throwIf(
        getSettings().getReferenceNetwork().getTransportLayers() == null || getSettings().getReferenceNetwork().getTransportLayers().size()<=0,
          "network is expected to be populated at start of parsing OSM zoning");
    
    initialiseSpatiallyIndexedOsmNodesInternalToPlanitLinks();
  }  

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmRelation);          
    try {              
      
      /* only parse when parser is active and type is available */
      if(getSettings().isParserActive() && tags.containsKey(OsmRelationTypeTags.TYPE)) {
        
        /* public transport type */
        if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.PUBLIC_TRANSPORT)) {
          
          /* stop_area: stop_positions only */
          if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_AREA)) {
                        
            extractPtv2StopAreaPostProcessingEntities(osmRelation, tags);
            
          }else {
            /* anything else is not expected */
            LOGGER.info(String.format("DISCARD: Unsupported public_transport relation %s referenced by relation %d",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
          }          
          
        }
        
      }      
      
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM relation (id:%d) for transfer infrastructure", osmRelation.getId())); 
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {         
    
    try {
      /* process remaining unprocessed entities that are not part of a relation (stop_area) */
      extractRemainingOsmEntitiesNotPartOfStopArea();
            
    }catch(PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("error while parsing remaining osm entities not part of a stop_area");
    }
            
    LOGGER.fine(" OSM (transfer) zone post-processing ...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    spatiallyIndexedOsmNodesInternalToPlanitLinks = new HashMap<MacroscopicNetworkLayer, Quadtree>();
  }
  
}

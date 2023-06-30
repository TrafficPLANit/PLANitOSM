package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.topobyte.osm4j.core.model.iface.*;
import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.osm.converter.network.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmZoningReaderOsmData;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.*;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
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
    for(MacroscopicNetworkLayer layer : getReferenceNetwork().getTransportLayers()) {
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

  /** From the provided options, select the most appropriate based on proximity, mode compatibility, relative location to transfer zone, and importance of the osm way type
   *
   * @param transferZone under consideration
   * @param osmAccessMode access mode to use
   * @param eligibleLinks for connectoids
   * @return most appropriate link that is found
   */
  private Pair<MacroscopicLink, Set<LinkSegment>> findMostAppropriateStopLocationLinkForWaitingArea(TransferZone transferZone, String osmAccessMode, Collection<MacroscopicLink> eligibleLinks) {
    // prep
    Function<MacroscopicLink, String> linkToSourceId = l -> l.getExternalId();
    var accessModeType = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModeType(osmAccessMode);
    var accessMode = getReferenceNetwork().getModes().get(accessModeType);

    /* 1) reduce candidates to access links related to access link segments that are deemed valid in terms of mode and location (closest already complies as per above) */
    Set<LinkSegment> accessLinkSegments = new HashSet<>(2);
    for(var currAccessLink : eligibleLinks) {
      boolean mustAvoidCrossingTraffic =  ZoningConverterUtils.isAvoidCrossTrafficForAccessMode(accessMode);
      var currAccessLinkSegments = ZoningConverterUtils.findAccessLinkSegmentsForWaitingArea(
          transferZone.getExternalId(),
          transferZone.getGeometry(),
          currAccessLink,
          linkToSourceId.apply(currAccessLink),
          accessMode,
          getZoningReaderData().getCountryName(),
          mustAvoidCrossingTraffic,
          null,
          null,
          getGeoUtils());
      if (currAccessLinkSegments != null && !currAccessLinkSegments.isEmpty()) {
        accessLinkSegments.addAll(currAccessLinkSegments);
      }
    }
    // extract parent links from options
    var candidatesToFilter =
        accessLinkSegments.stream().flatMap( ls -> Stream.of((MacroscopicLink)ls.getParent())).collect(Collectors.toSet());

    /* 2) make sure a valid stop_location on each remaining link can be created (for example if stop_location would be on an extreme node, it is possible no access link segment upstream of that node remains
     *    which would render an otherwise valid position invalid */
    var candidatesWithValidConnectoidLocation = new HashSet<MacroscopicLink>();
    for(var candidate : candidatesToFilter){
      if(null != ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLink(
          transferZone.getExternalId(),
          transferZone.getGeometry(),
          candidate,
          linkToSourceId.apply(candidate),
          accessMode,
          getSettings().getStopToWaitingAreaSearchRadiusMeters(),
          null,
          null,
          null,
          getZoningReaderData().getCountryName(),
          getGeoUtils())){
        candidatesWithValidConnectoidLocation.add(candidate);
      }
    }

    if(candidatesWithValidConnectoidLocation.isEmpty() ) {
      return null;
    }else if(candidatesWithValidConnectoidLocation.size()==1) {
      var selectedAccessLink = candidatesWithValidConnectoidLocation.iterator().next();
      accessLinkSegments.removeIf( ls -> !ls.getParent().equals(selectedAccessLink)); // sync
      return Pair.of(selectedAccessLink, accessLinkSegments);
    }

    /* 3) all proper candidates so  reduce options further based on proximity to closest viable link, while removing options outside of the closest distance buffer */
    var filteredCandidates =
        PlanitGraphGeoUtils.findEdgesWithinClosestDistanceDeltaToGeometry(
            transferZone.getGeometry(), candidatesWithValidConnectoidLocation, OsmPublicTransportReaderSettings.DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M, getGeoUtils()).keySet();
    accessLinkSegments.removeIf( ls -> !filteredCandidates.contains((MacroscopicLink) ls.getParent())); // sync

    if(filteredCandidates.size()==1){
      var selectedAccessLink = filteredCandidates.iterator().next();
      accessLinkSegments.removeIf( ls -> !ls.getParent().equals(selectedAccessLink)); // sync
      return Pair.of(selectedAccessLink, accessLinkSegments);
    }

    /* 4) Remaining options are all valid and close ... choose based on importance, the premise being that road based PT services tend to be located on main roads, rather than smaller roads
     * so, we choose the first link segment with the highest capacity found (if they differ) and then return its parent link as the candidate */
    if(!(accessMode.getPhysicalFeatures().getTrackType() == TrackModeType.RAIL) &&
        filteredCandidates.size()>1 &&
        filteredCandidates.stream().flatMap(l -> l.getLinkSegments().stream()).filter(ls -> accessLinkSegments.contains(ls)).map(
            ls -> ls.getCapacityOrDefaultPcuHLane()).distinct().count()>1){

      // retain only edge segments with the maximum capacity
      var maxCapacity = accessLinkSegments.stream().map(ls -> ((MacroscopicLinkSegment)ls).getCapacityOrDefaultPcuHLane()).max(Comparator.naturalOrder());
      var lowerCapacitySegments = filteredCandidates.stream().flatMap(l -> l.getLinkSegments().stream()).filter(ls -> Precision.smaller( ls.getCapacityOrDefaultPcuHLane(), maxCapacity.get(), Precision.EPSILON_6)).collect(Collectors.toUnmodifiableSet());
      accessLinkSegments.removeAll(lowerCapacitySegments);
      filteredCandidates.removeAll(lowerCapacitySegments.stream().map(ls -> ls .getParentLink()).collect(Collectors.toUnmodifiableSet()));
    }

    /* now find the closest remaining*/
    MacroscopicLink finalSelectedAccessLink = filteredCandidates.iterator().next();
    if(filteredCandidates.size()>1) {
      finalSelectedAccessLink = (MacroscopicLink) PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), filteredCandidates, getGeoUtils());
    }
    final var dummy = finalSelectedAccessLink;
    accessLinkSegments.removeIf(ls -> !ls.getParent().equals(dummy)); // sync

    return Pair.of(finalSelectedAccessLink, accessLinkSegments);
  }

  /** Find all links that are within the given search bounding box, are mode compatible, and have a matching vertical layer index, i.e., reside
   * on the same vertical plane (if the zone has a known layer)
   *
   * @param transferZone the transferZone created based on the underlying OSM entity
   * @param osmEntityId the osm id of the waiting area
   * @param eligibleOsmMode mode supported by the waiting area
   * @param searchBoundingBox to use
   * @return all links that are deemed accessible for this waiting area
   */
  private Collection<MacroscopicLink> findModeBBoxVerticalLayerIdxCompatibleLinksForTransferZone(
      TransferZone transferZone, Long osmEntityId, String eligibleOsmMode, Envelope searchBoundingBox) {
        
    Collection<String> eligibleOsmModes = Collections.singleton(eligibleOsmMode);
    /* match links spatially */
    Collection<MacroscopicLink> spatiallyMatchedLinks = getZoningReaderData().getPlanitData().findLinksSpatially(searchBoundingBox);
    if(spatiallyMatchedLinks == null || spatiallyMatchedLinks.isEmpty()) {
      return null;
    }    
  
    /* filter based on mode compatibility */
    Collection<MacroscopicLink> modeAndSpatiallyCompatibleLinks = getPtModeHelper().filterModeCompatibleLinks(eligibleOsmModes, spatiallyMatchedLinks, false /*only exact matches allowed */);
    if(modeAndSpatiallyCompatibleLinks == null || modeAndSpatiallyCompatibleLinks.isEmpty()) {
      return null;
    }

    /* filter based on vertical layer index compatibility */
    Collection<MacroscopicLink> modeSpatiallyAndVerticalPlaneCompatibleLinks = getTransferZoneHelper().filterVerticalLayerCompatibleLinks(
        transferZone, modeAndSpatiallyCompatibleLinks, true);
    if(modeSpatiallyAndVerticalPlaneCompatibleLinks == null || modeSpatiallyAndVerticalPlaneCompatibleLinks.isEmpty()) {
      return null;
    }
    
    return modeSpatiallyAndVerticalPlaneCompatibleLinks;
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
   */
  private TreeSet<MacroscopicLink> findStopLocationLinksForStation(
      OsmEntity stationEntity, TransferZone transferZone, String referenceOsmMode, Envelope searchBoundingBox, Integer maxMatches){
        
    Collection<MacroscopicLink> directionModeSpatiallyCompatibleLinks = findModeBBoxVerticalLayerIdxCompatibleLinksForTransferZone(
        transferZone, stationEntity.getId(), referenceOsmMode, searchBoundingBox);
    if(directionModeSpatiallyCompatibleLinks==null || directionModeSpatiallyCompatibleLinks.isEmpty()) {
      return null;
    }
    
    /* #matches compatibility */
    TreeSet<MacroscopicLink> chosenLinksForStopLocations = null;
    {
      var idealAccessResult = findMostAppropriateStopLocationLinkForWaitingArea(transferZone, referenceOsmMode, directionModeSpatiallyCompatibleLinks);
      var idealAccessLink = idealAccessResult==null ? null : idealAccessResult.first();
      if(idealAccessLink==null) {
        throw new PlanItRunTimeException("No appropriate link could be found from selection of eligible closeby links when finding stop locations for station %s, this should not happen", transferZone.getExternalId());
      }
      
      if(maxMatches==1) {
        
        /* road based station would require single match to link, e.g. bus station */
        chosenLinksForStopLocations = new TreeSet<>();
        chosenLinksForStopLocations.add(idealAccessLink);
        
      }else if(maxMatches>1) {

        /* multiple matches allowed, indicating we are searching for parallel platforms -> use closest match to establish a virtual line from station to this link's closest intersection point
         * and use it to identify other links that intersect with this virtual line, these are our parallel platforms */
        chosenLinksForStopLocations = new TreeSet<>();
        LineSegment stationToClosestPointOnClosestLinkSegment = null;
       
        /* create virtual line passing through station and closest link's geometry, extended to eligible distance */          
        if(Osm4JUtils.getEntityType(stationEntity) == EntityType.Node) {
          Coordinate closestCoordinate = OsmNodeUtils.findClosestProjectedCoordinateTo((OsmNode)stationEntity, idealAccessLink.getGeometry(), getGeoUtils());
          Point osmStationLocation = PlanitJtsUtils.createPoint(OsmNodeUtils.createCoordinate((OsmNode)stationEntity));
          stationToClosestPointOnClosestLinkSegment = PlanitJtsUtils.createLineSegment(osmStationLocation.getCoordinate(),closestCoordinate);
        }else if(Osm4JUtils.getEntityType(stationEntity) == EntityType.Way) {
          stationToClosestPointOnClosestLinkSegment = OsmWayUtils.findMinimumLineSegmentBetween(
                  (OsmWay)stationEntity, idealAccessLink.getGeometry(), getZoningReaderData().getOsmData().getOsmNodeData().getRegisteredOsmNodes(), getGeoUtils());
        }else {
          throw new PlanItRunTimeException("Unknown entity type %s for osm station encountered, this should not happen", Osm4JUtils.getEntityType(stationEntity).toString());
        }      
        final LineSegment interSectionLineSegment = getGeoUtils().createExtendedLineSegment(stationToClosestPointOnClosestLinkSegment, getSettings().getStationToParallelTracksSearchRadiusMeters(), true, true);
        final Geometry virtualInterSectionGeometryForParallelTracks = PlanitJtsUtils.createLineString(interSectionLineSegment.getCoordinate(0),interSectionLineSegment.getCoordinate(1));
        
        /* find all links of compatible modes that intersect with virtual line reflecting parallel accessible (train) tracks eligible to create a platform for */
        for(var link : directionModeSpatiallyCompatibleLinks) {
          if( link.getGeometry().intersects(virtualInterSectionGeometryForParallelTracks)) {
            /* intersect so still possible */
            final LinearLocation closestLinkLinearLocation = getGeoUtils().getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(transferZone.getGeometry(), link.getGeometry());
            final var closestLinkLocation = closestLinkLinearLocation.getCoordinate(link.getGeometry());
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
      throw new PlanItRunTimeException("No links could be identified from virtual line connecting station to closest by point on closest link for osm station %d, this should not happen", stationEntity.getId());
    }
    
    return chosenLinksForStopLocations;
  }  
        

  /**
   * Try to extract a station from the entity. In case no existing platforms/stop_positions can be found nearby we create them fro this station because the station
   * represents both platform(s), station, and potentially stop_positions. In case existing platforms/stop_positions can be found, it is likely the station is a legacy
   * tag that is not yet properly added to an existing stop_area, or the tagging itself only provides platforms without a stop_area. Either way, in this case the station is
   * to be discarded since appropriate infrastructure is already available.
   * 
   * @param osmStation to identify non stop_area station for
   * @param eligibleOsmModes to consider
   * @param eligibleSearchBoundingBox the search area to see if more detailed and related existing infrastructure can be found that is expected to be conected to the station
   */
  private void processLandBasedStationNotPartOfStopArea(
      OsmEntity osmStation, Collection<String> eligibleOsmModes, Envelope eligibleSearchBoundingBox){

    /* mark as processed */
    Map<String,String> tags = OsmModelUtil.getTagsAsMap(osmStation);    
    OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(tags);

    /* eligible modes for station, must at least support one or more mapped modes */
    Set<TransferZone> matchedTransferZones = new HashSet<>();
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getPlanitData().getTransferZonesSpatially(eligibleSearchBoundingBox);
    if(potentialTransferZones != null && !potentialTransferZones.isEmpty()) {          
            
      /* find potential matched transfer zones based on mode compatibility while tracking group memberships, for groups with multiple members
       * we enforce exact mode compatibility and do not allow for pseudo compatibility (yet) */
      Set<TransferZoneGroup> potentialTransferZoneGroups =
          getTransferZoneGroupHelper().findModeCompatibleTransferZoneGroups(eligibleOsmModes, potentialTransferZones, false /* exact mode compatibility */);
      if(potentialTransferZoneGroups!=null && !potentialTransferZoneGroups.isEmpty()) {
        
        /* find transfer group and zone match(es) based on proximity -> then process accordingly */
        if(!potentialTransferZoneGroups.isEmpty()) {

          /* when part of one or more transfer zone groups -> find transfer zone group with closest by transfer zone
           * then update all transfer zones within that group with this station information...
           * (in case multiple stations are close together we only want to update the right one).
           */      
          TransferZone closestZone = PlanitTransferZoneUtils.findTransferZoneClosestByTransferGroup(
              osmStation,
              potentialTransferZoneGroups,
              getZoningReaderData().getOsmData().getOsmNodeData().getRegisteredOsmNodes(),
              false,
              getGeoUtils());
          Set<TransferZoneGroup> groups = closestZone.getTransferZoneGroups();
          groups.stream().sorted(Comparator.comparing(TransferZoneGroup::getId)).forEach( group -> {
            TransferZoneGroupHelper.updateTransferZoneGroupName(group, osmStation, tags);
            for(TransferZone zone : group.getTransferZones()) {
              PlanitTransferZoneUtils.updateTransferZoneStationName(zone, tags);
              matchedTransferZones.add(zone);          
            }
          });
        }        
          
      }else {
        
        /* try finding stand-alone transfer zones that are pseudo mode compatible instead, i.e., we are less strict at this point */
        // todo consider modeless tranferzone matches if we do not find a match but there exist a modeless transferzone we should find (not yet supported)
        Set<TransferZone> modeCompatibleTransferZones = getTransferZoneHelper().filterModeCompatibleTransferZones(
            eligibleOsmModes, potentialTransferZones, true /* allow pseudo mode compatibility*/, false);
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
      if(!eligibleOsmModes.isEmpty()) {
                
        /* * 
         * station with eligible (OSM)  modes
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
   *
   * @param unprocessedStations stations to process
   * @param type of the stations
   */  
  private void processStationsNotPartOfStopArea(Set<OsmEntity> unprocessedStations, EntityType type, OsmPtVersionScheme ptVersion) {
    if(unprocessedStations != null) {
      unprocessedStations.stream().sorted(Comparator.comparing(OsmEntity::getId)).forEach( osmStation ->{

        var networkSettings = getNetworkToZoningData().getNetworkSettings();
        var tags = OsmModelUtil.getTagsAsMap(osmStation);

        /* mode compatibility check */
        Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
            getPtModeHelper().collectPublicTransportModesFromPtEntity(
                osmStation, tags, OsmModeUtils.identifyPtv1DefaultMode(osmStation.getId(), tags, true));
        if(!OsmModeUtils.hasMappedPlanitMode(modeResult)){
          return;
        }
        Collection<String> eligibleOsmModes = modeResult!= null ? modeResult.first() : null;

        /* special case - when a ferry terminal is marked as a station, we process it as a stand-alone ferry terminal
         * rather than a station... */
        if(networkSettings.isWaterwayParserActive() && OsmPtv1Tags.isFerryTerminal(tags) && OsmWaterModeTags.containsAnyMode(eligibleOsmModes)){

          if(type != EntityType.Node) {
            /* we can only treat stations that are ferry terminals as a stop_position/node based entity at the moment */
            LOGGER.warning(String.format(
                "DISCARD: Found Ptv2 stand alone station (%d) that is tagged as ferry terminal, but that is not an OSM node, verify correctness (tags: %s)",
                osmStation.getId(), tags));
          }else{
            processStandAloneFerryStop((OsmNode) osmStation, TransferZoneType.PLATFORM);
          }

        }
        /* regular station processing */
        else {

          Envelope boundingBox = OsmBoundingAreaUtils.createBoundingBoxForOsmWay(
              osmStation, getSettings().getStationToWaitingAreaSearchRadiusMeters(), getZoningReaderData().getOsmData().getOsmNodeData().getRegisteredOsmNodes(), getGeoUtils());
          if (boundingBox != null) {

            /* process based on bounding box */
            processLandBasedStationNotPartOfStopArea(osmStation, eligibleOsmModes, boundingBox);

          }
        }
        getZoningReaderData().getOsmData().removeUnproccessedStation(ptVersion, osmStation);

        /* profile */
        switch (ptVersion) {
          case VERSION_1:
            getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.STATION);
            break;
          case VERSION_2:
            getProfiler().incrementOsmPtv2TagCounter(OsmPtv1Tags.STATION);
            break;
          default:
            LOGGER.severe(String.format("Unknown Pt version found %s when processing station %s not part of a stop_area", osmStation.getId(), ptVersion.toString()));
            break;
        }
      });
    }
  }

  /**
   * process any remaining unprocessed stations that are not part of any stop_area. This means the station reflects both a transfer zone and an
   * implicit stop_position at the nearest viable node
   *
   */
  private void processStationsNotPartOfStopArea() {
    OsmZoningReaderOsmData osmData = getZoningReaderData().getOsmData();

    /* Ptv1 node station */
    if(!osmData.getUnprocessedPtv1Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<>(osmData.getUnprocessedPtv1Stations(EntityType.Node).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Node, OsmPtVersionScheme.VERSION_1);
    }
    /* Ptv1 way station */
    if(!osmData.getUnprocessedPtv1Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<>(osmData.getUnprocessedPtv1Stations(EntityType.Way).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Way, OsmPtVersionScheme.VERSION_1);                       
    }
    /* Ptv2 node station */    
    if(!osmData.getUnprocessedPtv2Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<>(osmData.getUnprocessedPtv2Stations(EntityType.Node).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Node, OsmPtVersionScheme.VERSION_2);            
    }
    /* Ptv2 way station */    
    if(!osmData.getUnprocessedPtv2Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<>(osmData.getUnprocessedPtv2Stations(EntityType.Way).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Way, OsmPtVersionScheme.VERSION_2);                
    }       
  }

  /**
   * process unprocessed ferry terminal that is not part of any stop_area.
   * This generally means the ferry terminal reflects both the transfer zone explicit stop_position on its node
   *
   * @param osmFerryStop to process
   * @param transferZoneType this stop represents
   * @return created new transfer zone or attached to existing one successfully, false otherwise
   */
  private boolean processStandAloneFerryStop(OsmNode osmFerryStop, TransferZoneType transferZoneType) {
    var tags = OsmModelUtil.getTagsAsMap(osmFerryStop);

    boolean terminalOnNetworkNode = hasNetworkLayersWithActiveOsmNode(osmFerryStop.getId());
    if(terminalOnNetworkNode && getSettings().isOverwriteWaitingAreaOfStopPosition(osmFerryStop.getId())) {

      /* transfer zone to use is user replaced, so immediately adopt this transfer zone  */
      Pair<EntityType, Long> result = getSettings().getOverwrittenWaitingAreaOfStopPosition(osmFerryStop.getId());
      var ferryTerminalTransferZone = getZoningReaderData().getPlanitData().getTransferZoneByOsmId(result.first(), result.second());
      LOGGER.fine(String.format("Mapped ferry stop %d to overwritten waiting area %d", osmFerryStop.getId(), result.second()));
      PlanitTransferZoneUtils.updateTransferZoneStationName(ferryTerminalTransferZone, tags);
      return true;
    }

    /* not overwritten, or not properly overwritten, proceed */

    /* unlike stations we create both the transfer zone and connectoids by default (unless we find this is not how ferries are used most of the time */
    var defaultMode = OsmWaterModeTags.FERRY;
    var modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmFerryStop, tags, OsmWaterModeTags.FERRY);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      return false;
    }

    if(!terminalOnNetworkNode && getSettings().isConnectDanglingFerryStopToNearbyFerryRoute()) {
      /* salvage by creating a new link to attach to ferry network and place terminal on a network node */
      connectDanglingFerryStopToNearbyFerryRoute(osmFerryStop, defaultMode, false);
      terminalOnNetworkNode = hasNetworkLayersWithActiveOsmNode(osmFerryStop.getId());
    }

    if(terminalOnNetworkNode) {

      /* transfer zone + connectoids */
      var createdTransferZone = getTransferZoneHelper().createAndRegisterTransferZoneWithConnectoidsAtOsmNode(
          osmFerryStop, tags, defaultMode, transferZoneType, getGeoUtils());
      return createdTransferZone!=null;

    }else{

      /* transfer zone not on waterway route, and not flagged to be connected to nearest available route */
      LOGGER.severe(String.format("DISCARD: Ferry stop OSM node (%d) is stop location, but not connected to ferry network, if to be kept consider activating connecting dangling ferry stops option", osmFerryStop.getId()));
      return false;

    }
  }

  /**
   * Given a ferry stop location and the mode (ferry) to use, construct a new link and link segments attaching the ferry terminal location
   * to the nearest ferry route (within acceptable search radius). We utilise the cloest existing ferry link's closest existing node as the
   * attachment point.
   *
   * @param osmFerryStop to attach after found dangling
   * @param osmMode mode to apply
   * @param suppressLogging when true suppress logging, false otherwise
   */
  private void connectDanglingFerryStopToNearbyFerryRoute(OsmNode osmFerryStop, String osmMode, boolean suppressLogging) {

    /* find closest ferry link to ferry ferry terminal */
    var ferryStopLocation = OsmNodeUtils.createPoint(osmFerryStop);
    var planitWaterMode = getReferenceNetwork().getModes().get(getNetworkToZoningData().getNetworkSettings().getWaterwaySettings().getMappedPlanitWaterMode(osmMode));
    var networkLayer = this.getReferenceNetwork().getLayerByMode(planitWaterMode);
    var boundingBox = getGeoUtils().createBoundingBox(ferryStopLocation.getEnvelopeInternal(), getSettings().getFerryStopToFerryRouteSearchRadiusMeters());
    Collection<MacroscopicLink> spatiallyMatchedLinks = getZoningReaderData().getPlanitData().findLinksSpatially(boundingBox);
    spatiallyMatchedLinks.removeIf( l -> !l.isModeAllowedOnAnySegment(planitWaterMode));
    if(spatiallyMatchedLinks.isEmpty()){
      LOGGER.warning(String.format("DISCARD: Dangling ferry stop %d, no mode compatible OSM ways within %.2fm found (tags: %s)",
          osmFerryStop.getId(), getSettings().getFerryStopToFerryRouteSearchRadiusMeters(), OsmModelUtil.getTagsAsMap(osmFerryStop)));
      return;
    }
    var closestLinkWithDistance = PlanitEntityGeoUtils.findPlanitEntityClosest(
        OsmNodeUtils.createCoordinate(osmFerryStop),
        spatiallyMatchedLinks, getSettings().getFerryStopToFerryRouteSearchRadiusMeters(),
        suppressLogging,
        getGeoUtils());

    /* create network node at ferry terminal location + find closest node on chosen ferry link */
    var ferryStopNode = PlanitNetworkLayerUtils.createPopulateAndRegisterNode(
        osmFerryStop, networkLayer, getNetworkToZoningData().getNetworkLayerData(networkLayer));
    var closestNodeWithDistance = PlanitEntityGeoUtils.findPlanitEntityClosest(
        ferryStopLocation.getCoordinate(),
        Set.<Node>of(closestLinkWithDistance.first().getNodeA(), closestLinkWithDistance.first().getNodeB()),
        Double.MAX_VALUE,
        suppressLogging,
        getGeoUtils());

    /* create new ferry link to attach to ferry network */
    var lineString = PlanitJtsUtils.createLineString(ferryStopLocation.getCoordinate(), closestNodeWithDistance.first().getPosition().getCoordinate());
    var ferryLink = PlanitNetworkLayerUtils.createPopulateAndRegisterLink(
        ferryStopNode, closestNodeWithDistance.first(), lineString, networkLayer, null, "dummy-ferry-link", getGeoUtils());

    /* create new ferry link segments */
    String waterWayKey = OsmWaterwayTags.ROUTE;
    String waterWayValue = OsmWaterwayTags.FERRY;
    var linkSegmentType = getReferenceNetwork().getDefaultLinkSegmentTypeByOsmTag(waterWayKey, waterWayValue).get(networkLayer);
    var speedLimit = getNetworkToZoningData().getNetworkSettings().getWaterwaySettings().getDefaultSpeedLimit(waterWayValue);
    var lanes = getNetworkToZoningData().getNetworkSettings().getDefaultDirectionalLanesByWayType(waterWayKey, waterWayValue);
    /* a->b */
    PlanitNetworkLayerUtils.createPopulateAndRegisterLinkSegment(
        ferryLink, true /* A->B */, linkSegmentType, speedLimit, lanes, networkLayer);
    /* b->a */
    PlanitNetworkLayerUtils.createPopulateAndRegisterLinkSegment(
        ferryLink, false /* B->A */, linkSegmentType, speedLimit, lanes, networkLayer);

    /* register the ferry terminal as a network node so we can look it up by its id when needed (this is needed when verifying
     * if the OSM node is now part of the physical network such that we can process the ferry terminal as a regular terminal from
     * now on */
    getNetworkToZoningData().registerNetworkOsmNode(osmFerryStop);
  }

  /**
   * process any remaining unprocessed ferry terminals that are not part of any stop_area.
   * This generally means the ferry terminal reflects both the transfer zone explicit stop_position on its node
   */
  private void processPtv1FerryTerminalsNotPartOfStopArea() {
    OsmZoningReaderOsmData osmData = getZoningReaderData().getOsmData();
    if(!getNetworkToZoningData().getNetworkSettings().isWaterwayParserActive()){
      return;
    }

    osmData.getUnprocessedPtv1FerryTerminals().entrySet().stream().map(e -> e.getValue()).forEach( unprocessedFerryTerminal ->{
      TransferZoneType ptv1TransferZoneType =
          PlanitTransferZoneUtils.extractTransferZoneTypeFromPtv1Tags(
              unprocessedFerryTerminal, OsmModelUtil.getTagsAsMap(unprocessedFerryTerminal));
      processStandAloneFerryStop(unprocessedFerryTerminal, ptv1TransferZoneType);
      getProfiler().incrementOsmPtv1TagCounter(OsmTags.FERRY_TERMINAL);

    });


    osmData.removeAllUnprocessedPtv1FerryTerminals();

  }

  /** Process the stop_position represented by the provided osm node that is nto part of any stop_area and therefore has not been matched
   * to any platform/pole yet, i.e., transfer zone. It is our task to do that now (if possible).
   *
   * @param osmNode to process as stop_position if possible
   * @param tags of the node
   */
  private void processStopPositionNotPartOfStopArea(OsmNode osmNode, Map<String, String> tags) {
    getZoningReaderData().getOsmData().removeUnprocessedStopPosition(osmNode.getId());
        
    /* modes for stop_position */
    String defaultOsmMode = OsmModeUtils.identifyPtv1DefaultMode(osmNode.getId(), tags, true);
    Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode, tags, defaultOsmMode);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      /* no eligible modes mapped to planit mode, ignore stop_position */
      return;
    }
    
    Collection<String> eligibleOsmModes = modeResult.first();
    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    for(String osmMode: eligibleOsmModes) {
      PredefinedModeType accessModeType = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModeType(osmMode);
      MacroscopicNetworkLayer networkLayer = getReferenceNetwork().getLayerByPredefinedModeType(accessModeType);

      /* special cases */
      {
        /*PTv2 ferry stop tagged as public_transport=stop_position with ferry=yes (that may or may not be tagged as amenity=ferry_terminal) AND
         *  therefore, is not just the stop position, but also the platform/station/transfer zone, so we should extract */
        if (this.getNetworkToZoningData().getNetworkSettings().isWaterwayParserActive() && OsmWaterModeTags.isWaterModeTag(osmMode)) {
          processStandAloneFerryStop(osmNode, TransferZoneType.PLATFORM); // treat as platform
          continue;
        }
      }

      /* a regular stop position should be part of an already parsed PLANit link, if not it is incorrectly tagged */
      if(!getNetworkToZoningData().getNetworkLayerData(networkLayer).isLocationPresentInLayer(osmNodeLocation)) {
        LOGGER.fine(String.format("DISCARD: stop_location %d is not part of any parsed link in the network, likely incorrectly tagged", osmNode.getId()));
        continue;
      }
  
      /* locate transfer zone(s) for stop_location/mode combination */
      var singletonSet = new TreeSet<String>();
      singletonSet.add(osmMode);
      Collection<TransferZone> matchedTransferZones =
          getTransferZoneHelper().findTransferZonesForStopPosition(osmNode, tags, singletonSet, false);
      boolean suppressLogging = getSettings().isOverwriteWaitingAreaOfStopPosition(osmNode.getId());

      if(!suppressLogging && (matchedTransferZones == null || matchedTransferZones.isEmpty())) {
        logWarningIfNotNearBoundingBox(String.format(
            "DISCARD: stop_position %d has no valid pole, platform, station reference, nor close-by infrastructure that qualifies as such for mode %s (tags %s)",osmNode.getId(), osmMode, tags),osmNodeLocation);
        return;
      }
      
      /* create connectoid(s) for stop_location/transfer zone/mode combination */
      boolean locationIsKnownOsmStopPosition = true;
      for(TransferZone transferZone : matchedTransferZones) {
        getConnectoidHelper().extractDirectedConnectoidsForMode(
            osmNode, locationIsKnownOsmStopPosition, transferZone, accessModeType, suppressLogging, getGeoUtils());
      }           
    }
  }

  /**
   * Process any remaining unprocessed stop_positions that are not part of any stop_area. This means the stop_position has not yet been matched
   * to any platform/pole, i.e., transfer zone. It is our task to do that now (if possible).
   *  
   */
  private void processStopPositionsNotPartOfStopArea() {
    var unprocessedStopPositions = new TreeMap<>(getZoningReaderData().getOsmData().getUnprocessedStopPositions());
    if(unprocessedStopPositions.isEmpty()) {
      return;
    }


    unprocessedStopPositions.entrySet().stream().map(e -> e.getValue()).forEach( osmNode -> {

      if(osmNode == null){
        LOGGER.severe(String.format("OSM node %d representing stop position not available in memory, unable to extract stop position", osmNode.getId()));
        return;
      }
      processStopPositionNotPartOfStopArea(osmNode, OsmModelUtil.getTagsAsMap(osmNode));

    });
  }

  /**
   * process a remaining transfer zones without any connectoids that is not part of any stop_area. This means that it has no stop_position or the stop_position 
   * has not yet been matched to any platform/pole, i.e., transfer zone. It is our task to do that now (if possible).
   * 
   * @param transferZone remaining unprocessed transfer zone (without connectoids)
   */  
  private void processIncompleteTransferZone(TransferZone transferZone) {

    if(transferZone.getExternalId().equals("2819919872")){
      int bla = 4;
    }

    EntityType osmEntityType = PlanitTransferZoneUtils.transferZoneGeometryToOsmEntityType(transferZone.getGeometry());
    long osmEntityId = Long.valueOf(transferZone.getExternalId());     
        
    /* validate mode support */
    Collection<String> accessOsmModes = 
        OsmModeUtils.extractPublicTransportModesFrom(PlanitTransferZoneUtils.getRegisteredOsmModesForTransferZone(transferZone));
    if(!getNetworkToZoningData().getNetworkSettings().hasAnyMappedPlanitModeType(accessOsmModes)) {
      LOGGER.warning(String.format("DISCARD: Waiting area (OSM id %d) has no supported public transport planit modes present", osmEntityId));
      return;             
    }
        
    /* validate location is separated from infrastructure */
    boolean transferZoneOnInfrastructure = osmEntityType.equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmEntityId);
    if(transferZoneOnInfrastructure) {
      /* all waiting areas that are on road/rail/ferry infrastructure should immediately have been processed including their connectoid creation, so if we still find one here
       * something went wrong that is worth logging */
      LOGGER.severe(String.format("DISCARD: Waiting area (OSM id %d) on top of road/rail/waterway infrastructure did not yet receive connectoids (stop location), this shouldn't happen, verify correctness", osmEntityId));
      return;
    }     
    
    /* per mode - find links, because if the transfer zone supports both a rail and road mode, we require different links for our connectoids */
    for(String osmAccessMode : accessOsmModes) {
      var accessModeType = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModeType(osmAccessMode);
      MacroscopicNetworkLayer networkLayer = getReferenceNetwork().getLayerByPredefinedModeType(accessModeType);

      MacroscopicLink selectedAccessLink = null;
      if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmEntityId, osmEntityType)) {
        
        /* user override for what link to use for connectoids */        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmEntityId, osmEntityType);
        selectedAccessLink = PlanitLinkOsmUtils.getClosestLinkWithOsmWayIdToGeometry( osmWayId, transferZone.getGeometry(), networkLayer, getGeoUtils());
        if(selectedAccessLink == null) {
          LOGGER.warning(String.format("DISCARD: User nominated OSM way %d not available for waiting area %s",osmWayId, transferZone.getExternalId()));
          return;
        }
        
      }else if(OsmWaterModeTags.isWaterModeTag(osmAccessMode)){

        /* water based mode, i.e. ferry is special case since dangling platforms without stop positions that are not on any infrastructure are likely
        unused. Also, we do not yet support parsing it as a stop and create connection to nearest ferry route since the OSM node(s) of the platform are not available anymore
         this would require some thinking on how to treat this (can't be done during main processing because it might have a stop location found later etc.)*/
        LOGGER.warning(String.format("DISCARD: Ptv2 platform (%d) for water mode %s without stop_position/ferry_terminal and disconnected from waterway network, ignore", osmEntityId, osmAccessMode));
        continue;
      }else {
        
        /* regular approach */                     
        Envelope searchBoundingBox = getGeoUtils().createBoundingBox(transferZone.getEnvelope(), getSettings().getStopToWaitingAreaSearchRadiusMeters());
        
        /* collect spatially, mode, compatible links */
        Collection<MacroscopicLink> modeSpatiallyCompatibleLinks = findModeBBoxVerticalLayerIdxCompatibleLinksForTransferZone(
            transferZone, osmEntityId, osmAccessMode, searchBoundingBox);
        if(modeSpatiallyCompatibleLinks == null || modeSpatiallyCompatibleLinks.isEmpty()) {
          logWarningIfNotNearBoundingBox(String.format("DISCARD: No accessible links (max distance %.2fm) for waiting area %s (mode: %s), tagging error or consider activating more road types)", getSettings().getStopToWaitingAreaSearchRadiusMeters(), transferZone.getExternalId(), osmAccessMode), transferZone.getGeometry());
          continue;
        }
        
        /* based on candidates, now select the most appropriate option based on a multitude of criteria */
        var accessResult = findMostAppropriateStopLocationLinkForWaitingArea(transferZone, osmAccessMode, modeSpatiallyCompatibleLinks);
        selectedAccessLink = accessResult==null ? null : accessResult.first();
      }
       
      /* create connectoids */    
      if(selectedAccessLink != null) {
        getConnectoidHelper().extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(
            Long.parseLong(transferZone.getExternalId()),
            transferZone.getGeometry(),
            selectedAccessLink,
            transferZone,
            accessModeType,
            getSettings().getStopToWaitingAreaSearchRadiusMeters(),
            networkLayer,
            false);
      }
    }
    
  }

  /**
   * process remaining transfer zones without any connectoids yet that are not part of any stop_area. This means that it has no stop_position or the stop_position 
   * has not yet been matched to any platform/pole, i.e., transferzone. It is our task to do that now (if possible).
   * 
   * @param transferZones remaining unprocessed transfer zones (without connectoids)
   *
   */
  private void processIncompleteTransferZones(SortedSet<TransferZone> transferZones) {
    Set<TransferZone> unprocessedTransferZones = new TreeSet<>(transferZones);
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
   */
  private void processIncompleteTransferZones() {
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
   */
  private void extractStandAloneStationConnectoids(
      OsmEntity osmStation, Map<String, String> tags, TransferZone stationTransferZone, SortedSet<String> osmAccessModes, PlanitJtsCrsUtils geoUtils){
    EntityType osmStationEntityType = Osm4JUtils.getEntityType(osmStation);
        
    /* modes */
    for(String osmAccessMode : osmAccessModes) {
      var accessModeType = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModeType(osmAccessMode);
      MacroscopicNetworkLayer networkLayer = getReferenceNetwork().getLayerByPredefinedModeType(accessModeType);
      
      /* station mode determines where to create stop_locations and how many. It is special in this regard and different from a regular transfer zone (platform/pole) */
      Pair<Double, Integer> result = determineSearchDistanceAndMaxStopLocationMatchesForStandAloneStation(osmStation.getId(), osmAccessMode, getSettings());
      if(result == null) {
        LOGGER.warning(String.format("DISCARD: unable to process stand-alone station %d supported mode %s, skip", osmStation.getId(),osmAccessMode));
        continue;
      }
      double maxSearchDistance =result.first();
      int maxStopLocations = result.second(); 
      
      /* links per mode*/
      TreeSet<MacroscopicLink> accessLinks = null;
      if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmStation.getId(), osmStationEntityType)) {
        
        /* user override for what link to use for connectoids */        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmStation.getId(), osmStationEntityType);
        MacroscopicLink nominatedLink = PlanitLinkOsmUtils.getClosestLinkWithOsmWayIdToGeometry(osmWayId, stationTransferZone.getGeometry(), networkLayer, geoUtils);
        if(nominatedLink != null) {
          accessLinks = new TreeSet<>();
          accessLinks.add(nominatedLink);
        }else {
          LOGGER.severe(String.format("User nominated OSM way not available for station %d",osmWayId));
        }                    
        
      }else {      
      
        /* regular approach */
        /* accessible links for station conditioned on found modes, proximity, relative location to transfer zone and importance of osm way type (if applicable) */
        Envelope searchBoundingBox = OsmBoundingAreaUtils.createBoundingBoxForOsmWay(
                osmStation, maxSearchDistance , getZoningReaderData().getOsmData().getOsmNodeData().getRegisteredOsmNodes(), geoUtils);
        accessLinks = findStopLocationLinksForStation(
            osmStation, stationTransferZone, osmAccessMode, searchBoundingBox, maxStopLocations);
      }
      
      if(accessLinks == null) {
        logWarningIfNotNearBoundingBox(
            String.format("DISCARD: Station %d without eligible access links for pt vehicles as stop locations (tags %s)",osmStation.getId(),  tags),stationTransferZone.getGeometry());
        return;
      }                   
      
      /* connectoids per mode per link */
      for(var accessLink : accessLinks) {
      
        /* identify locations based on links and spatial restrictions, because no stop_location is known in osm, nearest osm node might be to
         * far away and we must break the link and insert a planit node without an osm node counterpart present, this is what the below method does */
       getConnectoidHelper(). extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(
            osmStation.getId(),
           stationTransferZone.getGeometry() ,
           accessLink,
           stationTransferZone,
           accessModeType,
           getSettings().getStationToWaitingAreaSearchRadiusMeters(),
           networkLayer,
           false);
      }
    }
  }


  /** Once a station is identified as stand-alone during processing, i.e., no platforms, poles nearby, we must create the appropriate
   * transfer zones (without connectoids). Then afterwards we create the connectoids as well
   * 
   * @param osmStation to extract from
   * @param tags of the station
   * @param geoUtils to use
   */
  private void extractStandAloneStation(OsmEntity osmStation, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) {
        
    /* modes */
    String defaultMode = OsmModeUtils.identifyPtv1DefaultMode(osmStation.getId(), tags, OsmRailModeTags.TRAIN);
    Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmStation, tags, defaultMode);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      return;
    }
    SortedSet<String> osmAccessModes = modeResult.first();
        
    TransferZone stationTransferZone = null;
    EntityType osmStationEntityType = Osm4JUtils.getEntityType(osmStation);
    boolean stationOnTrack = EntityType.Node.equals(osmStationEntityType) && hasNetworkLayersWithActiveOsmNode(osmStation.getId());
    if(stationOnTrack && !getSettings().isOverwriteWaitingAreaOfStopPosition(osmStation.getId())) {
      /* transfer zone + connectoids */
            
      /* station is stop_location as well as transfer zone, create both transfer zone and connectoids based on this location */      
      OsmNode osmStationNode = getNetworkToZoningData().getNetworkOsmNodes().get(osmStation.getId());
      if(osmStationNode == null){
        LOGGER.severe(String.format("DISCARD: Station node (%d) expected to be also a stop location, yet OSM node not present on underlying network",osmStation.getId()));
      }
      TransferZoneType ptv1TransferZoneType = PlanitTransferZoneUtils.extractTransferZoneTypeFromPtv1Tags(osmStationNode, tags);
      getTransferZoneHelper().createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmStationNode, tags, defaultMode, ptv1TransferZoneType, geoUtils);      
      
    }else{
      /* either station is not on track, or it is, but a different transfer zone is user mandated, either way, we must obtain the transfer zone
       * separately from the connectoids */        
            
      /* transfer zone*/
      {        
        if(getSettings().isOverwriteWaitingAreaOfStopPosition(osmStation.getId())) {
          
          /* transfer zone to use is user replaced, so immediately adopt this transfer zone  */
          Pair<EntityType, Long> result = getSettings().getOverwrittenWaitingAreaOfStopPosition(osmStation.getId());
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
      if(getSettings().isWaitingAreaOfStopLocationOverwritten(osmStationEntityType, osmStation.getId())){
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
    
    /* unprocessed stations -> create transfer zone and connectoids (implicit stop_positions).
     * - Only create connectoids when station is on track/road, 
     * - otherwise create incomplete transfer zone to be dealt with in processing incomplete transfer zones */
    processStationsNotPartOfStopArea();

    /* Unproccessed ferry terminal -> create transfer zone and connectoids (implicit stop_positions).
     * Only create connectoids when station is on track/road, otherwise flag an error since ferry terminals should always be on a route (way)
     * todo: if it turns out there exist many ferry terminals without being attached to a ferry supporting way, we can reconsider and apply the station
     *  based approach instead
     */
    processPtv1FerryTerminalsNotPartOfStopArea();

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
   * @param suppressLogging when true suppress logging
   */
  private void extractKnownPtv2StopAreaStopPosition(
      OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup, boolean suppressLogging){
          
    /* supported modes */
    Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode, tags, null);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      return;
    }

    /* find the transfer zones this stop position is eligible for */
    Collection<TransferZone> matchedTransferZones = getTransferZoneHelper().findTransferZonesForStopPosition(osmNode, tags, modeResult.first(), transferZoneGroup, suppressLogging);
    suppressLogging = suppressLogging || getSettings().isOverwriteWaitingAreaOfStopPosition(osmNode.getId());
          
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* still no match, issue warning */
      if(!suppressLogging) {
        logWarningIfNotNearBoundingBox(
            String.format("DISCARD: Stop position %d in stop_area %s has no valid pole, platform, station reference, wharf, nor close-by infrastructure that qualifies (tags: %s)",
                osmNode.getId(), transferZoneGroup.getExternalId(), tags.toString()), OsmNodeUtils.createPoint(osmNode));
      }
      return;
    }             
    
    /* connectoids */
    boolean locationIsKnownOsmStopPosition = true;
    getConnectoidHelper().extractDirectedConnectoids(
        osmNode, locationIsKnownOsmStopPosition, matchedTransferZones, modeResult.second(), transferZoneGroup, suppressLogging);
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation but not yet identified in the regular phase of parsing as a stop_position. Hence, it is not properly
   * tagged. In this method we try to salvage it by inferring its properties (eligible modes, vertical plane). We do so, by mapping it to the nearest compatible transfer zone
   * available on the stop_area (if any) as a last resort attempt
   * 
   * @param osmNode node of unknown stop position
   * @param tags of the node
   * @param transferZoneGroup the group this stop position is part of
   * @param suppressLogging when true suppress logging
   */
  private void extractUnknownPtv2StopAreaStopPosition(
      OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup, boolean suppressLogging){

    if(osmNode.getId() == 1281064369L){
      int bla = 4;
    }

    /* not a proper stop_position, so we must infer its properties (eligible modes, transfer zone), eligible OSM modes may be null, but may be present */
    var eligibleModes = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode, tags, null);
    var eligibleOsmModes = eligibleModes!= null ? eligibleModes.first() : null;
    Collection<TransferZone> matchedTransferZones =
        getTransferZoneHelper().findTransferZonesForStopPosition(osmNode, tags, eligibleOsmModes, transferZoneGroup, suppressLogging);
    suppressLogging = suppressLogging || getSettings().isOverwriteWaitingAreaOfStopPosition(osmNode.getId());
            
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      
      /* log warning unless it relates to stop_position without any activate modes and/or near bounding box */
      if(!suppressLogging && OsmModeUtils.hasMappedPlanitMode(
          getPtModeHelper().collectPublicTransportModesFromPtEntity(
              osmNode, tags, OsmModeUtils.identifyPtv1DefaultMode(osmNode.getId(), tags, true)))) {
        logWarningIfNotNearBoundingBox(
            String.format("DISCARD: Stop_position %d without proper tagging on OSM network could not be mapped to close-by transfer zone in stop_area (tags: %s)", osmNode.getId(), tags.toString()), OsmNodeUtils.createPoint(osmNode));
      }      
      return;
    }else if(!suppressLogging && matchedTransferZones.size()>1){
      LOGGER.severe(String.format("Identified more than one spatially closest transfer zone for stop_position %d that was not tagged as such in stop_area %s, this should not happen",osmNode.getId(), transferZoneGroup.getExternalId()));
    }
    
    TransferZone foundZone = matchedTransferZones.iterator().next();  
    var accessModeTypes = getNetworkToZoningData().getNetworkSettings().getActivatedPlanitModeTypes(PlanitTransferZoneUtils.getRegisteredOsmModesForTransferZone(foundZone));
    if(accessModeTypes == null) {
      if(!suppressLogging) LOGGER.warning(String.format(
          "DISCARD: Stop_position %d without proper tagging on OSM network, unable to identify access modes from closest transfer zone in stop_area (tags: %s)", osmNode.getId(), tags.toString()));
      return;             
    }
             
    /* connectoids */
    boolean locationIsKnownOsmStopPosition = false;
    boolean success = getConnectoidHelper().extractDirectedConnectoids(
        osmNode, locationIsKnownOsmStopPosition, Collections.singleton(foundZone), accessModeTypes, transferZoneGroup, suppressLogging);
    if(!suppressLogging && success){
      LOGGER.info(String.format("SALVAGED: Stop_position %d in stop_area not marked as such on OSM node, mapped to most likely transfer zone (%s) in stop_area instead, verify correctness (tags %s)",osmNode.getId(), foundZone.getIdsAsString(), tags.toString()));
    }

  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param member member in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @param suppressLogging when true suppress logging
   */
  private void extractPtv2StopAreaStopPosition(
      final OsmRelationMember member, final TransferZoneGroup transferZoneGroup, boolean suppressLogging){
    PlanItRunTimeException.throwIfNull(member, "Stop_area stop role member null");
    var osmData = getZoningReaderData().getOsmData();
    
    /* only proceed when not marked as invalid earlier */
    if(osmData.isIgnoreStopAreaStopPosition(member.getType(), member.getId())) {
      return;
    }          
    
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_POSITION);
    
    /* validate state and input */
    if(member.getType() != EntityType.Node) {
      if(!suppressLogging) {
        LOGGER.severe(String.format("DISCARD: Stop_position %d encountered that it not an OSM node, this is not permitted", member.getId()));
      }
      return;
    }      

    OsmNode stopPositionNode = getNetworkToZoningData().getNetworkOsmNodes().get(member.getId());
    if(stopPositionNode==null) {
      /* likely missing because it falls outside bounding box, ignore */
      if(!getSettings().hasBoundingPolygon() && !suppressLogging) {
        LOGGER.warning(String.format("DISCARD: Unable to extract ptv2 stop position %d in OSM relation (stop area) %s, OSM node missing", member.getId(), transferZoneGroup.getExternalId()));
      }
      return;
    }

    /* regular Ptv2 stop_position or special cases due to tagging errors */
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(stopPositionNode);
    if(osmData.hasUnprocessedStopPosition(member.getId())){

      /* process as regular stop_position */
      extractKnownPtv2StopAreaStopPosition(stopPositionNode, tags, transferZoneGroup, suppressLogging);
      /* mark as processed */
      osmData.removeUnprocessedStopPosition(stopPositionNode.getId());
      return;

    }

    /* special cases */
    if(osmData.hasUnprocessedPtv1FerryTerminal(stopPositionNode.getId())){
      /* special case - a Ptv1 ferry terminal included in Ptv2 stop area where it is not marked as a Ptv2 stop_position (albeit with a stop role).
       * If this is the case, attempt to extract a stop position for it (not a transfer zone due to its assigned role) */

      /* process as regular stop_position as this is the most likely way we think it was intended */
      // TODO -> it might be that for ferry terminals no platform exists and the intention more often was to create both platform
      //         and stop position. If this turns out to be the case then we should allow for an option to create transfer zones in this situation
      extractKnownPtv2StopAreaStopPosition(stopPositionNode, tags, transferZoneGroup, suppressLogging);
      /* mark as processed */
      osmData.removeUnprocessedPtv1FerryTerminal(stopPositionNode.getId());
    }else {

      /* check if connectoids already exist, if not (due to being part of multiple stop_areas or because it is a Ptv1 stop that has already been processed) */
      boolean isPtv2NodeOnly = !OsmPtVersionSchemeUtils.isPtv2StopPositionPtv1Stop(stopPositionNode, tags);
      boolean alreadyProcessed = getZoningReaderData().getPlanitData().hasAnyDirectedConnectoidsForLocation(OsmNodeUtils.createPoint(stopPositionNode));
      if(isPtv2NodeOnly && alreadyProcessed && !suppressLogging) {
        /* stop_position resides in multiple stop_areas, this is strongly discouraged by OSM, but does occur still, so we identify and skip (no need to process twice anyway)*/
        LOGGER.fine(String.format("Stop_position %d present in multiple stop_areas, discouraged tagging behaviour, consider re-tagging",member.getId()));
      }
      if(alreadyProcessed) {
        /* already matched to at least one transfer zone (platform/pole), no need to attempt and create connectoid again. In case it serves more than one transferzone
         * these other transfer zones remain incomplete and will be supplemented with the connectoid at a later stage */
        return;
      }

      /* stop-position not processed and not tagged as stop_position known, so it is not properly tagged and we must infer mode access from infrastructure it resides on to salvage it */
      if(!suppressLogging) {
        LOGGER.fine(String.format("Stop_position %d in stop_area not marked as such on OSM node, inferring transfer zone and access modes by geographically closest transfer zone in stop_area instead ", member.getId()));
      }

      /* unknown, so node is not tagged properly, try to salvage */
      extractUnknownPtv2StopAreaStopPosition(stopPositionNode, tags, transferZoneGroup, suppressLogging);
    }
  }  
  
  /** extract stop area relation of Ptv2 scheme. We create connectoids for all now already present transfer zones.
   * 
   * @param osmRelation to extract stop_area for
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaPostProcessingEntities(OsmRelation osmRelation) throws PlanItException{
  
    /* transfer zone group */
    TransferZoneGroup transferZoneGroup = getZoningReaderData().getPlanitData().getTransferZoneGroupByOsmId(osmRelation.getId());
    if(transferZoneGroup == null) {
      LOGGER.severe(String.format("Found stop_area %d in post-processing for which no PLANit transfer zone group has been created, this should not happen",osmRelation.getId()));
    }
        
    /* process only stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      
      if( skipOsmPtEntity(member)) {
        continue;
      }
                  
      /* stop_position */
      if(member.getRole().equals(OsmPtv2Tags.STOP_ROLE)) {        

        /* suppress any warnings when we identified the member or area should be ignored for generating user warnings */
        boolean suppressLogging =
            getSettings().isSuppressOsmRelationStopAreaLogging(osmRelation.getId()) ||
            getZoningReaderData().getOsmData().isWaitingAreaWithoutMappedPlanitMode(member.getType(), member.getId()) ||
            getZoningReaderData().getOsmData().isIgnoreStopAreaStopPosition(member.getType(), member.getId());

        extractPtv2StopAreaStopPosition(member, transferZoneGroup, suppressLogging);
        
      }

    }    
       
  }  
  

  /**
   * Constructor
   * 
   * @param transferSettings for the handler
   * @param handlerData the handler data gathered by preceding handlers for zoning parsing
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @param referenceNetwork to use
   * @param zoningToPopulate to populate
   * @param profiler to use 
   */
  public OsmZoningPostProcessingHandler(
      final OsmPublicTransportReaderSettings transferSettings,
      final OsmZoningReaderData handlerData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      final PlanitOsmNetwork referenceNetwork,
      final Zoning zoningToPopulate,
      final OsmZoningHandlerProfiler profiler) {
    super(transferSettings, handlerData, network2ZoningData, referenceNetwork, zoningToPopulate, profiler);
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   *
   */
  public void initialiseBeforeParsing(){
    reset();
    
    PlanItRunTimeException.throwIf(
        getReferenceNetwork().getTransportLayers() == null || getReferenceNetwork().getTransportLayers().size()<=0,
          "Network is expected to be populated at start of parsing OSM zoning");
    
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
                        
            extractPtv2StopAreaPostProcessingEntities(osmRelation);
            
          }else {
            /* anything else is not expected */
            LOGGER.fine(String.format("DISCARD: Unsupported public_transport relation %s referenced by relation %d",
                tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));
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

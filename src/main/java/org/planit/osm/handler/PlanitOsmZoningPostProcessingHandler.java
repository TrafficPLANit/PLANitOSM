package org.planit.osm.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.osm.converter.reader.PlanitOsmNetworkReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkReaderLayerData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderOsmData;
import org.planit.osm.settings.zoning.PlanitOsmPublicTransportSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.linearref.LinearLocation;
import org.planit.converter.network.NetworkReader;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitGraphGeoUtils;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.graph.Vertex;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;
import org.planit.utils.misc.StringUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.TrackModeType;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

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
public class PlanitOsmZoningPostProcessingHandler extends PlanitOsmZoningBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningPostProcessingHandler.class.getCanonicalName());
    
  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils; 
    
  /** to be able to find osm nodes internal to parsed planit links that we want to break to for example create stop locations for stand alone station, 
   * we must be able to spatially find those nodes spatially because they are not referenced by the station or planit link eplicitly, this is what we do here. 
   * It is not placed in the zoning data as it is only utilised in post-processing */
  private Map<MacroscopicPhysicalNetwork, Quadtree> spatiallyIndexedOsmNodesInternalToPlanitLinks = null;
    
  
  /**
   * created spatially indexed osm nodes internal to existing planit links container
   */
  private void initialiseSpatiallyIndexedOsmNodesInternalToPlanitLinks() {
    
    double envelopeMinExtentAbsolute = Double.POSITIVE_INFINITY;
    for(MacroscopicPhysicalNetwork layer : getNetworkToZoningData().getOsmNetwork().infrastructureLayers) {
      PlanitOsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(layer);
      spatiallyIndexedOsmNodesInternalToPlanitLinks.put(layer, new Quadtree());
      Quadtree spatialcontainer = spatiallyIndexedOsmNodesInternalToPlanitLinks.get(layer);            
            
      Set<Point> registeredInternalLinkLocations = layerData.getRegisteredLocationsInternalToAnyPlanitLink();
      for( Point location: registeredInternalLinkLocations) {
        OsmNode osmNodeAtLocation = layerData.getOsmNodeInternalToLinkByLocation(location);
        Envelope pointEnvelope = new Envelope(location.getCoordinate());
        geoUtils.createBoundingBox(pointEnvelope, 5); // buffer around bounding box of point to avoid issues with JTS quadtree minimumExtent anomalies
        /* pad envelope with minium extent computed */
        spatialcontainer.insert(Quadtree.ensureExtent(pointEnvelope, envelopeMinExtentAbsolute), osmNodeAtLocation);
      }
    }
  } 
  
    
  /** find all links with at least one compatible mode (and planit mode mapped) based on the passed in reference osm modes
   * In case no eligible modes are provided (null), we allow any transfer zone with at least one valid mapped mode
   *  
   * @param referenceOsmModes to map agains (may be null)
   * @param potentialLinks to extract mode compatible links from
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water mode, when false only exact matches are allowed
   * @return matched transfer zones
   */   
  private Collection<Link> findModeCompatibleLinks(Collection<String> referenceOsmModes, Collection<Link> potentialLinks, boolean allowPseudoModeMatches) {
    Set<Link> modeCompatibleLinks = new HashSet<Link>();
    for(Link link : potentialLinks) {
      if(isLinkModeCompatible(link, referenceOsmModes, allowPseudoModeMatches)) {
        modeCompatibleLinks.add(link);
      }
    }    
    return modeCompatibleLinks;
  }

  /** for the passed in links collection determine the subset that is compatible with the driving direction information on the link given the
   * placement of the waiting area and the mode of transport, e.g. a bus waiting area must reside on the side of the road compatible with the driving
   * direction since a bus only has doors on one side. If it is on the wrong side, it is removed from the link collection that is returned.
   * 
   * @param waitingAreaGeometry the links appear accessible from/to
   * @param eligibleOsmModes of the waiting area
   * @param linksToVerify the link sto verify
   * @return links compatible with the driving direction
   * @throws PlanItException thrown if error
   */
  private Collection<Link> findDrivingDirectionCompatibleLinks(Geometry waitingAreaGeometry, Collection<String> eligibleOsmModes, Collection<Link> linksToVerify) throws PlanItException {
    boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(getZoningReaderData().getCountryName());    
    Collection<Mode> accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(PlanitOsmModeUtils.getPublicTransportModesFrom(eligibleOsmModes));
    return PlanitOsmHandlerHelper.removeLinksOnWrongSideOf(waitingAreaGeometry, linksToVerify, isLeftHandDrive, accessModes, geoUtils);
  
  }

  /** Find all links that are within the given search bounding box and are mode compatible with the given reference modes. Also make sure the waiting area location is 
   * compatible with the driving direction of the supported modes, e.g. for bus it must be situated on the same side as the countries driving direction (left for left-hand drive),
   * otherwise it has no doors for passengers to enter in this location). 
   * 
   * @param osmEntityId the osm id of the waiting area
   * @param waitingAreaGeometry to collect accessible links for 
   * @param eligibleOsmModes modes supported by the waiting area
   * @param searchBoundingBox to use
   * @return all links that are deemed accessible for this waiting area
   * @throws PlanItException thrown if error
   */
  private Collection<Link> findStopLocationLinksForWaitingArea(Long osmEntityId, Geometry waitingAreaGeometry, Collection<String> eligibleOsmModes, Envelope searchBoundingBox) throws PlanItException {
    
    if(osmEntityId == 1546620260) {
      int bla = 4;
    }
    
    
    /* match links spatially */
    Collection<Link> spatiallyMatchedLinks = getZoningReaderData().getPlanitData().findLinksSpatially(searchBoundingBox);    
    if(spatiallyMatchedLinks == null || spatiallyMatchedLinks.isEmpty()) {
      /* only issue warning when lack of matching links is likely not caused by witing area being on the boundary of the network */
      if(!geoUtils.isGeometryNearBoundingBox(waitingAreaGeometry, getNetworkToZoningData().getNetworkBoundingBox(), PlanitOsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS)) {
        LOGGER.warning(String.format("Waiting area (osm id %d) has no nearby infrastructure that qualifies for pt modes %s as stop locations", osmEntityId, PlanitOsmModeUtils.getPublicTransportModesFrom(eligibleOsmModes).toString())); 
      }
      return null;
    }    
  
    /* filter based on mode compatibility */
    Collection<Link> modeAndSpatiallyCompatibleLinks = findModeCompatibleLinks(eligibleOsmModes, spatiallyMatchedLinks, false /*only exact matches allowed */);    
    if(modeAndSpatiallyCompatibleLinks == null || modeAndSpatiallyCompatibleLinks.isEmpty()) {
      LOGGER.warning(String.format("Waiting area (osm id) %d has no compatible modes at nearby infrastructure that qualifies for pt modes %s as stop locations",
          osmEntityId, PlanitOsmModeUtils.getPublicTransportModesFrom(eligibleOsmModes).toString()));
      return null;
    }
    
    /* filter based on driving direction, country, mode */
    Collection<Link> directionAndModeAndSpatiallyCompatibleLinks = findDrivingDirectionCompatibleLinks(waitingAreaGeometry, eligibleOsmModes, modeAndSpatiallyCompatibleLinks);
    if(directionAndModeAndSpatiallyCompatibleLinks == null || directionAndModeAndSpatiallyCompatibleLinks.isEmpty()) {
      LOGGER.warning(String.format("Waiting area (osm id) %d for modes %s is situated on the wrong side of all found eligible roads %s to qualify for pt modes as stop locations",
          osmEntityId,PlanitOsmModeUtils.getPublicTransportModesFrom(eligibleOsmModes).toString(),modeAndSpatiallyCompatibleLinks.stream().map( (link) -> { return link.getExternalId();}).collect(Collectors.toList()).toString()));
    }
    return directionAndModeAndSpatiallyCompatibleLinks;
  }

  /** find links that are within the given search bounding box and are mode compatible with the given reference modes. If more links are found
   * than maxMatches, reduce the results to the closest maxMatches. We also make sure that in case multiple matches are allowed we only select
   * multiple links if they represent parallel train lines.
   * 
   * @param osmStation to find mode accessible links for
   * @param tags of the station 
   * @param referenceOsmModes station modes available and should be supported by the links
   * @param searchBoundingBox search area to identify links spatially
   * @param maxMatches number of matches at most that is allowed (typically only higher than 1 for train stations)
   * @return found links most likely to be accessible by the station
   * @throws PlanItException thrown if error
   */
  private Collection<Link> findStopLocationLinksForStation(
      OsmEntity osmStation, Map<String, String> tags, Collection<String> referenceOsmModes, Envelope searchBoundingBox, Integer maxMatches) throws PlanItException {
    
    /* First find all links that are spatially, mode, and driving direction compatible (switch of logging of issues regarding station geometry since these would already be logged when creating transfer zone)*/
    Geometry stationGeometry = PlanitOsmUtils.extractGeometry(osmStation, getNetworkToZoningData().getOsmNodes(), Level.OFF);
    
    Collection<Link> directionModeSpatiallyCompatibleLinks = findStopLocationLinksForWaitingArea(osmStation.getId(), stationGeometry, referenceOsmModes, searchBoundingBox);
    if(directionModeSpatiallyCompatibleLinks==null || directionModeSpatiallyCompatibleLinks.isEmpty()) {
      return null;
    }
    
    /* #matches compatibility */
    Collection<Link> chosenLinksForStopLocations = null;    
    {
      Link closestLinkForStopLocation = (Link)PlanitOsmUtils.findEdgeClosest(osmStation, directionModeSpatiallyCompatibleLinks, getNetworkToZoningData().getOsmNodes(), geoUtils);
      if(closestLinkForStopLocation==null) {
        throw new PlanItException("No closest link could be found from selection of eligible closeby links when finding stop locations for station %s, this should not happen", osmStation.getId());
      }
      
      if(maxMatches==1) {
        
        /* road based station would require single match to link, e.g. bus station */
        chosenLinksForStopLocations = Collections.singleton(closestLinkForStopLocation);
        
      }else if(maxMatches>1) {

        /* multiple matches allowed, indicating we are searching for parallel platforms -> use closest match to establish a virtual line from station to this link's closest intersection point
         * and use it to identify other links that intersect with this virtual line, these are our parallel platforms */
        chosenLinksForStopLocations = new HashSet<Link>();      
        LineSegment stationToClosestPointOnClosestLinkSegment = null;
       
        /* create virtual line passing through station and closest link's geometry, extended to eligible distance */          
        if(Osm4JUtils.getEntityType(osmStation) == EntityType.Node) {
          Coordinate closestCoordinate = PlanitOsmNodeUtils.findClosestProjectedCoordinateTo((OsmNode)osmStation, closestLinkForStopLocation.getGeometry(), geoUtils);
          Point osmStationLocation = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.createCoordinate((OsmNode)osmStation));
          stationToClosestPointOnClosestLinkSegment = PlanitJtsUtils.createLineSegment(osmStationLocation.getCoordinate(),closestCoordinate);
        }else if(Osm4JUtils.getEntityType(osmStation) == EntityType.Way) {
          stationToClosestPointOnClosestLinkSegment = PlanitOsmWayUtils.findMinimumLineSegmentBetween((OsmWay)osmStation, closestLinkForStopLocation.getGeometry(), getNetworkToZoningData().getOsmNodes(), geoUtils);
        }else {
          throw new PlanItException("unknown entity type %s for osm station encountered, this should not happen", Osm4JUtils.getEntityType(osmStation).toString());
        }      
        LineSegment interSectionLineSegment = geoUtils.createExtendedLineSegment(stationToClosestPointOnClosestLinkSegment, getSettings().getStationToParallelTracksSearchRadiusMeters(), true, true);
        Geometry virtualInterSectionGeometryForParallelTracks = PlanitJtsUtils.createLineString(interSectionLineSegment.getCoordinate(0),interSectionLineSegment.getCoordinate(1));
        
        /* find all links of compatible modes that intersect with virtual line reflecting parallel accessible (train) tracks eligible to create a platform for */
        for(Link link : directionModeSpatiallyCompatibleLinks) {
          if(link.getGeometry().intersects(virtualInterSectionGeometryForParallelTracks)) {
            chosenLinksForStopLocations.add(link);
          }
        }
      }else if(maxMatches<1) {
        LOGGER.severe(String.format("Invalid number of maximum matches %d provided when finding stop location links for station %d",maxMatches, osmStation.getId()));
        return null;
      }        
    }  
    
    if(chosenLinksForStopLocations== null || chosenLinksForStopLocations.isEmpty()) {
      /* cannot happen because at least the closestLinkForStopLocation we know exists should be found here */
      throw new PlanItException("No links could be identified from virtual line connecting station to closest by point on closest link for osm station %s, this should not happen", osmStation.getId());
    }
    
    return chosenLinksForStopLocations;
  }  
  
  /** find links for the given stand-alone osm station. Depending on whether this is a rail od road station different configurations are created
   * to search for accessible links
   * 
   * @param osmStation to find mode accessible links for
   * @param tags of the station
   * @param eligibleStationModes identified for this station 
   * @return found links most likely to be accessible by the station
   * @throws PlanItException 
   */
  private Collection<Link> findStopLocationLinksForStation(
      OsmEntity osmStation, Map<String, String> tags, Collection<String> osmStationModes, double searchDistance, int maxStopLocatinMatches) throws PlanItException {
            
    Envelope searchBoundingBox = PlanitOsmUtils.createBoundingBox(osmStation, searchDistance , getNetworkToZoningData().getOsmNodes(), geoUtils);
    return findStopLocationLinksForStation(osmStation, tags, osmStationModes, searchBoundingBox, maxStopLocatinMatches);    
  }  
      

  /** Verify if any valid access link segments exist for the given combination of link, on of its extreme nodes, and the access mode.
   * 
   * @param transferZone to check
   * @param accessLink nominated
   * @param node that is nominated
   * @param accessMode used
   * @return true when at least one valid access link segment exists, false otherwise
   * @throws PlanItException thrown if error
   */
  private boolean hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(TransferZone transferZone, Link accessLink, Node node, Mode accessMode) throws PlanItException {
    /* road based modes must stop with the waiting area in the driving direction, i.e., must avoid cross traffic, because otherwise they 
     * have no doors at the right side, e.g., travellers have to cross the road to get to the vehicle, which should not happen... */
    Long osmStopLocationId = node.getExternalId()!= null ? Long.valueOf(node.getExternalId()) : null;
    boolean mustAvoidCrossingTraffic = PlanitOsmHandlerHelper.isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(accessMode, transferZone, osmStopLocationId, getSettings());
    
    /* now collect the available access link segments (if any) - switch of logging of issues, since we are only interested in determining if this is feasible, we are not creating anything yet */    
    Collection<EdgeSegment> accessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(transferZone, accessLink, node, accessMode, mustAvoidCrossingTraffic, geoUtils);         
    
    return !accessLinkSegments.isEmpty();
  }


  /**Attempt to find the transfer zones by the use of the passed in tags containing references via key tag:
   * 
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   * <p>
   * In case multiple zones are found with the exact same reference, we select the zone that is closest by. In case multiple zones are found with unique references 
   * (when the reference value contains multiple reference, e.g. 1;2), then we keep all zones, since each one represents the closest by unique reference
   * <p>
   * Further the zone should be mode compatible with the referenceOsmModes. Compatible here implies that there is direct overlap between modes. If not, then we attempt to salvage
   * accounting for likely tagging errors, e.g. a train platform is created for tram, this will be allowed because it often happens and both are of type rail, given their is a reference
   * match this is likely still correct. However, if a rail mode and road mode is found across the two without overlap then we must assume there is an error in the reference used and we do
   * not allow the mapping and inform the user. If the zone lacks any mode information, we also allow the mapping because we rather have too many matches than missing them. 
   * 
   * 
   * @param osmNode referring to zero or more transfer zones via its tags
   * @param tags to search for reference keys in
   * @param availableTransferZones to choose from
   * @param referenceOsmModes the osm modes a transfer zone should ideally contain one overlapping mapped mode to be deemed accessible, if not user is informed
   * @param geoUtils to use in case of multiple matches requiring selection of closest entry spatially 
   * @return found transfer zones that have been parsed before, null if no match is found
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> findClosestTransferZonesByTagReference(
      OsmNode osmNode, 
      Map<String, String> tags, 
      Collection<TransferZone> availableTransferZones, 
      Collection<String> referenceOsmModes, 
      PlanitJtsUtils geoUtils) throws PlanItException {
    
    Map<String, TransferZone> foundTransferZones = null;
    /* ref value, can be a list of multiple values */
    String refValue = OsmTagUtils.getValueForSupportedRefKeys(tags);
    if(refValue != null) {
      String[] transferZoneRefValues = StringUtils.splitByAnythingExceptAlphaNumeric(refValue);
      for(int index=0; index < transferZoneRefValues.length; ++index) {
        boolean multipleMatchesForSameRef = false;
        String localRefValue = transferZoneRefValues[index];
        for(TransferZone transferZone : availableTransferZones) {
          Object refProperty = transferZone.getInputProperty(OsmTags.REF);
          if(refProperty != null && localRefValue.equals(String.class.cast(refProperty))) {
            /* match */
            if(foundTransferZones==null) {
              foundTransferZones = new HashMap<String,TransferZone>();
            }      
                        
            /* inform user of tagging issues in case platform is not fully correctly mode mapped */
            if(PlanitOsmHandlerHelper.getEligibleOsmModesForTransferZone(transferZone)==null) {
              LOGGER.info(String.format("SALVAGED: Platform/pole (%s) referenced by stop_position (%s), matched although platform has no known mode support", transferZone.getExternalId(), osmNode.getId()));              
            }else if(!isTransferZoneModeCompatible(transferZone, referenceOsmModes, true /* allow pseudo matches */)) {
              LOGGER.warning(String.format("DISCARD: Platform/pole (%s) referenced by stop_position (%s), but platform is not (pseudo) mode compatible with stop", transferZone.getExternalId(), osmNode.getId()));
              continue;
            }
                        
            TransferZone prevTransferZone = foundTransferZones.put(localRefValue,transferZone);
            if(prevTransferZone != null) {
              multipleMatchesForSameRef = true;
              /* choose closest of the two spatially */
              TransferZone closestZone = (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, Set.of(prevTransferZone, transferZone), geoUtils);
              foundTransferZones.put(localRefValue,closestZone);
            }
          }
        }
        if(multipleMatchesForSameRef == true ) {
          LOGGER.fine(String.format("SALVAGED: non-unique reference (%s) on stop_position %d, selected spatially closest platform/pole %s", localRefValue, osmNode.getId(),foundTransferZones.get(localRefValue).getExternalId()));         
        }        
      }
    }
    return foundTransferZones!=null ? foundTransferZones.values() : null;
  } 
  
  /**Attempt to find the transfer zones by the use of the passed in name where the transfer zone (representing an osm platform) must have the exact same name to match as well
   * as being at least pseudo mode compatible, i.e., they have modes of the same type road/rail/water.
   * 
   * @param osmId of the entity we are attempting to find a match for
   * @param nameToMatch to check for within eligible transfer zones
   * @param availableTransferZones to choose from
   * @param referenceOsmModes the osm modes a transfer zone should ideally contain one overlapping mapped mode to be deemed accessible, if not user is informed 
   * @return matches transfer zones, null if no match is found
   */  
  private Collection<TransferZone> findTransferZoneMatchByName(long osmId, String nameToMatch, Collection<TransferZone> availableTransferZones, Collection<String> referenceOsmModes) {
    /* when filtering for mode compatibility, allow for pseudo matches since name is already a strong indicator of valid match */
    boolean allowPseudoModeCompatibility = true;
    
    Collection<TransferZone> foundTransferZones = null;
    for(TransferZone transferZone : availableTransferZones) {
      String transferZoneName  = transferZone.getName();
      if(transferZoneName != null && transferZoneName.equals(nameToMatch)) {
        /* match */
        if(foundTransferZones == null) {
          foundTransferZones = new HashSet<TransferZone>();
        }
        foundTransferZones.add(transferZone);            
      }
    }
    
    if(foundTransferZones!=null) {
    
      Collection<TransferZone> nameAndModecompatibleZones = findModeCompatibleTransferZones(referenceOsmModes, foundTransferZones, allowPseudoModeCompatibility);
      if(nameAndModecompatibleZones==null || nameAndModecompatibleZones.isEmpty()) {        
        //nameAndModecompatibleZones = foundTransferZones;
        LOGGER.fine(String.format("Platform/pole(s) (%s) matched by name to stop_position (%s), but none are even pseudo mode compatible with stop", foundTransferZones.stream().map( z -> z.getExternalId()).collect(Collectors.toList()).toString(), osmId));
      }
    }
    
    if(foundTransferZones!=null && foundTransferZones.size()>1) {
      LOGGER.fine(String.format("multiple platform/pole matches found for name %s and access point osm id %d",nameToMatch, osmId));
    }     
  
    return foundTransferZones;
  }  
  
  /** find the transfer zones that are accessible to the stop_position on the given node and given the pool of eligible transfer zones provided.
   * We match based on tag references, names, and or proximity in descending order of precedence.
   * 
   * @param osmNode node representing the stop_position
   * @param tags of the node
   * @param referenceOsmModes the osm modes a transfer zone must at least contain one overlapping mapped mode from to be deemed accessible
   * @param stopAreaTransferzones the transfer zones of the stop_area this stop_position belongs to
   * @throws PlanItException thrown if error
   */   
  private Collection<TransferZone> findAccessibleTransferZonesByReferenceOrName(
      OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> stopAreaTransferZones, Collection<String> referenceOsmModes, PlanitJtsUtils geoUtils) throws PlanItException {
    
    /* first try explicit reference matching to platform, i.e. transfer zone */
    Collection<TransferZone> matchedTransferZones = findClosestTransferZonesByTagReference(osmNode, tags, stopAreaTransferZones, referenceOsmModes, geoUtils);    
    if(matchedTransferZones != null && !matchedTransferZones.isEmpty()) {
      /* explicit references found, these we trust most, so use them immediately as is */
      return matchedTransferZones;
    }
    
    if(tags.containsKey(OsmTags.NAME)) {
      /* now try matching names, this should only result in single match */
      matchedTransferZones = findTransferZoneMatchByName(osmNode.getId(),tags.get(OsmTags.NAME), stopAreaTransferZones, referenceOsmModes);
      if(matchedTransferZones!= null && !matchedTransferZones.isEmpty()) {
        /* possible matches found, but names are not very trustworthy, try to eliminate options if they are too far away, or are in illogical locations */
        
        /* filter based on distance */
        Collection<TransferZone> potentialTransferZones = getZoningReaderData().getPlanitData().getTransferZonesSpatially(
            PlanitOsmNodeUtils.createBoundingBox(osmNode, getSettings().getStopToWaitingAreaSearchRadiusMeters(), geoUtils));
        matchedTransferZones.retainAll(potentialTransferZones);
        
        /* filter based on illogical location; wrong side of the road */        
        matchedTransferZones = removeTransferZonesOnWrongSideOfRoadOfStopLocation(osmNode, matchedTransferZones, referenceOsmModes, geoUtils);                              
      }      
    }
    
    /* if multiple name matched transfer zones remain, select closest as most likely one */
    if(matchedTransferZones!= null) {
      if(matchedTransferZones.size()>1) {
        TransferZone foundTransferZone = (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, matchedTransferZones, geoUtils);        
        matchedTransferZones = Collections.singleton(foundTransferZone);
      }
    }
    
    return matchedTransferZones;
  }    
        
  /** find all transfer zone groups with at least one transfer zone that is mode compatible (and planit mode mapped)  with the passed in osm modes
   * In case no eligible modes are provided (null).
   *  
   * @param referenceOsmModes to map agains (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water mode, when false only exact matches are allowed
   * @return matched transfer zone groups
   */
  private Set<TransferZoneGroup> findModeCompatibleTransferZoneGroups(Collection<String> referenceOsmModes, Collection<TransferZone> potentialTransferZones, boolean allowPseudoModeMatches) {
    /* find potential matched transfer zones based on mode compatibility while tracking group memberships */
    Set<TransferZoneGroup> potentialTransferZoneGroups = new HashSet<TransferZoneGroup>();
    for(TransferZone transferZone : potentialTransferZones) {              
      if(isTransferZoneModeCompatible(transferZone, referenceOsmModes, allowPseudoModeMatches)) {
        
        /* matched to group and/or zones*/        
        Set<TransferZoneGroup> transferZoneGroups = transferZone.getTransferZoneGroups();
        if(transferZoneGroups!=null && !transferZoneGroups.isEmpty()) {
          potentialTransferZoneGroups.addAll(transferZoneGroups);
        }
      }
                
    } 
    return potentialTransferZoneGroups;
  }  
  
  /** find all transfer zones with at least one compatible mode (and planit mode mapped) based on the passed in reference osm modes
   * In case no eligible modes are provided (null), we allow any transfer zone with at least one valid mapped mode
   *  
   * @param referenceOsmModes to map agains (may be null)
   * @param potentialTransferZones to extract mode compatible transfer zones
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water mode, when false only exact matches are allowed
   * @return matched transfer zones
   */  
  private Set<TransferZone> findModeCompatibleTransferZones(Collection<String> eligibleOsmModes, Collection<TransferZone> potentialTransferZones, boolean allowPseudoModeMatches) {
    Set<TransferZone> modeCompatibleTransferZones = new HashSet<TransferZone>();
    for(TransferZone transferZone : potentialTransferZones) {
      if(isTransferZoneModeCompatible(transferZone, eligibleOsmModes, allowPseudoModeMatches)) {
        modeCompatibleTransferZones.add(transferZone);
      }
    }    
    return modeCompatibleTransferZones;
  } 
  
  /** find the closest and/or most likely transfer zone for the given osm node and its tags (with or without a reference
   * for additional information for mapping). Use the search radius from the settings to identify eligible transfer zones and then
   * use information on modes, references and spatial proximity to choose the most likely option. 
   * 
   * @param osmNode representing a stop position
   * @param tags of the node
   * @param referenceOsmModes the osm modes a transfer zone must at least contain one overlapping mapped mode from to be deemed accessible 
   * @param planitModes the stop is compatible with
   * @return most likely transfer zone(s). Multiple matches only in case the node has multiple references to eligible transfer zones tagged
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> findMostLikelyTransferZonesForStopPositionSpatially(OsmNode osmNode, Map<String, String> tags, Collection<String> referenceOsmModes) throws PlanItException {
    TransferZone foundZone = null;
        
    /* collect potential transfer zones based on spatial search*/
    double searchRadiusMeters = getSettings().getStopToWaitingAreaSearchRadiusMeters();    
    Envelope searchArea = PlanitOsmNodeUtils.createBoundingBox(osmNode, searchRadiusMeters, geoUtils);    
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getPlanitData().getTransferZonesSpatially(searchArea);
    
    if(potentialTransferZones==null || potentialTransferZones.isEmpty()) {
      LOGGER.fine(String.format("Unable to locate nearby transfer zone (search radius of %.2f (m)) when mapping stop position for osm node %d",searchRadiusMeters, osmNode.getId()));
      return null;
    }
    
    /* find matches based on reference, name from given potential options... */
    Collection<TransferZone> matchedTransferZones = findAccessibleTransferZonesByReferenceOrName(osmNode, tags, potentialTransferZones, referenceOsmModes, geoUtils);         
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* no explicit reference or name match is found, we collect the closest mode compatible match */
      matchedTransferZones = findModeCompatibleTransferZones(referenceOsmModes, potentialTransferZones, true);      
      foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, matchedTransferZones, geoUtils);
      if(foundZone == null) {
        LOGGER.warning(String.format("Unable to match osm node %d to any existing transfer zone within search radius of %.2f (m) that is mode compatible to %s, ignored",osmNode.getId(),searchRadiusMeters, referenceOsmModes.toString()));
      }else {
        matchedTransferZones = Collections.singleton(foundZone);
      }
    }
        
    return matchedTransferZones;
  }  
  
  /** find the transfer zone(s) for a given stop_position, either the user overwritten mapping, or conditioned on mode,reference/name/spatially, or 
   * just the closest one in absence of eligible modes
   *  
   * @param osmNode representing the stop_location
   * @param tags of the node
   * @param eligibleOsmModes eligible modes for the stop_location, may be null
   * @param transferZoneGroup the node belongs to, may be null
   * @return found transfer zone matches
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> findIncompleteTransferZonesForStopPosition(OsmNode osmNode, Map<String, String> tags, Collection<String> eligibleOsmModes, TransferZoneGroup transferZoneGroup) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = null;
    
    /* transfer zone(s) */
    if(getSettings().isOverwriteStopLocationWaitingArea(osmNode.getId())) {
      
      /* do not search simply use provided waiting area (transfer zone) */
      Pair<EntityType, Long> result = getSettings().getOverwrittenStopLocationWaitingArea(osmNode.getId());
      TransferZone foundZone = getZoningReaderData().getPlanitData().getTransferZoneByOsmId(result.first(), result.second());
      if(foundZone==null) {
        LOGGER.severe(String.format("User overwritten waiting area (platform, pole %d) for osm node %d, not available",result.second(), osmNode.getId()));
      }else {
        matchedTransferZones = Collections.singleton(foundZone);
        LOGGER.fine(String.format("Mapped stop_position %d to overwritten waiting area %d", osmNode.getId(),  result.second()));
      }
      
    }else if(eligibleOsmModes != null && !eligibleOsmModes.isEmpty()){
      /* eligible modes available */
      
      if(transferZoneGroup != null) {
        /* when transfer zone group available, first search among those zones as they are more likely to be matching */
        matchedTransferZones = findAccessibleTransferZonesByReferenceOrName(osmNode, tags, transferZoneGroup.getTransferZones(), eligibleOsmModes, geoUtils);        
      }
    
      if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
        /* no matches found yet, so either no transfer zone group present, or not known within based on reference/name match.
         * Either way we must still try to find the most likely match. We do so geographically and again based on mode, reference, name compatibility */
        matchedTransferZones = findMostLikelyTransferZonesForStopPositionSpatially(osmNode, tags, eligibleOsmModes);
      }      
    }else {
      /* eligible modes unknown, we can therefore we try to salvage by selecting the closest transfer zone and adopt those modes */   
      TransferZone foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, transferZoneGroup.getTransferZones(), getSettings().getStopToWaitingAreaSearchRadiusMeters(), geoUtils);
      if(foundZone!=null) {    
        matchedTransferZones = Collections.singleton(foundZone);
      }
    }
    
    return matchedTransferZones;
  }  
  
  /** Identical to the one with transfer zone group parameter, only here no stop_position is not part of transfer zone group and therefore we only can find matches spatially
   *  
   * @param osmNode representing the stop_location
   * @param tags of the node
   * @param eligibleOsmModes eligible modes for the stop_location, may be null
   * @param transferZoneGroup the node belongs to
   * @return found transfer zone matches
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> findIncompleteTransferZonesForStopPosition(OsmNode osmNode, Map<String, String> tags, Collection<String> eligibleOsmModes) throws PlanItException {
    return findIncompleteTransferZonesForStopPosition(osmNode, tags, eligibleOsmModes, null);
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
    OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(tags);
    getZoningReaderData().getOsmData().removeUnproccessedStation(ptVersion, osmStation);
    
    if(osmStation.getId()==6646032959l) {
      int bla = 4;
    }
            
    /* eligible modes for station, must at least support one or more mapped modes */
    Pair<Collection<String>, Collection<Mode>> modeResult = collectPublicTransportModesFromPtEntity(osmStation.getId(), tags, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags));
    Collection<String> eligibleOsmModes = modeResult!= null ? modeResult.first() : null;
    Set<TransferZone> matchedTransferZones = new HashSet<TransferZone>();
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getPlanitData().getTransferZonesSpatially(eligibleSearchBoundingBox);
    if(potentialTransferZones != null && !potentialTransferZones.isEmpty()) {          
            
      /* find potential matched transfer zones based on mode compatibility while tracking group memberships, for groups with multiple members
       * we enforce exact mode compatibility and do not allow for pseudo compatibility (yet) */
      Set<TransferZoneGroup> potentialTransferZoneGroups = findModeCompatibleTransferZoneGroups(eligibleOsmModes, potentialTransferZones, false /* exact mode compatibility */);
      if(potentialTransferZoneGroups!=null && !potentialTransferZoneGroups.isEmpty()) {
        
        /* find transfer group and zone match(es) based on proximity -> then process accordingly */
        if(!potentialTransferZoneGroups.isEmpty()) {
          /* when part of one or more transfer zone groups -> find transfer zone group with closest by transfer zone
           * then update all transfer zones within that group with this station information...
           * (in case multiple stations are close together we only want to update the right one) 
           */      
          TransferZone closestZone = (TransferZone) PlanitOsmUtils.findZoneClosestByTransferGroup(osmStation, potentialTransferZoneGroups, getNetworkToZoningData().getOsmNodes(), geoUtils);
          Set<TransferZoneGroup> groups = closestZone.getTransferZoneGroups();
          for(TransferZoneGroup group : groups) {
            updateTransferZoneGroupStationName(group, osmStation, tags);
            for(TransferZone zone : group.getTransferZones()) {
              updateTransferZoneStationName(zone, tags);
              matchedTransferZones.add(zone);          
            }
          }
        }        
          
      }else {
        
        /* try finding stand-alone transfer zones that are pseudo mode compatible instead, i.e., we are less strict at this point */
        Set<TransferZone> modeCompatibleTransferZones = findModeCompatibleTransferZones(eligibleOsmModes, potentialTransferZones, true /* allow pseudo mode compatibility*/);
        if(modeCompatibleTransferZones != null && !modeCompatibleTransferZones.isEmpty()){
          for(TransferZone zone : modeCompatibleTransferZones) {
            updateTransferZoneStationName(zone, tags);
            matchedTransferZones.add(zone);
          }        
        }    
      }
    }
                
    if(matchedTransferZones.isEmpty()) {
      /* create a new station with transfer zones and connectoids based on the stations eligible modes (if any) 
       * it is however possible that we found no matches because the station represents only unmapped modes, e.g. ferry 
       * in which case we can safely skip*/
      if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {
        
        /* * 
         * station with mapped modes 
         * --> extract a new station including dummy transfer zones and connectoids 
         * */
        extractStandAloneStation(osmStation, tags, geoUtils);
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
        
        Envelope boundingBox = PlanitOsmUtils.createBoundingBox(osmStation, getSettings().getStationToWaitingAreaSearchRadiusMeters(), getNetworkToZoningData().getOsmNodes(), geoUtils);        
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
    PlanitOsmZoningReaderOsmData osmData = getZoningReaderData().getOsmData();
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
    getZoningReaderData().getOsmData().removeUnprocessedPtv2StopPosition(osmNode.getId());
    
    if(osmNode.getId()==6646032960l) {
      int bla = 4;
    }
    
    /* modes for stop_position */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    Pair<Collection<String>, Collection<Mode>> modeResult = collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, defaultOsmMode);
    if(!PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {
      /* no eligible modes mapped to planit mode, ignore stop_position */
      return;
    }
    
    Collection<String> eligibleOsmModes = modeResult.first();
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    for(String osmMode: eligibleOsmModes) {
      Mode planitMode = getNetworkToZoningData().getSettings().getMappedPlanitMode(osmMode);
      MacroscopicPhysicalNetwork networkLayer = getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);
    
      /* a stop position should be part of an already parsed planit link, if not it is incorrectly tagged */
      if(!getNetworkToZoningData().getNetworkLayerData(networkLayer).isLocationPresentInLayer(osmNodeLocation)) {
        LOGGER.fine(String.format("DISCARD: stop_location %d is not part of any parsed link in the network, likely incorrectly tagged", osmNode.getId()));
        continue;
      }
  
      /* locate transfer zone(s) for stop_location/mode combination */
      Collection<TransferZone> matchedTransferZones = findIncompleteTransferZonesForStopPosition(osmNode, tags, Collections.singleton(osmMode));      
      if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {        
        
        if(PlanitOsmHandlerHelper.isPtv2StopPositionPtv1Stop(osmNode, tags)) {
          /* no potential transfer zones AND Ptv1 tagged (bus_stop, station, halt), try to salvage by parsing it as Ptv1 tag 
           * potentially revealing it resides on the road and can be treated as both stop_position and transfer zone now that we have eliminated the possibility of matching it to
           * a Ptv2 platform nearby */
          LOGGER.info(String.format("SALVAGED: process Ptv2 stop_position %d as Ptv1 entity, due to absence of eligible Ptv2 platform supporting mode %s",osmNode.getId(), osmMode));
          extractTransferInfrastructurePtv1(osmNode, tags, geoUtils);          
        }else {
          LOGGER.warning(String.format("DISCARD: stop_position %d has no valid pole, platform, station reference, nor closeby infrastructure that qualifies as such for mode %s",osmNode.getId(), osmMode));
        }
      }else {
        
        /* create connectoid(s) for stop_location/transferzone/mode combination */
        for(TransferZone transferZone : matchedTransferZones) {
          extractDirectedConnectoidsForMode(osmNode, transferZone, planitMode, geoUtils);          
        }
      }
      
      
      /* 
       * TODO ------->
       * what link segment to create the connectoid on is to be derived from the transfer zone
       * 1: transfer zone is not on road --> depending on which side of the road and country -> determine the direction
       * 2: when platform is also the stop_location (tram_stop/bus_stop) use both directions to be sure (can be removed later if needed)
       * 3: when mapped link only has single direction, use that instead
       */
      
      //stop_position can service multiple transfer zones (bus_stops) when poles are on either side of the road
      //  --> but there can also be two really close stop_locations servcigin two poles, this is difficult!
      //  --> solution when multiple potential transfer zones are found --> see if any other unprocessed stop_locations
      //      are any closer, if so, do not attach this transfer zone as well
      //      ALSO -> try to match on names/refs. when they both have a name --> it must match, otherwise it is discarded when dealing
      //               with multiple matches --> too unlikely they belong together...      
      //      example: 4111222208 and 4111222205      
      
    }
         
    
    
    //TODO continue here
    
  }

  /**
   * process any remaining unprocessed stop_positions that are not part of any stop_area. This means the stop_position has not yet been matched
   * to any platform/pole, i.e., transferzone. It is our task to do that now (if possible).
   *  
   * @throws PlanItException thrown if error
   */
  private void processStopPositionsNotPartOfStopArea() throws PlanItException {
    Set<Long> unprocessedStopPositions = Set.copyOf(getZoningReaderData().getOsmData().getUnprocessedPtv2StopPositions());
    if(!unprocessedStopPositions.isEmpty()) {
      for(Long osmNodeId : unprocessedStopPositions) {
        OsmNode osmNode =getNetworkToZoningData().getOsmNodes().get(osmNodeId);
        processStopPositionNotPartOfStopArea(osmNode, OsmModelUtil.getTagsAsMap(osmNode));  
      } 
      
      LOGGER.info(String.format("%d UNPROCESSED STOP_POSITIONS REMAIN -> TODO",unprocessedStopPositions.size()));
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
    if(transferZone.getExternalId()!=null && transferZone.getExternalId().equals("811015171")) {
      int bla = 4;
    }
    
    EntityType osmEntityType = (transferZone.getGeometry() instanceof Point) ? EntityType.Node : EntityType.Way;
    long osmEntityId = Long.valueOf(transferZone.getExternalId());      
        
    /* validate mode support */
    Collection<String> accessOsmModes = PlanitOsmHandlerHelper.getEligibleOsmModesForTransferZone(transferZone);
    accessOsmModes = PlanitOsmModeUtils.getPublicTransportModesFrom(accessOsmModes);
    if(!getNetworkToZoningData().getSettings().hasAnyMappedPlanitMode(accessOsmModes)) {
      LOGGER.severe(String.format("Incomplete transfer zone (osm id %d) without supported planit modes present, this shouldn't happen", osmEntityId));
      return;             
    }
        
    /* validate location is separated from infrastructure */
    boolean transferZoneOnInfrastructure = osmEntityType.equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmEntityId);
    if(transferZoneOnInfrastructure) {
      /* all waiting areas that are on road/rail infrastructure should immediately have been processed including their connectoid creation, so if we still find one here
       * something went wrong that is worth logging */
      LOGGER.severe(String.format("Incomplete transfer zone (osm id %d) identified to be placed on road/rail infrastructure, this shouldn't happen", osmEntityId));
      return;
    }     
    
    /* per mode - find links, because if the transfer zone supports both a rail and road mode, we require different links for our connectoids */
    for(String osmAccessMode : accessOsmModes) {
      Mode accessMode = getNetworkToZoningData().getSettings().getMappedPlanitMode(osmAccessMode);
      MacroscopicPhysicalNetwork networkLayer = getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(accessMode);     
    
      Collection<Link> directionModeSpatiallyCompatibleLinks = null;
      if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmEntityId, osmEntityType)) {
        
        /* user override for what link to use for connectoids */        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmEntityId, osmEntityType);
        Link nominatedLink = PlanitOsmHandlerHelper.getClosestLinkWithOsmWayIdToGeometry( osmWayId, transferZone.getGeometry(), networkLayer, geoUtils);
        if(nominatedLink != null) {
          directionModeSpatiallyCompatibleLinks = Collections.singleton(nominatedLink); 
        }else {
          LOGGER.severe(String.format("User nominated osm way not available for waiting area %d",osmWayId));
        }
      }else {
        
        /* regular approach */                     
        Envelope searchBoundingBox = geoUtils.createBoundingBox(transferZone.getEnvelope(), getSettings().getStopToWaitingAreaSearchRadiusMeters());
        /* collect spatially, mode, direction compatible links */
        directionModeSpatiallyCompatibleLinks = findStopLocationLinksForWaitingArea(osmEntityId, transferZone.getGeometry(), Collections.singleton(osmAccessMode), searchBoundingBox);
      }
      
      if(directionModeSpatiallyCompatibleLinks == null || directionModeSpatiallyCompatibleLinks.isEmpty()) {
        if(geoUtils.isGeometryNearBoundingBox(transferZone.getGeometry(), getNetworkToZoningData().getNetworkBoundingBox(), PlanitOsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS)) {
          LOGGER.fine(String.format("DISCARD: No accessible links for transfer zone %s and mode %s due to proximity to network bounding box", getSettings().getStopToWaitingAreaSearchRadiusMeters(), transferZone.getExternalId(), osmAccessMode));
        }else {
          LOGGER.warning(String.format("DISCARD: No accessible links within maximum search distance (%.2fm) found for transfer zone %s and mode %s", getSettings().getStopToWaitingAreaSearchRadiusMeters(), transferZone.getExternalId(), osmAccessMode));
        }
        return;
      }
                      
      /* select access link */
      Link closestLinkForStopLocation = (Link)PlanitGraphGeoUtils.findEdgeClosest(transferZone.getGeometry(), directionModeSpatiallyCompatibleLinks, geoUtils);
      if(closestLinkForStopLocation==null) {
        throw new PlanItException("No closest link could be found from selection of eligible closeby links when finding stop locations for transfer zone (osm entity id %s), this should not happen", transferZone.getExternalId());
      }   
      
      //TODO -> instead of the below have a method that finds the edges within Xm of the closest. Then if multiple are found, remove the ones that would not be suitable
      // as per below. Then select the closest one from those remaining.
      
      if(directionModeSpatiallyCompatibleLinks.size() > 1) {
        /* verify if closest link shares "closeness" with other links, which is only the case if its closest location is an extreme node */
        LinearLocation projectedLinearLocationOnLink = PlanitOsmHandlerHelper.getClosestProjectedLinearLocationOnLinkForTransferZone(transferZone,closestLinkForStopLocation, geoUtils);
        Coordinate closestLocation = projectedLinearLocationOnLink.getCoordinate(closestLinkForStopLocation.getGeometry());
        Node closestExtremeNode = null;
        if(closestLinkForStopLocation.getNodeA().isPositionEqual2D(closestLocation)){
          closestExtremeNode = closestLinkForStopLocation.getNodeA();
        }else if(closestLinkForStopLocation.getNodeB().isPositionEqual2D(closestLocation)) {
          closestExtremeNode = closestLinkForStopLocation.getNodeB();
        }
        if(closestExtremeNode != null) {
          /* possibly the wrong closest link chosen multiple options with equal distance */
          while(Link potentialLink : directionModeSpatiallyCompatibleLinks) {
            if(potentialLink.getNodeA().idEquals(closestExtremeNode) || potentialLink.getNodeB().idEquals(closestExtremeNode) &&
               hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(transferZone, potentialLink, closestExtremeNode, accessMode))
            {

            }
        }
      }      
            
               
      /* create connectoids */    
      extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(Long.valueOf(transferZone.getExternalId()),transferZone.getGeometry(),closestLinkForStopLocation, transferZone, networkLayer, accessMode);                                          
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
   * all transfer zones that were created without connectoids AND were found to not be linked to any stop_positions in a stop_area will still have no connectoids.
   * Connectoids need to be created based on implicit stop_position of vehicles which by OSM standards is defined as based on the nearest node. This is what we will do here.
   * @throws PlanItException thrown if error
   */
  private void processIncompleteTransferZonesNotPartOfStopArea() throws PlanItException {
    processIncompleteTransferZones(getZoningReaderData().getPlanitData().getTransferZonesByOsmId(EntityType.Node)); 
    processIncompleteTransferZones(getZoningReaderData().getPlanitData().getTransferZonesByOsmId(EntityType.Way));    
  }

  /** extract a connectoid location within the link based on an existing coordinate (osm node) or by inserting an additional coordinate in the location closest to the provided
   * waiting area geometry. A new location is only inserted into the link's geometry when all existing coordinates on the link's geometry fall outside the user specified distance between
   * waiting area and stop location.
   * 
   * @param osmEntityId osm id of the entity we are intending to create a stop location (connectoid) for
   * @param waitingAreaGeometry geometry of the waiting area
   * @param accessLink to create connectoid location on on either one of its extreme or internal coordinates
   * @param accessMode to consider
   * @param networkLayer the link is registered on
   * @return connectoid location to use, may or may not be an existing osm node location, or not
   * @throws PlanItException thrown if error
   */
  private Point extractConnectoidLocationForstandAloneTransferZoneOnLink(TransferZone transferZone, Link accessLink, Mode accessMode, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    /* determine distance to closest osm node on existing planit link to create stop location (connectoid) for*/
        
    Coordinate closestExistingCoordinate = geoUtils.getClosestExistingLineStringCoordinateToGeometry(transferZone.getGeometry(), accessLink.getGeometry());
    double distanceToExistingCoordinateOnLinkInMeters = geoUtils.getClosestDistanceInMeters(PlanitJtsUtils.createPoint(closestExistingCoordinate), transferZone.getGeometry());        
    
    /* if close enough break at existing osm node to create stop_position/connectoid, otherwise create artificial non-osm node in closest projected location which
     * in most cases will be closer and within threshold */
    Point connectoidLocation = null;
    if(distanceToExistingCoordinateOnLinkInMeters < getSettings().getStopToWaitingAreaSearchRadiusMeters()) {
      
      /* close enough, identify osm node at coordinate location 
       * 1) node is an extreme node
       * 2) or node is internal to link
       * */
      
      /* 1) verify if extreme node */
      if(accessLink.getVertexA().isPositionEqual2D(closestExistingCoordinate)) {
        /* because it is an extreme node there is only one of the two directions accessible since ann access link segments are assumed to be directly upstream of the node. This
         * can result in choosing a connectoid location that is not feasible when only considering the prxomitiy and not the link segment specific information such as the mode
         * and relative location to the transfer zone (left or right of the road). Therefore we must check this here before accepting this pre-existing extreme node. If this is a problem,
         * we do not create the location on the existing location, but instead break the link so that we can use the access link segment in the opposite direction instead */
        if(hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(transferZone, accessLink, accessLink.getNodeA(), accessMode)) {       
          connectoidLocation = PlanitJtsUtils.createPoint(closestExistingCoordinate);
        }
      }else if(accessLink.getVertexB().isPositionEqual2D(closestExistingCoordinate)) {
          if(hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(transferZone, accessLink, accessLink.getNodeB(), accessMode)){
            connectoidLocation = PlanitJtsUtils.createPoint(closestExistingCoordinate);  
          }        
      }else {
      
        /* 2) must be internal if not an extreme node */
        PlanitJtsIntersectOsmNodeVisitor spatialqueryVisitor = new PlanitJtsIntersectOsmNodeVisitor(PlanitJtsUtils.create2DPolygon(accessLink.getEnvelope()), new HashSet<OsmNode>());
        spatiallyIndexedOsmNodesInternalToPlanitLinks.get(networkLayer).query(accessLink.getEnvelope(),spatialqueryVisitor);
        Collection<OsmNode> potentialOsmNodes = spatialqueryVisitor.getResult();
        
        /* find osm node from nearby osm nodes */
        OsmNode linkInternalOsmNode = PlanitOsmNodeUtils.findOsmNodeWithCoordinate2D(closestExistingCoordinate, potentialOsmNodes);
        if(linkInternalOsmNode==null) {
          throw new PlanItException("Unable to locate link internal osm node even though it is expected to exist when creating stop locations for osm entity %s",transferZone.getExternalId());
        }
        
        /* now convert osm node to location, since this location already existed, the osm node's location is already marked as internal to the link and ensures the link is 
         * broken correctly when creating connectoids */
        connectoidLocation = PlanitOsmNodeUtils.createPoint(linkInternalOsmNode);
      }
    }
     
    if(connectoidLocation == null) {
      /* too far, or identified existing location is not suitable, so we must break the existing link in appropriate location instead */
      LinearLocation projectedLinearLocationOnLink = PlanitOsmHandlerHelper.getClosestProjectedLinearLocationOnLinkForTransferZone(transferZone,accessLink, geoUtils);      
      
      /* verify projected location is valid */
      Coordinate closestProjectedCoordinate = projectedLinearLocationOnLink.getCoordinate(accessLink.getGeometry());
      if( closestExistingCoordinate.equals2D(closestProjectedCoordinate) || 
          geoUtils.getClosestDistanceInMeters(PlanitJtsUtils.createPoint(closestProjectedCoordinate), transferZone.getGeometry()) > getSettings().getStopToWaitingAreaSearchRadiusMeters()) {
        /* no need to break link, the projected closest point is also too far away */
        return null;
      }
      
      /* add projected location to geometry of link */
      Pair<LineString, LineString> splitLineString = PlanitJtsUtils.splitLineString(accessLink.getGeometry(),projectedLinearLocationOnLink);          
      LineString linkGeometryWithExplicitProjectedCoordinate = PlanitJtsUtils.mergeLineStrings(splitLineString.first(),splitLineString.second());
      connectoidLocation = PlanitJtsUtils.createPoint(projectedLinearLocationOnLink.getCoordinate(accessLink.getGeometry()));
      accessLink.setGeometry(linkGeometryWithExplicitProjectedCoordinate);            
      
      /* new location must be marked as internal to link, otherwise the link will not be broken when extracting connectoids at this location*/
      getNetworkToZoningData().getNetworkLayerData(networkLayer).registerLocationAsInternalToPlanitLink(connectoidLocation, accessLink);
      
    }
    
    return connectoidLocation;
  }

  /** create and/or update directed connectoids for the transfer zones and mode combinations when eligible, based on the passed in osm node 
   * where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param tags to use
   * @param transferZones connectoids are assumed to provide access to
   * @param transferZoneGroup the connectoids belong to
   * @param planitModes this connectoid is allowed access for
   * @param transferZoneGroup it belongs to, when zone is not yet in the group the zone is added to the group (group is allowed to be null)
   * @return true when at least connectoids where created for one of the transfer zones identified
   * @throws PlanItException thrown if error
   */
  private boolean extractDirectedConnectoids(OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> transferZones, Collection<Mode> planitModes, TransferZoneGroup transferZoneGroup) throws PlanItException {
    boolean success = false; 
    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    for(Mode planitMode : planitModes) {
      
      /* layer */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);
      if(!getNetworkToZoningData().getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)) {
        LOGGER.info(String.format("DISCARD: stop_position %d is not present in parsed network layer supporting mode %s, likely it is dangling in original osm file",osmNode.getId(), planitMode.getExternalId()));
        continue;
      }
      
      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) */
        success = extractDirectedConnectoidsForMode(osmNode, transferZone, planitMode, geoUtils) || success;
        if(success && transferZoneGroup != null && !transferZone.isInTransferZoneGroup(transferZoneGroup)) {
          /* in some rare cases only the stop locations are part of the stop_area, but not the platforms next to the road/rail, only then this situation is triggered and we salve the situation */
          LOGGER.info(String.format("SALVAGED: Platform/pole %s identified for stop_position %d, platform/pole not in stop_area %s of stop_position, added it",transferZone.getExternalId(), osmNode.getId(), transferZoneGroup.getExternalId()));
          transferZoneGroup.addTransferZone(transferZone);
        }
      }      
    }
    
    return success;
  }
  
  /** create connectoids not based on osm node location but based on auto-generated geographic location on the provided link's link segments by
   * finding either a close enough existing coordinate (osm node), or if not close enough a newly created coordinate at the appropriate position.
   * then create connectoids accordingly by breaking the link in these locations
   * 
   * @param osmWaitingAreaId the waiting area pertains to
   * @param waitingAreaGeometry geometry of the waiting area
   * @param accessLink to create connectoids on by breaking it
   * @param transferZone to register connectoids on
   * @param networkLayer the modes relate to
   * @param accessMode eligible mode for the station
   * @throws PlanItException thrown if error
   */
  private void extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(
      long osmWaitingAreaId, Geometry waitingAreaGeometry , Link accessLink, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode accessMode) throws PlanItException {               
    
    if(transferZone.getExternalId().equals("811015171")) {
      int bla = 4;
    }
    
    /* geo location on planit link, possibly inserted for this purpose by this method if no viable osm node/existing coordinate is present */
    Point connectoidLocation = extractConnectoidLocationForstandAloneTransferZoneOnLink(transferZone, accessLink, accessMode, networkLayer);
    if(connectoidLocation == null) {
      LOGGER.warning(String.format(
          "DISCARD: Unable to create stop_location on identified access link %s, identified location is likely too far from waiting area %s",accessLink.getExternalId(),transferZone.getExternalId()));
    }
    
    /* special case - user overwrite verification */
    OsmNode osmStopLocationNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(connectoidLocation);
    if(osmStopLocationNode != null && getSettings().isOverwriteStopLocationWaitingArea(osmStopLocationNode.getId())) {
      /* user has chosen to overwrite waiting area for this connectoid (stop_location), so the transfer zone provided should correspond to the chosen waiting area id, otherwise
       * we simply ignore and return (when processing incomplete transfer zones, it might try to use a stop_location for a transfer zone that is incomplete but indicated by the user to
       * not be used for this connectoid, so there can be a valid reason why this method is invoked, as well as a valid reason to not create connectoids when checking for this situation */
      Pair<EntityType, Long>  overwriteResult = getSettings().getOverwrittenStopLocationWaitingArea(osmStopLocationNode.getId());
      /* when type match (point=node, otherwise=way)  and id match we can continue, otherwise not */
      if( !(waitingAreaGeometry instanceof Point && Long.valueOf(transferZone.getExternalId()) == overwriteResult.second())) {
        return;
      }else if( Long.valueOf(transferZone.getExternalId()) != overwriteResult.second()) {
        return;
      }
    }            
          
    /* create connectoids at identified location for mode and restricted to the accessLink identified (or update existing connectoid with mode access if valid) */
    extractDirectedConnectoidsForMode(connectoidLocation, transferZone, accessMode, Collections.singleton(accessLink), geoUtils);
  }

  /** Once a station is identified as stand-alone during processing and the transfer zone is created/identified we create its connectoids here.
   * 
   * @param osmStation to extract from
   * @param tags of the station
   * @param stationTransferZone transfer zone of the station
   * @param planitAccessModes the eligible modes
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  private void extractStandAloneStationConnectoids(OsmEntity osmStation, Map<String, String> tags, TransferZone stationTransferZone, Collection<Mode> planitAccessModes, PlanitJtsUtils geoUtils) throws PlanItException {
    EntityType osmStationEntityType = Osm4JUtils.getEntityType(osmStation);
    
    /* modes */
    for(Mode planitAccessMode : planitAccessModes) {
      MacroscopicPhysicalNetwork networkLayer = getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitAccessMode);
      Collection<String> osmModes = getNetworkToZoningData().getSettings().getMappedOsmModes(planitAccessMode);
      
      /* station mode determines where to create stop_locations and how many. It is special in this reagrd and different from a regular transfer zone (platform/pole) */
      Pair<Double, Integer> result = PlanitOsmHandlerHelper.determineSearchDistanceAndMaxStopLocationMatchesForStandAloneStation(osmStation.getId(), osmModes, getSettings());
      if(result == null) {
        LOGGER.warning(String.format("DISCARD: unable to process stand-alone station %d supported mode %s, skip", osmStation.getId(),osmModes.toString()));
        continue;
      }
      double maxSearchDistance =result.first();
      int maxStopLocations = result.second(); 
      
      /* links per mode*/
      Collection<Link> accessLinks = null;
      if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmStation.getId(), osmStationEntityType)) {
        
        /* user override for what link to use for connectoids */        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmStation.getId(), osmStationEntityType);
        Link nominatedLink = PlanitOsmHandlerHelper.getClosestLinkWithOsmWayIdToGeometry( osmWayId, stationTransferZone.getGeometry(), networkLayer, geoUtils);
        if(nominatedLink != null) {
          accessLinks = Collections.singleton(nominatedLink); 
        }else {
          LOGGER.severe(String.format("User nominated osm way not available for station %d",osmWayId));
        }                    
        
      }else {      
      
        /* regular approach */
        /* accessible links for station conditioned on found modes */
        accessLinks = findStopLocationLinksForStation(osmStation, tags, osmModes, maxSearchDistance, maxStopLocations);
      }
      
      if(accessLinks == null) {
        LOGGER.warning(String.format("DISCARD: station %d has no eligible links to qualify for pt vehicles as stop locations",osmStation.getId()));
        return;
      }                   
      
      /* connectoids per mode per link */
      for(Link accessLink : accessLinks) {
      
        /* identify locations based on links and spatial restrictions, because no stop_location is known in osm, nearest osm node might be to
         * far away and we must break the link and insert a planit node without an osm node counterpart present, this is what the below method does */
        extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(osmStation.getId(), stationTransferZone.getGeometry() , accessLink, stationTransferZone, networkLayer, planitAccessMode);            
      }
    }
  }


  /** Once a station is identified as stand-alone during processing, i.e., no platforms, poles nearby, we must create the appropriate
   * transfer zones (without connectoids). Then afterwards we create the connectoids as well
   * 
   * @param osmStation to extract from
   * @param tags of the station
   * @throws PlanItException thrown if error
   */
  private void extractStandAloneStation(OsmEntity osmStation, Map<String, String> tags, PlanitJtsUtils geoUtils) throws PlanItException {
    
    if(osmStation.getId()==715380193l) {
      int bla = 4;
    }
    
    /* modes */
    String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags, OsmRailModeTags.TRAIN);
    Pair<Collection<String>, Collection<Mode>> modeResult = collectPublicTransportModesFromPtEntity(osmStation.getId(), tags, defaultMode);
    if(!PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {
      return;
    }
    Collection<Mode> planitAccessModes = modeResult.second();              
        
    TransferZone stationTransferZone = null;
    EntityType osmStationEntityType = Osm4JUtils.getEntityType(osmStation);
    boolean stationOnTrack = osmStationEntityType.equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmStation.getId()); 
    if(stationOnTrack && !getSettings().isOverwriteStopLocationWaitingArea(osmStation.getId())) {              
      /* transfer zone + connectoids */
            
      /* station is stop_location as well as transfer zone*/      
      OsmNode osmStationNode = getNetworkToZoningData().getOsmNodes().get(osmStation.getId());      
      createAndRegisterPtv1TransferZoneWithConnectoidsAtOsmNode(osmStationNode, tags, defaultMode, geoUtils);
      
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
          stationTransferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmStation, tags, TransferZoneType.SMALL_STATION, defaultMode );          
        }
            
        if(stationTransferZone == null) {
          LOGGER.warning(String.format("UNABLE TO CREATE TRANSFERZONE FOR STATION %d", osmStation.getId()));  
        }
        this.updateTransferZoneStationName(stationTransferZone, tags);
      }        
      
      /* if station is assigned a particular pre-existing stop_location, no need to find stop_locations (create connectoids) as part of processing the station, this
       * will be taken care of when processing the stop_positions */
      if(getSettings().isWaitingAreaStopLocationOverwritten(osmStationEntityType, osmStation.getId())){         
         return;
      }else {
      
        /* connectoids */
        extractStandAloneStationConnectoids(osmStation, tags, stationTransferZone, planitAccessModes, geoUtils);
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
    
    /* unproccessed stations -> create transfer zone and connectoids (implicit stop_positions). Only create connectoids when station is on track/road, otherwise
     * create incomplete transfer zone to be dealt with later */
    processStationsNotPartOfStopArea();       
    
    /* unprocessed stop_positions -> do first because all transfer zones (without connectoids) are now available */
    processStopPositionsNotPartOfStopArea();    
    
    /* transfer zones without connectoids, i.e., implicit stop_positions not part of any stop_area, --> create connectoids for remaining transfer zones without connectoids*/
    processIncompleteTransferZonesNotPartOfStopArea();                  
    
  }  
    
  /** extract a regular Ptv2 stop position that is part of a stop_area relation and is registered before as a stop_position in the main processing phase. 
   * Extract based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param osmNode node that is the stop_position in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractKnownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {
    
    if(osmNode.getId()==1287222139l) {
      int bla = 4;
    }
          
    /* supported modes */
    Pair<Collection<String>, Collection<Mode>> modeResult = collectModesFromPtEntity(osmNode.getId(), tags, null);
    if(!PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {
      return;
    }
          
    /* find the transfer zones this stop position is eligible for */
    Collection<TransferZone> matchedTransferZones = findIncompleteTransferZonesForStopPosition(osmNode, tags, modeResult.first(), transferZoneGroup);
          
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* still no match, issue warning */
      LOGGER.severe(String.format("DISCARD: Stop position %d in stop_area %s has no valid pole, platform, station reference, nor closeby infrastructure that qualifies", osmNode.getId(), transferZoneGroup.getExternalId()));
      return;
    }             
    
    /* connectoids */
    extractDirectedConnectoids(osmNode, tags, matchedTransferZones, modeResult.second(), transferZoneGroup);
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
    Collection<TransferZone> matchedTransferZones = findIncompleteTransferZonesForStopPosition(osmNode, tags, null, transferZoneGroup);
            
    /* access modes */
    Set<Mode> accessModes;    
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      LOGGER.warning(String.format("DISCARD: stop_position %d without proper tagging on OSM network could not be mapped to closeby transfer zone in stop_area", osmNode.getId()));
      return;
    }else if(matchedTransferZones.size()>1){
      throw new PlanItException("Identified more than one spatially closest transfer zone for stop_position %d that was not tagged as such in stop_area %s, this should nto happen",osmNode.getId(), transferZoneGroup.getExternalId());
    }
    
    TransferZone foundZone = matchedTransferZones.iterator().next();  
    accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(PlanitOsmHandlerHelper.getEligibleOsmModesForTransferZone(foundZone));
    if(accessModes == null) {
      LOGGER.warning(String.format("DISCARD: stop_position %d without proper tagging on OSM network, unable to identify access modes from closest transfer zone in stop_area", osmNode.getId()));
      return;             
    }
             
    /* connectoids */
    extractDirectedConnectoids(osmNode, tags, Collections.singleton(foundZone), accessModes, transferZoneGroup);   
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param member member in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaStopPosition(final OsmRelationMember member, final TransferZoneGroup transferZoneGroup) throws PlanItException {
    PlanItException.throwIfNull(member, "Stop_area stop_position member null");
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_POSITION);
    
    /* only proceed when not marked as invalid earlier */
    if(getZoningReaderData().getOsmData().isInvalidStopAreaStopPosition(member.getType(), member.getId())) {
      return;
    }          
    /* validate state and input */
    if(member.getType() != EntityType.Node) {
      throw new PlanItException("Stop_position %d encountered that it not an OSM node, this is not permitted",member.getId());
    }      
    
    
    OsmNode stopPositionNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(stopPositionNode);            
    Boolean isKnownPtv2StopPosition = null;
    
    /* regular stop_position or special cases due to tagging errors */
    {
      if(getZoningReaderData().getOsmData().hasUnprocessedPtv2StopPosition(member.getId())){
      
        /* registered as unprocessed --> known and available for processing */      
        isKnownPtv2StopPosition = true;      
        
      }else {
        
        /* check if connectoids already exist, if not (due to being part of multiple stop_areas or because it is a Ptv1 stop that has already been processed) */
        boolean isPtv2NodeOnly = !PlanitOsmHandlerHelper.isPtv2StopPositionPtv1Stop(stopPositionNode, tags);
        boolean alreadyProcessed = getZoningReaderData().getPlanitData().hasAnyDirectedConnectoidsForLocation(PlanitOsmNodeUtils.createPoint(stopPositionNode)); 
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
        
    
    /* stop location via Osm node */    
    if(isKnownPtv2StopPosition) {
      
      /* process as regular stop_position */
      extractKnownPtv2StopAreaStopPosition(stopPositionNode, tags, transferZoneGroup);
      /* mark as processed */
      getZoningReaderData().getOsmData().removeUnprocessedPtv2StopPosition(stopPositionNode.getId());
      
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
            
      if(member.getId()==4237552379l) {
        int bla = 4;
      }
      
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
   * constructor
   * 
   * @param transferSettings for the handler
   * @param hanlderData the handler data gathered by preceding handlers for zoning parsing
   * @param network2ZoningData data collated from parsing network required to successfully popualte the zoning
   * @param zoningToPopulate to populate
   * @param profiler to use
   * are not of interest and would otherwise be discarded 
   */
  public PlanitOsmZoningPostProcessingHandler(
      final PlanitOsmPublicTransportSettings transferSettings, 
      final PlanitOsmZoningReaderData handlerData, 
      final PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {
    super(transferSettings, handlerData, network2ZoningData, zoningToPopulate, profiler);
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsUtils(network2ZoningData.getOsmNetwork().getCoordinateReferenceSystem());
    
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    reset();
    
    PlanItException.throwIf(
        getNetworkToZoningData().getOsmNetwork().infrastructureLayers == null || getNetworkToZoningData().getOsmNetwork().infrastructureLayers.size()<=0,
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
            LOGGER.info(String.format("Unknown public_transport relation %s encountered for relation %d, ignored",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
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
        
    /* log stats */
    getProfiler().logPostProcessingStats(getZoning());
    
    LOGGER.info(" OSM (transfer) zone post-processing ...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    spatiallyIndexedOsmNodesInternalToPlanitLinks = new HashMap<MacroscopicPhysicalNetwork, Quadtree>();
  }
  
}

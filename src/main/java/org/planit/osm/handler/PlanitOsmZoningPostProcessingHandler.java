package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
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
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsIntersectEdgeVisitor;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.misc.StringUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
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
 * Handler that conducts final parsing round where all stop_positions in relatons are mapped to the now parsed transfer zones.
 * This is done separately because transfer zones are sometimes also part of relations and it is not guaranteed that all transfer zones
 * are available when encountering a stop_position in a relation. So we parse them in another pass.
 * <p>
 * Also, all unprocessed stations that are not part of any relatino are converted into transfer zones and connectoids here since
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
  
  /** to be able to map stand-alone stations and platforms to connectoids in the network, we must be able to spatially find closeby created
   * links, this is what we do here. It is not placed in the zoning data as it is only utilised in post-processing */
  private Quadtree spatiallyIndexedPlanitLinks = null;
  
  /** to be able to find osm nodes internal to parsed planit links that we want to break to for example create stop locations for stand alone station, 
   * we must be able to spatially find those nodes spatially because they are not referenced by the station or planit link eplicitly, this is what we do here. 
   * It is not placed in the zoning data as it is only utilised in post-processing */
  private Map<MacroscopicPhysicalNetwork, Quadtree> spatiallyIndexedOsmNodesInternalToPlanitLinks = null;  
  
  /**
   * created spatially indexed link container
   */
  private void initialiseSpatiallyIndexedPlanitLinks() {
    for(MacroscopicPhysicalNetwork layer : getNetworkToZoningData().getOsmNetwork().infrastructureLayers) {
      for(Link link : layer.links) {
        spatiallyIndexedPlanitLinks.insert(link.getGeometry().getEnvelope().getEnvelopeInternal(),link);
      }
    }
  }  
  
  /**
   * created spatially indexed osm nodes internal to existing planit links container
   */
  private void initialiseSpatiallyIndexedOsmNodesInternalToPlanitLinks() {
    
    double envelopeMinExtentAbsolute = Double.POSITIVE_INFINITY;
    for(MacroscopicPhysicalNetwork layer : getNetworkToZoningData().getOsmNetwork().infrastructureLayers) {
      PlanitOsmNetworkLayerReaderData layerData = getNetworkToZoningData().getNetworkLayerData(layer);
      spatiallyIndexedOsmNodesInternalToPlanitLinks.put(layer, new Quadtree());
      Quadtree spatialcontainer = spatiallyIndexedOsmNodesInternalToPlanitLinks.get(layer);
            
      Map<Point, Pair<Node, OsmNode>> nodesByLocation = layerData.getCreatedPlanitNodesByLocation();
      if(!nodesByLocation.isEmpty()) {
        Pair<Node, OsmNode> anyEntry = nodesByLocation.values().iterator().next();
        /* because points have a delta of 0 in either dimension, the quadtree uses the default padding
         * of 1/2. However, because we use long/lat, this is way to coarse and we must override this minimum
         * extent, instead we set it roughly to the equivalent of 5 m (we do this once to avoid having to use geodetic calculations for every
         * node which would be very time consuming */
        double refPointX = PlanitOsmNodeUtils.getX(anyEntry.second());
        double refPointY = PlanitOsmNodeUtils.getY(anyEntry.second());
        Envelope boundingBox5Meters = geoUtils.createBoundingBox(refPointX, refPointY, 5 /*meters*/);
        double deltaX = Math.abs(refPointX - boundingBox5Meters.getMaxX());
        double deltaY = Math.abs(refPointY - boundingBox5Meters.getMaxY());
        envelopeMinExtentAbsolute = Math.max(deltaY, deltaX);        
      }
            
      Set<Point> registeredInternalLinkLocations = layerData.getRegisteredLocationsInternalToAnyPlanitLink();
      for( Point location: registeredInternalLinkLocations) {
        OsmNode osmNodeAtLocation = layerData.getOsmNodeInternalToLinkByLocation(location);
        Envelope pointEnvelope = new Envelope(location.getCoordinate());
        /* pad envelope with minium extent computed */
        spatialcontainer.insert(Quadtree.ensureExtent(pointEnvelope, envelopeMinExtentAbsolute), osmNodeAtLocation);
      }
    }
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
   * @throws PlanItException 
   */
  private Collection<Link> findStopLocationLinksForStationSpatially(
      OsmEntity osmStation, Map<String, String> tags, Collection<String> referenceOsmModes, Envelope searchBoundingBox, Integer maxMatches) throws PlanItException {
    
    /* match links spatially */
    PlanitJtsIntersectEdgeVisitor<Link> linkvisitor = new PlanitJtsIntersectEdgeVisitor<Link>(PlanitJtsUtils.create2DPolygon(searchBoundingBox), new HashSet<Link>());
    this.spatiallyIndexedPlanitLinks.query(searchBoundingBox, linkvisitor);
    Collection<Link> spatiallyMatchedLinks = linkvisitor.getResult();    
    if(spatiallyMatchedLinks == null || spatiallyMatchedLinks.isEmpty()) {
      LOGGER.warning(String.format("DISCARD: Stand alone station %d has no nearby infrastructure that qualifies for pt vehicles as stop locations",osmStation.getId()));
      return null;
    }    

    /* filter based on mode compatibility */
    Collection<Link> modeAndSpatiallyCompatibleLinks = findModeCompatibleLinks(referenceOsmModes, spatiallyMatchedLinks, false /*only exact matches allowed */);    
    if(modeAndSpatiallyCompatibleLinks == null || modeAndSpatiallyCompatibleLinks.isEmpty()) {
      LOGGER.warning(String.format("DISCARD: Stand alone station %d has no nearby infrastructure with compatible modes that qualifies for pt vehicles as stop locations",osmStation.getId()));
      return null;
    }
    
    /* #matches compatibility */
    Collection<Link> chosenLinksForStopLocations = null;    
    {
      Link closestLinkForStopLocation = (Link)PlanitOsmUtils.findEdgeClosest(osmStation, modeAndSpatiallyCompatibleLinks, getNetworkToZoningData().getOsmNodes(), geoUtils);
      if(closestLinkForStopLocation==null) {
        throw new PlanItException("no closest link could be found from selection of closeby links when finding stop locations for station %s, this should not happen", osmStation.getId());
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
        for(Link link : modeAndSpatiallyCompatibleLinks) {
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
      throw new PlanItException("no links could be identified from virtual line connecting station to closest by point on closest link for osm station %s, this should not happen", osmStation.getId());
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
  private Collection<Link> findStopLocationLinksForStation(OsmEntity osmStation, Map<String, String> tags, Collection<String> eligibleStationModes) throws PlanItException {
        
    Double searchDistance = null;
    Integer maxMatches = null;
    if(OsmRailModeTags.containsAnyMode(eligibleStationModes)) {
      /* rail based station -> match to nearby train tracks 
       * assumptions: small station would at most have two tracks with platforms and station might be a bit further away 
       * from tracks than a regular bus stop pole, so cast wider net */
      searchDistance = getSettings().getStationToWaitingAreaSearchRadiusMeters();
      maxMatches = 2;
    }else if(OsmRoadModeTags.containsAnyMode(eligibleStationModes)) {
      /* road based station -> match to nearest road link 
       * likely bus stop, so only match to closest by road link which should be very close, so use
       * at most single match and small search radius, same as used for pole->stop_position search */
      searchDistance = getSettings().getStopToWaitingAreaSearchRadiusMeters();
      maxMatches = 1;
    }else if(OsmWaterModeTags.containsAnyMode(eligibleStationModes)) {
      /* water based -> not supported yet */
      LOGGER.warning(String.format("DISCARD: water based stand-alone station detected %d, not supported yet, skip", osmStation.getId()));
    }
    
    Envelope searchBoundingBox = PlanitOsmUtils.createBoundingBox(osmStation, searchDistance , getNetworkToZoningData().getOsmNodes(), geoUtils);
    return findStopLocationLinksForStationSpatially(osmStation, tags, eligibleStationModes, searchBoundingBox, maxMatches);    
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
  private Collection<TransferZone> findClosestTransferZoneByTagReference(
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
            if(getEligibleOsmModesForTransferZone(transferZone)==null) {
              LOGGER.info(String.format("Salvaged: Platform/pole (%s) referenced by stop_position (%s), matched although platform has no known mode support", transferZone.getExternalId(), osmNode.getId()));              
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
          LOGGER.fine(String.format("Salvaged: non-unique reference (%s) on stop_position %d, selected spatially closest platform/pole %s", localRefValue, osmNode.getId(),foundTransferZones.get(localRefValue).getExternalId()));         
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
  
  /** find the closest and/or most likely transfer zone for the given osm node and its tags (with or without a reference
   * for additional information for mapping). Use the search radius from the settings to identify eligible transfer zones and then
   * use information on modes, references and spatial proximity to choose the most likely option. 
   * 
   * @param osmNode representing a stop position
   * @param tags of the node
   * @param referenceOsmModes the osm modes a transfer zone must at least contain one overlapping mapped mode from to be deemed accessible 
   * @param planitModes the stop is compatible with
   * @return most likely transfer zone
   * @throws PlanItException thrown if error
   */
  private TransferZone findMostLikelyTransferZoneForPtv2StopPositionSpatially(OsmNode osmNode, Map<String, String> tags, Collection<String> referenceOsmModes) throws PlanItException {
    TransferZone foundZone = null;
    
    /* collect potential transfer zones based on spatial search*/
    double searchRadiusMeters = getSettings().getStopToWaitingAreaSearchRadiusMeters();
    Envelope searchArea = geoUtils.createBoundingBox(osmNode.getLongitude(),osmNode.getLatitude(), searchRadiusMeters);
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getTransferZonesWithoutConnectoid(searchArea);
    
    if(potentialTransferZones==null || potentialTransferZones.isEmpty()) {
      LOGGER.fine(String.format("Unable to locate nearby transfer zone (search radius of %.2f (m)) when mapping stop position for osm node %d",searchRadiusMeters, osmNode.getId()));
      return null;
    }
        
    /* Ideally we relate via explicit references available on Osm tags */
    /* Occurs when: platform (zone) exists but is not included in stop_area. 
     * Note: This indicates poor tagging, yet occurs in reality, e.g. Sydney, circular quay for example */
    Collection<TransferZone> matchedTransferZones = findClosestTransferZoneByTagReference(osmNode, tags, potentialTransferZones, referenceOsmModes, geoUtils);      
    if(matchedTransferZones == null) {
      /* no explicit reference or name match is found, we collect the closest by potential match */
      foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, potentialTransferZones, geoUtils);
      if(foundZone == null) {
        LOGGER.warning(String.format("Unable to match osm node %d to any existing transfer zone within search radius of %.2f (m), ignored",osmNode,searchRadiusMeters));
      }
    }
        
    return foundZone;
  }

  /** find the transfer zones that are accessible to the stop_position on the given node and given the already identified transfer zones
   * in the group it belongs to
   * 
   * @param osmNode node representing the stop_position
   * @param tags of the node
   * @param referenceOsmModes the osm modes a transfer zone must at least contain one overlapping mapped mode from to be deemed accessible
   * @param stopAreaTransferzones the transfer zones of the stop_area this stop_position belongs to
   * @throws PlanItException thrown if error
   */  
  private Collection<TransferZone> findAccessibleTransferZonesForPtv2StopPosition(final OsmNode osmNode, final Map<String, String> tags, Collection<String> referenceOsmModes, final Collection<TransferZone> stopAreaTransferZones) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = null;
        
    /* reference to platform, i.e. transfer zone */
    matchedTransferZones = findClosestTransferZoneByTagReference(osmNode, tags, stopAreaTransferZones, referenceOsmModes, geoUtils);    
    if(matchedTransferZones == null || matchedTransferZones.isEmpty() && tags.containsKey(OsmTags.NAME)) {
      /* try matching names, this should only result in single match */
      matchedTransferZones = findTransferZoneMatchByName(osmNode.getId(),tags.get(OsmTags.NAME), stopAreaTransferZones, referenceOsmModes);
      if(matchedTransferZones!= null && matchedTransferZones.size()>1) {
        /* multiple match(es) found, find most likely spatially from this subset*/
        TransferZone foundTransferZone = (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, matchedTransferZones, geoUtils);        
        matchedTransferZones = Collections.singleton(foundTransferZone);
      }      
    }
          
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* no matches found by name or reference tag, so it has no reference, or transfer zone is not part of the area. Either way 
       * we must still try to find the most likely match. We do so geographically and based on mode compatibility */
      TransferZone foundTransferZone = findMostLikelyTransferZoneForPtv2StopPositionSpatially(osmNode, tags, referenceOsmModes);
      if(foundTransferZone != null) {
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
  private Set<TransferZoneGroup> findModeCompatibleTransferZoneGroups(Set<String> referenceOsmModes, Collection<TransferZone> potentialTransferZones, boolean allowPseudoModeMatches) {
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

  /** create and/or update directed connectoids for the transfer zones and mode combinations when eligible, based on the passed in osm node 
   * where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param tags to use
   * @param transferZones connectoids are assumed to provide access to
   * @param transferZoneGroup the connectoids belong to
   * @param planitMode this connectoid is allowed access for
   * @throws PlanItException thrown if error
   */
  private void extractDirectedConnectoids(OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> transferZones, Set<Mode> planitModes, TransferZoneGroup transferZoneGroup) throws PlanItException {
    boolean success = false; 
    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    for(Mode planitMode : planitModes) {
      /* layer */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);
  
      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) */
        success = extractDirectedConnectoidsForMode(osmNode, tags, transferZone, networkLayer, planitMode);
        if(success && transferZoneGroup != null && !transferZone.isInTransferZoneGroup(transferZoneGroup)) {
          /* in some rare cases only the stop locations are part of the stop_area, but not the platforms next to the road/rail, only then this situation is triggered and we salve the situation */
          LOGGER.info(String.format("Salvaged: platform/pole %s identified for stop_position %d is not part of the stop_area %s, added it to transfer zone group",transferZone.getExternalId(), osmNode.getId(), transferZoneGroup.getExternalId()));
          transferZoneGroup.addTransferZone(transferZone);
        }
      }      
    }
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
    getZoningReaderData().removeUnproccessedStation(ptVersion, osmStation);
            
    /* eligible modes for station, must at least support one or more mapped modes */
    Set<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmStation.getId(), tags, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags) );   
    Set<TransferZone> matchedTransferZones = new HashSet<TransferZone>();
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getTransferZonesWithoutConnectoid(eligibleSearchBoundingBox);
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
      if(getNetworkToZoningData().getSettings().hasAnyMappedPlanitMode(eligibleOsmModes)) {
        
        /* * 
         * station with mapped modes 
         * --> extract a new station including dummy transfer zones and connectoids 
         * */
        extractStandAloneStation(osmStation, tags);
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
      
    /* Ptv1 node station */
    if(!getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Node).values()); 
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Node, OsmPtVersionScheme.VERSION_1);
    }
    /* Ptv1 way station */
    if(!getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Way).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Way, OsmPtVersionScheme.VERSION_1);                       
    }
    /* Ptv2 node station */    
    if(!getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Node, OsmPtVersionScheme.VERSION_2);            
    }
    /* Ptv2 way station */    
    if(!getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).values());
      processStationsNotPartOfStopArea(unprocessedStations, EntityType.Way, OsmPtVersionScheme.VERSION_2);                
    }       
  }
  
  /** process the stop_position represented by the provided osm node that is nto part of any stop_area and therefore has not been matched
   * to any platform/pole yet, i.e., transfer zone. It is our task to do that now (if possible).
   * @param osmNode to process as stop_position if possible
   * @param tags of the node
   */
  private void processPtv2StopPositionNotPartOfStopArea(OsmNode osmNode, Map<String, String> tags) {
    /* modes for stop_position */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    Set<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmNode.getId(), tags, defaultOsmMode);
    Set<Mode> planitModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
    if(planitModes==null || planitModes.isEmpty()) {
      /* no eligible modes mapped to planit mode, ignore stop_position */
      return;
    }
    
    
    
    
    /* a stop position should be part of an already parsed planit link, if not it is incorrectly tagged */
    
  }   
  
  /**
   * process any remaining unprocessed stop_positions that are not part of any stop_area. This means the stop_position has not yet been matched
   * to any platform/pole, i.e., transferzone. It is our task to do that now (if possible).
   *  
   * @throws PlanItException thrown if error
   */
  private void processStopPositionsNotPartOfStopArea() {
    Set<Long> unprocessedStopPositions = getZoningReaderData().getUnprocessedPtv2StopPositions();
    if(!unprocessedStopPositions.isEmpty()) {
      for(Long osmNodeId : unprocessedStopPositions) {
        OsmNode osmNode =getNetworkToZoningData().getOsmNodes().get(osmNodeId);
        processPtv2StopPositionNotPartOfStopArea(osmNode, OsmModelUtil.getTagsAsMap(osmNode));  
      }            
      LOGGER.info(String.format("%d UNPROCESSED STOP_POSITIONS REMAIN -> TODO",unprocessedStopPositions.size()));
    }
  }   
  
 
  
  /** Once a station is identified as stand-alone during processing, i.e., no platforms, poles nearby, we must create the appropriate
   * transfer zones (without connectoids). Then afterwards we create the connectoids as well
   * 
   * @param osmStation to extract from
   * @param tags of the station
   * @throws PlanItException thrown if error
   */
  private void extractStandAloneStation(OsmEntity osmStation, Map<String, String> tags) throws PlanItException {
        
    /* First identify if this is a road or rail based station because they are treated differently */
    String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    Collection<String> eligibleStationModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmStation.getId(), tags, defaultMode);
    if(eligibleStationModes==null || eligibleStationModes.isEmpty()) {
      /* no known modes, we therefore assume train is the only eligible mode because it is most likely */
      defaultMode = OsmRailModeTags.TRAIN;
      eligibleStationModes = Collections.singleton(defaultMode);
    }    
    
    /* find the links to create connectoids on for this station (if any) */
    Collection<Link> accessibleLinks = findStopLocationLinksForStation(osmStation, tags, eligibleStationModes);
    if(accessibleLinks == null) {
      return;
    }    
    
    /* Single transfer zone for station*/
    TransferZone stationTransferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmStation, tags, TransferZoneType.SMALL_STATION, defaultMode );
    if(stationTransferZone == null) {
      LOGGER.warning(String.format("UNABLE TO CREATE TRANSFERZONE FOR STATION %d", osmStation.getId()));  
    }
    this.updateTransferZoneStationName(stationTransferZone, tags);
    
    /* connectoids for each valid link segment */
    for(Link link : accessibleLinks) {
      MacroscopicPhysicalNetwork networkLayer = getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(link);
      Collection<Mode> planitStationModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleStationModes);
      
      /* identify locations based on links and spatial restrictions, because no stop_location is known in osm, nearest osm node might be to
       * far away and we must break the link and insert a planit node without an osm node counterpart present, this is what the below method does */
      extractDirectedConnectoidsForTransferZoneBasedOnPlanitLinks(osmStation, tags, link, stationTransferZone, networkLayer, planitStationModes);   
       
    }
    
  }

  /** create connectoids not based on osm node location but based on auto-generated geographic location on the provided link's link segments by
   * finding either a close enough existing coordinate (osm node), or if not close enough a newly created coordinate at the appropriate position.
   * then create connectoids accordingly by breaking the link in these locations
   * 
   * @param osmStation to use
   * @param tags of the station
   * @param accessLink to create connectoids on by breaking it
   * @param stationTransferZone to register connectoids on
   * @param networkLayer the modes relate to
   * @param modes eligible modes for the station
   * @throws PlanItException thrown if error
   */
  private void extractDirectedConnectoidsForTransferZoneBasedOnPlanitLinks(
      OsmEntity osmStation, Map<String, String> tags, Link accessLink, TransferZone stationTransferZone, MacroscopicPhysicalNetwork networkLayer, Collection<Mode> modes) throws PlanItException {
    
    /* determine distance to closest osm node on existing planit link to create stop location (connectoid) for*/
    Coordinate closestExistingCoordinate = null;
    double distanceToExistingCoordinateOnLinkInMeters = Double.POSITIVE_INFINITY;
    EntityType osmStationType = Osm4JUtils.getEntityType(osmStation);
    if(osmStationType == EntityType.Node) {
      
      Coordinate osmStationCoordinate = PlanitOsmNodeUtils.createCoordinate((OsmNode)osmStation);
      closestExistingCoordinate = geoUtils.getClosestExistingCoordinateToPoint(PlanitJtsUtils.createPoint(osmStationCoordinate), accessLink.getGeometry());
      distanceToExistingCoordinateOnLinkInMeters = geoUtils.getDistanceInMetres(closestExistingCoordinate, osmStationCoordinate);
      
    }else if(osmStationType == EntityType.Way) {
      
      OsmWay osmStationWay = (OsmWay )osmStation;
      Geometry osmStationGeometry = PlanitOsmWayUtils.extractLineString(osmStationWay, getNetworkToZoningData().getOsmNodes());
      if(PlanitJtsUtils.isClosed2D(osmStationGeometry.getCoordinates())) {
        osmStationGeometry = PlanitJtsUtils.createPolygon(PlanitJtsUtils.makeClosed2D(osmStationGeometry.getCoordinates()));  
      }        
      closestExistingCoordinate = geoUtils.getClosestExistingCoordinateToGeometry(osmStationGeometry, accessLink.getGeometry());
      distanceToExistingCoordinateOnLinkInMeters = geoUtils.getClosestDistanceInMeters(PlanitJtsUtils.createPoint(closestExistingCoordinate), osmStationGeometry);
      
    }else {
      throw new PlanItException("unknown entity type %s encountered, skip connectoid creation for station %d, this should not happen",osmStation.getId());
    }
      
    /* if close enough break at existing osm node to create stop_position/connectoid, otherwise create artifical non-osm node in closest location */
    Point connectoidLocation = null;
    if(distanceToExistingCoordinateOnLinkInMeters > getSettings().getStopToWaitingAreaSearchRadiusMeters()) {
      /* too far, so we must break the existing link in appropriate location */
      LinearLocation projectedLinearLocationOnLink = null;
      if(osmStationType == EntityType.Node) {
        projectedLinearLocationOnLink = geoUtils.getClosestLinearLocationToPoint(PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.createCoordinate((OsmNode)osmStation)),accessLink.getGeometry());
      }else if(osmStationType == EntityType.Way) {
        Geometry stationGeometry = PlanitOsmWayUtils.extractGeometry((OsmWay)osmStation, getNetworkToZoningData().getOsmNodes());
        projectedLinearLocationOnLink = geoUtils.getClosestLinearLocationToGeometry(stationGeometry,accessLink.getGeometry());
      }else {
        throw new PlanItException("unknown entity type %s encountered for osm station %d, this should not happen",osmStation.getId());
      }
      
      /* add projected location to geometry of link */
      Pair<LineString, LineString> splitLineString = PlanitJtsUtils.splitLineString(accessLink.getGeometry(),projectedLinearLocationOnLink);          
      LineString linkGeometryWithExplicitProjectedCoordinate = PlanitJtsUtils.mergeLineStrings(splitLineString.first(),splitLineString.second());
      connectoidLocation = PlanitJtsUtils.createPoint(projectedLinearLocationOnLink.getCoordinate(accessLink.getGeometry()));
      accessLink.setGeometry(linkGeometryWithExplicitProjectedCoordinate);            
      
      /* new location must be marked as internal to link, otherwise the link will not be broken when extracting connectoids at this location*/
      getNetworkToZoningData().getNetworkLayerData(networkLayer).registerLocationAsInternalToPlanitLink(connectoidLocation, accessLink);
      
    }else {
      
      /* close enough, identify osm node at coordinate location */
      PlanitJtsIntersectOsmNodeVisitor spatialqueryVisitor = new PlanitJtsIntersectOsmNodeVisitor(PlanitJtsUtils.create2DPolygon(accessLink.getEnvelope()), new HashSet<OsmNode>());
      spatiallyIndexedOsmNodesInternalToPlanitLinks.get(networkLayer).query(accessLink.getEnvelope(),spatialqueryVisitor);
      Collection<OsmNode> potentialOsmNodes = spatialqueryVisitor.getResult();
      
      /* find osm node from nearby osm nodes */
      OsmNode linkInternalOsmNode = PlanitOsmNodeUtils.findOsmNodeWithCoordinate2D(closestExistingCoordinate, potentialOsmNodes);
      if(linkInternalOsmNode==null) {
        throw new PlanItException("Unable to locate link internal osm node even though it is expected to exist when creating stop locations for osm station %d",osmStation.getId());
      }
      
      /* now convert osm node to location, since this location already existed, the osm node's location is already marked as internal to the link and ensures the link is 
       * broken correctly when creating connectoids */
      connectoidLocation = PlanitOsmNodeUtils.createPoint(linkInternalOsmNode);
    }
      
    /* create connectoids at identified location */
    for(Mode planitMode : modes) {
      /* ... per mode (or update existing connectoid with mode access if valid */
      extractDirectedConnectoidsForMode(connectoidLocation, tags, stationTransferZone, networkLayer, planitMode);
    }       
  }

  /**
   * all transfer zones that were created without connectoids AND were found to not be linked to any stop_positions in a stop_area will still have no connectoids.
   * Connectoids need to be created based on implicit stop_position of vehicles which by OSM standards is defined as based on the nearest node. This is what we will do here.
   */
  private void extractRemainingNonStopAreaTransferZones() {
    if(!getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Node).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED (node) PLATFORMS REMAIN -> TODO",getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Node).size()));
    }  
    if(!getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Way).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED (way) PLATFORMS REMAIN -> TODO",getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Way).size()));
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
                
    /* unproccessed stations -> create transfer zone and connectoids (implicit stop_positions)*/
    processStationsNotPartOfStopArea();
        
    /* unprocessed stop_positions -> ? */
    processStopPositionsNotPartOfStopArea();
    
    /* transfer zones without connectoids, i.e., implicit stop_positions not part of any stop_area, --> create connectoids */
    extractRemainingNonStopAreaTransferZones();    
  }  
  
  /** extract a regular Ptv2 stop position that is part of a stop_area relation and is registered before as a stop_position in the main processing phase. 
   * Extract based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param osmNode node that is the stop_position in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @return 
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> extractKnownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {  
    Collection<TransferZone> matchedTransferZones = null;
        
    /* supported modes */
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmNode.getId(), tags, null);    
    Set<Mode> accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
    
    /* if at least one mapped mode is present continue */
    if(accessModes!=null && !accessModes.isEmpty()) {        
      matchedTransferZones = findAccessibleTransferZonesForPtv2StopPosition(osmNode, tags, eligibleOsmModes, transferZoneGroup.getTransferZones());
      
      if( (matchedTransferZones == null || matchedTransferZones.isEmpty()) && OsmPtv1Tags.isTramStop(tags)) {
        /* no match found, neither based on references, name, or geographically. Last possible option is that the stop_position is not only a stop_position, but also
         * the station/platform at the same time. This should not happen with Ptv2 tags. However in case a Ptv1 tag for a tram_stop is supplemented with a Ptv2 stop_position 
         * tag we get exactly this situation. The parser assumed a valid Ptv2 tagging and ignored the Ptv1 tag. However, it was only a valid Ptv1 tag and incomplete Ptv2 stop_area.
         * Since we can only detect this now, we must now identify this special situation and parse the tram_stop appropriately by creating a transfer zone for it before continuing
         * with the connectoids (technically this is a tagging error, we are just trying to fix it while parsing) */
        LOGGER.fine(String.format("Identified Ptv1 tram_stop (%d) that is also tagged as Ptv2 public_transport=stop_location, yet without Ptv2 platforms in the stop_area, parsing as Ptv1 entity instead", osmNode.getId()));
        TransferZone foundTransferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags));
        if(foundTransferZone != null) {
          transferZoneGroup.addTransferZone(foundTransferZone);
          matchedTransferZones = Collections.singleton(foundTransferZone);
        }
      }
      
      if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
        /* still no match, issue warning */
        LOGGER.severe(String.format("Stop position %d has no valid pole, platform, station reference, nor closeby infrastructure that qualifies as such, ignored",osmNode.getId()));
      }else {               
      /* connectoids */
        extractDirectedConnectoids(osmNode, tags, matchedTransferZones, accessModes, transferZoneGroup);
      }      
            
    }                  
      
    return matchedTransferZones;
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
  private Collection<TransferZone> extractUnknownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = null;
    Set<Mode> accessModes = null;
    
    /* not a proper stop_position, so we must infer its properties (eligible modes). We do so, by mapping it to the nearest transfer zone available on the stop_area (if any)
     *  as a last resort attempt */
    TransferZone foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneWithClosestCoordinateToNode(osmNode, transferZoneGroup.getTransferZones(), getSettings().getStopToWaitingAreaSearchRadiusMeters(), geoUtils);
    if(foundZone == null) {
      LOGGER.warning(String.format("DISCARD: stop_position %d without proper tagging on OSM network could not be mapped to closeby transfer zone in stop_area", osmNode.getId()));
    }else {        
      Collection<String> eligibleOsmModes = getEligibleOsmModesForTransferZone(foundZone);
      accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
      if(accessModes == null) {
        LOGGER.warning(String.format("DISCARD: stop_position %d without proper tagging on OSM network, unable to identify access modes from closest transfer zone in stop_area", osmNode.getId()));
      }else {
        matchedTransferZones = Collections.singleton(foundZone);
      }
    }
    
    if(accessModes != null){               
      /* connectoids */
      LOGGER.warning(String.format("Salvaged: stop_position %d without proper tagging on OSM network, matched to closest transfer zone with osm id %s instead", osmNode.getId(), foundZone.getExternalId()));
      extractDirectedConnectoids(osmNode, tags, matchedTransferZones, accessModes, transferZoneGroup);
    }    
    
    return matchedTransferZones;
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
    
    /* only proceed when not user excluded or when marked as invalid earlier */
    if(getSettings().isExcludedStopPosition(member.getId()) || getZoningReaderData().isInvalidStopAreaStopPosition(member.getType(), member.getId())) {
      return;
    }      
    
    /* validate state and input */
    if(member.getType() != EntityType.Node) {
      throw new PlanItException("Stop_position %d encountered that it not an OSM node, this is not permitted",member.getId());
    }      
    OsmNode stopPositionNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
    Point stopLocation = PlanitOsmNodeUtils.createPoint(stopPositionNode);
               
    Boolean isKnownPtv2StopPosition = null; 
    if(getZoningReaderData().getUnprocessedPtv2StopPositions().contains(member.getId())){
      /* registered as unprocessed --> known and available for processing */
      isKnownPtv2StopPosition = true;      
    }else if(getZoningReaderData().hasAnyDirectedConnectoidsForLocation(stopLocation)) {
      /* already processed by an earlier relation --> stop_position resides in multiple stop_areas, this is strongly discouraged by OSM, but does occur still so we identify and skip (no need to process twice anyway)*/
      LOGGER.warning(String.format("Encountered stop_position %d in stop_area %s that has been processed by earlier stop_area already, skip",member.getId(), transferZoneGroup.getExternalId()));
      return;
    }else {
      /* stop-position not processed and not know, so it is not properly tagged and we must infer mode access from infrastructure it resides on to salvage it */      
      LOGGER.fine(String.format("Stop_position %d in stop_area not marked as such on OSM node, inferring transfer zone and access modes by geographically closest transfer zone in stop_area instead ",member.getId()));
      isKnownPtv2StopPosition = false;
    }    
    
    /* stop location via Osm node */
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);    
    if(isKnownPtv2StopPosition) {
      
      /* process as regular stop_position */
      extractKnownPtv2StopAreaStopPosition(osmNode, tags, transferZoneGroup);
      /* mark as processed */
      getZoningReaderData().getUnprocessedPtv2StopPositions().remove(osmNode.getId());
      
    }else {
      
      /* unknown, so node is not tagged properly, try to salvage */
      extractUnknownPtv2StopAreaStopPosition(osmNode, tags, transferZoneGroup);
            
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
    TransferZoneGroup transferZoneGroup = getZoningReaderData().getTransferZoneGroupByOsmId(osmRelation.getId());
    if(transferZoneGroup == null) {
      LOGGER.severe(String.format("found stop_area %d in post-processing for which not PLANit transfer zone group has been created, this should not happen",osmRelation.getId()));
    }
        
    /* process only stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
            
      /* stop_position */
      if(member.getRole().equals(OsmPtv2Tags.STOP_ROLE)) {        
        
        extractPtv2StopAreaStopPosition(member, transferZoneGroup);       
        
      }else{
        
        
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
      final PlanitOsmTransferSettings transferSettings, 
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
    
    initialiseSpatiallyIndexedPlanitLinks();
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
    spatiallyIndexedPlanitLinks = new Quadtree();
    spatiallyIndexedOsmNodesInternalToPlanitLinks = new HashMap<MacroscopicPhysicalNetwork, Quadtree>();
  }
  
}

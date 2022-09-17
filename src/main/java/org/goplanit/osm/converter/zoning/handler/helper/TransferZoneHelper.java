package org.goplanit.osm.converter.zoning.handler.helper;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.osm.converter.network.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.OsmZoningHandlerProfiler;
import org.goplanit.osm.tags.OsmPtv1Tags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.util.Osm4JUtils;
import org.goplanit.osm.util.OsmBoundingAreaUtils;
import org.goplanit.osm.util.OsmModeUtils;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmPtVersionSchemeUtils;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.osm.util.OsmWayUtils;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.osm.util.PlanitTransferZoneUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.locale.DrivingDirectionDefaultByCountry;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneGroup;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Class to provide functionality for parsing transfer zones from OSM entities
 * 
 * @author markr
 *
 */
public class TransferZoneHelper extends ZoningHelperBase{
  
  /** logger to use */ 
  private static final Logger LOGGER = Logger.getLogger(TransferZoneHelper.class.getCanonicalName());
    
  /** the zoning to work on */
  private final Zoning zoning;
  
  /** zoning reader data used to track created entities */
  private final OsmZoningReaderData zoningReaderData;
      
  /** profiler to collect stats for */
  private final OsmZoningHandlerProfiler profiler;
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final OsmPublicTransportModeHelper publicTransportModeParser;
  
  /** parser functionality regarding the creation of PLANit connectoids from OSM entities */
  private final OsmConnectoidHelper connectoidParser;
  
  /** utilities for geographic information */
  private final PlanitJtsCrsUtils geoUtils;     
    
  /** Find links that can access the stop_location by the given mode. if location is on extreme node, we provide all links attached, otherwise only the
   * link on which the location resides
   * 
   * @param location stop_location
   * @param accessMode for stop_location (not used for filtering accessibility, only for lyaer identification)
   * @return links that can access the stop location.
   */
  private Collection<MacroscopicLink> getLinksWithAccessToLocationForMode(Point location, Mode accessMode) {
    /* If stop_location is situated on a one way road, or only has one way roads as incoming and outgoing roads, we identify if the eligible link segments 
     * lie on the wrong side of the road, i.e., would require passengers to cross the road to get to the stop position */
    MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(accessMode);
    OsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
    OsmNode osmNode =  layerData.getOsmNodeByLocation(location);
    
    /* links that can reach stop_location */
    Collection<MacroscopicLink> planitLinksToCheck = null;
    Node planitNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getPlanitNodeByLocation(location);
    if(planitNode != null) {        
      /* not internal to planit link, so regular match to planit node --> consider all incoming link segments as potentially usable  */
      planitLinksToCheck = planitNode.getLinks();
    }else {      
      /* not an extreme node, must be a node internal to a link up until now --> consider only link in question the location resides on */ 
      planitLinksToCheck = getNetworkToZoningData().getNetworkLayerData(networkLayer).findPlanitLinksWithInternalLocation(location);  
      if(planitLinksToCheck!=null){
        if(planitLinksToCheck.size()>1) {
          throw new PlanItRunTimeException("Location is internal to multiple planit links, should not happen %s", osmNode!=null ? "osm node "+osmNode.getId() : "");
        }                             
      }
    }
    return planitLinksToCheck;
  }

  /** Verify if the provided transfer zone supports more than a single stop_position. Transfer zones that reside next to the road
   * do, but point based transfer zones that reside on the road do not, because they are a stop_position and therefore only support that
   * stop_position.
   * 
   * @param transferZone to verify if supports multiple stop positions
   * @return true when supporting it, false otherwise
   */
  private boolean supportsMultipleStopPositions(TransferZone transferZone) {
    EntityType osmEntityType = PlanitTransferZoneUtils.transferZoneGeometryToOsmEntityType(transferZone.getGeometry());
    if(osmEntityType.equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(Long.valueOf(transferZone.getExternalId()))) {
        return false;
    }
    return true;    
  }  

  /** create a new but unpopulated transfer zone
   * 
   * @param transferZoneType of the zone
   * @return created transfer zone
   */
  private TransferZone createEmptyTransferZone(TransferZoneType transferZoneType) {
    
    /* create */
    TransferZone transferZone = zoning.getTransferZones().getFactory().createNew();
    /* type */
    transferZone.setType(transferZoneType);
    /* xml id = internal id */
    transferZone.setXmlId(String.valueOf(transferZone.getId()));
    
    profiler.logTransferZoneStatus(zoning.getTransferZones().size());
    return transferZone;
  }

  /** create a transfer zone based on the passed in osm entity, tags for feature extraction and access. Note that we attempt to also
   * parse its reference tags. Currently we look for keys:
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   *  to parse the reference for a transfer zone. If other keys are used, we are not (yet) able to pick them up.
   * 
   * @param osmEntity entity that is to be converted into a transfer zone
   * @param tags tags to extract features from
   * @param transferZoneType the type of the transfer zone 
   * @param geoUtils to use
   * @return transfer zone created
   */
  private TransferZone createAndPopulateTransferZone(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, PlanitJtsCrsUtils geoUtils){
    TransferZone transferZone = null;
        
    /* Verify if there are nodes missing before extracting geometry, if so and we are near bounding box log this information to user, but avoid logging the
     * regular feedback when nodes are missing, because it lacks context regarding being close to bounding box and would confuse the user */
    Level geometryExtractionLogLevel = LOGGER.getLevel();
    var osmNodeData = zoningReaderData.getOsmData().getOsmNodeData();
    if(Osm4JUtils.getEntityType(osmEntity).equals(EntityType.Way) && !OsmWayUtils.isAllOsmWayNodesAvailable((OsmWay)osmEntity,osmNodeData.getRegisteredOsmNodes())){
      Integer availableOsmNodeIndex = OsmWayUtils.findFirstAvailableOsmNodeIndexAfter(0,  (OsmWay) osmEntity, osmNodeData.getRegisteredOsmNodes());
      if(availableOsmNodeIndex!=null) {
        OsmNode referenceNode = osmNodeData.getRegisteredOsmNodes().get(((OsmWay) osmEntity).getNodeId(availableOsmNodeIndex));
        if(OsmBoundingAreaUtils.isNearNetworkBoundingBox(OsmNodeUtils.createPoint(referenceNode), getNetworkToZoningData().getNetworkBoundingBox(), geoUtils)) {
          LOGGER.info(String.format("OSM waiting area way (%d) geometry incomplete, network bounding box cut-off, truncated to available nodes",osmEntity.getId()));
          geometryExtractionLogLevel = Level.OFF;
        }
      }/*else {
        not a single node present, this implies entire transfer zone is outside of accepted bounding box, something which we could not verify until now
        in this case, we do not report back to user as this is most likely intended behaviour since bounding box was set by user explicitly
      }*/
    }
    
    /* geometry, either centroid location or polygon circumference */
    Geometry theGeometry = PlanitOsmUtils.extractGeometry(osmEntity, osmNodeData.getRegisteredOsmNodes(), geometryExtractionLogLevel);
    if(theGeometry != null && !theGeometry.isEmpty()) {
    
      /* create */
      transferZone = createEmptyTransferZone(transferZoneType);
      transferZone.setGeometry(theGeometry); 
      if(theGeometry instanceof Point) {
        transferZone.getCentroid().setPosition((Point) theGeometry);
      }
      
      /* XML id = internal id*/
      transferZone.setXmlId(Long.toString(osmEntity.getId()));
      /* external id  = osm node id*/
      transferZone.setExternalId(transferZone.getXmlId());
      
      /* name */
      if(tags.containsKey(OsmTags.NAME)) {
        transferZone.setName(tags.get(OsmTags.NAME));
      }

      /* ref (to allow other entities to refer to this transfer zone locally) */
      var refValues = OsmTagUtils.getValuesForSupportedRefKeys(tags);
      if(refValues!= null) {
        transferZone.addTransferZonePlatformNames(refValues);
      }

    }else {
      LOGGER.warning(String.format("Transfer zone not created, geometry incomplete (polygon, line string) for osm way %s, possibly nodes outside bounding box, or invalid OSM entity",osmEntity.getId()));
    }
        
    return transferZone;
  }

  /** create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param geoUtils to use
   * @return transfer zone created, null if something happened making it impossible to create the zone
   */
  private TransferZone createAndRegisterTransferZoneWithoutConnectoids(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, PlanitJtsCrsUtils geoUtils){
    /* create and register */
    TransferZone transferZone = createAndPopulateTransferZone(osmEntity,tags, transferZoneType, geoUtils);
    if(transferZone != null) {
      zoning.getTransferZones().register(transferZone);
      EntityType entityType = Osm4JUtils.getEntityType(osmEntity);
    
      /* register locally */
      zoningReaderData.getPlanitData().addTransferZoneByOsmId(entityType, osmEntity.getId(), transferZone);
    }
    return transferZone;
  }
  
  /** create a subset of transfer zones from the passed in ones, removing all transfer zones for which we can be certain they are located on the wrong side of the road infrastructure.
   * This is verified by checking if the stop_location resides on a one-way link. If so, we can be sure (based on the driving direction of the country) if a transfer zone is located on
   * the near or far side of the road, i.e., do people have to cross the road to egt to the stop position. If so, it is not eligible and we remove it, otherwise we keep it.
   * 
   * @param osmNode representing the stop location
   * @param transferZones to create subset for
   * @param osmModes eligible for the stop
   * @param geoUtils to use
   * @return subset of transfer zones
   */
  private Collection<TransferZone> removeTransferZonesOnWrongSideOfRoadOfStopLocation(OsmNode osmNode, Collection<TransferZone> transferZones, Collection<String> osmModes, PlanitJtsCrsUtils geoUtils) {
    Collection<TransferZone> matchedTransferZones = new HashSet<TransferZone>(transferZones);
    boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(zoningReaderData.getCountryName());
    
    /* If stop_location is situated on a one way road, or only has one way roads as incoming and outgoing roads, we exclude the matches that lie on the wrong side of the road, i.e.,
     * would require passengers to cross the road to get to the stop position */
    osmModes = OsmModeUtils.extractPublicTransportModesFrom(osmModes);
    for(String osmMode : osmModes) {
      Mode accessMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmMode);
      if(accessMode==null) {
        continue;
      }
            
      /* remove all link's that are not reachable without experiencing cross-traffic */
      for(TransferZone transferZone : transferZones) { 
        if(isTransferZoneOnWrongSideOfRoadOfStopLocation(OsmNodeUtils.createPoint(osmNode),transferZone, isLeftHandDrive, accessMode, geoUtils)) {
          LOGGER.fine(String.format(
              "DISCARD: Platform/pole %s matched on name to stop_position %d, but discarded based on placement on the wrong side of the road",transferZone.getExternalId(), osmNode.getId()));
          matchedTransferZones.remove(transferZone);
        }
      }
    }
    
    return matchedTransferZones;
  }

  /** Verify based on the stop_position location that is assumed to be located on earlier parsed road infrastructure, if the transfer zone is located
   * on an eligible side of the road. Meaning that the closest experienced driving direction of the nearby road is the logical one, i.e., when
   * transfer zone is on the left the closest driving direction should be left hand drive and vice versa.
   * 
   * @param location representation stop_location
   * @param transferZone representing waiting area
   * @param isLeftHandDrive is driving direction left hand drive
   * @param accessMode to verify
   * @param geoUtils to use
   * @return true when not on the wrong side, false otherwise
   */
  private boolean isTransferZoneOnWrongSideOfRoadOfStopLocation(Point location, TransferZone transferZone, boolean isLeftHandDrive, Mode accessMode, PlanitJtsCrsUtils geoUtils) {
    
    /* first collect links that can access the connectoid location */
    Collection<MacroscopicLink> planitLinksToCheck = getLinksWithAccessToLocationForMode(location, accessMode);
        
    /* remove all link's that are not reachable without experiencing cross-traffic from the perspective of the transfer zone*/
    if(planitLinksToCheck!=null){
      Collection<MacroscopicLink> accessibleLinks = ZoningConverterUtils.excludeLinksOnWrongSideOf(transferZone.getGeometry(), planitLinksToCheck, isLeftHandDrive, Collections.singleton(accessMode), geoUtils);
      if(accessibleLinks==null || accessibleLinks.isEmpty()) {
        /* all links experience cross-traffic, so not reachable */
        return true;
      }
    }
    
    /* reachable, not on wrong side */
    return false;    
    
  }

  /** Find out if transfer zone is mode compatible with the passed in reference OSM modes. Mode compatible means at least one overlapping
   * mode that is mapped to a PLANit mode.If the zone has no known modes, it is by definition not mode compatible. When one allows for pseudo compatibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param transferZone to verify
   * @param referenceOsmModes to macth against
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  private boolean isTransferZoneModeCompatible(TransferZone transferZone, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Collection<String> transferZoneSupportedModes = PlanitTransferZoneUtils.getRegisteredOsmModesForTransferZone(transferZone);
    if(transferZoneSupportedModes==null) {       
      /* zone has no known modes, not a trustworthy match */ 
      return false;
    } 
    
    /* check mode compatibility on extracted transfer zone supported modes*/
    return publicTransportModeParser.isModeCompatible(transferZoneSupportedModes, referenceOsmModes, allowPseudoMatches);    
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

   * @return found transfer zones that have been parsed before, null if no match is found
   */
  private Collection<TransferZone> findClosestTransferZonesByTagReference(
      OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> availableTransferZones, Collection<String> referenceOsmModes) {
    
    Map<String, TransferZone> foundTransferZones = null;
    /* ref value, can be a list of multiple values */
    List<String> refValues = OsmTagUtils.getValuesForSupportedRefKeys(tags);
    for(String osmNodeRefValue : refValues) {
      boolean multipleMatchesForSameRef = false;
      for(TransferZone transferZone : availableTransferZones) {
        if(!transferZone.hasPlatformNames()){
          continue;
        }

        /* refs are persisted as platform specific names within PLANit */
        List<String> tzRefValues = transferZone.getTransferZonePlatformNames();

        /* refs can comprise multiple entries */
        for(var tzRefValue : tzRefValues){
          if(osmNodeRefValue.equals(tzRefValue)) {
            /* match */
            if(foundTransferZones==null) {
              foundTransferZones = new HashMap<>();
            }

            /* inform user of tagging issues in case platform is not fully correctly mode mapped */
            if(PlanitTransferZoneUtils.getRegisteredOsmModesForTransferZone(transferZone)==null) {
              LOGGER.info(String.format("SALVAGED: Platform/pole (%s) referenced by stop_position (%s), matched although platform has no known mode support", transferZone.getExternalId(), osmNode.getId()));
            }else if(!isTransferZoneModeCompatible(transferZone, referenceOsmModes, true /* allow pseudo matches */)) {
              LOGGER.fine(String.format("Platform/pole (%s) referenced by stop_position (%s), but platform is not (pseudo) mode compatible with stop_position, ignore match", transferZone.getExternalId(), osmNode.getId()));
              continue;
            }

            TransferZone prevTransferZone = foundTransferZones.put(osmNodeRefValue,transferZone);
            if(prevTransferZone != null) {
              multipleMatchesForSameRef = true;
              /* choose closest of the two spatially */
              TransferZone closestZone = (TransferZone) OsmNodeUtils.findZoneClosest(osmNode, Set.of(prevTransferZone, transferZone), geoUtils);
              foundTransferZones.put(osmNodeRefValue,closestZone);
            }
          }
        }
      }
      if(multipleMatchesForSameRef == true ) {
        LOGGER.fine(String.format("SALVAGED: non-unique reference (%s) on stop_position %d, selected spatially closest platform/pole %s", osmNodeRefValue, osmNode.getId(),foundTransferZones.get(osmNodeRefValue).getExternalId()));
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
    
      Collection<TransferZone> nameAndModecompatibleZones = filterModeCompatibleTransferZones(referenceOsmModes, foundTransferZones, allowPseudoModeCompatibility);
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
   * @param stopAreaTransferZones the transfer zones of the stop_area this stop_position belongs to
   * @param referenceOsmModes the osm modes a transfer zone must at least contain one overlapping mapped mode from to be deemed accessible
   * @param geoUtils to use
   */
  private Collection<TransferZone> findAccessibleTransferZonesByReferenceOrName(
      OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> stopAreaTransferZones, Collection<String> referenceOsmModes, PlanitJtsCrsUtils geoUtils) {
    
    /* first try explicit reference matching to platform, i.e. transfer zone */
    Collection<TransferZone> matchedTransferZones = findClosestTransferZonesByTagReference(osmNode, tags, stopAreaTransferZones, referenceOsmModes);    
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
        Collection<TransferZone> potentialTransferZones = zoningReaderData.getPlanitData().getTransferZonesSpatially(
            OsmBoundingAreaUtils.createBoundingBox(osmNode, getSettings().getStopToWaitingAreaSearchRadiusMeters(), geoUtils));
        matchedTransferZones.retainAll(potentialTransferZones);
        
        /* filter based on illogical location; wrong side of the road */        
        matchedTransferZones = removeTransferZonesOnWrongSideOfRoadOfStopLocation(osmNode, matchedTransferZones, referenceOsmModes, geoUtils);                              
      }      
    }
    
    /* if multiple name matched transfer zones remain, select closest as most likely one */
    if(matchedTransferZones!= null) {
      if(matchedTransferZones.size()>1) {
        TransferZone foundTransferZone = (TransferZone) OsmNodeUtils.findZoneClosest(osmNode, matchedTransferZones, geoUtils);        
        matchedTransferZones = Collections.singleton(foundTransferZone);
      }
    }
    
    return matchedTransferZones;
  }

  /** find the closest and/or most likely transfer zone for the given osm node and its tags (with or without a reference
   * for additional information for mapping). Use the search radius from the settings to identify eligible transfer zones and then
   * use information on modes, references and spatial proximity to choose the most likely option. 
   * 
   * @param osmNode representing a stop position
   * @param tags of the node
   * @param referenceOsmModes the osm modes a transfer zone must at least contain one overlapping mapped mode from to be deemed accessible 
   * @return most likely transfer zone(s). Multiple matches only in case the node has multiple references to eligible transfer zones tagged
   */
  private Collection<TransferZone> findMostLikelyTransferZonesForStopPositionSpatially(OsmNode osmNode, Map<String, String> tags, Collection<String> referenceOsmModes) {
    TransferZone foundZone = null;
        
    /* collect potential transfer zones based on spatial search*/
    double searchRadiusMeters = getSettings().getStopToWaitingAreaSearchRadiusMeters();    
    Envelope searchArea = OsmBoundingAreaUtils.createBoundingBox(osmNode, searchRadiusMeters, geoUtils);    
    Collection<TransferZone> potentialTransferZones = zoningReaderData.getPlanitData().getTransferZonesSpatially(searchArea);
    
    if(potentialTransferZones==null || potentialTransferZones.isEmpty()) {
      LOGGER.fine(String.format("Unable to locate nearby transfer zone (search radius of %.2f (m)) when mapping stop position for osm node %d",searchRadiusMeters, osmNode.getId()));
      return null;
    }
    
    /* filter transfer zones that cannot be valid for additional stop_positions (if they have any already) */
    if( potentialTransferZones != null && !potentialTransferZones.isEmpty()){
        Iterator<TransferZone> iterator = potentialTransferZones.iterator();
        while(iterator.hasNext()) {
          TransferZone transferZone = iterator.next();
          if( zoningReaderData.getPlanitData().hasConnectoids(transferZone) && !supportsMultipleStopPositions(transferZone)) {
            iterator.remove();
          }          
        }      
    }
    
    /* find matches based on reference, name from given potential options... */
    Collection<TransferZone> matchedTransferZones = findAccessibleTransferZonesByReferenceOrName(osmNode, tags, potentialTransferZones, referenceOsmModes, geoUtils);         
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* no explicit reference or name match is found, we collect the closest mode compatible match */
      matchedTransferZones = filterModeCompatibleTransferZones(referenceOsmModes, potentialTransferZones, true);      
      foundZone =  (TransferZone) OsmNodeUtils.findZoneClosest(osmNode, matchedTransferZones, geoUtils);
      if(foundZone != null) {
        matchedTransferZones = Collections.singleton(foundZone);
      }
    }
     
        
    return matchedTransferZones;
  }

  /** Constructor 
   * 
   * @param zoning to use
   * @param zoningReaderData to use
   * @param transferSettings to use
   * @param profiler to use
   */
  public TransferZoneHelper(
      Zoning zoning, 
      OsmZoningReaderData zoningReaderData, 
      OsmPublicTransportReaderSettings transferSettings,  
      OsmZoningHandlerProfiler profiler) {
    
    super(transferSettings);
    
    this.zoningReaderData = zoningReaderData;
    this.zoning = zoning;
    this.profiler = profiler;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsCrsUtils(transferSettings.getReferenceNetwork().getCoordinateReferenceSystem());    
    
    /* parser for identifying, filtering etc. of PT PLANit modes from OSM entities */
    this.publicTransportModeParser = new OsmPublicTransportModeHelper(getNetworkToZoningData().getNetworkSettings());
    
    /* parser for identifying pt PLANit modes from OSM entities */
    this.connectoidParser = new OsmConnectoidHelper(zoning, zoningReaderData, transferSettings, profiler);
  }
  
  

  /** Find all transfer zones with at least one compatible mode (and PLANit mode mapped) based on the passed in reference osm modes
   * In case no eligible modes are provided (null), we allow any transfer zone with at least one valid mapped mode
   *  
   * @param eligibleOsmModes to map against (may be null)
   * @param potentialTransferZones to extract mode compatible transfer zones
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water mode, when false only exact matches are allowed
   * @return matched transfer zones
   */  
  public Set<TransferZone> filterModeCompatibleTransferZones(Collection<String> eligibleOsmModes, Collection<TransferZone> potentialTransferZones, boolean allowPseudoModeMatches) {
    Set<TransferZone> modeCompatibleTransferZones = new HashSet<TransferZone>();
    for(TransferZone transferZone : potentialTransferZones) {
      if(isTransferZoneModeCompatible(transferZone, eligibleOsmModes, allowPseudoModeMatches)) {
        modeCompatibleTransferZones.add(transferZone);
      }
    }    
    return modeCompatibleTransferZones;
  }

  /** Attempt to create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside. Find eligible access modes as input properties as well which can be used later
   * to map stop_positions more easily. Note that one can provide a default osm mode that is deemed eligible in case no tags are provided on the osm entity. In case no mode information
   * can be extracted a warning is issued but the transfer zone is still created because this is a tagging error and we might be able to salvage later on. If there are osm modes
   * but none of them are mapped, then we should not create the zone since it will not be of use.
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param defaultOsmMode to apply
   * @param geoUtils to use
   * @return transfer zone created, null if something happened making it impossible or not useful to create the zone
   */
  public TransferZone createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
      OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, String defaultOsmMode, PlanitJtsCrsUtils geoUtils){  
    
    TransferZone transferZone = null;
        
    /* tagged osm modes */        
    Pair<Collection<String>, Collection<Mode>> modeResult = publicTransportModeParser.collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(!OsmModeUtils.hasEligibleOsmMode(modeResult)) {
      /* no information on modes --> tagging issue, transfer zone might still be needed and could be salvaged based on close by stop_positions with additional information 
       * log issue, yet still create transfer zone (without any osm modes) */
      LOGGER.fine(String.format("SALVAGED: Transfer zone of type %s found for osm entity %d without osm mode support, likely tagging mistake",transferZoneType.name(), osmEntity.getId()));
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType, geoUtils);
    }else if(OsmModeUtils.hasMappedPlanitMode(modeResult)){  
      /* mapped planit modes are available and we should create the transfer zone*/
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType, geoUtils);
      PlanitTransferZoneUtils.registerOsmModesOnTransferZone(transferZone, modeResult.first());
    }else{
      /* waiting area with valid OSM mode, but not mapped to PLANit mode, mark as such to avoid logging a warning when this transfer zone is part of stop_area 
       * and it cannot be found when we try to collect it */
      zoningReaderData.getOsmData().addWaitingAreaWithoutMappedPlanitMode(Osm4JUtils.getEntityType(osmEntity),osmEntity.getId());
    }
    return transferZone;    
  }

  /** Attempt to create a new transfer zone and register it, do not create connectoids for it. Register the provided access modes as eligible by setting them on the input properties 
   * which can be used later to map stop_positions more easily.
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param eligibleOsmModes the eligible osm modes considered
   * @param geoUtils to use
   * @return transfer zone created, null if something happened making it impossible to create the zone
   */
  public TransferZone createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(
      OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, Collection<String> eligibleOsmModes, PlanitJtsCrsUtils geoUtils){
    TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM, geoUtils);
    if(transferZone != null) {
      PlanitTransferZoneUtils.registerOsmModesOnTransferZone(transferZone, eligibleOsmModes);
    }
    return transferZone;
  }

  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the OSM node. This is only relevant for very specific types
   * of OSM pt nodes, such as tram_stop, some bus_stops that are tagged on the road, and potentially halts and/or stations. In case no existing transfer zone in this
   * location exists, we create one first using the default transfer zone type provided, otherwise we utilise the existing transfer zone
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultOsmMode that is to be expected here
   * @param defaultTransferZoneType in case a transfer zone needs to be created in this location
   * @param geoUtils to use
   * @return created transfer zone (if not already in existence)
   */  
  public TransferZone createAndRegisterTransferZoneWithConnectoidsAtOsmNode(
      OsmNode osmNode, Map<String, String> tags, String defaultOsmMode, TransferZoneType defaultTransferZoneType, PlanitJtsCrsUtils geoUtils){        
        
    Pair<Collection<String>, Collection<Mode>> modeResult = publicTransportModeParser.collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, defaultOsmMode);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {    
      throw new PlanItRunTimeException("Should not attempt to parse osm node %d when no planit modes are activated for it", osmNode.getId());
    }
      
    /* transfer zone */
    TransferZone transferZone = zoningReaderData.getPlanitData().getTransferZoneByOsmId(EntityType.Node,osmNode.getId());
    if(transferZone == null) {
      /* not created for other layer; create and register transfer zone */
      transferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, defaultTransferZoneType, defaultOsmMode, geoUtils);
      if(transferZone == null) {
        throw new PlanItRunTimeException("Unable to create transfer zone for osm node %d",osmNode.getId());
      }
    }
    
    /* connectoid(s) */
    for(Mode mode : modeResult.second()) {
      MacroscopicNetworkLayer networkLayer = getSettings().getReferenceNetwork().getLayerByMode(mode);             
      
      /* we can immediately create connectoids since Ptv1 tram stop is placed on tracks and no Ptv2 tag is present */
      /* railway generally has no direction, so create connectoid for both incoming directions (if present), so we can service any tram line using the tracks */        
      connectoidParser.createAndRegisterDirectedConnectoidsOnTopOfTransferZone(transferZone, networkLayer, mode, geoUtils);      
    }    
    
    return transferZone;
  }

  /** Find the transfer zone(s) for a given stop_position, either the user overwritten mapping, or conditioned on mode,reference/name/spatially, or 
   * just the closest one in absence of eligible modes. In the special case the stop_position is in fact also the transfer zone and none is present we
   * create a new TransferZone
   *  
   * @param osmNode representing the stop_location
   * @param tags of the node
   * @param eligibleOsmModes eligible modes for the stop_location, may be null
   * @param transferZoneGroup the node belongs to, may be null
   * @return found transfer zone matches, can be multiple if multiple are serviced by the same stop position
   */
  public Collection<TransferZone> findTransferZonesForStopPosition(OsmNode osmNode, Map<String, String> tags, Collection<String> eligibleOsmModes, TransferZoneGroup transferZoneGroup) {
    Collection<TransferZone> matchedTransferZones = null;
    
    /* USER OVERWRITE */
    if(getSettings().isOverwriteStopLocationWaitingArea(osmNode.getId())) {
      
      /* do not search simply use provided waiting area (transfer zone) */
      Pair<EntityType, Long> result = getSettings().getOverwrittenStopLocationWaitingArea(osmNode.getId());
      TransferZone foundZone = zoningReaderData.getPlanitData().getTransferZoneByOsmId(result.first(), result.second());
      if(foundZone==null) {
        LOGGER.severe(String.format("User overwritten waiting area (platform, pole %d) for osm node %d, not available",result.second(), osmNode.getId()));
      }else {
        matchedTransferZones = Collections.singleton(foundZone);
        LOGGER.fine(String.format("Mapped stop_position %d to overwritten waiting area %d", osmNode.getId(),  result.second()));
      }
      
    }
    /* REGULAR SITUATION */
    else if(eligibleOsmModes != null && !eligibleOsmModes.isEmpty()){
      
      
      /* REFERENCE/NAME */
      if(transferZoneGroup != null) {
        /* when transfer zone group available, first search among those zones as they are more likely to be matching */
        matchedTransferZones = findAccessibleTransferZonesByReferenceOrName(osmNode, tags, transferZoneGroup.getTransferZones(), eligibleOsmModes, geoUtils);        
      }
    
      /* SPATIAL */
      if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
        /* no matches found yet, so either no transfer zone group present, or not known within based on reference/name match.
         * Either way we must still try to find the most likely match. We do so geographically and again based on mode, reference, name compatibility */
        matchedTransferZones = findMostLikelyTransferZonesForStopPositionSpatially(osmNode, tags, eligibleOsmModes);
      }   
      
      /* PTV1 ON ROAD/RAIL */
      if( (matchedTransferZones == null || matchedTransferZones.isEmpty()) && 
          OsmPtVersionSchemeUtils.isPtv2StopPositionPtv1Stop(osmNode, tags) &&
          hasNetworkLayersWithActiveOsmNode(osmNode.getId())) {
  
        /* no potential transfer zones AND Ptv1 tagged (bus_stop, station, halt, trams_stop), meaning that while we tried to match to
         * separate waiting area, none is present. Instead, we accept the stop_location is in fact also the waiting area and we create a
         * transfer zone in this location as well */
        TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmNode, tags, TransferZoneType.PLATFORM, eligibleOsmModes, geoUtils);          
        if(transferZone== null) {
          LOGGER.fine(String.format("Unable to convert stop_location %d residing on road infrastucture into a transfer zone for modes %s",osmNode.getId(), eligibleOsmModes.toString()));
        }else {
          if(OsmPtv1Tags.isBusStop(tags)){
            /* halt and tram_stop are common and valid to be located on road infrastructure without platform, so never log that situation */
            LOGGER.fine(String.format("SALVAGED: process Ptv2 stop_position %d as Ptv1 tag representing both stop and waiting area in one for modes %s",osmNode.getId(), eligibleOsmModes.toString()));
          }
          matchedTransferZones = Collections.singleton(transferZone);
        }
      }      
      
    }
    /* NO MODES KNOWN */
    else {
      /* eligible modes unknown, we can therefore we try to salvage by selecting the closest transfer zone and adopt those modes */   
      TransferZone foundZone =  (TransferZone) OsmNodeUtils.findZoneClosest(osmNode, transferZoneGroup.getTransferZones(), getSettings().getStopToWaitingAreaSearchRadiusMeters(), geoUtils);
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
   * @return found transfer zone matches
   */
  public Collection<TransferZone> findTransferZonesForStopPosition(OsmNode osmNode, Map<String, String> tags, Collection<String> eligibleOsmModes) {
    return findTransferZonesForStopPosition(osmNode, tags, eligibleOsmModes, null);
  }   
  
}

package org.planit.osm.converter.zoning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.planit.osm.converter.network.PlanitOsmNetworkHandlerHelper;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderData;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderLayerData;
import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.planit.graph.listener.SyncDirectedEdgeXmlIdsToInternalIdOnBreakEdge;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.graph.modifier.BreakEdgeListener;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.LinkSegment;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;
import org.planit.zoning.listener.UpdateConnectoidsOnBreakLink;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Base Handler for all zoning handlers. Contains shared functionality that is used across the different zoning handlers 
 * 
 * @author markr
 * 
 *
 */
public abstract class PlanitOsmZoningBaseHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningBaseHandler.class.getCanonicalName());
          
  // references
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final PlanitOsmPublicTransportReaderSettings transferSettings;   
  
  /** network2ZoningData data collated from parsing network required to successfully popualte the zoning */
  private final PlanitOsmNetworkToZoningReaderData network2ZoningData;
  
  /** holds, add to all the tracking data required for parsing zones */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** profiler for this reader */
  private final PlanitOsmZoningHandlerProfiler profiler;   
    
  /** Verify if passed in tags reflect transfer based infrastructure that is eligible (and supported) to be parsed by this class, e.g.
   * tags related to original PT scheme stops ( railway=halt, railway=tram_stop, highway=bus_stop and highway=platform),
   * or the current v2 PT scheme (public_transport=stop_position, platform, station, stop_area)
   * 
   * @param tags
   * @return which scheme it is compatible with, NONE if none could be found
   */
  private static OsmPtVersionScheme isTransferBasedOsmInfrastructure(Map<String, String> tags) {
    if(PlanitOsmUtils.isCompatibleWith(OsmPtVersionScheme.VERSION_2, tags)){
      return OsmPtVersionScheme.VERSION_2;
    }else if(PlanitOsmUtils.isCompatibleWith(OsmPtVersionScheme.VERSION_1,tags)) {
      return OsmPtVersionScheme.VERSION_1;
    }
    return OsmPtVersionScheme.NONE;
  }  
  
  /** skip osm pt entity when marked for exclusion in settings
   * 
   * @param type of entity to verify
   * @param osmId id to verify
   * @return true when it should be skipped, false otherwise
   */  
  private boolean skipOsmPtEntity(EntityType entityType, long osmId) {
    return 
        (entityType.equals(EntityType.Node) && getSettings().isExcludedOsmNode(osmId)) 
        ||
        (entityType.equals(EntityType.Way) && getSettings().isExcludedOsmWay(osmId)); 
  }    
  
  /** find out if link osmModesToCheck are compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode. When one allows for pseudo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param referenceOsmModes to map against (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  private boolean isModeCompatible(Collection<String> osmModesToCheck, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    /* collect compatible modes */
    Collection<String> overlappingModes = PlanitOsmModeUtils.getCompatibleModes(osmModesToCheck, referenceOsmModes, allowPseudoMatches);    
    
    /* only proceed when there is a valid mapping based on overlapping between reference modes and zone modes, while in absence
     * of reference osm modes, we trust any nearby zone with mapped mode */
    if(getNetworkToZoningData().getNetworkSettings().hasAnyMappedPlanitMode(overlappingModes)) {
      /* no overlapping mapped modes while both have explicit osm modes available, not a match */
      return true;
    }
    return false;    

  }   
  
  /** create a new but unpopulated transfer zone
   * 
   * @param transferZoneType of the zone
   * @return created transfer zone
   */
  private TransferZone createEmptyTransferZone(TransferZoneType transferZoneType) {
    /* create */
    TransferZone transferZone = getZoning().transferZones.createNew();
    /* type */
    transferZone.setType(transferZoneType);
    /* xml id = internal id */
    transferZone.setXmlId(String.valueOf(transferZone.getId()));
    
    getProfiler().logTransferZoneStatus(getZoning().transferZones.size());
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
   * @throws PlanItException thrown if error
   */
  private TransferZone createAndPopulateTransferZone(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    TransferZone transferZone = null;
    
    /* first verify is there are nodes missing before extracting geometry, if so and we are near bounding box log this information to user, but avoid logging the
     * regular feedback when nodes are missing, because it lacks context regarding being close to bounding box and would confuse the user */
    Level geometryExtractionLogLevel = LOGGER.getLevel();
    if(Osm4JUtils.getEntityType(osmEntity).equals(EntityType.Way) && !PlanitOsmWayUtils.isAllOsmWayNodesAvailable((OsmWay)osmEntity, getNetworkToZoningData().getOsmNodes())){
      int availableOsmNodeIndex = PlanitOsmWayUtils.findFirstAvailableOsmNodeIndexAfter(0,  (OsmWay) osmEntity, getNetworkToZoningData().getOsmNodes());
      OsmNode referenceNode = getNetworkToZoningData().getOsmNodes().get(((OsmWay) osmEntity).getNodeId(availableOsmNodeIndex));
      if(isNearNetworkBoundingBox(PlanitOsmNodeUtils.createPoint(referenceNode), geoUtils)) {
        LOGGER.info(String.format("osm waiting area way (%d) geometry incomplete due to bounding box cut-off, truncated to available nodes",osmEntity.getId()));
        geometryExtractionLogLevel = Level.OFF;
      }
    }
    
    /* geometry, either centroid location or polygon circumference */
    Geometry theGeometry = PlanitOsmUtils.extractGeometry(osmEntity, getNetworkToZoningData().getOsmNodes(), geometryExtractionLogLevel);
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
      
      String refValue = OsmTagUtils.getValueForSupportedRefKeys(tags);
      /* ref (to allow other entities to refer to this transfer zone locally) */
      if(refValue != null) {
        transferZone.addInputProperty(OsmTags.REF, refValue);
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
   * @return transfer zone created, null if something happenned making it impossible to create the zone
   * @throws PlanItException thrown if error
   */
  private TransferZone createAndRegisterTransferZoneWithoutConnectoids(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* create and register */
    TransferZone transferZone = createAndPopulateTransferZone(osmEntity,tags, transferZoneType, geoUtils);
    if(transferZone != null) {
      getZoning().transferZones.register(transferZone);
      EntityType entityType = Osm4JUtils.getEntityType(osmEntity);
    
      /* register locally */
      getZoningReaderData().getPlanitData().addTransferZoneByOsmId(entityType, osmEntity.getId(), transferZone);
    }
    return transferZone;
  }    
  
  /** check if geometry is near network bounding box
   * @param geometry to check
   * @param geoUtils to use
   * @return truw when near, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean isNearNetworkBoundingBox(Geometry geometry, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    return geoUtils.isGeometryNearBoundingBox(geometry, getNetworkToZoningData().getNetworkBoundingBox(), PlanitOsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS);
  }   
  
  /** log the given warning message but only when it is not too close to the bounding box, because then it is too likely that it is discarded due to missing
   * infrastructure or other missing assets that could not be parsed fully as they pass through the bounding box barrier. Therefore the resulting warning message is likely 
   * more confusing than helpful in those situation and is therefore ignored
   * 
   * @param message to log if not too close to bounding box
   * @param geometry to determine distance to bounding box to
   * @parma logger to log on
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  protected void logWarningIfNotNearBoundingBox(String message, Geometry geometry, Logger logger, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    if(!isNearNetworkBoundingBox(geometry, geoUtils)) {
      logger.warning(message);
    }
  }    
  
  /** skip osm relation member when marked for exclusion in settings
   * 
   * @param member to verify
   * @return true when it should be skipped, false otherwise
   */
  protected boolean skipOsmPtEntity(OsmRelationMember member) {
    return skipOsmPtEntity(member.getType(), member.getId()); 
  }  

  /** skip osm node when marked for exclusion in settings
   * 
   * @param osmNode to verify
   * @return true when it should be skipped, false otherwise
   */  
  protected boolean skipOsmNode(OsmNode osmNode) {
    return skipOsmPtEntity(EntityType.Node, osmNode.getId());
  }
  
  /** skip osm way when marked for exclusion in settings
   * 
   * @param osmWay to verify
   * @return true when it should be skipped, false otherwise
   */  
  protected boolean skipOsmWay(OsmWay osmWay) {
    return skipOsmPtEntity(EntityType.Way, osmWay.getId());
  } 
  
  /** collect the pt modes both osm and mapped planit modes for a pt entity. If no default mode is found based on the tags, a default mode may be 
   * provided explicitly by the user which will then be added to the osm mode
   * 
   * @param osmPtEntityId to use
   * @param tags of the osm entity
   * @return pair containing eligible osm modes identified and their mapped planit counterparts
   */
  public Pair<Collection<String>, Collection<Mode>> collectPublicTransportModesFromPtEntity(long osmPtEntityId, Map<String, String> tags, String defaultMode) {    
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmPublicTransportModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModes(eligibleOsmModes);      
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
  
  /** collect the eligible modes both osm and mapped planit modes for the given osm entity representing pt infrastructure. If no default mode is found based on the tags, a default mode may be 
   * provided explicitly by the user which will then be added to the osm mode
   * 
   * @param osmPtEntityId to use
   * @param tags of the osm entity
   * @return pair containing eligible osm modes identified and their mapped planit counterparts
   */
  public Pair<Collection<String>, Collection<Mode>> collectModesFromPtEntity(long osmPtEntityId, Map<String, String> tags, String defaultMode) {
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = getNetworkToZoningData().getNetworkSettings().getMappedPlanitModes(eligibleOsmModes);      
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a planit link
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {    
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(osmNodeId);
    if(osmNode != null) {
      for(InfrastructureLayer networkLayer : transferSettings.getReferenceNetwork().infrastructureLayers) {        
        if(getNetworkToZoningData().getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }   
  
  /** Find the link segments that are accessible for the given acces link, node, mode combination taking into account the relative location of the transfer zone if needed and
   * mode compatibility.
   * 
   * @param transferZone these link segments pertain to
   * @param accessLink that is nominated
   * @param node extreme node of the link
   * @param accessMode eligible access mode
   * @param mustAvoidCrossingTraffic indicates of transfer zone must be on the logical side of the road or if it does not matter
   * @param geoUtils to use
   * @return found link segments that are deemed valid given the constraints
   * @throws PlanItException thrown if error
   */
  protected Collection<EdgeSegment> findAccessLinkSegmentsForStandAloneTransferZone(
      TransferZone transferZone, Link accessLink, Node node, Mode accessMode, boolean mustAvoidCrossingTraffic, PlanitJtsCrsUtils geoUtils) throws PlanItException {
        
    Long osmWaitingAreaId = Long.valueOf(transferZone.getExternalId());
    Long osmNodeIdOfLinkExtremeNode = node.getExternalId()!= null ? Long.valueOf(node.getExternalId()) : null;
    EntityType osmWaitingAreaEntityType = PlanitOsmZoningHandlerHelper.getOsmEntityType(transferZone);
    
    /* potential link segments based on mode compatibility and access link restriction */ 
    Collection<EdgeSegment> accessLinkSegments = new ArrayList<EdgeSegment>(4);
    for(EdgeSegment linkSegment : node.getEntryEdgeSegments()) {
      if( ((MacroscopicLinkSegment)linkSegment).isModeAllowed(accessMode) && (linkSegment.getParentEdge().idEquals(accessLink))){      
        accessLinkSegments.add(linkSegment);
      }
    }  
    
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      return accessLinkSegments;
    }
        
    /* user overwrite checks and special treatment */
    boolean removeInvalidAccessLinkSegmentsIfNoMatchLeft = true;
    {
      /* in both cases: When a match, we must use the user overwrite value. We will still try to remove access link segments
       * that are invalid, but if due to this check no matches remain, we revert this and instead use all entry link segments on the osm way
       * since the user has indicated to explicitly use this combination which overrules the automatic filter we would ordinarily apply */
          
      /* stopLocation -> waiting area overwrite */
      if(node.getExternalId()!=null && getSettings().isOverwriteStopLocationWaitingArea(osmNodeIdOfLinkExtremeNode)) {      
        Pair<EntityType, Long> result = getSettings().getOverwrittenStopLocationWaitingArea(osmNodeIdOfLinkExtremeNode);      
        removeInvalidAccessLinkSegmentsIfNoMatchLeft = osmWaitingAreaId == result.second();
      }    
      /* waiting area -> osm way (stop_location) overwrite */
      else if (getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmWaitingAreaId, osmWaitingAreaEntityType)) {        
        long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmWaitingAreaId, osmWaitingAreaEntityType);
        removeInvalidAccessLinkSegmentsIfNoMatchLeft = !(Long.valueOf(accessLink.getExternalId()).equals(osmWayId));
      }
    }
  
    /* accessible link segments for planit node based on relative location of waiting area compared to infrastructure*/    
    if(mustAvoidCrossingTraffic) { 
                          
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(getZoningReaderData().getCountryName());
      Collection<EdgeSegment> toBeRemoveAccessLinkSegments = 
          PlanitOsmZoningHandlerHelper.identifyInvalidTransferZoneAccessLinkSegmentsBasedOnRelativeLocationToInfrastructure(accessLinkSegments, transferZone, accessMode, isLeftHandDrive, geoUtils);
      
      if(removeInvalidAccessLinkSegmentsIfNoMatchLeft || toBeRemoveAccessLinkSegments.size() < accessLinkSegments.size()) {
        /* filter because "normal" situation or there are still matches left even after filtering despite the explicit user override for this  combination */
        accessLinkSegments.removeAll(toBeRemoveAccessLinkSegments);
      }
      /* else  keep the access link segments to far */
    }
    return accessLinkSegments;
  }

  /** find out if transfer zone is mode compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode.If the zone has no known modes, it is by definition not mode compatible. When one allows for psuedo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param transferZone to verify
   * @param referenceOsmModes to macth against
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  protected boolean isTransferZoneModeCompatible(TransferZone transferZone, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Collection<String> transferZoneSupportedModes = PlanitOsmZoningHandlerHelper.getEligibleOsmModesForTransferZone(transferZone);
    if(transferZoneSupportedModes==null) {       
      /* zone has no known modes, not a trustworthy match */ 
      return false;
    } 
    
    /* check mode compatibility on extracted transfer zone supported modes*/
    return isModeCompatible(transferZoneSupportedModes, referenceOsmModes, allowPseudoMatches);    
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
   * @throws PlanItException thrown if error
   */
  protected Collection<TransferZone> removeTransferZonesOnWrongSideOfRoadOfStopLocation(OsmNode osmNode, Collection<TransferZone> transferZones, Collection<String> osmModes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = new HashSet<TransferZone>(transferZones);
    boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(getZoningReaderData().getCountryName());
    
    /* If stop_location is situated on a one way road, or only has one way roads as incoming and outgoing roads, we exclude the matches that lie on the wrong side of the road, i.e.,
     * would require passengers to cross the road to get to the stop position */
    osmModes = PlanitOsmModeUtils.getPublicTransportModesFrom(osmModes);
    for(String osmMode : osmModes) {
      Mode accessMode = getNetworkToZoningData().getNetworkSettings().getMappedPlanitMode(osmMode);
      if(accessMode==null) {
        continue;
      }
            
      /* remove all link's that are not reachable without experiencing cross-traffic */
      for(TransferZone transferZone : transferZones) { 
        if(isTransferZoneOnWrongSideOfRoadOfStopLocation(PlanitOsmNodeUtils.createPoint(osmNode),transferZone, isLeftHandDrive, accessMode, geoUtils)) {
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
   * @throws PlanItException thrown if error
   */
  protected boolean isTransferZoneOnWrongSideOfRoadOfStopLocation(Point location, TransferZone transferZone, boolean isLeftHandDrive, Mode accessMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    /* first collect links that can access the connectoid location */
    Collection<Link> planitLinksToCheck = getLinksWithAccessToConnectoidLocation(location, accessMode);
        
    /* remove all link's that are not reachable without experiencing cross-traffic from the perspective of the transfer zone*/
    if(planitLinksToCheck!=null){
      Collection<Link> accessibleLinks = PlanitOsmZoningHandlerHelper.removeLinksOnWrongSideOf(transferZone.getGeometry(), planitLinksToCheck, isLeftHandDrive, Collections.singleton(accessMode), geoUtils);
      if(accessibleLinks==null || accessibleLinks.isEmpty()) {
        /* all links experience cross-traffic, so not reachable */
        return true;
      }
    }
    
    /* reachable, not on wrong side */
    return false;    
    
  }    
  
  /** Find links that can access the stop_location by the given mode. if location is on extreme node, we provide all links attached, otherwise only the
   * link on which the location resides
   * 
   * @param location stop_location
   * @param accessMode for stop_location (not used for filteraing accessibility, only for lyaer identification)
   * @return links that can access the stop location.
   * @throws PlanItException
   */
  protected Collection<Link> getLinksWithAccessToConnectoidLocation(Point location, Mode accessMode) throws PlanItException {
    /* If stop_location is situated on a one way road, or only has one way roads as incoming and outgoing roads, we identify if the eligible link segments 
     * lie on the wrong side of the road, i.e., would require passengers to cross the road to get to the stop position */
    MacroscopicPhysicalNetwork networkLayer = transferSettings.getReferenceNetwork().infrastructureLayers.get(accessMode);
    PlanitOsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
    OsmNode osmNode =  layerData.getOsmNodeByLocation(location);
    
    /* links that can reach stop_location */
    Collection<Link> planitLinksToCheck = null;
    Node planitNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getPlanitNodeByLocation(location);
    if(planitNode != null) {        
      /* not internal to planit link, so regular match to planit node --> consider all incoming link segments as potentially usable  */
      planitLinksToCheck = planitNode.<Link>getLinks();              
    }else {      
      /* not an extreme node, must be a node internal to a link up until now --> consider only link in question the location resides on */ 
      planitLinksToCheck = getNetworkToZoningData().getNetworkLayerData(networkLayer).findPlanitLinksWithInternalLocation(location);  
      if(planitLinksToCheck!=null){
        if(planitLinksToCheck.size()>1) {
          throw new PlanItException("location is internal to multiple planit links, should not happen %s", osmNode!=null ? "osm node "+osmNode.getId() : "");  
        }                             
      }
    }
    return planitLinksToCheck;
  }

  /** find out if link is mode compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode. If the zone has no known modes, it is by definition not mode compatible. 
   * When one allows for pseudo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param referenceOsmModes to map agains (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  protected boolean isLinkModeCompatible(Link link, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Collection<String> osmLinkModes = new HashSet<String>(); 
    if(link.hasEdgeSegmentAb()) {      
      Collection<Mode> planitModes = ((MacroscopicLinkSegment)link.getEdgeSegmentAb()).getLinkSegmentType().getAvailableModes();
      osmLinkModes.addAll(getNetworkToZoningData().getNetworkSettings().getMappedOsmModes(planitModes));
    }
    if(link.hasEdgeSegmentBa()) {      
      Collection<Mode> planitModes = ((MacroscopicLinkSegment)link.getEdgeSegmentBa()).getLinkSegmentType().getAvailableModes();
      osmLinkModes.addAll(getNetworkToZoningData().getNetworkSettings().getMappedOsmModes(planitModes));
    }
    if(osmLinkModes==null || osmLinkModes.isEmpty()) {
      return false;
    }
    
    /* check mode compatibility on extracted link supported modes*/
    return isModeCompatible(osmLinkModes, referenceOsmModes, allowPseudoMatches);
  }  
                                                          

  /** verify if tags represent an infrastructure used for transfers between modes, for example PT platforms, stops, etc. 
   * and is also activated for parsing based on the related settings
   * 
   * @param tags to verify
   * @return which scheme it is compatible with, NONE if none could be found or if it is not active 
   */  
  protected OsmPtVersionScheme isActivatedTransferBasedInfrastructure(Map<String, String> tags) {
    if(transferSettings.isParserActive()) {
      return isTransferBasedOsmInfrastructure(tags);
    }
    return OsmPtVersionScheme.NONE;
  }  
  
   
  /** update an existing directed connectoid with new access zone and allowed modes. In case the link segment does not have any of the 
   * passed in modes listed as allowed, the connectoid is not updated with these modes for the given access zone as it would not be possible to utilise it. 
   * 
   * @param accessZone to relate connectoids to
   * @param allowedModes to add to the connectoid for the given access zone
   */  
  protected void updateDirectedConnectoid(DirectedConnectoid connectoidToUpdate, TransferZone accessZone, Set<Mode> allowedModes) {    
    final Set<Mode> realAllowedModes = ((MacroscopicLinkSegment)connectoidToUpdate.getAccessLinkSegment()).getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      if(!connectoidToUpdate.hasAccessZone(accessZone)) {
        connectoidToUpdate.addAccessZone(accessZone);
      }
      connectoidToUpdate.addAllowedModes(accessZone, realAllowedModes);   
    }
  }  
  
  /** create a dummy transfer zone without access to underlying osmNode or way and without any geometry or populated
   * content other than its ids and type
   * 
   * @param osmId to use
   * @param transferZoneType to use
   * @return created transfer zone
   */
  protected TransferZone createDummyTransferZone(long osmId, TransferZoneType transferZoneType) {
    TransferZone transferZone = createEmptyTransferZone(transferZoneType);
    transferZone.setXmlId(String.valueOf(osmId));
    transferZone.setExternalId(transferZone.getXmlId());
    return transferZone;
  }   
    
  /** attempt to create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
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
   * @throws PlanItException thrown if error
   */
  protected TransferZone createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
      OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, String defaultOsmMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {  
    
    TransferZone transferZone = null;
        
    /* tagged osm modes */        
    Pair<Collection<String>, Collection<Mode>> modeResult = collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(!PlanitOsmZoningHandlerHelper.hasEligibleOsmMode(modeResult)) {
      /* no information on modes --> tagging issue, transfer zone might still be needed and could be salvaged based on close by stop_positions with additional information 
       * log issue, yet still create transfer zone (without any osm modes) */
      LOGGER.fine(String.format("SALVAGED: Transfer zone of type %s found for osm entity %d without osm mode support, likely tagging mistake",transferZoneType.name(), osmEntity.getId()));
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType, geoUtils);
    }else if(PlanitOsmZoningHandlerHelper.hasMappedPlanitMode(modeResult)){  
      /* mapped planit modes are available and we should create the transfer zone*/
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType, geoUtils);
      PlanitOsmZoningHandlerHelper.addOsmAccessModesToTransferZone(transferZone, modeResult.first());
    }else{
      /* waiting area with valid osm mode, but not mapped to planit mode, mark as such to avoid logging a warning when this transfer zone is part of stop_area 
       * and it cannot be found when we try to collect it */
      getZoningReaderData().getOsmData().addWaitingAreaWithoutMappedPlanitMode(Osm4JUtils.getEntityType(osmEntity),osmEntity.getId());
    }
    return transferZone;    
  }  
  
  /** attempt to create a new transfer zone and register it, do not create connectoids for it. Register the provided access modes as eligible by setting them on the input properties 
   * which can be used later to map stop_positions more easily.
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param geoUtils to use
   * @return transfer zone created, null if something happenned making it impossible to create the zone
   * @throws PlanItException thrown if error
   */
  protected TransferZone createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(
      OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, Collection<String> eligibleOsmModes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM, geoUtils);
    if(transferZone != null) {
      PlanitOsmZoningHandlerHelper.addOsmAccessModesToTransferZone(transferZone, eligibleOsmModes);
    }
    return transferZone;
  }  
  
  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the osm node. This is only relevant for very specific types
   * of osm pt nodes, such as tram_stop, some bus_stops that are tagged on the road, and potentially halts and/or stations. since we assume this is in the context of
   * the existence of Ptv1 tags, we utilise the Ptv1 tags to extract the correct transfer zone type in case the transfer zone does not yet exist in this location.
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultOsmMode to create it for
   * @param geoUtils to use
   * @return created transfer zone (if not already in existence)
   * @throws PlanItException thrown if error
   */
  protected TransferZone createAndRegisterPtv1TransferZoneWithConnectoidsAtOsmNode(
      OsmNode osmNode, Map<String, String> tags, String defaultOsmMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    TransferZoneType ptv1TransferZoneType = PlanitOsmZoningHandlerHelper.getPtv1TransferZoneType(osmNode, tags);
    return createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmNode, tags, defaultOsmMode, ptv1TransferZoneType, geoUtils);    
  }

  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the osm node. This is only relevant for very specific types
   * of osm pt nodes, such as tram_stop, some bus_stops that are tagged on the road, and potentially halts and/or stations. In case no existing transfer zone in this
   * location exists, we create one first using the default transfer zone type provided, otherwise we utilise the existing transfer zone
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultOsmMode that is to be expected here
   * @param defaultTransferZoneType in case a transfer zone needs to be created in this location
   * @param geoUtils to use
   * @return created transfer zone (if not already in existence)
   * @throws PlanItException thrown if error
   */  
  protected TransferZone createAndRegisterTransferZoneWithConnectoidsAtOsmNode(
      OsmNode osmNode, Map<String, String> tags, String defaultOsmMode, TransferZoneType defaultTransferZoneType, PlanitJtsCrsUtils geoUtils) throws PlanItException {        
        
    Pair<Collection<String>, Collection<Mode>> modeResult = collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, defaultOsmMode);
    if(!PlanitOsmZoningHandlerHelper.hasMappedPlanitMode(modeResult)) {    
      throw new PlanItException("Should not attempt to parse osm node %d when no planit modes are activated for it", osmNode.getId());
    }
      
    /* transfer zone */
    TransferZone transferZone = getZoningReaderData().getPlanitData().getTransferZoneByOsmId(EntityType.Node,osmNode.getId());
    if(transferZone == null) {
      /* not created for other layer; create and register transfer zone */
      transferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, defaultTransferZoneType, defaultOsmMode, geoUtils);
      if(transferZone == null) {
        throw new PlanItException("Unable to create transfer zone for osm node %d",osmNode.getId());
      }
    }
    
    /* connectoid(s) */
    for(Mode mode : modeResult.second()) {
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(mode);             
      
      /* we can immediately create connectoids since Ptv1 tram stop is placed on tracks and no Ptv2 tag is present */
      /* railway generally has no direction, so create connectoid for both incoming directions (if present), so we can service any tram line using the tracks */        
      createAndRegisterDirectedConnectoidsOnTopOfTransferZone(transferZone, networkLayer, mode, geoUtils);      
    }    
    
    return transferZone;
  }

  /** create directed connectoid for the link segment provided, all related to the given transfer zone and with access modes provided. When the link segment does not have any of the 
   * passed in modes listed as allowed, no connectoid is created and null is returned
   * 
   * @param accessZone to relate connectoids to
   * @param linkSegment to create connectoid for
   * @param allowedModes used for the connectoid
   * @return created connectoid when at least one of the allowed modes is also allowed on the link segment
   * @throws PlanItException thrown if error
   */
  protected DirectedConnectoid createAndRegisterDirectedConnectoid(final TransferZone accessZone, final MacroscopicLinkSegment linkSegment, final Set<Mode> allowedModes) throws PlanItException {
    final Set<Mode> realAllowedModes = linkSegment.getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      DirectedConnectoid connectoid = zoning.transferConnectoids.registerNew(linkSegment,accessZone);
      
      /* xml id = internal id */
      connectoid.setXmlId(String.valueOf(connectoid.getId()));
      
      /* link connectoid to zone and register modes for access*/
      connectoid.addAllowedModes(accessZone, realAllowedModes);   
      return connectoid;
    }
    return null;
  }   
  
  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with access modes provided. connectoids are only created
   * when the access link segment has at least one of the allowed modes as an eligible mode
   * 
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @return created connectoids
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(final TransferZone transferZone, final MacroscopicPhysicalNetwork networkLayer, final Collection<? extends EdgeSegment> linkSegments, final Set<Mode> allowedModes) throws PlanItException {
    Set<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(EdgeSegment linkSegment : linkSegments) {
      DirectedConnectoid newConnectoid = createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes);
      if(newConnectoid != null) {
        createdConnectoids.add(newConnectoid);
        
        /* update planit data tracking information */ 
        /* 1) index by access link segment's downstream node location */
        zoningReaderData.getPlanitData().addDirectedConnectoidByLocation(networkLayer, newConnectoid.getAccessLinkSegment().getDownstreamVertex().getPosition() ,newConnectoid);
        /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
        zoningReaderData.getPlanitData().addConnectoidByTransferZone(transferZone, newConnectoid);
      }
    }         
    
    return createdConnectoids;
  }
  
  /** Create directed connectoids for transfer zones that reside on osw ways. For such transfer zones, we simply create connectoids in both directions for all eligible incoming 
   * link segments. This is a special case because due to residing on the osm way it is not possible to distinguish what intended direction of the osm way is serviced (it is neither
   * left nor right of the way). Therefore any attempt to extract this information is bypassed here.
   * 
   * @param transferZone residing on an osm way
   * @param networkLayer related to the mode
   * @param planitMode the connectoid is accessible for
   * @param geoUtils to use
   * @return created connectoids, null if it was not possible to create any due to some reason
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoidsOnTopOfTransferZone(
      TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* collect the osmNode for this transfer zone */
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(Long.valueOf(transferZone.getExternalId()));
    
    Collection<? extends EdgeSegment> nominatedLinkSegments = null;
    if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node)) {
      /* user overwrite */
      
      long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node);
      Link nominatedLink = PlanitOsmZoningHandlerHelper.getClosestLinkWithOsmWayIdToGeometry( osmWayId, PlanitOsmNodeUtils.createPoint(osmNode), networkLayer, geoUtils);
      if(nominatedLink != null) {
        nominatedLinkSegments = nominatedLink.getEdgeSegments(); 
      }else {
        LOGGER.severe(String.format("User nominated osm way not available for waiting area on road infrastructure %d",osmWayId));
      }                      
      
    }else {
      /* regular approach */
      
      /* create/collect planit node with access link segment */
      Node planitNode = extractConnectoidAccessNodeByOsmNode(osmNode, networkLayer);
      if(planitNode == null) {
        LOGGER.warning(String.format("DISCARD: osm node (%d) could not be converted to access node for transfer zone osm entity %s at same location",osmNode.getId(), transferZone.getExternalId()));
        return null;
      }
      
      nominatedLinkSegments = planitNode.getEntryLinkSegments();
    }
    
    
    
    /* connectoid(s) */
        
    /* create connectoids on top of transfer zone */
    /* since located on osm way we cannot deduce direction of the stop, so create connectoid for both incoming directions (if present), so we can service any line using the way */        
    return createAndRegisterDirectedConnectoids(transferZone, networkLayer, nominatedLinkSegments, Collections.singleton(planitMode));    
  }  
  
  /** process an osm entity that is classified as a (train) station. For this to register on the group, we only see if we can utilise its name and use it for the group, but only
   * if the group does not already have a name
   *   
   * @param transferZoneGroup the osm station relates to 
   * @param osmEntityStation of the relation to process
   * @param tags of the osm entity representation a station
   */
  protected void updateTransferZoneGroupStationName(TransferZoneGroup transferZoneGroup, OsmEntity osmEntityStation, Map<String, String> tags) {
    
    if(!transferZoneGroup.hasName()) {
      String stationName = tags.get(OsmTags.NAME);
      if(stationName!=null) {
        transferZoneGroup.setName(stationName);
      }
    }
      
  }  
  
  /** process an osm entity that is classified as a (train) station. For this to register on the transfer zone, we try to utilise its name and use it for the zone
   * name if it is empty. We also record it as an input property for future reference, e.g. key=station and value the name of the osm station
   *   
   * @param transferZone the osm station relates to 
   * @param tags of the osm entity representation a station
   */  
  protected void updateTransferZoneStationName(TransferZone transferZone, Map<String, String> tags) {
    
    String stationName = tags.get(OsmTags.NAME);
    if(!transferZone.hasName()) {      
      if(stationName!=null) {
        transferZone.setName(stationName);
      }
    }
    /* only set when not already set, because when already set it is likely the existing station name is more accurate */
    if(!PlanitOsmZoningHandlerHelper.hasTransferZoneStationName(transferZone)) {
      PlanitOsmZoningHandlerHelper.setTransferZoneStationName(transferZone, stationName);
    }
  }      
  
  /** break a planit link at the planit node location while also updating all osm related tracking indices and/or planit network link and link segment reference 
   * that might be affected by this process:
   * <ul>
   * <li>tracking of osmways with multiple planit links</li>
   * <li>connectoid access link segments affected by breaking of link (if any)</li>
   * </ul>
   * 
   * @param planitNode to break link at
   * @param networkLayer the node and link(s) reside on
   * @param linksToBreak the links to break 
   * @throws PlanItException thrown if error
   */
  protected void breakLinksAtPlanitNode(Node planitNode, MacroscopicPhysicalNetwork networkLayer, List<Link> linksToBreak) throws PlanItException {
    PlanitOsmNetworkReaderLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);

    /* track original combinations of linksegment/downstream vertex for each connectoid possibly affected by the links we're about to break link (segments) 
     * if after breaking links this relation is modified, restore it by updating the connectoid to the correct access link segment directly upstream of the original 
     * downstream vertex identified */
    Map<Point, DirectedConnectoid> connectoidsAccessNodeLocationBeforeBreakLink = 
        PlanitOsmZoningHandlerHelper.collectConnectoidAccessNodeLocations(linksToBreak, getZoningReaderData().getPlanitData().getDirectedConnectoidsByLocation(networkLayer));
    
    /* register additional actions on breaking link via callback listeners:
     * 1) connectoid update (see above)
     * 2) xml id update on links (and its link segments) by syncing to internal ids so they remain unique. 
     * 
     * 2) is needed when persisting based on planit xml ids which otherwise leads to problems due to duplicate xml ids when breaking links (only internal ids remain unique). 
     * Note that this syncing works because we create planit links such that initially xml ids are in sync with internal ids upon creation. If this is not the case we cannot 
     * guarantee uniqueness of xml ids using this method.
     */
    Set<BreakEdgeListener<Node, Link>> breakLinkListeners = 
        Set.of(
            new UpdateConnectoidsOnBreakLink<Node, Link, LinkSegment>(connectoidsAccessNodeLocationBeforeBreakLink),
            new SyncDirectedEdgeXmlIdsToInternalIdOnBreakEdge<Node, Link, LinkSegment>());
        
    /* LOCAL TRACKING DATA CONSISTENCY  - BEFORE */    
    {      
      /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links exist with smaller geometries... insert those after as replacements*/
      getZoningReaderData().getPlanitData().removeLinksFromSpatialLinkIndex(linksToBreak); 
    }    
          
    /* break links */
    Map<Long, Set<Link>> newlyBrokenLinks = PlanitOsmNetworkHandlerHelper.breakLinksWithInternalNode(
        planitNode, linksToBreak, networkLayer, transferSettings.getReferenceNetwork().getCoordinateReferenceSystem(), breakLinkListeners);   

    /* TRACKING DATA CONSISTENCY - AFTER */
    {
      /* insert created/updated links and their geometries to spatial index instead */
      newlyBrokenLinks.forEach( (id, links) -> {getZoningReaderData().getPlanitData().addLinksToSpatialLinkIndex(links);});
                    
      /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to be able to find the correct planit links for (internal) osm nodes */
      layerData.updateOsmWaysWithMultiplePlanitLinks(newlyBrokenLinks);                            
    }
          
  }  
  
  /** extract the connectoid access node based on the given location. Either it already exists as a planit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the planit node
   *
   * @param osmNodeLocation to collect/create planit node for 
   * @param networkLayer to extract node on
   * @return planit node collected/created
   * @throws PlanItException thrown if error
   */  
  protected Node extractConnectoidAccessNodeByLocation(Point osmNodeLocation, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    final PlanitOsmNetworkReaderLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
    
    /* check if already exists */
    Node planitNode = layerData.getPlanitNodeByLocation(osmNodeLocation);
    if(planitNode == null) {
      /* does not exist yet...create */
      
      /* find the links with the location registered as internal */
      List<Link> linksToBreak = layerData.findPlanitLinksWithInternalLocation(osmNodeLocation);
      if(linksToBreak != null) {
      
        /* location is internal to an existing link, create it based on osm node if possible, otherwise base it solely on location provided*/
        OsmNode osmNode = layerData.getOsmNodeByLocation(osmNodeLocation);
        if(osmNode != null) {
          /* all regular cases */
          planitNode = PlanitOsmZoningHandlerHelper.createPlanitNodeForConnectoidAccess(osmNode, layerData, networkLayer);
        }else {
          /* special cases whenever parser decided that location required planit node even though there exists no osm node at this location */ 
          planitNode = PlanitOsmZoningHandlerHelper.createPlanitNodeForConnectoidAccess(osmNodeLocation, layerData, networkLayer);
        }
        getProfiler().logConnectoidStatus(getZoning().transferConnectoids.size());
                             
        /* now perform the breaking of links at the given node and update related tracking/reference information to broken link(segment)(s) where needed */
        breakLinksAtPlanitNode(planitNode, networkLayer, linksToBreak);
      }
    }
    return planitNode;
  }  
  
  /** extract the connectoid access node. either it already exists as a planit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the planit node
   * 
   * @param osmNode to collect planit node version for
   * @param networkLayer to extract node on
   * @return planit node collected/created
   * @throws PlanItException thrown if error
   */
  protected Node extractConnectoidAccessNodeByOsmNode(OsmNode osmNode, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {        
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);    
    return extractConnectoidAccessNodeByLocation(osmNodeLocation, networkLayer);
  }
  
  protected boolean extractDirectedConnectoidsForMode(TransferZone transferZone, Mode planitMode, Collection<EdgeSegment> eligibleLinkSegments, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(planitMode);
    
    for(EdgeSegment edgeSegment : eligibleLinkSegments) {
     
      /* update accessible link segments of already created connectoids (if any) */      
      Point proposedConnectoidLocation = edgeSegment.getDownstreamVertex().getPosition();
      boolean createConnectoidsForLinkSegment = true;
      
      if(zoningReaderData.getPlanitData().hasDirectedConnectoidForLocation(networkLayer, proposedConnectoidLocation)) {      
        /* existing connectoid: update model eligibility */
        Collection<DirectedConnectoid> connectoidsForNode = zoningReaderData.getPlanitData().getDirectedConnectoidsByLocation(proposedConnectoidLocation, networkLayer);        
        for(DirectedConnectoid connectoid : connectoidsForNode) {
          if(edgeSegment.idEquals(connectoid.getAccessLinkSegment())) {
            /* update mode eligibility */
            updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode));
            createConnectoidsForLinkSegment  = false;
            break;
          }
        }
      }
                    
      /* for remaining access link segments without connectoid -> create them */        
      if(createConnectoidsForLinkSegment) {
                
        /* create and register */
        Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, networkLayer, Collections.singleton(edgeSegment), Collections.singleton(planitMode));
        
        if(newConnectoids==null || newConnectoids.isEmpty()) {
          LOGGER.warning(String.format("Found eligible mode %s for stop_location of transferzone %s, but no access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
          return false;
        }
      }  
    }
    
    return true;
  }
  
  /** Identical to the same method with additional paramater containing link restrictions. Only by calling this method all entry link segments are considred whereas in the
   * more general version only link segments with a parent link in the provided link collection are considered. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    return extractDirectedConnectoidsForMode(location, transferZone, planitMode, null, geoUtils);
  }

  /** create and/or update directed connectoids for the given mode and layer based on the passed in location where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone. Generally a single connectoid is created for the most likely link segment identified, i.e., if the transfer
   * zone is placed on the left of the infrastructure, the closest by incoming link segment to the given location is used. Since the geometry of a link applies to both link segments
   * we define closest based on the driving position of the country, so a left-hand drive country will use the incoming link segment where the transfer zone is placed on the left, etc. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param eligibleAccessLinks only links in this collection are considered when compatible with provided location
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, Mode planitMode, Collection<Link> eligibleAccessLinks, PlanitJtsCrsUtils geoUtils) throws PlanItException {    
    if(location == null || transferZone == null || planitMode == null || geoUtils == null) {
      return false;
    }
    
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(planitMode);
    OsmNode osmNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(location);                
    
    /* planit access node */
    Node planitNode = extractConnectoidAccessNodeByLocation(location, networkLayer);    
    if(planitNode==null) {
      if(osmNode != null) {
        LOGGER.warning(String.format("DISCARD: osm node %d could not be converted to access node for transfer zone representation of osm entity %s",osmNode.getId(), transferZone.getXmlId(), transferZone.getExternalId()));
      }else {
        LOGGER.warning(String.format("DISCARD: location (%s) could not be converted to access node for transfer zone representation of osm entity %s",location.toString(), transferZone.getXmlId(), transferZone.getExternalId()));
      }
      return false;
    }
    
    /* must avoid cross traffic when:
     * 1) stop position does not coincide with transfer zone, i.e., waiting area is not on the road/rail, and
     * 2) mode requires waiting area to be on a specific side of the road, e.g. buses can only open doors on one side, so it matters for them, but not for train
     */
    boolean mustAvoidCrossingTraffic = !planitNode.getPosition().equalsTopo(transferZone.getGeometry());
    if(mustAvoidCrossingTraffic) {
      mustAvoidCrossingTraffic = PlanitOsmZoningHandlerHelper.isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation( planitMode, transferZone, osmNode!= null ? osmNode.getId() : null, getSettings());  
    }         
    
    /* find access link segments */
    Collection<EdgeSegment> accessLinkSegments = null;
    for(Link link : planitNode.<Link>getLinks()) {
      Collection<EdgeSegment> linkAccessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(transferZone,link, planitNode, planitMode, mustAvoidCrossingTraffic, geoUtils);
      if(linkAccessLinkSegments != null && !linkAccessLinkSegments.isEmpty()) {
        if(accessLinkSegments == null) {
          accessLinkSegments = linkAccessLinkSegments;
        }else {
          accessLinkSegments.addAll(linkAccessLinkSegments);
        }
      }
    }    
      
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      LOGGER.info(String.format("DICARD platform/pole/station %s its stop_location %s deemed invalid, no access link segment found due to incompatible modes or transfer zone on wrong side of road/rail", 
          transferZone.getExternalId(), planitNode.getExternalId()!= null ? planitNode.getExternalId(): ""));
      return false;
    }                           
    
    /* connectoids for link segments */
    return extractDirectedConnectoidsForMode(transferZone, planitMode, accessLinkSegments, geoUtils);
  }
  
  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node (location) where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used to determine location of transfer zone relative to infrastructure to identify which link segment(s) are eligible for connectoids placement
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(OsmNode osmNode, TransferZone transferZone, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    return extractDirectedConnectoidsForMode(osmNodeLocation, transferZone, planitMode, geoUtils);
  }
  
  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the osm node, unless the user has overwritten the default behaviour
   * with a custom mapping of stop_location to waiting area. In that case, we mark the stop_position as unprocessed, because then it will be processed later in post processing where
   * the stop_position is converted into a connectoid and the appropriate user mapper waiting area (Transfer zone) is collected to match. 
   * This methodis only relevant for very specific types of osm pt nodes, such as tram_stop, some bus_stops, and potentially halts and/or stations, e.g., only when the
   * stop location and transfer zone are both tagged on the road for a Ptv1 tagged node.
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultMode that is to be expected here
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */  
  protected void extractPtv1TransferZoneWithConnectoidsAtStopPosition(OsmNode osmNode, Map<String, String> tags, String defaultMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    if(getSettings().isOverwriteStopLocationWaitingArea(osmNode.getId())) {       
      /* postpone processing of stop location because alternative waiting area might not have been created yet. When all transfer zones (waiting areas) 
       * have been created it will be processed, we trigger this by marking this node as an unprocessed stop_position */
      getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode.getId());
    }else {              
      /* In the special case a Ptv1 tag for a tram_stop, bus_stop, halt, or station is supplemented with a Ptv2 stop_position we must treat this as stop_position AND transfer zone in one and therefore 
       * create a transfer zone immediately */      
      createAndRegisterPtv1TransferZoneWithConnectoidsAtOsmNode(osmNode, tags, defaultMode, geoUtils);
    }
    
  }   
  
  /** extract a platform since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform
   * 
   * @param osmEntity to extract from
   * @param tags all tags of the osm Node
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */    
  protected void extractPtv1RailwayPlatform(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
    
    /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
     * Ptv2 stop position reference is used, so postpone creating connectoids for now, and deal with it later when stop_positions have all been parsed */
    String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultMode.equals(OsmRailModeTags.TRAIN)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 railway platform %s,",defaultMode));
    }
    createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, defaultMode, geoUtils);
  }    
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=platform on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */  
  protected void extractTransferInfrastructurePtv1HighwayPlatform(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 highway platform %s,",defaultOsmMode));
    }    

    Pair<Collection<String>, Collection<Mode>> modeResult = collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(PlanitOsmZoningHandlerHelper.hasMappedPlanitMode(modeResult)) {               
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, modeResult.first(), geoUtils);
    }
  }   
      
    
  /** get profiler
   * @return profiler
   */
  protected final PlanitOsmZoningHandlerProfiler getProfiler() {
    return profiler;
  }
  
  /** get zoning reader data
   * @return data
   */
  protected final PlanitOsmZoningReaderData getZoningReaderData() {
    return this.zoningReaderData;
  }
  
  /** collect zoning
   * @return zoning;
   */
  protected final Zoning getZoning() {
    return this.zoning;
  }
  
  /** get network to zoning data
   * @return network to zoning data
   */
  protected final PlanitOsmNetworkToZoningReaderData getNetworkToZoningData() {
    return this.network2ZoningData;
  }
  
  /** get settings
   * @return settings
   */
  protected PlanitOsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }  
  
  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param network2ZoningData data collated from parsing network required to successfully popualte the zoning
   * @param zoningToPopulate to populate
   * @param profiler to keep track of created/parsed entities across zone handlers
   */
  public PlanitOsmZoningBaseHandler(
      final PlanitOsmPublicTransportReaderSettings transferSettings, 
      PlanitOsmZoningReaderData zoningReaderData, 
      final PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {

    /* profiler */
    this.profiler = profiler;
    
    /* references */
    this.network2ZoningData = network2ZoningData;
    this.zoning = zoningToPopulate;       
    this.transferSettings = transferSettings;
    this.zoningReaderData = zoningReaderData;
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public abstract void initialiseBeforeParsing() throws PlanItException;
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public abstract void reset();  
  

  
}

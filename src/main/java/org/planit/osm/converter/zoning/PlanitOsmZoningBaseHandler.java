package org.planit.osm.converter.zoning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.network.PlanitOsmNetworkHandlerHelper;
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
  
  /** parser functionality regarding the creation of PLANit transfer zones from OSM entities */
  private final PlanitOsmTransferZoneParser transferZoneParser;
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final PlanitOsmPublicTransportModeParser publicTransportModeParser;  
    
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
  
  /** log the given warning message but only when it is not too close to the bounding box, because then it is too likely that it is discarded due to missing
   * infrastructure or other missing assets that could not be parsed fully as they pass through the bounding box barrier. Therefore the resulting warning message is likely 
   * more confusing than helpful in those situation and is therefore ignored
   * 
   * @param message to log if not too close to bounding box
   * @param geometry to determine distance to bounding box to
   * @param logger to log on
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  protected void logWarningIfNotNearBoundingBox(String message, Geometry geometry, Logger logger, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    if(!PlanitOsmBoundingBoxUtils.isNearNetworkBoundingBox(geometry, getNetworkToZoningData().getNetworkBoundingBox(), geoUtils)) {
      logger.warning(message);
    }
  }    
  
  /** Verify if node resides on or within the zoning bounding polygon. If no bounding area is defined
   * this always returns true
   * 
   * @param osmNode to verify
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  protected boolean isCoveredByZoningBoundingPolygon(OsmNode osmNode) {
    if(osmNode==null) {
      return false;
    }
    
    /* without explicit bounding polygon all nodes are eligible */
    if(!getSettings().hasBoundingPolygon()) {
      return true;
    }
    
    /* within or on bounding polygon yields true, false otherwise */
    return PlanitOsmNodeUtils.createPoint(osmNode).coveredBy(getSettings().getBoundingPolygon());  
  }
  
  /** Verify if osm way has at least one node that resides within the zoning bounding polygon. If no bounding area is defined
   * this always returns true
   * 
   * @param osmWay to verify
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  protected boolean isCoveredByZoningBoundingPolygon(OsmWay osmWay) {
    if(osmWay==null) {
      return false;
    }
    
    /* without explicit bounding polygon all ways are eligible */
    if(!getSettings().hasBoundingPolygon()) {
      return true;
    }
    
    /* check if at least a single node of the OSM way is present within bounding box of zoning, implicitly assuming
     * that zoning bounding box is smaller than that of network, since only nodes within network bounding box are checked
     * otherwise the node is considered not available by definition */
    boolean coveredByBoundingPolygon = false;
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      long osmNodeId = osmWay.getNodeId(index);
      if(getSettings().getNetworkDataForZoningReader().getOsmNodes().containsKey(osmNodeId)) {
        OsmNode osmNode = getSettings().getNetworkDataForZoningReader().getOsmNodes().get(osmNodeId);
        if(isCoveredByZoningBoundingPolygon(osmNode)) {
          coveredByBoundingPolygon = true;
          break;
        }
      }
    }
    
    return coveredByBoundingPolygon;  
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
   * @throws PlanItException thrown if error
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
   * When one allows for pseudo compatibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param link to verify
   * @param referenceOsmModes to map agains (may be null)
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
    return transferZoneParser.createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmNode, tags, defaultOsmMode, ptv1TransferZoneType, geoUtils);    
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
    transferZoneParser.createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, defaultMode, geoUtils);
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

    Pair<Collection<String>, Collection<Mode>> modeResult = publicTransportModeParser.collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(PlanitOsmZoningHandlerHelper.hasMappedPlanitMode(modeResult)) {               
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      transferZoneParser.createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, modeResult.first(), geoUtils);
    }
  }         
    
  /** Get profiler
   * 
   * @return profiler
   */
  protected final PlanitOsmZoningHandlerProfiler getProfiler() {
    return profiler;
  }
  
  /** Get zoning reader data
   * 
   * @return data
   */
  protected final PlanitOsmZoningReaderData getZoningReaderData() {
    return this.zoningReaderData;
  }
  
  /** Collect zoning
   * 
   * @return zoning;
   */
  protected final Zoning getZoning() {
    return this.zoning;
  }
  
  /** Get network to zoning data
   * 
   * @return network to zoning data
   */
  protected final PlanitOsmNetworkToZoningReaderData getNetworkToZoningData() {
    return this.network2ZoningData;
  }
  
  /** Get PT settings
   * 
   * @return settings
   */
  protected PlanitOsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  } 
  
  /** Get the transfer zone parser
   * 
   * @return transfer zone parser 
   */
  protected PlanitOsmTransferZoneParser getTransferZoneParser() {
    return this.transferZoneParser;
  }
  
  /** Get the public transport mode parser
   * 
   * @return public transport mode parser 
   */
  protected PlanitOsmPublicTransportModeParser getPtModeParser() {
    return this.publicTransportModeParser;
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
    
    /* parser for creating PLANit transfer zones */
    this.transferZoneParser = new PlanitOsmTransferZoneParser(zoningToPopulate, zoningReaderData, transferSettings, network2ZoningData, profiler);
    
    /* parser for identifying pt PLANit modes from OSM entities */
    this.publicTransportModeParser = new PlanitOsmPublicTransportModeParser(network2ZoningData.getNetworkSettings());
  }
  
  /** Call this BEFORE we parse the OSM network to initialise the handler properly
   * 
   * @throws PlanItException  thrown if error
   */
  public abstract void initialiseBeforeParsing() throws PlanItException;
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public abstract void reset();  
  

  
}

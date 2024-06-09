package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.*;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneGroup;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Envelope;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

import static org.goplanit.osm.util.OsmModeUtils.identifyPtv1DefaultMode;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations to the relevant transfer zones. This handler conducts the main processing pass
 * whereas there also exist a pre- and post-processing handlers to initialise and finalise the parsing when the ordering of how OSM entities are parsed from file and
 * offered to the handlers do not allow us to parse the OSM data in a single pass due to interdependencies.
 * 
 * @author markr
 * 
 *
 */
public class OsmZoningMainProcessingHandler extends OsmZoningHandlerBase {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningMainProcessingHandler.class.getCanonicalName());

  /** When an OSM node has been identified in pre-processing as used by pt/zoning infrastructure, finalise registration by adding the actual OSM node instance
   *  otherwise ignore
   *
   * @param osmNode to check
   */
  private void registerIfPreregistered(OsmNode osmNode) {
    var osmNodeData = getZoningReaderData().getOsmData().getOsmNodeData();
    if(osmNodeData.containsPreregisteredOsmNode(osmNode.getId()) && !osmNodeData.containsOsmNode(osmNode.getId())){
      osmNodeData.registerEligibleOsmNode(osmNode);
    }
  }

  /** Extract the platform member of a Ptv2 stop_area and register it on the transfer zone group
   * 
   * @param transferZoneGroup to register on
   * @param osmRelation the platform belongs to
   * @param member the platform member
   * @param suppressLogging when true suppress logging
   */
  private void registerPtv2StopAreaPlatformOnGroup(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member, boolean suppressLogging) {

    EntityType type = member.getType();
    long osmId = member.getId();
    if(member.getType().equals(EntityType.Relation)) {
      /* special case - if platform is not a regular OSM way, but modelled as a multi-polygon, then it is a relation in itself, in which case we
       * stored its outer boundary (outer role). Use that instead of the relation to collect the transfer zone that was created */      
      OsmRelationMember internalMember = 
          OsmRelationUtils.findFirstOsmRelationMemberWithRole(osmRelation ,OsmMultiPolygonTags.OUTER_ROLE);
      if(internalMember!=null) {
        if(getZoningReaderData().getOsmData().hasOuterRoleOsmWay(internalMember.getId())) {
          OsmWay osmWay = getZoningReaderData().getOsmData().getOuterRoleOsmWay(internalMember.getId());
          type = EntityType.Way;
          osmId = osmWay.getId();
        }else if(!suppressLogging){
          LOGGER.severe("Identified platform as multi-polygon/relation, however its `outer role` member is not available or converted into a transfer zone");
        }        
      }
    }      
    
    /* register on group */
    getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmId, type, transferZoneGroup, suppressLogging);
  }

  /** Verify if the stop role member is indeed a stop_position, if so return false, it is correctly tagged, if not return true indicating 
   * it is wrongly tagged.
   * 
   * @param member the member of the relation to check
   * @return true when wrongly tagged role found for this stop_position, false otherwise
   */
  private boolean isPtv2StopAreaWronglyTaggedStopRole(OsmRelationMember member) {
    
    boolean wronglyTaggedRole = false;
    if(member.getType() == EntityType.Node) {
      OsmNode osmNode = getZoningReaderData().getOsmData().getOsmNodeData().getRegisteredOsmNode(member.getId());
      if(osmNode == null) {
        /* node not available, likely outside bounding box, since unknown we cannot claim it is wrongly tagged */
        return false;
      }
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);
      if(!(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_POSITION))) {

        wronglyTaggedRole = true;
      }
    }else {
      /* stop_position is always a node: identified problem */
      wronglyTaggedRole = true;
    }
    
    return wronglyTaggedRole;
  }  
  
  /** When a member on stop_area is wrongly tagged, use this method to try and salvage it for what it is (if possible)
   * 
   * @param transferZoneGroup the stop_area reflects in PLANit
   * @param osmRelation the OSM stop_area relation
   * @param member the member that is wrongly tagged
   * @return true when salvaged in a way that no longer requires processing the entity as part of the stop area, false, otherwise
   */
  private boolean salvageWronglyTaggedStopRolePtv2StopAreaRelation(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {

    boolean suppressLogging = getSettings().isSuppressOsmRelationStopAreaLogging(osmRelation.getId());

    if(member.getType()==EntityType.Node && getZoningReaderData().getOsmData().getUnprocessedPtv1FerryTerminal(member.getId())!=null){
      /* while somehow wrongly tagged as a PTV2 entity, the indication it is a ferry terminal, might indicate it indeed is a stop (position)
       * hence, still consider as a stop role and do not flag to be removed */
      return false;
    }

    /* station? -> then tag the groups name while retaining it for post-processing */
    Pair<OsmPtVersionScheme, OsmEntity> unprocessedStationPair = getZoningReaderData().getOsmData().getUnprocessedStation(member.getType(), member.getId());
    if(unprocessedStationPair != null) {
      /* station -> update name and indicate salvaged as it will be processed as a separate station and then potentially be re-attached*/
      if(!suppressLogging) LOGGER.info(String.format("SALVAGED: Stop_area %s member %d with stop role identified as station", transferZoneGroup.getExternalId(), member.getId()));
      TransferZoneGroupHelper.updateTransferZoneGroupName(transferZoneGroup, unprocessedStationPair.second(), OsmModelUtil.getTagsAsMap(unprocessedStationPair.second()));
      return true;
    }

    /* platform? --> then we should already have a transfer zone for it*/
    if(getZoningReaderData().getPlanitData().getTransferZoneByOsmId(member.getType(), member.getId())!=null) {
      if(!suppressLogging) {
        LOGGER.info(String.format("SALVAGED: stop_area %s member %d incorrectly given stop role...identified as platform", transferZoneGroup.getExternalId(), member.getId()));
      }
      /* platform -> process as such */
      registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, member, suppressLogging);
      return true;
    }

    if(!suppressLogging){
      LOGGER.warning(String.format("DISCARD: stop_area %s member %d incorrectly given stop role...remains unidentified", transferZoneGroup.getExternalId(), member.getId()));
    }

    return true;
  }  
  
  /** process a stop_area member that has no role tag identifying its purpose but it is a Ptv1 supporting entity for highways. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   * @param suppressLogging when true suppress logging
   */
  private void processPtv2StopAreaMemberNodePtv1HighwayWithoutRole(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags, boolean suppressLogging) {

    String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
    if(ptv1ValueTag==null) {
      if(suppressLogging)
      LOGGER.severe(String.format("Highway tag not present for alleged Ptv1 highway %d on stop_area %d, this should not happen ignored", osmNode.getId(), osmRelation.getId()));
      return;
    }

    /* bus stop */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){

      /* register on group */
      getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmNode, tags, transferZoneGroup, suppressLogging);
      
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, OsmRoadModeTags.BUS, getGeoUtils());
      
    }else {
      LOGGER.warning(String.format("Unsupported Ptv1 highway=%s tag encountered, ignored",ptv1ValueTag));
    }     
    
  }  
  
  /** Process a stop_area member that has no role tag identifying its purpose but it is a Ptv1 supporting entity for railways. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a platform, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   * @param suppressLogging when true suppress logging
   */  
  private void processPtv2StopAreaMemberNodePtv1RailwayWithoutRole(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags, boolean suppressLogging) {
    String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);
    if(ptv1ValueTag==null) {
      if(suppressLogging) {
        LOGGER.severe(String.format("Railway tag not present for alleged Ptv1 railway %d on stop_area %d, this should not happen ignored", osmNode.getId(), osmRelation.getId()));
      }
      return;
    }
    
    /* non-tram mode exists */    
    if(getNetworkToZoningData().getNetworkSettings().isRailwayParserActive() &&
        getNetworkToZoningData().getNetworkSettings().getRailwaySettings().hasActivatedOsmModeOtherThan(OsmRailwayTags.TRAM)) {
      
      /* train station or halt (not for trams) */
      if(OsmTagUtils.matchesAnyValueTag(ptv1ValueTag, OsmPtv1Tags.STATION, OsmPtv1Tags.HALT)) {
        
        /* use name only */
        TransferZoneGroupHelper.updateTransferZoneGroupName(transferZoneGroup, osmNode, tags);
        getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
        
      }
      /* train platform */
      else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag) ) {
      
        /* register on group */
        getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmNode, tags, transferZoneGroup, suppressLogging);
      }
              
    }
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag)) {
    
      /* register on group */
      getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmNode, tags, transferZoneGroup, suppressLogging);
    
    }
    
    /* entrances/exits */
    if(OsmPtv1Tags.SUBWAY_ENTRANCE.equals(ptv1ValueTag) && getNetworkToZoningData().getNetworkSettings().getHighwaySettings().isOsmModeActivated(OsmRoadModeTags.FOOT)) {
      // entrances are to be converted to connectoids in post_processing
      //TODO
    }   
    
  }  

  /** process a stop_area member that has no role tag identifying its purpose but it is a Ptv1 supporting entity. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   * @param suppressLogging when true suppress logging
   */
  private void processPtv2StopAreaMemberNodePtv1WithoutRole(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags, boolean suppressLogging) {
    
    if(OsmRailwayTags.hasRailwayKeyTag(tags)) {     
      
      processPtv2StopAreaMemberNodePtv1RailwayWithoutRole(transferZoneGroup, osmRelation, osmNode, tags, suppressLogging);
      
    }else if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      
      processPtv2StopAreaMemberNodePtv1HighwayWithoutRole(transferZoneGroup, osmRelation, osmNode, tags, suppressLogging);
      
    }else if(OsmPtv1Tags.isFerryTerminal(tags) && getNetworkToZoningData().getNetworkSettings().isWaterwayParserActive()) {
      // without a role present we can't assume it is a transfer zone (beyond stop position), nor that it is the only ferry terminal
      // part of the group, so don't do anything yet until we understand its use better
    }
    
  }    

  /** process a stop_area member that has no role tag identifying its purpose, but it is a Ptv2 supporting entity. In this case this is only deemed useful for stations where we extract
   * the name to assign to the group
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   * @param suppressLogging when true suppress logging
   */  
  private void processPtv2StopAreaMemberNodePtv2WithoutRole(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags , boolean suppressLogging) {

    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STATION)) {
      
      TransferZoneGroupHelper.updateTransferZoneGroupName(transferZoneGroup, osmNode, tags);
      getProfiler().incrementOsmPtv2TagCounter(OsmPtv1Tags.STATION);
      
    }
  }  
  
  /** process a stop_area member node that has no role tag identifying its purpose. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param suppressLogging when true suppress logging
   */
  private void processPtv2StopAreaMemberNodeWithoutRole(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, boolean suppressLogging) {
    
    /* determine pt version */
    Map<String, String> osmNodeTags = OsmModelUtil.getTagsAsMap(osmNode);
    OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(osmNodeTags);
    /* process by version */
    switch (ptVersion) {
      case VERSION_2:
        processPtv2StopAreaMemberNodePtv2WithoutRole(transferZoneGroup, osmRelation, osmNode, osmNodeTags, suppressLogging);
        break;
      case VERSION_1:
        processPtv2StopAreaMemberNodePtv1WithoutRole(transferZoneGroup, osmRelation, osmNode, osmNodeTags, suppressLogging);
      default:          
        break;
    } 
  }  
  
  /** process a stop_area member station. We extract its name and set it on the group and transfer zones when possible.
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmStation of the relation to process
   */    
  private void processPtv2StopAreaMemberStation(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmEntity osmStation) {
    /* process as station */
    Map<String,String> tags = OsmModelUtil.getTagsAsMap(osmStation);
    /* on group */
    TransferZoneGroupHelper.updateTransferZoneGroupName(transferZoneGroup, osmStation, tags);
    /* on each transfer zone on the group (we must use relation because not all transfer zones might be registered on the group yet */
    for(int index=0;index<osmRelation.getNumberOfMembers();++index) {
      OsmRelationMember transferZoneMember = osmRelation.getMember(index);
      TransferZone transferZone = getZoningReaderData().getPlanitData().getTransferZoneByOsmId(transferZoneMember.getType(), osmStation.getId());
      if(transferZone!=null) {
        PlanitTransferZoneUtils.updateTransferZoneStationName(transferZone, tags);
      }
    }
    OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(tags);
    getZoningReaderData().getOsmData().removeUnproccessedStation(ptVersion, osmStation);    
    getProfiler().incrementOsmTagCounter(ptVersion, OsmPtv1Tags.STATION);    
  }
  
  /** process a stop_area member way that has no role tag identifying its purpose. Based on already parsed entities we attempt
   * to extract relevant information related to its transfer group if possible. For example, if it is a station, we obtain its name, if it is a platform
   * we register its related transfer zone to the group so it is properly available.
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmWayMember of the relation to process
   * @param suppressLogging when true suppress logging
   */  
  private void processPtv2StopAreaMemberWayWithoutRole(
      TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember osmWayMember, boolean suppressLogging) {
    
    boolean unidentified = false; 
    /* we do not store all OSM ways in memory, so we cannot simply collect and check  tags, instead we must rely
     * on unprocessed or already processed entities. For ways these are restricted to:
     * 
     *  - (unprocessed) stations -> process and extract name for group
     *  - (processed)   platforms (as transfer zones) -> register transfer zone on group */
          
    /* station */
    Pair<OsmPtVersionScheme, OsmEntity> osmWayPair = getZoningReaderData().getOsmData().getUnprocessedStation(EntityType.Way, osmWayMember.getId());                  
    if(osmWayPair != null) {
      processPtv2StopAreaMemberStation(transferZoneGroup, osmRelation, osmWayPair.second());
      unidentified = false;        
    }
    
    /* platform */
    if(unidentified) {
      TransferZone transferZone = getZoningReaderData().getPlanitData().getTransferZoneByOsmId(EntityType.Way, osmWayMember.getId());
      if(transferZone != null) { 
        /* process as platform */
        registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, osmWayMember, suppressLogging);
        unidentified = false;
      }
    }
  
    if(unidentified && !suppressLogging) {
      LOGGER.warning(String.format("DISCARD: unable to collect OSM way %d referenced in stop_area %d", osmWayMember.getId(), osmRelation.getId()));
    }  
  }   

  /** Process a stop_area member that has no role tag identifying its purpose. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param member of the relation to process
   */
  private void processPtv2StopAreaMemberWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    boolean suppressLogging = getSettings().isSuppressOsmRelationStopAreaLogging(osmRelation.getId());
    if(member.getType() == EntityType.Node) {
            
      /* collect OSM node */
      OsmNode osmNode = getZoningReaderData().getOsmData().getOsmNodeData().getRegisteredOsmNode(member.getId());
      if(osmNode == null) {
        if(!getSettings().hasBoundingBoundary() && !suppressLogging) {
          LOGGER.warning(String.format(
                  "DISCARD: OSM node %d (without role tag) referenced in stop_area %d not available, expected to reside outside bounding box, if not verify correctness", member.getId(), osmRelation.getId()));
        }
        return;
      }
      
      /* process as node, still it either adds an already parsed entity to the group, or marks unprocessed entities as processed so they are no longer
       * required to be processed as stand-alone Pt entities (not part of a stop_area) */
      processPtv2StopAreaMemberNodeWithoutRole(transferZoneGroup, osmRelation, osmNode, suppressLogging);
      
    }else if(member.getType() == EntityType.Way){
            
      /* we do not store all osm ways in memory, so we pass along the member information instead and attempt to still process the way best we can */
      processPtv2StopAreaMemberWayWithoutRole(transferZoneGroup, osmRelation, member, suppressLogging);
      
    }else if(suppressLogging){
      LOGGER.info(String.format("DISCARD: stop_area (%d) member without a role found (%d) that is not a node or way",osmRelation.getId(),member.getId()));
    }
  }   
  
  /** Identify and register - or directly extract - the Ptv2 stop_position with additional Ptv1 information. Use Ptv1 information to
   * determine eligibility regarding mode support. Only when compatible consider the stop_position, otherwise ignore it or log issues found
   * 
   * @param osmNode the stop_position
   * @param tags of the OSM node
   * @param planitModeTypes supported mode types
   * @return if true it is discarded and should not be processed again later on, for example as part of a stop area
   */
  private boolean extractPtv2Ptv1StopPosition(OsmNode osmNode, Map<String, String> tags, Collection<PredefinedModeType> planitModeTypes) {
    boolean DISCARD = true;

    var readerData = getZoningReaderData();
    if(hasNetworkLayersWithActiveOsmNode(osmNode.getId())){
      /* mark as stop position as it resides on infrastructure, mark for post_processing to create transfer zone and connectoids for it */
      readerData.getOsmData().addUnprocessedStopPosition(osmNode);
      return !DISCARD; // not discarded
    }    
    
    /* Ptv1 tags as well, use this context to determine if a tagging mistake has occurred and/or how to process in case special treatment is needed due
     * to user error or contextual interpretation that indicates we should use the Ptv1 tag instead of the Ptv2 tag to process this entity */
    if(OsmPtv1Tags.isTramStop(tags)){
      /* tagging error because Ptv1 tram stop must also be on a tram track  */
      if(OsmBoundingAreaUtils.isPartlyOrWhollyWithinBoundaryArea(osmNode, readerData.getBoundingArea(), true)) LOGGER.warning(String.format("DISCARD: Ptv2 stop_position with railway=tram_stop (%d) resides on discarded(out of bounds) OSM way, or it does not reside on an OSM way", osmNode.getId()));
      return DISCARD;
    }else if(OsmPtv1Tags.isFerryTerminal(tags)) {
      /* When stop position is a ferry terminal it may not (yet) be connected, how to deal with this is determined in post-processing, so keep it for now*/
      readerData.getOsmData().addUnprocessedStopPosition(osmNode);
      return !DISCARD;
    }

    /* potentially transform to alternative Ptv1 type if tag indicates it and which does not require it to be on a road.
     * Note that a stop_position that does not reside on a network layer can also be a result of the underlying road not being parsed. Hence, if we can transform it
     * which requires mapping it to a nearby road/rail later on, we first ensure such a road is nearby. If not, then it is highly likely the stop_position is not to
     * be parsed due to a coarser network and roads being excluded (in which case we simply discard the entry). */
    double searchRadius = getSettings().getStopToWaitingAreaSearchRadiusMeters();
    if(OsmPtv1Tags.isHalt(tags) || OsmPtv1Tags.isRailwayStation(tags, true)) {
      searchRadius = getSettings().getStationToParallelTracksSearchRadiusMeters();
    }

    /* ensure nearby mode compatible links exist to match the potentially salvaged Ptv1 entry to spatially */
    Envelope searchBoundingBox = OsmBoundingAreaUtils.createBoundingBox(osmNode, searchRadius, getGeoUtils());
    Collection<MacroscopicLink> spatiallyMatchedLinks = readerData.getPlanitData().findLinksSpatially(searchBoundingBox);
    spatiallyMatchedLinks = getPtModeHelper().filterModeCompatibleLinks(
        getNetworkToZoningData().getNetworkSettings().getMappedOsmModes(planitModeTypes), spatiallyMatchedLinks, false /*only exact matches allowed */);

    if( (spatiallyMatchedLinks == null || spatiallyMatchedLinks.isEmpty()) &&
        OsmBoundingAreaUtils.isPartlyOrWhollyWithinBoundaryArea(osmNode, readerData.getBoundingArea(), true)) {
      /* tagging error: discard, most likely stop_position resides on deactivated OSM road type that has not been parsed and if not it could not be mapped anyway*/
      LOGGER.info(String.format("DISCARD: Ptv2 stop_position %d on deactivated/non-existent infrastructure (Ptv1 tag conversion infeasible, no nearby compatible infrastructure)", osmNode.getId()));
      return DISCARD;
    }

    /* expected to be salvageable, log user feedback and do it */
    if(OsmPtv1Tags.isBusStop(tags)) {
      LOGGER.info(String.format("SALVAGED: Ptv2 public_transport=stop_position also tagged as Ptv1 bus_stop (%d), yet does not reside on parsed road infrastructure, attempt to parse as pole instead", osmNode.getId()));
    }else if(OsmPtv1Tags.isHalt(tags)) {
      LOGGER.info(String.format("SALVAGED: Ptv2 public_transport=stop_position also tagged as Ptv1 halt (%d), yet it does not reside on parsed road infrastructure, attempt to parse as small station instead", osmNode.getId()));
    }else if(OsmPtv1Tags.isRailwayStation(tags, true)) {
      LOGGER.info(String.format("SALVAGED: Ptv2 public_transport=stop_position also tagged as Ptv1 station (%d), yet it does not reside on parsed road infrastructure, attempt to parse as Ptv1 station instead", osmNode.getId()));
    }else if(OsmBoundingAreaUtils.isPartlyOrWhollyWithinBoundaryArea(osmNode, readerData.getBoundingArea(), true)){
      LOGGER.warning(String.format("DISCARD: Expected additional Ptv1 tagging for Ptv2 public_transport=stop_location on node %d but found none, while not residing on parsed road infrastructure, possible tagging error and/or dangling node", osmNode.getId()));
      return DISCARD;
    }

    extractTransferInfrastructurePtv1(osmNode, tags, getGeoUtils());
    return !DISCARD;
  }

  /** Identify and register - or directly extract - the Ptv2 stop_position. It is possibly combined with PTv1 tags, if so determine based on context if this should be treated as a Ptv2 tag, a Ptv1 tag  or the user made 
   * a mistake during tagging and attempt to salvage
   * 
   * @param osmNode the stop_position
   * @param tags of the OSM node
   */
  private void processPtv2StopPosition(OsmNode osmNode, Map<String, String> tags){

    boolean discarded = false;

    /* ensure the position is required given the activated modes */
    String ptv1DefaultMode = identifyPtv1DefaultMode(osmNode.getId(), tags, true /* allowed to not have this info as PTv2 entity */);

    /* PTv1 tagging present, so mode information available, use this to verify against activated mode(s), mark for further processing if available, otherwise ignore */
    Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode, tags, ptv1DefaultMode);

    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)){
      discarded = true;
    }else if(ptv1DefaultMode != null) {

      /* use Ptv1 knowledge to extract Ptv2 stop_position, discard if tagging error found */
      discarded = extractPtv2Ptv1StopPosition(osmNode, tags, modeResult.second());

    }else {
      /* PTv2 stop_position tag only with valid mode mapping (no Ptv1 tags for additional context) */

      /* stop positions relates to connectoids that provide access to transfer zones. The transfer zones are based on platforms, but these are processed separately. 
       * So, postpone parsing of all Ptv2 stop positions, and simply track them for delayed processing after all platforms/transfer zones have been identified */
      getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode);
    }

    if(discarded){
      getZoningReaderData().getOsmData().addIgnoreStopAreaStopPosition(EntityType.Node, osmNode.getId());
    }
  }

  /** Identify and register - or directly extract - the Ptv2 station. It is possibly combined with PTv1 tags, if so determine based on context if this should be treated as a Ptv2 tag, a Ptv1 tag  or the user made
   * a mistake during tagging and attempt to salvage
   *
   * @param osmNode the station
   * @param tags of the OSM node
   */
  private void processPtv2Station(OsmNode osmNode, Map<String, String> tags){

    /* ...otherwise stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
     * ignore them altogether. Therefore, postpone parsing them until after we have parsed the relations */
    getZoningReaderData().getOsmData().addUnprocessedPtv2Station(osmNode);
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv1Tags.STATION);
  }
  
  /** Extract a ptv2 platform for a given OSM node. When this node is on the road infrastructure we create a transfer zone and connectoids (discouraged tagging behaviour), but in most
   * cases, a platform is separated from the road infrastructure, in which case we create a transfer zone without connectoids and stop_locations (connectoids) will be attached during the
   * parsing of stop_locations, stop_areas, or spatially in post-processing
   * 
   * @param osmNode the platform is represented by
   * @param tags tags of the OSM node
   */
  private void processPtv2Platform(OsmNode osmNode, Map<String, String> tags){
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.PLATFORM);
    
    boolean platformOnNetwork = hasNetworkLayersWithActiveOsmNode(osmNode.getId());
    boolean notYetButToBeAttachedToNetwork = OsmPtv1Tags.isFerryTerminal(tags) && getSettings().isConnectDanglingFerryStopToNearbyFerryRoute();
    if(platformOnNetwork || notYetButToBeAttachedToNetwork) {

      /* platform is situated on (or could be attached in future) road/rail/water network . This may happen when it is not only a platform but also a (potential) stop_position,
       * is dealt with by extracting it as a stop position rather than a separate platform */
      processPtv2StopPosition(osmNode, tags);
      if(notYetButToBeAttachedToNetwork){
        getZoningReaderData().getOsmData().getOsmNodeData().preregisterEligibleOsmNode(osmNode.getId());
        getZoningReaderData().getOsmData().getOsmNodeData().registerEligibleOsmNode(osmNode);
      }

    }else {
      /* regular platform separated from vehicle stop position; create transfer zone but no connectoids, 
       * these will be constructed during or after we have parsed relations, i.e. stop_areas */
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
          osmNode,
          tags,
          TransferZoneType.PLATFORM,
          identifyPtv1DefaultMode(osmNode.getId(), tags, true),
          getGeoUtils());
    }   
  }   
  
  /** Extract a platform since it is deemed eligible for the PLANit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform
   * 
   * @param osmEntity to extract from
   * @param tags all tags of the OSM Node
   * @param geoUtils to use
   */    
  private void extractPtv1RailwayPlatform(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils){
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
    
    /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
     * Ptv2 stop position reference is used, so postpone creating connectoids for now, and deal with it later when stop_positions have all been parsed */
    String defaultMode = identifyPtv1DefaultMode(osmEntity.getId(), tags);
    if(!defaultMode.equals(OsmRailModeTags.TRAIN)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 railway platform %s,",defaultMode));
    }
    getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, defaultMode, geoUtils);
  }

  /** Extract a halt separate from infrastructure, i.e., not on rail tracks, but next to it. Create transfer zone without connectoids for it
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param geoUtils to use
   */
  private void extractPtv1StandAloneHalt(OsmNode osmNode, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.HALT);
        
    String expectedDefaultMode = OsmRailModeTags.TRAIN;    
    String defaultMode = identifyPtv1DefaultMode(osmNode.getId(), tags);
    if(!defaultMode.equals(expectedDefaultMode)) {
      LOGGER.warning(String.format("Unexpected osm mode identified for Ptv1 halt %s",defaultMode));
    }
    getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.SMALL_STATION, defaultMode, geoUtils);      
  }    

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=platform on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the OSM entity
   * @param geoUtils to use
   */  
  private void extractTransferInfrastructurePtv1HighwayPlatform(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils){
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = identifyPtv1DefaultMode(osmEntity.getId(), tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("Unexpected OSM mode identified for Ptv1 highway platform %s,",defaultOsmMode));
    }    
  
    Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmEntity, tags, defaultOsmMode);
    if(OsmModeUtils.hasMappedPlanitMode(modeResult)) {               
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, modeResult.first(), geoUtils);
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=bus_stop on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the OSM entity
   * @param geoUtils to use
   */  
  private void extractTransferInfrastructurePtv1HighwayBusStop(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils){
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = identifyPtv1DefaultMode(osmEntity.getId(), tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("Unexpected OSM mode identified for Ptv1 bus_stop %s,",defaultOsmMode));
    }      
    
    Pair<SortedSet<String>, SortedSet<PredefinedModeType>> modeResult =
        getPtModeHelper().collectPublicTransportModesFromPtEntity(osmEntity, tags, defaultOsmMode);
    if(OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.BUS_STOP);      
      if(Osm4JUtils.getEntityType(osmEntity).equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmEntity.getId())){
        
        /* bus_stop on the road and NO Ptv2 tags (or Ptv2 tags assessed and decided they should be ignored), treat it as
         * a stop_location (connectoid) rather than a waiting area (transfer zone), mark for post_processing as such */
        getZoningReaderData().getOsmData().addUnprocessedStopPosition((OsmNode) osmEntity);
        
      }else {
        
        /* bus_stop not on the road, only create transfer zone (waiting area), postpone creation of stop_location */
        getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.POLE, modeResult.first(), geoUtils);
      }
    }
  }   
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmway (no Ptv2 tags)
   * 
   * @param osmWay the way to extract
   * @param tags all tags of the OSM Node
   * @param ptv1ValueTag the value tag going with key highway=
   */
  private void extractTransferInfrastructurePtv1Highway(OsmWay osmWay, Map<String, String> tags, String ptv1ValueTag){
    
    /* platform -> create transfer zone */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      extractTransferInfrastructurePtv1HighwayPlatform(osmWay, tags, getGeoUtils());
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmNode (no Ptv2 tags)
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the OSM Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @param geoUtils to use
   */
  private void extractTransferInfrastructurePtv1Highway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag, PlanitJtsCrsUtils geoUtils){       
    OsmNetworkReaderSettings networkSettings = getNetworkToZoningData().getNetworkSettings();
    if(!networkSettings.isHighwayParserActive()) {
      return;
    }    
    
    /* bus stop -> create transfer zone */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){
      
      extractTransferInfrastructurePtv1HighwayBusStop(osmNode, tags, geoUtils);
      
    }
    /* platform -> create transfer zone */
    else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){ 
      
      extractTransferInfrastructurePtv1HighwayPlatform(osmNode, tags, geoUtils);
      
    }else {
      LOGGER.warning(String.format("unsupported Ptv1 higway=%s tag encountered, ignored",ptv1ValueTag));
    }
  }   
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag railway=* for an OsmWay
   * 
   * @param osmWay the way to extract
   * @param tags all tags of the OSM Node
   * @param ptv1ValueTag the value tag going with key railway=
   */ 
  private void extractTransferInfrastructurePtv1Railway(OsmWay osmWay, Map<String, String> tags, String ptv1ValueTag){
    OsmNetworkReaderSettings networkSettings = getNetworkToZoningData().getNetworkSettings();
    if(!networkSettings.isRailwayParserActive()) {
      return;
    }
    
    /* platform edge */
    if(OsmPtv1Tags.PLATFORM_EDGE.equals(ptv1ValueTag)) {
      
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      /* platform edges are for additional geographic information, nothing requires them to be there in our format, so we take note
       * but do not parse, see also https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform_edge */
    }
    
    /* platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
  
      extractPtv1RailwayPlatform(osmWay, tags, getGeoUtils());      
    }  
    
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasActivatedOsmModeOtherThan(OsmRailwayTags.TRAM)) {
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      getZoningReaderData().getOsmData().addUnprocessedPtv1Station(osmWay);
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.STATION);
    }     
  }

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag railway=* for an OsmNode
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key railway=
   * @param geoUtils to use
   */
  private void extractTransferInfrastructurePtv1Railway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag, PlanitJtsCrsUtils geoUtils) {
    OsmNetworkReaderSettings networkSettings = getNetworkToZoningData().getNetworkSettings();

    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().isOsmModeActivated(OsmRailwayTags.TRAM)) {
      
      if(!hasNetworkLayersWithActiveOsmNode(osmNode.getId())){
        
        /* tagging error */
        LOGGER.info(String.format("DISCARD: Ptv1 railway=tram_stop (%d) does not reside on tram tracks", osmNode.getId()));
        
      }else {      
        
        /* mark as stop position as it resides on infrastructure, mark for post_processing to create transfer zone and connectoids for it,
         * since it might have a separate waiting platform */
        getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.TRAM_STOP);
        getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode);
      }
      
    }
    
    /* train platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      
      /* extract platform as transfer zone without connectoids*/
      extractPtv1RailwayPlatform(osmNode, tags, geoUtils);
    }          
    
    /* train halt (not for trams)*/
    if(OsmPtv1Tags.HALT.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasActivatedOsmModeOtherThan(OsmRailwayTags.TRAM)) {
            
      if(!hasNetworkLayersWithActiveOsmNode(osmNode.getId())){
        
        /* extract halt as separate transfer zone without connectoids, reflecting a waiting area, not a stop position */  
        extractPtv1StandAloneHalt(osmNode, tags, geoUtils);
        
      }else {
        
        /* mark as stop position as it resides on infrastructure, mark for post_processing to create transfer zone and connectoids for it
         * since it might have a separate waiting platform */
        getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.HALT);
        getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode);
      }      
      
    }
    
    /* train station (not for trams) */
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasActivatedOsmModeOtherThan(OsmRailwayTags.TRAM)) {
      
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      getZoningReaderData().getOsmData().addUnprocessedPtv1Station(osmNode);
    }    
  }

  /** Original treatment of water modes (ferry), while not treated via PTv1 scheme, as it is the original approach we lump it in with the PTv1 scheme).
   *  Currently, this verifies if a ferry terminal is eligible and when so we verify it resides on a ferry route (way). If so, it is postponed for treatment
   *  similar to train stations residing on a train track. If not on the ferry route, it is likely a tagging error which we will log as such
   *
   * @param osmNode the node to extract
   * @param tags all tags of the OSM Node
   * @param geoUtils to use
   */
  private void extractTransferInfrastructurePtv1Water(OsmNode osmNode, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) {
    OsmNetworkReaderSettings networkSettings = getNetworkToZoningData().getNetworkSettings();

    /* ferry terminal */
    if(OsmPtv1Tags.isFerryTerminal(tags) && networkSettings.getWaterwaySettings().isOsmModeActivated(OsmWaterModeTags.FERRY)) {

      /* ferry terminals are often part of Ptv2 stop_areas and sometimes even more than one ferry terminal exists within the single stop_area.
       * It might be that the ferry terminal acts as a stop position (on the OSM way) rather than a transfer zone/platform. However,  we can only
       * hope to distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, the ferry terminal remains, i.e.,
       * are not part of a stop_area nor do we know what role it plays, then we parse it as a stop position and transfer zone combined.
       * For now, we track this instance and postpone parsing until then */
      if(!hasNetworkLayersWithActiveOsmNode(osmNode.getId()) && !getSettings().isConnectDanglingFerryStopToNearbyFerryRoute()){
        /* tagging error without remedy */
        LOGGER.warning(String.format("DISCARD: amenity=ferry_terminal (%d) does not reside on OSM way supporting a ferry route", osmNode.getId()));
        return;
      }
      /* either valid, or will attempt to create connection to nearby ferry route and supplement network with additional link/nodes, this is done in post-processing */

      /* mark for post_processing to create transfer zone and connectoids for it, since it might have a separate waiting platform */
      getProfiler().incrementOsmPtv1TagCounter(OsmTags.FERRY_TERMINAL);
      getZoningReaderData().getOsmData().addUnprocessedPtv1FerryTerminal(osmNode);
    }
  }


  /** Classic PT infrastructure based on Ptv2 OSM public transport scheme for an Osm way
   * 
   * @param osmWay to parse
   * @param tags of the node
   */  
  private void extractTransferInfrastructurePtv2(OsmWay osmWay, Map<String, String> tags){
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {
      String ptv2ValueTag = tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT);
      
      /* platform */
      if(OsmPtv2Tags.PLATFORM.equals(ptv2ValueTag)) {
              
        /* create transfer zone but no connectoids, these will be constructed during, or after, we have parsed relations, i.e., stop_areas */
        getProfiler().incrementOsmPtv2TagCounter(ptv2ValueTag);
        var defaultOsmMode = identifyPtv1DefaultMode(osmWay.getId(), tags, true);
        getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
            osmWay, tags, TransferZoneType.PLATFORM, defaultOsmMode, getGeoUtils());
      }      
      
      /* stop position */
      if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        /* should not be on a way, log and ignore */
        LOGGER.info(String.format("Encountered stop_position on osm way %d, this is not properly tagged, ignored",osmWay.getId()));
      }
      
      /* station */
      if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore, postpone parsing them until after we have parsed the relations */
        getZoningReaderData().getOsmData().addUnprocessedPtv2Station(osmWay);
      }     
      
      /* stop area */
      if(OsmPtv2Tags.STOP_AREA.equals(ptv2ValueTag)) {
        /* should not be on a way, log and ignore */
        LOGGER.info(String.format("Encountered stop_area on OSM way %d, this is not properly tagged, ignored",osmWay.getId()));
      }

    }else {
      throw new PlanItRunTimeException(String.format("Parsing transfer infrastructure (Ptv2) for osm way %s, but no public_transport key tag found",osmWay.getId()));
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme (no Ptv2 tags) for an OSM way
   * 
   * @param osmWay to parse
   * @param tags of the node
   */
  private void extractTransferInfrastructurePtv1(OsmWay osmWay, Map<String, String> tags){
    
    /* PTv1 highway=* */
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {

      if(getNetworkToZoningData().getNetworkSettings().isHighwayParserActive()) {
        String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
        extractTransferInfrastructurePtv1Highway(osmWay, tags, ptv1ValueTag);
      }
      
    }
    /* PTv1 railway=* */
    else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {

      if(getNetworkToZoningData().getNetworkSettings().isRailwayParserActive()) {
        String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
        extractTransferInfrastructurePtv1Railway(osmWay, tags, ptv1ValueTag);
      }
      
    }
    /* amenity=ferry_terminal*/
    else if(OsmPtv1Tags.isFerryTerminal(tags)) {
      if(getNetworkToZoningData().getNetworkSettings().isWaterwayParserActive()) {
        LOGGER.warning(String.format("DISCARD: amenity=ferry_terminal only allowed on OSM nodes, not on OSM way (tags: %s)", tags));
      }
    }else {
      throw new PlanItRunTimeException(String.format("Parsing transfer infrastructure (Ptv1) for OSM way %s, but no compatible key tags found",osmWay.getId()));
    }
  }  
  
 
  /** Classic PT infrastructure based on Ptv2 OSM public transport scheme for osm node.
   * 
   * @param osmNode to parse
   * @param tags of the node
   * @return true when node resulted in PLANit entity created
   */
  private void extractTransferInfrastructurePtv2(OsmNode osmNode, Map<String, String> tags){
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {
      String ptv2ValueTag = tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT);
      
      /* platform */
      if(OsmPtv2Tags.PLATFORM.equals(ptv2ValueTag)) {
                
        processPtv2Platform(osmNode, tags);

      }            
      /* stop position */
      else if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        
        processPtv2StopPosition(osmNode, tags);

      }     
      /* station */
      else if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {

        processPtv2Station(osmNode, tags);

      }           
      /* stop area */
      else if(OsmPtv2Tags.STOP_AREA.equals(ptv2ValueTag)) {
        /* should not be on a node, log and ignore */
        LOGGER.info(String.format("DISCARD: Encountered stop_area on OSM node %d, this is not properly tagged, ignored",osmNode.getId()));
      }          
                  
    }else {
      throw new PlanItRunTimeException(String.format("Parsing transfer infrastructure (Ptv2) for OSM node %s, but no compatible key tags found",osmNode.getId()));
    }
  }
  
  /** Classic PT infrastructure based on original OSM public transport scheme (not Ptv2 tags) for OSM node
   * 
   * @param osmNode to parse
   * @param tags of the node
   * @param geoUtils to use
   */
  private void extractTransferInfrastructurePtv1(OsmNode osmNode, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) {
        
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {

      if(getNetworkToZoningData().getNetworkSettings().isHighwayParserActive()) {
        String ptv1ValueTag = tags.get(OsmHighwayTags.getHighwayKeyTag());
        extractTransferInfrastructurePtv1Highway(osmNode, tags, ptv1ValueTag, geoUtils);
      }
      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {

      if(getNetworkToZoningData().getNetworkSettings().isRailwayParserActive()) {
        String ptv1ValueTag = tags.get(OsmRailwayTags.getRailwayKeyTag());
        extractTransferInfrastructurePtv1Railway(osmNode, tags, ptv1ValueTag, geoUtils);
      }
      
    }else if(OsmPtv1Tags.isFerryTerminal(tags)) {

      if(getNetworkToZoningData().getNetworkSettings().isWaterwayParserActive()) {
        extractTransferInfrastructurePtv1Water(osmNode, tags, geoUtils);
      }

    }else {
      throw new PlanItRunTimeException("Parsing transfer infrastructure (Ptv1) for OSM node %s, but no compatible key tags found",osmNode.getId());
    }  
        
  }

  /** Extract stop area relation of Ptv2 scheme. We create transfer zone groups for each valid stop_area, connect it to the transfer zones but do not yet created connectoids for the stop positions.
   * This is left to post-processing. We also mark processed stations, platforms, etc., such that after all
   * stop_areas have been processed, we can extract planit instances for the remaining unprocessed osm entities, knowing they do not belong to any stop_area and
   * constitute their own separate entity.
   * 
   * @param osmRelation to extract stop_area for
   * @param tags of the stop_area relation
   * @param suppressLogging when true suppress logging
   */
  private void extractPtv2StopAreaRelation(OsmRelation osmRelation, Map<String, String> tags, boolean suppressLogging){

    /* transfer zone group */
    TransferZoneGroup transferZoneGroup = getTransferZoneGroupHelper().createPopulateAndRegisterTransferZoneGroup(osmRelation, tags);    
    
    /* process all but stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);    
            
      if( skipOsmPtEntity(member)) {
        continue;
      }                
      
      /* platform */
      if(member.getRole().equals(OsmPtv2Tags.PLATFORM_ROLE)) {              
        
        registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, member, suppressLogging);
        
      }
      /* stop_position */
      else if(member.getRole().equals(OsmPtv2Tags.STOP_ROLE)) {
        
        /* stop_position processing is postponed to post_processing, do nothing for (potentially) valid stop_positions */
        
        /* Identify wrongly tagged members that state to be of role: stop, but are in fact platforms, or stations and need to be registered as such
        * a ferry terminal is considered an exception as it is considered more likely a mistake was made in the PTv2 tag and a ferry terminal can be a stop position, so not proven to be wrong */
        if(isPtv2StopAreaWronglyTaggedStopRole(member)) {
          
          /* we support wrongly tagged: stations, platforms that have already been identified in standalone fashion */
          boolean ignoreAfterSalvage = salvageWronglyTaggedStopRolePtv2StopAreaRelation(transferZoneGroup, osmRelation, member);

          if(ignoreAfterSalvage) {
            /* flag to not process as stop_position in post-processing since it is not a stop_position and invalidly tagged as such */
            getZoningReaderData().getOsmData().addIgnoreStopAreaStopPosition(member.getType(), member.getId());
          }
        }
      }
      /* other than stop_position */
      else {
        
        /* some common members do not have a dedicated role type, verify the supported ones here, since they must be processed anyway 
         * 
         * - station
         * - entrance
         * - other (bus_stop, tram_stop, etc.)
         * 
         * */
                       
        /* unknown */
        if(member.getRole().equals("")) {
         
          /* can be a PT related member that requires mapping to the group, so process */
          processPtv2StopAreaMemberWithoutRole(transferZoneGroup, osmRelation, member);
          
        }        
      }
    }
                
  }
  
  /** Extract a platform relation's member that is specified as the outer role, i.e. contains the geometry of the platform boundary. Use this to create
   * the platform. Can be part of a multi-polygon, or a platform relation within a stop_area. Both are very uncommon but do occasionally surface in case entries to the platform
   * are modelled explicitly as holes (inner boundary) within the platform
   * 
   * @param osmRelation of the platform
   * @param member representing the outer boundary
   * @param tags to use
   */
  private void extractPtv2OuterRolePlatformRelation(OsmRelation osmRelation, OsmRelationMember member, Map<String, String> tags) {
    /* try if it has been parsed, not if it has no tags (likely), yes if it has PTv2 tags (unlikely for multipolygon member)) */
    TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(EntityType.Way, member.getId());
    if(transferZone == null && getZoningReaderData().getOsmData().hasOuterRoleOsmWay(member.getId())) {
      /* collect from unprocessed ways, should be present */
      OsmWay unprocessedWay = getZoningReaderData().getOsmData().getOuterRoleOsmWay(member.getId());
      if(unprocessedWay == null) {
        LOGGER.severe(String.format("OSM way %d referenced by Ptv2 multipolygon %d not available in parser, this should not happen, relation ignored",member.getId(),osmRelation.getId()));
        return;
      }
            
      /* create transfer zone, use tags of relation that contain the PT information */
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
          unprocessedWay, tags, TransferZoneType.PLATFORM, identifyPtv1DefaultMode(unprocessedWay.getId(), tags), getGeoUtils());
    }
  }

  /** Extract Ptv2 platforms from the multi-polygon. Do not create connectoids for them as the stop_positions
   * might not have been parsed yet. For multi-polygons we use the outer polygon as their shape and ignore the rest.
   * Since this shape is modelled as an Osmway, we must find it. Since the Osmway is not tagged as public transport (likely), we must use 
   * the unprocessed ways we identified for this purpose in pre-processing. For the found OsmWay we create a transfer zone (without connectoids)
   * 
   * @param osmRelation to extract platform from
   * @param tags to use
   */
  private void extractPtv2PlatformRelation(OsmRelation osmRelation, Map<String, String> tags) {
    
    /* role:outer -> extract geometry for transfer zone*/
    OsmRelationMember member = OsmRelationUtils.findFirstOsmRelationMemberWithRole(osmRelation ,OsmMultiPolygonTags.OUTER_ROLE);
    if(member != null && !skipOsmPtEntity(member)) {         
      /* extract platform based on outer role member and geometry */
      extractPtv2OuterRolePlatformRelation(osmRelation, member, tags);
    }
  }

  /** extract the transfer infrastructure which will contribute to newly created transfer zones on the zoning instance
   * 
   * @param osmNode to parse
   * @param ptVersion this node adheres to
   * @param tags to use
   */
  protected void extractTransferInfrastructure(OsmNode osmNode, OsmPtVersionScheme ptVersion, Map<String, String> tags){

    /* attempt to parse PLANit pt entity from this OSM node */
    if(ptVersion == OsmPtVersionScheme.VERSION_2) {
      extractTransferInfrastructurePtv2(osmNode, tags);
    }else if(ptVersion == OsmPtVersionScheme.VERSION_1) {
      extractTransferInfrastructurePtv1(osmNode, tags, getGeoUtils());
    }

  }  
  
  /** extract the transfer infrastructure which will contribute to newly created transfer zones on the zoning instance
   * 
   * @param osmWay to parse
   * @param ptVersion this way adheres to
   * @param tags to use
   */
  protected void extractTransferInfrastructure(OsmWay osmWay, OsmPtVersionScheme ptVersion, Map<String, String> tags){
    if(ptVersion == OsmPtVersionScheme.VERSION_2) {
      extractTransferInfrastructurePtv2(osmWay, tags);
    }else if(ptVersion == OsmPtVersionScheme.VERSION_1) {
      extractTransferInfrastructurePtv1(osmWay, tags);
    }
  }    
  
  /** Conduct actual processing of an OSM way that is deemed compatible and contains public transport features that ought to be parsed
   * 
   * @param osmWay to parse
   * @param ptVersion identified for this OSM way
   * @param tags of this OSM way
   */
  protected void handlePtOsmWay(OsmWay osmWay, OsmPtVersionScheme ptVersion, Map<String,String> tags){
    if(ptVersion != OsmPtVersionScheme.NONE ) {
      /* regular pt entity*/

      /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
      extractTransferInfrastructure(osmWay, ptVersion, tags);
    
    }else{
      /* assumed multi-polygon used as pt platform */
      
      if(isCoveredByZoningBoundingPolygon(osmWay)) {
        /* even though OSM way itself appears not to be public transport related, it is marked for keeping
         * so we keep it. This occurs when way is part of relation (multipolygon) where its shape represents for
         * example the outline of a platform, but all pt tags reside on the relation and not on the way itself. 
         * Processing of the way is postponed until we parse relations */
        getZoningReaderData().getOsmData().addOsmRelationOuterRoleOsmWay(osmWay);
      }else{
        /* the osm relation's outer role multi-polygon way is marked to keep, but we now know it falls outside the
         * zoning bounding box, therefore, it can safely be removed as being flagged. It should in fact be ignored */
        getZoningReaderData().getOsmData().removeOsmRelationOuterRoleOsmWay(osmWay.getId());
      }
    }
  }

  /**
   * Handle eligible PT OSM relation to either extract an OSM Ptv2 stop area or multipolygon that represents a public transport platform
   * @param osmRelation to handle
   * @param tags of the relation
   */
  protected void handleOsmPtRelation(OsmRelation osmRelation, Map<String,String> tags){

    /* public transport type */
    String relationType = tags.get(OsmRelationTypeTags.TYPE);

    if(relationType.equals(OsmRelationTypeTags.PUBLIC_TRANSPORT) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_AREA)) {
      /* stop_area: process all but stop_positions (parsed in post-processing)*/
        getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_AREA);
        extractPtv2StopAreaRelation(osmRelation, tags, getSettings().isSuppressOsmRelationStopAreaLogging(osmRelation.getId()));
    }else if(relationType.equals(OsmRelationTypeTags.MULTIPOLYGON) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
      /* multi_polygons representing public transport platform */
      getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.PLATFORM_ROLE);
      extractPtv2PlatformRelation(osmRelation, tags);
    }
  }

  /**
   * Constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @param referenceNetwork  to use
   * @param zoningToPopulate to populate
   * @param profiler to use
   */
  public OsmZoningMainProcessingHandler(
      final OsmPublicTransportReaderSettings transferSettings,
      final OsmZoningReaderData zoningReaderData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      final PlanitOsmNetwork referenceNetwork,
      final Zoning zoningToPopulate,
      final OsmZoningHandlerProfiler profiler) {
    super(transferSettings, zoningReaderData, network2ZoningData, referenceNetwork, zoningToPopulate, profiler);
  }
  
  /**
   * Call this before we parse the OSM network to initialise the handler properly
   * 
   */
  public void initialiseBeforeParsing(){
    reset();
    
    PlanItRunTimeException.throwIf(
        getReferenceNetwork().getTransportLayers() == null || getReferenceNetwork().getTransportLayers().size()<=0,
          "Network is expected to be populated at start of parsing OSM zoning");
  }    

  /**
   * Construct PLANit nodes/connectoids/transferzones from OSM nodes when relevant
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) {

    /* parse as stand-alone PT-entity node when it is deemed spatially eligible */
    wrapHandleSpatialAndPtCompatibleOsmNode(osmNode, this::extractTransferInfrastructure);

  }

  /**
   * Parse an OSM way to extract for example platforms, or other transfer zone related geometry
   *
   * @param osmWay to handle
   */
  @Override
  public void handle(OsmWay osmWay) {

    /* delegate after verifying eligibility */
    super.wrapHandleSpatialAndPtCompatibleOsmWay(osmWay, this::handlePtOsmWay);
            
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) {

    /* delegate after verifying eligibility */
    wrapHandleSpatialAndPtCompatibleOsmRelation(osmRelation, this::handleOsmPtRelation);

  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {       
        
    LOGGER.fine(" OSM transfer zone group parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {

  }

  
}

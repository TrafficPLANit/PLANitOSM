package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.goplanit.osm.tags.*;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.physical.Link;
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

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations to the relevant transfer zones. This handler conducts the main processing pass
 * whereas there also exist a pre- and post-processing handlers to initialise and finalise the parsing when the ordering of how OSM entities are parsed from file and
 * offered to the handlers do not allow us to parse the OSM data in a single pass due to interdependencies.
 * 
 * @author markr
 * 
 *
 */
public class OsmZoningProcessingHandler extends OsmZoningHandlerBase {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningProcessingHandler.class.getCanonicalName());
            
  /** Extract the platform member of a Ptv2 stop_area and register it on the transfer zone group
   * 
   * @param transferZoneGroup to register on
   * @param osmRelation the platform belongs to
   * @param member the platform member
   */
  private void registerPtv2StopAreaPlatformOnGroup(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    EntityType type = member.getType();
    long osmId = member.getId();
    if(member.getType().equals(EntityType.Relation)) {
      /* special case - if platform is not a regular osm way, but modelled as a multi-polygon, then it is a relation in itself, in which case we
       * stored its outer boundary (outer role). Use that instead of the relation to collect the transfer zone that was created */      
      OsmRelationMember internalMember = 
          OsmRelationUtils.findFirstOsmRelationMemberWithRole(osmRelation ,OsmMultiPolygonTags.OUTER_ROLE);
      if(internalMember!=null) {
        if(getZoningReaderData().getOsmData().hasOuterRoleOsmWay(internalMember.getId())) {
          OsmWay osmWay = getZoningReaderData().getOsmData().getOuterRoleOsmWay(internalMember.getId());
          type = EntityType.Way;
          osmId = osmWay.getId();
        }else {
          LOGGER.severe("Identified platform as multi-polygon/relation, however its `outer role` member is not available or converted into a transfer zone");
        }        
      }
    }      
    
    /* register on group */
    getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmId, type, transferZoneGroup);
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
      OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
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
   */
  private void salvageWronglyTaggedStopRolePtv2StopAreaRelation(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    
    /* station? -> then we it should exist as unprocessed station */
    Pair<OsmPtVersionScheme, OsmEntity> unprocessedStationPair = getZoningReaderData().getOsmData().getUnprocessedStation(member.getType(), member.getId());
    if(unprocessedStationPair != null) {
      /* station -> process it as such */
      LOGGER.info(String.format("SALVAGED: stop_area %s member %d incorrectly given stop role...identified as station", transferZoneGroup.getExternalId(), member.getId()));
      TransferZoneGroupHelper.updateTransferZoneGroupStationName(transferZoneGroup, unprocessedStationPair.second(), OsmModelUtil.getTagsAsMap(unprocessedStationPair.second()));          
    }      
    /* platform? --> then we should already have a transfer zone for it*/      
    else if(getZoningReaderData().getPlanitData().getTransferZoneByOsmId(member.getType(), member.getId())!=null) {
      LOGGER.info(String.format("SALVAGED: stop_area %s member %d incorrectly given stop role...identified as platform", transferZoneGroup.getExternalId(), member.getId()));
      /* platform -> process as such */
      registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, member);
    }else {
      LOGGER.warning(String.format("DISCARD: stop_area %s member %d incorrectly given stop role...remains unidentified", transferZoneGroup.getExternalId(), member.getId()));
    }       
  }  
  
  /** process a stop_area member that has no role tag identifying its purpose but it is a Ptv1 supporting entity for highways. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   * @throws PlanItException thrown if error
   */   
  private void processPtv2StopAreaMemberNodePtv1HighwayWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
    if(ptv1ValueTag==null) {
      LOGGER.severe(String.format("highway tag not present for alleged Ptv1 highway %d on stop_area %d, this should not happen ignored", osmNode.getId(), osmRelation.getId()));
      return;
    }

    /* bus stop */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){

      /* register on group */
      getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmNode, tags, transferZoneGroup);      
      
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, OsmRoadModeTags.BUS, getGeoUtils());
      
    }else {
      LOGGER.warning(String.format("unsupported Ptv1 higway=%s tag encountered, ignored",ptv1ValueTag));
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
   */  
  private void processPtv2StopAreaMemberNodePtv1RailwayWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);
    if(ptv1ValueTag==null) {
      LOGGER.severe(String.format("railway tag not present for alleged Ptv1 railway %d on stop_area %d, this should not happen ignored", osmNode.getId(), osmRelation.getId()));
      return;
    }
    
    /* non-tram mode exists */    
    if(getNetworkToZoningData().getNetworkSettings().isRailwayParserActive() &&
        getNetworkToZoningData().getNetworkSettings().getRailwaySettings().hasActivatedOsmModeOtherThan(OsmRailwayTags.TRAM)) {
      
      /* train station or halt (not for trams) */
      if(OsmTagUtils.matchesAnyValueTag(ptv1ValueTag, OsmPtv1Tags.STATION, OsmPtv1Tags.HALT)) {
        
        /* use name only */
        TransferZoneGroupHelper.updateTransferZoneGroupStationName(transferZoneGroup, osmNode, tags);
        getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
        
      }
      /* train platform */
      else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag) ) {
      
        /* register on group */
        getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmNode, tags, transferZoneGroup);        
      }
              
    }
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag)) {
    
      /* register on group */
      getTransferZoneGroupHelper().registerTransferZoneOnGroup(osmNode, tags, transferZoneGroup);
    
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
   * @throws PlanItException thrown if error
   */
  private void processPtv2StopAreaMemberNodePtv1WithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    
    if(OsmRailwayTags.hasRailwayKeyTag(tags)) {     
      
      processPtv2StopAreaMemberNodePtv1RailwayWithoutRole(transferZoneGroup, osmRelation, osmNode, tags);
      
    }else if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      
      processPtv2StopAreaMemberNodePtv1HighwayWithoutRole(transferZoneGroup, osmRelation, osmNode, tags);
      
    }               
    
  }    

  /** process a stop_area member that has no role tag identifying its purpose but it is a Ptv2 supporting entity. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   */  
  private void processPtv2StopAreaMemberNodePtv2WithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STATION)) {
      
      TransferZoneGroupHelper.updateTransferZoneGroupStationName(transferZoneGroup, osmNode, tags);
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
   * @throws PlanItException thrown if error
   */  
  private void processPtv2StopAreaMemberNodeWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode) throws PlanItException {
    
    /* determine pt version */
    Map<String, String> osmNodeTags = OsmModelUtil.getTagsAsMap(osmNode);
    OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(osmNodeTags);
    /* process by version */
    switch (ptVersion) {
      case VERSION_2:
        processPtv2StopAreaMemberNodePtv2WithoutRole(transferZoneGroup, osmRelation, osmNode, osmNodeTags);
        break;
      case VERSION_1:
        processPtv2StopAreaMemberNodePtv1WithoutRole(transferZoneGroup, osmRelation, osmNode, osmNodeTags);
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
    TransferZoneGroupHelper.updateTransferZoneGroupStationName(transferZoneGroup, osmStation, tags);
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
   */  
  private void processPtv2StopAreaMemberWayWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember osmWayMember) {
    
    boolean unidentified = false; 
    /* we do not store all ways in memory, so we cannot simply collect and check  tags, instead we must rely
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
        registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, osmWayMember);                    
        unidentified = false;
      }
    }
  
    if(unidentified) {
      LOGGER.warning(String.format("DISCARD: unable to collect osm way %d referenced in stop_area %d", osmWayMember.getId(), osmRelation.getId()));
    }  
  }   

  /** Process a stop_area member that has no role tag identifying its purpose. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param member of the relation to process
   * @throws PlanItException thrown if error
   */
  private void processPtv2StopAreaMemberWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) throws PlanItException {
    
    if(member.getType() == EntityType.Node) {
            
      /* collect osm node */
      OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
      if(osmNode == null) {
        if(!getSettings().hasBoundingPolygon()) {
          LOGGER.warning(String.format("DISCARD: Unable to collect osm node %d referenced in stop_area %d, verify it lies outside the parsed area", member.getId(), osmRelation.getId()));
        }
        return;
      }
      
      /* process as node, still it either adds an already parsed entity to the group, or marks unprocessed entities as processed so they are no longer
       * required to be processed as stand-alone Pt entities (not part of a stop_area) */
      processPtv2StopAreaMemberNodeWithoutRole(transferZoneGroup, osmRelation, osmNode);       
      
    }else if(member.getType() == EntityType.Way){
            
      /* we do not store all osm ways in memory, so we pass along the member information instead and attempt to still process the way best we can */
      processPtv2StopAreaMemberWayWithoutRole(transferZoneGroup, osmRelation, member);     
      
    }else {      
      LOGGER.info(String.format("DISCARD: top_area (%d) member without a role found (%d) that is not a node or way",osmRelation.getId(),member.getId()));
    }
  }   
  
  /** Identify and register - or directly extract - the Ptv2 stop_position with additional Ptv1 information. Use Ptv1 information to
   * determine eligibility regarding mode support. Only when compatible consider the stop_position, otherwise ignore it or log issues found
   * 
   * @param osmNodeof the stop_position
   * @param tags of the OSM node
   * @param ptv1DefaultMode identified Ptv1DefaultMode
   * @throws PlanItException thrown if error
   */
  private void extractPtv2Ptv1StopPosition(OsmNode osmNode, Map<String, String> tags, String ptv1DefaultMode) throws PlanItException {
    /* PTv1 tagging present, so mode information available, use this to verify against activated mode(s), mark for further processing if available, otherwise ignore */
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, ptv1DefaultMode);
    if(!OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      return;
    }  
    
    if(hasNetworkLayersWithActiveOsmNode(osmNode.getId())){
      /* mark as stop position as it resides on infrastructure, mark for post_processing to create transfer zone and connectoids for it */
      getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode.getId());
      return;
    }    
    
    /* Ptv1 tags as well, use this context to determine if a tagging mistake has occurred and/or how to process in case special treatment is needed due
     * to user error or contextual interpretation that indicates we should use the Ptv1 tag instead of the Ptv2 tag to process this entity */
    if(OsmPtv1Tags.isTramStop(tags)) {
      /* tagging error because Ptv1 tram stop must also be on a tram track */
      LOGGER.info(String.format("DISCARD: Ptv2 stop_position with railway=tram_stop (%d) does not reside on parsed tram tracks", osmNode.getId()));     
    }else{
      /* potentially transform to alternative Ptv1 type if tag indicates it and which does not require it to be on a road. 
       * Note that a stop_position that does not reside on a network layer can also be a result of the underlyng road not being parsed. Hence, if we can transform it
       * which requires mapping it to a nearby road/rail later on, we first ensure such a road is nearby. If not, then it is highly likely the stop_position is not to
       * be parsed due to a coarser network and roads being excluded (in which case we simply discard the entry). */
      double searchRadius = getSettings().getStopToWaitingAreaSearchRadiusMeters();
      if(OsmPtv1Tags.isHalt(tags) || OsmPtv1Tags.isStation(tags)) {
        searchRadius = getSettings().getStationToParallelTracksSearchRadiusMeters();
      }      
      
      /* ensure nearby mode compatible links exist to match the potentially salvaged Ptv1 entry to spatially */
      Envelope searchBoundingBox = OsmBoundingAreaUtils.createBoundingBox(osmNode, searchRadius, getGeoUtils());
      Collection<Link> spatiallyMatchedLinks = getZoningReaderData().getPlanitData().findLinksSpatially(searchBoundingBox);      
      spatiallyMatchedLinks = getPtModeHelper().filterModeCompatibleLinks(getNetworkToZoningData().getNetworkSettings().getMappedOsmModes(modeResult.second()), spatiallyMatchedLinks, false /*only exact matches allowed */);
      if(spatiallyMatchedLinks == null || spatiallyMatchedLinks.isEmpty()) {
        /* tagging error: discard, most likely stop_position resides on deactivated OSM road type that has not been parsed and if not it could not be mapped anyway*/
        LOGGER.info(String.format("DISCARD: Ptv2 stop_position %d on deactivated/non-existent infrastructure (Ptv1 tag conversion infeasible, no nearby compatible infrastructure)", osmNode.getId()));
        return;
      } 
      
      /* expected to be salvageable, log user feedback and do it */
      if(OsmPtv1Tags.isBusStop(tags)) {
        LOGGER.info(String.format("SALVAGED: Ptv2 public_transport=stop_position also tagged as Ptv1 bus_stop (%d), yet does not reside on parsed road infrastructure, attempt to parse as pole instead", osmNode.getId()));
      }else if(OsmPtv1Tags.isHalt(tags)) {
        LOGGER.info(String.format("SALVAGED: Ptv2 public_transport=stop_position also tagged as Ptv1 halt (%d), yet it does not reside on parsed road infrastructure, attempt to parse as small station instead", osmNode.getId()));
      }else if(OsmPtv1Tags.isStation(tags)) {
        LOGGER.info(String.format("SALVAGED: Ptv2 public_transport=stop_position also tagged as Ptv1 station (%d), yet it does not reside on parsed road infrastructure, attempt to parse as Ptv1 station instead", osmNode.getId()));     
      }else {
        LOGGER.warning(String.format("Expected additional Ptv1 tagging for Ptv2 public_transport=stop_location on node %d but found none, this shouldn't happen", osmNode.getId()));
        return;
      }
    
      extractTransferInfrastructurePtv1(osmNode, tags, getGeoUtils());
    }
  }

  /** Identify and register - or directly extract - the Ptv2 stop_position. It is possibly combined with PTv1 tags, if so determine based on context if this should be treated as a Ptv2 tag, a Ptv1 tag  or the user made 
   * a mistake during tagging and attempt to salvage
   * 
   * @param osmNodeof the stop_position
   * @param tags of the OSM node
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopPosition(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    
    /* ensure the position is required given the activated modes */
    String ptv1DefaultMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    if(ptv1DefaultMode != null) {
      
     /* use Ptv1 knowledge to extract Ptv2 stop_position */
      extractPtv2Ptv1StopPosition(osmNode, tags, ptv1DefaultMode);
      
    }else {
      /* PTv2 stop_position tag only (no Ptv1 tags for additional context) */
      
      /* stop positions relates to connectoids that provide access to transfer zones. The transfer zones are based on platforms, but these are processed separately. 
       * So, postpone parsing of all Ptv2 stop positions, and simply track them for delayed processing after all platforms/transfer zones have been identified */
      getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode.getId());
    }
  }
  
  /** Extract a ptv2 platform for a given osm node. When this node is on the road infrastructure we create a transfer zone and connectoids (discouraged tagging behaviour), but in most
   * cases, a platform is seaprated from the road infrastructure, in which case we create a transfer zone without connectoids and stop_locations (connectoids) will be attached during the
   * parsing of stop_locations, stop_areas, or spatially in post processing
   * 
   * @param osmNode the platform is represented by
   * @param tags tags of the osm node
   * @throws PlanItException thrown if error
   */
  private void extractPtv2Platform(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.PLATFORM);
    
    String defaultOsmMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    if(hasNetworkLayersWithActiveOsmNode(osmNode.getId())) {
      /* platform is situated on the road infrastructure. This is discouraged and arguably a tagging error, but occurs sometimes and can be salvaged by
       * extracting the platform and stop position together */
      getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode.getId());
    }else {
      /* regular platform separated from vehicle stop position; create transfer zone but no connectoids, 
       * these will be constructed during or after we have parsed relations, i.e. stop_areas */
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, defaultOsmMode, getGeoUtils());
    }   
  }   
  
  /** Extract a platform since it is deemed eligible for the PLANit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform
   * 
   * @param osmEntity to extract from
   * @param tags all tags of the OSM Node
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */    
  private void extractPtv1RailwayPlatform(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
    
    /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
     * Ptv2 stop position reference is used, so postpone creating connectoids for now, and deal with it later when stop_positions have all been parsed */
    String defaultMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultMode.equals(OsmRailModeTags.TRAIN)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 railway platform %s,",defaultMode));
    }
    getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, defaultMode, geoUtils);
  }

  /** extract a halt separate from infrastructure, i.e., not on rail tracks, but next to it. Create transfer zone without connectoids for it 
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */  
  private void extractPtv1StandAloneHalt(OsmNode osmNode, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.HALT);
        
    String expectedDefaultMode = OsmRailModeTags.TRAIN;    
    String defaultMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultMode.equals(expectedDefaultMode)) {
      LOGGER.warning(String.format("Unexpected osm mode identified for Ptv1 halt %s",defaultMode));
    }
    getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.SMALL_STATION, defaultMode, geoUtils);      
  }    

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=platform on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1HighwayPlatform(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 highway platform %s,",defaultOsmMode));
    }    
  
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(OsmModeUtils.hasMappedPlanitMode(modeResult)) {               
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, modeResult.first(), geoUtils);
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=bus_stop on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1HighwayBusStop(OsmEntity osmEntity, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = OsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 bus_stop %s,",defaultOsmMode));
    }      
    
    Pair<Collection<String>, Collection<Mode>> modeResult = getPtModeHelper().collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(OsmModeUtils.hasMappedPlanitMode(modeResult)) {
      
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.BUS_STOP);      
      if(Osm4JUtils.getEntityType(osmEntity).equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmEntity.getId())){
        
        /* bus_stop on the road and NO Ptv2 tags (or Ptv2 tags assessed and decided they should be ignored), treat it as
         * a stop_location (connectoid) rather than a waiting area (transfer zone), mark for post_processing as such */
        getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmEntity.getId());
        
      }else {
        
        /* bus_stop not on the road, only create transfer zone (waiting area), postpone creation of stop_location */
        getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.POLE, modeResult.first(), geoUtils);
      }
    }
  }   
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmway (no Ptv2 tags)
   * 
   * @param osmWay the way to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1Highway(OsmWay osmWay, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
    
    /* platform -> create transfer zone */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      extractTransferInfrastructurePtv1HighwayPlatform(osmWay, tags, getGeoUtils());
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmNode (no Ptv2 tags)
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1Highway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag, PlanitJtsCrsUtils geoUtils) throws PlanItException {       
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
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key railway=
   * @throws PlanItException thrown if error
   */ 
  private void extractTransferInfrastructurePtv1Railway(OsmWay osmWay, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
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
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1Railway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    OsmNetworkReaderSettings networkSettings = getNetworkToZoningData().getNetworkSettings();
    if(!networkSettings.isRailwayParserActive()) {
      return;
    }
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().isOsmModeActivated(OsmRailwayTags.TRAM)) {
      
      if(!hasNetworkLayersWithActiveOsmNode(osmNode.getId())){
        
        /* tagging error */
        LOGGER.info(String.format("DISCARD: Ptv1 railway=tram_stop (%d) does not reside on tram tracks", osmNode.getId()));
        
      }else {      
        
        /* mark as stop position as it resides on infrastructure, mark for post_processing to create transfer zone and connectoids for it,
         * since it might have a separate waiting platform */
        getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.TRAM_STOP);
        getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode.getId());
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
        getZoningReaderData().getOsmData().addUnprocessedStopPosition(osmNode.getId());
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

  /** Classic PT infrastructure based on Ptv2 OSM public transport scheme for an Osm way
   * 
   * @param osmWay to parse
   * @param tags of the node
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv2(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {
      String ptv2ValueTag = tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT);
      
      /* platform */
      if(OsmPtv2Tags.PLATFORM.equals(ptv2ValueTag)) {
              
        /* create transfer zone but no connectoids, these will be constructed during or after we have parsed relations, i.e. stop_areas */
        getProfiler().incrementOsmPtv2TagCounter(ptv2ValueTag);        
        getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmWay, tags, TransferZoneType.PLATFORM, OsmModeUtils.identifyPtv1DefaultMode(tags), getGeoUtils());        
      }      
      
      /* stop position */
      if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        /* should not be on a way, log and ignore */
        LOGGER.info(String.format("encountered stop_position on osm way %d, this is not properly tagged, ignored",osmWay.getId()));
      }
      
      /* stop position */
      if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore postpone parsing them until after we have parsed the relations */
        getZoningReaderData().getOsmData().addUnprocessedPtv2Station(osmWay);
      }     
      
      /* stop area */
      if(OsmPtv2Tags.STOP_AREA.equals(ptv2ValueTag)) {
        /* should not be on a way, log and ignore */
        LOGGER.info(String.format("encountered stop_area on osm way %d, this is not properly tagged, ignored",osmWay.getId()));
      }       
      
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv2) for osm way %s, but no public_transport key tag found",osmWay.getId()));
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme (no Ptv2 tags) for an Osm way
   * 
   * @param osmWay to parse
   * @param tags of the node
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    
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
      
    }else {
      throw new PlanItException(String.format("Parsing transfer infrastructure (Ptv1) for osm way %s, but no compatible key tags found",osmWay.getId()));
    }
  }  
  
 
  /** Classic PT infrastructure based on Ptv2 OSM public transport scheme for osm node.
   * 
   * @param osmNode to parse
   * @param tags of the node
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv2(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {
      String ptv2ValueTag = tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT);
      
      /* platform */
      if(OsmPtv2Tags.PLATFORM.equals(ptv2ValueTag)) {
                
        extractPtv2Platform(osmNode, tags);                     
      }            
      /* stop position */
      else if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        
        extractPtv2StopPosition(osmNode, tags);

      }     
      /* station */
      else if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore postpone parsing them until after we have parsed the relations */
        getZoningReaderData().getOsmData().addUnprocessedPtv2Station(osmNode);
        getProfiler().incrementOsmPtv2TagCounter(OsmPtv1Tags.STATION);
      }           
      /* stop area */
      else if(OsmPtv2Tags.STOP_AREA.equals(ptv2ValueTag)) {
        /* should not be on a node, log and ignore */
        LOGGER.info(String.format("encountered stop_area on osm node %d, this is not properly tagged, ignored",osmNode.getId()));
      }          
                  
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv2) for osm node %s, but no compatible key tags found",osmNode.getId()));
    }
  }
  
  /** Classic PT infrastructure based on original OSM public transport scheme (not Ptv2 tags) for osm node
   * 
   * @param osmNode to parse
   * @param tags of the node
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1(OsmNode osmNode, Map<String, String> tags, PlanitJtsCrsUtils geoUtils) throws PlanItException {    
        
    if(OsmHighwayTags.hasHighwayKeyTag(tags) && getNetworkToZoningData().getNetworkSettings().isHighwayParserActive()) {
      
      String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
      extractTransferInfrastructurePtv1Highway(osmNode, tags, ptv1ValueTag, geoUtils);
      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
      extractTransferInfrastructurePtv1Railway(osmNode, tags, ptv1ValueTag, geoUtils);     
      
    }else {
      throw new PlanItException("parsing transfer infrastructure (Ptv1) for osm node %s, but no compatible key tags found",osmNode.getId());
    }  
        
  }  
  
  /** Extract stop area relation of Ptv2 scheme. We create transfer zone groups for each valid stop_area, connect it to the transfer zones but do not yet created connectoids for the stop positions.
   * This is left to post-processing. We also mark processed stations, platforms, etc., such that after all
   * stop_areas have been processed, we can extract planit instances for the remaining unprocessed osm entities, knowing they do not belong to any stop_area and
   * constitute their own separate entity.
   * 
   * @param osmRelation to extract stop_area for
   * @param tags of the stop_area relation
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaRelation(OsmRelation osmRelation, Map<String, String> tags) throws PlanItException{
  
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
        
        registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, member);
        
      }
      /* stop_position */
      else if(member.getRole().equals(OsmPtv2Tags.STOP_ROLE)) {
        
        /* stop_position processing is postponed to post_processing, do nothing for valid stop_positions */ 
        
        /* Identify wrongly tagged members that state to be of role: stop, but are in fact platforms, or stations and need to be registered as such */
        if(isPtv2StopAreaWronglyTaggedStopRole(member)) {
          
          /* we support wrongly tagged: stations, platforms that have already been identified in standalone fashion */
          salvageWronglyTaggedStopRolePtv2StopAreaRelation(transferZoneGroup, osmRelation, member);
          /* flag to not process as stop_position in post-processing since it is not a stop_position and invalidly tagged as such */
          getZoningReaderData().getOsmData().addIgnoreStopAreaStopPosition(member.getType(), member.getId());          
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
   * @throws PlanItException thrown if error
   */
  private void extractPtv2OuterRolePlatformRelation(OsmRelation osmRelation, OsmRelationMember member, Map<String, String> tags) throws PlanItException {
    /* try if it has been parsed, not if it has no tags (likely), yes if it has PTv2 tags (unlikely for multipolygon member)) */
    TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(EntityType.Way, member.getId());
    if(transferZone == null && getZoningReaderData().getOsmData().hasOuterRoleOsmWay(member.getId())) {
      /* collect from unprocessed ways, should be present */
      OsmWay unprocessedWay = getZoningReaderData().getOsmData().getOuterRoleOsmWay(member.getId());
      if(unprocessedWay == null) {
        LOGGER.severe(String.format("Osm way %d referenced by Ptv2 multipolygon %d not available in parser, this should not happen, relation ignored",member.getId(),osmRelation.getId()));
        return;
      }
            
      /* create transfer zone, use tags of relation that contain the PT information */
      getTransferZoneHelper().createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
          unprocessedWay, tags, TransferZoneType.PLATFORM, OsmModeUtils.identifyPtv1DefaultMode(tags), getGeoUtils());
    }
  }

  /** extract Ptv2 platforms from the multi-polygon. Do not create connectoids for them as the stop_positions
   * might not have been parsed yet. For multi-polygons we use the outer polygon as their shape and ignore the rest.
   * Since this shape is modelled as an Osmway, we must find it. Since the Osmway is not tagged as public transport (likely), we must use 
   * the unprocessed ways we identified for this purpose in pre-processing. For the found OsmWay we create a transfer zone (without connectoids)
   * 
   * @param osmRelation to extract platform from
   * @param tags to use
   * @throws PlanItException thrown if error
   */
  private void extractPtv2PlatformRelation(OsmRelation osmRelation, Map<String, String> tags) throws PlanItException {
    
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
   * @throws PlanItException thrown if error
   */  
  protected void extractTransferInfrastructure(OsmNode osmNode, OsmPtVersionScheme ptVersion, Map<String, String> tags) throws PlanItException{
    
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
   * @throws PlanItException thrown if error
   */
  protected void extractTransferInfrastructure(OsmWay osmWay, OsmPtVersionScheme ptVersion, Map<String, String> tags) throws PlanItException{
    if(ptVersion == OsmPtVersionScheme.VERSION_2) {
      extractTransferInfrastructurePtv2(osmWay, tags);
    }else if(ptVersion == OsmPtVersionScheme.VERSION_1) {
      extractTransferInfrastructurePtv1(osmWay, tags);
    }
  }    


  /**
   * Constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param zoningToPopulate to populate
   * @param profiler to use
   */
  public OsmZoningProcessingHandler(
      final OsmPublicTransportReaderSettings transferSettings, 
      final OsmZoningReaderData zoningReaderData,  
      final Zoning zoningToPopulate,
      final OsmZoningHandlerProfiler profiler) {
    super(transferSettings, zoningReaderData, zoningToPopulate, profiler);    
  }
  
  /**
   * Call this before we parse the OSM network to initialise the handler properly
   * 
   * @throws PlanItException thrown if error
   */
  public void initialiseBeforeParsing() throws PlanItException {
    reset();
    
    PlanItException.throwIf(
        getSettings().getReferenceNetwork().getTransportLayers() == null || getSettings().getReferenceNetwork().getTransportLayers().size()<=0,
          "Network is expected to be populated at start of parsing OSM zoning");
  }    

  /**
   * Construct PLANit nodes/connectoids/transferzones from OSM nodes when relevant
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {             
        
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);          
    try {
            
      /* only parse nodes that are potentially used for (PT) transfers*/
      OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(tags);
      if(ptVersion != OsmPtVersionScheme.NONE) {
        
        /* skip if marked explicitly for exclusion */
        if(skipOsmNode(osmNode)) {
          LOGGER.fine(String.format("Skipped osm node %d, marked for exclusion", osmNode.getId()));
          return;
        }
        
        /* verify if within designated bounding polygon */
        if(!isCoveredByZoningBoundingPolygon(osmNode)) {
          return;
        }         
        
        /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
        extractTransferInfrastructure(osmNode, ptVersion, tags);
      }
      
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM node (id:%d) for transfer infrastructure", osmNode.getId())); 
    }     
  }

  /**
   * parse an OSM way to extract for example platforms, or other transfer zone related geometry
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
                                    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);  
    
    try {       
      
      /* only parse ways that are potentially used for (PT) transfers*/
      OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(tags);
      if(ptVersion != OsmPtVersionScheme.NONE || getZoningReaderData().getOsmData().shouldOsmRelationOuterRoleOsmWayBeKept(osmWay)) {
        
        if(skipOsmWay(osmWay)) {
          LOGGER.fine(String.format("Skipped osm way %d, marked for exclusion", osmWay.getId()));
          return;
        }                    
        
        if(ptVersion != OsmPtVersionScheme.NONE ) {
          /* regular pt entity*/
          
          /* skip if none of the OSM way nodes fall within the zoning bounding box*/
          if(!isCoveredByZoningBoundingPolygon(osmWay)) {
            return;
          }   
        
          /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
          extractTransferInfrastructure(osmWay, ptVersion, tags);
        
        }else{
          /* multi-polygon used as pt platform */
          
          if(isCoveredByZoningBoundingPolygon(osmWay)) {
            /* even though osm way itself appears not to be public transport related, it is marked for keeping
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
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM way (id:%d) for transfer infrastructure", osmWay.getId())); 
    }      
            
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
          
          /* stop_area: all but stop_positions (parsed in post-processing)*/
          if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_AREA)) {
            
            getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_AREA);
            extractPtv2StopAreaRelation(osmRelation, tags);
            
          }else {
            /* anything else is not expected */
            LOGGER.info(String.format("DISCARD: The public_transport relation type `%s` (%d) not (yet) supported",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
          }          
          
        }
        
        /* multi_polygons can represent public transport platforms */ 
        if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.MULTIPOLYGON)) {
          
          /* only consider public_transport=platform multi-polygons */
          if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
            
            getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.PLATFORM_ROLE);
            extractPtv2PlatformRelation(osmRelation, tags);
            
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
        
    LOGGER.fine(" OSM transfer zone group parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // do nothing yet
  }

  
}

package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderPlanitData;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.zoning.PlanitOsmPublicTransportSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations to the relevant transfer zones. 
 * 
 * @author markr
 * 
 *
 */
public class PlanitOsmZoningHandler extends PlanitOsmZoningBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningHandler.class.getCanonicalName());     
  
  /** add the node's transfer zone (if it can be found) to the provided transfer zone group
   * 
   * @param transferZoneGroup to add to
   * @param osmNode to collect parsed transfer zone for to add
   * @param tags, String osmDefaultMode of the node
   */
  private void addPtv2StopAreaMemberTransferZoneToGroup(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    
    /* register transfer zone if it exists on group */
    TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(EntityType.Node, osmNode.getId());
    if(transferZone ==null) {            
      Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmNode.getId(), tags, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags));
      if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {      
        /* no match, this is only valid when the osm entity (platform) has no eligible modes tagged and therefore no transfer zone was ever created (correctly) */        
        LOGGER.warning(String.format("Found stop_area %d node member %d that complies with %s, has no role and no PLANit transfer zone information available, ignored",osmRelation.getId(), osmNode.getId()));
      }
      return;
    }
    transferZoneGroup.addTransferZone(transferZone);
  }    
  
  /**
   * After handlings pt nodes, ways, and relations, we can identify all transfer zones (platforms, poles) that have successfully been mapped to connectoids (stop_locations)
   * doing so removes them from the pool of remaining transferzones that still require connectoids and are up for post-processing.
   */
  private void identifyCompletedTransferZones() {
    identifyCompletedTransferZones(EntityType.Node, 
        new HashMap<Long, TransferZone>(getZoningReaderData().getPlanitData().getIncompleteTransferZonesByOsmId(EntityType.Node)));
    identifyCompletedTransferZones(EntityType.Way, 
        new HashMap<Long, TransferZone>(getZoningReaderData().getPlanitData().getIncompleteTransferZonesByOsmId(EntityType.Way)));
  }
  
  /**
   * After handling pt nodes, ways, and relations, we can identify all transfer zones (platforms, poles) that have successfully been mapped to connectoids (stop_locations)
   * doing so removes them from the pool of remaining transferzones that still require connectoids and are up for post-processing.
   * 
   * @param entityType of the zones
   * @param transferZonesToVerify zones to verify
   */
  private void identifyCompletedTransferZones(EntityType entityType, Map<Long, TransferZone> transferZonesToVerify) {
    if(transferZonesToVerify== null) {
      return;
    }
    PlanitOsmZoningReaderPlanitData planitData = getZoningReaderData().getPlanitData();
    for(Entry<Long, TransferZone> entry : transferZonesToVerify.entrySet()) {
      if(planitData.hasConnectoids(entry.getValue())){
        /* connectoids available, already, assumed to be complete, remove from incomplete transfer zone register */
        planitData.removeIncompleteTransferZone(entityType, Long.valueOf(entry.getValue().getExternalId()));
      }
    }
    
  }  
  
  /** Verify if the stop role member is indeed a stop_position, if so do nothing, if it is wrongly tagged
   * process it the way it should have been if it was tagged with the correct role.
   * 
   * @param transferZoneGroup the relation belongs to
   * @param osmRelation the osm relation
   * @param member the member of the relation to check
   * @return true when wrongly tagged role found for this stop_position, false otherwise
   */
  private boolean identifyPtv2StopAreaWronglyTaggedStopRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    
    boolean wronglyTaggedRole = false;
    if(member.getType() == EntityType.Node) {
      OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);
      if(!(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_POSITION))) {
        wronglyTaggedRole = true;
      }
    }else {
      /* stop_position is always a node: identified problem */
      wronglyTaggedRole = true;
    }
    
    /* we support wrongly tagged: stations, platforms that have already been identified in standalone fashion */
    if(wronglyTaggedRole) {
      /* station? -> then we it should exist as unprocessed station */
      Pair<OsmPtVersionScheme, OsmEntity> unprocessedStationPair = getZoningReaderData().getOsmData().getUnprocessedStation(member.getType(), member.getId());
      if(unprocessedStationPair != null) {
        /* station -> process it as such */
        LOGGER.info(String.format("SALVAGED: stop_area %s member %d incorrectly given stop role...identified as station", transferZoneGroup.getExternalId(), member.getId()));
        updateTransferZoneGroupStationName(transferZoneGroup, unprocessedStationPair.second(), OsmModelUtil.getTagsAsMap(unprocessedStationPair.second()));          
      }      
      /* platform? --> then we should already have a transfer zone for it*/      
      else if(getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(member.getType(), member.getId())!=null) {
        LOGGER.info(String.format("SALVAGED: stop_area %s member %d incorrectly given stop role...identified as platform", transferZoneGroup.getExternalId(), member.getId()));
        /* platform -> process as such */
        registerPtv2StopAreaPlatformOnGroup(transferZoneGroup, osmRelation, member);
      }else {
        LOGGER.warning(String.format("DISCARD: stop_area %s member %d incorrectly given stop role...remains unidentified", transferZoneGroup.getExternalId(), member.getId()));
      }
      
      /* flag to not process as stop_position in post-processing since it is not a stop_position and invalidly tagged as such */
      getZoningReaderData().getOsmData().addInvalidStopAreaStopPosition(member.getType(), member.getId());      
    }
    
    return wronglyTaggedRole;
  }  
  
  /** process a stop_area member that has no role tag identifying its purpose but it is a Ptv1 supporting entity for highways. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmNode of the relation to process
   * @param tags of the node
   */   
  private void processPtv2StopAreaMemberNodePtv1HighwayWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
    if(ptv1ValueTag==null) {
      LOGGER.severe(String.format("highway tag not present for alleged Ptv1 highway %d on stop_area %d, this should not happen ignored", osmNode.getId(), osmRelation.getId()));
      return;
    }

    /* bus stop */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){

      /* register on group */
      addPtv2StopAreaMemberTransferZoneToGroup(transferZoneGroup, osmRelation, osmNode, tags);      
      
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, OsmRoadModeTags.BUS);
      
    }else {
      LOGGER.warning(String.format("unsupported Ptv1 higway=%s tag encountered, ignored",ptv1ValueTag));
    }     
    
  }  
  
  /** process a stop_area member that has no role tag identifying its purpose but it is a Ptv1 supporting entity for railways. Based on already parsed entities we attempt
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
    if(getNetworkToZoningData().getSettings().getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      
      /* train station or halt (not for trams) */
      if(OsmTagUtils.matchesAnyValueTag(ptv1ValueTag, OsmPtv1Tags.STATION, OsmPtv1Tags.HALT)) {
        
        /* use name only */
        updateTransferZoneGroupStationName(transferZoneGroup, osmNode, tags);
        getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
        
      }
      /* train platform */
      else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag) ) {
      
        /* register on group */
        addPtv2StopAreaMemberTransferZoneToGroup(transferZoneGroup, osmRelation, osmNode, tags);
        
      }
              
    }
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag)) {
    
      /* register on group */
      addPtv2StopAreaMemberTransferZoneToGroup(transferZoneGroup, osmRelation, osmNode, tags);
    
    }
    
    /* entrances/exits */
    if(OsmPtv1Tags.SUBWAY_ENTRANCE.equals(ptv1ValueTag) && getNetworkToZoningData().getSettings().getHighwaySettings().hasMappedPlanitMode(OsmRoadModeTags.FOOT)) {
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
   */
  private void processPtv2StopAreaMemberNodePtv1WithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    
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
      
      updateTransferZoneGroupStationName(transferZoneGroup, osmNode, tags);
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
   */  
  private void processPtv2StopAreaMemberNodeWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode) {
    
    /* determine pt version */
    Map<String, String> osmNodeTags = OsmModelUtil.getTagsAsMap(osmNode);
    OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(osmNodeTags);
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
    updateTransferZoneGroupStationName(transferZoneGroup, osmStation, tags);
    /* on each transfer zone on the group (we must use relation because not all transfer zones might be registered on the group yet */
    for(int index=0;index<osmRelation.getNumberOfMembers();++index) {
      OsmRelationMember transferZoneMember = osmRelation.getMember(index);
      TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(transferZoneMember.getType(), osmStation.getId());
      if(transferZone!=null) {
        updateTransferZoneStationName(transferZone, tags);
      }
    }
    OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(tags);
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
      TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(EntityType.Way, osmWayMember.getId());
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

  /** process a stop_area member that has no role tag identifying its purpose. Based on already parsed entities we attempt
   * to register it on the transfer group if possible. For example, if it is a bus_stop, then we obtain the node and parsed transfer zone
   * and register that transfer zone to the group so it is properly available
   * 
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param member of the relation to process
   */
  private void processPtv2StopAreaMemberWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    
    if(member.getType() == EntityType.Node) {
            
      /* collect osm node */
      OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
      if(osmNode == null) {
        LOGGER.warning(String.format("unable to collect osm node %d referenced in stop_area %d", member.getId(), osmRelation.getId()));
        return;
      }
      
      /* process as node, still it either adds an already parsed entity to the group, or marks unprocessed entities as processed so they are no longer
       * required to be processed as stand-alone Pt entities (not part of a stop_area) */
      processPtv2StopAreaMemberNodeWithoutRole(transferZoneGroup, osmRelation, osmNode);       
      
    }else if(member.getType() == EntityType.Way){
            
      /* we do not store all osm ways in memory, so we pass along the member information instead and attempt to still process the way best we can */
      processPtv2StopAreaMemberWayWithoutRole(transferZoneGroup, osmRelation, member);     
      
    }else {      
      LOGGER.info(String.format("stop_area (%d) member without a role found (%d) that is not a node or way, ignored",osmRelation.getId(),member.getId()));
    }
  }  
     
    
  /** create a transfer zone group based on the passed in osm entity, tags for feature extraction and access
   * @param osmRelation the stop_area is based on 
   * @param tags tags to extract features from
   * @param transferZoneGroupType the type of the transfer zone group 
   * @return transfer zone group created
   */  
  private TransferZoneGroup createAndPopulateTransferZoneGroup(OsmRelation osmRelation, Map<String, String> tags) {
      /* create */
      TransferZoneGroup transferZoneGroup = getZoning().transferZoneGroups.createNew();
            
      /* XML id = internal id*/
      transferZoneGroup.setXmlId(String.valueOf(transferZoneGroup.getId()));
      /* external id  = osm node id*/
      transferZoneGroup.setExternalId(String.valueOf(osmRelation.getId()));
      
      /* name */
      if(tags.containsKey(OsmTags.NAME)) {
        transferZoneGroup.setName(tags.get(OsmTags.NAME));
      }    
      
      return transferZoneGroup;
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
   * @throws PlanItException thrown if error
   */  
  private void extractPtv1TransferZoneWithConnectoidsAtStopPosition(OsmNode osmNode, Map<String, String> tags, String defaultMode) throws PlanItException {
    if(getSettings().isOverwriteStopLocationWaitingArea(osmNode.getId())) {       
      /* postpone processing of stop location when all transfer zones (waiting areas) have been created, but do mark this location as an unprocessed stop_position */
      getZoningReaderData().getOsmData().addUnprocessedPtv2StopPosition(osmNode.getId());
    }else {              
      /* In the special case a Ptv1 tag for a tram_stop, bus_stop, halt, or station is supplemented with a Ptv2 stop_position we must treat this as stop_position AND transfer zone in one and therefore 
       * create a transfer zone immediately */      
      createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmNode, tags, defaultMode);
    }
  }    
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=platform on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1HighwayPlatform(OsmEntity osmEntity, Map<String, String> tags) {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 highway platform %s,",defaultOsmMode));
    }    

    Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmEntity.getId(), tags, defaultOsmMode);
    if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {               
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, modeResult.first());
    }
  }  
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=bus_stop on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1HighwayBusStop(OsmEntity osmEntity, Map<String, String> tags) {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 bus_stop %s,",defaultOsmMode));
    }      
    
    Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmEntity.getId(), tags, defaultOsmMode);
    if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {           
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.BUS_STOP);
      createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.POLE, modeResult.first());
    }
  }   

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmNode (no Ptv2 tags)
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1Highway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
        
    /* bus stop -> create transfer zone */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){
      
      extractTransferInfrastructurePtv1HighwayBusStop(osmNode, tags);
      
    }
    /* platform -> create transfer zone */
    else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){ 
      
      extractTransferInfrastructurePtv1HighwayPlatform(osmNode, tags);
      
    }else {
      LOGGER.warning(String.format("unsupported Ptv1 higway=%s tag encountered, ignored",ptv1ValueTag));
    }            
  }  
  

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmway (no Ptv2 tags)
   * 
   * @param osmWay the way to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1Highway(OsmWay osmWay, Map<String, String> tags, String ptv1ValueTag) {
    
    /* platform -> create transfer zone */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      extractTransferInfrastructurePtv1HighwayPlatform(osmWay, tags);
    }
  }

  /** extract a tram stop since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dtram_stop
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */
  private void extractPtv1TramStop(OsmNode osmNode, Map<String, String> tags) throws PlanItException {    
        
    /* in contrast to (normal) highway=bus_stop this tag is placed on the track, so we can and will create connectoids immediately */
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.TRAM_STOP);
    extractPtv1TransferZoneWithConnectoidsAtStopPosition(osmNode, tags, OsmRailModeTags.TRAM);
  }  
  
  /** extract a halt since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dhalt
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */  
  private void extractPtv1Halt(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.HALT);
        
    String expectedDefaultMode = OsmRailModeTags.TRAIN;
    if(hasNetworkLayersWithActiveOsmNode(osmNode.getId())) {
      
      extractPtv1TransferZoneWithConnectoidsAtStopPosition(osmNode, tags, expectedDefaultMode);
      
    }else {
      /* halt not on railway, just create transfer zone at this point */
      
      String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
      if(!defaultMode.equals(expectedDefaultMode)) {
        LOGGER.warning(String.format("Unexpected osm mode identified for Ptv1 halt %s",defaultMode));
      }
      createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.SMALL_STATION, defaultMode);      
    }
  }

  /** extract a platform since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform
   * 
   * @param osmEntity to extract from
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */    
  private void extractPtv1RailwayPlatform(OsmEntity osmEntity, Map<String, String> tags) {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
    
    /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
     * Ptv2 stop position reference is used, so postpone creating connectoids for now, and deal with it later when stop_positions have all been parsed */
    String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultMode.equals(OsmRailModeTags.TRAIN)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 railway platform %s,",defaultMode));
    }
    createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, defaultMode);
  }
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag railway=* for an OsmNode
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key railway=
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1Railway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
    PlanitOsmNetworkSettings networkSettings = getNetworkToZoningData().getSettings();
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasMappedPlanitMode(OsmRailwayTags.TRAM)) {
      
      extractPtv1TramStop(osmNode, tags);
    }
    
    /* train platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      extractPtv1RailwayPlatform(osmNode, tags);
    }          
    
    /* train halt (not for trams)*/
    if(OsmPtv1Tags.HALT.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      extractPtv1Halt(osmNode, tags);
    }
    
    /* train station (not for trams) */
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      getZoningReaderData().getOsmData().addUnprocessedPtv1Station(osmNode);
    }    
  }


  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag railway=* for an OsmWay
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key railway=
   * @throws PlanItException thrown if error
   */ 
  private void extractTransferInfrastructurePtv1Railway(OsmWay osmWay, Map<String, String> tags, String ptv1ValueTag) {
    PlanitOsmNetworkSettings networkSettings = getNetworkToZoningData().getSettings();
    
    /* platform edge */
    if(OsmPtv1Tags.PLATFORM_EDGE.equals(ptv1ValueTag)) {
      
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      /* platform edges are for additional geographic information, nothing requires them to be there in our format, so we take note
       * but do not parse, see also https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform_edge */
    }
    
    /* platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {

      extractPtv1RailwayPlatform(osmWay, tags);      
    }  
    
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      getZoningReaderData().getOsmData().addUnprocessedPtv1Station(osmWay);
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
        getProfiler().incrementOsmPtv2TagCounter(ptv2ValueTag);
        
        /* create transfer zone but no connectoids, these will be constructed during or after we have parsed relations, i.e. stop_areas */
        createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmWay, tags, TransferZoneType.PLATFORM, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags));        
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

      String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
      extractTransferInfrastructurePtv1Highway(osmWay, tags, ptv1ValueTag);      
      
    }
    /* PTv1 railway=* */
    else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
      extractTransferInfrastructurePtv1Railway(osmWay, tags, ptv1ValueTag);       
      
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv1) for osm way %s, but no compatible key tags found",osmWay.getId()));
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
        
        getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.PLATFORM);
        /* create transfer zone but no connectoids, these will be constructed during or after we have parsed relations, i.e. stop_areas */
        createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags));        
      }            
      /* stop position */
      else if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
                
        if(PlanitOsmHandlerHelper.isPtv2StopPositionPtv1Stop(osmNode, tags)) { 
          LOGGER.fine(String.format("Ptv2 stop_position tag combined with Ptv1 tagging, redirected to Ptv1 parser for osm node %d",osmNode.getId()));
          /* often people mistakenly add stop_position to bus_stops next to the road, or to tram_stops on the tracks etc. This is a tagging error and should instead be 
           * treated as Ptv1 infrastructure rather than a Ptv2 stop. Therefore redirect */
          extractTransferInfrastructurePtv1(osmNode, tags);                                     
          
        }else {
          /* stop positions relate to connectoids that provide access to transfer zones. The transfer zones are based on platforms, but these might not have been
           * processed yet. So, we postpone parsing of all stop positions, and simply track them for delayed processing after all platforms/transfer zones have been identified */
          getZoningReaderData().getOsmData().addUnprocessedPtv2StopPosition(osmNode.getId());          
        }                          
        
      }     
      /* stop position */
      else if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore postpone parsing them until after we have parsed the relations */
        getZoningReaderData().getOsmData().addUnprocessedPtv2Station(osmNode);
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
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1(OsmNode osmNode, Map<String, String> tags) throws PlanItException {    
        
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
      extractTransferInfrastructurePtv1Highway(osmNode, tags, ptv1ValueTag);
      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
      extractTransferInfrastructurePtv1Railway(osmNode, tags, ptv1ValueTag);     
      
    }else {
      throw new PlanItException("parsing transfer infrastructure (Ptv1) for osm node %s, but no compatible key tags found",osmNode.getId());
    }  
        
  }  
  
  /** extract the platform member of a Ptv2 stop_area and register it on the transfer zone group
   * 
   * @param transferZoneGroup to register on
   * @param osmRelation the platform belongs to
   * @param member the platform member
   */
  private void registerPtv2StopAreaPlatformOnGroup(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    
    /* should be parsed (without connectoids), connect to group and let stop_positions create connectoids */
    TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(member.getType(), member.getId());
    if(transferZone==null) {
      /* not parsed due to problems, discard */
      LOGGER.warning(String.format("DISCARD: platform for OSM entity %d not available, although referenced by stop_area %d",member.getId(),osmRelation.getId()));
    }else {
      transferZoneGroup.addTransferZone(transferZone);
    }
  }


  /** extract stop area relation of Ptv2 scheme. We create transfer zone groups for each valid stop_area, connect it to the transfer zones but do not yet created connectoids for the stop positions.
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
    TransferZoneGroup transferZoneGroup = createAndPopulateTransferZoneGroup(osmRelation, tags);
    getZoning().transferZoneGroups.register(transferZoneGroup);
    getZoningReaderData().getPlanitData().addTransferZoneGroupByOsmId(osmRelation.getId(), transferZoneGroup);    
    
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
        
        /* stop_positions are processed in post_processing, here we only identify wrongly tagged
         * members that state to be of role: stop, but are in fact platforms, or stations and need to be registered as such */
        identifyPtv2StopAreaWronglyTaggedStopRole(transferZoneGroup, osmRelation, member);         
        
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

  /** extract Ptv2 platforms from the multi-polygon. Do not create connectoids for them as the stop_positions
   * might not have been parsed yet. For multi-polygons we use the outer polygon as their shape and ignore the rest.
   * Since this shape is modelled as an Osmway, we must find it. Since the Osmway is not tagged as public transport (likely), we must use 
   * the unprocessed ways we identified for this purpose in pre-processing. For the found OsmWay we create a transfer zone (without connectoids)
   * 
   * @param osmRelation to extract platform from
   * @param tags to use
   */
  private void extractPtv2MultiPolygonPlatformRelation(OsmRelation osmRelation, Map<String, String> tags) {
    
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      
      if( skipOsmPtEntity(member)) {
        continue;
      }      
      
      /* role:outer -> extract geometry for transfer zone*/
      if(member.getRole().equals(OsmMultiPolygonTags.OUTER_ROLE)) {
         
        /* try if it has been parsed, not if it has no tags (likely), yes if it has PTv2 tags (unlikely for multipolygon member)) */
        TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZoneByOsmId(EntityType.Way, member.getId());
        if(transferZone == null) {
          /* collect from unprocessed ways, should be present */
          OsmWay unprocessedWay = getZoningReaderData().getOsmData().getUnprocessedMultiPolygonOsmWay(member.getId());
          if(unprocessedWay == null) {
            LOGGER.severe(String.format("Osm way %d referenced by Ptv2 multipolygon %d not available in parser, this should not happen, relation ignored",member.getId(),osmRelation.getId()));
            return;
          }
          
          /* create transfer zone, use tags of relation that contain the PT information */
          createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(unprocessedWay, tags, TransferZoneType.PLATFORM, PlanitOsmModeUtils.identifyPtv1DefaultMode(tags));
        }
      }
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
      extractTransferInfrastructurePtv1(osmNode, tags);
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
   * constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param network2ZoningData data collated from parsing network required to successfully popualte the zoning
   * @param zoningToPopulate to populate
   * @param profiler to use
   */
  public PlanitOsmZoningHandler(
      final PlanitOsmPublicTransportSettings transferSettings, 
      final PlanitOsmZoningReaderData zoningReaderData, 
      final PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {
    super(transferSettings, zoningReaderData,network2ZoningData, zoningToPopulate, profiler);    
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    reset();
    
    PlanItException.throwIf(
        this.getNetworkToZoningData().getOsmNetwork().infrastructureLayers == null || this.getNetworkToZoningData().getOsmNetwork().infrastructureLayers.size()<=0,
          "network is expected to be populated at start of parsing OSM zoning");
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // do nothing
  }  

  /**
   * construct PLANit nodes/connectoids/transferzones from OSM nodes when relevant
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {
        
    if(skipOsmNode(osmNode)) {
      LOGGER.fine(String.format("Skipped osm node %d, marked for exclusion", osmNode.getId()));
      return;
    }
    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);          
    try {              
      
      /* only parse nodes that are potentially used for (PT) transfers*/
      OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(tags);
      if(ptVersion != OsmPtVersionScheme.NONE) {
        
        /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
        extractTransferInfrastructure(osmNode, ptVersion, tags);
      }
      
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM node (id:%d) for transfer infrastructure", osmNode.getId())); 
    }     
  }

  /**
   * parse an osm way to extract for example platforms, or other transfer zone related geometry
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
    
    if(skipOsmWay(osmWay)) {
      LOGGER.fine(String.format("Skipped osm way %d, marked for exclusion", osmWay.getId()));
      return;
    }
                    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
    try {       
      
      /* only parse ways that are potentially used for (PT) transfers*/
      OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(tags);
      if(ptVersion != OsmPtVersionScheme.NONE) {
        
        /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
        extractTransferInfrastructure(osmWay, ptVersion, tags);
        
      }else if(getZoningReaderData().getOsmData().shouldMultiPolygonOsmWayBeKept(osmWay)) {
        
        /* even though osm way itself appears not to be public transport related, it is marked for keeping
         * so we keep it. This occurs when way is part of relation (multipolygon) where its shape represents for
         * example the outline of a platform, but all pt tags reside on the relation and not on the way itself. 
         * Processing of the way is postponed until we parse relations */
        getZoningReaderData().getOsmData().addUnprocessedMultiPolygonOsmWay(osmWay);
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
            LOGGER.info(String.format("unsupported public_transport relation `%s` (%d) ignored",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
          }          
          
        }
        
        /* multi_polygons can represent public transport platforms */ 
        if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.MULTIPOLYGON)) {
          
          /* only consider public_transport=platform multi-polygons */
          if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
            
            getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.PLATFORM_ROLE);
            extractPtv2MultiPolygonPlatformRelation(osmRelation, tags);
            
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
    
    /* stats*/
    getProfiler().logProcessingStats(getZoning());     
    
    /* mark all transfer zones that now have been mapped to connectoids as such, this ensures
     * they are not considered for further post-processing */
    identifyCompletedTransferZones();
    
    LOGGER.info(" OSM (transfer) zone parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // do nothing yet
  }

  
}

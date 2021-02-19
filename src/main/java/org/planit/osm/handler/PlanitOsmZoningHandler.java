package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Node;
import org.planit.utils.zoning.DirectedConnectoid;
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
  
  /** add the node's transfer zone (if t can be found) to the provided transfer zone group
   * 
   * @param transferZoneGroup to add to
   * @param osmNode to collect parsed transfer zone for to add
   * @param tags, String osmDefaultMode of the node
   * @param osmDefaultMode the mode that by default is expected to be access on this transfer zone, even if no explicit tags are present to indicate so, can be null
   */
  private void addStopAreaMemberTransferZoneToGroup(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags, String osmDefaultMode) {
    
    /* register transfer zone if it exists on group */
    TransferZone transferZone = getZoningReaderData().getTransferZoneWithoutConnectoid(EntityType.Node, osmNode.getId());
    if(transferZone ==null) {      
      
      /* no match, this is only valid when the platform has no eligible modes mapped and therefore no transfer zone was ever created (correctly) */
      Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmNode.getId(), tags, osmDefaultMode);    
      if(getNetworkToZoningData().getSettings().hasAnyMappedPlanitMode(eligibleOsmModes)) {        
        LOGGER.warning(String.format("found stop_area %d node member %d that complies with %s, has no role and no PLANit transfer zone information available, ignored",osmRelation.getId(), osmNode.getId()));
      }
      return;
    }
    transferZoneGroup.addTransferZone(transferZone);
  }  
  
  /** process a stop_area member that is a train station. For this to register on the group, we only see if we can utilise its name and use it for the group, but only
   * if the group does not already have a name
   *   
   * @param transferZoneGroup the member relates to 
   * @param osmRelation relating to the group
   * @param osmEntity of the relation to process
   * @param tags of the node
   */
  private void processStopAreaMemberStation(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmEntity osmEntity, Map<String, String> tags) {
    
    if(!transferZoneGroup.hasName()) {
      String stationName = tags.get(OsmTags.NAME);
      if(stationName!=null) {
        transferZoneGroup.setName(stationName);
      }
    }
    
    OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(tags);
    getZoningReaderData().removeUnproccessedStation(ptVersion, osmEntity);  
    
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
  private void processStopAreaMemberNodePtv1HighwayWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
    if(ptv1ValueTag==null) {
      LOGGER.severe(String.format("highway tag not present for alleged Ptv1 highway %d on stop_area %d, this should not happen ignored", osmNode.getId(), osmRelation.getId()));
      return;
    }

    /* bus stop */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag) && getNetworkToZoningData().getSettings().getHighwaySettings().hasMappedPlanitMode(OsmRoadModeTags.BUS)){

      /* register on group */
      addStopAreaMemberTransferZoneToGroup(transferZoneGroup, osmRelation, osmNode, tags, OsmRoadModeTags.BUS);      
      
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.PLATFORM);
      addEligibleAccessModesToTransferZone(transferZone, osmNode.getId(), tags, OsmRoadModeTags.BUS);
      
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
  private void processStopAreaMemberNodePtv1RailwayWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
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
        processStopAreaMemberStation(transferZoneGroup, osmRelation, osmNode, tags);
        getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.STATION);
        
      }
      /* train platform */
      else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag) ) {
      
        /* register on group */
        addStopAreaMemberTransferZoneToGroup(transferZoneGroup, osmRelation, osmNode, tags, OsmRailModeTags.TRAIN);
        
      }
              
    }
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && getNetworkToZoningData().getSettings().getRailwaySettings().hasMappedPlanitMode(OsmRailModeTags.TRAM)) {
    
      /* register on group */
      addStopAreaMemberTransferZoneToGroup(transferZoneGroup, osmRelation, osmNode, tags, OsmRailModeTags.TRAM);
    
    }
    
    /* entrances/exits */
    if(OsmPtv1Tags.SUBWAY_ENTRANCE.equals(ptv1ValueTag) && getNetworkToZoningData().getSettings().getHighwaySettings().hasMappedPlanitMode(OsmRoadModeTags.FOOT)) {
      // entrances are to be converted to connectoids in post_processing
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
  private void processStopAreaMemberNodePtv1WithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    
    if(OsmRailwayTags.hasRailwayKeyTag(tags)) {     
      
      processStopAreaMemberNodePtv1RailwayWithoutRole(transferZoneGroup, osmRelation, osmNode, tags);
      
    }else if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      
      processStopAreaMemberNodePtv1HighwayWithoutRole(transferZoneGroup, osmRelation, osmNode, tags);
      
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
  private void processStopAreaMemberNodePtv2WithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode, Map<String, String> tags) {
    
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STATION)) {
      
      processStopAreaMemberStation(transferZoneGroup, osmRelation, osmNode, tags);
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
  private void processStopAreaMemberNodeWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmNode osmNode) {
    
    /* determine pt version */
    Map<String, String> osmNodeTags = OsmModelUtil.getTagsAsMap(osmNode);
    OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(osmNodeTags);
    /* process by version */
    switch (ptVersion) {
      case VERSION_2:
        processStopAreaMemberNodePtv2WithoutRole(transferZoneGroup, osmRelation, osmNode, osmNodeTags);
        break;
      case VERSION_1:
        processStopAreaMemberNodePtv1WithoutRole(transferZoneGroup, osmRelation, osmNode, osmNodeTags);
      default:          
        break;
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
  private void processStopAreaMemberWithoutRole(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    
    if(member.getType() == EntityType.Node) {
      
      /* collect osm node */
      OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
      if(osmNode == null) {
        LOGGER.warning(String.format("unable to collect osm node %d referenced in stop_area %d", member.getId(), osmRelation.getId()));
        return;
      }
      
      /* process as node, still it either adds an already parsed entity to the group, or marks unprocessed entities as processed so they are no longer
       * required to be processed as stand-alone Pt entities (not part of a stop_area) */
      processStopAreaMemberNodeWithoutRole(transferZoneGroup, osmRelation, osmNode);       
      
    }else if(member.getType() == EntityType.Way){
            
      boolean unprocessed = false; 
      /* we do not store all ways in memory, so we cannot simply collect and check  tags, instead we must rely
       * on unprocessed or already processed entities. For ways these are restricted to:
       * 
       *  - (unprocessed) stations -> process and extract name for group
       *  - (processed)   platforms (as transfer zones) -> register transfer zone on group */
            
      /* station */
      Pair<OsmPtVersionScheme, OsmEntity> osmWayPair = getZoningReaderData().getUnprocessedStation(EntityType.Way, member.getId());                  
      if(osmWayPair != null) {
        processStopAreaMemberStation(transferZoneGroup, osmRelation, osmWayPair.second(), OsmModelUtil.getTagsAsMap(osmWayPair.second()));
        unprocessed = false;
        getProfiler().incrementOsmTagCounter(osmWayPair.first(), OsmPtv1Tags.STATION);
      }
      
      /* platform */
      if(unprocessed) {
        TransferZone transferZone = getZoningReaderData().getTransferZoneWithoutConnectoid(EntityType.Way, member.getId());
        if(transferZone != null) {          
          /* register on group */
          transferZoneGroup.addTransferZone(transferZone);
          unprocessed = false;
        }
      }
    
      if(unprocessed) {
        LOGGER.warning(String.format("unable to collect osm way %d referenced in stop_area %d", member.getId(), osmRelation.getId()));
      }            
      
    }else {      
      LOGGER.info(String.format("stop_area (%d) member without a role found (%d) that is not a node or way, ignored",osmRelation.getId(),member.getId()));
    }
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
    return transferZone;
  }
  
  /** create a dummy transfer zone without access to underlying osmNode or way and without any geometry or populated
   * content other than its ids and type
   * 
   * @param osmId to use
   * @param transferZoneType to use
   * @return created transfer zone
   */
  private TransferZone createDummyTransferZone(long osmId, TransferZoneType transferZoneType) {
    TransferZone transferZone = createEmptyTransferZone(transferZoneType);
    transferZone.setXmlId(String.valueOf(osmId));
    transferZone.setExternalId(transferZone.getXmlId());
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
   * @return transfer zone created
   */
  private TransferZone createAndPopulateTransferZone(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType) {
    /* create */
    TransferZone transferZone = createEmptyTransferZone(transferZoneType);

    /* geometry, either centroid location or polygon circumference */
    if(osmEntity instanceof OsmNode){
      OsmNode osmNode = OsmNode.class.cast(osmEntity);
      try {
        Point geometry = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getXCoordinate(osmNode), PlanitOsmNodeUtils.getYCoordinate(osmNode));
        transferZone.getCentroid().setPosition(geometry);
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node %d when creating transfer zone", osmNode.getId()));
      }
    }else if(osmEntity instanceof OsmWay) {
      /* either area or linestring */
      OsmWay osmWay = OsmWay.class.cast(osmEntity);
      if(PlanitOsmWayUtils.isOsmWayPerfectLoop(osmWay)) {
        /* area, so extract polygon geometry */
        try {
          Polygon geometry = PlanitJtsUtils.createPolygon(PlanitOsmWayUtils.createCoordinateArray(osmWay, getNetworkToZoningData().getOsmNodes()));
          transferZone.setGeometry(geometry);
        }catch(PlanItException e) {
          LOGGER.fine(String.format("unable to extract polygon from osm way %s when creating transfer zone, likely some nodes are outside the bounding box",osmWay.getId()));
        }
      }else {
        /* line string -> for convenience we take the centre point of the two extreme nodes and make it our centroid location */
        OsmNode nodeA = getNetworkToZoningData().getOsmNodes().get(osmWay.getNodeId(0));
        OsmNode nodeB = getNetworkToZoningData().getOsmNodes().get(osmWay.getNodeId(osmWay.getNumberOfNodes()-1));
        try {
          if(nodeA==null || nodeB==null) {
            throw new PlanItException("dummy");
          }
          Point geometry = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getXCoordinate(nodeA)+PlanitOsmNodeUtils.getXCoordinate(nodeB)/2, PlanitOsmNodeUtils.getYCoordinate(nodeA)+PlanitOsmNodeUtils.getYCoordinate(nodeB)/2);
          transferZone.getCentroid().setPosition(geometry); 
        }catch(PlanItException e) {
          LOGGER.fine(String.format("unable to extract location from osm way %s when creating transfer zone, likely some nodes are outside the bounding box",osmWay.getId()));
        }
      }
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
        
    return transferZone;
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
      transferZoneGroup.setXmlId(Long.toString(osmRelation.getId()));
      /* external id  = osm node id*/
      transferZoneGroup.setExternalId(transferZoneGroup.getXmlId());
      
      /* name */
      if(tags.containsKey(OsmTags.NAME)) {
        transferZoneGroup.setName(tags.get(OsmTags.NAME));
      }    
      
      return transferZoneGroup;
  }
    
  /** create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @return transfer zone created
   */
  private TransferZone createAndRegisterTransferZoneWithoutConnectoids(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType) {
    /* create and register */
    TransferZone transferZone = createAndPopulateTransferZone(osmEntity,tags, transferZoneType);
    getZoning().transferZones.register(transferZone);
    EntityType entityType = Osm4JUtils.getEntityType(osmEntity);
    
    /* register locally */
    getZoningReaderData().addTransferZoneWithoutConnectoid(entityType, osmEntity.getId(), transferZone);
    return transferZone;
  }    
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=platform on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1HighwayPlatform(OsmEntity osmEntity, Map<String, String> tags) {
    
    /* create transfer zone when at least one mode is supported */
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmEntity.getId(), tags, OsmRoadModeTags.BUS);    
    if(getNetworkToZoningData().getSettings().hasAnyMappedPlanitMode(eligibleOsmModes)) {      
      
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM);
      addEligibleAccessModesToTransferZone(transferZone, eligibleOsmModes);
      
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
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmEntity.getId(), tags, OsmRoadModeTags.BUS);    
    if(getNetworkToZoningData().getSettings().hasAnyMappedPlanitMode(eligibleOsmModes)) {      
      
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.BUS_STOP);
      TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.POLE);
      addEligibleAccessModesToTransferZone(transferZone, eligibleOsmModes);      
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
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.TRAM_STOP);
    
    /* in contrast to highway=bus_stop this tag is placed on the track, so we can and will create connectoids immediately */
    PlanitOsmNetworkSettings networkSettings = getNetworkToZoningData().getSettings();    
       
    /* Tram connectoid: find layer and node/link segment for vehicle stop */ 
    Mode planitTramMode = networkSettings.getMappedPlanitMode(OsmRailwayTags.TRAM);
    if(planitTramMode == null) {
      throw new PlanItException("should not attempt to parse tram stop when tram mode is not activated on planit network");
    }
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitTramMode);
    Node planitNode = extractConnectoidAccessNode(networkLayer,osmNode);   

    if(planitNode.getEdges().size()>2) {
      LOGGER.severe(String.format("encountered tram stop on OSM node %d, with more than one potential incoming track, only two links expected at maximum, ignored", osmNode.getId()));
      return;
    }
    
    /* create and register transfer zone */
    TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.PLATFORM);
    getZoning().transferZones.register(transferZone);
    
    /* register eligible modes for transfer zone */
    addEligibleAccessModesToTransferZone(transferZone, osmNode.getId(), tags, OsmRailModeTags.TRAM);
    
    /* we can immediately create connectoids since Ptv1 tram stop is placed on tracks and no Ptv2 tag is present */
    /* railway generally has no direction, so create connectoid for both incoming directions (if present), so we can service any tram line using the tracks */        
    Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone,planitNode.getEntryLinkSegments(), Collections.singleton(planitTramMode));
    newConnectoids.forEach( connectoid -> getZoningReaderData().addDirectedConnectoidByOsmId(networkLayer, osmNode.getId(),connectoid));
  }  
  
  /** extract a halt since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dhalt
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */  
  private void extractPtv1Halt(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    PlanitOsmNetworkSettings networkSettings = getNetworkToZoningData().getSettings();
    
    /* eligible modes */
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmRailModesOnPtOsmEntity(osmNode.getId(), tags, OsmRailwayTags.RAIL);
    Set<Mode> planitModes = networkSettings.getMappedPlanitModes(eligibleOsmModes);
    if(planitModes==null || planitModes.isEmpty()) {
      return;
    }
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.HALT);    
    
    /* create and register transfer zone */
    TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.SMALL_STATION);
    getZoning().transferZones.register(transferZone);
        
    
    /* record serviced osm modes */
    addEligibleAccessModesToTransferZone(transferZone, eligibleOsmModes);    
    
    /* a halt is either placed on a line, or separate (preferred), both should be supported. In the former case we can create
     * connectoids immediately, in the latter case, we must find them based on the closest infrastructure (railway) or via separately
     * tagged stop_positions, in which case we postpone the creation of connectoids */
    for(Mode planitMode : planitModes) {      
      /* find node on parsed infrastructure */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);      
      
      PlanitOsmNetworkLayerReaderData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
      if(layerData.getNodesByOsmId().get(osmNode.getId()) == null && layerData.getLinksByInternalOsmNodeIds().get(osmNode.getId())==null) {
         /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
         * Ptv2 stop position reference is used, so postpone creating connectoid for now, and deal with it later when stop_positions have all been parsed */
        getZoningReaderData().addTransferZoneWithoutConnectoid(EntityType.Node, osmNode.getId(), transferZone);
         continue;
      }
      
      /* connectoid */
      extractDirectedConnectoidsForMode(osmNode, tags, transferZone, networkLayer, planitMode);            
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
    TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM);
    addEligibleAccessModesToTransferZone(transferZone, osmEntity.getId(), tags, OsmRailModeTags.TRAIN);    
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
      getZoningReaderData().addUnprocessedPtv1Station(osmNode);
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
      getZoningReaderData().addUnprocessedPtv1Station(osmWay);
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
        TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmWay, tags, TransferZoneType.PLATFORM);
        addEligibleAccessModesToTransferZone(transferZone, osmWay.getId(), tags, null);        
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
        getZoningReaderData().addUnprocessedPtv2Station(osmWay);
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
  
 
  /** Classic PT infrastructure based on Ptv2 OSM public transport scheme for osm node
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
        TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.PLATFORM);
        addEligibleAccessModesToTransferZone(transferZone, osmNode.getId(), tags, null);
      }            
      /* stop position */
      else if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        /* stop positions relate to connectoids that provide access to transfer zones. The transfer zones are based on platforms, but these might not have been
         * processed yet. So, we postpone parsing of all stop positions, and simply track them for delayed processing after all platforms/transfer zones have been identified */
        getZoningReaderData().getUnprocessedPtv2StopPositions().add(osmNode.getId());
      }     
      /* stop position */
      else if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore postpone parsing them until after we have parsed the relations */
        getZoningReaderData().addUnprocessedPtv2Station(osmNode);
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
    
    /* make very sure we are indeed correct in parsing this as Ptv1 scheme */
    if(tags.containsKey(OsmPtv2Tags.PUBLIC_TRANSPORT)) {
      LOGGER.warning(String.format("parsing node %d as PTv1 but tags contain PTv2 tag %s, entry ignored",osmNode.getId(), tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT)));
      return;
    }
    
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
      extractTransferInfrastructurePtv1Highway(osmNode, tags, ptv1ValueTag);
      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
      extractTransferInfrastructurePtv1Railway(osmNode, tags, ptv1ValueTag);     
      
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv1) for osm node %s, bit no compatible key tags found",osmNode.getId()));
    }  
    
    getProfiler().logTransferZoneStatus(getZoning().transferZones.size());
    getProfiler().logConnectoidStatus(getZoning().connectoids.size());    
  }  
  
  /** extract the platform member of a Ptv2 stop_area and register it on the transfer zone group
   * 
   * @param transferZoneGroup to register on
   * @param osmRelation the platform belongs to
   * @param member the platform member
   */
  private void extractPtv2StopAreaPlatform(TransferZoneGroup transferZoneGroup, OsmRelation osmRelation, OsmRelationMember member) {
    
    /* should be parsed (without connectoids), connect to group and let stop_positions create connectoids */
    TransferZone transferZone = getZoningReaderData().getTransferZoneWithoutConnectoid(member.getType(), member.getId());
    if(transferZone==null) {
      /* not parsed, likely on fringe of bounding box, create dummy */
      LOGGER.warning(String.format("platform %d not available in planit memory model, yet referenced by stop_area %d, creating dummy",member.getId(),osmRelation.getId()));
      transferZone = createDummyTransferZone(member.getId(),TransferZoneType.PLATFORM);
      getZoningReaderData().addTransferZoneWithoutConnectoid(member.getType(), member.getId(), transferZone);
    }
    transferZoneGroup.addTransferZone(transferZone);
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
    getZoningReaderData().addTransferZoneGroupByOsmId(osmRelation.getId(), transferZoneGroup);
    
    /* process all but stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      
      /* platform */
      if(member.getRole().equals(OsmPtv2Tags.PLATFORM_ROLE)) {        
        
        extractPtv2StopAreaPlatform(transferZoneGroup, osmRelation, member);
        
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
          processStopAreaMemberWithoutRole(transferZoneGroup, osmRelation, member);
          
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
      
      /* role:outer -> extract geometry for transfer zone*/
      if(member.getRole().equals(OsmMultiPolygonTags.OUTER_ROLE)) {
         
        /* try if it has been parsed, not if it has no tags (likely), yes if it has PTv2 tags (unlikely for multipolygon member)) */
        TransferZone transferZone = getZoningReaderData().getTransferZoneWithoutConnectoid(EntityType.Way, member.getId());
        if(transferZone == null) {
          /* collect from unprocessed ways, should be present */
          OsmWay unprocessedWay = getZoningReaderData().getUnprocessedMultiPolygonOsmWay(member.getId());
          if(unprocessedWay == null) {
            LOGGER.severe(String.format("Osm way %d referenced by Ptv2 multipolygon %d not available in parser, this should not happen, relation ignored",member.getId(),osmRelation.getId()));
            return;
          }
          
          /* create transfer zone, use tags of relation that contain the PT information */
          transferZone = createAndRegisterTransferZoneWithoutConnectoids(unprocessedWay, tags, TransferZoneType.PLATFORM);
          addEligibleAccessModesToTransferZone(transferZone, member.getId(), tags, null);          
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
      final PlanitOsmTransferSettings transferSettings, 
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
                  
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
    try {       
      
      /* only parse ways that are potentially used for (PT) transfers*/
      OsmPtVersionScheme ptVersion = isActivatedTransferBasedInfrastructure(tags);
      if(ptVersion != OsmPtVersionScheme.NONE) {
        
        /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
        extractTransferInfrastructure(osmWay, ptVersion, tags);
        
      }else if(getZoningReaderData().shouldMultiPolygonOsmWayBeKept(osmWay)) {
        
        /* even though osm way itself appears not to be public transport related, it is marked for keeping
         * so we keep it. This occurs when way is part of relation (multipolygon) where its shape represents for
         * example the outline of a platform, but all pt tags reside on the relation and not on the way itself. 
         * Processing of the way is postponed until we parse relations */
        getZoningReaderData().addUnprocessedMultiPolygonOsmWay(osmWay);
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
    
    LOGGER.info(" OSM (transfer) zone parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // do nothing yet
  }

  
}

package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData.NetworkLayerData;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Node;
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
   * 
   * @param tags tags to extract features from
   * @param transferZoneGroupType the type of the transfer zone group 
   * @return transfer zone group created
   */  
  private TransferZoneGroup createAndPopulateTransferZoneGroup(Map<String, String> tags) {
      /* create */
      TransferZoneGroup transferZoneGroup = getZoning().transferZoneGroups.createNew();
            
      /* XML id = internal id*/
      transferZoneGroup.setXmlId(Long.toString(transferZoneGroup.getId()));
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
    EntityType entityType = null;
    if(osmEntity instanceof OsmNode) {
      entityType = EntityType.Node;
    }else if( osmEntity instanceof OsmWay){
      entityType = EntityType.Way;
    }else {
      LOGGER.severe(String.format("unknown osm entity %d encountered when registering transfer zone, transfer zone not registered",osmEntity.getId()));
      return null;
    }
    
    /* register locally */
    getZoningReaderData().addTransferZoneWithoutConnectoid(entityType, osmEntity.getId(), transferZone);
    return transferZone;
  }    

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmNode (no Ptv2 tags)
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1Highway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
        
    /* postpone creation of connectoid, in case Ptv2 tag with stop position is referenced that we can later relate to this transfer zone */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.POLE);
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.PLATFORM);
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
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      /* create transfer zone but no connectoids, these will be constructed during or after we have parsed relations, i.e. stop_areas */
      createAndRegisterTransferZoneWithoutConnectoids(osmWay, tags, TransferZoneType.PLATFORM);
    }
  }

  /** extract a tram stop since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dtram_stop
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */
  private void extractPtv1TramStop(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
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
    
    /* we can immediately create connectoids since Ptv1 tram stop is placed on tracks and no Ptv2 tag is present */
    /* railway generally has no direction, so create connectoid for both incoming directions (if present), so we can service any tram line using the tracks */        
    createAndRegisterDirectedConnectoids(transferZone,planitNode.getEntryLinkSegments(), Collections.singleton(planitTramMode));           
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
    Collection<String> eligibleOsmModes = PlanitOsmNodeUtils.collectEligibleOsmRailModesOnPtOsmNode(osmNode, tags, OsmRailwayTags.RAIL);
    Set<Mode> planitModes = networkSettings.getMappedPlanitModes(eligibleOsmModes);
    if(planitModes==null || planitModes.isEmpty()) {
      return;
    }
    
    /* create and register transfer zone */
    TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.SMALL_STATION);
    getZoning().transferZones.register(transferZone);    
    
    /* a halt is either placed on a line, or separate (preferred), both should be supported. In the former case we can create
     * connectoids immediately, in the latter case, we must find them based on the closest infrastructure (railway) or via separately
     * tagged stop_positions, in which case we postpone the creation of connectoids */
    for(Mode planitMode : planitModes) {      
      /* find node on parsed infrastructure */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);      
      
      NetworkLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
      if(layerData.getPlanitNodesByOsmId().get(osmNode.getId()) == null && layerData.getOsmNodeIdsInternalToLink().get(osmNode.getId())==null) {
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
    /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
     * Ptv2 stop position reference is used, so postpone creating connectoids for now, and deal with it later when stop_positions have all been parsed */
    createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM);    
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
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1TramStop(osmNode, tags);
    }
    
    /* train platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1RailwayPlatform(osmNode, tags);
    }          
    
    /* train halt (not for trams)*/
    if(OsmPtv1Tags.HALT.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1Halt(osmNode, tags);
    }
    
    /* train station (not for trams) */
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      getZoningReaderData().getUnprocessedPtv1Stations().add(osmNode);
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
    
    /* platform edge */
    if(OsmPtv1Tags.PLATFORM_EDGE.equals(ptv1ValueTag)) {
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      /* platform edges are for additional geographic information, nothing requires them to be there in our format, so we take note
       * but do not parse, see also https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform_edge */
    }
    
    /* platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      getProfiler().incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1RailwayPlatform(osmWay, tags);      
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
        createAndRegisterTransferZoneWithoutConnectoids(osmWay, tags, TransferZoneType.PLATFORM);   
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
        getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).add(osmWay);
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
        /* create transfer zone but no connectoids, these will be constructed during or after we have parsed relations, i.e. stop_areas */
        createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.PLATFORM);   
      }      
      
      /* stop position */
      if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        /* stop positions relate to connectoids that provide access to transfer zones. The transfer zones are based on platforms, but these might not have been
         * processed yet. So, we postpone parsing of all stop positions, and simply track them for delayed processing after all platforms/transfer zones have been identified */
        getZoningReaderData().getUnprocessedPtv2StopPositions().add(osmNode.getId());
      }
      
      /* stop position */
      if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore postpone parsing them until after we have parsed the relations */
        getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).add(osmNode);
      }     
      
      /* stop area */
      if(OsmPtv2Tags.STOP_AREA.equals(ptv2ValueTag)) {
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


  /** extract stop area relation of Ptv2 scheme. We create transfer zone groups for each valid stop_area, connect it to the transfer zones and
   * extract the related connectoids and their connections to the transfer zones. We also mark processed stations, platforms, etc., such that after all
   * stop_areas have been processed, we can extract planit instances for the remaining unprocessed osm entities, knowing they do not belong to any stop_area and
   * constitute their own separate entity.
   * 
   * @param osmRelation to extract stop_area for
   * @param tags of the stop_area relation
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaRelation(OsmRelation osmRelation, Map<String, String> tags) throws PlanItException{
  
    /* transfer zone group */
    TransferZoneGroup transferZoneGroup = createAndPopulateTransferZoneGroup(tags);
    getZoning().transferZoneGroups.register(transferZoneGroup);
    getZoningReaderData().addTransferZoneGroupByOsmId(osmRelation.getId(), transferZoneGroup);
    
    /* process all but stop_positions */
    Map<EntityType, Set<Long>> processedTransferZones = new HashMap<EntityType,Set<Long>>();
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      
      /* platform */
      if(member.getRole().equals(OsmPtv2Tags.PLATFORM_ROLE)) {        
        
        /* should be parsed (without connectoids), connect to group and let stop_positions create connectoids */
        TransferZone transferZone = getZoningReaderData().getTransferZoneWithoutConnectoid(member.getType(), member.getId());
        if(transferZone==null) {
          /* not parsed, likely on fringe of bounding box, create dummy */
          LOGGER.warning(String.format("platform %d not available in planit memory model, yet referenced by stop_area %d, creating dummy",member.getId(),osmRelation.getId()));
          transferZone = createDummyTransferZone(member.getId(),TransferZoneType.PLATFORM);
          getZoningReaderData().addTransferZoneWithoutConnectoid(member.getType(), member.getId(), transferZone);
        }
        transferZoneGroup.addTransferZone(transferZone);
        processedTransferZones.putIfAbsent(member.getType(), new HashSet<Long>());
        processedTransferZones.get(member.getType()).add(member.getId());
        
      }
      /* other than stop_position */
      else {
        /* some common members do not have a dedicated role type, verify the supported ones here */
        
        /* station */
        //TODO (+ remove from unprocessed entries in data)
        
        /* entrances/exits */
        //TODO (+ remove from unprocessed entries in data?)       
      }
    }
        
    /* remove processed transfer zones since they now should have connectoids */
    processedTransferZones.forEach( (type, transferZones) -> {
      getZoningReaderData().removeTransferZonesWithoutConnectoids(type, transferZones);} );        
  }

  /** extract Ptv2 platforms from the multi-polygon. Do not create connectoids for them as the stop_positions
   * might not have been parsed yet.
   * 
   * @param osmRelation to extract platform from
   * @param tags to use
   */
  private void extractPtv2MultiPolygonPlatformRelation(OsmRelation osmRelation, Map<String, String> tags) {
    // TODO Auto-generated method stub
    
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
        
      }else if(getZoningReaderData().shouldOsmWayBeKept(osmWay)) {
        
        /* even though osm way itself appears not to be public transport related, it is marked for keeping
         * so we keep it. This occurs when way is part of relation (multipolygon) where its shape represents for
         * example the outline of a platform, but all pt tags reside on the relation and not on the way itself. 
         * Processing of the way is postponed until we parse relations */
        getZoningReaderData().addUnprocessedOsmWay(osmWay);
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
            
            extractPtv2StopAreaRelation(osmRelation, tags);
            
          }else {
            /* anything else is not expected */
            LOGGER.info(String.format("unknown public_transport relation %s encountered for relation %d, ignored",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
          }          
          
        }
        
        /* multi_polygons can represent public transport platforms */ 
        if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.MULTIPOLYGON)) {
          
          /* only consider public_transport=platform multi-polygons */
          if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
            
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
    getProfiler().logProfileInformation(getZoning());            
    
    LOGGER.info(" OSM (transfer) zone parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // do nothing yet
  }

  
}

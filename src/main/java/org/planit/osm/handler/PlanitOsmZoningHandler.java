package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData.NetworkLayerData;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.network.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitJtsUtils;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.misc.StringUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
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
public class PlanitOsmZoningHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningHandler.class.getCanonicalName());
  
  /** profiler for this reader */
  private final PlanitOsmZoningHandlerProfiler profiler;
  
  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils;  
  
  /** track unprocessed but identified Ptv1 station nodes */
  private final Set<OsmNode> unprocessedPtv1Stations = new HashSet<OsmNode>();
  
  /** track unprocessed but identified Ptv2 station nodes/ways */
  private final Map<EntityType,Set<OsmEntity>> unprocessedPtv2Stations = new HashMap<EntityType,Set<OsmEntity>>();  
  
  /** track unprocessed but identified Ptv2 stop positions by their osm node id */
  private final Set<Long> unprocessedPtv2StopPositions= new HashSet<Long>();
  
  /** track transfer zones without connectoids yet that were extracted from an OsmNode or way (osm id is key) */
  private final Map<EntityType, Map<Long, TransferZone>> transferZoneWithoutConnectoidByOsmEntityType = new HashMap<EntityType,Map<Long,TransferZone>>();
  
  /** track created connectoids by their osm node id and layer they reside on, needed to avoid creating duplicates when dealing with multiple modes/layers */
  private final Map<InfrastructureLayer,Map<Long, Set<DirectedConnectoid>>> directedConnectoidsByOsmNodeId = new HashMap<InfrastructureLayer,Map<Long, Set<DirectedConnectoid>>>();
  
    
  // references
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final PlanitOsmTransferSettings transferSettings;   
  
  /** network2ZoningData data collated from parsing network required to successfully popualte the zoning */
  private final PlanitOsmNetworkToZoningReaderData network2ZoningData; 
  
  /**
   * check if tags contain entries compatible with the provided Pt scheme given that we are verifying an OSM way/node that might reflect
   * a platform, stop, etc.
   *  
   * @param scheme to check against
   * @param tags to verify
   * @return true when present, false otherwise
   */
  private static boolean isCompatibleWith(OsmPtVersionScheme scheme, Map<String, String> tags) {
    if(scheme.equals(OsmPtVersionScheme.VERSION_1)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags) || OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return OsmPtv1Tags.hasPtv1ValueTag(tags);
      }
    }else if(scheme.equals(OsmPtVersionScheme.VERSION_2)) {
      return OsmPtv2Tags.hasPtv2ValueTag(tags);
    }else {
     LOGGER.severe(String.format("unknown OSM public transport scheme %s provided to check compatibility with, ignored",scheme.value()));

    }
    return false;
  }  
  
  /** Verify if passed in tags reflect transfer based infrastructure that is eligible (and supported) to be parsed by this class, e.g.
   * tags related to original PT scheme stops ( railway=halt, railway=tram_stop, highway=bus_stop and highway=platform),
   * or the current v2 PT scheme (public_transport=stop_position, platform, station, stop_area)
   * 
   * @param tags
   * @return which scheme it is compatible with, NONE if none could be found
   */
  private static OsmPtVersionScheme isTransferBasedOsmInfrastructure(Map<String, String> tags) {
    if(isCompatibleWith(OsmPtVersionScheme.VERSION_2, tags)){
      return OsmPtVersionScheme.VERSION_2;
    }else if(isCompatibleWith(OsmPtVersionScheme.VERSION_1,tags)) {
      return OsmPtVersionScheme.VERSION_1;
    }
    return OsmPtVersionScheme.NONE;
  }  
                                                          
  
  /** verify if tags represent an infrastructure used for transfers between modes, for example PT platforms, stops, etc. 
   * and is also activated for parsing based on the related settings
   * 
   * @param tags to verify
   * @return which scheme it is compatible with, NONE if none could be found or if it is not active 
   */  
  private OsmPtVersionScheme isActivatedTransferBasedInfrastructure(Map<String, String> tags) {
    if(transferSettings.isParserActive()) {
      return isTransferBasedOsmInfrastructure(tags);
    }
    return OsmPtVersionScheme.NONE;
  }
  
  /**Attempt to find the transfer zones by the use of the passed in tags containing references via key tag:
   * 
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   * 
   * @param tags to search for reference keys in
   * @param availableTransferZones to choose from
   * @return found transfer zones that have been parsed before, null if no match is found
   */
  private Set<TransferZone> findTransferZonesByTagReference(Map<String, String> tags, Collection<TransferZone> availableTransferZones) {
    Set<TransferZone> foundTransferZones = null;
    
    /* ref value, can be a list of multiple values */
    String refValue = PlanitOsmUtils.getValueForSupportedRefKeys(tags);
    if(refValue != null) {
      String[] transferZoneRefValues = StringUtils.splitByAnythingExceptAlphaNumeric(refValue);
      for(int index=0; index < transferZoneRefValues.length; ++index) {
        String localRefValue = transferZoneRefValues[index];
        for(TransferZone transferZone : availableTransferZones) {
          Object refProperty = transferZone.getInputProperty(OsmTags.REF);
          if(refProperty != null && localRefValue.equals(String.class.cast(refProperty))) {
            /* match */
            if(foundTransferZones==null) {
              foundTransferZones = new HashSet<TransferZone>();
            }
            foundTransferZones.add(transferZone);
          }
        }
      }
    }
    return foundTransferZones;
  }  
  
  /** create a new but unpopulated transfer zone
   * 
   * @param transferZoneType of the zone
   * @return created transfer zone
   */
  private TransferZone createEmptyTransferZone(TransferZoneType transferZoneType) {
    /* create */
    TransferZone transferZone = zoning.transferZones.createNew();
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
        Point geometry = PlanitJtsUtils.createPoint(PlanitOsmUtils.getXCoordinate(osmNode), PlanitOsmUtils.getYCoordinate(osmNode));
        transferZone.getCentroid().setPosition(geometry);
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node %d when creating transfer zone", osmNode.getId()));
      }
    }else if(osmEntity instanceof OsmWay) {
      /* either area or linestring */
      OsmWay osmWay = OsmWay.class.cast(osmEntity);
      if(PlanitOsmUtils.isOsmWayPerfectLoop(osmWay)) {
        /* area, so extract polygon geometry */
        try {
          Polygon geometry = PlanitJtsUtils.createPolygon(PlanitOsmUtils.createCoordinateArray(osmWay, network2ZoningData.getOsmNodes()));
          transferZone.setGeometry(geometry);
        }catch(PlanItException e) {
          LOGGER.fine(String.format("unable to extract polygon from osm way %s when creating transfer zone, likely some nodes are outside the bounding box",osmWay.getId()));
        }
      }else {
        /* line string -> for convenience we take the centre point of the two extreme nodes and make it our centroid location */
        OsmNode nodeA = network2ZoningData.getOsmNodes().get(osmWay.getNodeId(0));
        OsmNode nodeB = network2ZoningData.getOsmNodes().get(osmWay.getNodeId(osmWay.getNumberOfNodes()-1));
        try {
          if(nodeA==null || nodeB==null) {
            throw new PlanItException("dummy");
          }
          Point geometry = PlanitJtsUtils.createPoint(PlanitOsmUtils.getXCoordinate(nodeA)+PlanitOsmUtils.getXCoordinate(nodeB)/2, PlanitOsmUtils.getYCoordinate(nodeA)+PlanitOsmUtils.getYCoordinate(nodeB)/2);
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

    String refValue = PlanitOsmUtils.getValueForSupportedRefKeys(tags);
    /* ref (to allow other entities to refer to this transfer zone locally) */
    if(refValue != null) {
      transferZone.addInputProperty(OsmTags.REF, tags.get(OsmTags.REF));
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
      TransferZoneGroup transferZoneGroup = zoning.transferZoneGroups.createNew();
            
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
  
  /** update an existing directed connectoid with new access zone and allowed modes. In case the link segment does not have any of the 
   * passed in modes listed as allowed, the connectoid is not updated with these modes for the given access zone as it would not be possible to utilise it. 
   * 
   * @param accessZone to relate connectoids to
   * @param allowedModes to add to the connectoid for the given access zone
   */  
  private void updateDirectedConnectoid(DirectedConnectoid connectoidToUpdate, TransferZone accessZone, Set<Mode> allowedModes) {    
    final Set<Mode> realAllowedModes = ((MacroscopicLinkSegment)connectoidToUpdate.getAccessLinkSegment()).getAllowedModes(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      if(!connectoidToUpdate.hasAccessZone(accessZone)) {
        connectoidToUpdate.addAccessZone(accessZone);
      }
      connectoidToUpdate.addAllowedModes(accessZone, realAllowedModes);   
    }
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
  private DirectedConnectoid createAndRegisterDirectedConnectoid(final TransferZone accessZone, final MacroscopicLinkSegment linkSegment, final Set<Mode> allowedModes) throws PlanItException {
    final Set<Mode> realAllowedModes = linkSegment.getAllowedModes(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      DirectedConnectoid connectoid = zoning.connectoids.registerNew(linkSegment,accessZone);
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
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @return created connectoids
   * @throws PlanItException thrown if error
   */
  private Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(TransferZone transferZone, Set<EdgeSegment> linkSegments, Set<Mode> allowedModes) throws PlanItException {
    Set<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(EdgeSegment linkSegment : linkSegments) {
      DirectedConnectoid newConnectoid = createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes);
      if(newConnectoid != null) {
        createdConnectoids.add(newConnectoid);
      }
    } 
    return createdConnectoids;
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
    zoning.transferZones.register(transferZone);     
    if(osmEntity instanceof OsmNode) {
      transferZoneWithoutConnectoidByOsmEntityType.get(EntityType.Node).put(osmEntity.getId(), transferZone);
    }else if( osmEntity instanceof OsmWay){
      transferZoneWithoutConnectoidByOsmEntityType.get(EntityType.Way).put(osmEntity.getId(), transferZone);
    }else {
      LOGGER.severe(String.format("unknown osm entity %d encountered when registering transfer zone",osmEntity.getId()));
    }
    return transferZone;
  }  
  
  /** Create a new PLANit node, register it and update stats
   * 
   * @param osmNode to extract PLANit node for
   * @param networkLayer to create it on
   * @return created planit node
   */
  private Node extractPlanitNode(OsmNode osmNode, final Map<Long, Node> nodesByOsmId,  MacroscopicPhysicalNetwork networkLayer) {
    Node planitNode = PlanitOsmHandlerHelper.createAndPopulateNode(osmNode, networkLayer);                
    nodesByOsmId.put(osmNode.getId(), planitNode);
    profiler.logNodeStatus(networkLayer.nodes.size());
    return planitNode;
  }  
  
  /** extract the connectoid access node. either it already exists as a regular node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broke. In the former case, we simply collect the node
   * 
   * @param networkLayer to extract node on
   * @param osmNode to collect planit node version for
   * @return planit node collected/created
   * @throws PlanItException thrown if error
   */
  private Node extractConnectoidAccessNode(MacroscopicPhysicalNetwork networkLayer, OsmNode osmNode) throws PlanItException {
    final Map<Long, Node> nodesByOsmId = network2ZoningData.getNetworkLayerData(networkLayer).getPlanitNodesByOsmId();
    Node planitNode = nodesByOsmId.get(osmNode.getId());
    if(planitNode == null) {
      /* node is internal to an existing link, create it and break existing link */
      planitNode = extractPlanitNode(osmNode, nodesByOsmId, networkLayer);
      
      /* make sure that we have the correct mapping from node to link (in case the link has been broken before in the network reader, or here, for example) */
      NetworkLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
      List<Link> linksWithOsmNodeInternally = layerData.getOsmNodeIdsInternalToLink().get(osmNode.getId());      
      PlanitOsmHandlerHelper.updateLinksForInternalNode(planitNode, layerData.getOsmWaysWithMultiplePlanitLinks(), linksWithOsmNodeInternally);
            
      /* break link */
      CoordinateReferenceSystem crs = network2ZoningData.getOsmNetwork().getCoordinateReferenceSystem();
      Map<Long, Set<Link>> newlyBrokenLinks = PlanitOsmHandlerHelper.breakLinksWithInternalNode(planitNode, linksWithOsmNodeInternally, networkLayer, crs);
      /* update mapping since another osmWayId now has multiple planit links */
      PlanitOsmHandlerHelper.addAllTo(newlyBrokenLinks, layerData.getOsmWaysWithMultiplePlanitLinks());      
    }
    return planitNode;
  }  
  
  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param tags to use
   * @param transferZone this connectoid is assumed to provide access to
   * @param networkLayer this connectoid resides on
   * @param planitMode this connectoid is allowed access for
   * @throws PlanItException thrown if error
   */
  private void extractDirectedConnectoidsForMode(OsmNode osmNode, Map<String, String> tags, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode) throws PlanItException {
    
    /* access node */
    Node planitNode = extractConnectoidAccessNode(networkLayer,osmNode);    
    if(planitNode==null) {
      LOGGER.severe(String.format("unable to create connectoid for osm node (%d) even though it is thought to be a transfer access node, ignored",osmNode.getId()));
      return;
    }
    
    /* already created connectoids */
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer, new HashMap<Long,Set<DirectedConnectoid>>());
    Map<Long, Set<DirectedConnectoid>> existingConnectoids = directedConnectoidsByOsmNodeId.get(networkLayer);    
                
    if(existingConnectoids.containsKey(osmNode.getId())) {      
      
      /* update: connectoid already exists */
      Set<DirectedConnectoid> connectoidsForNode = existingConnectoids.get(osmNode.getId());
      connectoidsForNode.forEach( connectoid -> updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode)));
      
    }else {
      
      /* new connectoid, create and register */
      Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, planitNode.getEntryEdgeSegments(), Collections.singleton(planitMode));      
      if(newConnectoids==null || newConnectoids.isEmpty()) {
        LOGGER.warning(String.format("found eligible mode %s for osm node %d, but no access link segment supports this mode", planitMode.getExternalId(), osmNode.getId()));
      }
      
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
        
    /* postpone creation of connectoid, in case Ptv2 tag with stop position is referenced that we can later relate to this transfer zone */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
      createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.POLE);
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
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
    PlanitOsmNetworkSettings networkSettings = network2ZoningData.getSettings();    
       
    /* Tram connectoid: find layer and node/link segment for vehicle stop */ 
    Mode planitTramMode = networkSettings.getMappedPlanitMode(OsmRailwayTags.TRAM);
    if(planitTramMode == null) {
      throw new PlanItException("should not attempt to parse tram stop when tram mode is not activated on planit network");
    }
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) network2ZoningData.getOsmNetwork().infrastructureLayers.get(planitTramMode);
    Node planitNode = extractConnectoidAccessNode(networkLayer,osmNode);   

    if(planitNode.getEdges().size()>2) {
      LOGGER.severe(String.format("encountered tram stop on OSM node %d, with more than one potential incoming track, only two links expected at maximum, ignored", osmNode.getId()));
      return;
    }
    
    /* create and register transfer zone */
    TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.PLATFORM);
    zoning.transferZones.register(transferZone);
    
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
    PlanitOsmNetworkSettings networkSettings = network2ZoningData.getSettings();
    
    /* eligible modes */
    Collection<String> eligibleOsmModes = PlanitOsmHandlerHelper.collectEligibleOsmRailModesOnPtOsmNode(osmNode, tags, OsmRailwayTags.RAIL);
    Set<Mode> planitModes = networkSettings.getMappedPlanitModes(eligibleOsmModes);
    if(planitModes==null || planitModes.isEmpty()) {
      return;
    }
    
    /* create and register transfer zone */
    TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.SMALL_STATION);
    zoning.transferZones.register(transferZone);    
    
    /* a halt is either placed on a line, or separate (preferred), both should be supported. In the former case we can create
     * connectoids immediately, in the latter case, we must find them based on the closest infrastructure (railway) or via separately
     * tagged stop_positions, in which case we postpone the creation of connectoids */
    for(Mode planitMode : planitModes) {      
      /* find node on parsed infrastructure */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) network2ZoningData.getOsmNetwork().infrastructureLayers.get(planitMode);      
      
      NetworkLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
      if(layerData.getPlanitNodesByOsmId().get(osmNode.getId()) == null && layerData.getOsmNodeIdsInternalToLink().get(osmNode.getId())==null) {
         /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
         * Ptv2 stop position reference is used, so postpone creating connectoid for now, and deal with it later when stop_positions have all been parsed */
         transferZoneWithoutConnectoidByOsmEntityType.get(EntityType.Node).put(osmNode.getId(), transferZone);
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
  
  /** extract a Ptv2 stop position part of a stop_area relation. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param member member in stop_area relation
   * @param availableTransferZones the transfer zones this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopPosition(OsmRelationMember member, Collection<TransferZone> availableTransferZones) throws PlanItException {
    PlanItException.throwIfNull(member, "stop_area stop_position member null");
    if(member.getType() != EntityType.Node) {
      throw new PlanItException("stop_position encountered that it not an OSM node, this is not permitted");
    }
    if(!this.unprocessedPtv2StopPositions.contains(member.getId())){
      LOGGER.severe(String.format("stop_position %d not marked as unproccessed even though it is expected to be unprocessed up until now",member.getId()));
    }        
    
    /* stop location via Osm node */
    OsmNode osmNode = network2ZoningData.getOsmNodes().get(member.getId());
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);
    
    /* supported modes */
    Collection<String> eligibleOsmModes = PlanitOsmHandlerHelper.collectEligibleOsmRailModesOnPtOsmNode(osmNode, tags, null);
    eligibleOsmModes.addAll(PlanitOsmHandlerHelper.collectEligibleOsmRoadModesOnPtOsmNode(osmNode, tags, null));    
    Set<Mode> planitModes = network2ZoningData.getSettings().getMappedPlanitModes(eligibleOsmModes);
    if(planitModes==null || planitModes.isEmpty()) {
      /* while no connectoids allowed due to mode restrictions, it is successfully processed */
      return;
    }
    
    /* reference to platform, i.e. transfer zone */
    Set<TransferZone> transferZones = findTransferZonesByTagReference(tags, availableTransferZones);    
    if(transferZones == null || transferZones.isEmpty()) {
      /* no matches found, we must find match geographically to obtain transfer zone! */
      //TODO
      LOGGER.severe(String.format("stop position %d has no valid transfer zone reference --> find closest by transfer zone instead, TODO",member.getId()));
      return;
    }
    
    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    for(Mode planitMode : planitModes) {
      /* layer */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) network2ZoningData.getOsmNetwork().infrastructureLayers.get(planitMode);

      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) */
        extractDirectedConnectoidsForMode(osmNode, tags, transferZone, networkLayer, planitMode);
      }      
    }

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
  private void extractPtv2StopArea(OsmRelation osmRelation, Map<String, String> tags) throws PlanItException{

    /* transfer zone group */
    TransferZoneGroup transferZoneGroup = createAndPopulateTransferZoneGroup(tags);
    zoning.transferZoneGroups.register(transferZoneGroup);
    
    /* process all but stop_positions */
    Map<EntityType, Set<Long>> processedTransferZones = new HashMap<EntityType,Set<Long>>();
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      
      /* platform */
      if(member.getRole().equals(OsmPtv2Tags.PLATFORM_ROLE)) {        
        
        /* should be parsed (without connectoids), connect to group and let stop_positions create connectoids */
        TransferZone transferZone = this.transferZoneWithoutConnectoidByOsmEntityType.get(member.getType()).get(member.getId());
        if(transferZone==null) {
          /* not parsed, likely on fringe of bounding box, create dummy */
          LOGGER.warning(String.format("platform %d not available in planit memory model, yet referenced by stop_area %d, creating dummy",member.getId(),osmRelation.getId()));
          transferZone = createDummyTransferZone(member.getId(),TransferZoneType.PLATFORM);
          transferZoneWithoutConnectoidByOsmEntityType.get(member.getType()).put(member.getId(), transferZone);
        }
        transferZoneGroup.addTransferZone(transferZone);
        processedTransferZones.putIfAbsent(member.getType(), new HashSet<Long>());
        processedTransferZones.get(member.getType()).add(member.getId());
        
      }
      /* other than stop_position */
      else {
        /* some common members do not have a dedicated role type, verify the supported ones here */
        
        /* station */
        //TODO
        
        /* entrances/exits */
        //TODO        
      }
    }
    
    /* postpone stop positions in case dummy transfer zones are created, in which case stop positions can only use them after they have been created in previous loop */
    
    /* process stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);      
      if(member.getRole().equals(OsmPtv2Tags.STOP_POSITION_ROLE)) {

        extractPtv2StopPosition(member, transferZoneGroup.getTransferZones());
        unprocessedPtv2StopPositions.remove(member.getId());
      }      
    }
    
    /* remove processed transfer zones since they now should have connectoids */
    processedTransferZones.forEach( (type, transferZones) -> {
      Map<Long,TransferZone> unprocessedTransferZones = this.transferZoneWithoutConnectoidByOsmEntityType.get(type);
      transferZones.forEach( transferZoneId -> unprocessedTransferZones.remove(transferZoneId));      
    } );
    
    
  }  

  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag railway=* for an OsmNode
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key railway=
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1Railway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
    PlanitOsmNetworkSettings networkSettings = network2ZoningData.getSettings();
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasMappedPlanitMode(OsmRailwayTags.TRAM)) {
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1TramStop(osmNode, tags);
    }
    
    /* train platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1RailwayPlatform(osmNode, tags);
    }          
    
    /* train halt (not for trams)*/
    if(OsmPtv1Tags.HALT.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
      extractPtv1Halt(osmNode, tags);
    }
    
    /* train station (not for trams) */
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      unprocessedPtv1Stations.add(osmNode);
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
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
      /* platform edges are for additional geographic information, nothing requires them to be there in our format, so we take note
       * but do not parse, see also https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform_edge */
    }
    
    /* platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      profiler.incrementOsmPtv1TagCounter(ptv1ValueTag);
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
        profiler.incrementOsmPtv2TagCounter(ptv2ValueTag);
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
        unprocessedPtv2Stations.get(EntityType.Way).add(osmWay);
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
        unprocessedPtv2StopPositions.add(osmNode.getId());
      }
      
      /* stop position */
      if(OsmPtv2Tags.STATION.equals(ptv2ValueTag)) {
        /* stations of the Ptv2 variety are sometimes part of Ptv2 stop_areas which means they represent a transfer zone group, or they are stand-alone, in which case we can
         * ignore them altogether. Therefore postpone parsing them until after we have parsed the relations */
        unprocessedPtv2Stations.get(EntityType.Node).add(osmNode);
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
    
    profiler.logTransferZoneStatus(zoning.transferZones.size());
    profiler.logConnectoidStatus(zoning.connectoids.size());    
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
   * @param network2ZoningData data collated from parsing network required to successfully popualte the zoning
   * @param referenceNetwork to use
   * @param zoningToPopulate to populate
   */
  public PlanitOsmZoningHandler(final PlanitOsmTransferSettings transferSettings, final PlanitOsmNetworkToZoningReaderData network2ZoningData, final Zoning zoningToPopulate) {
    /* gis initialisation */
    this.geoUtils = new PlanitJtsUtils(network2ZoningData.getOsmNetwork().getCoordinateReferenceSystem());
    /* profiler */
    this.profiler = new PlanitOsmZoningHandlerProfiler();
    
    /* references */
    this.network2ZoningData = network2ZoningData;
    this.zoning = zoningToPopulate;       
    this.transferSettings = transferSettings;
    
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    reset();
    
    PlanItException.throwIf(
        this.network2ZoningData.getOsmNetwork().infrastructureLayers == null || this.network2ZoningData.getOsmNetwork().infrastructureLayers.size()<=0,
          "network is expected to be populated at start of parsing OSM zoning");
    
    /* initialise for two possible entity types */
    transferZoneWithoutConnectoidByOsmEntityType.put(EntityType.Node, new HashMap<Long,TransferZone>());
    transferZoneWithoutConnectoidByOsmEntityType.put(EntityType.Way, new HashMap<Long,TransferZone>());
    /* initialise for two possible entity types */
    unprocessedPtv2Stations.put(EntityType.Node, new HashSet<OsmEntity>());
    unprocessedPtv2Stations.put(EntityType.Way, new HashSet<OsmEntity>());
  }  


  /**
   * Not used
   */
  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // not used
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
      
      /* only parse public_transport relations */
      if(transferSettings.isParserActive() && tags.containsKey(OsmTags.TYPE) && tags.get(OsmTags.TYPE).equals(OsmPtv2Tags.PUBLIC_TRANSPORT)) {
 
        /* stop_area */
        if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_AREA)) {
          extractPtv2StopArea(osmRelation, tags);
        }else {
          /* anything else is not expected */
          LOGGER.info(String.format("unknown public_transport relation %s encountered for relation %d, ignored",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
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
    profiler.logProfileInformation(zoning);            
    
    LOGGER.info(" OSM (transfer) zone parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {   
    unprocessedPtv1Stations.clear();
    unprocessedPtv2Stations.clear();
    unprocessedPtv2StopPositions.clear();
    transferZoneWithoutConnectoidByOsmEntityType.clear();
    directedConnectoidsByOsmNodeId.clear();
  }
  
}

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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitJtsUtils;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.LinkSegment;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneGroupType;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
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
  
  /** track unprocessed but identified Ptv2 stop positions */
  private final Set<OsmNode> unprocessedPtv2StopPositions= new HashSet<OsmNode>();
  
  /** track transfer zones without connectoids yet that were extracted from an OsmNode (id is key) */
  private final Map<Long, TransferZone> transferZoneWithoutConnectoidFromOsmNode = new HashMap<Long,TransferZone>();
  
  /** track transfer zones without connectoids yet that were extracted from an OsmWay (id is key) */
  private final Map<Long, TransferZone> transferZoneWithoutConnectoidFromOsmWay = new HashMap<Long,TransferZone>();
    
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
  
  /** Collect the rail modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is elgible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the passed in rail mode as the default 
   * 
   * @param osmNode to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  private Collection<String> collectEligibleOsmRailModes(OsmNode osmNode, Map<String, String> tags, String defaultOsmMode) {
    Collection<String> explicitlyIncludedOsmModes = PlanitOsmUtils.getOsmRailModesWithAccessValue(tags, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Collection<String> explicitlyExcludedOsmModes = PlanitOsmUtils.getOsmRailModesWithAccessValue(tags, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        LOGGER.severe(String.format("we currently do not yet support explicitly excluded rail modes on railway=halt, ignored exclusion of %s",explicitlyExcludedOsmModes.toString()));
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = Collections.singleton(defaultOsmMode);
    }
    return explicitlyIncludedOsmModes;
  }  
  
  /** create a transfer zone based on the passed in osm entity, tags for feature extraction and access
   * @param osmEntity entity that is to be converted into a transfer zone
   * @param tags tags to extract features from
   * @param transferZoneType the type of the transfer zone 
   * @return transfer zone created
   */
  private TransferZone createAndPopulateTransferZone(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType) {
    /* create */
    TransferZone transferZone = zoning.transferZones.createNew();
    /* type */
    transferZone.setType(transferZoneType);

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
    transferZone.setXmlId(Long.toString(transferZone.getId()));
    /* external id  = osm node id*/
    transferZone.setExternalId(transferZone.getXmlId());
    
    /* name */
    if(tags.containsKey(OsmTags.NAME)) {
      transferZone.setName(tags.get(OsmTags.NAME));
    }    
    
    return transferZone;
  }  
  
  /** create a transfer zone group based on the passed in osm entity, tags for feature extraction and access
   * 
   * @param osmNode node that is to be converted into a transfer zone group
   * @param tags tags to extract features from
   * @param transferZoneGroupType the type of the transfer zone group 
   * @return transfer zone group created
   */  
  private TransferZoneGroup createAndPopulateTransferZoneGroup(OsmNode osmNode, Map<String, String> tags, TransferZoneGroupType transferZoneGroupType) {
      /* create */
      TransferZoneGroup transferZoneGroup = zoning.transferZoneGroups.createNew();
      
      /* type */
      transferZoneGroup.setType(transferZoneGroupType);
      
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

  /** create directed connectoid for the link segment provided, all related to the given transfer zone and with access modes provided
   * 
   * @param transferZone to relate connectoids to
   * @param linkSegment to create connectoid for
   * @param allowedModes used for each connectoid
   * @return created connectoid
   * @throws PlanItException thrown if error
   */
  private DirectedConnectoid createAndRegisterDirectedConnectoid(TransferZone transferZone, MacroscopicLinkSegment linkSegment, Set<Mode> allowedModes) throws PlanItException {
      DirectedConnectoid connectoid = zoning.connectoids.registerNew(linkSegment,transferZone);
      /* link connectoid to zone and register modes for access*/
      connectoid.addAllowedModes(transferZone, allowedModes);   
      return connectoid;
  }   
  
  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with access modes provided
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
      createdConnectoids.add(createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes));   
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
      transferZoneWithoutConnectoidFromOsmNode.put(osmEntity.getId(), transferZone);
    }else if( osmEntity instanceof OsmWay){
      transferZoneWithoutConnectoidFromOsmWay.put(osmEntity.getId(), transferZone);
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
      createAndRegisterTransferZoneWithoutConnectoids(osmNode, tags, TransferZoneType.POLE);
    }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
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
    Collection<String> eligibleOsmModes = collectEligibleOsmRailModes(osmNode, tags, OsmRailwayTags.RAIL);
    Set<Mode> planitModes = networkSettings.getMappedPlanitModes(eligibleOsmModes);
    if(planitModes==null || planitModes.isEmpty()) {
      return;
    }
    
    /* create and register transfer zone */
    TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.SMALL_STATION);
    zoning.transferZones.register(transferZone);    
    
    /* a halt is either placed on a line, or separate (preferred), both should be supported. In the former case we can create
     * connectoids immediately, in the latter case, we must find them based on the closest infrastructure (railway) or via separately
     * tagged stop_positions, hence we postpone the creation of connectoids */
    Collection<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(Mode planitMode : planitModes) {      
      /* find node on parsed infrastructure */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) network2ZoningData.getOsmNetwork().infrastructureLayers.get(planitMode);      
      
      NetworkLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
      Node haltNode = layerData.getPlanitNodesByOsmId().get(osmNode.getId()); 
      if(haltNode == null) {
        if(layerData.getOsmNodeIdsInternalToLink().get(osmNode.getId())!=null){
          /* halt on existing railway's internal node, break link and node becomes connectoid */
          haltNode = extractConnectoidAccessNode(networkLayer, osmNode);
        }else {
          /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
           * Ptv2 stop position reference is used, so postpone creating connectoid for now, and deal with it later when stop_positions have all been parsed */
          transferZoneWithoutConnectoidFromOsmNode.put(osmNode.getId(), transferZone);
          continue;
        } 
      }
      
      if(haltNode==null) {
        LOGGER.severe(String.format("unable to create connectoid for halt (%d) while halt is placed on railway, ignored",osmNode.getId()));
        continue;
      }
      
      /* determine if connectoid is already created by previous processed mode for this layer */
      boolean connectoidExists = false;
      for(DirectedConnectoid connectoid : createdConnectoids) {
        if(haltNode.getEntryEdgeSegments().contains(connectoid.getAccessLinkSegment())){
          
          if(!((MacroscopicLinkSegment)connectoid.getAccessLinkSegment()).getAllowedModes().contains(planitMode)) {
            LOGGER.warning(String.format("found eligible mode %s for halt %d, but access link segment does not support this mode",planitMode.getExternalId(), osmNode.getId()));
          }
          
          /* connectoid exists already, add allowed mode */
          connectoid.addAllowedMode(transferZone, planitMode);
          connectoidExists = true;
        }
      }
      
      /* connectoid not yet created, create it */
      if(!connectoidExists) {
        Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, haltNode.getEntryEdgeSegments(), Collections.singleton(planitMode));
        
        newConnectoids.forEach( connectoid -> {
          if(!((MacroscopicLinkSegment)connectoid.getAccessLinkSegment()).getAllowedModes().contains(planitMode)) {
            LOGGER.warning(String.format("found eligible mode %s for halt %d, but access link segment does not support this mode",planitMode.getExternalId(), osmNode.getId()));
          }
        });
        
        createdConnectoids.addAll(newConnectoids); 
      }
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

  /** extract a platform since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dplatform
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */  
  private void extractPtv2Platform(OsmNode osmNode, Map<String, String> tags) {
    
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

  private void extractTransferInfrastructurePtv2(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {

    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv2) for osm way %s, bit no compatible key tags found",osmWay.getId()));
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme (no Ptv2 tags) for an Osm way
   * 
   * @param osmWay to parse
   * @param tags of the node
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {

      String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
      extractTransferInfrastructurePtv1Highway(osmWay, tags, ptv1ValueTag);      
      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
      extractTransferInfrastructurePtv1Railway(osmWay, tags, ptv1ValueTag);       
      
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv1) for osm way %s, bit no compatible key tags found",osmWay.getId()));
    }
  }  
  
 
  
  private void extractTransferInfrastructurePtv2(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {
      String ptv2ValueTag = tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT);
      
      /* platform */
      if(OsmPtv2Tags.PLATFORM.equals(ptv2ValueTag)) {
        extractPtv2Platform(osmNode, tags);
      }      
      
      /* stop position */
      if(OsmPtv2Tags.STOP_POSITION.equals(ptv2ValueTag)) {
        /* stop positions relate to connectoids that provide access to transfer zones. The transfer zones are based on platforms, but these might not have been
         * processed yet. So, we postpone parsing of all stop positions, and simply track them for delayed processing after all platforms/transfer zones have been identified */
        unprocessedPtv2StopPositions.add(osmNode);
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
    PlanItException.throwIf(
        this.network2ZoningData.getOsmNetwork().infrastructureLayers == null || this.network2ZoningData.getOsmNetwork().infrastructureLayers.size()<=0,
          "network is expected to be populated at start of parsing OSM zoning");       
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
    // delegate
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
    unprocessedPtv2StopPositions.clear();
    transferZoneWithoutConnectoidFromOsmNode.clear();
    transferZoneWithoutConnectoidFromOsmWay.clear();
  }
  
}

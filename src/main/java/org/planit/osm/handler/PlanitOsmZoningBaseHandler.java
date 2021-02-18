package org.planit.osm.handler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmNode;

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
  
  /** to be able to retain the supported osm modes on a planit transfer zone, we place tham on the zone as an input property under this key.
   *  This avoids having to store all osm tags, while still allowing to leverage the information in the rare cases it is needed when this information is lacking
   *  on stop_positions that use this transfer zone
   */
  protected static final String TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY = "osmmodes";
        
  // references
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final PlanitOsmTransferSettings transferSettings;   
  
  /** network2ZoningData data collated from parsing network required to successfully popualte the zoning */
  private final PlanitOsmNetworkToZoningReaderData network2ZoningData;
  
  /** holds, add to all the tracking data required for parsing zones */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** profiler for this reader */
  private final PlanitOsmZoningHandlerProfiler profiler;   
  
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
  
  /** while PLANit does not require access modes on transfer zones because it is handled by connectoids, OSM stop_positions (connectoids) might lack the required
   * tagging to identify their mode access in which case we revert to the related transfer zone to deduce it. Therefore, we store osm mode information on a transfer zone
   * via the generic input properties to be able to retrieve it if needed later
   * 
   * @param transferZone to use
   * @param eligibleOsmModes to add
   */
  protected static void addEligibleAccessModesToTransferZone(final TransferZone transferZone, Collection<String> eligibleOsmModes) {
    if(transferZone != null && eligibleOsmModes!= null) {
      /* register identified eligible access modes */
      transferZone.addInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY, eligibleOsmModes);
    }
  }    
  
  /** while PLANit does not require access modes on transfer zones because it is handled by connectoids, OSM stop_positions (connectoids) might lack the required
   * tagging to identify their mode access in which case we revert to the related transfer zone to deduce it. Therefore, we store osm mode information on a transfer zone
   * via the generic input properties to be able to retrieve it if needed later
   * 
   * @param transferZone to use
   * @param osmEntityId it relates to
   * @param defaultOsmMode default mode for this zone (can be null)
   */
  protected static void addEligibleAccessModesToTransferZone(final TransferZone transferZone, final long osmEntityId, final Map<String, String> tags, final String defaultOsmMode) {
    if(transferZone != null) {
      /* register identified eligible access modes */
      Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmEntityId, tags, defaultOsmMode);
      addEligibleAccessModesToTransferZone(transferZone, eligibleOsmModes);
    }
  }
  
  /** collect any prior registered eligible osm modes on a Planit transfer zone 
   * 
   * @param transferZone to collect from
   * @return eligible osm modes, null if none
   */
  @SuppressWarnings("unchecked")
  protected static Collection<String> getEligibleOsmModesForTransferZone(final TransferZone transferZone){
    return (Collection<String>) transferZone.getInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY);
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
  protected DirectedConnectoid createAndRegisterDirectedConnectoid(final TransferZone accessZone, final MacroscopicLinkSegment linkSegment, final Set<Mode> allowedModes) throws PlanItException {
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
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(TransferZone transferZone, Set<EdgeSegment> linkSegments, Set<Mode> allowedModes) throws PlanItException {
    Set<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(EdgeSegment linkSegment : linkSegments) {
      DirectedConnectoid newConnectoid = createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes);
      if(newConnectoid != null) {
        createdConnectoids.add(newConnectoid);
      }
    } 
    return createdConnectoids;
  }
  
  /** Create a new PLANit node, register it and update stats
   * 
   * @param osmNode to extract PLANit node for
   * @param networkLayer to create it on
   * @return created planit node
   */
  protected Node extractPlanitNode(OsmNode osmNode, final Map<Long, Node> nodesByOsmId,  MacroscopicPhysicalNetwork networkLayer) {
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
  protected Node extractConnectoidAccessNode(MacroscopicPhysicalNetwork networkLayer, OsmNode osmNode) throws PlanItException {
    PlanitOsmNetworkLayerReaderData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
    
    final Map<Long, Node> nodesByOsmId = layerData.getNodesByOsmId();
    Node planitNode = nodesByOsmId.get(osmNode.getId());
    if(planitNode == null) {
      
      /* make sure that we have the correct mapping from node to link (in case the link has been broken before in the network reader, or here, for example) */
      List<Link> linksWithOsmNodeInternally = layerData.getLinksByInternalOsmNodeIds().get(osmNode.getId()); 
      if(linksWithOsmNodeInternally == null) {
        LOGGER.warning(String.format("Osm pt access node (%d) not internal to parsed Osm way, stop position possibly not attached to network, ignored",osmNode.getId()));
        return null;
      }

      /* node is internal to an existing link, create it and break existing link */
      planitNode = extractPlanitNode(osmNode, nodesByOsmId, networkLayer);      
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
  protected void extractDirectedConnectoidsForMode(OsmNode osmNode, Map<String, String> tags, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode) throws PlanItException {
    
    /* access node */
    Node planitNode = extractConnectoidAccessNode(networkLayer,osmNode);    
    if(planitNode==null) {
      LOGGER.warning(String.format("unable to create pt access node from osm node (%d), ignored",osmNode.getId()));
      return;
    }
    
    /* already created connectoids */
    Map<Long, Set<DirectedConnectoid>> existingConnectoids = zoningReaderData.getDirectedConnectoidsByOsmNodeId(networkLayer);                    
    if(existingConnectoids.containsKey(osmNode.getId())) {      
      
      /* update: connectoid already exists */
      Set<DirectedConnectoid> connectoidsForNode = existingConnectoids.get(osmNode.getId());
      connectoidsForNode.forEach( connectoid -> updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode)));
      
    }else {
      
      /* new connectoid, create and register */
      Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, planitNode.getEntryEdgeSegments(), Collections.singleton(planitMode));      
      if(newConnectoids==null || newConnectoids.isEmpty()) {
        LOGGER.warning(String.format("found eligible mode %s for osm node %d, but no access link segment supports this mode", planitMode.getExternalId(), osmNode.getId()));
      }else {
        newConnectoids.forEach( connectoid -> zoningReaderData.addDirectedConnectoidByOsmId(networkLayer, osmNode.getId(),connectoid));
      }
      
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
  protected PlanitOsmTransferSettings getSettings() {
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
      final PlanitOsmTransferSettings transferSettings, 
      PlanitOsmZoningReaderData zoningReaderData, 
      final PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {

    /* profiler */
    this.profiler = new PlanitOsmZoningHandlerProfiler();
    
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

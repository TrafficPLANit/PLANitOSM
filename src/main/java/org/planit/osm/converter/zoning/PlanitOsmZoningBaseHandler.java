package org.planit.osm.converter.zoning;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.zoning.parser.PlanitOsmConnectoidParser;
import org.planit.osm.converter.zoning.parser.PlanitOsmPublicTransportModeParser;
import org.planit.osm.converter.zoning.parser.PlanitOsmTransferZoneGroupParser;
import org.planit.osm.converter.zoning.parser.PlanitOsmTransferZoneParser;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;
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
    
  /** holds, add to all the tracking data required for parsing zones */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** profiler for this reader */
  private final PlanitOsmZoningHandlerProfiler profiler;   
  
  /** parser functionality regarding the creation of PLANit transfer zones from OSM entities */
  private final PlanitOsmTransferZoneParser transferZoneParser;
  
  /** parser functionality regarding the creation of PLANit transfer zone groups from OSM entities */  
  private final PlanitOsmTransferZoneGroupParser transferZoneGroupParser;
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final PlanitOsmPublicTransportModeParser publicTransportModeParser;  
  
  /** parser functionality regarding the creation of PLANit connectoids from OSM entities */
  private final PlanitOsmConnectoidParser connectoidParser;
    
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
   
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {
    return PlanitOsmZoningHandlerHelper.hasNetworkLayersWithActiveOsmNode(
        osmNodeId, getNetworkToZoningData().getOsmNodes(),getSettings().getReferenceNetwork(), getNetworkToZoningData());
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
    if(PlanitOsmZoningHandlerHelper.containsMappedPlanitMode(modeResult)) {               
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
    return getSettings().getNetworkDataForZoningReader();
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
  
  /** Get the transfer zone group parser
   * 
   * @return transfer zone group parser 
   */
  protected PlanitOsmTransferZoneGroupParser getTransferZoneGroupParser() {
    return this.transferZoneGroupParser;
  }  
  
  /** Get the public transport mode parser
   * 
   * @return public transport mode parser 
   */
  protected PlanitOsmPublicTransportModeParser getPtModeParser() {
    return this.publicTransportModeParser;
  }  
  
  /** Get the connectoid parser
   * 
   * @return connectoidParser parser 
   */
  protected PlanitOsmConnectoidParser getConnectoidParser() {
    return this.connectoidParser;
  }    
    
  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param zoningToPopulate to populate
   * @param profiler to keep track of created/parsed entities across zone handlers
   */
  public PlanitOsmZoningBaseHandler(
      final PlanitOsmPublicTransportReaderSettings transferSettings, 
      PlanitOsmZoningReaderData zoningReaderData,  
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {

    /* profiler */
    this.profiler = profiler;
    
    /* references */
    this.zoning = zoningToPopulate;       
    this.transferSettings = transferSettings;
    this.zoningReaderData = zoningReaderData;
    
    /* parser for creating PLANit transfer zones */
    this.transferZoneParser = new PlanitOsmTransferZoneParser(zoningToPopulate, zoningReaderData, transferSettings, profiler);
    
    /* parser for creating PLANit transfer zone groups */
    this.transferZoneGroupParser = new PlanitOsmTransferZoneGroupParser(zoningToPopulate, zoningReaderData, transferSettings, profiler);    
    
    /* parser for identifying pt PLANit modes from OSM entities */
    this.publicTransportModeParser = new PlanitOsmPublicTransportModeParser(transferSettings.getNetworkDataForZoningReader().getNetworkSettings());
    
    /* parser for creating PLANit connectoids */
    this.connectoidParser = new PlanitOsmConnectoidParser(zoningToPopulate, zoningReaderData, transferSettings, profiler);
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

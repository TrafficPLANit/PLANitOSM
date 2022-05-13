package org.goplanit.osm.converter.zoning.handler;

import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.helper.ConnectoidHelper;
import org.goplanit.osm.converter.zoning.handler.helper.OsmPublicTransportModeHelper;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneHelper;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.functionalinterface.TriConsumer;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Geometry;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Base Handler for all zoning handlers. Contains shared functionality that is used across the different zoning handlers 
 * 
 * @author markr
 * 
 */
public abstract class OsmZoningHandlerBase extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmZoningHandlerBase.class.getCanonicalName());
          
  // references
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final OsmPublicTransportReaderSettings transferSettings;   
    
  /** holds, add to all the tracking data required for parsing zones */
  private final OsmZoningReaderData zoningReaderData;
  
  /** profiler for this reader */
  private final OsmZoningHandlerProfiler profiler; 
  
  /** utilities for geographic information */
  private final PlanitJtsCrsUtils geoUtils;   
  
  /** parser functionality regarding the creation of PLANit transfer zones from OSM entities */
  private final TransferZoneHelper transferZoneHelper;
  
  /** parser functionality regarding the creation of PLANit transfer zone groups from OSM entities */  
  private final TransferZoneGroupHelper transferZoneGroupHelper;
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final OsmPublicTransportModeHelper publicTransportModeHelper;  
  
  /** parser functionality regarding the creation of PLANit connectoids from OSM entities */
  private final ConnectoidHelper connectoidHelper;
      
  /** Skip OSM pt entity when marked for exclusion in settings
   * 
   * @param entityType of entity to verify
   * @param osmId id to verify
   * @return true when it should be skipped, false otherwise
   */  
  private boolean skipOsmPtEntity(EntityType entityType, long osmId) {
    return 
        (entityType.equals(EntityType.Node) && getSettings().isExcludedOsmNode(osmId)) 
        ||
        (entityType.equals(EntityType.Way) && getSettings().isExcludedOsmWay(osmId)); 
  }    
           
  
  /** Skip SOM relation member when marked for exclusion in settings
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

  /** Verify if node resides on or within the zoning bounding polygon. If no bounding area is defined
   * this always returns true
   * 
   * @param osmNode to verify
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  protected boolean isCoveredByZoningBoundingPolygon(OsmNode osmNode) {    
    if(!getSettings().hasBoundingPolygon()) {
      return true;
    }else {
      return OsmBoundingAreaUtils.isCoveredByZoningBoundingPolygon(osmNode, getSettings().getBoundingPolygon());
    }
  }
  
  /** Verify if OSM way has at least one node that resides within the zoning bounding polygon. If no bounding area is defined
   * this always returns true
   * 
   * @param osmWay to verify
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  protected boolean isCoveredByZoningBoundingPolygon(OsmWay osmWay) {    
    if(!getSettings().hasBoundingPolygon()) {
      return true;
    }else {
      return OsmBoundingAreaUtils.isCoveredByZoningBoundingPolygon(osmWay, getSettings().getNetworkDataForZoningReader().getRegisteredOsmNodes(), getSettings().getBoundingPolygon());
    }
  }  
  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId){
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId, getSettings().getReferenceNetwork(), getNetworkToZoningData());
  }   
                                                             
  /** Verify if tags represent an infrastructure used for transfers between modes, for example PT platforms, stops, etc. 
   * and is also activated for parsing based on the related settings
   * 
   * @param tags to verify
   * @return which scheme it is compatible with, NONE if none could be found or if it is not active 
   */  
  protected OsmPtVersionScheme isActivatedPublicTransportInfrastructure(Map<String, String> tags) {
    if(transferSettings.isParserActive()) {
      return OsmPtVersionSchemeUtils.isPublicTransportBasedInfrastructure(tags);
    }
    return OsmPtVersionScheme.NONE;
  }          
  
  /** log the given warning message but only when it is not too close to the bounding box, because then it is too likely that it is discarded due to missing
   * infrastructure or other missing assets that could not be parsed fully as they pass through the bounding box barrier. Therefore the resulting warning message is likely 
   * more confusing than helpful in those situation and is therefore ignored
   * 
   * @param message to log if not too close to bounding box
   * @param geometry to determine distance to bounding box to
   */
  protected void logWarningIfNotNearBoundingBox(String message, Geometry geometry) {
    OsmBoundingAreaUtils.logWarningIfNotNearBoundingBox(message, geometry, getNetworkToZoningData().getNetworkBoundingBox(), geoUtils);
  }
  
  /** Wrap the handling of OSM way for OSM zoning by checking if it is eligible and catch any run time PLANit exceptions, if eligible delegate to consumer.
   * 
   * @param osmWay to parse
   * @param osmWayConsumer to apply to eligible OSM way
   */
  protected void wrapHandlePtOsmWay(OsmWay osmWay, TriConsumer<OsmWay, OsmPtVersionScheme, Map<String, String>> osmWayConsumer) {
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);  
    
    try {       
      
      /* only parse ways that are potentially used for (PT) transfers*/
      OsmPtVersionScheme ptVersion = isActivatedPublicTransportInfrastructure(tags);
      if(ptVersion != OsmPtVersionScheme.NONE || getZoningReaderData().getOsmData().shouldOsmRelationOuterRoleOsmWayBeKept(osmWay)) {
        
        if(skipOsmWay(osmWay)) {
          LOGGER.fine(String.format("Skipped OSM way %d, marked for exclusion", osmWay.getId()));
          return;
        }                    
        
        // Delegate, deemed eligible
        osmWayConsumer.accept(osmWay, ptVersion, tags);
        
      }      
    } catch (PlanItRunTimeException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM way (id:%d) for public transport (zoning transfer) infrastructure", osmWay.getId())); 
    }      

  }

  /** Wrap the handling of OSM node by checking if it is eligible (PT specific) and catch any run time PLANit exceptions, if eligible delegate to consumer.
   *
   * @param osmNode to parse
   * @param osmNodeConsumer to apply to eligible OSM way
   */
  protected void wrapHandlePtOsmNode(OsmNode osmNode, TriConsumer<OsmNode, OsmPtVersionScheme, Map<String, String>> osmNodeConsumer) {

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

        osmNodeConsumer.accept(osmNode,ptVersion, tags);
      }

    } catch (PlanItRunTimeException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM node (id:%d) for public transport infrastructure", osmNode.getId()));
    }
  }
  
  /** Get geo utils
   * 
   * @return geo utils
   */
  protected final PlanitJtsCrsUtils getGeoUtils() {
    return geoUtils;
  }  
    
  /** Get profiler
   * 
   * @return profiler
   */
  protected final OsmZoningHandlerProfiler getProfiler() {
    return profiler;
  }
  
  /** Get zoning reader data
   * 
   * @return data
   */
  protected final OsmZoningReaderData getZoningReaderData() {
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
  protected final OsmNetworkToZoningReaderData getNetworkToZoningData() {
    return getSettings().getNetworkDataForZoningReader();
  }
  
  /** Get PT settings
   * 
   * @return settings
   */
  protected OsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  } 
  
  /** Get the transfer zone parser
   * 
   * @return transfer zone parser 
   */
  protected TransferZoneHelper getTransferZoneHelper() {
    return this.transferZoneHelper;
  }
  
  /** Get the transfer zone group parser
   * 
   * @return transfer zone group parser 
   */
  protected TransferZoneGroupHelper getTransferZoneGroupHelper() {
    return this.transferZoneGroupHelper;
  }  
  
  /** Get the public transport mode parser
   * 
   * @return public transport mode parser 
   */
  protected OsmPublicTransportModeHelper getPtModeHelper() {
    return this.publicTransportModeHelper;
  }  
  
  /** Get the connectoid parser
   * 
   * @return connectoidParser parser 
   */
  protected ConnectoidHelper getConnectoidHelper() {
    return this.connectoidHelper;
  }    
    
  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param zoningToPopulate to populate
   * @param profiler to keep track of created/parsed entities across zone handlers
   */
  public OsmZoningHandlerBase(
      final OsmPublicTransportReaderSettings transferSettings, 
      OsmZoningReaderData zoningReaderData,  
      final Zoning zoningToPopulate,
      final OsmZoningHandlerProfiler profiler) {

    /* profiler */
    this.profiler = profiler;       
    
    /* references */
    this.zoning = zoningToPopulate;       
    this.transferSettings = transferSettings;
    this.zoningReaderData = zoningReaderData;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsCrsUtils(getSettings().getReferenceNetwork().getCoordinateReferenceSystem());    
    
    /* parser for creating PLANit transfer zones */
    this.transferZoneHelper = new TransferZoneHelper(zoningToPopulate, zoningReaderData, transferSettings, profiler);
    
    /* parser for creating PLANit transfer zone groups */
    this.transferZoneGroupHelper = new TransferZoneGroupHelper(zoningToPopulate, zoningReaderData, transferSettings, profiler);    
    
    /* parser for identifying pt PLANit modes from OSM entities */
    this.publicTransportModeHelper = new OsmPublicTransportModeHelper(transferSettings.getNetworkDataForZoningReader().getNetworkSettings());
    
    /* parser for creating PLANit connectoids */
    this.connectoidHelper = new ConnectoidHelper(zoningToPopulate, zoningReaderData, transferSettings, profiler);
  }
  
  /** Call this BEFORE we parse the OSM network to initialise the handler properly
   * 
   */
  public abstract void initialiseBeforeParsing();
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public abstract void reset();  
  

  
}

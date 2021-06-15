package org.planit.osm.converter.zoning.handler;

import java.util.Map;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Geometry;
import org.planit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.planit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.planit.osm.converter.zoning.OsmZoningReaderData;
import org.planit.osm.converter.zoning.handler.helper.ConnectoidHelper;
import org.planit.osm.converter.zoning.handler.helper.OsmPublicTransportModeHelper;
import org.planit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.planit.osm.converter.zoning.handler.helper.TransferZoneHelper;
import org.planit.osm.util.*;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.zoning.Zoning;
import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;

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
      return OsmBoundingAreaUtils.isCoveredByZoningBoundingPolygon(osmWay, getSettings().getNetworkDataForZoningReader().getOsmNodes(), getSettings().getBoundingPolygon());      
    }
  }  
  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {
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
   * @throws PlanItException thrown if error
   */
  protected void logWarningIfNotNearBoundingBox(String message, Geometry geometry) throws PlanItException {
    OsmBoundingAreaUtils.logWarningIfNotNearBoundingBox(message, geometry, getNetworkToZoningData().getNetworkBoundingBox(), geoUtils);
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
   * @throws PlanItException  thrown if error
   */
  public abstract void initialiseBeforeParsing() throws PlanItException;
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public abstract void reset();  
  

  
}

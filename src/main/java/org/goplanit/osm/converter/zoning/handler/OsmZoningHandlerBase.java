package org.goplanit.osm.converter.zoning.handler;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.*;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.helper.OsmConnectoidHelper;
import org.goplanit.osm.converter.zoning.handler.helper.OsmPublicTransportModeConversion;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneGroupHelper;
import org.goplanit.osm.converter.zoning.handler.helper.TransferZoneHelper;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.OsmPtv2Tags;
import org.goplanit.osm.tags.OsmRelationTypeTags;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.functionalinterface.TriConsumer;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Geometry;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

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

  /** the reference network to use */
  private final PlanitOsmNetwork referenceNetwork;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final OsmPublicTransportReaderSettings transferSettings;   
    
  /** holds, add to all the tracking data required for parsing zones */
  private final OsmZoningReaderData zoningReaderData;

  /**
   * the network data required to perform successful parsing of zones, passed in exogenously from an OSM network reader
   * after parsing the reference network
   */
  private final OsmNetworkToZoningReaderData network2ZoningData;
  
  /** profiler for this reader */
  private final OsmZoningHandlerProfiler profiler; 
  
  /** utilities for geographic information */
  private final PlanitJtsCrsUtils geoUtils;   
  
  /** parser functionality regarding the creation of PLANit transfer zones from OSM entities */
  private final TransferZoneHelper transferZoneHelper;
  
  /** parser functionality regarding the creation of PLANit transfer zone groups from OSM entities */  
  private final TransferZoneGroupHelper transferZoneGroupHelper;
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final OsmPublicTransportModeConversion publicTransportModeHelper;
  
  /** parser functionality regarding the creation of PLANit connectoids from OSM entities */
  private final OsmConnectoidHelper connectoidHelper;
      
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

  protected PlanitOsmNetwork getReferenceNetwork(){
    return referenceNetwork;
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
    if(!getSettings().hasBoundingBoundary() || !getSettings().getBoundingArea().hasBoundingPolygon()) {
      return true;
    }else {
      return OsmBoundingAreaUtils.isCoveredByZoningBoundingPolygon(osmNode, getSettings().getBoundingArea().getBoundingPolygon());
    }
  }

  /** Verify if node resides near the zoning bounding polygon based on #OsmNetworkReaderData. If no bounding area is defined
   * this always returns true
   *
   * @param osmNode to verify
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  protected boolean isNearNetworkBoundingBox(OsmNode osmNode) {
    if(!getSettings().hasBoundingBoundary() || !getSettings().getBoundingArea().hasBoundingPolygon()){
      return false;
    }
    return OsmBoundingAreaUtils.isNearNetworkBoundingBox(
            OsmNodeUtils.createPoint(osmNode),
            getSettings().getBoundingArea().getBoundingPolygon().getEnvelopeInternal(), getGeoUtils());
  }
  
  /** Verify if OSM way has at least one node that resides within the zoning bounding polygon. If no bounding area is defined
   * this always returns true
   * 
   * @param osmWay to verify
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  protected boolean isCoveredByZoningBoundingPolygon(OsmWay osmWay) {    
    if(!getSettings().hasBoundingBoundary() || !getSettings().getBoundingArea().hasBoundingPolygon()) {
      return true;
    }else {
      return OsmBoundingAreaUtils.isCoveredByZoningBoundingPolygon(
              osmWay,
              zoningReaderData.getOsmData().getOsmNodeData().getRegisteredOsmNodes(),
              getSettings().getBoundingArea().getBoundingPolygon());
    }
  }  
  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId){
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId, getReferenceNetwork(), getNetworkToZoningData());
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

  /**
   * Wrap the handling of OSM relation by performing basic checks on eligibility and wrapping any run time excpetions thrown
   *
   * @param osmRelation to wrap parsing of
   * @param osmRelationConsumer to apply when relation is has Ptv2 public transport tags and is either a stop area or multipolygon transport platform
   */
  protected void wrapHandlePtOsmRelation(OsmRelation osmRelation, BiConsumer<OsmRelation, Map<String, String>> osmRelationConsumer){
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmRelation);
    try {

      /* only parse when parser is active and type is available */
      if(getSettings().isParserActive() && tags.containsKey(OsmRelationTypeTags.TYPE) && OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {

        /* public transport type */
        String ptv2Type = tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT);
        String relationType = tags.get(OsmRelationTypeTags.TYPE);
        if(relationType.equals(OsmRelationTypeTags.PUBLIC_TRANSPORT)) {
          /* stop_area: is only supported/expected type under PT relation */
          if(!ptv2Type.equals(OsmPtv2Tags.STOP_AREA)) {
            /* anything else is not expected */
            LOGGER.info(String.format("DISCARD: The public_transport relation type `%s` (%d) not (yet) supported", tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));
            return;
          }
        }else if(relationType.equals(OsmRelationTypeTags.MULTIPOLYGON)) {
          /* multi_polygons are only accepted when  representing public transport platforms */
          if(!ptv2Type.equals(OsmPtv2Tags.PLATFORM_ROLE)) {
            return;
          }
        }else{
          return;
        }

        /* when not returned, it represents a potentially supported OSM Pt relation */
        osmRelationConsumer.accept(osmRelation, tags);
      }

    } catch (PlanItRunTimeException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM relation (id:%d) for transfer infrastructure", osmRelation.getId()));
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
    return network2ZoningData;
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
  protected OsmPublicTransportModeConversion getPtModeHelper() {
    return this.publicTransportModeHelper;
  }  
  
  /** Get the connectoid parser
   * 
   * @return connectoidParser parser 
   */
  protected OsmConnectoidHelper getConnectoidHelper() {
    return this.connectoidHelper;
  }    
    
  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @param referenceNetwork to use
   * @param zoningToPopulate to populate
   * @param profiler to keep track of created/parsed entities across zone handlers
   */
  public OsmZoningHandlerBase(
      final OsmPublicTransportReaderSettings transferSettings,
      OsmZoningReaderData zoningReaderData,
      OsmNetworkToZoningReaderData network2ZoningData,
      final PlanitOsmNetwork referenceNetwork,
      final Zoning zoningToPopulate,
      final OsmZoningHandlerProfiler profiler) {

    /* profiler */
    this.profiler = profiler;       
    
    /* references */
    this.referenceNetwork = referenceNetwork;
    this.zoning = zoningToPopulate;       
    this.transferSettings = transferSettings;
    this.zoningReaderData = zoningReaderData;

    this.network2ZoningData = network2ZoningData;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsCrsUtils(getReferenceNetwork().getCoordinateReferenceSystem());
    
    /* parser for creating PLANit transfer zones */
    this.transferZoneHelper = new TransferZoneHelper(
        getReferenceNetwork(), zoningToPopulate, zoningReaderData, network2ZoningData, transferSettings, profiler);
    
    /* parser for creating PLANit transfer zone groups */
    this.transferZoneGroupHelper = new TransferZoneGroupHelper(
        getReferenceNetwork(), zoningToPopulate, zoningReaderData, network2ZoningData, transferSettings, profiler);
    
    /* parser for identifying pt PLANit modes from OSM entities */
    this.publicTransportModeHelper =
        new OsmPublicTransportModeConversion(
            getNetworkToZoningData().getNetworkSettings(), getSettings(), getReferenceNetwork().getModes());
    
    /* parser for creating PLANit connectoids */
    this.connectoidHelper = new OsmConnectoidHelper(
        referenceNetwork, zoningToPopulate, zoningReaderData, network2ZoningData, transferSettings, profiler);
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

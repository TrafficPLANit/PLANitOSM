package org.planit.osm.zoning;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.network.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Point;
import org.planit.geo.PlanitJtsUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
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
  
  // references
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;
  
  /** settings used to populate the reference network */
  PlanitOsmNetworkSettings referenceNetworkSettings;   
  
  /** the network assumed to have been populated already and compatible with the about to be populated zoning */
  private final PlanitOsmNetwork referenceNetwork;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final PlanitOsmTransferSettings transferSettings;    

  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils;
      
  
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
    if(isCompatibleWith(OsmPtVersionScheme.VERSION_1,tags)) {
      return OsmPtVersionScheme.VERSION_1;
    }else if(isCompatibleWith(OsmPtVersionScheme.VERSION_2, tags)){
      return OsmPtVersionScheme.VERSION_2;
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
  
  /** create a transfer zone based on the passed in osm entity, tags for feature extraction and access
   * @param osmNode node that is to be converted into a transfer zone
   * @param tags tags to extract features from
   * @param transferZoneType the type of the transfer zone 
   * @return transfer zone created
   */
  private TransferZone createAndPopulateTransferZone(OsmNode osmNode, Map<String, String> tags, TransferZoneType transferZoneType) {
    /* create */
    TransferZone transferZone = zoning.transferZones.createNew();
    /* type */
    transferZone.setTransferZoneType(transferZoneType);

    /* centroid based location */
    {
      try {
        Point geometry = PlanitJtsUtils.createPoint(PlanitOsmUtils.getXCoordinate(osmNode), PlanitOsmUtils.getYCoordinate(osmNode));
        transferZone.getCentroid().setPosition(geometry);
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
      }
    }
    
    /* XML id = internal id*/
    transferZone.setXmlId(Long.toString(transferZone.getId()));
    /* external id  = osm node id*/
    transferZone.setExternalId(transferZone.getXmlId());
    
    return transferZone;
  }  
  
  private void extractOsmWayTransferInfrastructurePtv2(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {

    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv2) for osm way %s, bit no compatible key tags found",osmWay.getId()));
    }
  }

  private void extractOsmWayTransferInfrastructurePtv1(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {

    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv1) for osm way %s, bit no compatible key tags found",osmWay.getId()));
    }
  }  
  

  /** extract the transfer infrastructure which will contribute to newly created transfer zones on the zoning instance
   * 
   * @param osmWay to parse
   * @param ptVersion this way adheres to
   * @param tags to use
   * @throws PlanItException thrown if error
   */
  protected void extractOsmWayTransferInfrastructure(OsmWay osmWay, OsmPtVersionScheme ptVersion, Map<String, String> tags) throws PlanItException{
    if(ptVersion == OsmPtVersionScheme.VERSION_2) {
      extractOsmWayTransferInfrastructurePtv2(osmWay, tags);
    }else if(ptVersion == OsmPtVersionScheme.VERSION_1) {
      extractOsmWayTransferInfrastructurePtv1(osmWay, tags);
    }
  }    
  
  private void extractOsmNodeTransferInfrastructurePtv2(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {

    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv2) for osm node %s, but no compatible key tags found",osmNode.getId()));
    }
  }

  /** Classic PT infrastructure based on original OSM public transport scheme
   * 
   * @param osmNode to parse
   * @param tags of the node
   * @throws PlanItException thrown if error
   */
  private void extractOsmNodeTransferInfrastructurePtv1(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {      
      
      /* bus_stop or (bus) platform*/
      if(referenceNetworkSettings.hasMappedPlanitMode(OsmRoadModeTags.BUS)) {
        /* bus stops are located next to the roads as they indicate waiting locations,
         * in Ptv1 the stop position of the bus is implicit and based on the nearest link/node */
        
        String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
        TransferZoneType transferzoneType = TransferZoneType.NONE;
        boolean busStopBased = false;      
        
        if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){
          busStopBased = true;
          transferzoneType = TransferZoneType.POLE;
        }else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){
          LOGGER.warning(String.format("node %s with highway=platform encountered, assuming bus access only",osmNode.getId()));
          busStopBased = referenceNetworkSettings.hasMappedPlanitMode(OsmRoadModeTags.BUS);
          transferzoneType = TransferZoneType.PLATFORM;
        }
        
        if(busStopBased) {
          /* create and register */
          TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, transferzoneType);
          zoning.transferZones.register(transferZone);
                  
          /* connectoid */
          Mode planitBusMode = referenceNetworkSettings.getMappedPlanitMode(OsmRoadModeTags.BUS);        
          MacroscopicLinkSegment accessLinkSegment = null;  //TODO: identify most likely closest node/link accessible with planitBusMode
          double length = 0;
          zoning.connectoids.registerNew(accessLinkSegment, transferZone, length);        
        }else {
          LOGGER.warning(String.format("unsupported Ptv1 higway=%s tag encountered, ignored",ptv1ValueTag));
        }        
      }

    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);
      
      /* tram stop */
      if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && referenceNetworkSettings.hasMappedPlanitMode(OsmRailwayTags.TRAM)) {
        /* in contrast to highway=bus_stop this tag is tagged on the track because tram tracks are usually 
         * one-way and different directions don't have to be considered */
        
        /* create and register */
        TransferZone transferZone = createAndPopulateTransferZone(osmNode,tags, TransferZoneType.PLATFORM);
        zoning.transferZones.register(transferZone);    
        
        Mode planitTramMode = referenceNetworkSettings.getMappedPlanitMode(OsmRailwayTags.TRAM);
        /* collect node by external id, then find incoming links. Should alsways be 1, if not issue warning
         * use incoming link as the link segment to register with */
         CONTINUE HERE READ THE ABOVE
        MacroscopicLinkSegment accessLinkSegment = null;  
        double length = 0;
        zoning.connectoids.registerNew(accessLinkSegment, transferZone, length);        
      }
      
      /* train halt */
      
      /* train station */
      
    }else {
      throw new PlanItException(String.format("parsing transfer infrastructure (Ptv1) for osm node %s, bit no compatible key tags found",osmNode.getId()));
    }    
  }  

  /** extract the transfer infrastructure which will contribute to newly created transfer zones on the zoning instance
   * 
   * @param osmNode to parse
   * @param ptVersion this node adheres to
   * @param tags to use
   * @throws PlanItException thrown if error
   */  
  protected void extractOsmNodeTransferInfrastructure(OsmNode osmNode, OsmPtVersionScheme ptVersion, Map<String, String> tags) throws PlanItException{
    if(ptVersion == OsmPtVersionScheme.VERSION_2) {
      extractOsmNodeTransferInfrastructurePtv2(osmNode, tags);
    }else if(ptVersion == OsmPtVersionScheme.VERSION_1) {
      extractOsmNodeTransferInfrastructurePtv1(osmNode, tags);
    }
  }  


  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param referenceNetworkSettings used to populate the referenceNetwork
   * @param referenceNetwork to use
   * @param zoningToPopulate to populate
   */
  public PlanitOsmZoningHandler(final PlanitOsmTransferSettings transferSettings, PlanitOsmNetworkSettings referenceNetworkSettings, final PlanitOsmNetwork referenceNetwork, final Zoning zoningToPopulate) {
    this.referenceNetwork = referenceNetwork;
    this.referenceNetworkSettings = referenceNetworkSettings;
    this.zoning = zoningToPopulate;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsUtils(referenceNetwork.getCoordinateReferenceSystem());
    
    this.transferSettings = transferSettings;
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(referenceNetwork.infrastructureLayers == null || referenceNetwork.infrastructureLayers.size()<=0,"network is expected to be populated at start of parsing OSM zoning");       
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
        extractOsmNodeTransferInfrastructure(osmNode, ptVersion, tags);
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
        extractOsmWayTransferInfrastructure(osmWay, ptVersion, tags);
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
                
    LOGGER.info(" OSM (transfer) zone parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {   
  }
  
}

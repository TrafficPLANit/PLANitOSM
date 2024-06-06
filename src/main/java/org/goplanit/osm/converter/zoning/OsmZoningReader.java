package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.util.logging.Logger;

import org.goplanit.converter.zoning.ZoningReader;
import org.goplanit.osm.converter.OsmBoundaryManager;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.OsmZoningHandlerBase;
import org.goplanit.osm.converter.zoning.handler.OsmZoningHandlerProfiler;
import org.goplanit.osm.converter.zoning.handler.OsmZoningPostProcessingHandler;
import org.goplanit.osm.converter.zoning.handler.OsmZoningPreProcessingHandler;
import org.goplanit.osm.converter.zoning.handler.OsmZoningPreProcessingHandler.Stage;
import org.goplanit.osm.converter.zoning.handler.OsmZoningMainProcessingHandler;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.Osm4JUtils;
import org.goplanit.osm.util.PlanitZoningUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.zoning.Zoning;

import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;
import org.locationtech.jts.geom.TopologyException;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit zoning instance comprising of the identified transfer zones.
 * Note that OSM data does not contain any information regarding OD zones, so this will be empty.
 * <p>
 * Further note that because a PLANit zoning relies on the network, we must first initialise the zoning reader
 * before calling the read() method with the necessary data obtained by the related Osm network reader, otherwise
 * parsing the zoning information will fail.
 *
 * @author markr
 *
 */
public class OsmZoningReader implements ZoningReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningReader.class.getCanonicalName());
  
  /** the handler conducting parsing in preparation for the osmHandler*/
  private OsmZoningPreProcessingHandler osmPreProcessingHandler = null;
      
  /** the handler conducting the main parsing pass */
  private OsmZoningMainProcessingHandler osmHandler = null;   
  
  /** the handler for the final parsing as post-processing step of the reader */
  private OsmZoningPostProcessingHandler osmPostProcessingHandler = null;
  
  /** the settings the user can configure for parsing transfer zones */
  private final OsmPublicTransportReaderSettings transferSettings;
  
  /** the (temporary) data gathered during parsing of OSM (transfer) zones */
  private OsmZoningReaderData zoningReaderData;

  /**
   * the network data required to perform successful parsing of zones, passed in exogenously from an OSM network reader
   * after parsing the reference network
   */
  private final OsmNetworkToZoningReaderData network2ZoningData;

  /** reference network to use */
  private final PlanitOsmNetwork referenceNetwork;
    
  // references
      
  /** zoning to populate */
  private Zoning zoning;
       
  /**
   * Log some information about this reader's configuration 
   */
  private void logInfo() {
    getSettings().logSettings();
  }       
  
  /** Make sure that if a bounding area is available on the network, any explicitly defined zoning bounding polygon
   * should ideally not exceed the network bounding area since it makes little sense to try and parse pt infrastructure
   * outside the network's geographically parsed area. If it does exceed, log a warning and replace it with the network
   * bounding area instead.
   * <p>
   *   In case the bounding area for the zoning is based on a name rather than a polygon, we cannot yet detect such a mismatch
   *   as the polygon for the zoning is yet to be extracted from the OSM data. In such a case this check is ignored.
   * </p>
   *
   * @param boundaryManager bounding area manager for zoning
   */
  private void validateZoningBoundingPolygon(OsmBoundaryManager boundaryManager) {
    var networkBoundingBoundary = network2ZoningData.getNetworkBoundingBoundary();
    if(networkBoundingBoundary==null){
      // undefined for network unable to determine if zoning boundary falls within easily
      return;
    }
    if(!networkBoundingBoundary.hasBoundingPolygon()){
      LOGGER.severe("Network bounding boundary configured but no polygon available, this shouldn't happen when parsing zoning");
      return;
    }
    var networkBoundingPolygon = networkBoundingBoundary.getBoundingPolygon();

    if(boundaryManager.isConfigured() && boundaryManager.isComplete()){

      // able to compare, attempt to perform comparison (this may go wrong if any of the two polygons is malformed)
      boolean sameBoundingArea = true;
      try {
        sameBoundingArea = !networkBoundingPolygon.equalsTopo(boundaryManager.getCompleteBoundingArea().getBoundingPolygon());
      }catch(TopologyException e){
        LOGGER.warning(e.getMessage());
        LOGGER.warning("Comparing network and zoning Bounding polygons caused unexpected exception, comparison skipped");
      }

      if(!sameBoundingArea &&
          !boundaryManager.getCompleteBoundingArea().getBoundingPolygon().within(networkBoundingPolygon)){
        LOGGER.warning("SALVAGE: Bounding polygon for network is more restrictive than public transport, " +
            "replacing with network bounding polygon");
        boundaryManager.overrideBoundingArea(networkBoundingBoundary.deepClone());
      }
    }
  }

  /**
   * perform final preparation before conducting parsing of OSM pt entities
   *
   * @param boundaryManager bounding area manager for zoning
   */
  private void initialiseBeforeParsing(OsmBoundaryManager boundaryManager) {
    
    /* if not set, create zoning to populate here based on network id tokens */
    if(zoning==null) {
      this.zoning = new Zoning(getReferenceNetwork().getIdGroupingToken(),getReferenceNetwork().getNetworkGroupingTokenId());
    }
    
    /* make country name available in zoning reader data during parsing */
    this.zoningReaderData = new OsmZoningReaderData(getSettings().getCountryName());    
    /* spatially index all links to register on data trackers for use in handlers */
    zoningReaderData.getPlanitData().initialiseSpatiallyIndexedLinks(getReferenceNetwork());
    
    /* make sure that if a bounding box has been set, the zoning bounding box does not exceed the network bounding box
     * since it makes little sense to try and parse pt infrastructure outside of the network's geographically parsed area */
    validateZoningBoundingPolygon(boundaryManager);
  }

  /** it is possible some preregistered OSM nodes part of ways are not available, for example when the way crosses the bounding box and
   * is only partly present. In those cases we want to prune those nodes from the pre-registered nodes to avoid issues later on where
   * the index exists, but not the actual value. this method is to be called after the processing of all OSM nodes but before we do OSM ways
   */
  private void pruneUnavailablePreregisteredOsmNodes() {
    this.zoningReaderData.getOsmData().getOsmNodeData().removeRegisteredOsmNodesIf(e -> e.getValue()==null);
  }

  /**
   * Conduct pre-processing step of zoning reader that cannot be conducted as part of the regular processing due to
   * ordering conflicts of parsing OSM entities.
   *
   * @param boundaryManager to deal with bounding area (if any)
   * @param profiler        to use
   */
  private void doPreprocessing(OsmBoundaryManager boundaryManager, final OsmZoningHandlerProfiler profiler){

    // STAGE 1-3: BOUNDARY PREPROCESSING (3 stages if active)
    performBoundingAreaPreProcessing(boundaryManager, profiler);
    if(boundaryManager.isConfigured()){
      if(!boundaryManager.isComplete()) {
        LOGGER.severe("User configured bounding area, but no valid boundary could be constructed during pre-processing, this shouldn't happen");
      }else {
        zoningReaderData.setBoundingArea(boundaryManager.getCompleteBoundingArea());
      }
    }

    // STAGE 4: PROCESS RELATIONS WITH MEMBERS THAT NEED TRACKING
    {
      /* identify all relations that represent a (single) platform either as a single polygon, or multi-polygon
       * and mark their ways to be kept, which then in the next pass ensures these ways' nodes are pre-registered to be kept as well */
      createHandlerAndRead(Stage.FOUR_IDENTIFY_RELATION_MEMBERS, boundaryManager, profiler);
      LOGGER.info("Pre-processing: Identifying eligible public transport infrastructure compatible relations");
      if (zoningReaderData.getOsmData().hasOsmRelationOuterRoleOsmWays()) {
        LOGGER.info(String.format("Pre-processing: Identified %d OSM ways that are outer roles of osm relations and eligible to be converted to platforms", zoningReaderData.getOsmData().getNumberOfOuterRoleOsmWays()));
      }
    }

    // STAGE 5: PREREGISTER NODES of WAYS
    {
      LOGGER.info("Pre-processing: Identifying OSM ways for public transport infrastructure parsing");
      createHandlerAndRead(Stage.FIVE_PREREGISTER_ZONING_WAY_NODES, boundaryManager, profiler);
    }

    // STAGE 5: FINALISE REGISTRATION OF IDENTIFIED NODES AND WAYS
    {
      LOGGER.info("Pre-processing: registering eligible OSM nodes of identified OSM ways for public transport processing");
      createHandlerAndRead(Stage.SIX_REGISTER_ZONING_NODES_AND_WAYS, boundaryManager, profiler);
    }

    pruneUnavailablePreregisteredOsmNodes();
  }

  private void performBoundingAreaPreProcessing(
      OsmBoundaryManager boundaryManager, final OsmZoningHandlerProfiler profiler) {
    if (!boundaryManager.isConfigured()) {
      LOGGER.info("Pre-processing: No zoning bounding boundary defined, skip pre-processing all OSM entities eligible");
      return;
    }

    /* STAGE 1 - BOUNDARY IDENTIFICATION
     * identify OSM relation by name if bounding area is specified by name rather than an explicit bounding box */
    if (boundaryManager.isConfigured() && !boundaryManager.isComplete()) {

      /* STAGE 1 - BOUNDARY IDENTIFICATION */
      {
        LOGGER.info(String.format(
            "Pre-processing: Identifying zoning bounding boundary for %s", getSettings().getBoundingArea().getBoundaryName()));
        createHandlerAndRead(OsmZoningPreProcessingHandler.Stage.ONE_IDENTIFY_BOUNDARY_BY_NAME, boundaryManager, profiler);
      }

      /* STAGE 2 - REGULAR PREPROCESSING */
      {
        LOGGER.info("Preprocessing: reducing memory footprint, identifying required OSM nodes for network building");
        createHandlerAndRead(OsmZoningPreProcessingHandler.Stage.TWO_IDENTIFY_WAYS_FOR_BOUNDARY, boundaryManager, profiler);
      }

      /* STAGE 3 - FINALISE BOUNDING BOUNDARY */
      {
        LOGGER.info("Preprocessing: Finalising network bounding boundary, tracking OSM nodes for boundary");
        createHandlerAndRead(OsmZoningPreProcessingHandler.Stage.THREE_FINALISE_BOUNDARY_BY_NAME, boundaryManager, profiler);
      }
    }else{
      LOGGER.info("Skip pre-processing boundary identification stages, zoning bounding boundary directly defined");
    }

    if(boundaryManager.isConfigured() && !boundaryManager.isComplete()){
      LOGGER.severe("User configured bounding area, but no valid boundary could be constructed during pre-processing, this shouldn't happen");
      return;
    }

    zoningReaderData.setBoundingArea(boundaryManager.getCompleteBoundingArea());
  }

  private void createHandlerAndRead(
      Stage preProcessingStage, OsmBoundaryManager boundaryManager, OsmZoningHandlerProfiler profiler) {

    /* reader to parse the actual file or source location */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputSource());
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for preprocessing zoning, aborting");
      return;
    }
    var osmHandler = new OsmZoningPreProcessingHandler(
        this.getReferenceNetwork(),
        this.zoning,
        this.transferSettings,
        boundaryManager, this.zoningReaderData,
        this.network2ZoningData,
        preProcessingStage,
        profiler);
    read(osmReader, osmHandler);
  }

  /**
   * conduct main processing step of zoning reader given the information available from pre-processing
   *  
   * @param profiler  to use
   */
  private void doMainProcessing(OsmZoningHandlerProfiler profiler) {
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputSource());
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for zones, aborting");
    }else {

      /* handler to deal with callbacks from osm4j */
      osmHandler = new OsmZoningMainProcessingHandler(
          this.transferSettings, 
          this.zoningReaderData,
          this.network2ZoningData,
          getReferenceNetwork(),
          this.zoning, 
          profiler);
      read(osmReader, osmHandler);
    } 
  }   
  
  /**
   * conduct post-processing processing step of zoning reader given the information available from pre-processing
   *  
   * @param profiler  to use
   */
  private void doPostProcessing(OsmZoningHandlerProfiler profiler) {
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputSource());
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for post-processing zones, aborting");
    }else {

      /* handler to deal with callbacks from osm4j */
      osmPostProcessingHandler = new OsmZoningPostProcessingHandler(
          this.transferSettings, 
          this.zoningReaderData,
          this.network2ZoningData,
          getReferenceNetwork(),
          this.zoning,
          profiler);
      read(osmReader, osmPostProcessingHandler);        
    } 
  }

  protected PlanitOsmNetwork getReferenceNetwork(){
    return referenceNetwork;
  }

  /** conduct reading of data with given reader and handler
   * 
   * @param osmReader to use
   * @param osmHandler to use
   */
  protected void read(OsmReader osmReader, OsmZoningHandlerBase osmHandler){
    try {  
      osmHandler.initialiseBeforeParsing();
      /* register handler */
      osmReader.setHandler(osmHandler);
      /* conduct parsing which will call back the handler*/
      osmReader.read();  
    }catch (OsmInputException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItRunTimeException("Error during parsing of OSMfile",e);
    }       
  }

  /**
   * Constructor 
   * 
   * @param inputSource to parse from
   * @param countryName this zoning is used for
   * @param zoningToPopulate zoning to populate 
   * @param referenceNetwork to use
   * @param network2ZoningData to use
   */
  protected OsmZoningReader(
      URL inputSource, String countryName, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData){
    this(new OsmPublicTransportReaderSettings(inputSource, countryName), zoningToPopulate, referenceNetwork, network2ZoningData);
  }

  /**
   * Constructor. Requires user to set reference network and networkToZoning data manually afterwards
   *
   * @param settings to use
   * @param referenceNetwork to use
   * @param zoningToPopulate zoning to populate
   * @param network2ZoningData to use
   */
  protected OsmZoningReader(
      OsmPublicTransportReaderSettings settings,  Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData){
    this.transferSettings = settings;
    this.referenceNetwork = referenceNetwork;
    this.zoning = zoningToPopulate;
    this.network2ZoningData = network2ZoningData;
  }

  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a PLANit Zoning instance given the configuration options that have been set
   * 
   * @return macroscopic zoning that has been parsed
   */
  @Override
  public Zoning read() {
    PlanItRunTimeException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()), "Country not set for OSM zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getSettings().getInputSource(), "Input source not set for OSM zoning reader, unable to proceed");
    PlanItRunTimeException.throwIfNull(getReferenceNetwork(),"Reference network not available when parsing OSM zoning, unable to proceed");
    PlanItRunTimeException.throwIfNull(getReferenceNetwork().isEmpty(),"Reference network empty, unable to attach OSM zoning results");
    PlanItRunTimeException.throwIfNull(network2ZoningData,"Reference network data (network to zoning data) not available when parsing OSM zoning, unable to proceed until provided via zoning settings");

    var userConfiguredBoundingArea = getSettings().getBoundingArea();
    if(userConfiguredBoundingArea == null && network2ZoningData.getNetworkBoundingBoundary() != null){
      LOGGER.warning("Network is based on bounding area, but zoning is not, adopting network bounding area to match");
      userConfiguredBoundingArea = network2ZoningData.getNetworkBoundingBoundary();
    }
    var boundaryManager = new OsmBoundaryManager(userConfiguredBoundingArea);

    /* prepare for parsing */
    initialiseBeforeParsing(boundaryManager);
    
    OsmZoningHandlerProfiler handlerProfiler = new OsmZoningHandlerProfiler();
    logInfo();
                
    /* preprocessing (multi-polygon relation: OSM way identification)*/
    doPreprocessing(boundaryManager, handlerProfiler);
    
    /* main processing  (all but stop_positions)*/
    doMainProcessing(handlerProfiler);
    
    /* post-processing (stop_positions to connectoid) */
    doPostProcessing(handlerProfiler);
    
    /* log stats */
    handlerProfiler.logProcessingStats(zoningReaderData, zoning);
    
    /* remove any dangling zones, e g., transfer zones without connectoids etc. */
    if(getSettings().isRemoveDanglingZones()) {
      PlanitZoningUtils.removeDanglingZones(zoning);
    }
    
    /* remove any dangling zones, e g., transfer zones without connectoids etc. */
    if(getSettings().isRemoveDanglingTransferZoneGroups()) {    
      PlanitZoningUtils.removeDanglingTransferZoneGroups(zoning);
    }    
    
    LOGGER.info(" OSM zoning parsing...DONE");
    
    /* return parsed zoning */
    return this.zoning;    
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    /* free memory */
    this.osmHandler.reset();
    this.osmPreProcessingHandler.reset();
    this.osmPostProcessingHandler.reset();
    this.zoningReaderData.reset();
  }  

  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public OsmPublicTransportReaderSettings getSettings() {
    return transferSettings;
  }



}

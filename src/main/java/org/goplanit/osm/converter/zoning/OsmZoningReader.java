package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.util.logging.Logger;

import org.goplanit.converter.zoning.ZoningReader;
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
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.zoning.Zoning;

import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;

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
    LOGGER.info(String.format("OSM (transfer) zoning input file: %s",getSettings().getInputSource()));
    if(getSettings().hasBoundingPolygon()) {
      LOGGER.info(String.format("Bounding polygon set to: %s",getSettings().getBoundingPolygon().toString()));
    }
  }       
  
  /** Make sure that if a bounding box has been set, the zoning bounding box does not exceed the network bounding box
   * since it makes little sense to try and parse pt infrastructure outside of the network's geographically parsed area
   */
  private void validateZoningBoundingPolygon() {

    boolean zoningBoundingPolygonWithinNetworkBoundingPolygon = true;
    if(getSettings().hasBoundingPolygon() && network2ZoningData.getNetworkSettings().hasBoundingPolygon() &&
       !getSettings().getBoundingPolygon().equalsTopo(network2ZoningData.getNetworkSettings().getBoundingPolygon())){
      /* check if it falls within the network bounding polygon */
      zoningBoundingPolygonWithinNetworkBoundingPolygon = 
          getSettings().getBoundingPolygon().within(network2ZoningData.getNetworkSettings().getBoundingPolygon());
    }else if(!getSettings().hasBoundingPolygon() && network2ZoningData.getNetworkSettings().hasBoundingPolygon()) {
        zoningBoundingPolygonWithinNetworkBoundingPolygon = false;
    }
    if(!zoningBoundingPolygonWithinNetworkBoundingPolygon) {
      LOGGER.warning("SALVAGE: Bounding polygon for network is more restrictive than public transport, truncating to network bounding polygon");
      getSettings().setBoundingPolygon(network2ZoningData.getNetworkSettings().getBoundingPolygon());
    }
  }

  /**
   * perform final preparation before conducting parsing of OSM pt entities
   */
  private void initialiseBeforeParsing() {
    
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
    validateZoningBoundingPolygon();

  }

  /**
   * conduct pre-processing pass to identify the platform relation OSM ways that we should mark to register (its nodes) to be available
   * in memory when conducting the actual parsing of features later on. 
   * @param profiler to use
   */
  private void preProcessPlatformRelations(final OsmZoningHandlerProfiler profiler) {
    /* reader to parse the actual file for preprocessing  */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputSource());
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for pre-processing platforms modelled as polygons, aborting");
    }else {    
      osmPreProcessingHandler = new OsmZoningPreProcessingHandler(
          this.getReferenceNetwork(),
          this.zoning,
          this.transferSettings,
          this.zoningReaderData,
          this.network2ZoningData,
          Stage.IDENTIFY_PLATFORM_AS_RELATIONS,
          profiler);
      read(osmReader, osmPreProcessingHandler);     
    }
  }

  /**
   * Conduct pre-processing pass to identify the nodes required to perform platform parsing of platforms identified earlier as being coded as relations, see {@link #preProcessPlatformRelations(OsmZoningHandlerProfiler)}
   * @param profiler to use
   */
  private void preProcessPtNodePreregistration(final OsmZoningHandlerProfiler profiler) {
    /* reader to parse the actual file for preprocessing  */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputSource());
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for pre-processing public transport node pre-registration, aborting");
    }else {
      osmPreProcessingHandler = new OsmZoningPreProcessingHandler(
          this.getReferenceNetwork(),
          this.zoning,
          this.transferSettings,
          this.zoningReaderData,
          this.network2ZoningData,
          Stage.IDENTIFY_PT_NODES,
          profiler);
      read(osmReader, osmPreProcessingHandler);
    }
  }  

  /**
   * Conduct pre-processing step of zoning reader that cannot be conducted as part of the regular processing due to 
   * ordering conflicts of parsing OSM entities.
   * 
   * @param profiler  to use
   */
  private void doPreprocessing(final OsmZoningHandlerProfiler profiler){
    
    /* identify all relations that represent a (single) platform either as a single polygon, or multi-polygon 
     * and mark their ways to be kept, which then in the next pass ensures these way's nodes are pre-registered to be kept as well */
    LOGGER.info("Pre-processing: Identifying relations representing public transport platforms");
    preProcessPlatformRelations(profiler);
    if(zoningReaderData.getOsmData().hasOsmRelationOuterRoleOsmWays()) {
      LOGGER.info(String.format("Identified %d OSM ways that are outer roles of osm relations and eligible to be converted to platforms",zoningReaderData.getOsmData().getNumberOfOuterRoleOsmWays()));
    }

    LOGGER.info("Pre-processing: Identifying OSM nodes for public transport");
    preProcessPtNodePreregistration(profiler);

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

    /* prepare for parsing */
    initialiseBeforeParsing();
    
    OsmZoningHandlerProfiler handlerProfiler = new OsmZoningHandlerProfiler();
    logInfo();
                
    /* preprocessing (multi-polygon relation: OSM way identification)*/
    doPreprocessing(handlerProfiler);
    
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

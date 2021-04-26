package org.planit.osm.converter.zoning;

import java.util.logging.Logger;

import org.planit.converter.zoning.ZoningReader;
import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.util.Osm4JUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.StringUtils;
import org.planit.zoning.Zoning;

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
public class PlanitOsmZoningReader implements ZoningReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningReader.class.getCanonicalName());
  
  /** the handler conducting parsing in preparation for the osmHandler*/
  private PlanitOsmZoningPreProcessingHandler osmPreProcessingHandler = null;  
      
  /** the handler conducting the main parsing pass */
  private PlanitOsmZoningHandler osmHandler = null;   
  
  /** the handler for the final parsing as post-processing step of the reader */
  private PlanitOsmZoningPostProcessingHandler osmPostProcessingHandler = null;
  
  /** the settings the user can configure for parsing transfer zones */
  private final PlanitOsmPublicTransportReaderSettings transferSettings;
  
  /** the (temporary) data gathered during parsing of OSM (transfer) zones */
  private PlanitOsmZoningReaderData zoningReaderData;
    
  // references
      
  /** zoning to populate */
  private final Zoning zoning;
  
  /** data from network parsing that is required to successfully complete the zoning parsing */
  PlanitOsmNetworkToZoningReaderData network2ZoningData;
     
  /**
   * Log some information about this reader's configuration 
   */
  private void logInfo() {
    LOGGER.info(String.format("OSM (transfer) zoning input file: %s",getSettings().getInputFile()));    
  }       
  
  /** conduct reading of data with given reader and handler
   * 
   * @param osmReader to use
   * @param osmHandler to use
   * @throws PlanItException thrown if error
   */
  protected void read(OsmReader osmReader, PlanitOsmZoningBaseHandler osmHandler) throws PlanItException {
    try {  
      osmHandler.initialiseBeforeParsing();
      /* register handler */
      osmReader.setHandler(osmHandler);
      /* conduct parsing which will call back the handler*/
      osmReader.read();  
    }catch (OsmInputException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error during parsing of osm file",e);
    }       
  }  
  
  /**
   * conduct pre-processing step of zoning reader that cannot be conducted as part of the regular processing due to 
   * ordering conflicts
   * @param profiler  to use
   * 
   * @throws PlanItException thrown if error
   */
  private void doPreprocessing(PlanitOsmZoningHandlerProfiler profiler) throws PlanItException {
    /* reader to parse the actual file for preprocessing  */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputFile());
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for pre-processing zones, aborting");
    }else {    

      /* preprocessing handler to deal with callbacks from osm4j */
      osmPreProcessingHandler = new PlanitOsmZoningPreProcessingHandler(this.transferSettings, this.zoningReaderData, profiler);
      read(osmReader, osmPreProcessingHandler);     
    }
    
  }  
  
  /**
   * conduct main processing step of zoning reader given the information available from pre-processing
   *  
   * @param profiler  to use
   * @throws PlanItException thrown if error
   */
  private void doMainProcessing(PlanitOsmZoningHandlerProfiler profiler) throws PlanItException {
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputFile());
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for zones, aborting");
    }else {

      /* handler to deal with callbacks from osm4j */
      osmHandler = new PlanitOsmZoningHandler(
          this.transferSettings, 
          this.zoningReaderData, 
          this.network2ZoningData, 
          this.zoning, 
          profiler);
      read(osmReader, osmHandler);
    } 
  }   
  
  /**
   * conduct post-processing processing step of zoning reader given the information available from pre-processing
   *  
   * @param profiler  to use
   * @throws PlanItException thrown if error
   */
  private void doPostProcessing(PlanitOsmZoningHandlerProfiler profiler) throws PlanItException {
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(getSettings().getInputFile());
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for post-processing zones, aborting");
    }else {

      /* handler to deal with callbacks from osm4j */
      osmPostProcessingHandler = new PlanitOsmZoningPostProcessingHandler(
          this.transferSettings, 
          this.zoningReaderData, 
          this.network2ZoningData, 
          this.zoning,
          profiler);
      read(osmReader, osmPostProcessingHandler);        
    } 
  }  
  
  /**
   * remove any dangling zones
   */
  protected void removeDanglingZones() {
    /* delegate to zoning modifier */
    int originalNumberOfTransferZones = zoning.transferZones.size();
    zoning.getZoningModifier().removeDanglingZones();
    LOGGER.info(String.format("Removed dangling transfer zones, remaining number of zones %d (original: %d)", zoning.transferZones.size(), originalNumberOfTransferZones));
  }  
  
  /**
   * remove any dangling transfer zone groups
   */  
  protected void removeDanglingTransferZoneGroups() {
    /* delegate to zoning modifier */
    int originalNumberOfTransferZoneGroups = zoning.transferZoneGroups.size();
    zoning.getZoningModifier().removeDanglingTransferZoneGroups();    
    LOGGER.info(String.format("Removed dangling transfer zone groups, remaining number of groups %d (original: %d)", zoning.transferZoneGroups.size(), originalNumberOfTransferZoneGroups));    
  }  
    

  /**
   * Constructor 
   * 
   * @param settings to use
   * @param zoningToPopulate zoning to populate 
   */
  protected PlanitOsmZoningReader(PlanitOsmPublicTransportReaderSettings settings, Zoning zoningToPopulate){
    this.transferSettings = settings;    
    this.zoning = zoningToPopulate; 
  }
  
  /**
   * Constructor 
   * 
   * @param inputFile to parse from
   * @param countryName this zoning is used for
   * @param zoningToPopulate zoning to populate 
   */
  protected PlanitOsmZoningReader(String inputFile, String countryName, Zoning zoningToPopulate){
    this.transferSettings = new PlanitOsmPublicTransportReaderSettings(inputFile, countryName);
    
    // output
    this.zoning = zoningToPopulate; 
  }  
     
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network
   * given the configuration options that have been set
   * 
   * @param inputFile to parse
   * @return macroscopic network that has been parsed
   * @throws PlanItException thrown if error
   */  
  @Override
  public Zoning read() throws PlanItException {
    PlanItException.throwIf(StringUtils.isNullOrBlank(getSettings().getInputFile()), "Input file not set for OSM zoning reader, unable to proceed");
    PlanItException.throwIfNull(getSettings().getReferenceNetwork(),"Reference network not available when parsing OSM zoning, unable to proceed");
    
    /* make country name available in zoning reader data during parsing */
    this.zoningReaderData = new PlanitOsmZoningReaderData(getSettings().getCountryName());
    /* spatially index all links to register on data trackers for use in handlers */
    zoningReaderData.getPlanitData().initialiseSpatiallyIndexedLinks(getSettings().getReferenceNetwork());
    
    PlanitOsmZoningHandlerProfiler handlerProfiler = new PlanitOsmZoningHandlerProfiler();
    logInfo();
                
    /* preprocessing (multi-polygon relation: osm way identification)*/
    doPreprocessing(handlerProfiler);
    
    /* main processing  (all but stop_positions)*/
    doMainProcessing(handlerProfiler);
    
    /* post-processing (stop_positions to connectoid) */
    doPostProcessing(handlerProfiler);
    
    /* log stats */
    handlerProfiler.logProcessingStats(zoningReaderData, zoning);
    
    /* remove any dangling zones, e g., transfer zones without connectoids etc. */
    if(getSettings().isRemoveDanglingZones()) {
      removeDanglingZones();
    }
    
    /* remove any dangling zones, e g., transfer zones without connectoids etc. */
    if(getSettings().isRemoveDanglingTransferZoneGroups()) {    
      removeDanglingTransferZoneGroups();
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
    getSettings().reset();
  }  

  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public PlanitOsmPublicTransportReaderSettings getSettings() {
    return transferSettings;
  }



}

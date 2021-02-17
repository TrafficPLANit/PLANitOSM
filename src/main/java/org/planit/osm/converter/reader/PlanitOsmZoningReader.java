package org.planit.osm.converter.reader;

import java.util.logging.Logger;

import org.planit.converter.zoning.ZoningReader;
import org.planit.osm.handler.PlanitOsmZoningBaseHandler;
import org.planit.osm.handler.PlanitOsmZoningHandler;
import org.planit.osm.handler.PlanitOsmZoningHandlerProfiler;
import org.planit.osm.handler.PlanitOsmZoningPostProcessingHandler;
import org.planit.osm.handler.PlanitOsmZoningPreProcessingHandler;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
import org.planit.osm.util.Osm4JUtils;
import org.planit.utils.exceptions.PlanItException;
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
  private final PlanitOsmTransferSettings transferSettings;
  
  /** the (temporary) data gathered during parsing of OSM (transfer) zones */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** the profiler shared across the handlers */
  private final PlanitOsmZoningHandlerProfiler handlerProfiler;
  
  // references
  
  /** input file to use */
  private final String inputFile;
    
  /** zoning to populate */
  private final Zoning zoning;
  
  /** data from network parsing that is required to successfully complete the zoning parsing */
  PlanitOsmNetworkToZoningReaderData network2ZoningData;
     
  /**
   * Log some information about this reader's configuration
   * @param inputFile 
   */
  private void logInfo(String inputFile) {
    LOGGER.info(String.format("OSM (transfer) zoning input file: %s",inputFile));    
  }  
  
  /** should be called after the network has been parsed but before we call the read() method on this instance to 
   * provide this instance with the necessary data/references required to relate properly to the parsed network elements
   * 
   * @param osmNetworkReader to extract references from
   */
  protected void setNetworkToZoningReaderData(PlanitOsmNetworkToZoningReaderData network2zoningReaderData) {
    this.network2ZoningData = network2zoningReaderData;
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
   * 
   * @throws PlanItException thrown if error
   */
  private void doPreprocessing() throws PlanItException {
    /* reader to parse the actual file for preprocessing  */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for pre-processing zones, aborting");
    }else {    

      /* preprocessing handler to deal with callbacks from osm4j */
      osmPreProcessingHandler = new PlanitOsmZoningPreProcessingHandler(this.transferSettings, this.zoningReaderData);
      read(osmReader, osmPreProcessingHandler);     
    }
    
  }  
  
  /**
   * conduct main processing step of zoning reader given the information available from pre-processing
   *  
   * @throws PlanItException thrown if error
   */
  private void doMainProcessing() throws PlanItException {
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for zones, aborting");
    }else {

      /* handler to deal with callbacks from osm4j */
      osmHandler = new PlanitOsmZoningHandler(
          this.transferSettings, 
          this.zoningReaderData, 
          this.network2ZoningData, 
          this.zoning, 
          this.handlerProfiler);
      read(osmReader, osmHandler);
    } 
  }   
  
  /**
   * conduct post-processing processing step of zoning reader given the information available from pre-processing
   *  
   * @throws PlanItException thrown if error
   */
  private void doPostProcessing() throws PlanItException {
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for post-processing zones, aborting");
    }else {

      /* handler to deal with callbacks from osm4j */
      osmPostProcessingHandler = new PlanitOsmZoningPostProcessingHandler(
          this.transferSettings, 
          this.zoningReaderData, 
          this.network2ZoningData, 
          this.zoning,
          this.handlerProfiler);
      read(osmReader, osmPostProcessingHandler);        
    } 
  }    

  /**
   * Constructor 
   * 
   * @param inputFile to parse from
   * @param zoningToPopulate zoning to populate 
   */
  protected PlanitOsmZoningReader(String inputFile, Zoning zoningToPopulate){
    this.transferSettings = new PlanitOsmTransferSettings();
    this.zoningReaderData = new PlanitOsmZoningReaderData();
    this.handlerProfiler = new PlanitOsmZoningHandlerProfiler();

    // references
    this.inputFile = inputFile;
    
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
    
    logInfo(inputFile);
            
    /* preprocessing (multi-polygon relation: osm way identification)*/
    doPreprocessing();
    
    /* main processing  (all but stop_positions)*/
    doMainProcessing();
    
    /* post-processing (stop_positions to connectoid) */
    doPostProcessing();        
    
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
    this.handlerProfiler.reset();
  }  

  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public PlanitOsmTransferSettings getSettings() {
    return transferSettings;
  }



}

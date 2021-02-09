package org.planit.osm.converter.reader;

import java.util.logging.Logger;

import org.planit.converter.zoning.ZoningReader;
import org.planit.osm.settings.network.PlanitOsmTransferSettings;
import org.planit.osm.util.Osm4JUtils;
import org.planit.osm.zoning.PlanitOsmZoningHandler;
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
      
  /** tha handler conducting the actual parsing */
  private PlanitOsmZoningHandler osmHandler;
  
  /** the settings the user can configure for parsing transfer zones */
  private final PlanitOsmTransferSettings transferSettings;
  
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

  /**
   * Constructor 
   * 
   * @param inputFile to parse from
   * @param zoningToPopulate zoning to populate 
   */
  protected PlanitOsmZoningReader(String inputFile, Zoning zoningToPopulate){
    this.transferSettings = new PlanitOsmTransferSettings();

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
      
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for zones, aborting");
    }else {
    
      /* handler to deal with callbacks from osm4j */
      osmHandler = new PlanitOsmZoningHandler(transferSettings, network2ZoningData, this.zoning);
      osmHandler.initialiseBeforeParsing();
      
      /* register handler */
      osmReader.setHandler(osmHandler);
      
      /* conduct parsing which will call back the handler*/
      try {
        osmReader.read();
      } catch (OsmInputException e) {
        LOGGER.severe(e.getMessage());
        throw new PlanItException("error during parsing of osm file",e);
      }      
    }
    
    LOGGER.info(" OSM full network parsing...DONE");
    
    /* return parsed zoning */
    return this.zoning;    
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    /* free memory */
    osmHandler.reset();
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

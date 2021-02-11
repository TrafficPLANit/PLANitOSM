package org.planit.osm.converter.reader;

import java.util.logging.Logger;

import org.planit.converter.network.NetworkReader;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.osm.handler.PlanitOsmNetworkHandler;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.util.Osm4JUtils;
import org.planit.utils.exceptions.PlanItException;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit network instance
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkReader implements NetworkReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkReader.class.getCanonicalName());
    
  /** input file to use */
  private final String inputFile;
  
  /** settings to use */
  private final PlanitOsmNetworkSettings settings;
  
  /** network to populate */
  private final PlanitOsmNetwork osmNetwork;
  
  /** tha handler responsible for the actual parsing */
  private PlanitOsmNetworkHandler osmHandler;
  
  /** flag indicating if network reader is part of intermodal reader, relevant for not discarding indices when finished
   * parsing */
  private boolean intermodalReaderActive = false;
     
  /**
   * Log some information about this reader's configuration
   * @param inputFile 
   */
  private void logInfo(String inputFile) {
    LOGGER.info(String.format("OSM network input file: %s",inputFile));
    LOGGER.info(String.format("setting Coordinate Reference System: %s",settings.getSourceCRS().getName()));    
  }    
  
  /** provide the handler that performs the actual parsing
   * 
   * @return osm handler
   */
  protected PlanitOsmNetworkHandler getOsmNetworkHandler() {
    return osmHandler;
  }
  
  /** indicate of network reader is part of intermodal reader, if so, it retains some of the indices
   * tracked ruing parsing for later use by other parts of the intermodal reader
   * @param 
   */
  protected void setPartOfIntermodalReader(boolean intermodalReaderActive) {
    this.intermodalReaderActive = intermodalReaderActive;
  }  
  
  /**
   * Constructor 
   * 
   * @param inputFile
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetwork network to populate 
   * @param settings for populating the network
   */
  protected PlanitOsmNetworkReader(String inputFile, String countryName, PlanitOsmNetwork osmNetwork){
    this.inputFile = inputFile;
    this.osmNetwork = osmNetwork; 
    
    this.settings = new PlanitOsmNetworkSettings(countryName, osmNetwork.modes);
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
  public MacroscopicNetwork read() throws PlanItException {
    /* ensure that the network CRS is consistent with the chosen source CRS */
    osmNetwork.transform(settings.getSourceCRS());    
    logInfo(inputFile);
    
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for network, aborting");
    }else {
    
      /* set handler to deal with call backs from osm4j */
      osmHandler = new PlanitOsmNetworkHandler(osmNetwork, settings);
      osmHandler.initialiseBeforeParsing(intermodalReaderActive);
      
      /* register handler */
      osmReader.setHandler(osmHandler);
      
      /* conduct parsing which will call back the handler*/
      try {
        osmReader.read();
      } catch (OsmInputException e) {
        LOGGER.severe(e.getMessage());
        throw new PlanItException("error during parsing of osm file",e);
      }
      
      if(settings.isRemoveDanglingSubnetworks()) {
        // CONTINUE HERE
        osmNetwork.removeDanglingSubnetworks(
            settings.getDiscardDanglingNetworkBelowSize(), settings.getDiscardDanglingNetworkAboveSize(), settings.isAlwaysKeepLargestsubNetwork());
      }
    }
    
    LOGGER.info(" OSM full network parsing...DONE");
    
    /* return result */
    return osmNetwork;
  }  
    
  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public PlanitOsmNetworkSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    /* reset last used handler */
    osmHandler.reset();
  }

  protected Object getOsmNodes() {
    // TODO Auto-generated method stub
    return null;
  }

}

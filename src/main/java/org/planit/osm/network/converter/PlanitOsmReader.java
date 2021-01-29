package org.planit.osm.network.converter;

import java.io.File;
import java.util.logging.Logger;

import org.planit.network.converter.NetworkReader;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.osm.physical.network.macroscopic.PlanitOsmHandler;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.PlanitOsmSettings;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.FileUtils;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;
import de.topobyte.osm4j.pbf.seq.PbfReader;
import de.topobyte.osm4j.xml.dynsax.OsmXmlReader;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit network instance
 * 
 * @author markr
 *
 */
public class PlanitOsmReader implements NetworkReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmReader.class.getCanonicalName());
    
  /** input file to use */
  private final String inputFile;
  
  /** settings to use */
  private final PlanitOsmSettings settings;
  
  /** network to populate */
  private final PlanitOsmNetwork osmNetwork;
     
  /**
   * Log some information about this reader's configuration
   * @param inputFile 
   */
  private void logInfo(String inputFile) {
    LOGGER.info(String.format("input file: %s",inputFile));
    LOGGER.info(String.format("setting Coordinate Reference System: %s",settings.getSourceCRS().getName()));    
  }  
  
  /** depending on the format create either an OSM or PBF reader
   * 
   * @param inputFileName file name to create reader for
   * @return osmReader created, null if not possible
   */
  private OsmReader createOsm4jReader(String inputFileName) {
    final boolean parseMetaData = false; 
    try{
      File inputFile = new File(inputFileName);
      String extension = FileUtils.getExtension(inputFile);
      switch (extension) {
      case OSM_XML_EXTENSION:
        return new OsmXmlReader(inputFile, parseMetaData);
      case OSM_PBF_EXTENSION:
        return new PbfReader(inputFile, parseMetaData);
      default:
        LOGGER.warning(String.format("unsupported OSM file format for file: (%s), skip parsing", inputFileName));
        return null;
      }
    }catch(Exception e) {
      LOGGER.warning(String.format("open street map input file does not exist: (%s) skip parsing", inputFileName));
    }
    return null;
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
  PlanitOsmReader(String inputFile, String countryName, PlanitOsmNetwork osmNetwork){
    this.inputFile = inputFile;
    this.osmNetwork = osmNetwork; 
    this.settings = new PlanitOsmSettings(countryName, osmNetwork.modes);
  }
  
  /** osm XML extension string */
  public static final String OSM_XML_EXTENSION = "osm";
  
  /** osm PBF extension string */
  public static final String OSM_PBF_EXTENSION = "pbf";  
   
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
    OsmReader osmReader = createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader, aborting");
    }else {
    
      /* handler to deal with call backs from osm4j */
      PlanitOsmHandler osmHandler = new PlanitOsmHandler(osmNetwork, settings);
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
      
      if(settings.isRemoveDanglingSubnetworks()) {
        // CONTINUE HERE
        osmNetwork.getDefaultNetworkLayer().removeDanglingSubnetworks(
            settings.getDiscardDanglingNetworkBelowSize(), settings.getDiscardDanglingNetworkAboveSize(), settings.isAlwaysKeepLargestsubNetwork());
      }
    }
    
    /* return result */
    return osmNetwork;
  }  
    
  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the setings
   */
  public PlanitOsmSettings getSettings() {
    return settings;
  }



}

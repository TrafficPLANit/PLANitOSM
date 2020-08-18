package org.planit.osm.reader;

import java.io.File;
import java.util.logging.Logger;

import org.planit.osm.physical.network.macroscopic.PLANitOSMNetwork;
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
public class PLANitOSMReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PLANitOSMReader.class.getCanonicalName());
  
  public static final String OSM_XML_EXTENSION = "osm";
  
  public static final String OSM_PBF_EXTENSION = "pbf";  
  
  /* settings to use */
  private final PLANitOSMReaderSettings settings;
  
  /* network to populate */
  private final PLANitOSMNetwork osmNetwork;
   
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
  private OsmReader createOSMReader(String inputFileName) {
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
   * @param osmNetwork network to populate 
   * @param settings for populating the network
   */
  PLANitOSMReader(PLANitOSMNetwork osmNetwork){
    this.osmNetwork = osmNetwork; 
    this.settings = new PLANitOSMReaderSettings();
  }
  


  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network
   * given the configuration options that have been set
   * 
   * @param inputFile to parse
   * @return macroscopic network that has been parsed
   * @throws PlanItException thrown if error
   */
  public PLANitOSMNetwork parse(String inputFile) throws PlanItException {
    logInfo(inputFile);
    
    /* handler to deal with call backs from osm4j */
    PLANitOSMHandler osmHandler = new PLANitOSMHandler(osmNetwork, settings);
    
    /* reader to parse the actual file */
    OsmReader osmReader = createOSMReader(inputFile);
    
    /* register handler */
    osmReader.setHandler(osmHandler);
    
    /* conduct parsing which will call back the handler*/
    try {
      osmReader.read();
    } catch (OsmInputException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("error during parsing of osm file",e);
    }
    
    /* return result */
    return osmNetwork;
  }

}

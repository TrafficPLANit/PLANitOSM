package org.planit.osm.util;

import java.io.File;
import java.util.logging.Logger;

import org.planit.utils.misc.FileUtils;

import de.topobyte.osm4j.core.access.OsmReader;
import de.topobyte.osm4j.pbf.seq.PbfReader;
import de.topobyte.osm4j.xml.dynsax.OsmXmlReader;

/**
 * Utiilities regarding the use of the OSM4J reader that provides us with raw stream of OSM entities to parse
 * 
 * @author markr
 *
 */
public class Osm4JUtils {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(Osm4JUtils.class.getCanonicalName());

  /** osm XML extension string */
  public static final String OSM_XML_EXTENSION = "osm";
  
  /** osm PBF extension string */
  public static final String OSM_PBF_EXTENSION = "pbf";
  
  /** depending on the format create either an OSM or PBF reader
   * 
   * @param inputFileName file name to create reader for
   * @return osmReader created, null if not possible
   */
  public static OsmReader createOsm4jReader(String inputFileName) {
    final boolean parseMetaData = false; 
    try{
      File inputFile = new File(inputFileName);
      String extension = FileUtils.getExtension(inputFile);
      switch (extension) {
      case Osm4JUtils.OSM_XML_EXTENSION:
        return new OsmXmlReader(inputFile, parseMetaData);
      case Osm4JUtils.OSM_PBF_EXTENSION:
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
}

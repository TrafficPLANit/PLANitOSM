package org.goplanit.osm.util;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.logging.Logger;

import org.goplanit.utils.misc.FileUtils;
import org.goplanit.utils.misc.UrlUtils;

import de.topobyte.osm4j.core.access.OsmReader;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
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
  
  /** Depending on the format create either an OSM or PBF reader based on local file specified by path
   * 
   * @param inputFile data source to create reader for
   * @return osmReader created, null if not possible
   */
  public static OsmReader createOsm4jReader(final File inputFile) {
    
    final boolean parseMetaData = false;
    try {
      String extension = FileUtils.getExtension(inputFile);
      switch (extension) {
      case Osm4JUtils.OSM_XML_EXTENSION:
        return new OsmXmlReader(inputFile, parseMetaData);
      case Osm4JUtils.OSM_PBF_EXTENSION:
        return new PbfReader(inputFile, parseMetaData);
      default:
        LOGGER.warning(String.format("Unsupported OSM file format for file: (%s), skip parsing", inputFile));
        return null;
      }
    }catch(Exception e) {
      LOGGER.warning(String.format("open street map input file does not exist: (%s) skip parsing", inputFile));
    }
    return null;    
  }
  
  /** Depending on the format create either an OSM or PBF reader
   * 
   * @param inputSource data source to create reader for
   * @return osmReader created, null if not possible
   */
  public static OsmReader createOsm4jReader(URL inputSource) {           
    try{
      
      /* special treatment when local file */
      if(UrlUtils.isLocal(inputSource)) {
        return createOsm4jReader(Paths.get(inputSource.toURI()).toFile());
      }else {
        // Create a reader for (remote) XML data
        return new OsmXmlReader(inputSource.openStream(), false);
      }
    }catch(Exception e) {
      LOGGER.warning(String.format("Open street map input source could not be accessed: (%s) skip parsing", inputSource.toString()));
    }
    return null;
  } 
  
  /** Create a comparator for osm entities absed on their id. Can only be used  within each entittypes as across
   * entity types the ids are NOT unique
   * 
   * @return entity type based comaprator
   */
  public static Comparator<? super OsmEntity> createOsmEntityComparator(){
    return new Comparator<OsmEntity>() {
      public int compare(OsmEntity e1,OsmEntity e2)
      {
          return Long.valueOf(e1.getId()).compareTo(Long.valueOf(e2.getId()));
      }
    };
  }
  
  /** Collect entity type based on the entity
   * 
   * @param entity to check
   * @return type extracted
   */
  public static EntityType getEntityType(OsmEntity entity) {
    if(entity instanceof OsmNode) {
      return EntityType.Node;
    }else if( entity instanceof OsmWay){
      return EntityType.Way;
    }else {
      LOGGER.severe(String.format("Unknown OSM entity %d encountered when registering transfer zone, transfer zone not registered",entity.getId()));
      return null;
    }
  }
  
}

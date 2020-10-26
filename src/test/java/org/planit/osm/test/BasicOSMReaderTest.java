package org.planit.osm.test;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.planit.logging.Logging;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.converter.PlanitOsmReader;
import org.planit.osm.converter.PlanitOsmReaderFactory;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRailWayTags;

/**
 * basic *.osm and *.osm.pbf reader test
 * 
 * @author markr
 *
 */
public class BasicOSMReaderTest {
  
  private static Logger LOGGER;
  
  private static final String RESOURCE_DIR = "./src/test/resources/";
  
  private static final String SYDNEYCBD_OSM = RESOURCE_DIR.concat("osm/sydney-cbd/sydneycbd.osm");
  
  private static final String SYDNEYCBD_PBF = RESOURCE_DIR.concat("osm/sydney-cbd/sydneycbd.osm.pbf");  
  
  @BeforeClass
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(BasicOSMReaderTest.class);
    } 
  }

  @AfterClass
  public static void tearDown() {
    Logging.closeLogger(LOGGER); 
  }  

  /**
   * test *.osm format parsing on small network
   */
  @Test
  public void osmReadertest() {
    try {
      PlanitOsmReader osmReader = PlanitOsmReaderFactory.createReader(SYDNEYCBD_OSM);
      
      /* test out excluding a particular type highway:road from parsing */
      osmReader.getSettings().deactivateOsmWayType(OsmHighwayTags.HIGHWAY, OsmHighwayTags.ROAD);
      
      /* test out setting different defaults for the highway:primary type*/
      osmReader.getSettings().overwriteOsmHighwayTypeDefaults(OsmHighwayTags.PRIMARY, 2200.0, 180.0);
      
      /* add railway mode tram to secondary_link type, since it is allowed on this type of link */
      osmReader.getSettings().getModeAccessConfiguration().addAllowedHighwayModes(OsmHighwayTags.SECONDARY, OsmRailWayTags.TRAM);
            
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);
      
      //TODO: find a way to test the settings had the intended effect
      
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());      
      e.printStackTrace();
      fail();      
    }
  }
  
  /**
   * test *.osm.pbf format parsing on small network
   */
  @Test
  public void pbfReadertest() {
    try {
      PlanitOsmReader osmReader = PlanitOsmReaderFactory.createReader(SYDNEYCBD_PBF);
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail();
    }
  }  

}

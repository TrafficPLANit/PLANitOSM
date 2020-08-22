package org.planit.osm.test;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.planit.logging.Logging;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.reader.PLANitOSMReader;
import org.planit.osm.reader.PLANitOSMReaderFactory;

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
      PLANitOSMReader osmReader = PLANitOSMReaderFactory.create();
      MacroscopicNetwork network = osmReader.parse(SYDNEYCBD_OSM);
      assertNotNull(network);
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
      PLANitOSMReader osmReader = PLANitOSMReaderFactory.create();
      MacroscopicNetwork network = osmReader.parse(SYDNEYCBD_PBF);
      assertNotNull(network);
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail();
    }
  }  

}

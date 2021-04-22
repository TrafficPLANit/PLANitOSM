package org.planit.osm.test;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.planit.logging.Logging;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.osm.converter.reader.PlanitOsmIntermodalReader;
import org.planit.osm.converter.reader.PlanitOsmIntermodalReaderFactory;
import org.planit.osm.converter.reader.PlanitOsmNetworkReader;
import org.planit.osm.converter.reader.PlanitOsmNetworkReaderFactory;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.utils.locale.CountryNames;
import org.planit.utils.misc.Pair;
import org.planit.zoning.Zoning;

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
   * test *.osm format parsing on small network just collecting road infrastructure
   */
  @Test
  public void osmReaderRoadInfrastructureTest() {
    try {
      PlanitOsmNetworkReader osmReader = PlanitOsmNetworkReaderFactory.create(SYDNEYCBD_OSM, CountryNames.AUSTRALIA);
      
      /* test out excluding a particular type highway:road from parsing */
      osmReader.getSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.ROAD);
      
      /* test out setting different defaults for the highway:primary type*/
      osmReader.getSettings().getHighwaySettings().overwriteOsmHighwayTypeDefaultsCapacityMaxDensity(OsmHighwayTags.PRIMARY, 2200.0, 180.0);
      
      /* add railway mode tram to secondary_link type, since it is allowed on this type of link */
      osmReader.getSettings().getHighwaySettings().addAllowedHighwayModes(OsmHighwayTags.SECONDARY, OsmRailwayTags.TRAM);
            
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
   * test *.osm format parsing on small network collecting both road, rail AND stops, platforms, stations, e.g. inter-modal support
   */
  @Test
  public void osmReaderRoadAndPtTest() {
    try {
      PlanitOsmIntermodalReader osmReader = PlanitOsmIntermodalReaderFactory.create(SYDNEYCBD_OSM, CountryNames.AUSTRALIA);
      
      /* test out excluding a particular type highway:road from parsing */
      osmReader.getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
      osmReader.getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.FOOTWAY);
      osmReader.getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.PEDESTRIAN);
      
      /* activate railways */
      osmReader.getNetworkSettings().activateRailwayParser(true);
                  
      Pair<InfrastructureNetwork<?,?>, Zoning> resultPair = osmReader.read();
      MacroscopicNetwork network = (MacroscopicNetwork) resultPair.first();
      Zoning zoning = resultPair.second();
      
      
      assertNotNull(network);
      assertNotNull(zoning);
      
      assertFalse(network.infrastructureLayers.isNoLayers());
      assertFalse(network.infrastructureLayers.getFirst().isEmpty());
      assertTrue(zoning.odZones.isEmpty());
      assertFalse(zoning.transferZones.isEmpty());
      
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
      PlanitOsmNetworkReader osmReader = PlanitOsmNetworkReaderFactory.create(SYDNEYCBD_PBF, CountryNames.AUSTRALIA);
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail();
    }
  }  

}

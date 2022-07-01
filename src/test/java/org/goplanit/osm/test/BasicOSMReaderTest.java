package org.goplanit.osm.test;

import static org.junit.Assert.*;

import java.net.URL;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReader;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderFactory;
import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.zoning.Zoning;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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
  
  private static final String EXAMPLE_REMOTE_URL = "https://api.openstreetmap.org/api/0.6/map?bbox=13.465661,52.504055,13.469817,52.506204";

  /** configure for parsing road and pt infrastructure networks
   *
   * @param osmReader to configure
   */
  private void configureForRoadAndPt(final OsmIntermodalReader osmReader){
    /* test out excluding a particular type highway:road from parsing */
    osmReader.getSettings().getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    osmReader.getSettings().getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    osmReader.getSettings().getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.PEDESTRIAN);

    /* activate railways */
    osmReader.getSettings().getNetworkSettings().activateRailwayParser(true);
  }
  
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
      OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(SYDNEYCBD_OSM, CountryNames.AUSTRALIA);
      
      /* test out excluding a particular type highway:road from parsing */
      osmReader.getSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.ROAD);
      
      /* test out setting different defaults for the highway:primary type*/
      osmReader.getSettings().getHighwaySettings().overwriteCapacityMaxDensityDefaults(OsmHighwayTags.PRIMARY, 2200.0, 180.0);
      
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
      OsmIntermodalReader osmReader = OsmIntermodalReaderFactory.create(SYDNEYCBD_OSM, CountryNames.AUSTRALIA);
      configureForRoadAndPt(osmReader);

      Pair<MacroscopicNetwork, Zoning> resultPair = osmReader.read();
      MacroscopicNetwork network = resultPair.first();
      Zoning zoning = resultPair.second();
            
      assertNotNull(network);
      assertNotNull(zoning);
      
      assertFalse(network.getTransportLayers().isNoLayers());
      assertFalse(network.getTransportLayers().getFirst().isEmpty());
      assertTrue(zoning.getOdZones().isEmpty());
      assertFalse(zoning.getTransferZones().isEmpty());
      
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
      OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(SYDNEYCBD_PBF, CountryNames.AUSTRALIA);
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail();
    }
  }

  /**
   * test *.pbf format parsing on small network collecting both road, rail AND stops, platforms, stations, e.g. inter-modal support as well
   * as the GTFS infusion. Predicated on the assumption that {@link #osmReaderRoadAndPtTest()} succeeds
   */
  @Test
  public void osmReaderRoadAndPtAndGtfsTest() {
    try {
      OsmIntermodalReader osmReader = OsmIntermodalReaderFactory.create(SYDNEYCBD_PBF, CountryNames.AUSTRALIA);
      configureForRoadAndPt(osmReader);

      /* GTFS configuration */
      //TODO

      Pair<MacroscopicNetwork, Zoning> resultPair = osmReader.read();
      MacroscopicNetwork network = resultPair.first();
      Zoning zoning = resultPair.second();


    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      e.printStackTrace();
      fail();
    }
  }
  
  /**
   * test if we can parse from cloud based URL instead of local fule
   */
  @Test
  public void osmReaderUrlStreamTest() {
    try {
      
      URL exampleUrl = new URL(EXAMPLE_REMOTE_URL);
      OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(exampleUrl, CountryNames.GERMANY);
      osmReader.getSettings().setInputSource(EXAMPLE_REMOTE_URL);
      
      osmReader.getSettings().getHighwaySettings().deactivateAllOsmHighwayTypesExcept(OsmHighwayTags.FOOTWAY);
      osmReader.getSettings().getHighwaySettings().deactivateAllRoadModesExcept(OsmRoadModeTags.FOOT);
                        
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);
      
      //TODO: find a way to test the settings had the intended effect
      
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());      
      e.printStackTrace();
      fail();      
    }    
  }

}

package org.goplanit.osm.test;

import org.goplanit.converter.intermodal.IntermodalConverterFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalReaderFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalWriterFactory;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReader;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderFactory;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.zoning.Zoning;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * basic *.osm and *.osm.pbf reader test
 * 
 * @author markr
 *
 */
public class BasicOSMReaderTest {
  
  private static Logger LOGGER;
  
  private static final String RESOURCE_DIR = Path.of(".","src","test","resources").toString();

  private static final String SYDNEYCBD_2023_OSM = Path.of(RESOURCE_DIR,"osm","sydney-cbd","sydneycbd_2023.osm").toString();
  
  private static final String SYDNEYCBD_2023_PBF = Path.of(RESOURCE_DIR,"osm","sydney-cbd","sydneycbd_2023.osm.pbf").toString();
  
  private static final String EXAMPLE_REMOTE_URL = "https://api.openstreetmap.org/api/0.6/map?bbox=13.465661,52.504055,13.469817,52.506204";

  /** configure for parsing road and pt infrastructure networks (activate rail and disable walk and cycle infrastructure)
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
      OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(SYDNEYCBD_2023_OSM, CountryNames.AUSTRALIA);
      
      /* test out excluding a particular type highway:road from parsing */
      osmReader.getSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.ROAD);
      
      /* test out setting different defaults for the highway:primary type*/
      osmReader.getSettings().getHighwaySettings().overwriteCapacityMaxDensityDefaults(OsmHighwayTags.PRIMARY, 2200.0, 180.0);
      
      /* add railway mode tram to secondary_link type, since it is allowed on this type of link */
      osmReader.getSettings().getHighwaySettings().addAllowedHighwayModes(OsmHighwayTags.SECONDARY, OsmRailwayTags.TRAM);
            
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);

      // when input source is updated this will fail, mainl meant to serve as check to flag a change when any changes are made to how OSM data is parsed and make sure the changes
      // are deemed correct
      assertEquals(network.getTransportLayers().size(), 1);
      assertEquals(network.getTransportLayers().getFirst().getLinks().size(), 1075);
      assertEquals(network.getTransportLayers().getFirst().getLinkSegments().size(), 2119);
      assertEquals(network.getTransportLayers().getFirst().getNodes().size(), 882);
      
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
      OsmIntermodalReader osmReader = OsmIntermodalReaderFactory.create(SYDNEYCBD_2023_OSM, CountryNames.AUSTRALIA);
      configureForRoadAndPt(osmReader);

      Pair<MacroscopicNetwork, Zoning> resultPair = osmReader.read();
      MacroscopicNetwork network = resultPair.first();
      Zoning zoning = resultPair.second();
            
      assertNotNull(network);
      assertNotNull(zoning);
      
      assertFalse(network.getTransportLayers().isEmpty());
      assertFalse(network.getTransportLayers().getFirst().isEmpty());
      assertTrue(zoning.getOdZones().isEmpty());
      assertFalse(zoning.getTransferZones().isEmpty());

      // when input source is updated this will fail, mainly meant to serve as check to flag a change when any changes are made to how OSM data is parsed and make sure the changes
      // are deemed correct
      assertEquals(network.getTransportLayers().size(), 1);
      assertEquals(network.getTransportLayers().getFirst().getLinks().size(),1178);
      assertEquals(network.getTransportLayers().getFirst().getLinkSegments().size(), 2325);
      assertEquals(network.getTransportLayers().getFirst().getNodes().size(), 978);

      assertEquals(0, zoning.getOdZones().size() );
      assertEquals(71, zoning.getTransferZones().size() );
      assertEquals(7, zoning.getTransferZoneGroups().size());
      assertEquals(0, zoning.getOdConnectoids().size());
      assertEquals(94, zoning.getTransferConnectoids().size());

      assertEquals(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.BUS), true);
      assertEquals(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.TRAIN), true);
      assertEquals(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.TRAM),true);
      assertEquals(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.LIGHTRAIL),  true);
      
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
      OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(SYDNEYCBD_2023_PBF, CountryNames.AUSTRALIA);
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);
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

    }catch(Exception e) {
      LOGGER.severe(e.getMessage());      
      e.printStackTrace();
      fail();      
    }    
  }
  
}

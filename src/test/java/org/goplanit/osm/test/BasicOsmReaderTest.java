package org.goplanit.osm.test;

import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReader;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderFactory;
import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.graph.Vertex;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.zoning.Zoning;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * basic *.osm and *.osm.pbf reader test
 * 
 * @author markr
 *
 */
public class BasicOsmReaderTest {
  
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

    var highwaySettings = osmReader.getSettings().getNetworkSettings().getHighwaySettings();

    /* test out excluding a particular type highway:road from parsing */
    highwaySettings.deactivateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    highwaySettings.deactivateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    highwaySettings.deactivateOsmHighwayType(OsmHighwayTags.PEDESTRIAN);

    /* activate railways */
    osmReader.getSettings().getNetworkSettings().activateRailwayParser(true);
  }
  
  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(BasicOsmReaderTest.class);
    } 
  }

  @AfterAll
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
      osmReader.getSettings().setRetainOsmTags(true);
      var highwaySettings = osmReader.getSettings().getHighwaySettings();

      /* test out excluding a particular type highway:road from parsing */
      highwaySettings.deactivateOsmHighwayType(OsmHighwayTags.ROAD);
      
      /* test out setting different defaults for the highway:primary type*/
      highwaySettings.overwriteCapacityMaxDensityDefaults(OsmHighwayTags.PRIMARY, 2200.0, 180.0);
      
      /* add railway mode tram to secondary_link type, since it is allowed on this type of link */
      highwaySettings.addAllowedOsmHighwayModes(OsmHighwayTags.SECONDARY, OsmRailwayTags.TRAM);

      OsmNetworkSettingsTestCaseUtils.sydney2023MinimiseVerifiedWarnings(osmReader.getSettings());

      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);

      // when input source is updated this will fail, mainly meant to serve as check to flag a change when any changes are made to how OSM data is parsed and make sure the changes
      // are deemed correct
      assertEquals(network.getTransportLayers().size(), 1);
      assertEquals(network.getTransportLayers().getFirst().getLinks().size(), 1075);
      assertEquals(network.getTransportLayers().getFirst().getLinkSegments().size(), 2123);
      assertEquals(network.getTransportLayers().getFirst().getNodes().size(), 882);

      assert network.getTransportLayers().getFirst().getLinks().stream().allMatch(Edge::hasInputProperty) : "OSM tags not retained on all links";

      //todo flesh out this assert as not all nodes will have tags...
      //assert network.getTransportLayers().getFirst().getNodes().stream().allMatch(Vertex::hasInputProperty) : "OSM tags not retained on all nodes";

    }catch(Exception e) {
      LOGGER.severe(e.getMessage());      
      e.printStackTrace();
      fail("osmReaderRoadInfrastructureTest");
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
      osmReader.getSettings().getNetworkSettings().setConsolidateLinkSegmentTypes(true);

      OsmNetworkSettingsTestCaseUtils.sydney2023MinimiseVerifiedWarnings(osmReader.getSettings().getNetworkSettings());
      OsmPtSettingsTestCaseUtils.sydney2023MinimiseVerifiedWarnings(osmReader.getSettings().getPublicTransportSettings());

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
      assertEquals(1, network.getTransportLayers().size());

      assertEquals(1234, network.getTransportLayers().getFirst().getLinks().size());
      assertEquals(2441, network.getTransportLayers().getFirst().getLinkSegments().size());
      assertEquals(1030, network.getTransportLayers().getFirst().getNodes().size());

      assertEquals(0, zoning.getOdZones().size() );
      assertEquals(104, zoning.getTransferZones().size() );
      assertEquals(8, zoning.getTransferZoneGroups().size());
      assertEquals(0, zoning.getOdConnectoids().size());
      assertEquals(131, zoning.getTransferConnectoids().size());

      assertTrue(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.BUS));
      assertTrue(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.TRAIN));
      assertTrue(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.TRAM));
      assertTrue(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.LIGHTRAIL));
      assertTrue(network.getTransportLayers().getFirst().supportsPredefinedMode(PredefinedModeType.FERRY));
      
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
      OsmNetworkSettingsTestCaseUtils.sydney2023MinimiseVerifiedWarnings(osmReader.getSettings());
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
      osmReader.getSettings().getHighwaySettings().deactivateAllOsmRoadModesExcept(OsmRoadModeTags.FOOT);
                        
      MacroscopicNetwork network = osmReader.read();
      assertNotNull(network);

    }catch(Exception e) {
      LOGGER.severe(e.getMessage());      
      e.printStackTrace();
      fail();      
    }    
  }
  
}

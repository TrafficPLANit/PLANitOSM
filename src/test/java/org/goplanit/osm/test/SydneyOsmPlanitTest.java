package org.goplanit.osm.test;

import org.goplanit.converter.network.NetworkConverter;
import org.goplanit.converter.network.NetworkConverterFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalWriterSettings;
import org.goplanit.io.converter.network.PlanitNetworkWriter;
import org.goplanit.io.converter.network.PlanitNetworkWriterFactory;
import org.goplanit.io.test.PlanitAssertionUtils;
import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting networks from one format to another
 * 
 * @author markr
 *
 */
public class SydneyOsmPlanitTest {

  public static final Path RESOURCE_PATH = Path.of("src","test","resources");

  public static final Path SYDNEYCBD_2022_OSM = Path.of(RESOURCE_PATH.toString(),"osm","sydney-cbd","sydneycbd.osm");

  @SuppressWarnings("unused")
  public static final Path SYDNEYCBD_2023_PBF = Path.of(RESOURCE_PATH.toString(),"osm","sydney-cbd","sydneycbd_2023.osm.pbf");

  /** the logger */
  private static Logger LOGGER = null;

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(SydneyOsmPlanitTest.class);
    } 
  }

  /**
   * run garbage collection after each test as it apparently is not triggered properly within
   * Eclipse (or takes too long before being triggered)
   */
  @AfterEach
  public void afterTest() {
    IdGenerator.reset();
    System.gc();
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
  }

  /**
   * Test case which parses an OSM file, loads it into PLANit memory model and persists only the ferry network and stops as
   * a PLANit network/zoning result
   */
  @Test
  public void testOsm2PlanitFerryNetwork() {

    final Path PLANIT_OUTPUT_DIR =  Path.of(RESOURCE_PATH.toString(),"testcases","planit","sydney","osm_network_ferry");
    final Path PLANIT_REF_DIR =  Path.of(RESOURCE_PATH.toString(),"planit","sydney","osm_network_ferry");

    try {

      OsmIntermodalReaderSettings readerSettings =
          new OsmIntermodalReaderSettings(SYDNEYCBD_2023_PBF.toAbsolutePath().toString(), CountryNames.AUSTRALIA);

      readerSettings.getNetworkSettings().activateHighwayParser(false);
      readerSettings.getNetworkSettings().activateRailwayParser(false);
      readerSettings.getNetworkSettings().activateWaterwayParser(true);
      readerSettings.getNetworkSettings().setRemoveDanglingSubnetworks(false);

      OsmPtSettingsTestCaseUtils.sydney2023MinimiseVerifiedWarnings(readerSettings.getPublicTransportSettings());


      PlanitIntermodalWriterSettings writerSettings =
          new PlanitIntermodalWriterSettings( PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      Osm2PlanitConversionTemplates.osm2PlanitIntermodalNoServices(readerSettings, writerSettings);


      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), PLANIT_REF_DIR.toAbsolutePath().toString());
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), PLANIT_REF_DIR.toAbsolutePath().toString());

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail("testOsm2PlanitFerryNetwork");
    }
  }
    
  /**
   * Test case which parses an OSM network file, loads it into PLANit memory model and persists it as a PLANit network
   */
  @Test
  public void testOsm2PlanitNetworkComprehensive() {
    
    final Path PLANIT_OUTPUT_DIR =  Path.of(RESOURCE_PATH.toString(),"testcases","planit","sydney","osm_network_comprehensive");
    final Path PLANIT_REF_DIR =  Path.of(RESOURCE_PATH.toString(),"planit","sydney","osm_network_comprehensive");

    try {
      /* OSM reader */
      OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(
              SYDNEYCBD_2023_PBF.toAbsolutePath().toString(),  CountryNames.AUSTRALIA);

      /* reader configuration */
      osmReader.getSettings().activateRailwayParser(true);
      osmReader.getSettings().getHighwaySettings().activateAllOsmHighwayTypes();

      /* PLANit writer */
      PlanitNetworkWriter planitWriter = PlanitNetworkWriterFactory.create(
              PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.AUSTRALIA);

      /* convert */
      NetworkConverter theConverter = NetworkConverterFactory.create(osmReader, planitWriter);
      theConverter.convert();

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), PLANIT_REF_DIR.toAbsolutePath().toString());

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail("testOsm2PlanitNetworkComprehensive");
    }    
  }

  /**
   * Test case which parses an OSM network file, loads it into PLANit memory model and persists it as a PLANit network and zoning (containing stops but no services).
   * We do so for rail, bus, and ferry.
   */
  @Test
  public void testOsm2PlanitIntermodalNoServices() {

    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","sydney","osm_intermodal_no_services");
    final Path PLANIT_REF_DIR =  Path.of(RESOURCE_PATH.toString(),"planit","sydney","osm_intermodal_no_services");
    try {

      var readerSettings =
          new OsmIntermodalReaderSettings(SYDNEYCBD_2023_PBF.toAbsolutePath().toString(), CountryNames.AUSTRALIA);

      /* activate rail and water pt infrastructure parsing */
      readerSettings.getNetworkSettings().getRailwaySettings().activateParser(true);
      readerSettings.getNetworkSettings().getWaterwaySettings().activateParser(true);

      /* reduce warnings based on verified situations that are identified as ok to ignore */
      OsmPtSettingsTestCaseUtils.sydney2023MinimiseVerifiedWarnings(readerSettings.getPublicTransportSettings());

      var writerSettings =
          new PlanitIntermodalWriterSettings( PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.AUSTRALIA);


      /* execute */
      Osm2PlanitConversionTemplates.osm2PlanitIntermodalNoServices(readerSettings, writerSettings);

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), PLANIT_REF_DIR.toAbsolutePath().toString());
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), PLANIT_REF_DIR.toAbsolutePath().toString());

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail("testOsm2PlanitIntermodalNoServices");
    }
  }

}
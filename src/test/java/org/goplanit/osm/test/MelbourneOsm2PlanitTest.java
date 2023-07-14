package org.goplanit.osm.test;

import org.goplanit.gtfs.converter.intermodal.GtfsIntermodalReaderSettings;
import org.goplanit.gtfs.enums.RouteTypeChoice;
import org.goplanit.io.converter.intermodal.PlanitIntermodalWriterSettings;
import org.goplanit.io.test.PlanitAssertionUtils;
import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.resource.ResourceUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting networks from one format to another
 * 
 * @author markr
 *
 */
public class MelbourneOsm2PlanitTest {

  public static final Path RESOURCE_PATH = Path.of("src", "test", "resources");

  public static final Path MELBOURNE_PBF = Path.of(RESOURCE_PATH.toString(), "osm", "melbourne", "3_2023_melbourne.osm.pbf");

  /** the logger */
  private static Logger LOGGER = null;

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MelbourneOsm2PlanitTest.class);
    }
    IdGenerator.reset();
  }

  @AfterEach
  public void afterEach() {
    IdGenerator.reset();
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
  }

  /**
   * Test that attempts to extract PLANit network and PT infrastructure from OSM. Limited to smaller bounding box as this tests output
   * is also used as input to other tests that ingest Melbourne PLANit network and export it in a different format. Hence, results
   * are to be small enough to be in repo resources.
   *
   * read from disk and then persist the result in the PLANit data format
   */
  @Test
  public void test1Osm2PlanitIntermodalNoServicesBoundingBox() {

    final String PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","melbourne","osm_intermodal_no_services_bb").toAbsolutePath().toString();
    final String PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit", "melbourne","osm_intermodal_no_services_bb").toAbsolutePath().toString();
    try {

      var inputSettings = new OsmIntermodalReaderSettings(MELBOURNE_PBF.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      var outputSettings = new PlanitIntermodalWriterSettings(PLANIT_OUTPUT_DIR, CountryNames.AUSTRALIA);

      // apply bounding box
      inputSettings.getNetworkSettings().setBoundingBox(144.995842, 144.921341, -37.855068,-37.786996);

      /* minimise warnings Melbourne v2 */
      OsmNetworkSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings.getNetworkSettings());
      OsmPtSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings.getPublicTransportSettings());

      Osm2PlanitConversionTemplates.osm2PlanitIntermodalNoServices(inputSettings, outputSettings);

      //todo: Error for differences is 99% certain due to layers exactly on top of each other. Fix https://github.com/TrafficPLANit/PLANitOSM/issues/40
      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      e.printStackTrace();
      fail("test1Osm2PlanitIntermodalNoServicesBoundingBox");
    }
  }

}
package org.goplanit.osm.test;

import org.goplanit.io.converter.intermodal.PlanitIntermodalWriterSettings;
import org.goplanit.io.converter.network.PlanitNetworkWriterSettings;
import org.goplanit.io.test.PlanitAssertionUtils;
import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.tags.OsmBoundaryTags;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.geo.SimpleShapeFileParser;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;

import java.nio.file.Path;
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

  public static final Path MELBOURNE_PBF = Path.of(
      RESOURCE_PATH.toString(), "osm", "melbourne", "3_2023_melbourne.osm.pbf");

  /** bounding area to apply */
  public static final Envelope MELBOURNE_SIMPLE_BOUNDING_BOX =
      new Envelope(144.995842, 144.921341, -37.855068,-37.786996);

  /** the logger */
  private static Logger LOGGER = null;

  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(MelbourneOsm2PlanitTest.class);
    }
    IdGenerator.reset();
  }

  /**
   * run garbage collection after each test as it apparently is not triggered properly within
   * in some test environments (or takes too long before being triggered)
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
   * Test that attempts to extract PLANit network and PT infrastructure from OSM. Limited to smaller bounding box as
   * this tests output is also used as input to other tests that ingest Melbourne PLANit network and export it
   * in a different format. Hence, results are to be small enough to be in repo resources.
   * read from disk and then persist the result in the PLANit data format
   */
  @Test
  public void osm2PlanitIntermodalNoServicesBoundingBoxTest() {

    final String PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),
        "testcases","planit","melbourne","osm_intermodal_no_services_bb").toAbsolutePath().toString();
    final String PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),
        "planit", "melbourne","osm_intermodal_no_services_bb").toAbsolutePath().toString();
    try {

      var inputSettings = new OsmIntermodalReaderSettings(
          MELBOURNE_PBF.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      var outputSettings = new PlanitIntermodalWriterSettings(
          PLANIT_OUTPUT_DIR, CountryNames.AUSTRALIA);

      inputSettings.getNetworkSettings().setConsolidateLinkSegmentTypes(false);

      // apply simple bounding box
      inputSettings.getNetworkSettings().setBoundingArea(OsmBoundary.of(MELBOURNE_SIMPLE_BOUNDING_BOX));

      // example of explicitly registering (unsupported or deactivated) types and providing defaults,
      // so they can be parsed directly
      inputSettings.getNetworkSettings().registerNewOsmWayType(
              "highway","proposed", 1, 30, 500,
          180, OsmRoadModeTags.MOTOR_CAR);
      inputSettings.getNetworkSettings().registerNewOsmWayType(
              "railway", "disused", 1, 30, 500,
          180, OsmRailModeTags.TRAIN);

      /* minimise warnings Melbourne v2 */
      OsmNetworkSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings.getNetworkSettings());
      OsmPtSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings.getPublicTransportSettings());

      Osm2PlanitConversionTemplates.osm2PlanitIntermodalNoServices(inputSettings, outputSettings);

      //todo: Error for differences is 99% certain due to layers exactly on top of each other.
      // Fix https://github.com/TrafficPLANit/PLANitOSM/issues/40
      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      e.printStackTrace();
      fail("osm2PlanitIntermodalNoServicesBoundingBoxTest");
    }
  }

  /**
   * Test that is identical to {@link this.osm2PlanitIntermodalNoServicesBoundingBoxTest} only now we use a named
   * bounding box for the "Melbourne District" which is a political boundary applied both to the network and zoning
   * (pt infrastructure)
   */
  @Test
  public void osm2PlanitIntermodalNoServicesNamedBoundingBoxTest() {

    final String PLANIT_OUTPUT_DIR = Path.of(
        RESOURCE_PATH.toString(),"testcases","planit","melbourne","osm_intermodal_no_services_named_bb").toAbsolutePath().toString();
    final String PLANIT_REF_DIR = Path.of(
        RESOURCE_PATH.toString(),"planit", "melbourne","osm_intermodal_no_services_named_bb").toAbsolutePath().toString();
    try {

      var inputSettings = new OsmIntermodalReaderSettings(MELBOURNE_PBF.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      var outputSettings = new PlanitIntermodalWriterSettings(PLANIT_OUTPUT_DIR, CountryNames.AUSTRALIA);

      inputSettings.getNetworkSettings().setConsolidateLinkSegmentTypes(false);

      // apply a boundary area based on name (osm relation id: 3898547)
      inputSettings.setBoundingArea(
          OsmBoundary.of("Melbourne District", OsmBoundaryTags.POLITICAL));

      /* minimise warnings Melbourne v2 */
      OsmNetworkSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings.getNetworkSettings());
      OsmPtSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings.getPublicTransportSettings());

      Osm2PlanitConversionTemplates.osm2PlanitIntermodalNoServices(inputSettings, outputSettings);

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      e.printStackTrace();
      fail("test2Osm2PlanitIntermodalNoServicesNamedBoundingBox");
    }
  }

  /**
   * Test that attempts to extract PLANit network from OSM based on external bounding box polygon
   * read from disk and then persist the result in the PLANit data format
   */
  @Test
  public void osm2PlanitNetworkBoundingPolygonTest() {
    final String BOUNDING_POLY_SHAPES =
            Path.of(RESOURCE_PATH.toString(),"melb_shape_boundary","municipal-boundary.shp").toAbsolutePath().toString();
    final String PLANIT_OUTPUT_DIR =
            Path.of(RESOURCE_PATH.toString(),"testcases","planit","melbourne","osm_network_polybb").toAbsolutePath().toString();
    final String PLANIT_REF_DIR =
            Path.of(RESOURCE_PATH.toString(),"planit", "melbourne","osm_network_polybb").toAbsolutePath().toString();
    try {

      var inputSettings =
          new OsmNetworkReaderSettings(MELBOURNE_PBF.toAbsolutePath().toString(), CountryNames.AUSTRALIA);
      var outputSettings = new PlanitNetworkWriterSettings(PLANIT_OUTPUT_DIR, CountryNames.AUSTRALIA);

      inputSettings.setConsolidateLinkSegmentTypes(true);

      // Example filter
      FilterFactory ff = CommonFactoryFinder.getFilterFactory();
      Filter filter = ff.equals(
          ff.property("mccid_gis"),   // attribute name
          ff.literal("1")               // comparison value
      );

      var geometriesByLayer = SimpleShapeFileParser.parseShapeFileAsJtsGeometries(
          BOUNDING_POLY_SHAPES, filter, true);
      var polygon = PlanitJtsUtils.createPolygon(
          ((Geometry) geometriesByLayer.get(
              "municipal-boundary").second().get(0).getDefaultGeometry()).getCoordinates());

      // apply polygon based boundary
      inputSettings.setBoundingArea(OsmBoundary.of(polygon));

      /* minimise warnings Melbourne v2 */
      OsmNetworkSettingsTestCaseUtils.melbourneMinimiseVerifiedWarnings(inputSettings);
      Osm2PlanitConversionTemplates.osm2PlanitSettingsBased(inputSettings, outputSettings);

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (Exception e) {
      e.printStackTrace();
      fail("osm2PlanitNetworkBoundingPolygonTest");
    }
  }

}
package org.goplanit.osm.converter.settings;

import static org.goplanit.osm.converter.settings.OsmSettingsTestUtils.networkSettingsOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.tags.OsmBoundaryTags;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.problem.SettingsProblemKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Envelope;

/**
 * Tests the keys at the root of an OSM settings file, those of {@link OsmReaderYamlKeyedSettings}: the bounding area in
 * each of its three forms, the input source and whether OSM tags are kept, each given once and applied to the settings
 * of every reader, and the values the bounding area refuses.
 * <p>
 * A key at the root applies to the network and the public transport settings alike, so the intermodal reader takes
 * one value for both
 * </p>
 *
 * @author markr
 */
public class OsmReaderYamlKeyedSettingsTest {

  /** A box around Sydney, as a settings file writes it */
  private static final String SYDNEY_BOX = "box: {west: 150.5, east: 151.4, south: -34.2, north: -33.5}";

  /**
   * Assert the text given has one problem, of the kind given
   *
   * @param kind of the problem
   * @param body the lines after the header
   */
  private static void assertOneProblemOf(SettingsProblemKind kind, String body) {
    OsmSettingsTestUtils.assertOneProblemOf(kind, () -> networkSettingsOf(body));
  }

  /** folder for the test files */
  @TempDir
  public Path tempDir;

  /** A box reads back as the bounding area between its four edges */
  @Test
  public void boundingBoxReadsBack() {
    var settings = networkSettingsOf("boundingArea:\n  " + SYDNEY_BOX + "\n");
    assertEquals(OsmBoundary.of(150.5, 151.4, -34.2, -33.5), settings.getBoundingArea());
  }

  /** A polygon written as WKT reads back as that polygon */
  @Test
  public void boundingPolygonReadsBack() {
    var settings = networkSettingsOf("boundingArea:\n" +
        "  polygon: \"POLYGON ((151.1 -33.95, 151.3 -33.95, 151.3 -33.8, 151.1 -33.8, 151.1 -33.95))\"\n");
    assertTrue(settings.getBoundingArea().getBoundingPolygon().equalsTopo(
        PlanitJtsUtils.create2DPolygon(new Envelope(151.1, 151.3, -33.95, -33.8))));
  }

  /** A named OSM boundary reads back with its type and admin level */
  @Test
  public void namedOsmBoundaryReadsBack() {
    var settings = networkSettingsOf(
        "boundingArea:\n  osmBoundary: {name: City of Sydney, type: administrative, adminLevel: 6}\n");
    assertEquals(OsmBoundary.of("City of Sydney", OsmBoundaryTags.ADMINISTRATIVE, "6", null),
        settings.getBoundingArea());
  }

  /**
   * Given once at the root, the bounding area applies to the network and the public transport settings of an
   * intermodal reader alike, each holding its own copy
   */
  @Test
  public void boundingAreaAppliesToNetworkAndPublicTransport() {
    var settings = OsmSettingsTestUtils.intermodalSettingsOf("boundingArea:\n  " + SYDNEY_BOX + "\n");

    var networkArea = settings.getNetworkSettings().getBoundingArea();
    var publicTransportArea = settings.getPublicTransportSettings().getBoundingArea();
    assertEquals(OsmBoundary.of(150.5, 151.4, -34.2, -33.5), networkArea);
    assertEquals(networkArea, publicTransportArea);
    assertNotSame(networkArea, publicTransportArea);
  }

  /** Two forms at once are refused: which area is meant is unclear */
  @Test
  public void twoFormsAreRefused() {
    assertOneProblemOf(SettingsProblemKind.VALUE_REFUSED,
        "boundingArea:\n  " + SYDNEY_BOX + "\n  osmBoundary: {name: City of Sydney}\n");
  }

  /** No form at all is refused */
  @Test
  public void noFormIsRefused() {
    assertOneProblemOf(SettingsProblemKind.VALUE_REFUSED, "boundingArea: {}\n");
  }

  /** A box without one of its edges is refused */
  @Test
  public void boxWithoutAnEdgeIsRefused() {
    assertOneProblemOf(SettingsProblemKind.VALUE_REFUSED,
        "boundingArea:\n  box: {west: 150.5, east: 151.4, south: -34.2}\n");
  }

  /** A polygon that is not WKT is refused */
  @Test
  public void badWktIsRefused() {
    assertOneProblemOf(SettingsProblemKind.VALUE_REFUSED, "boundingArea:\n  polygon: \"POLYGON ((151.1 -33.95\"\n");
  }

  /** A field a form does not have is refused, such as a box with a top, so a misspelt edge is never lost */
  @Test
  public void unknownFieldIsRefused() {
    assertOneProblemOf(SettingsProblemKind.VALUE_REFUSED,
        "boundingArea:\n  box: {west: 150.5, east: 151.4, south: -34.2, north: -33.5, top: 1}\n");
  }

  /** The bounding area is no key of the network section: it sits at the root */
  @Test
  public void boundingAreaInNetworkSectionIsUnknown() {
    assertOneProblemOf(SettingsProblemKind.UNKNOWN_KEY, "network:\n  boundingArea:\n    " + SYDNEY_BOX + "\n");
  }

  /**
   * The input source reads back as the file it names, a relative path read against the folder of the settings file,
   * for the network and the public transport settings alike
   *
   * @throws IOException when the settings file cannot be written
   */
  @Test
  public void inputSourceReadsBackRelativeToTheFile() throws IOException {
    var file = tempDir.resolve("settings/sydney.yaml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "planitOsmConverterSettings: 1\ninputSource: data/sydney.osm.pbf\n");
    var settings =
        OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(List.of(SettingsSource.ofFile(file)));

    var expected = tempDir.resolve("settings/data/sydney.osm.pbf").normalize().toUri().toURL();
    assertEquals(expected, settings.getNetworkSettings().getInputSource());
    assertEquals(expected, settings.getPublicTransportSettings().getInputSource());
  }

  /** Whether OSM tags are kept reads back: kept here, not by default */
  @Test
  public void retainOsmTagsReadsBack() {
    assertFalse(networkSettingsOf("network: {}\n").isRetainOsmTags());
    assertTrue(networkSettingsOf("retainOsmTags: true\n").isRetainOsmTags());
  }
}

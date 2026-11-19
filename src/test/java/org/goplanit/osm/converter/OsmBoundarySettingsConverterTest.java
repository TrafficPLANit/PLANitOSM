package org.goplanit.osm.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.goplanit.osm.tags.OsmBoundaryTags;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link OsmBoundarySettingsConverter} writes a bounding area back as a settings file holds it: read again,
 * it gives the same area.
 * <p>
 * Reading the three forms from a file, and the values refused, are tested through files in
 * {@code OsmReaderYamlKeyedSettingsTest}
 * </p>
 *
 * @author markr
 */
public class OsmBoundarySettingsConverterTest {

  /** the converter tested */
  private final OsmBoundarySettingsConverter converter = new OsmBoundarySettingsConverter();

  /** A named OSM boundary is written by its name, type and admin level, and reads back equal */
  @Test
  public void namedOsmBoundaryWrittenBack() {
    var boundary = OsmBoundary.of("City of Sydney", OsmBoundaryTags.ADMINISTRATIVE, "6", null);
    assertEquals(boundary, converter.fromPlainValue(converter.toPlainValue(boundary)));
  }

  /** A polygon is written as WKT and reads back equal, a box included, as a box is kept as its polygon */
  @Test
  public void polygonWrittenBack() {
    var box = OsmBoundary.of(150.5, 151.4, -34.2, -33.5);
    assertEquals(box, converter.fromPlainValue(converter.toPlainValue(box)));
  }
}

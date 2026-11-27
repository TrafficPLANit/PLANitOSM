package org.goplanit.osm.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.settings.SettingsValueConverter;
import org.goplanit.utils.yaml.SimpleYamlReader;

/**
 * Converts the bounding area of a settings file to an {@link OsmBoundary} and back. A file gives exactly one of three
 * forms:
 * <pre>
 * boundingArea:
 *   box: { west: 150.5, east: 151.4, south: -34.2, north: -33.5 }
 * boundingArea:
 *   polygon: "POLYGON ((151.1 -33.95, 151.3 -33.95, 151.3 -33.8, 151.1 -33.8, 151.1 -33.95))"
 * boundingArea:
 *   osmBoundary: { name: "City of Sydney", type: administrative, adminLevel: 6 }
 * </pre>
 * <p>
 * An {@link OsmBoundary} cannot change once made and comes in these forms, so it cannot be filled key by key as a small
 * section is. The box and the polygon are in degrees of longitude and latitude, as OSM is, and the box has named edges
 * so their order cannot be mixed up. Written back, a named boundary is written by its name, its polygon being found in
 * the OSM data, and a box comes out as its polygon, which is all an {@link OsmBoundary} keeps of it
 * </p>
 *
 * @author markr
 */
public final class OsmBoundarySettingsConverter implements SettingsValueConverter<OsmBoundary> {

  /** form of a box, by its edges */
  private static final String BOX = "box";

  /** form of a polygon, as WKT */
  private static final String POLYGON = "polygon";

  /** form of an OSM boundary, by its name */
  private static final String OSM_BOUNDARY = "osmBoundary";

  /** western edge of a box, in degrees of longitude */
  private static final String WEST = "west";

  /** eastern edge of a box, in degrees of longitude */
  private static final String EAST = "east";

  /** southern edge of a box, in degrees of latitude */
  private static final String SOUTH = "south";

  /** northern edge of a box, in degrees of latitude */
  private static final String NORTH = "north";

  /** name of an OSM boundary */
  private static final String NAME = "name";

  /** type of an OSM boundary, such as administrative */
  private static final String TYPE = "type";

  /** admin level of an administrative OSM boundary */
  private static final String ADMIN_LEVEL = "adminLevel";

  /**
   * The fields of a form, each a single value, checked to hold every field the form needs and none it does not have
   *
   * @param form name of the form
   * @param value of the form
   * @param required fields it needs
   * @param optional fields it can have besides
   * @return the fields by name
   * @throws IllegalArgumentException when it is not keys with values, misses a field it needs, has a field it does not
   *     have, or a field that is not a single value
   */
  private static Map<String, Object> fieldsOf(String form, Object value, Set<String> required, Set<String> optional) {
    var fields = SimpleYamlReader.asMap(value);
    ExceptionUtils.throwIllegalArgumentIf(fields == null, "Bounding area %s is not given by its fields", form);
    for (var field : fields.entrySet()) {
      ExceptionUtils.throwIllegalArgumentIf(!required.contains(field.getKey()) && !optional.contains(field.getKey()),
          "Bounding area %s has no field %s", form, field.getKey());
      ExceptionUtils.throwIllegalArgumentIf(SimpleYamlReader.asText(field.getValue()) == null,
          "Field %s of bounding area %s is not a single value", field.getKey(), form);
    }
    for (var field : required) {
      ExceptionUtils.throwIllegalArgumentIf(!fields.containsKey(field), "Bounding area %s misses its %s", form, field);
    }
    return fields;
  }

  /**
   * The bounding area of a box, by its edges in degrees
   *
   * @param value of the form
   * @return the bounding area
   */
  private static OsmBoundary boxOf(Object value) {
    var edges = fieldsOf(BOX, value, Set.of(WEST, EAST, SOUTH, NORTH), Set.of());
    return OsmBoundary.of(Double.valueOf(SimpleYamlReader.asText(edges.get(WEST))),
        Double.valueOf(SimpleYamlReader.asText(edges.get(EAST))),
        Double.valueOf(SimpleYamlReader.asText(edges.get(SOUTH))),
        Double.valueOf(SimpleYamlReader.asText(edges.get(NORTH))));
  }

  /**
   * The bounding area of a polygon given as WKT
   *
   * @param value of the form
   * @return the bounding area
   */
  private static OsmBoundary polygonOf(Object value) {
    var wkt = SimpleYamlReader.asText(value);
    ExceptionUtils.throwIllegalArgumentIf(wkt == null, "Bounding area polygon is not WKT text");
    return OsmBoundary.of(PlanitJtsUtils.createPolygonFromWkt(wkt));
  }

  /**
   * The bounding area of a named OSM boundary
   *
   * @param value of the form
   * @return the bounding area
   */
  private static OsmBoundary osmBoundaryOf(Object value) {
    var fields = fieldsOf(OSM_BOUNDARY, value, Set.of(NAME), Set.of(TYPE, ADMIN_LEVEL));
    return OsmBoundary.of(SimpleYamlReader.asText(fields.get(NAME)), SimpleYamlReader.asText(fields.get(TYPE)),
        SimpleYamlReader.asText(fields.get(ADMIN_LEVEL)), null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<OsmBoundary> getValueType() {
    return OsmBoundary.class;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OsmBoundary fromPlainValue(Object plainValue) {
    var forms = SimpleYamlReader.asMap(plainValue);
    ExceptionUtils.throwIllegalArgumentIf(forms == null || forms.size() != 1,
        "A bounding area is exactly one of %s, %s or %s", BOX, POLYGON, OSM_BOUNDARY);
    var form = forms.entrySet().iterator().next();
    switch (form.getKey()) {
    case BOX:
      return boxOf(form.getValue());
    case POLYGON:
      return polygonOf(form.getValue());
    case OSM_BOUNDARY:
      return osmBoundaryOf(form.getValue());
    default:
      throw new IllegalArgumentException(String.format("A bounding area is exactly one of %s, %s or %s, not %s",
          BOX, POLYGON, OSM_BOUNDARY, form.getKey()));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object toPlainValue(OsmBoundary boundary) {
    if (boundary.hasBoundaryName()) {
      var fields = new LinkedHashMap<String, Object>();
      fields.put(NAME, boundary.getBoundaryName());
      if (boundary.hasBoundaryType()) {
        fields.put(TYPE, boundary.getBoundaryType());
      }
      if (boundary.hasBoundaryAdminLevel()) {
        fields.put(ADMIN_LEVEL, boundary.getBoundaryAdminLevel());
      }
      return Map.of(OSM_BOUNDARY, fields);
    }
    PlanItRunTimeException.throwIf(!boundary.hasBoundingPolygon(), "Bounding area %s has neither name nor polygon",
        boundary);
    return Map.of(POLYGON, boundary.getBoundingPolygon().toText());
  }
}

package org.goplanit.osm.tags;

import org.apache.commons.collections4.set.UnmodifiableSet;

import java.util.Map;
import java.util.Set;

/** tags used by Osm Boundary relation, e.g. boundary=administrative
 * 
 * @author markr
 *
 */
public class OsmBoundaryTags {

  /* key */
  public static final String BOUNDARY = "boundary";

  /* values - types */

  /** administrative boundaries, the most used and most relevant for PLANit typically */
  public static final String ADMINISTRATIVE = "administrative";

  /**  boundary of recognized aboriginal / indigenous / native peoples */
  public static final String ABORIGINAL = "aboriginal_lands";

  /** single forest demarcation,  */
  public static final String FOREST = "forest";

  public static final String FOREST_COMPARTMENT = "forest_compartment";

  public static final String HAZARD = "hazard";

  public static final String HEALTH = "health";

  public static final String MARITIME = "maritime";

  /** point, cannot be used for bounding box purposes */
  public static final String MARKER = "marker";

  public static final String NATIONAL_PARK = "national_park";

  public static final String PLACE = "place";

  public static final String POLITICAL = "political";

  public static final String POSTAL_CODE = "postal_code";

  public static final String PROTECED_AREA = "proteced_area";

  /* values - attributes */

  public static final String ADMIN_LEVEL = "admin_level";

  /** list all supported values for boundary key */
  protected final static UnmodifiableSet<String> boundaryValueTags;

  static {
    boundaryValueTags = (UnmodifiableSet<String>) Set.of(
            ADMINISTRATIVE,
            ABORIGINAL,
            FOREST,
            FOREST_COMPARTMENT,
            HAZARD,
            HEALTH,
            MARITIME,
            MARKER,
            NATIONAL_PARK,
            PLACE,
            POLITICAL,
            POSTAL_CODE,
            PROTECED_AREA
    );
  }

  /** get the boundary key tag
   *
   * @return boundary key tag
   */
  public static String getBoundaryKeyTag() {
    return BOUNDARY;
  }

  /**
   * Access to set of all boundary value tags that are supported (in no particular order)
   *
   * @return boundary value tags
   */
  public static UnmodifiableSet<String> getBoundaryValues(){
    return boundaryValueTags;
  }

  /** verify if passed in tag is indeed the boundary key tag
   *
   * @param tag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isBoundaryKeyTag(String tag) {
    return BOUNDARY.equals(tag);
  }

  /** Verify if tags contain the boundary key
   *
   * @param tags to verify
   * @return true if boundary=* exists, false otherwise
   */
  public static boolean hasBoundaryKeyTag(Map<String, String> tags) {
    return tags.containsKey(getBoundaryKeyTag());
  }

  /** Verify if tags contain the value for tha boundary key indicated
   *
   * @param tags to verify
   * @param boundaryValueTag tag to verify  for boundary=value
   * @return true if a match false otherwise
   */
  public static boolean hasBoundaryValueTag(Map<String, String> tags, String boundaryValueTag) {
    return hasBoundaryKeyTag(tags) && tags.get(getBoundaryKeyTag()).equals(boundaryValueTag);
  }
}

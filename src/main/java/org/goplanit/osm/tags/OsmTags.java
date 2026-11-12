package org.goplanit.osm.tags;

import java.util.Map;

/**
 * common OSM tags that we might need to refer to
 *
 */
public class OsmTags {

  private OsmTags(){}
      
    /* misc */

  /** key of an amenity, e.g. {@code amenity=ferry_terminal} */
  public static final String AMENITY = "amenity";

  /** key saying whether a closed way is an area, e.g. {@code area=yes} */
  public static final String AREA = "area";
  /** value of a ferry terminal, e.g. {@code amenity=ferry_terminal} */
  public static final String FERRY_TERMINAL = "ferry_terminal";

  /** value meaning the left side */
  public static final String LEFT = "left";

  /** key of a name */
  public static final String NAME = "name";

  /** value meaning no */
  public static final String NO = "no";

  /** value meaning none */
  public static final String NONE = "none";

  /** value meaning yes */
  public static final String YES = "yes";

  /** value meaning the right side */
  public static final String RIGHT = "right";

  /** value meaning both sides, or both directions */
  public static final String BOTH = "both";

  /** value of an entity that is proposed, not built */
  public static final String PROPOSED = "proposed";

  /** key of a relation's type, see {@link OsmRelationTypeTags} */
  public static final String TYPE = "type";

  /** key of a reference, such as a road number or a platform number */
  public static final String REF = "ref";

  /** key of a local reference, written as {@code loc_ref}, see {@link #LOCAL_REF} */
  public static final String LOC_REF = "loc_ref";

  /** key of a local reference, such as a platform number within a station */
  public static final String LOCAL_REF = "local_ref";

  /** key of the vertical layer of a way, e.g. {@code layer=1} for a bridge */
  public static final String LAYER = "layer";


  /** Check if tags indicate the entity is in fact an area
     *
     * @param tags to verify
     * @return true when area, false otherwise
     */
    public static boolean isArea(Map<String, String> tags) {
      return tags.containsKey(AREA) && !tags.get(AREA).equals(NO);
    }

    /** Check if tags indicate the entity is an amenity
   *
   * @param tags to verify
   * @return true when amenity, false otherwise
   */
    public static boolean isAmenity(Map<String, String> tags) {
      return tags.containsKey(AMENITY) && !tags.get(AMENITY).equals(NO);
    }

  /** verify if the key value combination is under construction, e.g., normally key=value, but when under construction it
   * is tagged key=construction and construction=value.
   *
   * @param tags to use
   * @param key to check against
   * @param value to check against
   * @return true when under construction, false otherwise
   */
    public static boolean isUnderConstruction(Map<String, String> tags, String key, String value){
      return tags.containsKey(key) && tags.get(key).equals(OsmRailwayTags.CONSTRUCTION) &&
      tags.containsKey(OsmRailwayTags.CONSTRUCTION) && tags.get(OsmRailwayTags.CONSTRUCTION).equals(value);
    }
}

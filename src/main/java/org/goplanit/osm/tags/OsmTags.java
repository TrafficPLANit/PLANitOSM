package org.goplanit.osm.tags;

import java.util.Map;

/**
 * common OSM tags that we might need to refer to
 *
 */
public class OsmTags {
      
    /*  tags related to nodes */
        
    public static final String TRAFFIC_SINGALS = "traffic_signals";

    public static final String CROSSING = "crossing";    
                     
    /* misc */

  public static final String AMENITY = "amenity";

  public static final Object AREA = "area";
  public static final String FERRY_TERMINAL = "ferry_terminal";

  public static final String LEFT = "left";

  public static final String NAME = "name";

  public static final String NO = "no";

  public static final String NONE = "none";

  public static final String YES = "yes";

  public static final String RIGHT = "right";

  public static final String BOTH = "both";

  public static final String PROPOSED = "proposed";

  public static final String TYPE = "type";

  public static final String REF = "ref";

  public static final String LOC_REF = "loc_ref";

  public static final String LOCAL_REF = "local_ref";

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

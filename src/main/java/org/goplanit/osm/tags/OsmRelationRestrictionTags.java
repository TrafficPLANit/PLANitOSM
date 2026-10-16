package org.goplanit.osm.tags;

/**
 * Relation restriction value tags that exist (for key restriction).
 * See <a href=https://wiki.openstreetmap.org/wiki/Relation:restriction>OSM wiki restrictions</a> page for more info
 *
 * @author markr
 *
 */
public class OsmRelationRestrictionTags {

  /** key for restriction types */
  public static final String RESTRICTION = OsmRelationTypeTags.RESTRICTION;
  
  /* values */
  
  public static final String NO_RIGHT_TURN = "no_right_turn";
  public static final String NO_LEFT_TURN = "no_left_turn";
  public static final String NO_U_TURN = "no_u_turn";
  public static final String NO_STRAIGHT_ON = "no_straight_on";
  public static final String ONLY_RIGHT_TURN = "only_right_turn";
  public static final String ONLY_LEFT_TURN = "only_left_turn";
  public static final String ONLY_U_TURN = "only_u_turn";
  public static final String ONLY_STRAIGHT_ON = "only_straight_on";
  public static final String NO_ENTRY = "no_entry";
  public static final String NO_EXIT = "no_exit";

  /** key listing the OSM modes a restriction does not apply to, separated by semicolons */
  public static final String EXCEPT = "except";

  /** start of the key of a restriction for one OSM mode only, e.g. restriction:hgv */
  public static final String RESTRICTION_FOR_MODE_PREFIX = RESTRICTION + ":";

  /** key of a restriction applying at certain times only, not a mode */
  public static final String RESTRICTION_CONDITIONAL = RESTRICTION_FOR_MODE_PREFIX + "conditional";

  /**
   * The OSM mode a key restricts for that mode only, such as hgv for restriction:hgv
   *
   * @param key to check
   * @return the OSM mode (or mode category), null when the key is not a restriction for one mode
   */
  public static String getModeOfRestrictionForMode(String key) {
    if (key == null || !key.startsWith(RESTRICTION_FOR_MODE_PREFIX) || key.equals(RESTRICTION_CONDITIONAL)) {
      return null;
    }
    return key.substring(RESTRICTION_FOR_MODE_PREFIX.length());
  }

  /**
   * Verify if the tags hold a restriction, for every mode or for one mode only
   *
   * @param tags to check
   * @return true when they hold a restriction tag or a restriction tag for one mode, false otherwise
   */
  public static boolean hasRestriction(java.util.Map<String, String> tags) {
    return tags.containsKey(RESTRICTION) ||
        tags.keySet().stream().anyMatch(key -> getModeOfRestrictionForMode(key) != null);
  }

  
  
}

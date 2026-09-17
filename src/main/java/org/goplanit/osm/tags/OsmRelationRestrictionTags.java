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

  
  
}

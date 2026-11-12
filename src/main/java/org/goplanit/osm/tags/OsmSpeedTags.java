package org.goplanit.osm.tags;

/**
 * common speed limit information tags
 * 
 * @author markr
 *
 */
public class OsmSpeedTags {

  private OsmSpeedTags(){}
  
  /** key of the speed limit of a way, both directions */
  public static final String MAX_SPEED = "maxspeed";
  
  /** key of the speed limit in the way's direction */
  public static final String MAX_SPEED_FORWARD = "maxspeed:forward";
  
  /** key of the speed limit against the way's direction */
  public static final String MAX_SPEED_BACKWARD = "maxspeed:backward";
  
  /** key of the speed limit of each lane, separated by | */
  public static final String MAX_SPEED_LANES = "maxspeed:lanes";
  
  /** key of the speed limit of each lane in the way's direction, separated by | */
  public static final String MAX_SPEED_FORWARD_LANES = "maxspeed:forward:lanes";
  
  /** key of the speed limit of each lane against the way's direction, separated by | */
  public static final String MAX_SPEED_BACKWARD_LANES = "maxspeed:backward:lanes";
  
  /** unit addendum for max speed values in miles per hour*/
  public static final String MILES_PER_HOUR = "mph";
  
  /** unit addendum for max speed values in knots*/
  public static final String KNOTS = "knots";  

}

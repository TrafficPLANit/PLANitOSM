package org.planit.osm.tags;

import java.util.Map;

import org.planit.utils.locale.DrivingDirectionDefaultByCountry;

/**
 * Tags related to directions, e.g. direction=<option>, or mode specific direction subtags, e.g. vehicle:backward=yes
 * 
 * @author markr
 *
 */
public class OsmDirectionTags {
  
  /** key */
  public static final String DIRECTION = "direction";
  
  /* possible values */

  public static final String CLOCKWISE = "clockwise";
  
  public static final String ANTI_CLOCKWISE = "anticlockwise";
  
  /* other direction related tags that can be used in combination with road modes */

  /** direction subtype for road modes, e.g. vehicle:foward=yes" */
  public static final String FORWARD = "forward";
  
  /** direction subtype for road modes, e.g. vehicle:backward=yes" */
  public static final String BACKWARD = "backward";
  
  /** direction subtype for road modes, e.g. bicycle:lane:both=yes" */
  public static final String BOTH = "both";
  
  /** check if explicit driving direction in clockwise direction is provided
   * @param tags to check
   * @return true when direction=clockwise is present, false otherwise
   */
  public static boolean isDirectionExplicitClockwise(Map<String,String> tags) {
    return tags.containsKey(OsmDirectionTags.DIRECTION) && tags.get(OsmDirectionTags.DIRECTION).equals(OsmDirectionTags.CLOCKWISE);
  }
  
  /** check if explicit driving direction in clockwise direction is provided
   * @param tags to check
   * @return true when direction=anticlockwise is present, false otherwise
   */  
  public static boolean isDirectionExplicitAntiClockwise(Map<String,String> tags) {
    return tags.containsKey(OsmDirectionTags.DIRECTION) && tags.get(OsmDirectionTags.DIRECTION).equals(OsmDirectionTags.ANTI_CLOCKWISE);
  }  
    
}

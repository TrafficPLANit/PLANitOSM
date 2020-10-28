package org.planit.osm.util;

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
}

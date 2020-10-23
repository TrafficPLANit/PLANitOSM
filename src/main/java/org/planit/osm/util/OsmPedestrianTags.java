package org.planit.osm.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * pedestrian related tags which are not really grouped in a logical way in OSM, hence we provide all of them here as well as some convenience methods
 * to quickly collect some of the most relevant ones
 * 
 * @author markr
 *
 */
public class OsmPedestrianTags {
  
  /** set of available sidewalk value tags */
  protected static Set<String> osmSideWalkValueTags = new HashSet<String>();
  
  /** set of available pedestrian key tags */
  protected static Set<String> osmPedestrianKeyTags = new HashSet<String>();
  
    
  /** can be used as key or value, e.g. highway=footway, or footway=x */
  public static final String FOOTWAY = "footway";
  
  /** value for footway to indicate crossing of a road, footway=crossing*/ 
  public static final String CROSSING = "crossing";  
  
  /** can be used as key or value, e.g., footway=sidewalk, or sidewalk=x */ 
  public static final String SIDEWALK = "sidewalk";
  
  /* sidewalk specific values */
  
  /** value for sidewalk key, see also https://wiki.openstreetmap.org/wiki/Key:sidewalk */ 
  public static final String BOTH = "both";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String RIGHT = "right";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String LEFT = "left";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String SEPARATE = "separate";  
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String SIDEWALK_YES = "yes";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String SIDEWALK_NO = "no";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String SIDEWALK_NONE = "none";  
    
  /* populate sidewalk value options */
  static {
    osmSideWalkValueTags.add(BOTH);
    osmSideWalkValueTags.add(RIGHT);
    osmSideWalkValueTags.add(LEFT);
    osmSideWalkValueTags.add(SEPARATE);
    osmSideWalkValueTags.add(SIDEWALK_YES);
    osmSideWalkValueTags.add(SIDEWALK_NO);
    osmSideWalkValueTags.add(SIDEWALK_NONE);
    osmSideWalkValueTags = Collections.unmodifiableSet(osmSideWalkValueTags);
  }
  
  
  /** can be used as key to indicate access for the mode pedestrians , e.g., foot=yes*/ 
  public static final String FOOT = "foot";
  
  /* populate pedestrian related key tags */
  static {
    osmPedestrianKeyTags.add(FOOT);
    osmPedestrianKeyTags.add(FOOTWAY);    
    osmPedestrianKeyTags.add(SIDEWALK);
    osmPedestrianKeyTags = Collections.unmodifiableSet(osmPedestrianKeyTags);
  }  
  
  /** Collect the Osm sidewalk value tags
   * 
   * @return unmodifiable set of side walk value tags
   */
  public static final Set<String> getOsmSidewalkValueTags(){
    return osmSideWalkValueTags;
  }
  
  /** collect set of key tags that signify specific pedestrian configurations. Currently:
   * 
   * <ul>
   * <li>sidewalk</li>
   * <li>foot</li>
   * <li>footway</li>
   * </ul>
   * 
   * @return unmodifiable set of pedestrian related key tags
   */
  public static final Set<String> getOsmPedestrianKeyTags(){
    return osmPedestrianKeyTags;
  }  

}

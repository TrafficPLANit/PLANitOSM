package org.planit.osm.tags;

import java.util.Map;
import java.util.stream.Stream;

import org.planit.osm.util.OsmTagUtils;

/**
 * pedestrian related tags which are not really grouped in a logical way in OSM, hence we provide all of them here as well as some convenience methods
 * to quickly collect some of the most relevant ones. For the mode "foot" see also {@code OsmRoadModeTags}
 * 
 * @author markr
 *
 */
public class OsmPedestrianTags {
  
  protected static String[] PEDESTRIAN_POSITIVE_VALUE_TAGS = {OsmPedestrianTags.YES, OsmPedestrianTags.SIDEWALK};
  
  protected static String[] PEDESTRIAN_NEGATIVE_VALUE_TAGS = {OsmPedestrianTags.NO, OsmPedestrianTags.NONE, OsmPedestrianTags.SEPARATE};
      
  protected static String[] PEDESTRIAN_LOCATION_VALUE_TAGS = {OsmPedestrianTags.BOTH, OsmPedestrianTags.LEFT, OsmPedestrianTags.RIGHT};
  
  protected static String[] PEDESTRIAN_POSITIVE_AND_LOCATION_VALUE_TAGS = Stream.concat(Stream.of(PEDESTRIAN_POSITIVE_VALUE_TAGS), Stream.of(PEDESTRIAN_LOCATION_VALUE_TAGS)).toArray(String[]::new);  
    
  /** set of available pedestrian key tags 
   * <ul>
   * <li>foot</li>
   * <li>pedestrian</li>
   * <li>footway</li>
   * <li>sidewalk</li>
   * </ul>
   * */
  protected static final String[] osmPedestrianKeyTags = { OsmPedestrianTags.FOOT, OsmPedestrianTags.FOOTWAY, OsmPedestrianTags.SIDEWALK, OsmPedestrianTags.PEDESTRIAN};
  
  /** can be used as key to indicate access for the mode pedestrians , e.g., foot=yes*/ 
  public static final String FOOT = OsmRoadModeTags.FOOT;  
    
  /** can be used as key or value, e.g. highway=footway, or footway=x */
  public static final String FOOTWAY = OsmHighwayTags.FOOTWAY;
  
  /** value for footway to indicate crossing of a road, footway=crossing*/ 
  public static final String CROSSING = "crossing";  
  
  /** can be used as key or value, e.g., footway=sidewalk, or sidewalk=x */ 
  public static final String SIDEWALK = "sidewalk";
  
  /** should not be used, but some legacy tags exist, so should be parseable, see https://wiki.openstreetmap.org/wiki/Key:foot */ 
  public static final String PEDESTRIAN = "pedestrian";  
  
  /* sidewalk specific values */
  
  /** value for sidewalk key, see also https://wiki.openstreetmap.org/wiki/Key:sidewalk */ 
  public static final String BOTH = "both";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String RIGHT = "right";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String LEFT = "left";
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String SEPARATE = OsmAccessTags.SEPARATE;  
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String YES = OsmTags.YES;
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String NO = OsmTags.NO;
  
  /** value for sidewalk key , see also https://wiki.openstreetmap.org/wiki/Key:sidewalk*/ 
  public static final String NONE = OsmTags.NONE;         
  
  /** collect set of key tags that signify specific pedestrian configurations. Currently:
   * 
   * <ul>
   * <li>sidewalk</li>
   * <li>foot</li>
   * <li>footway</li>
   * <li>pedestrian</li>
   * </ul>
   * 
   * @return unmodifiable set of pedestrian related key tags
   */
  public static final String[] getOsmPedestrianKeyTags(){
    return osmPedestrianKeyTags;
  }  
  
  /** Verify from the passed in tags if a side walk or footway osmkey is present with any of these value tags
   * 
   * @param tags to verify
   * @param accessValueTags to consider
   * @return true when one or more of the tag values is found, false otherwise
   */
  public static boolean hasExplicitSidewalkOrFootwayWithAccessValue(Map<String, String> tags, String... accessValueTags) {
    if(OsmTagUtils.containsAnyKey(tags, getOsmPedestrianKeyTags())){
      return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, getOsmPedestrianKeyTags(), accessValueTags);
    }
    return false;    
  }
  
  /** Verify from the passed in tags if a side walk or footway is present that is accessible to pedestrians
   * 
   *  sidewalk=
   * <ul>
   * <li>yes</li>
   * <li>both</li>
   * <li>left</li>
   * <li>right</li>
   * </ul>
   * or footway=sidewalk
   * 
   * @param tags to verify
   * @return true when explicitly mentioned and available, false otherwise (could still support pedestrians if highway type suports it by default)
   */
  public static boolean hasExplicitlyIncludedSidewalkOrFootway(Map<String, String> tags) {
    return hasExplicitSidewalkOrFootwayWithAccessValue(tags, PEDESTRIAN_POSITIVE_AND_LOCATION_VALUE_TAGS);
  }
  
  /** verify from the passed in tags if a side walk or footway is present that is not accesible to pedestrians based on
   * 
   *  sidewalk=
   * <ul>
   * <li>no</li>
   * <li>none</li>
   * <li>separate</li>
   * </ul>
   * 
   * @param tags to verify
   * @return true when explicitly mentioned and available, false otherwise (could still support pedestrians if highway type suports it by default)
   */
  public static boolean hasExplicitlyExcludedSidewalkOrFootway(Map<String, String> tags) {
    return hasExplicitSidewalkOrFootwayWithAccessValue(tags, PEDESTRIAN_NEGATIVE_VALUE_TAGS);
  }   

}

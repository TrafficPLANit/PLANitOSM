package org.planit.osm.tags;

import java.util.Map;
import java.util.stream.Stream;

import org.planit.osm.util.OsmTagUtils;

/**
 * Commonly used tags in relation to bicycles or bicycle ways. similar to buses, bicycle ways and lanes can be tagged following two different schemes:
 * <ul>
 * <li>cycleway scheme, relying on location (left or right, and opposite direction or not)</li>
 * <li>bicycle lanes, lane specification for bicycle lanes (in 99% of cases combine with oneway)</li>
 * </ul>
 * 
 * @author markr
 *
 */
public class OsmBicycleTags {
  
  /**
   * <ul>
   * <li>lane</li>
   * <li>shared_lane</li>
   * <li>shoulder</li>
   * <li>track</li>
   * <li>share_busway</li>
   * </ul>
   */
  protected static final String[] CYCLEWAY_REGULAR_POSITIVE_VALUE_TAGS = 
    {OsmBicycleTags.LANE, OsmBicycleTags.SHARED_LANE, OsmBicycleTags.SHOULDER, OsmBicycleTags.TRACK, OsmBicycleTags.SHARE_BUSWAY};
  
  /**
   * <ul>
   * <li>no</li>
   * <li>seaprate</li>
   * <li>proposed</li>
   * </ul>
   */
  protected static final String[] CYCLEWAY_REGULAR_NEGATIVE_VALUE_TAGS = 
    {OsmBicycleTags.NO, OsmBicycleTags.SEPARATE, OsmBicycleTags.PROPOSED};  
  
  /**
   * <ul>
   * <li>opposite_lane</li>
   * <li>opposite_track</li>
   * <li>opposite_share_busway</li>
   * </ul>
   */
  protected static final String[] CYCLEWAY_OPPOSITE_DIRECTION_POSITIVE_VALUE_TAGS = 
    {OsmBicycleTags.OPPOSITE_TRACK, OsmBicycleTags.OPPOSITE_LANE, OsmBicycleTags.OPPOSITE_SHARE_BUSWAY};
  
  /**
   * <ul>
   * <li>cycleway</li>
   * <li>cycleway:both</li>
   * </ul>
   */  
  protected static final String[] BASIC_CYCLEWAY_KEY_TAGS = 
    {OsmBicycleTags.CYCLEWAY, OsmBicycleTags.CYCLEWAY_BOTH ,OsmBicycleTags.CYCLEWAY_RIGHT, OsmBicycleTags.CYCLEWAY_LEFT};
  
  /**
   * <ul>
   * <li>cycleway:left</li>
   * <li>cycleway:right</li>
   * </ul>
   */  
  protected static final String[] LOCATION_BASED_CYCLEWAY_KEY_TAGS = {OsmBicycleTags.CYCLEWAY_RIGHT, OsmBicycleTags.CYCLEWAY_LEFT};    

  /**
   * combining {@code basicCycleWayKeyTags} and {@code locationBasedCycleWayKeyTags}
   */  
  protected static final String[] BASIC_AND_LOCATION_BASED_KEY_TAGS = Stream.concat(Stream.of(BASIC_CYCLEWAY_KEY_TAGS),Stream.of(LOCATION_BASED_CYCLEWAY_KEY_TAGS)).toArray(String[]::new); 
  
  /**
   * <ul>
   * <li>cycleway:left:oneway</li>
   * <li>cycleway:right:oneway</li>
   * </ul>
   */  
  protected static final String[] ONEWAY_LOCATION_BASED_CYCLEWAY_KEY_TAGS = 
    {OsmBicycleTags.CYCLEWAY_LEFT_ONEWAY, OsmBicycleTags.CYCLEWAY_RIGHT_ONEWAY};  
  
  /*keys*/
  
  /** mode bicycle, can be used as key bicycle=yes */
  public static final String BICYCLE = OsmRoadModeTags.BICYCLE;
  
  /** highway type cycle way, which can also be used as key cycleway=*/
  public static final String CYCLEWAY = OsmHighwayTags.CYCLEWAY;   
  
  public static final String CYCLEWAY_BOTH = OsmTagUtils.createCompositeOsmKey(CYCLEWAY, OsmTags.BOTH);
  
  /* cycleway scheme */
  
  public static final String CYCLEWAY_RIGHT = OsmTagUtils.createCompositeOsmKey(CYCLEWAY, OsmTags.RIGHT);
  
  public static final String CYCLEWAY_LEFT = OsmTagUtils.createCompositeOsmKey(CYCLEWAY, OsmTags.LEFT);    
  
  public static final String CYCLEWAY_RIGHT_ONEWAY = OsmTagUtils.createCompositeOsmKey(CYCLEWAY_RIGHT, OsmOneWayTags.ONEWAY);
  
  public static final String CYCLEWAY_LEFT_ONEWAY = OsmTagUtils.createCompositeOsmKey(CYCLEWAY_LEFT, OsmOneWayTags.ONEWAY);
    
  /* values */
    
  public static final String DISMOUNT = OsmAccessTags.DISMOUNT; 
  
  public static final String LANE = OsmLaneTags.LANE;   
  
  public static final String NO = OsmTags.NO;
  
  public static final String OPPOSITE = "opposite";
   
  public static final String OPPOSITE_TRACK = "opposite_track";
  
  public static final String OPPOSITE_LANE = OsmLaneTags.OPPOSITE_LANE;
  
  public static final String OPPOSITE_SHARE_BUSWAY = "opposite_share_busway";  
  
  public static final String SHARE_BUSWAY = "share_busway";
  
  public static final String SHARED_LANE = OsmLaneTags.SHARED_LANE;
  
  public static final String SHOULDER = "shoulder";
  
  public static final String SEPARATE = OsmAccessTags.SEPARATE;
  
  public static final String SIDEPATH = "sidepath";
  
  public static final String TRACK = OsmHighwayTags.TRACK;
  
  public static final String YES = OsmTags.YES;  
  
  private static final String PROPOSED = OsmTags.PROPOSED;  
  
  
  /* cycleway scheme */
  
  /** Collect basic cycleway key tags based on {@code BASIC_CYCLEWAY_KEY_TAGS}, when includeLocationSubKey is true, we also include the subtags left and right consistent with
   * {@code BASIC_AND_LOCATION_BASED_KEY_TAGS}
   * 
   * @param includeLocationSubflag
   * @return request key tags
   */
  public static final String[] getCycleWayKeyTags(boolean includeLocationSubKey) {
    if(!includeLocationSubKey) {
      return BASIC_CYCLEWAY_KEY_TAGS;
    }else {
      return BASIC_AND_LOCATION_BASED_KEY_TAGS;
    }
   }
  
  /** Collect our used cycleway key tags (including subtags) based on {@code ONEWAY_LOCATION_BASED_CYCLEWAY_KEY_TAGS}
   * 
   * @return location based (left,right) one way cycleway keys
   */
  public static String[] getCycleWayOneWayLocationBasedKeyTags() {  
    return ONEWAY_LOCATION_BASED_CYCLEWAY_KEY_TAGS;
   }
  

  /** Collect all value tags that are deemed to represent a cycleway in the main direction(s) of travel, based on
   *  {@code CYCLEWAY_VALUE_TAGS_MAIN_DIRECTION} protected member
   * 
   * @return main direction value tags indicating a cycleway
   */
  public static String[] getCycleWayRegularPositiveValueTags() {  
   return CYCLEWAY_REGULAR_POSITIVE_VALUE_TAGS;
  }  
  
  /** collect all value tags that are deemed to represent the existence of a cycleway in the opposite direction of travel in case of a one way street. Based on
   *  {@code CYCLEWAY_VALUE_TAGS_OPPOSITE_DIRECTION} protected member
   * 
   * @return opposite direction value tags indicating a cycleway
   */  
  public static String[] getCycleWayPositiveOppositeDirectionValueTags() {  
    return CYCLEWAY_OPPOSITE_DIRECTION_POSITIVE_VALUE_TAGS;
  }
  
  /** collect all value tags that are deemed to explicitly not represent a cycleway, based on
   *  {@code CYCLEWAY_REGULAR_NEGATIVE_VALUE_TAGS} protected member
   * 
   * @return regular negative value tags 
   */
  public static String[] getCycleWayRegularNegativeValueTags() {  
   return CYCLEWAY_REGULAR_NEGATIVE_VALUE_TAGS;
  }  
  
  /** Verify if a cycleway is present for the eligible keys, where the value of any of the keys is assumed to either match the {@code getCycleWayValueTagsForMainDirection} in
   * case oppositeDirection is set to false, and {@code getCycleWayValueTagsForOppositeDirection} when oppositeDirection is set to true 
   *  
   * @param tags to verify
   * @param oppositeDirection when true we only check for opposite direction value tags based on  {@code getCycleWayValueTagsForOppositeDirection}, otherwise we utilise {@code getCycleWayValueTagsForMainDirection}
   * @param cyclewayKeys eligible keys, assumed to be valid cycleway keys from this class (not checked)
   * @return true when present, false otherwise
   */
  public static boolean isCyclewayIncludedForAnyOf(Map<String, String> tags, boolean oppositeDirection, String... cyclewayKeys) {
    if(!oppositeDirection) {
      return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, cyclewayKeys, OsmBicycleTags.getCycleWayRegularPositiveValueTags());
    }else {
      return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, cyclewayKeys, OsmBicycleTags.getCycleWayPositiveOppositeDirectionValueTags());
    }
  }    
  
  /** same as {@code isCyclewayIncludedForAnyOf(tags, true, cyclewayKeys)}
   * 
   * @param tags to verify
   * @param cyclewayKeys eligible keys, assumed to be valid cycleway keys from this class (not checked)
   * @return true when present, false otherwise
   */
  public static boolean isOppositeCyclewayIncludedForAnyOf(Map<String, String> tags, String... cyclewayKeys) {
      return isCyclewayIncludedForAnyOf(tags, true, cyclewayKeys);
    }


  /** same as {@code isCyclewayIncludedForAnyOf(tags, false, cyclewayKeys)}
   * 
   * @param tags to verify
   * @param cyclewayKeys eligible keys, assumed to be valid cycleway keys from this class (not checked)
   * @return true when present, false otherwise
   */
  public static boolean isCyclewayIncludedForAnyOf(Map<String, String> tags, String... cyclewayKeys) {
    return isCyclewayIncludedForAnyOf(tags, false, cyclewayKeys);
  }
  
  /** Verify if a cycleway is explicitly not present for the eligible keys, using the cycle ways regular negative affirmation value tags as a means of checking  

   * @param tags to verify
   * @param cyclewayKeys eligible keys, assumed to be valid cycleway keys from this class (not checked)
   * @return true when present, false otherwise
   */  
  public static boolean isCyclewayExcludedForAnyOf(Map<String, String> tags, String... cyclewayKeys) {
    return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, cyclewayKeys, OsmBicycleTags.getCycleWayRegularNegativeValueTags());
  }   
  

  /** Verify if a bi directional cycle way is present on either the left or right hand side of the way, e.g. {@code cycleway:left:oneway =<negative access value>} or
   * {@code cycleway:right:oneway =<negative access value>}
   * 
   * @param tags to verify
   * @return true when present, false otherwise
   */
  public static boolean isNoOneWayCyclewayInAnyLocation(Map<String, String> tags) {
    return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, getCycleWayOneWayLocationBasedKeyTags(), OsmBicycleTags.getCycleWayRegularNegativeValueTags());
  }
  
}

package org.goplanit.osm.tags;

import java.util.Map;

import org.goplanit.osm.util.OsmTagUtils;

/**
 * tags related to lanes and some basic convenience methods to check for lane related OSM tags
 * 
 * @author markr
 *
 */
public class OsmLaneTags {

   /* keys */
  
  public static final String LANES = "lanes";

  public static final String LANES_FORWARD = OsmTagUtils.createCompositeOsmKey(LANES, OsmDirectionTags.FORWARD);

  public static final String LANES_BACKWARD = OsmTagUtils.createCompositeOsmKey(LANES, OsmDirectionTags.BACKWARD);
  
  public static String LANES_PSV = OsmTagUtils.createCompositeOsmKey(LANES, OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE);
  
  public static String LANES_BUS = OsmTagUtils.createCompositeOsmKey(LANES, OsmRoadModeTags.BUS);
  
  public static final String LANES_PSV_FORWARD = OsmTagUtils.createCompositeOsmKey(LANES_PSV, OsmDirectionTags.FORWARD);
  
  public static final String LANES_PSV_BACKWARD = OsmTagUtils.createCompositeOsmKey(LANES_PSV, OsmDirectionTags.BACKWARD);
  
public static final String LANES_BUS_FORWARD = OsmTagUtils.createCompositeOsmKey(LANES_PSV, OsmDirectionTags.FORWARD);
  
  public static final String LANES_BUS_BACKWARD = OsmTagUtils.createCompositeOsmKey(LANES_PSV, OsmDirectionTags.BACKWARD);  
  
  /* value tags */
  
  /** e.g. cycleway:left = lane */
  public static final String LANE = "lane";
  
  /** e.g. busway = opposite_lane */
  public static final String OPPOSITE_LANE = "opposite_lane";
  
  /** e.g. cycleway = shared_lane */
  public static final String SHARED_LANE = "shared_lane";
  
  /** Verify if any of the eligible keys have a value that represents an OSMLaneTags.LANE for the given tags
   * 
   * @param tags to verify
   * @param keys eligible keys
   * @return true when {@code <key>=lane} is present, false otherwise
   */
  public static boolean isLaneIncludedForAnyOf(Map<String, String> tags, String... keys) {
    return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, keys, OsmLaneTags.LANE);
  }    
  
  /** Verify if any of the eligible keys have a value that represents an OSMLaneTags.LANE for the given tags
   * 
   * @param tags to verify
   * @param keys eligible keys
   * @return true when {@code <key>=opposite_lane} is present, false otherwise
   */
  public static boolean isOppositeLaneIncludedForAnyOf(Map<String, String> tags, String... keys) {
    return OsmTagUtils.anyKeyMatchesAnyValueTag(tags, keys, OsmLaneTags.OPPOSITE_LANE);
  }

   
  
}

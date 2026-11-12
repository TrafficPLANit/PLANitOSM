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

  private OsmLaneTags(){}

   /* keys */
  
  /** key of the number of lanes of a way, both directions together */
  public static final String LANES = "lanes";

  /** key of the number of lanes in the way's direction, {@code lanes:forward} */
  public static final String LANES_FORWARD = OsmTagUtils.createCompositeOsmKey(LANES, OsmDirectionTags.FORWARD);

  /** key of the number of lanes against the way's direction, {@code lanes:backward} */
  public static final String LANES_BACKWARD = OsmTagUtils.createCompositeOsmKey(LANES, OsmDirectionTags.BACKWARD);
  
  /** key of the number of lanes for public service vehicles, {@code lanes:psv} */
  public static final String LANES_PSV =
          OsmTagUtils.createCompositeOsmKey(LANES, OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE);
  
  /** key of the number of bus lanes, {@code lanes:bus} */
  public static final String LANES_BUS = OsmTagUtils.createCompositeOsmKey(LANES, OsmRoadModeTags.BUS);
  
  /** key of the number of lanes for public service vehicles in the way's direction, {@code lanes:psv:forward} */
  public static final String LANES_PSV_FORWARD = OsmTagUtils.createCompositeOsmKey(LANES_PSV, OsmDirectionTags.FORWARD);
  
  /** key of the number of lanes for public service vehicles against the way's direction, {@code lanes:psv:backward} */
  public static final String LANES_PSV_BACKWARD =
          OsmTagUtils.createCompositeOsmKey(LANES_PSV, OsmDirectionTags.BACKWARD);
  
  /** key of the number of bus lanes in the way's direction, {@code lanes:bus:forward} */
  public static final String LANES_BUS_FORWARD = OsmTagUtils.createCompositeOsmKey(LANES_BUS, OsmDirectionTags.FORWARD);
  
  /** key of the number of bus lanes against the way's direction, {@code lanes:bus:backward} */
  public static final String LANES_BUS_BACKWARD =
          OsmTagUtils.createCompositeOsmKey(LANES_BUS, OsmDirectionTags.BACKWARD);
  
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

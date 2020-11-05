package org.planit.osm.util;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Commonly used tags in relation to buses
 * 
 * Note that buses and/or public service vehicles have three different possible tagging schemes that are supported by open street maps, namely:
 * 
 * <ul>
 * <li>busway scheme</li>
 * <li>lanes:mode scheme</li>
 * <li>mode:lanes scheme</li>
 * </ul>
 *  * 
 * To provide support for all three schemes, we added some convenience methods that provide some of the most common keys that adhere to each of these schemes. 
 * For more information on these schemes see https://wiki.openstreetmap.org/wiki/Bus_lanes
 * 
 * @author markr
 *
 */
public class OsmBusTags {
  
  /* busway scheme */
  
  /** busway scheme compatible
   * <ul>
   * <li>busway</li>
   * <li>busway:both</li>
   * <li>busway:left</li>
   * <li>busway:right</li>
   * </ul>
   */
  protected static final String[] BUSWAY_SCHEME_KEYTAGS = {OsmBusTags.BUSWAY, OsmBusTags.BUSWAY_RIGHT, OsmBusTags.BUSWAY_LEFT, OsmBusTags.BUSWAY_BOTH};
  
  /* lanes:mode scheme */
  
  /** lanes:mode scheme compatible
   * <ul>
   * <li>lanes:psv:forward</li>
   * <li>lanes:psv:backward</li>
   * </ul>
   */
  protected static final String[] LANES_PSV_DIRECTION_KEYTAGS = {OsmBusTags.LANES_PSV_FORWARD, OsmBusTags.LANES_PSV_BACKWARD};
  
  /** lanes:mode scheme compatible
   * <ul>
   * <li>lanes:bus:forward</li>
   * <li>lanes:bus:backward</li>
   * </ul>
   */
  protected static final String[] LANES_BUS_DIRECTION_KEYTAGS = {OsmBusTags.LANES_BUS_FORWARD, OsmBusTags.LANES_BUS_BACKWARD};    
  
  /** lanes:mode scheme compatible for one way OSM ways
   * <ul>
   * <li>lanes:bus</li>
   * <li>lanes:psv</li>
   * </ul>
   */  
  protected static final String[] LANES_BUS_AND_PSV_KEYTAGS = {OsmBusTags.LANES_BUS, OsmBusTags.LANES_PSV};  
  
  /** combination of {@code  LANES_PSV_KEYTAGS} and {@code BUSWAY_SCHEME_KEYTAGS} */
  protected static final String[] LANES_BUS_AND_PSV_DIRECTION_KEYTAGS = Stream.concat(Arrays.stream(LANES_BUS_DIRECTION_KEYTAGS), Arrays.stream(LANES_PSV_DIRECTION_KEYTAGS)).toArray(String[]::new);
  
  /** lanes:mode scheme compatible
   * 
   * <ul>
   * <li>lanes:bus:forward</li>
   * <li>lanes:psv:forward</li>
   * </ul>
   */
  protected static final String[] LANES_BUS_AND_PSV_FORWARD_KEYTAGS = {OsmBusTags.LANES_PSV_FORWARD, OsmBusTags.LANES_BUS_FORWARD};
  
  /** lanes:mode scheme compatible
   * <ul>
   * <li>lanes:bus:backward</li>
   * <li>lanes:psv:backward</li>
   * </ul>
   */
  protected static final String[] LANES_BUS_AND_PSV_BACKWARD_KEYTAGS = {OsmBusTags.LANES_PSV_BACKWARD, OsmBusTags.LANES_BUS_BACKWARD};
  
  /* mode:lanes scheme */
  
  /** mode:lanes scheme compatible for oneway OSM ways where the lanes imply the main direction
   * 
   * <ul>
   * <li>bus:lanes</li>
   * <li>psv:lanes</li>
   * </ul>
   */
  protected static final String[] BUS_AND_PSV_LANES_KEYTAGS = {OsmBusTags.PSV_LANES, OsmBusTags.BUS_LANES};    
  
  /** mode:lanes scheme compatible
   * 
   * <ul>
   * <li>bus:lanes:forward</li>
   * <li>psv:lanes:forward</li>
   * </ul>
   */
  protected static final String[] BUS_AND_PSV_LANES_FORWARD_KEYTAGS = {OsmBusTags.PSV_LANES_FORWARD, OsmBusTags.BUS_LANES_FORWARD};
  
  /** mode:lanes scheme compatible
   * <ul>
   * <li>bus:lanes:backward</li>
   * <li>psv:lanes:backward</li>
   * </ul>
   */
  protected static final String[] BUS_AND_PSV_LANES_BACKWARD_KEYTAGS = {OsmBusTags.PSV_LANES_BACKWARD, OsmBusTags.BUS_LANES_BACKWARD};  
  
  
  /*keys*/
  
  /** mode bus, can be used as key bus=yes */
  public static final String BUS = OsmRoadModeTags.BUS;
  
  /** mode category psv under which bus falls, can be used as key psv=yes */
  public static final String PUBLIC_SERVICE_VEHICLE = OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE;  
  
  /* busway scheme */
  
  /** busway, used as key busway=*/
  public static final String BUSWAY = "busway";
  
  public static final String BUSWAY_RIGHT = PlanitOsmUtils.createCompositeOsmKey(BUSWAY, OsmTags.RIGHT);
  
  public static final String BUSWAY_LEFT = PlanitOsmUtils.createCompositeOsmKey(BUSWAY, OsmTags.LEFT);
  
  public static final String BUSWAY_BOTH = PlanitOsmUtils.createCompositeOsmKey(BUSWAY, OsmTags.BOTH);
  
  /* lanes:mode scheme */
  
  public static final String LANES_PSV = OsmLaneTags.LANES_PSV;
  
  public static final String LANES_BUS = OsmLaneTags.LANES_BUS;  
  
  public static final String LANES_PSV_FORWARD = OsmLaneTags.LANES_PSV_FORWARD;

  public static final String LANES_PSV_BACKWARD = OsmLaneTags.LANES_PSV_BACKWARD;
  
  public static final String LANES_BUS_FORWARD = OsmLaneTags.LANES_BUS_FORWARD;

  public static final String LANES_BUS_BACKWARD = OsmLaneTags.LANES_BUS_BACKWARD;  
  
  /* mode:lanes scheme for one way*/
  
  public static final String PSV_LANES = PlanitOsmUtils.createCompositeOsmKey(PUBLIC_SERVICE_VEHICLE, OsmLaneTags.LANES);
  
  public static final String BUS_LANES = OsmLaneTags.LANES_BUS;
  
  /* mode:lanes scheme for bi-directional way*/  
  
  public static final String PSV_LANES_FORWARD = PlanitOsmUtils.createCompositeOsmKey(PSV_LANES, OsmDirectionTags.FORWARD);

  public static final String PSV_LANES_BACKWARD = PlanitOsmUtils.createCompositeOsmKey(PSV_LANES, OsmDirectionTags.BACKWARD);
  
  public static final String BUS_LANES_FORWARD = PlanitOsmUtils.createCompositeOsmKey(BUS_LANES, OsmDirectionTags.FORWARD);

  public static final String BUS_LANES_BACKWARD = PlanitOsmUtils.createCompositeOsmKey(BUS_LANES, OsmDirectionTags.BACKWARD);    
  
  /* values */
      
  public static final String LANE = OsmLaneTags.LANE;
      
  public static final String OPPOSITE_LANE = OsmLaneTags.OPPOSITE_LANE;
   
 
  /* busway scheme  methods */  
  
  /** Collect our used busway key tags (including subtags) based on {@code BUSWAY_SCHEME_KEYTAGS} which are relevant when following the busway scheme approach
   * @return busway key tags
   */
  public static final String[] getBuswaySchemeKeyTags() {  
    return BUSWAY_SCHEME_KEYTAGS;
   }  
  
  /* lanes:<mode> scheme  methods */
  
  /** Collect our lanes:psv:* key and lanes:bus:* tags (including subtags) based on {@code LANES_PSV_AND_BUS_KEYTAGS} 
   * @return lanes:psv:* and lanes:bus:* key tags
   */
  public static final String[] getLanesBusAndPsvSchemeKeyTags() {  
    return LANES_BUS_AND_PSV_KEYTAGS;
   }     
  
  /** Collect our lanes:psv:* key tags (including subtags) based on {@code LANES_PSV_KEYTAGS} which are relevant when following the lanes:psv scheme approach
   * @return lanes:psv:* key tags
   */
  public static final String[] getLanesPsvSchemeKeyDirectionTags() {  
    return LANES_PSV_DIRECTION_KEYTAGS;
   }
  
  /** Collect our lanes:psv:* key tags (including subtags) based on {@code LANES_PSV_KEYTAGS} which are relevant when following the lanes:psv scheme approach
   * @return lanes:bus:* key tags
   */
  public static final String[] getLanesBusSchemeDirectionKeyTags() {  
    return LANES_BUS_DIRECTION_KEYTAGS;
   }  
  
  /** Collect our lanes:psv:* key and lanes:bus:* tags (including subtags) based on {@code LANES_PSV_AND_BUS_KEYTAGS} 
   * @return lanes:psv:* and lanes:bus:* key tags
   */
  public static final String[] getLanesBusAndPsvSchemeDirectionKeyTags() {  
    return LANES_BUS_AND_PSV_DIRECTION_KEYTAGS;
   }   
    
  /** Collect our lanes:psv:forward key and lanes:bus:forward tags based on {@code LANES_PSV_AND_BUS_FORWARD_KEYTAGS} 
   * @return {@code LANES_PSV_AND_BUS_FOWARD_KEYTAGS}
   */
  public static final String[] getLanesBusAndPsvForwardKeyTags() {  
    return LANES_BUS_AND_PSV_FORWARD_KEYTAGS;
   }    

  /** Collect our lanes:psv:backward key and lanes:bus:backward tags based on {@code LANES_PSV_AND_BUS_BACKWARD_KEYTAGS} 
   * @return {@code LANES_PSV_AND_BUS_FOWARD_KEYTAGS}
   */
  public static final String[] getLanesBusAndPsvBackwardKeyTags() {  
    return LANES_BUS_AND_PSV_BACKWARD_KEYTAGS;
   }   
  
  /** Collect our lanes:psv:forward key and lanes:bus:forward tags (or backward) depending on the passed in direction. This is compatible with the
   * lanes:mode scheme approach
   * 
   * @param  isForwardDirection when true we get the forward direction keys, otherwise backward
   * @return relevant keys 
   */
  public static final String[] getLanesBusAndPsvKeyTagsInDirection(boolean isForwardDirection) {  
    return isForwardDirection ? getLanesBusAndPsvForwardKeyTags() :  getLanesBusAndPsvBackwardKeyTags();
  }
   
  
  /* <mode>:lanes scheme  methods */
  
  /** Collect our lanes:psv key and lanes:bus key. This is compatible with the lanes:mode scheme approach for one way OSM ways consistent with
   * {@code BUS_AND_PSV_LANES_KEYTAGS}
   * 
   * @return relevant keys 
   */
  public static final String[] getBusAndPsvLanesKeyTags() {  
    return BUS_AND_PSV_LANES_KEYTAGS;
  }   
  
  /** Collect our psv:lanes:forward key and lanes:bus:forward tags based on {@code PSV_AND_BUS_LANES_FORWARD_KEYTAGS} 
   * @return {@code LANES_PSV_AND_BUS_FOWARD_KEYTAGS}
   */
  public static final String[] getBusAndPsvLanesForwardKeyTags() {  
    return BUS_AND_PSV_LANES_FORWARD_KEYTAGS;
   }    

  /** Collect our psv:lanes:backward key and bus:lanes:backward tags based on {@code PSV_AND_BUS_LANES_BACKWARD_KEYTAGS} 
   * @return {@code LANES_PSV_AND_BUS_FOWARD_KEYTAGS}
   */
  public static final String[] getBusAndPsvLanesBackwardKeyTags() {  
    return BUS_AND_PSV_LANES_BACKWARD_KEYTAGS;
   }

  /** Collect our psv:lanes:forward key and bus:lanes:forward tags (or backward) depending on the passed in direction. This is compatible with the
   * /<mode/>:lanes scheme approach
   * 
   * @param  isForwardDirection when true we get the forward direction keys, otherwise backward
   * @return relevant keys 
   */  
  public static String[] getBusAndPsvLanesTagsInDirection(boolean isForwardDirection) {
    return isForwardDirection ? getBusAndPsvLanesForwardKeyTags() :  getBusAndPsvLanesBackwardKeyTags();
  }    
   

}

package org.planit.osm.tags;

import org.planit.osm.util.PlanitOsmUtils;

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
     

}

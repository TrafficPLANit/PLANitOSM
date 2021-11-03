package org.goplanit.osm.tags;

import org.goplanit.osm.util.OsmTagUtils;

/**
 * Commonly used tags in relation to busways
 * 
 * Note that buses and/or public service vehicles have three different possible tagging schemes that are supported by open street maps, namely:
 * 
 * <ul>
 * <li>busway scheme</li>
 * <li>lanes:mode scheme</li>
 * <li>mode:lanes scheme</li>
 * </ul>
 *   
 * The tags and method in this class are focussed on the busways only
 * 
 * @author markr
 *
 */
public class OsmBusWayTags {
  
  /* busway scheme */
  
  /** busway scheme compatible
   * <ul>
   * <li>busway</li>
   * <li>busway:both</li>
   * <li>busway:left</li>
   * <li>busway:right</li>
   * </ul>
   */
  protected static final String[] BUSWAY_SCHEME_KEYTAGS = {OsmBusWayTags.BUSWAY, OsmBusWayTags.BUSWAY_RIGHT, OsmBusWayTags.BUSWAY_LEFT, OsmBusWayTags.BUSWAY_BOTH};
    
  /*keys*/
  
  /** busway, used as key busway=*/
  public static final String BUSWAY = "busway";     
  
  /* busway scheme */
    
  public static final String BUSWAY_RIGHT = OsmTagUtils.createCompositeOsmKey(BUSWAY, OsmTags.RIGHT);
  
  public static final String BUSWAY_LEFT = OsmTagUtils.createCompositeOsmKey(BUSWAY, OsmTags.LEFT);
  
  public static final String BUSWAY_BOTH = OsmTagUtils.createCompositeOsmKey(BUSWAY, OsmTags.BOTH);
   
  /* values for the busway scheme */
      
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

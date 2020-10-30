package org.planit.osm.util;

/**
 * Commonly used tags in relation to buses
 * 
 * @author markr
 *
 */
public class OsmBusTags {
  
  /*keys*/
  
  /** mode bus, can be used as key bus=yes */
  public static final String BUS = OsmRoadModeTags.BUS;
  
  /** busway, used as key busway=*/
  public static final String BUSWAY = "busway";
  
  public static final String BUSWAY_RIGHT = PlanitOsmUtils.createCompositeOsmKey(BUSWAY, OsmTags.RIGHT);
  
  public static final String BUSWAY_LEFT = PlanitOsmUtils.createCompositeOsmKey(BUSWAY, OsmTags.LEFT);
  
  public static final String BUSWAY_BOTH = PlanitOsmUtils.createCompositeOsmKey(BUSWAY, OsmTags.BOTH);
  
  /* values */
      
  public static final String LANE = OsmLaneTags.LANE;
      
  public static final String OPPOSITE_LANE = OsmLaneTags.OPPOSITE_LANE;
  
  public static final String LANES_PSV_FORWARD = OsmLaneTags.LANES_PSV_FORWARD;

  public static final String LANES_PSV_BACKWARD = OsmLaneTags.LANES_PSV_BACKWARD;
 
    

}

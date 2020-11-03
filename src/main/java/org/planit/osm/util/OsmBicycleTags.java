package org.planit.osm.util;

/**
 * Commonly used tags in relation to bicycles or bicycle ways
 * 
 * @author markr
 *
 */
public class OsmBicycleTags {
  
  protected static final String[] cycleWayValueTagsMainDirection = 
    {OsmBicycleTags.LANE, OsmBicycleTags.SHARED_LANE, OsmBicycleTags.SHOULDER, OsmBicycleTags.TRACK, OsmBicycleTags.SHARE_BUSWAY};
  
  protected static final String[] cycleWayValueTagsOppositeDirection = 
    {OsmBicycleTags.OPPOSITE_TRACK, OsmBicycleTags.OPPOSITE_LANE, OsmBicycleTags.OPPOSITE_SHARE_BUSWAY};  
  
  /*keys*/
  
  /** mode bicycle, can be used as key bicycle=yes */
  public static final String BICYCLE = OsmRoadModeTags.BICYCLE;
  
  /** highway type cycle way, which can also be used as key cycleway=*/
  public static final String CYCLEWAY = OsmHighwayTags.CYCLEWAY;
  
  public static final String CYCLEWAY_RIGHT = PlanitOsmUtils.createCompositeOsmKey(CYCLEWAY, OsmTags.RIGHT);
  
  public static final String CYCLEWAY_LEFT = PlanitOsmUtils.createCompositeOsmKey(CYCLEWAY, OsmTags.LEFT);
  
  public static final String CYCLEWAY_BOTH = PlanitOsmUtils.createCompositeOsmKey(CYCLEWAY, OsmTags.BOTH);  
  
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
  

  public static String[] getCycleWayValueTagsForMainDirection() {  
   return cycleWayValueTagsMainDirection;
  }
  
  public static String[] getCycleWayValueTagsForOppositeDirection() {  
    return cycleWayValueTagsOppositeDirection;
   }  
}

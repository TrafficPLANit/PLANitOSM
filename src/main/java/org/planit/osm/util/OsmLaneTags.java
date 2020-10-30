package org.planit.osm.util;

/**
 * tags related to lanes
 * 
 * @author markr
 *
 */
public class OsmLaneTags {

   /* keys */
  
  public static final String LANES = "lanes";

  public static final String LANES_FORWARD = PlanitOsmUtils.createCompositeOsmKey(LANES, OsmDirectionTags.FORWARD);

  public static final String LANES_BACKWARD = PlanitOsmUtils.createCompositeOsmKey(LANES, OsmDirectionTags.BACKWARD);
  
  public static final String LANES_PSV_FORWARD = PlanitOsmUtils.createCompositeOsmKey(LANES, OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE,OsmDirectionTags.FORWARD);
  
  public static final String LANES_PSV_BACKWARD = PlanitOsmUtils.createCompositeOsmKey(LANES, OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE,OsmDirectionTags.BACKWARD);
  
  /* value tags */
  
  /** e.g. cycleway:left = lane */
  public static final String LANE = "lane";
  
  /** e.g. busway = opposite_lane */
  public static final String OPPOSITE_LANE = "opposite_lane";
  
  /** e.g. cycleway = shared_lane */
  public static final String SHARED_LANE = "shared_lane";  
}

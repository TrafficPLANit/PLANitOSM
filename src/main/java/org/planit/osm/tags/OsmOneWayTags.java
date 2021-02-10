package org.planit.osm.tags;

import java.util.Map;

import org.planit.osm.util.OsmTagUtils;

/**
 * common OSM one way tags and some helper methods
 *
 */
public class OsmOneWayTags {
  
  /* key */
  public static final String ONEWAY = "oneway";
  
  /* values */
  
  public static final String YES = OsmTags.YES;
  
  public static final String ONE_WAY_REVERSE_DIRECTION = "-1";
  
  public static final String NO = OsmTags.NO;
  
  public static final String REVERSIBLE = "reversible";
  
  public static final String ALTERNATING = "alternating";
  
  /** verify if the tags indicate a one way OSM way
   * 
   * @param tags to check
   * @return true when one way false otherwise
   */
  public static boolean isOneWay(Map<String,String> tags) {        
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmOneWayTags.ONEWAY, YES, ONE_WAY_REVERSE_DIRECTION, ALTERNATING, REVERSIBLE );
  }
  
  /** verify if the tags indicate a reversed one way OSM way, i.e., the value is set to "-1" indicating the main direction flows in the opposite direction
   * of the geometry (backward)
   * 
   * @param tags to check
   * @return true when one way false otherwise
   */
  public static boolean isReversedOneWay(Map<String,String> tags) {        
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmOneWayTags.ONEWAY, ONE_WAY_REVERSE_DIRECTION);
  }  
  
}

package org.goplanit.osm.tags;

import java.util.Map;

/**
 * Tags related to junction values, e.g. {@code junction=<option>}
 * 
 * @author markr
 *
 */
public class OsmJunctionTags {
  
  /* key */
    
  public static final String JUNCTION = "junction";
  
  /* values */
  
  public static final String ROUNDABOUT = "roundabout";
  
  public static final String CIRCULAR = "circular";
  
  /** verify if the tags indicate a circular way or a road that is part of a circular way by checking its
   * junction tag's value (if any). roundabouts and circular tagged junctions can be considered as such and when
   * found true is returned, otherwise false
   * 
   * @param tags to verify
   * @return tru when junction=circular or junction=roundabout is present, false otherwise
   */
  public static boolean isPartOfCircularWayJunction(Map<String,String> tags) {
    if(tags.containsKey(JUNCTION)) {
      /* when valid value tag it is accepted as part of a circular road*/
      String value = tags.get(JUNCTION);
      return (value.equals(ROUNDABOUT) || value.equals(CIRCULAR));
    }
    return false;
  }
    

}

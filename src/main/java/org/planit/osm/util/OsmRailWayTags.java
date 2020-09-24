package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Most OSM rail way tags that could be of value for a network. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:railway 
 * 
 * @author markr
 *
 */
public class OsmRailWayTags {
  
  /** all currently available rail way tags */
  private static final Set<String> railway = new HashSet<String>();
  
  /**
   * populate the available railway mode tags
   */
  private static void populateModeTags() {
    railway.add(FUNICULAR);
    railway.add(LIGHT_RAIL);
    railway.add(MONO_RAIL);
    railway.add(NARROW_GAUGE);
    railway.add(RAIL);
    railway.add(SUBWAY);
    railway.add(TRAM);
  }
  
  static {
    populateModeTags();
  }
  
  /* key */
  public static final String RAILWAY = "railway";
  
  /* values */
  
  public static final String FUNICULAR = "funicular";
  
  public static final String LIGHT_RAIL = "light_rail";
  
  public static final String MONO_RAIL = "monorail";
   
  public static final String NARROW_GAUGE = "narrow_gauge";
  
  public static final String RAIL = "rail";
  
  public static final String SUBWAY = "subway";
  
  public static final String TRAM = "tram";  
  

  /** verify if passed in tag is indeed a railway tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailWayTag(String railwayTag) {
    return railway.contains(railwayTag);
  }

  /**
   * provide a copy of all supported rail mode tags
   * @return all supported road modes
   */
  public static Collection<String> getSupportedRailModeTags() {
    return new HashSet<String>(railway);
  }
    
}

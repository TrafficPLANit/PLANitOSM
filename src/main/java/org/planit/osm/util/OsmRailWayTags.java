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
  
  /* values of types of railways */
  
  public static final String ABANDONED = "abandoned";
  
  public static final String CONSTRUCTION = "construction";
  
  public static final String DISUSED = "disused";
    
  public static final String FUNICULAR = "funicular";
  
  public static final String LIGHT_RAIL = "light_rail";
  
  public static final String MONO_RAIL = "monorail";
   
  public static final String NARROW_GAUGE = "narrow_gauge";
  
  public static final String PRESERVED = "preserved";  
  
  public static final String RAIL = "rail";
  
  public static final String SUBWAY = "subway";
  
  public static final String TRAM = "tram";  
  
  /* other railway values that do not reflect a railway but are used as a way in combination with the railway key */
  
  /* a platform is a separate (disconnected) way to indicate a platform */
  public static final String PLATFORM = "platform";      

  /** verify if passed in tag is indeed a railway value tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailwayValueTag(String railwayTag) {
    return railway.contains(railwayTag);
  }
  
  /** verify if passed in tag is indeed the railway key tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailwayKeyTag(String railwayTag) {
    return RAILWAY.equals(railwayTag);
  }  

  /**
   * provide a copy of all supported rail mode tags
   * @return all supported road modes
   */
  public static Collection<String> getSupportedRailModeTags() {
    return new HashSet<String>(railway);
  }
    
}

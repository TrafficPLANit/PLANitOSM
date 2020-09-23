package org.planit.osm.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Most OSM rail mode tags that could be of value for a network. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:railway 
 * 
 * @author markr
 *
 */
public class OsmRailModeTags {
  
  /** all currently available rail mode tags */
  private static final Set<String> modeTags = new HashSet<String>();
  
  /**
   * populate the available mode tags
   */
  private static void populateModeTags() {
    modeTags.add(FUNICULAR);
    modeTags.add(LIGHT_RAIL);
    modeTags.add(MONO_RAIL);
    modeTags.add(NARROW_GAUGE);
    modeTags.add(RAIL);
    modeTags.add(SUBWAY);
    modeTags.add(TRAM);
  }
  
  static {
    populateModeTags();
  }
  
  public static final String FUNICULAR = "funicular";
  
  public static final String LIGHT_RAIL = "light_rail";
  
  public static final String MONO_RAIL = "monorail";
   
  public static final String NARROW_GAUGE = "narrow_gauge";
  
  public static final String RAIL = "rail";
  
  public static final String SUBWAY = "subway";
  
  public static final String TRAM = "tram";  
  

  /** verify if passed in tag is indeed a mode tag
   * @param modeTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailModeTag(String modeTag) {
    return modeTags.contains(modeTag);
  }
  
}

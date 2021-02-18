package org.planit.osm.tags;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Most OSM rail based mode tags. This is specifically addressing when rail modes are used as key for access indication, e.g., train=yes rather than
 * railway=rail for example
 * 
 * @author markr
 *
 */
public class OsmRailModeTags {
  
  /** all currently available mode tags */
  private static final Set<String> MODE_TAGS = new HashSet<String>();
  
  /**
   * populate the available mode tags
   */
  private static void populateModeTags() {
    MODE_TAGS.add(FUNICULAR);
    MODE_TAGS.add(LIGHT_RAIL);
    MODE_TAGS.add(MONO_RAIL);
    MODE_TAGS.add(NARROW_GAUGE);
    MODE_TAGS.add(SUBWAY);
    MODE_TAGS.add(TRAIN); /* translates to railway=rail */
    MODE_TAGS.add(OsmRailwayTags.TRAM);
  }
  
  static {
    populateModeTags();
  }

  /* NO VEHICLE */
  
  public static final String FUNICULAR = OsmRailwayTags.FUNICULAR;
  
  public static final String LIGHT_RAIL = OsmRailwayTags.LIGHT_RAIL;
  
  public static final String MONO_RAIL = OsmRailwayTags.MONO_RAIL;
  
  public static final String NARROW_GAUGE = OsmRailwayTags.NARROW_GAUGE;
  
  public static final String SUBWAY = OsmRailwayTags.SUBWAY;
  
  public static final String TRAIN = "train";
  
  public static final String TRAM = OsmRailwayTags.TRAM;
    
  
  /** verify if passed in tag is indeed a mode tag
   * @param modeTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailModeTag(String modeTag) {
    return MODE_TAGS.contains(modeTag);
  }
  
  /**
   * provide a copy of all supported rail mode tags
   * @return all supported rail modes
   */
  public static Collection<String> getSupportedRailModeTags(){
    return new HashSet<String>(MODE_TAGS);
  }

  /** convert the rail mode to the related railway type. In all cases they are the same, except for mode "train" which translates to railway=rail
   * 
   * @param osmMode to convert
   * @return converted railway value that goes with the mode
   */
  public static String convertModeToRailway(String osmMode) {
    return TRAIN.equals(osmMode) ? OsmRailwayTags.RAIL : osmMode; 
  }
  
  /** convert the railway to the related  mode. In all cases they are the same, except for mode "train" which translates to railway=rail
   * 
   * @param osmRailwayValue to convert
   * @return converted mode that goes with the railway 
   */
  public static String convertRailwayToMode(String osmRailwayValue) {
    return OsmRailwayTags.RAIL.equals(osmRailwayValue) ? TRAIN : osmRailwayValue;
  }  
  
}
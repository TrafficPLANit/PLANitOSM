package org.goplanit.osm.tags;

import java.util.*;

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
  
  private OsmRailModeTags(){}

  static {
    populateModeTags();
  }

  /* NO VEHICLE */
  
  /** mode of a funicular, an inclined railway pulled by cable */
  public static final String FUNICULAR = OsmRailwayTags.FUNICULAR;
  
  /** mode of a light rail */
  public static final String LIGHT_RAIL = OsmRailwayTags.LIGHT_RAIL;
  
  /** mode of a monorail */
  public static final String MONO_RAIL = OsmRailwayTags.MONO_RAIL;
  
  /** mode of a narrow gauge railway */
  public static final String NARROW_GAUGE = OsmRailwayTags.NARROW_GAUGE;
  
  /** mode of a subway, a metro */
  public static final String SUBWAY = OsmRailwayTags.SUBWAY;
  
  /** mode of a train, on a {@code railway=rail} */
  public static final String TRAIN = "train";
  
  /** mode of a tram */
  public static final String TRAM = OsmRailwayTags.TRAM;
    
  
  /** verify if passed in tag is indeed a mode tag
   * @param modeTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailModeTag(String modeTag) {
    return MODE_TAGS.contains(modeTag);
  }
  
  /**
   * provide a all supported rail mode tags (unmodifiable)
   * @return all supported rail modes
   */
  public static Set<String> getSupportedRailModeTags(){
    return Collections.unmodifiableSet(MODE_TAGS);
  }
  
  /**
   * Provide a copy of all supported rail mode tags
   * 
   * @return all supported rail modes
   */  
  public static String[] getSupportedRailModeTagsAsArray() {
    return MODE_TAGS.toArray(new String[MODE_TAGS.size()]);
  }  

  /** Convert the rail mode to the related railway type. In all cases they are the same, except for mode "train" which translates to railway=rail
   * 
   * @param osmMode to convert
   * @return converted railway value that goes with the mode
   */
  public static String convertModeToRailway(String osmMode) {
    return TRAIN.equals(osmMode) ? OsmRailwayTags.RAIL : osmMode; 
  }
  
  /** Convert the railway to the related  mode. In all cases they are the same, except for mode "train" which translates to railway=rail
   * 
   * @param osmRailwayValue to convert
   * @return converted mode that goes with the railway 
   */
  public static String convertRailwayToMode(String osmRailwayValue) {
    return OsmRailwayTags.RAIL.equals(osmRailwayValue) ? TRAIN : osmRailwayValue;
  } 
  
  /** Verify if any of the passed in osmModes can be qualified as a rail mode
   * 
   * @param osmModes to consider
   * @return true when overlap exists, false otherwise
   */
  public static boolean containsAnyMode(Collection<String> osmModes) {
    return !Collections.disjoint(MODE_TAGS, osmModes);
  }  
  
  /** Collect the modes that represent the intersection of the passed in modes and available modes of this class
   * 
   * @param eligibleOsmModes to use
   * @return intersection with modes in this class
   */
  public static TreeSet<String> getModesFrom(final Collection<String> eligibleOsmModes) {
    TreeSet<String> intersectionModes = new TreeSet<>(eligibleOsmModes);
    intersectionModes.retainAll(MODE_TAGS);
    return intersectionModes;
  }
  
  /** collect all rail based pt modes available from the passed in modes
   * 
   * @param eligibleOsmModes to extract from
   * @return found public transport based modes
   */
  public static TreeSet<String> getPublicTransportModesFrom(final Collection<String> eligibleOsmModes) {
    return getModesFrom(eligibleOsmModes);
  }
  
}

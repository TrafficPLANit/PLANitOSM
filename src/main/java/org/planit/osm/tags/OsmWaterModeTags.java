package org.planit.osm.tags;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Most OSM water based mode tags. This is specifically addressing when water modes are used as key for access indication, e.g., ferry=yes
 * 
 * @author markr
 *
 */
public class OsmWaterModeTags {
  
  /** all currently available mode tags */
  private static final Set<String> MODE_TAGS = new HashSet<String>();
  
  /**
   * populate the available mode tags
   */
  private static void populateModeTags() {
    MODE_TAGS.add(FERRY);    
  }
  
  static {
    populateModeTags();
  }

  /* Water modes */
  
  public static final String FERRY = "ferry";
       
  /** verify if passed in tag is indeed a mode tag
   * @param modeTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isWaterModeTag(String modeTag) {
    return MODE_TAGS.contains(modeTag);
  }
  
  /**
   * provide a copy of all supported water mode tags
   * @return all supported rail modes
   */
  public static Set<String> getSupportedWaterModeTags(){
    return Collections.unmodifiableSet(MODE_TAGS);
  }
  
  /**
   * provide a copy of all supported water mode tags
   * @return all supported rail modes
   */  
  public static String[] getSupportedWaterModeTagsAsArray() {
    return MODE_TAGS.toArray(new String[MODE_TAGS.size()]);
  }  
  
  /** verify if any of the passed in osmModes can be qualified as a water mode
   * @param osmModes
   * @return true when overlap exists, false otherwise
   */
  public static boolean containsAnyMode(Collection<String> osmModes) {
    return !Collections.disjoint(MODE_TAGS, osmModes);
  }  
  
  /** collect the modes that represent the intersection of the passed in modes and available modes of this class
   * 
   * @param eligibleOsmModes to use
   * @return intersection with modes in this class
   */
  public static Collection<String> getModesFrom(final Collection<String> eligibleOsmModes) {
    HashSet<String> intersectionModes = new HashSet<String>(eligibleOsmModes);
    intersectionModes.retainAll(MODE_TAGS);
    return intersectionModes;
  } 
  
  /** collect all rail based pt modes available from the passed in modes
   * 
   * @param eligibleOsmModes to extract from
   * @return found public transport based modes
   */
  public static Collection<String> getPublicTransportModesFrom(final Collection<String> eligibleOsmModes) {
    return getModesFrom(eligibleOsmModes);
  }   
  
}

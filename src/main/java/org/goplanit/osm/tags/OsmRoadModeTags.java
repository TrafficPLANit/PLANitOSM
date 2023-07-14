package org.goplanit.osm.tags;

import java.util.*;

/**
 * Most OSM road based mode tags. Not included: water modes or very specialised modes. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:access  
 * 
 * @author markr
 *
 */
public class OsmRoadModeTags {
  
  /** all currently available mode tags */
  private static final Set<String> MODE_TAGS = new HashSet<String>();
  
  /** all currently available public transport based road mode tags */
  private static final Set<String> PT_MODE_TAGS = new HashSet<String>();  
  
  /**
   * populate the available pt road mode tags
   */
  private static void populatePTModeTags() {
    PT_MODE_TAGS.add(CARRIAGE);
    PT_MODE_TAGS.add(TOURIST_BUS);
    PT_MODE_TAGS.add(COACH);
    PT_MODE_TAGS.add(BUS);
    PT_MODE_TAGS.add(SHARE_TAXI);
    PT_MODE_TAGS.add(MINI_BUS);
  }
  
  /**
   * populate the available road mode tags
   */
  private static void populateModeTags() {
    MODE_TAGS.add(FOOT);
    MODE_TAGS.add(DOG);
    MODE_TAGS.add(HORSE);
    MODE_TAGS.add(BICYCLE);
    MODE_TAGS.add(CARRIAGE);
    MODE_TAGS.add(TRAILER);
    MODE_TAGS.add(CARAVAN);
    MODE_TAGS.add(MOTOR_CYCLE);
    MODE_TAGS.add(MOPED);
    MODE_TAGS.add(MOFA);
    MODE_TAGS.add(MOTOR_CAR);
    MODE_TAGS.add(MOTOR_HOME);
    MODE_TAGS.add(TOURIST_BUS);
    MODE_TAGS.add(COACH);
    MODE_TAGS.add(AGRICULTURAL);
    MODE_TAGS.add(GOLF_CART);
    MODE_TAGS.add(ATV);
    MODE_TAGS.add(GOODS);
    MODE_TAGS.add(HEAVY_GOODS);
    MODE_TAGS.add(HEAVY_GOODS_ARTICULATED);
    MODE_TAGS.add(BUS);
    MODE_TAGS.add(TAXI);
    MODE_TAGS.add(SHARE_TAXI);
    MODE_TAGS.add(MINI_BUS);
  }
  
  static {
    populatePTModeTags();
    populateModeTags();
  }

  /* NO VEHICLE */
  
  public static final String FOOT = "foot";
  
  public static final String DOG = "dog";
  
  public static final String HORSE = "horse";
  
  /* VEHICLE  category */
  
  /** bicycle part of vehicle category type */
  public static final String BICYCLE = "bicycle";
  
  /** horse and carriage */
  public static final String CARRIAGE = "carriage";
  
  /** vehicle towing a trailer */
  public static final String TRAILER = "trailer";
  
  /** vehicle towing a caravan */
  public static final String CARAVAN = "caravan";  
  
  /* MOTORISED VEHICLE  category */
  
  /** motor cycle part of motorised vehicle category type */
  public static final String MOTOR_CYCLE = "motorcycle";
  
  /** motorised bicycls with a speed restriction of 45km/h */
  public static final String MOPED = "moped";
  
  /** motorised bicycls with a speed restriction of 25km/h */
  public static final String MOFA = "mofa";  
  
  /** motor car part of motorised vehicle category type */
  public static final String MOTOR_CAR = "motorcar"; 
  
  /** motor home part of motorised vehicle category type */
  public static final String MOTOR_HOME = "motorhome";
  
  /** tourist bus for long distance non public transport */
  public static final String TOURIST_BUS = "tourist_bus";
  
  /** bus for long distance non public transport */
  public static final String COACH = "coach";
  
  /** agricultural vehicles */
  public static final String AGRICULTURAL = "agricultural";
  
  /** golf cart vehicles */
  public static final String GOLF_CART = "golf_cart"; 
  
  /** all-terrain vehicles, e.g. quads */
  public static final String ATV = "atv";   
  
  /* MOTORISED VEHICLE carrying GOODS (not an official category) */
  
  /** goods vehicle part of goods category type, smaller than 3.5 tonnes */
  public static final String GOODS = "goods";  
  
  /** heavy goods vehicle part of goods category type, larger than 3.5 tonnes*/
  public static final String HEAVY_GOODS = "hgv";
  
  /** motor car part of goods category type */
  public static final String HEAVY_GOODS_ARTICULATED = "hgv_articulated"; 
  
  /* PUBLIC SERVICE VEHICLE category */
  
  /** heavy bus for public transport */
  public static final String BUS = "bus";
  
  /** taxi */
  public static final String TAXI = "taxi";
  
  /** share taxi, i.e., demand responsive small bus */
  public static final String SHARE_TAXI = "share_taxi";  
  
  /** smaller bus for public transport */
  public static final String MINI_BUS = "minibus";
   
  
  /** verify if passed in tag is indeed a mode tag
   * @param modeTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRoadModeTag(final String modeTag) {
    return MODE_TAGS.contains(modeTag);
  }
  
  /**
   * provide a copy of all supported road mode tags
   * @return all supported road modes
   */
  public static final Collection<String> getSupportedRoadModeTags(){
    return new HashSet<String>(MODE_TAGS);
  }

  /**
   * provide a copy of all supported road mode tags
   * @return all supported road modes
   */  
  public final static String[] getSupportedRoadModeTagsAsArray() {
    String[] modeTagsArray = new String[MODE_TAGS.size()];
    return MODE_TAGS.toArray(modeTagsArray);
  }

  /** Verify if any of the passed in osmModes can be qualified as a road mode
   * 
   * @param osmModes to consider
   * @return true when overlap exists, false otherwise
   */
  public static boolean containsAnyMode(final Collection<String> osmModes) {
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
    TreeSet<String> intersectionModes = new TreeSet<>(eligibleOsmModes);
    intersectionModes.retainAll(PT_MODE_TAGS);
    return intersectionModes;
  }  
  
}

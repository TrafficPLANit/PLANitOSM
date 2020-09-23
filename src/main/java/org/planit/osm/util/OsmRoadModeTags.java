package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Most OSM road based mode tags. Not included: water modes or very specialised modes. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:access  
 * 
 * @author markr
 *
 */
public class OsmRoadModeTags {
  
  /** all currently available mode tags */
  private static final Set<String> modeTags = new HashSet<String>();
  
  /**
   * populate the available mode tags
   */
  private static void populateModeTags() {
    modeTags.add(FOOT);
    modeTags.add(DOG);
    modeTags.add(HORSE);
    modeTags.add(BICYCLE);
    modeTags.add(CARRIAGE);
    modeTags.add(TRAILER);
    modeTags.add(CARAVAN);
    modeTags.add(MOTOR_CYCLE);
    modeTags.add(MOPED);
    modeTags.add(MOFA);
    modeTags.add(MOTOR_CAR);
    modeTags.add(MOTOR_HOME);
    modeTags.add(TOURIST_BUS);
    modeTags.add(COACH);
    modeTags.add(AGRICULTURAL);
    modeTags.add(GOLF_CART);
    modeTags.add(ATV);
    modeTags.add(GOODS);
    modeTags.add(HEAVY_GOODS);
    modeTags.add(HEAVY_GOODS_ARTICULATED);
    modeTags.add(BUS);
    modeTags.add(TAXI);
    modeTags.add(SHARE_TAXI);
    modeTags.add(MINI_BUS);
  }
  
  static {
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
  public static final String MOTOR_CYCLE = "motor_cycle";
  
  /** motorised bicycls with a speed restriction of 45km/h */
  public static final String MOPED = "moped";
  
  /** motorised bicycls with a speed restriction of 25km/h */
  public static final String MOFA = "mofa";  
  
  /** motor car part of motorised vehicle category type */
  public static final String MOTOR_CAR = "motor_car"; 
  
  /** motor home part of motorised vehicle category type */
  public static final String MOTOR_HOME = "motor_home";
  
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
  public static boolean isRoadModeTag(String modeTag) {
    return modeTags.contains(modeTag);
  }
  
  /**
   * provide a copy of all supported road mode tags
   * @return all supported road modes
   */
  public static Collection<String> getSupportedRoadModeTags(){
    return new HashSet<String>(modeTags);
  }
  
}

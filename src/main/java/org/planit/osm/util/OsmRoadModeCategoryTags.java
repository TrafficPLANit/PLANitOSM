package org.planit.osm.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Most common OSM mode tags used for multiple modes, i.e., indicating a category of modes 
 * 
 * @author markr
 *
 */
/**
 * @author markr
 *
 */
public class OsmRoadModeCategoryTags {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmRoadModeCategoryTags.class.getCanonicalName());  
  
  /** initialise the grouping of modes to categories as it is currently outlined on 
   * https://wiki.openstreetmap.org/wiki/Key:access */
  static {
    populateModesByCategory();
    populateCategoriesByMode();   
  }  
  
  /**
   * track all modes by category
   */
  private static final Map<String, Set<String> > osmCategory2Modes = new HashMap<String, Set<String> >();
  
  /**
   * track all categories by modes
   */
  private static final Map<String, Set<String> > osmMode2Categories = new HashMap<String, Set<String> >();
  
  /**
   * populate the modes by category
   */
  private static void populateModesByCategory() {
    /** no modes within this category yet */
    osmCategory2Modes.put(HIGH_OCCUPANCY_VEHICLE, new HashSet<String>());
    
    /** all public service vehicles */
    Set<String> publicServiceVehicles = new HashSet<String>();
    {
      publicServiceVehicles.add(OsmRoadModeTags.BUS);
      publicServiceVehicles.add(OsmRoadModeTags.TAXI);
      publicServiceVehicles.add(OsmRoadModeTags.SHARE_TAXI);
      publicServiceVehicles.add(OsmRoadModeTags.MINI_BUS);
    }
    osmCategory2Modes.put(PUBLIC_SERVICE_VEHICLE, publicServiceVehicles);
    
    /** all motor vehicles */
    Set<String> motorVehicles = new HashSet<String>(publicServiceVehicles);
    {
      /* single tracked */
      motorVehicles.add(OsmRoadModeTags.MOTOR_CYCLE);
      motorVehicles.add(OsmRoadModeTags.MOPED);
      motorVehicles.add(OsmRoadModeTags.MOFA);
      /* tourist vehicles */
      motorVehicles.add(OsmRoadModeTags.MOTOR_HOME);
      motorVehicles.add(OsmRoadModeTags.TOURIST_BUS);
      motorVehicles.add(OsmRoadModeTags.TOURIST_BUS);
      motorVehicles.add(OsmRoadModeTags.COACH);
      /* freight modes */
      motorVehicles.add(OsmRoadModeTags.GOODS);
      motorVehicles.add(OsmRoadModeTags.HEAVY_GOODS);
      motorVehicles.add(OsmRoadModeTags.HEAVY_GOODS_ARTICULATED);
      /* agricultural modes */
      motorVehicles.add(OsmRoadModeTags.AGRICULTURAL);
      /* other modes */
      motorVehicles.add(OsmRoadModeTags.GOLF_CART);
    }  
    osmCategory2Modes.put(MOTOR_VEHICLE, motorVehicles);    
    
    /** all vehicles, train excluded*/
    Set<String> vehicles = new HashSet<String>(motorVehicles);
    {
      /* non-motorised single tracked */
      vehicles.add(OsmRoadModeTags.BICYCLE);
      
      /* non-motorised double tracked */
      vehicles.add(OsmRoadModeTags.CARAVAN);
      vehicles.add(OsmRoadModeTags.TRAILER);
      vehicles.add(OsmRoadModeTags.CARRIAGE);
    }  
    osmCategory2Modes.put(VEHICLE, vehicles);      
  }
  
  /**
   * populate the categories by mode
   */
  private static void populateCategoriesByMode() {
    osmCategory2Modes.forEach( 
        (category, modes) -> {
          modes.forEach( 
              mode -> osmMode2Categories.putIfAbsent(mode,new HashSet<String>()).add(category)
          );
        });
  }
 
  /** group indicating any vehicle type */
  public static final String VEHICLE = "vehicle";
  
  /** group indicating any motor vehicle type */
  public static final String MOTOR_VEHICLE = "motor_vehicle";
   
  /** group indicating any public service vehicle */
  public static final String PUBLIC_SERVICE_VEHICLE = "psv";
  
  /** group indicating any public service vehicle */
  public static final String HIGH_OCCUPANCY_VEHICLE = "hov";
    
  /**
   * verify of the mode category is a valid Osm mode category
   * 
   * @param osmModeCategory to check
   * @return true when part of the category false otherwise
   */
  public static boolean isRoadModeCategoryTag(String osmModeCategory){
    return osmCategory2Modes.containsKey(osmModeCategory); 
  }   
    
  /**
   * collect all the OSM modes that fit within the given Osm mode category
   * 
   * @param osmModeCategory to collect modes for
   * @return modes within given category, or empty set if not present
   */
  public static Set<String> getRoadModesByCategory(String osmModeCategory){
    if(!osmCategory2Modes.containsKey(osmModeCategory)) {
      LOGGER.warning(String.format("OSM mode category %s is not listed among available categories, ignored",osmModeCategory));
    }
    return osmCategory2Modes.getOrDefault(osmModeCategory, new HashSet<String>());  
  }  
  
  /**
   * verify of the mode is part of the given Osm mode category
   * 
   * @param osmModeCategory to check
   * @return true when part of the category false otherwise
   */
  public static boolean containsRoadMode(String osmModeCategory){
    return osmCategory2Modes.getOrDefault(osmModeCategory, new HashSet<String>()).contains(osmModeCategory); 
  }

  /** given the mode, find the related mode category
   * @param osmMode to get its categories for, null if not in a category
   */
  public static Set<String> getRoadModeCategoriesByMode(String osmMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmMode)) {
      LOGGER.warning(String.format("mode %s is not a recognised OSM mode when obtaining its parent category, ignored", osmMode));
    } 
    return osmMode2Categories.get(osmMode);
  }   
  
  /** given the mode, find the related mode category
   * @param osmMode to get its categories for, null if not in a category
   */
  public static boolean isRoadModeInCategory(String osmMode, String osmModeCategory) {
    Set<String> modeCategories = getRoadModeCategoriesByMode(osmMode);
    if(modeCategories == null) {
      return false;
    }else if(!isRoadModeCategoryTag(osmModeCategory)) {
      LOGGER.warning(String.format("mode category %s is not a recognised OSM mode category, ignored", osmModeCategory));   
      return false; 
    }
    return modeCategories.contains(osmModeCategory);
  }   
}

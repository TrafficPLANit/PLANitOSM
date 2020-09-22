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
public class OsmModeCategoryTags {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmModeCategoryTags.class.getCanonicalName());  
  
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
    
    /** all public service vehicles, supplemented with train */
    Set<String> publicServiceVehicles = new HashSet<String>();
    {
      publicServiceVehicles.add(OsmModeTags.BUS);
      publicServiceVehicles.add(OsmModeTags.TAXI);
      publicServiceVehicles.add(OsmModeTags.SHARE_TAXI);
      publicServiceVehicles.add(OsmModeTags.MINI_BUS);
      publicServiceVehicles.add(OsmModeTags.TRAIN);      
    }
    osmCategory2Modes.put(PUBLIC_SERVICE_VEHICLE, publicServiceVehicles);
    
    /** all motor vehicles, train excluded*/
    Set<String> motorVehicles = new HashSet<String>(publicServiceVehicles);
    {
      /* single tracked */
      motorVehicles.add(OsmModeTags.MOTOR_CYCLE);
      motorVehicles.add(OsmModeTags.MOPED);
      motorVehicles.add(OsmModeTags.MOFA);
      /* tourist vehicles */
      motorVehicles.add(OsmModeTags.MOTOR_HOME);
      motorVehicles.add(OsmModeTags.TOURIST_BUS);
      motorVehicles.add(OsmModeTags.TOURIST_BUS);
      motorVehicles.add(OsmModeTags.COACH);
      /* freight modes */
      motorVehicles.add(OsmModeTags.GOODS);
      motorVehicles.add(OsmModeTags.HEAVY_GOODS);
      motorVehicles.add(OsmModeTags.HEAVY_GOODS_ARTICULATED);
      /* agricultural modes */
      motorVehicles.add(OsmModeTags.AGRICULTURAL);
      /* other modes */
      motorVehicles.add(OsmModeTags.GOLF_CART);
      /* minus train */
      motorVehicles.remove(OsmModeTags.TRAIN);
    }  
    osmCategory2Modes.put(MOTOR_VEHICLE, motorVehicles);    
    
    /** all vehicles, train excluded*/
    Set<String> vehicles = new HashSet<String>(motorVehicles);
    {
      /* non-motorised single tracked */
      vehicles.add(OsmModeTags.BICYCLE);
      
      /* non-motorised double tracked */
      vehicles.add(OsmModeTags.CARAVAN);
      vehicles.add(OsmModeTags.TRAILER);
      vehicles.add(OsmModeTags.CARRIAGE);
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
  public static boolean isModeCategoryTag(String osmModeCategory){
    return osmCategory2Modes.containsKey(osmModeCategory); 
  }   
    
  /**
   * collect all the OSM modes that fit within the given Osm mode category
   * 
   * @param osmModeCategory to collect modes for
   * @return modes within given category, or empty set if not present
   */
  public static Set<String> getModesByCategory(String osmModeCategory){
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
  public static boolean containsMode(String osmModeCategory){
    return osmCategory2Modes.getOrDefault(osmModeCategory, new HashSet<String>()).contains(osmModeCategory); 
  }

  /** given the mode, find the related mode category
   * @param osmMode to get its categories for, null if not in a category
   */
  public static Set<String> getModeCategoriesByMode(String osmMode) {
    if(!OsmModeTags.isModeTag(osmMode)) {
      LOGGER.warning(String.format("mode %s is not a recognised OSM mode when obtaining its parent category, ignored", osmMode));
    } 
    return osmMode2Categories.get(osmMode);
  }   
  
  /** given the mode, find the related mode category
   * @param osmMode to get its categories for, null if not in a category
   */
  public static boolean isModeInCategory(String osmMode, String osmModeCategory) {
    Set<String> modeCategories = getModeCategoriesByMode(osmMode);
    if(modeCategories == null) {
      return false;
    }else if(!isModeCategoryTag(osmModeCategory)) {
      LOGGER.warning(String.format("mode category %s is not a recognised OSM mode category, ignored", osmModeCategory));   
      return false; 
    }
    return modeCategories.contains(osmModeCategory);
  }   
}

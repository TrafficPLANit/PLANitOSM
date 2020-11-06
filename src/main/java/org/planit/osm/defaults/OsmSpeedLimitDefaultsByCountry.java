package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailWayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;
import org.planit.utils.locale.CountryNames;

/**
 * Convenience class for quickly collecting the speed limits for various countries, where possible
 * based on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed (highway types)
 * <p>
 * Note that a distinction is made between inside or outside a place, which refers to the road being
 * inside or outside an urban area. this signficantly impacts the speed limit.
 * </p><p>
 * Unfortunately, the link cannot judge if it is inside or outside. So, we leave it to the user
 * of this class to indicate which of the two values should be used (if there are two available).
 * </p><p>
 * Note 1: railway speed limits are not known in OSM, so we use a global default for all types, which the user
 * may override if required. Also no distinction is made between insode or outside an urban area.
 * </p>
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaultsByCountry {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmSpeedLimitDefaultsByCountry.class.getCanonicalName());
  
  /** store the global defaults as fall back option */
  protected static OsmSpeedLimitDefaults globalDefaultSpeedLimits;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmSpeedLimitDefaults> speedLimitDefaultsByCountryCode = new HashMap<String, OsmSpeedLimitDefaults>();  
  
  /* initialise */
  static {    
    try {     
      populateGlobalSpeedLimits();
    
      populateAustralianSpeedLimits();
      
    }catch (PlanItException e) {
      LOGGER.severe("unable to initialise global and/or country specific OSM speed limit defaults");
    }    
  }  
             
  /**
   * populate the global defaults for highway types in case the country is not available, or in case the road type for that country is not available
   * 
   * @param globalSpeedLimits to populate
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalDefaultHighwaySpeedLimits(OsmSpeedLimitDefaults globalSpeedLimits) throws PlanItException {
    /* note that these are physical speed limits for the most unrestricted mode on the highway type, when a mode has a lower
     * speed limit based on the mode, this will be applied, hence it is not needed to reduce the speed for a footway to 5 km/h */
    
    /* GLOBAL -->                                                          URBAN,  NON_URBAN */
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.MOTORWAY,        100,  120);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.MOTORWAY_LINK,   100,  120);    
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.TRUNK,           80,   100);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.TRUNK_LINK,      80,   100);    
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.PRIMARY,         60,   100);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.PRIMARY_LINK,    60,   100);    
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.SECONDARY,       50,   80);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.SECONDARY_LINK,  50,   80);    
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.TERTIARY,        50,   80);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.TERTIARY_LINK,   50,   80);    
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.UNCLASSIFIED,    50,   80);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.RESIDENTIAL,     40,   80);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.LIVING_STREET,   20,   20);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.PEDESTRIAN,      20,   20);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.TRACK,           20,   40);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.ROAD,            20,   40);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.SERVICE,         20,   40);   
    
    /* other less common tags PLANit specific made up defaults, generally low speed limits */
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.FOOTWAY,         20,   20);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.PATH,            20,   20);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.CYCLEWAY,        20,   20);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.STEPS,           10,   10);
    globalSpeedLimits.setHighwaySpeedLimitDefault(OsmHighwayTags.BRIDLEWAY,       20,   20);
  }  
  
  /**
   * populate the defaults for railway types for given country (or global)
   * 
   * Currently, we set the {@code OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH} as the default for all modes. This can overridden by the user if required 
   * 
   * 
   * @param speedLimitsToPopulate to populate
   * @throws PlanItException thrown if error
   */  
  protected static void populateDefaultRailwaySpeedLimits(OsmSpeedLimitDefaults speedLimitsToPopulate) {
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.FUNICULAR,     OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.LIGHT_RAIL,    OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.MONO_RAIL,     OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.NARROW_GAUGE,  OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.RAIL,          OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.SUBWAY,        OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setRailwaySpeedLimitDefault(OsmRailWayTags.TRAM,          OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);   
  }  
  
  /**
   * populate the global defaults for railway types in case the country is not available, or in case the railway type for that country is not available.
   * 
   * Currently, we set the {@code OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH} as the default for all modes. This can overridden by the user if required 
   * 
   * 
   * @param globalSpeedLimits to populate
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalDefaultRailwaySpeedLimits(OsmSpeedLimitDefaults globalSpeedLimits) throws PlanItException {        
    /* GLOBAL */    
    populateDefaultRailwaySpeedLimits(globalSpeedLimits);    
  }   
  
  /**
   * populate the global defaults for highway/railway types
   * 
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults globalSpeedLimits = new OsmSpeedLimitDefaults();
    populateGlobalDefaultHighwaySpeedLimits(globalSpeedLimits);
    populateGlobalDefaultRailwaySpeedLimits(globalSpeedLimits);
    setGlobalDefaults(globalSpeedLimits);
  }   
  
  /**
   * populate the defaults for Australia
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults ausSpeedLimitDefaults = new OsmSpeedLimitDefaults(CountryNames.AUSTRALIA);
    
    /* AUSTRALIA */ 
    {
      /*        HIGHWAY -->                                                           URBAN,  NON_URBAN */
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.MOTORWAY,         100, 100);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.MOTORWAY_LINK,    80,  80);    
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.TRUNK,            60,  100);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.TRUNK_LINK,       60,  60);    
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.PRIMARY,          60,  80);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.PRIMARY_LINK,     60,  60);    
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.SECONDARY,        60,  80);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.SECONDARY_LINK,   60,  60);    
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.TERTIARY,         50,  80);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.TERTIARY_LINK,    50,  60);    
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.UNCLASSIFIED,     50,  80);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.RESIDENTIAL,      50,  80);
      ausSpeedLimitDefaults.setHighwaySpeedLimitDefault(OsmHighwayTags.LIVING_STREET,    20,  20);
      /*        RAILWAY */
      populateDefaultRailwaySpeedLimits(ausSpeedLimitDefaults); 
      /*        REGISTER */    
      setDefaultsByCountry(ausSpeedLimitDefaults);      
    }
  }
  
  /** TODO: Add more countries *
   *          |
   *          |
   *          V
   */   

  /** collect the speed limit based on the highway type, e.g. highway=type, inside an urban area. Speed limit is collected based on the chosen country. If either the country
   * is not defined or the highway type is not available on the country's defaults, the global defaults will be used
   * 
   * @param type highway type value
   * @return speed limit inside urban area for this type
   * @throws PlanItException thrown if error
   */
  protected static double getSpeedLimitByHighwayType(OsmSpeedLimitDefaults countryDefaults, boolean outside, String type) throws PlanItException {
    /* country limit */
    Double speedLimit = countryDefaults.getHighwaySpeedLimit(type, outside);
    if(speedLimit == null) {
      /* global limit */
      speedLimit = globalDefaultSpeedLimits.getHighwaySpeedLimit(type, outside);
    }
    
    if(speedLimit==null) {
      throw new PlanItException(String.format("unable to find speed limit for highway=%s (urban area=%s)",type, Boolean.toString(outside)));
    }
    
    return  speedLimit;
  }
    
  /**
   * register speed limits for a specific country
   * 
   * @param countryName full country name (for english Locale) 
   * @param countrySpeedLimits speed limits by highway type
   * @throws PlanItException thrown if error
   */
  protected static void setDefaultsByCountry(OsmSpeedLimitDefaults countrySpeedLimits) throws PlanItException {
    String iso2Australia = LocaleUtils.getIso2CountryCodeByName(countrySpeedLimits.getCountry());    
    PlanItException.throwIfNull(iso2Australia, "country name could not be converted into ISO2 code");
    
    try {
      speedLimitDefaultsByCountryCode.put(iso2Australia, countrySpeedLimits.clone());
    } catch (CloneNotSupportedException e) {
      throw new PlanItException("unable to clone passed in country specific speed limit defaults", e);
    }
  }
  
  /**
   * set/overwrite  the global defaults
   * @param globalSpeedLimits speed limits by highway type 
   * @throws PlanItException throw if error
   */
  protected static void setGlobalDefaults(OsmSpeedLimitDefaults globalSpeedLimits) throws PlanItException {
    try {
      globalDefaultSpeedLimits = globalSpeedLimits.clone();
    } catch (CloneNotSupportedException e) {
      throw new PlanItException("unable to clone passed in global speed limit defaults", e);
    }
  }  
  
  /** collect the (original) speed limit defaults (outside,inside urban areas) for a given country name
   * 
   * @param countryName to collect for
   * @return speed limits, null if not presently available
   */
  protected static OsmSpeedLimitDefaults getDefaultsByCountryName(String countryName) {    
    return getDefaultsByCountryISO2(LocaleUtils.getIso2CountryCodeByName(countryName));
  }
   
  /** collect the (original) speed limit defaults (outside,inside urban areas) for a given country ISO2 code
   * 
   * @param countryISO2 to collect for
   * @return speed limits, null if not presently available
   */
  protected static OsmSpeedLimitDefaults getDefaultsByCountryISO2(String countryISO2) {    
    return speedLimitDefaultsByCountryCode.get(countryISO2);
  }  
  
  /**
   * Factory method to create global defaults only. 
   * 
   */
  public static OsmSpeedLimitDefaults create() {
    OsmSpeedLimitDefaults createdDefaults = null;
    try {
      createdDefaults = globalDefaultSpeedLimits.clone();
    } catch (CloneNotSupportedException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to clone global speed limit defaults");
    }
    return createdDefaults;
  }  
  
  /**
   * Factory method to create speed limits for a particular country. It will utilise this country's defaults. If not available, or particular road type's are not available
   * it will revert to the globally set defaults 
   * 
   * @param countryName
   */
  public static OsmSpeedLimitDefaults create(String countryName) {
    OsmSpeedLimitDefaults createdDefaults = null;
    /* clone defaults so they can be adjusted without impacting actual defaults */
    try {
      
      if(countryName != null && !countryName.isBlank()) {
        createdDefaults = getDefaultsByCountryName(countryName).clone();
      }
      
      if(createdDefaults==null) {
        createdDefaults = globalDefaultSpeedLimits.clone(); 
        LOGGER.warning(String.format("No OSM speed limit defaults available for country %s, reverting to global defaults",countryName));
      }
      
    } catch (CloneNotSupportedException e) {
      LOGGER.severe(e.getMessage());      
      LOGGER.severe(String.format("Unable to copy default speed limits for indicated country %s",countryName));
    }
    
    return createdDefaults;
  }  
  
  /** collect the (original) speed limit defaults (outside,inside urban areas) globally (non-country specific)
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaults getGlobalDefaults() {    
    return globalDefaultSpeedLimits;
  }    
       
  
}

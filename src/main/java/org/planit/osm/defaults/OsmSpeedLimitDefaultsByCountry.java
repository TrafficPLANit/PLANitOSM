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
  
  /** store the global railway defaults as fall back option */
  protected static OsmSpeedLimitDefaults globalSpeedLimits = new OsmSpeedLimitDefaults(CountryNames.GLOBAL);
      
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
  
  /** set global defaults for highways
   * @param type of highway
   * @param urbanSpeedLimit urban limit
   * @param nonUrbanSpeedLimit non-urnam limit
   */
  protected static void setGlobalHighwaySpeedLimitDefaults(String type, double urbanSpeedLimit, double nonUrbanSpeedLimit) {
    globalSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(type, urbanSpeedLimit);
    globalSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(type, nonUrbanSpeedLimit);
  }
  
  /** set global defaults for railways
   * @param type of railway
   * @param speedLimit to use
   */
  protected static void setGlobalRailwaySpeedLimitDefaults(String type, double speedLimit) {
    globalSpeedLimits.getRailwayDefaults().setSpeedLimitDefault(type, speedLimit);
  }  
             
  /**
   * populate the global defaults for highway types in case the country is not available, or in case the road type for that country is not available
   * 
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalDefaultHighwaySpeedLimits() throws PlanItException {
    /* note that these are physical speed limits for the most unrestricted mode on the highway type, when a mode has a lower
     * speed limit based on the mode, this will be applied, hence it is not needed to reduce the speed for a footway to 5 km/h */
    
    /* GLOBAL -->                                                  URBAN,  NON_URBAN */
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.MOTORWAY,        100,  120);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.MOTORWAY_LINK,   100,  120);    
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.TRUNK,           80,   100);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.TRUNK_LINK,      80,   100);    
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.PRIMARY,         60,   100);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.PRIMARY_LINK,    60,   100);    
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.SECONDARY,       50,   80);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.SECONDARY_LINK,  50,   80);    
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.TERTIARY,        50,   80);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.TERTIARY_LINK,   50,   80);    
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.UNCLASSIFIED,    50,   80);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.RESIDENTIAL,     40,   80);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.LIVING_STREET,   20,   20);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.PEDESTRIAN,      20,   20);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.TRACK,           20,   40);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.ROAD,            20,   40);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.SERVICE,         20,   40);   
    
    /* other less common tags PLANit specific made up defaults, generally low speed limits */
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.FOOTWAY,         20,   20);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.PATH,            20,   20);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.CYCLEWAY,        20,   20);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.STEPS,           10,   10);
    setGlobalHighwaySpeedLimitDefaults(OsmHighwayTags.BRIDLEWAY,       20,   20);
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
  protected static void populateDefaultRailwaySpeedLimits(OsmSpeedLimitDefaultsCategory speedLimitsToPopulate) {
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.FUNICULAR,     OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.LIGHT_RAIL,    OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.MONO_RAIL,     OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.NARROW_GAUGE,  OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.RAIL,          OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.SUBWAY,        OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailWayTags.TRAM,          OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);   
  }  
  
  /**
   * populate the global defaults for railway types in case the country is not available, or in case the railway type for that country is not available.
   * 
   * Currently, we set the {@code OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH} as the default for all modes. This can overridden by the user if required 
   * 
   * 
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalDefaultRailwaySpeedLimits() throws PlanItException {        
    /* GLOBAL */    
    populateDefaultRailwaySpeedLimits(globalSpeedLimits.getRailwayDefaults());    
  }   
  
  /**
   * populate the global defaults for highway/railway types
   * 
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalSpeedLimits() throws PlanItException {
    populateGlobalDefaultHighwaySpeedLimits();
    populateGlobalDefaultRailwaySpeedLimits();
  }   
  
  /**
   * populate the defaults for Australia
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults australianSpeedLimits = new OsmSpeedLimitDefaults(CountryNames.AUSTRALIA, globalSpeedLimits);
    
    /* AUSTRALIA */ 
    {
      /*        HIGHWAY -->                                                                   URBAN */
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.MOTORWAY,         100);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.MOTORWAY_LINK,    80);    
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TRUNK,            60);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TRUNK_LINK,       60);    
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.PRIMARY,          60);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.PRIMARY_LINK,     60);    
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.SECONDARY,        60);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.SECONDARY_LINK,   60);    
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TERTIARY,         50);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TERTIARY_LINK,    50);    
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.UNCLASSIFIED,     50);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.RESIDENTIAL,      50);
      australianSpeedLimits.getUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.LIVING_STREET,    20);
      
      /*        HIGHWAY -->                                                                      NON_URBAN */
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.MOTORWAY,         100);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.MOTORWAY_LINK,    80);    
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TRUNK,            100);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TRUNK_LINK,       60);    
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.PRIMARY,          80);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.PRIMARY_LINK,     60);    
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.SECONDARY,        80);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.SECONDARY_LINK,   60);    
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TERTIARY,         80);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.TERTIARY_LINK,    60);    
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.UNCLASSIFIED,     80);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.RESIDENTIAL,      80);
      australianSpeedLimits.getNonUrbanHighwayDefaults().setSpeedLimitDefault(OsmHighwayTags.LIVING_STREET,    20);
      
      /*        RAILWAY */
      populateDefaultRailwaySpeedLimits(australianSpeedLimits.getRailwayDefaults()); 
      
      /*        REGISTER */
      setDefaultsByCountry(australianSpeedLimits);      
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
    Double speedLimit = outside ? countryDefaults.getNonUrbanHighwayDefaults().getSpeedLimit(type) : countryDefaults.getUrbanHighwayDefaults().getSpeedLimit(type); 
    if(speedLimit == null) {
      /* global limit */
      speedLimit = outside ? globalSpeedLimits.getNonUrbanHighwayDefaults().getSpeedLimit(type) : globalSpeedLimits.getUrbanHighwayDefaults().getSpeedLimit(type);
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
    
    speedLimitDefaultsByCountryCode.put(iso2Australia, countrySpeedLimits.clone());
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
    createdDefaults = globalSpeedLimits.clone();
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
    if(countryName != null && !countryName.isBlank()) {
      createdDefaults = getDefaultsByCountryName(countryName).clone();
    }
    
    if(createdDefaults==null) {
      createdDefaults =create(); 
      LOGGER.warning(String.format("No OSM speed limit defaults available for country %s, reverting to global defaults",countryName));
    }
    
    return createdDefaults;
  }  
  
  /** collect the (original) highway-urban speed limit defaults 
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaultsCategory getGlobalUrbanHighwayDefaults() {    
    return globalSpeedLimits.getUrbanHighwayDefaults();
  }
  
  /** collect the (original) highway-non-urban speed limit defaults 
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaultsCategory getGlobalNonUrbanHighwayDefaults() {    
    return globalSpeedLimits.getNonUrbanHighwayDefaults();
  }
  
  /** collect the (original) railway speed limit defaults 
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaultsCategory getGlobalRailwayDefaults() {    
    return globalSpeedLimits.getRailwayDefaults();
  }        
       
  
}

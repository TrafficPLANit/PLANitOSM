package org.goplanit.osm.defaults;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVRecord;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.locale.LocaleUtils;
import org.goplanit.utils.misc.Pair;

/**
 * Convenience class for quickly collecting the speed limits for various countries, where possible
 * based on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed (highway types)
 * <p>
 * Note that a distinction is made between inside or outside a place, which refers to the road being
 * inside or outside an urban area. this significantly impacts the speed limit.
 * </p><p>
 * Unfortunately, the link cannot judge if it is inside or outside. So, we leave it to the user
 * of this class to indicate which of the two values should be used (if there are two available).
 * </p><p>
 * Note 1: railway speed limits are not known in OSM, so we use a global default for all types, which the user
 * may override if required. Also no distinction is made between inside or outside an urban area.
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
  
  /** reference to the resource dir where we store the country specific speed limit defaults */
  private static final String SPEED_LIMIT_RESOURCE_DIR = "speed_limit";
  
  /** reference to the resource dir where we store the country specific highway speed limit defaults (requires forward slash due to being a resource/URI driven path)*/
  private static final String SPEED_LIMIT_HIGHWAY_RESOURCE_DIR = SPEED_LIMIT_RESOURCE_DIR.concat("/highway");
  
  /** reference to the resource dir where we store the country specific railway speed limit defaults (requires forward slash due to being a resource/URI driven path)*/
  private static final String SPEED_LIMIT_RAILWAY_RESOURCE_DIR = SPEED_LIMIT_RESOURCE_DIR.concat("/railway");
  
  /** store the global railway defaults as fall back option */
  protected static final OsmSpeedLimitDefaults GLOBAL_SPEED_LIMIT_DEFAULTS = new OsmSpeedLimitDefaults(CountryNames.GLOBAL);
      
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmSpeedLimitDefaults> speedLimitDefaultsByCountryCode = new HashMap<String, OsmSpeedLimitDefaults>();
    
  
  /* initialise */
  static {    
    try {
      /* global (hard coded) */
      populateGlobalSpeedLimits();
    
      /* country specific (file based) */
      populateCountrySpecificSpeedLimits();
      
    }catch (PlanItException e) {
      LOGGER.severe("unable to initialise global and/or country specific OSM speed limit defaults");
    }    
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
   * Populate the country specific defaults for highway/railway types for supported countries
   * 
   * @throws PlanItException thrown if error
   */  
  protected static void populateCountrySpecificSpeedLimits() throws PlanItException {
    /* delegate so we call the country specific parser wof each file in the speed limit highway dir */
    CountrySpecificDefaultUtils.callForEachFileInResourceDir(
        SPEED_LIMIT_RAILWAY_RESOURCE_DIR, OsmSpeedLimitDefaultsByCountry::populateCountrySpecificRailwayDefaultSpeedLimits);
    CountrySpecificDefaultUtils.callForEachFileInResourceDir(
        SPEED_LIMIT_HIGHWAY_RESOURCE_DIR, OsmSpeedLimitDefaultsByCountry::populateCountrySpecificHighwayDefaultSpeedLimits);   
  }  
  
  /** The speed limit defaults are parsed as CSV format and overwrite the global defaults for this country. 
   * If no explicit value is provided, we revert to the global defaults instead.
   * 
   * @param inputReader to extract speed limit defaults from
   * @param fullCountryName these defaults relate to
   */
  protected static void populateCountrySpecificRailwayDefaultSpeedLimits(InputStreamReader inputReader, String fullCountryName){  
    try {            
      /* copy the global defaults and make adjustments */
      boolean defaultsNotYetRegistered = false;
      OsmSpeedLimitDefaults countryDefaults = getDefaultsByCountryName(fullCountryName);
      if(countryDefaults==null) {
        countryDefaults = GLOBAL_SPEED_LIMIT_DEFAULTS.clone();
        countryDefaults.setCountry(fullCountryName);
        defaultsNotYetRegistered = true;
      }            
      
      /* railway defaults csv rows */
      Map<String, Double> updatedSpeedLimits = new TreeMap<String,Double>();
      Iterable<CSVRecord> records = CountrySpecificDefaultUtils.collectCsvRecordIterable(inputReader);      
      for(CSVRecord record : records) {
        /* HEADER: OSM railway type | speed limit */
        if(record.size() != 2) {
          LOGGER.warning(String.format("DISCARD: Csv record row in railway speed limit defaults should have two columns, found %s",record.toString()));
          continue;
        }
        String osmRailwayType = record.get(0).trim();
        if(!OsmRailwayTags.isRailBasedRailway(osmRailwayType)) {
          LOGGER.warning(String.format("DISCARD: Csv record row in railway speed limit defaults should have first column reflect a valid railway value type, found %s",osmRailwayType));
          continue;
        }
        
        /* speed limit */
        try {
          updatedSpeedLimits.put(osmRailwayType, Double.parseDouble(record.get(1).trim()));
        }catch(NumberFormatException e) {
          LOGGER.warning(String.format("Invalid speed limit found for railway %s", osmRailwayType));
        }
      }
      
      /* when not only railway defaults are new, but no speed limit defaults for this country have been registered at all
       * then register them, and hereby also registering the railway defaults within them */
      if(!updatedSpeedLimits.isEmpty()) {
        OsmSpeedLimitDefaultsCategory countryRailwayDefaults = countryDefaults.getRailwayDefaults();        
        updatedSpeedLimits.entrySet().stream().forEach(entry -> countryRailwayDefaults.setSpeedLimitDefault(entry.getKey(), entry.getValue()));
        
        if(defaultsNotYetRegistered) {
          setDefaultsByCountry(countryDefaults);
        }
      }
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Parsing of csv input stream with railway speed limit defaults failed for %s", fullCountryName));
    }
  }
  
  /** The speed limit defaults are parsed as CSV format and overwrite the global defaults for this country. 
   * If no explicit value is provided, we revert to the global defaults instead.
   * 
   * @param inputReader to extract speed limit defaults from
   * @param fullCountryName these defaults relate to
   */
  protected static void populateCountrySpecificHighwayDefaultSpeedLimits(InputStreamReader inputReader, String fullCountryName ){  
    try {      
      
      /* copy the global defaults and make adjustments */
      boolean defaultsNotYetRegistered = false;
      OsmSpeedLimitDefaults countryDefaults = getDefaultsByCountryName(fullCountryName);
      if(countryDefaults==null) {
        countryDefaults = GLOBAL_SPEED_LIMIT_DEFAULTS.clone();
        countryDefaults.setCountry(fullCountryName);
        defaultsNotYetRegistered = true;
      }       
      
      /* highway defaults csv rows */
      Map<String, Pair<Double,Double>> updatedSpeedLimits = new TreeMap<String,Pair<Double,Double>>();
      Iterable<CSVRecord> records = CountrySpecificDefaultUtils.collectCsvRecordIterable(inputReader);            
      for(CSVRecord record : records) {
        /* HEADER: OSM highway way type | urban speed limit | non-urban speed limit */
        if(record.size() != 3) {
          LOGGER.warning(String.format("DISCARD: Csv record row in highway speed limit defaults should have three columns, found %s",record.toString()));
          continue;
        }
        String osmHighwayType = record.get(0).trim();
        if(!OsmHighwayTags.isRoadBasedHighwayValueTag(osmHighwayType)) {
          LOGGER.warning(String.format("DISCARD: Csv record row in highway speed limit defaults should have first column reflect a valid highway value type, found %s",record.get(0)));
          continue;
        }
        
        /* speed limit */
        try {
          updatedSpeedLimits.put(osmHighwayType, Pair.of( Double.parseDouble(record.get(1).trim()), Double.parseDouble(record.get(2).trim())));
        }catch(NumberFormatException e) {
          LOGGER.warning(String.format("Invalid speed limit found for highway %s", osmHighwayType));
        }
      }
      
      /* when not only railway defaults are new, but no speed limit defaults for this country have been registered at all
       * then register them, and hereby also registering the railway defaults within them */
      if(!updatedSpeedLimits.isEmpty()) {
        OsmSpeedLimitDefaultsCategory countryHighwayUrbanDefaults = countryDefaults.getUrbanHighwayDefaults();
        OsmSpeedLimitDefaultsCategory countryHighwayNonUrbanDefaults = countryDefaults.getNonUrbanHighwayDefaults();
        updatedSpeedLimits.entrySet().stream().forEach(entry -> countryHighwayUrbanDefaults.setSpeedLimitDefault(entry.getKey(), entry.getValue().first()));
        updatedSpeedLimits.entrySet().stream().forEach(entry -> countryHighwayNonUrbanDefaults.setSpeedLimitDefault(entry.getKey(), entry.getValue().second()));
        
        if(defaultsNotYetRegistered) {
          setDefaultsByCountry(countryDefaults);
        }
      }        
      
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Parsing of csv input stream with highway speed limit defaults failed for %s", fullCountryName));
    }
  }  
  
  /** Set global defaults for highways
   * 
   * @param type of highway
   * @param urbanSpeedLimit urban limit
   * @param nonUrbanSpeedLimit non-urban limit
   */
  protected static void setGlobalHighwaySpeedLimitDefaults(String type, double urbanSpeedLimit, double nonUrbanSpeedLimit) {
    GLOBAL_SPEED_LIMIT_DEFAULTS.getUrbanHighwayDefaults().setSpeedLimitDefault(type, urbanSpeedLimit);
    GLOBAL_SPEED_LIMIT_DEFAULTS.getNonUrbanHighwayDefaults().setSpeedLimitDefault(type, nonUrbanSpeedLimit);
  }
  
  /** Set global defaults for railways
   * 
   * @param type of railway
   * @param speedLimit to use
   */
  protected static void setGlobalRailwaySpeedLimitDefaults(String type, double speedLimit) {
    GLOBAL_SPEED_LIMIT_DEFAULTS.getRailwayDefaults().setSpeedLimitDefault(type, speedLimit);
  }  
             
  /**
   * Populate the global defaults for highway types in case the country is not available, or in case the road type for that country is not available
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
   * Populate the defaults for railway types for given country (or global).
   * Currently, we set the {@code OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH} as the default for all modes. This can overridden by the user if required 
   * 
   * @param speedLimitsToPopulate to populate
   */  
  protected static void populateDefaultRailwaySpeedLimits(OsmSpeedLimitDefaultsCategory speedLimitsToPopulate) {
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.FUNICULAR,     OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.LIGHT_RAIL,    OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.MONO_RAIL,     OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.NARROW_GAUGE,  OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.RAIL,          OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.SUBWAY,        OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);    
    speedLimitsToPopulate.setSpeedLimitDefault(OsmRailwayTags.TRAM,          OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH);   
  }  
  
  /**
   * populate the global defaults for railway types in case the country is not available, or in case the railway type for that country is not available.
   * 
   * Currently, we set the {@code OsmSpeedLimitDefaults.GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH} as the default for all modes. This can overridden by the user if required 
   * 
   * @throws PlanItException thrown if error
   */
  protected static void populateGlobalDefaultRailwaySpeedLimits() throws PlanItException {        
    /* GLOBAL */    
    populateDefaultRailwaySpeedLimits(GLOBAL_SPEED_LIMIT_DEFAULTS.getRailwayDefaults());    
  }     
  
  /**
   * Populate the defaults for Australia
   * 
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults australianSpeedLimits = new OsmSpeedLimitDefaults(CountryNames.AUSTRALIA, GLOBAL_SPEED_LIMIT_DEFAULTS);
    
    /* TODO: To be replaced by defaults from file (already added, just parsing not -> 
     * base on parsing for mode access defaults), where if one of the two exists (highway/railway) both need configuring
     * by cloning and then overwriting for each available record!
     */
    
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
  
  /** Collect the speed limit based on the highway type, e.g. highway=type, inside an urban area. Speed limit is collected based on the chosen country. If either the country
   * is not defined or the highway type is not available on the country's defaults, the global defaults will be used
   * 
   * @param countryDefaults to use
   * @param outside indicates to use urban or non-urban defaults
   * @param type highway type value
   * @return speed limit inside urban area for this type
   * @throws PlanItException thrown if error
   */
  protected static double getSpeedLimitByHighwayType(OsmSpeedLimitDefaults countryDefaults, boolean outside, String type) throws PlanItException {
    /* country limit */
    Double speedLimit = outside ? countryDefaults.getNonUrbanHighwayDefaults().getSpeedLimit(type) : countryDefaults.getUrbanHighwayDefaults().getSpeedLimit(type); 
    if(speedLimit == null) {
      /* global limit */
      speedLimit = outside ? GLOBAL_SPEED_LIMIT_DEFAULTS.getNonUrbanHighwayDefaults().getSpeedLimit(type) : GLOBAL_SPEED_LIMIT_DEFAULTS.getUrbanHighwayDefaults().getSpeedLimit(type);
    }
    
    if(speedLimit==null) {
      throw new PlanItException(String.format("unable to find speed limit for highway=%s (urban area=%s)",type, Boolean.toString(outside)));
    }
    
    return  speedLimit;
  }
    
  /**
   * Register speed limits for a specific country
   * 
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
   * @return created defaults
   */
  public static OsmSpeedLimitDefaults create() {
    OsmSpeedLimitDefaults createdDefaults = null;
    createdDefaults = GLOBAL_SPEED_LIMIT_DEFAULTS.clone();
    return createdDefaults;
  }  
  
  /**
   * Factory method to create speed limits for a particular country. It will utilise this country's defaults. If not available, or particular road type's are not available
   * it will revert to the globally set defaults 
   * 
   * @param countryName to use
   * @return created defaults
   */
  public static OsmSpeedLimitDefaults create(String countryName) {    
    OsmSpeedLimitDefaults createdDefaults = null;
    if(countryName != null && !countryName.isBlank() && !countryName.equals(CountryNames.GLOBAL)) {
      createdDefaults = getDefaultsByCountryName(countryName);
    }
    if(createdDefaults==null) {
      createdDefaults = create(); 
      LOGGER.warning(String.format("No OSM speed limit defaults available for %s, reverting to global defaults",countryName));
    }else {
      /* make a copy so true defaults are not changed if user makes changes for project */
      createdDefaults = createdDefaults.clone(); 
    }        
    
    return createdDefaults;
  }  
  
  /** collect the (original) highway-urban speed limit defaults 
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaultsCategory getGlobalUrbanHighwayDefaults() {    
    return GLOBAL_SPEED_LIMIT_DEFAULTS.getUrbanHighwayDefaults();
  }
  
  /** collect the (original) highway-non-urban speed limit defaults 
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaultsCategory getGlobalNonUrbanHighwayDefaults() {    
    return GLOBAL_SPEED_LIMIT_DEFAULTS.getNonUrbanHighwayDefaults();
  }
  
  /** collect the (original) railway speed limit defaults 
   * 
   * @return global speed limit defaults
   */
  public static OsmSpeedLimitDefaultsCategory getGlobalRailwayDefaults() {    
    return GLOBAL_SPEED_LIMIT_DEFAULTS.getRailwayDefaults();
  }        
       
  
}

package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;

/**
 * Convenience class for quickly collecting the speed limits for various countries, where possible
 * based on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed.
 * 
 * Note that a distinction is made between inside or outside a place, which refers to the road being
 * inside or outside an urban area. this signficantly impacts the speed limit.
 * 
 * Unfortunately, the link cannot judge if it is inside or outside. So, we leave it to the user
 * of this class to indicate which of the two values should be used (if there are two available).
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaultsByCountry {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmSpeedLimitDefaultsByCountry.class.getCanonicalName());  
  
  /* initialise */
  static {    
    try {
      TODO --> ADD RAILWAY DEFAULTS!
      
      populateDefaultSpeedLimits();
    
      populateAustralianSpeedLimits();
      
    }catch (PlanItException e) {
      LOGGER.severe("unable to initialise global and/or country specific OSM speed limit defaults");
    }    
  }  
     
  /** store the global defaults as fall back option */
  protected static OsmSpeedLimitDefaults defaultSpeedLimits;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmSpeedLimitDefaults> speedLimitDefaultsByCountry = new HashMap<String, OsmSpeedLimitDefaults>();
        
  /**
   * populate the global defaults in case the country is not available, or in case the road type for that country is not available
   * @throws PlanItException thrown if error
   */
  protected static void populateDefaultSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults globalSpeedLimits = new OsmSpeedLimitDefaults();
    
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
    
    setGlobalDefaults(globalSpeedLimits);
  }  
  
  /**
   * populate the defaults for Australia
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults ausSpeedLimitDefaults = new OsmSpeedLimitDefaults();
    
    /* AUSTRALIA  -->                                           URBAN,  NON_URBAN */
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
        
    setDefaultsByCountry("Australia", ausSpeedLimitDefaults);
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
      speedLimit = defaultSpeedLimits.getHighwaySpeedLimit(type, outside);
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
  protected static void setDefaultsByCountry(String countryName, OsmSpeedLimitDefaults countrySpeedLimits) throws PlanItException {
    String iso2Australia = LocaleUtils.getIso2CountryCodeByName(countryName);    
    PlanItException.throwIfNull(iso2Australia, "country name could not be converted into ISO2 code");
    
    try {
      speedLimitDefaultsByCountry.put(iso2Australia, countrySpeedLimits.clone());
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
      defaultSpeedLimits = globalSpeedLimits.clone();
    } catch (CloneNotSupportedException e) {
      throw new PlanItException("unable to clone passed in global speed limit defaults", e);
    }
  }  
  
  /** collect the (original) speed limit defaults (outside,inside urban areas) for a given country name
   * 
   * @param countryName to collect for
   * @return speed limits, null if not presently available
   */
  protected static OsmSpeedLimitDefaults getDefaultsByCountry(String countryName) {
    return speedLimitDefaultsByCountry.get(countryName);
  }
  
  /**
   * Factory method to create global defaults only. 
   * 
   */
  public static OsmSpeedLimitDefaults create() {
    OsmSpeedLimitDefaults createdDefaults = null;
    try {
      createdDefaults = defaultSpeedLimits.clone();
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
      createdDefaults = getDefaultsByCountry(countryName).clone();
      
      if(createdDefaults==null) {
        createdDefaults = defaultSpeedLimits.clone(); 
        LOGGER.warning(String.format("No OSM speed limit defaults available for country %s, reverting to global defaults",countryName));
      }
      
    } catch (CloneNotSupportedException e) {
      LOGGER.severe(e.getMessage());      
      LOGGER.severe(String.format("Unable to copy default speed limits for indicated country %s",countryName));
    }
    
    return createdDefaults;
  }  
     
  
}

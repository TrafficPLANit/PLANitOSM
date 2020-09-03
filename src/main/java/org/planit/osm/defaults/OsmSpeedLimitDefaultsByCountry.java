package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;
import org.planit.utils.misc.Pair;

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
  
  /** store the global defaults as fall back option */
  protected static Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults> defaultSpeedLimits;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>> speedLimitDefaultsByCountry = new HashMap<String,Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>>();
      
  /* initialise */
  static {    
    try {
      populateDefaultSpeedLimits();
    
      populateAustralianSpeedLimits();
      
    }catch (PlanItException e) {
      LOGGER.severe("unable to initialise global and/or country specific OSM speed limit defaults");
    }    
  }
  
  /**
   * populate the global defaults in case the country is not available, or in case the road type for that country is not available
   */
  protected static void populateDefaultSpeedLimits() {
    OsmSpeedLimitDefaults globalOutside = new OsmSpeedLimitDefaults();
    OsmSpeedLimitDefaults globalInside = new OsmSpeedLimitDefaults();
    
    /* OUTSIDE */
    globalOutside.setSpeedDefault(OsmHighwayTags.MOTORWAY,        120);
    globalOutside.setSpeedDefault(OsmHighwayTags.MOTORWAY_LINK,   120);    
    globalOutside.setSpeedDefault(OsmHighwayTags.TRUNK,           100);
    globalOutside.setSpeedDefault(OsmHighwayTags.TRUNK_LINK,      100);    
    globalOutside.setSpeedDefault(OsmHighwayTags.PRIMARY,         100);
    globalOutside.setSpeedDefault(OsmHighwayTags.PRIMARY_LINK,    100);    
    globalOutside.setSpeedDefault(OsmHighwayTags.SECONDARY,       80);
    globalOutside.setSpeedDefault(OsmHighwayTags.SECONDARY_LINK,  80);    
    globalOutside.setSpeedDefault(OsmHighwayTags.TERTIARY,        80);
    globalOutside.setSpeedDefault(OsmHighwayTags.TERTIARY_LINK,   80);    
    globalOutside.setSpeedDefault(OsmHighwayTags.UNCLASSIFIED,    80);
    globalOutside.setSpeedDefault(OsmHighwayTags.RESIDENTIAL,     80);
    globalOutside.setSpeedDefault(OsmHighwayTags.LIVING_STREET,   20);
    globalOutside.setSpeedDefault(OsmHighwayTags.PEDESTRIAN,      20);
    globalOutside.setSpeedDefault(OsmHighwayTags.TRACK,           40);
    globalOutside.setSpeedDefault(OsmHighwayTags.ROAD,            40);
    globalOutside.setSpeedDefault(OsmHighwayTags.SERVICE,         40);
    
    /* INSIDE */
    globalInside.setSpeedDefault(OsmHighwayTags.MOTORWAY,         100);
    globalInside.setSpeedDefault(OsmHighwayTags.MOTORWAY_LINK,    100);    
    globalInside.setSpeedDefault(OsmHighwayTags.TRUNK,            80);
    globalInside.setSpeedDefault(OsmHighwayTags.TRUNK_LINK,       80);    
    globalInside.setSpeedDefault(OsmHighwayTags.PRIMARY,          60);
    globalInside.setSpeedDefault(OsmHighwayTags.PRIMARY_LINK,     60);    
    globalInside.setSpeedDefault(OsmHighwayTags.SECONDARY,        50);
    globalInside.setSpeedDefault(OsmHighwayTags.SECONDARY_LINK,   50);    
    globalInside.setSpeedDefault(OsmHighwayTags.TERTIARY,         50);
    globalInside.setSpeedDefault(OsmHighwayTags.TERTIARY_LINK,    50);    
    globalInside.setSpeedDefault(OsmHighwayTags.UNCLASSIFIED,     50);
    globalInside.setSpeedDefault(OsmHighwayTags.RESIDENTIAL,      40);
    globalInside.setSpeedDefault(OsmHighwayTags.LIVING_STREET,    20);
    globalInside.setSpeedDefault(OsmHighwayTags.PEDESTRIAN,       20);
    globalInside.setSpeedDefault(OsmHighwayTags.TRACK,            20);
    globalInside.setSpeedDefault(OsmHighwayTags.ROAD,             20);
    globalInside.setSpeedDefault(OsmHighwayTags.SERVICE,          20);    
    
    setGlobalDefaults(globalOutside, globalInside);
  }  
  
  /**
   * populate the defaults for Australia
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianSpeedLimits() throws PlanItException {
    OsmSpeedLimitDefaults ausOutside = new OsmSpeedLimitDefaults();
    OsmSpeedLimitDefaults ausInside = new OsmSpeedLimitDefaults();
    
    /* AUS - OUTSIDE */
    ausOutside.setSpeedDefault(OsmHighwayTags.MOTORWAY,         100);
    ausOutside.setSpeedDefault(OsmHighwayTags.MOTORWAY_LINK,    80);    
    ausOutside.setSpeedDefault(OsmHighwayTags.TRUNK,            100);
    ausOutside.setSpeedDefault(OsmHighwayTags.TRUNK_LINK,       60);    
    ausOutside.setSpeedDefault(OsmHighwayTags.PRIMARY,          80);
    ausOutside.setSpeedDefault(OsmHighwayTags.PRIMARY_LINK,     60);    
    ausOutside.setSpeedDefault(OsmHighwayTags.SECONDARY,        80);
    ausOutside.setSpeedDefault(OsmHighwayTags.SECONDARY_LINK,   60);    
    ausOutside.setSpeedDefault(OsmHighwayTags.TERTIARY,         80);
    ausOutside.setSpeedDefault(OsmHighwayTags.TERTIARY_LINK,    60);    
    ausOutside.setSpeedDefault(OsmHighwayTags.UNCLASSIFIED,     80);
    ausOutside.setSpeedDefault(OsmHighwayTags.RESIDENTIAL,      80);
    ausOutside.setSpeedDefault(OsmHighwayTags.LIVING_STREET,    20);
    
    /* AUS - INSIDE */
    ausInside.setSpeedDefault(OsmHighwayTags.MOTORWAY,         100);
    ausInside.setSpeedDefault(OsmHighwayTags.MOTORWAY_LINK,    80);    
    ausInside.setSpeedDefault(OsmHighwayTags.TRUNK,            60);
    ausInside.setSpeedDefault(OsmHighwayTags.TRUNK_LINK,       60);    
    ausInside.setSpeedDefault(OsmHighwayTags.PRIMARY,          60);
    ausInside.setSpeedDefault(OsmHighwayTags.PRIMARY_LINK,     60);    
    ausInside.setSpeedDefault(OsmHighwayTags.SECONDARY,        60);
    ausInside.setSpeedDefault(OsmHighwayTags.SECONDARY_LINK,   60);    
    ausInside.setSpeedDefault(OsmHighwayTags.TERTIARY,         50);
    ausInside.setSpeedDefault(OsmHighwayTags.TERTIARY_LINK,    50);    
    ausInside.setSpeedDefault(OsmHighwayTags.UNCLASSIFIED,     50);
    ausInside.setSpeedDefault(OsmHighwayTags.RESIDENTIAL,      50);
    ausInside.setSpeedDefault(OsmHighwayTags.LIVING_STREET,    20);
    
    setDefaultsByCountry("Australia", ausOutside, ausInside);
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
    Double speedLimit = countryDefaults.getSpeedLimit(type);
    if(speedLimit == null) {
      /* global limit */
      OsmSpeedLimitDefaults globalDefaults = outside ? defaultSpeedLimits.getFirst() : defaultSpeedLimits.getSecond();
      speedLimit = globalDefaults.getSpeedLimit(type);
    }
    
    if(speedLimit==null) {
      throw new PlanItException(String.format("unable to find speed limit for highway=%s (urban area=%s)",type, Boolean.toString(outside)));
    }
    
    return speedLimit;
  }
  
  /** chosen country for instance of this class */
  protected String currentCountry;
  
  /** speed limits applicable for instance of this class based on current country */
  protected Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults> currentCountrySpeedLimits;


  /**
   * register speed limits for a specific country
   * 
   * @param countryName full country name (for english Locale) 
   * @param outsideSpeedLimits speed limits for outside urban areas by highway type
   * @param insideSpeedLimimits speed limits inside urban areas by highway type
   * @throws PlanItException thrown if error
   */
  protected static void setDefaultsByCountry(String countryName, OsmSpeedLimitDefaults outsideSpeedLimits, OsmSpeedLimitDefaults insideSpeedLimimits) throws PlanItException {
    String iso2Australia = LocaleUtils.getIso2CountryCodeByName(countryName);    
    PlanItException.throwIfNull(iso2Australia, "country name could not be converted into ISO2 code");
    speedLimitDefaultsByCountry.put(iso2Australia, new Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>(outsideSpeedLimits,insideSpeedLimimits));
  }
  
  /**
   * set/overwrite  the global defaults
   * @param outsideSpeedLimits speed limits for outside urban areas by highway type 
   * @param insideSpeedLimimits speed limits inside urban areas by highway type
   */
  protected static void setGlobalDefaults(OsmSpeedLimitDefaults outsideSpeedLimits, OsmSpeedLimitDefaults insideSpeedLimimits) {
    defaultSpeedLimits = new Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>(outsideSpeedLimits, insideSpeedLimimits);
  }  
  
  /** collect the speed limits (outside,inside urban areas) for a given country name
   * 
   * @param countryName
   * @return speed limits (outside,inside urban areas), null if not presently available
   */
  protected static Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults> getDefaultsByCountry(String countryName) {
    return speedLimitDefaultsByCountry.get(countryName);
  }
  
  /**
   * Constructor without a particular country. It will utilise the global defaults only. 
   * 
   */
  public OsmSpeedLimitDefaultsByCountry() {
    this.currentCountrySpeedLimits = defaultSpeedLimits;
  }  
  
  /**
   * Constructor for a particular country. It will utilise this country's defaults. If not available, or particular road type's are not available
   * it will revert to the globally set defaults 
   * 
   * @param countryName
   */
  public OsmSpeedLimitDefaultsByCountry(String countryName) {
    try {
      this.currentCountrySpeedLimits = 
          new Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>(
              getDefaultsByCountry(countryName).getFirst().clone(),
              getDefaultsByCountry(countryName).getSecond().clone());
      
      FIX THIS UP AS WELL -> COPY SO ANY CHANGES DO NOT AFFECT OTHER INSTANCES OF THIS CLASS -> THEN DO THE SAME FOR LANES
      if(currentCountrySpeedLimits==null) {
        this.currentCountrySpeedLimits = defaultSpeedLimits; 
        LOGGER.warning(String.format("No OSM speed limit defaults available for country %s, reverting to global defaults",countryName));
      }
      
    } catch (CloneNotSupportedException e) {
      CONTINUE HERE --> 
      LOGGER.severe("");
    }
  }  
  
  /** collect the speed limit based on the highway type, e.g. highway=type. Speed limit is collected based on the chosen country. If either the country
   * is not defined or the highway type is not available on the country's defaults, the global defaults will be used
   * 
   * @param type highway type value
   * @return speed limit outside and inside urban area for this type
   * @throws PlanItException thrown if error
   */
  public Pair<Double,Double> getSpeedLimitByHighwayType(String type) throws PlanItException{
    return new Pair<Double,Double>(getOutsideSpeedLimitByHighwayType(type),getInsideSpeedLimitByHighwayType(type));
  }
  
  /** collect the speed limit based on the highway type, e.g. highway=type, outside of an urban area. Speed limit is collected based on the chosen country. If either the country
   * is not defined or the highway type is not available on the country's defaults, the global defaults will be used
   * 
   * @param type highway type value
   * @return speed limit outside urban area for this type
   * @throws PlanItException thrown if error
   */
  public Double getOutsideSpeedLimitByHighwayType(String type) throws PlanItException{
    return getSpeedLimitByHighwayType(currentCountrySpeedLimits.getFirst(), true /*outside */, type);
  }  
  
  /** collect the speed limit based on the highway type, e.g. highway=type, inside an urban area. Speed limit is collected based on the chosen country. If either the country
   * is not defined or the highway type is not available on the country's defaults, the global defaults will be used
   * 
   * @param type highway type value
   * @return speed limit inside urban area for this type
   * @throws PlanItException thrown if error
   */
  public Double getInsideSpeedLimitByHighwayType(String type) throws PlanItException{
    return getSpeedLimitByHighwayType(currentCountrySpeedLimits.getSecond(), false /*outside*/, type);
  }    
  
  
}

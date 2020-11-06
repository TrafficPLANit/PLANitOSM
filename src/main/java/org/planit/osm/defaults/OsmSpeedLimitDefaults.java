package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.utils.locale.CountryNames;
import org.planit.utils.misc.Pair;

/**
 * Container class for storing urban/non-urban speed limits for different highway=type/railway=type for OSM.
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaults implements Cloneable {
  
  /** the Logger for this class */
  private static final Logger LOGGER = Logger.getLogger(OsmSpeedLimitDefaults.class.getCanonicalName());
  
  /**
   * store urban highway defaults in this map
   */
  protected final Map<String,Double> highwayUrbanSpeedLimitDefaults;
  
  /**
   * store non-urban highway defaults in this map
   */
  protected final Map<String,Double> highwayNonUrbanSpeedLimitDefaults;
  
  /**
   * store railwat defaults in this map
   */
  protected final Map<String,Double> railwaySpeedLimitDefaults;
  
  /** chosen country for instance of this class */
  protected final String currentCountry;
  
  /** in absence of OSM default, we create a global highway speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_HIGHWAY_SPEEDLIMIT_KMH = 50;  
    
  /** in absence of OSM defined defaults, we make a global rail way speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH = 70;
  
  /**
   * Default constructor
   */
  public OsmSpeedLimitDefaults() {
    this.currentCountry = CountryNames.GLOBAL;
    this.highwayUrbanSpeedLimitDefaults = new HashMap<String,Double>();
    this.highwayNonUrbanSpeedLimitDefaults = new HashMap<String,Double>();
    this.railwaySpeedLimitDefaults = new HashMap<String,Double>();
  }
  
  /**
   * constructor
   * 
   * @param countryName defaults specific to this country
   */
  public OsmSpeedLimitDefaults(String countryName) {
    this.currentCountry = countryName;
    this.highwayUrbanSpeedLimitDefaults = new HashMap<String,Double>();
    this.highwayNonUrbanSpeedLimitDefaults = new HashMap<String,Double>();
    this.railwaySpeedLimitDefaults = new HashMap<String,Double>();
  }  
  
  /**
   * copy constructor
   *  
   * @param other to use
   * 
   */
  public OsmSpeedLimitDefaults(OsmSpeedLimitDefaults other) {
    this.currentCountry = other.currentCountry;
    this.highwayUrbanSpeedLimitDefaults =  new HashMap<String,Double>(other.highwayUrbanSpeedLimitDefaults);
    this.highwayNonUrbanSpeedLimitDefaults = new HashMap<String,Double>(other.highwayNonUrbanSpeedLimitDefaults);
    this.railwaySpeedLimitDefaults = new HashMap<String,Double>(other.railwaySpeedLimitDefaults);
  }
  
  /** set a speed default for a given highway=type
   * 
   * @param type of road to set speed default for
   * @param urbanSpeedLimit the physical speed limit (km/h)
   * @param nonUrbanSpeedLimit the physical speed limit (km/h)
   */
  public void setHighwaySpeedLimitDefault(final String type, double urbanSpeedLimit, double nonUrbanSpeedLimit){
    highwayUrbanSpeedLimitDefaults.put(type, urbanSpeedLimit);
    highwayNonUrbanSpeedLimitDefaults.put(type, urbanSpeedLimit);
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Pair<Double,Double> getHighwaySpeedLimit(String type) {
    return Pair.create(getHighwaySpeedLimit(type, false /* urban */ ), getHighwaySpeedLimit(type, true /* non-urban */ ));
  }
  
  /** set a speed default for a given railway=type
   * 
   * @param type of railway to set speed default for
   */
  public void setRailwaySpeedLimitDefault(final String type, double speedLimit){
    railwaySpeedLimitDefaults.put(type, speedLimit);
  }
  
  /** get a speed limit default for a given railway=type
   * 
   * @param type of road to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Double getRailwaySpeedLimit(String type) {
    Double railWaySpeedLimit = railwaySpeedLimitDefaults.get(type);
    if(railWaySpeedLimit == null) {
      railWaySpeedLimit = OsmSpeedLimitDefaultsByCountry.getGlobalDefaults().getRailwaySpeedLimit(type);
    }        
    return railWaySpeedLimit;
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @param outsideUrbanArea flag indicating outside urban area or not
   * @return the physical speed limit (km/h)
   */
  public Double getHighwaySpeedLimit(String type, boolean outsideUrbanArea) {
    Double speedLimit = outsideUrbanArea ?  highwayNonUrbanSpeedLimitDefaults.get(type) : highwayUrbanSpeedLimitDefaults.get(type);
    if(speedLimit == null) {
      if(OsmSpeedLimitDefaultsByCountry.getGlobalDefaults().containsHighwaySpeedLimit(type, outsideUrbanArea)) {
        speedLimit = OsmSpeedLimitDefaultsByCountry.getGlobalDefaults().getHighwaySpeedLimit(type, outsideUrbanArea);
      }else {
        LOGGER.severe(String.format("unable to obtain default speed limit for highway=%s, reverting to global default %.2f", type, GLOBAL_DEFAULT_HIGHWAY_SPEEDLIMIT_KMH));
        speedLimit = GLOBAL_DEFAULT_HIGHWAY_SPEEDLIMIT_KMH;
      }
    }
    return speedLimit;    
  }
  
  /** verify if a default speed limit is available for the given type
   * @param type to verify
   * @param outsideUrbanArea flag indicating outside urban area or not
   * @return true when available false otherwise
   */
  public boolean containsHighwaySpeedLimit(String type, boolean outsideUrbanArea) {
    return outsideUrbanArea ?  highwayNonUrbanSpeedLimitDefaults.containsKey(type) : highwayUrbanSpeedLimitDefaults.containsKey(type);
  }

  /** Collect the country for which these defaults are meant. When not set "global" is returned
   * @return country set
   */
  public String getCountry() {
    return currentCountry;
  }  
   
  
  /**
   * clone this class instance
   */
  @Override
  public OsmSpeedLimitDefaults clone() throws CloneNotSupportedException {
    return new OsmSpeedLimitDefaults(this);
  }  

}

package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;

import org.planit.utils.misc.Pair;

/**
 * Container class for storing urban/non-urban speed limits for different highway=type/railway=type for OSM.
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaults implements Cloneable {
  
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
  
  /** country name set to this string when global defaults are applied */ 
  public static final String GLOBAL = "global";
  
  /** in absence of OSM defined defaults, we make a global rail way speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH = 70;
  
  /**
   * Default constructor
   */
  public OsmSpeedLimitDefaults() {
    this.currentCountry = GLOBAL;
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
    return new Pair<Double,Double>(highwayUrbanSpeedLimitDefaults.get(type),highwayNonUrbanSpeedLimitDefaults.get(type));
  }
  
  /** set a speed default for a given railway=type
   * 
   * @param type of railway to set speed default for
   */
  public void setRailwaySpeedLimitDefault(final String type, double speedLimit){
    railwaySpeedLimitDefaults.put(type, speedLimit);
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Double getRailwaySpeedLimit(String type) {
    return Double.valueOf(railwaySpeedLimitDefaults.get(type));
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @param outsideUrbanArea flag indicating outside urban area or not
   * @return the physical speed limit (km/h)
   */
  public Double getHighwaySpeedLimit(String type, boolean outsideUrbanArea) {
    return outsideUrbanArea ?  highwayNonUrbanSpeedLimitDefaults.get(type) : highwayUrbanSpeedLimitDefaults.get(type);
  } 
   
  
  /**
   * clone this class instance
   */
  @Override
  public OsmSpeedLimitDefaults clone() throws CloneNotSupportedException {
    return new OsmSpeedLimitDefaults(this);
  }  

}

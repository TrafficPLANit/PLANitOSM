package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;

/**
 * Container class for storing speed limits for different highway=type for OSM.
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaults implements Cloneable {
  
  /**
   * store defaults in this map
   */
  protected final Map<String,Double> speedLimitDefaults;
  
  /**
   * Default constructor
   */
  public OsmSpeedLimitDefaults() {
    this.speedLimitDefaults = new HashMap<String,Double>();
  }
  
  /**
   * constructor 
   * @param defaults to use
   * 
   */
  public OsmSpeedLimitDefaults(Map<String,Double> defaults) {
    this.speedLimitDefaults =  new HashMap<String,Double>(defaults);
  }
  
  /** set a speed default for a given highway=type
   * 
   * @param type of road to set speed default for
   * @param speedLimit the physical speed limit (km/h)
   * @return the previous value associated with key, or null if there was no mapping for key
   */
  public Double setSpeedDefault(String type, double speedLimit){
    return speedLimitDefaults.put(type, speedLimit);
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Double getSpeedLimit(String type) {
    return speedLimitDefaults.get(type);
  }
  
  /**
   * clone this class instance
   */
  @Override
  public OsmSpeedLimitDefaults clone() throws CloneNotSupportedException {
    return new OsmSpeedLimitDefaults(this.speedLimitDefaults);
  }  

}

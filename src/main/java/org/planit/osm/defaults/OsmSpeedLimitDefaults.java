package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;

import org.planit.utils.misc.Pair;

/**
 * Container class for storing urban/non-urban speed limits for different highway=type for OSM.
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaults implements Cloneable {
  
  /**
   * store urban defaults in this map
   */
  protected final Map<String,Double> urbanSpeedLimitDefaults;
  
  /**
   * store non-urban defaults in this map
   */
  protected final Map<String,Double> nonUrbanSpeedLimitDefaults;  
  
  /** chosen country for instance of this class */
  protected final String currentCountry;
  
  /** country name set to this string when global defaults are applied */ 
  public static final String GLOBAL = "global";
  
  /**
   * Default constructor
   */
  public OsmSpeedLimitDefaults() {
    this.currentCountry = GLOBAL;
    this.urbanSpeedLimitDefaults = new HashMap<String,Double>();
    this.nonUrbanSpeedLimitDefaults = new HashMap<String,Double>();
  }
  
  /**
   * copy constructor
   *  
   * @param urbanDefaults to use
   * @param nonUrbanDefaults to use
   * 
   */
  public OsmSpeedLimitDefaults(OsmSpeedLimitDefaults other) {
    this.currentCountry = other.currentCountry;
    this.urbanSpeedLimitDefaults =  new HashMap<String,Double>(other.urbanSpeedLimitDefaults);
    this.nonUrbanSpeedLimitDefaults = new HashMap<String,Double>(other.nonUrbanSpeedLimitDefaults);
  }
  
  /** set a speed default for a given highway=type
   * 
   * @param type of road to set speed default for
   * @param urbanSpeedLimit the physical speed limit (km/h)
   * @param nonUrbanSpeedLimit the physical speed limit (km/h)
   * @return the previous value associated with key, or null if there was no mapping for key
   */
  public void setSpeedLimitDefault(final String type, double urbanSpeedLimit, double nonUrbanSpeedLimit){
    urbanSpeedLimitDefaults.put(type, urbanSpeedLimit);
    nonUrbanSpeedLimitDefaults.put(type, urbanSpeedLimit);
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Pair<Double,Double> getSpeedLimit(String type) {
    return new Pair<Double,Double>(urbanSpeedLimitDefaults.get(type),nonUrbanSpeedLimitDefaults.get(type));
  }
  
  /** get a speed limit default for a given highway=type
   * 
   * @param type of road to get speed default for
   * @param outsideUrbanArea flag indicating outside urban area or not
   * @return the physical speed limit (km/h)
   */
  public Double getSpeedLimit(String type, boolean outsideUrbanArea) {
    return outsideUrbanArea ?  nonUrbanSpeedLimitDefaults.get(type) : urbanSpeedLimitDefaults.get(type);
  }  
  
  /**
   * clone this class instance
   */
  @Override
  public OsmSpeedLimitDefaults clone() throws CloneNotSupportedException {
    return new OsmSpeedLimitDefaults(this);
  }  

}

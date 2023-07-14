package org.goplanit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Container class for storing speed limits for different waytypes for OSM. It is agnostic to if these are highway or railway types, 
 * urban or non-urban, that needs to be handled by the user of this class
 * 
 * @author markr
 *
 */
public class OsmSpeedLimitDefaultsCategory {
  
  /** the Logger for this class */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmSpeedLimitDefaultsCategory.class.getCanonicalName());
  
  /**
   * store OSM way defaults in this map by key,value of the tagging used
   */
  protected final Map<String, Map<String,Double>> speedLimitDefaults;
      
  protected final OsmSpeedLimitDefaultsCategory backupDefaults;
  
  /** country these defaults apply for */
  protected final String countryName;

  /**
   * Copy constructor
   *
   * @param other to use
   * @param deepClone when true deep clone, otherwise shallow close
   *
   */
  protected OsmSpeedLimitDefaultsCategory(OsmSpeedLimitDefaultsCategory other, boolean deepClone) {
    this.backupDefaults = (deepClone && other.backupDefaults!=null) ?
        other.backupDefaults.deepClone() : other.backupDefaults;
    this.countryName = other.countryName;

    this.speedLimitDefaults = new HashMap<>();
    other.speedLimitDefaults.entrySet().stream().forEach( e -> {
      speedLimitDefaults.put(e.getKey(), new HashMap<>(e.getValue()));
    });

  }
    
  /**
   * Constructor. 
   * 
   * @param countryName these apply for
   */
  public OsmSpeedLimitDefaultsCategory(String countryName) {
    this.countryName = countryName;
    this.backupDefaults = null;
    this.speedLimitDefaults = new HashMap<>();
  }   
  
  /**
   * Constructor. It is advised to provide the global defaults depending on whether this is used for rail or highway 
   * 
   * @param countryName these apply for
   * @param backupDefaults to use when no speed limit is defined for a given way type on this instance
   */
  public OsmSpeedLimitDefaultsCategory(String countryName, OsmSpeedLimitDefaultsCategory backupDefaults) {
    this.countryName = countryName;
    this.backupDefaults = backupDefaults;
    this.speedLimitDefaults = new HashMap<>();
  }   

  /** Set a speed default for a given type
   *
   * @param key key for type
   * @param type of the way to set speed default for
   * @param speedLimitKmH the physical speed limit (km/h)
   */
  public void setSpeedLimitDefault(String key, String type, double speedLimitKmH){
    speedLimitDefaults.putIfAbsent(key, new HashMap<>());
    speedLimitDefaults.get(key).put(type, speedLimitKmH);
  }
  
  /** Get a speed limit default for a given way type
   *
   * @param key key for type
   * @param type of way to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Double getSpeedLimit(String key, String type) {
    var speedLimitsForKey = speedLimitDefaults.get(key);
    if (speedLimitsForKey == null){
      return backupDefaults.getSpeedLimit(key, type);
    }

    Double speedLimit = speedLimitsForKey.get(type);
    if(speedLimit == null) {
      speedLimit = backupDefaults.getSpeedLimit(key, type);
    }        
    return speedLimit;
  }
      
  /** verify if a default speed limit is available for the given type
   *
   * @param type to verify
   * @return true when available false otherwise
   */
  public boolean containsSpeedLimit(String type) {
    return speedLimitDefaults.values().stream().anyMatch(e -> e.containsKey(type));
  }
  
  /** collect the country name
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }

   
  
  /**
   * shallow clone this class instance
   *
   * @return shallow copy
   */
  public OsmSpeedLimitDefaultsCategory shallowClone() {
    return new OsmSpeedLimitDefaultsCategory(this, false);
  }

  /**
   * deep clone this class instance
   *
   * @return deep copy
   */
  public OsmSpeedLimitDefaultsCategory deepClone()
  {
    return new OsmSpeedLimitDefaultsCategory(this, true);
  }

}

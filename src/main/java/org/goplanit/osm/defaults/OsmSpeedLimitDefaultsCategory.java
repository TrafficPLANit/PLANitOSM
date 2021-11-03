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
public class OsmSpeedLimitDefaultsCategory implements Cloneable {
  
  /** the Logger for this class */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmSpeedLimitDefaultsCategory.class.getCanonicalName());
  
  /**
   * store osmway defaults in this map
   */
  protected final Map<String,Double> speedLimitDefaults;
      
  protected final OsmSpeedLimitDefaultsCategory backupDefaults;
  
  /** country these defaults apply for */
  protected final String countryName; 
    
  /**
   * Constructor. 
   * 
   * @param countryName these apply for
   */
  public OsmSpeedLimitDefaultsCategory(String countryName) {
    this.countryName = countryName;
    this.backupDefaults = null;
    this.speedLimitDefaults = new HashMap<String,Double>();    
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
    this.speedLimitDefaults = new HashMap<String,Double>();    
  }   
  
  /**
   * Copy constructor
   *  
   * @param other to use
   * 
   */
  public OsmSpeedLimitDefaultsCategory(OsmSpeedLimitDefaultsCategory other) {
    this.backupDefaults = other.backupDefaults;
    this.countryName = other.countryName;
    this.speedLimitDefaults =  new HashMap<String,Double>(other.speedLimitDefaults);
  }
  
  /** Set a speed default for a given type
   * 
   * @param type of the way to set speed default for
   * @param speedLimitKmH the physical speed limit (km/h)
   */
  public void setSpeedLimitDefault(final String type, double speedLimitKmH){
    speedLimitDefaults.put(type, speedLimitKmH);
  }
  
  /** Get a speed limit default for a given way type
   * 
   * @param type of way to get speed default for
   * @return the physical speed limit (km/h)
   */
  public Double getSpeedLimit(String type) {
    Double speedLimit = speedLimitDefaults.get(type);
    if(speedLimit == null) {
      speedLimit = backupDefaults.getSpeedLimit(type);
    }        
    return speedLimit;
  }
      
  /** verify if a default speed limit is available for the given type
   * @param type to verify
   * @return true when available false otherwise
   */
  public boolean containsSpeedLimit(String type) {
    return speedLimitDefaults.containsKey(type);
  }
  
  /** collect the country name
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }

   
  
  /**
   * clone this class instance
   */
  @Override
  public OsmSpeedLimitDefaultsCategory clone() {
    return new OsmSpeedLimitDefaultsCategory(this);
  }  

}

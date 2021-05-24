package org.planit.osm.defaults;

/**
 * A class containing instances for each of the OSM speed limit default categories: urban/non-urban highways and railways
 * 
 * @author markr
 *
 */

public class OsmSpeedLimitDefaults implements Cloneable {
  
  /**
   * urban highway speed limit defaults
   */
  protected final OsmSpeedLimitDefaultsCategory urbanHighwayDefaults;

  /**
   * non-urban highway speed limit defaults
   */  
  protected final OsmSpeedLimitDefaultsCategory nonUrbanHighwayDefaults;
  
  /**
   * country name for the defaults
   */
  protected String countryName;
  
  /**
   * railway speed limit defaults
   */  
  protected final OsmSpeedLimitDefaultsCategory railwayDefaults;
  
  
  /** in absence of OSM default, we create a global highway speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_HIGHWAY_SPEEDLIMIT_KMH = 50;  
    
  /** in absence of OSM defined defaults, we make a global rail way speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH = 70;
  
  /** update country
   * 
   * @param countryName to use
   */
  protected void setCountry(final String countryName) {
    this.countryName = countryName;
  }
  
  /** constructor 
   * @param countryName country
   */
  public OsmSpeedLimitDefaults(String countryName) {
    this.countryName = countryName;
    this.urbanHighwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName);
    this.nonUrbanHighwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName);
    this.railwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName);
  }  
  
  /** constructor 
   * @param countryName country
   * @param backup to use in case this does not contain the default
   */
  public OsmSpeedLimitDefaults(String countryName, OsmSpeedLimitDefaults backup) {
    this.countryName = countryName;
    this.urbanHighwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName, backup.getUrbanHighwayDefaults());
    this.nonUrbanHighwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName, backup.getNonUrbanHighwayDefaults());
    this.railwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName, backup.getRailwayDefaults());
  }  

  
  /** constructor 
   * @param countryName country
   * @param urbanHighwayDefaults defaults
   * @param nonUrbanHighwayDefaults defaults
   * @param nonUrbanHighwayDefaults defaults
   */
  public OsmSpeedLimitDefaults(String countryName, OsmSpeedLimitDefaultsCategory urbanHighwayDefaults, OsmSpeedLimitDefaultsCategory nonUrbanHighwayDefaults, OsmSpeedLimitDefaultsCategory railwayDefaults) {
    this.countryName = countryName;
    this.urbanHighwayDefaults =urbanHighwayDefaults;
    this.nonUrbanHighwayDefaults =nonUrbanHighwayDefaults;
    this.railwayDefaults = railwayDefaults;
  }
  
  /** Copy constructor 
   * @param other
   * @throws CloneNotSupportedException  thrown if error
   */
  public OsmSpeedLimitDefaults(OsmSpeedLimitDefaults other) {
    if(other != null) {
      this.countryName = other.countryName;
      if(other.urbanHighwayDefaults != null) {
        this.urbanHighwayDefaults = other.urbanHighwayDefaults.clone();
      }else {
        this.urbanHighwayDefaults = null;
      }
      if(other.nonUrbanHighwayDefaults != null) {
        this.nonUrbanHighwayDefaults = other.nonUrbanHighwayDefaults.clone();
      }
      else {
        this.nonUrbanHighwayDefaults = null;
      }
      if(other.railwayDefaults != null) {
        this.railwayDefaults = other.railwayDefaults.clone();
      }else {
        this.railwayDefaults = null;
      }
    }else {
      this.urbanHighwayDefaults = null;
      this.railwayDefaults = null;
      this.nonUrbanHighwayDefaults = null;
      this.countryName = null;
    }
  }   
  
  /**
   * clone
   * 
   * @return shallow copy
   */
  public OsmSpeedLimitDefaults clone() {
    return new OsmSpeedLimitDefaults(this);
  }
  
  public OsmSpeedLimitDefaultsCategory getUrbanHighwayDefaults() {
    return urbanHighwayDefaults;
  }


  public OsmSpeedLimitDefaultsCategory getNonUrbanHighwayDefaults() {
    return nonUrbanHighwayDefaults;
  }


  public OsmSpeedLimitDefaultsCategory getRailwayDefaults() {
    return railwayDefaults;
  }
  
  /** collect the country name
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }
}

package org.goplanit.osm.defaults;

/**
 * A class containing instances for each of the OSM speed limit default categories: urban/non-urban highways and railways
 * 
 * @author markr
 *
 */

public class OsmSpeedLimitDefaults {
  
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

  /**
   * waterway speed limit defaults
   */
  protected final OsmSpeedLimitDefaultsCategory waterwayDefaults;
  
  
  /** in absence of OSM default, we create a global highway speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_HIGHWAY_SPEEDLIMIT_KMH = 50;  
    
  /** in absence of OSM defined defaults, we make a global railway speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_RAILWAY_SPEEDLIMIT_KMH = 70;

  /* in absence of OSM defined defaults, we provide a global waterway speed limit (km/h) available */
  public static final double GLOBAL_DEFAULT_WATERWAY_SPEEDLIMIT_KMH = 20;
  
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
    this.waterwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName);
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
    this.waterwayDefaults = new OsmSpeedLimitDefaultsCategory(countryName, backup.getWaterwayDefaults());
  }  

  
  /** Constructor
   *  
   * @param countryName country
   * @param urbanHighwayDefaults defaults
   * @param nonUrbanHighwayDefaults defaults
   * @param railwayDefaults defaults
   * @param waterwayDefaults defaults
   */
  public OsmSpeedLimitDefaults(
      String countryName,
      OsmSpeedLimitDefaultsCategory urbanHighwayDefaults,
      OsmSpeedLimitDefaultsCategory nonUrbanHighwayDefaults,
      OsmSpeedLimitDefaultsCategory railwayDefaults,
      OsmSpeedLimitDefaultsCategory waterwayDefaults) {
    this.countryName = countryName;
    this.urbanHighwayDefaults =urbanHighwayDefaults;
    this.nonUrbanHighwayDefaults =nonUrbanHighwayDefaults;
    this.railwayDefaults = railwayDefaults;
    this.waterwayDefaults = waterwayDefaults;
  }
  
  /** Copy constructor 
   * 
   * @param other to copy from
   */
  public OsmSpeedLimitDefaults(OsmSpeedLimitDefaults other) {
    if(other != null) {
      this.countryName = other.countryName;
      if(other.urbanHighwayDefaults != null) {
        this.urbanHighwayDefaults = other.urbanHighwayDefaults.deepClone();
      }else {
        this.urbanHighwayDefaults = null;
      }
      if(other.nonUrbanHighwayDefaults != null) {
        this.nonUrbanHighwayDefaults = other.nonUrbanHighwayDefaults.deepClone();
      }
      else {
        this.nonUrbanHighwayDefaults = null;
      }
      if(other.railwayDefaults != null) {
        this.railwayDefaults = other.railwayDefaults.deepClone();
      }else {
        this.railwayDefaults = null;
      }
      if(other.waterwayDefaults != null) {
        this.waterwayDefaults = other.waterwayDefaults.deepClone();
      }else {
        this.waterwayDefaults = null;
      }
    }else {
      this.urbanHighwayDefaults = null;
      this.railwayDefaults = null;
      this.waterwayDefaults = null;
      this.nonUrbanHighwayDefaults = null;
      this.countryName = null;
    }
  }   
  
  /**
   * clone
   * 
   * @return shallow copy
   */
  public OsmSpeedLimitDefaults shallowClone() {
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

  public OsmSpeedLimitDefaultsCategory getWaterwayDefaults() {
    return waterwayDefaults;
  }
  
  /** collect the country name
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }
}

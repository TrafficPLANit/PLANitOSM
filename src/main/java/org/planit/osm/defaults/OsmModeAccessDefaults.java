package org.planit.osm.defaults;

import java.util.logging.Logger;
import org.planit.utils.locale.CountryNames;

/**
 * Class representing the default mode access restrictions/allowance for modes for a given
 * highway type.
 * 
 * Disallowed modes take precedence over any other setting, allowed modes take precedence over mode category settings
 * and mode category settings define groups of allowed modes (when not present, it is assumed the category is not allowed as a whole)
 * 
 * @author markr
 *
 */
public class OsmModeAccessDefaults implements Cloneable {
  
  /**
   * The logger for this class
   */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmModeAccessDefaults.class.getCanonicalName());
  
  private final OsmModeAccessDefaultsCategory highwayModeAccessDefaults;
  
  private final OsmModeAccessDefaultsCategory railwayModeAccessDefaults;
  
  /** country for which these defaults hold */
  private String countryName;  
  
 
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaults() {
    this.countryName = CountryNames.GLOBAL;
    this.highwayModeAccessDefaults = new OsmModeAccessDefaultsCategory();
    this.railwayModeAccessDefaults = new OsmModeAccessDefaultsCategory();    
  }
  
  /**
   * Default constructor
   * 
   * @param countryName to use
   */
  public OsmModeAccessDefaults(String countryName) {
    this.countryName = countryName;
    this.highwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(countryName);
    this.railwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(countryName);        
  }  
  
  /**
   * Copy constructor
   * 
   * @param other to use
   */
  public OsmModeAccessDefaults(OsmModeAccessDefaults other) {
    this.countryName = other.countryName;
    this.highwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(other.highwayModeAccessDefaults);
    this.railwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(other.railwayModeAccessDefaults);    
  }  
  
  /** The country for which these defaults hold. In absence of a country, it should return CountryNames.GLOBAL
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }
  
  /** set the country name
   * @param countryName to use
   */
  public void setCountry(String countryName) {
    this.countryName = countryName;
    this.highwayModeAccessDefaults.setCountry(countryName);
    this.railwayModeAccessDefaults.setCountry(countryName);
  }    
  

  /**
   * {@inheritDoc}
   */
  @Override
  public OsmModeAccessDefaults clone() throws CloneNotSupportedException {
    return new OsmModeAccessDefaults(this);
  }  
     

  
  /** collect the defaults specifically for highways
   * @return highway mode access defaults
   */
  public OsmModeAccessDefaultsCategory getHighwayModeAccessDefaults() {
    return highwayModeAccessDefaults;
  }

  /** collect the defaults specifically for railways
   * @return railway mode access defaults
   */
  public OsmModeAccessDefaultsCategory getRailwayModeAccessDefaults() {
    return railwayModeAccessDefaults;
  }


}

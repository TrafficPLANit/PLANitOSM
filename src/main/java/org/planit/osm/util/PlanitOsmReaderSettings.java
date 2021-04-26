package org.planit.osm.util;

import org.planit.converter.ConverterReaderSettings;
import org.planit.utils.locale.CountryNames;

/**
 * Settings relevant for a Planit Xml reader
 * 
 * @author markr
 *
 */
public abstract class PlanitOsmReaderSettings implements ConverterReaderSettings {

  /** directory to look in */
  private String inputFile;
  
  /** country name to use to initialise OSM defaults for */
  private final String countryName;    
  
  /**
   * Default constructor with default locale (Global)
   */
  public PlanitOsmReaderSettings() {
    this(null, CountryNames.GLOBAL);
  }
  
  /**
   * Constructor
   * 
   *  @param inputFile to use
   *  @param countryName to use
   */
  public PlanitOsmReaderSettings(final String countryName) {
    this(null, countryName);
  }    
  
  /**
   * Constructor
   * 
   *  @param inputFile to use
   *  @param countryName to use
   */
  public PlanitOsmReaderSettings(final String inputFile, final String countryName) {
    this.inputFile = inputFile;
    this.countryName = countryName;
  }  
  
  /** the input file used
   * @return input file used
   */
  public final String getInputFile() {
    return this.inputFile;
  }
  
  /** set the inputFile  to use
   * @param inputFile to use
   */
  public void setInputFile(final String inputFile) {
    this.inputFile = inputFile;
  }  
  
  /** the country name used to initialise OSM defaults for
   * @return country name
   */
  public final String getCountryName() {
    return this.countryName;
  } 
   
}

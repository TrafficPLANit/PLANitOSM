package org.planit.osm.converter.zoning;

import java.util.logging.Logger;

import org.planit.utils.locale.CountryNames;

/**
 * Data specifically required in the zoning reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class OsmZoningReaderData {
  
  /** logeger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmZoningReaderData.class.getCanonicalName());
  
  /** the country name, used for geographic mapping that depends on driving direction on the infrastructure */
  private final String countryName;  
  
  /* UNPROCESSED OSM */
  
  /* planit entity related tracking during parsing */
  OsmZoningReaderPlanitData planitData = new OsmZoningReaderPlanitData();
  
  /* osm entity related tracking during parsing */
  OsmZoningReaderOsmData osmData = new OsmZoningReaderOsmData();   
  
  /**
   * Default constructor using country set to GLOBAL (right hand drive)
   */
  public OsmZoningReaderData() {
    this(CountryNames.GLOBAL);
  }  
  
  /** Constructor 
   * @param countryName for this zoning
   */
  public OsmZoningReaderData(String countryName) {
    this.countryName = countryName;
  }
  
  /** collect the country name
   * @return country name
   */
  public String getCountryName() {
    return countryName;
  }  

  /**
   * reset the handler
   */
  public void reset() {
    planitData.reset();
    osmData.reset();        
  }

  /** collect the planit related tracking data 
   * 
   * @return planit data
   */
  public OsmZoningReaderPlanitData getPlanitData() {
    return planitData;
  }
  
  /** collect the osm related tracking data 
   * 
   * @return osm data
   */
  public OsmZoningReaderOsmData getOsmData() {
    return osmData;
  }
  
}

package org.goplanit.osm.converter.zoning;

import java.util.logging.Logger;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.utils.locale.CountryNames;

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
  
  /* PLANit entity related tracking during parsing */
  OsmZoningReaderPlanitData planitData = new OsmZoningReaderPlanitData();
  
  /* OSM entity related tracking during parsing */
  OsmZoningReaderOsmData osmData = new OsmZoningReaderOsmData();

  /** the osmBoundary used during parsing.
   */
  private OsmBoundary osmBoundingArea = null;
  
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
  
  /** Collect the country name
   * 
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
    osmBoundingArea = null;
  }

  /** collect the planit related tracking data 
   * 
   * @return planit data
   */
  public OsmZoningReaderPlanitData getPlanitData() {
    return planitData;
  }
  
  /** collect the OSM related tracking data 
   * 
   * @return osm data
   */
  public OsmZoningReaderOsmData getOsmData() {
    return osmData;
  }

  /** get the bounding area
   *
   * @return bounding area
   */
  public OsmBoundary getBoundingArea(){
    return osmBoundingArea;
  }

  /**
   * Set the bounding area to use
   *
   * @param osmBoundingArea to use
   */
  public void setBoundingArea(OsmBoundary osmBoundingArea){
    this.osmBoundingArea = osmBoundingArea;
  }

  /**
   * Check if zoning has a bounding boundary area set
   *
   * @return ture if present, false otherwise
   */
  public boolean hasBoundingArea() {
    return getBoundingArea() != null;
  }
}

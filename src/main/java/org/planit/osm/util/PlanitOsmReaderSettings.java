package org.planit.osm.util;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.planit.converter.ConverterReaderSettings;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.locale.CountryNames;

/**
 * Settings relevant for a Planit Xml reader
 * 
 * @author markr
 *
 */
public abstract class PlanitOsmReaderSettings implements ConverterReaderSettings {

  /** directory and file to look in */
  private String inputFile;
  
  /** country name to use to initialise OSM defaults for */
  private final String countryName;  
  
  /** allow to restrict parsing to only within the bounding polygon, when null entire input is parsed */
  private Polygon boundingPolygon = null;
  
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
  
  /** the input file used including the path
   * @return input file used
   */
  public final String getInputFile() {
    return this.inputFile;
  }
  
  /** set the inputFile  to use including the path
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
  
  /** Set a square bounding box based on provided envelope
   * @param x1, first x coordinate
   * @param y1, first y coordinate
   * @param x2, second x coordinate
   * @param y2, second y coordinate
   * @throws PlanItException thrown if error
   */
  public final void setBoundingBox(Number x1, Number x2, Number y1, Number y2) throws PlanItException {
    setBoundingBox(new Envelope(PlanitJtsUtils.createPoint(x1, y1).getCoordinate(), PlanitJtsUtils.createPoint(x2, y2).getCoordinate()));
  }
  
  /** Set a square bounding box based on provided envelope (which internally is converted to the bounding polygon that happens to be square)
   * @param boundingBox to use
   */
  public final void setBoundingBox(Envelope boundingBox) {
    setBoundingPolygon(PlanitJtsUtils.create2DPolygon(boundingBox));
  }
  
  /** Set a polygon based bounding box to restrict parsing to
   * @param boundingPolygon to use
   */
  public final void setBoundingPolygon(Polygon boundingPolygon) {
    this.boundingPolygon = boundingPolygon;
  } 
  
  /** Set a polygon based bounding box to restrict parsing to
   * @return boundingPolygon used, can be null
   */
  public final Polygon getBoundingPolygon() {
    return this.boundingPolygon;
  }  
  
  /** Set a polygon based bounding box to restrict parsing to
   * @return boundingPolygon used, can be null
   */
  public final boolean hasBoundingPolygon() {
    return this.boundingPolygon!=null;
  }   
   
}

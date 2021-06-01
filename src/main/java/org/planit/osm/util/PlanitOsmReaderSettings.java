package org.planit.osm.util;

import java.net.URL;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.planit.converter.ConverterReaderSettings;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.locale.CountryNames;
import org.planit.utils.misc.UrlUtils;

/**
 * Settings relevant for a Planit Xml reader
 * 
 * @author markr
 *
 */
public abstract class PlanitOsmReaderSettings implements ConverterReaderSettings {

  /** input source to use */
  private URL inputSource;
  
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
  public PlanitOsmReaderSettings(final URL inputSource, final String countryName) {
    this.inputSource = inputSource;
    this.countryName = countryName;
  }  
  
  /** The input source used 
   * 
   * @return input source used
   */
  public final URL getInputSource() {
    return this.inputSource;
  }
  
  /** Set the input source  to use
   * @param inputSource to use
   */
  public void setInputSource(final URL inputSource) {
    this.inputSource = inputSource;
  }  
  
  /** Set the input file to use, which is internally converted into a URL
   * @param inputFile to use
   * @throws PlanItException thrown if error
   */
  public void setInputFile(final String inputFile) throws PlanItException {
    try{
      setInputSource(UrlUtils.createFromPath(inputFile));
    }catch(Exception e) {
      throw new PlanItException("Unable to extract URL from input file location %s",inputFile);
    }
  }    
  
  /** The country name used to initialise OSM defaults for
   * 
   * @return country name
   */
  public final String getCountryName() {
    return this.countryName;
  } 
  
  /** Set an additional (more restricting) square bounding box based on provided envelope
   * 
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
   * 
   * @param boundingBox to use
   */
  public final void setBoundingBox(Envelope boundingBox) {
    setBoundingPolygon(PlanitJtsUtils.create2DPolygon(boundingBox));
  }
  
  /** Set a polygon based bounding box to restrict parsing to
   * 
   * @param boundingPolygon to use
   */
  public final void setBoundingPolygon(Polygon boundingPolygon) {
    this.boundingPolygon = boundingPolygon;
  } 
  
  /** Set a polygon based bounding box to restrict parsing to
   * 
   * @return boundingPolygon used, can be null
   */
  public final Polygon getBoundingPolygon() {
    return this.boundingPolygon;
  }  
  
  /** Set a polygon based bounding box to restrict parsing to
   * 
   * @return boundingPolygon used, can be null
   */
  public final boolean hasBoundingPolygon() {
    return this.boundingPolygon!=null;
  }   
   
}

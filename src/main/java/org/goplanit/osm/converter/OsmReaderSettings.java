package org.goplanit.osm.converter;

import java.net.URL;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.UrlUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

/**
 * Settings relevant for a Planit Xml reader
 * 
 * @author markr
 *
 */
public abstract class OsmReaderSettings implements ConverterReaderSettings {

  /** input source to use */
  private URL inputSource;
  
  /** country name to use to initialise OSM defaults for */
  private final String countryName;  
  
  /** allow to restrict parsing to only within the bounding polygon, when null entire input is parsed */
  private Polygon boundingPolygon = null;
  
  /**
   * Default constructor with default locale (Global)
   */
  public OsmReaderSettings() {
    this((URL)null, CountryNames.GLOBAL);
  }
  
  /**
   * Constructor
   * 
   *  @param countryName to use
   */
  public OsmReaderSettings(final String countryName) {
    this((URL) null, countryName);
  }

  /**
   * Constructor
   *
   *  @param inputSource to use
   *  @param countryName to use
   */
  public OsmReaderSettings(final String inputSource, final String countryName) {
    setInputSource(inputSource);
    this.countryName = countryName;
  }

  /**
   * Constructor
   * 
   *  @param inputSource to use
   *  @param countryName to use
   */
  public OsmReaderSettings(final URL inputSource, final String countryName) {
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
   * 
   * @param inputSource to use
   */
  public void setInputSource(final URL inputSource) {
    this.inputSource = inputSource;
  }  
  
  /** Set the input source  to use, we attempt to extract a URL from the String directly here
   * 
   * @param inputSource to use
   */
  public void setInputSource(final String inputSource) {
    try {
      setInputSource(UrlUtils.createFrom(inputSource));
    }catch (Exception e) {
      throw new PlanItRunTimeException("Unable to extract URL from input source %s",inputSource);
    }
  }    
  
  /** Set the input file to use, which is internally converted into a URL
   * 
   * @param inputFile to use
   * @throws PlanItException thrown if error
   */
  public void setInputFile(final String inputFile) throws PlanItException {
    try{
      setInputSource(UrlUtils.createFromLocalPath(inputFile));
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

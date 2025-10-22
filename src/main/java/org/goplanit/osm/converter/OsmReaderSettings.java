package org.goplanit.osm.converter;

import java.net.URL;
import java.util.logging.Logger;

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

  private static final Logger LOGGER = Logger.getLogger(OsmReaderSettings.class.getCanonicalName());

  /** input source to use */
  private URL inputSource;
  
  /** country name to use to initialise OSM defaults for */
  private final String countryName;

  /** OsmBoundary to apply, if null no restriction is applied */
  private OsmBoundary osmBoundary = null;

  /**
   * Flag to indicate if OSM tags are to be retained as is as part of parsing for relevant
   * entities, e.g., links, nodes.
   */
  private boolean retainOsmTags = DEFAULT_RETAIN_OSM_TAGS;

  public static final boolean DEFAULT_RETAIN_OSM_TAGS = false;

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

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    LOGGER.info(String.format("%-40s: %s", "Retain original OSM tags", isRetainOsmTags()));
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


  /**
   * boundary to restrict parsing to
   *
   * @param osmBoundary to apply
   */
  public void setBoundingArea(final OsmBoundary osmBoundary){
    this.osmBoundary = osmBoundary;
  }

  /**
   * The OsmBoundary configured by the user
   *
   * @return osmBoundary
   */
  public OsmBoundary getBoundingArea(){
    return this.osmBoundary;
  }

  /** Set a polygon based bounding box to restrict parsing to
   * 
   * @return boundingPolygon used, can be null
   */
  public final boolean hasBoundingBoundary() {
    return this.osmBoundary!=null;
  }

  /**
   * Flag to indicate if OSM tags are retained as is as part of parsing for relevant
   * entities, e.g., links, nodes.
   *
   * @return flag
   */
  public boolean isRetainOsmTags() {
    return retainOsmTags;
  }

  /**
   * Flag to indicate if OSM tags are to be retained as is as part of parsing for relevant
   * entities, e.g., links, nodes.
   *
   * @param retainOsmTags flag to set
   */
  public void setRetainOsmTags(boolean retainOsmTags) {
    this.retainOsmTags = retainOsmTags;
  }
}

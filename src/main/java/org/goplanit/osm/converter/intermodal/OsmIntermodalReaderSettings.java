package org.goplanit.osm.converter.intermodal;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.UrlUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

import java.net.URL;

/**
 * Capture all the user configurable settings regarding the OSM intermodal reader, which in turn has a network
 * and zoning reader. Hence, these settings provide access to OSM network and zoning reader settings
 * 
 * @author markr
 *
 */
public class OsmIntermodalReaderSettings implements ConverterReaderSettings {
  
  /** the network settings to use */
  protected final OsmNetworkReaderSettings networkSettings;
  
  /** the zoning PT settings to use */
  protected final OsmPublicTransportReaderSettings zoningPtSettings;
  
  /**
   * Constructor
   * 
   * @param countryName to use
   */
  public OsmIntermodalReaderSettings(final String countryName) {
    this((URL) null, countryName);
  }

  /**
   * Constructor
   *
   * @param inputSource to use
   * @param countryName to use
   */
  public OsmIntermodalReaderSettings(final String inputSource, final String countryName) {
    this(UrlUtils.createFromLocalPath(inputSource), countryName);
  }

  /**
   * Constructor
   * 
   * @param inputSource to use
   * @param countryName to use
   */
  public OsmIntermodalReaderSettings(final URL inputSource, final String countryName) {
    this(
        new OsmNetworkReaderSettings(inputSource, countryName),
        new OsmPublicTransportReaderSettings(inputSource, countryName));

    /* default activate rail and ferry when performing intermodal parsing */
    getNetworkSettings().getRailwaySettings().activateParser(true);
    getNetworkSettings().getWaterwaySettings().activateParser(true);
  }  
         
  
  /**
   * Constructor
   * 
   * @param networkSettings to use
   * @param zoningPtSettings to use
   */
  public OsmIntermodalReaderSettings(final OsmNetworkReaderSettings networkSettings, final OsmPublicTransportReaderSettings zoningPtSettings) {
    this.networkSettings = networkSettings;
    this.zoningPtSettings = zoningPtSettings;
  } 
  

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    networkSettings.reset();
    zoningPtSettings.reset();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    networkSettings.logSettings();
    zoningPtSettings.logSettings();
  }

  // GETTERS/SETTERS
  
  /** Provide access to the network reader settings
   * 
   * @return network reader settings
   */
  public OsmNetworkReaderSettings getNetworkSettings() {
    return networkSettings;
  }
  
  /** Provide access to the zoning (pt)  reader settings
   * 
   * @return zoning reader pt settings
   */
  public OsmPublicTransportReaderSettings getPublicTransportSettings() {
    return zoningPtSettings;
  }  
  
  /** Set the inputSource  to use including for both the network and public transport settings (both should use the same source)
   * 
   * @param inputSource to use
   */
  public void setInputSource(final URL inputSource) {
    getNetworkSettings().setInputSource(inputSource);
    getPublicTransportSettings().setInputSource(inputSource);
  }  
  
  /** Set the input file to use, which is internally converted into a URL
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
  
  /** Set a square bounding box based on provided envelope
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
   * @param boundingBox to use
   */
  public final void setBoundingBox(Envelope boundingBox) {
    setBoundingPolygon(PlanitJtsUtils.create2DPolygon(boundingBox));
  }
  
  /** Set a polygon based bounding box to restrict parsing to
   * @param boundingPolygon to use
   */
  public final void setBoundingPolygon(Polygon boundingPolygon) {
    getNetworkSettings().setBoundingPolygon(boundingPolygon);
    getPublicTransportSettings().setBoundingPolygon(boundingPolygon);
  }   
  
}

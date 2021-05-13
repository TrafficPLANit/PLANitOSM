package org.planit.osm.converter.intermodal;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.planit.converter.ConverterReaderSettings;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;

/**
 * Capture all the user configurable settings regarding the OSM intermodal reader, which in turn has a network
 * and zoning reader. Hence these settings rpovide access to OSM network and zoning reader settings
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReaderSettings implements ConverterReaderSettings {
  
  /** the network settings to use */
  protected final PlanitOsmNetworkReaderSettings networkSettings;
  
  /** the zoning PT settings to use */
  protected final PlanitOsmPublicTransportReaderSettings zoningPtSettings;
  
  /**
   * constructor
   * 
   * @param countryName to use
   */
  public PlanitOsmIntermodalReaderSettings(final String countryName) {
    this(countryName, new PlanitOsmNetwork());
  }  
  
  /**
   * constructor
   * 
   * @param countryName to use
   * @param networkToPopulate to use
   */
  public PlanitOsmIntermodalReaderSettings(final String countryName, final PlanitOsmNetwork networkToPopulate) {
    this(null, countryName, networkToPopulate);
  }
  
  /**
   * constructor
   * 
   * @param inputFile to use
   * @param countryName to use
   * @param networkToPopulate to use
   */
  public PlanitOsmIntermodalReaderSettings(final String inputFile, final String countryName, final PlanitOsmNetwork networkToPopulate) {
    this(
        new PlanitOsmNetworkReaderSettings(inputFile, countryName, networkToPopulate), 
        new PlanitOsmPublicTransportReaderSettings(inputFile, countryName, networkToPopulate));
  }  
         
  
  /**
   * constructor
   * 
   * @param networkSettings to use
   * @param zoningPtSettings to use
   */
  public PlanitOsmIntermodalReaderSettings(final PlanitOsmNetworkReaderSettings networkSettings, final PlanitOsmPublicTransportReaderSettings zoningPtSettings) {
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
  
  // GETTERS/SETTERS
  
  /** provide access to the network reader settings
   * @return network reader settings
   */
  public PlanitOsmNetworkReaderSettings getNetworkSettings() {
    return networkSettings;
  }
  
  /** provide access to the zoning (pt)  reader settings
   * @return zoning reader pt settings
   */
  public PlanitOsmPublicTransportReaderSettings getPublicTransportSettings() {
    return zoningPtSettings;
  }  
  
  /** set the inputFile  to use including the path for both the network and public transport settings (both should
   * use the same file)
   * 
   * @param inputFile to use
   */
  public void setInputFile(final String inputFile) {
    getNetworkSettings().setInputFile(inputFile);
    getPublicTransportSettings().setInputFile(inputFile);
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
    getNetworkSettings().setBoundingPolygon(boundingPolygon);
    getPublicTransportSettings().setBoundingPolygon(boundingPolygon);
  }   
  
}

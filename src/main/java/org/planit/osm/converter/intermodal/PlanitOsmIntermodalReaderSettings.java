package org.planit.osm.converter.intermodal;

import org.planit.converter.ConverterReaderSettings;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;

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
  
}

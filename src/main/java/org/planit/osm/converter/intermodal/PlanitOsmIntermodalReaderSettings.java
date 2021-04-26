package org.planit.osm.converter.intermodal;

import org.planit.converter.ConverterReaderSettings;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;

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
  
}

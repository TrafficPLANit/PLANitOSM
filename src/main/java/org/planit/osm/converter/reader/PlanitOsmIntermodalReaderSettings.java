package org.planit.osm.converter.reader;

import org.planit.converter.ConverterReaderSettings;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.settings.zoning.PlanitOsmPublicTransportSettings;

/**
 * osm intermodal reader settings
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReaderSettings implements ConverterReaderSettings {
  
  /** the network settings to use */
  protected final PlanitOsmNetworkReaderSettings networkSettings;
  
  /** the pt settings to use */
  protected final PlanitOsmPublicTransportSettings ptSettings;  

  
  public PlanitOsmIntermodalReaderSettings(PlanitOsmNetwork osmNetworkToPopulate) {
    this.networkSettings = new PlanitOsmNetworkReaderSettings(osmNetworkToPopulate);
    this.ptSettings = new PlanitOsmPublicTransportSettings();
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    networkSettings.reset();
    ptSettings.reset();
  }

}

package org.goplanit.osm.converter.zoning.handler.helper;

import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.util.PlanitNetworkLayerUtils;
import org.goplanit.utils.exceptions.PlanItException;

/**
 * Base class for all parser classes targeting support for parsing a specific PLANit zoning related entity (connectoid, transfer zone etc.)
 * This base class provides common funcionality to be made available to all parsers deriving from it
 *    
 * @author markr
 *
 */
class ZoningHelperBase {
  
  /** settings to adhere to */
  private final OsmPublicTransportReaderSettings transferSettings;
  
  /** Collect the pt settings
   * 
   * @return pulibc transport settings
   */
  protected OsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }
  
  /** collect network to zoning data from settings
   * 
   * @return network to zoning data
   */
  protected OsmNetworkToZoningReaderData getNetworkToZoningData() {
    return transferSettings.getNetworkDataForZoningReader();
  }  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId ,getSettings().getReferenceNetwork(), getNetworkToZoningData());    
  }  

  /** Constructor 
   * 
   * @param transferSettings to use
   */
  protected ZoningHelperBase(final OsmPublicTransportReaderSettings transferSettings) {
    this.transferSettings = transferSettings;
  }
    
}

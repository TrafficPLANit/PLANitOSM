package org.goplanit.osm.converter.zoning.handler.helper;

import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.PlanitNetworkLayerUtils;

/**
 * Base class for all parser classes targeting support for parsing a specific PLANit zoning related entity (connectoid, transfer zone etc.)
 * This base class provides common funcionality to be made available to all parsers deriving from it
 *    
 * @author markr
 *
 */
class OsmZoningHelperBase {
  
  /** settings to adhere to */
  private final OsmPublicTransportReaderSettings transferSettings;

  /** reference network to use */
  private final PlanitOsmNetwork referenceNetwork;
  
  /** Collect the pt settings
   * 
   * @return pulibc transport settings
   */
  protected OsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }

  protected PlanitOsmNetwork getReferenceNetwork(){
    return referenceNetwork;
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
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) {
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId , referenceNetwork, getNetworkToZoningData());
  }  

  /** Constructor 
   * 
   * @param transferSettings to use
   */
  protected OsmZoningHelperBase(final PlanitOsmNetwork referenceNetwork, final OsmPublicTransportReaderSettings transferSettings) {
    this.transferSettings = transferSettings;
    this.referenceNetwork = referenceNetwork;
  }
    
}

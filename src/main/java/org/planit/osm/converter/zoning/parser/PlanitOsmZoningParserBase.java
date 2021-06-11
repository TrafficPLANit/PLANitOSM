package org.planit.osm.converter.zoning.parser;

import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmZoningHandlerHelper;
import org.planit.utils.exceptions.PlanItException;

/**
 * Base class for all parser classes targeting support for parsing a specific PLANit zoning related entity (connectoid, transfer zone etc.)
 * This base class provides common funcionality to be made available to all parsers deriving from it
 *    
 * @author markr
 *
 */
class PlanitOsmZoningParserBase {
  
  /** settings to adhere to */
  private final PlanitOsmPublicTransportReaderSettings transferSettings;
  
  /** Collect the pt settings
   * 
   * @return pulibc transport settings
   */
  protected PlanitOsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }
  
  /** collect network to zoning data from settings
   * 
   * @return network to zoning data
   */
  protected PlanitOsmNetworkToZoningReaderData getNetworkToZoningData() {
    return transferSettings.getNetworkDataForZoningReader();
  }  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {
    return PlanitOsmZoningHandlerHelper.hasNetworkLayersWithActiveOsmNode(
        osmNodeId, getNetworkToZoningData().getOsmNodes(),getSettings().getReferenceNetwork(), getNetworkToZoningData());    
  }  

  /** Constructor 
   * 
   * @param transferSettings to use
   */
  protected PlanitOsmZoningParserBase(final PlanitOsmPublicTransportReaderSettings transferSettings) {
    this.transferSettings = transferSettings;
  }
    
}

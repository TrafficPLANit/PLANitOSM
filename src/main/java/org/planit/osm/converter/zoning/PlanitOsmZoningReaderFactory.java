package org.planit.osm.converter.zoning;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.zoning.Zoning;

/**
 * Factory for creating PLANitOSM zoning Readers. For now OSM zoning reader require the presence of an OSM network reader as
 * those settings and subsequent reference network (that it is expected to populate) are inputs to the factory method.
 * 
 * @author markr
 *
 */
public class PlanitOsmZoningReaderFactory {    
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param networkReaderSettings settings used to populate the network, including the mappings from osm entities to planit entities, such as modes
   * @param referenceNetwork the network to populate
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, PlanitOsmNetworkSettings networkReaderSettings, PlanitOsmNetwork referenceNetwork) {
    return new PlanitOsmZoningReader(inputFile, networkReaderSettings, referenceNetwork, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()));    
  }  
  
}

package org.planit.osm.converter.network;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;

/**
 * Factory for creating PLANitOSMReaders
 * @author markr
 *
 */
public class PlanitOsmNetworkReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param inputFile to use
   * @return create osm reader
   */
  public static PlanitOsmNetworkReader create(String inputFile) {
    return new PlanitOsmNetworkReader(inputFile, "", new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create osm reader
   */
  public static PlanitOsmNetworkReader create(String inputFile, String countryName) {
    return new PlanitOsmNetworkReader(inputFile, countryName, new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return create osm reader
   */
  public static PlanitOsmNetworkReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate) {
    return new PlanitOsmNetworkReader(inputFile, countryName, osmNetworkToPopulate);    
  }  
  
}

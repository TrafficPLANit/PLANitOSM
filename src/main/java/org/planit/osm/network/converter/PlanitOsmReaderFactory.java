package org.planit.osm.network.converter;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;

/**
 * Factory for creating PLANitOSMReaders
 * @author markr
 *
 */
public class PlanitOsmReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param inputFuile to use
   * @return create osm reader
   */
  public static PlanitOsmReader createReader(String inputFile) {
    return new PlanitOsmReader(inputFile, "", new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network
   * 
   * @param inputFuile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create osm reader
   */
  public static PlanitOsmReader createReader(String inputFile, String countryName) {
    return new PlanitOsmReader(inputFile, countryName, new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFuile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return create osm reader
   */
  public static PlanitOsmReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate) {
    return new PlanitOsmReader(inputFile, countryName, osmNetworkToPopulate);    
  }  
  
}

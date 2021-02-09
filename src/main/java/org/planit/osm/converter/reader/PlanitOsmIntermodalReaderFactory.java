package org.planit.osm.converter.reader;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;
import org.planit.zoning.Zoning;

/**
 * Factory for creating PLANitOsmIntermodalReaders
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReaderFactory {
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network, zoning and non-locale specific defaults for any right hand driving country
   * 
   * @param inputFile to use
   * @return create osm reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile) {
    String countryName = "";
    return create(inputFile, countryName);    
  }  
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName) {
    PlanitOsmNetwork networkToPopulate = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
    return create(inputFile, countryName, networkToPopulate, new Zoning(networkToPopulate.getIdGroupingToken(), networkToPopulate.getNetworkGroupingTokenId()));    
  }
  
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    return new PlanitOsmIntermodalReader(inputFile, countryName, osmNetworkToPopulate, zoningToPopulate);      
  }  
  
}

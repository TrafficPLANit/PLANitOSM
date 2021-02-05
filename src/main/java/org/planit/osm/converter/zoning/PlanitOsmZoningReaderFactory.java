package org.planit.osm.converter.zoning;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;
import org.planit.zoning.Zoning;

/**
 * Factory for creating PLANitOSMReaders
 * @author markr
 *
 */
public class PlanitOsmZoningReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param inputFuile to use
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile) {
    PlanitOsmNetwork network = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
    return create(inputFile, network);    
  }  
  
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFuile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, PlanitOsmNetwork osmNetworkToPopulate) {
    return new PlanitOsmZoningReader(inputFile, osmNetworkToPopulate, new Zoning(osmNetworkToPopulate.getIdGroupingToken(), osmNetworkToPopulate.getNetworkGroupingTokenId()));    
  }  
  
}

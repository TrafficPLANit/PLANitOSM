package org.planit.osm.converter;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;

/**
 * Factory for creating PLANitOSMReaders
 * @author markr
 *
 */
public class PlanitOsmReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network
   * 
   * @param inputFuile to use
   * @return create osm reader
   */
  public static PlanitOsmReader createReader(String inputFile) {
    return new PlanitOsmReader(inputFile, new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFuile to use
   * @param osmNetworkToPopulate the network to populate
   * @return create osm reader
   */
  public static PlanitOsmReader create(String inputFile, PlanitOsmNetwork osmNetworkToPopulate) {
    return new PlanitOsmReader(inputFile, osmNetworkToPopulate);    
  }  
  
}

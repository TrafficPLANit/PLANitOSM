package org.planit.osm.reader;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;

/**
 * Factory for creating PLANitOSMReaders
 * @author markr
 *
 */
public class PLANitOSMReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network
   * 
   * @return create osm reader
   */
  public static PlanitOsmReader create() {
    return new PlanitOsmReader(new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param osmNetworkToPopulate the network to populate
   * @return create osm reader
   */
  public static PlanitOsmReader create(PlanitOsmNetwork osmNetworkToPopulate) {
    return new PlanitOsmReader(osmNetworkToPopulate);    
  }  
  
}

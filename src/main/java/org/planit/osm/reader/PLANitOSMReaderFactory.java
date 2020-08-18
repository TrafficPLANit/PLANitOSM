package org.planit.osm.reader;

import org.planit.osm.physical.network.macroscopic.PLANitOSMNetwork;
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
  public static PLANitOSMReader create() {
    return new PLANitOSMReader(new PLANitOSMNetwork(IdGroupingToken.collectGlobalToken()));    
  }
  
}

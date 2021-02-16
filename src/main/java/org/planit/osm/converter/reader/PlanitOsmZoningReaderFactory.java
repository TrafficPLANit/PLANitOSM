package org.planit.osm.converter.reader;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
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
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, PlanitOsmNetwork referenceNetwork) {
    return new PlanitOsmZoningReader(inputFile, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()));    
  }  
  
}

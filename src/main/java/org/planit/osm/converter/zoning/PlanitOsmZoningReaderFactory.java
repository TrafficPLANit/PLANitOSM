package org.planit.osm.converter.zoning;

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
   * @param settings to use
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(PlanitOsmPublicTransportReaderSettings settings, PlanitOsmNetwork referenceNetwork) {
    return new PlanitOsmZoningReader(settings, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()));    
  }  
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, PlanitOsmNetwork referenceNetwork) {
    return new PlanitOsmZoningReader(inputFile, countryName, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()));    
  }   
  
}

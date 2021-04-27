package org.planit.osm.converter.zoning;

import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.zoning.Zoning;

/**
 * Factory for creating PLANitOSM zoning Readers. For now OSM zoning reader require the presence of an OSM network reader as
 * those settings and subsequent reference network (that it is expected to populate) are inputs to the factory method. In other words
 * and OSM zoning reader cannot be created in a stand-alone fashion, it always requires an OSMNetwork reader as well.
 * 
 * @author markr
 *
 */
public class PlanitOsmZoningReaderFactory {   
  
  /** Create a default PLANitOsmZoningReader. User is expected to configure the reader via settings before invoking read() method 
   * 
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create() {
    return create(new PlanitOsmPublicTransportReaderSettings());    
  }   
    
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param settings to use
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(PlanitOsmPublicTransportReaderSettings settings) {
    return new PlanitOsmZoningReader(settings);    
  }    
    
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, PlanitOsmNetwork referenceNetwork) {
    return new PlanitOsmZoningReader(
        inputFile, countryName, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()),referenceNetwork);
  }   
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, PlanitOsmNetwork referenceNetwork, PlanitOsmNetworkToZoningReaderData network2ZoningData) {
    return create(
        inputFile, countryName, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()),referenceNetwork, network2ZoningData);
  } 
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param zoningToPopulate the zoning to populate
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return create osm reader
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, PlanitOsmNetworkToZoningReaderData network2ZoningData) {
    return new PlanitOsmZoningReader(inputFile, countryName, zoningToPopulate ,referenceNetwork, network2ZoningData);
  }   
  
}

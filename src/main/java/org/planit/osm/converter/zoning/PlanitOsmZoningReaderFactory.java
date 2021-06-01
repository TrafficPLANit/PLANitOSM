package org.planit.osm.converter.zoning;

import java.net.URL;
import java.nio.file.Paths;

import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.exceptions.PlanItException;
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
   * @param zoningToPopulate to populate
   * @return create OSM reader
   * @throws PlanItException 
   */
  public static PlanitOsmZoningReader create(Zoning zoningToPopulate) throws PlanItException {
    PlanItException.throwIfNull(zoningToPopulate, "no zoning instance provided to OSM zoning reader factory method");
    return create(new PlanitOsmPublicTransportReaderSettings(), zoningToPopulate);    
  }   
    
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param settings to use
   * @return create OSM reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(PlanitOsmPublicTransportReaderSettings settings) throws PlanItException {
    PlanItException.throwIfNull(settings, "no settings instance provided to OSM zoning reader factory method");
    PlanItException.throwIfNull(settings.getReferenceNetwork(),"Unable to initialise OSM zoning reader, network not available to base zoning instance from");
    return create(settings, new Zoning(settings.getReferenceNetwork().getIdGroupingToken(),settings.getReferenceNetwork().getNetworkGroupingTokenId()));    
  }  
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param settings to use
   * @param zoningToPopulate to populate
   * @return create OSM reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(PlanitOsmPublicTransportReaderSettings settings, Zoning zoningToPopulate) throws PlanItException {
    PlanItException.throwIfNull(settings, "no settings instance provided to OSM zoning reader factory method");
    PlanItException.throwIfNull(zoningToPopulate, "no zoning instance provided to OSM zoning reader factory method");
    return new PlanitOsmZoningReader(settings, zoningToPopulate);    
  }  
    
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create OSM reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, PlanitOsmNetwork referenceNetwork) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, referenceNetwork);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to Url", e, inputFile);
    }
  }   
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return create OSM reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, PlanitOsmNetwork referenceNetwork, PlanitOsmNetworkToZoningReaderData network2ZoningData) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, referenceNetwork, network2ZoningData);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to Url", e, inputFile);
    }
  } 
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param zoningToPopulate the zoning to populate
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return create OSM reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(String inputFile, String countryName, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, PlanitOsmNetworkToZoningReaderData network2ZoningData) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, zoningToPopulate, referenceNetwork, network2ZoningData);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to Url", e, inputFile);
    }
  }  
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputSource to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @return create osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(URL inputSource, String countryName, PlanitOsmNetwork referenceNetwork) throws PlanItException {
    PlanItException.throwIfNull(referenceNetwork, "no reference network provided to OSM zoning reader factory method");    
    return new PlanitOsmZoningReader(
        inputSource, countryName, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()),referenceNetwork);
  }    
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputSource to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return create osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(URL inputSource, String countryName, PlanitOsmNetwork referenceNetwork, PlanitOsmNetworkToZoningReaderData network2ZoningData) throws PlanItException {
    PlanItException.throwIfNull(referenceNetwork, "no reference network provided to OSM zoning reader factory method");
    return create(
        inputSource, countryName, new Zoning(referenceNetwork.getIdGroupingToken(), referenceNetwork.getNetworkGroupingTokenId()),referenceNetwork, network2ZoningData);
  }   
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputSource to use
   * @param countryName name of the country
   * @param zoningToPopulate the zoning to populate
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return create OSM reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmZoningReader create(URL inputSource, String countryName, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, PlanitOsmNetworkToZoningReaderData network2ZoningData) throws PlanItException {
    PlanItException.throwIfNull(zoningToPopulate, "no zoning instance provided to OSM zoning reader factory method");
    PlanItException.throwIfNull(referenceNetwork, "no reference network provided to OSM zoning reader factory method");
    return new PlanitOsmZoningReader(inputSource, countryName, zoningToPopulate ,referenceNetwork, network2ZoningData);
  }   
  
}

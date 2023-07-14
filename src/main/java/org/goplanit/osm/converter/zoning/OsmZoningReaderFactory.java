package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.nio.file.Paths;

import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitOSM zoning Readers. For now OSM zoning reader require the presence of an OSM network reader as
 * those settings and subsequent reference network (that it is expected to populate) are inputs to the factory method. In other words
 * and OSM zoning reader cannot be created in a stand-alone fashion, it always requires an OSMNetwork reader as well.
 * 
 * @author markr
 *
 */
public class OsmZoningReaderFactory {   

  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return created OSM reader
   */
  public static OsmZoningReader create(
      String inputFile, String countryName, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData) {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, referenceNetwork, network2ZoningData);
    }catch(Exception e) {
      throw new PlanItRunTimeException("Unable to convert input file %s to Url", e, inputFile);
    }
  } 
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName name of the country
   * @param zoningToPopulate the zoning to populate
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return created OSM reader
   */
  public static OsmZoningReader create(
      String inputFile, String countryName, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData) {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, zoningToPopulate, referenceNetwork, network2ZoningData);
    }catch(Exception e) {
      throw new PlanItRunTimeException("Unable to convert input file %s to Url", e, inputFile);
    }
  }

  /** Create a PLANitOSMReader while providing an OSM network to populate
   *
   * @param settings to use
   * @param zoningToPopulate the zoning to populate
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return created OSM reader
   */
  public static OsmZoningReader create(
      OsmPublicTransportReaderSettings settings, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData){
    return new OsmZoningReader(settings, zoningToPopulate, referenceNetwork, network2ZoningData);
  }

  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputSource to use
   * @param countryName name of the country
   * @param referenceNetwork to use the same setup regarding id creation for zoning
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @return created OSM reader
   */
  public static OsmZoningReader create(
      URL inputSource, String countryName, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData){
    PlanItRunTimeException.throwIfNull(referenceNetwork, "No reference network provided to OSM zoning reader factory method");
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
   * @return created OSM reader
   */
  public static OsmZoningReader create(
      URL inputSource, String countryName, Zoning zoningToPopulate, PlanitOsmNetwork referenceNetwork, OsmNetworkToZoningReaderData network2ZoningData){
    PlanItRunTimeException.throwIfNull(zoningToPopulate, "No zoning instance provided to OSM zoning reader factory method");
    PlanItRunTimeException.throwIfNull(referenceNetwork, "No reference network provided to OSM zoning reader factory method");
    return new OsmZoningReader(inputSource, countryName, zoningToPopulate ,referenceNetwork, network2ZoningData);
  }   
  
}

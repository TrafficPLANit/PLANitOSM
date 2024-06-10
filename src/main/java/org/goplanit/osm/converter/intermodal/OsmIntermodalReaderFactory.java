package org.goplanit.osm.converter.intermodal;

import java.net.URL;
import java.nio.file.Paths;

import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

/**
 * Factory for creating PLANitOsmIntermodalReaders
 * 
 * @author markr
 *
 */
public class OsmIntermodalReaderFactory {
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network, zoning and non-locale specific defaults for any right hand driving country
   * 
   * @return create OSM intermodal reader
   */
  public static OsmIntermodalReader create() {
    return create(CountryNames.GLOBAL);    
  }    
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network, zoning. Locale based on country name provided
   * 
   * @param countryName to use
   * @return create OSM intermodal reader
   */
  public static OsmIntermodalReader create(String countryName) {
    return create((URL)null, countryName);    
  }  
    
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create OSM intermodal reader
   * @throws PlanItException thrown if error
   */
  public static OsmIntermodalReader create(String inputFile, String countryName) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to URL", e, inputFile);
    }    
  }
  
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create OSM intermodal reader
   * @throws PlanItException thrown if error
   */
  public static OsmIntermodalReader create(
      String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, osmNetworkToPopulate, zoningToPopulate);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to URL", e, inputFile);
    }       
  }    
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param inputSource to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create OSM intermodal reader
   */
  public static OsmIntermodalReader create(URL inputSource, String countryName) {
    PlanitOsmNetwork networkToPopulate = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
    networkToPopulate.setXmlId(networkToPopulate.getId());
    return create(inputSource, countryName, networkToPopulate, new Zoning(networkToPopulate.getIdGroupingToken(), networkToPopulate.getNetworkGroupingTokenId()));    
  }  
  
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputSource to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create OSM intermodal reader
   */
  public static OsmIntermodalReader create(
      URL inputSource, String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    return new OsmIntermodalReader(inputSource, countryName, osmNetworkToPopulate, zoningToPopulate);
  }    
  
  /** Create a PLANitOsmIntermodalReader which requires the user to set the remaining required settings on the provided settings instances
   * 
   * @param settings to use
   * @return create OSM intermodal reader
   */
  public static OsmIntermodalReader create(OsmIntermodalReaderSettings settings) {
    var networkToPopulate = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
    networkToPopulate.setXmlId(networkToPopulate.getId());
    var zoningToPopulate = new Zoning(networkToPopulate.getIdGroupingToken(),  networkToPopulate.getNetworkGroupingTokenId());
    return create(settings, networkToPopulate, zoningToPopulate);
  }    

  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param settings to use
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create OSM intermodal reader
   */
  public static OsmIntermodalReader create(
      OsmIntermodalReaderSettings settings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    return new OsmIntermodalReader(settings, osmNetworkToPopulate, zoningToPopulate);
  }  
          
}

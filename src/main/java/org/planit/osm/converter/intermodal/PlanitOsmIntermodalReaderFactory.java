package org.planit.osm.converter.intermodal;

import java.net.URL;
import java.nio.file.Paths;

import org.planit.osm.converter.network.OsmNetworkReaderSettings;
import org.planit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.locale.CountryNames;
import org.planit.zoning.Zoning;

/**
 * Factory for creating PLANitOsmIntermodalReaders
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReaderFactory {
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network, zoning and non-locale specific defaults for any right hand driving country
   * 
   * @return create OSM intermodal reader
   */
  public static PlanitOsmIntermodalReader create() {
    return create(CountryNames.GLOBAL);    
  }    
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network, zoning. Locale based on country name provided
   * 
   * @param countryName to use
   * @return create OSM intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String countryName) {
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
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName) throws PlanItException {
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
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) throws PlanItException {
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
  public static PlanitOsmIntermodalReader create(URL inputSource, String countryName) {
    PlanitOsmNetwork networkToPopulate = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
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
  public static PlanitOsmIntermodalReader create(URL inputSource, String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    return new PlanitOsmIntermodalReader(inputSource, countryName, osmNetworkToPopulate, zoningToPopulate);      
  }    
  
  /** Create a PLANitOsmIntermodalReader which requires the user to set the remaining required settings on the provided settings instances
   * 
   * @param networkSettings to use
   * @param ptSettings settings to use for the public transport aspect
   * @return create OSM intermodal reader
   * @throws PlanItException throw if error
   */
  public static PlanitOsmIntermodalReader create(OsmNetworkReaderSettings networkSettings, OsmPublicTransportReaderSettings ptSettings) throws PlanItException {
    return create(networkSettings, ptSettings, new PlanitOsmNetwork());    
  }    
    
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param networkSettings to use
   * @param osmNetworkToPopulate the network to populate
   * @return create OSM intermodal reader
   * @throws PlanItException throw if network settings are inconsistent with provided country and network to populate
   */
  public static PlanitOsmIntermodalReader create(OsmNetworkReaderSettings networkSettings, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    return create(
        networkSettings, 
        new OsmPublicTransportReaderSettings(
            networkSettings.getCountryName()), osmNetworkToPopulate, new Zoning(osmNetworkToPopulate.getIdGroupingToken(), osmNetworkToPopulate.getNetworkGroupingTokenId()));    
  }  
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param networkSettings to use
   * @param ptSettings settings to use for the public transport aspect
   * @param osmNetworkToPopulate the network to populate
   * @return create OSM intermodal reader
   * @throws PlanItException throw if error
   */
  public static PlanitOsmIntermodalReader create(OsmNetworkReaderSettings networkSettings, OsmPublicTransportReaderSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    return create(
        networkSettings, ptSettings, osmNetworkToPopulate, new Zoning(osmNetworkToPopulate.getIdGroupingToken(), osmNetworkToPopulate.getNetworkGroupingTokenId()));    
  }  
      
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param networkSettings to use
   * @param ptSettings settings to use for the public transport aspect
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create OSM intermodal reader
   * @throws PlanItException throw if network settings are inconsistent with provided country and network to populate
   */
  public static PlanitOsmIntermodalReader create(OsmNetworkReaderSettings networkSettings, OsmPublicTransportReaderSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) throws PlanItException {
    return new PlanitOsmIntermodalReader(networkSettings, ptSettings, osmNetworkToPopulate, zoningToPopulate);      
  }  
          
}

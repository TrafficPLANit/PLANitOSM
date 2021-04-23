package org.planit.osm.converter.reader;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.settings.zoning.PlanitOsmPublicTransportSettings;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
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
   * @param inputFile to use
   * @return create osm reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile) {
    String countryName = "";
    return create(inputFile, countryName);    
  }  
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName) {
    PlanitOsmNetwork networkToPopulate = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
    return create(inputFile, countryName, networkToPopulate, new Zoning(networkToPopulate.getIdGroupingToken(), networkToPopulate.getNetworkGroupingTokenId()));    
  }
  
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    return new PlanitOsmIntermodalReader(inputFile, countryName, osmNetworkToPopulate, zoningToPopulate);      
  }    
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param ptSettings settings to use for the public transport aspect
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName, PlanitOsmPublicTransportSettings ptSettings) {
    PlanitOsmNetwork networkToPopulate = new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken());
    return new PlanitOsmIntermodalReader(inputFile, countryName, ptSettings, networkToPopulate, new Zoning(networkToPopulate.getIdGroupingToken(), networkToPopulate.getNetworkGroupingTokenId()));    
  }
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param networkSettings to use
   * @param ptSettings settings to use for the public transport aspect
   * @param osmNetworkToPopulate the network to populate
   * @return create osm intermodal reader
   * @throws PlanItException throw if network settings are inconsistent with provided country and network to populate
   */
  public static PlanitOsmIntermodalReader create(PlanitOsmNetworkReaderSettings networkSettings, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    return new PlanitOsmIntermodalReader(networkSettings, new PlanitOsmPublicTransportSettings(), osmNetworkToPopulate, new Zoning(osmNetworkToPopulate.getIdGroupingToken(), osmNetworkToPopulate.getNetworkGroupingTokenId()));    
  }  
  
  /** Create a PLANitOsmIntermodalReader which will create its own macroscopic network and zoning
   * 
   * @param networkSettings to use
   * @param ptSettings settings to use for the public transport aspect
   * @param osmNetworkToPopulate the network to populate
   * @return create osm intermodal reader
   * @throws PlanItException throw if error
   */
  public static PlanitOsmIntermodalReader create(PlanitOsmNetworkReaderSettings networkSettings, PlanitOsmPublicTransportSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    return new PlanitOsmIntermodalReader(networkSettings, ptSettings, osmNetworkToPopulate, new Zoning(osmNetworkToPopulate.getIdGroupingToken(), osmNetworkToPopulate.getNetworkGroupingTokenId()));    
  }  
    
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param ptSettings settings to use for the public transport aspect
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(
      String inputFile, String countryName, PlanitOsmPublicTransportSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    return new PlanitOsmIntermodalReader(inputFile, countryName, ptSettings, osmNetworkToPopulate, zoningToPopulate);      
  }
  
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param networkSettings to use
   * @param ptSettings settings to use for the public transport aspect
   * @param osmNetworkToPopulate the network to populate
   * @param zoningToPopulate the zoning to populate
   * @return create osm intermodal reader
   * @throws PlanItException throw if network settings are inconsistent with provided country and network to populate
   */
  public static PlanitOsmIntermodalReader create(PlanitOsmNetworkReaderSettings networkSettings, PlanitOsmPublicTransportSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) throws PlanItException {
    return new PlanitOsmIntermodalReader(networkSettings, ptSettings, osmNetworkToPopulate, zoningToPopulate);      
  }  
  
  
  
  
  
}

package org.planit.osm.converter.intermodal;

import org.planit.osm.converter.network.PlanitOsmNetworkReader;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderFactory;
import org.planit.osm.converter.zoning.PlanitOsmZoningReader;
import org.planit.osm.converter.zoning.PlanitOsmZoningReaderFactory;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.id.IdGroupingToken;

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
    return create(inputFile, countryName, networkToPopulate);    
  }
  
  /** Create a PLANitOsmIntermodalReader while providing an OSM network, and zoning to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return create osm intermodal reader
   */
  public static PlanitOsmIntermodalReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate) {
    PlanitOsmNetworkReader networkReader = PlanitOsmNetworkReaderFactory.create(inputFile, countryName, osmNetworkToPopulate);
    PlanitOsmZoningReader zoningReader = PlanitOsmZoningReaderFactory.create(inputFile, osmNetworkToPopulate);
    return new PlanitOsmIntermodalReader( networkReader, zoningReader);      
  }  
  
}

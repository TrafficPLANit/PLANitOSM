package org.planit.osm.converter.reader;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkReaderSettings;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitOSMReaders
 * @author markr
 *
 */
public class PlanitOsmNetworkReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmNetworkReader create() throws PlanItException {
    return create(CountryNames.GLOBAL);    
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param countryName to use for the defaults to apply
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmNetworkReader create(String countryName) throws PlanItException {
    return new PlanitOsmNetworkReader(countryName, new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));    
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmNetworkReader create(String inputFile, String countryName) throws PlanItException {
    PlanitOsmNetworkReader reader =  create(countryName);
    reader.getSettings().setInputFile(inputFile);
    return reader;
  }
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmNetworkReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    PlanitOsmNetworkReader reader = new PlanitOsmNetworkReader(countryName, osmNetworkToPopulate);
    reader.getSettings().setInputFile(inputFile);
    return reader;
  }  
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param settings to use, make sure they are consistent with the network and country provided here otherwise an exeption will be thrown
   * @param osmNetworkToPopulate the network to populate
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static PlanitOsmNetworkReader create(PlanitOsmNetworkReaderSettings settings, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    return new PlanitOsmNetworkReader(settings, osmNetworkToPopulate);    
  }   
  
}

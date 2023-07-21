package org.goplanit.osm.converter.network;

import java.net.URL;
import java.nio.file.Paths;

import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.locale.CountryNames;

/**
 * Factory for creating PLANitOSMReaders
 * 
 * @author markr
 *
 */
public class OsmNetworkReaderFactory {
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @return created osm reader
   */
  public static OsmNetworkReader create() {
    return create(CountryNames.GLOBAL);    
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network and non-locale specific defaults for any right hand driving country
   * 
   * @param countryName to use for the defaults to apply
   * @return created osm reader
   */
  public static OsmNetworkReader create(String countryName) {
    return create(new OsmNetworkReaderSettings(countryName));
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network
   * 
   * @param inputFile local file to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static OsmNetworkReader create(String inputFile, String countryName) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to Url", e, inputFile);
    }
  }
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputFile local file to use
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return created osm reader
   * @throws PlanItException thrown if error
   */
  public static OsmNetworkReader create(String inputFile, String countryName, PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException {
    try {
      return create(Paths.get(inputFile).toUri().toURL(), countryName, osmNetworkToPopulate);
    }catch(Exception e) {
      throw new PlanItException("Unable to convert input file %s to Url", e, inputFile);
    }
  }  
  
  /** Create a PLANitOSMReader which will create its own macroscopic network by drawing from a cloud based map source
   * 
   * @param inputQuery Url location to retrieve OSM XML data from, e.g. {@code new URL("https://api.openstreetmap.org/api/0.6/map?bbox=13.465661,52.504055,13.469817,52.506204");}
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @return created osm reader
   */
  public static OsmNetworkReader create(URL inputQuery, String countryName) {
    OsmNetworkReader reader =  create(countryName);
    reader.getSettings().setInputSource(inputQuery);
    return reader;
  }  
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param inputQuery Url location to retrieve OSM XML data from, e.g. new URL("https://api.openstreetmap.org/api/0.6/map?bbox=13.465661,52.504055,13.469817,52.506204");
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetworkToPopulate the network to populate
   * @return created osm reader
   */
  public static OsmNetworkReader create(URL inputQuery, String countryName, PlanitOsmNetwork osmNetworkToPopulate) {
    OsmNetworkReader reader = new OsmNetworkReader(countryName, osmNetworkToPopulate);
    reader.getSettings().setInputSource(inputQuery);
    return reader;
  }    
  
  /** Create a PLANitOSMReader while providing an OSM network to populate
   * 
   * @param settings to use, make sure they are consistent with the network and country provided here otherwise an exception will be thrown
   * @return created osm reader
   */
  public static OsmNetworkReader create(OsmNetworkReaderSettings settings) {
    return new OsmNetworkReader(settings, new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));
  }   
  
}

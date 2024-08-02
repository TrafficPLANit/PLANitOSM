package org.goplanit.osm.test;

import org.goplanit.converter.intermodal.IntermodalConverter;
import org.goplanit.converter.intermodal.IntermodalConverterFactory;
import org.goplanit.converter.network.NetworkConverter;
import org.goplanit.converter.network.NetworkConverterFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalWriter;
import org.goplanit.io.converter.intermodal.PlanitIntermodalWriterFactory;
import org.goplanit.io.converter.intermodal.PlanitIntermodalWriterSettings;
import org.goplanit.io.converter.network.PlanitNetworkReaderSettings;
import org.goplanit.io.converter.network.PlanitNetworkWriter;
import org.goplanit.io.converter.network.PlanitNetworkWriterFactory;
import org.goplanit.io.converter.network.PlanitNetworkWriterSettings;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReader;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderFactory;
import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.exceptions.PlanItException;

import java.util.logging.Logger;

/**
 * some example templates for converting OSM files to Planit network files with various predefined configurations
 * 
 * @author markr
 *
 */
public class Osm2PlanitConversionTemplates {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(Osm2PlanitConversionTemplates.class.getCanonicalName());

  /**
   * Template for simple car centric conversion of OSM network to PLANit network, with only the main road types active
   *
   * @param osmSettings the input settings
   * @param planitSettings the output settings
   * @throws PlanItException thrown if error
   */
  public static void osm2PlanitSettingsBased(
      OsmNetworkReaderSettings osmSettings, PlanitNetworkWriterSettings planitSettings) throws PlanItException {

    /* OSM reader */
    OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(osmSettings);

    /* PLANit writer */
    PlanitNetworkWriter planitWriter = PlanitNetworkWriterFactory.create(planitSettings);

    /* perform the conversion*/
    NetworkConverterFactory.create(osmReader, planitWriter).convert();
  }
  
  /**
   * Template for simple car centric conversion of OSM network to PLANit network, with only the main road types active
   * 
   * @param inputFile the input file
   * @param outputPath the output dir
   * @param countryName the country name
   * @param excludedOsmWays allow to exclude certain ways by id (if any)
   * @throws PlanItException thrown if error 
   */  
  public static void osm2PlanitCarSimple(String inputFile, String outputPath, String countryName, Long... excludedOsmWays) throws PlanItException {
              
    OsmNetworkReader osmReader = OsmNetworkReaderFactory.create(inputFile, countryName);
    osmReader.getSettings().excludeOsmWaysFromParsing(excludedOsmWays);
    
    osmReader.getSettings().getHighwaySettings().deactivateAllOsmRoadModesExcept(OsmRoadModeTags.MOTOR_CAR);
    osmReader.getSettings().setRemoveDanglingSubnetworks(true);
    osmReader.getSettings().setDiscardDanglingNetworksBelow(20);
    osmReader.getSettings().deactivateAllOsmWayTypesExcept(
        OsmHighwayTags.MOTORWAY, OsmHighwayTags.MOTORWAY_LINK,
        OsmHighwayTags.TRUNK, OsmHighwayTags.TRUNK_LINK,
        OsmHighwayTags.PRIMARY, OsmHighwayTags.PRIMARY_LINK, 
        OsmHighwayTags.SECONDARY, OsmHighwayTags.SECONDARY_LINK);   
    
    
    /* PLANit writer */
    PlanitNetworkWriter planitWriter = PlanitNetworkWriterFactory.create(outputPath, countryName);
        
    /* perform the conversion*/
    NetworkConverterFactory.create(osmReader, planitWriter).convert();
  }

  /**
   * Template for parsing intermodal road and rail infrastructure of OSM network to PLANit network for your typical assignment but without pedestrian or cyclist infrastructure
   *
   * @param inputFile the input file
   * @param outputPath the output path
   * @param countryName the country name
   * @throws PlanItException thrown if error
   */
  public static void osm2PlanitBasicIntermodalNoServices(String inputFile, String outputPath, String countryName) throws PlanItException {

    /* OSM intermodal reader */
    OsmIntermodalReader osmIntermodalReader = OsmIntermodalReaderFactory.create(inputFile, countryName);

    osmIntermodalReader.getSettings().getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    osmIntermodalReader.getSettings().getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    osmIntermodalReader.getSettings().getNetworkSettings().getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.PEDESTRIAN);

    /* activate railways */
    osmIntermodalReader.getSettings().getNetworkSettings().activateRailwayParser(true);

    /* PLANit intermodal writer */
    PlanitIntermodalWriter planitIntermodalWriter = PlanitIntermodalWriterFactory.create();
    planitIntermodalWriter.getSettings().setCountry(countryName);
    planitIntermodalWriter.getSettings().setOutputDirectory(outputPath);

    /* convert */
    IntermodalConverter theConverter = IntermodalConverterFactory.create(osmIntermodalReader, planitIntermodalWriter);
    theConverter.convert();
  }

  /**
   * Template for parsing intermodal OSM network/infrastructure without any services based on provided settings
   *
   * @param settings to use
   * @param writerSettings to use
   * @throws PlanItException thrown if error
   */
  public static void osm2PlanitIntermodalNoServices(
      OsmIntermodalReaderSettings settings,
      PlanitIntermodalWriterSettings writerSettings) throws PlanItException {

    /* reader */
    var osmReader = OsmIntermodalReaderFactory.create(settings);

    /* writer */
    PlanitIntermodalWriter planitWriter = PlanitIntermodalWriterFactory.create(writerSettings);

    /* convert */
    IntermodalConverter theConverter = IntermodalConverterFactory.create(osmReader, planitWriter);
    theConverter.convert();
  }

}

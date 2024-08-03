package org.goplanit.osm.converter.intermodal;

import java.net.URL;
import java.util.logging.Logger;

import org.goplanit.converter.intermodal.IntermodalReader;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReader;
import org.goplanit.osm.converter.zoning.OsmZoningReaderFactory;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.PlanitZoningUtils;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.zoning.Zoning;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit intermodal network which includes the transfer zones
 * of a zoning instance. By default an intermodal reader will activate parsing transfer infrastructure as well as the network infrastructure (including rail which for a 
 * "regular" network reader is turned off by default, since we assume that more often than not, once desires to include rail when parsing pt networks.
 * One can manually change these defaults via the various settings made available.
 * 
 * @author markr
 *
 */
public class OsmIntermodalReader implements IntermodalReader<ServiceNetwork, RoutedServices> {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmIntermodalReader.class.getCanonicalName());
  
  /** the settings to use */
  private final OsmIntermodalReaderSettings settings;
  
  /** the zoning to populate if any */
  private final Zoning zoningToPopulate;

  /** the network to populate */
  private final PlanitOsmNetwork osmNetworkToPopulate;


  /** Make sure settings are consistent for those properties that are assumed to be
   * 
   * @return true when valid, false otherwise
   */
  private boolean isSettingsValid() {
    OsmNetworkReaderSettings networkSettings = getSettings().getNetworkSettings();
    OsmPublicTransportReaderSettings ptSettings = getSettings().getPublicTransportSettings();
    
    /* both source countries must be the same */
    if( !networkSettings.getCountryName().equals(ptSettings.getCountryName())){
        LOGGER.severe(String.format(
            "OSM intermodal reader requires both the network and zoning (pt) to utilise the same source country upon parsing, found %s and %s respctively instead",networkSettings.getCountryName(), ptSettings.getCountryName()));
      return false;
    }
    
    /* both input files must be the same */
    if(!networkSettings.getInputSource().equals(ptSettings.getInputSource())) {
      LOGGER.warning(
          String.format("OSM intermodal reader requires both the network and zoning (pt) to utilise the same osm input file upon parsing, found %s and %s respctively instead",networkSettings.getInputSource(), ptSettings.getInputSource()));
      if(networkSettings.getInputSource()!=null) {
        LOGGER.warning(
            String.format("SALVAGED: set zoning input file to network input file instead: %s" ,networkSettings.getInputSource()));
        ptSettings.setInputSource(networkSettings.getInputSource());
      }else if(ptSettings.getInputSource()!=null) {
        LOGGER.warning(
            String.format("SALVAGED: set network input file to zoning input file instead: %s" ,ptSettings.getInputSource()));
        networkSettings.setInputSource(ptSettings.getInputSource());
      }else {
        return false;
      }
    }

    if(!(networkSettings.isHighwayParserActive() || networkSettings.isRailwayParserActive() || networkSettings.isWaterwayParserActive())){
      LOGGER.warning("Not a single type of network is activated nor road, rail, or water");
      return false;
    }
    
    return true;
       
  }

  /** Based on configuration remove any dangling subnetworks if required
   * 
   * @param osmNetworkReader to use
   * @param osmZoningReader to use
   * @param zoning to use
   */
  private void removeDanglingEntities(OsmNetworkReader osmNetworkReader, OsmZoningReader osmZoningReader, Zoning zoning) {
    
    /* subnetworks */
    if(osmNetworkReader.getSettings().isRemoveDanglingSubnetworks()) {
      osmNetworkReader.removeDanglingSubNetworks(zoning);
    }
    
    /* (transfer) zones */
    if(osmZoningReader.getSettings().isRemoveDanglingZones()) {
      PlanitZoningUtils.removeDanglingZones(zoning);
    }     
    
    /* transfer zone groups */
    if(osmZoningReader.getSettings().isRemoveDanglingTransferZoneGroups()) {
      PlanitZoningUtils.removeDanglingTransferZoneGroups(zoning);
    } 
  }

  /**
   * Constructor 
   * 
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   */
  protected OsmIntermodalReader(
      final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new OsmIntermodalReaderSettings(countryName), osmNetworkToPopulate, zoningToPopulate);
  }   
  
  /**
   * Constructor 
   * 
   * @param inputSource to use for all intermodal parsing
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   */
  protected OsmIntermodalReader(
      final URL inputSource, final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new OsmIntermodalReaderSettings(inputSource, countryName), osmNetworkToPopulate, zoningToPopulate);
  }     

  /**
   * Constructor 
   * 
   * @param settings to use
   * @param zoningToPopulate to populate
   * @param osmNetworkToPopulate to populate
   */
  protected OsmIntermodalReader(
      OsmIntermodalReaderSettings settings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate){
    this.settings = settings;
    this.zoningToPopulate = zoningToPopulate;
    this.osmNetworkToPopulate = osmNetworkToPopulate;
  }
  
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network and zoning
   * given the configuration options that have been set
   * 
   * @return network and zoning that has been parsed, or null if detected problem has occurred and logged
   */  
  @Override
  public Pair<MacroscopicNetwork, Zoning> read() {
    
    /* only proceed when configuration is valid */
    if(!isSettingsValid()) {
      return null;
    }
            
    /* NETWORK READER */
    OsmNetworkReader osmNetworkReader = OsmNetworkReaderFactory.create(getSettings().getNetworkSettings());
    
    /* disable removing dangling subnetworks, until zoning has been parsed as well */
    boolean originalRemoveDanglingSubNetworks = osmNetworkReader.getSettings().isRemoveDanglingSubnetworks();
    osmNetworkReader.getSettings().setRemoveDanglingSubnetworks(false);
    
    PlanitOsmNetwork network = (PlanitOsmNetwork) osmNetworkReader.read();

    //TODO: ugly, should be done in a less ugly way
    /* ensure crs are compatible */
    zoningToPopulate.setCoordinateReferenceSystem(network.getCoordinateReferenceSystem());

    /* ZONING READER */
    OsmPublicTransportReaderSettings ptSettings = getSettings().getPublicTransportSettings();
    var n2zData = osmNetworkReader.createNetworkToZoningReaderData();
    OsmZoningReader osmZoningReader = OsmZoningReaderFactory.create(
        ptSettings, zoningToPopulate, network, n2zData);
    
    /* configuration */
    boolean originalRemoveDanglingZones = osmZoningReader.getSettings().isRemoveDanglingZones();
    boolean originalRemoveDanglingTransferZoneGroups = osmZoningReader.getSettings().isRemoveDanglingTransferZoneGroups();
    {
      /* default activate the parser because otherwise there is no point in using an intermodal reader anyway */
      osmZoningReader.getSettings().activateParser(true);    
      
      osmZoningReader.getSettings().setRemoveDanglingZones(false);    
      osmZoningReader.getSettings().setRemoveDanglingTransferZoneGroups(false);      
    }
    Zoning zoning = osmZoningReader.read();

    /* restore original settings on dangling and ...*/
    osmNetworkReader.getSettings().setRemoveDanglingSubnetworks(originalRemoveDanglingSubNetworks);
    osmZoningReader.getSettings().setRemoveDanglingZones(originalRemoveDanglingZones);
    osmZoningReader.getSettings().setRemoveDanglingTransferZoneGroups(originalRemoveDanglingTransferZoneGroups);
    /* ... remove dangling entities if indicated */
    removeDanglingEntities(osmNetworkReader, osmZoningReader, zoning);

    /* return result */
    return Pair.of(network, zoning);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
  }      
  
  /**
   * {@inheritDoc}
   */
  @Override
  public OsmIntermodalReaderSettings getSettings() {
    return settings;
  }

  /**
   * Currently no support for this yet on the OSM side. To be implemented in the future. For now services are to be sourced
   * from GTFS and spliced into the OSM network
   *
   * @return false
   */
  @Override
  public boolean supportServiceConversion() {
    return false;
  }

  /**
   * Currently no support yet for this feature
   *
   * @return created network, zoning, service network and services
   */
  @Override
  public Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> readWithServices() {

    /* only proceed when configuration is valid */
    if(!isSettingsValid()) {
      return null;
    }

    throw new PlanItRunTimeException("Support for service reader as part of Intermodal reader not yet supported in OSMIntermodalReader");
  }
}

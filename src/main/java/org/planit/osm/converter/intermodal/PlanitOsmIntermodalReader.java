package org.planit.osm.converter.intermodal;

import java.net.URL;
import java.util.logging.Logger;

import org.planit.converter.intermodal.IntermodalReader;
import org.planit.network.InfrastructureNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkReader;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderFactory;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmZoningHandlerHelper;
import org.planit.osm.converter.zoning.PlanitOsmZoningReader;
import org.planit.osm.converter.zoning.PlanitOsmZoningReaderFactory;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.zoning.Zoning;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit intermodal network which includes the transfer zones
 * of a zoning instance. By default an intermodal reader will activate parsing transfer infrastructure as well as the network infrastructure (including rail which for a 
 * "regular" network reader is turned off by default, since we assume that more often than not, once desires to include rail when parsing pt networks.
 * One can manually change these defaults via the various settings made available.
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReader implements IntermodalReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmIntermodalReader.class.getCanonicalName());
  
  /** the settings to use */
  private PlanitOsmIntermodalReaderSettings settings;
  
  /** the zoning to populate if any */
  private Zoning zoningToPopulate;
       
    
  /** make sure settings are consistent for those properties that are assumed to be
   * 
   * @return true when valid, false otherwise
   */
  private boolean validateSettings() throws PlanItException {
    PlanitOsmNetworkReaderSettings networkSettings = getSettings().getNetworkSettings();
    PlanitOsmPublicTransportReaderSettings ptSettings = getSettings().getPublicTransportSettings();
    
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
    
    return true;
       
  }

  /**
   * Constructor 
   * 
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   */
  protected PlanitOsmIntermodalReader(final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new PlanitOsmIntermodalReaderSettings(countryName, osmNetworkToPopulate), zoningToPopulate);  
  }   
  
  /**
   * Constructor 
   * 
   * @param inputSource to use for all intermodal parsing
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   */
  protected PlanitOsmIntermodalReader(final URL inputSource, final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new PlanitOsmIntermodalReaderSettings(inputSource, countryName, osmNetworkToPopulate), zoningToPopulate);  
  }     
    
  /**
   * Constructor 
   * 
   * @param networkSettings to use
   * @param ptSettings to use
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   * @throws PlanItException throws if network settings are inconsistent with network and country provided
   */
  protected PlanitOsmIntermodalReader(PlanitOsmNetworkReaderSettings networkSettings, PlanitOsmPublicTransportReaderSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) throws PlanItException{
    this(new PlanitOsmIntermodalReaderSettings(networkSettings, ptSettings), zoningToPopulate);
    getSettings().getPublicTransportSettings().setReferenceNetwork(osmNetworkToPopulate);
  }
  
  /**
   * Constructor 
   * 
   * @param settings to use
   * @param zoningToPopulate to populate
   */
  protected PlanitOsmIntermodalReader(PlanitOsmIntermodalReaderSettings settings, Zoning zoningToPopulate){
    this.settings = settings;
    this.zoningToPopulate = zoningToPopulate;
    /* by default activate rail to parse in intermodal settings */
    getSettings().getNetworkSettings().activateRailwayParser(true);   
  }  
  
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network and zoning
   * given the configuration options that have been set
   * 
   * @return network and zoning that has been parsed, or null if detected problem has occurred and logged
   * @throws PlanItException thrown if error
   */  
  @Override
  public Pair<InfrastructureNetwork<?,?>, Zoning> read() throws PlanItException {
    
    /* only proceed when configuration is valid */
    if(!validateSettings()) {
      return null;
    }
    
    PlanitOsmNetworkReaderSettings networkSettings = getSettings().getNetworkSettings();
        
    /* OSM network reader */
    PlanitOsmNetworkReader osmNetworkReader = PlanitOsmNetworkReaderFactory.create(networkSettings);
    
    /* disable removing dangling subnetworks, until zoning has been parsed as well */
    boolean originalRemoveDanglingSubNetworks = osmNetworkReader.getSettings().isRemoveDanglingSubnetworks();
    osmNetworkReader.getSettings().setRemoveDanglingSubnetworks(false);
    
    /* parse OSM network */
    PlanitOsmNetwork network = (PlanitOsmNetwork) osmNetworkReader.read();    
    
    /* ZONING READER */
    PlanitOsmPublicTransportReaderSettings ptSettings = getSettings().getPublicTransportSettings();
    ptSettings.setReferenceNetwork(network);
    ptSettings.setNetworkDataForZoningReader(osmNetworkReader.createNetworkToZoningReaderData());
    PlanitOsmZoningReader osmZoningReader = PlanitOsmZoningReaderFactory.create(ptSettings, zoningToPopulate);
    
    /* configuration */
    boolean originalRemoveDanglingZones = osmZoningReader.getSettings().isRemoveDanglingZones();
    boolean originalRemoveDanglingTransferZoneGroups = osmZoningReader.getSettings().isRemoveDanglingTransferZoneGroups();
    {
      /* default activate the parser because otherwise there is no point in using an intermodal reader anyway */
      osmZoningReader.getSettings().activateParser(true);    
      
      osmZoningReader.getSettings().setRemoveDanglingZones(false);    
      osmZoningReader.getSettings().setRemoveDanglingTransferZoneGroups(false);      
    }            
           
    
    /* then parse the intermodal zoning aspect, i.e., transfer/od zones */
    Zoning zoning = osmZoningReader.read();
    
    /* now remove dangling entities if indicated */
    {
      /* subnetworks */
      osmNetworkReader.getSettings().setRemoveDanglingSubnetworks(originalRemoveDanglingSubNetworks);
      if(osmNetworkReader.getSettings().isRemoveDanglingSubnetworks()) {
        osmNetworkReader.removeDanglingSubNetworks(zoning);
      }
      
      /* (transfer) zones */
      osmZoningReader.getSettings().setRemoveDanglingZones(originalRemoveDanglingZones);
      if(osmZoningReader.getSettings().isRemoveDanglingZones()) {
        PlanitOsmZoningHandlerHelper.removeDanglingZones(zoning);
      }     
      
      /* transfer zone groups */
      osmZoningReader.getSettings().setRemoveDanglingTransferZoneGroups(originalRemoveDanglingTransferZoneGroups);
      if(osmZoningReader.getSettings().isRemoveDanglingTransferZoneGroups()) {
        PlanitOsmZoningHandlerHelper.removeDanglingTransferZoneGroups(zoning);
      }        
    }
    
    /* return result */
    return Pair.of(network, zoning);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    getSettings().reset();    
  }      
  
  /**
   * {@inheritDoc}
   */
  @Override
  public PlanitOsmIntermodalReaderSettings getSettings() {
    return settings;
  }  
  
}

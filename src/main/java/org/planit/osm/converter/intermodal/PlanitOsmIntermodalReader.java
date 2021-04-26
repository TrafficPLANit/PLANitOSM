package org.planit.osm.converter.intermodal;

import java.util.Map.Entry;
import java.util.logging.Logger;

import org.planit.converter.ConverterReaderSettings;
import org.planit.converter.intermodal.IntermodalReader;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkLayerHandler;
import org.planit.osm.converter.network.PlanitOsmNetworkReader;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderFactory;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;
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
       
    
  /**
   * Constructor 
   * 
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoning to populate
   */
  protected PlanitOsmIntermodalReader(final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new PlanitOsmIntermodalReaderSettings(countryName, osmNetworkToPopulate), zoningToPopulate);  
  }   
  
  /**
   * Constructor 
   * 
   * @param inputFile to use
   * @param countryName to use for parsing the geometries in desired projection
   * @param ptSettings to use
   * @param osmNetworkToPopulate to populate
   * @param zoning to populate
   */
  protected PlanitOsmIntermodalReader(PlanitOsmIntermodalReaderSettings settings, Zoning zoningToPopulate){
    /* by default activate rail to parse in intermodal settings */
    getSettings().getNetworkSettings()..activateRailwayParser(true);   
  }
  
  /**
   * Constructor 
   * 
   * @param inputFile to use
   * @param countryName to use for parsing the geometries in desired projection
   * @param networkSettings to use
   * @param ptSettings to use
   * @param osmNetworkToPopulate to populate
   * @param zoning to populate
   * @throws PlanItException throws if network settings are inconsistent with network and country provided
   */
  protected PlanitOsmIntermodalReader(PlanitOsmNetworkReaderSettings networkSettings, PlanitOsmPublicTransportReaderSettings ptSettings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) throws PlanItException{
    /* both source countries must be the same */
    PlanItException.throwIf(
        !networkSettings.getCountryName().equals(ptSettings.getCountryName()), 
        String.format(
            "OSM intermodal reader requires both the network and zoning (pt) to utilise the same source country upon parsing, found %s and %s respctively instead",networkSettings.getCountryName(), ptSettings.getCountryName()));
    
    
    /* NETWORK READER */
    initialiseNetworkReader(networkSettings, osmNetworkToPopulate);
    
    /* ZONING READER */
    initialiseZoningReader(ptSettings, osmNetworkToPopulate);   
  }  
  
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network and zoning
   * given the configuration options that have been set
   * 
   * @return network and zoning that has been parsed
   * @throws PlanItException thrown if error
   */  
  @Override
  public Pair<InfrastructureNetwork<?,?>, Zoning> read() throws PlanItException {     
    
    /* OSM network reader */
    PlanitOsmNetworkReader osmNetworkReader = PlanitOsmNetworkReaderFactory.create(getSettings().getNetworkSettings());
    
    /* disable removing dangling subnetworks, until zoning has been parsed as well */
    boolean originalRemoveDanglingSubNetworks = osmNetworkReader.getSettings().isRemoveDanglingSubnetworks();
    osmNetworkReader.getSettings().setRemoveDanglingSubnetworks(false);
    
    /* parse OSM network */
    PlanitOsmNetwork network = (PlanitOsmNetwork) osmNetworkReader.read();    
    PlanitOsmNetworkToZoningReaderData network2ZoningData = osmNetworkReader.createNetworkToZoningReaderData();
    
    /* ZONING READER */
    PlanitOsmZoningReader osmZoningReader = PlanitOsmZoningReaderFactory.create(getSettings().getPublicTransportSettings(), network);
    
    /* configuration */
    boolean originalRemoveDanglingZones = osmZoningReader.getSettings().isRemoveDanglingZones();
    boolean originalRemoveDanglingTransferZoneGroups = osmZoningReader.getSettings().isRemoveDanglingTransferZoneGroups();
    {
      /* default activate the parser because otherwise there is no point in using an intermodal reader anyway */
      osmZoningReader.getSettings().activateParser(true);    
      osmZoningReader.getSettings().setNetworkDataForZoningReader(network2ZoningData);
      
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
        osmZoningReader.removeDanglingZones();
      }     
      
      /* transfer zone groups */
      osmZoningReader.getSettings().setRemoveDanglingTransferZoneGroups(originalRemoveDanglingTransferZoneGroups);
      if(osmZoningReader.getSettings().isRemoveDanglingTransferZoneGroups()) {
        osmZoningReader.removeDanglingTransferZoneGroups();
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

package org.planit.osm.converter.reader;

import java.util.Map.Entry;
import java.util.logging.Logger;

import org.planit.converter.intermodal.IntermodalReader;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData.NetworkLayerData;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetworkLayerHandler;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.network.PlanitOsmTransferSettings;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.zoning.Zoning;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit intermodal network which includes the transfer zones
 * of a zoning instance. By default an intermodal reader will activate parsing transfer infrastructure as well as the network infrastructure.
 * One can manually change these defaults via the various settings made available.
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReader implements IntermodalReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmIntermodalReader.class.getCanonicalName());
  
  /** location of the input file to use */
  protected final String inputFile;
  
  /**
   * the network reader
   */
  protected final PlanitOsmNetworkReader osmNetworkReader;
  
  
  /**
   * the zoning reader 
   */
  protected PlanitOsmZoningReader osmZoningReader;
    
  
  /** gather all internal references that would be of use to an OSM zoning reader when used after this
   * reader completes
   * 
   * @param network 
   * @return gathered data references
   */
  protected PlanitOsmNetworkToZoningReaderData createNetworkToZoningReaderData(PlanitOsmNetwork network) {
    if(network.infrastructureLayers.isNoLayers() || network.infrastructureLayers.getFirst().isEmpty()) {
      LOGGER.warning("OSM zoning reader can only perform network->zoning data transfer when network has been populated by OSM network reader");
    }

    /* DTO */
    PlanitOsmNetworkToZoningReaderData network2zoningData = new PlanitOsmNetworkToZoningReaderData();
    
    /* generate data references */
    network2zoningData.setOsmNetwork(network);
    network2zoningData.setSettings(osmNetworkReader.getSettings());
    
    /* layer specific data references */
    for(Entry<MacroscopicPhysicalNetwork, PlanitOsmNetworkLayerHandler> entry : osmNetworkReader.getOsmNetworkHandler().getLayerHandlers().entrySet()){
      NetworkLayerData layerData = network2zoningData.registerNewLayerData(entry.getKey());
      PlanitOsmNetworkLayerHandler layerHandler = entry.getValue();
      /* nodes by osm id by layer reference */
      layerData.setNodesByOsmId(layerHandler.getParsedNodesByOsmId());
      /* collect osm nodes internal to a parsed Planit link that have not yet been converted to nodes*/
      layerData.setOsmNodeIdsInternalToLink(layerHandler.getOsmNodesInternalToLink());
    }
    
    return network2zoningData;
  }  

  /**
   * Constructor 
   * 
   * @param inputFile
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoning to populate
   */
  protected PlanitOsmIntermodalReader(final String inputFile, final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate){
    this.inputFile = inputFile;
    this.osmNetworkReader = PlanitOsmNetworkReaderFactory.create(inputFile, countryName, osmNetworkToPopulate);
    this.osmZoningReader = PlanitOsmZoningReaderFactory.create(inputFile, osmNetworkToPopulate);
    /* default activate the parser because otherwise there is no point in using an intermodal reader anyway */
    this.osmZoningReader.getSettings().activateParser(true);        
  }
  
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network and zoning
   * given the configuration options that have been set
   * 
   * @return network and zoning that has been parsed
   * @throws PlanItException thrown if error
   */  
  @Override
  public Pair<InfrastructureNetwork, Zoning> read() throws PlanItException {

    /* first parse the network */
    PlanitOsmNetwork network = (PlanitOsmNetwork) osmNetworkReader.read();
            
    /* transfer network reader data required for zonal reading to the zoning reader */
    this.osmZoningReader.setNetworkToZoningReaderData(createNetworkToZoningReaderData(network));       
    
    /* then parse the intermodal zoning aspect, i.e., transfer/od zones */
    Zoning zoning = osmZoningReader.read();
    
    /* return result */
    return Pair.create(network, zoning);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    /* reset both underlying readers */
    osmZoningReader.reset();
    osmNetworkReader.reset();    
  }      
  
  /** settings for the network reader component
   * 
   * @return network settings
   */
  public PlanitOsmNetworkSettings getNetworkSettings() {
    return osmNetworkReader.getSettings();
  }
  
  /** settings for the zoning/transfer reader component
   * 
   * @return transfer settings
   */
  public PlanitOsmTransferSettings getTransferSettings() {
    return osmZoningReader.getSettings();
  }  

}

package org.planit.osm.converter.reader;

import java.util.Set;
import java.util.logging.Logger;

import org.planit.converter.network.NetworkReader;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.network.macroscopic.MacroscopicPhysicalNetworkLayers;
import org.planit.osm.handler.PlanitOsmNetworkHandler;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.util.Osm4JUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.modifier.RemoveSubGraphListener;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.zoning.Zoning;
import org.planit.zoning.listener.UpdateConnectoidsOnSubGraphRemoval;

import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit network instance
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkReader implements NetworkReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkReader.class.getCanonicalName());
    
  /** input file to use */
  private final String inputFile;
  
  /** network reader data tracked during parsing */
  private final PlanitOsmNetworkReaderData networkData;
  
  /** settings to use */
  private final PlanitOsmNetworkSettings settings;
    
  /** tha handler responsible for the actual parsing */
  private PlanitOsmNetworkHandler osmHandler;
       
  /**
   * Log some information about this reader's configuration
   * @param inputFile 
   */
  private void logInfo(String inputFile) {
    LOGGER.info(String.format("OSM network input file: %s",inputFile));
    LOGGER.info(String.format("setting Coordinate Reference System: %s",settings.getSourceCRS().getName()));    
  }    
  
  /** provide the handler that performs the actual parsing
   * 
   * @return osm handler
   */
  protected PlanitOsmNetworkHandler getOsmNetworkHandler() {
    return osmHandler;
  }  
  
  /**
   * collect the network data gathered
   * @return network data
   */
  protected PlanitOsmNetworkReaderData getNetworkReaderData() {
    return networkData;
  }
  
  /**
   * remove dangling subnetworks when settings dictate it 
   * @throws PlanItException thrown if error
   */
  protected void removeDanglingSubNetworks() throws PlanItException {
    removeDanglingSubNetworks(null);
  }
  
  /**
   * remove dangling subnetworks when settings dictate it. In case the removal of subnetworks causes zones to become dangling
   * the user is required to remove those afterwards themselves, by providing the zoning, only the directly impacted connectoids
   * are removed if affected.
   * 
   * @param zoning to also remove connectoids from when they reference removed road/rail subnetworks
   * @throws PlanItException thrown if error
   */  
  public void removeDanglingSubNetworks(Zoning zoning) throws PlanItException {
    if(settings.isRemoveDanglingSubnetworks()) {

      Integer discardMinsize = settings.getDiscardDanglingNetworkBelowSize();
      Integer discardMaxsize = settings.getDiscardDanglingNetworkAboveSize();
      boolean keepLargest = settings.isAlwaysKeepLargestsubNetwork();
      
      /* logging stats  - before */
      MacroscopicPhysicalNetworkLayers layers = networkData.getOsmNetwork().infrastructureLayers;
      {
        LOGGER.info(String.format("Removing dangling subnetworks with less than %s vertices", discardMinsize != Integer.MAX_VALUE ? String.valueOf(discardMinsize) : "infinite"));
        if (discardMaxsize != Integer.MAX_VALUE) {
          LOGGER.info(String.format("Removing dangling subnetworks with more than %s vertices", String.valueOf(discardMaxsize)));
        }        
        if(zoning == null) {
          LOGGER.info(String.format("Original number of nodes %d, links %d, link segments %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments()));
        }else {
          LOGGER.info(String.format("Original number of nodes %d, links %d, link segments %d, connectoids %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments(), zoning.transferConnectoids.size()));
        }
      }      
           
      /* remove dangling subnetworks and account for the connectoids that are to be removed as well in case they reside on a dangling network */
      Set<RemoveSubGraphListener<?, ?>> listeners = zoning==null ? null : Set.of(new UpdateConnectoidsOnSubGraphRemoval<Node, Link, MacroscopicLinkSegment>(zoning)); 
      networkData.getOsmNetwork().removeDanglingSubnetworks(discardMinsize, discardMaxsize, keepLargest, listeners);
      
      /* logging stats  - after */
      {
        if(zoning == null) {
          LOGGER.info(String.format("Remaining number of nodes %d, links %d, link segments %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments()));
        }else {
          LOGGER.info(String.format("Remaining number of nodes %d, links %d, link segments %d, connectoids %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments(), zoning.transferConnectoids.size()));
        }
      }
            
    }
  }  
  
  /**
   * Constructor 
   * 
   * @param inputFile
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetwork network to populate 
   * @param settings for populating the network
   * @throws PlanItException throw if settings are inconsistent with reader configuration (different country name or network used)
   */
  protected PlanitOsmNetworkReader(String inputFile, String countryName, PlanitOsmNetworkSettings settings, PlanitOsmNetwork osmNetwork) throws PlanItException{
    this.inputFile = inputFile; 
    this.networkData = new PlanitOsmNetworkReaderData(countryName, osmNetwork);
    if(!settings.isConsistentWith(networkData)) {
      throw new PlanItException("provided settings inconsistent with network reader settings");
    }
    this.settings = settings;
  }  
  
  /**
   * Constructor 
   * 
   * @param inputFile
   * @param countryName country which the input file represents, used to determine defaults in case not specifically specified in OSM data, when left blank global defaults will be used
   * based on a right hand driving approach
   * @param osmNetwork network to populate 
   * @param settings for populating the network
   * @throws PlanItException never throws 
   */
  protected PlanitOsmNetworkReader(String inputFile, String countryName, PlanitOsmNetwork osmNetwork) throws PlanItException {
    this(inputFile, countryName, new PlanitOsmNetworkSettings(countryName, osmNetwork), osmNetwork);            
  }
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network
   * given the configuration options that have been set
   * 
   * @param inputFile to parse
   * @return macroscopic network that has been parsed
   * @throws PlanItException thrown if error
   */  
  @Override
  public MacroscopicNetwork read() throws PlanItException {
    /* ensure that the network CRS is consistent with the chosen source CRS */
    networkData.getOsmNetwork().transform(settings.getSourceCRS());    
    logInfo(inputFile);
    
    /* reader to parse the actual file */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(inputFile);
    if(osmReader == null) {
      LOGGER.severe("unable to create OSM reader for network, aborting");
    }else {
    
      /* set handler to deal with call backs from osm4j */
      osmHandler = new PlanitOsmNetworkHandler(networkData, settings);
      osmHandler.initialiseBeforeParsing();
      
      /* register handler */
      osmReader.setHandler(osmHandler);
      
      /* conduct parsing which will call back the handler*/
      try {
        osmReader.read();
      } catch (OsmInputException e) {
        LOGGER.severe(e.getMessage());
        throw new PlanItException("error during parsing of osm file",e);
      }
      
      /* dangling subnetworks */
      if(getSettings().isRemoveDanglingSubnetworks()) {
        removeDanglingSubNetworks();
      }
    }
    
    LOGGER.info("OSM full network parsing...DONE");
    
    /* return result */
    return networkData.getOsmNetwork();
  }  
    
  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public PlanitOsmNetworkSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    /* reset last used handler */
    osmHandler.reset();
  }

}

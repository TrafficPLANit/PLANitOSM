package org.goplanit.osm.converter.network;

import java.net.URL;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.goplanit.converter.network.NetworkReader;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.MacroscopicNetworkLayerConfigurator;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.Osm4JUtils;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.modifier.event.DirectedGraphModifierListener;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layers.MacroscopicNetworkLayers;
import org.goplanit.zoning.Zoning;
import org.goplanit.zoning.modifier.event.handler.UpdateConnectoidsOnVertexRemovalHandler;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit network instance
 * 
 * @author markr
 *
 */
public class OsmNetworkReader implements NetworkReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReader.class.getCanonicalName());
      
  /** network reader data tracked during parsing */
  private final OsmNetworkReaderData networkData;

  /** the network to populate */
  private final PlanitOsmNetwork osmNetworkToPopulate;
  
  /** settings to use */
  private final OsmNetworkReaderSettings settings;
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler(s) properly
   * 
   * @throws PlanItException thrown if error
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(getOsmNetworkToPopulate().getTransportLayers() != null && getOsmNetworkToPopulate().getTransportLayers().size()>0,
        "Network is expected to be empty at start of parsing OSM network, but it has layers already");
    
    /* gis initialisation */
    PlanitJtsCrsUtils geoUtils = new PlanitJtsCrsUtils(settings.getSourceCRS());
    try {
      getOsmNetworkToPopulate().transform(settings.getSourceCRS());
    }catch(PlanItException e) {
      LOGGER.severe(String.format("Unable to update network to CRS %s", settings.getSourceCRS().getName()));
    }

    /* initialise the modes on the network based on the settings chosen */
    getOsmNetworkToPopulate().createAndRegisterOsmCompatiblePlanitPredefinedModes(getSettings());
    if(getOsmNetworkToPopulate().getModes().firstMatch( m -> !m.isPredefinedModeType()) != null){
      // todo if we support custom modes, then all locations where we determine the mapping from OSM mode to PLANit mode needs revisiting to account for such custom mode mappings
      //  + settings need updating to support this activation somehow !!
      throw new PlanItRunTimeException("OSM based PLANit networks currently support only predefined mode mappings, but found custom PLANit mode, this is not allowed");
    }
    //todo: make the configuration configurable again via the settings, however this requires changing the configurator to work with predefined mode types rather than actual mode
    //      instances
    var planitInfrastructureLayerConfiguration = MacroscopicNetworkLayerConfigurator.createAllInOneConfiguration(osmNetworkToPopulate.getModes());

    /* (default) link segment types (on the network) */
    getOsmNetworkToPopulate().createAndRegisterLayers(planitInfrastructureLayerConfiguration);
    getOsmNetworkToPopulate().createAndRegisterOsmCompatibleLinkSegmentTypes(getSettings());
    /* when modes are deactivated causing supported osm way types to have no active modes, add them to unsupported way types to avoid warnings during parsing */
    settings.excludeOsmWayTypesWithoutActivatedModes();
    settings.logUnsupportedOsmWayTypes();
        
    /* initialise layer specific parsers */
    networkData.initialiseLayerParsers(getOsmNetworkToPopulate(), settings, geoUtils);
  }  
           
  /** Read based on reader and handler where the reader performs a callback to the handler provided
   * 
   * @param osmReader to use
   * @param osmHandler to use
   * @throws PlanItException thorw if error
   */
  private void read(OsmReader osmReader, DefaultOsmHandler osmHandler) throws PlanItException {
       
    try {
      osmReader.setHandler(osmHandler);      
      osmReader.read();
    } catch (OsmInputException e) {
      String cause = e.getCause()!=null ? e.getCause().getMessage() : "";
      LOGGER.severe(e.getMessage() + "cause:" + cause);
      throw new PlanItException("error during parsing of osm file",e);
    }
  }

  /**
   * Log some information about this reader's configuration
   */
  private void logInfo() {
    
    LOGGER.info(String.format("OSM network input source: %s",settings.getInputSource()));
    LOGGER.info(String.format("Country to base defaults on: %s",settings.getCountryName()));
    LOGGER.info(String.format("Setting Coordinate Reference System: %s",settings.getSourceCRS().getName()));
    if(getSettings().hasBoundingPolygon()) {
      LOGGER.info(String.format("Bounding polygon set to: %s",getSettings().getBoundingPolygon().toString()));
    }
    
  }    
  
  /** Perform preprocessing if needed, only needed when we have set a bounding box and we need to restrict the OSM entities
   *  parsed to this bounding box
   * 
   * @throws PlanItException thrown if error
   */
  private void doPreprocessing() throws PlanItException {
    
    LOGGER.info("Preprocessing: reducing memory footprint, identifying required OSM nodes");
    
//    /* preprocessing currently is only needed in case of bounding polygon present and user specified
//     * OSM ways to keep outside of this bounding polygon, otherwise skip */
//    if(getSettings().hasBoundingPolygon() && 
//        (getSettings().hasKeepOsmWaysOutsideBoundingPolygon() || getSettings().hasKeepOsmNodesOutsideBoundingPolygon())) {
      
      /* reader to parse the actual file or source location */
      OsmReader osmReader = Osm4JUtils.createOsm4jReader(settings.getInputSource());
      if(osmReader == null) {
        LOGGER.severe("Unable to create OSM reader for preprocessing network, aborting");
      }
      
      /* set handler to deal with call backs from osm4j */
      OsmNetworkPreProcessingHandler osmHandler = new OsmNetworkPreProcessingHandler(getOsmNetworkToPopulate(), networkData, settings);
      read(osmReader, osmHandler);  
//    }
  }

  /** Perform main processing of OSM network reader
   * 
   * @throws PlanItException thrown if error
   */
  private void doMainProcessing() throws PlanItException{

    OsmReader osmReader = Osm4JUtils.createOsm4jReader(settings.getInputSource());
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for network, aborting");
    }   
    OsmNetworkMainProcessingHandler osmHandler = new OsmNetworkMainProcessingHandler(getOsmNetworkToPopulate(), networkData, settings);
    read(osmReader, osmHandler);     
  }
  
  /** Collect the network data gathered
   * 
   * @return network data
   */
  protected OsmNetworkReaderData getNetworkReaderData() {
    return networkData;
  }
  
  /** Remove dangling subnetworks when settings dictate it
   *  
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
      boolean keepLargest = settings.isAlwaysKeepLargestSubnetwork();
      
      /* logging stats  - before */
      MacroscopicNetworkLayers layers = getOsmNetworkToPopulate().getTransportLayers();
      {
        LOGGER.info(String.format("Removing dangling subnetworks with less than %s vertices", discardMinsize != Integer.MAX_VALUE ? String.valueOf(discardMinsize) : "infinite"));
        if (discardMaxsize != Integer.MAX_VALUE) {
          LOGGER.info(String.format("Removing dangling subnetworks with more than %s vertices", String.valueOf(discardMaxsize)));
        }        
        if(zoning == null) {
          LOGGER.info(String.format("Original number of nodes %d, links %d, link segments %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments()));
        }else {
          LOGGER.info(String.format("Original number of nodes %d, links %d, link segments %d, connectoids %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments(), zoning.getTransferConnectoids().size()));
        }
      }      
           
      if(layers.size()!=1) {
        LOGGER.warning("Currently OSM networks only support a single infrastructure layer in PLANit");
      }
      
      /* account for the connectoids that are to be removed as well in case they reside on a dangling network 
       * TODO: refactor this listener and instead make sure it is automatically dealt with by the zoning as an internal listener in some way
       * as this always needs to happen not only in OSM 
       * TODO: this listener and zoning in general does not properly support layers since vertices across layers might have the same id whereas 
       * zone connectoids are now globally stored on the zoning and not per layer. This should be changed to avoid this problem possibly easier when this functionality
       * is not separate but dealt with within the zoning */
      DirectedGraphModifierListener listener = null;
      if(zoning != null) {
        listener = new UpdateConnectoidsOnVertexRemovalHandler(zoning);
        layers.getFirst().getLayerModifier().addListener(listener);
      }
      
      /* remove dangling subnetworks */ 
      getOsmNetworkToPopulate().removeDanglingSubnetworks(discardMinsize, discardMaxsize, keepLargest);
      
      /* remove listener as it is currently meant for local use only due to expensive initialisation which is also not kept up to date */
      if(zoning != null) {
        layers.getFirst().getLayerModifier().removeListener(listener);
      }
      
      /* logging stats  - after */
      {
        if(zoning == null) {
          LOGGER.info(String.format("Remaining number of nodes %d, links %d, link segments %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments()));
        }else {
          LOGGER.info(String.format("Remaining number of nodes %d, links %d, link segments %d, connectoids %d", layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments(), zoning.getTransferConnectoids().size()));
        }
      }
            
    }
  }

  /** Collect the osm network to populate
   *
   * @return osm network
   */
  protected PlanitOsmNetwork getOsmNetworkToPopulate() {
    return this.osmNetworkToPopulate;
  }
  
  /**
   * Constructor 
   * 
   * @param osmNetwork network to populate 
   * @throws PlanItException thrown if error
   */
  protected OsmNetworkReader(final PlanitOsmNetwork osmNetwork) throws PlanItException{
    this(CountryNames.GLOBAL, osmNetwork);
  }  
  
  /**
   * Constructor 
   * 
   * @param countryName to use
   * @param osmNetwork network to populate
   * @throws PlanItException thrown if error 
   */
  protected OsmNetworkReader(final String countryName, final PlanitOsmNetwork osmNetwork) throws PlanItException{
    this(null, countryName, osmNetwork);
  }  
  
  /**
   * Constructor 
   * 
   * @param inputSource to use
   * @param countryName to use
   * @param osmNetworkToPopulate network to populate
   * @throws PlanItException thrown if error
   */
  protected OsmNetworkReader(final URL inputSource, final String countryName, final PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException{
    this(new OsmNetworkReaderSettings(inputSource, countryName), osmNetworkToPopulate);
  }    
    
  /**
   * Constructor 
   *  
   * @param settings for populating the network
   * @param osmNetworkToPopulate network to populate
   * @throws PlanItException throw if settings are inconsistent with reader configuration (different country name or network used)
   */
  protected OsmNetworkReader(OsmNetworkReaderSettings settings, final PlanitOsmNetwork osmNetworkToPopulate) throws PlanItException{
    this.settings = settings;   
    this.networkData = new OsmNetworkReaderData();
    this.osmNetworkToPopulate = osmNetworkToPopulate;
  }  
     
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network
   * given the configuration options that have been set
   * 
   * @return macroscopic network that has been parsed
   * @throws PlanItException thrown if error
   */  
  @Override
  public MacroscopicNetwork read() throws PlanItException {
    PlanItException.throwIfNull(getSettings().getInputSource(),"input source not set for OSM network to parse");
    PlanItException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()),"country name not set for OSM network to parse");
    PlanItException.throwIfNull(getOsmNetworkToPopulate(),"planit network to populate not set for OSM network to parse");

    logInfo();
    
    /* initialise */
    initialiseBeforeParsing();    
    
    /* preprocessing (if needed)*/
    doPreprocessing();
    
    /* main processing  (always)*/
    doMainProcessing();    
      
    /* dangling subnetworks */
    if(getSettings().isRemoveDanglingSubnetworks()) {
      removeDanglingSubNetworks();
    }

    if(!osmNetworkToPopulate.isEmpty()) {
      LOGGER.info(String.format("Bounding box of final network: %s", getNetworkReaderData().getBoundingBox().toString()));
    }
    LOGGER.info("OSM full network parsing...DONE");
    
    /* return result */
    return osmNetworkToPopulate;
  }  
    
  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public OsmNetworkReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
  }

  /** Factory method to create bridging data required for an OSM zoning reader to successfully parse the Pt zones
   *  based on the osm network parsed by this network reader. Without this data it is not possible to relate the two
   *  properly
   *  
   * @return created network to zoning reader data to use
   */
  public OsmNetworkToZoningReaderData createNetworkToZoningReaderData() {
    if(getOsmNetworkToPopulate().getTransportLayers().size()==0 || getOsmNetworkToPopulate().getTransportLayers().getFirst().isEmpty()) {
      LOGGER.warning("Can only perform network->zoning data transfer when network has been populated by OSM network reader, i.e., first invoke the read() method before this call");
      return null;
    }

    /* DTO */
    OsmNetworkToZoningReaderData network2zoningData = new OsmNetworkToZoningReaderData(networkData, getSettings());
        
    /* layer specific data references */
    for(Entry<MacroscopicNetworkLayer, OsmNetworkLayerParser> entry : networkData.getLayerParsers().entrySet()){
      OsmNetworkLayerParser layerHandler = entry.getValue();
      network2zoningData.registerLayerData(entry.getKey(), layerHandler.getLayerData());
    }
    
    return network2zoningData;
  }

}

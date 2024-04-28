package org.goplanit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.locationtech.jts.geom.Envelope;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class that hosts all the data gathered (e.g., references, mappings, etc.) during the parsing of the OSM network
 * that is also of use to the OSM zoning reader. It is used by the intermodal OSM reader to pass this information along in 
 * an elegant fashion.
 * 
 * @author markr
 *
 */
public class OsmNetworkToZoningReaderData {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkToZoningReaderData.class.getCanonicalName());
  
  /** the network reader settings used */
  private final OsmNetworkReaderSettings networkReaderSettings;
    
  /** data from the network reader collected during parsing */
  private final OsmNetworkReaderData networkData;  
  
  /** layer specific data that is to be made available to the zoning reader */
  private final Map<NetworkLayer, OsmNetworkReaderLayerData> networkLayerData = new HashMap<NetworkLayer, OsmNetworkReaderLayerData>();
  
  /** register layer specific data
   * @param networkLayer to register for
   * @param layerData the data to register
   */
  protected void registerLayerData(MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData) {
    networkLayerData.put(networkLayer, layerData);    
  }   
  
  /** Constructor
   * 
   * @param networkData to use
   * @param networkReaderSettings to use
   */
  protected OsmNetworkToZoningReaderData(final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings networkReaderSettings) {
    if(networkData==null) {
      LOGGER.severe("Network data provided to PlanitOsmNetworkToZoningReaderData constructor null");
    }
    if(networkReaderSettings==null) {
      LOGGER.severe("Network reader settings provided to PlanitOsmNetworkToZoningReaderData constructor null");
    }
    this.networkData = networkData;
    this.networkReaderSettings = networkReaderSettings;
  }
    
  /** Collect layer specific data
   * 
   * @param networkLayer to collect for
   * @return layer data
   */
  public OsmNetworkReaderLayerData  getNetworkLayerData(NetworkLayer networkLayer) {
    OsmNetworkReaderLayerData data =  networkLayerData.get(networkLayer);
    return data;
  }

  /** collect the bounding box of the network that is parsed
   * 
   * @return network bounding box
   */
  public Envelope getNetworkBoundingBox() {
    return networkData.getNetworkSpanningBoundingBox();
  }

  /**
   * Access to bounding boundary of the network
   *
   * @return network bounding boundary
   */
  public OsmBoundary getNetworkBoundingBoundary() {
    return networkData.getBoundingArea();
  }
  
  /** network reader settings as used for populating the planti network absed on osm data
   * @return network reader settings used
   */
  public OsmNetworkReaderSettings getNetworkSettings() {
    return networkReaderSettings;
  }

  /**
   * Collect the retained OSM nodes used to extract PLANit network infrastructure
   *
   * @return retained OSM  nodes
   */
  public Map<Long, OsmNode> getNetworkOsmNodes(){
    return networkData.getOsmNodeData().getRegisteredOsmNodes();
  }

  /**
   * Register additional OSM nodes as being part of the network after the network has been parsed.
   * This may happen when we artificially expand the network while identifying OSM nodes that should in
   * fact be part of the network, such as dangling ferry stops that we want to connect.
   *
   * @param osmNodeId  node to register
   */
  public void preRegisterOsmNode(long osmNodeId){
    networkData.getOsmNodeData().preRegisterEligibleOsmNode(osmNodeId);
  }

  /**
   * Register additional OSM nodes as being part of the network after the network has been parsed.
   * This may happen when we artificially expand the network while identifying OSM nodes that should in
   * fact be part of the network, such as dangling ferry stops that we want to connect.
   *
   * @param osmNode  node to register
   */
  public void registerNetworkOsmNode(OsmNode osmNode){
    networkData.getOsmNodeData().registerEligibleOsmNode(osmNode);
  }

  /**
   * Verify if an OSM way is processed but identified as unavailable. Any subsequent dependencies on this OSM way
   * can be safely ignored without issuing further warnings
   *
   * @param osmWayId to verify
   * @return true when processed and unavailable, false otherwise
   */
  public boolean isOsmWayProcessedAndUnavailable(long osmWayId){
    return networkData.isOsmWayProcessedAndUnavailable(osmWayId);
  }

}

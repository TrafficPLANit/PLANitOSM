package org.goplanit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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

  /** Pre-register an OSM node for future population with the actual node contents (see {@link #registerEligibleOsmNode(OsmNode)}
   * @param osmNodeId to pre-register
   */
  public void preRegisterEligibleOsmNode(long osmNodeId) {
    networkData.preRegisterEligibleOsmNode(osmNodeId);
  }

  /** Add the actual OSM node to an already eligible marked OSM node entry
   * @param osmNode to register
   */
  public void registerEligibleOsmNode(OsmNode osmNode) {
    networkData.registerEligibleOsmNode(osmNode);
  }

  /** Verify if OSM node pre-registered while actual node may not yet be available
   * @param osmNodeId to verify
   * @return true when pre-registered, false otherwise
   */
  public boolean isPreRegisteredOsmNode(long osmNodeId) {
    return networkData.containsPreRegisteredOsmNode(osmNodeId);
  }

  /** Verify if OSM node itself is registered and available
   * @param osmNodeId to verify
   * @return true when available, false otherwise
   */
  public boolean containsOsmNode(long osmNodeId) {
    return networkData.containsOsmNode(osmNodeId);
  }

  /** Collect an OSM node
   * @param osmNodeId to collect
   * @return osm node, null if not present
   */
  public OsmNode getRegisteredOsmNode(long osmNodeId) {
    return networkData.getOsmNode(osmNodeId);
  }

  /** Collect the OSM nodes (unmodifiable)
   *
   * @return registered OSM nodes
   */
  public Map<Long,OsmNode> getRegisteredOsmNodes() {
    return networkData.getRegisteredOsmNodes();
  }

  /** collect the bounding box of the network that is parsed
   * 
   * @return network bounding box
   */
  public Envelope getNetworkBoundingBox() {
    return networkData.getBoundingBox();
  }
  
  /** network reader settings as used for populating the planti network absed on osm data
   * @return network reader settings used
   */
  public OsmNetworkReaderSettings getNetworkSettings() {
    return networkReaderSettings;
  }

}

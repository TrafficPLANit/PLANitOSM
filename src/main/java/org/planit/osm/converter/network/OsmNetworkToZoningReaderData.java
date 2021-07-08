package org.planit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Envelope;
import org.planit.utils.network.layer.MacroscopicNetworkLayer;
import org.planit.utils.network.layer.TransportLayer;

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
  private final Map<TransportLayer, OsmNetworkReaderLayerData> networkLayerData = new HashMap<TransportLayer, OsmNetworkReaderLayerData>();
  
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
      LOGGER.severe("network data provided to PlanitOsmNetworkToZoningReaderData constructor null");
    }
    if(networkReaderSettings==null) {
      LOGGER.severe("network reader settings provided to PlanitOsmNetworkToZoningReaderData constructor null");
    }
    this.networkData = networkData;
    this.networkReaderSettings = networkReaderSettings;
  }
    
  /** Collect layer specific data
   * 
   * @param networkLayer to collect for
   * @return layer data
   */
  public OsmNetworkReaderLayerData  getNetworkLayerData(TransportLayer networkLayer) {
    OsmNetworkReaderLayerData data =  networkLayerData.get(networkLayer);
    return data;
  }  
  
  /** Collect OSM nodes
   * 
   * @return osm nodes
   */
  public Map<Long, OsmNode> getOsmNodes() {
    return networkData.getOsmNodes();
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

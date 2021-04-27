package org.planit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Envelope;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class that hosts all the data gathered (e.g., references, mappings, etc.) during the parsing of the OSM network
 * that is also of use to the OSM zoning reader. It is used by the intermodal OSM reader to pass this information along in 
 * an elegant fashion.
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkToZoningReaderData {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkToZoningReaderData.class.getCanonicalName());
  
  /** the network reader settings used */
  private final PlanitOsmNetworkReaderSettings networkReaderSettings;
    
  /** data from the network reader collected during parsing */
  private final PlanitOsmNetworkReaderData networkData;  
  
  /** layer specific data that is to be made available to the zoning reader */
  private final Map<InfrastructureLayer, PlanitOsmNetworkReaderLayerData> networkLayerData = new HashMap<InfrastructureLayer, PlanitOsmNetworkReaderLayerData>();
  
  /** register layer specific data
   * @param networkLayer to register for
   * @param layerData the data to register
   */
  protected void registerLayerData(MacroscopicPhysicalNetwork networkLayer, PlanitOsmNetworkReaderLayerData layerData) {
    networkLayerData.put(networkLayer, layerData);    
  }   
  
  /** Constructor
   * @param networkData to use
   * @param settings to use
   */
  protected PlanitOsmNetworkToZoningReaderData(final PlanitOsmNetworkReaderData networkData, final PlanitOsmNetworkReaderSettings networkReaderSettings) {
    if(networkData==null) {
      LOGGER.severe("network data provided to PlanitOsmNetworkToZoningReaderData constructor null");
    }
    if(networkReaderSettings==null) {
      LOGGER.severe("network reader settings provided to PlanitOsmNetworkToZoningReaderData constructor null");
    }
    this.networkData = networkData;
    this.networkReaderSettings = networkReaderSettings;
  }
    
  /** collect layer specific data
   * @param networkLayer to collect for
   * @return layer data
   */
  public PlanitOsmNetworkReaderLayerData  getNetworkLayerData(InfrastructureLayer networkLayer) {
    PlanitOsmNetworkReaderLayerData data =  networkLayerData.get(networkLayer);
    return data;
  }  
  
  /** collect osm nodes
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
  public PlanitOsmNetworkReaderSettings getNetworkSettings() {
    return networkReaderSettings;
  }
    
}

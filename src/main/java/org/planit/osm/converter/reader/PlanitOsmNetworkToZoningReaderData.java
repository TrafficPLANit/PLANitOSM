package org.planit.osm.converter.reader;

import java.util.HashMap;
import java.util.Map;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class that hosts all the data gathered (e.g., references, mappings, etc.) during the parsing of the OSM network
 * that is also of use to the OSM zoning reader. It is used by the intermodal OSM reader to pass this informatino along in 
 * an elegant fashion.
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkToZoningReaderData {
  
  /** settings used in network reader */
  private PlanitOsmNetworkSettings settings;
  
  /** data from the network reader collected during parsing */
  private PlanitOsmNetworkReaderData networkData;  
  
  /** layer specific data that is to be made available to the zoning reader */
  private Map<InfrastructureLayer, PlanitOsmNetworkReaderLayerData> networkLayerData = new HashMap<InfrastructureLayer, PlanitOsmNetworkReaderLayerData>(); 

  public PlanitOsmNetworkSettings getSettings() {
    return settings;
  }
  
  /** Constructor
   * @param networkData to use
   * @param settings to use
   */
  protected PlanitOsmNetworkToZoningReaderData(PlanitOsmNetworkReaderData networkData, PlanitOsmNetworkSettings settings) {
    this.networkData = networkData;
    this.settings = settings;
  }


  /** collect network
   * @return osm network
   */
  public PlanitOsmNetwork getOsmNetwork() {
    return networkData.getOsmNetwork();
  }
    
  public PlanitOsmNetworkReaderLayerData  getNetworkLayerData(InfrastructureLayer networkLayer) {
    PlanitOsmNetworkReaderLayerData data =  networkLayerData.get(networkLayer);
    return data;
  }
  
  public void registerLayerData(MacroscopicPhysicalNetwork networkLayer, PlanitOsmNetworkReaderLayerData layerData) {
    networkLayerData.put(networkLayer, layerData);    
  }  
  
  /** collect osm nodes
   * @return osm nodes
   */
  public Map<Long, OsmNode> getOsmNodes() {
    return networkData.getOsmNodes();
  }
    
}

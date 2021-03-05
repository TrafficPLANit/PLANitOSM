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
  
  /** populated network  */
  private PlanitOsmNetwork osmNetwork;
  
  /** all osm nodes in the osm network across layers */
  private Map<Long, OsmNode> osmNodes = new HashMap<Long, OsmNode>();  
  
  /** layer specific data that is to be made available to the zoning reader */
  private Map<InfrastructureLayer, PlanitOsmNetworkLayerReaderData> networkLayerData = new HashMap<InfrastructureLayer, PlanitOsmNetworkLayerReaderData>(); 

  public PlanitOsmNetworkSettings getSettings() {
    return settings;
  }

  public void setSettings(PlanitOsmNetworkSettings settings) {
    this.settings = settings;
  }

  public PlanitOsmNetwork getOsmNetwork() {
    return osmNetwork;
  }

  public void setOsmNetwork(PlanitOsmNetwork osmNetwork) {
    this.osmNetwork = osmNetwork;
  }
    
  public PlanitOsmNetworkLayerReaderData  getNetworkLayerData(InfrastructureLayer networkLayer) {
    PlanitOsmNetworkLayerReaderData data =  networkLayerData.get(networkLayer);
    return data;
  }
  
  public void registerLayerData(MacroscopicPhysicalNetwork networkLayer, PlanitOsmNetworkLayerReaderData layerData) {
    networkLayerData.put(networkLayer, layerData);    
  }  

  public void setOsmNodes(Map<Long, OsmNode> osmNodes) {
    this.osmNodes= osmNodes;
  }
  
  public Map<Long, OsmNode> getOsmNodes() {
    return this.osmNodes;
  }
    
}

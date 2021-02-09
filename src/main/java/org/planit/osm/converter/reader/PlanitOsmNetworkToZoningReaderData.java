package org.planit.osm.converter.reader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.planit.network.InfrastructureLayer;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;

/**
 * Class that hosts all the data gathered (e.g., references, mappings, etc.) during the parsing of the OSM network
 * that is also of use to the OSM zoning reader. It is used by the intermodal OSM reader to pass this informatino along in 
 * an elegant fashion.
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkToZoningReaderData {
  
  /**
   * layer specific data
   * 
   * @author markr
   *
   */
  public class NetworkLayerData {
    
    /**
     * the PLANit nodes created on a layer by their original OSM id so they can by looked up quickly
     */
    private Map<Long, Node> nodesByOsmId = new HashMap<Long, Node>();
    
    /**
     * should provide all osm node ids that are internal to parsed links. Note that there should only
     * be a single link in the list, it is however a list because while being parsed in the network reader, it might
     * have been more than a single link at some point (resulting in broken links)
     */
    private Map<Long, List<Link>> osmNodeIdsInternalToLink = new HashMap<Long, List<Link>>();
    
    
    public final Map<Long, Node> getNodesByOsmId() {
      return nodesByOsmId;
    }

    public void setNodesByOsmId(final Map<Long, Node> nodesByOsmId) {
      this.nodesByOsmId = nodesByOsmId;
    }

    public void setOsmNodeIdsInternalToLink(final Map<Long, List<Link>> osmNodeIdsInternalToLink) {
      this.osmNodeIdsInternalToLink = osmNodeIdsInternalToLink;      
    }
    
    public Map<Long, List<Link>> getOsmNodeIdsInternalToLink() {
      return this.osmNodeIdsInternalToLink;      
    }        
  }

  /** settings used in network reader */
  private PlanitOsmNetworkSettings settings;
  
  /** populated network  */
  private PlanitOsmNetwork osmNetwork;
  
  /** layer specific data that is to be made available to the zoning reader */
  private Map<InfrastructureLayer, NetworkLayerData> networkLayerData = new HashMap<InfrastructureLayer, NetworkLayerData>(); 

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
    
  public NetworkLayerData  getNetworkLayerData(InfrastructureLayer networkLayer) {
    NetworkLayerData data =  networkLayerData.get(networkLayer);
    if( data == null) {
      return registerNewLayerData(networkLayer);
    }
    return data;
  }

  public NetworkLayerData registerNewLayerData(InfrastructureLayer networkLayer) {
    networkLayerData.put(networkLayer, new NetworkLayerData());
    return networkLayerData.get(networkLayer);    
  }  
}

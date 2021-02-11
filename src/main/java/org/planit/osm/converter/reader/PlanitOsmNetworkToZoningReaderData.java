package org.planit.osm.converter.reader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.planit.network.InfrastructureLayer;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;

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
    
    /**
     * tracks all the osm wayids for which more than one planit link has been generated (due to breaking links, cicrular ways etc.). This is useful since
     * in that case we can no longer directly find a link by its external id, since multiple planit links with the same external id exist
     */
    private Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks = new HashMap<Long, Set<Link>>();
    
    
    public final Map<Long, Node> getPlanitNodesByOsmId() {
      return nodesByOsmId;
    }

    public void setPlanitNodesByOsmId(final Map<Long, Node> nodesByOsmId) {
      this.nodesByOsmId = nodesByOsmId;
    }

    public void setOsmNodeIdsInternalToLink(final Map<Long, List<Link>> osmNodeIdsInternalToLink) {
      this.osmNodeIdsInternalToLink = osmNodeIdsInternalToLink;      
    }
    
    public Map<Long, List<Link>> getOsmNodeIdsInternalToLink() {
      return this.osmNodeIdsInternalToLink;      
    }

    public void setOsmWaysWithMultiplePlanitLinks(Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks) {
      this.osmWaysWithMultiplePlanitLinks = osmWaysWithMultiplePlanitLinks;      
    }
    
    public Map<Long, Set<Link>> getOsmWaysWithMultiplePlanitLinks() {
      return this.osmWaysWithMultiplePlanitLinks;      
    }            
  }

  /** settings used in network reader */
  private PlanitOsmNetworkSettings settings;
  
  /** populated network  */
  private PlanitOsmNetwork osmNetwork;
  
  /** all osm nodes in the osm network */
  private Map<Long, OsmNode> osmNodes = new HashMap<Long, OsmNode>();  
  
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

  public void setOsmNodes(Map<Long, OsmNode> osmNodes) {
    this.osmNodes= osmNodes;
  }
  
  public Map<Long, OsmNode> getOsmNodes() {
    return this.osmNodes;
  }    
}

package org.goplanit.osm.util;

import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.network.OsmNetworkLayerParser;
import org.goplanit.osm.converter.network.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.utils.network.layer.NetworkLayer;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Utilities regarding PLANit network layers with respect to parsing OSM netities for it 
 * 
 * @author markr
 *
 */
public class PlanitNetworkLayerUtils {

  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @param network to consider
   * @param networkToZoningData to extract layer specific data from
   * @return true when one or more layers are found, false otherwise
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(
      long osmNodeId, LayeredNetwork<?, ?> network, OsmNetworkToZoningReaderData networkToZoningData){    
    OsmNode osmNode = networkToZoningData.getRegisteredOsmNodes().get(osmNodeId);
    if(osmNode != null) {
      for(NetworkLayer networkLayer : network.getTransportLayers()) {        
        if(networkToZoningData.getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @param network to consider
   * @param networkData to extract layer specific data from
   * @return true when one or more layers are found, false otherwise
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId, LayeredNetwork<?, ?> network, OsmNetworkReaderData networkData){    
    OsmNode osmNode = networkData.getOsmNode(osmNodeId);
    if(osmNode != null) {      
      for(NetworkLayer networkLayer : network.getTransportLayers()) {
        OsmNetworkLayerParser layerHandler = networkData.getLayerParser((MacroscopicNetworkLayerImpl) networkLayer);
        if(layerHandler.getLayerData().isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }  
}

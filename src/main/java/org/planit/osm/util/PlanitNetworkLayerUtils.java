package org.planit.osm.util;

import org.planit.network.TransportLayerNetwork;
import org.planit.network.layer.macroscopic.MacroscopicPhysicalLayer;
import org.planit.osm.converter.network.OsmNetworkLayerParser;
import org.planit.osm.converter.network.OsmNetworkReaderData;
import org.planit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.network.layer.TransportLayer;

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
   * @throws PlanItException thrown if error
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(
      long osmNodeId, TransportLayerNetwork<?, ?> network, OsmNetworkToZoningReaderData networkToZoningData) throws PlanItException {    
    OsmNode osmNode = networkToZoningData.getOsmNodes().get(osmNodeId);
    if(osmNode != null) {
      for(TransportLayer networkLayer : network.transportLayers) {        
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
   * @throws PlanItException thrown if error
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId, TransportLayerNetwork<?, ?> network, OsmNetworkReaderData networkData) throws PlanItException {    
    OsmNode osmNode = networkData.getOsmNode(osmNodeId);
    if(osmNode != null) {      
      for(TransportLayer networkLayer : network.transportLayers) {
        OsmNetworkLayerParser layerHandler = networkData.getLayerParser((MacroscopicPhysicalLayer) networkLayer);
        if(layerHandler.getLayerData().isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }  
}

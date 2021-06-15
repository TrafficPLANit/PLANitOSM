package org.planit.osm.util;

import org.planit.network.InfrastructureLayer;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.OsmNetworkLayerParser;
import org.planit.osm.converter.network.OsmNetworkReaderData;
import org.planit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.planit.utils.exceptions.PlanItException;

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
      long osmNodeId, InfrastructureNetwork<?, ?> network, OsmNetworkToZoningReaderData networkToZoningData) throws PlanItException {    
    OsmNode osmNode = networkToZoningData.getOsmNodes().get(osmNodeId);
    if(osmNode != null) {
      for(InfrastructureLayer networkLayer : network.infrastructureLayers) {        
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
  public static boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId, InfrastructureNetwork<?, ?> network, OsmNetworkReaderData networkData) throws PlanItException {    
    OsmNode osmNode = networkData.getOsmNode(osmNodeId);
    if(osmNode != null) {      
      for(InfrastructureLayer networkLayer : network.infrastructureLayers) {
        OsmNetworkLayerParser layerHandler = networkData.getLayerParser((MacroscopicPhysicalNetwork) networkLayer);
        if(layerHandler.getLayerData().isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }  
}

package org.goplanit.osm.util;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.network.OsmNetworkLayerParser;
import org.goplanit.osm.converter.network.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Utilities regarding PLANit network layers with respect to parsing OSM netities for it 
 * 
 * @author markr
 *
 */
public class PlanitNetworkLayerUtils {

  private static final Logger LOGGER = Logger.getLogger(PlanitNetworkLayerUtils.class.getCanonicalName());

  /** Collect the layers where the OSM node is active in either as an extreme node or internal to a PLANit link
   *
   * @param osmNodeId to use
   * @param network to consider
   * @param networkToZoningData to extract layer specific data from
   * @return true when one or more layers are found, false otherwise
   */
  public static List<? extends NetworkLayer> getNetworkLayersWithActiveOsmNode(
      long osmNodeId, LayeredNetwork<?, ?> network, OsmNetworkToZoningReaderData networkToZoningData){
    OsmNode osmNode = networkToZoningData.getNetworkOsmNodes().get(osmNodeId);

    ArrayList<NetworkLayer> foundLayers = new ArrayList<>(1);
    if(osmNode != null) {
      for(NetworkLayer networkLayer : network.getTransportLayers()) {
        if(networkToZoningData.getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)){
          foundLayers.add(networkLayer);
        }
      }
    }
    return foundLayers;
  }

  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @param network to consider
   * @param networkToZoningData to extract layer specific data from
   * @return true when one or more layers are found, false otherwise
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(
      long osmNodeId, LayeredNetwork<?, ?> network, OsmNetworkToZoningReaderData networkToZoningData){    
    return !getNetworkLayersWithActiveOsmNode(osmNodeId, network, networkToZoningData).isEmpty();
  }
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @param network to consider
   * @param networkData to extract layer specific data from
   * @return true when one or more layers are found, false otherwise
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId, LayeredNetwork<?, ?> network, OsmNetworkReaderData networkData){
    OsmNode osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
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

  /**
   * Default way to create a link segment (and register it ) in OSM
   *
   * @param link link to create it on
   * @param directionAb direction of the segment
   * @param linkSegmentType type to apply
   * @param networkLayer to register on
   * @param speedLimit to apply
   * @param numLanes to apply
   * @return  created link segment
   */
  public static MacroscopicLinkSegment createPopulateAndRegisterLinkSegment(
      MacroscopicLink link,
      boolean directionAb,
      MacroscopicLinkSegmentType linkSegmentType,
      Double speedLimit,
      Integer numLanes,
      MacroscopicNetworkLayer networkLayer){
    MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) link.getEdgeSegment(directionAb);
    if(linkSegment == null) {
      linkSegment = networkLayer.getLinkSegments().getFactory().registerNew(link, directionAb, true /*register on nodes and link*/);
      /* Xml id */
      linkSegment.setXmlId(Long.toString(linkSegment.getId()));
      /* external id, identical to link since OSM has no directional ids */
      linkSegment.setExternalId(link.getExternalId());

      /* default max speed limit */
      linkSegment.setPhysicalSpeedLimitKmH(speedLimit);

      /* number of lanes */
      linkSegment.setNumberOfLanes(numLanes);

    }else{
      LOGGER.warning(String.format(
          "Already exists link segment (id:%d) between OSM nodes (%s, %s) of OSM way (%d), ignored entity",linkSegment.getId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), link.getExternalId()));
    }

    /* link segment type */
    linkSegment.setLinkSegmentType(linkSegmentType);

    return linkSegment;
  }

  /**
   * Default way to create a link (and register it on its nodes) in OSM
   *
   * @param nodeA to use
   * @param nodeB to use
   * @param geometry to set
   * @param layer to register on
   * @param externalId to set (optional)
   * @param name to set (optional)
   * @param geoUtils to use
   * @return created and registered link
   */
  public static MacroscopicLink createPopulateAndRegisterLink(
      Node nodeA,
      Node nodeB,
      LineString geometry,
      MacroscopicNetworkLayer layer,
      String externalId,
      String name,
      PlanitJtsCrsUtils geoUtils){

    /* length and geometry */
    double linkLength = 0;
    /* update the length based on the geometry */
    linkLength = geoUtils.getDistanceInKilometres(geometry);

    /* create link */
    var link = layer.getLinks().getFactory().registerNew(nodeA, nodeB, linkLength, true);
    /* geometry */
    link.setGeometry(geometry);
    /* XML id */
    link.setXmlId(Long.toString(link.getId()));

    /* external id (may be null) */
    link.setExternalId(externalId);
    /* name (may be null) */
    link.setName(name);

    return link;
  }

  /**
   * Extract a PLANit node from the osmNode information
   *
   * @param osmNode to create PLANit node for
   * @param networkLayer to create node on
   * @return created node, null when something went wrong
   */
  public static Node createAndPopulateNode(OsmNode osmNode, MacroscopicNetworkLayer networkLayer)  {
    if(osmNode == null || networkLayer == null) {
      LOGGER.severe("No OSM node or network layer provided when creating new PLANit node, ignore");
      return null;
    }

    /* geometry */
    Point geometry = OsmNodeUtils.createPoint(osmNode);

    /* create node */
    Node node = networkLayer.getNodes().getFactory().registerNew(geometry, true);

    /* external id */
    node.setExternalId(String.valueOf(osmNode.getId()));

    return node;
  }

  /**
   * Extract a PLANit node from the osmNode information and register it on the provided layer
   *
   * @param osmNode to create PLANit node for
   * @param networkLayer to create node on
   * @param layerData to register on
   * @return created node, null when something went wrong
   */
  public static Node createPopulateAndRegisterNode(
      OsmNode osmNode, MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData){

    /* create */
    Node node = createAndPopulateNode(osmNode, networkLayer);
    if(node!= null) {
      /* register */
      layerData.registerPlanitNodeByOsmNode(osmNode, node);
      layerData.getProfiler().logNodeStatus(networkLayer.getNumberOfNodes());
    }
    return node;
  }

  /**
   * Extract a PLANit node from the location information and register it on the provided layer
   *
   * @param osmNodeLocation to create PLANit node for
   * @param networkLayer to create node on
   * @param layerData to register on
   * @return created node, null when something went wrong
   */
  public static Node createPopulateAndRegisterNode(
      Point osmNodeLocation, MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData){
    /* create */
    Node node = networkLayer.getNodes().getFactory().registerNew(osmNodeLocation, true);
    if(node!= null) {
      /* register */
      layerData.registerPlanitNodeByLocation(osmNodeLocation, node);
      layerData.getProfiler().logNodeStatus(networkLayer.getNumberOfNodes());
    }
    return node;
  }
}

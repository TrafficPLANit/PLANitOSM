package org.goplanit.osm.converter.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Helper class for the OSM network handlers, providing static helper methods that reflect common code across various
 * handlers that are not considered general enough to be part of a utility class.
 * 
 * @author markr
 *
 */
public class OsmNetworkHandlerHelper {
  
  /** the logger to use */
  public static final Logger LOGGER = Logger.getLogger(OsmNetworkHandlerHelper.class.getCanonicalName());
  
  /** to be able to retain the information on the osm way type to be able to identify the importance of an osm way compared to others we use the osm way type
   * and store it as an input property on the link using this key 
   */
  protected static final String LINK_OSMWAY_TYPE_PROPERTY_KEY = "osm_way_type";

  /** to be able to retain the information on the vertical layer index so we can map it to infrastructure at the same index level we
   *  store it as an input property on the link using this key
   */
  protected static final String LINK_OSM_LAYER_PROPERTY_KEY = "osm_vertical_layer_index";
  
  /** set the OSM way type
   * @param link to set for
   * @param osmWayType to use
   */
  public static void setLinkOsmWayType(Link link, String osmWayType) {
    link.addInputProperty(LINK_OSMWAY_TYPE_PROPERTY_KEY, osmWayType);
  }
  
  /** Collect the OSM way type of the link
   * @param link to collect from
   * @return osm way type, null if not present
   */
  public static String getLinkOsmWayType(Link link) {
    Object value = link.getInputProperty(LINK_OSMWAY_TYPE_PROPERTY_KEY);
    if(value != null) {
      return (String)value;
    }
    return null;
  }

  /** Collect the OSM vertical layer index for the link
   * @param link to collect from
   * @return vertical layer index, defaults to 0 if not explicitly registered
   */
  public static int getLinkVerticalLayerIndex(Link link) {
    Object value = link.getInputProperty(LINK_OSM_LAYER_PROPERTY_KEY);
    return value == null ? 0 : (Integer) value;
  }

  /** Set the OSM vertical layer index for the link based on its OSM tags
   * @param link to set index for
   * @param  tags to extract index from, if absent, OSM default of 0 is implicitly assumed
   */
  public static void setLinkVerticalLayerIndex(MacroscopicLink link, Map<String, String> tags) {
    if(!OsmTagUtils.containsAnyKey(tags, OsmTags.LAYER)){
      /* no layer tag, so default applies, which we do not explicitly store */
      return;
    }
    var layerValue = OsmTagUtils.getValueAsInt(tags, OsmTags.LAYER);
    link.addInputProperty(LINK_OSM_LAYER_PROPERTY_KEY, layerValue);
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
      LOGGER.severe("no OSM node or network layer provided when creating new PLANit node, ignore");
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
  public static Node createPopulateAndRegisterNode(Point osmNodeLocation, MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData){
    /* create */
    Node node = networkLayer.getNodes().getFactory().registerNew(osmNodeLocation, true);
    if(node!= null) {
      /* register */
      layerData.registerPlanitNodeByLocation(osmNodeLocation, node);       
      layerData.getProfiler().logNodeStatus(networkLayer.getNumberOfNodes());
    }
    return node;
  }  
   

  /** add addition to destination
   * @param addition to add
   * @param destination to add to
   */
  public static void addAllTo(Map<Long, Set<MacroscopicLink>> addition, Map<Long, Set<MacroscopicLink>> destination) {
    addition.forEach( (osmWayId, links) -> {
      destination.putIfAbsent(osmWayId, new HashSet<>());
      destination.get(osmWayId).addAll(links);
    });
  }

}

package org.planit.osm.converter.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Point;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.modifier.BreakEdgeListener;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Helper class for the OSM network handlers, providing static helper methods that reflect common code across various
 * handlers that are not considered general enough to be part of a utility class.
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkHandlerHelper {
  
  /** the logger to use */
  public static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkHandlerHelper.class.getCanonicalName());
  
  /** to be able to retain the information on the osm way type to be able to identify the importance of an osm way compared to others we use the osm way type
   * and store it as an input property on the link using this key 
   */
  protected static final String LINK_OSMWAY_TYPE_PROPERTY_KEY = "osmwaytype";
  
  /** set the osm way type
   * @param link to set for
   * @param osmWayType to use
   */
  public static void setLinkOsmWayType(Link link, String osmWayType) {
    link.addInputProperty(LINK_OSMWAY_TYPE_PROPERTY_KEY, osmWayType);
  }
  
  /** Collect the osm way type of the link
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
    
  /** Check if we should break any links for the passed in node and if so, do it
   * 
   * @param theNode to verify
   * @param linksToBreak contains the links that (at least at some point) had theNode as an internal link
   * @param networkLayer the node resides on
   * @param crs of the network layer 
   * @param breakLinkListeners to apply when breaking edges
   * @return newly broken planit links by their original osmWayId 
   *  
   * @throws PlanItException thrown if error
   */
  public static Map<Long, Set<Link>> breakLinksWithInternalNode(
      Node theNode, List<Link> linksToBreak, MacroscopicPhysicalNetwork networkLayer, CoordinateReferenceSystem crs, Set<BreakEdgeListener<Node, Link>> breakLinkListeners) throws PlanItException {
    Map<Long, Set<Link>> newOsmWaysWithMultiplePlanitLinks = new HashMap<Long, Set<Link>>();
    
    if(linksToBreak != null) {
            
      try {
        /* performing breaking of links at the node given, returns the broken links by the original link's PLANit edge id */
        Map<Long, Set<Link>> localBrokenLinks = networkLayer.breakLinksAt(linksToBreak, theNode, crs, breakLinkListeners);                 
        /* add newly broken links to the mapping from original external OSM link id, to the broken link that together form this entire original OSMway*/      
        if(localBrokenLinks != null) {
          localBrokenLinks.forEach((id, links) -> {
            links.forEach( brokenLink -> {
              Long brokenLinkOsmId = Long.parseLong(brokenLink.getExternalId());
              newOsmWaysWithMultiplePlanitLinks.putIfAbsent(brokenLinkOsmId, new HashSet<Link>());
              newOsmWaysWithMultiplePlanitLinks.get(brokenLinkOsmId).add(brokenLink);
            });
          });        
        }
      }catch(PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("unable to break links %s for node %s, something unexpected went wrong",
            linksToBreak.stream().map( link -> link.getExternalId()).collect(Collectors.toSet()).toString(), theNode.getExternalId()));
      }
    } 
        
    return newOsmWaysWithMultiplePlanitLinks;
  }
  
  /**
   * Extract a PLANit node from the osmNode information
   * 
   * @param osmNode to create PLANit node for
   * @param networkLayer to create node on
   * @return created node, null when something went wrong
   */
  public static Node createAndPopulateNode(OsmNode osmNode, MacroscopicPhysicalNetwork networkLayer)  {
    if(osmNode == null || networkLayer == null) {
      LOGGER.severe("no OSM node or network layer provided when creating new PLANit node, ignore");
      return null;
    }
    
    /* geometry */
    Point geometry = PlanitOsmNodeUtils.createPoint(osmNode);
    
    Node node = createAndPopulateNode(geometry, networkLayer);
    
    /* external id */
    node.setExternalId(String.valueOf(osmNode.getId()));
    
    return node;
  }
  
  /**
   * Extract a PLANit node from the osmNode information
   * 
   * @param geometry to place on PLANit node
   * @param networkLayer to create node on
   * @return created node, null when something went wrong
   */
  public static Node createAndPopulateNode(Point geometry, MacroscopicPhysicalNetwork networkLayer)  {
    /* create and register */
    Node node = networkLayer.nodes.registerNew();
    
    /* XML id */
    node.setXmlId(Long.toString(node.getId()));

    /* position */
    node.setPosition(geometry);
    
    return node;
  }  
  
  /**
   * Extract a PLANit node from the osmNode information and register it on the provided layer
   * 
   * @param osmNode to create PLANit node for
   * @param networkLayer to create node on
   * @param layerData to register on
   * @return created node, null when something went wrong
   * @throws PlanItException thrown if error
   */
  public static Node createPopulateAndRegisterNode(
      OsmNode osmNode, MacroscopicPhysicalNetwork networkLayer, PlanitOsmNetworkReaderLayerData layerData) throws PlanItException  {
    
    /* create */
    Node node = createAndPopulateNode(osmNode, networkLayer);            
    if(node!= null) {
      /* register */
      layerData.registerPlanitNodeByOsmNode(osmNode, node);       
      layerData.getProfiler().logNodeStatus(networkLayer.nodes.size());
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
   * @throws PlanItException thrown if error
   */  
  public static Node createPopulateAndRegisterNode(Point osmNodeLocation, MacroscopicPhysicalNetwork networkLayer,
      PlanitOsmNetworkReaderLayerData layerData) throws PlanItException {
    /* create */
    Node node = createAndPopulateNode(osmNodeLocation, networkLayer);            
    if(node!= null) {
      /* register */
      layerData.registerPlanitNodeByLocation(osmNodeLocation, node);       
      layerData.getProfiler().logNodeStatus(networkLayer.nodes.size());
    }
    return node;
  }  
   

  /** add addition to destination
   * @param addition to add
   * @param destination to add to
   */
  public static void addAllTo(Map<Long, Set<Link>> addition, Map<Long, Set<Link>> destination) {
    addition.forEach( (osmWayId, links) -> {
      destination.putIfAbsent(osmWayId, new HashSet<Link>());
      destination.get(osmWayId).addAll(links);
    });
  }




  
}

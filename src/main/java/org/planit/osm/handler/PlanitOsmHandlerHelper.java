package org.planit.osm.handler;

import java.util.Collection;
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
import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.DirectedVertex;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.zoning.ZoningModifier;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Helper class for the OSM handlers, providing static helper methods that reflect common code across various
 * handlers that are not considered general enough to be part of a utility class.
 * 
 * @author markr
 *
 */
public class PlanitOsmHandlerHelper {
  
  /** the logger to use */
  public static final Logger LOGGER = Logger.getLogger(PlanitOsmHandlerHelper.class.getCanonicalName());
    
  /** find all already registered directed connectoids that reference a link segment part of the passed in link in the given network layer
   * 
   * @param link to find referencing directed connectoids for
   * @param knownConnectoidsByLocation all connectoids of the network layer indexed by their location
   * @return all identified directed connectoids
   */
  protected static Collection<DirectedConnectoid> findDirectedConnectoidsRefencingLink(Link link, Map<Point, Set<DirectedConnectoid>> knownConnectoidsByLocation) {
    Collection<DirectedConnectoid> referencingConnectoids = new HashSet<DirectedConnectoid>();
    /* find eligible locations for connectoids based on downstream locations of link segments on link */
    Set<Point> eligibleLocations = new HashSet<Point>();
    if(link.hasEdgeSegmentAb()) {      
      eligibleLocations.add(link.getEdgeSegmentAb().getDownstreamVertex().getPosition());
    }
    if(link.hasEdgeSegmentBa()) {
      eligibleLocations.add(link.getEdgeSegmentBa().getDownstreamVertex().getPosition());
    }
    
    /* find all directed connectoids with link segments that have downstream locations matching the eligible locations identified*/
    for(Point location : eligibleLocations) {
      Set<DirectedConnectoid> knownConnectoidsForLink = knownConnectoidsByLocation.get(location);
      if(knownConnectoidsForLink != null && !knownConnectoidsForLink.isEmpty()) {
        for(DirectedConnectoid connectoid : knownConnectoidsForLink) {
          if(connectoid.getAccessLinkSegment().idEquals(link.getEdgeSegmentAb()) || connectoid.getAccessLinkSegment().idEquals(link.getEdgeSegmentBa()) ) {
            /* match */
            referencingConnectoids.add(connectoid);
          }
        }
      }
    }
    
    return referencingConnectoids;
  }  
  
  /** Check if we should break any links for the passed in node and if so, do it
   * 
   * @param theNode to verify
   * @param linksToBreak contains the links that (at least at some point) had theNode as an internal link
   * @param networkLayer the node resides on
   * @param crs of the network layer 
   * @return newly broken planit links by their original osmWayId 
   *  
   * @throws PlanItException thrown if error
   */
  public static Map<Long, Set<Link>> breakLinksWithInternalNode(Node theNode, List<Link> linksToBreak, MacroscopicPhysicalNetwork networkLayer, CoordinateReferenceSystem crs) throws PlanItException {
    Map<Long, Set<Link>> newOsmWaysWithMultiplePlanitLinks = new HashMap<Long, Set<Link>>();
    
    if(linksToBreak != null) {
      
      try {
        /* performing breaking of links at the node given, returns the broken links by the original link's PLANit edge id */
        Map<Long, Set<Link>> localBrokenLinks = networkLayer.breakLinksAt(linksToBreak, theNode, crs);                 
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
  
  /** Delegate to zoning modifier, to be used in tandem with {@link breakLinksWithInternalNode} because it may invalidate the references to link segments on connectoids. this
   * method will update the connectoids link segments in accordance with the breakLink action given the correct inputs are provided.
   * 
   * @param connectoidsDownstreamVerticesBeforeBreakLink to use
   * @throws PlanItException thrown if error
   */
  public static void updateAccessLinkSegmentsForDirectedConnectoids(Map<DirectedConnectoid, DirectedVertex> connectoidsDownstreamVerticesBeforeBreakLink) throws PlanItException {
    ZoningModifier.updateLinkSegmentsForDirectedConnectoids(connectoidsDownstreamVerticesBeforeBreakLink);
  }
  
  /**
   * Delegate to zoning modifier, to be used in tandem with {@link breakLinksWithInternalNode} because it may invalidate the references to link segments on connectoids. this
   * method will identify the connectoids access link segment's downstream vertex and should be invoked BEFORE any breakLink action. after the break link use 
   * {@link updateAccessLinkSegmentsForDirectedConnectoids} with the results of this call to update the connectoids affected and make sure the refer to the same original vertex via
   * an updated link segment (if needed). 
   * 
   * @param links to collect connectoid information for, i.e., only connectoids referencing link segments with a parent link in this collection
   * @param connectoidsByLocation all connectoids indexed by their location
   * @return found connectoids and their access link segment's current downstream vertex 
   */
  public static Map<DirectedConnectoid, DirectedVertex> collectAccessLinkSegmentDownstreamVerticesForConnectoids(Collection<Link> links, Map<Point, Set<DirectedConnectoid>> connectoidsByLocation) {    
    Map<DirectedConnectoid,DirectedVertex> connectoidsDownstreamVerticesBeforeBreakLink = new HashMap<DirectedConnectoid,DirectedVertex>();
    for(Link link : links) {
      Collection<DirectedConnectoid> connectoids = findDirectedConnectoidsRefencingLink(link,connectoidsByLocation);
      if(connectoids !=null && !connectoids.isEmpty()) {
        connectoids.forEach( connectoid -> connectoidsDownstreamVerticesBeforeBreakLink.put(connectoid, connectoid.getAccessLinkSegment().getDownstreamVertex()));          
      }
    }
    return connectoidsDownstreamVerticesBeforeBreakLink;
  }  

  /** Create a new PLANit node required for connectoid access, register it and update stats
   * 
   * @param osmNode to extract PLANit node for
   * @param layerData to register osm node to planit node mapping on
   * @param networkLayer to create it on
   * @return created planit node
   * @throws PlanItException thrown if error
   */
  public static Node createPlanitNodeForConnectoidAccess(OsmNode osmNode, final PlanitOsmNetworkLayerReaderData layerData,  MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    Node planitNode = PlanitOsmHandlerHelper.createAndPopulateNode(osmNode, networkLayer);
    layerData.registerPlanitNodeByOsmNode(osmNode, planitNode);
    return planitNode;
  }  
  
  /** Create a new PLANit node required for connectoid access, not based on an existing osm node, but based on an auto-generated location due to missing
   * osm nodes in the input file (within pre-specified distance of transfer zone
   * 
   * @param osmNode to extract PLANit node for
   * @param layerData to register location to planit node mapping on
   * @param networkLayer to create it on
   * @return created planit node
   * @throws PlanItException thrown if error
   */
  public static Node createPlanitNodeForConnectoidAccess(Point location, final PlanitOsmNetworkLayerReaderData layerData,  MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    Node planitNode = PlanitOsmHandlerHelper.createAndPopulateNode(location, networkLayer);
    layerData.registerPlanitNodeByLocation(location, planitNode);
    return planitNode;
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

    Node node = null;
    try {
      Point geometry = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getX(osmNode), PlanitOsmNodeUtils.getY(osmNode));
      node = createAndPopulateNode(geometry, networkLayer);
    } catch (PlanItException e) {
      LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
    }

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

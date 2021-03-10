package org.planit.osm.handler;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.DirectedVertex;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
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
      node.setExternalId(String.valueOf(osmNode.getId()));
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

  /** Verify if the geometry of the transfer zone equates to the provided location
   * @param transferZone to verify
   * @param location to verify against
   * @return true when residing at the exact same location at the reference location, false otherwise
   * @throws PlanItException thrown if error
   */
  public static boolean isTransferZoneAtLocation(TransferZone transferZone, Point location) throws PlanItException {
    PlanItException.throwIfNull(transferZone, "Transfer zone is null, unable to verify location");
      
    if(transferZone.hasCentroid() && transferZone.getCentroid().hasPosition()) {
      return location.equals(transferZone.getCentroid().getPosition());
    }else if(transferZone.hasGeometry()) {
      if(transferZone.getGeometry() instanceof Point) {
        return location.equals(transferZone.getGeometry());
      }
    }else { 
      throw new PlanItException("Transferzone representing platform/pole %s has no valid geometry attached, unable to verify location", transferZone.getExternalId());
    }
      
    return false;
  }

  /** Verify of the transfer zone resides left of the line coordA to coordB
   * 
   * @param transferZone to check
   * @param coordA of line 
   * @param coordB of line
   * @return true when left, false otherwise
   * @throws PlanItException thrown if error
   */
  public static boolean isTransferZoneLeftOf(TransferZone transferZone, Coordinate coordA, Coordinate coordB, PlanitJtsUtils geoUtils) throws PlanItException {
    
    Coordinate transferZoneReferenceCoordinate = null; 
    if(transferZone.hasCentroid() && transferZone.getCentroid().hasPosition()) {
      transferZoneReferenceCoordinate = transferZone.getCentroid().getPosition().getCoordinate();
    }else if(transferZone.hasGeometry()) {
      if(transferZone.getGeometry() instanceof Point) {
        transferZoneReferenceCoordinate = ((Point)transferZone.getGeometry()).getCoordinate();  
      }else {
        /* find projected coordinate closest to coordB */
        transferZoneReferenceCoordinate = geoUtils.getClosestProjectedCoordinateOnGeometry(PlanitJtsUtils.createPoint(coordB),  transferZone.getGeometry());
      }
    }else { 
      throw new PlanItException("Transferzone representing platform/pole %s has no valid geometry attached, unable to determine on which side of line AB (%s, %s) is resides", transferZone.getExternalId(), coordA.toString(), coordB.toString());
    }
    
    return PlanitJtsUtils.isCoordinateLeftOf(transferZoneReferenceCoordinate, coordA, coordB);
   
  }

  /** Find the access link segments eligible given the intended location of the to be created connectoid, the transfer zone provided, and the access mode.
   * When transfer zone location differs from the connectoid location determine on which side of the infrastructure it exists and based on the country's driving direction
   * and access mode determine the access link segments
   *  
   * @param connectoidNode the planit node from which access link segments are to be sourced to create connectoid(s) for
   * @param transferZone to create connectoid(s) for
   * @param planitMode that is accessible
   * @param leftHandDrive is infrastructure left hand drive or not
   * @param geoUtils to use for determining geographic eleigibility
   * @return eligible link segments to be access link segments for connectoid at this location
   * @throws PlanItException thrown if error
   */
  public static Set<EdgeSegment> findAccessibleLinkSegmentsForTransferZoneAtConnectoidLocation(Node planitNode, TransferZone transferZone, Mode planitMode, boolean leftHandDrive, PlanitJtsUtils geoUtils) throws PlanItException {
        
    Set<EdgeSegment> accessLinkSegments = null;
    if(PlanitOsmHandlerHelper.isTransferZoneAtLocation(transferZone, planitNode.getPosition())) {
      /* transfer zone equates to stop location, so impossible to determine on what side of the infrastructure it lies: All incoming link segments used for connectoid */
      accessLinkSegments = planitNode.getEntryEdgeSegments();
    }else {
      accessLinkSegments = new HashSet<EdgeSegment>();
      for(EdgeSegment linkSegment : planitNode.getEntryEdgeSegments()) {
        
        /* final line segment of link segment geometry */
        Coordinate toCoordinate = linkSegment.getDownstreamVertex().getPosition().getCoordinate();
        /* select internal coordinate predecing toCoordinate, since sourced from link geometry, the index depends on direction of segment */
        int fromCoordinateIndex = (linkSegment.isDirectionAb() && linkSegment.getParentEdge().isGeometryInAbDirection()) ? linkSegment.getParentEdge().getGeometry().getNumPoints()-2 : 1; 
        Coordinate fromCoordinate  =  linkSegment.getParentEdge().getGeometry().getCoordinateN(fromCoordinateIndex);
                    
        /* determine location relative to infrastructure */
        boolean isTransferZoneLeftOfInfrastructure = PlanitOsmHandlerHelper.isTransferZoneLeftOf(transferZone, fromCoordinate, toCoordinate, geoUtils);      
        if(isTransferZoneLeftOfInfrastructure==leftHandDrive) {
          /* viable --> add */
          accessLinkSegments.add(linkSegment);
        }
      }
    }
        
    /* filter by accessible modes */
    if(accessLinkSegments!= null && !accessLinkSegments.isEmpty()) {
      Iterator<EdgeSegment> iterator = accessLinkSegments.iterator();
      while(iterator.hasNext()) {
        MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) iterator.next();
        if(!linkSegment.isModeAllowed(planitMode)) {
          /* not eligible, because not mode compatible, remove */
          iterator.remove();
        }
      }
    }
    
    return accessLinkSegments;
  }
  
}

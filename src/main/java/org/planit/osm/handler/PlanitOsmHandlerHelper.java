package org.planit.osm.handler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LinearLocation;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.DirectedVertex;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.TrackModeType;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneType;
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
  
  /** to be able to retain the supported osm modes on a planit transfer zone, we place tham on the zone as an input property under this key.
   *  This avoids having to store all osm tags, while still allowing to leverage the information in the rare cases it is needed when this information is lacking
   *  on stop_positions that use this transfer zone
   */
  protected static final String TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY = "osmmodes";  
  
  /** When known, transfer zones are provided with a station name extracted from the osm station entity (if possible). Its name is stored under
   * this key as input property
   */
  protected static final String TRANSFERZONE_STATION_INPUT_PROPERTY_KEY = "station";  
    
  /** find all already registered directed connectoids that reference a link segment part of the passed in link in the given network layer
   * 
   * @param link to find referencing directed connectoids for
   * @param knownConnectoidsByLocation all connectoids of the network layer indexed by their location
   * @return all identified directed connectoids
   */
  protected static Collection<DirectedConnectoid> findDirectedConnectoidsRefencingLink(Link link, Map<Point, List<DirectedConnectoid>> knownConnectoidsByLocation) {
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
      Collection<DirectedConnectoid> knownConnectoidsForLink = knownConnectoidsByLocation.get(location);
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
  public static Map<DirectedConnectoid, DirectedVertex> collectAccessLinkSegmentDownstreamVerticesForConnectoids(Collection<Link> links, Map<Point, List<DirectedConnectoid>> connectoidsByLocation) {    
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
    
    Geometry transferzoneGeometry = null; 
    if(transferZone.hasCentroid() && transferZone.getCentroid().hasPosition()) {
      transferzoneGeometry = transferZone.getCentroid().getPosition();
    }else if(transferZone.hasGeometry()) {
      transferzoneGeometry = transferZone.getGeometry();
    }else { 
      throw new PlanItException("Transferzone representing platform/pole %s has no valid geometry attached, unable to determine on which side of line AB (%s, %s) is resides", transferZone.getExternalId(), coordA.toString(), coordB.toString());
    }
    
    return geoUtils.isGeometryLeftOf(transferzoneGeometry, coordA, coordB);   
  }
    

  /** Find the access link segments eligible given the intended location of the to be created connectoid, the transfer zone provided, and the access mode.
   * When transfer zone location differs from the connectoid location determine on which side of the infrastructure it exists and based on the country's driving direction
   * and access mode determine the access link segments
   *  
   * @param connectoidNode the planit node from which access link segments are to be sourced to create connectoid(s) for
   * @param transferZone to create connectoid(s) for
   * @param planitMode that is accessible
   * @param leftHandDrive is infrastructure left hand drive or not
   * @param mustAvoidCrossingTraffic flag indicating if we filter out link segments where the traveller must cross traffic 
   *  (due to pt vehicle doors only available on driving direction side)
   * @param geoUtils to use for determining geographic eligibility
   * @return eligible link segments to be access link segments for connectoid at this location
   * @throws PlanItException thrown if error
   */
  public static Set<EdgeSegment> findAccessibleLinkSegmentsForTransferZoneAtConnectoidLocation(
      Node planitNode, TransferZone transferZone, Mode planitMode, boolean leftHandDrive, boolean mustAvoidCrossingTraffic, PlanitJtsUtils geoUtils) throws PlanItException {
        
    Set<EdgeSegment> accessLinkSegments = null;
    accessLinkSegments = new HashSet<EdgeSegment>();    
    if(!mustAvoidCrossingTraffic || isTransferZoneAtLocation(transferZone, planitNode.getPosition())) {
      /* transfer zone equates to stop location or we assume train/tram platforms are present servicing either direction, so what side of the infrastructure the transfer zone lies is either not important or
       * infeasible to determine : All incoming link segments used for connectoid */
      for(EdgeSegment linkSegment : planitNode.getEntryEdgeSegments()) {
        if(((MacroscopicLinkSegment)linkSegment).isModeAllowed(planitMode)){      
          accessLinkSegments.add(linkSegment);
        }
      }
    }else {
      /* we do care about location of waiting area (transfer zone) compared to road, we only select link segments that are in the expected driving direction given
       * the location of the waiting area */      
      accessLinkSegments = new HashSet<EdgeSegment>();              
      for(EdgeSegment linkSegment : planitNode.getEntryEdgeSegments()) {
        if(((MacroscopicLinkSegment)linkSegment).isModeAllowed(planitMode)){
          /* use line geometry closest to connectoid location */
          LineSegment finalLineSegment = extractClosestLineSegmentToGeometryFromLinkSegment(transferZone.getGeometry(), (MacroscopicLinkSegment)linkSegment, geoUtils);
          if(mustAvoidCrossingTraffic) {
            /* determine location relative to infrastructure */
            boolean isTransferZoneLeftOfInfrastructure = PlanitOsmHandlerHelper.isTransferZoneLeftOf(transferZone, finalLineSegment.p0, finalLineSegment.p1, geoUtils);      
            if(isTransferZoneLeftOfInfrastructure==leftHandDrive) {
              /* viable no opposite traffic directions needs to be crossed on the link to get to stop location --> add */
              accessLinkSegments.add(linkSegment);
            }
          }else {
            accessLinkSegments.add(linkSegment);
          } 
        }                
      }
      if(mustAvoidCrossingTraffic && accessLinkSegments.isEmpty()) {
        LOGGER.info(String.format("platform/pole/station %s for stop_location %s discarded due to passengers having to cross traffic in opposite driving direction to reach stop", transferZone.getExternalId(), planitNode.getExternalId()));
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

  /** extract a JTS line segment based on the closest two coordinates on the link segment geometry in its intended direction to the reference geometry provided
   * 
   * @param referenceGeometry to find closest line segment to 
   * @param edgeSegment to extract line segment from
   * @param geoUtils for distance calculations
   * @return line segment if found
   * @throws PlanItException  thrown if error
   */
  public static LineSegment extractClosestLineSegmentToGeometryFromLinkSegment(Geometry referenceGeometry, MacroscopicLinkSegment linkSegment, PlanitJtsUtils geoUtils) throws PlanItException {
    
    LineString linkSegmentGeometry = linkSegment.getParentEdge().getGeometry();
    if(linkSegmentGeometry == null) {
      throw new PlanItException("geometry not available on osm way %s, unable to determine if link (segment) is closest to reference geometry, this shouldn't happen", linkSegment.getParentEdge().getExternalId());
    }
    LinearLocation linearLocation = geoUtils.getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(referenceGeometry, linkSegmentGeometry);
    boolean linearLocationInLinkSegmentDirection = linkSegment.isDirectionAb() && linkSegment.getParentEdge().isGeometryInAbDirection();
    
    LineSegment lineSegment = linearLocation.getSegment(linkSegment.getParentEdge().getGeometry());
    if(!linearLocationInLinkSegmentDirection) {
      lineSegment.reverse();
    }
    return lineSegment;        
  }
  
  /** collect the one way edge segment for the mode if the link is in fact one way. If it is not (for the mode), null is returned
   * 
   * @param link to collect one way edge segment (for mode) from, if available
   * @param accessMode to check one-way characteristic
   * @return edge segment that is one way for the mode, i.e., the other edge segment (if any) does not support this mode, null if this is not the case
   */
  public static MacroscopicLinkSegment getLinkSegmentIfLinkIsOneWayForMode(Link link, Mode accessMode) {
    EdgeSegment edgeSegment = null;
    if(link.hasEdgeSegmentAb() != link.hasEdgeSegmentBa()) {
      /* link is one way across all modes, so transfer zones are identifiable to be one the wrong or right side of the road */
      edgeSegment = link.hasEdgeSegmentAb() ? link.getLinkSegmentAb() : link.getLinkSegmentBa();
    }else if(link.<MacroscopicLinkSegment>getLinkSegmentAb().isModeAllowed(accessMode) != link.<MacroscopicLinkSegment>getLinkSegmentBa().isModeAllowed(accessMode)) {
      /* link is one way for our mode, so transfer zones are identifiable to be one the wrong or right side of the road */
      edgeSegment = link.<MacroscopicLinkSegment>getLinkSegmentAb().isModeAllowed(accessMode) ? link.getLinkSegmentAb() : link.getLinkSegmentBa();
    }

    return (MacroscopicLinkSegment) edgeSegment;
  }  

  /** collect the downstream node of a link conditioned on the fact the link is identified as one-way for the provided mode (otherwise it is not possible to
   * determine what downstream is for a link)
   * 
   * @param link to extract downstream node from
   * @param accessMode to check one-way characteristic
   * @return downstream node, null if not one-way for given mode
   */
  public static Node getDownstreamNodeIfLinkIsOneWayForMode(Link link, Mode accessMode) {
    EdgeSegment edgeSegment = getLinkSegmentIfLinkIsOneWayForMode(link, accessMode);    
    if(edgeSegment != null) {
      /* select the downstream planit node */
      return (Node)edgeSegment.getDownstreamVertex();
    }
    return null;
  }

  /** create a subset of links from the passed in ones, removing all links for which we can be certain that geometry is located on the wrong side of the road infrastructure geometry.
   * This is verified by checking if the link is one-way. If so, we can be sure (based on the driving direction of the country) if the geometry is located to the closest by (logical) 
   * driving direction given the placement of the geometry, i.e., on the left hand side for left hand drive countries, on the right hand side for right hand driving countries
   * 
   * @param waitingAreaGeometry representing the waiting area (station, platform, pole)
   * @param links to remove ineligible ones from
   * @param referenceOsmModes eligible for the waiting area
   * @return remaining links that are deemed eligible
   * @throws PlanItException thrown if error
   */   
  public static Collection<Link> removeLinksOnWrongSideOf(Geometry waitingAreaGeometry, Collection<Link> links, boolean isLeftHandDrive, Collection<Mode> accessModes, PlanitJtsUtils geoUtils) throws PlanItException{
    Collection<Link> matchedLinks = new HashSet<Link>(links);  
    for(Link link : links) {            
      for(Mode accessMode : accessModes){
        
        /* road based modes must stop with the waiting area in the driving direction, i.e., must avoid cross traffic, because otherwise they 
         * have no doors at the right side, e.g., travellers have to cross the road to get to the vehicle, which should not happen */
        boolean mustAvoidCrossingTraffic = true;
        if(accessMode.getPhysicalFeatures().getTrackType().equals(TrackModeType.RAIL)) {
          mustAvoidCrossingTraffic = false;
        }           
        
        MacroscopicLinkSegment oneWayLinkSegment = PlanitOsmHandlerHelper.getLinkSegmentIfLinkIsOneWayForMode(link, accessMode);        
        if(oneWayLinkSegment != null && mustAvoidCrossingTraffic) {
          /* use line geometry closest to connectoid location */
          LineSegment finalLineSegment = PlanitOsmHandlerHelper.extractClosestLineSegmentToGeometryFromLinkSegment(waitingAreaGeometry, oneWayLinkSegment, geoUtils);                    
          /* determine location relative to infrastructure */
          boolean isStationLeftOfOneWayLinkSegment = geoUtils.isGeometryLeftOf(waitingAreaGeometry, finalLineSegment.p0, finalLineSegment.p1);  
          if(isStationLeftOfOneWayLinkSegment != isLeftHandDrive) {
            /* travellers cannot reach doors of mode on this side of the road, so deemed not eligible */
            matchedLinks.remove(link);
            break; // from mode loop
          }
        }
      }             
    }
    return matchedLinks;
  }
  
  /** Verify if the passed on osm node that represents a stop_location is in fact also a Ptv1 stop, either a highway=tram_stop or
   * highway=bus_stop. This is effectively wrongly tagged, but does occur due to confusion regarding the tagging schemes. Therefore we
   * identify this situation allowing the parser to change its behaviour and if it is a Patv1 stop, process it as such if deemed necessary
   * 
   * @param osmNode that is classified as Ptv2 stop_location
   * @param tags of the stop_location
   * @return true when also a Ptv1 stop (bus or tram stop), false otherwise
   */
  public static boolean isPtv2StopPositionPtv1Stop(OsmNode osmNode, Map<String, String> tags) {
    
    /* Context: The parser assumed a valid Ptv2 tagging and ignored the Ptv1 tag. However, it was only a valid Ptv1 tag and incorrectly tagged Ptv2 stop_location.
     * We therefore identify this special situation */    
    if(OsmPtv1Tags.isTramStop(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 tram_stop (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isBusStop(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 bus_stop (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isHalt(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 halt (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isStation(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 station (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }
    return false;
  }   

  /** collect the transfer zone type based on the tags
   * 
   * @param osmNode node 
   * @param tags tags of the node
   * @return transfer zone type, unknown if not able to map 
   */
  public static TransferZoneType getPtv1TransferZoneType(OsmNode osmNode, Map<String, String> tags) {
    if(OsmPtv1Tags.isBusStop(tags)) {
      return TransferZoneType.POLE;
    }else if(OsmPtv1Tags.isTramStop(tags) ) {
      return TransferZoneType.PLATFORM;
    }else if(OsmPtv1Tags.isHalt(tags)) {
      return TransferZoneType.SMALL_STATION;
    }else if(OsmPtv1Tags.isStation(tags)) {
      return TransferZoneType.STATION;
    }else {
      LOGGER.severe(String.format("unable to map node %d to Ptv1 transferzone type", osmNode.getId()));
      return TransferZoneType.UNKNOWN;
    }
  }
      
  /** collect the station name for a transfer zone (if any)
   * @param transferZone to collect for
   * @return station name
   */
  public static String getTransferZoneStationName(TransferZone transferZone) {
    return (String)transferZone.getInputProperty(TRANSFERZONE_STATION_INPUT_PROPERTY_KEY);
  }
  
  /** collect the station name for a transfer zone (if any)
   * @param transferZone to collect for
   * @return station name
   */
  public static void  setTransferZoneStationName(TransferZone transferZone, String stationName) {
    transferZone.addInputProperty(TRANSFERZONE_STATION_INPUT_PROPERTY_KEY, stationName);
  }  
  
  /** Verify if the transfer zone has a station name set
   * @param transferZone to verify
   * @return true when present, false otherwise
   */  
  public static boolean hasTransferZoneStationName(TransferZone transferZone) {
    return getTransferZoneStationName(transferZone) != null;
  }   
    
  /** while PLANit does not require access modes on transfer zones because it is handled by connectoids, OSM stop_positions (connectoids) might lack the required
   * tagging to identify their mode access in which case we revert to the related transfer zone to deduce it. Therefore, we store osm mode information on a transfer zone
   * via the generic input properties to be able to retrieve it if needed later
   * 
   * @param transferZone to use
   * @param eligibleOsmModes to add
   */
  public static void addOsmAccessModesToTransferZone(final TransferZone transferZone, Collection<String> eligibleOsmModes) {
    if(transferZone != null && eligibleOsmModes!= null) {
      /* register identified eligible access modes */
      transferZone.addInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY, eligibleOsmModes);
    }
  }    
    
  /** collect any prior registered eligible osm modes on a Planit transfer zone (unmodifiable)
   * 
   * @param transferZone to collect from
   * @return eligible osm modes, null if none
   */
  @SuppressWarnings("unchecked")
  public static Collection<String> getEligibleOsmModesForTransferZone(final TransferZone transferZone){
    Collection<String> eligibleOsmModes = (Collection<String>) transferZone.getInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY);
    if(eligibleOsmModes != null)
    {
      return Collections.unmodifiableCollection(eligibleOsmModes);
    }
    return null;
  }  
  
  /** more readable check to see if pair with eligible modes contains any eligible osm mode
   * 
   * @param modeResult of collectEligibleModes on zoning base handler
   * @return true when has at least one mapped planit mode present
   */
  public static boolean hasEligibleOsmMode(Pair<Collection<String>, Collection<Mode>> modeResult) {
    if(modeResult!= null && modeResult.first()!=null && !modeResult.first().isEmpty()) {
      /* eligible modes available */
      return true;
    }else {
      return false;
    }
  }  

  /** more readable check to see if pair with eligible modes contains any mapped planit mode
   * 
   * @param modeResult of collectEligibleModes on zoning base handler
   * @return true when has at least one mapped planit mode present
   */
  public static boolean hasMappedPlanitMode(Pair<Collection<String>, Collection<Mode>> modeResult) {
    if(modeResult!= null && modeResult.second()!=null && !modeResult.second().isEmpty()) {
      /* eligible modes mapped to planit mode*/
      return true;
    }else {
      return false;
    }
  }
  
}

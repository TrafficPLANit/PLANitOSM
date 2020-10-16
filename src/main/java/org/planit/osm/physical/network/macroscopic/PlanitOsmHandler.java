package org.planit.osm.physical.network.macroscopic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.geo.PlanitJtsUtils;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.util.OsmDirection;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmLaneTags;
import org.planit.osm.util.OsmRailFeatureTags;
import org.planit.osm.util.OsmRailWayTags;
import org.planit.osm.util.OsmSpeedTags;
import org.planit.osm.util.OsmTags;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.utils.arrays.ArrayUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations. We parse these entities in distinct order, first all nodes, then all ways, and then all relations. this allows
 * us to incrementally construct the network without backtracking or requiring the entire file to be in memory in addition to the memory model we're creating.
 * 
 * @author markr
 * 
 *
 */
public class PlanitOsmHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmHandler.class.getCanonicalName());

  /** the network to populate */
  private final PlanitOsmNetwork network;

  /** the settings to adhere to */
  private final PlanitOsmSettings settings;

  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils;
  
  /** utility class for profiling this instance */
  private final PlanitOsmHandlerProfiler profiler;
  
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** Mapping from internal Osm node id to the links they are internal to. When done parsing, we verify if any
   * entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existin link's extreme node is internal to any other link, the link where
   * this node is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link
   */
  private final Map<Long, List<Link>> linkInternalOsmNodes;  
  
  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays;
  
  /**
   * track the nodes by their external id so they can by looked up quickly while parsing ways
   */
  private final Map<Long,Node> nodesByExternalId = new HashMap<Long, Node>();
     
  /**
   * Log all de-activated OSM way types
   */  
  private void logUnsupportedOsmWayTypes() {
    settings.unsupportedOsmRoadLinkSegmentTypes.forEach( 
        osmTag -> LOGGER.info(String.format("[DEACTIVATED] highway:%s", osmTag)));
    settings.unsupportedOsmRailLinkSegmentTypes.forEach( 
        osmTag -> LOGGER.info(String.format("[DEACTIVATED] railway:%s", osmTag)));    
  }
  
  /** Identify which links we should break that have the node to break at as one of its internal nodes
   * 
   * @param theNodeToBreakAt the node to break at
   * @param brokenLinksByOriginalExternalLinkId earlier broken links
   * @return the link to break, null if none could be found
   * @throws PlanItException thrown if error
   */
  private List<Link> findLinksToBreak(Node theNodeToBreakAt, Map<Long, Set<Link>> brokenLinksByOriginalExternalLinkId) throws PlanItException {
    List<Link> linksToBreak = null;
    if(theNodeToBreakAt != null && linkInternalOsmNodes.containsKey(theNodeToBreakAt.getExternalId())) {
      
      /* find the links to break assuming no links have been broken yet */
      linksToBreak = linkInternalOsmNodes.get(theNodeToBreakAt.getExternalId());
      
      /* find replacement links for the original link to break in case the original already has been broken and we should use 
       * one of the split off broken links instead of the original for the correct breaking for the given node (since it now resides on one of the broken
       * links rather than the original full link that no longer exists in that form */
      Set<Link> replacementLinks = new HashSet<Link>();
      Iterator<Link> linksToBreakIter = linksToBreak.iterator();
      while(linksToBreakIter.hasNext()) {
        Link orginalLinkToBreak = linksToBreakIter.next(); 
        
        if(brokenLinksByOriginalExternalLinkId.containsKey(orginalLinkToBreak.getExternalId())) {
          
          /* link has been broken before, find out in which of its broken links the node to break at resides on */
          Set<Link> earlierBrokenLinks = brokenLinksByOriginalExternalLinkId.get(orginalLinkToBreak.getExternalId());
          Link matchingEarlierBrokenLink = null;
          for(Link link : earlierBrokenLinks) {
            Optional<Integer> coordinatePosition = geoUtils.findCoordinatePosition(theNodeToBreakAt.getPosition().getCoordinate(),link.getGeometry());
            if(coordinatePosition.isPresent()) {
              matchingEarlierBrokenLink = link;
            }
          }
          
          /* verify if match is valid (which it should be) */
          if(matchingEarlierBrokenLink==null) {
            throw new PlanItException(
                String.format("it is expected that broken link's internal nodes match exactly with original link's internal nodes, this seems not to be the case for link %s (id:%d)",
                orginalLinkToBreak.getExternalId(), orginalLinkToBreak.getId()));            
          }
          
          /* remove original and mark found link as replacement link to break */
          linksToBreakIter.remove();
          replacementLinks.add(matchingEarlierBrokenLink);
        }
      }
      linksToBreak.addAll(replacementLinks);
    }
    return linksToBreak;
  }  
  
  /** check if we should break the links on the passed in node and if so, do it
   * @param theNode to verify
   * @param brokenLinksByOriginalExternalLinkId track all broken links that originated from one original link, tracked by its external OSM id 
   * @throws PlanItException thrown if error
   */
  private void breakLinksWithInternalNode(Node theNode, Map<Long, Set<Link>> brokenLinksByOriginalExternalLinkId) throws PlanItException {
        
    /* find the link that we should break */
    List<Link> linksToBreak = findLinksToBreak(theNode, brokenLinksByOriginalExternalLinkId);
    if(linksToBreak != null) {
      /* performing breaking of links, returns the broken links by the original link's PLANit edge id */
      Map<Long, Set<Link>> localBrokenLinks = this.network.breakLinksAt(linksToBreak, theNode);           
      
      /* add newly broken links to the mapping from original external OSM link id, to the broken link that together form this entire original OSMway*/
      if(localBrokenLinks != null) {
        localBrokenLinks.forEach((id, links) -> {
          links.forEach( brokenLink -> {
            brokenLinksByOriginalExternalLinkId.putIfAbsent((Long) brokenLink.getExternalId(), new HashSet<Link>());
            brokenLinksByOriginalExternalLinkId.get(brokenLink.getExternalId()).add(brokenLink);
          });
        });        
      }  
    }     
  }  
  

  /**
   * parse the maximum speed for the link segments
   * 
   * @param link on which link segments reside
   * @param direction osm direction information
   * @param tags osm tags
   * @throws PlanItException thrown if error
   */
  private void populateLinkSegmentsSpeed(Link link, OsmDirection direction, Map<String, String> tags) throws PlanItException {
    
    Double speedLimitAbKmh = null;
    Double speedLimitBaKmh = null;
    
    if(tags.containsKey(OsmSpeedTags.MAX_SPEED)) {
      /* regular speed limit for all available directions and across all modes */
      speedLimitAbKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED));
      speedLimitBaKmh = speedLimitAbKmh;
    }else if(tags.containsKey(OsmSpeedTags.MAX_SPEED_LANES)) {
      /* check for lane specific speed limit */
      double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_LANES));
      /* Note: PLANit does not support lane specific speeds at the moment, maximum speed across lanes is selected */      
      speedLimitAbKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);
      speedLimitBaKmh = speedLimitAbKmh;
    }else if( tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD) || tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)){
      /* check for forward speed limit */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD)) {
        speedLimitAbKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD));  
      }
      /* check for forward speed limit per lane */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)) {
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)); 
        speedLimitAbKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);   
      }
    }else if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)|| tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
      /* check for backward speed limit */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)) {
        speedLimitBaKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD));        
      }
      /* check for backward speed limit per lane */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)); 
        speedLimitBaKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);           
      }
    }else
    {
      /* no speed limit information, revert to defaults */
      speedLimitAbKmh = settings.getDefaultSpeedLimitByOsmWayType(tags);
      speedLimitBaKmh = speedLimitAbKmh;
      profiler.incrementMissingSpeedLimitCounter();
    }
    
    /* we assume the direction information is consistent with the speed information in the tags */
    
    if(!direction.isOneWay() || direction.isReverseDirection()) {
      PlanItException.throwIfNull(speedLimitBaKmh, "speed limit not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentBa()).setPhysicalSpeedLimitKmH(speedLimitBaKmh);
    }
    if(!direction.isOneWay() || !direction.isReverseDirection()) {
      PlanItException.throwIfNull(speedLimitAbKmh, "speed limit not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentAb()).setPhysicalSpeedLimitKmH(speedLimitAbKmh);
    }
    
    /* mode specific speed limits */
    //TODO
    
  }
  
  /**
   * parse the number of lanes on the link and link segments
   * 
   * @param link for which lanes are specified (and its link segments)
   * @param direction osm direction information for this link(segments)
   * @param tags containing lane information
   * @throws PlanItException 
   */
  private void populateLinkSegmentsLanes(Link link, OsmDirection direction, Map<String, String> tags) throws PlanItException {
    Integer totalLanes = null;
    Integer lanesAb = null;
    Integer lanesBa = null;    

    /* collect total and direction specific road based lane information */
    String osmWayKey = null;
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmWayKey = OsmHighwayTags.HIGHWAY;
      
      if(tags.containsKey(OsmLaneTags.LANES)) {
        totalLanes = Integer.parseInt(tags.get(OsmLaneTags.LANES));
      }    
      if(tags.containsKey(OsmLaneTags.LANES_FORWARD)) {
        lanesAb = Integer.parseInt(tags.get(OsmLaneTags.LANES_FORWARD));
      }
      if(tags.containsKey(OsmLaneTags.LANES_BACKWARD)) {
        lanesBa = Integer.parseInt(tags.get(OsmLaneTags.LANES_BACKWARD));
      }
      
      if( totalLanes!=null && lanesAb==null && lanesBa==null) {
          /* in case of one way link, total lanes = directional lanes, enforce this if explicit tag is missing */
        if(direction.isOneWay()) {
          lanesBa = direction.isReverseDirection() ? totalLanes : null;
          lanesAb = direction.isReverseDirection() ? null : totalLanes;
        }else if(totalLanes%2==0) {
          /* two directions, with equal number of lanes does not require directional tags, simply split in two */
          lanesBa = totalLanes/2;
          lanesAb = lanesBa;
        } 
      }
    /* convert number of tracks to lanes */
    }else if(tags.containsKey(OsmRailWayTags.RAILWAY)) {
      osmWayKey = OsmRailWayTags.RAILWAY;
      if(tags.containsKey(OsmRailFeatureTags.TRACKS)) {
        totalLanes = Integer.parseInt(tags.get(OsmRailFeatureTags.TRACKS));
      }   
    }
    
    /* we assume that only when both are not set something went wrong, otherwise it is assumed it is a one-way link and it is properly configured */
    if(lanesAb==null && lanesBa==null) {
      lanesAb = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      lanesBa = lanesAb;
      profiler.incrementMissingLaneCounter();
    }
        
    /* populate link segments */  
    if(!direction.isOneWay() || direction.isReverseDirection()) {
      PlanItException.throwIfNull(lanesBa, "number of lanes not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentBa()).setNumberOfLanes(lanesBa);
    }
    if(!direction.isOneWay() || !direction.isReverseDirection()) {
      PlanItException.throwIfNull(lanesAb, "number of lanes not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentAb()).setNumberOfLanes(lanesAb);
    }
  }  
  
  /**
   * Extract the geometry for the passed in way
   * @param osmWay way to extract geometry from
   * @return line string instance representing the shape of the way
   * @throws PlanItException 
   */
  private LineString extractLinkGeometry(OsmWay osmWay) throws PlanItException {
    Coordinate[] coordinates = new Coordinate[osmWay.getNumberOfNodes()];
    int numberOfNodes = osmWay.getNumberOfNodes();
    for(int index = 0; index < numberOfNodes; ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode == null) {
        throw new PlanItException(String.format("referenced osmNode %d in osmWay %d not available in OSM parser",osmWay.getNodeId(index), osmWay.getId()));
      }
      coordinates[index] = new Coordinate(PlanitOsmUtils.getXCoordinate(osmNode),PlanitOsmUtils.getYCoordinate(osmNode));
    }
    return  geoUtils.createLineStringFromCoordinates(coordinates);
  }  
  
  /**
   * Extract a PLANit node from the osmNode information
   * 
   * @param osmNodeId to convert
   * @return created or retrieved node
   * @throws PlanItException 
   */
  private Node extractNode(final long osmNodeId) throws PlanItException {
    
    Node node = nodesByExternalId.get(osmNodeId);
    if(node == null) {
      
      /* not yet created */      
      OsmNode osmNode = osmNodes.get(osmNodeId);
      if(osmNode==null) {
        throw new PlanItException(String.format("osmNodeId (%d) not provided by parser, unable to retrieve node",osmNodeId));
      }
      
      /* location info */
      Point geometry = null;
      try {
        geometry = geoUtils.createPoint(PlanitOsmUtils.getXCoordinate(osmNode), PlanitOsmUtils.getYCoordinate(osmNode));
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
      }

      /* create and register */
      node = network.nodes.registerNew(osmNodeId);
      node.setPosition(geometry);
      nodesByExternalId.put(osmNodeId, node);
     
      profiler.logNodeStatus(network.nodes.size());   
    }
    return node;
  }
  
  /** create and populate link if it does not already exists for the given two PLANit nodes based on the passed in osmWay information
   * 
   * @param nodeFirst extreme node of to be created link
   * @param nodeLast extreme node of to be created link
   * @param osmWay to populate link data with
   * @param tags to populate link data with
   * @return created or fetched link
   * @throws PlanItException thrown if error
   */
  private Link createAndPopulateLink(Node nodeFirst, Node nodeLast, OsmWay osmWay, Map<String, String> tags) throws PlanItException {

    /* osm way is directional, link is not, check existence */
    Link link = null;
    if(nodeFirst != null) {
      link = (Link) nodeFirst.getEdge(nodeLast);      
    }
               
    if(link == null) {

      /* length and geometry */
      double linkLength = 0;      
      LineString lineSring = extractLinkGeometry(osmWay);
      /* update the length based on the geometry */
      linkLength = geoUtils.getDistanceInKilometres(lineSring);
      
      /* create link */
      link = network.links.registerNew(nodeFirst, nodeLast, linkLength, true);      
      link.setGeometry(lineSring);      
      
      /* external id */
      link.setExternalId(osmWay.getId());
      
      if(tags.containsKey(OsmTags.NAME)) {
        link.setName(tags.get(OsmTags.NAME));
      }
    }
    return link;      
  }  
  
  /** extract a link from the way
   * @param osmWay the way to process
   * @param tags tags that belong to the way
   * @return the link corresponding to this way
   * @throws PlanItException thrown if error
   */
  private Link extractLink(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    
    /* collect memory model nodes */
    int firstNodeIndex = 0;
    int lastNodeIndex = osmWay.getNumberOfNodes()-1;
    Node nodeFirst = extractNode(osmWay.getNodeId(firstNodeIndex));
    Node nodeLast = extractNode(osmWay.getNodeId(lastNodeIndex));
                          
    Link link = createAndPopulateLink(nodeFirst, nodeLast, osmWay, tags);   
    if(link != null) {
 
      /* lay index on internal nodes of link to allow for splitting the link if needed due to intersecting internally with other links */
      for(int nodeIndex = firstNodeIndex+1; nodeIndex < lastNodeIndex-1;++nodeIndex) {
        OsmNode internalNode = osmNodes.get(osmWay.getNodeId(nodeIndex));
        linkInternalOsmNodes.putIfAbsent(internalNode.getId(), new ArrayList<Link>());
        linkInternalOsmNodes.get(internalNode.getId()).add(link);
      }                       
 
      profiler.logLinkStatus(network.links.size());
    }
    return link;
  }
  
  
  /** Extract a link segment from the way corresponding to the link and the indicated direction
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param linkSegmentType the link segment type corresponding to this way
   * @param directionAb the direction to create the segment for  
   * @return created link segment, or null if already exists
   * @throws PlanItException thrown if error
   */  
  private MacroscopicLinkSegment extractMacroscopicLinkSegment(OsmWay osmWay, Map<String, String> tags, Link link, MacroscopicLinkSegmentType defaultLinkSegmentType, boolean directionAb) throws PlanItException {
    MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) link.getEdgeSegment(directionAb);
    if(linkSegment == null) {
      linkSegment = network.linkSegments.registerNew(link, directionAb, true /*register on nodes and link*/);      
    }else{
      LOGGER.warning(String.format(
          "Already exists link segment (id:%d) between OSM nodes (%s, %s) of OSM way (%d), ignored entity",linkSegment.getId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), osmWay.getId()));
    }
    
    /* link segment type */
    linkSegment.setLinkSegmentType(defaultLinkSegmentType);
        
    profiler.logLinkSegmentStatus(network.linkSegments.size());      
    return linkSegment;
  }
  
  /** Extract one or two link segments from the way corresponding to the link
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param linkSegmentType the default link segment type corresponding to this way  
   * @return created link segment, or null if already exists
   * @throws PlanItException thrown if error
   */
  private void extractMacroscopicLinkSegments(OsmWay osmWay, Map<String, String> tags, Link link, MacroscopicLinkSegmentType linkSegmentType) throws PlanItException {
    OsmDirection direction = new OsmDirection(tags, settings.getCountryName());
            
    /* determine the direction of the way in terms of the PLANit link */
    boolean directionAb = true;
    if(!direction.isReverseDirection() && osmWay.getNodeId(0) != (long)link.getVertexA().getExternalId() && osmWay.getNodeId(0) == (long)link.getVertexB().getExternalId()) {
      directionAb = false;
    }
    
    //TODO: process access restrictions + designated tags to activate/affirm foot,bicycle support
    //      --> leads to (on-the-fly creation of) custom link segment type replacing the default link segment type
    
    /* direction 1 */
    directionAb = direction.isReverseDirection() ? !directionAb : directionAb;
    extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentType, directionAb);
    
    /* direction 2 */
    if(!direction.isOneWay()){
      directionAb = !directionAb;
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentType, directionAb);
    }
    
    /* speed */
    populateLinkSegmentsSpeed(link, direction, tags);
    /* lanes */
    populateLinkSegmentsLanes(link, direction, tags);
    
  }  
  
  /**
   * now parse the remaining circular osmWays, which by default are converted into multiple links/linksegments for each part of
   * the circular way in between connecting in and outgoing links/linksegments that were parsed during the regular parsing phase
   * 
   * @param circularOsmWay the circular osm way to parse 
   * @throws PlanItException thrown if error
   */
  protected void handleCircularWay(OsmWay circularOsmWay) throws PlanItException {
    Map<String, String> osmWayTags = OsmModelUtil.getTagsAsMap(circularOsmWay);
    
    TEST IF THIS WORKS
    MacroscopicLinkSegmentType linkSegmentType = getLinkSegmentType(circularOsmWay, osmWayTags);
    if(linkSegmentType != null) {
    
      int firstNodeIndex = 0;
      int lastNodeIndex = circularOsmWay.getNumberOfNodes()-1;
      Node nodeFirst = extractNode(circularOsmWay.getNodeId(firstNodeIndex));
      /* find first node on geometry that is part of an already parsed link (Exclude the last node since it is the same as the first*/
      for(int index = 1 ; index <= lastNodeIndex ; ++index) {
        long osmNodeId = circularOsmWay.getNodeId(index);
        if(nodesByExternalId.containsKey(osmNodeId)) {
          
          Node nodeLast = extractNode(osmNodeId);
          /* create link from start node to the intermediate node that attaches to an already existing planit link on the circular way */        
          Link link = createAndPopulateLink(nodeFirst, nodeLast, circularOsmWay, osmWayTags);
          TRUNCATE GEOMETRY TO FROM NODE FIRST TO NODE LAST SINCE ORIGINAL GOES ROUND
          extractMacroscopicLinkSegments(circularOsmWay, osmWayTags, link, linkSegmentType);
          
          nodeFirst = nodeLast;
        }
      }
      
      /* last partial link should connect to final node of circular way, if not something went wrong */
      if( ((Long)nodeFirst.getExternalId()) != circularOsmWay.getNodeId(lastNodeIndex)) {
        throw new PlanItException(String.format("circular OSM way %s was not parsed correctly, attempt to break into separate PLANit links failed",circularOsmWay.getId()));
      }
    }
  }  
  
  /**
   * whenever we find that internal nodes are used by more than one link OR a node is an extreme node
   * on an existing link but also an internal link on another node, we break the links where this node
   * is internal. the end result is a situations where all nodes used by more than one link are extreme 
   * nodes, i.e., start/end nodes
   * @throws PlanItException  thrown if error
   */
  protected void breakLinksWithInternalConnections() throws PlanItException {
    /* track the broken links of by original OSM link id (external id) */
    Map<Long, Set<Link>> brokenLinksByOriginalOsmLinkId = new HashMap<Long, Set<Link>>();
        
    long linkIndex = -1;
    long originalNumberOfLinks = this.network.links.size();
    while(++linkIndex<originalNumberOfLinks) {
      Link link = this.network.links.get(linkIndex);       
            
      // 1. break links when a link's internal node is another existing link's extreme node 
      breakLinksWithInternalNode(link.getNodeA(), brokenLinksByOriginalOsmLinkId);
      linkInternalOsmNodes.remove(link.getNodeA().getExternalId());
      
      /* apply to node B as well */
      breakLinksWithInternalNode(link.getNodeB(), brokenLinksByOriginalOsmLinkId);
      linkInternalOsmNodes.remove(link.getNodeB().getExternalId());
    }
    
    //2. break links where an internal node of multiple links is shared, but it is never an extreme node of a link
    for(Entry<Long, List<Link>> entry : linkInternalOsmNodes.entrySet()) {        
      /* only intersection of links when at least two links are registered */
      if(entry.getValue().size() > 1) {
        /* node does not yet exist in PLANit network because it was internal node so far, so create it first */
        Node planitIntersectionNode = extractNode(entry.getKey());
        breakLinksWithInternalNode(planitIntersectionNode, brokenLinksByOriginalOsmLinkId);
      }
    }
    
    linkInternalOsmNodes.clear();
  }  
    

  /**
   * @return the network
   */
  protected MacroscopicNetwork getNetwork() {
    return network;
  }
  
  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param way the way
   * @param tags the tags of this way
   * @return the link segment type if available, otherwise nullis returned
   */
  protected MacroscopicLinkSegmentType getLinkSegmentType(OsmWay osmWay, Map<String, String> tags) {
    MacroscopicLinkSegmentType linkSegmentType = null;
    String osmTypeValueToUse = null;
    String osmTypeKeyToUse = null;
    
    /* highway (road) or railway (rail) */
    if (tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmTypeKeyToUse = OsmHighwayTags.HIGHWAY;        
    }else if(tags.containsKey(OsmRailWayTags.RAILWAY)) {
      osmTypeKeyToUse = OsmRailWayTags.RAILWAY;            
    }
    
    if(osmTypeKeyToUse != null) {  
      osmTypeValueToUse = tags.get(osmTypeKeyToUse);           
      linkSegmentType = network.getSegmentTypeByOsmTag(osmTypeValueToUse);            
      if(linkSegmentType != null) {
        profiler.incrementOsmTagCounter(osmTypeValueToUse);        
        return linkSegmentType;
      }
      
      /* determine the reason why we couldn't find it */
      if(!settings.isOsmWayTypeUnsupported(osmTypeKeyToUse, osmTypeValueToUse)) {
        /*... not unsupported so something is not properly configured, or the osm file is corrupt or not conform the standard*/
        LOGGER.warning(String.format(
            "no link segment type available for OSM way: %s:%s (id:%d) --> ignored. Consider explicitly supporting or unsupporting this type", 
            osmTypeKeyToUse, osmTypeValueToUse, osmWay.getId()));              
      }
    }
    
    return linkSegmentType;
  }  
  

  /**
   * constructor
   * 
   * @param settings for the handler
   */
  public PlanitOsmHandler(final PlanitOsmNetwork network, final PlanitOsmSettings settings) {
    this.network = network;
    this.geoUtils = new PlanitJtsUtils(settings.getSourceCRS());
    this.network.setCoordinateReferenceSystem(settings.getSourceCRS());
    
    this.settings = settings;
    this.profiler  = new PlanitOsmHandlerProfiler();
    
    this.osmNodes = new HashMap<Long, OsmNode>();
    this.linkInternalOsmNodes = new HashMap<Long, List<Link>>();
    this.osmCircularWays = new HashMap<Long, OsmWay>();
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(network.linkSegments.size()>0,"network is expected to be empty at start of parsing OSM network, but it has link segments");
    PlanItException.throwIf(network.links.size()>0,"network is expected to be empty at start of parsing OSM network, but it has links");
    PlanItException.throwIf(network.nodes.size()>0,"network is expected to be empty at start of parsing OSM network, but it has nodes");
    
    /* create the supported link segment types on the network */
    network.createOsmCompatibleLinkSegmentTypes(settings);
    /* when modes are deactivated causing supported osm way types to have no active modes, add them to unsupport way types to avoid warnings during parsing */
    settings.excludeOsmWayTypesWithoutActiveModes();
    logUnsupportedOsmWayTypes();
  }  


  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // not used
  }

  /**
   * construct PLANit nodes from OSM nodes
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {
    /* store for later processing */
    osmNodes.put(osmNode.getId(), osmNode);   
  }

  /**
   * parse an osm way to extract link and link segments (including type). If insufficient information
   * is available the handler will try to infer the missing data by using defaults set by the user
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);

    try {
      
      if(PlanitOsmUtils.isCircularWay(osmWay)) {
        /* postpone creation of link(s) for roundabouts */
        /* Note: in OSM roundabouts are a circular way, in PLANit, they comprise several one-way link connecting exists and entries to the roundabout */
        osmCircularWays.put(osmWay.getId(), osmWay);        
      }else
      {
        /* a default link segment type should be available as starting point*/
        MacroscopicLinkSegmentType linkSegmentType = getLinkSegmentType(osmWay, tags);
        if(linkSegmentType != null) {
    
          /* a link only consists of start and end node, no direction and has no model information */
          Link link = extractLink(osmWay, tags);                
          
          /* a macroscopic link segment is directional and can have a shape, it also has model information */
          extractMacroscopicLinkSegments(osmWay, tags, link, linkSegmentType);          
        }   
      }           
    } catch (PlanItException e) {
      if(e.getCause() != null && e.getCause() instanceof PlanItException) {
        LOGGER.severe(e.getCause().getMessage());
      }
      LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
    }         
  }

  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    // delegate
  }

  @Override
  public void complete() throws IOException {
    
    /* process circular ways*/
    osmCircularWays.forEach((k,v) -> handleCircularWay(v));
    
    /* break all links that have internal nodes that are extreme nodes of other links*/
    try {
      breakLinksWithInternalConnections();
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to break OSM links with internal intersections");
    }
    
    /* stats*/
    profiler.logProfileInformation(network);
        
    LOGGER.info("DONE");

    /* free memory */
    osmCircularWays.clear();    
    osmNodes.clear();
    nodesByExternalId.clear();
  }

}

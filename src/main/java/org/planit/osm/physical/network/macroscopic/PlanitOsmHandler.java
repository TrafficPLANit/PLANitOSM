package org.planit.osm.physical.network.macroscopic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.coordinate.LineString;
import org.opengis.geometry.coordinate.Position;
import org.planit.geo.PlanitGeoUtils;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.util.OsmDirection;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmLaneTags;
import org.planit.osm.util.OsmSpeedTags;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.utils.arrays.ArrayUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

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
  private final PlanitGeoUtils geoUtils;
  
  /** utility class for profiling this instance */
  private final PlanitOsmHandlerProfiler profiler;
  
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays;
  
  /**
   * track the nodes by their external id so they can by looked up quickly while parsing ways
   */
  private final Map<Long,Node> nodesByExternalId = new HashMap<Long, Node>();
     
  /**
   * Log all de-activated OSM highway types
   */  
  private void logUnsupportedOSMHighwayTypes() {
    settings.unsupportedOSMLinkSegmentTypes.forEach( 
        osmTag -> LOGGER.info(String.format("highway:%s DEACTIVATED", osmTag)));    
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
      speedLimitAbKmh = settings.getDefaultSpeedLimitByHighwayType(tags.get(OsmHighwayTags.HIGHWAY));
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

    /* collect total and direction specific lane information */
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
    
    /* we assume that only when both are not set something went wrong, otherwise it is assumed it is a one-way link and it is properly configured */
    if(lanesAb==null && lanesBa==null) {
      lanesAb = settings.getDefaultDirectionalLanesByHighwayType(tags.get(OsmHighwayTags.HIGHWAY));
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
    List<Position> positionList = new ArrayList<Position>(osmWay.getNumberOfNodes());
    int numberOfNodes = osmWay.getNumberOfNodes();
    for(int index = 0; index < numberOfNodes; ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode == null) {
        throw new PlanItException(String.format("referenced osmNode %d in osmWay %d not available in OSM parser",osmWay.getNodeId(index), osmWay.getId()));
      }
      positionList.add(geoUtils.createDirectPosition(PlanitOsmUtils.getXCoordinate(osmNode),PlanitOsmUtils.getYCoordinate(osmNode)));
    }
    return  geoUtils.createLineStringFromPositions(positionList);
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
      DirectPosition geometry = null;
      try {
        geometry = geoUtils.createDirectPosition(PlanitOsmUtils.getXCoordinate(osmNode), PlanitOsmUtils.getYCoordinate(osmNode));
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
      }

      /* create and register */
      node = network.nodes.registerNew(osmNodeId);
      node.setCentrePointGeometry(geometry);
      nodesByExternalId.put(osmNodeId, node);
     
      profiler.logNodeStatus(network.nodes.size());   
    }
    return node;
  }
  
  /** extract a link from the way
   * @param osmWay the way to process
   * @param tags tags that belong to the way
   * @return the link corresponding to this way
   * @throws PlanItException thrown if error
   */
  private Link extractLink(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    
    /* collect memory model nodes */
    Node nodeFirst = extractNode(osmWay.getNodeId(0));
    Node nodeLast = extractNode(osmWay.getNodeId(osmWay.getNumberOfNodes()-1));
                  
    /* osm way is directional, link is not, check existence */
    Link link = null;
    if(nodeFirst != null) {
      link = (Link) nodeFirst.getEdge(nodeLast);           
    }
               
    if(link == null) {

      /* length and geometry */
      double linkLength = 0;      
      LineString lineSring = null;
      if(settings.isParseOsmWayGeometry()) {
        lineSring = extractLinkGeometry(osmWay);
        /* update the length based on the geometry */
        linkLength = geoUtils.getDistanceInKilometres(lineSring);
      }else {
        /* update length based on start and end node only */
        linkLength = geoUtils.getDistanceInKilometres(nodeFirst, nodeLast);
      }
      
      /* create link */
      link = network.links.registerNew(nodeFirst, nodeLast, linkLength, true);      
      if(settings.isParseOsmWayGeometry()) {
        link.setGeometry(lineSring);      
      }
      
      /* external id */
      link.setExternalId(osmWay.getId());
    }               

    profiler.logLinkStatus(network.links.size());
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
      linkSegment = network.linkSegments.createAndRegisterNew(link, directionAb, true /*register on nodes and link*/);      
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
    OsmDirection direction = new OsmDirection(tags);
        
    /* determine the direction of the way in terms of the PLANit link */
    boolean directionAb = true;
    if(!direction.isReverseDirection() && osmWay.getNodeId(0) == (long)link.getVertexB().getExternalId()) {
      directionAb = false;
    }
        
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
   * now parse the remaining cicular osmWays, which by default are converted into multiple links/linksegments for each part of
   * the circular way in between connecting in and outgoing links/linksegments
   * 
   * @param circularOsmWay the circular osm way to parse 
   */
  protected void handleCircularWay(OsmWay circularOsmWay) {
    
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
    if (tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      
      String highWayType = tags.get(OsmHighwayTags.HIGHWAY);
      profiler.incrementOsmTagCounter(highWayType);            
      linkSegmentType = network.getSegmentTypeByOSMTag(highWayType);            
      if(linkSegmentType != null) {
        return linkSegmentType;
      }
      
      /* determine the reason why we couldn't find it */
      if(!settings.isOSMHighwayTypeUnsupported(highWayType)) {
        /*... not unsupported so something is not properly configured, or the osm file is corrupt or not conform the standard*/
        LOGGER.warning(String.format(
            "no link segment type available for OSM way: highway:%s (id:%d) --> ignored. Consider explicitly supporting or unsupporting this type", 
            highWayType, osmWay.getId()));              
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
    this.settings = settings;
    this.geoUtils = new PlanitGeoUtils(settings.getSourceCRS());
    this.profiler  = new PlanitOsmHandlerProfiler();
    
    this.osmNodes = new HashMap<Long, OsmNode>();
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
    network.createOSMCompatibleLinkSegmentTypes(settings);
    logUnsupportedOSMHighwayTypes();
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
    
    /* process circular ways last */
    osmCircularWays.forEach((k,v) -> handleCircularWay(v));
    
    /* stats*/
    profiler.logProfileInformation(network);
        
    // not used
    LOGGER.info("DONE");
  }

}

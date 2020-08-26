package org.planit.osm.physical.network.macroscopic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.coordinate.LineString;
import org.opengis.geometry.coordinate.Position;
import org.planit.geo.PlanitGeoUtils;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.util.OsmDirection;
import org.planit.osm.util.OsmTags;
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
  
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /**
   * track the nodes by their external id so they can by looked up quickly while parsing ways
   */
  private final Map<Long,Node> nodesByExternalId = new HashMap<Long, Node>();
  
  /**
   * track a counter by highway tag of the encountered entities
   */
  private final Map<String, LongAdder> counterByHighwayTag;
    
  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */
  private long moduloLoggingCounterLinks = 500;

  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingCounterLinkSegments = 500;
  
  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingCounterNodes = 500;
   
  /**
   * Log all de-activated OSM highway types
   */  
  private void logUnsupportedOSMHighwayTypes() {
    settings.unsupportedOSMLinkSegmentTypes.forEach( 
        osmTag -> LOGGER.info(String.format("highway:%s DEACTIVATED", osmTag)));    
  }  
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  private static double getXCoordinate(OsmNode osmNode) {
    return osmNode.getLongitude();
  }
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  private static double getYCoordinate(OsmNode osmNode) {
    return osmNode.getLatitude();
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
    if (tags.containsKey(OsmTags.HIGHWAY)) {
      
      String highWayType = tags.get(OsmTags.HIGHWAY);
      counterByHighwayTag.putIfAbsent(highWayType, new LongAdder());
      counterByHighwayTag.get(highWayType).increment();
            
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
      positionList.add(geoUtils.createDirectPosition(getXCoordinate(osmNode),getYCoordinate(osmNode)));
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
        geometry = geoUtils.createDirectPosition(getXCoordinate(osmNode), getYCoordinate(osmNode));
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
      }

      /* create and register */
      node = network.nodes.registerNewNode(osmNodeId);
      node.setCentrePointGeometry(geometry);
      nodesByExternalId.put(osmNodeId, node);
      
      if(network.nodes.getNumberOfNodes() >= moduloLoggingCounterNodes) {
        LOGGER.info(String.format("Created %d nodes out of OSM nodes",network.nodes.getNumberOfNodes()));
        moduloLoggingCounterNodes *=2;
      }      
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
      link = network.links.registerNewLink(
          nodeFirst, nodeLast, geoUtils.getDistanceInKilometres(nodeFirst, nodeLast), true /*register on nodes */);
    }    
    
    /* geometry of link */
    if(settings.isParseOsmWayGeometry()) {
      link.setGeometry(extractLinkGeometry(osmWay));
    }    
    
    if(network.links.getNumberOfLinks() == moduloLoggingCounterLinks) {
      LOGGER.info(String.format("Created %d links out of OSM ways",network.links.getNumberOfLinks()));
      moduloLoggingCounterLinks *=2;
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
      linkSegment = network.linkSegments.createAndRegisterLinkSegment(link, directionAb, true /*register on nodes and link*/);      
    }else{
      LOGGER.warning(String.format(
          "Already exists link segment (id:%d) between start and end node of OSM way (%d), ignored entity",linkSegment.getId(),osmWay.getId()));
    }
        
    if(network.linkSegments.getNumberOfLinkSegments() == moduloLoggingCounterLinkSegments) {
      LOGGER.info(String.format("Created %d links segments out of OSM ways",network.linkSegments.getNumberOfLinkSegments()));
      moduloLoggingCounterLinkSegments *=2;    
    }   
    
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
    if(osmWay.getNodeId(0) == (long)link.getVertexB().getExternalId()) {
      directionAb = false;
    }
    
    directionAb = direction.isReverseDirection() ? !directionAb : directionAb;
    extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentType, directionAb);
    
    if(!direction.isOneWay()){
      directionAb = !directionAb;
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentType, directionAb);
    }       
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
    this.counterByHighwayTag = new HashMap<String, LongAdder>();
    this.osmNodes = new HashMap<Long, OsmNode>();
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(network.linkSegments.getNumberOfLinkSegments()>0,"network is expected to be empty at start of parsing OSM network, but it has link segments");
    PlanItException.throwIf(network.links.getNumberOfLinks()>0,"network is expected to be empty at start of parsing OSM network, but it has links");
    PlanItException.throwIf(network.nodes.getNumberOfNodes()>0,"network is expected to be empty at start of parsing OSM network, but it has nodes");
    
    /* create the supported link segment types on the network */
    network.createOSMCompatibleLinkSegmentTypes(settings);
    logUnsupportedOSMHighwayTypes();
  }  

  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // not used
  }

  /**
   * construct PLANit cnodes from OSM nodes
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
     
      /* a default link segment type should be available as starting point*/
      MacroscopicLinkSegmentType linkSegmentType = getLinkSegmentType(osmWay, tags);
      if(linkSegmentType != null) {
  
        /* a link only consists of start and end node, no direction and has no model information */
        Link link = extractLink(osmWay, tags);
        
        /* a macroscopic link segment is directional and can have a shape, it also has model information */
        extractMacroscopicLinkSegments(osmWay, tags, link, linkSegmentType);
        
      }
      
    } catch (PlanItException e) {
      LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId()));
    }         
  }


  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    // delegate
  }

  @Override
  public void complete() throws IOException {
    
    /* stats of encountered way entities */
    counterByHighwayTag.forEach( 
        (type,counter) -> LOGGER.info(String.format(" [PROCESSED] highway:%s count:%d", type, counter.longValue())));
    
    /* stats on exact numbr of created PLANit network objects */
    LOGGER.info(String.format(" [CREATED] PLANit %d nodes",network.nodes.getNumberOfNodes()));
    LOGGER.info(String.format(" [CREATED] PLANit %d links",network.links.getNumberOfLinks()));
    LOGGER.info(String.format(" [CREATED] PLANit %d links segments ",network.linkSegments.getNumberOfLinkSegments()));
    
    // not used
    LOGGER.info("DONE");
  }

}

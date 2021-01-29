package org.planit.osm.physical.network.macroscopic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.planit.geo.PlanitJtsUtils;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.settings.PlanitOsmSettings;
import org.planit.osm.tags.OsmAccessTags;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmLaneTags;
import org.planit.osm.tags.OsmOneWayTags;
import org.planit.osm.tags.OsmRailFeatureTags;
import org.planit.osm.tags.OsmRailWayTags;
import org.planit.osm.tags.OsmSpeedTags;
import org.planit.osm.tags.OsmTags;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.utils.arrays.ArrayUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.Edge;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Takes care of populating a PLANit layer based on the OSM way information that has been identified
 * as relevant to this layer by the {@link PlanitOsmHandler}
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkLayerHandler {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkLayerHandler.class.getCanonicalName());
  
  // local members only
  
  /** utility class for profiling this layer for logging purposes */
  private final PlanitOsmHandlerProfiler profiler;  
  
  /** geo utility instance based on network wide crs this layer is part of */
  private final PlanitJtsUtils geoUtils;  
  
  
  /**
   * track the PLANit nodes craeted on this layer by their original OSM id  so they can by looked up quickly while parsing ways
   */
  private final Map<Long, Node> nodesByOsmId = new HashMap<Long, Node>();
  
  /** Mapping from Osm node id to the links they are internal to. When done parsing, we verify if any
   * entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existin link's extreme node is internal to any other link, the link where
   * this node is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link
   */
  private final Map<Long, List<Link>> linkInternalOsmNodes = new HashMap<Long, List<Link>>();
  
  // references
  
  /** reference to parsed OSM nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** settings relevant to this parser */
  private PlanitOsmSettings settings;
  
  /** the network layer to use */
  private MacroscopicPhysicalNetwork networkLayer;  
  
  /** register all nodes within the provided (inclusive) range as link internal nodes for the passed in link
   * 
   * @param link to register link internal ndoes for
   * @param startIndex the start index
   * @param endIndex the end index
   * @param osmWay the link corresponds to
   */
  private void registerLinkInternalOsmNodes(Link link, int startIndex, int endIndex, OsmWay osmWay) {
    /* lay index on internal nodes of link to allow for splitting the link if needed due to intersecting internally with other links */
    for(int nodeIndex = startIndex; nodeIndex <= endIndex;++nodeIndex) {
      OsmNode internalNode = osmNodes.get(osmWay.getNodeId(nodeIndex));
      linkInternalOsmNodes.putIfAbsent(internalNode.getId(), new ArrayList<Link>());
      linkInternalOsmNodes.get(internalNode.getId()).add(link);
    }   
  }   
  
  /** create and populate link if it does not already exists for the given two PLANit nodes based on the passed in osmWay information. 
   * In case a new link is to be created but internal nodes of the geometry are missing due to the meandering road falling outside the boundaing box that is being parsed, null is returned 
   * and the link is not created
   * 
   * @param osmWay to populate link data with
   * @param tags to populate link data with
   * @param startNodeIndex of the OSM way that will represent start node of this link
   * @param endNodeIndex of the OSM way that will represent end node of this link
   * @return created or fetched link
   * @throws PlanItException thrown if error
   */
  private Link createAndPopulateLink(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex) throws PlanItException {
    
    PlanItException.throwIf(startNodeIndex < 0 || startNodeIndex >= osmWay.getNumberOfNodes(), String.format("invalid start node index %d when extracting link from Osm way %s",startNodeIndex, osmWay.getId()));
    PlanItException.throwIf(endNodeIndex < 0 || endNodeIndex >= osmWay.getNumberOfNodes(), String.format("invalid end node index %d when extracting link from Osm way %s",startNodeIndex, osmWay.getId()));   
     
    
    /* collect memory model nodes */
    Node nodeFirst = extractNode(osmWay.getNodeId(startNodeIndex));
    Node nodeLast = extractNode(osmWay.getNodeId(endNodeIndex));       
    if(nodeFirst==null || nodeLast==null) {
      LOGGER.fine(String.format("OSM way %s could not be parsed, one or more nodes could not be created, likely outside bounding box",osmWay.getId()));
      return null;
    }
      
    /* parse geometry */
    LineString lineString = null;          
    try {
      lineString = extractPartialLinkGeometry(osmWay, startNodeIndex, endNodeIndex);
    }catch (PlanItException e) {
      LOGGER.fine(String.format("OSM way %s internal geometry incomplete, one or more internal nodes could not be created, likely outside bounding box",osmWay.getId()));
      return null;
    }    
        
    Link link = null;
    /* osm way can be direction directional, PLANit link is never, check existence */
    if(nodeFirst != null) {
      Set<Edge> potentialEdges = nodeFirst.getEdges(nodeLast);
      for(Edge potentialEdge : potentialEdges) {
        Link potentialLink = ((Link)potentialEdge);
        if(link != null && potentialLink.getGeometry().equals(lineString)) {
          /* matching geometry, so they are in indeed the same link*/
          link = potentialLink;
          break;
        }        
      }
    }      
               
    /* when not present and valid geometry, create new link */
    if(link == null) {

      /* length and geometry */
      double linkLength = 0;      
      /* update the length based on the geometry */
      linkLength = geoUtils.getDistanceInKilometres(lineString);
      
      /* create link */
      link = networkLayer.links.registerNew(nodeFirst, nodeLast, linkLength, true);
      /* geometry */
      link.setGeometry(lineString);      
      /* XML id */
      link.setXmlId(Long.toString(link.getId()));
      /* external id */
      link.setExternalId(String.valueOf(osmWay.getId()));
      
      if(tags.containsKey(OsmTags.NAME)) {
        link.setName(tags.get(OsmTags.NAME));
      }
    }
    return link;      
  }  
  
  /** given the OSM way tags we construct or find the appropriate link segment type, if no better alternative could be found
   * than the one that is passed in is used, which is assumed to be the default link segment type for the OSM way.
   * <b>It is not assumed that changes to mode access are ALWAYS accompanied by an access=X. However when this tag is available we apply its umbrella result to either include or exclude all supported modes as a starting point</b>
   * 
   * The following access=X value tags correspond to a situation where all modes will be allowed unless specific exclusions are
   * provided:
   * 
   * access=
   *  <ul>
   *  <li>yes</li>
   *  </ul>
   *  
   *  If other access values are found it is assumed all modes are excluded unless specific inclusions are provided. Note that it is very well possible
   *  that due to the configuration chosen a modified link segment type is created that has no modes that are supported. In which case
   *  the link segment type is still created, but it is left to the user to identify the absence of supported planit modes and take appropriate action.
   *  
   * @param osmWay the tags belong to
   * @param tags of the OSM way to extract the link segment type for
   * @param direction information already extracted from tags
   * @param linkSegmentType use thus far for this way
   * @return the link segment types for the main direction and contraflow direction
   * @throws PlanItException thrown if error
   */  
  private MacroscopicLinkSegmentType extractDirectionalLinkSegmentTypeByOsmAccessTags(OsmWay osmWay, Map<String, String> tags, MacroscopicLinkSegmentType linkSegmentType, boolean forwardDirection) throws PlanItException {  
        
    /* identify explicitly excluded and included modes */
    continue here try to move these methods to this class
    Set<Mode> excludedModes = getExplicitlyExcludedModes(tags, forwardDirection);
    Set<Mode> includedModes = getExplicitlyIncludedModes(tags, forwardDirection);
        
    
    /* global access is defined for both ways, or explored direction coincides with the main direction of the one way */
    boolean isOneWay = OsmOneWayTags.isOneWay(tags);
    /*                                              two way || oneway->forward    || oneway->backward and reversed oneway */  
    boolean accessTagAppliesToExploredDirection =  !isOneWay || (forwardDirection || OsmOneWayTags.isReversedOneWay(tags));
    
    /* supplement with implicitly included modes for the explored direction */
    if(accessTagAppliesToExploredDirection && tags.containsKey(OsmAccessTags.ACCESS)) {
      String accessValue = tags.get(OsmAccessTags.ACCESS).replaceAll(PlanitOsmUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");    
      if(PlanitOsmUtils.matchesAnyValueTag(accessValue, OsmAccessTags.getPositiveAccessValueTags())) {
        includedModes.addAll(networkLayer.getSupportedModes());
        includedModes.removeAll(excludedModes);
      }else {
        excludedModes.addAll(networkLayer.getSupportedModes());
        excludedModes.removeAll(includedModes);
      }
    }
    
    /* reduce included modes to only the modes supported by the layer the link segment type resides on*/
    if(!includedModes.isEmpty()) {
      includedModes.retainAll(networkLayer.getSupportedModes());
    }
    
    /* identify differences with default link segment type in terms of mode access */
    Set<Mode> toBeAddedModes = linkSegmentType.getUnAvailableModesFrom(includedModes);
    Set<Mode> toBeRemovedModes = linkSegmentType.getAvailableModesFrom(excludedModes);    
    
    MacroscopicLinkSegmentType finalLinkSegmentType = linkSegmentType;
    if(!toBeAddedModes.isEmpty() || !toBeRemovedModes.isEmpty()) {
      
      finalLinkSegmentType = modifiedLinkSegmentTypes.getModifiedLinkSegmentType(linkSegmentType, toBeAddedModes, toBeRemovedModes);
      if(finalLinkSegmentType==null) {
        /* even though the segment type is modified, the modified version does not yet exist on the PLANit network, so create it */
        finalLinkSegmentType = network.getDefaultNetworkLayer().linkSegmentTypes.registerUniqueCopyOf(linkSegmentType);
        
        /* XML id */
        finalLinkSegmentType.setXmlId(Long.toString(finalLinkSegmentType.getId()));
        
        /* update mode properties */
        if(!toBeAddedModes.isEmpty()) {
          double osmWayTypeMaxSpeed = settings.getDefaultSpeedLimitByOsmWayType(tags);          
          network.addLinkSegmentTypeModeProperties(finalLinkSegmentType, toBeAddedModes, osmWayTypeMaxSpeed);
        }
        if(!toBeRemovedModes.isEmpty()) {
          finalLinkSegmentType.removeModeProperties(toBeRemovedModes);
        }
        
        /* register modification */
        modifiedLinkSegmentTypes.addModifiedLinkSegmentType(linkSegmentType, finalLinkSegmentType, toBeAddedModes, toBeRemovedModes);
      }      
    }       
    
    return finalLinkSegmentType;
  }  
  
  /** given the OSM way tags we construct or find the appropriate link segment types for both directions, if no better alternative could be found
   * than the one that is passed in is used, which is assumed to be the default link segment type for the OSM way.
   * <b>It is not assumed that changes to mode access are ALWAYS accompanied by an access=X. However when this tag is available we apply its umbrella result to either include or exclude all supported modes as a starting point</b>
   *  
   * @param osmWay the tags belong to
   * @param tags of the OSM way to extract the link segment type for
   * @param direction information already extracted from tags
   * @param allowedModes modes allowed for this type
   * @param layer the link segment type is destined for, used to identify the modes that at most can be supported by the link segment type
   * @param linkSegmentType use thus far for this way
   * @return the link segment types for the forward direction and backward direction as per OSM specification of forward and backward. When no allowed modes exist in a direction the link segment type is set to null
   * @throws PlanItException thrown if error
   */
  private Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> extractLinkSegmentTypeByOsmAccessTags(final OsmWay osmWay, final Map<String, String> tags, MacroscopicLinkSegmentType linkSegmentType) throws PlanItException {
    
    /* collect the link segment types for the two possible directions (forward, i.e., in direction of the geometry, and backward, i.e., the opposite of the geometry)*/
    boolean forwardDirection = true;
    MacroscopicLinkSegmentType  forwardDirectionLinkSegmentType = extractDirectionalLinkSegmentTypeByOsmAccessTags(osmWay, tags, linkSegmentType, forwardDirection);
    MacroscopicLinkSegmentType  backwardDirectionLinkSegmentType = extractDirectionalLinkSegmentTypeByOsmAccessTags(osmWay, tags, linkSegmentType, !forwardDirection);
    
    /* reset when no modes are available, in which case no link segment should be created for the direction */
    if(!forwardDirectionLinkSegmentType.hasAvailableModes()) {
      forwardDirectionLinkSegmentType = null;
    }
    if(!backwardDirectionLinkSegmentType.hasAvailableModes()) {
      backwardDirectionLinkSegmentType = null;
    }    
        
    return Pair.create(forwardDirectionLinkSegmentType, backwardDirectionLinkSegmentType);    
  }  
    
  /** extract geometry from the OSM way based on the start and end node index, only the portion of geometry in between the two indices will be collected.
   * Note that it is possible to have a smaller end node index than start node index in which case, the geometry is constructed such that it overflows from the
   * end and starting back at the beginning.
   *  
   * @param osmWay to extract geometry from
   * @param startNodeIndex of geometry
   * @param endNodeIndex of the geometry
   * @return (partial) geometry
   * @throws PlanItException throw if error
   */
  private LineString extractPartialLinkGeometry(OsmWay osmWay, int startNodeIndex, int endNodeIndex) throws PlanItException {
    LineString lineString = extractLinkGeometry(osmWay);        
    if(startNodeIndex>0 || endNodeIndex < (osmWay.getNumberOfNodes()-1)) {          
      /* update geometry and length in case link represents only a subsection of the OSM way */
      LineString updatedGeometry = PlanitJtsUtils.createCopyWithoutCoordinatesBefore(startNodeIndex, lineString);
      if(endNodeIndex < startNodeIndex) {
        /* When the last node position is located before the first (possible because the way is circular)*/
        /* supplement with coordinates from the beginning of the original circular way */
        LineString overFlowGeometry = PlanitJtsUtils.createCopyWithoutCoordinatesAfter(endNodeIndex, lineString);
        updatedGeometry = PlanitJtsUtils.concatenate(updatedGeometry, overFlowGeometry);
        /* since circular ways include one node twice and this node is now part of the overflow of this section, we must remove it */
        updatedGeometry = PlanitJtsUtils.createCopyWithoutAdjacentDuplicateCoordinates(updatedGeometry);
      }else {
        /* present, so simply remove coordinates after */
        updatedGeometry = PlanitJtsUtils.createCopyWithoutCoordinatesAfter(endNodeIndex-startNodeIndex, updatedGeometry);
      }  
      lineString = updatedGeometry;
    }
    return lineString;
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
    return  PlanitJtsUtils.createLineStringFromCoordinates(coordinates);
  }  
  
  /** Determine the speed limits in the forward and backward direction (if any).
   * 
   * @param link pertaining to the osmway tags
   * @param tags assumed to contain speed limit information
   * @return speed limtis in forward and backward direction
   * @throws PlanItException thrown if error
   */
  private Pair<Double,Double> extractDirectionalSpeedLimits(Link link, Map<String, String> tags) throws PlanItException {
    Double speedLimitForwardKmh = null;
    Double speedLimitBackwardKmh = null;
    
    /* (lane specific) backward or forward speed limits */
    if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)|| tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
      /* check for backward speed limit */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)) {
        speedLimitBackwardKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_BACKWARD));        
      }
      /* check for backward speed limit per lane */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)); 
        speedLimitBackwardKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);           
      }
    }
    if( tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD) || tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)){
      /* check for forward speed limit */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD)) {
        speedLimitForwardKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD));  
      }
      /* check for forward speed limit per lane */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)) {
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)); 
        speedLimitForwardKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);   
      }
    }
    
    /* if any of the two are not yet found, find general speed limit information not tied to direction */
    if(speedLimitBackwardKmh==null || speedLimitForwardKmh==null) {
      Double nonDirectionalSpeedLimitKmh = null;
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED)) {
        /* regular speed limit for all available directions and across all modes */
        nonDirectionalSpeedLimitKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED));
      }else if(tags.containsKey(OsmSpeedTags.MAX_SPEED_LANES)) {
        /* check for lane specific speed limit */
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_LANES));
        /* Note: PLANit does not support lane specific speeds at the moment, maximum speed across lanes is selected */      
        nonDirectionalSpeedLimitKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);
      }else
      { /* no speed limit information, revert to defaults */
        nonDirectionalSpeedLimitKmh = settings.getDefaultSpeedLimitByOsmWayType(tags);
        profiler.incrementMissingSpeedLimitCounter();
      }
      
      if(nonDirectionalSpeedLimitKmh!=null) {
        speedLimitForwardKmh = (speedLimitForwardKmh==null) ? nonDirectionalSpeedLimitKmh : speedLimitForwardKmh;
        speedLimitBackwardKmh = (speedLimitBackwardKmh==null) ? nonDirectionalSpeedLimitKmh : speedLimitBackwardKmh;
      }else {
        throw new PlanItException(String.format("no default speed limit available for OSM way %s",link.getExternalId()));
      }
    }
            
    /* mode specific speed limits*/
    //TODO
    
    return Pair.create(speedLimitForwardKmh,speedLimitBackwardKmh);
  }  
  
  /**
   * parse the number of lanes on the link in forward and backward direction (if explicitly set), when not available defaults are used
   * 
   * @param link for which lanes are specified (and its link segments)
   * @param tags containing lane information
   * @param linkSegmentTypes identified in forward and backward direction based on tags, useful to assign a minimum number of lanes in case no explicit lanes could be found by type is present 
   * @throws PlanItException 
   */  
  private Pair<Integer, Integer> extractDirectionalLanes(Link link, Map<String, String> tags, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes ) {
    Integer totalLanes = null;
    Integer lanesForward = null;
    Integer lanesBackward = null;    

    /* collect total and direction specific road based lane information */
    String osmWayKey = null;
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmWayKey = OsmHighwayTags.HIGHWAY;
      
      if(tags.containsKey(OsmLaneTags.LANES)) {
        totalLanes = Integer.parseInt(tags.get(OsmLaneTags.LANES));
      }    
      if(tags.containsKey(OsmLaneTags.LANES_FORWARD)) {
        lanesForward = Integer.parseInt(tags.get(OsmLaneTags.LANES_FORWARD));
      }
      if(tags.containsKey(OsmLaneTags.LANES_BACKWARD)) {
        lanesBackward = Integer.parseInt(tags.get(OsmLaneTags.LANES_BACKWARD));
      }
         
      /* one way exceptions or implicit directional lanes */
      if(totalLanes!=null && (lanesForward==null || lanesBackward==null)) {
        if(OsmOneWayTags.isOneWay(tags)) {
          boolean isReversedOneWay = OsmOneWayTags.isReversedOneWay(tags);
          if(isReversedOneWay && lanesBackward==null) {
            lanesBackward = totalLanes;
          }else if(!isReversedOneWay && lanesForward==null) {
            lanesForward = totalLanes;          
          }else if( (lanesForward==null && lanesBackward==null) && totalLanes%2==0) {
            /* two directions, with equal number of lanes does not require directional tags, simply split in two */
            lanesBackward = totalLanes/2;
            lanesForward = lanesBackward;
          }
        }
      }
            
    /* convert number of tracks to lanes */
    }else if(tags.containsKey(OsmRailWayTags.RAILWAY)) {
      osmWayKey = OsmRailWayTags.RAILWAY;
      if(tags.containsKey(OsmRailFeatureTags.TRACKS)) {
        /* assumption is that same rail is used in both directions */
        lanesForward = Integer.parseInt(tags.get(OsmRailFeatureTags.TRACKS));
        lanesBackward = lanesForward;
      }       
    }
    
    /* we assume that only when both are not set something went wrong, otherwise it is assumed it is a one-way link and it is properly configured */
    if(lanesForward==null && lanesBackward==null) {
      lanesForward = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      lanesBackward = lanesForward;
      profiler.incrementMissingLaneCounter();
    }
    
    /* when no lanes are allocated for vehicle modes, but direction has activated modes (for example due to presence of opposite lanes, or active modes -> assign 1 lane */
    boolean missingLaneInformation = false;
    if(lanesForward == null && linkSegmentTypes.first()!=null) {
      lanesForward = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      missingLaneInformation = true;
    }
    if(lanesBackward == null && linkSegmentTypes.second()!=null) {
      lanesBackward = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      missingLaneInformation = true;
    }
    if(missingLaneInformation) {
      profiler.incrementMissingLaneCounter();
    }
    
    return Pair.create(lanesForward, lanesBackward);
        
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
  private MacroscopicLinkSegment extractMacroscopicLinkSegment(OsmWay osmWay, Map<String, String> tags, Link link, MacroscopicLinkSegmentType linkSegmentType, boolean directionAb) throws PlanItException {
    MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) link.getEdgeSegment(directionAb);
    if(linkSegment == null) {
      linkSegment = networkLayer.linkSegments.registerNew(link, directionAb, true /*register on nodes and link*/);
      /* Xml id */
      linkSegment.setXmlId(Long.toString(linkSegment.getId()));
      /* external id, identical to link since OSM has no directional ids */
      linkSegment.setExternalId(link.getExternalId());
    }else{
      LOGGER.warning(String.format(
          "Already exists link segment (id:%d) between OSM nodes (%s, %s) of OSM way (%d), ignored entity",linkSegment.getId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), osmWay.getId()));
    }
    
    /* link segment type */
    linkSegment.setLinkSegmentType(linkSegmentType);
        
    profiler.logLinkSegmentStatus(networkLayer.linkSegments.size());      
    return linkSegment;
  }  
  
  /** Extract one or two link segments from the way corresponding to the link
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param linkSegmentTypes the link segment types for the forward and backward direction of this way  
   * @return created link segment, or null if already exists
   * @throws PlanItException thrown if error
   */
  private void extractMacroscopicLinkSegments(OsmWay osmWay, Map<String, String> tags, Link link, Pair<MacroscopicLinkSegmentType,MacroscopicLinkSegmentType> linkSegmentTypes) throws PlanItException {
                
    /* match A->B of PLANit link to geometric forward/backward direction of OSM paradigm */
    boolean directionAbIsForward = link.isGeometryInAbDirection() ? true : false;
    if(!directionAbIsForward) {
      LOGGER.warning("directionAB is not forward in geometry SHOULD NOT HAPPEN!");
    }
    
    /* speed limits in forward and backward direction based on tags and defaults if missing*/
    Pair<Double,Double> speedLimits = extractDirectionalSpeedLimits(link, tags);
    /* lanes in forward and backward direction based on tags and defaults if missing */
    Pair<Integer,Integer> lanes = extractDirectionalLanes(link, tags, linkSegmentTypes);
    
    /* create link segment A->B when eligible */
    MacroscopicLinkSegmentType linkSegmentTypeAb = directionAbIsForward ? linkSegmentTypes.first() : linkSegmentTypes.second();
    if(linkSegmentTypeAb!=null) {
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentTypeAb, true /* A->B */);
      Double speedLimit = directionAbIsForward ? speedLimits.first() : speedLimits.second();
      link.getLinkSegmentAb().setPhysicalSpeedLimitKmH(speedLimit);
      link.getLinkSegmentAb().setNumberOfLanes(directionAbIsForward ? lanes.first() : lanes.second());
    }
    /* create link segment B->A when eligible */
    MacroscopicLinkSegmentType linkSegmentTypeBa = directionAbIsForward ? linkSegmentTypes.second() : linkSegmentTypes.first();
    if(linkSegmentTypeBa!=null) {
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentTypeBa, false /* B->A */);
      link.getLinkSegmentBa().setPhysicalSpeedLimitKmH(directionAbIsForward ? speedLimits.second() : speedLimits.first());
      link.getLinkSegmentBa().setNumberOfLanes(directionAbIsForward ? lanes.second() : lanes.first());
    }                 
    
  }   
 
  /**
   * Extract a PLANit node from the osmNode information
   * 
   * @param osmNodeId to convert
   * @return created or retrieved node
   * @throws PlanItException 
   */
  private Node extractNode(final long osmNodeId) throws PlanItException {
    
    Node node = nodesByOsmId.get(osmNodeId);
    if(node == null) {
      
      /* not yet created */      
      OsmNode osmNode = osmNodes.get(osmNodeId);
      if(osmNode == null){
        LOGGER.fine(String.format("referenced OSM node %s not available, likely outside bounding box",osmNodeId));
      }else {
     
        /* location info */
        Point geometry = null;
        try {
          geometry = PlanitJtsUtils.createPoint(PlanitOsmUtils.getXCoordinate(osmNode), PlanitOsmUtils.getYCoordinate(osmNode));
        } catch (PlanItException e) {
          LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
        }
  
        /* create and register */
        node = networkLayer.nodes.registerNew();
        /* XML id */
        node.setXmlId(Long.toString(node.getId()));
        /* external id */
        node.setExternalId(String.valueOf(osmNodeId));
        /* position */
        node.setPosition(geometry);
        
        nodesByOsmId.put(osmNodeId, node);
       
        profiler.logNodeStatus(networkLayer.nodes.size());
        
        /* remove from osmNodes as it has been processed */
        osmNodes.remove(osmNodeId);
      }
    }
    return node;
  }   
  
  
  /** extract a link from the way
   * @param osmWay the way to process
   * @param tags tags that belong to the way
   * @param endNodeIndex for this link compared to the full OSM way
   * @param startNodeIndex for this link compared to the full OSM way 
   * @return the link corresponding to this way
   * @throws PlanItException thrown if error
   */
  private Link extractLink(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex) throws PlanItException {
     
    /* create the link */
    Link link = createAndPopulateLink(osmWay, tags, startNodeIndex, endNodeIndex);   
    if(link != null) {
      /* register internal nodes for breaking links later on during parsing */
      registerLinkInternalOsmNodes(link,startNodeIndex+1,endNodeIndex-1, osmWay);                  
      profiler.logLinkStatus(networkLayer.links.size());
    }
    return link;
  }  
  
  /**
   * provide access to the profiler for this layer
   * 
   * @return profiler of this layer
   */
  protected PlanitOsmHandlerProfiler getProfiler() {
    return this.profiler;
  }

  /** Constructor
   * @param networkLayer to use
   * @param osmNodes reference to parsed osmNodes
   * @param settings used for this parser
   * @param geoUtils geometric utility class instance based on network wide crs
   */
  protected PlanitOsmNetworkLayerHandler(MacroscopicPhysicalNetwork networkLayer, Map<Long, OsmNode> osmNodes, PlanitOsmSettings settings, PlanitJtsUtils geoUtils) {
    this.networkLayer = networkLayer;
    this.osmNodes = osmNodes;
    this.geoUtils = geoUtils;
    this.settings = settings;
    
    this.profiler  = new PlanitOsmHandlerProfiler();
  }


  /**
   * extract OSM way's PLANit infrastructure for the part of the way that is indicated. When it is marked as being a (partial) section of a circular way, then
   * we only allow the presumed one way direction applicable when creating directional link segments. The result is a newly registered link, its nodes, and linksegment(s) on
   * the network layer. The parser will try to infer missing/default data by using defaults set by the user. The provided link segment types are based on the osmWay data
   * and are assumed to be readily available and provided by the PlanitOsmHandler when idenitfying the correct layer (this layer)
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @return created link (if any), if no link could be created null is returned
   * @throws PlanItException thrown if error
   */    
  public Link extractPartialOsmWay(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex,
      boolean isPartOfCircularWay, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes) throws PlanItException {

    Link link  = null;
    if(linkSegmentTypes!=null && linkSegmentTypes.anyIsNotNull() ) {
      
      /* a link only consists of start and end node, no direction and has no model information */
      link = extractLink(osmWay, tags, startNodeIndex, endNodeIndex);                                      
      if(link != null) {
        
        if(isPartOfCircularWay) {
          /* when circular we only accept one direction as accessible regardless of what has been identified so far;
           * clockwise equates to forward direction while anti-clockwise equates to backward direction */
          if(PlanitOsmUtils.isCircularWayDefaultDirectionClockwise(settings.getCountryName())) {
            linkSegmentTypes = Pair.create(linkSegmentTypes.first(), null);
          }else {
            linkSegmentTypes = Pair.create(null, linkSegmentTypes.second());
          }
        }
        
        /* a macroscopic link segment is directional and can have a shape, it also has model information */
        extractMacroscopicLinkSegments(osmWay, tags, link, linkSegmentTypes);
      }                          
    }    
    return link;
  }

}

package org.planit.osm.physical.network.macroscopic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.planit.geo.PlanitJtsUtils;
import org.planit.network.macroscopic.physical.MacroscopicModePropertiesFactory;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.tags.OsmAccessTags;
import org.planit.osm.tags.OsmBicycleTags;
import org.planit.osm.tags.OsmBusWayTags;
import org.planit.osm.tags.OsmDirectionTags;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmJunctionTags;
import org.planit.osm.tags.OsmLaneTags;
import org.planit.osm.tags.OsmOneWayTags;
import org.planit.osm.tags.OsmPedestrianTags;
import org.planit.osm.tags.OsmRailFeatureTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.osm.tags.OsmSpeedTags;
import org.planit.osm.tags.OsmTags;
import org.planit.osm.util.ModifiedLinkSegmentTypes;
import org.planit.osm.util.OsmLanesModeTaggingSchemeHelper;
import org.planit.osm.util.OsmModeLanesTaggingSchemeHelper;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.utils.arrays.ArrayUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.Edge;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
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
 * as relevant to this layer by the {@link PlanitOsmNetworkHandler}
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkLayerHandler {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkLayerHandler.class.getCanonicalName());
  
  // local members only
  
  /** utility class for profiling this layer for logging purposes */
  private final PlanitOsmNetworkHandlerProfiler profiler;
  
  /** helper class to deal with parsing tags under the lanesMode tagging scheme for eligible modes */
  private OsmLanesModeTaggingSchemeHelper lanesModeSchemeHelper = null;
  
  /** helper class to deal with parsing tags under the modeLanes tagging scheme for eligible modes */
  private OsmModeLanesTaggingSchemeHelper modeLanesSchemeHelper = null;  
      
  /**
   * track the PLANit nodes created on this layer by their original OSM id  so they can by looked up quickly while parsing ways
   */
  private final Map<Long, Node> nodesByOsmId = new HashMap<Long, Node>();
  
  /** Mapping from Osm node id to the links they are internal to. When done parsing, we verify if any
   * entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existing link's extreme node is internal to any other link, the link where
   * this node is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link
   */
  private final Map<Long, List<Link>> linkInternalOsmNodes = new HashMap<Long, List<Link>>();
  
  /** track all modified link segment types compared to the original defaults used in OSM, for efficient updates of the PLANit link segment types while parsing */
  private final ModifiedLinkSegmentTypes modifiedLinkSegmentTypes = new ModifiedLinkSegmentTypes();  
  
  // references
  
  /** geo utility instance based on network wide crs this layer is part of */
  private final PlanitJtsUtils geoUtils;   
  
  /** reference to parsed OSM nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** settings relevant to this parser */
  private PlanitOsmNetworkSettings settings;
  
  /** the network layer to use */
  private MacroscopicPhysicalNetwork networkLayer;  
  
  /** Identify which links we should break that have the node to break at as one of its internal nodes
   * 
   * @param theNodeToBreakAt the node to break at
   * @param brokenLinksByOriginalOsmId earlier broken links
   * @return the link to break, null if none could be found
   * @throws PlanItException thrown if error
   */
  private List<Link> findLinksToBreak(Node theNodeToBreakAt, Map<Long, Set<Link>> brokenLinksByOriginalOsmId) throws PlanItException {
    List<Link> linksToBreak = null;
    Long nodeToBreakAtOsmId = Long.parseLong(theNodeToBreakAt.getExternalId());
    if(theNodeToBreakAt != null && linkInternalOsmNodes.containsKey(nodeToBreakAtOsmId)) {
      
      /* find the links to break assuming no links have been broken yet */
      linksToBreak = linkInternalOsmNodes.get(nodeToBreakAtOsmId);
      
      /* find replacement links for the original link to break in case the original already has been broken and we should use 
       * one of the split off broken links instead of the original for the correct breaking for the given node (since it now resides on one of the broken
       * links rather than the original full link that no longer exists in that form */
      Set<Link> replacementLinks = new HashSet<Link>();
      Iterator<Link> linksToBreakIter = linksToBreak.iterator();
      while(linksToBreakIter.hasNext()) {
        Link orginalLinkToBreak = linksToBreakIter.next(); 
        
        Long osmOriginalId = Long.valueOf(orginalLinkToBreak.getExternalId());
        if(brokenLinksByOriginalOsmId.containsKey(osmOriginalId)) {
          
          /* link has been broken before, find out in which of its broken links the node to break at resides on */
          Set<Link> earlierBrokenLinks = brokenLinksByOriginalOsmId.get(osmOriginalId);
          Link matchingEarlierBrokenLink = null;
          for(Link link : earlierBrokenLinks) {
            Optional<Integer> coordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(theNodeToBreakAt.getPosition().getCoordinate(),link.getGeometry());
            if(coordinatePosition.isPresent()) {
              matchingEarlierBrokenLink = link;
            }
          }
          
          /* remove original and mark found link as replacement link to break */
          linksToBreakIter.remove();          
          
          /* verify if match is valid (which it should be) */
          if(matchingEarlierBrokenLink==null) {
            LOGGER.warning(String.format("unable to locate broken sublink of OSM way %s (id:%d), likely malformed way encountered, ignored",
                orginalLinkToBreak.getExternalId(), orginalLinkToBreak.getId()));            
          }else {
            replacementLinks.add(matchingEarlierBrokenLink);
          }          
        }
      }
      linksToBreak.addAll(replacementLinks);
    }
    return linksToBreak;
  }   
  
  /** Check if we should break any links for the passed in node and if so, do it
   * 
   * @param theNode to verify
   * @param brokenLinksByOriginalOsmId track all broken links that originated from one original link, tracked by its external OSM id
   * @return number of link broken for this node
   *  
   * @throws PlanItException thrown if error
   */
  private int breakLinksWithInternalNode(Node theNode, Map<Long, Set<Link>> brokenLinksByOriginalOsmId) throws PlanItException {
        
    /* find the link that we should break */
    List<Link> linksToBreak = findLinksToBreak(theNode, brokenLinksByOriginalOsmId);
    if(linksToBreak != null) {
      /* performing breaking of links, returns the broken links by the original link's PLANit edge id */
      Map<Long, Set<Link>> localBrokenLinks = networkLayer.breakLinksAt(linksToBreak, theNode, geoUtils.getCoordinateReferenceSystem());           
      
      /* add newly broken links to the mapping from original external OSM link id, to the broken link that together form this entire original OSMway*/
      if(localBrokenLinks != null) {
        localBrokenLinks.forEach((id, links) -> {
          links.forEach( brokenLink -> {
            Long brokenLinkOsmId = Long.parseLong(brokenLink.getExternalId());
            brokenLinksByOriginalOsmId.putIfAbsent(brokenLinkOsmId, new HashSet<Link>());
            brokenLinksByOriginalOsmId.get(brokenLinkOsmId).add(brokenLink);
          });
        });        
      }  
    } 
    
    return linksToBreak==null ? 0 : linksToBreak.size();
  }  
  
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
  
  /** All modes that are explicitly made (un)available in a particular direction (without any further details are identified via this method, e.g. bus:forward=yes
   * @param tags to verify
   * @param isForwardDirection forward when true, backward otherwise
   * @param included when true included modes are identified, otherwise excluded modes
   * @return the mapped PLANitModes found
   */
  private Collection<? extends Mode> getModesForDirection(Map<String, String> tags, boolean isForwardDirection, boolean included) {
    String osmDirectionCondition= isForwardDirection ? OsmDirectionTags.FORWARD : OsmDirectionTags.BACKWARD;
    String[] accessValueTags = included ?  OsmAccessTags.getPositiveAccessValueTags() : OsmAccessTags.getNegativeAccessValueTags();
    /* found modes with given access value tags in explored direction */
    Set<Mode> foundModes = settings.collectMappedPlanitModes(PlanitOsmUtils.getPostfixedOsmModesWithAccessValue(osmDirectionCondition, tags, accessValueTags));
    return foundModes;
  }    
  
  /** equivalent to {@link getModesForDirection(tags, isForwardDirection, true) */
  private Collection<? extends Mode> getExplicitlyIncludedModesForDirection(Map<String, String> tags, boolean isForwardDirection) {
    return getModesForDirection(tags, isForwardDirection, true /*included*/);
  }
  
  /** equivalent to {@link getModesForDirection(tags, isForwardDirection, false) */
  private Collection<? extends Mode> getExplicitlyExcludedModesForDirection(Map<String, String> tags, boolean isForwardDirection) {
    return getModesForDirection(tags, isForwardDirection, false /*excluded*/);
  }   
  
  /** Whenever a mode is tagged as a /<mode/>:oneway=no it implies it is available in both directions. This is what is checked here. Typically used in conjunction with a oneway=yes
   * tag but not necessarily
   * 
   * @param tags to verify
   * @return the mapped PLANitModes found
   */
  private Set<Mode> getExplicitlyIncludedModesNonOneWay(Map<String, String> tags) {
    return settings.collectMappedPlanitModes(PlanitOsmUtils.getPrefixedOsmModesWithAccessValue(OsmOneWayTags.ONEWAY, tags, OsmOneWayTags.NO));
  }          

  /** Collect explicitly included modes for a bi-directional OSMway, i.e., so specifically NOT a one way. 
   * 
   * <ul>
   * <li>bicycle: when cycleway=/<positive access value/></li>
   * <li>bicycle: when cycleway:<driving_location>=/<positive access value/></li>
   * <li>bicycle: when cycleway:<driving_location>:oneway=/<negative access value/></li>
   * <li>bus: when busway:<driving_location>=/<positive access value/></li>
   * </ul>
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isDrivingDirectionLocationLeft  flag indicating if driving location that is explored corresponds to the left hand side of the way
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Set<Mode> getExplicitlyIncludedModesTwoWayForLocation(Map<String, String> tags, boolean isDrivingDirectionLocationLeft) {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {
      /*... bicycles --> include when inclusion indicating tag is present for correct location [explored direction]*/
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE)) {                  
          if(OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY)) {
            /* both directions implicit*/
            includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
          }else if(OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, isDrivingDirectionLocationLeft ? OsmBicycleTags.CYCLEWAY_LEFT : OsmBicycleTags.CYCLEWAY_RIGHT)) {
            /* cycleway scheme, location based (single) direction, see also https://wiki.openstreetmap.org/wiki/Bicycle example T4 */
            includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
          }else if(OsmBicycleTags.isNoOneWayCyclewayInAnyLocation(tags)) {
            /* location is explicitly in both directions (non-oneway on either left or right hand side, see also https://wiki.openstreetmap.org/wiki/Bicycle example T2 */
            includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
          }
      }      
            
      /*... buses --> include when inclusion indicating tag is present for correct location [explored direction], see https://wiki.openstreetmap.org/wiki/Bus_lanes */
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BUS) && OsmLaneTags.isLaneIncludedForAnyOf(tags, isDrivingDirectionLocationLeft ? OsmBusWayTags.BUSWAY_LEFT : OsmBusWayTags.BUSWAY_LEFT)) {
        includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BUS)); 
      }       
      
    }      
 
    return includedModes;
  }
  
  /** Collect explicitly excluded modes for a bi-directional OSMway, i.e., so specifically NOT a one way. 
   * 
   * <ul>
   * <li>bicycle: when cycleway:<driving_location>=/<negative access value/></li>
   * </ul>
   * 
   * @param tags to find explicitly excluded (planit) modes from
   * @param isDrivingDirectionLocationLeft  flag indicating if driving location that is explored corresponds to the left hand side of the way
   * @return the excluded planit modes supported by the parser in the designated direction
   */    
  private Collection<? extends Mode> getExplicitlyExcludedModesTwoWayForLocation(Map<String, String> tags, boolean isDrivingDirectionLocationLeft) {
    Set<Mode> excludedModes = new HashSet<Mode>();
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes are only included with tags when at least one lane is available, so not exclusions can be gathered from them*/      
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {
      /*... bicycles --> exclude when explicitly excluded in location of explored driving direction*/
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayExcludedForAnyOf(tags, isDrivingDirectionLocationLeft ? OsmBicycleTags.CYCLEWAY_LEFT : OsmBicycleTags.CYCLEWAY_RIGHT)) {                  
        excludedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
      }      
            
      /*... for buses --> left and/or right non-presence explicit exclusions hardly exist, so not worthwhile checking for */      
    } 
    return excludedModes; 
  }  

  /** Collect explicitly included modes from the passed in tags but only for the oneway main direction.
   * 
   * A mode is considered explicitly included when
   * <ul>
   * <li>lanes:/<mode/>=*</li>
   * <li>/<mode/>:lanes=*</li>
   * <li>bicycle: when cycleway:\<any_location\>= see positive cycleway access values </li>
   * <li>bus: when busway:\<any_location\>=lane is present</li>
   * </ul>
   * 
   * cycleway positive access values=
   * <ul>
   * <li>lane</li>
   * <li>shared_lane</li>
   * <li>share_busway</li>
   * <li>share_busway</li>
   * <li>shoulder</li>
   * <li>track</li>
   * </ul>
   * </p>
   * <p>
   * 
   * @param tags to find explicitly included (planit) modes from
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Collection<? extends Mode> getExplicitlyIncludedModesOneWayMainDirection(Map<String, String> tags) {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes */
    {
      /* see example of both lanes:mode and mode:lanes schemes specific for bus on https://wiki.openstreetmap.org/wiki/Bus_lanes, but works the same way for other modes */
      if(lanesModeSchemeHelper!=null && lanesModeSchemeHelper.hasEligibleModes()) {
        /* lanes:<mode>=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        lanesModeSchemeHelper.getModesWithLanesWithoutDirection(tags).forEach(osmMode -> includedModes.add(settings.getMappedPlanitMode(osmMode)));
      }else if(modeLanesSchemeHelper!=null && modeLanesSchemeHelper.hasEligibleModes()) {
        /* <mode>:lanes=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        modeLanesSchemeHelper.getModesWithLanesWithoutDirection(tags).forEach(osmMode -> includedModes.add(settings.getMappedPlanitMode(osmMode)));
      }          
    }        
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {      
      /* RIGHT and LEFT on a oneway street DO NOT imply direction (unless possibly in the value but not via the key)
      
      /*... bicycles explore location specific (left, right) presence [main direction]*/
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY_LEFT, OsmBicycleTags.CYCLEWAY_RIGHT)) {
        includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
      }          
      
      /*... for buses adopting the busway scheme approach [main direction] */   
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BUS) && OsmLaneTags.isLaneIncludedForAnyOf(tags, OsmBusWayTags.BUSWAY_LEFT, OsmBusWayTags.BUSWAY_RIGHT)) {
          includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BUS));
      }            
    }  
    
    return includedModes;
  }
  
  /** Collect explicitly excluded modes from the passed in tags but only for the oneway main direction.
   * 
   * A mode is considered explicitly excluded when
   * <ul>
   * <li>bicycle: when cycleway:<any_location>=/<negative_access_values/>see positive cycleway access values </li>
   * </ul>
   * 
   * @param tags to find explicitly excluded (planit) modes from
   * @return the excluded planit modes supported by the parser in the designated direction
   */    
  private Set<Mode> getExplicitlyExcludedModesOneWayMainDirection(Map<String, String> tags) {
    Set<Mode> excludedModes = new HashSet<Mode>();  
    
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes are only included with tags when at least one lane is available, so not exclusions can be gathered from them*/                
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {
      /* alternatively the busway or cycleway scheme can be used, which has to be checked separately by mode since it is less generic */
      
      /*... bicycles --> left or right non-presence [explored direction] */
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayExcludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY_LEFT, OsmBicycleTags.CYCLEWAY_RIGHT)) {
        excludedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
      }          
      
      /*... for buses --> left and/or right non-presence explicit exclusions hardly exist, so not worthwhile checking for */              
    } 
    return excludedModes;
  }  

  /** Collect explicitly included modes from the passed in tags but only for modes explicitly included in the opposite direction of a one way OSM way
   * 
   * <ul>
   * <li>bicycle: when cycleway=opposite_X is present</li>
   * <li>bus: when busway=opposite_lane is present</li>
   * </ul>
   *  
   * @param tags to find explicitly included (planit) modes from
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Set<Mode> getExplicitlyIncludedModesOneWayOppositeDirection(Map<String, String> tags) {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY is the only scheme with opposite direction specific tags for oneway OSM ways*/

    /*... bicycle location [opposite direction], as per https://wiki.openstreetmap.org/wiki/Key:cycleway cycleway:left=opposite or cycleway:right=opposite are not valid*/
    if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isOppositeCyclewayIncludedForAnyOf(tags, OsmBicycleTags.getCycleWayKeyTags(false /* no left right*/ ))) {
      includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));
    }
    
    /*... buses for busway scheme [opposite direction], see https://wiki.openstreetmap.org/wiki/Bus_lanes */
    if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BUS) && OsmLaneTags.isOppositeLaneIncludedForAnyOf(tags, OsmBusWayTags.getBuswaySchemeKeyTags())) {
      includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BUS));  
    }          
    
    return includedModes;
  }
  
  /** Collect explicitly excluded modes from the passed in tags but only for modes explicitly excluded in the opposite direction of a one way OSM way. In this case
   * These are always and only:
   * 
   * <ul>
   * <li>all vehicular modes</li>
   * </ul>
   *  
   * @param tags to find explicitly included (planit) modes from
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Collection<? extends Mode> getExplicitlyExcludedModesOneWayOppositeDirection() {
    /* vehicular modes are excluded, equates to vehicle:<direction>=no see https://wiki.openstreetmap.org/wiki/Key:oneway */
    return settings.collectMappedPlanitModes(OsmRoadModeCategoryTags.getRoadModesByCategory(OsmRoadModeCategoryTags.VEHICLE));
  }     

  /** Collect explicitly included modes from the passed in tags but only for tags that have the same meaning regarldess if the way is tagged as one way or not
   * 
   * 
   * Modes are included when tagged 
   * <ul>
   * <li>/<mode/>:oneway=no</li>
   * <li>/<mode/>:<explored_direction>=yes</li>
   * <li>lanes:/<mode/>:/<explored_direction/>=*</li>
   * <li>/<mode/>:lanes:/<explored_direction/>=*</li>
   * <li>cycleway=both if bicycles</li>
   * <li>footway is present if pedestrian</li>
   * <li>sidewalk is present if pedestrian</li>
   * </ul>
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @return the included planit modes supported by the parser in the designated direction
   */
  private Set<Mode> getExplicitlyIncludedModesOneWayAgnostic(Map<String, String> tags, boolean isForwardDirection)   
  {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* ...all modes --> tagged with oneway:<mode>=no signify access BOTH directions and should be included. Regarded as generic because can be used on non-oneway streets */
    includedModes.addAll(getExplicitlyIncludedModesNonOneWay(tags));
    
    /* ...all modes --> inclusions in explicit directions matching our explored direction, e.g. bicycle:forward=yes based*/
    includedModes.addAll(getExplicitlyIncludedModesForDirection(tags, isForwardDirection));
    
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes */
    {
      /* see example of both lanes:mode and mode:lanes schemes specific for bus on https://wiki.openstreetmap.org/wiki/Bus_lanes, but works the same way for other modes */
      if(lanesModeSchemeHelper!=null && lanesModeSchemeHelper.hasEligibleModes()) {
        /* lanes:<mode>:<direction>=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        lanesModeSchemeHelper.getModesWithLanesInDirection(tags, isForwardDirection).forEach(osmMode -> includedModes.add(settings.getMappedPlanitMode(osmMode)));
      }else if(modeLanesSchemeHelper!=null && modeLanesSchemeHelper.hasEligibleModes()) {
        /* <mode>:lanes:<direction>=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        modeLanesSchemeHelper.getModesWithLanesInDirection(tags, isForwardDirection).forEach(osmMode -> includedModes.add(settings.getMappedPlanitMode(osmMode)));
      }
    }
    
    /*...bicycle --> explicitly tagged cycleway being available in both directions */
    if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY_BOTH)) {
        includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));      
    }
      
    /* ...pedestrian modes can also be added by means of defined sidewalks, footways, etc. Note that pedestrians can always move in both direction is any infrastructure is present */
    if(settings.hasMappedPlanitMode(OsmRoadModeTags.FOOT) && OsmPedestrianTags.hasExplicitlyIncludedSidewalkOrFootway(tags)) {
      includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.FOOT));
    }
    
    return includedModes;
  }  
  
  /** Collect explicitly excluded modes from the passed in tags but only for tags that have the same meaning regardless if the way is tagged as one way or not
   * 
   * Modes are excluded when tagged 
   * <ul>
   * <li>/<mode/>=/<negative_access_value/></li>
   * <li>/<mode/>:/<explored_direction/>==/<negative_access_value/></li>
   * <li>cycleway or cycleway:both=/<negative_access_value/> if bicycles</li>
   * <li>footway is excluded if pedestrian</li>
   * <li>sidewalk is excluded if pedestrian</li>
   * </ul>
   * 
   * @param tags to find explicitly excluded (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @param settings to access mapping from osm mdoes to planit modes
   * @return the excluded planit modes supported by the parser in the designated direction
   */
  private Set<Mode> getExplicitlyExcludedModesOneWayAgnostic(Map<String, String> tags, boolean isForwardDirection, PlanitOsmNetworkSettings settings){
    Set<Mode> excludedModes = new HashSet<Mode>();
    
    /* ... roundabout is implicitly one way without being tagged as such, all modes in non-main direction are to be excluded */
    if(OsmJunctionTags.isPartOfCircularWayJunction(tags) && PlanitOsmUtils.isCircularWayDirectionClosed(tags, isForwardDirection, settings.getCountryName())) {
      excludedModes.addAll(networkLayer.getSupportedModes());
    }else {

      /* ... all modes --> general exclusion of modes */
      excludedModes =  settings.collectMappedPlanitModes(PlanitOsmUtils.getOsmModesWithAccessValue(tags, OsmAccessTags.getNegativeAccessValueTags()));      
      
      /* ...all modes --> exclusions in explicit directions matching our explored direction, e.g. bicycle:forward=no, FORWARD/BACKWARD/BOTH based*/
      excludedModes.addAll(getExplicitlyExcludedModesForDirection(tags, isForwardDirection));
      
      /*... busways are generaly not explicitly excluded when keys are present, hence no specific check */
      
      /*...bicycle --> cycleways explicitly tagged being not available at all*/
      if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayExcludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY,OsmBicycleTags.CYCLEWAY_BOTH)) {
        excludedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE));      
      }
        
      /* ...pedestrian modes can also be excluded by means of excluded sidewalks, footways, etc. Note that pedestrians can always move in both direction is any infrastructure is present */
      if(settings.hasMappedPlanitMode(OsmRoadModeTags.FOOT) && OsmPedestrianTags.hasExplicitlyExcludedSidewalkOrFootway(tags)) {
        excludedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.FOOT));
      }  
      
    }

    return excludedModes;
  }    
 

  /** Collect explicitly included modes from the passed in tags. Given the many ways this can be tagged we distinguish between:
   * 
   * <ul>
   * <li>mode inclusions agnostic to the way being tagged as oneway or not {@link getExplicitlyIncludedModesOneWayAgnostic}</li>
   * <li>mode inclusions when way is tagged as oneway and exploring the one way direction {@link getExplicitlyIncludedModesOneWayMainDirection}</li>
   * <li>mode inclusions when way is tagged as oneway and exploring the opposite direction of oneway direction {@link getExplicitlyIncludedModesOneWayOppositeDirection}</li>
   * <li>mode inclusions when we are NOT exploring the opposite direction of a oneway when present, e.g., either main direction of one way, or non-oneway {@link getExplicitlyIncludedModesTwoWayForLocation}</li>
   * </ul>
   * 
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @param settings to access mapping from osm mdoes to planit modes
   * @return the included planit modes supported by the parser in the designated direction
   */
  private Set<Mode> getExplicitlyIncludedModes(Map<String, String> tags, boolean isForwardDirection, PlanitOsmNetworkSettings settings) {          
    Set<Mode> includedModes = new HashSet<Mode>();     
    
    /* 1) generic mode inclusions INDEPENDNT of ONEWAY tags being present or not*/
    includedModes.addAll(getExplicitlyIncludedModesOneWayAgnostic(tags, isForwardDirection));
                          
    boolean exploreOneWayOppositeDirection = false;
    if(tags.containsKey(OsmOneWayTags.ONEWAY)){      
      /* mode inclusions for ONE WAY tagged way (RIGHT and LEFT on a oneway street DO NOT imply direction)*/
      
      final String oneWayValueTag = tags.get(OsmOneWayTags.ONEWAY);
      String osmDirectionValue = isForwardDirection ? OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION : OsmOneWayTags.YES;
  
      /* 2a) mode inclusions for ONE WAY OPPOSITE DIRECTION if explored */
      if(oneWayValueTag.equals(osmDirectionValue)) {        
        exploreOneWayOppositeDirection = true;        
        includedModes.addAll(getExplicitlyIncludedModesOneWayOppositeDirection(tags));                                                
      }
      /* 2b) mode inclusions for ONE WAY MAIN DIRECTION if explored*/
      else if(PlanitOsmUtils.matchesAnyValueTag(oneWayValueTag, OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION, OsmOneWayTags.YES)) {        
        includedModes.addAll(getExplicitlyIncludedModesOneWayMainDirection(tags));                                              
      }            
    }else {
      /* 2c) mode inclusions for BIDIRECTIONAL WAY in explored direction: RIGHT and LEFT now DO IMPLY DIRECTION, unless indicated otherwise, see https://wiki.openstreetmap.org/wiki/Bicycle */
      
      /* country settings matter : left hand drive -> left = forward direction, right hand drive -> left is opposite direction */
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(settings.getCountryName());
      boolean isDrivingDirectionLocationLeft = (isForwardDirection && isLeftHandDrive) ? true : false; 
      
      /* location means left or right hand side of the road specific tags pertaining to mode inclusions */
      includedModes.addAll(getExplicitlyIncludedModesTwoWayForLocation(tags, isDrivingDirectionLocationLeft));            
    }
    
    /* 3) mode inclusions for explored direction that is NOT ONE WAY OPPOSITE DIRECTION */
    if(!exploreOneWayOppositeDirection) {      
      /* ...all modes --> general inclusions in main or both directions <mode>= */
      includedModes.addAll(settings.collectMappedPlanitModes(PlanitOsmUtils.getOsmModesWithAccessValue(tags, OsmAccessTags.getPositiveAccessValueTags())));          
    }
           
    return includedModes;                  
  }
  
  /** Collect explicitly excluded modes from the passed in tags. Given the many ways this can be tagged we distinguish between:
   * 
   * <ul>
   * <li>mode exclusions agnostic to the way being tagged as oneway or not {@link getExplicitlyExcludedModesOneWayAgnostic}</li>
   * <li>mode exclusions when way is tagged as oneway and exploring the one way direction {@link getExplicitlyExcludedModesOneWayMainDirection}</li>
   * <li>mode exclusions when way is tagged as oneway and exploring the opposite direction of oneway direction {@link getExplicitlyExcludedModesOneWayOppositeDirection}</li>
   * </ul>
   * 
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @param settings required for access to mode mapping between osm modes and PLANit modes
   * @return the included planit modes supported by the parser in the designated direction
   */
  private Set<Mode> getExplicitlyExcludedModes(final Map<String, String> tags, final boolean isForwardDirection, final PlanitOsmNetworkSettings settings) {    
    Set<Mode> excludedModes = new HashSet<Mode>();       
    
    /* 1) generic mode exclusions INDEPENDNT of ONEWAY tags being present or not*/
    excludedModes.addAll(getExplicitlyExcludedModesOneWayAgnostic(tags, isForwardDirection, settings));               
    
    if(tags.containsKey(OsmOneWayTags.ONEWAY)){
      /* mode exclusions for ONE WAY tagged way (RIGHT and LEFT on a oneway street DO NOT imply direction)*/     
      
      final String oneWayValueTag = tags.get(OsmOneWayTags.ONEWAY);
      String osmDirectionValue = isForwardDirection ? OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION : OsmOneWayTags.YES;

      /* 2a) mode exclusions for ONE WAY OPPOSITE DIRECTION if explored */
      if(oneWayValueTag.equals(osmDirectionValue)) {        
        excludedModes.addAll(getExplicitlyExcludedModesOneWayOppositeDirection());                       
      }    
      /* 2b) mode inclusions for ONE WAY MAIN DIRECTION if explored*/
      else if(PlanitOsmUtils.matchesAnyValueTag(oneWayValueTag, OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION, OsmOneWayTags.YES)) {
        excludedModes.addAll(getExplicitlyExcludedModesOneWayMainDirection(tags));                                             
      }
    }else {
      /* 2c) mode inclusions for BIDIRECTIONAL WAY in explored direction: RIGHT and LEFT now DO IMPLY DIRECTION, unless indicated otherwise, see https://wiki.openstreetmap.org/wiki/Bicycle */
      
      /* country settings matter : left hand drive -> left = forward direction, right hand drive -> left is opposite direction */
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(settings.getCountryName());
      boolean isDrivingDirectionLocationLeft = (isForwardDirection && isLeftHandDrive) ? true : false;
      
      /* location means left or right hand side of the road specific tags pertaining to mode inclusions */
      excludedModes.addAll(getExplicitlyExcludedModesTwoWayForLocation(tags, isDrivingDirectionLocationLeft));                      
    }      
    
    return excludedModes;
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
    Set<Mode> excludedModes = getExplicitlyExcludedModes(tags, forwardDirection, settings);
    Set<Mode> includedModes = getExplicitlyIncludedModes(tags, forwardDirection, settings);
        
    
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
        finalLinkSegmentType = networkLayer.linkSegmentTypes.registerUniqueCopyOf(linkSegmentType);
        
        /* XML id */
        finalLinkSegmentType.setXmlId(Long.toString(finalLinkSegmentType.getId()));
        
        /* update mode properties */
        if(!toBeAddedModes.isEmpty()) {
          double osmWayTypeMaxSpeed = settings.getDefaultSpeedLimitByOsmWayType(tags);          
          MacroscopicModePropertiesFactory.createOnLinkSegmentType(finalLinkSegmentType, toBeAddedModes, osmWayTypeMaxSpeed);
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
    }else if(tags.containsKey(OsmRailwayTags.RAILWAY)) {
      osmWayKey = OsmRailwayTags.RAILWAY;
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
  protected Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> extractLinkSegmentTypeByOsmAccessTags(final OsmWay osmWay, final Map<String, String> tags, final MacroscopicLinkSegmentType linkSegmentType) throws PlanItException {
    
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
  
  /**
   * provide access to the profiler for this layer
   * 
   * @return profiler of this layer
   */
  protected PlanitOsmNetworkHandlerProfiler getProfiler() {
    return this.profiler;
  }

  /** Constructor
   * @param networkLayer to use
   * @param osmNodes reference to parsed osmNodes
   * @param settings used for this parser
   * @param geoUtils geometric utility class instance based on network wide crs
   */
  protected PlanitOsmNetworkLayerHandler(MacroscopicPhysicalNetwork networkLayer, Map<Long, OsmNode> osmNodes, PlanitOsmNetworkSettings settings, PlanitJtsUtils geoUtils) {
    this.networkLayer = networkLayer;
    this.osmNodes = osmNodes;
    this.geoUtils = geoUtils;
    this.settings = settings;
    
    this.profiler  = new PlanitOsmNetworkHandlerProfiler();
    
    /* initialise the tagging scheme helpers based on the registered modes */
    if(OsmLanesModeTaggingSchemeHelper.requireLanesModeSchemeHelper(settings, networkLayer)) {
      lanesModeSchemeHelper = new OsmLanesModeTaggingSchemeHelper(OsmLanesModeTaggingSchemeHelper.getEligibleLanesModeSchemeHelperModes(settings, networkLayer));
    }
    if(OsmModeLanesTaggingSchemeHelper.requireLanesModeSchemeHelper(settings, networkLayer)) {
      modeLanesSchemeHelper = new OsmModeLanesTaggingSchemeHelper(OsmModeLanesTaggingSchemeHelper.getEligibleModeLanesSchemeHelperModes(settings, networkLayer));
    }    
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
  
  /**
   * whenever we find that internal nodes are used by more than one link OR a node is an extreme node
   * on an existing link but also an internal link on another node, we break the links where this node
   * is internal. the end result is a situations where all nodes used by more than one link are extreme 
   * nodes, i.e., start/end nodes.
   * <p>
   * One can pass in already broken links on another occasion to make sure the correct PLANit link is selected to be broken further in case it is
   * found that the OSM way needs to be broken again (in which case the original OSM id does not suffice to find the related link
   * 
   * @param brokenLinksByOriginalOsmLinkId all already broken links by original OSM id, yet multiple PLANit links exist for it (possibly in different layers)
   */
  protected void breakLinksWithInternalConnections(final Map<Long, Set<Link>> brokenLinksByOriginalOsmLinkId) {
    
    LOGGER.info("Breaking OSM ways with internal connections into multiple links ...");        
    
    try {
          
      long nodeIndex = -1;
      long originalNumberOfNodes = networkLayer.nodes.size();
            
      HashSet<Long> processedNodes = new HashSet<Long>();
      while(++nodeIndex<originalNumberOfNodes) {
        Node node = networkLayer.nodes.get(nodeIndex);    
                
        // 1. break links when a link's internal node is another existing link's extreme node
        //NOTE: 9/2/2021 changed this from using each links a and b node and calling below twice, to looping over nodes and 
        // calling it only once, this should be better (more elegant avoiding duplicate code running, but not fully tested
        // REMOVE NOTE WHEN FOUND CORRECT OR REVERT BACK
        breakLinksWithInternalNode(node, brokenLinksByOriginalOsmLinkId);
        linkInternalOsmNodes.remove(Long.parseLong(node.getExternalId()));        
      }
      
      //2. break links where an internal node of multiple links is shared, but it is never an extreme node of a link
      for(Entry<Long, List<Link>> entry : linkInternalOsmNodes.entrySet()) {        
        /* only intersection of links when at least two links are registered */
        if(entry.getValue().size() > 1 && !processedNodes.contains(entry.getKey())) {
          /* node does not yet exist in PLANit network because it was internal node so far, so create it first */
          Node planitIntersectionNode = extractNode(entry.getKey());
          breakLinksWithInternalNode(planitIntersectionNode, brokenLinksByOriginalOsmLinkId);
          linkInternalOsmNodes.remove(entry.getKey());
        }
      }
      
      LOGGER.info(String.format("Broke %d OSM ways into multiple links...DONE",brokenLinksByOriginalOsmLinkId.size()));      
    
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to break OSM links with internal intersections");
    }          
    
  }   
  
  /** verify if OSM node id is converted to a PLANit node or is part of a PLANit link's internal geometry
   * 
   * @param osmNodeId to check
   * @return true when part of a geometry in the layer, false otherwise
   */
  public boolean isOsmNodePresentInLayer(long osmNodeId) {
    return (nodesByOsmId.containsKey(osmNodeId) || linkInternalOsmNodes.containsKey(osmNodeId));
  }

  /**
   * log progile information gathered during parsing (so far)
   */
  public void logProfileInformation() {
    this.profiler.logProfileInformation(networkLayer);;
  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    nodesByOsmId.clear();
    linkInternalOsmNodes.clear();
    modifiedLinkSegmentTypes.reset();
  }

  /** provide the map that indexed all created nodes in this layer by its OSM id
   * 
   * @return nodes by OSM id
   */
  public final Map<Long, Node> getParsedNodesByOsmId() {
    return nodesByOsmId;
  }
  
  /** collect all osm nodes that are internal to a parsed Planit Link
   * @return osm node ids that are internal to a parsed link
   */
  public final Map<Long, List<Link>> getOsmNodesInternalToLink(){
    return this.linkInternalOsmNodes;
  }

}

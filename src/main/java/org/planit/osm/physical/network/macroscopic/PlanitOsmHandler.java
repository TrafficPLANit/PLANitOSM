package org.planit.osm.physical.network.macroscopic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.settings.PlanitOsmSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;

import org.planit.geo.PlanitJtsUtils;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

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
      
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** track layer specific information and handler to delegate processing the parts of osm ways assigned to a layer */
  private final Map<MacroscopicPhysicalNetwork, PlanitOsmNetworkLayerHandler> osmLayerHandlers = new HashMap<MacroscopicPhysicalNetwork, PlanitOsmNetworkLayerHandler>();
    
  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays;     
           
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
      Map<Long, Set<Link>> localBrokenLinks = this.network.getDefaultNetworkLayer().breakLinksAt(linksToBreak, theNode, network.getCoordinateReferenceSystem());           
      
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
  
    
  
                    
    
    
  /** verify if tags represent an highway or railway that is NOT an area and is activated based on the settings
   * @param tags to verify
   * @return true when activated and highway or railway (not an area), false otherwise
   */
  private boolean isActivatedHighwayOrRailway(Map<String, String> tags) {
    
    if(!OsmTags.isArea(tags) && (OsmHighwayTags.hasHighwayKeyTag(tags) || OsmRailWayTags.hasRailwayKeyTag(tags))) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
        return settings.getHighwaySettings().isOsmHighwayTypeActivated(tags.get(OsmHighwayTags.HIGHWAY));
      }else if(OsmRailWayTags.hasRailwayKeyTag(tags)) {
        return settings.getRailwaySettings().isOsmRailwayTypeActivated(tags.get(OsmRailWayTags.RAILWAY));
      }
    }
    return false;
  }  

  
  /**
   * now parse the remaining circular osmWays, which by default are converted into multiple links/linksegments for each part of
   * the circular way in between connecting in and outgoing links/linksegments that were parsed during the regular parsing phase
   * 
   * @param circularOsmWay the circular osm way to parse 
   * @return set of created links for this circular way if any, null if no links are created
   * @throws PlanItException thrown if error
   */
  protected Set<Link> handleRawCircularWay(final OsmWay circularOsmWay) throws PlanItException {
        
    Set<Link> createdLinks = null;    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(circularOsmWay);
    if(isActivatedHighwayOrRailway(tags)) {
      createdLinks = handleRawCircularWay(circularOsmWay, tags, 0 /* start at initial index */);
    }
    return createdLinks;
  }  
  
  /** Recursive method that processes osm ways that have at least one circular section in it, but this might not be perfect, i.e., the final node might
   * not connect to the initial node. to deal with this, we first identify the non-circular section(s), extract separate links for them, and then process
   * the remaining (perfectly) circular component of the OSM way via {@code handlePerfectCircularWay}
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param initialNodeIndex offset for starting point, part of the recursion
   * @param finalNodeIndex offset of the final point, part of the recursion
   * @return set of created links for this circular way if any, empty set if none
   * @throws PlanItException thrown if error
   */
  private Set<Link> handleRawCircularWay(final OsmWay circularOsmWay, final Map<String, String> osmWayTags, int initialNodeIndex) throws PlanItException {
    Set<Link> createdLinks = new HashSet<>();  
    int finalNodeIndex = (circularOsmWay.getNumberOfNodes()-1);
        
    /* when circular road is not perfect, i.e., its end node is not the start node, we first split it
     * in a perfect circle and a regular non-circular osmWay */
    Pair<Integer,Integer> firstCircularIndices = PlanitOsmUtils.findIndicesOfFirstLoop(circularOsmWay, initialNodeIndex);            
    if(firstCircularIndices != null) {    
      /* unprocessed circular section exists */

      if(firstCircularIndices.first() > initialNodeIndex ) {
        /* create separate link for the lead up part that is NOT circular */         
        Link createdLink = extractPartialOsmWay(circularOsmWay, osmWayTags, initialNodeIndex, firstCircularIndices.first(), false /* not a circular section */);
        if(createdLink != null) {
          createdLinks.add(createdLink);
        }
        /* update offsets for circular part */
        initialNodeIndex = firstCircularIndices.first();
      }
      
      /* continue with the remainder (if any) starting at the end point of the circular component 
       * this is done first because we want all non-circular components to be available as regular links before processing the circular parts*/
      if(firstCircularIndices.second() < finalNodeIndex) {
        Set<Link> theCreatedLinks = handleRawCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.second());
        createdLinks.addAll(theCreatedLinks);
      }      
        
      /* extract the identified perfectly circular component */
      Set<Link> theCreatedLinks = handlePerfectCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.first(), firstCircularIndices.second());
      createdLinks.addAll(theCreatedLinks);
      
    }else if(initialNodeIndex < finalNodeIndex) {
      /* last section is not circular, so extract partial link for it */
      Link createdLink = extractPartialOsmWay(circularOsmWay, osmWayTags, initialNodeIndex, finalNodeIndex, false /* not a circular section */);
      if(createdLink != null) {
        createdLinks.add(createdLink);
      }      
    }  
    
    return createdLinks;
  }

  /** Process a circular way that is assumed to be perfect for the given start and end node, i.e., its end node is the same as its start node
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param initialNodeIndex where the circular section starts
   * @param finalNodeIndex where the circular section ends (at the start)
   * @return set of created links for this circular way if any, empty set if none
   * @throws PlanItException thrown if error
   */
  private Set<Link> handlePerfectCircularWay(OsmWay circularOsmWay, Map<String, String> osmWayTags, int initialNodeIndex, int finalNodeIndex) throws PlanItException {

    Set<Link> createdLinks = new HashSet<>();
    int firstPartialLinkStartNodeIndex = -1;
    int partialLinkStartNodeIndex = -1;
    int partialLinkEndNodeIndex = -1;
    int numberOfConsideredNodes = finalNodeIndex-initialNodeIndex;
    boolean partialLinksPartOfCircularWay = true;
    
    /* construct partial links based on nodes on the geometry that are an extreme node of an already parsed link or are an internal node of an already parsed link */
    for(int index = initialNodeIndex ; index <= finalNodeIndex ; ++index) {
      long osmNodeId = circularOsmWay.getNodeId(index);
              
      if(nodesByOsmId.containsKey(osmNodeId) || linkInternalOsmNodes.containsKey(osmNodeId)) {                    
        
        if(partialLinkStartNodeIndex < 0) {
          /* set first node to earlier realised node */
          partialLinkStartNodeIndex = index;
          firstPartialLinkStartNodeIndex = partialLinkStartNodeIndex;
        }else if(!(index==finalNodeIndex && partialLinkStartNodeIndex==firstPartialLinkStartNodeIndex)) {            
          /* identified valid partial link (statement above makes sure that in case the one duplicate node (first=last) is chosen as partial link, we do not accept is as a partial link as it represents  the entire loop, otherwise
           * create link from start node to the intermediate node that attaches to an already existing planit link on the circular way */          
          partialLinkEndNodeIndex = index;
          Link createdLink = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
          if(createdLink != null) {
            createdLinks.add(createdLink);
          }
           
          /* update first node to last node of this link for next partial link */          partialLinkStartNodeIndex = partialLinkEndNodeIndex;                   
        }
      }
    }
    
    if(partialLinkStartNodeIndex < 0) {
      Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes = extractLinkSegmentTypes(circularOsmWay, osmWayTags);
      if(linkSegmentTypes!=null && linkSegmentTypes.anyIsNotNull()) {
        /* issue warning when circular way is of a viable type, i.e., it has mapped link segment type(s), but not a single connection to currently parsed network exists, this may indicate a problem */
        LOGGER.fine(String.format("circular way %d appears to have has no connections to activated OSM way types ", circularOsmWay.getId()));
        /* still we continue parsing it by simply creating a new planit nodes, marked by setting partialLinkStartNodeIndex to 0  and continue */ 
        partialLinkStartNodeIndex = 0;
      }
    }
    
    Link createdLink = null;
    if (partialLinkStartNodeIndex>= 0) {
      if (partialLinkEndNodeIndex < 0){        
        /* first partial link is not created either, only single connection point exists, so:
         * 1) when partialLinkStartNodeIndex = initial node -> take the halfway point as the dummy node, and the final node as the end point, if not then...
         * 2) reset partialLinkStartNodeIndex to initial node and the earlier found partialLinkStartNodeIndex as the midway point and then the final node as the end point */
        if(partialLinkStartNodeIndex == initialNodeIndex) {
          partialLinkEndNodeIndex = partialLinkStartNodeIndex + (numberOfConsideredNodes/2);  
        }else {
          partialLinkEndNodeIndex = partialLinkStartNodeIndex; 
          partialLinkStartNodeIndex = initialNodeIndex;
        }
        createdLink = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
        if(createdLink != null) {
          createdLinks.add(createdLink);
        }        
        partialLinkStartNodeIndex = partialLinkEndNodeIndex;
        partialLinkEndNodeIndex = finalNodeIndex;
        createdLink = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
      }else if(partialLinkEndNodeIndex != finalNodeIndex){            
        /* last partial link did not end at end of circular way but later, i.e., first partial link did not start at node zero.
         * finalise by creating the final partial link to the first partial links start node*/
        partialLinkEndNodeIndex = firstPartialLinkStartNodeIndex;       
        createdLink = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
      }    
      
      if(createdLink != null) {
        createdLinks.add(createdLink);
      }
    }
    return createdLinks;    
  }
   
  
  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param way the way
   * @param tags the tags of this way
   * @return the link segment types per layer if available, otherwise null is returned
   */
  protected Map<InfrastructureLayer, MacroscopicLinkSegmentType> getDefaultLinkSegmentTypeByOsmWayType(OsmWay osmWay, Map<String, String> tags) {
    String osmTypeKeyToUse = null;
    
    /* exclude ways that are areas and in fact not ways */
    boolean isExplicitArea = OsmTags.isArea(tags);
    boolean isHighway = true;
    if(isExplicitArea) {
      return null;
    }
      
    /* highway (road) or railway (rail) */
    if (OsmHighwayTags.hasHighwayKeyTag(tags)) {
      osmTypeKeyToUse = OsmHighwayTags.HIGHWAY;      
    }else if(OsmRailWayTags.hasRailwayKeyTag(tags)) {
      osmTypeKeyToUse = OsmRailWayTags.RAILWAY;
      isHighway = false;
    }
    
    /* without mapping no type */
    if(osmTypeKeyToUse==null) {
      return null;
    }
        
    String osmTypeValueToUse = tags.get(osmTypeKeyToUse);        
    Map<InfrastructureLayer,MacroscopicLinkSegmentType> linkSegmentTypes = network.getDefaultLinkSegmentTypeByOsmTag(osmTypeValueToUse);
    if(linkSegmentTypes != null) {
      linkSegmentTypes.forEach( (layer, linkSegmentType)  -> {
        if(linkSegmentType != null) {
          this.osmLayerHandlers.get(layer).getProfiler().incrementOsmTagCounter(osmTypeValueToUse);
        } });
    }
    /* determine if we should inform the user on not finding a mapped type, i.e., is this of concern or legitimate because we do not want or it cannot be mapped in the first place*/
    else {
      boolean isWayTypeDeactived = isHighway ?
          settings.getHighwaySettings().isOsmHighWayTypeDeactivated(osmTypeValueToUse) :settings.getRailwaySettings().isOsmRailwayTypeDeactivated(osmTypeValueToUse);
      if(!isWayTypeDeactived) {
        boolean typeConfigurationMissing = isHighway ? OsmHighwayTags.isNonRoadBasedHighwayValueTag(osmTypeValueToUse) : OsmRailWayTags.isNonRailBasedRailway(osmTypeValueToUse);         
        
        /*... not available event though it is not marked as deactivated AND it appears to be a type that can be converted into a link, so something is not properly configured*/
        if(typeConfigurationMissing) {            
          LOGGER.warning(String.format(
              "no link segment type available for OSM way: %s:%s (id:%d) --> ignored. Consider explicitly supporting or unsupporting this type", osmTypeKeyToUse, osmTypeValueToUse, osmWay.getId()));
        }
      }
      
    }
        
    return linkSegmentTypes;
  }  
  
  /** process all registered circular ways after parsing of basic nodes and ways is complete. Because circular ways are transformed into multiple
   * links, they in effect yield multiple links per original OSM way (id). In case such an OSMway is referenced later it no longer maps to a single 
   * PLANit link, hence we return how each OSMway is mapped to the set of links created for the circular way
   *  
   * @return map of created links per OSM way id 
   */
  protected  Map<Long, Set<Link>> processCircularWays() {
    
    LOGGER.info("Converting OSM circular ways into multiple link topologies...");
    
    /* process circular ways*/
    Map<Long, Set<Link>> createdLinksByOsmWayId = new HashMap<>();    
    for(Entry<Long,OsmWay> entry : osmCircularWays.entrySet()) {
      try {        
        Set<Link> createdLinks = handleRawCircularWay(entry.getValue());
        if(createdLinks!=null && !createdLinks.isEmpty()) {
          createdLinksByOsmWayId.putIfAbsent(entry.getKey(), new HashSet<Link>());
          createdLinksByOsmWayId.get(entry.getKey()).addAll(createdLinks);
        }
      }catch (PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("unable to process circular way OSM id: %d",entry.getKey()));
      }        
    }
    
    LOGGER.info(String.format("Processed %d circular ways...DONE",osmCircularWays.size()));
    return createdLinksByOsmWayId;
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
   * @param brokenLinksByOriginalOsmLinkId all already broken links that have the same original OSM id, yet multiple PLANit link s exist for it
   */
  protected void breakLinksWithInternalConnections(final Map<Long, Set<Link>> brokenLinksByOriginalOsmLinkId) {
    
    LOGGER.info("Breaking OSM ways with internal connections into multiple links ...");
    
    try {
          
      long linkIndex = -1;
      long originalNumberOfLinks = this.network.getDefaultNetworkLayer().links.size();
            
      while(++linkIndex<originalNumberOfLinks) {
        Link link = this.network.getDefaultNetworkLayer().links.get(linkIndex);    
        
        // 1. break links when a link's internal node is another existing link's extreme node 
        breakLinksWithInternalNode(link.getNodeA(), brokenLinksByOriginalOsmLinkId);
        long nodeAOsmId = Long.parseLong(link.getNodeA().getExternalId());
        linkInternalOsmNodes.remove(nodeAOsmId);
        
        /* apply to node B as well */
        breakLinksWithInternalNode(link.getNodeB(), brokenLinksByOriginalOsmLinkId);
        long nodeBOsmId = Long.parseLong(link.getNodeB().getExternalId());
        linkInternalOsmNodes.remove(nodeBOsmId);
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
      
      LOGGER.info(String.format("Broke %d OSM ways into multiple links...DONE",brokenLinksByOriginalOsmLinkId.size()));      
    
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to break OSM links with internal intersections");
    }          
    
    linkInternalOsmNodes.clear();
  }    
  
  /**
   * extract OSM way's PLANit infrastructure for the entire way, i.e., link, nodes, and link segments where applicable. 
   * The parser will try to infer missing/default data by using defaults set by the user
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @throws PlanItException thrown if error
   */
  protected void extractOsmWay(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    /* parse entire OSM way (0-endNodeIndex), and not part of a circular piece of infrastructure */
    extractPartialOsmWay(osmWay, tags, 0, osmWay.getNumberOfNodes()-1, false /*not part of circular infrastructure */);
    
  }

  /**
   * extract OSM way's PLANit infrastructure for the part of the way that is indicated. When it is marked as being a (partial) section of a circular way, then
   * we only allow the presumed one way direction applicable when creating directional link segments. The result is a newly registered link, its nodes, and linksegment(s) on
   * the network. The parser will try to infer missing/default data by using defaults set by the user.
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @return created link (if any), if no link could be created null is returned
   * @throws PlanItException thrown if error
   */  
  protected Link extractPartialOsmWay(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex, boolean isPartOfCircularWay) throws PlanItException {
    
    Link link = null;
    
    Map<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayer = extractLinkSegmentTypes(osmWay,tags);
    for(Entry<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> entry : linkSegmentTypesByLayer.entrySet()) {
      MacroscopicPhysicalNetwork networkLayer = entry.getKey();
      Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes = entry.getValue();
      
      PlanitOsmNetworkLayerHandler layerHandler = osmLayerHandlers.get(networkLayer);
      if(layerHandler == null) {
        throw new PlanItException("layer handler not available, should have been instantiated in PlanitOsmHandler constructor");
      }
      /* delegate to layer handler */
      link = layerHandler.extractPartialOsmWay(osmWay, tags, startNodeIndex, endNodeIndex, isPartOfCircularWay, linkSegmentTypes);       
    }    
    
    return link;
  }       

  /**
   * constructor
   * 
   * @param settings for the handler
   */
  public PlanitOsmHandler(final PlanitOsmNetwork network, final PlanitOsmSettings settings) {
    this.network = network;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsUtils(settings.getSourceCRS());
    try {
      this.network.transform(settings.getSourceCRS());
    }catch(PlanItException e) {
      LOGGER.severe(String.format("unable to update network to CRS %s", settings.getSourceCRS().getName()));
    }
    
    /* prep */
    this.settings = settings;   
    
    this.osmNodes = new HashMap<Long, OsmNode>();
    this.osmCircularWays = new HashMap<Long, OsmWay>();

  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(network.infrastructureLayers != null && network.infrastructureLayers.size()>0,"network is expected to be empty at start of parsing OSM network, but it has layers already");
    
    /* create the supported link segment types on the network */
    network.initialiseInfrastructureLayers(settings.getPlanitInfrastructureLayerConfiguration());
    /* for each layer initialise a handler */
    for(InfrastructureLayer networkLayer : network.infrastructureLayers) {
      MacroscopicPhysicalNetwork macroNetworkLayer = (MacroscopicPhysicalNetwork)networkLayer;
      PlanitOsmNetworkLayerHandler layerHandler = new PlanitOsmNetworkLayerHandler(macroNetworkLayer, osmNodes, settings, geoUtils);
      osmLayerHandlers.put(macroNetworkLayer, layerHandler);
    }
    
    
    network.createOsmCompatibleLinkSegmentTypes(settings);
    /* when modes are deactivated causing supported osm way types to have no active modes, add them to unsupport way types to avoid warnings during parsing */
    settings.excludeOsmWayTypesWithoutActivatedModes();
    settings.logUnsupportedOsmWayTypes();    
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
        
    if(!settings.isOsmWayExcluded(osmWay.getId())) {
      
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
      try {              
        
        /* only parse ways that are potentially road infrastructure */
        if(isActivatedHighwayOrRailway(tags)) {
          
          /* circular ways special case filter */
          if(PlanitOsmUtils.isCircularOsmWay(osmWay, tags, false)) {          
            
            /* postpone creation of link(s) for activated OSM highways that have a circular component and are not areas (areas cannot become roads) */
            /* Note: in OSM roundabouts are a circular way, in PLANit, they comprise several one-way link connecting exists and entries to the roundabout */
            osmCircularWays.put(osmWay.getId(), osmWay);
            
          }else{
            
            /* extract regular OSM way; convert to PLANit infrastructure */          
            extractOsmWay(osmWay, tags);                    
                        
          }
        }
        
      } catch (PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
      }      
    }
            
  }

  /** extract the correct link segment type based on the configuration of supported modes, the defaults for the given osm way and any 
   * modifications to the mode access based on the passed in tags
   * 
   * of the OSM way
   * @param osmWay the way this type extraction is executed for 
   * @param tags tags belonging to the OSM way
   * @return appropriate link segment types for forward and backward direction per network layer. If no modes are allowed in a direction, the link segment type will be null
   * @throws PlanItException thrown if error
   */
  protected Map<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> extractLinkSegmentTypes(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    Map<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayerByDirection = new HashMap<>(); 
    /* a default link segment type should be available as starting point*/
    Map<InfrastructureLayer, MacroscopicLinkSegmentType> linkSegmentTypesByLayer = getDefaultLinkSegmentTypeByOsmWayType(osmWay, tags);
    if(linkSegmentTypesByLayer != null) {      
      
      /* per layer identify the directional link segment types based on additional access changes from osm tags */      
      for(Entry<InfrastructureLayer, MacroscopicLinkSegmentType> entry : linkSegmentTypesByLayer.entrySet()) {
        InfrastructureLayer networkLayer = entry.getKey();
        MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
        
        /* collect possibly modified type (per direction) */
        Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> typesPerdirectionPair = this.osmLayerHandlers.get(entry.getKey()).extractLinkSegmentTypeByOsmAccessTags(osmWay, tags, linkSegmentType);                
        if(typesPerdirectionPair != null) {
          linkSegmentTypesByLayerByDirection.put(entry.getKey(), typesPerdirectionPair);
        }
      }
    }
    return linkSegmentTypesByLayerByDirection;
  }

  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    // delegate
  }

  @Override
  public void complete() throws IOException {
        
    /* process circular ways */
    Map<Long, Set<Link>> createdLinksByOsmWayId = processCircularWays();
        
    /* break all links that have internal nodes that are extreme nodes of other links */
    breakLinksWithInternalConnections(createdLinksByOsmWayId);
    createdLinksByOsmWayId.clear();
    
    /* useful for debugging */
    network.getDefaultNetworkLayer().validate();
    
    /* stats*/
    profiler.logProfileInformation(network);
        
    LOGGER.info("DONE");

    /* free memory */
    osmCircularWays.clear();    
    osmNodes.clear();
    nodesByOsmId.clear();
  }


}

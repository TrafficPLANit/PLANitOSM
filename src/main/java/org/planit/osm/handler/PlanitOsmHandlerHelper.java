package org.planit.osm.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Point;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;

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
  
  /** Identify which links truly have the passed in node as an internal node. whenever we have started with breaking links, or processing cirular ways
   * we can no longer rely on the original internal node mapping. Instead, we must use a two step process:
   * 1) identify the original osmWayId the node was internal to
   * 2) use the known broken links by osmWayId, track the planit link that represents part of the original osmway and mark that as a link with the node as internal.
   * 
   * That is what this method does by updating the linksWithNodeInternallyToUpdate list based on the osmWaysWithMultiplePlanitLinks given the reference node
   * 
   * @param node the node to break at
   * @param osmWaysWithMultiplePlanitLinks known osm ways with multiple planit links due to earlier breaking of links
   * @param linksWithNodeInternallyToUpdate list of links the node is internal to not taken into account breaking of links that has occurred since (to be updated)
   * @return the link to break, null if none could be found
   * @throws PlanItException thrown if error
   */
  protected static void updateLinksForInternalNode(Node node, Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks, List<Link> linksWithNodeInternallyToUpdate) throws PlanItException {
    if(node != null && linksWithNodeInternallyToUpdate!= null && !linksWithNodeInternallyToUpdate.isEmpty()) {
            
      /* find replacement links for the original link to break in case the original already has been broken and we should use 
       * one of the split off broken links instead of the original for the correct breaking for the given node (since it now resides on one of the broken
       * links rather than the original full link that no longer exists in that form */
      Set<Link> replacementLinks = new HashSet<Link>();
      Iterator<Link> linksToBreakIter = linksWithNodeInternallyToUpdate.iterator();
      while(linksToBreakIter.hasNext()) {
        Link orginalLinkToBreak = linksToBreakIter.next(); 
        
        Long osmOriginalWayId = Long.valueOf(orginalLinkToBreak.getExternalId());
        if(osmWaysWithMultiplePlanitLinks != null && osmWaysWithMultiplePlanitLinks.containsKey(osmOriginalWayId)) {
          
          /* link has been broken before, find out in which of its broken links the node to break at resides on */
          Set<Link> earlierBrokenLinks = osmWaysWithMultiplePlanitLinks.get(osmOriginalWayId);
          Link matchingEarlierBrokenLink = null;
          for(Link link : earlierBrokenLinks) {
            Optional<Integer> coordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(node.getPosition().getCoordinate(),link.getGeometry());
            if(coordinatePosition.isPresent()) {
              matchingEarlierBrokenLink = link;
              break;
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
      }      linksWithNodeInternallyToUpdate.addAll(replacementLinks);
    }
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
      /* performing breaking of links, returns the broken links by the original link's PLANit edge id */
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
    } 
    
    return newOsmWaysWithMultiplePlanitLinks;
  }   

  /**
   * Extract a PLANit node from the osmNode information
   * 
   * @param osmNode to create PLANit node for
   * @param networkLayer to create node on
   * @return created node, null when something wen wrong
   */
  public static Node createAndPopulateNode(OsmNode osmNode, MacroscopicPhysicalNetwork networkLayer)  {
    if(osmNode == null || networkLayer == null) {
      LOGGER.severe("no OSM node or network layer provided when creating new PLANit node, ignore");
      return null;
    }
    /* location info */
    Point geometry = null;
    try {
      geometry = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getXCoordinate(osmNode), PlanitOsmNodeUtils.getYCoordinate(osmNode));
    } catch (PlanItException e) {
      LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
    }

    /* create and register */
    Node node = networkLayer.nodes.registerNew();
    /* XML id */
    node.setXmlId(Long.toString(node.getId()));
    /* external id */
    node.setExternalId(String.valueOf(osmNode.getId()));
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

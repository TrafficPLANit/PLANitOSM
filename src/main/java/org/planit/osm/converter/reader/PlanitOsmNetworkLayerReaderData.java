package org.planit.osm.converter.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.handler.PlanitOsmHandlerHelper;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class containing data that maps Osm entities to PLANit entities required during parsing for a specific network layer
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkLayerReaderData {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkLayerReaderData.class.getCanonicalName());
  
  /** track osmways with multiple planit links if they are created due to circular ways or breaking of links. Only track globally when
   * part of intermodal reader where follow up components require this information, otherwise it is locally discarded after use */
  protected Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks = null;
  
  /** Identify which links truly have the passed in node as an internal node. whenever we have started with breaking links, or processing cirular ways
   * we can no longer rely on the original internal node mapping. Instead, we must use a two step process:
   * 1) identify the original osmWayId the node was internal to
   * 2) use the known broken links by osmWayId, track the planit link that represents part of the original osmway and mark that as a link with the node as internal.
   * 
   * That is what this method does by updating the linksWithNodeInternallyToUpdate list based on the osmWaysWithMultiplePlanitLinks given the reference node
   * 
   * @param osmNode the node to break at
   * @param osmWaysWithMultiplePlanitLinks known osm ways with multiple planit links due to earlier breaking of links
   * @param linksWithNodeInternallyToUpdate list of links the node is internal to not taken into account breaking of links that has occurred since (to be updated)
   * @return the link to break, null if none could be found
   * @throws PlanItException thrown if error
   */
  private static void updateLinksForInternalNode(final OsmNode osmNode, final Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks, final List<Link> linksWithNodeInternallyToUpdate) throws PlanItException {
    if(osmNode != null && linksWithNodeInternallyToUpdate!= null && !linksWithNodeInternallyToUpdate.isEmpty()) {
            
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
            Optional<Integer> coordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(PlanitOsmNodeUtils.createCoordinate(osmNode),link.getGeometry());
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
      }
      linksWithNodeInternallyToUpdate.addAll(replacementLinks);
    }
  }    
  
  /**
   * track the PLANit nodes created on this layer by their original OSM id  so they can by looked up quickly while parsing ways
   */
  protected final Map<Long, Node> nodesByOsmId = new HashMap<Long, Node>();
  
  /** Mapping from Osm node id to the links they are internal to. When done intial parsing, we verify if any
   * entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existing link's extreme node is internal to any other link, the link where
   * this node is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link. Since during breaking of links, the mapping between osmnode and planit links is no longer correct
   * we use a seaprate mapping via {@link osmWaysWithMultiplePlanitLinks} to track how original osm ways (links) are now split
   * still allowing us to map an osm node to the correct planit link even after breaking of links
   */
  protected Map<Long, List<Link>> originalLinkInternalOsmNodes = new HashMap<Long, List<Link>>();
          
  
  public Map<Long, Node> getNodesByOsmId() {
    return nodesByOsmId;
  }
  
  /** add a mapping from osm node id to the (initial) planit link it is internal to
   * @param osmNodeId to use
   * @param planitLink to register as osm node being internal to
   */
  public void registerOsmNodeIdAsInternalToPlanitLink(long osmNodeId, Link planitLink) {
    originalLinkInternalOsmNodes.putIfAbsent(osmNodeId, new ArrayList<Link>());
    originalLinkInternalOsmNodes.get(osmNodeId).add(planitLink);
  }  
  
  /** update all known osm ways with multiple planit links. To use whenever a planit link is broken and split into multiple
   * planit links that cover the same original osm way. This registration is used to find the correct planit links that are internal
   * to osm nodes when needed.
   * 
   * @param newOsmWayToPlanitLinkMapping contains new mapping from osm way id to known planit links that cover this osm way
   */
  public void updateOsmWaysWithMultiplePlanitLinks(Map<Long, Set<Link>> newOsmWayToPlanitLinkMapping) {
    PlanitOsmHandlerHelper.addAllTo(newOsmWayToPlanitLinkMapping, osmWaysWithMultiplePlanitLinks);    
  }  
 
  /** Verify if osm node is registered as internal to a planit link
   * 
   * @param osmNodeId to verify
   * @return true when registered as internal, false otherwise
   */
  public boolean isOsmNodeInternalToAnyLink(Long osmNodeId) {
    return originalLinkInternalOsmNodes.containsKey(osmNodeId);
  }   
  
  /** verify if OSM node id is present as PLANit node or is part of a PLANit link's internal geometry
   * 
   * @param osmNodeId to check
   * @return true when part of a geometry in the layer, false otherwise
   */
  public boolean isOsmNodePresentInLayer(long osmNodeId) {
    return (getNodesByOsmId().containsKey(osmNodeId) || originalLinkInternalOsmNodes.containsKey(osmNodeId));
  }    
  
  /** collect all osmNodeIds that are internal to at least the given number of planit links
   * 
   * @param numberOfLinksNodeMustAtLeastBeInternalTo restriction
   * @return found osmNodeIds
   */
  public Set<Long> getOsmNodeIdsInternalToAnyPlanitLink(int numberOfLinksNodeMustAtLeastBeInternalTo) {
    Set<Long> osmNodeIds = new HashSet<Long>();
    for( Entry<Long, List<Link>> entry : originalLinkInternalOsmNodes.entrySet()) {
      if(entry.getValue().size() >= numberOfLinksNodeMustAtLeastBeInternalTo) {
        osmNodeIds.add(entry.getKey());
      }
    }
    return osmNodeIds;
  }
  
  /** based on the original internal osm node to planit link information as well as subsequently break link actions that
   * caused changes to what planit link an osm node is internal to, we identify which current planit links the given osm node
   * id provided is internal to
   * 
   * @param osmNode to use
   * @throws PlanItException thrown if error
   */
  public List<Link> findPlanitLinksWithInternalOsmNode(OsmNode osmNode) throws PlanItException {
    /* collect original mapping frmo node to planit link (however due to breaking links, the referenced link may now we repurposes as part of the original link it represented) */
    List<Link> linksWithOsmNodeInternally = originalLinkInternalOsmNodes.get(osmNode.getId()); 
    if(linksWithOsmNodeInternally == null) {
      LOGGER.warning(String.format("Discard: Osm pt access node (%d) stop_position not attached to network, or not included in OSM file",osmNode.getId()));
      return null;
    }      
    /* update the references to which link the node is internal to based on latest information regarding layerData.getOsmWaysWithMultiplePlanitLinks() so we break the correct links */
    updateLinksForInternalNode(osmNode, osmWaysWithMultiplePlanitLinks, linksWithOsmNodeInternally /* <-- updated */);
    return linksWithOsmNodeInternally;
  }  
   

  public void setOsmWaysWithMultiplePlanitLinks(Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks) {
    this.osmWaysWithMultiplePlanitLinks = osmWaysWithMultiplePlanitLinks;
  }
    

  /**
   * reset contents of members
   */
  public void reset() {
    nodesByOsmId.clear();
    originalLinkInternalOsmNodes.clear();
    osmWaysWithMultiplePlanitLinks.clear();
  }

}
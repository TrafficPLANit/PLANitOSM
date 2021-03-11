package org.planit.osm.converter.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Point;
import org.planit.osm.handler.PlanitOsmHandlerHelper;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.misc.Pair;
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
  protected Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks = new HashMap<Long, Set<Link>>();
  
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
  private void updateLinksForInternalLocation(Point location, Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks, List<Link> linksWithLocationInternally) {
    if(location != null && linksWithLocationInternally!= null && !linksWithLocationInternally.isEmpty()) {
      
      /* find replacement links for the original link to break in case the original already has been broken and we should use 
       * one of the split off broken links instead of the original for the correct breaking for the given node (since it now resides on one of the broken
       * links rather than the original full link that no longer exists in that form */
      Set<Link> replacementLinks = new HashSet<Link>();
      Iterator<Link> linksWithLocationInternal = linksWithLocationInternally.iterator();
      while(linksWithLocationInternal.hasNext()) {
        Link orginalLinkToBreak = linksWithLocationInternal.next(); 
        
        Long osmOriginalWayId = Long.valueOf(orginalLinkToBreak.getExternalId());
        if(osmWaysWithMultiplePlanitLinks != null && osmWaysWithMultiplePlanitLinks.containsKey(osmOriginalWayId)) {
          
          /* link has been broken before, find out in which of its broken links the node to break at resides on */
          Set<Link> earlierBrokenLinks = osmWaysWithMultiplePlanitLinks.get(osmOriginalWayId);
          Link matchingEarlierBrokenLink = null;
          boolean locationInternal = true;
          for(Link link : earlierBrokenLinks) {
            Optional<Integer> coordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(location.getCoordinate(),link.getGeometry());
            if(coordinatePosition.isPresent()) {
              matchingEarlierBrokenLink = link;
              
              int position = coordinatePosition.get();
              if(position==0 || position == (link.getGeometry().getNumPoints()-1)) {
                /* match found, but position is not longer internal due to link being broken before on this location */
                locationInternal = false;
              }
              break;
            }
          }
          
          /* remove original and mark found link as replacement link to break */
          linksWithLocationInternal.remove();          
          
          /* verify if match is valid (which it should be) */
          if(matchingEarlierBrokenLink==null) {
            LOGGER.warning(String.format("unable to locate broken sublink of OSM way %s (id:%d), likely malformed way encountered, ignored",
                orginalLinkToBreak.getExternalId(), orginalLinkToBreak.getId()));            
          }else if(locationInternal){
            replacementLinks.add(matchingEarlierBrokenLink);
          }
        }
      }
      linksWithLocationInternally.addAll(replacementLinks);
    }
  }  
  
  /**
   * track the PLANit nodes created on this layer by their location (which reflects eith an osm node, or an auto-generated stop_location, not related to an osm node
   * in the latter case, no osm node is available) so they can be collected when needed, for example when breaking planit links
   */
  protected final Map<Point, Pair<Node, OsmNode>> planitNodesByLocation = new HashMap<Point,  Pair<Node, OsmNode>>();
  
  /** Mapping from locations (representing known osm nodes or auto-generated planit nodes without osm node, in the latter case, no osm node is stored in the pair) to the links they are internal to. When initial parsing is done, 
   * we verify if any entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existing link's extreme node is internal to any other link, the link where
   * this location is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link. Since during breaking of links, the mapping between known locations (osm nodes/auto-generated planit nodes) and planit links is no longer correct
   * we use a separate mapping via {@link osmWaysWithMultiplePlanitLinks} to track how original osm ways (links) are now split allowing us to map any previously registered  
   * location to the correct planit link even after breaking of links
   */
  protected Map<Point, Pair<List<Link>,OsmNode>> originalLinkInternalAvailableLocations = new HashMap<Point, Pair<List<Link>, OsmNode>>();
                 
  
  /** collect the planit node available for this osm node (if any)
   * 
   * @param osmNode found, null otherwise
   * @throws PlanItException thrown if error
   */
  public Node getPlanitNodeByOsmNode(OsmNode osmNode) throws PlanItException {
    if(osmNode != null) {
      return getPlanitNodeByLocation(PlanitOsmNodeUtils.createPoint(osmNode));
    }
    return null;
  } 
  
  /** collect the planit node available for the given location
   * 
   * @param osmNode found, null otherwise
   * @throws PlanItException thrown if error
   */
  public Node getPlanitNodeByLocation(Point location) throws PlanItException {
    if(location != null) {
      Pair<Node, OsmNode> result = planitNodesByLocation.get(location);
      if(result != null) {
        return result.first();
      }
    }
    return null;
  }  
  
  /** collect the osm node available for the given location (if any), either internal to existing planit node
   * or already available as converted planit node at that location
   * 
   * @param osmNode found, null otherwise
   * @throws PlanItException thrown if error
   */  
  public OsmNode getOsmNodeByLocation(Point location) {
    if(location != null) {
      Pair<Node, OsmNode> result = planitNodesByLocation.get(location);
      if(result != null) {
        return result.second();
      }else {
        /* check if internal to a link and not yet converted to a planit node */
        return getOsmNodeInternalToLinkByLocation(location);
      }
    }
    return null;
  }  
  
  /** provide read access to the registered planit nodes (and original osm node if any was used) by location
   * 
   * @return mapping of locations for which planit nodes are created, potentially based on osm node
   */
  public Map<Point, Pair<Node, OsmNode>> getCreatedPlanitNodesByLocation() {
    return Collections.unmodifiableMap(planitNodesByLocation);
  }
  
  /** register a planit node based on an osm node for this layer
   * @param osmNode to index by
   * @param planitNode to register
   * @throws PlanItException 
   */
  public void registerPlanitNodeByOsmNode(OsmNode osmNode, Node planitNode) throws PlanItException {
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    planitNodesByLocation.put(osmNodeLocation, Pair.of(planitNode, osmNode));
  }
  
  /** register a planit node based on a location only, instead of based on an osm node
   * 
   * @param location to index by
   * @param planitNode to register
   * @throws PlanItException 
   */
  public void registerPlanitNodeByLocation(Point location, Node planitNode) throws PlanItException {
    planitNodesByLocation.put(location, Pair.of(planitNode, null));
  }  
  
  /** add a mapping from osm node id to the (initial) planit link it is internal to
   * @param osmNodeId to use
   * @param planitLink to register as osm node being internal to
   * @throws PlanItException thrown if error
   */
  public void registerOsmNodeAsInternalToPlanitLink(OsmNode osmNode, Link planitLink) throws PlanItException {
    Point location = PlanitOsmNodeUtils.createPoint(osmNode);
    originalLinkInternalAvailableLocations.putIfAbsent(location, Pair.of(new ArrayList<Link>(), osmNode ));
    registerLocationAsInternalToPlanitLink(location, planitLink);
  }  
  
  /** add a mapping from location to the (initial) planit link it is internal to
   * @param location to use
   * @param planitLink to register as location being internal to (location either being a known osm node, or, for example, an auto-generated stop_position, not absed on a planit node)
   */  
  public void registerLocationAsInternalToPlanitLink(Point location, Link planitLink) {
    originalLinkInternalAvailableLocations.putIfAbsent(location, Pair.of(new ArrayList<Link>(), null /* no node */));
    originalLinkInternalAvailableLocations.get(location).first().add(planitLink);
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
  
  /** update all known osm ways with multiple planit links. To use whenever a planit link is broken and split into multiple
   * planit links that cover the same original osm way. This registration is used to find the correct planit links that are internal
   * to osm nodes when needed.
   * 
   * @param osmWayId to add links for
   * @param newOsmWayToPlanitLinkMapping contains additional planit links created for this osm way
   */
  public void updateOsmWaysWithMultiplePlanitLinks(Long osmWayId, Set<Link> newOsmWayToPlanitLinkMapping) {
    if(newOsmWayToPlanitLinkMapping.size() < 2) {
      LOGGER.warning(String.format("registering multiple planit links for osm way %d, but only one or less planit links provided",osmWayId));
    }
    if(osmWaysWithMultiplePlanitLinks.containsKey(osmWayId)) {
      osmWaysWithMultiplePlanitLinks.get(osmWayId).addAll(newOsmWayToPlanitLinkMapping);  
    }else {
      osmWaysWithMultiplePlanitLinks.put(osmWayId, newOsmWayToPlanitLinkMapping);
    }    
  }    
  
  /** the number of osm ways with multiple planit links created for them
   * @return total
   */
  public long getNumberOfOsmWaysWithMultiplePlanitLinks() {
    return osmWaysWithMultiplePlanitLinks.size();
  }  
 
  /** Verify if location is registered as internal to a planit link
   * 
   * @param osmNodeId to verify
   * @return true when registered as internal, false otherwise
   */
  public boolean isLocationInternalToAnyLink(Point location) {
    return originalLinkInternalAvailableLocations.containsKey(location);
  }
  
  /** verify if osm node is part of this layer either as a planit node, or internal to any planit link 
   * 
   * @param location to check
   * @return true when part of a geometry in the layer, false otherwise
   * @throws PlanItException thrown if error
   */
  public boolean isOsmNodePresentInLayer(OsmNode osmNode) throws PlanItException {
    return isLocationPresentInLayer(PlanitOsmNodeUtils.createPoint(osmNode));
  }     
  
  /** verify if location is registered on this layer either as an internal location on a planit link or as an extreme node
   * of a planit link
   * 
   * @param location to check
   * @return true when part of a geometry in the layer, false otherwise
   */
  public boolean isLocationPresentInLayer(Point location) {
    return (planitNodesByLocation.containsKey(location) || isLocationInternalToAnyLink(location));
  }
  
  /** collect all registered locations internal to a planit link (unmoidfiable)
   * 
   * @param numberOfLinksNodeMustAtLeastBeInternalTo restriction
   * @return found locations
   */
  public Set<Point> getRegisteredLocationsInternalToAnyPlanitLink() {
    return Collections.unmodifiableSet(originalLinkInternalAvailableLocations.keySet());
  }  
  
  /** collect all registered locations that are internal to at least the given number of planit links
   * 
   * @param numberOfLinksNodeMustAtLeastBeInternalTo restriction
   * @return found locations
   */
  public Set<Point> getRegisteredLocationsInternalToAnyPlanitLink(int numberOfLinksNodeMustAtLeastBeInternalTo) {
    Set<Point> foundLocations = new HashSet<Point>();
    for( Entry<Point, Pair<List<Link>,OsmNode>> entry : originalLinkInternalAvailableLocations.entrySet()) {
      if(entry.getValue().first().size() >= numberOfLinksNodeMustAtLeastBeInternalTo) {
        foundLocations.add(entry.getKey());
      }
    }
    return foundLocations;
  }
  
  /** collect all registered osm nodes that are internal to at least the given number of planit links
   * 
   * @param numberOfLinksNodeMustAtLeastBeInternalTo restriction
   * @return found osm nodes
   */  
  public Set<OsmNode> getRegisteredOsmNodesInternalToAnyPlanitLink(int numberOfLinksNodeMustAtLeastBeInternalTo) {
    Set<OsmNode> foundOsmNodes = new HashSet<OsmNode>();
    for( Entry<Point, Pair<List<Link>,OsmNode>> entry : originalLinkInternalAvailableLocations.entrySet()) {
      List<Link> planitLinks = entry.getValue().first();
      OsmNode osmNode = entry.getValue().second();
      if(planitLinks.size() >= numberOfLinksNodeMustAtLeastBeInternalTo && osmNode!=null) {
        foundOsmNodes.add(entry.getValue().second());        
      }
    }
    return foundOsmNodes;
  }  
  
  /** collect osm node registered as internal to an already created planit link based on its location
   * 
   * @param location to use
   * @return osmNode, null if no match was found
   */
  public OsmNode getOsmNodeInternalToLinkByLocation(Point location) {
    Pair<List<Link>, OsmNode> result = originalLinkInternalAvailableLocations.get(location);
    if(result!=null) {
      return result.second();
    }
    return null;
  }
  
  /** We identify which current planit links have the given location registered as internal to them
   * 
   * @param location to use
   * @throws PlanItException thrown if error
   */
  public List<Link> findPlanitLinksWithInternalLocation(Point location) {
    /* collect original mapping from a known internal location (osm node, auto-generated location) to planit link (however due to breaking links, the referenced link may now we repurposed as part of the original link it represented) */
    Pair<List<Link>,OsmNode> result = originalLinkInternalAvailableLocations.get(location);  
    if(result==null || result.first() == null) {
      LOGGER.fine(String.format("DISCARD: Osm pt stop_position %s not available on network layer within planit link or as extreme node", location.toString()));
      return null;
    }  
    
    List<Link> linksWithLocationInternally = result.first();
    /* update the references to which link the location is internal to based on latest information regarding layerData.getOsmWaysWithMultiplePlanitLinks() so we break the correct links */
    updateLinksForInternalLocation(location, osmWaysWithMultiplePlanitLinks, linksWithLocationInternally /* <-- updated */);
    return linksWithLocationInternally;
  }
  
  /** We identify which current planit links have the given osm node registered as internal to them
   * 
   * @param osmNode to use
   * @throws PlanItException thrown if error
   */  
  public List<Link> findPlanitLinksWithInternalOsmNode(OsmNode osmNode) throws PlanItException {
    return findPlanitLinksWithInternalLocation(PlanitOsmNodeUtils.createPoint(osmNode));
  }  
       
  /**
   * reset contents of members
   */
  public void reset() {
    planitNodesByLocation.clear();
    originalLinkInternalAvailableLocations.clear();
    osmWaysWithMultiplePlanitLinks.clear();
  }


}
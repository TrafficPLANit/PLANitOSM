package org.goplanit.osm.converter.network;

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

import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Point;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class containing data that maps Osm entities to PLANit entities required during parsing for a specific network layer
 * 
 * @author markr
 *
 */
public class OsmNetworkReaderLayerData {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderLayerData.class.getCanonicalName());
  
  /** profiler for the network layer */
  private final OsmNetworkHandlerProfiler profiler = new OsmNetworkHandlerProfiler();  
  
  /** track osmways with multiple planit links if they are created due to circular ways or breaking of links. Only track globally when
   * part of intermodal reader where follow up components require this information, otherwise it is locally discarded after use */
  protected Map<Long, Set<MacroscopicLink>> osmWaysWithMultiplePlanitLinks = new HashMap<>();
  
  /** Identify which links truly have the passed in node as an internal node. whenever we have started with breaking links, or processing cirular ways
   * we can no longer rely on the original internal node mapping. Instead, we must use a two step process:
   * 1) identify the original osmWayId the node was internal to
   * 2) use the known broken links by osmWayId, track the planit link that represents part of the original osmway and mark that as a link with the node as internal.
   * 
   * That is what this method does by updating the linksWithNodeInternallyToUpdate list based on the osmWaysWithMultiplePlanitLinks given the reference node
   * 
   * @param location the point to break at
   * @param osmWaysWithMultiplePlanitLinks known osm ways with multiple planit links due to earlier breaking of links
   * @param linksWithLocationInternally list of links the point is internal to not taken into account breaking of links that has occurred since (to be updated)
   * @return the link to break, null if none could be found
   */
  private void updateLinksForInternalLocation(Point location, Map<Long, Set<MacroscopicLink>> osmWaysWithMultiplePlanitLinks, List<MacroscopicLink> linksWithLocationInternally) {
    if(location != null && linksWithLocationInternally!= null && !linksWithLocationInternally.isEmpty()) {
      
      /* find replacement links for the original link to break in case the original already has been broken and we should use 
       * one of the split off broken links instead of the original for the correct breaking for the given node (since it now resides on one of the broken
       * links rather than the original full link that no longer exists in that form */
      Set<MacroscopicLink> replacementLinks = new HashSet<>();
      Iterator<MacroscopicLink> linksWithLocationInternal = linksWithLocationInternally.iterator();
      final double coordinateTolerance = 0;
      while(linksWithLocationInternal.hasNext()) {
        Link orginalLinkToBreak = linksWithLocationInternal.next(); 
        
        Long osmOriginalWayId = Long.valueOf(orginalLinkToBreak.getExternalId());
        if(osmWaysWithMultiplePlanitLinks != null && osmWaysWithMultiplePlanitLinks.containsKey(osmOriginalWayId)) {
          
          /* link has been broken before, find out in which of its broken links the node to break at resides on */
          Set<MacroscopicLink> earlierBrokenLinks = osmWaysWithMultiplePlanitLinks.get(osmOriginalWayId);
          MacroscopicLink matchingEarlierBrokenLink = null;
          boolean locationInternal = true;
          for(var link : earlierBrokenLinks) {
            Optional<Integer> coordinatePosition = PlanitJtsUtils.findFirstCoordinatePosition(location.getCoordinate(),link.getGeometry(), coordinateTolerance);
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
            LOGGER.warning(String.format("Unable to locate broken sublink of OSM way %s (id:%d), likely malformed way encountered, ignored",
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
  
  /** Mapping from locations (representing known OSM nodes or auto-generated PLANit nodes without OSM node, in the latter case, no OSM node is stored in the pair) to the links they are internal to. When initial parsing is done, 
   * we verify if any entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existing link's extreme node is internal to any other link, the link where
   * this location is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link. Since during breaking of links, the mapping between known locations (osm nodes/auto-generated planit nodes) and planit links is no longer correct
   * we use a separate mapping via {@link #osmWaysWithMultiplePlanitLinks} to track how original osm ways (links) are now split allowing us to map any previously registered
   * location to the correct planit link even after breaking of links
   */
  protected Map<Point, Pair<List<MacroscopicLink>,OsmNode>> originalLinkInternalAvailableLocations = new HashMap<>();
                 
  
  /** Collect the PLANit node available for this osm node (if any)
   * 
   * @param osmNode to find node for
   * @return PLANit node found, null if not found
   */
  public Node getPlanitNodeByOsmNode(OsmNode osmNode){
    if(osmNode != null) {
      Node planitNode = getPlanitNodeByLocation(OsmNodeUtils.createPoint(osmNode));
      if(planitNode!=null && osmNode.getId() != Long.valueOf(planitNode.getExternalId())) {
        /* match found, but different osm ids for same location, meaning that separate nodes reside in same location */
        LOGGER.warning(String.format("OsmNodes %d and %s, reside on same location, likely tagging error", osmNode.getId(), planitNode.getExternalId()));
      }
      return planitNode;
    }
    return null;
  } 
  
  /** Collect the PLANit node available for the given location
   * 
   * @param location to find for
   * @return PLANit node found, null if not found
   */
  public Node getPlanitNodeByLocation(Point location){
    if(location != null) {
      Pair<Node, OsmNode> result = planitNodesByLocation.get(location);
      if(result != null) {
        return result.first();
      }
    }
    return null;
  }  
  
  /** Collect the OSM node available for the given location (if any), either internal to existing PLANit node
   * or already available as converted PLANit node at that location
   * 
   * @param location to find for
   * @return osmNode found null otherwise
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
  
  /** provide read access to the registered PLANit nodes (and original OSM node if any was used) by location
   * 
   * @return mapping of locations for which planit nodes are created, potentially based on OSM node
   */
  public Map<Point, Pair<Node, OsmNode>> getCreatedPlanitNodesByLocation() {
    return Collections.unmodifiableMap(planitNodesByLocation);
  }
  
  /** Register a PLANit node based on an OSM node for this layer
   * 
   * @param osmNode to index by
   * @param planitNode to register
   */
  public void registerPlanitNodeByOsmNode(OsmNode osmNode, Node planitNode){
    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    planitNodesByLocation.put(osmNodeLocation, Pair.of(planitNode, osmNode));
  }
  
  /** register a PLANit node based on a location only, instead of based on an OSM node
   * 
   * @param location to index by
   * @param planitNode to register
   */
  public void registerPlanitNodeByLocation(Point location, Node planitNode){
    planitNodesByLocation.put(location, Pair.of(planitNode, null));
  }  
  
  /** Add a mapping from OSM node id to the (initial) PLANit link it is internal to
   * 
   * @param osmNode to use
   * @param planitLink to register as OSM node being internal to
   */
  public void registerOsmNodeAsInternalToPlanitLink(OsmNode osmNode, MacroscopicLink planitLink){
    Point location = OsmNodeUtils.createPoint(osmNode);
    originalLinkInternalAvailableLocations.putIfAbsent(location, Pair.of(new ArrayList<>(), osmNode ));
    registerLocationAsInternalToPlanitLink(location, planitLink);
  }  
  
  /** add a mapping from location to the (initial) PLANit link it is internal to
   * 
   * @param location to use
   * @param planitLink to register as location being internal to (location either being a known osm node, or, for example, an auto-generated stop_position, not absed on a planit node)
   */  
  public void registerLocationAsInternalToPlanitLink(Point location, MacroscopicLink planitLink) {
    originalLinkInternalAvailableLocations.putIfAbsent(location, Pair.of(new ArrayList<>(), null /* no node */));
    originalLinkInternalAvailableLocations.get(location).first().add(planitLink);
  }  
  
  /** update all known OSM ways with multiple PLANit links. To use whenever a PLANit link is broken and split into multiple
   * PLANit links that cover the same original OSM way. This registration is used to find the correct PLANit links that are internal
   * to OSM nodes when needed.
   * 
   * @param newOsmWayToPlanitLinkMapping contains new mapping from osm way id to known planit links that cover this osm way
   */
  public void updateOsmWaysWithMultiplePlanitLinks(Map<Long, Set<MacroscopicLink>> newOsmWayToPlanitLinkMapping) {
    OsmNetworkHandlerHelper.addAllTo(newOsmWayToPlanitLinkMapping, osmWaysWithMultiplePlanitLinks);    
  }
  
  /** update all known OSM ways with multiple PLANit links. To use whenever a PLANit link is broken and split into multiple
   * PLANit links that cover the same original OSM way. This registration is used to find the correct PLANit links that are internal
   * to OSM nodes when needed.
   * 
   * @param osmWayId to add links for
   * @param newOsmWayToPlanitLinkMapping contains additional PLANit links created for this OSM way
   */
  public void updateOsmWaysWithMultiplePlanitLinks(Long osmWayId, Set<MacroscopicLink> newOsmWayToPlanitLinkMapping) {
    if(newOsmWayToPlanitLinkMapping.size() < 2) {
      LOGGER.warning(String.format("registering multiple planit links for osm way %d, but only one or less planit links provided",osmWayId));
    }
    if(osmWaysWithMultiplePlanitLinks.containsKey(osmWayId)) {
      osmWaysWithMultiplePlanitLinks.get(osmWayId).addAll(newOsmWayToPlanitLinkMapping);  
    }else {
      osmWaysWithMultiplePlanitLinks.put(osmWayId, newOsmWayToPlanitLinkMapping);
    }    
  }    
  
  /** the number of OSM ways with multiple PLANit links created for them
   * 
   * @return total
   */
  public long getNumberOfOsmWaysWithMultiplePlanitLinks() {
    return osmWaysWithMultiplePlanitLinks.size();
  }  
 
  /** Verify if location is registered as internal to a PLANit link
   * 
   * @param location to verify
   * @return true when registered as internal, false otherwise
   */
  public boolean isLocationInternalToAnyLink(Point location) {
    return originalLinkInternalAvailableLocations.containsKey(location);
  }
  
  /** verify if OSM node is part of this layer either as a PLANit node, or internal to any PLANit link 
   * 
   * @param osmNode to check
   * @return true when part of a geometry in the layer, false otherwise
   */
  public boolean isOsmNodePresentInLayer(OsmNode osmNode){
    return isLocationPresentInLayer(OsmNodeUtils.createPoint(osmNode));
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
  
  /** Collect all registered locations internal to a planit link (unmodifiable)
   * 
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
    Set<Point> foundLocations = new HashSet<>();
    for( Entry<Point, Pair<List<MacroscopicLink>,OsmNode>> entry : originalLinkInternalAvailableLocations.entrySet()) {
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
    Set<OsmNode> foundOsmNodes = new HashSet<>();
    for( Entry<Point, Pair<List<MacroscopicLink>,OsmNode>> entry : originalLinkInternalAvailableLocations.entrySet()) {
      List<MacroscopicLink> planitLinks = entry.getValue().first();
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
    Pair<List<MacroscopicLink>, OsmNode> result = originalLinkInternalAvailableLocations.get(location);
    if(result!=null) {
      return result.second();
    }
    return null;
  }
  
  /** We identify which current planit links have the given location registered as internal to them
   * 
   * @param location to use
   * @return found planit links, null if input is null
   */
  public List<MacroscopicLink> findPlanitLinksWithInternalLocation(Point location) {
    if(location == null) {
      return null;
    }
    
    /* collect original mapping from a known internal location (osm node, auto-generated location) to planit link (however due to breaking links, the referenced link may now we repurposed as part of the original link it represented) */
    Pair<List<MacroscopicLink>,OsmNode> result = originalLinkInternalAvailableLocations.get(location);
    if(result==null || result.first() == null) {
      LOGGER.fine(String.format("DISCARD: Osm pt stop_position %s not available on network layer within planit link or as extreme node", location.toString()));
      return null;
    }  
    
    List<MacroscopicLink> linksWithLocationInternally = result.first();
    /* update the references to which link the location is internal to based on latest information regarding layerData.getOsmWaysWithMultiplePlanitLinks() so we break the correct links */
    updateLinksForInternalLocation(location, osmWaysWithMultiplePlanitLinks, linksWithLocationInternally /* <-- updated */);
    return linksWithLocationInternally;
  }
       
  /**
   * reset contents of members
   */
  public void reset() {
    planitNodesByLocation.clear();
    originalLinkInternalAvailableLocations.clear();
    osmWaysWithMultiplePlanitLinks.clear();
  }

  /** Collect the profiler
   * 
   * @return profiler for this layer
   */
  public OsmNetworkHandlerProfiler getProfiler() {
    return profiler;
  }


}
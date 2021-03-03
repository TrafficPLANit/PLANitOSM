package org.planit.osm.converter.reader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;

/**
 * Class containing data that maps Osm entities to PLANit entities required during parsing for a specific network layer
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkLayerReaderData {
  
  /**
   * track the PLANit nodes created on this layer by their original OSM id  so they can by looked up quickly while parsing ways
   */
  protected final Map<Long, Node> nodesByOsmId = new HashMap<Long, Node>();
  
  /** Mapping from Osm node id to the links they are internal to. When done parsing, we verify if any
   * entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existing link's extreme node is internal to any other link, the link where
   * this node is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link
   */
  protected Map<Long, List<Link>> linkInternalOsmNodes = new HashMap<Long, List<Link>>();
  
  /** track osmways with multiple planit links if they are created due to circular ways or breaking of links. Only track globally when
   * part of intermodal reader where follow up components require this information, otherwise it is locally discarded after use */
  protected Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks = null;
        
  
  public Map<Long, Node> getNodesByOsmId() {
    return nodesByOsmId;
  }
 
  public Map<Long, List<Link>> getLinksByInternalOsmNodeId() {
    return linkInternalOsmNodes;
  }

  
  public Map<Long, Set<Link>> getOsmWaysWithMultiplePlanitLinks() {
    return osmWaysWithMultiplePlanitLinks;
  }

  public void setOsmWaysWithMultiplePlanitLinks(Map<Long, Set<Link>> osmWaysWithMultiplePlanitLinks) {
    this.osmWaysWithMultiplePlanitLinks = osmWaysWithMultiplePlanitLinks;
  }
  
  /** verify if OSM node id is present as PLANit node or is part of a PLANit link's internal geometry
   * 
   * @param osmNodeId to check
   * @return true when part of a geometry in the layer, false otherwise
   */
  public boolean isOsmNodePresentInLayer(long osmNodeId) {
    return (getNodesByOsmId().containsKey(osmNodeId) || getLinksByInternalOsmNodeId().containsKey(osmNodeId));
  }  

  /**
   * reset contents of members
   */
  public void reset() {
    nodesByOsmId.clear();
    linkInternalOsmNodes.clear();
    osmWaysWithMultiplePlanitLinks.clear();
  }  
}
package org.goplanit.osm.converter;

import de.topobyte.osm4j.core.model.iface.OsmNode;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Manage OSM nodes that are retained in memory for parsing. This is supported through a two stage process where nodes can be pre-registered
 * and then be registered at a later point.
 */
public class OsmNodeData {

  private static final Logger LOGGER = Logger.getLogger(OsmNodeData.class.getCanonicalName());

  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final Map<Long, OsmNode> osmNodes = new HashMap<>();

  /** Collect the OSM nodes (unmodifiable)
   *
   * @return osm nodes
   */
  public Map<Long, OsmNode> getRegisteredOsmNodes() {
    return Collections.unmodifiableMap(osmNodes);
  }

  /** Add the actual OSM node to an already eligible marked OSM node entry
   * @param osmNode to register
   */
  public void registerEligibleOsmNode(OsmNode osmNode) {
    if(!osmNodes.containsKey(osmNode.getId())) {
      LOGGER.severe("Only OSM nodes that have already been marked as eligible can be complemented with the actual OSM node contents");
    }
    osmNodes.put(osmNode.getId(), osmNode);
  }

  /** Pre-register an OSM node for future population with the actual node contents (see {@link #registerEligibleOsmNode(OsmNode)}
   * @param osmNodeId to pre-register
   */
  public void preRegisterEligibleOsmNode(long osmNodeId) {
    if(osmNodeId == 223007282L){
      int bla = 4;
    }
    osmNodes.put(osmNodeId, null);
  }

  /** Collect an OSM node
   * @param osmNodeId to collect
   * @return osm node, null if not present
   */
  public OsmNode getRegisteredOsmNode(long osmNodeId) {
    return osmNodes.get(osmNodeId);
  }

  /** Verify if OSM node itself is registered and available
   * @param osmNodeId to verify
   * @return true when available, false otherwise
   */
  public boolean containsOsmNode(long osmNodeId) {
    return getRegisteredOsmNode(osmNodeId)!=null;
  }

  /** Verify if OSM node pre-registered while actual node may not yet be available
   * @param osmNodeId to verify
   * @return true when pre-registered, false otherwise
   */
  public boolean containsPreregisteredOsmNode(long osmNodeId) {
    return osmNodes.containsKey(osmNodeId);
  }

  /**
   * Remove all registered OSM node based on provided predicate
   *
   * @param predicate remove if this returns true for an entry
   */
  public void removeRegisteredOsmNodesIf(Predicate<Map.Entry<Long, OsmNode>> predicate) {
    osmNodes.entrySet().removeIf(predicate);
  }

  public void reset(){
    osmNodes.clear();
  }

}

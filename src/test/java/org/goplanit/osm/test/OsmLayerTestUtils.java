package org.goplanit.osm.test;

import java.nio.file.Path;

import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;

/**
 * Lookups for tests on a layer parsed from a hand-made OSM scenario: PLANit entities by the OSM nodes they were created
 * for
 */
public final class OsmLayerTestUtils {

  /** directory of the hand-made traffic signal scenario files */
  public static final Path TRAFFIC_SIGNAL_SCENARIO_DIR = Path.of("src", "test", "resources", "osm", "traffic-signals");

  /**
   * Not instantiable
   */
  private OsmLayerTestUtils() {
  }

  /**
   * The PLANit node created for an OSM node
   *
   * @param layer to search
   * @param osmNodeId of the OSM node
   * @return the node
   */
  public static Node nodeByOsmId(MacroscopicNetworkLayer layer, long osmNodeId) {
    return layer.getNodes().stream()
        .filter(node -> String.valueOf(osmNodeId).equals(node.getExternalId())).findFirst().orElseThrow();
  }

  /**
   * The link segment running from the PLANit node of one OSM node to that of another
   *
   * @param layer to search
   * @param fromOsmNodeId OSM node id of the upstream node
   * @param toOsmNodeId OSM node id of the downstream node
   * @return the segment
   */
  public static MacroscopicLinkSegment segment(MacroscopicNetworkLayer layer, long fromOsmNodeId, long toOsmNodeId) {
    var from = nodeByOsmId(layer, fromOsmNodeId);
    var to = nodeByOsmId(layer, toOsmNodeId);
    return layer.getLinkSegments().stream()
        .filter(ls -> ls.getUpstreamVertex() == from && ls.getDownstreamVertex() == to).findFirst().orElseThrow();
  }
}

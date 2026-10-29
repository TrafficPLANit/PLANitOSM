package org.goplanit.osm.test;

import java.nio.file.Path;

import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicBannedTurn;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;

/**
 * Reading and lookups for tests on a layer parsed from a hand-made OSM scenario: PLANit entities by the OSM nodes they
 * were created for
 */
public final class OsmLayerTestUtils {

  /** directory of the hand-made traffic signal scenario files */
  public static final Path TRAFFIC_SIGNAL_SCENARIO_DIR = Path.of("src", "test", "resources", "osm", "traffic-signals");

  /** directory of the hand-made lanes scenario files */
  public static final Path LANES_SCENARIO_DIR = Path.of("src", "test", "resources", "osm", "lanes");

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

  /**
   * The banned turn from one link segment onto another
   *
   * @param layer holding the banned turns
   * @param from link segment arriving at the node
   * @param to link segment leaving it
   * @return the banned turn, null when there is none
   */
  public static MacroscopicBannedTurn bannedTurn(
      MacroscopicNetworkLayer layer, MacroscopicLinkSegment from, MacroscopicLinkSegment to) {
    return layer.getBannedTurns().getBySegment(from).stream()
        .filter(ban -> ban.getSegmentFrom() == from && ban.getSegmentTo() == to)
        .map(MacroscopicBannedTurn.class::cast).findFirst().orElse(null);
  }

  /**
   * Read the first layer of a scenario in Australia, keeping every way: dangling subnetworks are not removed, so a
   * scenario of separate or one-way roads stays whole
   *
   * @param scenario path of the OSM file
   * @return the first layer read
   */
  public static MacroscopicNetworkLayer readFirstLayerKeepingDanglingSubnetworks(String scenario) {
    var reader = OsmNetworkReaderFactory.create(scenario, CountryNames.AUSTRALIA);
    reader.getSettings().deactivateRemoveDanglingSubnetworks();
    return reader.read().getTransportLayers().getFirst();
  }
}

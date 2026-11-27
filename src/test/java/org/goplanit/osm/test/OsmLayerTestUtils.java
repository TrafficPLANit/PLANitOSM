package org.goplanit.osm.test;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicBannedTurn;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.Intersection;
import org.goplanit.utils.network.layer.physical.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reading and lookups for tests on a layer parsed from a hand-made OSM scenario: PLANit entities by the OSM nodes they
 * were created for
 */
public final class OsmLayerTestUtils {

  /**
   * Not instantiable
   */
  private OsmLayerTestUtils() {
  }

  /** directory of the hand-made traffic signal scenario files */
  public static final Path TRAFFIC_SIGNAL_SCENARIO_DIR = Path.of("src", "test", "resources", "osm", "traffic-signals");

  /** directory of the hand-made lanes scenario files */
  public static final Path LANES_SCENARIO_DIR = Path.of("src", "test", "resources", "osm", "lanes");

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
   * Verify whether any link segment of an OSM way allows a mode
   *
   * @param layer to search
   * @param mode to verify
   * @param osmWayId id of the OSM way, the external id of its links
   * @return true when a link segment of the way allows the mode, false otherwise, also when the way has no links
   */
  public static boolean isModeAllowedOnWay(MacroscopicNetworkLayer layer, Mode mode, String osmWayId) {
    return layer.getLinkSegments().stream().anyMatch(
        segment -> osmWayId.equals(segment.getParent().getExternalId()) && segment.isModeAllowed(mode));
  }

  /**
   * A reader of a scenario in Australia with the default settings, changed as given. For a test that changes settings,
   * or reads the reader's data after
   *
   * @param scenario path of the OSM file
   * @param settingsChange made to the default settings, such as overrides
   * @return the reader, not read yet
   */
  public static OsmNetworkReader createReader(String scenario, Consumer<OsmNetworkReaderSettings> settingsChange) {
    var reader = OsmNetworkReaderFactory.create(scenario, CountryNames.AUSTRALIA);
    settingsChange.accept(reader.getSettings());
    return reader;
  }

  /**
   * A reader of a scenario in Australia, keeping every way: dangling subnetworks are not removed, so a scenario of
   * separate or one-way roads stays whole. For a test that changes more settings, or reads the reader's data after
   *
   * @param scenario path of the OSM file
   * @param settingsChange made to the settings after dangling subnetworks are kept, such as overrides
   * @return the reader, not read yet
   */
  public static OsmNetworkReader createReaderKeepingDanglingSubnetworks(
      String scenario, Consumer<OsmNetworkReaderSettings> settingsChange) {
    return createReader(scenario, settings -> {
      settings.deactivateRemoveDanglingSubnetworks();
      settingsChange.accept(settings);
    });
  }

  /**
   * Read the first layer of a scenario in Australia, keeping every way: dangling subnetworks are not removed, so a
   * scenario of separate or one-way roads stays whole
   *
   * @param scenario path of the OSM file
   * @return the first layer read
   */
  public static MacroscopicNetworkLayer readFirstLayerKeepingDanglingSubnetworks(String scenario) {
    return createReaderKeepingDanglingSubnetworks(scenario, settings -> { }).read().getTransportLayers().getFirst();
  }

  /**
   * The layer's only intersection
   *
   * @param layer holding one intersection
   * @return the intersection
   */
  public static Intersection onlyIntersection(MacroscopicNetworkLayer layer) {
    assertEquals(1, layer.getIntersections().size());
    return layer.getIntersections().stream().findFirst().orElseThrow();
  }
}

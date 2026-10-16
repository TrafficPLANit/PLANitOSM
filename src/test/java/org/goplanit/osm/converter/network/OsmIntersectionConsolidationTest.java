package org.goplanit.osm.converter.network;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.intersection.SpatialIntersection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.nodeByOsmId;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;

/**
 * Consolidating the intersections parsed from traffic signals, switched on in the reader settings. The scenario is
 * two divided roads crossing, with a traffic signal on each of the four nodes where the carriageways cross
 * <pre>
 *             11     14
 *              |     ^
 *              v     |
 *     16 <---- 1 <-- 2 <---- 15      way 12 westbound: 15-2-1-16, way 13 eastbound: 17-3-4-18
 *              |     ^               way 10 southbound: 11-1-3-12, way 11 northbound: 13-4-2-14
 *              v     |               nodes 1 to 4 signalised, about 15 m apart
 *     17 ----> 3 --> 4 ----> 18
 *              |     ^
 *              v     |
 *             12     13
 * </pre>
 *
 * @author markr
 */
public class OsmIntersectionConsolidationTest {

  /** records the warnings the reader logs */
  private static class WarningCounter extends Handler {
    int warnings = 0;
    @Override public void publish(LogRecord record) { if (record.getLevel() == Level.WARNING) { ++warnings; } }
    @Override public void flush() {}
    @Override public void close() {}
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** a reader of a scenario file with the given settings changes */
  private static OsmNetworkReader readerOf(String scenario, Consumer<OsmNetworkReaderSettings> configure) {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(scenario + ".osm").toString(), CountryNames.AUSTRALIA);
    configure.accept(reader.getSettings());
    return reader;
  }

  /** Without consolidation, each of the four signalised nodes is its own intersection */
  @Test
  public void eachNodeItsOwnIntersectionByDefault() {
    var reader = readerOf("divided_roads_crossing", settings -> {});
    var layer = reader.read().getTransportLayers().getFirst();

    assertFalse(reader.getSettings().isConsolidateIntersections());
    assertEquals(4, layer.getIntersections().size());
  }

  /** With consolidation switched on, the four become one spatial intersection with four member nodes; the four it
   * replaces are each recorded once */
  @Test
  public void consolidationSwitchedOnJoinsTheDividedRoadsCrossing() {
    var reader = readerOf("divided_roads_crossing", settings -> settings.setConsolidateIntersections(true));
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var consolidated = (SpatialIntersection) layer.getIntersections().getFirst();
    assertEquals(Set.of(nodeByOsmId(layer, 1), nodeByOsmId(layer, 2), nodeByOsmId(layer, 3), nodeByOsmId(layer, 4)),
        Set.copyOf(consolidated.getMemberNodes()));
    assertTrue(consolidated.getInternalSegmentsWithSignal().isEmpty());
    var replaced = reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.INTERSECTION_REPLACED_BY_CONSOLIDATION);
    assertEquals(4, replaced.size());
    assertEquals(4, Set.copyOf(replaced).size());
  }

  /** A vehicle signal for westbound traffic stands between the carriageways, between 2 and 1: once consolidated, that
   * segment is an internal segment with a signal, and the others are plain */
  @Test
  public void stopLineBetweenTheCarriagewaysBecomesAnInternalSegmentWithASignal() {
    var reader = readerOf("divided_roads_crossing_stop_line_between_carriageways",
        settings -> settings.setConsolidateIntersections(true));
    var layer = reader.read().getTransportLayers().getFirst();

    var consolidated = (SpatialIntersection) layer.getIntersections().getFirst();
    assertEquals(List.of(segment(layer, 2, 1)), consolidated.getInternalSegmentsWithSignal());
    assertTrue(consolidated.getInternalSegments().containsAll(
        List.of(segment(layer, 1, 3), segment(layer, 4, 2), segment(layer, 2, 1), segment(layer, 3, 4))));
  }

  /** Collapsing pedestrians adds a centre node to the consolidated intersection, and its movements stay as they are */
  @Test
  public void collapsingPedestriansAddsACentreNode() {
    var reader = readerOf("divided_roads_crossing", settings -> {
      settings.setConsolidateIntersections(true);
      settings.setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);
    });
    var network = reader.read();
    var consolidated = (SpatialIntersection) network.getTransportLayers().getFirst().getIntersections().getFirst();
    IdGenerator.reset();
    var notCollapsed = (SpatialIntersection) readerOf("divided_roads_crossing",
        settings -> settings.setConsolidateIntersections(true)).read().getTransportLayers().getFirst()
        .getIntersections().getFirst();

    assertEquals(5, consolidated.getMemberNodes().size());
    assertEquals(notCollapsed.getMovements().size(), consolidated.getMovements().size());
  }

  /** Consolidation switched on while intersections are not parsed does not run, and a warning says so */
  @Test
  public void consolidationNeedsParsedIntersections() {
    var counter = new WarningCounter();
    var logger = Logger.getLogger(OsmNetworkReader.class.getCanonicalName());
    logger.addHandler(counter);
    try {
      readerOf("divided_roads_crossing", settings -> settings.setParseIntersections(false)).read();
      int warningsWithoutConsolidation = counter.warnings;
      counter.warnings = 0;
      IdGenerator.reset();

      var layer = readerOf("divided_roads_crossing", settings -> {
        settings.setParseIntersections(false);
        settings.setConsolidateIntersections(true);
      }).read().getTransportLayers().getFirst();

      assertTrue(layer.getIntersections().isEmpty());
      assertEquals(warningsWithoutConsolidation + 1, counter.warnings);
    } finally {
      logger.removeHandler(counter);
    }
  }
}

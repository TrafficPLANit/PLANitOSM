package org.goplanit.osm.converter.network;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
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

  /** a reader of a scenario file with the given settings changes */
  private static OsmNetworkReader readerOf(String scenario, Consumer<OsmNetworkReaderSettings> configure) {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(scenario + ".osm").toString(), CountryNames.AUSTRALIA);
    configure.accept(reader.getSettings());
    return reader;
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
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

  /**
   * A vehicle signal for westbound traffic stands between the carriageways, between 2 and 1: once consolidated, that
   * segment is an internal segment with a signal, and the roads are otherwise plain. A footway runs from 1 to 2 north
   * of the carriageway, and a cycleway crosses it at 30 with lights. No road or rail passes 30, so it is no
   * intersection: no traffic it controls has an approach. The footway is a short connection between 1 and 2, so 30
   * joins, and the footway segments into 30 become internal segments with a signal, for its lights
   * <pre>
   *               33    34
   *                 \  /          cycleway 33-30-34
   *        31 ~~~~~~ 30 ~~~~~~ 32   footway 1-31-30-32-2, about 8 m north of the carriageway, 31 m long; 31 and 32
   *        ~                    ~   are shape points
   *        1 <------ 21 <------ 2   21: the vehicle signal for westbound traffic
   *        |                    ^
   *        v                    |   the divided roads crossing of this class
   *        3 -----------------> 4
   * </pre>
   */
  @Test
  public void stopLineBetweenTheCarriagewaysAndLightsOnAFootwayBecomeInternalSegmentsWithASignal() {
    var reader = readerOf("divided_roads_crossing_stop_line_between_carriageways", settings -> {
      settings.setConsolidateIntersections(true);
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var consolidated = (SpatialIntersection) layer.getIntersections().getFirst();
    assertTrue(consolidated.hasMemberNode(nodeByOsmId(layer, 30)));
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 1, 30), segment(layer, 2, 30)),
        Set.copyOf(consolidated.getInternalSegmentsWithSignal()));
    assertTrue(consolidated.getInternalSegments().containsAll(
        List.of(segment(layer, 1, 3), segment(layer, 4, 2), segment(layer, 2, 1), segment(layer, 3, 4))));
  }

  /** Collapsing pedestrians adds a centre node to the consolidated intersection, and its movements stay as they are */
  @Test
  public void collapsingPedestriansAddsACentreNode() {
    var reader = readerOf("divided_roads_crossing", settings -> {
      settings.setConsolidateIntersections(true);
      settings.getIntersectionCollapserSettings().setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);
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

  /** Collapsing the OSM mode foot collapses the mode it is mapped to, pedestrians, so a centre node is added as in
   * {@link #collapsingPedestriansAddsACentreNode()} */
  @Test
  public void collapsingTheOsmModeFootCollapsesPedestrians() {
    var reader = readerOf("divided_roads_crossing", settings -> {
      settings.setConsolidateIntersections(true);
      settings.getIntersectionCollapserSettings().setCollapsedOsmModes(OsmRoadModeTags.FOOT);
    });
    var consolidated = (SpatialIntersection) reader.read().getTransportLayers().getFirst().getIntersections()
        .getFirst();

    assertEquals(5, consolidated.getMemberNodes().size());
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

  /**
   * Nodes 1 and 4 are listed to keep in separate intersections, so the divided roads crossing does not become one
   * intersection holding both. Node 999 is listed too, but no layer has it, so it is recorded as not found
   * <pre>
   *     16 <---- 1 <-- 2 <---- 15      1 and 4 listed to keep in separate intersections, with 999
   *              |     ^
   *              v     |
   *     17 ----> 3 --> 4 ----> 18      the divided roads crossing of this class
   * </pre>
   */
  @Test
  public void nodesListedToKeepApartEndUpInSeparateIntersections() {
    var reader = readerOf("divided_roads_crossing", settings -> {
      settings.setConsolidateIntersections(true);
      settings.addOsmNodeIdsToKeepInSeparateIntersections(1, 4, 999);
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertNotSame(layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 1)),
        layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 4)));
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();
    assertEquals(List.of("999"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.NODE_LISTED_TO_JOIN_OR_KEEP_APART_NOT_FOUND));
  }

  /**
   * Nodes 2 and 3 are listed to join, but the divided roads crossing joins them into one intersection anyway, so there
   * is nothing to join and the crossing is one intersection as without the list
   * <pre>
   *     16 <---- 1 <-- 2 <---- 15      2 and 3 listed to join
   *              |     ^
   *              v     |
   *     17 ----> 3 --> 4 ----> 18      the divided roads crossing of this class
   * </pre>
   */
  @Test
  public void nodesListedToJoinThatAreInOneIntersectionAlreadyChangeNothing() {
    var reader = readerOf("divided_roads_crossing", settings -> {
      settings.setConsolidateIntersections(true);
      settings.addOsmNodeIdsToJoinIntoOneIntersection(2, 3);
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
  }

  /**
   * Node 1 is excluded from collapse, so collapsing pedestrians leaves the consolidated intersection as it is, with its
   * four member nodes and no centre node, see {@link #collapsingPedestriansAddsACentreNode()}
   * <pre>
   *     16 <---- 1 <-- 2 <---- 15      1 excluded from collapse
   *              |     ^
   *              v     |
   *     17 ----> 3 --> 4 ----> 18      the divided roads crossing of this class
   * </pre>
   */
  @Test
  public void nodeExcludedFromCollapseKeepsItsIntersectionFromCollapsing() {
    var reader = readerOf("divided_roads_crossing", settings -> {
      settings.setConsolidateIntersections(true);
      settings.getIntersectionCollapserSettings().setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);
      settings.getIntersectionCollapserSettings().addOsmNodeIdsExcludedFromCollapse(1);
    });
    var consolidated = (SpatialIntersection) reader.read().getTransportLayers().getFirst().getIntersections()
        .getFirst();

    assertEquals(4, consolidated.getMemberNodes().size());
  }

  /**
   * In this scenario the signalised crossing 31 lies about 10 m along the road from the signalised junction 1. With
   * footways parsed, 31 is a crossing intersection of its own. With the joining reach at 5 m, the crossing rules leave
   * it as it is. Nodes 1 and 31 are listed to join, so the two are joined all the same
   * <pre>
   *                     21    8
   *                     :     |                    way 10: 2-31-1-3 primary, way 11: 8-1-9 secondary
   *   2 --------------- 31 -- 1 --------------- 3  way 12: 20-31-21 footway, crossing the road at 31
   *                     :     |                    1 and 31 listed to join, joining reach 5 m
   *                    20     9
   * </pre>
   */
  @Test
  public void crossingListedToJoinThatTheCrossingRulesLeaveIsJoined() {
    var reader = readerOf("crossing_near_signalised_junction", settings -> {
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
      settings.setConsolidateIntersections(true);
      settings.getIntersectionConsolidatorSettings().setMaximumJoiningReachMeters(5);
      settings.addOsmNodeIdsToJoinIntoOneIntersection(1, 31);
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();
    assertTrue(diagnostics.getEntityIds(OsmPlanitEntityIssue.CROSSING_JOINED_TO_JUNCTION).isEmpty());
  }
}

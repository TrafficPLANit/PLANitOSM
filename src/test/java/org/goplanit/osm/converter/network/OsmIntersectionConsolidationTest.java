package org.goplanit.osm.converter.network;

import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.goplanit.algorithms.intersection.GreenRatioAlgorithmType;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.intersection.CapacityFactorOrigin;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionSignalSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.SpatialIntersection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.goplanit.osm.converter.network.OsmTrafficSignalScenarioTestUtils.createTrafficSignalScenarioReader;
import static org.goplanit.osm.test.OsmLayerTestUtils.nodeByOsmId;
import static org.goplanit.osm.test.OsmLayerTestUtils.onlyIntersection;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;

/**
 * Consolidating the intersections parsed from traffic signals, and computing the green ratios of their approaches, each
 * switched on in the intersection settings of the reader. The scenario is
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

  /** Without consolidation, each of the four signalised nodes is its own intersection */
  @Test
  public void eachNodeItsOwnIntersectionByDefault() {
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {});
    var layer = reader.read().getTransportLayers().getFirst();

    assertFalse(reader.getSettings().getIntersectionSettings().isConsolidateIntersections());
    assertEquals(4, layer.getIntersections().size());
  }

  /** With consolidation switched on, the four become one spatial intersection with four member nodes; the four it
   * replaces are each recorded once */
  @Test
  public void consolidationSwitchedOnJoinsTheDividedRoadsCrossing() {
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing",
        settings -> settings.getIntersectionSettings().setConsolidateIntersections(true));
    var layer = reader.read().getTransportLayers().getFirst();

    var consolidated = (SpatialIntersection) onlyIntersection(layer);
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
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing_stop_line_between_carriageways",
        settings -> {
          settings.getIntersectionSettings().setConsolidateIntersections(true);
          settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
          settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
        });
    var layer = reader.read().getTransportLayers().getFirst();

    var consolidated = (SpatialIntersection) onlyIntersection(layer);
    assertTrue(consolidated.hasMemberNode(nodeByOsmId(layer, 30)));
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 1, 30), segment(layer, 2, 30)),
        Set.copyOf(consolidated.getInternalSegmentsWithSignal()));
    assertTrue(consolidated.getInternalSegments().containsAll(
        List.of(segment(layer, 1, 3), segment(layer, 4, 2), segment(layer, 2, 1), segment(layer, 3, 4))));
  }

  /** Collapsing pedestrians adds a centre node to the consolidated intersection, and its movements stay as they are */
  @Test
  public void collapsingPedestriansAddsACentreNode() {
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
      var intersectionSettings = settings.getIntersectionSettings();
      intersectionSettings.setConsolidateIntersections(true);
      intersectionSettings.getIntersectionCollapserSettings().setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);
    });
    var network = reader.read();
    var consolidated = (SpatialIntersection) network.getTransportLayers().getFirst().getIntersections().getFirst();
    var notCollapsed = (SpatialIntersection) createTrafficSignalScenarioReader("divided_roads_crossing",
        settings -> settings.getIntersectionSettings().setConsolidateIntersections(true))
        .read().getTransportLayers().getFirst().getIntersections().getFirst();

    assertEquals(5, consolidated.getMemberNodes().size());
    assertEquals(notCollapsed.getMovements().size(), consolidated.getMovements().size());
  }

  /** Collapsing the OSM mode foot collapses the mode it is mapped to, pedestrians, so a centre node is added as in
   * {@link #collapsingPedestriansAddsACentreNode()} */
  @Test
  public void collapsingTheOsmModeFootCollapsesPedestrians() {
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
      settings.getIntersectionSettings().setConsolidateIntersections(true);
      settings.getIntersectionSettings().getIntersectionCollapserSettings().setCollapsedOsmModes(OsmRoadModeTags.FOOT);
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
      createTrafficSignalScenarioReader("divided_roads_crossing", settings -> settings.activateIntersectionParser(false))
          .read();
      int warningsWithoutConsolidation = counter.warnings;
      counter.warnings = 0;

      var layer = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
        settings.activateIntersectionParser(false);
        settings.getIntersectionSettings().setConsolidateIntersections(true);
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
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
      settings.getIntersectionSettings().setConsolidateIntersections(true);
      settings.getIntersectionSettings().addOsmNodeIdsToKeepInSeparateIntersections(1, 4, 999);
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
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
      settings.getIntersectionSettings().setConsolidateIntersections(true);
      settings.getIntersectionSettings().addOsmNodeIdsToJoinIntoOneIntersection(2, 3);
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
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
      var intersectionSettings = settings.getIntersectionSettings();
      intersectionSettings.setConsolidateIntersections(true);
      intersectionSettings.getIntersectionCollapserSettings().setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);
      intersectionSettings.getIntersectionCollapserSettings().addOsmNodeIdsExcludedFromCollapse(1);
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
    var reader = createTrafficSignalScenarioReader("crossing_near_signalised_junction", settings -> {
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
      settings.getIntersectionSettings().setConsolidateIntersections(true);
      settings.getIntersectionSettings().getIntersectionConsolidatorSettings().setMaximumJoiningReachMeters(5);
      settings.getIntersectionSettings().addOsmNodeIdsToJoinIntoOneIntersection(1, 31);
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();
    assertTrue(diagnostics.getEntityIds(OsmPlanitEntityIssue.CROSSING_JOINED_TO_JUNCTION).isEmpty());
  }

  /**
   * With consolidation and green ratios on, the four approaches of the consolidated junction share its usable green
   * time: two phases of opposite approaches, a 90 s cycle and 5 s lost at each phase change give each approach
   * (90 - 10) / 90 / 2, about 0.44
   */
  @Test
  public void greenRatiosOfTheConsolidatedJunctionShareItsUsableGreenTime() {
    var layer = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
      settings.getIntersectionSettings().setConsolidateIntersections(true);
      settings.getIntersectionSettings().setComputeGreenRatiosAndSaturationFlowFactors(true);
      settings.getIntersectionSettings().getGreenRatioAndSaturationFlowFactorSettings()
          .setAlgorithmType(GreenRatioAlgorithmType.EQUAL_SPLIT);
    }).read().getTransportLayers().getFirst();

    var consolidated = layer.getIntersections().getFirst();
    assertEquals(4, consolidated.getApproaches().size());
    for (var approach : consolidated.getApproaches()) {
      var withSignal = (IntersectionSignalSegment) approach;
      assertEquals(CapacityFactorOrigin.COMPUTED, withSignal.getGreenRatioOrigin());
      assertEquals(80.0 / 90 / 2, withSignal.getGreenRatio(), 1e-9);
    }
  }

  /**
   * With green ratios on and consolidation off, each of the four parsed intersections gets values of its own, and one
   * warning more is logged than without green ratios: a junction parsed as several intersections is timed as several
   * junctions. Each node has two approaches at right angles, the road from outside and the carriageway from the next
   * node, so two phases and about 0.44 each, and traffic through the junction meets two reduced approaches
   */
  @Test
  public void greenRatiosWithoutConsolidationAreComputedPerParsedIntersectionWithOneWarning() {
    var counter = new WarningCounter();
    var logger = Logger.getLogger(OsmNetworkReader.class.getCanonicalName());
    logger.addHandler(counter);
    try {
      createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {}).read();
      int warningsWithoutGreenRatios = counter.warnings;
      counter.warnings = 0;

      var layer = createTrafficSignalScenarioReader("divided_roads_crossing", settings -> {
        settings.getIntersectionSettings().setComputeGreenRatiosAndSaturationFlowFactors(true);
        settings.getIntersectionSettings().getGreenRatioAndSaturationFlowFactorSettings()
            .setAlgorithmType(GreenRatioAlgorithmType.EQUAL_SPLIT);
      }).read().getTransportLayers().getFirst();

      assertEquals(4, layer.getIntersections().size());
      for (var intersection : layer.getIntersections()) {
        assertEquals(2, intersection.getApproaches().size());
        for (var approach : intersection.getApproaches()) {
          var withSignal = (IntersectionSignalSegment) approach;
          assertEquals(CapacityFactorOrigin.COMPUTED, withSignal.getGreenRatioOrigin());
          assertEquals(80.0 / 90 / 2, withSignal.getGreenRatio(), 1e-9);
        }
      }
      assertEquals(warningsWithoutGreenRatios + 1, counter.warnings);
    } finally {
      logger.removeHandler(counter);
    }
  }

  /** With default settings no green ratio or saturation flow factor is computed, so no approach has a value */
  @Test
  public void noGreenRatiosByDefault() {
    var reader = createTrafficSignalScenarioReader("divided_roads_crossing",
        settings -> settings.getIntersectionSettings().setConsolidateIntersections(true));
    var layer = reader.read().getTransportLayers().getFirst();

    assertFalse(reader.getSettings().getIntersectionSettings().isComputeGreenRatiosAndSaturationFlowFactors());
    for (var approach : layer.getIntersections().getFirst().getApproaches()) {
      assertNull(((IntersectionSignalSegment) approach).getGreenRatioOrigin());
      assertNull(approach.getSaturationFlowFactorOrigin());
    }
  }
}

package org.goplanit.osm.converter.network;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.diagnostics.OsmNearestJunctionBucket;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcomeGroup;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.macroscopic.intersection.SpatialIntersection;
import org.goplanit.utils.network.layer.physical.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome.*;
import static org.goplanit.osm.converter.network.OsmTrafficSignalScenarioTestUtils.BREAK_LINKS_AT_ISOLATED_SIGNALS;
import static org.goplanit.osm.converter.network.OsmTrafficSignalScenarioTestUtils.breakLinksAtIsolatedSignalsParsingOnlyWithin;
import static org.goplanit.osm.converter.network.OsmTrafficSignalScenarioTestUtils.parseTrafficSignalScenario;
import static org.goplanit.osm.test.OsmLayerTestUtils.nodeByOsmId;
import static org.goplanit.osm.test.OsmLayerTestUtils.onlyIntersection;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;

/**
 * What the traffic signal integrator records about the signals it integrates, beyond the intersections it creates
 */
public class OsmTrafficSignalIntegratorTest {

  /**
   * The segments entering a node that a motorised road or rail mode may use: the approaches a signal on the node gives
   *
   * @param node to find them at
   * @return the segments
   */
  private static Set<MacroscopicLinkSegment> segmentsForMotorisedRoadOrRailEntering(Node node) {
    return node.streamEntrySegments().map(MacroscopicLinkSegment.class::cast)
        .filter(segment -> segment.isAnyModeAllowed(Mode::isMotorisedRoadOrRailMode))
        .collect(Collectors.toSet());
  }

  /**
   * Reset the id generation
   */
  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  /**
   * Reset the id generation
   */
  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** An approach cut short by breaking its link at an isolated crossing stays with the junction and its signal: the
   * new link segment arriving at the junction is its approach, and the junction lists only its own signal. The link
   * segment left before the crossing is an approach of the crossing intersection, which lists only the crossing signal
   * (scenario and drawing: OsmTrafficSignalsTest, crossingBreaksJunctionApproach) */
  @Test
  public void approachBrokenAtCrossingStaysWithJunctionSignal() {
    var layer =
        parseTrafficSignalScenario("crossing_breaks_junction_approach", BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();

    var junction = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 1));
    assertTrue(junction.getApproachSegments().contains(segment(layer, 31, 1)));
    assertEquals(Set.of("1"), Set.of(junction.getSplitExternalId()));
    var crossing = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 31));
    assertTrue(crossing.getApproachSegments().contains(segment(layer, 2, 31)));
    assertEquals(Set.of("31"), Set.of(crossing.getSplitExternalId()));
  }

  /** What became of each signal is recorded per outcome with the signal's id: the vehicle signal on the junction's
   * centre, and the crossing signal 10 m before it, footways not parsed, part of that junction (scenario and drawing:
   * OsmTrafficSignalsTest, crossingNearSignalisedJunction) */
  @Test
  public void outcomeRecordedPerSignal() {
    var parsed = parseTrafficSignalScenario("crossing_near_signalised_junction", BREAK_LINKS_AT_ISOLATED_SIGNALS);

    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_ON_JUNCTION_NODE));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_PART_OF_SIGNALISED_JUNCTION));
    assertTrue(parsed.signalIds(ISOLATED_CROSSING_OWN_INTERSECTION).isEmpty());
  }

  /** A vehicle signal whose direction is none of forward, backward or both is handled as untagged, applying to the
   * direction whose first target is nearest only, and recorded */
  @Test
  public void signalWithUnrecognisedDirectionTakenAsUntagged() {
    /*
     *                                         7
     *                                         |
     *     2 ------------ 4 ---------- 1 ----- 6 ------ 3      way 10: 2-4-1-6-3 primary, two-way (west to east in OSM)
     *                    |    ~30 m     ~10 m |               way 11: 4-9 residential, two-way (side street)
     *                    9                    8               way 12: 7-6-8 primary, two-way
     *
     *   1: highway=traffic_signals, traffic_signals:direction=140 (a compass bearing), a shape node of way 10
     *   expected: as untagged, the nearer junction 6 gets approach 4->6; side street junction 4, within the 50 m default
     *   behind it, gets nothing
     */
    var parsed = parseTrafficSignalScenario("signal_with_unrecognised_direction");
    var layer = parsed.layer();

    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 4, 6)), junction.getApproachSegments());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_DIRECTION_UNRECOGNISED));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN));
  }

  /** A signal carrying both kinds has one outcome as a vehicle signal and one as a crossing signal, so each group of
   * outcomes adds up to the signals of its kind (scenario and drawing: OsmTrafficSignalsTest, bothKindsOnOneNode) */
  @Test
  public void signalOfBothKindsHasOneOutcomePerKind() {
    var diagnostics = parseTrafficSignalScenario("both_kinds_on_centre").networkReaderData().getParseDiagnostics();

    assertEquals(1, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL));
    assertEquals(1, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.CROSSING_SIGNAL));
    assertEquals(0, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.SIGNAL_OF_ANY_KIND));
  }

  /** What is noted alongside a signal's outcome is kept apart from the outcomes, so a vehicle signal with notes still
   * counts once among the vehicle signals (scenario and drawing: signalWithUnrecognisedDirectionTakenAsUntagged) */
  @Test
  public void notesKeptApartFromOutcomes() {
    var diagnostics =
        parseTrafficSignalScenario("signal_with_unrecognised_direction").networkReaderData().getParseDiagnostics();

    assertEquals(1, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL));
    assertEquals(2, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME));
  }

  /** A vehicle signal without a junction within reach is recorded with how far along the road its nearest junction
   * is (scenario and drawing: OsmTrafficSignalsTest, vehicleSignalFarFromAnyJunctionOnShapeNode, junction 6 about 148 m
   * from signal 1) */
  @Test
  public void nearestJunctionRecordedForVehicleSignalWithoutJunction() {
    var parsed = parseTrafficSignalScenario("signal_far_from_junction_on_shape_node");

    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(List.of("1"),
        parsed.networkReaderData().getParseDiagnostics().getSignalIds(OsmNearestJunctionBucket.FROM_100_TO_150_M));
  }

  /**
   * A vehicle signal without a junction ahead near the edge of the bounding area is ignored rather than made a crossing
   */
  @Test
  public void vehicleSignalWithoutJunctionAtEdgeOfParsedAreaIgnored() {
    /*
     *          edge          7
     *            |           |
     *        2---|--1--------6-----3      signal 1 on a shape node of way 10, junction 6 about 148 m along it,
     *            |           |            the bounding box's west edge about 37 m from signal 1, node 2 just outside
     *                        8
     *
     *   the road ends at node 2, outside the bounding box, about 46 m from signal 1: with the maximum distance from a
     *   vehicle signal to its junction lowered to 40 m it is out of reach, so no junction is found in either direction
     */
    var parsed = parseTrafficSignalScenario("signal_far_from_junction_on_shape_node",
        breakLinksAtIsolatedSignalsParsingOnlyWithin(OsmBoundary.of(151.2081, 151.2115, -33.8715, -33.8685)).andThen(
            settings -> settings.getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(40)));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(0, parsed.networkReaderData().getPlanitEntityDiagnostics().getIntersectionsCreated());
  }

  /**
   * A vehicle signal without a junction ahead joins the signalised crossing ahead of it within reach, rather than
   * becoming a crossing of its own
   */
  @Test
  public void vehicleSignalWithoutJunctionJoinsCrossingAhead() {
    /*
     *   2 ------> 1 --> 31 ------> 3     one-way way 10, vehicle signal 1 about 18 m before signalised crossing 31,
     *                                    no junction anywhere
     */
    var parsed =
        parseTrafficSignalScenario("vehicle_signal_before_mid_block_crossing", BREAK_LINKS_AT_ISOLATED_SIGNALS);
    var layer = parsed.layer();

    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(1, parsed.networkReaderData().getPlanitEntityDiagnostics().getIntersectionsCreated());
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(segment(layer, 2, 31)), crossing.getApproachSegments());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));
  }

  /**
   * A signalised crossing with no junction in reach is ignored when it lies outside the parsed area, or inside within
   * the crossing distance of its edge, since a junction it belongs to may be cut off there. It gets no intersection, no
   * link is broken, and the vehicle signal stopping at it is ignored with it. Farther inside it is isolated as before
   */
  @Test
  public void crossingWithoutJunctionAtEdgeOfParsedAreaIgnored() {
    /*
     *      edge (a)            edge (b)
     *         |                   |
     *   2 ------> 1 -> 31 ------- | -----> 3     one-way way 10, vehicle signal 1 about 18 m before signalised
     *         |                   |              crossing 31, no junction anywhere
     *
     *   (a) the bounding area's west edge about 20 m before crossing 31, which lies inside, within the 30 m crossing
     *       distance of the edge
     *   (b) the west edge about 4 m past crossing 31, which lies outside; the way is kept whole as node 3 is inside
     *   (c) the west edge about 35 m before crossing 31: isolated, its own intersection, joined by signal 1
     */
    for (double westEdge : new double[] {151.20978, 151.2104}) {
      var parsed = parseTrafficSignalScenario("vehicle_signal_before_mid_block_crossing",
          breakLinksAtIsolatedSignalsParsingOnlyWithin(OsmBoundary.of(westEdge, 151.2120, -33.8715, -33.8685)));

      assertEquals(Set.of("31"), parsed.signalIds(CROSSING_AT_EDGE_OF_PARSED_AREA));
      assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
      assertTrue(parsed.layer().getIntersections().isEmpty());
      assertEquals(1, parsed.layer().getLinks().size());
    }

    var parsed = parseTrafficSignalScenario("vehicle_signal_before_mid_block_crossing",
        breakLinksAtIsolatedSignalsParsingOnlyWithin(OsmBoundary.of(151.20962, 151.2120, -33.8715, -33.8685)));

    assertEquals(Set.of("31"), parsed.signalIds(ISOLATED_CROSSING_OWN_INTERSECTION));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    var crossing = onlyIntersection(parsed.layer());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));
  }

  /**
   * The stop line of an isolated crossing at the edge of the parsed area is ignored with that crossing, rather than
   * joining it or being assigned past it to the junction
   */
  @Test
  public void stopLineOfCrossingAtEdgeOfParsedAreaIgnored() {
    /*
     *      edge                         7
     *        |                          |
     *   2 ---|---> 1 -> 31 -----------> 6 ------> 3     one-way way 10, signal 1 (forward) about 9 m before signalised
     *        |                          |               crossing 31, which lies about 37 m before junction 6 with
     *                                   8               side street way 11
     *
     *   the bounding area's west edge about 18 m before crossing 31, which lies inside, within the 30 m crossing
     *   distance of the edge; junction 6 lies about 55 m inside
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_isolated_crossing",
        breakLinksAtIsolatedSignalsParsingOnlyWithin(OsmBoundary.of(151.2091, 151.2120, -33.8715, -33.8685)));

    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_AT_EDGE_OF_PARSED_AREA));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(parsed.layer().getIntersections().isEmpty());
  }

  /**
   * A signalised crossing at the edge of the parsed area next to a signalised junction is still part of that junction
   */
  @Test
  public void crossingAtEdgeOfParsedAreaNextToSignalisedJunctionPartOfIt() {
    /*
     *              edge  8
     *               |    |
     *   2 ------- 31|--- 1 ------- 3     two-way primary way 10, signalised junction 1 with secondary way 11 (8-1-9),
     *               |    |               signalised crossing 31 about 10 m before it, footways not parsed
     *                    9
     *
     *   the bounding area's west edge about 1 m past crossing 31, which lies outside; junction 1 lies about 9 m inside
     */
    var parsed = parseTrafficSignalScenario("crossing_near_signalised_junction",
        breakLinksAtIsolatedSignalsParsingOnlyWithin(OsmBoundary.of(151.2099, 151.2120, -33.8715, -33.8685)));

    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_PART_OF_SIGNALISED_JUNCTION));
    assertEquals(Set.of(), parsed.signalIds(CROSSING_AT_EDGE_OF_PARSED_AREA));
  }

  /**
   * A vehicle signal without a junction in reach is ignored when a crossing ignored at the edge of the parsed area lies
   * within the crossing distance of it, also when the signal itself lies farther from the edge than a junction's reach
   */
  @Test
  public void vehicleSignalNearCrossingAtEdgeOfParsedAreaIgnored() {
    /*
     *                           edge
     *                             |
     *   2 ------> 1 -> 31 ------- | ---> 3     one-way way 10, vehicle signal 1 about 18 m before signalised crossing
     *                             |            31, no junction anywhere; road end 3 outside, out of reach
     *
     *   the maximum distance from a vehicle signal to its junction lowered to 40 m. The bounding area's east edge lies
     *   about 25 m past crossing 31, within the 30 m crossing distance, and about 43 m past signal 1, beyond the 40 m
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_mid_block_crossing",
        breakLinksAtIsolatedSignalsParsingOnlyWithin(OsmBoundary.of(151.2060, 151.21027, -33.8715, -33.8685)).andThen(
            settings -> settings.getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(40)));

    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_AT_EDGE_OF_PARSED_AREA));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertTrue(parsed.layer().getIntersections().isEmpty());
  }

  /**
   * A vehicle signal is the stop line of an isolated crossing also when the crossing lies past a junction the signal
   * passes over, because only a service lane joins there
   */
  @Test
  public void vehicleSignalStopsAtIsolatedCrossingPastServiceLane() {
    /*
     *                  9                 7
     *                  |                 |
     *   2 ------- 1 -> 4 - 31 ---------- 5 -------- 3     way 10 primary, two-way; signal 1 (forward); service lane
     *               ~10 m ~13 m    ~48 m |                way 11 (9-4); signalised crossing 31; primary way 12 (7-5-8)
     *                                    8
     *
     *   the signal resolves to junction 5, passing service lane 4; crossing 31 lies between, 35 m
     *   before junction 5, so it is isolated and the signal is its stop line: no junction gets it
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_side_street_and_isolated_crossing");
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(parsed.layer().getIntersections().isEmpty());
  }

  /**
   * A junction where only a service lane joins is passed over for the next junction along the road, unless the lane
   * crosses the median to the other carriageway of the same road, a way of the same name, where the junction has a
   * vehicle signal of its own: the two carriageways make one junction there, and a vehicle signal before it stops there
   */
  @Test
  public void junctionAcrossMedianFromSignalisedJunctionIsNotPassedOver() {
    /*
     *              12   2
     *              ^    |
     *              |    1          way 10 "Main Road", one-way south: signal 1 (forward) ~16 m before node 4
     *              |    |
     *              5 -- 4          service way 30 across the median, ~14 m
     *              ^    |
     *              11   |          way 20 "Main Road", one-way north: signal 11 (forward) ~13 m before node 5
     *              |    6 ---- 7   side street way 40 at junction 6, ~24 m past node 4
     *              13   3
     *
     *   second file: node 11 is no signal
     */
    var layer = parseTrafficSignalScenario("vehicle_signal_before_median_crossing_to_signalised_carriageway").layer();
    assertNull(layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 6)));
    var acrossMedian = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 4));
    assertEquals(List.of(segment(layer, 2, 4)), acrossMedian.getApproachSegments());

    var parsed = parseTrafficSignalScenario("vehicle_signal_before_median_crossing_to_unsignalised_carriageway");
    var unsignalisedLayer = parsed.layer();
    assertEquals(Set.of("1"), parsed.signalIds(LOWEST_ORDER_ROAD_JUNCTION_PASSED));
    var sideStreet = onlyIntersection(unsignalisedLayer);
    assertEquals(List.of(nodeByOsmId(unsignalisedLayer, 6)), sideStreet.getMemberNodes());
    assertEquals(List.of(segment(unsignalisedLayer, 4, 6)), sideStreet.getApproachSegments());
  }

  /**
   * A vehicle signal before a signalised crossing with no junction within the crossing distance beyond it is that
   * crossing's stop line, rather than being assigned past it to the junction; with the junction within that distance
   * beyond the crossing, it is assigned to the junction as before, unless only a lowest order road such as a service
   * lane joins there
   */
  @Test
  public void vehicleSignalStopsAtIsolatedCrossing() {
    /*
     *                                  7
     *                                  |
     *   2 ------> 1 -> 31 -----------> 6 ------> 3     one-way way 10, signal 1 (forward) ~9 m before signalised
     *                                  |               crossing 31, ~37 m before junction 6 with side street way 11;
     *                                  8               in the second file crossing 31 lies ~18 m before junction 6
     *
     *   2 ------> 1 -> 31 -> 5 ------------------> 3     third file: service lane way 12 (5-9) joins ~9 m after
     *                        |                           crossing 31, no other junction; fourth file: the lane
     *                        9                           carries its own signal 10, ~6 m before 5
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_isolated_crossing");
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(parsed.layer().getIntersections().isEmpty());

    /* breaking links at isolated signals on: the stop line joins the crossing's own intersection */
    var layer = parseTrafficSignalScenario("vehicle_signal_before_isolated_crossing", BREAK_LINKS_AT_ISOLATED_SIGNALS)
        .layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    /* crossing within the crossing distance of a junction that shows no other signal: still the crossing's stop line,
     * and the junction gets no intersection */
    parsed = parseTrafficSignalScenario("vehicle_signal_before_crossing_near_junction");
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(parsed.layer().getIntersections().isEmpty());

    /* only a service lane within the crossing distance beyond the crossing: still the crossing's stop line */
    parsed = parseTrafficSignalScenario("vehicle_signal_before_crossing_near_service_lane");
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_ISOLATED_PAST_JUNCTION_WITHOUT_OTHER_SIGNAL));

    /* the service lane with a signal of its own: a junction like any other, claiming the crossing */
    parsed = parseTrafficSignalScenario("vehicle_signal_before_crossing_near_signalised_service_lane");
    layer = parsed.layer();
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 5)), junction.getMemberNodes());
    assertEquals(Set.of("1", "10"), Set.of(junction.getSplitExternalId()));
  }

  /**
   * A crossing within the crossing distance of a junction is part of it only when the junction shows a signal other
   * than those along the crossing's own road; then the vehicle signal before the crossing is the junction's stop line.
   * The junction shows one when a signalised crossing arrives on another of its entries, or when the other carriageway
   * of a divided road has a stop line at a junction node within the joining reach, reached straight across. It does
   * not when the only other stop line is that of the next junction along the crossing's own road, beyond the crossing
   * distance (base scenario: vehicleSignalStopsAtIsolatedCrossing, second file)
   */
  @Test
  public void crossingNearJunctionIsPartOfItOnlyWhenTheJunctionShowsAnotherSignal() {
    /*
     *                                  7
     *                                  |
     *                                  32               first file: signalised crossing 32 on the side street,
     *                                  |                ~10 m north of junction 6
     *   2 ------> 1 -> 31 -----------> 6 ------> 3
     *                                  |
     *                                  8
     *
     *                                  7
     *                                  |
     *   2 ------> 1 -> 31 -----------> 6 ------> 3     second file: Main Street divided into ways 10 and 12, ~15 m
     *                                  |                apart, Side Street crossing both; signal 41 (forward) ~9 m
     *   4 <--------------------------- 16 <-- 41 - 5    before junction 16
     *                                  |
     *                                  8
     *
     *                                  7      9
     *                                  |      |
     *   2 ------> 1 -> 31 -----------> 6 -42-> 19 --> 3     third file: junction 19 with Other Street ~37 m past
     *                                  |      |             junction 6, signal 42 (forward) ~5 m before it
     *                                  8      20
     */
    var parsed =
        parseTrafficSignalScenario("vehicle_signal_before_crossing_near_junction_with_crossing_on_side_street");
    var layer = parsed.layer();
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of(nodeByOsmId(layer, 6)), onlyIntersection(layer).getMemberNodes());
    assertEquals(Set.of("31", "32"), parsed.signalIds(CROSSING_PART_OF_SIGNALISED_JUNCTION));

    parsed = parseTrafficSignalScenario("vehicle_signal_before_crossing_near_divided_road_junction");
    layer = parsed.layer();
    assertEquals(Set.of("1", "41"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(Set.of(nodeByOsmId(layer, 6), nodeByOsmId(layer, 16)), layer.getIntersections().stream()
        .flatMap(intersection -> intersection.getMemberNodes().stream()).collect(Collectors.toSet()));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_PART_OF_SIGNALISED_JUNCTION));

    parsed = parseTrafficSignalScenario("vehicle_signal_before_crossing_near_junction_and_next_signalised_junction");
    layer = parsed.layer();
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(Set.of("42"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of(nodeByOsmId(layer, 19)), onlyIntersection(layer).getMemberNodes());
  }

  /**
   * A vehicle signal tagged with direction both on a road, but not on a junction node, is a crossing signal, noted to
   * check in OSM: the junction it would otherwise reach gets no intersection, and with breaking links at isolated
   * signals on, it is a crossing intersection of its own, next to the signalised crossing beside it. On a junction
   * node it is a vehicle signal like any other, not noted
   */
  @Test
  public void vehicleSignalForBothDirectionsBetweenJunctionsIsCrossingSignal() {
    /*
     *                                     7
     *                                     |
     *   2 ------------- 31 - 1 ---------- 6 ------------- 3     two-way way 10; signal 1 (both) ~14 m before
     *                                     |                     junction 6 with side street way 11, crossing 31
     *                                     8                     ~3 m from it, away from the junction
     *
     *   2 ----------------------------- (6) ------------- 3     second file: signal (both) on junction node 6
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_both_directions_beside_crossing");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BOTH_DIRECTIONS_TAKEN_AS_CROSSING));
    assertEquals(Set.of("1", "31"), parsed.signalIds(ISOLATED_CROSSING_NO_LINK_BROKEN));

    var layer = parseTrafficSignalScenario("vehicle_signal_both_directions_beside_crossing",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    assertEquals(Set.of(nodeByOsmId(layer, 1), nodeByOsmId(layer, 31)), layer.getIntersections().stream()
        .flatMap(intersection -> intersection.getMemberNodes().stream()).collect(Collectors.toSet()));
    assertTrue(layer.getIntersections().stream().allMatch(intersection -> intersection.isCrossingOnly()));

    parsed = parseTrafficSignalScenario("vehicle_signal_both_directions_on_junction_node");
    assertEquals(Set.of("6"), parsed.signalIds(VEHICLE_SIGNAL_ON_JUNCTION_NODE));
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_BOTH_DIRECTIONS_TAKEN_AS_CROSSING));
  }

  /**
   * A traffic signal direction overridden in the settings replaces the tagged one, and gives an untagged signal one:
   * the signal is then a stop line of the junction in the direction overridden only. A node given a direction that no
   * layer has as a traffic signal is recorded once as not found (scenarios and drawings: OsmTrafficSignalsTest,
   * directionTaggedSignalWithSideStreetBehind and signalWithoutDirectionPrefersHigherCapacityJunctionOnNearTie)
   */
  @Test
  public void signalDirectionOverriddenInTheSettingsReplacesTheTaggedOne() {
    /*
     *                                         7
     *                                         |
     *     2 ------------ 4 ---------- 1 ----- 6 ------ 3      signal 1 tagged forward, overridden backward:
     *                    |    ~30 m     ~10 m |               junction 4 behind it gets approach 6->4, junction 6
     *                    9                    8               nothing; directions also overridden for junction
     *                                                         node 6, no signal, and for 999, no node
     *
     *                   9        7
     *                   |        |
     *     2 ----------- 4 -- 1 --- 6 --------- 3      second file: signal 1 untagged, overridden backward:
     *                     ~12 m ~18.5 m               junction 4 gets approach 6->4, junction 6 nothing
     *                            8
     */
    var parsed = parseTrafficSignalScenario("direction_tagged_signal_with_side_street_behind", settings -> {
      var intersectionSettings = settings.getIntersectionSettings();
      intersectionSettings.overrideTrafficSignalDirection(1, OsmTrafficSignalDirection.BACKWARD);
      intersectionSettings.overrideTrafficSignalDirection(6, OsmTrafficSignalDirection.FORWARD);
      intersectionSettings.overrideTrafficSignalDirection(999, OsmTrafficSignalDirection.FORWARD);
    });
    var layer = parsed.layer();
    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 4)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 6, 4)), junction.getApproachSegments());
    assertEquals(Set.of("6", "999"), parsed.entityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));

    layer = parseTrafficSignalScenario("signal_without_direction_near_tie", settings -> settings
        .getIntersectionSettings().overrideTrafficSignalDirection(1, OsmTrafficSignalDirection.BACKWARD)).layer();
    junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 4)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 6, 4)), junction.getApproachSegments());
  }

  /**
   * A traffic signal kind overridden in the settings replaces what the node's tags say: a vehicle signal overridden as
   * a crossing signal is an isolated crossing; a signal overridden as not a signal gives no intersection; a node
   * without signal tags overridden as a vehicle signal makes its junction signalised; a signal tagged as both kinds
   * overridden as a crossing signal is a crossing only. A node overridden that no layer has is recorded once as not
   * found
   */
  @Test
  public void signalKindOverriddenInTheSettingsReplacesTheTaggedOne() {
    /*
     *                                    7
     *                                    |
     *     2 --------------------- 1 ---- 6 ------- 3      first file: untagged vehicle signal 1 ~12 m before
     *                                    |                junction 6, overridden as a crossing signal, links
     *                                    8                broken at isolated crossings; also overridden: 999, no node
     *
     *                  4
     *                  |
     *     2 ---------- 1 ---------- 3      second file: signal on centre node 1, overridden as not a signal;
     *                  |                   third file: no signal tags on node 1, overridden as a vehicle signal;
     *                  5                   fourth file: vehicle and crossing signal on node 1, overridden as a
     *                                      crossing signal
     */
    var parsed = parseTrafficSignalScenario("signal_12m_before_junction", BREAK_LINKS_AT_ISOLATED_SIGNALS.andThen(
        settings -> {
          settings.getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.CROSSING_SIGNAL);
          settings.getIntersectionSettings().overrideTrafficSignalKind(999, OsmTrafficSignalKind.NOT_A_SIGNAL);
        }));
    var layer = parsed.layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 1)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of("999"), parsed.entityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));

    parsed = parseTrafficSignalScenario("four_arm_junction_signal_on_centre", settings -> settings
        .getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.NOT_A_SIGNAL));
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of(), parsed.entityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));

    layer = parseTrafficSignalScenario("junction_without_signals", settings -> settings
        .getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.VEHICLE_SIGNAL)).layer();
    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 1)), junction.getMemberNodes());
    assertEquals(Set.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(4, junction.getApproachSegments().size());

    layer = parseTrafficSignalScenario("both_kinds_on_centre", settings -> settings
        .getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.CROSSING_SIGNAL)).layer();
    assertEquals(Set.of(IntersectionType.CROSSING), onlyIntersection(layer).getTypes());
  }

  /**
   * A crossing node without signal tags that a vehicle signal reaches first along its link, within the crossing
   * distance, is a signalised crossing, noted to check in OSM: the vehicle signal is its stop line. Near a junction
   * that shows no other signal it is isolated, so the junction gets no intersection; with no junction ahead the signal
   * joins it as for any signalised crossing ahead
   */
  @Test
  public void crossingNodeRightAfterVehicleSignalIsSignalisedCrossing() {
    /*
     *                                     7
     *                                     |
     *   2 ------------> 1 -> 31 --------> 6 ------> 3     one-way way 10; signal 1 (forward) ~9 m before crossing node
     *                    ~9 m    ~18 m    |               31 (highway=crossing, no signal tags), side street way 11 at
     *                                     8               junction 6, no other signal
     *
     *   2 ------> 1 --> 31 ------> 3                      second file: one-way, signal 1 ~18 m before crossing node 31
     *                                                     without signal tags, no junction anywhere
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_crossing_node_near_junction");
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));
    assertTrue(parsed.layer().getIntersections().isEmpty());

    var layer = parseTrafficSignalScenario("vehicle_signal_before_crossing_node_near_junction",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    parsed =
        parseTrafficSignalScenario("vehicle_signal_before_mid_block_crossing_node", BREAK_LINKS_AT_ISOLATED_SIGNALS);
    layer = parsed.layer();
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));
    assertEquals(List.of(nodeByOsmId(layer, 31)), onlyIntersection(layer).getMemberNodes());
  }

  /**
   * A node where a footway, cycleway or similar way meets the road is a crossing node too, also without any crossing
   * tags, whether the way passes through it or ends there, and whether such ways are parsed or not. A vehicle signal
   * right before it marks it a signalised crossing, noted to check in OSM. An outline tagged area=yes, such as a
   * plaza's, is no such way
   */
  @Test
  public void footwayMeetingRoadRightAfterVehicleSignalMarksSignalisedCrossing() {
    /*
     *                        40                7
     *                        |                 |
     *   2 ----------> 1 ---> 31 -------------> 6 ------> 3     one-way way 10; signal 1 (forward) ~9 m before node 31,
     *                  ~9 m  |      ~18 m      |               where footway way 12 (40-31-41) crosses without crossing
     *                        41                8               tags; side street way 11 at junction 6, no other signal
     *
     *   second file: footway way 12 (40-31) ends on the road at 31
     *   third file: way 12 (40-31-41-42-40) is a plaza outline, highway=pedestrian and area=yes, through 31
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_untagged_footway_crossing_near_junction");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));

    var layer = parseTrafficSignalScenario("vehicle_signal_before_untagged_footway_crossing_near_junction",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    parsed = parseTrafficSignalScenario("vehicle_signal_before_untagged_footway_ending_near_junction");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));

    parsed = parseTrafficSignalScenario("vehicle_signal_before_plaza_outline_near_junction");
    layer = parsed.layer();
    assertEquals(List.of(nodeByOsmId(layer, 6)), onlyIntersection(layer).getMemberNodes());
    assertEquals(Set.of(), parsed.signalIds(CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));
  }

  /**
   * A signalised crossing whose road traffic faces a stop or give way sign between the crossing and the junction is
   * taken as not signalised: no intersection, also with breaking links at isolated signals on, and its outcome is
   * recorded to check in OSM. A sign facing the traffic leaving the junction does not count
   */
  @Test
  public void crossingSignalBeforeStopOrGiveWaySignIsNotSignalised() {
    /*
     *                  7
     *                  |
     *                  32         signalised crossing node 32 on side street way 11, ~15 m from junction 6
     *                  |
     *                  33         stop sign 33, ~5 m from junction 6, facing traffic heading south to it
     *                  |
     *   2 ------------ 6 ------------ 3     two-way primary way 10
     *                  |
     *                  8          second file: stop sign 33 faces traffic leaving the junction northwards
     */
    var parsed =
        parseTrafficSignalScenario("crossing_before_stop_sign_on_side_street", BREAK_LINKS_AT_ISOLATED_SIGNALS);
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("32"), parsed.signalIds(CROSSING_SIGNAL_BEFORE_STOP_OR_GIVE_WAY_SIGN));

    parsed = parseTrafficSignalScenario("crossing_before_stop_sign_facing_away", BREAK_LINKS_AT_ISOLATED_SIGNALS);
    assertEquals(List.of(nodeByOsmId(parsed.layer(), 32)), onlyIntersection(parsed.layer()).getMemberNodes());
    assertEquals(Set.of(), parsed.signalIds(CROSSING_SIGNAL_BEFORE_STOP_OR_GIVE_WAY_SIGN));
  }

  /**
   * A node tagged both a vehicle signal and a crossing stands on its own crossing, which counts like a crossing ahead
   * of the vehicle signal. Where the junction it reaches shows no other signal, that crossing is isolated and the
   * signal is the crossing's, so the junction is not signalised. Where the junction shows another signal, the vehicle
   * signal is the junction's stop line, as for a junction mapped with its signals at the stop lines
   */
  @Test
  public void vehicleAndCrossingSignalOnOneNodeIsCrossingWhenJunctionShowsNoOtherSignal() {
    /*
     *                                  7
     *                                  |
     *   2 ---------------------> 1 --> 6 ------> 3     one-way way 10; node 1 tagged both a vehicle signal (forward)
     *                             ~9 m |               and a crossing, ~9 m before junction 6 with side street way 11,
     *                                  8               no other signal
     *
     *                                  7
     *                                  |
     *                                  9               second file: side street signal 9 (forward), ~11 m before
     *                                  |               junction 6
     *   2 ---------------------> 1 --> 6 ------> 3
     *                                  |
     *                                  8
     */
    var parsed = parseTrafficSignalScenario("vehicle_and_crossing_signal_before_unsignalised_side_street");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));

    var layer = parseTrafficSignalScenario("vehicle_and_crossing_signal_before_unsignalised_side_street",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 1)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(List.of(segment(layer, 2, 1)), crossing.getApproachSegments());

    parsed = parseTrafficSignalScenario("vehicle_and_crossing_signal_before_signalised_side_street");
    layer = parsed.layer();
    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertTrue(junction.getApproachSegments().contains(segment(layer, 2, 6)));
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));
  }

  /**
   * Salvage attempt: a vehicle signal tagged with its direction, a few metres past an isolated crossing with no
   * crossing ahead of it before its junction, is taken as that crossing's signal, as one on the crossing's own node,
   * and noted to check in OSM. The junction ahead is then not signalised, and the crossing keeps its own stop line. A
   * signal without a direction tag is not salvaged: it stays the junction's stop line
   */
  @Test
  public void vehicleSignalJustPastIsolatedCrossingSalvagedAsItsSignal() {
    /*
     *                                         7
     *                                         |
     *   2 ------> 1 ----------> 31 -> 5 ----> 6 ------> 3     one-way way 10: signal 1 (forward) ~12 m before
     *                   ~12 m      ~4 m  ~18 m                signalised crossing 31, signal 5 (forward) ~4 m past
     *                                                         it, junction 6 with side street way 11 ~18 m on
     *
     *   second file: signal 5 without a direction tag
     */
    var parsed = parseTrafficSignalScenario("tagged_vehicle_signal_just_past_crossing_before_side_street");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("5"), parsed.signalIds(VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));
    assertEquals(Set.of("5"), parsed.signalIds(VEHICLE_SIGNAL_PAST_ISOLATED_CROSSING_SALVAGED_AS_ITS_SIGNAL));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));

    /* breaking links at isolated signals on: the crossing has its own intersection with its stop line */
    var layer = parseTrafficSignalScenario("tagged_vehicle_signal_just_past_crossing_before_side_street",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(List.of(segment(layer, 2, 31)), crossing.getApproachSegments());

    parsed = parseTrafficSignalScenario("untagged_vehicle_signal_just_past_crossing_before_side_street");
    assertEquals(Set.of("5"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_PAST_ISOLATED_CROSSING_SALVAGED_AS_ITS_SIGNAL));
    assertEquals(List.of(nodeByOsmId(parsed.layer(), 6)), onlyIntersection(parsed.layer()).getMemberNodes());
  }

  /**
   * A traffic signal on a barrier, such as a bollard or gate, holds traffic at the barrier, so it stands for a crossing
   * on its own node, like a node tagged both a vehicle signal and a crossing. Where the junction it reaches shows no
   * other signal, it is a crossing only; where the junction shows another signal, it stays the junction's stop line
   */
  @Test
  public void signalOnBarrierIsCrossingWhenJunctionShowsNoOtherSignal() {
    /*
     *                                  7
     *                                  |
     *   2 ------------------- 1 ------ 6 ------> 3     two-way way 10; node 1 a signal on a bollard, no direction,
     *                           ~20 m  |               ~20 m before junction 6 with side street way 11, no other signal
     *                                  8
     *
     *                                  7
     *                                  |
     *                                  9               second file: node 1 a signal on a gate (forward), and side
     *                                  |               street signal 9 (forward) ~11 m before junction 6
     *   2 ------------------> 1 -----> 6 ------> 3
     *                                  |
     *                                  8
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_on_bollard_before_side_street");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));

    var layer = parseTrafficSignalScenario("vehicle_signal_on_bollard_before_side_street",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 1)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 6, 1)), Set.copyOf(crossing.getApproachSegments()));

    layer = parseTrafficSignalScenario("vehicle_signal_on_gate_before_signalised_side_street").layer();
    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertTrue(junction.getApproachSegments().contains(segment(layer, 2, 6)));
  }

  /**
   * A roundabout counts as one junction for a crossing near one of its entries: it shows another signal when a vehicle
   * signal is on, or reaches first, any of its nodes, however far round the ring. The crossing is then part of the
   * entry's junction, and the vehicle signal before it is the entry's stop line. A roundabout without any signal leaves
   * the crossing isolated, with the vehicle signal as its stop line
   */
  @Test
  public void roundaboutWithSignalShowsSignalForCrossingNearAnyEntry() {
    /*
     *                            7
     *                            |
     *                            9              signal 9 (forward) ~10 m before entry node 42
     *                            |
     *                            42
     *                          /    \           roundabout way 20, clockwise 41-42-43-44, its nodes ~57 m apart
     *   2 ----> 1 -> 31 ---> 41      43 ----> 3  along the ring
     *          ~25 m  ~20 m    \    /
     *                            44             signal 1 ~25 m and signalised crossing 31 ~20 m before entry node 41
     *                            |
     *                            8              second file: node 9 is no signal
     */
    var parsed = parseTrafficSignalScenario("crossing_before_entry_of_signalised_roundabout");
    var signalisedLayer = parsed.layer();
    var entry = signalisedLayer.getIntersections().getByMemberNode(nodeByOsmId(signalisedLayer, 41));
    assertEquals(List.of(segment(signalisedLayer, 2, 41)), entry.getApproachSegments());
    assertEquals(Set.of(), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));

    parsed = parseTrafficSignalScenario("crossing_before_entry_of_unsignalised_roundabout");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
  }

  /**
   * Vehicle signals facing a signalised crossing from either side are its own stop lines, also with junctions within
   * the crossing distance of it. Where those junctions show no other signal, the crossing is isolated past them. Where
   * the junction beyond has a signal of its own, a signal passing the crossing is still not assigned to it when, past
   * the crossing and before that junction, a vehicle signal faces back towards it for the traffic coming the other way:
   * the crossing is isolated between its stop lines. A signal facing the other way behind the passing signal, not past
   * the crossing, does not count
   */
  @Test
  public void vehicleSignalsFacingCrossingFromEitherSideAreItsStopLines() {
    /*
     *              8                          10
     *              |                          |
     *   2 -------- 6 ---- 1 -> 31 <- 5 ------ 7 -------- 3     way 10 primary, two-way; signal 1 (forward) and signal
     *              |         ~9 m ~9 m        |                5 (backward) either side of signalised crossing 31,
     *              9                          11               which lies ~18 m from junction 6 with residential way
     *                                                          11 and from junction 7 with residential way 12
     *
     *   second file: side street way 12 carries signal 13 (forward), ~11 m before junction 7
     *
     *   2 -------- 6 - 5 <- 1 -> 31 --------- 7 -------- 3     third file: signal 5 back to back with signal 1, ~5 m
     *                                                          west of it, facing the traffic heading for junction 6;
     *                                                          fourth file: as the third, with signal 13 on way 12
     *                                                          as in the second file
     */
    var parsed = parseTrafficSignalScenario("vehicle_signals_bracketing_crossing_between_junctions");
    assertEquals(Set.of("1", "5"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_ISOLATED_PAST_JUNCTION_WITHOUT_OTHER_SIGNAL));
    assertTrue(parsed.layer().getIntersections().isEmpty());

    /* breaking links at isolated signals on: both stop lines join the crossing's own intersection */
    var layer = parseTrafficSignalScenario("vehicle_signals_bracketing_crossing_between_junctions",
        BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertEquals(Set.of("31", "1", "5"), Set.of(crossing.getSplitExternalId()));
    assertEquals(Set.of(segment(layer, 6, 31), segment(layer, 7, 31)), Set.copyOf(crossing.getApproachSegments()));

    /* the junction beyond with a signal of its own: still the crossing's stop lines, the junction keeps its own */
    parsed = parseTrafficSignalScenario("vehicle_signals_bracketing_crossing_next_to_signalised_junction");
    layer = parsed.layer();
    assertEquals(Set.of("1", "5"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(Set.of("13"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(Set.of("31"), parsed.signalIds(ISOLATED_CROSSING_NO_LINK_BROKEN));
    assertEquals(Set.of("31"), parsed.signalIds(CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES));
    assertEquals(List.of(nodeByOsmId(layer, 7)), onlyIntersection(layer).getMemberNodes());

    /* the signal facing the other way stands behind signal 1, not past the crossing: signal 5 goes to junction 6 ahead
     * of it, and signal 1 is the crossing's stop line, junction 7 showing no other signal */
    parsed = parseTrafficSignalScenario("vehicle_signals_back_to_back_before_crossing");
    layer = parsed.layer();
    assertEquals(Set.of("5"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(List.of(nodeByOsmId(layer, 6)), onlyIntersection(layer).getMemberNodes());

    /* the same, with junction 7 showing a signal of its own: signal 5 behind signal 1 does not make the crossing lie
     * between its stop lines, so each signal goes to the junction ahead */
    parsed = parseTrafficSignalScenario("vehicle_signals_back_to_back_before_crossing_next_to_signalised_junction");
    layer = parsed.layer();
    assertEquals(Set.of("1", "5", "13"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    assertEquals(Set.of(), parsed.signalIds(CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES));
    assertEquals(Set.of(nodeByOsmId(layer, 6), nodeByOsmId(layer, 7)), layer.getIntersections().stream()
        .flatMap(intersection -> intersection.getMemberNodes().stream()).collect(Collectors.toSet()));
  }

  /**
   * A crossing between its own stop lines, two vehicle signals facing each other across it with no junction between,
   * makes no junction signalised for another crossing nearby, and neither do its stop lines. Where nothing else near
   * the other crossing's junctions shows a signal, the other crossing is isolated and the vehicle signal before it is
   * its stop line. A crossing with a vehicle signal before it but none facing back is no crossing between its stop
   * lines: that signal is a junction's signal, and the other crossing is part of the junction ahead of it
   */
  @Test
  public void crossingBetweenItsStopLinesMakesNoJunctionSignalisedForOtherCrossing() {
    /*
     *                                     8
     *                                     |
     *   2 ----------> 1 -> 31 ----------> 6 ---.         one-way way 10: signal 1 (forward) ~5 m before signalised
     *                                           \        crossing 31, junction 6 with side street way 11 ~5 m on, way
     *                                            \       10 merging into way 20 at 12, ~22 m from crossing 31
     *   4 ------ 7 ------------ 21 > 32 < 22 --- 12 -------- 5
     *            |                                        two-way way 20: signalised crossing 32 between signal 21
     *            9                                        (forward) and signal 22 (backward), ~23 m before 12,
     *                                                     junction 7 with side street way 13 ~42 m west of crossing 32
     *
     *   second file: node 22 is no signal
     */
    var parsed = parseTrafficSignalScenario("crossing_near_merge_with_crossing_between_its_stop_lines");
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1", "21", "22"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(Set.of("32"), parsed.signalIds(CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES));

    parsed = parseTrafficSignalScenario("crossing_near_merge_with_signal_before_crossing");
    var stopLineLayer = parsed.layer();
    assertTrue(parsed.signalIds(VEHICLE_SIGNAL_SNAPPED).contains("1"));
    var sideStreetJunction = stopLineLayer.getIntersections().getByMemberNode(nodeByOsmId(stopLineLayer, 6));
    assertTrue(sideStreetJunction.getApproachSegments().contains(segment(stopLineLayer, 2, 6)));
  }

  /**
   * A vehicle signal before a tram track that crosses the road stops at the track: its stop line is before the track,
   * so the node where the track crosses is its junction, not the road junction beyond it
   */
  @Test
  public void vehicleSignalStopsAtTramTrack() {
    /*
     *                       51          7
     *                       #           |
     *   2 ------> 1 ------> 41 -------> 6 ------> 3     one-way way 10, signal 1 (forward) ~18 m before 41, where
     *                       #           |               tram way 20 crosses, ~28 m before junction 6 with side
     *                       52          8               street way 11
     */
    var layer = parseTrafficSignalScenario("vehicle_signal_before_tram_track",
        settings -> settings.activateRailwayParser(true)).layer();

    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 41)), junction.getMemberNodes());
    assertTrue(Set.of(junction.getSplitExternalId()).contains("1"));
    assertTrue(junction.getApproachSegments().contains(segment(layer, 2, 41)));
  }

  /**
   * A road that carries a tram on the same way has no tram track crossing it: a node along it where only a footway
   * crosses is not a junction for vehicle signals, so the signal goes on to the road junction beyond it
   */
  @Test
  public void vehicleSignalOnRoadWithTramOnTheSameWayGoesOnToJunction() {
    /*
     *                       51          7
     *                       ~           |
     *   2 ======> 1 ======> 41 =======> 6 ------> 3     one-way way 10, road and tram on the same way (=), signal 1
     *                       ~           |               (forward) ~18 m before 41, where footway 20 (~) crosses,
     *                       52          8               ~28 m before junction 6 with side street way 11
     */
    var layer = parseTrafficSignalScenario("vehicle_signal_on_road_with_tram_on_the_same_way", settings -> {
      settings.activateRailwayParser(true);
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    }).layer();

    assertEquals(List.of(nodeByOsmId(layer, 6)), onlyIntersection(layer).getMemberNodes());
  }

  /**
   * A tram or light rail track meeting the road near a crossing makes the crossing part of a junction, unless the track
   * runs along the crossing's own road: on the same OSM way, or on a way of the same name. With the road's own track
   * just past a side street without a signal, the crossing is isolated and the vehicle signal before it is its stop
   * line. The side street's track, named after the side street, makes the crossing part of the side street's junction
   */
  @Test
  public void tramTrackMakesCrossingPartOfJunctionUnlessAlongCrossingRoad() {
    /*
     *                           7    51
     *                           |    #
     *   2 -----> 1 -> 31 -----> 6 -> 41 ------> 3     one-way way 10 "Main Street": signal 1 (forward) ~5 m before
     *                                #                signalised crossing 31, junction 6 with side street way 11 "Side
     *                                52               Street" ~9 m on, tram way 20 meeting the road at 41 ~9 m further
     *
     *   first file: tram way 20 named "Main Street"; second file: named "Side Street"
     */
    var parsed = parseTrafficSignalScenario("crossing_before_side_street_and_tram_track_named_after_road",
        settings -> settings.activateRailwayParser(true));
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));

    parsed = parseTrafficSignalScenario("crossing_before_side_street_and_tram_track_named_after_side_street",
        settings -> settings.activateRailwayParser(true));
    var layer = parsed.layer();
    assertEquals(Set.of("1"), parsed.signalIds(VEHICLE_SIGNAL_SNAPPED));
    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertTrue(junction.getApproachSegments().contains(segment(layer, 2, 6)));
  }

  /** A ramp meter becomes an intersection of its own at its position: the link is broken there, and the segment
   * arriving there is its only approach */
  @Test
  public void rampMeterBuiltAtItsPosition() {
    /*
     *     1 --------->--------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals=ramp_meter, a shape node; no junction anywhere
     *   breaking links at isolated signals on: the link is broken at 2, a ramp meter intersection, approached from 1
     */
    var parsed = parseTrafficSignalScenario("ramp_meter", BREAK_LINKS_AT_ISOLATED_SIGNALS);
    var layer = parsed.layer();

    assertEquals(2, layer.getLinks().size());
    var rampMeter = onlyIntersection(layer);
    assertEquals(EnumSet.of(IntersectionType.RAMP_METER), rampMeter.getTypes());
    assertEquals(List.of(nodeByOsmId(layer, 2)), rampMeter.getMemberNodes());
    assertEquals(List.of(segment(layer, 1, 2)), rampMeter.getApproachSegments());
    assertEquals(Set.of("2"), parsed.signalIds(RAMP_METER_OWN_INTERSECTION));
    assertTrue(parsed.signalIds(SIGNAL_NOT_CONTROLLING_TRAFFIC).isEmpty());
  }

  /** Without breaking links at isolated signals a ramp meter is not built: no link broken, no intersection, and it is
   * recorded as such */
  @Test
  public void rampMeterNotBuiltWithoutBreakingLinksAtIsolatedSignals() {
    /*
     *     1 ------------------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals=ramp_meter, a shape node; default settings
     */
    var parsed = parseTrafficSignalScenario("ramp_meter");

    assertEquals(1, parsed.layer().getLinks().size());
    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("2"), parsed.signalIds(RAMP_METER_NO_LINK_BROKEN));
  }

  /** A ramp meter 150 m along an on-ramp leaving a signalised junction stands apart from it: the junction keeps its
   * node and approaches, and the meter is an intersection of its own */
  @Test
  public void rampMeterAfterSignalisedJunctionStandsApart() {
    /*
     *     1 ------------------- 2 ------------------- 3     way 10: 1-2-3 primary, two-way; 2 a signalised junction
     *                           |
     *                           v                           way 11: 2-20-21 motorway_link, one-way south
     *                           |
     *                           20                          20: ramp meter, a shape node ~150 m from 2
     *                           |
     *                           21
     */
    var layer = parseTrafficSignalScenario("ramp_meter_after_signalised_junction", BREAK_LINKS_AT_ISOLATED_SIGNALS)
        .layer();

    assertEquals(2, layer.getIntersections().size());
    var junction = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 2));
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(List.of(nodeByOsmId(layer, 2)), junction.getMemberNodes());
    assertEquals(Set.of(segment(layer, 1, 2), segment(layer, 3, 2)), Set.copyOf(junction.getApproachSegments()));
    var rampMeter = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 20));
    assertEquals(EnumSet.of(IntersectionType.RAMP_METER), rampMeter.getTypes());
    assertEquals(List.of(segment(layer, 2, 20)), rampMeter.getApproachSegments());
  }

  /** A ramp meter on a two-way road applying forward only holds the traffic in the way's direction only */
  @Test
  public void rampMeterOnTwoWayRoadHoldsItsDirectionOnly() {
    /*
     *     1 ------------------- 2 ------------------- 3     way 10: 1-2-3 primary, two-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals=ramp_meter, traffic_signals:direction=forward, a shape node
     */
    var layer = parseTrafficSignalScenario("ramp_meter_on_two_way_road", BREAK_LINKS_AT_ISOLATED_SIGNALS).layer();

    var rampMeter = onlyIntersection(layer);
    assertEquals(EnumSet.of(IntersectionType.RAMP_METER), rampMeter.getTypes());
    assertEquals(List.of(segment(layer, 1, 2)), rampMeter.getApproachSegments());
  }

  /** A ramp meter tagged as a plain vehicle signal, overridden as a ramp meter, becomes a ramp meter rather than an
   * isolated crossing */
  @Test
  public void plainSignalOverriddenAsRampMeterIsOne() {
    /*
     *     1 --------->--------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals:direction=forward, a shape node, overridden as a ramp meter
     */
    var parsed = parseTrafficSignalScenario("plain_signal_on_ramp", BREAK_LINKS_AT_ISOLATED_SIGNALS.andThen(
        settings -> settings.getIntersectionSettings().overrideTrafficSignalKind(2, OsmTrafficSignalKind.RAMP_METER)));
    var layer = parsed.layer();

    var rampMeter = onlyIntersection(layer);
    assertEquals(EnumSet.of(IntersectionType.RAMP_METER), rampMeter.getTypes());
    assertEquals(List.of(segment(layer, 1, 2)), rampMeter.getApproachSegments());
    assertEquals(Set.of("2"), parsed.signalIds(RAMP_METER_OWN_INTERSECTION));
  }

  /** A ramp meter within the joining reach of a consolidated junction joins it, as a crossing does: its node becomes a
   * member node, and its approach an internal segment with a signal, since a ramp meter is not run with the junction */
  @Test
  public void rampMeterInsideJunctionJoinsItKeepingItsSignal() {
    /*
     *             11    14
     *              |     ^
     *              v     |
     *     16 <---- 1 <-- 2 <---- 15       ways 10 (11-1-5-3-12) and 11 (13-4-2-14) one-way, crossed by ways 12
     *              |     ^                (15-2-1-16) and 13 (17-3-4-18); nodes 1 to 4 signalised, about 15 m apart
     *              5     |                5: ramp meter, a shape node halfway between 1 and 3
     *              v     |
     *     17 ----> 3 --> 4 ----> 18       consolidation on
     *              |     ^
     *              v     |
     *             12     13
     */
    var parsed = parseTrafficSignalScenario("ramp_meter_inside_divided_roads_crossing",
        BREAK_LINKS_AT_ISOLATED_SIGNALS.andThen(
            settings -> settings.getIntersectionSettings().setConsolidateIntersections(true)));
    var layer = parsed.layer();

    var junction = (SpatialIntersection) onlyIntersection(layer);
    assertTrue(junction.hasMemberNode(nodeByOsmId(layer, 5)));
    assertEquals(EnumSet.of(IntersectionType.JUNCTION, IntersectionType.RAMP_METER), junction.getTypes());
    assertEquals(List.of(segment(layer, 1, 5)), junction.getInternalSegmentsWithSignal());
    assertEquals(Set.of("5"), parsed.signalIds(RAMP_METER_OWN_INTERSECTION));
    assertEquals(Set.of("5"), parsed.entityIds(OsmPlanitEntityIssue.RAMP_METER_JOINED_TO_JUNCTION));
  }

  /** A ramp meter where a signalised crossing crosses the ramp is no intersection of its own: the crossing keeps the
   * node */
  @Test
  public void rampMeterOnSignalisedCrossingIsInsideTheCrossing() {
    /*
     *                           4
     *                           |                           way 11: 4-2-5 footway crossing, crossing=traffic_signals
     *     1 --------->--------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *                           |
     *                           5                           2: ramp meter
     */
    var parsed = parseTrafficSignalScenario("ramp_meter_on_signalised_crossing_way", BREAK_LINKS_AT_ISOLATED_SIGNALS);
    var layer = parsed.layer();

    var crossing = onlyIntersection(layer);
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(List.of(nodeByOsmId(layer, 2)), crossing.getMemberNodes());
    assertEquals(Set.of("2"), parsed.signalIds(RAMP_METER_INSIDE_OTHER_INTERSECTION));
  }

  /** A ramp meter on a ramp removed with the dangling subnetworks is lost by pruning, as any intersection there is */
  @Test
  public void rampMeterOnDanglingRampIsLostByPruning() {
    /*
     *     1 ------------- 2 ------------- 3 ------------- 4     way 10: 1-2-3-4 primary, two-way, the largest
     *                                                           subnetwork
     *                     30 ----->------ 31 ------------ 32    way 11: 30-31-32 motorway_link, one-way, connected
     *                                                           to nothing; 31: ramp meter, a shape node
     *
     *   car the only road mode, so pruning the dangling subnetworks for car removes the ramp, its nodes included
     */
    var parsed = parseTrafficSignalScenario("ramp_meter_on_dangling_ramp", BREAK_LINKS_AT_ISOLATED_SIGNALS.andThen(
        settings -> settings.getHighwaySettings().deactivateAllOsmRoadModesExcept(OsmRoadModeTags.MOTOR_CAR)));

    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("31"), parsed.entityIds(OsmPlanitEntityIssue.INTERSECTION_LOST_BY_PRUNING));
  }

  /** A traffic signal direction overridden on a ramp meter is found: the layer has the signal */
  @Test
  public void directionOverriddenOnRampMeterIsFound() {
    /*
     *     1 ------------------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals=ramp_meter, a shape node; its direction overridden forward
     */
    var parsed = parseTrafficSignalScenario("ramp_meter", settings -> settings.getIntersectionSettings()
        .overrideTrafficSignalDirection(2, OsmTrafficSignalDirection.FORWARD));

    assertEquals(Set.of(), parsed.entityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));
  }

  /** Traffic signals on a track or a cycleway make the node where it meets a road ahead an intersection: a junction
   * for a track, car traffic being held for the tram, a crossing for a cycleway; with no road within reach they are
   * ignored and recorded; no link is broken at any of them */
  @Test
  public void signalsOnTrackOrCycleway() {
    /*
     *                     14
     *                     #
     *                     13                          # tram track, way 31: 10-11-2-12-13-14
     *                     #   (~330 m from 2)         ~ cycleway, way 32: 20-21-4
     *                     12                          way 30: 1-2-3-4 primary, two-way
     *                     #
     *   1 --------------- 2 --------------- 3 --------------- 4
     *                     # ~15 m                             ~ ~10 m
     *                     11                                  21
     *                     #                                   ~
     *                     10                                  20
     *
     *   11, 13, 21: highway=traffic_signals, shape nodes of the track and the cycleway
     *   railway parser on, cycleways parsed, breaking links at isolated signals on:
     *   11 is on the track 15 m before it crosses the road at 2: 2 is a signalised junction, every segment entering it
     *   an approach, road and track; 21 is on the cycleway 10 m before it meets the road at 4: 4 is a signalised
     *   crossing, the road its only approach, since approaches are for motorised road and rail traffic; 13 has no road
     *   within 50 m along the track: ignored; no link broken: five links
     */
    var parsed = parseTrafficSignalScenario("track_and_cycleway_signals", BREAK_LINKS_AT_ISOLATED_SIGNALS.andThen(
        settings -> {
          settings.activateRailwayParser(true);
          settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
        }));
    var layer = parsed.layer();

    assertEquals(5, layer.getLinks().size());
    assertEquals(2, layer.getIntersections().size());
    var trackCrossing = nodeByOsmId(layer, 2);
    var junction = layer.getIntersections().getByMemberNode(trackCrossing);
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(trackCrossing.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(junction.getApproachSegments()));
    var cyclewayEnd = nodeByOsmId(layer, 4);
    var crossing = layer.getIntersections().getByMemberNode(cyclewayEnd);
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(segmentsForMotorisedRoadOrRailEntering(cyclewayEnd), Set.copyOf(crossing.getApproachSegments()));
    assertNotEquals(cyclewayEnd.streamEntrySegments().count(), crossing.getApproachSegments().size());

    assertEquals(Set.of("11"), parsed.signalIds(TRACK_SIGNAL_AT_ROAD_CROSSING));
    assertEquals(Set.of("21"), parsed.signalIds(CYCLEWAY_SIGNAL_AT_ROAD_CROSSING));
    assertEquals(Set.of("13"), parsed.signalIds(TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING));
  }

  /** A signal on a track that branches before reaching a road, each branch reaching a road within reach, makes the node
   * where it branches the intersection, with the segment the signal is on as its only approach, joining no road node:
   * the tram is held once, before choosing a branch */
  @Test
  public void signalOnTrackBranchingBeforeRoadMakesBranchTheIntersection() {
    /*
     *                     12    13
     *                     #     #
     *   1 --------------- 2 --- 5 --------------- 3      way 30: 1-2-5-3 primary, two-way
     *                      #   #                         # tram track, way 31: 10-11-15-2-12; way 32: 15-5-13
     *                       # #   (~22 m each)
     *                        15   tracks branch
     *                        #    ~10 m
     *                        11
     *                        #
     *                        10
     *
     *   11: highway=traffic_signals, a shape node of the track; railway parser on
     *   beyond 11 the track branches at 15, each branch crossing the road within 50 m of 11, at 2 and at 5: 15 is a
     *   signalised junction with 10-15, the segment 11 is on, its only approach; neither 2 nor 5 is an intersection
     */
    var parsed =
        parseTrafficSignalScenario("track_branching_before_road", settings -> settings.activateRailwayParser(true));
    var layer = parsed.layer();

    assertEquals(1, layer.getIntersections().size());
    var branch = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 15));
    assertNotNull(branch);
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), branch.getTypes());
    assertEquals(Set.of(segment(layer, 10, 15)), Set.copyOf(branch.getApproachSegments()));
    assertEquals(Set.of("11"), parsed.signalIds(TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH));
    assertTrue(parsed.signalIds(TRACK_SIGNAL_AT_ROAD_CROSSING).isEmpty());
    assertTrue(parsed.signalIds(TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING).isEmpty());
  }

  /** A signal on a track that branches before reaching a road, only one branch reaching a road within reach, makes the
   * node where that branch crosses the road the intersection, as when the track does not branch */
  @Test
  public void signalOnTrackBranchingWithOneBranchReachingRoadJoinsThatCrossing() {
    /*
     *                     12
     *                     #
     *   1 --------------- 2 --------------- 3      way 30: 1-2-3 primary, two-way
     *                     #                        # tram track, way 31: 10-11-15-2-12; spur, way 32: 15-16
     *                     #  (~20 m)
     *                     15 ##### 16              spur ends ~28 m beyond 15, reaching no road
     *                     #   ~10 m
     *                     11
     *                     #
     *                     10
     *
     *   11: highway=traffic_signals, a shape node of the track; railway parser on
     *   beyond 11 the track branches at 15, only the branch to 2 reaching a road: 2 is a signalised junction, every
     *   segment entering it an approach, road and track
     */
    var parsed =
        parseTrafficSignalScenario("track_branch_ending_before_road", settings -> settings.activateRailwayParser(true));
    var layer = parsed.layer();

    assertEquals(1, layer.getIntersections().size());
    var trackCrossing = nodeByOsmId(layer, 2);
    var junction = layer.getIntersections().getByMemberNode(trackCrossing);
    assertNotNull(junction);
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(trackCrossing.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(junction.getApproachSegments()));
    assertEquals(Set.of("11"), parsed.signalIds(TRACK_SIGNAL_AT_ROAD_CROSSING));
    assertTrue(parsed.signalIds(TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH).isEmpty());
  }

  /** A signal on a cycleway that a footway crosses before the road makes the node where the cycleway meets the road a
   * crossing: only segments that allow bicycles are followed, so the footway is no branch */
  @Test
  public void signalOnCyclewayCrossedByFootwayBeforeRoadMakesCrossing() {
    /*
     *   1 --------------- 2 --------------- 3      way 30: 1-2-3 primary, two-way
     *                     ~  (~3 m)                ~ cycleway, way 32: 20-21-22-2
     *              23 ... 22 ... 24                . footway, way 33: 23-22-24, crossing the cycleway at 22
     *                     ~  (~7 m)
     *                     21
     *                     ~
     *                     20
     *
     *   21: highway=traffic_signals, a shape node of the cycleway; cycleways and footways parsed
     *   beyond 21 the footway crosses at 22, not followed: 2 is a signalised crossing, the road segments entering it
     *   its approaches
     */
    var parsed = parseTrafficSignalScenario("cycleway_footway_crossing_before_road", settings -> {
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    });
    var layer = parsed.layer();

    assertEquals(1, layer.getIntersections().size());
    var cyclewayEnd = nodeByOsmId(layer, 2);
    var crossing = layer.getIntersections().getByMemberNode(cyclewayEnd);
    assertNotNull(crossing);
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(segmentsForMotorisedRoadOrRailEntering(cyclewayEnd), Set.copyOf(crossing.getApproachSegments()));
    assertEquals(Set.of("21"), parsed.signalIds(CYCLEWAY_SIGNAL_AT_ROAD_CROSSING));
  }

  /** A traffic signal on a way that is not a road, track or cycleway is ignored and recorded, though the way meets
   * a road within reach */
  @Test
  public void trafficSignalOnFootwayIgnored() {
    /*
     *   1 --------------- 2 --------------- 3      way 30: 1-2-3 primary, two-way
     *                     .  (~10 m)               . footway, way 33: 20-21-2
     *                     21
     *                     .
     *                     20
     *
     *   21: highway=traffic_signals, a shape node of the footway; footways parsed
     *   21 is on neither a road, a track nor a cycleway: no intersection, and 21 is recorded as such
     */
    var parsed = parseTrafficSignalScenario("signal_on_footway_before_road",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));

    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("21"), parsed.signalIds(TRAFFIC_SIGNAL_NOT_ON_ROAD_TRACK_OR_CYCLEWAY));
    assertTrue(parsed.signalIds(TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING).isEmpty());
  }

  /** A crossing signal tagged only on the crossing way is recorded as a signal where the crossing way meets the road,
   * noted as such, and has the outcome of any crossing signal on a junction node */
  @Test
  public void crossingSignalTaggedOnlyOnCrossingWayNoted() {
    /*
     *              4
     *              :
     *     2 ------ 1 ------ 3      way 10: 2-1-3 primary, two-way (---)
     *              :                way 11: 4-1-5 footway, footway=crossing, crossing=traffic_signals (:)
     *              5
     *
     *   1: highway=crossing, crossing=marked; footways parsed
     */
    var parsed = parseTrafficSignalScenario("crossing_signal_on_crossing_way_only",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));

    assertEquals(Set.of(1L), Set.copyOf(parsed.networkReaderData().getTrafficSignalOsmNodeIds()));
    assertEquals(Set.of("1"), parsed.signalIds(CROSSING_SIGNAL_ONLY_ON_CROSSING_WAY));
    assertEquals(Set.of("1"), parsed.signalIds(CROSSING_SIGNAL_ON_JUNCTION_NODE));
    assertTrue(parsed.signalIds(CROSSING_SIGNAL_TAGS_INCONSISTENT).isEmpty());
  }

  /** A crossing signal tagged on the crossing way is taken though its node says the crossing has none, and the
   * inconsistency is noted so it can be checked in OSM */
  @Test
  public void crossingWaySignalTakenOverNodeSayingNone() {
    /*
     *              4
     *              :
     *     2 ------ 1 ------ 3      way 10: 2-1-3 primary, two-way (---)
     *              :                way 11: 4-1-5 footway, footway=crossing, crossing=traffic_signals (:)
     *              5
     *
     *   1: highway=crossing, crossing=zebra, a crossing without signals; footways parsed
     */
    var parsed = parseTrafficSignalScenario("crossing_way_signal_node_says_zebra",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));
    var layer = parsed.layer();

    assertEquals(1, layer.getIntersections().size());
    assertNotNull(layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 1)));
    assertEquals(Set.of("1"), parsed.signalIds(CROSSING_SIGNAL_ONLY_ON_CROSSING_WAY));
    assertEquals(Set.of("1"), parsed.signalIds(CROSSING_SIGNAL_TAGS_INCONSISTENT));
  }

  /** A vehicle signal whose junction is where its road ends outside the parsed area is noted as such, alongside its
   * outcome: on the junction node when on the road end itself, snapped when looking ahead to it (scenarios and
   * drawings: OsmTrafficSignalsTest, vehicleSignalWhereRoadEndsOutsideDeclaredBoundsMakesItsNodeTheJunction and
   * vehicleSignalBeforeRoadEndOutsideParsedAreaSnapsToRoadEnd) */
  @Test
  public void vehicleSignalAtRoadEndOutsideParsedAreaNoted() {
    var onRoadEnd = parseTrafficSignalScenario("vehicle_signal_where_road_ends_outside_parsed_area");
    assertEquals(Set.of("5"), onRoadEnd.signalIds(VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA));
    assertEquals(Set.of("5"), onRoadEnd.signalIds(VEHICLE_SIGNAL_ON_JUNCTION_NODE));

    var beforeRoadEnd = parseTrafficSignalScenario("vehicle_signal_before_road_end_outside_parsed_area");
    assertEquals(Set.of("6"), beforeRoadEnd.signalIds(VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA));
    assertEquals(Set.of("6"), beforeRoadEnd.signalIds(VEHICLE_SIGNAL_SNAPPED));
  }

  /** The road end outside the parsed area is only within reach as far as any junction is: beyond the maximum distance
   * from a vehicle signal to its junction, the signal is at the edge of the parsed area, which the bounds the input
   * file declares mark as well when no bounding area is set */
  @Test
  public void vehicleSignalBeyondReachOfRoadEndOutsideParsedAreaAtEdge() {
    /*
     *              4
     *              |                 |
     *     1 ------ 2 --------------- | ---- 6 -------- 5      way 10: 1-2-6-5 primary, two-way; way 11: 2-4 secondary
     *                                |   <- about 37 m ->
     *                     edge of the declared bounds (lon 151.2120)
     *
     *   6: highway=traffic_signals, traffic_signals:direction=forward; maximum distance from a vehicle signal to its
     *   junction lowered to 20 m, so the road end 5 is out of reach
     */
    var parsed = parseTrafficSignalScenario("vehicle_signal_before_road_end_outside_parsed_area",
        settings -> settings.getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(20));

    assertTrue(parsed.layer().getIntersections().isEmpty());
    assertEquals(Set.of("6"), parsed.signalIds(VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
  }

  /** What became of the intersections is recorded with their ids: one pruned away with its island, one removed for
   * having no approach, since no vehicle a traffic signal controls can use it */
  @Test
  public void intersectionIssuesRecorded() {
    /*
     *                                                          island, not connected:
     *   1 ------ 2 ------ 3 ------ 4 ------ 5                  41 ------ 40 ------ 42
     *            ~        :        ~                                     |
     *            22 ~~~~~ 30 ~~~~~ 23                                    43
     *                     :
     *                     21
     *
     *   way 10: 1-2-3-4-5 primary; way 11: 2-22-30-23-4 cycleway (~); way 12: 3-30-21 footway (:); all two-way
     *   way 20: 41-40-42 and way 21: 43-40 primary, two-way, on an island
     *   30: highway=crossing, crossing=traffic_signals, where the footway crosses the cycleway, no road through it
     *   40: highway=traffic_signals, on the island's junction
     *   parsed with footways and cycleways, car, foot and bicycle as the only road modes, and dangling subnetworks
     *   pruned for all three: the island is unusable by any mode and removed with its intersection; the crossing at 30
     *   is removed once every signal is handled: only footways and cycleways enter it, so it has no approach
     */
    var parsed = parseTrafficSignalScenario("crossing_on_paths_and_pruned_island", settings -> {
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
      settings.getHighwaySettings().deactivateAllOsmRoadModesExcept(
          OsmRoadModeTags.MOTOR_CAR, OsmRoadModeTags.FOOT, OsmRoadModeTags.BICYCLE);
      settings.activateRemoveDanglingSubnetworks(PredefinedModeType.PEDESTRIAN);
      settings.activateRemoveDanglingSubnetworks(PredefinedModeType.BICYCLE);
    });

    assertEquals(2, parsed.networkReaderData().getPlanitEntityDiagnostics().getIntersectionsCreated());
    assertEquals(Set.of("40"), parsed.entityIds(OsmPlanitEntityIssue.INTERSECTION_LOST_BY_PRUNING));
    assertEquals(Set.of("30"), parsed.entityIds(OsmPlanitEntityIssue.INTERSECTION_REMOVED_WITHOUT_CONTROLLED_APPROACH));
    assertTrue(parsed.layer().getIntersections().isEmpty());
  }

  /**
   * Signals that create nothing are still counted: a mid-block crossing with breaking links at isolated signals off,
   * and a vehicle signal beyond a tighter maximum distance
   */
  @Test
  public void signalsCreatingNothingCounted() {
    var parsed = parseTrafficSignalScenario("mid_block_crossing");
    assertEquals(Set.of("31"), parsed.signalIds(ISOLATED_CROSSING_NO_LINK_BROKEN));

    parsed = parseTrafficSignalScenario("signal_12m_before_junction",
        settings -> settings.getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(10));
    assertEquals(1, parsed.networkReaderData().getParseDiagnostics()
        .getOccurrences(VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
  }
}

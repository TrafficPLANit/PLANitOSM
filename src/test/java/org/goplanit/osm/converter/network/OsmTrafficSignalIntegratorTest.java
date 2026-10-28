package org.goplanit.osm.converter.network;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.diagnostics.OsmNearestJunctionBucket;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcomeGroup;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.physical.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.nodeByOsmId;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;

/**
 * What the traffic signal integrator records about the signals it integrates, beyond the intersections it creates
 */
public class OsmTrafficSignalIntegratorTest {

  /**
   * Create a reader of a traffic signal scenario limited to a bounding area, breaking links at isolated crossings
   *
   * @param scenarioFile name of the scenario file
   * @param boundingArea to parse within
   * @return the reader
   */
  private static OsmNetworkReader createReaderWithinBoundingArea(String scenarioFile, OsmBoundary boundingArea) {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(scenarioFile).toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    reader.getSettings().setBoundingArea(boundingArea);
    return reader;
  }

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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_breaks_junction_approach.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();

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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_near_signalised_junction.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_JUNCTION_NODE));
    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION));
    assertEquals(0, diagnostics.getOccurrences(OsmTrafficSignalOutcome.ISOLATED_CROSSING_OWN_INTERSECTION));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_with_unrecognised_direction.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var junction = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 6));
    assertNotNull(junction);
    assertEquals(List.of(segment(layer, 4, 6)), junction.getApproachSegments());
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_DIRECTION_UNRECOGNISED));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN));
  }

  /** A signal carrying both kinds has one outcome as a vehicle signal and one as a crossing signal, so each group of
   * outcomes adds up to the signals of its kind (scenario and drawing: OsmTrafficSignalsTest, bothKindsOnOneNode) */
  @Test
  public void signalOfBothKindsHasOneOutcomePerKind() {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("both_kinds_on_centre.osm").toString(), CountryNames.AUSTRALIA);
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(1, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL));
    assertEquals(1, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.CROSSING_SIGNAL));
    assertEquals(0, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.SIGNAL_OF_ANY_KIND));
  }

  /** What is noted alongside a signal's outcome is kept apart from the outcomes, so a vehicle signal with notes still
   * counts once among the vehicle signals (scenario and drawing: signalWithUnrecognisedDirectionTakenAsUntagged) */
  @Test
  public void notesKeptApartFromOutcomes() {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_with_unrecognised_direction.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(1, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL));
    assertEquals(2, diagnostics.getOccurrences(OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME));
  }

  /** A vehicle signal without a junction within reach is recorded with how far along the road its nearest junction
   * is (scenario and drawing: OsmTrafficSignalsTest, vehicleSignalFarFromAnyJunctionOnShapeNode, junction 6 about 148 m
   * from signal 1) */
  @Test
  public void nearestJunctionRecordedForVehicleSignalWithoutJunction() {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_far_from_junction_on_shape_node.osm").toString(), CountryNames.AUSTRALIA);
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmNearestJunctionBucket.FROM_100_TO_150_M));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_far_from_junction_on_shape_node.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    reader.getSettings().getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(40);
    reader.getSettings().setBoundingArea(OsmBoundary.of(151.2081, 151.2115, -33.8715, -33.8685));
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertEquals(List.of(), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(0, reader.getNetworkReaderData().getPlanitEntityDiagnostics().getIntersectionsCreated());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_mid_block_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    assertEquals(List.of(), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(1, reader.getNetworkReaderData().getPlanitEntityDiagnostics().getIntersectionsCreated());
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
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
      IdGenerator.reset();
      var reader = createReaderWithinBoundingArea(
          "vehicle_signal_before_mid_block_crossing.osm", OsmBoundary.of(westEdge, 151.2120, -33.8715, -33.8685));
      var layer = reader.read().getTransportLayers().getFirst();
      var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

      assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA));
      assertEquals(List.of("1"),
          diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
      assertTrue(layer.getIntersections().isEmpty());
      assertEquals(1, layer.getLinks().size());
    }

    IdGenerator.reset();
    var reader = createReaderWithinBoundingArea(
        "vehicle_signal_before_mid_block_crossing.osm", OsmBoundary.of(151.20962, 151.2120, -33.8715, -33.8685));
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.ISOLATED_CROSSING_OWN_INTERSECTION));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
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
    var reader = createReaderWithinBoundingArea(
        "vehicle_signal_before_isolated_crossing.osm", OsmBoundary.of(151.2091, 151.2120, -33.8715, -33.8685));
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertEquals(List.of(), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(layer.getIntersections().isEmpty());
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
    var reader = createReaderWithinBoundingArea(
        "crossing_near_signalised_junction.osm", OsmBoundary.of(151.2099, 151.2120, -33.8715, -33.8685));
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION));
    assertEquals(List.of(), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA));
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
    var reader = createReaderWithinBoundingArea(
        "vehicle_signal_before_mid_block_crossing.osm", OsmBoundary.of(151.2060, 151.21027, -33.8715, -33.8685));
    reader.getSettings().getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(40);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertTrue(layer.getIntersections().isEmpty());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_side_street_and_isolated_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(layer.getIntersections().isEmpty());
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
    var reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signal_before_median_crossing_to_signalised_carriageway.osm").toString(), CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().stream()
        .noneMatch(intersection -> intersection.getMemberNodes().contains(nodeByOsmId(layer, 6))));
    var acrossMedian = layer.getIntersections().stream()
        .filter(intersection -> intersection.getMemberNodes().contains(nodeByOsmId(layer, 4)))
        .findFirst().orElseThrow();
    assertEquals(List.of(segment(layer, 2, 4)), acrossMedian.getApproachSegments());

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signal_before_median_crossing_to_unsignalised_carriageway.osm").toString(), CountryNames.AUSTRALIA);
    var unsignalisedLayer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.LOWEST_ORDER_ROAD_JUNCTION_PASSED));
    var sideStreet = unsignalisedLayer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, unsignalisedLayer.getIntersections().size());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_isolated_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(layer.getIntersections().isEmpty());

    /* breaking links at isolated crossings on: the stop line joins the crossing's own intersection */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_isolated_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(1, layer.getIntersections().size());
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    /* crossing within the crossing distance of a junction that shows no other signal: still the crossing's stop line,
     * and the junction gets no intersection */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_crossing_near_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(layer.getIntersections().isEmpty());

    /* only a service lane within the crossing distance beyond the crossing: still the crossing's stop line */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_crossing_near_service_lane.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("31"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.CROSSING_ISOLATED_PAST_JUNCTION_WITHOUT_OTHER_SIGNAL));

    /* the service lane with a signal of its own: a junction like any other, claiming the crossing */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_crossing_near_signalised_service_lane.osm")
            .toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of(), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
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
    var reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signal_before_crossing_near_junction_with_crossing_on_side_street.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of(nodeByOsmId(layer, 6)), layer.getIntersections().stream().findFirst().orElseThrow()
        .getMemberNodes());
    assertEquals(Set.of("31", "32"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION)));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signal_before_crossing_near_divided_road_junction.osm").toString(), CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(Set.of("1", "41"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED)));
    assertEquals(Set.of(nodeByOsmId(layer, 6), nodeByOsmId(layer, 16)), layer.getIntersections().stream()
        .flatMap(intersection -> intersection.getMemberNodes().stream()).collect(Collectors.toSet()));
    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signal_before_crossing_near_junction_and_next_signalised_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(List.of("42"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of(nodeByOsmId(layer, 19)), layer.getIntersections().stream().findFirst().orElseThrow()
        .getMemberNodes());
    assertEquals(1, layer.getIntersections().size());
  }

  /**
   * A vehicle signal tagged with direction both on a road, but not on a junction node, is a crossing signal, noted to
   * check in OSM: the junction it would otherwise reach gets no intersection, and with breaking links at isolated
   * crossings on, it is a crossing intersection of its own, next to the signalised crossing beside it. On a junction
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_both_directions_beside_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BOTH_DIRECTIONS_TAKEN_AS_CROSSING));
    assertEquals(Set.of("1", "31"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.ISOLATED_CROSSING_NO_LINK_BROKEN)));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_both_directions_beside_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(Set.of(nodeByOsmId(layer, 1), nodeByOsmId(layer, 31)), layer.getIntersections().stream()
        .flatMap(intersection -> intersection.getMemberNodes().stream()).collect(Collectors.toSet()));
    assertTrue(layer.getIntersections().stream().allMatch(intersection -> intersection.isCrossingOnly()));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_both_directions_on_junction_node.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("6"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_JUNCTION_NODE));
    assertEquals(List.of(),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BOTH_DIRECTIONS_TAKEN_AS_CROSSING));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("direction_tagged_signal_with_side_street_behind.osm").toString(),
        CountryNames.AUSTRALIA);
    var intersectionSettings = reader.getSettings().getIntersectionSettings();
    intersectionSettings.overrideTrafficSignalDirection(1, OsmTrafficSignalDirection.BACKWARD);
    intersectionSettings.overrideTrafficSignalDirection(6, OsmTrafficSignalDirection.FORWARD);
    intersectionSettings.overrideTrafficSignalDirection(999, OsmTrafficSignalDirection.FORWARD);
    var layer = reader.read().getTransportLayers().getFirst();
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 4)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 6, 4)), junction.getApproachSegments());
    assertEquals(List.of("6", "999"), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_without_direction_near_tie.osm").toString(),
            CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalDirection(1,
        OsmTrafficSignalDirection.BACKWARD);
    layer = reader.read().getTransportLayers().getFirst();
    junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_12m_before_junction.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.CROSSING_SIGNAL);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalKind(999, OsmTrafficSignalKind.NOT_A_SIGNAL);
    var layer = reader.read().getTransportLayers().getFirst();
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 1)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(List.of("999"), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("four_arm_junction_signal_on_centre.osm").toString(),
            CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.NOT_A_SIGNAL);
    layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of(), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("junction_without_signals.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.VEHICLE_SIGNAL);
    layer = reader.read().getTransportLayers().getFirst();
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 1)), junction.getMemberNodes());
    assertEquals(Set.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(4, junction.getApproachSegments().size());

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("both_kinds_on_centre.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalKind(1, OsmTrafficSignalKind.CROSSING_SIGNAL);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(Set.of(IntersectionType.CROSSING),
        layer.getIntersections().stream().findFirst().orElseThrow().getTypes());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_crossing_node_near_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(List.of("31"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));
    assertTrue(layer.getIntersections().isEmpty());

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_crossing_node_near_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_mid_block_crossing_node.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    assertEquals(List.of("31"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));
    assertEquals(List.of(nodeByOsmId(layer, 31)),
        layer.getIntersections().stream().findFirst().orElseThrow().getMemberNodes());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_untagged_footway_crossing_near_junction.osm")
            .toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(List.of("31"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_untagged_footway_crossing_near_junction.osm")
            .toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_untagged_footway_ending_near_junction.osm")
            .toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("31"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_plaza_outline_near_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of(nodeByOsmId(layer, 6)),
        layer.getIntersections().stream().findFirst().orElseThrow().getMemberNodes());
    assertEquals(List.of(), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED));
  }

  /**
   * A signalised crossing whose road traffic faces a stop or give way sign between the crossing and the junction is
   * taken as not signalised: no intersection, also with breaking links at isolated crossings on, and its outcome is
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_before_stop_sign_on_side_street.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("32"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_BEFORE_STOP_OR_GIVE_WAY_SIGN));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_before_stop_sign_facing_away.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of(nodeByOsmId(layer, 32)),
        layer.getIntersections().stream().findFirst().orElseThrow().getMemberNodes());
    assertEquals(List.of(), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_BEFORE_STOP_OR_GIVE_WAY_SIGN));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_and_crossing_signal_before_unsignalised_side_street.osm")
            .toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_and_crossing_signal_before_unsignalised_side_street.osm")
            .toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 1)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(List.of(segment(layer, 2, 1)), crossing.getApproachSegments());

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_and_crossing_signal_before_signalised_side_street.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertTrue(junction.getApproachSegments().contains(segment(layer, 2, 6)));
    assertEquals(List.of(), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));
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
    var reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "tagged_vehicle_signal_just_past_crossing_before_side_street.osm").toString(), CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("5"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));
    assertEquals(List.of("5"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_PAST_ISOLATED_CROSSING_SALVAGED_AS_ITS_SIGNAL));
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));

    /* breaking links at isolated crossings on: the crossing has its own intersection with its stop line */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "tagged_vehicle_signal_just_past_crossing_before_side_street.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(List.of(segment(layer, 2, 31)), crossing.getApproachSegments());

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "untagged_vehicle_signal_just_past_crossing_before_side_street.osm").toString(), CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("5"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of(),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_PAST_ISOLATED_CROSSING_SALVAGED_AS_ITS_SIGNAL));
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_on_bollard_before_side_street.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_ISOLATED_CROSSING));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_on_bollard_before_side_street.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 1)), crossing.getMemberNodes());
    assertTrue(crossing.isCrossingOnly());
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 6, 1)), Set.copyOf(crossing.getApproachSegments()));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_on_gate_before_signalised_side_street.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_before_entry_of_signalised_roundabout.osm").toString(),
        CountryNames.AUSTRALIA);
    var signalisedLayer = reader.read().getTransportLayers().getFirst();
    var entry = signalisedLayer.getIntersections().stream()
        .filter(intersection -> intersection.getMemberNodes().contains(nodeByOsmId(signalisedLayer, 41)))
        .findFirst().orElseThrow();
    assertEquals(List.of(segment(signalisedLayer, 2, 41)), entry.getApproachSegments());
    assertEquals(List.of(), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_before_entry_of_unsignalised_roundabout.osm").toString(),
        CountryNames.AUSTRALIA);
    var unsignalisedLayer = reader.read().getTransportLayers().getFirst();
    assertTrue(unsignalisedLayer.getIntersections().isEmpty());
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signals_bracketing_crossing_between_junctions.osm").toString(),
        CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(Set.of("1", "5"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING)));
    assertEquals(List.of("31"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_ISOLATED_PAST_JUNCTION_WITHOUT_OTHER_SIGNAL));
    assertTrue(layer.getIntersections().isEmpty());

    /* breaking links at isolated crossings on: both stop lines join the crossing's own intersection */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signals_bracketing_crossing_between_junctions.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(1, layer.getIntersections().size());
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertEquals(Set.of("31", "1", "5"), Set.of(crossing.getSplitExternalId()));
    assertEquals(Set.of(segment(layer, 6, 31), segment(layer, 7, 31)), Set.copyOf(crossing.getApproachSegments()));

    /* the junction beyond with a signal of its own: still the crossing's stop lines, the junction keeps its own */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signals_bracketing_crossing_next_to_signalised_junction.osm").toString(), CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(Set.of("1", "5"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING)));
    assertEquals(List.of("13"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.ISOLATED_CROSSING_NO_LINK_BROKEN));
    assertEquals(List.of("31"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES));
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 7)),
        layer.getIntersections().stream().findFirst().orElseThrow().getMemberNodes());

    /* the signal facing the other way stands behind signal 1, not past the crossing: signal 5 goes to junction 6 ahead
     * of it, and signal 1 is the crossing's stop line, junction 7 showing no other signal */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signals_back_to_back_before_crossing.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("5"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of("1"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));
    assertEquals(List.of(nodeByOsmId(layer, 6)), layer.getIntersections().stream().findFirst().orElseThrow()
        .getMemberNodes());
    assertEquals(1, layer.getIntersections().size());

    /* the same, with junction 7 showing a signal of its own: signal 5 behind signal 1 does not make the crossing lie
     * between its stop lines, so each signal goes to the junction ahead */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "vehicle_signals_back_to_back_before_crossing_next_to_signalised_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(Set.of("1", "5", "13"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED)));
    assertEquals(List.of(),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES));
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
    var reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "crossing_near_merge_with_crossing_between_its_stop_lines.osm").toString(), CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(Set.of("1", "21", "22"),
        Set.copyOf(diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING)));
    assertEquals(List.of("32"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "crossing_near_merge_with_signal_before_crossing.osm").toString(), CountryNames.AUSTRALIA);
    var stopLineLayer = reader.read().getTransportLayers().getFirst();
    diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertTrue(diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED).contains("1"));
    var sideStreetJunction = stopLineLayer.getIntersections().stream()
        .filter(intersection -> intersection.getMemberNodes().contains(nodeByOsmId(stopLineLayer, 6)))
        .findFirst().orElseThrow();
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_tram_track.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().activateRailwayParser(true);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_on_road_with_tram_on_the_same_way.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().activateRailwayParser(true);
    reader.getSettings().getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
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
    var reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "crossing_before_side_street_and_tram_track_named_after_road.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().activateRailwayParser(true);
    var layer = reader.read().getTransportLayers().getFirst();
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(
        "crossing_before_side_street_and_tram_track_named_after_side_street.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().activateRailwayParser(true);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    var junction = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(1, layer.getIntersections().size());
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertTrue(junction.getApproachSegments().contains(segment(layer, 2, 6)));
  }

  /** A ramp meter controls no junction or crossing: it is ignored and recorded as such, not made an isolated crossing */
  @Test
  public void rampMeterIgnored() {
    /*
     *     1 ------------------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals=ramp_meter, a shape node; no junction anywhere
     *   breaking links at isolated crossings on: a ramp meter controls no junction or crossing, so it is ignored and
     *   recorded as such; it is not an isolated crossing: no link broken, no intersection
     */
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("ramp_meter.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getLinks().size());
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("2"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.SIGNAL_NOT_CONTROLLING_TRAFFIC));
  }

  /** A traffic signal direction overridden on a signal that controls no junction or crossing is found: the layer has
   * the signal, although it is ignored */
  @Test
  public void directionOverriddenOnRampMeterIsFound() {
    /*
     *     1 ------------------- 2 ------------------- 3     way 10: 1-2-3 motorway_link, one-way, ~150 m either side
     *
     *   2: highway=traffic_signals, traffic_signals=ramp_meter, a shape node; its direction overridden forward
     */
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("ramp_meter.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().overrideTrafficSignalDirection(2, OsmTrafficSignalDirection.FORWARD);
    reader.read();

    assertEquals(List.of(), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND));
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
     *   railway parser on, cycleways parsed, breaking links at isolated crossings on:
     *   11 is on the track 15 m before it crosses the road at 2: 2 is a signalised junction, every segment entering it
     *   an approach, road and track; 21 is on the cycleway 10 m before it meets the road at 4: 4 is a signalised
     *   crossing, the road its only approach, since approaches are for motorised road and rail traffic; 13 has no road
     *   within 50 m along the track: ignored; no link broken: five links
     */
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("track_and_cycleway_signals.osm").toString(), CountryNames.AUSTRALIA);
    var settings = reader.getSettings();
    settings.activateRailwayParser(true);
    settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    settings.getIntersectionSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();

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

    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("11"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_SIGNAL_AT_ROAD_CROSSING));
    assertEquals(List.of("21"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CYCLEWAY_SIGNAL_AT_ROAD_CROSSING));
    assertEquals(List.of("13"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("track_branching_before_road.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().activateRailwayParser(true);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var branch = layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 15));
    assertNotNull(branch);
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), branch.getTypes());
    assertEquals(Set.of(segment(layer, 10, 15)), Set.copyOf(branch.getApproachSegments()));
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("11"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH));
    assertTrue(diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_SIGNAL_AT_ROAD_CROSSING).isEmpty());
    assertTrue(
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING).isEmpty());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("track_branch_ending_before_road.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().activateRailwayParser(true);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var trackCrossing = nodeByOsmId(layer, 2);
    var junction = layer.getIntersections().getByMemberNode(trackCrossing);
    assertNotNull(junction);
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(trackCrossing.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(junction.getApproachSegments()));
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("11"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_SIGNAL_AT_ROAD_CROSSING));
    assertTrue(diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH).isEmpty());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("cycleway_footway_crossing_before_road.osm").toString(),
        CountryNames.AUSTRALIA);
    var settings = reader.getSettings();
    settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    var cyclewayEnd = nodeByOsmId(layer, 2);
    var crossing = layer.getIntersections().getByMemberNode(cyclewayEnd);
    assertNotNull(crossing);
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(segmentsForMotorisedRoadOrRailEntering(cyclewayEnd), Set.copyOf(crossing.getApproachSegments()));
    assertEquals(List.of("21"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.CYCLEWAY_SIGNAL_AT_ROAD_CROSSING));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_on_footway_before_road.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    var layer = reader.read().getTransportLayers().getFirst();

    assertTrue(layer.getIntersections().isEmpty());
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("21"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRAFFIC_SIGNAL_NOT_ON_ROAD_TRACK_OR_CYCLEWAY));
    assertTrue(
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING).isEmpty());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_signal_on_crossing_way_only.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    reader.read();

    var networkData = reader.getNetworkReaderData();
    var diagnostics = networkData.getParseDiagnostics();
    assertEquals(Set.of(1L), Set.copyOf(networkData.getTrafficSignalOsmNodeIds()));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ONLY_ON_CROSSING_WAY));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ON_JUNCTION_NODE));
    assertTrue(diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_TAGS_INCONSISTENT).isEmpty());
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_way_signal_node_says_zebra.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getIntersections().size());
    assertNotNull(layer.getIntersections().getByMemberNode(nodeByOsmId(layer, 1)));
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ONLY_ON_CROSSING_WAY));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_SIGNAL_TAGS_INCONSISTENT));
  }

  /** A vehicle signal whose junction is where its road ends outside the parsed area is noted as such, alongside its
   * outcome: on the junction node when on the road end itself, snapped when looking ahead to it (scenarios and
   * drawings: OsmTrafficSignalsTest, vehicleSignalWhereRoadEndsOutsideDeclaredBoundsMakesItsNodeTheJunction and
   * vehicleSignalBeforeRoadEndOutsideParsedAreaSnapsToRoadEnd) */
  @Test
  public void vehicleSignalAtRoadEndOutsideParsedAreaNoted() {
    var onRoadEnd = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_where_road_ends_outside_parsed_area.osm").toString(),
        CountryNames.AUSTRALIA);
    onRoadEnd.read();
    var diagnostics = onRoadEnd.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("5"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA));
    assertEquals(List.of("5"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_JUNCTION_NODE));

    IdGenerator.reset();
    var beforeRoadEnd = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_road_end_outside_parsed_area.osm").toString(),
        CountryNames.AUSTRALIA);
    beforeRoadEnd.read();
    diagnostics = beforeRoadEnd.getNetworkReaderData().getParseDiagnostics();
    assertEquals(List.of("6"),
        diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA));
    assertEquals(List.of("6"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_road_end_outside_parsed_area.osm").toString(),
        CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(20);
    var layer = reader.read().getTransportLayers().getFirst();

    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("6"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
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
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_on_paths_and_pruned_island.osm").toString(),
        CountryNames.AUSTRALIA);
    var settings = reader.getSettings();
    settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
    settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    settings.getHighwaySettings().deactivateAllOsmRoadModesExcept(
        OsmRoadModeTags.MOTOR_CAR, OsmRoadModeTags.FOOT, OsmRoadModeTags.BICYCLE);
    settings.activateRemoveDanglingSubnetworks(PredefinedModeType.PEDESTRIAN);
    settings.activateRemoveDanglingSubnetworks(PredefinedModeType.BICYCLE);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(2, diagnostics.getIntersectionsCreated());
    assertEquals(List.of("40"), diagnostics.getEntityIds(OsmPlanitEntityIssue.INTERSECTION_LOST_BY_PRUNING));
    assertEquals(List.of("30"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.INTERSECTION_REMOVED_WITHOUT_CONTROLLED_APPROACH));
    assertTrue(layer.getIntersections().isEmpty());
  }

  /**
   * Signals that create nothing are still counted: a mid-block crossing with breaking links at isolated crossings off,
   * and a vehicle signal beyond a tighter maximum distance
   */
  @Test
  public void signalsCreatingNothingCounted() {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("mid_block_crossing.osm").toString(), CountryNames.AUSTRALIA);
    reader.read();
    assertEquals(List.of("31"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.ISOLATED_CROSSING_NO_LINK_BROKEN));

    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("signal_12m_before_junction.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().getIntersectionSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(10);
    reader.read();
    assertEquals(1, reader.getNetworkReaderData().getParseDiagnostics()
        .getOccurrences(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
  }
}

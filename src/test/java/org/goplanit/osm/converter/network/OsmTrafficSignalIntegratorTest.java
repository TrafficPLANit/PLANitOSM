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
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.topobyte.osm4j.core.model.iface.OsmNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.nodeByOsmId;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;

/**
 * What the traffic signal integrator records about the signals it integrates, beyond the intersections it creates
 */
public class OsmTrafficSignalIntegratorTest {

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

  /**
   * The OSM node ids of signals
   *
   * @param signals to collect the ids of, may be null
   * @return ids in order, empty when none
   */
  private static List<Long> osmIds(List<OsmNode> signals) {
    return signals == null ? List.of() : signals.stream().map(OsmNode::getId).collect(Collectors.toList());
  }

  /** An approach cut short by breaking its link at an isolated crossing keeps the signal it is an approach for, now
   * recorded for the piece arriving at the junction (scenario and drawing: OsmTrafficSignalsTest,
   * crossingBreaksJunctionApproach) */
  @Test
  public void signalFollowsApproachBrokenAtCrossing() {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_breaks_junction_approach.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();
    var signalsByApproach = reader.getNetworkReaderData().getLayerParsers().get(layer)
        .getTrafficSignalIntegrator().getSignalsByApproach();

    assertEquals(List.of(1L), osmIds(signalsByApproach.get(segment(layer, 31, 1))));
    /* the piece left behind is an approach of the crossing intersection at 31, for the crossing signal only */
    assertEquals(List.of(31L), osmIds(signalsByApproach.get(segment(layer, 2, 31))));
  }

  /** What became of each signal is recorded per outcome with the signal's id: the vehicle signal on the junction's
   * centre, and the crossing signal 10 m before it, footways not parsed, part of that junction (scenario and drawing:
   * OsmTrafficSignalsTest, crossingNearSignalisedJunction) */
  @Test
  public void outcomeRecordedPerSignal() {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("crossing_near_signalised_junction.osm").toString(), CountryNames.AUSTRALIA);
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
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
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    reader.getSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(40);
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
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();
    var signalsByApproach = reader.getNetworkReaderData().getLayerParsers().get(layer)
        .getTrafficSignalIntegrator().getSignalsByApproach();

    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD));
    assertEquals(List.of(), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
    assertEquals(1, reader.getNetworkReaderData().getPlanitEntityDiagnostics().getIntersectionsCreated());
    assertEquals(List.of(31L, 1L), osmIds(signalsByApproach.get(segment(layer, 2, 31))));
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));
  }

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
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    reader.getSettings().setBoundingArea(boundingArea);
    return reader;
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
    reader.getSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(40);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getParseDiagnostics();

    assertEquals(List.of("31"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA));
    assertEquals(List.of("1"), diagnostics.getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
    assertTrue(layer.getIntersections().isEmpty());
  }

  /**
   * A vehicle signal is the stop line of an isolated crossing also when the crossing lies past a junction the signal
   * passes over for a higher class one further along
   */
  @Test
  public void vehicleSignalStopsAtIsolatedCrossingPastSideStreet() {
    /*
     *                  9                 7
     *                  |                 |
     *   2 ------- 1 -> 4 - 31 ---------- 5 -------- 3     way 10 primary, two-way; signal 1 (forward); residential
     *               ~10 m ~13 m    ~48 m |                way 11 (9-4); signalised crossing 31; primary way 12 (7-5-8)
     *                                    8
     *
     *   the signal resolves to junction 5, passing side street 4 for the higher class road; crossing 31 lies between, 35 m
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
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(1, layer.getIntersections().size());
    var crossing = layer.getIntersections().stream().findFirst().orElseThrow();
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertEquals(Set.of("31", "1"), Set.of(crossing.getSplitExternalId()));

    /* crossing within the crossing distance of the junction: linked to the junction */
    IdGenerator.reset();
    reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("vehicle_signal_before_crossing_near_junction.osm").toString(),
        CountryNames.AUSTRALIA);
    layer = reader.read().getTransportLayers().getFirst();
    assertEquals(List.of("1"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED));
    assertEquals(List.of(nodeByOsmId(layer, 6)), layer.getIntersections().stream().findFirst().orElseThrow()
        .getMemberNodes());

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
        .getSignalIds(OsmTrafficSignalOutcome.CROSSING_ISOLATED_PAST_LOWEST_ORDER_ROAD));

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
    reader.getSettings().setBreakLinksAtIsolatedSignalisedCrossings(true);
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(1, layer.getLinks().size());
    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("2"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.SIGNAL_NOT_CONTROLLING_TRAFFIC));
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
     *   crossing; 13 has no road within 50 m along the track: ignored; no link broken: five links
     */
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("track_and_cycleway_signals.osm").toString(), CountryNames.AUSTRALIA);
    var settings = reader.getSettings();
    settings.activateRailwayParser(true);
    settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.CYCLEWAY);
    settings.setBreakLinksAtIsolatedSignalisedCrossings(true);
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
    assertEquals(cyclewayEnd.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(crossing.getApproachSegments()));

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
     *   beyond 21 the footway crosses at 22, not followed: 2 is a signalised crossing, every segment entering it an
     *   approach
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
    assertEquals(cyclewayEnd.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(crossing.getApproachSegments()));
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
    reader.getSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(20);
    var layer = reader.read().getTransportLayers().getFirst();

    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(List.of("6"), reader.getNetworkReaderData().getParseDiagnostics()
        .getSignalIds(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA));
  }

  /** What became of the intersections is recorded with their ids: one pruned away with its island, one without an
   * approach any vehicle a traffic signal controls can use */
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
     *   stays, with only footway and cycleway approaches
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
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(2, diagnostics.getIntersectionsCreated());
    assertEquals(List.of("40"), diagnostics.getEntityIds(OsmPlanitEntityIssue.INTERSECTION_LOST_BY_PRUNING));
    assertEquals(List.of("30"), diagnostics.getEntityIds(OsmPlanitEntityIssue.INTERSECTION_WITHOUT_CONTROLLED_APPROACH));
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
    reader.getSettings().setMaximumDistanceVehicleSignalToJunctionInMeters(10);
    reader.read();
    assertEquals(1, reader.getNetworkReaderData().getParseDiagnostics()
        .getOccurrences(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD));
  }
}

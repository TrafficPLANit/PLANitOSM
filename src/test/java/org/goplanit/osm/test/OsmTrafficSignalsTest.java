package org.goplanit.osm.test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.intersection.Intersection;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionControlType;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.nodeByOsmId;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;

/**
 * Tests turning OSM traffic signal tagging into intersections, one small hand-made OSM file per scenario
 *
 * @author markr
 */
public class OsmTrafficSignalsTest {

  /** location of the hand-made scenario files */
  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  @AfterAll
  public static void tearDown() {
    IdGenerator.reset();
  }

  /** read a scenario file with the given settings changes, and return its only layer */
  private static MacroscopicNetworkLayer parse(String scenario, Consumer<OsmNetworkReaderSettings> configure) {
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(scenario + ".osm").toString(), CountryNames.AUSTRALIA);
    configure.accept(reader.getSettings());
    var network = reader.read();
    assertEquals(1, network.getTransportLayers().size());
    return network.getTransportLayers().getFirst();
  }

  /** read a scenario file with default settings */
  private static MacroscopicNetworkLayer parse(String scenario) {
    return parse(scenario, settings -> {});
  }

  /** the intersection whose member node was created for an OSM node */
  private static Intersection intersectionAt(MacroscopicNetworkLayer layer, long osmNodeId) {
    var node = nodeByOsmId(layer, osmNodeId);
    return layer.getIntersections().stream()
        .filter(intersection -> intersection.hasMemberNode(node)).findFirst().orElseThrow();
  }

  /** the layer's only intersection */
  private static Intersection onlyIntersection(MacroscopicNetworkLayer layer) {
    assertEquals(1, layer.getIntersections().size());
    return layer.getIntersections().stream().findFirst().orElseThrow();
  }

  /** A junction without traffic signals gets no intersection; the network itself is read as usual */
  @Test
  public void junctionWithoutSignalsHasNoIntersection() {
    /*
     *              4
     *              |
     *     2 ------ 1 ------ 3      way 10: 2-1-3, way 11: 4-1-5, both primary and two-way,
     *              |                arms of about 100 m, no tags on any node
     *              5
     */
    var layer = parse("junction_without_signals");

    assertEquals(5, layer.getNodes().size());
    assertEquals(4, layer.getLinks().size());
    assertTrue(layer.getIntersections().isEmpty());
  }

  /** A stop sign is not a traffic signal, so it creates no intersection */
  @Test
  public void stopSignIgnored() {
    /*
     *              4
     *              |
     *     2 ------ 1 ------ 3      way 10: 2-1-3, way 11: 4-1-5, both primary and two-way,
     *              |                arms of about 100 m
     *              5
     *
     *   1: highway=stop
     */
    var layer = parse("stop_sign");

    assertEquals(5, layer.getNodes().size());
    assertTrue(layer.getIntersections().isEmpty());
  }

  /** A vehicle signal on the shared node of two crossing two-way roads makes that node one signalised junction,
   * with every segment entering it as an approach */
  @Test
  public void fourArmJunctionTaggedOnItsCentre() {
    /*
     *              4
     *              |
     *              v
     *     2 --->-- 1 --<--- 3      way 10: 2-1-3, way 11: 4-1-5, both primary and two-way,
     *              ^                arms of about 100 m; arrows mark the approaches
     *              |
     *              5
     *
     *   1: highway=traffic_signals
     */
    var layer = parse("four_arm_junction_signal_on_centre");

    var intersection = onlyIntersection(layer);
    var centre = nodeByOsmId(layer, 1);
    assertEquals(List.of(centre), intersection.getMemberNodes());
    assertEquals(IntersectionControlType.SIGNALISED, intersection.getControlType());
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), intersection.getTypes());
    assertEquals("1", intersection.getExternalId());
    assertEquals(4, intersection.getApproachSegments().size());
    assertEquals(centre.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(intersection.getApproachSegments()));
  }

  /** A vehicle signal on a junction's centre leaves out only the segment entering against a one-way arm leading
   * away from it: that arm's traffic exits the junction; a footway and a tram track entering it are still approaches */
  @Test
  public void junctionOnCentreLeavesOutOnlyTheOneWayArmLeavingIt() {
    /*
     *           6  4
     *            : |
     *             :v
     *     2 --->-- 1 ======> 3      way 10: 2-1 primary, two-way; way 12: 1-3 primary, one-way away from the centre
     *             ^^                way 11: 4-1-5 primary, two-way; way 13: 6-1 footway, parsed; way 14: 7-1 tram,
     *            # |                parsed; arms of about 100 m; arrows mark the approaches
     *           7  5
     *
     *   1: highway=traffic_signals, on the junction's centre
     *   way 12's segment 3->1 runs against its direction, for walking only: its link carries motorised traffic away
     *   from the centre only, so it is not an approach; the footway's segment 6->1 and the tram's 7->1 are: people on
     *   foot and trams approach the junction along them; the junction has five approaches: 2->1, 4->1, 5->1, 6->1,
     *   7->1
     */
    var layer = parse("one_way_arm_leaving_junction", settings -> {
      settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY);
      settings.activateRailwayParser(true);
    });

    var intersection = onlyIntersection(layer);
    assertNotNull(segment(layer, 3, 1), "the walking segment against the one-way arm enters the junction");
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 4, 1), segment(layer, 5, 1), segment(layer, 6, 1),
            segment(layer, 7, 1)),
        Set.copyOf(intersection.getApproachSegments()));
  }

  /** With footways parsed, a crossing signal where a footway crosses the road makes that node its own signalised
   * crossing, the footway segments entering it included as approaches */
  @Test
  public void signalisedCrossingWithFootwaysParsed() {
    /*
     *              4
     *              :
     *              v
     *     2 --->-- 1 --<--- 3      way 10: 2-1-3 primary, two-way (---)
     *              ^                way 11: 4-1-5 footway, two-way (:)
     *              :                arms of about 100 m; arrows mark the approaches
     *              5
     *
     *   1: highway=crossing, crossing=traffic_signals
     */
    var layer = parse("crossing_signal_footways_parsed",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));

    var intersection = onlyIntersection(layer);
    var crossing = nodeByOsmId(layer, 1);
    assertEquals(List.of(crossing), intersection.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), intersection.getTypes());
    assertEquals("1", intersection.getExternalId());
    assertEquals(4, intersection.getApproachSegments().size());
    assertEquals(crossing.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(intersection.getApproachSegments()));
  }

  /** A crossing signal tagged only on the crossing way makes the node where it meets the road a signalised crossing,
   * as if the node carried it; the ends of the crossing way are no crossing */
  @Test
  public void crossingSignalTaggedOnlyOnCrossingWay() {
    /*
     *              4
     *              :
     *              v
     *     2 --->-- 1 --<--- 3      way 10: 2-1-3 primary, two-way (---)
     *              ^                way 11: 4-1-5 footway, footway=crossing, crossing=traffic_signals (:)
     *              :                arms of about 100 m; arrows mark the approaches
     *              5
     *
     *   1: highway=crossing, crossing=marked, so the node says nothing about signals
     */
    var layer = parse("crossing_signal_on_crossing_way_only",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));

    var intersection = onlyIntersection(layer);
    var crossing = nodeByOsmId(layer, 1);
    assertEquals(List.of(crossing), intersection.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), intersection.getTypes());
    assertEquals(crossing.streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(intersection.getApproachSegments()));
  }

  /** A crossing signal tagged only on the crossing way counts though footways are not parsed: the road node it crosses
   * at is an isolated signalised crossing */
  @Test
  public void crossingSignalTaggedOnlyOnCrossingWayFootwaysNotParsed() {
    /*
     *     2 --->-- 1 --<--- 3      way 10: 2-1-3 primary, two-way (---); arrows mark the approaches
     *                               way 11: 4-1-5 footway crossing at 1, not parsed, crossing=traffic_signals
     *
     *   1: highway=crossing, crossing=marked; breaking links at isolated signalised crossings on
     */
    var layer = parse("crossing_signal_on_crossing_way_only",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 1)), intersection.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), intersection.getTypes());
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 3, 1)), Set.copyOf(intersection.getApproachSegments()));
  }

  /** A vehicle signal where its road ends outside the bounds the input file declares makes that node its junction: the
   * junction itself is cut off by the edge, and the node where the parsed road ends stands in for it */
  @Test
  public void vehicleSignalWhereRoadEndsOutsideDeclaredBoundsMakesItsNodeTheJunction() {
    /*
     *                         4
     *                         |                |
     *     1 --------------->- 2 ----->-------- | -- 5      way 10: 1-2-5 primary, two-way; way 11: 2-4 secondary
     *                         <- about 37 m -> | <- 18 m   arrow marks the approach of 5
     *                                    edge of the declared bounds (lon 151.2120)
     *
     *   5: highway=traffic_signals, where the road ends outside the bounds; no bounding area set
     */
    var layer = parse("vehicle_signal_where_road_ends_outside_parsed_area");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 5)), intersection.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), intersection.getTypes());
    assertEquals(List.of(segment(layer, 2, 5)), intersection.getApproachSegments());
  }

  /** A bounding area marks the edge of the parsed area too, over the bounds the input file declares */
  @Test
  public void vehicleSignalWhereRoadEndsOutsideBoundingAreaMakesItsNodeTheJunction() {
    /*
     *                         4
     *                         |                |
     *     1 --------------->- 2 ----->-------- | -- 5      way 10: 1-2-5 primary, two-way; way 11: 2-4 secondary
     *                                          |           the declared bounds reach past 5, the bounding area
     *                                    edge of the bounding area (lon 151.2120)
     *
     *   5: highway=traffic_signals, where the road ends outside the bounding area
     */
    var layer = parse("vehicle_signal_where_road_ends_inside_parsed_area",
        settings -> settings.setBoundingArea(OsmBoundary.of(151.2060, 151.2120, -33.8720, -33.8680)));

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 5)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 2, 5)), intersection.getApproachSegments());
  }

  /** Where a road ends inside the parsed area, it really ends, so a vehicle signal there does not make a junction of
   * it, but looks for the junction along the road as before */
  @Test
  public void vehicleSignalWhereRoadEndsInsideParsedAreaSnapsToJunctionAlongRoad() {
    /*
     *                         4
     *                         |                         |
     *     1 ----------------- 2 -<--------------- 5     |   way 10: 1-2-5 primary, two-way; way 11: 2-4 secondary
     *                         <- about 37 m ->          |   arrow marks the approach of 2
     *                                    edge of the declared bounds (lon 151.2140)
     *
     *   5: highway=traffic_signals, where the road ends inside the bounds
     */
    var layer = parse("vehicle_signal_where_road_ends_inside_parsed_area");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 2)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 5, 2)), intersection.getApproachSegments());
  }

  /** A vehicle signal looking ahead along its road takes the node where the road ends outside the parsed area as the
   * junction it controls, when it lies within reach */
  @Test
  public void vehicleSignalBeforeRoadEndOutsideParsedAreaSnapsToRoadEnd() {
    /*
     *              4
     *              |                 |
     *     1 ------ 2 ------->------- | ---- 6 ---->--- 5      way 10: 1-2-6-5 primary, two-way; way 11: 2-4 secondary
     *                                |   <- about 37 m ->     arrow marks the approach of 5
     *                     edge of the declared bounds (lon 151.2120)
     *
     *   6: highway=traffic_signals, traffic_signals:direction=forward, a shape node outside the bounds; 5: the road
     *   ends there, outside the bounds
     */
    var layer = parse("vehicle_signal_before_road_end_outside_parsed_area");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 5)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 2, 5)), intersection.getApproachSegments());
  }

  /** A node carrying both a vehicle signal and a crossing signal is one intersection of both kinds */
  @Test
  public void bothKindsOnOneNode() {
    /*
     *              4
     *              |
     *              v
     *     2 --->-- 1 --<--- 3      way 10: 2-1-3, way 11: 4-1-5, both primary and two-way,
     *              ^                arms of about 100 m; arrows mark the approaches
     *              |
     *              5
     *
     *   1: highway=traffic_signals, crossing=traffic_signals
     */
    var intersection = onlyIntersection(parse("both_kinds_on_centre"));

    assertEquals(EnumSet.of(IntersectionType.JUNCTION, IntersectionType.CROSSING), intersection.getTypes());
    assertEquals(4, intersection.getApproachSegments().size());
  }

  /** With intersections switched off, a traffic signal creates no intersection */
  @Test
  public void intersectionsSwitchedOff() {
    /*
     * the network of fourArmJunctionTaggedOnItsCentre, 1: highway=traffic_signals, read with intersections off
     *
     *              4
     *              |
     *     2 ------ 1 ------ 3
     *              |
     *              5
     */
    var layer = parse("four_arm_junction_signal_on_centre", settings -> settings.setParseIntersections(false));

    assertTrue(layer.getIntersections().isEmpty());
    assertEquals(5, layer.getNodes().size());
  }

  /** A signal on a node where only the two links of one road meet is not on a junction node: that node gets no
   * intersection of its own, the signal snaps to the junction ahead, whose intersection gets the segment leaving the
   * signal as an approach */
  @Test
  public void signalOnTwoLinkNodeSnapsToJunctionAhead() {
    /*
     *                               7
     *                               |
     *     2 ------------ 1 -------- 6 ------ 3      way 10: 2-1, way 11: 1-6-3, way 12: 7-6-8,
     *                        ~30 m  |                all primary and two-way
     *                               8
     *
     *   1: highway=traffic_signals; ways 10 and 11 meet there, so it is a PLANit node with only two links
     *   6: junction node, four links, no tags
     *   expected: junction 6 gets approach 1->6 (forward along way 11); backward, way 10's segment 1->2 reaches dead
     *   end 2, so nothing; no intersection at 1
     */
    var layer = parse("signal_on_two_link_node_before_junction");

    var signalNode = nodeByOsmId(layer, 1);
    assertEquals(2, signalNode.getNumberOfEdges());
    assertTrue(layer.getIntersections().stream().noneMatch(intersection -> intersection.hasMemberNode(signalNode)));

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals("1", intersection.getExternalId());
    assertEquals(List.of(segment(layer, 1, 6)), intersection.getApproachSegments());
  }

  /** An untagged vehicle signal on a shape node of a two-way road applies to one direction of travel, the one whose
   * junction is nearest: that junction gets the segment the signal lies on as an approach */
  @Test
  public void signalWithoutDirectionSnapsToNearestJunction() {
    /*
     *                7                       9
     *                |                       |
     *     2 -------- 6 --- 1 --------------- 8 -------- 3      way 10: 2-6-1-8-3 primary, two-way
     *                |  ~20 m      ~40 m     |                 way 11: 7-6-5, way 12: 9-8-4, primary, two-way
     *                5                       4
     *
     *   1: highway=traffic_signals, a shape node of way 10 (only way 10 uses it)
     *   6, 8: junctions, internal to way 10 and shared with ways 11 and 12, so PLANit breaks way 10 there
     *   expected: junction 6, the nearer, gets approach 8->6, the segment of link 6-8 the signal lies on; junction 8
     *   gets nothing
     */
    var layer = parse("signal_on_shape_node_between_junctions");

    var west = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), west.getMemberNodes());
    assertEquals(List.of(segment(layer, 8, 6)), west.getApproachSegments());
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), west.getTypes());
    assertEquals("1", west.getExternalId());
  }

  /** An untagged vehicle signal with junctions in both directions within the near tie delta distance of each other
   * goes to the one where the road of higher capacity crosses, even when that one is farther */
  @Test
  public void signalWithoutDirectionPrefersHigherCapacityJunctionOnNearTie() {
    /*
     *                   9        7
     *                   |        |
     *     2 ----------- 4 -- 1 --- 6 --------- 3      way 10: 2-4-1-6-3 primary, two-way
     *                     ~12 m ~18.5 m               way 11: 9-4 residential, way 12: 7-6-8 primary, two-way
     *                            8
     *
     *   1: highway=traffic_signals without a direction, a shape node of way 10
     *   expected: junctions 4 and 6 lie within 10 m of each other's distance, so the primary road crossing at 6 wins:
     *   junction 6 gets approach 4->6, junction 4 gets nothing
     */
    var layer = parse("signal_without_direction_near_tie");

    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 4, 6)), junction.getApproachSegments());
  }

  /** An untagged vehicle signal compares, per direction, the junction it would be assigned to there: a side street
   * passed over for a higher class junction further along does not make that direction the nearer */
  @Test
  public void signalWithoutDirectionComparesAssignedJunctions() {
    /*
     *              7                 10
     *              |                 |
     *     2 ------ 5 ---- 4 --- 1 -- 6 --------- 3      way 10: 2-5-4-1-6-3 primary, two-way
     *              |~30 m | ~12 m ~15 m                 way 12: 7-5-8 primary; ways 11 (9-4) and 13 (10-6) residential
     *              8      9
     *
     *   1: highway=traffic_signals without a direction, a shape node of way 10
     *   expected: west resolves to primary junction 5 (30 m), passing side street 4; east to side street 6 (15 m), more
     *   than 10 m nearer, so junction 6 gets approach 4->6, nothing else
     */
    var layer = parse("signal_without_direction_side_street_before_primary");

    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 4, 6)), junction.getApproachSegments());
  }

  /** An untagged vehicle signal choosing its direction does not count a junction where only a service lane without
   * signals of its own joins, as for signalised crossings, also when the lane can only be driven out of */
  @Test
  public void signalWithoutDirectionSkipsServiceLaneWhenChoosing() {
    /*
     *                9           7
     *                |           |
     *     2 -------- 4 ---- 5 -- 1 ---- 6 --------- 3      way 10: 2-4-5-1-6-3 primary, two-way
     *                 ~33 m |~5 m  ~22 m                   ways 11 (9-4) and 13 (7-6) residential, two-way; way 12
     *                       10                             (5-10) a one-way driveway leaving way 10
     *
     *   1: highway=traffic_signals without a direction, a shape node of way 10
     *   expected: the service lane at 5 does not count, so junction 6 (22 m) is nearer than junction 4 (33 m): junction
     *   6 gets approach 5->6, nothing else
     */
    var layer = parse("signal_without_direction_past_service_lane");

    var junction = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), junction.getMemberNodes());
    assertEquals(List.of(segment(layer, 5, 6)), junction.getApproachSegments());
  }

  /** A vehicle signal on a shape node where PLANit broke the road for a parsed footway: only the road's two links
   * are usable by motorised modes, so for the vehicle signal the node is a crosswalk, not a junction, and it snaps to
   * the junction ahead along the pieces of the broken way */
  @Test
  public void signalOnNodeBrokenForFootwaySnapsToJunctionAhead() {
    /*
     *                7
     *                |
     *     2 -------- 6 ------ 1 ---------- 3      way 10: 2-6-1-3 primary, two-way (one way in OSM)
     *                |  ~30 m :                   way 11: 7-6-8 primary, two-way (makes 6 a junction)
     *                8        :                   way 12: 1-4 footway, parsed
     *                         4
     *
     *   1: highway=traffic_signals, a shape node of way 10 until PLANit breaks way 10 there for the footway ending at
     *      it; two road links and one footway link then meet at 1: a junction for a crossing signal, but a crosswalk
     *      for a vehicle signal, since only links usable by motorised road modes count for it
     *   expected: junction 6 gets approach 1->6 (backward along way 10's piece 6-1); forward, 1->3 reaches dead end 3;
     *   the footway is not followed; no intersection at 1
     */
    var layer = parse("signal_on_node_broken_for_footway",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));

    var signalNode = nodeByOsmId(layer, 1);
    assertEquals(3, signalNode.getNumberOfEdges());
    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 1, 6)), intersection.getApproachSegments());
  }

  /** A vehicle signal followed along the road passes through a node where the road simply continues, and the
   * segment arriving at the junction beyond becomes its approach */
  @Test
  public void signalWalksThroughTwoLinkNodeToJunction() {
    /*
     *                                          7
     *                                          |
     *     2 --------- 1 --------- 5 ---------- 6 -------- 3      way 10: 2-1-5, way 11: 5-6-3, way 12: 7-6-8,
     *                    ~28 m        ~23 m    |                 all primary and two-way
     *                 |<------- ~51 m ------->|
     *                                          8
     *
     *   1: highway=traffic_signals, a shape node of way 10; the only signal
     *   5: no tags; ways 10 and 11 meet end to end there, a PLANit node with only two links, so the road continues
     *   6: junction node, four links, about 51 m from the signal: just over the 50 m default, so the maximum distance
     *      from a vehicle signal to its junction is raised to 60 m to keep the junction within reach
     *   expected: forward, the signal's segment 2->5 reaches 5, the road continues on 5->6 to junction 6, which gets
     *   approach 5->6; backward, 5->2 reaches dead end 2, so nothing; no intersection at 5
     */
    var layer = parse("signal_with_two_link_node_before_junction",
        settings -> settings.setMaximumDistanceVehicleSignalToJunctionInMeters(60));

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 5, 6)), intersection.getApproachSegments());
  }

  /** A vehicle signal with no junction within the maximum distance is an isolated crossing: with default settings
   * it creates nothing and breaks no link, with breaking links at isolated crossings on the link is broken at it and it
   * is its own crossing intersection */
  @Test
  public void vehicleSignalFarFromAnyJunctionOnShapeNode() {
    /*
     *                                              7
     *                                              |
     *     2 ------ 1 ----------------------------- 6 ------ 3      way 10: 2-1-6-3 primary, two-way
     *        ~46 m               ~148 m            |               way 11: 7-6-8 primary, two-way
     *                                              8
     *
     *   1: highway=traffic_signals, a shape node of way 10
     *   6: junction node, about 148 m from the signal, beyond the 50 m default maximum distance
     *   forward, junction 6 is out of reach; backward, dead end 2; so the signal is an isolated crossing
     *   expected, default settings (breaking links at isolated signalised crossings off): no intersection and no link
     *   broken: four links (2-6, 6-3, 7-6, 6-8)
     *   expected, setting on: way 10 broken at 1, five links; 1 is a signalised crossing intersection with approaches
     *   2->1 and 6->1 and external id 1
     */
    var off = parse("signal_far_from_junction_on_shape_node");
    assertTrue(off.getIntersections().isEmpty());
    assertEquals(4, off.getLinks().size());

    IdGenerator.reset();
    var on = parse("signal_far_from_junction_on_shape_node",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));
    assertEquals(5, on.getLinks().size());
    var crossing = onlyIntersection(on);
    assertEquals(List.of(nodeByOsmId(on, 1)), crossing.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals("1", crossing.getExternalId());
    assertEquals(Set.of(segment(on, 2, 1), segment(on, 6, 1)), Set.copyOf(crossing.getApproachSegments()));
  }

  /** A vehicle signal with no junction within the maximum distance, on a node where two links meet, is an isolated
   * crossing that needs no link broken */
  @Test
  public void vehicleSignalFarFromAnyJunctionOnTwoLinkNode() {
    /*
     *     2 ------------------- 1 ------------------- 3    way 10: 2-1 primary, way 11: 1-3 primary, both two-way
     *            ~150 m                ~150 m              no junction anywhere
     *
     *   1: highway=traffic_signals, where the two ways meet: a PLANit node with two links
     *   expected, default settings (breaking links at isolated signalised crossings off): no intersection, two links
     *   expected, setting on: 1 is a signalised crossing intersection with approaches 2->1 and 3->1; still two links,
     *   no break needed
     */
    var off = parse("signal_far_from_junction_on_two_link_node");
    assertTrue(off.getIntersections().isEmpty());
    assertEquals(2, off.getLinks().size());

    IdGenerator.reset();
    var on = parse("signal_far_from_junction_on_two_link_node",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));
    assertEquals(2, on.getLinks().size());
    var crossing = onlyIntersection(on);
    assertEquals(List.of(nodeByOsmId(on, 1)), crossing.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(Set.of(segment(on, 2, 1), segment(on, 3, 1)), Set.copyOf(crossing.getApproachSegments()));
  }

  /** The maximum distance from a vehicle signal to its junction decides whether a signal 12 m before a junction
   * reaches it */
  @Test
  public void tighterMaximumDistance() {
    /*
     *                                   7
     *                                   |
     *     2 ------------------- 1 ----- 6 ------ 3      way 10: 2-1-6-3 primary, two-way
     *                             ~12 m |                way 11: 7-6-8 primary, two-way
     *                                   8
     *
     *   1: highway=traffic_signals, a shape node of way 10, about 12 m before junction 6
     *   expected: with the default maximum distance (50 m) junction 6 gets approach 2->6, the segment the signal lies
     *   on; with the maximum distance set to 10 m the junction is out of reach, so no intersection and no link broken
     */
    var defaults = parse("signal_12m_before_junction");
    assertEquals(List.of(segment(defaults, 2, 6)), onlyIntersection(defaults).getApproachSegments());

    IdGenerator.reset();
    var tighter = parse("signal_12m_before_junction",
        settings -> settings.setMaximumDistanceVehicleSignalToJunctionInMeters(10));
    assertTrue(tighter.getIntersections().isEmpty());
    assertEquals(4, tighter.getLinks().size());
  }

  /** An untagged vehicle signal on a one-way road applies only in the direction motorised modes can travel, even
   * where pedestrians may walk the other way */
  @Test
  public void oneWayRoad() {
    /*
     *                7                       9
     *                |                       |
     *     2 ->-->--- 6 ->- 1 ->----->------- 8 ->-->--- 3      way 10: 2-6-1-8-3 primary, oneway=yes (west to east)
     *                |  ~20 m      ~40 m     |                 way 11: 7-6-5, way 12: 9-8-4, primary, two-way
     *                5                       4
     *
     *   1: highway=traffic_signals, no direction tag, a shape node of way 10
     *   pedestrians may walk way 10 both ways, so link 6-8 has a pedestrian-only segment 8->6 against the traffic
     *   expected: junction 8 gets approach 6->8; junction 6 gets nothing, since no motorised mode travels 8->6
     */
    var layer = parse("signal_on_oneway_road_between_junctions");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 8)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 6, 8)), intersection.getApproachSegments());
  }

  /** A vehicle signal followed along the road passes through a crosswalk, where a parsed footway crosses the road:
   * only links usable by motorised road modes count towards a junction for a vehicle signal */
  @Test
  public void vehicleSignalBeforeCrosswalkWithFootwaysParsed() {
    /*
     *                          20      7
     *                           :      |
     *     2 ---------------- 1 -5 ---- 6 ------ 3      way 10: 2-1-5-6-3 primary, two-way
     *                        ~5 m : ~10 m|             way 11: 20-5-21 footway, parsed
     *                          21      8               way 12: 7-6-8 primary, two-way
     *
     *   1: highway=traffic_signals, a shape node of way 10 at the stop line
     *   5: crosswalk, where the footway crosses; two road links and two footway links meet there
     *   6: junction node, four road links
     *   expected: forward, the signal's segment 2->5 reaches crosswalk 5, passed through, then 5->6 reaches junction 6,
     *   which gets approach 5->6; backward, 5->2 reaches dead end 2; no intersection at 5
     *
     *   the crosswalk itself is left alone here because node 5 carries no signal tag. Had it carried a crossing signal
     *   (crossing=traffic_signals), it would become its own crossing intersection as well, next to junction 6's: a
     *   crossing signal counts every link, so with footways parsed node 5 is a junction for it and it is handled
     *   together with the vehicle signals (see signalisedCrossingWithFootwaysParsed); without footways parsed node 5
     *   would not exist, and the crossing signal is only handled after all vehicle signals, since whether it is part
     *   of a signalised junction depends on the approaches those created
     */
    var layer = parse("signal_before_crosswalk_footways_parsed",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));

    assertEquals(4, nodeByOsmId(layer, 5).getNumberOfEdges());
    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 5, 6)), intersection.getApproachSegments());
  }

  /** A vehicle signal tagged with a direction applies only to traffic in that direction of its OSM way, so a side
   * street behind it does not get the signal, even within the maximum distance */
  @Test
  public void directionTaggedSignal() {
    /*
     *                                         7
     *                                         |
     *     2 ------------ 4 ---------- 1 ----- 6 ------ 3      way 10: 2-4-1-6-3 primary, two-way (west to east in OSM)
     *                    |    ~30 m     ~10 m |               way 11: 4-9 residential, two-way (side street)
     *                    9                    8               way 12: 7-6-8 primary, two-way
     *
     *   1: highway=traffic_signals, traffic_signals:direction=forward, a shape node of way 10; way 10 is broken at 4
     *      and 6, and forward along way 10 stays the A to B direction of each of its links
     *   4: side street junction (three road links), about 30 m behind the signal
     *   6: junction node, about 10 m ahead of the signal
     *   expected: forward (west to east), junction 6 gets approach 4->6; backward is not followed, so side street
     *   junction 4 gets nothing although it is within the 50 m default
     */
    var layer = parse("direction_tagged_signal_with_side_street_behind");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 4, 6)), intersection.getApproachSegments());
  }

  /** A vehicle signal tagged with a direction, where OSM ways of opposite orientation meet, cannot tell which
   * direction is meant: only the nearer of the junctions reached in its two directions gets it */
  @Test
  public void waysDisagreeOnTheDirection() {
    /*
     *                7                    9
     *                |                    |
     *     2 ------>- 5 ------>------ 1 -<- 6 --<----- 3      way 10: 2-5-1, drawn west to east (ends at 1)
     *                |      ~28 m     ~15 m|                  way 11: 3-6-1, drawn east to west (ends at 1)
     *                8                    4                  way 12: 7-5-8, way 13: 9-6-4; all primary, two-way
     *
     *   1: highway=traffic_signals, traffic_signals:direction=forward, where ways 10 and 11 meet: forward is eastbound
     *      for way 10 but westbound for way 11, so the ways disagree on the direction meant
     *   expected: both junctions are within the 50 m default; junction 6 is nearer, so only 6 gets approach 1->6
     */
    var layer = parse("ways_disagree_on_direction");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 1, 6)), intersection.getApproachSegments());
  }

  /** A vehicle signal passes over a side street without signals of its own for a junction further along its road,
   * within the maximum distance, where a road of higher capacity crosses */
  @Test
  public void unsignalisedSideStreetBeforeHigherClassJunction() {
    /*
     *                                  7
     *                                  |
     *     2 ------------------- 1 -4 --- 6 ------ 3      way 10: 2-1-4-6-3 primary, two-way (west to east in OSM)
     *                         ~5 m | ~20 m|              way 11: 9-4 residential side street, no signals of its own
     *                              9     8               way 12: 7-6-8 primary, two-way
     *
     *   1: highway=traffic_signals, traffic_signals:direction=forward, a shape node of way 10
     *   4: side street junction (three road links), about 5 m ahead of the signal, no signal evidence of its own
     *   6: junction with a primary road, about 25 m ahead of the signal, within the 50 m default
     *   expected: the side street is passed over for junction 6, where a road of higher capacity (primary against
     *   residential, capacity per segment) crosses: junction 6 gets approach 4->6, junction 4 gets nothing
     */
    var layer = parse("unsignalised_side_street_before_primary_junction");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 6)), intersection.getMemberNodes());
    assertEquals(List.of(segment(layer, 4, 6)), intersection.getApproachSegments());
  }

  /** A side street with a vehicle signal of its own is a signalised junction: the vehicle signal on the main road
   * before it stays with it, even with a junction where a road of higher capacity crosses further along */
  @Test
  public void signalisedSideStreetBeforeHigherClassJunction() {
    /*
     *                                  7
     *                                  |
     *     2 ------------------- 1 -4 --- 6 ------ 3      way 10: 2-1-4-6-3 primary, two-way (west to east in OSM)
     *                         ~5 m | ~20 m|              way 11: 9-20-4 residential side street, two-way
     *                             20     8               way 12: 7-6-8 primary, two-way
     *                              |
     *                              9
     *
     *   1: highway=traffic_signals, traffic_signals:direction=forward, a shape node of way 10
     *   20: highway=traffic_signals, traffic_signals:direction=forward, a shape node of way 11 about 10 m before 4:
     *       the side street's own signal, which gives junction 4 signal evidence of its own
     *   expected: junction 4 gets approaches 2->4 (from signal 1) and 9->4 (from signal 20); junction 6 gets nothing
     */
    var layer = parse("signalised_side_street_before_primary_junction");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 4)), intersection.getMemberNodes());
    assertEquals(Set.of(segment(layer, 2, 4), segment(layer, 9, 4)), Set.copyOf(intersection.getApproachSegments()));
  }

  /** Vehicle signals at the stop line of every arm, the centre node untagged, make one intersection at the centre
   * with one approach per arm */
  @Test
  public void signalsOnEveryArmCentreUntagged() {
    /*
     *                         4
     *                         |
     *                         24 v
     *                         |
     *     2 ------- 21 > ---- 1 ---- < 23 ------- 3      way 10: 2-21-1-23-3 primary, two-way (west to east)
     *                         |                          way 11: 4-24-1-25-5 primary, two-way (north to south)
     *                         25 ^
     *                         |
     *                         5
     *
     *   21, 23, 24, 25: highway=traffic_signals, each a shape node about 15 m before node 1, tagged with the direction
     *                   towards node 1 (forward on the arms before 1 in way order, backward on those after it)
     *   1: no tags, the junction node
     *   expected: one intersection at 1, approaches 2->1, 3->1, 4->1 and 5->1, one per arm, each from its own signal
     */
    var layer = parse("signals_on_every_arm_centre_untagged");

    var intersection = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 1)), intersection.getMemberNodes());
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 3, 1), segment(layer, 4, 1), segment(layer, 5, 1)),
        Set.copyOf(intersection.getApproachSegments()));
  }

  /** A junction whose centre node and one of its stop lines both carry a vehicle signal has each entering segment
   * as an approach exactly once */
  @Test
  public void centreAndStopLineBothTagged() {
    /*
     *                         4
     *                         |
     *     2 ------- 21 > ---- 1 ------------ 3      way 10: 2-21-1-3 primary, two-way (west to east)
     *                         |                     way 11: 4-1-5 primary, two-way (north to south)
     *                         5
     *
     *   1: highway=traffic_signals, on the junction node
     *   21: highway=traffic_signals, traffic_signals:direction=forward, a shape node about 15 m before 1
     *   expected: one intersection at 1 with four approaches, 2->1 among them once, although both signals give it
     */
    var layer = parse("centre_and_stop_line_both_tagged");

    var approaches = onlyIntersection(layer).getApproachSegments();
    assertEquals(4, approaches.size());
    assertEquals(Set.of(segment(layer, 2, 1), segment(layer, 3, 1), segment(layer, 4, 1), segment(layer, 5, 1)),
        Set.copyOf(approaches));
  }

  /** A junction of a dual carriageway and a two-way road consists of two nodes, one per carriageway: each node with a
   * signal before it is its own single-node intersection, and the road between the carriageways is not an approach */
  @Test
  public void dualCarriagewayJunction() {
    /*
     *                         7
     *                         27 v
     *     2 ->-- 21 > ------- 5 ------->-- 3      way 10: 2-21-5-3 primary, oneway=yes (eastbound carriageway)
     *                         |  ~15 m                way 11: 4-23-6-8 primary, oneway=yes (westbound, ~15 m south)
     *     8 --<------------- 6 ------ < 23 --<-- 4    way 12: 7-27-5-6-29-9 primary, two-way (cross road)
     *                         29 ^
     *                         9
     *
     *   21, 23, 27, 29: highway=traffic_signals, shape nodes about 15 m before the crossing node of their arm, tagged
     *                   with the direction towards it
     *   expected: two intersections, at 5 with approaches 2->5 and 7->5, at 6 with approaches 4->6 and 9->6; the cross
     *   road between the carriageways (5->6, 6->5) is no approach, and the two are not joined into one
     */
    var layer = parse("dual_carriageway_junction");

    assertEquals(2, layer.getIntersections().size());
    assertEquals(Set.of(segment(layer, 2, 5), segment(layer, 7, 5)),
        Set.copyOf(intersectionAt(layer, 5).getApproachSegments()));
    assertEquals(Set.of(segment(layer, 4, 6), segment(layer, 9, 6)),
        Set.copyOf(intersectionAt(layer, 6).getApproachSegments()));
    /* each defined by the signals reaching it */
    assertEquals(Set.of("21", "27"), Set.of(intersectionAt(layer, 5).getSplitExternalId()));
    assertEquals(Set.of("23", "29"), Set.of(intersectionAt(layer, 6).getSplitExternalId()));
  }

  /** A signalised pedestrian crossing along a road far from any junction is isolated: with breaking links at isolated
   * signalised crossings off (the default) nothing changes; on, the link is broken at it and it becomes its own
   * crossing intersection */
  @Test
  public void midBlockCrossing() {
    /*
     *     2 ------------------- 31 ------------------- 3      way 10: 2-31-3 primary, two-way, no junction anywhere
     *               ~150 m               ~150 m
     *
     *   31: highway=crossing, crossing=traffic_signals, a shape node of way 10 (the footway is not in the data)
     *   expected, setting off (default): no intersection and one link
     *   expected, setting on: way 10 broken at 31 into two links; 31 is a signalised crossing intersection with
     *   approaches 2->31 and 3->31 and external id 31
     */
    var off = parse("mid_block_crossing");
    assertTrue(off.getIntersections().isEmpty());
    assertEquals(1, off.getLinks().size());

    IdGenerator.reset();
    var on = parse("mid_block_crossing", settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));
    assertEquals(2, on.getLinks().size());
    var crossing = onlyIntersection(on);
    assertEquals(List.of(nodeByOsmId(on, 31)), crossing.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals("31", crossing.getExternalId());
    assertEquals(Set.of(segment(on, 2, 31), segment(on, 3, 31)), Set.copyOf(crossing.getApproachSegments()));
  }

  /** A crossing signal on the road 10 m before a signalised junction: with footways not parsed it is isolated from the
   * pedestrian network and part of the junction, with footways parsed it is its own crossing next to the junction */
  @Test
  public void crossingNearSignalisedJunction() {
    /*
     *                           8
     *                     21    |                      way 11: 8-1-9 secondary, two-way
     *                     :     |
     *   2 --------------- 31 -- 1 --------------- 3    way 10: 2-31-1-3 primary, two-way
     *                     : ~10 m
     *                     :     |
     *                    20     9                      way 12: 20-31-21 footway, crossing the road at 31
     *
     *   1: highway=traffic_signals, on the junction's centre: a signalised junction
     *   31: highway=crossing, crossing=traffic_signals, a shape node of way 10, ~10 m before 1
     *
     *   footways not parsed (active modes off, the default here): 31 has only the road through it, so the crossing
     *   signal is isolated from the pedestrian network; being within 30 m of junction 1 along its approach 2->1, it is
     *   part of that junction: even with breaking links at isolated crossings on, no link is broken at 31 and the
     *   junction's intersection is unchanged
     *   footways parsed: 31 is where the footway crosses the road, a junction node, so it is its own signalised
     *   crossing intersection next to the junction's, the footway segments included as approaches
     */
    var footwaysNotParsed = parse("crossing_near_signalised_junction",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));
    assertEquals(4, footwaysNotParsed.getLinks().size());
    var junction = onlyIntersection(footwaysNotParsed);
    assertEquals(List.of(nodeByOsmId(footwaysNotParsed, 1)), junction.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(Set.of(segment(footwaysNotParsed, 2, 1), segment(footwaysNotParsed, 3, 1),
            segment(footwaysNotParsed, 8, 1), segment(footwaysNotParsed, 9, 1)),
        Set.copyOf(junction.getApproachSegments()));

    IdGenerator.reset();
    var footwaysParsed = parse("crossing_near_signalised_junction",
        settings -> settings.getHighwaySettings().activateOsmHighwayType(OsmHighwayTags.FOOTWAY));
    assertEquals(2, footwaysParsed.getIntersections().size());
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), intersectionAt(footwaysParsed, 1).getTypes());
    var crossing = intersectionAt(footwaysParsed, 31);
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(4, crossing.getApproachSegments().size());
    assertEquals(nodeByOsmId(footwaysParsed, 31).streamEntrySegments().collect(Collectors.toSet()),
        Set.copyOf(crossing.getApproachSegments()));
  }

  /** A crossing signal on the exit arm of a signalised junction, past it along a one-way road, is part of that junction
   * when within the crossing distance of it, although looking along the traffic it faces away from the junction */
  @Test
  public void crossingOnExitArmOfSignalisedJunction() {
    /*
     *                 7
     *                 |
     *   2 ----------> 6 --> 31 ----------------> 3    way 10: 2-6-31-3 primary, one-way
     *                 | ~15 m                         way 11: 7-6-8 secondary, two-way
     *                 8
     *
     *   6: highway=traffic_signals, on the junction's centre: a signalised junction
     *   31: highway=crossing, crossing=traffic_signals, a shape node of way 10, ~15 m past 6 on its exit arm
     *
     *   within the 30 m crossing distance of junction 6, behind it against the traffic: part of that junction, so even
     *   with breaking links at isolated crossings on, no link is broken at 31. With the crossing distance lowered to
     *   10 m, junction 6 is out of reach and 31 is an isolated crossing
     */
    var withinReach = parse("crossing_on_exit_arm_of_signalised_junction",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));
    assertEquals(4, withinReach.getLinks().size());
    assertEquals(List.of(nodeByOsmId(withinReach, 6)), onlyIntersection(withinReach).getMemberNodes());

    IdGenerator.reset();
    var beyondReach = parse("crossing_on_exit_arm_of_signalised_junction", settings -> {
      settings.setBreakLinksAtIsolatedSignalisedCrossings(true);
      settings.setMaximumDistanceSignalisedCrossingToJunctionInMeters(10);
    });
    assertEquals(5, beyondReach.getLinks().size());
    assertEquals(2, beyondReach.getIntersections().size());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), intersectionAt(beyondReach, 31).getTypes());
  }

  /** A crossing signal on the road 10 m before a junction without traffic signals, footways not parsed, is isolated:
   * the junction nearby does not make it part of anything */
  @Test
  public void crossingNearUnsignalisedJunction() {
    /*
     *                           8
     *                     21    |                      way 11: 8-1-9 secondary, two-way
     *                     :     |
     *   2 --------------- 31 -- 1 --------------- 3    way 10: 2-31-1-3 primary, two-way
     *                     : ~10 m
     *                     :     |
     *                    20     9                      way 12: 20-31-21 footway, not parsed
     *
     *   1: no traffic signals, an unsignalised junction
     *   31: highway=crossing, crossing=traffic_signals, a shape node of way 10, ~10 m before 1
     *
     *   footways not parsed (active modes off, the default here): 31 has only the road through it, so the crossing
     *   signal is isolated from the pedestrian network; junction 1 has no intersection, so the crossing is not part of
     *   it: with breaking links at isolated crossings on, way 10 is broken at 31 and 31 is its own signalised crossing
     *   intersection with approaches 2->31 and 1->31; 1 gets no intersection
     */
    var layer = parse("crossing_near_unsignalised_junction",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));

    assertEquals(5, layer.getLinks().size());
    var crossing = onlyIntersection(layer);
    assertEquals(List.of(nodeByOsmId(layer, 31)), crossing.getMemberNodes());
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(Set.of(segment(layer, 2, 31), segment(layer, 1, 31)), Set.copyOf(crossing.getApproachSegments()));
  }

  /** Breaking a link at an isolated crossing that is an approach of a signalised junction further away keeps that
   * junction's approach, now the piece of the link arriving at the junction */
  @Test
  public void crossingBreaksJunctionApproach() {
    /*
     *                                     8
     *                                     |                way 11: 8-1-9 secondary, two-way
     *   2 ---------- 31 ----------------- 1 ---------- 3   way 10: 2-31-1-3 primary, two-way
     *                      ~40 m          |
     *                                     9
     *
     *   1: highway=traffic_signals, on the junction's centre: a signalised junction, approach 2->1 among its four
     *   31: highway=crossing, crossing=traffic_signals, a shape node of way 10, ~40 m before 1: beyond the 30 m for a
     *       crossing to count as part of the junction (the footway is not in the data, as when footways are not parsed)
     *   expected, breaking links at isolated crossings on: way 10 broken at 31, five links; 31 is its own crossing
     *   intersection with approaches 2->31 and 1->31; junction 1 keeps four approaches, its approach from the west now
     *   the piece arriving at it, 31->1, instead of 2->1
     */
    var layer = parse("crossing_breaks_junction_approach",
        settings -> settings.setBreakLinksAtIsolatedSignalisedCrossings(true));

    assertEquals(5, layer.getLinks().size());
    assertEquals(2, layer.getIntersections().size());
    var junction = intersectionAt(layer, 1);
    assertEquals(EnumSet.of(IntersectionType.JUNCTION), junction.getTypes());
    assertEquals(Set.of(segment(layer, 31, 1), segment(layer, 3, 1), segment(layer, 8, 1), segment(layer, 9, 1)),
        Set.copyOf(junction.getApproachSegments()));
    var crossing = intersectionAt(layer, 31);
    assertEquals(EnumSet.of(IntersectionType.CROSSING), crossing.getTypes());
    assertEquals(Set.of(segment(layer, 2, 31), segment(layer, 1, 31)), Set.copyOf(crossing.getApproachSegments()));
  }
}

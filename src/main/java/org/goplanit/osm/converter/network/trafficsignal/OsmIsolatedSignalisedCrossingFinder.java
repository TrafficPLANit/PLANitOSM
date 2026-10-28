package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerUtils;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.network.OsmIntersectionSettings;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmTrafficSignalUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.LinkSegmentUtils;
import org.goplanit.utils.network.layer.physical.LinkUtils;
import org.goplanit.utils.network.layer.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: finds the isolated signalised
 * crossings, and lists each with the junctions it reaches, per segment arriving at each, with the distance. Every
 * signalised crossing is judged, but only the isolated ones are returned: no junction showing a signal other than those
 * along the crossing's own road lies within the crossing distance beyond it, or it lies between its own stop lines.
 * The list tells when a vehicle signal is the stop line of a crossing rather than of a junction. Example: a pedestrian
 * crossing 60 m before a junction without signals; the vehicle signal before it is the crossing's stop line. A
 * crossing next to a signalised junction is left out, and joins that junction later.
 *
 * @author markr
 */
final class OsmIsolatedSignalisedCrossingFinder {

  /**
   * Per segment arriving at the first junction vehicle signals reach in a direction, those signals, each with its
   * distance to that junction
   *
   * @param vehicleSignals on a road but not on a junction node, with the junctions they reach
   * @return the signals and their distances in metres, per segment arriving at a first junction
   */
  private static Map<MacroscopicLinkSegment, List<Pair<OsmNode, Double>>> findVehicleSignalsByFirstArrival(
      List<PendingOsmVehicleSignal> vehicleSignals) {
    var byFirstArrival = new IdentityHashMap<MacroscopicLinkSegment, List<Pair<OsmNode, Double>>>();
    for (var pendingSignal : vehicleSignals) {
      for (var junctions : pendingSignal.junctionsPerDirection) {
        byFirstArrival.computeIfAbsent(junctions.get(0).arriving, segment -> new ArrayList<>(1))
            .add(Pair.of(pendingSignal.signal, junctions.get(0).distance));
      }
    }
    return byFirstArrival;
  }

  /**
   * The vehicle signals passing a crossing towards the first junction it reaches: they reach that junction first on
   * the same segment as the crossing, from farther away than the crossing
   *
   * @param firstJunction the first junction reached from the crossing, in one direction
   * @param vehicleSignalsByFirstArrival per segment arriving at a first junction, the vehicle signals reaching it first
   *                                     there, with their distances
   * @return the signals passing the crossing, empty when none does
   */
  private static List<OsmNode> findVehicleSignalsPassingCrossing(LookAhead.TargetReached firstJunction,
      Map<MacroscopicLinkSegment, List<Pair<OsmNode, Double>>> vehicleSignalsByFirstArrival) {
    return vehicleSignalsByFirstArrival.getOrDefault(firstJunction.arriving, List.of()).stream()
        .filter(signal -> Precision.greater(signal.second(), firstJunction.distance, Precision.EPSILON_6))
        .map(Pair::first).collect(Collectors.toList());
  }

  /** the intersection settings, holding the maximum distances, the joining reach of consolidation and the traffic
   * signal directions set */
  private final OsmIntersectionSettings intersectionSettings;

  /** the parsing data of the network: the OSM ways of roundabouts, and the diagnostics */
  private final OsmNetworkReaderData networkData;

  /** the parsing data of the layer, to find where a signal sits on the PLANit layer */
  private final OsmNetworkReaderLayerData layerData;

  /** to measure distances along links */
  private final PlanitJtsCrsUtils geoUtils;

  /** the parsed area: the bounding area, or else the bounds the input file declares, see
   * {@link OsmProjectedBoundingAreaOrExtentHelper} */
  private final OsmProjectedBoundingAreaOrExtentHelper parsedArea;

  /** looking ahead along the road from a signal for junctions for vehicle signals, see
   * {@link LookAhead#createForJunctionsForVehicleSignals}, which says what a junction for vehicle signals is */
  private final LookAhead lookAheadForJunctions;

  /**
   * Verify if a junction reached from a crossing shows a signal other than those along the crossing's own road. That is
   * a vehicle signal on its node, or a crossing signal on its node where the road branches; a tram or light rail track
   * meeting the road there, other than one along the crossing's own road, see
   * {@link LookAhead.TargetReached#isWhereTrackNotAlongArrivingRoadMeetsRoad()}; on another of its entries a vehicle
   * signal reaching it first, or a signalised crossing within the crossing distance; or a vehicle signal on, or
   * reaching first, a junction node within the joining reach of consolidation, such as the other carriageway of a
   * divided road. A vehicle signal reaching such a node along the crossing's own road counts only when the node lies
   * within the crossing distance, or is reached without going along that road, such as straight across to the other
   * carriageway: one further along the crossing's road belongs to the next junction or crossing. Last, a junction node
   * on a roundabout shows another signal when a vehicle signal is on, or reaches first, any node of that roundabout,
   * other than along the crossing's own segment: a roundabout counts as one junction, however large, see
   * {@link #isOnRoundaboutShowingVehicleSignal(LookAhead.TargetReached, VehicleSignalEvidence)}. Only then is a
   * crossing near the junction part of it. The junctions passed on the way from the crossing are left out, and
   * crossings near those other junction nodes do not count, so the two halves of one crossing over a divided road do
   * not vouch for each other.
   *
   * @param junction reached from the crossing, with the segment arriving there
   * @param passed the junctions reached from the crossing up to and including this one
   * @param evidence of the junctions' own vehicle signals
   * @param junctionNodesWithCrossingSignal junction nodes where the road branches with a crossing signal on them
   * @param crossingFirstArrivals per junction, the segments crossings arrive on when it is the first junction they
   *                              reach, within the crossing distance
   * @return true when it shows such a signal, false otherwise
   */
  private boolean hasSignalOtherThanAlongCrossingRoad(LookAhead.TargetReached junction,
      List<LookAhead.TargetReached> passed, VehicleSignalEvidence evidence, Set<Node> junctionNodesWithCrossingSignal,
      Map<Node, List<MacroscopicLinkSegment>> crossingFirstArrivals) {
    /* where a tram or light rail track meets the road, a stop line before it holds traffic for the track, so the track
     * node is that stop line's junction, see LookAhead#createForJunctionsForVehicleSignals: a crossing near it is part
     * of it, unless the track runs along the crossing's own road */
    if (junctionNodesWithCrossingSignal.contains(junction.getTargetNode()) ||
        junction.isWhereTrackNotAlongArrivingRoadMeetsRoad() ||
        crossingFirstArrivals.getOrDefault(junction.getTargetNode(), List.of()).stream()
            .anyMatch(arriving -> arriving != junction.arriving)) {
      return true;
    }
    /* a vehicle signal on, or arriving at, the junction or a junction node near it, other than along the crossing's
     * own segment */
    double reach = intersectionSettings.getIntersectionConsolidatorSettings().getMaximumJoiningReachMeters();
    double crossingDistance = intersectionSettings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var crossingRoad = junction.arriving.getParent();
    var road = LinkUtils.permitsMotorisedRoadModeOnAnySegment();
    Predicate<Edge> offCrossingRoad =
        edge -> road.test(edge) && !PlanitLinkOsmUtils.isSameOsmWayOrName((Link) edge, crossingRoad);
    var reachedOffCrossingRoad = new HashSet<>(MacroscopicNetworkLayerUtils.findShortestConnections(
        junction.getTargetNode(), offCrossingRoad, node -> true, reach).getReached());
    var nearby =
        MacroscopicNetworkLayerUtils.findShortestConnections(junction.getTargetNode(), road, node -> true, reach);
    var passedNodes = passed.stream().map(LookAhead.TargetReached::getTargetNode).collect(Collectors.toSet());
    return nearby.getReached().stream()
        .filter(node -> node == junction.getTargetNode() ||
            (!passedNodes.contains(node) && lookAheadForJunctions.isTargetNode.test(node)))
        .anyMatch(node -> evidence.hasVehicleSignalOnOrArrivingAt(node, arriving -> arriving != junction.arriving &&
            (!PlanitLinkOsmUtils.isSameOsmWayOrName(arriving.getParent(), crossingRoad) ||
                reachedOffCrossingRoad.contains(node) ||
                !Precision.greater(nearby.getCostToReach(node), crossingDistance, Precision.EPSILON_6)))) ||
        isOnRoundaboutShowingVehicleSignal(junction, evidence);
  }

  /**
   * Verify if a junction node lies on a roundabout with a vehicle signal on, or reaching first, any of its nodes, other
   * than along the segment arriving at the junction. The roundabout's nodes are those connected to the junction node
   * over the links of its OSM ways, tagged junction=roundabout or circular, however far round it they lie
   *
   * @param junction reached, with the segment arriving there
   * @param evidence of the junctions' own vehicle signals
   * @return true when on such a roundabout, false otherwise, also when on no roundabout at all
   */
  private boolean isOnRoundaboutShowingVehicleSignal(LookAhead.TargetReached junction, VehicleSignalEvidence evidence) {
    Predicate<Edge> isOfRoundabout = edge -> networkData.isOsmWayIdOfRoundabout(PlanitOsmUtils.parseOsmId(edge));
    if (junction.getTargetNode().getEdges().stream().noneMatch(isOfRoundabout)) {
      return false;
    }
    return MacroscopicNetworkLayerUtils.findShortestConnections(
            junction.getTargetNode(), isOfRoundabout, node -> true, Double.POSITIVE_INFINITY).getReached().stream()
        .anyMatch(node -> evidence.hasVehicleSignalOnOrArrivingAt(node, arriving -> arriving != junction.arriving));
  }

  /**
   * Where the vehicle signals tagged with one direction, forward or backward, stand on the segments carrying traffic
   * past them, see
   * {@link OsmVehicleSignalPositionsWithDirection#recordWhereVehicleSignalStands(OsmNode, MacroscopicLinkSegment)}. A
   * signal whose direction the OSM ways through it disagree on is left out.
   *
   * @param vehicleSignals on a road but not on a junction node
   * @return where those tagged with one direction stand
   */
  private OsmVehicleSignalPositionsWithDirection findWhereVehicleSignalsTaggedWithOneDirectionStand(
      List<PendingOsmVehicleSignal> vehicleSignals) {
    var positions = new OsmVehicleSignalPositionsWithDirection();
    for (var pendingSignal : vehicleSignals) {
      var direction = OsmTrafficSignalUtils.getDirectionOf(
          pendingSignal.signal, OsmModelUtil.getTagsAsMap(pendingSignal.signal), intersectionSettings);
      if (pendingSignal.oneDirectionUnknown || !OsmDirectionTags.isForwardOrBackward(direction)) {
        continue;
      }
      for (var passing : SignalOnPlanitLayer.locateAndCreate(pendingSignal.signal, layerData)
          .findSegmentsPassing(direction, lookAheadForJunctions.travels)) {
        positions.recordWhereVehicleSignalStands(pendingSignal.signal, passing);
      }
    }
    return positions;
  }

  /**
   * Verify if a vehicle signal for the traffic coming the other way stands on a segment followed away from a crossing,
   * past the crossing: on the segment's opposite segment, nearer the segment's end than the crossing is
   *
   * @param segment followed away from the crossing
   * @param crossingToItsEnd distance from the crossing to the segment's downstream end, along the way, in metres
   * @param whereVehicleSignalsStand where the vehicle signals that count stand
   * @return true when such a signal stands there, false otherwise
   */
  private boolean isVehicleSignalFacingBackTowardsCrossingOn(MacroscopicLinkSegment segment, double crossingToItsEnd,
      OsmVehicleSignalPositionsWithDirection whereVehicleSignalsStand) {
    var comingTheOtherWay = (MacroscopicLinkSegment) segment.getOppositeDirectionSegment();
    return comingTheOtherWay != null && whereVehicleSignalsStand.findWhereVehicleSignalsStandOn(comingTheOtherWay)
        .anyMatch(position -> Precision.smaller(PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(
            segment, PlanitJtsUtils.createPoint(position), geoUtils), crossingToItsEnd, Precision.EPSILON_6));
  }

  /**
   * Verify if a vehicle signal faces a crossing from beyond it: it stands past the crossing, on the road up to the
   * first junction reached, for the traffic coming the other way, towards the crossing. Only the road ahead of the
   * crossing is looked at, never the road behind it.
   * <pre>
   *      T                C                   S
   *   ---o----------------+-------------------o------------ J    looking east from crossing C to junction J
   *   westbound signal    crossing            westbound signal past C, before J: S faces C from beyond
   *   behind C: never counts
   * </pre>
   *
   * @param crossing the OSM node of the crossing signal
   * @param firstJunction the first junction reached from the crossing, in one direction
   * @param whereVehicleSignalsStand where the vehicle signals that count stand
   * @return true when such a signal faces it, false otherwise
   */
  private boolean isFacedFromBeyond(OsmNode crossing, LookAhead.TargetReached firstJunction,
      OsmVehicleSignalPositionsWithDirection whereVehicleSignalsStand) {
    var start = firstJunction.passing;
    double crossingToStartEnd = PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(
        start, OsmNodeUtils.createPoint(crossing), geoUtils);
    Boolean faced = LinkSegmentUtils.followOnlySegmentsOnwardUntilAnswered(
        start,
        true,
        crossingToStartEnd,
        intersectionSettings.getMaximumDistanceVehicleSignalToJunctionInMeters(),
        lookAheadForJunctions.travels,
        (current, crossingToItsEnd) -> {
          if (isVehicleSignalFacingBackTowardsCrossingOn(current, crossingToItsEnd, whereVehicleSignalsStand)) {
            return true;
          }
          return current == firstJunction.arriving ? false : null;
        });
    return Boolean.TRUE.equals(faced);
  }

  /**
   * Constructor
   *
   * @param intersectionSettings holding the maximum distances, the joining reach of consolidation and the traffic
   *                             signal directions set
   * @param networkData holding the OSM ways of roundabouts, and the diagnostics
   * @param layerData of the layer
   * @param geoUtils to measure distances along links
   * @param parsedArea the bounding area, or else the bounds the input file declares
   * @param lookAheadForJunctions looking ahead along the road for junctions for vehicle signals
   */
  public OsmIsolatedSignalisedCrossingFinder(OsmIntersectionSettings intersectionSettings,
      OsmNetworkReaderData networkData, OsmNetworkReaderLayerData layerData, PlanitJtsCrsUtils geoUtils,
      OsmProjectedBoundingAreaOrExtentHelper parsedArea, LookAhead lookAheadForJunctions) {
    this.intersectionSettings = intersectionSettings;
    this.networkData = networkData;
    this.layerData = layerData;
    this.geoUtils = geoUtils;
    this.parsedArea = parsedArea;
    this.lookAheadForJunctions = lookAheadForJunctions;
  }

  /**
   * Find the isolated signalised crossings. A crossing is isolated when no junction showing a signal other than those
   * along the crossing's own road lies within the crossing distance beyond it, see
   * {@link #hasSignalOtherThanAlongCrossingRoad}. For each isolated crossing, the result keeps the junctions it reaches
   * along the road, within the maximum distance from a vehicle signal to a junction, up to and including the first one
   * showing such a signal. They are kept per segment arriving at each junction, with the distance to it. An isolated
   * crossing not on a junction node that lies near or beyond the boundary of the parsed area is marked as such: the
   * third pass ignores it, see
   * {@link OsmProjectedBoundingAreaOrExtentHelper#isNearOrBeyondBoundary(OsmNode, double)}. A crossing that is
   * isolated although a junction showing no such signal lies within the crossing distance is counted, unless it lies
   * near or beyond the boundary of the parsed area. The junctions every crossing reaches are found before any crossing
   * is judged, so the outcome does not depend on their order.
   * <p>
   * A crossing is also isolated in a direction, whatever the junctions there, when it lies between its own stop lines:
   * a vehicle signal passes it in that direction, and past it, before the first junction, a vehicle signal faces back
   * towards it, see
   * {@link #isFacedFromBeyond(OsmNode, LookAhead.TargetReached, OsmVehicleSignalPositionsWithDirection)}. It is
   * counted too, unless it lies near or beyond the boundary of the parsed area, and kept for the third pass, which
   * never makes it part of a signalised junction nearby.
   * <p>
   * A crossing between its own stop lines makes no junction signalised for another crossing, and neither do its stop
   * lines, the vehicle signals passing it towards the first junction it reaches: they hold traffic for that crossing
   * only. So every other crossing is judged without them. A crossing between its own stop lines is judged with every
   * signal and crossing, its own stop lines included. Example: one signalised crossing to a tram stop across three
   * roadways, its middle part between two signals facing each other; that part's stop line reaching a junction just
   * past the other parts does not make them part of that junction.
   *
   * @param firstPass holds the crossing signals not on a junction node, and those on a junction node where the road
   *                  does not branch, and the vehicle signals on a road but not on a junction node
   * @param evidence of the junctions' own signals, from every vehicle signal collected, stop lines of isolated
   *                 crossings included
   * @return first the isolated crossings before the junctions they reach, per segment arriving there, second the
   *         crossings isolated between their own stop lines, kept by identity
   */
  public Pair<Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>>, Set<OsmNode>>
      findIsolatedCrossingsByJunctionArrival(
          OsmTrafficSignalFirstPassResult firstPass, VehicleSignalEvidence evidence) {

    var crossingDistance = intersectionSettings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var reach = intersectionSettings.getMaximumDistanceVehicleSignalToJunctionInMeters();
    var whereVehicleSignalsTaggedWithOneDirectionStand =
        findWhereVehicleSignalsTaggedWithOneDirectionStand(firstPass.vehicleSignalsNotOnJunctionNode);
    var vehicleSignalsByFirstArrival = findVehicleSignalsByFirstArrival(firstPass.vehicleSignalsNotOnJunctionNode);
    var notOnJunctionNode = Collections.newSetFromMap(new IdentityHashMap<OsmNode, Boolean>());
    notOnJunctionNode.addAll(firstPass.crossingSignalsNotOnJunctionNode);
    var crossings = Stream.concat(firstPass.crossingSignalsNotOnJunctionNode.stream(),
        firstPass.crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch.stream()).collect(Collectors.toList());

    /* the junctions each crossing reaches */
    var junctionsReachedByCrossing = new IdentityHashMap<OsmNode, List<List<LookAhead.TargetReached>>>();
    for (var crossing : crossings) {
      junctionsReachedByCrossing.put(
          crossing, lookAheadForJunctions.findSegmentsArrivingAtTargetsOnwardFromSignal(crossing, null, reach));
    }

    /* per crossing and direction, whether it lies between its own stop lines there, collecting those stop lines: the
     * vehicle signals passing it towards the first junction it reaches */
    var betweenItsStopLinesPerDirection = new IdentityHashMap<OsmNode, List<Boolean>>();
    var stopLinesOfCrossingsBetweenThem = Collections.newSetFromMap(new IdentityHashMap<OsmNode, Boolean>());
    for (var crossing : crossings) {
      var perDirection = new ArrayList<Boolean>(2);
      for (var junctions : junctionsReachedByCrossing.get(crossing)) {
        var passing = findVehicleSignalsPassingCrossing(junctions.get(0), vehicleSignalsByFirstArrival);
        boolean betweenItsStopLines = !passing.isEmpty() &&
            isFacedFromBeyond(crossing, junctions.get(0), whereVehicleSignalsTaggedWithOneDirectionStand);
        if (betweenItsStopLines) {
          stopLinesOfCrossingsBetweenThem.addAll(passing);
        }
        perDirection.add(betweenItsStopLines);
      }
      betweenItsStopLinesPerDirection.put(crossing, perDirection);
    }

    /* per junction the segments crossings arrive on when it is the first junction they reach, within the crossing
     * distance: of every crossing, and of the crossings not between their own stop lines */
    var crossingFirstArrivals = new HashMap<Node, List<MacroscopicLinkSegment>>();
    var crossingFirstArrivalsNotBetweenTheirStopLines = new HashMap<Node, List<MacroscopicLinkSegment>>();
    for (var crossing : crossings) {
      boolean betweenItsStopLines = betweenItsStopLinesPerDirection.get(crossing).contains(true);
      for (var junctions : junctionsReachedByCrossing.get(crossing)) {
        var first = junctions.get(0);
        if (Precision.greater(first.distance, crossingDistance, Precision.EPSILON_6)) {
          continue;
        }
        crossingFirstArrivals.computeIfAbsent(first.getTargetNode(), node -> new ArrayList<>(1)).add(first.arriving);
        if (!betweenItsStopLines) {
          crossingFirstArrivalsNotBetweenTheirStopLines.computeIfAbsent(
              first.getTargetNode(), node -> new ArrayList<>(1)).add(first.arriving);
        }
      }
    }

    /* the evidence without the stop lines of crossings between them, for judging the other crossings */
    var evidenceWithoutStopLinesOfCrossingsBetweenThem = new VehicleSignalEvidence(
        firstPass.junctionNodesWithVehicleSignal, firstPass.vehicleSignalsNotOnJunctionNode.stream()
        .filter(pendingSignal -> !stopLinesOfCrossingsBetweenThem.contains(pendingSignal.signal))
        .collect(Collectors.toList()));

    var isolated = new IdentityHashMap<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>>();
    var crossingsBetweenTheirStopLines = Collections.newSetFromMap(new IdentityHashMap<OsmNode, Boolean>());
    for (var crossing : crossings) {

      boolean nearOrBeyondBoundary =
          notOnJunctionNode.contains(crossing) && parsedArea.isNearOrBeyondBoundary(crossing, crossingDistance);
      boolean isolatedPastJunctionWithoutOtherSignal = false;
      boolean isolatedBetweenItsStopLines = false;
      var betweenItsStopLinesByDirection = betweenItsStopLinesPerDirection.get(crossing);
      /* a crossing between its own stop lines is judged with every signal and crossing, its own stop lines included */
      boolean judgedWithEverySignal = betweenItsStopLinesByDirection.contains(true);
      var evidenceForCrossing = judgedWithEverySignal ? evidence : evidenceWithoutStopLinesOfCrossingsBetweenThem;
      var crossingFirstArrivalsForCrossing =
          judgedWithEverySignal ? crossingFirstArrivals : crossingFirstArrivalsNotBetweenTheirStopLines;
      var reached = junctionsReachedByCrossing.get(crossing);
      for (int direction = 0; direction < reached.size(); direction++) {
        var junctions = reached.get(direction);

        var passed = new ArrayList<LookAhead.TargetReached>(1);
        boolean isIsolated = true;
        boolean junctionWithoutOtherSignalWithinCrossingDistance = false;
        for (var junction : junctions) {
          passed.add(junction);
          boolean withinCrossingDistance = !Precision.greater(junction.distance, crossingDistance, Precision.EPSILON_6);
          if (hasSignalOtherThanAlongCrossingRoad(junction, passed, evidenceForCrossing,
              firstPass.junctionNodesWithCrossingSignal, crossingFirstArrivalsForCrossing)) {
            isIsolated = !withinCrossingDistance;
            break;
          }
          junctionWithoutOtherSignalWithinCrossingDistance |= withinCrossingDistance;
        }
        boolean betweenItsStopLines = !isIsolated && betweenItsStopLinesByDirection.get(direction);
        if (isIsolated || betweenItsStopLines) {
          passed.forEach(
              junction -> isolated.computeIfAbsent(junction.arriving, segment -> new ArrayList<>(1))
              .add(new IsolatedCrossingBeforeJunction(junction.distance, nearOrBeyondBoundary)));
          isolatedPastJunctionWithoutOtherSignal |= isIsolated && junctionWithoutOtherSignalWithinCrossingDistance;
          isolatedBetweenItsStopLines |= betweenItsStopLines;
        }
      }
      if (isolatedPastJunctionWithoutOtherSignal && !nearOrBeyondBoundary) {
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.CROSSING_ISOLATED_PAST_JUNCTION_WITHOUT_OTHER_SIGNAL, crossing);
      }
      if (isolatedBetweenItsStopLines) {
        crossingsBetweenTheirStopLines.add(crossing);
        if (!nearOrBeyondBoundary) {
          networkData.getParseDiagnostics().register(
              OsmTrafficSignalOutcome.CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES, crossing);
        }
      }
    }
    return Pair.of(isolated, crossingsBetweenTheirStopLines);
  }
}

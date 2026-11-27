package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the second of its three passes.
 * It assigns each vehicle signal the first pass postponed to the junction it controls in each direction, the segment
 * arriving there becoming an approach, once every junction's own signals are known. A signal whose first target is an
 * isolated crossing, see {@link OsmIsolatedSignalisedCrossingFinder}, is that crossing's stop line and goes to the
 * third pass. Example: a signal 30 m before a junction becomes an approach of it; one 30 m before an isolated
 * pedestrian crossing goes to the third pass.
 *
 * @author markr
 */
final class OsmTrafficSignalSecondPass {

  /** The first target of a vehicle signal in one direction: the junction it would be assigned to, or an isolated
   * signalised crossing before it */
  private static final class FirstTargetInDirection {

    /** the junctions reached in the direction, nearest first */
    private final List<LookAhead.TargetReached> junctions;

    /** the junction this direction resolves to, see
     * {@link VehicleSignalJunctionResolver#resolveJunction(List, VehicleSignalEvidence)} */
    private final LookAhead.TargetReached junction;

    /** distance from the signal to the target, in metres, negative for an isolated crossing just behind the signal */
    private final double distance;

    /** true when the target is an isolated crossing before the junction, false when it is the junction itself */
    private final boolean isCrossing;

    /** true when the target is an isolated crossing ignored for lying near or beyond the boundary of the parsed area,
     * false otherwise */
    private final boolean isCrossingNearOrBeyondBoundary;

    /**
     * Constructor
     *
     * @param junctions reached in the direction, nearest first
     * @param junction the junction the direction resolves to
     * @param distance from the signal to the target, in metres
     * @param isCrossing true when the target is an isolated crossing before the junction
     * @param isCrossingNearOrBeyondBoundary true when the target is an isolated crossing ignored for lying near or
     *                                       beyond the boundary of the parsed area
     */
    private FirstTargetInDirection(List<LookAhead.TargetReached> junctions, LookAhead.TargetReached junction,
        double distance, boolean isCrossing, boolean isCrossingNearOrBeyondBoundary) {
      this.junctions = junctions;
      this.junction = junction;
      this.distance = distance;
      this.isCrossing = isCrossing;
      this.isCrossingNearOrBeyondBoundary = isCrossingNearOrBeyondBoundary;
    }

    /**
     * Verify if the target is an isolated crossing on the signal's own node, such as one tagged both a vehicle signal
     * and a crossing
     *
     * @return true when so, false when the target is a junction or a crossing further on or behind
     */
    private boolean isIsolatedCrossingOnSignalNode() {
      return isCrossing && !Precision.greater(Math.abs(distance), 0, Precision.EPSILON_6);
    }

    /**
     * Verify if the target is an isolated crossing just behind the signal: the salvage attempt took the signal as that
     * crossing's, see {@link #MAX_DISTANCE_TO_SALVAGE_VEHICLE_SIGNAL_PAST_CROSSING_M}
     *
     * @return true when so, false otherwise
     */
    private boolean isSalvagedAsSignalOfIsolatedCrossingJustBehind() {
      return isCrossing && Precision.smaller(distance, 0, Precision.EPSILON_6);
    }
  }

  /** A vehicle signal without a usable direction normally takes the nearest junction. It takes a junction where a road
   * of higher capacity crosses instead, when that is at most this much farther, in metres */
  private static final double NEAR_TIE_DELTA_DISTANCE_M = 10;

  /** Salvage attempt, see {@link #findFirstTarget(List, Map, VehicleSignalEvidence, boolean)}: a vehicle signal tagged
   * with its direction at most this far past an isolated signalised crossing, in metres, with no isolated crossing
   * ahead of it before its junction, is taken as that crossing's signal. Half a crosswalk plus a signal pole on its far
   * side */
  private static final double MAX_DISTANCE_TO_SALVAGE_VEHICLE_SIGNAL_PAST_CROSSING_M = 5;

  /**
   * Distance from a signal to the nearest isolated signalised crossing meeting a condition before the junction a
   * direction resolves to. A crossing counts when it lies on the segment arriving at that junction, or at a junction
   * passed on the way, and is not farther from that junction than the signal is, plus the reach behind the signal: a
   * crossing on the signal's own node, at distance 0, counts too, and one behind the signal within that reach, at a
   * negative distance.
   *
   * @param junctions reached in the direction, nearest first
   * @param resolved the junction the direction resolves to, one of them
   * @param isolatedCrossings isolated crossings before the junctions they reach, per segment arriving there
   * @param reachBehind how far behind the signal a crossing still counts, in metres, 0 for none
   * @param condition a crossing must meet to count
   * @return distance in metres, negative behind the signal, infinite when no such crossing lies before the junction
   */
  private static double isolatedCrossingDistance(List<LookAhead.TargetReached> junctions,
      LookAhead.TargetReached resolved,
      Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> isolatedCrossings,
      double reachBehind, Predicate<IsolatedCrossingBeforeJunction> condition) {
    double crossingDistance = Double.POSITIVE_INFINITY;
    for (var passed : junctions) {
      for (var crossing : isolatedCrossings.getOrDefault(passed.arriving, List.of())) {
        double distance = passed.distance - crossing.distanceToJunction;
        if (condition.test(crossing) && !Precision.smaller(distance, -reachBehind, Precision.EPSILON_6) &&
            Precision.smaller(Math.abs(distance), Math.abs(crossingDistance), Precision.EPSILON_6)) {
          crossingDistance = distance;
        }
      }
      if (passed == resolved) {
        break;
      }
    }
    return crossingDistance;
  }

  /** finds the isolated signalised crossings, whose stop lines are left for the third pass */
  private final OsmIsolatedSignalisedCrossingFinder isolatedCrossingFinder;

  /** the parsing data of the network: the recorded traffic signals, the OSM nodes and the diagnostics */
  private final OsmNetworkReaderData networkData;

  /** the parsed area: the bounding area, or else the bounds the input file declares, see
   * {@link OsmProjectedBoundingAreaOrExtentHelper} */
  private final OsmProjectedBoundingAreaOrExtentHelper parsedArea;

  /** the junction a vehicle signal is assigned to, given the signal evidence */
  private final VehicleSignalJunctionResolver junctionResolver;

  /** the intersections signals make, built before they are registered on the layer */
  private final OsmSignalIntersectionPreRegistration intersectionPreRegistration;

  /** where the vehicle signals made into an approach stand, kept across link breaks */
  private final OsmVehicleSignalPositionsWithDirection vehicleSignalPositions;

  /**
   * The first target of a signal in one direction: the junction it would be assigned to there, see
   * {@link VehicleSignalJunctionResolver#resolveJunction(List, VehicleSignalEvidence)}, or an isolated signalised
   * crossing before it. When there is such a crossing, it also tells whether the nearest one lies near or beyond the
   * boundary of the parsed area.
   * <p>
   * Salvage attempt, apart from that logic: when no isolated crossing lies ahead before the junction, one at most
   * {@link #MAX_DISTANCE_TO_SALVAGE_VEHICLE_SIGNAL_PAST_CROSSING_M} behind the signal is its target, the signal
   * standing just past it. That signal is most likely a signal head on the far side of the crossing, or the crossing's
   * stop line mapped past it, rather than the stop line of the junction ahead. Only for a signal whose direction is
   * known: one without a direction tag right next to a crossing is more likely the stop line before it, heading for a
   * junction.
   *
   * @param junctions reached in the direction, nearest first
   * @param isolatedCrossings isolated crossings before the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @param salvageSignalJustPastCrossing true to make the salvage attempt, for a signal whose direction is known
   * @return the first target
   */
  private FirstTargetInDirection findFirstTarget(List<LookAhead.TargetReached> junctions,
      Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> isolatedCrossings,
      VehicleSignalEvidence evidence, boolean salvageSignalJustPastCrossing) {
    var junction = junctionResolver.resolveJunction(junctions, evidence);
    double reachBehind = 0;
    double crossingDistance =
        isolatedCrossingDistance(junctions, junction, isolatedCrossings, reachBehind, crossing -> true);
    if (salvageSignalJustPastCrossing &&
        !Precision.smaller(crossingDistance, junction.distance, Precision.EPSILON_6)) {
      /* salvage attempt: no isolated crossing ahead before the junction, so one just behind the signal makes it that
       * crossing's signal */
      reachBehind = MAX_DISTANCE_TO_SALVAGE_VEHICLE_SIGNAL_PAST_CROSSING_M;
      crossingDistance =
          isolatedCrossingDistance(junctions, junction, isolatedCrossings, reachBehind, crossing -> true);
    }
    if (!Precision.smaller(crossingDistance, junction.distance, Precision.EPSILON_6)) {
      return new FirstTargetInDirection(junctions, junction, junction.distance, false, false);
    }
    double crossingNearOrBeyondBoundaryDistance = isolatedCrossingDistance(
        junctions, junction, isolatedCrossings, reachBehind, crossing -> crossing.nearOrBeyondBoundary);
    boolean nearestIsNearOrBeyondBoundary = !Precision.greater(
        Math.abs(crossingNearOrBeyondBoundaryDistance), Math.abs(crossingDistance), Precision.EPSILON_6);
    return new FirstTargetInDirection(junctions, junction, crossingDistance, true, nearestIsNearOrBeyondBoundary);
  }

  /**
   * The first target of a vehicle signal in each direction it applies to, see
   * {@link #findFirstTarget(List, Map, VehicleSignalEvidence, boolean)}, with the salvage attempt for a signal whose
   * direction is known. Each direction is resolved once here.
   *
   * @param pending the signal with the junctions it reaches
   * @param isolatedCrossings isolated crossings before the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @return per direction, its first target, in the order of the signal's directions
   */
  private List<FirstTargetInDirection> findFirstTargets(PendingOsmVehicleSignal pending,
      Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> isolatedCrossings,
      VehicleSignalEvidence evidence) {
    return pending.junctionsPerDirection.stream()
        .map(junctions -> findFirstTarget(junctions, isolatedCrossings, evidence, !pending.oneDirectionUnknown))
        .collect(Collectors.toList());
  }

  /**
   * Keep only one direction for a vehicle signal whose direction is unknown: the one whose first target is nearest.
   * There is one exception. If that target is a junction, and another direction reaches a junction at most
   * {@link #NEAR_TIE_DELTA_DISTANCE_M} farther where a road of higher capacity crosses, that direction is kept instead.
   *
   * @param pending the signal with the junctions it reaches
   * @param firstTargets per direction, its first target, see
   *                     {@link #findFirstTargets(PendingOsmVehicleSignal, Map, VehicleSignalEvidence)}
   * @return the first targets as they are when the direction is known, or when there is only one. Otherwise only the
   *         first target of the kept direction
   */
  private List<FirstTargetInDirection> keepNearestDirectionWhenUnknown(PendingOsmVehicleSignal pending,
      List<FirstTargetInDirection> firstTargets) {
    if (!pending.oneDirectionUnknown || firstTargets.size() < 2) {
      return firstTargets;
    }
    var byDistance = firstTargets.stream()
        .sorted(Comparator.comparingDouble(target -> target.distance)).collect(Collectors.toList());
    var nearest = byDistance.get(0);
    var chosen = nearest;
    if (!nearest.isCrossing) {
      for (var other : byDistance.subList(1, byDistance.size())) {
        if (!other.isCrossing &&
            !Precision.greater(other.distance - nearest.distance, NEAR_TIE_DELTA_DISTANCE_M, Precision.EPSILON_6) &&
            Precision.greater(other.junction.computeCrossingRoadCapacity(),
                chosen.junction.computeCrossingRoadCapacity(), Precision.EPSILON_6)) {
          chosen = other;
        }
      }
    }
    networkData.getParseDiagnostics().register(
        OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN, pending.signal);
    return List.of(chosen);
  }

  /**
   * Assign a vehicle signal not on a junction node to a junction in each direction it applies to, see
   * {@link VehicleSignalJunctionResolver#resolveJunction(List, VehicleSignalEvidence)}. The segment arriving at that
   * junction becomes an approach.
   *
   * @param pending the signal with the junctions it reaches
   * @param evidence of the junctions' own signals
   * @return true when assigned to at least one junction, false otherwise
   */
  private boolean assignToJunctions(PendingOsmVehicleSignal pending, VehicleSignalEvidence evidence) {
    boolean lowestOrderRoadJunctionPassed = false;
    boolean roadEndOutsideParsedAreaChosen = false;
    for (var junctions : pending.junctionsPerDirection) {
      var chosen = junctionResolver.resolveJunction(junctions, evidence);
      lowestOrderRoadJunctionPassed |= chosen != junctions.get(0);
      roadEndOutsideParsedAreaChosen |= parsedArea.isRoadEndOutside(chosen.getTargetNode());
      intersectionPreRegistration.addArrivingSegmentAsApproach(chosen, pending.signal, IntersectionType.JUNCTION);
      vehicleSignalPositions.recordWhereVehicleSignalStands(pending.signal, chosen.passing);
    }
    if (lowestOrderRoadJunctionPassed) {
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalOutcome.LOWEST_ORDER_ROAD_JUNCTION_PASSED, pending.signal);
    }
    if (roadEndOutsideParsedAreaChosen) {
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA, pending.signal);
    }
    return !pending.junctionsPerDirection.isEmpty();
  }

  /**
   * Constructor
   *
   * @param isolatedCrossingFinder finds the isolated signalised crossings
   * @param networkData holding the recorded traffic signals, the OSM nodes and the diagnostics
   * @param parsedArea the bounding area, or else the bounds the input file declares
   * @param junctionResolver the junction a vehicle signal is assigned to, given the signal evidence
   * @param intersectionPreRegistration the intersections signals make, built before they are registered on the layer
   * @param vehicleSignalPositions where the vehicle signals made into an approach stand
   */
  public OsmTrafficSignalSecondPass(OsmIsolatedSignalisedCrossingFinder isolatedCrossingFinder,
      OsmNetworkReaderData networkData, OsmProjectedBoundingAreaOrExtentHelper parsedArea,
      VehicleSignalJunctionResolver junctionResolver, OsmSignalIntersectionPreRegistration intersectionPreRegistration,
      OsmVehicleSignalPositionsWithDirection vehicleSignalPositions) {
    this.isolatedCrossingFinder = isolatedCrossingFinder;
    this.networkData = networkData;
    this.parsedArea = parsedArea;
    this.junctionResolver = junctionResolver;
    this.intersectionPreRegistration = intersectionPreRegistration;
    this.vehicleSignalPositions = vehicleSignalPositions;
  }

  /**
   * Second pass: assign the vehicle signals collected in the first pass to their junctions. It works in six steps:
   * <ol>
   * <li>Find the isolated signalised crossings. A crossing is isolated when no junction lies within the crossing
   * distance beyond it, or when a vehicle signal past it faces back towards it, see
   * {@link OsmIsolatedSignalisedCrossingFinder#findIsolatedCrossingsByJunctionArrival(OsmTrafficSignalFirstPassResult,
   * VehicleSignalEvidence)}.</li>
   * <li>Some signals have no usable direction. They are untagged, or their direction means opposite ways on the two
   * roads through them. Such a signal may reach junctions both ways. It then keeps only one direction: the one whose
   * first target is nearest. That target is the junction it would be assigned to, or an isolated crossing before it.
   * There is one exception. If the other way is at most {@link #NEAR_TIE_DELTA_DISTANCE_M} farther, and a road of
   * higher capacity crosses there, that way is kept instead.</li>
   * <li>Some signals have an isolated crossing between them and their junction, or on their own node, such as a node
   * tagged both a vehicle signal and a crossing. In that direction the signal is the crossing's stop line, so the
   * direction is dropped. A signal with no direction left goes to the third pass, where it joins the crossing, unless
   * it stands on the crossing's own node: that crossing's intersection holds it already. A signal whose crossings all
   * lie near or beyond the boundary of the parsed area is ignored with them. As a salvage attempt, a signal tagged with
   * its direction just past an isolated crossing, with none ahead, is taken as that crossing's signal too, and noted
   * to check in OSM, see {@link #MAX_DISTANCE_TO_SALVAGE_VEHICLE_SIGNAL_PAST_CROSSING_M}.</li>
   * <li>Decide again which junctions have signals of their own, their signal evidence. This uses the signals on
   * junction nodes and the signals still left. Stop lines of isolated crossings no longer count.</li>
   * <li>Assign each signal left to its junction in each direction, see
   * {@link VehicleSignalJunctionResolver#resolveJunction(List, VehicleSignalEvidence)}. A signal that reaches no
   * junction goes to the third pass.</li>
   * <li>Register the intersections created so far.</li>
   * </ol>
   * Steps 1 to 3 still count every signal as evidence, stop lines of isolated crossings included. That is because which
   * signals are such stop lines is only known once those steps are done. The one exception is known before: the stop
   * lines of a crossing between its own stop lines, which step 1 leaves out when judging the other crossings, see
   * {@link OsmIsolatedSignalisedCrossingFinder#findIsolatedCrossingsByJunctionArrival(OsmTrafficSignalFirstPassResult,
   * VehicleSignalEvidence)}.
   *
   * @param firstPass holds the vehicle signals not on a junction node, the junction nodes with a vehicle signal on
   *                  them, and the crossing signals, from the first pass
   * @return the stop lines of isolated crossings, the vehicle signals without a junction ahead, and the crossings
   *         isolated between their own stop lines, for the third pass
   */
  public OsmTrafficSignalSecondPassResult assignVehicleSignalsToJunctions(OsmTrafficSignalFirstPassResult firstPass) {
    var secondPass = new OsmTrafficSignalSecondPassResult();

    /* step 1: find the isolated crossings. The evidence still includes the signals that may turn out to be stop
     * lines */
    var evidenceWithIsolatedCrossingStopLines = new VehicleSignalEvidence(
        firstPass.junctionNodesWithVehicleSignal, firstPass.vehicleSignalsNotOnJunctionNode);
    var found =
        isolatedCrossingFinder.findIsolatedCrossingsByJunctionArrival(firstPass, evidenceWithIsolatedCrossingStopLines);
    var isolatedCrossings = found.first();
    secondPass.crossingsBetweenTheirStopLines.addAll(found.second());

    /* steps 2 and 3: keep one direction when it is still unknown, then drop the directions past an isolated
     * crossing, also one on the signal's own node, or, as a salvage attempt, one just behind a signal tagged with its
     * direction. A signal with no direction left is that crossing's stop line, or its own signal. It is
     * deferred to the third pass, which adds it as an approach of the crossing's intersection, when there is one,
     * unless it stands on the crossing's own node */
    var vehicleSignalsExceptIsolatedCrossingStopLines =
        new ArrayList<PendingOsmVehicleSignal>(firstPass.vehicleSignalsNotOnJunctionNode.size());
    for (var pendingSignal : firstPass.vehicleSignalsNotOnJunctionNode) {
      var firstTargets = keepNearestDirectionWhenUnknown(pendingSignal,
          findFirstTargets(pendingSignal, isolatedCrossings, evidenceWithIsolatedCrossingStopLines));
      var directionsToJunctions = firstTargets.stream().filter(target -> !target.isCrossing)
          .map(target -> target.junctions).collect(Collectors.toList());
      if (!firstTargets.isEmpty() && directionsToJunctions.isEmpty()) {
        if (firstTargets.stream().allMatch(target -> target.isCrossingNearOrBeyondBoundary)) {
          /* the stop line of crossings the third pass ignores for lying near or beyond the boundary of the parsed
           * area: ignored with them, rather than assigned past them to a junction */
          networkData.getParseDiagnostics().register(
              OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA, pendingSignal.signal);
        } else if (firstTargets.stream().allMatch(target -> target.isIsolatedCrossingOnSignalNode() ||
            target.isSalvagedAsSignalOfIsolatedCrossingJustBehind())) {
          /* the signal stands on the isolated crossing itself, tagged both a vehicle signal and a crossing, or where a
           * signalised crossing way meets the road: it is that crossing's signal, held by the crossing's intersection,
           * not the stop line of another crossing further on. Or the salvage attempt took it as the signal of the
           * isolated crossing just behind it, which is noted to check in OSM */
          networkData.getParseDiagnostics().register(
              OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_ISOLATED_CROSSING, pendingSignal.signal);
          if (firstTargets.stream().anyMatch(FirstTargetInDirection::isSalvagedAsSignalOfIsolatedCrossingJustBehind)) {
            networkData.getParseDiagnostics().register(
                OsmTrafficSignalOutcome.VEHICLE_SIGNAL_PAST_ISOLATED_CROSSING_SALVAGED_AS_ITS_SIGNAL,
                pendingSignal.signal);
          }
        } else {
          /* every direction left leads to an isolated crossing first: the signal is the stop line of that crossing.
           * The third pass joins it there */
          networkData.getParseDiagnostics().register(
              OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING, pendingSignal.signal);
          secondPass.isolatedCrossingStopLines.add(pendingSignal.signal);
        }
      } else {
        boolean stillOneDirectionUnknown =
            pendingSignal.oneDirectionUnknown && pendingSignal.junctionsPerDirection.size() < 2;
        vehicleSignalsExceptIsolatedCrossingStopLines.add(
            new PendingOsmVehicleSignal(pendingSignal.signal, directionsToJunctions, stillOneDirectionUnknown));
      }
    }

    /* step 4: decide again which junctions have signals of their own, now without the stop lines of isolated
     * crossings */
    var evidenceWithoutIsolatedCrossingStopLines = new VehicleSignalEvidence(
        firstPass.junctionNodesWithVehicleSignal, vehicleSignalsExceptIsolatedCrossingStopLines);

    /* step 5: assign each signal left to its junctions */
    for (var pendingSignal : vehicleSignalsExceptIsolatedCrossingStopLines) {
      if (assignToJunctions(pendingSignal, evidenceWithoutIsolatedCrossingStopLines)) {
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED, pendingSignal.signal);
      } else {
        /* no junction ahead: the third pass handles it */
        secondPass.vehicleSignalsWithoutJunction.add(pendingSignal.signal);
      }
    }

    /* step 6: register the intersections created so far */
    intersectionPreRegistration.registerAllPreRegistered();
    return secondPass;
  }
}

package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the signal evidence of
 * junctions, which junctions have vehicle signals of their own. It is worked out from the vehicle signals on a road,
 * never from intersections. That way, assigning a signal does not depend on the order in which signals are handled. A
 * vehicle signal on a junction node gives that junction evidence. A vehicle signal on a road but not on a junction node
 * gives evidence to the first junction it reaches in each direction, for the road it arrives on. Signals on a track or
 * cycleway, and discarded signals, take no part. Whether stop lines of isolated crossings take part depends on the
 * signals it is created from. A signal is never evidence for its own road, see
 * {@link #hasOwnSignals(LookAhead.TargetReached)}.
 *
 * @author markr
 */
final class VehicleSignalEvidence {

  /** junction nodes with a vehicle signal on them */
  private final Set<Node> junctionNodesWithVehicleSignal;

  /** per junction, the segments on which vehicle signals arrive when it is the first junction they reach */
  private final Map<Node, List<MacroscopicLinkSegment>> firstArrivals = new HashMap<>();

  /**
   * Constructor
   *
   * @param junctionNodesWithVehicleSignal junction nodes with a vehicle signal on them
   * @param pending vehicle signals on a road but not on a junction node, with the junctions they reach
   */
  public VehicleSignalEvidence(Set<Node> junctionNodesWithVehicleSignal, List<PendingOsmVehicleSignal> pending) {
    this.junctionNodesWithVehicleSignal = junctionNodesWithVehicleSignal;
    for (var pendingSignal : pending) {
      for (var junctions : pendingSignal.junctionsPerDirection) {
        var first = junctions.get(0);
        firstArrivals.computeIfAbsent(first.getTargetNode(), junction -> new ArrayList<>(1)).add(first.arriving);
      }
    }
  }

  /**
   * Verify if a junction reached from a signal has signals of its own. That is the case when a vehicle signal is on
   * its node, or when a vehicle signal on another road reaches it first.
   *
   * @param junctionAhead reached from the signal
   * @return true when it has signals of its own, false otherwise
   */
  public boolean hasOwnSignals(LookAhead.TargetReached junctionAhead) {
    return hasVehicleSignalOnOrArrivingAt(junctionAhead.getTargetNode(), arriving ->
        !PlanitLinkOsmUtils.isSameOsmWayOrName(arriving.getParent(), junctionAhead.arriving.getParent()));
  }

  /**
   * Verify if a vehicle signal is on a junction node, or reaches it first on a segment that counts
   *
   * @param junction node to check
   * @param arrivalCounts true for a segment on which a vehicle signal arriving counts
   * @return true when it has such a vehicle signal, false otherwise
   */
  public boolean hasVehicleSignalOnOrArrivingAt(Node junction, Predicate<MacroscopicLinkSegment> arrivalCounts) {
    return junctionNodesWithVehicleSignal.contains(junction) ||
        firstArrivals.getOrDefault(junction, List.of()).stream().anyMatch(arrivalCounts);
  }
}

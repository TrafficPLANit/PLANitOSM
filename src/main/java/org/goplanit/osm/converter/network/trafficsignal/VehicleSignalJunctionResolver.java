package org.goplanit.osm.converter.network.trafficsignal;

import java.util.List;
import java.util.function.Predicate;

import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerUtils;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.network.layer.physical.Link;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the junction a vehicle signal is
 * assigned to in a direction, given the signal evidence of the junctions it reaches, see {@link VehicleSignalEvidence}:
 * the first one that counts. A junction counts when it has signals of its own, when more than lowest order roads of
 * lower capacity join the road there, see {@link OsmLowestOrderRoads}, or when it lies across the median from a
 * junction with a signal of its own.
 *
 * @author markr
 */
final class VehicleSignalJunctionResolver {

  /** the lowest order roads parsed */
  private final OsmLowestOrderRoads lowestOrderRoads;

  /** the settings, holding the joining reach of consolidation */
  private final OsmNetworkReaderSettings settings;

  /**
   * Verify if a junction reached counts when placing signals near it. It counts when it has signals of its own, or when
   * more than lowest order roads of lower capacity join the road there, or when it lies across the median from a
   * junction with a signal of its own, see {@link #isAcrossMedianFromSignalisedJunction(LookAhead.TargetReached,
   * VehicleSignalEvidence)}.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @param evidence of the junctions' own signals
   * @return true when it counts, false otherwise
   */
  private boolean countsAsJunction(LookAhead.TargetReached junctionAhead, VehicleSignalEvidence evidence) {
    return evidence.hasOwnSignals(junctionAhead) || !lowestOrderRoads.isJoinedOnlyByLowestOrderRoads(junctionAhead) ||
        isAcrossMedianFromSignalisedJunction(junctionAhead, evidence);
  }

  /**
   * Verify if a junction reached lies across the median from a junction with a signal of its own on the other
   * carriageway of the same road. A lowest order road joining there leads, within the joining reach of consolidation,
   * to a node on a way of the same name as the road arrived on, other than that road's own link, with a vehicle signal
   * on it or reaching it first. The two carriageways then make one junction, as the crossing rule takes them. Example:
   * on Punt Road, Melbourne, a service lane crosses the median from the southbound carriageway to the signalised Gate 6
   * junction on the northbound one, so a southbound signal stops where the lane joins, rather than going on to
   * Richmond Terrace. It relies on the other carriageway having the same name, so it is kept in this one method.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @param evidence of the junctions' own signals
   * @return true when so, false otherwise
   */
  private boolean isAcrossMedianFromSignalisedJunction(LookAhead.TargetReached junctionAhead,
      VehicleSignalEvidence evidence) {
    var road = junctionAhead.arriving.getParent();
    double reach =
        settings.getIntersectionSettings().getIntersectionConsolidatorSettings().getMaximumJoiningReachMeters();
    Predicate<Edge> lowestOrderRoad = edge -> edge instanceof Link && lowestOrderRoads.isOnLowestOrderRoad((Link) edge);
    return MacroscopicNetworkLayerUtils.findShortestConnections(
            junctionAhead.getTargetNode(), lowestOrderRoad, node -> true, reach).getReached().stream()
        .filter(node -> node != junctionAhead.getTargetNode() && node.getEdges().stream().anyMatch(edge ->
            edge instanceof Link && edge != road && !lowestOrderRoads.isOnLowestOrderRoad((Link) edge) &&
                PlanitLinkOsmUtils.isSameOsmWayOrName((Link) edge, road)))
        .anyMatch(node -> evidence.hasVehicleSignalOnOrArrivingAt(node, arriving -> true));
  }

  /**
   * Constructor
   *
   * @param lowestOrderRoads the lowest order roads parsed
   * @param settings holding the joining reach of consolidation
   */
  public VehicleSignalJunctionResolver(OsmLowestOrderRoads lowestOrderRoads, OsmNetworkReaderSettings settings) {
    this.lowestOrderRoads = lowestOrderRoads;
    this.settings = settings;
  }

  /**
   * The junction a vehicle signal is assigned to in one direction: the first junction reached that counts, see
   * {@link #countsAsJunction(LookAhead.TargetReached, VehicleSignalEvidence)}. So a junction where only service lanes
   * or living streets without signals join is passed over, when one that counts follows within reach, unless such a
   * lane crosses the median to a signalised junction of the same road's other carriageway. A junction that counts is
   * never passed over, even when a road of higher capacity crosses further along. If none counts, the first junction
   * reached is used.
   *
   * @param junctions reached in the direction, nearest first
   * @param evidence of the junctions' own signals
   * @return the junction assigned to
   */
  public LookAhead.TargetReached resolveJunction(List<LookAhead.TargetReached> junctions,
      VehicleSignalEvidence evidence) {
    int first = 0;
    while (first < junctions.size() && !countsAsJunction(junctions.get(first), evidence)) {
      first++;
    }
    if (first == junctions.size()) {
      first = 0;
    }
    return junctions.get(first);
  }
}

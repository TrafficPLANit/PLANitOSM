package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.network.layer.physical.NodeUtils;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}, and by
 * {@link OsmIsolatedSignalisedCrossingFinder}: what the first pass, {@link OsmTrafficSignalFirstPass}, leaves for the
 * passes after it. That is the vehicle signals and crossing signals it postponed, the junction nodes with a vehicle
 * signal on them, the crossing signals on a junction node where the road does not branch, and the junction nodes where
 * the road branches with a crossing signal on them.
 *
 * @author markr
 */
final class OsmTrafficSignalFirstPassResult {

  /** vehicle signals on a road but not on a junction node, with the junctions they reach */
  public final List<PendingOsmVehicleSignal> vehicleSignalsNotOnJunctionNode = new ArrayList<>();

  /** junction nodes with a vehicle signal on them */
  public final Set<Node> junctionNodesWithVehicleSignal = new HashSet<>();

  /** crossing signals not on a junction node */
  public final List<OsmNode> crossingSignalsNotOnJunctionNode = new ArrayList<>();

  /** crossing signals on a junction node where the road does not branch, see
   * {@link NodeUtils#isWhereThreeOrMoreMotorisedRoadLinksMeet(Node)}. The other links there are ones no motorised
   * road traffic can use, such as footways, cycleways or tracks */
  public final List<OsmNode> crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch = new ArrayList<>();

  /** junction nodes where the road branches with a crossing signal on them */
  public final Set<Node> junctionNodesWithCrossingSignal = new HashSet<>();

  /**
   * Constructor, nothing collected yet
   */
  public OsmTrafficSignalFirstPassResult() {
  }
}

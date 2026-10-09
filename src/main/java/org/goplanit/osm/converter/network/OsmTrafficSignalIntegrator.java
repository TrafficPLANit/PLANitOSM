package org.goplanit.osm.converter.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.graph.directed.modifier.event.BreakEdgeSegmentEvent;
import org.goplanit.osm.converter.diagnostics.OsmNearestJunctionBucket;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.helper.OsmProjectedBoundingAreaHelper;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.osm.util.PlanitNetworkLayerModifierUtils;
import org.goplanit.osm.util.PlanitOsmNetworkLayerUtils;
import org.goplanit.utils.event.EventType;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.modifier.event.DirectedGraphModificationEvent;
import org.goplanit.utils.graph.modifier.event.DirectedGraphModifierListener;
import org.goplanit.utils.graph.modifier.event.GraphModificationEvent;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.Intersection;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionControlType;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Turns the traffic signals recorded during pre-processing into intersections of one network layer. It runs once the
 * layer's links are complete and broken at every junction node. A signal on a junction node makes that node an
 * intersection. A vehicle signal before a junction makes the segment arriving at that junction one of its approaches.
 *
 * @author markr
 */
public class OsmTrafficSignalIntegrator {

  /** minimum number of links meeting at a junction node */
  private static final int MIN_LINKS_AT_JUNCTION = 3;

  /** A vehicle signal without a usable direction normally takes the nearest junction. It takes a junction where a road
   * of higher capacity crosses instead, when that is at most this much farther, in metres */
  private static final double NEAR_TIE_DELTA_DISTANCE_M = 10;

  /** the lowest order roads: service lanes, alleys among them, and living streets. A junction where only these join a
   * road of higher capacity does not claim a signalised crossing next to it */
  private static final Set<String> LOWEST_ORDER_ROAD_TYPES =
      Set.of(OsmHighwayTags.SERVICE, OsmHighwayTags.LIVING_STREET);

  /** looking ahead along the road from a vehicle signal. It travels segments motorised road traffic can use, looks for
   * nodes where the road branches, and carries on past them along the same OSM way or a way of the same name */
  private static final LookAhead ROAD_LOOK_AHEAD = new LookAhead(
      OsmTrafficSignalIntegrator::isMotorisedRoadSegment,
      OsmTrafficSignalIntegrator::isRoadBranchNode, true);

  /** the layer to add intersections to */
  private final MacroscopicNetworkLayer networkLayer;

  /** the parsing data of the layer, used to find the PLANit node or links an OSM node ended up on */
  private final OsmNetworkReaderLayerData layerData;

  /** the parsing data of the network: the recorded traffic signals, the OSM nodes and the diagnostics */
  private final OsmNetworkReaderData networkData;

  /** the settings, holding the maximum distances */
  private final OsmNetworkReaderSettings settings;

  /** to measure distances along links */
  private final PlanitJtsCrsUtils geoUtils;

  /** the parsed area, empty when not bounded, set when executing */
  private OsmProjectedBoundingAreaHelper boundingArea;

  /** OSM way ids of the lowest order roads parsed. The layer parser hands them over while it creates the links. Cleared
   * after executing */
  private final Set<String> lowestOrderRoadOsmWayIds = new HashSet<>();

  /** looking ahead along the road from a vehicle signal to the first signalised crossing */
  private final LookAhead crossingLookAhead = new LookAhead(
      OsmTrafficSignalIntegrator::isMotorisedRoadSegment, this::isSignalisedCrossingNode, false);

  /** intersections created but not yet registered on the layer, in the order created */
  private final List<Intersection> created = new ArrayList<>();

  /** intersections registered on the layer, in the order registered */
  private final List<Intersection> registered = new ArrayList<>();

  /** intersections created, by the OSM node id of their signal or junction node. Only kept while executing: later
   * changes to the layer, such as pruning, could leave it pointing at removed intersections */
  private final Map<Long, Intersection> intersectionsByOsmNodeId = new HashMap<>();

  /** per approach segment, the OSM nodes of the signals that made it an approach, in the order handled. Keyed by
   * identity, because segment ids are recreated after parsing */
  private final Map<MacroscopicLinkSegment, List<OsmNode>> signalsByApproach = new IdentityHashMap<>();

  /**
   * How to look ahead from a signal along its way. It says which segments may be travelled, which nodes are targets,
   * and whether to carry on past a target to find more. Carrying on only follows the same OSM way, or a way of the same
   * name.
   */
  private static final class LookAhead {

    /** the segments it may travel, both the ones it starts on and the ones it continues on */
    private final Predicate<MacroscopicLinkSegment> travels;

    /** the nodes looked for */
    private final Predicate<Node> isTarget;

    /** true to carry on past a target, along the same OSM way or a way of the same name, false to stop at the first
     * target */
    private final boolean beyondTarget;

    /**
     * Constructor
     *
     * @param travels the segments it may travel, both the ones it starts on and the ones it continues on
     * @param isTarget the nodes looked for
     * @param beyondTarget true to carry on past a target, along the same OSM way or a way of the same name, false to
     *                     stop at the first target
     */
    private LookAhead(Predicate<MacroscopicLinkSegment> travels, Predicate<Node> isTarget, boolean beyondTarget) {
      this.travels = travels;
      this.isTarget = isTarget;
      this.beyondTarget = beyondTarget;
    }
  }

  /** A node a look-ahead reaches from a signal: the segment arriving there, and the distance to it. The node can be a
   * junction or a signalised crossing. From a track or cycleway it can also be a road node or a branch node */
  private static final class JunctionAhead {

    /** the segment arriving at the node */
    private final MacroscopicLinkSegment arriving;

    /** from the signal to the node, along the way, in metres */
    private final double distance;

    /**
     * Constructor
     *
     * @param arriving the segment arriving at the node
     * @param distance from the signal to the node, along the way, in metres
     */
    private JunctionAhead(MacroscopicLinkSegment arriving, double distance) {
      this.arriving = arriving;
      this.distance = distance;
    }

    /**
     * Collect the node reached
     *
     * @return node reached
     */
    private Node getJunction() {
      return (Node) arriving.getDownstreamVertex();
    }
  }

  /** A vehicle signal on a road but not on a junction node, with the junctions it reaches in each direction it
   * applies to */
  private static final class PendingVehicleSignal {

    /** the OSM node of the signal */
    private final OsmNode signal;

    /** per direction the signal applies to, the junctions reached along the road, nearest first. No list is empty */
    private final List<List<JunctionAhead>> junctionsPerDirection;

    /** true when the signal applies to one direction, but it is unknown which. That is the case when no direction is
     * tagged, or when the OSM ways through it disagree on the tagged one */
    private final boolean oneDirectionUnknown;

    /**
     * Constructor
     *
     * @param signal the OSM node of the signal
     * @param junctionsPerDirection per direction, the junctions reached along the road, nearest first. No list is empty
     * @param oneDirectionUnknown true when the signal applies to one direction, but it is unknown which
     */
    private PendingVehicleSignal(OsmNode signal, List<List<JunctionAhead>> junctionsPerDirection,
        boolean oneDirectionUnknown) {
      this.signal = signal;
      this.junctionsPerDirection = junctionsPerDirection;
      this.oneDirectionUnknown = oneDirectionUnknown;
    }
  }

  /**
   * The signal evidence of junctions: which junctions have vehicle signals of their own. It is worked out from the
   * vehicle signals on a road, never from intersections. That way, assigning a signal does not depend on the order in
   * which signals are handled. A vehicle signal on a junction node gives that junction evidence. A vehicle signal on a
   * road but not on a junction node gives evidence to the first junction it reaches in each direction, for the road it
   * arrives on. Signals on a track or cycleway, and discarded signals, take no part. Whether stop lines of isolated
   * crossings take part depends on the signals it is created from. A signal is never evidence for its own road, see
   * {@link #hasOwnSignals(JunctionAhead)}.
   */
  private static final class VehicleSignalEvidence {

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
    private VehicleSignalEvidence(Set<Node> junctionNodesWithVehicleSignal, List<PendingVehicleSignal> pending) {
      this.junctionNodesWithVehicleSignal = junctionNodesWithVehicleSignal;
      for (var pendingSignal : pending) {
        for (var junctions : pendingSignal.junctionsPerDirection) {
          var first = junctions.get(0);
          firstArrivals.computeIfAbsent(first.getJunction(), junction -> new ArrayList<>(1)).add(first.arriving);
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
    private boolean hasOwnSignals(JunctionAhead junctionAhead) {
      var junction = junctionAhead.getJunction();
      if (junctionNodesWithVehicleSignal.contains(junction)) {
        return true;
      }
      return firstArrivals.getOrDefault(junction, List.of()).stream().anyMatch(
          arriving -> !isSameOsmWayOrName(arriving.getParent(), junctionAhead.arriving.getParent()));
    }
  }

  /** The first target of a vehicle signal in one direction: the junction it would be assigned to, or an isolated
   * signalised crossing before it */
  private static final class DirectionTarget {

    /** the junctions reached in the direction, nearest first */
    private final List<JunctionAhead> junctions;

    /** the junction this direction resolves to, see {@link #resolveJunction(List, VehicleSignalEvidence)} */
    private final JunctionAhead junction;

    /** distance from the signal to the target, in metres */
    private final double distance;

    /** true when the target is an isolated crossing before the junction, false when it is the junction itself */
    private final boolean isCrossing;

    /**
     * Constructor
     *
     * @param junctions reached in the direction, nearest first
     * @param junction the junction the direction resolves to
     * @param distance from the signal to the target, in metres
     * @param isCrossing true when the target is an isolated crossing before the junction
     */
    private DirectionTarget(List<JunctionAhead> junctions, JunctionAhead junction, double distance, boolean isCrossing) {
      this.junctions = junctions;
      this.junction = junction;
      this.distance = distance;
      this.isCrossing = isCrossing;
    }
  }

  /**
   * Where a signal sits in the layer: on a PLANit node, or on a shape node inside one or more links. Always find it
   * when it is needed, see {@link #locate(OsmNode)}. Never keep it from one pass to the next: the third pass breaks
   * links, which changes the links a position lies inside.
   */
  private static final class SignalLocation {

    /** the OSM node of the signal */
    private final OsmNode signal;

    /** the node the signal is on, null when it lies inside links */
    private final Node node;

    /** the links the signal lies inside, null when it is on a node */
    private final List<MacroscopicLink> links;

    /**
     * Constructor
     *
     * @param signal the OSM node of the signal
     * @param node the signal is on, null when it lies inside links
     * @param links the signal lies inside, null when it is on a node
     */
    private SignalLocation(OsmNode signal, Node node, List<MacroscopicLink> links) {
      this.signal = signal;
      this.node = node;
      this.links = links;
    }

    /**
     * Verify if the signal is on a node
     *
     * @return true when on a node, false when inside links
     */
    private boolean isOnNode() {
      return node != null;
    }

    /**
     * Collect the links the signal lies inside, or that meet at its node
     *
     * @return links
     */
    private List<MacroscopicLink> getLinks() {
      return isOnNode() ?
          node.getEdges().stream().map(MacroscopicLink.class::cast).collect(Collectors.toList()) : links;
    }
  }

  /** What the first pass leaves for the passes after it: the vehicle signals and crossing signals it postponed, the
   * junction nodes with a vehicle signal on them, and the crossing signals on a junction node where the road does not
   * branch. See {@link #discardPostponeOrConstructIntersections()} */
  private static final class FirstPassResult {

    /** vehicle signals on a road but not on a junction node, with the junctions they reach */
    private final List<PendingVehicleSignal> vehicleSignalsNotOnJunctionNode = new ArrayList<>();

    /** junction nodes with a vehicle signal on them */
    private final Set<Node> junctionNodesWithVehicleSignal = new HashSet<>();

    /** crossing signals not on a junction node */
    private final List<OsmNode> crossingSignalsNotOnJunctionNode = new ArrayList<>();

    /** crossing signals on a junction node where the road does not branch, see {@link #isRoadBranchNode(Node)}. The
     * other links there are ones no motorised road traffic can use, such as footways, cycleways or tracks */
    private final List<OsmNode> crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch = new ArrayList<>();

    /**
     * Constructor, nothing collected yet
     */
    private FirstPassResult() {
    }
  }

  /** What the second pass leaves for the third: the vehicle signals it could not assign to a junction. These are the
   * stop lines of isolated crossings, and the vehicle signals without a junction ahead. See
   * {@link #assignVehicleSignalsToJunctions(FirstPassResult)} */
  private static final class SecondPassResult {

    /** vehicle signals at the stop line of an isolated signalised crossing */
    private final List<OsmNode> isolatedCrossingStopLines = new ArrayList<>();

    /** vehicle signals without a junction ahead, and not at the edge of the parsed area */
    private final List<OsmNode> vehicleSignalsWithoutJunction = new ArrayList<>();

    /**
     * Constructor, nothing collected yet
     */
    private SecondPassResult() {
    }
  }

  /**
   * Listens for links broken during the third pass. When an approach is cut short, the intersections move the approach
   * to the piece that still arrives at the node. This handler moves the record of its signals along with it.
   */
  private final class SignalsFollowBrokenApproachesHandler implements DirectedGraphModifierListener {

    /**
     * Constructor
     */
    private SignalsFollowBrokenApproachesHandler() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventType[] getKnownSupportedEventTypes() {
      return new EventType[] { BreakEdgeSegmentEvent.EVENT_TYPE };
    }

    /**
     * Nothing to follow for undirected graph modifications
     *
     * @param event ignored
     */
    @Override
    public void onGraphModificationEvent(GraphModificationEvent event) {
    }

    /**
     * Move the signals recorded for an approach that was cut short to the piece broken off, which now arrives where the
     * approach did
     *
     * @param event of a link segment broken
     */
    @Override
    public void onDirectedGraphModificationEvent(DirectedGraphModificationEvent event) {
      var breakEvent = (BreakEdgeSegmentEvent) event;
      var original = (MacroscopicLinkSegment) breakEvent.getOriginalEdgeSegment();
      if (original.getDownstreamVertex() == breakEvent.getVertexToBreakAt()) {
        var signals = signalsByApproach.remove(original);
        if (signals != null) {
          signalsByApproach.put((MacroscopicLinkSegment) breakEvent.getNewlyBrokenEdgeSegment(), signals);
        }
      }
    }
  }

  /**
   * The direction a signal applies to, relative to its OSM way: forward, backward or both. Any other value, such as a
   * compass bearing, is not recognised. The signal is then handled as untagged.
   *
   * @param tags of the signal
   * @return the direction tagged, null when not tagged or not recognised
   */
  private static String findRecognisedDirection(Map<String, String> tags) {
    var direction = tags.get(OsmTrafficSignalTags.TRAFFIC_SIGNALS_DIRECTION);
    return OsmDirectionTags.FORWARD.equals(direction) || OsmDirectionTags.BACKWARD.equals(direction) ||
        OsmDirectionTags.BOTH.equals(direction) ? direction : null;
  }

  /**
   * Verify if a node is a junction node: three or more links meet there. Every link counts, footways included when they
   * are parsed. Where only two links meet, the road simply passes through.
   *
   * @param node to check
   * @return true when a junction node, false otherwise
   */
  private static boolean isJunctionNode(Node node) {
    return node.getNumberOfEdges() >= MIN_LINKS_AT_JUNCTION;
  }

  /**
   * Verify if the road branches at a node. Three or more links that motorised road traffic can use meet there, see
   * {@link #isBranchNode(Node, Predicate)}. Motorised traffic can go on along more than one link, or another road joins
   * it there. This makes the node a junction node for vehicle signals. A crosswalk, where footways meet the road, is
   * not one.
   *
   * @param node to check
   * @return true when the road branches there, false otherwise
   */
  private static boolean isRoadBranchNode(Node node) {
    return isBranchNode(node, OsmTrafficSignalIntegrator::isMotorisedRoadSegment);
  }

  /**
   * Verify if a road meets a node: at least one link usable by a motorised road mode meets there
   *
   * @param node to check
   * @return true when a road meets it, false otherwise
   */
  private static boolean hasMotorisedRoadLink(Node node) {
    return countMotorisedRoadLinks(node) > 0;
  }

  /**
   * Count the links usable by a motorised road mode meeting at a node
   *
   * @param node to count at
   * @return number of such links
   */
  private static int countMotorisedRoadLinks(Node node) {
    return node.getNumberOfEdges(edge -> isMotorisedRoadLink((Link) edge));
  }

  /**
   * Verify if a way branches at a node. Three or more links with a segment the look-ahead may travel meet there. So the
   * way goes on along more than one link, or another way joins it there.
   *
   * @param node to check
   * @param travels the segments the look-ahead may travel
   * @return true when the way branches there, false otherwise
   */
  private static boolean isBranchNode(Node node, Predicate<MacroscopicLinkSegment> travels) {
    return node.getNumberOfEdges(edge -> {
      var link = (MacroscopicLink) edge;
      return (link.hasLinkSegmentAb() && travels.test(link.getLinkSegmentAb())) ||
          (link.hasLinkSegmentBa() && travels.test(link.getLinkSegmentBa()));
    }) >= MIN_LINKS_AT_JUNCTION;
  }

  /**
   * Verify if a link is usable by a motorised road mode, in at least one direction
   *
   * @param link to check
   * @return true when usable by a motorised road mode, false otherwise
   */
  private static boolean isMotorisedRoadLink(Link link) {
    return link.isAnyModeAllowedOnAnySegment(Mode::isMotorisedRoadMode);
  }

  /**
   * Verify if motorised road traffic can use a segment, in the segment's direction
   *
   * @param segment to check
   * @return true when usable by a motorised road mode, false otherwise
   */
  private static boolean isMotorisedRoadSegment(MacroscopicLinkSegment segment) {
    return segment.isAnyModeAllowed(Mode::isMotorisedRoadMode);
  }

  /**
   * Verify if a segment entering a node runs against traffic leaving it. No mode a signal controls can use the segment,
   * while one can use the opposite segment of its link. So vehicles on that link only leave the node. An example is the
   * walking segment against the direction of a one-way road.
   *
   * @param entering segment entering the node
   * @return true when against traffic leaving the node, false otherwise
   */
  private static boolean isAgainstTrafficLeaving(MacroscopicLinkSegment entering) {
    var opposite = (MacroscopicLinkSegment) entering.getOppositeDirectionSegment();
    return !entering.isAnyModeAllowed(OsmTrafficSignalIntegrator::isControlledVehicleMode) &&
        opposite != null && opposite.isAnyModeAllowed(OsmTrafficSignalIntegrator::isControlledVehicleMode);
  }

  /**
   * Verify if a mode is one a traffic signal controls: a motorised road mode, or a rail mode such as a tram
   *
   * @param mode to check
   * @return true when controlled by a traffic signal, false otherwise
   */
  private static boolean isControlledVehicleMode(Mode mode) {
    return mode.isMotorisedRoadMode() || mode.isRailMode();
  }

  /**
   * Verify if a mode is the predefined bicycle mode
   *
   * @param mode to check
   * @return true when the bicycle mode, false otherwise
   */
  private static boolean isBicycleMode(Mode mode) {
    return mode.getPredefinedModeType().equals(PredefinedModeType.BICYCLE);
  }

  /**
   * Verify if two links are on the same OSM way, or on OSM ways with the same name. This still holds after PLANit broke
   * an OSM way into several links. It compares the external ids, which are OSM way ids, and the names.
   *
   * @param one link
   * @param other link
   * @return true when on the same OSM way or on OSM ways with the same name, false otherwise
   */
  private static boolean isSameOsmWayOrName(Link one, Link other) {
    if (one.getExternalId() != null && one.getExternalId().equals(other.getExternalId())) {
      return true;
    }
    return one.getName() != null && !one.getName().isBlank() && one.getName().equals(other.getName());
  }

  /**
   * The links of the other roads at a junction reached. Motorised road traffic can use them in at least one direction.
   * They are not on the OSM way arrived along, nor on a way with the same name, see
   * {@link #isSameOsmWayOrName(Link, Link)}.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @return links of the other roads, empty when none
   */
  private static List<MacroscopicLink> crossingRoadLinks(JunctionAhead junctionAhead) {
    var arrivingLink = junctionAhead.arriving.getParent();
    return junctionAhead.getJunction().getEdges().stream().map(MacroscopicLink.class::cast)
        .filter(link -> link != arrivingLink && isMotorisedRoadLink(link) && !isSameOsmWayOrName(link, arrivingLink))
        .collect(Collectors.toList());
  }

  /**
   * Capacity of the road crossing at a junction reached from a signal. It is the highest capacity of any segment of the
   * other roads there that motorised road traffic can use, in either direction, see
   * {@link #crossingRoadLinks(JunctionAhead)}. A segment's capacity is its capacity per lane times its lanes.
   *
   * @param junctionAhead reached from the signal
   * @return capacity in pcu/h, 0 when no other road meets it
   */
  private static double crossingCapacity(JunctionAhead junctionAhead) {
    double capacity = 0;
    for (var link : crossingRoadLinks(junctionAhead)) {
      for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
        if (segment != null && isMotorisedRoadSegment(segment)) {
          capacity = Math.max(capacity, segment.getCapacityOrDefaultPcuH());
        }
      }
    }
    return capacity;
  }

  /**
   * Verify if the OSM ways ending at a node disagree on their orientation there. They disagree when both run into the
   * node, or both run out of it. A direction tagged on the node then means opposite directions along them.
   *
   * @param node the ways meet at
   * @param roadLinks pieces of the ways ending at the node, usable by motorised road modes
   * @return true when they disagree, false otherwise
   */
  private static boolean waysDisagreeOnOrientation(Node node, List<MacroscopicLink> roadLinks) {
    long runningIn = roadLinks.stream().filter(link -> link.getVertexB() == node).count();
    return roadLinks.size() == 2 && runningIn != 1;
  }

  /**
   * The segment that continues the way beyond a node. It is the only segment leaving the node, on a link other than the
   * one arrived on, that is an allowed continuation. When only the same OSM way or name may continue, it must also be
   * on the same OSM way, or on a way with the same name, see {@link #isSameOsmWayOrName(Link, Link)}. When more than
   * one segment qualifies, the way does not continue clearly, and nothing is returned.
   *
   * @param arriving segment arriving at the node; its link is never a continuation
   * @param node to continue beyond; only segments leaving it are considered
   * @param allowedContinuation the segments the way may continue on
   * @param sameOsmWayOrNameOnly true when only the same OSM way, or a way with the same name, may continue, as past a
   *                             junction. False when any allowed continuation may, as through a node the way simply
   *                             passes
   * @return the continuing segment, null when none qualifies, or when more than one does
   */
  private static MacroscopicLinkSegment findContinuingLinkSegment(MacroscopicLinkSegment arriving, Node node,
      Predicate<MacroscopicLinkSegment> allowedContinuation, boolean sameOsmWayOrNameOnly) {
    var continuing = findAllowedContinuations(arriving, node, allowedContinuation).stream()
        .filter(leaving -> !sameOsmWayOrNameOnly || isSameOsmWayOrName(leaving.getParent(), arriving.getParent()))
        .limit(2).collect(Collectors.toList());
    return continuing.size() == 1 ? continuing.get(0) : null;
  }

  /**
   * The segments leaving a node, on a link other than the one arrived on, that are allowed continuations
   *
   * @param arriving segment arriving at the node; its link is never a continuation
   * @param node to continue beyond; only segments leaving it are considered
   * @param allowedContinuation the segments the way may continue on
   * @return allowed continuations, empty when none
   */
  private static List<MacroscopicLinkSegment> findAllowedContinuations(MacroscopicLinkSegment arriving, Node node,
      Predicate<MacroscopicLinkSegment> allowedContinuation) {
    var continuations = new ArrayList<MacroscopicLinkSegment>(2);
    for (MacroscopicLinkSegment leaving : node.<MacroscopicLinkSegment>getExitLinkSegments()) {
      if (leaving.getParent() != arriving.getParent() && allowedContinuation.test(leaving)) {
        continuations.add(leaving);
      }
    }
    return continuations;
  }

  /**
   * Distance from a signal to the nearest isolated signalised crossing before the junction a direction resolves to. A
   * crossing counts when it lies on the segment arriving at that junction, or at a junction passed on the way, and is
   * closer to that junction than the signal is.
   *
   * @param junctions reached in the direction, nearest first
   * @param resolved the junction the direction resolves to, one of them
   * @param isolatedCrossings distances from isolated crossings to the junctions they reach, per segment arriving there
   * @return distance in metres, infinite when no isolated crossing lies before the junction
   */
  private static double isolatedCrossingDistance(List<JunctionAhead> junctions, JunctionAhead resolved,
      Map<MacroscopicLinkSegment, List<Double>> isolatedCrossings) {
    double crossingDistance = Double.POSITIVE_INFINITY;
    for (var passed : junctions) {
      for (double toJunction : isolatedCrossings.getOrDefault(passed.arriving, List.of())) {
        if (Precision.smaller(toJunction, passed.distance, Precision.EPSILON_6)) {
          crossingDistance = Math.min(crossingDistance, passed.distance - toJunction);
        }
      }
      if (passed == resolved) {
        break;
      }
    }
    return crossingDistance;
  }

  /**
   * First pass. Each recorded signal is discarded, postponed to a later pass, or its intersection is constructed, see
   * {@link #discardPostponeOrConstructIntersection(long, FirstPassResult)}.
   *
   * @return the postponed signals and the junction nodes with a vehicle signal on them, for the passes after it
   */
  private FirstPassResult discardPostponeOrConstructIntersections() {
    var firstPass = new FirstPassResult();
    for (long osmNodeId : networkData.getTrafficSignalOsmNodeIds()) {
      discardPostponeOrConstructIntersection(osmNodeId, firstPass);
    }
    return firstPass;
  }

  /**
   * Discard a recorded signal, postpone it to a later pass, or construct its intersection now. If the signal and its
   * own node or way are enough to decide, it is settled now: discarded, or its intersection constructed. If the outcome
   * depends on other signals, it is postponed. Per signal, the first case that applies:
   * <ul>
   * <li>Discarded: the signal controls no traffic, or it is not in the layer. Also a vehicle signal on a way that is
   * not a road, track or cycleway, or on a track or cycleway with no road in reach.</li>
   * <li>Intersection constructed: (i) a vehicle signal on a junction node makes that junction. (ii) A vehicle signal on
   * a track or cycleway makes the road crossing or branch ahead. (iii) A crossing signal on a junction node makes that
   * junction a crossing too.</li>
   * <li>Postponed: (iv) a vehicle signal on a road but not on a junction node, e.g. at a stop line before the junction.
   * It goes to the second pass with the junctions it reaches. Which of them it gets depends on the signal evidence of
   * every junction. (v) A crossing signal not on a junction node. It goes to the third pass. Whether it belongs to a
   * signalised junction nearby depends on the intersections the first two passes construct.</li>
   * </ul>
   *
   * @param osmNodeId of the signal
   * @param firstPass to collect the postponed signals and the junction nodes with a vehicle signal in
   */
  private void discardPostponeOrConstructIntersection(long osmNodeId, FirstPassResult firstPass) {
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
    var tags = OsmModelUtil.getTagsAsMap(osmNode);
    if (OsmTrafficSignalTags.isNonControllingTrafficSignals(tags)) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.SIGNAL_NOT_CONTROLLING_TRAFFIC, osmNode);
      return;
    }
    var direction = findRecognisedDirection(tags);
    if (direction == null && tags.containsKey(OsmTrafficSignalTags.TRAFFIC_SIGNALS_DIRECTION) &&
        OsmTrafficSignalTags.isVehicleTrafficSignal(tags)) {
      /* noted only: from here on the signal is handled as untagged, its direction being null */
      trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_DIRECTION_UNRECOGNISED, osmNode);
    }
    var location = locate(osmNode);
    if (location == null) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.SIGNAL_NOT_IN_LAYER, osmNode);
      return;
    }

    if (OsmTrafficSignalTags.isVehicleTrafficSignal(tags)) {
      if (location.isOnNode() && isRoadBranchNode(location.node)) {
        // attempt case (i)
        integrateSignalOnNode(osmNode, location.node, IntersectionType.JUNCTION);
        firstPass.junctionNodesWithVehicleSignal.add(location.node);
        trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_JUNCTION_NODE, osmNode);
      } else if (location.getLinks().stream().anyMatch(OsmTrafficSignalIntegrator::isMotorisedRoadLink)) {
        /* case (iv), postponed: vehicle signal on a road but not on a junction node, junctions ahead collected for
         * the second pass */
        firstPass.vehicleSignalsNotOnJunctionNode.add(collectVehicleSignal(location, direction));
      } else {
        /* attempt case (ii) of construction: no road at the signal: on a track or cycleway, the road crossing or
         * branch ahead becomes its intersection; on any other way, or with no road in reach, it is discarded */
        integrateSignalOnRailOrActiveModeWay(location);
      }
    }
    if (OsmTrafficSignalTags.isCrossingTrafficSignals(tags)) {
      if (location.isOnNode() && isJunctionNode(location.node)) {
        // attempt case (iii)
        integrateSignalOnNode(osmNode, location.node, IntersectionType.CROSSING);
        trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ON_JUNCTION_NODE, osmNode);
        if (!isRoadBranchNode(location.node)) {
          firstPass.crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch.add(osmNode);
        }
      } else {
        /* case (v), postponed: crossing signal not on a junction node, collected for the third pass */
        firstPass.crossingSignalsNotOnJunctionNode.add(osmNode);
      }
    }
  }

  /**
   * Collect a vehicle signal on a road but not on a junction node, with the junctions it reaches along the road, see
   * {@link #findTargetsAlongRoad(SignalLocation, String, double, LookAhead)}. A special case is a node where two OSM
   * ways end and the road simply continues. If those ways disagree on the tagged direction, the conflict is counted.
   * The signal then applies to one direction without knowing which, as if untagged.
   *
   * @param location of the signal
   * @param direction the signal applies to, null when not tagged
   * @return the signal with the junctions it reaches, not yet assigned
   */
  private PendingVehicleSignal collectVehicleSignal(SignalLocation location, String direction) {
    var maxDistance = settings.getMaximumDistanceVehicleSignalToJunctionInMeters();
    boolean oneDirection = OsmDirectionTags.FORWARD.equals(direction) || OsmDirectionTags.BACKWARD.equals(direction);
    if (oneDirection && location.isOnNode() && waysDisagreeOnOrientation(location.node, location.getLinks().stream()
        .filter(OsmTrafficSignalIntegrator::isMotorisedRoadLink).collect(Collectors.toList()))) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.DIRECTION_CONFLICT, location.signal);
      return new PendingVehicleSignal(
          location.signal, findTargetsAlongRoad(location, null, maxDistance, ROAD_LOOK_AHEAD), true);
    }
    return new PendingVehicleSignal(
        location.signal, findTargetsAlongRoad(location, direction, maxDistance, ROAD_LOOK_AHEAD), direction == null);
  }

  /**
   * Make a node an intersection, for a signal on it or reaching it. Every segment entering the node becomes an
   * approach. For a junction there is one exception: a segment against traffic leaving the node, see
   * {@link #isAgainstTrafficLeaving(MacroscopicLinkSegment)}. It leads to nothing the signal controls.
   *
   * @param signal the OSM node of the signal
   * @param node the signal is on or reaches
   * @param type kind of intersection the signal makes
   */
  private void integrateSignalOnNode(OsmNode signal, Node node, IntersectionType type) {
    var intersection = getOrCreateIntersection(Long.parseLong(node.getExternalId()), node, type);
    for (MacroscopicLinkSegment entering : node.<MacroscopicLinkSegment>getEntryLinkSegments()) {
      if (type != IntersectionType.JUNCTION || !isAgainstTrafficLeaving(entering)) {
        addApproach(intersection, entering, signal);
      }
    }
  }

  /**
   * Handle a vehicle signal with no road at its location: on a tram or light rail track, or on a cycleway. Its way is
   * followed to the nearest node where a road meets it. On a cycleway, only segments that allow bicycles are followed.
   * From a track, within the railway settings' maximum distance, that node becomes a signalised junction: car traffic
   * is held there for the tram. From a cycleway, within the crossing distance, it becomes a signalised crossing. Every
   * segment entering it is an approach. If the way branches before the road, and more than one branch reaches a road,
   * the branch node becomes the intersection instead, see
   * {@link #findRoadCrossingOrBranch(JunctionAhead, double, LookAhead)}. Its only approach is then the segment arriving
   * from the signal, and no road node is joined. The signal is discarded when no road is in reach, or when it is on
   * another kind of way, such as a footway. The outcome is recorded either way, and no link is broken.
   *
   * @param location of the signal
   */
  private void integrateSignalOnRailOrActiveModeWay(SignalLocation location) {
    var signal = location.signal;
    var links = location.getLinks();
    boolean onTrack = links.stream().anyMatch(link -> link.isAnyModeAllowedOnAnySegment(Mode::isRailMode));
    if (!onTrack && links.stream().noneMatch(
        link -> link.isAnyModeAllowedOnAnySegment(OsmTrafficSignalIntegrator::isBicycleMode))) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.TRAFFIC_SIGNAL_NOT_ON_ROAD_TRACK_OR_CYCLEWAY, signal);
      return;
    }
    Predicate<MacroscopicLinkSegment> travels = onTrack ?
        segment -> segment.isAnyModeAllowed(Mode::isRailMode) :
        segment -> segment.isAnyModeAllowed(OsmTrafficSignalIntegrator::isBicycleMode);
    var lookAhead = new LookAhead(travels, node -> hasMotorisedRoadLink(node) || isBranchNode(node, travels), false);
    double maxDistance = onTrack ?
        settings.getRailwaySettings().getMaximumDistanceTrackSignalToRoadCrossingInMeters() :
        settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var reached = findTargetsAlongRoad(location, null, maxDistance, lookAhead);
    var nearest = reached.stream().map(targets -> findRoadCrossingOrBranch(targets.get(0), maxDistance, lookAhead))
        .filter(Objects::nonNull).min(Comparator.comparingDouble(target -> target.distance));
    if (nearest.isEmpty()) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING, signal);
      return;
    }
    var type = onTrack ? IntersectionType.JUNCTION : IntersectionType.CROSSING;
    if (!hasMotorisedRoadLink(nearest.get().getJunction())) {
      addArrivingSegmentAsApproach(nearest.get(), signal, type);
      trackAsDiagnostic(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH, signal);
      return;
    }
    integrateSignalOnNode(signal, nearest.get().getJunction(), type);
    trackAsDiagnostic(onTrack ? OsmTrafficSignalOutcome.TRACK_SIGNAL_AT_ROAD_CROSSING :
        OsmTrafficSignalOutcome.CYCLEWAY_SIGNAL_AT_ROAD_CROSSING, signal);
  }

  /**
   * Find where the intersection of a signal on a track or cycleway goes, given the node its look-ahead reached. If a
   * road meets that node, it is that node. If the way branches there instead, each branch is followed on within the
   * maximum distance, see {@link #isBranchNode(Node, Predicate)}. If only one branch reaches a road, the result is
   * where that branch leads. If more than one does, it is the branch node itself.
   *
   * @param reached the node reached, and the segment arriving there
   * @param maxDistance from the signal a road may be at, along the way, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the road node or the branch node, with the segment arriving there, null when no branch reaches a road
   */
  private JunctionAhead findRoadCrossingOrBranch(JunctionAhead reached, double maxDistance, LookAhead lookAhead) {
    var node = reached.getJunction();
    if (hasMotorisedRoadLink(node)) {
      return reached;
    }
    JunctionAhead alongOnlyBranch = null;
    for (var leaving : findAllowedContinuations(reached.arriving, node, lookAhead.travels)) {
      var targets = findJunctionsAhead(
          leaving, reached.distance + leaving.getParent().getLengthKm() * 1000, maxDistance, lookAhead);
      var alongBranch = targets.isEmpty() ? null : findRoadCrossingOrBranch(targets.get(0), maxDistance, lookAhead);
      if (alongBranch != null) {
        if (alongOnlyBranch != null) {
          return reached;
        }
        alongOnlyBranch = alongBranch;
      }
    }
    return alongOnlyBranch;
  }

  /**
   * Second pass: assign the vehicle signals collected in the first pass to their junctions. It works in six steps:
   * <ol>
   * <li>Find the isolated signalised crossings. A crossing is isolated when no junction lies within the crossing
   * distance beyond it, see {@link #findIsolatedCrossingsByJunctionArrival(List, VehicleSignalEvidence)}.</li>
   * <li>Some signals have no usable direction. They are untagged, or their direction means opposite ways on the two
   * roads through them. Such a signal may reach junctions both ways. It then keeps only one direction: the one whose
   * first target is nearest. That target is the junction it would be assigned to, or an isolated crossing before it.
   * There is one exception. If the other way is at most {@link #NEAR_TIE_DELTA_DISTANCE_M} farther, and a road of
   * higher capacity crosses there, that way is kept instead.</li>
   * <li>Some signals have an isolated crossing between them and their junction. In that direction the signal is the
   * crossing's stop line, so the direction is dropped. A signal with no direction left goes to the third pass. There
   * it joins the crossing.</li>
   * <li>Decide again which junctions have signals of their own, their signal evidence. This uses the signals on
   * junction nodes and the signals still left. Stop lines of isolated crossings no longer count.</li>
   * <li>Assign each signal left to its junction in each direction, see
   * {@link #resolveJunction(List, VehicleSignalEvidence)}. A signal that reaches no junction is ignored when it is at
   * the edge of the parsed area. Otherwise it goes to the third pass.</li>
   * <li>Register the intersections created so far.</li>
   * </ol>
   * Steps 1 to 3 still count every signal as evidence, stop lines of isolated crossings included. That is because which
   * signals are such stop lines is only known once those steps are done.
   *
   * @param firstPass holds the vehicle signals not on a junction node, the junction nodes with a vehicle signal on
   *                  them, and the crossing signals, from the first pass
   * @return the stop lines of isolated crossings and the vehicle signals without a junction ahead, for the third pass
   */
  private SecondPassResult assignVehicleSignalsToJunctions(FirstPassResult firstPass) {
    var secondPass = new SecondPassResult();

    /* step 1: find the isolated crossings. The evidence still includes the signals that may turn out to be stop
     * lines */
    var evidenceWithIsolatedCrossingStopLines = new VehicleSignalEvidence(
        firstPass.junctionNodesWithVehicleSignal, firstPass.vehicleSignalsNotOnJunctionNode);
    var isolatedCrossings = findIsolatedCrossingsByJunctionArrival(
        Stream.concat(firstPass.crossingSignalsNotOnJunctionNode.stream(),
            firstPass.crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch.stream())
            .collect(Collectors.toList()),
        evidenceWithIsolatedCrossingStopLines);

    /* steps 2 and 3: keep one direction when it is still unknown, then drop the directions past an isolated
     * crossing. A signal with no direction left is that crossing's stop line. It is deferred to the third pass,
     * which adds it as an approach of the crossing's intersection, when there is one */
    var vehicleSignalsExceptIsolatedCrossingStopLines =
        new ArrayList<PendingVehicleSignal>(firstPass.vehicleSignalsNotOnJunctionNode.size());
    for (var pendingSignal : firstPass.vehicleSignalsNotOnJunctionNode) {
      var remainingDirectionsOfPendingSignal = dropDirectionsPastIsolatedCrossings(
          inNearestDirectionWhenUnknown(pendingSignal, isolatedCrossings, evidenceWithIsolatedCrossingStopLines),
          isolatedCrossings, evidenceWithIsolatedCrossingStopLines);
      if (remainingDirectionsOfPendingSignal != pendingSignal &&
          remainingDirectionsOfPendingSignal.junctionsPerDirection.isEmpty()) {
        /* no direction left: the signal is the stop line of an isolated crossing. The third pass joins it there */
        trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING, pendingSignal.signal);
        secondPass.isolatedCrossingStopLines.add(pendingSignal.signal);
      } else {
        vehicleSignalsExceptIsolatedCrossingStopLines.add(remainingDirectionsOfPendingSignal);
      }
    }

    /* step 4: decide again which junctions have signals of their own, now without the stop lines of isolated
     * crossings */
    var evidenceWithoutIsolatedCrossingStopLines = new VehicleSignalEvidence(
        firstPass.junctionNodesWithVehicleSignal, vehicleSignalsExceptIsolatedCrossingStopLines);

    /* step 5: assign each signal left to its junctions */
    for (var pendingSignal : vehicleSignalsExceptIsolatedCrossingStopLines) {
      if (assignToJunctions(pendingSignal, evidenceWithoutIsolatedCrossingStopLines)) {
        trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_SNAPPED, pendingSignal.signal);
      } else if (isAtEdgeOfParsedArea(pendingSignal.signal)) {
        trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA, pendingSignal.signal);
      } else {
        /* no junction ahead, and not at the edge of the parsed area: the third pass handles it */
        secondPass.vehicleSignalsWithoutJunction.add(pendingSignal.signal);
      }
    }

    /* step 6: register the intersections created so far */
    created.forEach(this::register);
    created.clear();
    return secondPass;
  }

  /**
   * Verify if a link is part of a lowest order road, see {@link #LOWEST_ORDER_ROAD_TYPES}
   *
   * @param link to verify
   * @return true when part of a lowest order road, false otherwise
   */
  private boolean isOnLowestOrderRoad(Link link) {
    return lowestOrderRoadOsmWayIds.contains(link.getExternalId());
  }

  /**
   * Verify if only lowest order roads of lower capacity join the road at a junction reached. The road arrived along is
   * not a lowest order road. Every other road there is one, see {@link #crossingRoadLinks(JunctionAhead)}. And their
   * capacity is below that of the segment arriving.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @return true when only lowest order roads of lower capacity join there, false otherwise
   */
  private boolean isJoinedOnlyByLowestOrderRoads(JunctionAhead junctionAhead) {
    if (isOnLowestOrderRoad(junctionAhead.arriving.getParent())) {
      return false;
    }
    var otherRoadLinks = crossingRoadLinks(junctionAhead);
    return !otherRoadLinks.isEmpty() && otherRoadLinks.stream().allMatch(this::isOnLowestOrderRoad) &&
        Precision.smaller(
            crossingCapacity(junctionAhead), junctionAhead.arriving.getCapacityOrDefaultPcuH(), Precision.EPSILON_6);
  }

  /**
   * Verify if a junction reached counts when placing signals near it. It counts when it has signals of its own, or when
   * more than lowest order roads of lower capacity join the road there.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @param evidence of the junctions' own signals
   * @return true when it counts, false otherwise
   */
  private boolean countsAsJunction(JunctionAhead junctionAhead, VehicleSignalEvidence evidence) {
    return evidence.hasOwnSignals(junctionAhead) || !isJoinedOnlyByLowestOrderRoads(junctionAhead);
  }

  /**
   * The junction a vehicle signal is assigned to in one direction. Normally it is the first junction reached that
   * counts, see {@link #countsAsJunction(JunctionAhead, VehicleSignalEvidence)}. So a junction where only service lanes
   * or living streets without signals join is passed over, when one that counts follows within reach. If none counts,
   * the first junction reached is used. There is one exception. If the junction found has no signals of its own, a
   * junction further along the same OSM way, or a way with the same name, may be taken instead. That happens when a
   * road of higher capacity crosses there, within the maximum distance. The junction with the highest capacity crossing
   * road wins, the nearest on a tie.
   *
   * @param junctions reached in the direction, nearest first
   * @param evidence of the junctions' own signals
   * @return the junction assigned to
   */
  private JunctionAhead resolveJunction(List<JunctionAhead> junctions, VehicleSignalEvidence evidence) {
    int first = 0;
    while (first < junctions.size() && !countsAsJunction(junctions.get(first), evidence)) {
      first++;
    }
    if (first == junctions.size()) {
      first = 0;
    }
    var chosen = junctions.get(first);
    if (!evidence.hasOwnSignals(chosen)) {
      for (var further : junctions.subList(first + 1, junctions.size())) {
        if (Precision.greater(crossingCapacity(further), crossingCapacity(chosen), Precision.EPSILON_6)) {
          chosen = further;
        }
      }
    }
    return chosen;
  }

  /**
   * Find the isolated signalised crossings. A crossing is isolated when no junction lies within the crossing distance
   * beyond it. A junction joined only by lowest order roads of lower capacity does not count here, unless it has
   * signals of its own. For each isolated crossing, the result keeps the junctions it reaches along the road, within
   * the maximum distance from a vehicle signal to a junction, up to and including the first one that counts. They are
   * kept per segment arriving at each junction, with the distance to it. A crossing that is isolated only because such
   * a lowest order junction lies within the crossing distance is counted.
   *
   * @param crossingSignals the crossing signals on the road but not on a junction node
   * @param evidence of the junctions' own signals, from every vehicle signal collected, stop lines of isolated
   *                 crossings included
   * @return distances from isolated crossings to the junctions they reach, per segment arriving there
   */
  private Map<MacroscopicLinkSegment, List<Double>> findIsolatedCrossingsByJunctionArrival(
      List<OsmNode> crossingSignals, VehicleSignalEvidence evidence) {
    var crossingDistance = settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var reach = settings.getMaximumDistanceVehicleSignalToJunctionInMeters();
    var isolated = new IdentityHashMap<MacroscopicLinkSegment, List<Double>>();
    for (var crossing : crossingSignals) {
      boolean isolatedPastLowestOrderRoad = false;
      for (var junctions : findTargetsAlongRoad(crossing, null, reach, ROAD_LOOK_AHEAD)) {
        var passed = new ArrayList<JunctionAhead>(1);
        boolean isIsolated = true;
        boolean lowestOrderRoadWithinCrossingDistance = false;
        for (var junction : junctions) {
          passed.add(junction);
          boolean withinCrossingDistance = !Precision.greater(junction.distance, crossingDistance, Precision.EPSILON_6);
          if (countsAsJunction(junction, evidence)) {
            isIsolated = !withinCrossingDistance;
            break;
          }
          lowestOrderRoadWithinCrossingDistance |= withinCrossingDistance;
        }
        if (isIsolated) {
          passed.forEach(junction ->
              isolated.computeIfAbsent(junction.arriving, segment -> new ArrayList<>(1)).add(junction.distance));
          isolatedPastLowestOrderRoad |= lowestOrderRoadWithinCrossingDistance;
        }
      }
      if (isolatedPastLowestOrderRoad) {
        trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_ISOLATED_PAST_LOWEST_ORDER_ROAD, crossing);
      }
    }
    return isolated;
  }

  /**
   * The first target of a signal in one direction: the junction it would be assigned to there, see
   * {@link #resolveJunction(List, VehicleSignalEvidence)}, or an isolated signalised crossing before it
   *
   * @param junctions reached in the direction, nearest first
   * @param isolatedCrossings distances from isolated crossings to the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @return the first target
   */
  private DirectionTarget findFirstTarget(List<JunctionAhead> junctions,
      Map<MacroscopicLinkSegment, List<Double>> isolatedCrossings, VehicleSignalEvidence evidence) {
    var junction = resolveJunction(junctions, evidence);
    double crossingDistance = isolatedCrossingDistance(junctions, junction, isolatedCrossings);
    return Precision.smaller(crossingDistance, junction.distance, Precision.EPSILON_6) ?
        new DirectionTarget(junctions, junction, crossingDistance, true) :
        new DirectionTarget(junctions, junction, junction.distance, false);
  }

  /**
   * Keep only one direction for a vehicle signal whose direction is unknown: the one whose first target is nearest.
   * There is one exception. If that target is a junction, and another direction reaches a junction at most
   * {@link #NEAR_TIE_DELTA_DISTANCE_M} farther where a road of higher capacity crosses, that direction is kept instead.
   *
   * @param pending the signal with the junctions it reaches
   * @param isolatedCrossings distances from isolated crossings to the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @return the signal as it is when its direction is known, or when it reaches targets in one direction only.
   *         Otherwise a copy with only the kept direction
   */
  private PendingVehicleSignal inNearestDirectionWhenUnknown(PendingVehicleSignal pending,
      Map<MacroscopicLinkSegment, List<Double>> isolatedCrossings, VehicleSignalEvidence evidence) {
    if (!pending.oneDirectionUnknown || pending.junctionsPerDirection.size() < 2) {
      return pending;
    }
    var targets = pending.junctionsPerDirection.stream()
        .map(junctions -> findFirstTarget(junctions, isolatedCrossings, evidence))
        .sorted(Comparator.comparingDouble(target -> target.distance)).collect(Collectors.toList());
    var nearest = targets.get(0);
    var chosen = nearest;
    if (!nearest.isCrossing) {
      for (var other : targets.subList(1, targets.size())) {
        if (!other.isCrossing &&
            !Precision.greater(other.distance - nearest.distance, NEAR_TIE_DELTA_DISTANCE_M, Precision.EPSILON_6) &&
            Precision.greater(crossingCapacity(other.junction), crossingCapacity(chosen.junction), Precision.EPSILON_6)) {
          chosen = other;
        }
      }
    }
    trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN, pending.signal);
    return new PendingVehicleSignal(pending.signal, List.of(chosen.junctions), false);
  }

  /**
   * Drop the directions in which an isolated signalised crossing lies between the signal and its junction, see
   * {@link #isolatedCrossingDistance(List, JunctionAhead, Map)}
   *
   * @param pending the signal with the junctions it reaches
   * @param isolatedCrossings distances from isolated crossings to the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @return the signal as it is when no direction is dropped. Otherwise a copy with the directions left
   */
  private PendingVehicleSignal dropDirectionsPastIsolatedCrossings(PendingVehicleSignal pending,
      Map<MacroscopicLinkSegment, List<Double>> isolatedCrossings, VehicleSignalEvidence evidence) {
    var kept = pending.junctionsPerDirection.stream().filter(junctions -> Double.isInfinite(
        isolatedCrossingDistance(junctions, resolveJunction(junctions, evidence), isolatedCrossings)))
        .collect(Collectors.toList());
    return kept.size() == pending.junctionsPerDirection.size() ? pending :
        new PendingVehicleSignal(pending.signal, kept, pending.oneDirectionUnknown);
  }

  /**
   * Assign a vehicle signal not on a junction node to a junction in each direction it applies to, see
   * {@link #resolveJunction(List, VehicleSignalEvidence)}. The segment arriving at that junction becomes an approach.
   *
   * @param pending the signal with the junctions it reaches
   * @param evidence of the junctions' own signals
   * @return true when assigned to at least one junction, false otherwise
   */
  private boolean assignToJunctions(PendingVehicleSignal pending, VehicleSignalEvidence evidence) {
    boolean higherClassJunctionChosen = false;
    for (var junctions : pending.junctionsPerDirection) {
      var chosen = resolveJunction(junctions, evidence);
      higherClassJunctionChosen |= chosen != junctions.get(0);
      addArrivingSegmentAsApproach(chosen, pending.signal, IntersectionType.JUNCTION);
    }
    if (higherClassJunctionChosen) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.HIGHER_CLASS_JUNCTION_CHOSEN, pending.signal);
    }
    return !pending.junctionsPerDirection.isEmpty();
  }

  /**
   * Verify if a signal is at the edge of the parsed area. It is when it lies outside the area, on a way running out of
   * it. It also is when it lies inside, but no farther from the edge than the maximum distance from a vehicle signal to
   * its junction. The junction it controls is then likely cut off.
   *
   * @param signal the OSM node of the signal
   * @return true when at the edge of the parsed area, false otherwise, also when the area is not bounded
   */
  private boolean isAtEdgeOfParsedArea(OsmNode signal) {
    if (boundingArea.isEmpty()) {
      return false;
    }
    var position = OsmNodeUtils.createPoint(signal);
    return !boundingArea.isWhollyWithinBoundaryArea(position, true) ||
        boundingArea.calculateProjectedDistanceToBoundingPolygon(position, true) <=
            settings.getMaximumDistanceVehicleSignalToJunctionInMeters();
  }

  /**
   * Third pass, in three steps. First, each collected crossing signal is either part of a signalised junction nearby,
   * or an isolated crossing. Second, each stop line of an isolated crossing joins that crossing, when it has an
   * intersection. Third, each
   * vehicle signal left without a junction joins the signalised crossing ahead, or else becomes an isolated crossing.
   * Isolated crossings are broken at and registered one at a time. Breaking a link through the layer modifier keeps the
   * intersections registered so far consistent, and the record of signals per approach follows along.
   *
   * @param firstPass holds the crossing signals not on a junction node, postponed by the first pass
   * @param secondPass holds the stop lines of isolated crossings and the vehicle signals without a junction ahead,
   *                   left by the second pass
   */
  private void integrateCrossingsAndUnassignedVehicleSignals(FirstPassResult firstPass,
      SecondPassResult secondPass) {
    DirectedGraphModifierListener followBreaks = new SignalsFollowBrokenApproachesHandler();
    networkLayer.getLayerModifier().addListener(followBreaks);
    try {
      for (var crossingSignal : firstPass.crossingSignalsNotOnJunctionNode) {
        if (isPartOfSignalisedJunction(crossingSignal)) {
          /* nothing more to do, now or later: the crossing lies on an approach of a signalised junction nearby, so
           * that junction's intersection already holds it. No link is broken and nothing is added; only the outcome
           * is recorded */
          trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION, crossingSignal);
        } else {
          trackAsDiagnostic(constructIntersectionAtSignalPosition(crossingSignal) ?
              OsmTrafficSignalOutcome.ISOLATED_CROSSING_OWN_INTERSECTION :
              OsmTrafficSignalOutcome.ISOLATED_CROSSING_NO_LINK_BROKEN, crossingSignal);
        }
      }
      /* stop lines of isolated crossings join them where they have an intersection */
      secondPass.isolatedCrossingStopLines.forEach(this::addApproachToSignalisedCrossingAhead);

      /* vehicle signals without a junction in reach join the signalised crossing ahead, all before any of them becomes
       * a crossing itself so the outcome does not depend on their order; the others are isolated crossings too:
       * what crosses there is unknown, but not only cars */
      var isolated = new ArrayList<OsmNode>();
      for (var vehicleSignal : secondPass.vehicleSignalsWithoutJunction) {
        if (addApproachToSignalisedCrossingAhead(vehicleSignal)) {
          trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD, vehicleSignal);
        } else {
          isolated.add(vehicleSignal);
        }
      }
      for (var vehicleSignal : isolated) {
        trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD, vehicleSignal);
        networkData.getParseDiagnostics().registerNearestJunction(
            vehicleSignal, findNearestJunctionDistance(vehicleSignal));
        constructIntersectionAtSignalPosition(vehicleSignal);
      }
    } finally {
      networkLayer.getLayerModifier().removeListener(followBreaks);
    }
  }

  /**
   * Verify if a crossing signal not on a junction node is part of a signalised junction. Its walking infrastructure is
   * not parsed and attached. It is part of a junction when, in either direction along the road, the first junction
   * within the crossing distance is signalised, and the segment arriving there is one of its approaches.
   *
   * @param signal the OSM node of the crossing signal
   * @return true when part of a signalised junction, false otherwise
   */
  private boolean isPartOfSignalisedJunction(OsmNode signal) {
    var maxDistance = settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    return findFirstTargetsAlongRoad(signal, null, maxDistance, ROAD_LOOK_AHEAD).stream().anyMatch(first -> {
      var intersection = intersectionsByOsmNodeId.get(Long.parseLong(first.getJunction().getExternalId()));
      return intersection != null && intersection.isSignalised() &&
          intersection.getApproachSegment(first.arriving.getId()) != null;
    });
  }

  /**
   * Verify if a node is a signalised crossing: it has an intersection of kind crossing
   *
   * @param node to check
   * @return true when a signalised crossing, false otherwise
   */
  private boolean isSignalisedCrossingNode(Node node) {
    var intersection = intersectionsByOsmNodeId.get(Long.parseLong(node.getExternalId()));
    return intersection != null && intersection.getTypes().contains(IntersectionType.CROSSING);
  }

  /**
   * Add the segment arriving at the signalised crossing ahead of a vehicle signal as an approach of that crossing's
   * intersection. In each direction the signal applies to, the first signalised crossing within the crossing distance
   * is taken. Used for the stop lines of isolated crossings, and for vehicle signals without a junction in reach.
   *
   * @param signal the OSM node of the signal
   * @return true when at least one approach was added, false otherwise
   */
  private boolean addApproachToSignalisedCrossingAhead(OsmNode signal) {
    var direction = findRecognisedDirection(OsmModelUtil.getTagsAsMap(signal));
    var reached = findFirstTargetsAlongRoad(signal, direction,
        settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters(), crossingLookAhead);
    for (var crossing : reached) {
      addApproach(intersectionsByOsmNodeId.get(Long.parseLong(crossing.getJunction().getExternalId())),
          crossing.arriving, signal);
    }
    return !reached.isEmpty();
  }

  /**
   * Construct an intersection at the signal's own position, for a signal with no junction to belong to: an isolated
   * crossing signal, or a vehicle signal without a junction in reach. This is only done when breaking links at
   * isolated crossings is on. Otherwise nothing is created. If the signal is not on a PLANit node yet, the link is
   * broken there first. The intersection is of kind crossing: road traffic is held there for something crossing the
   * road. Every segment entering it becomes an approach. A newly created intersection is registered straight away.
   * That keeps it consistent when links are broken for later crossings.
   *
   * @param signal the OSM node of the signal
   * @return true when the intersection was constructed, false when breaking links at isolated crossings is off
   */
  private boolean constructIntersectionAtSignalPosition(OsmNode signal) {
    if (!settings.isBreakLinksAtIsolatedSignalisedCrossings()) {
      return false;
    }
    var node = PlanitOsmNetworkLayerUtils.getOrCreatePopulateAndRegisterNode(
        signal, networkLayer, layerData, settings.isRetainOsmTags());
    PlanitNetworkLayerModifierUtils.breakLinksWithInternalNode(
        networkLayer, layerData, node, geoUtils.getCoordinateReferenceSystem());
    var intersection = getOrCreateIntersection(signal.getId(), node, IntersectionType.CROSSING);
    for (MacroscopicLinkSegment entering : node.<MacroscopicLinkSegment>getEntryLinkSegments()) {
      addApproach(intersection, entering, signal);
    }
    if (created.remove(intersection)) {
      register(intersection);
    }
    return true;
  }

  /**
   * How far along the road the nearest junction is from a vehicle signal, in either direction, up to
   * {@link OsmNearestJunctionBucket#MAX_DISTANCE_M}. Used for reporting only.
   *
   * @param signal the OSM node of the signal
   * @return distance in metres, null when none within reach
   */
  private Double findNearestJunctionDistance(OsmNode signal) {
    return findFirstTargetsAlongRoad(signal, null, OsmNearestJunctionBucket.MAX_DISTANCE_M, ROAD_LOOK_AHEAD).stream()
        .map(first -> first.distance).min(Double::compare).orElse(null);
  }

  /**
   * Record the intersections none of whose approaches a motorised road or rail mode can use. This is done once all
   * signals are handled. Only then is an intersection complete: a later signal can still add approaches to one
   * registered earlier.
   */
  private void recordIntersectionsWithoutControlledApproach() {
    var planitEntityDiagnostics = networkData.getPlanitEntityDiagnostics();
    for (var intersection : registered) {
      if (intersection.getApproachSegments().stream().noneMatch(
          approach -> approach.isAnyModeAllowed(OsmTrafficSignalIntegrator::isControlledVehicleMode))) {
        planitEntityDiagnostics.registerIssue(
            OsmPlanitEntityIssue.INTERSECTION_WITHOUT_CONTROLLED_APPROACH, intersection.getExternalId());
      }
    }
  }

  /**
   * Find where a signal sits in the layer now: on a PLANit node, or on a shape node inside links
   *
   * @param signal the OSM node of the signal
   * @return its location, null when it is not in the layer
   */
  private SignalLocation locate(OsmNode signal) {
    var node = layerData.getPlanitNodeByOsmNode(signal);
    if (node != null) {
      return new SignalLocation(signal, node, null);
    }
    // internal node of link, stop-line on osm way not on extreme node
    var links = layerData.findPlanitLinksWithInternalLocation(OsmNodeUtils.createPoint(signal));
    return links == null || links.isEmpty() ? null : new SignalLocation(signal, null, links);
  }

  /**
   * The targets a look-ahead reaches along the road from a signal, in each direction it applies to, within a maximum
   * distance. The signal is located first, see
   * {@link #findTargetsAlongRoad(SignalLocation, String, double, LookAhead)}.
   *
   * @param signal the OSM node of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the targets reached per direction, nearest first, no direction without one
   */
  private List<List<JunctionAhead>> findTargetsAlongRoad(
      OsmNode signal, String direction, double maxDistance, LookAhead lookAhead) {
    var location = locate(signal);
    return location == null ? List.of() : findTargetsAlongRoad(location, direction, maxDistance, lookAhead);
  }

  /**
   * The targets a look-ahead reaches along the road from a signal, in each direction it applies to, within a maximum
   * distance. From a signal on a node, it follows the segments starting at the node. Those pass the signal and lie
   * wholly ahead of it; a segment ending at the node is behind it. From a signal inside links, it follows every segment
   * of them, since each passes it. The distance to a segment's end is then measured from the signal's position.
   *
   * @param location of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the targets reached per direction, nearest first, no direction without one
   */
  private List<List<JunctionAhead>> findTargetsAlongRoad(
      SignalLocation location, String direction, double maxDistance, LookAhead lookAhead) {
    if (location.isOnNode()) {
      return findAlongLinks(location.getLinks(), direction, segment -> segment.getUpstreamVertex() == location.node,
          segment -> segment.getParent().getLengthKm() * 1000, maxDistance, lookAhead);
    }
    var signalPosition = OsmNodeUtils.createPoint(location.signal);
    return findAlongLinks(location.links, direction, segment -> true,
        segment -> PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(segment, signalPosition, geoUtils),
        maxDistance, lookAhead);
  }

  /**
   * The first target a look-ahead reaches along the road from a signal, in each direction it applies to, within a
   * maximum distance
   *
   * @param signal the OSM node of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the first target reached per direction, empty when none within reach
   */
  private List<JunctionAhead> findFirstTargetsAlongRoad(
      OsmNode signal, String direction, double maxDistance, LookAhead lookAhead) {
    var reached = findTargetsAlongRoad(signal, direction, maxDistance, lookAhead);
    return reached.stream().map(junctions -> junctions.get(0)).collect(Collectors.toList());
  }

  /**
   * Find the targets ahead along the segments of the given links, without changing anything. A segment is followed when
   * the look-ahead travels it, it carries traffic past the signal, and its direction is one the signal applies to. Each
   * link is a piece of an OSM way, and its A to B segment is the way's forward direction.
   *
   * @param links the signal lies on or ends at
   * @param direction the signal applies to, relative to the OSM way of each link, see
   *                  {@link PlanitLinkOsmUtils#isInOsmDirection(MacroscopicLinkSegment, String)}
   * @param passesSignal true for a segment carrying traffic past the signal, not towards it
   * @param distanceFromSignal from the signal to a passing segment's downstream end, along the road, in metres
   * @param maxDistance from the signal a target may be at, along the way, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return per passing segment that reaches any, the targets reached, nearest first
   */
  private List<List<JunctionAhead>> findAlongLinks(List<MacroscopicLink> links, String direction,
      Predicate<MacroscopicLinkSegment> passesSignal, ToDoubleFunction<MacroscopicLinkSegment> distanceFromSignal,
      double maxDistance, LookAhead lookAhead) {
    var found = new ArrayList<List<JunctionAhead>>(2);
    for (var link : links) {
      for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
        if (segment != null && lookAhead.travels.test(segment) &&
            PlanitLinkOsmUtils.isInOsmDirection(segment, direction) && passesSignal.test(segment)) {
          var junctions = findJunctionsAhead(
              segment, distanceFromSignal.applyAsDouble(segment), maxDistance, lookAhead);
          if (!junctions.isEmpty()) {
            found.add(junctions);
          }
        }
      }
    }
    return found;
  }

  /**
   * Follow the way downstream from a segment and collect the targets reached, without changing anything. It passes
   * through nodes where the way simply continues, crosswalks included. If the look-ahead carries on past targets, it
   * does so along the same OSM way, or a way with the same name. It stops at a dead end, where the way cannot be
   * travelled in this direction, or where it does not continue clearly, see
   * {@link #findContinuingLinkSegment(MacroscopicLinkSegment, Node, Predicate, boolean)}. It also stops when it gets
   * back to a node already passed, or beyond the maximum distance, measured along the way.
   *
   * @param segment carrying traffic past the signal
   * @param distanceToItsEnd from the signal to the segment's downstream end, along the way, in metres
   * @param maxDistance from the signal a target may be at, along the way, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the targets reached, nearest first
   */
  private List<JunctionAhead> findJunctionsAhead(
      MacroscopicLinkSegment segment, double distanceToItsEnd, double maxDistance, LookAhead lookAhead) {
    var junctions = new ArrayList<JunctionAhead>(1);
    var distance = distanceToItsEnd;
    var passed = new HashSet<Node>();
    var current = segment;
    while (current != null && distance <= maxDistance) {
      var ahead = (Node) current.getDownstreamVertex();
      if (!passed.add(ahead)) {
        break;
      }
      if (lookAhead.isTarget.test(ahead)) {
        junctions.add(new JunctionAhead(current, distance));
        if (!lookAhead.beyondTarget) {
          break;
        }
        current = findContinuingLinkSegment(current, ahead, lookAhead.travels, true);
      } else {
        current = findContinuingLinkSegment(current, ahead, lookAhead.travels, false);
      }
      if (current != null) {
        distance += current.getParent().getLengthKm() * 1000;
      }
    }
    return junctions;
  }

  /**
   * Get the intersection created for an OSM node, or create it, unregistered, with the given kind. It is signalised.
   * Its external id lists the signals defining it, added as they come.
   *
   * @param osmNodeId of the node or signal the intersection is created for
   * @param node its member node
   * @param type kind to create it with, or to add to one already created
   * @return intersection
   */
  private Intersection getOrCreateIntersection(long osmNodeId, Node node, IntersectionType type) {
    var intersection = intersectionsByOsmNodeId.get(osmNodeId);
    if (intersection != null) {
      intersection.addType(type);
      return intersection;
    }
    intersection = networkLayer.getIntersections().getFactory().create(node, IntersectionControlType.SIGNALISED, type);
    intersection.setXmlId(String.valueOf(intersection.getId()));
    intersectionsByOsmNodeId.put(osmNodeId, intersection);
    created.add(intersection);
    return intersection;
  }

  /**
   * Add a segment as an approach of an intersection, for a signal. The signal's OSM node id is added to the
   * intersection's external ids, comma separated, each signal once. A newly added approach is counted. When no vehicle
   * a signal controls can use it, that is recorded as an issue. The signal is also recorded for the approach.
   *
   * @param intersection to add the approach to
   * @param approach segment to add
   * @param signal the OSM node of the signal
   */
  private void addApproach(Intersection intersection, MacroscopicLinkSegment approach, OsmNode signal) {
    var signalId = String.valueOf(signal.getId());
    if (!intersection.containsExternalId(signalId)) {
      intersection.appendExternalId(signalId);
    }
    if (intersection.addApproachSegment(approach)) {
      var planitEntityDiagnostics = networkData.getPlanitEntityDiagnostics();
      planitEntityDiagnostics.registerApproachCreated();
      if (!approach.isAnyModeAllowed(OsmTrafficSignalIntegrator::isControlledVehicleMode)) {
        planitEntityDiagnostics.registerIssue(OsmPlanitEntityIssue.APPROACH_WITHOUT_CONTROLLED_TRAFFIC,
            approach.getParent().getExternalId(), "intersection " + intersection.getExternalId());
      }
    }
    var signals = signalsByApproach.computeIfAbsent(approach, segment -> new ArrayList<>(1));
    if (!signals.contains(signal)) {
      signals.add(signal);
    }
  }

  /**
   * Make the segment arriving at a node reached from a signal an approach of that node's intersection. The intersection
   * is created, with the given kind, when there is none yet.
   *
   * @param reached the node reached from the signal, and the segment arriving there
   * @param signal the OSM node of the signal
   * @param type kind of intersection the signal makes
   */
  private void addArrivingSegmentAsApproach(JunctionAhead reached, OsmNode signal, IntersectionType type) {
    var node = reached.getJunction();
    addApproach(getOrCreateIntersection(Long.parseLong(node.getExternalId()), node, type), reached.arriving, signal);
  }

  /**
   * Register an intersection on the layer. It is remembered as registered by the integrator, and counted as created.
   *
   * @param intersection to register
   */
  private void register(Intersection intersection) {
    networkLayer.getIntersections().getFactory().register(intersection);
    registered.add(intersection);
    networkData.getPlanitEntityDiagnostics().registerIntersectionCreated();
  }

  /**
   * Track an outcome of a signal as a diagnostic of the parse, recorded with the signal's OSM node id
   *
   * @param outcome of the signal
   * @param signal the OSM node of the signal
   */
  private void trackAsDiagnostic(OsmTrafficSignalOutcome outcome, OsmNode signal) {
    networkData.getParseDiagnostics().register(outcome, signal);
  }

  /**
   * Constructor
   *
   * @param networkLayer to add intersections to
   * @param layerData of the layer
   * @param networkData of the network
   * @param settings to use
   * @param geoUtils to measure distances with
   */
  public OsmTrafficSignalIntegrator(MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData,
      OsmNetworkReaderData networkData, OsmNetworkReaderSettings settings, PlanitJtsCrsUtils geoUtils) {
    this.networkLayer = networkLayer;
    this.layerData = layerData;
    this.networkData = networkData;
    this.settings = settings;
    this.geoUtils = geoUtils;
  }

  /**
   * Remember a link as part of a lowest order road when the tags of its OSM way say so, see
   * {@link #LOWEST_ORDER_ROAD_TYPES}. It is kept by OSM way id, so the links the way is broken into later are covered
   * too.
   *
   * @param link created
   * @param tags of the OSM way it is created from
   */
  public void registerIfLowestOrderRoad(MacroscopicLink link, Map<String, String> tags) {
    var highwayValue = tags.get(OsmHighwayTags.HIGHWAY);
    if (highwayValue != null && LOWEST_ORDER_ROAD_TYPES.contains(highwayValue)) {
      lowestOrderRoadOsmWayIds.add(link.getExternalId());
    }
  }

  /**
   * Turn the recorded traffic signals into intersections of the layer, creating and registering them. Each signal is
   * handled by the first case that applies, cheapest check first. There are three passes, because later cases depend on
   * what earlier ones create: {@link #discardPostponeOrConstructIntersections()},
   * {@link #assignVehicleSignalsToJunctions(FirstPassResult)} and
   * {@link #integrateCrossingsAndUnassignedVehicleSignals(FirstPassResult, SecondPassResult)}.
   */
  public void execute() {
    boundingArea = OsmProjectedBoundingAreaHelper.ofNullable(networkData.getBoundingArea(), settings.getSourceCRS(),
        settings.getCountryName(), settings.getMaximumDistanceFerryOutsideBoundingPolygonInMeters());

    // pass one
    var firstPassResult = discardPostponeOrConstructIntersections();
    // pass two
    var secondPassResult = assignVehicleSignalsToJunctions(firstPassResult);
    // pass three
    integrateCrossingsAndUnassignedVehicleSignals(firstPassResult, secondPassResult);

    // diagnostics
    recordIntersectionsWithoutControlledApproach();
    lowestOrderRoadOsmWayIds.clear();
    intersectionsByOsmNodeId.clear();
  }

  /**
   * Get, per approach, the OSM nodes of the signals that made it an approach, in the order handled
   *
   * @return signals by approach, unmodifiable
   */
  public Map<MacroscopicLinkSegment, List<OsmNode>> getSignalsByApproach() {
    return Collections.unmodifiableMap(signalsByApproach);
  }
}

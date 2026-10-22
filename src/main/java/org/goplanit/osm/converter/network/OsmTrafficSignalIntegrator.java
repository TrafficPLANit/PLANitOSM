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
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.osm.util.PlanitNetworkLayerModifierUtils;
import org.goplanit.osm.util.PlanitOsmNetworkLayerUtils;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.event.EventType;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.modifier.event.DirectedGraphModificationEvent;
import org.goplanit.utils.graph.modifier.event.DirectedGraphModifierListener;
import org.goplanit.utils.graph.modifier.event.GraphModificationEvent;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkUtils;
import org.goplanit.utils.network.layer.macroscopic.intersection.Intersection;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionControlType;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;

import org.locationtech.jts.geom.Coordinate;

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

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmTrafficSignalIntegrator.class.getCanonicalName());

  /** minimum number of links meeting at a junction node */
  private static final int MIN_LINKS_AT_JUNCTION = 3;

  /** A vehicle signal without a usable direction normally takes the nearest junction. It takes a junction where a road
   * of higher capacity crosses instead, when that is at most this much farther, in metres */
  private static final double NEAR_TIE_DELTA_DISTANCE_M = 10;

  /** the lowest order roads: service lanes, alleys among them, and living streets. A junction where only these join a
   * road of higher capacity does not claim a signalised crossing next to it */
  private static final Set<String> LOWEST_ORDER_ROAD_TYPES =
      Set.of(OsmHighwayTags.SERVICE, OsmHighwayTags.LIVING_STREET);

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

  /** the parsed area: the bounding area, or else the bounds the input file declares. Empty when there is neither, set
   * when executing */
  private OsmProjectedBoundingAreaHelper parsedArea;

  /** OSM way ids of the lowest order roads parsed. The layer parser hands them over while it creates the links. Cleared
   * after executing */
  private final Set<String> lowestOrderRoadOsmWayIds = new HashSet<>();

  /** looking ahead along the road from a vehicle signal. It travels segments motorised road traffic can use, looks for
   * junctions for vehicle signals, see {@link #isJunctionForVehicleSignals(Node)}, and carries on past them along the
   * same OSM way or a way of the same name */
  private final LookAhead lookAheadForJunctions = new LookAhead(
      MacroscopicLinkSegmentUtils.permitsMotorisedRoadMode(), this::isJunctionForVehicleSignals, true);

  /** looking ahead along the road from a vehicle signal to the first signalised crossing */
  private final LookAhead lookAheadForSignalisedCrossings = new LookAhead(
      MacroscopicLinkSegmentUtils.permitsMotorisedRoadMode(), this::isSignalisedCrossingNode, false);

  /** intersections created but not yet registered on the layer, in the order created */
  private final List<Intersection> created = new ArrayList<>();

  /** intersections created, by the OSM node id of their signal or junction node. Only kept while executing: later
   * changes to the layer, such as pruning, could leave it pointing at removed intersections */
  private final Map<Long, Intersection> intersectionsByOsmNodeId = new HashMap<>();

  /** per approach segment, the OSM nodes of the signals that made it an approach, in the order handled. Keyed by
   * identity, because segment ids are recreated after parsing */
  private final Map<MacroscopicLinkSegment, List<OsmNode>> signalsByApproach = new IdentityHashMap<>();

  /** per OSM way id, where the vehicle signals made into an approach stand on its links, see
   * {@link #isVehicleSignalStandingOn(MacroscopicLinkSegment)} */
  private final Map<String, List<VehicleSignalPosition>> vehicleSignalPositionsByOsmWayId = new HashMap<>();

  /** the nodes with a signal where no road or rail passes, such as a crossing of a footway and a cycleway. Their
   * intersection is removed, since it has no approach: an approach is for traffic a signal controls, see
   * {@link #removeIntersectionsWithoutApproach()}. The nodes are tracked so their light is not lost. When consolidation
   * later joins such a node into a larger intersection, for example a footway node between two crossings, the segments
   * entering it become internal segments with a signal rather than plain ones, see
   * {@link #hasSignalOfItsOwn(MacroscopicLinkSegment)}. Kept by identity, because node ids are recreated after
   * parsing */
  private final Set<Node> nodesWithSignalWhereNoRoadOrRailPasses = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * How to look ahead from a signal along its way. It says which segments may be travelled, which nodes are targets,
   * and whether to carry on past a target to find more. Carrying on only follows the same OSM way, or a way of the same
   * name.
   */
  private static final class LookAhead {

    /** the segments it may travel, both the ones it starts on and the ones it continues on */
    private final Predicate<? super MacroscopicLinkSegment> travels;

    /** the nodes looked for */
    private final Predicate<Node> isTargetNode;

    /** true to carry on past a target, along the same OSM way or a way of the same name, false to stop at the first
     * target */
    private final boolean beyondTarget;

    /**
     * Constructor
     *
     * @param travels the segments it may travel, both the ones it starts on and the ones it continues on
     * @param isTargetNode the nodes looked for
     * @param beyondTarget true to carry on past a target, along the same OSM way or a way of the same name, false to
     *                     stop at the first target
     */
    private LookAhead(
        Predicate<? super MacroscopicLinkSegment> travels, Predicate<Node> isTargetNode, boolean beyondTarget) {
      this.travels = travels;
      this.isTargetNode = isTargetNode;
      this.beyondTarget = beyondTarget;
    }
  }

  /** A link segment arriving at a node a look-ahead looks for, its target, such as a junction node or the node of a
   * signalised crossing; from a track or cycleway also a road node or a branch node. It holds the distance from the
   * signal to that node along the road, and the segment passing the signal where the look-ahead started */
  private static final class SegmentArrivingAtTarget {

    /** the segment arriving at the node */
    private final MacroscopicLinkSegment arriving;

    /** from the signal to the node, along the way, in metres */
    private final double distance;

    /** the segment carrying traffic past the signal, where the look-ahead started */
    private final MacroscopicLinkSegment passing;

    /**
     * Constructor
     *
     * @param arriving the segment arriving at the node
     * @param distance from the signal to the node, along the way, in metres
     * @param passing the segment carrying traffic past the signal, where the look-ahead started
     */
    private SegmentArrivingAtTarget(MacroscopicLinkSegment arriving, double distance, MacroscopicLinkSegment passing) {
      this.arriving = arriving;
      this.distance = distance;
      this.passing = passing;
    }

    /**
     * Collect the node reached
     *
     * @return node reached
     */
    private Node getTargetNode() {
      return arriving.getDownstreamNode();
    }
  }

  /** Where a vehicle signal stands: its position, and the direction of the segment carrying traffic past it, relative
   * to its link. Pieces of a link broken later keep that direction, so it still holds for them */
  private static final class VehicleSignalPosition {

    /** position of the signal, as in the geometry of the link it lies inside, or of the node it is on */
    private final Coordinate position;

    /** true when the traffic passing the signal runs from node A to node B of its link */
    private final boolean directionAb;

    /**
     * Constructor
     *
     * @param position of the signal
     * @param directionAb true when the traffic passing the signal runs from node A to node B of its link
     */
    private VehicleSignalPosition(Coordinate position, boolean directionAb) {
      this.position = position;
      this.directionAb = directionAb;
    }
  }

  /** A vehicle signal on a road but not on a junction node, with the junctions it reaches in each direction it
   * applies to */
  private static final class PendingVehicleSignal {

    /** the OSM node of the signal */
    private final OsmNode signal;

    /** per direction the signal applies to, the junctions reached along the road, nearest first. No list is empty */
    private final List<List<SegmentArrivingAtTarget>> junctionsPerDirection;

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
    private PendingVehicleSignal(OsmNode signal, List<List<SegmentArrivingAtTarget>> junctionsPerDirection,
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
   * {@link #hasOwnSignals(SegmentArrivingAtTarget)}.
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
    private boolean hasOwnSignals(SegmentArrivingAtTarget junctionAhead) {
      var junction = junctionAhead.getTargetNode();
      if (junctionNodesWithVehicleSignal.contains(junction)) {
        return true;
      }
      return firstArrivals.getOrDefault(junction, List.of()).stream().anyMatch(
          arriving -> !PlanitLinkOsmUtils.isSameOsmWayOrName(arriving.getParent(), junctionAhead.arriving.getParent()));
    }
  }

  /** An isolated signalised crossing as seen from a junction it reaches: how far before that junction it lies, and
   * whether it is ignored for lying at the edge of the parsed area */
  private static final class IsolatedCrossingBeforeJunction {

    /** from the crossing to the junction, along the road, in metres */
    private final double distanceToJunction;

    /** true when the crossing lies at the edge of the parsed area, so it is ignored, false otherwise */
    private final boolean atEdgeOfParsedArea;

    /**
     * Constructor
     *
     * @param distanceToJunction from the crossing to the junction, along the road, in metres
     * @param atEdgeOfParsedArea true when the crossing lies at the edge of the parsed area, so it is ignored
     */
    private IsolatedCrossingBeforeJunction(double distanceToJunction, boolean atEdgeOfParsedArea) {
      this.distanceToJunction = distanceToJunction;
      this.atEdgeOfParsedArea = atEdgeOfParsedArea;
    }
  }

  /** The first target of a vehicle signal in one direction: the junction it would be assigned to, or an isolated
   * signalised crossing before it */
  private static final class FirstTargetInDirection {

    /** the junctions reached in the direction, nearest first */
    private final List<SegmentArrivingAtTarget> junctions;

    /** the junction this direction resolves to, see {@link #resolveJunction(List, VehicleSignalEvidence)} */
    private final SegmentArrivingAtTarget junction;

    /** distance from the signal to the target, in metres */
    private final double distance;

    /** true when the target is an isolated crossing before the junction, false when it is the junction itself */
    private final boolean isCrossing;

    /** true when the target is an isolated crossing ignored for lying at the edge of the parsed area, false otherwise */
    private final boolean isCrossingAtEdgeOfParsedArea;

    /**
     * Constructor
     *
     * @param junctions reached in the direction, nearest first
     * @param junction the junction the direction resolves to
     * @param distance from the signal to the target, in metres
     * @param isCrossing true when the target is an isolated crossing before the junction
     * @param isCrossingAtEdgeOfParsedArea true when the target is an isolated crossing ignored for lying at the edge of
     *                                     the parsed area
     */
    private FirstTargetInDirection(List<SegmentArrivingAtTarget> junctions, SegmentArrivingAtTarget junction,
        double distance, boolean isCrossing, boolean isCrossingAtEdgeOfParsedArea) {
      this.junctions = junctions;
      this.junction = junction;
      this.distance = distance;
      this.isCrossing = isCrossing;
      this.isCrossingAtEdgeOfParsedArea = isCrossingAtEdgeOfParsedArea;
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
      return isOnNode() ? new ArrayList<>(node.<MacroscopicLink>getLinks()) : links;
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
   * stop lines of isolated crossings, and the vehicle signals without a junction ahead. Also the crossings isolated
   * between their own stop lines. See {@link #assignVehicleSignalsToJunctions(FirstPassResult)} */
  private static final class SecondPassResult {

    /** vehicle signals at the stop line of an isolated signalised crossing */
    private final List<OsmNode> isolatedCrossingStopLines = new ArrayList<>();

    /** vehicle signals without a junction ahead */
    private final List<OsmNode> vehicleSignalsWithoutJunction = new ArrayList<>();

    /** crossing signals isolated between their own stop lines, see
     * {@link #isFacedFromBeyond(OsmNode, SegmentArrivingAtTarget, Map)}. Such a crossing is never part of a signalised
     * junction nearby. Kept by identity */
    private final Set<OsmNode> crossingsBetweenTheirStopLines = Collections.newSetFromMap(new IdentityHashMap<>());

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
    return isBranchNode(node, MacroscopicLinkSegmentUtils.permitsMotorisedRoadMode());
  }

  /**
   * Verify if a way branches at a node. Three or more links with a segment the look-ahead may travel meet there. So the
   * way goes on along more than one link, or another way joins it there.
   *
   * @param node to check
   * @param travels the segments the look-ahead may travel
   * @return true when the way branches there, false otherwise
   */
  private static boolean isBranchNode(Node node, Predicate<? super MacroscopicLinkSegment> travels) {
    return node.getNumberOfEdges(edge -> MacroscopicLinkUtils.hasSegmentMatching((MacroscopicLink) edge, travels)) >=
        MIN_LINKS_AT_JUNCTION;
  }

  /**
   * The links of the other roads at a junction reached. Motorised road traffic can use them in at least one direction.
   * They are not on the OSM way arrived along, nor on a way with the same name, see
   * {@link PlanitLinkOsmUtils#isSameOsmWayOrName(Link, Link)}.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @return links of the other roads, empty when none
   */
  private static List<MacroscopicLink> crossingRoadLinks(SegmentArrivingAtTarget junctionAhead) {
    var arrivingLink = junctionAhead.arriving.getParent();
    var permitsMotorisedRoadMode = MacroscopicLinkUtils.permitsMotorisedRoadModeOnAnySegment();
    return junctionAhead.getTargetNode().<MacroscopicLink>getLinks().stream()
        .filter(link -> link != arrivingLink && permitsMotorisedRoadMode.test(link) &&
            !PlanitLinkOsmUtils.isSameOsmWayOrName(link, arrivingLink))
        .collect(Collectors.toList());
  }

  /**
   * Capacity of the road crossing at a junction reached from a signal. It is the highest capacity of any segment of the
   * other roads there that motorised road traffic can use, in either direction, see
   * {@link #crossingRoadLinks(SegmentArrivingAtTarget)}. A segment's capacity is its capacity per lane times its lanes.
   *
   * @param junctionAhead reached from the signal
   * @return capacity in pcu/h, 0 when no other road meets it
   */
  private static double crossingCapacity(SegmentArrivingAtTarget junctionAhead) {
    double capacity = 0;
    var permitsMotorisedRoadMode = MacroscopicLinkSegmentUtils.permitsMotorisedRoadMode();
    for (var link : crossingRoadLinks(junctionAhead)) {
      for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
        if (segment != null && permitsMotorisedRoadMode.test(segment)) {
          capacity = Math.max(capacity, segment.getCapacityOrDefaultPcuH());
        }
      }
    }
    return capacity;
  }

  /**
   * Distance from a signal to the nearest isolated signalised crossing meeting a condition before the junction a
   * direction resolves to. A crossing counts when it lies on the segment arriving at that junction, or at a junction
   * passed on the way, and is closer to that junction than the signal is.
   *
   * @param junctions reached in the direction, nearest first
   * @param resolved the junction the direction resolves to, one of them
   * @param isolatedCrossings isolated crossings before the junctions they reach, per segment arriving there
   * @param condition a crossing must meet to count
   * @return distance in metres, infinite when no such crossing lies before the junction
   */
  private static double isolatedCrossingDistance(List<SegmentArrivingAtTarget> junctions,
      SegmentArrivingAtTarget resolved,
      Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> isolatedCrossings,
      Predicate<IsolatedCrossingBeforeJunction> condition) {
    double crossingDistance = Double.POSITIVE_INFINITY;
    for (var passed : junctions) {
      for (var crossing : isolatedCrossings.getOrDefault(passed.arriving, List.of())) {
        if (condition.test(crossing) &&
            Precision.smaller(crossing.distanceToJunction, passed.distance, Precision.EPSILON_6)) {
          crossingDistance = Math.min(crossingDistance, passed.distance - crossing.distanceToJunction);
        }
      }
      if (passed == resolved) {
        break;
      }
    }
    return crossingDistance;
  }

  /**
   * Per segment arriving at the first junction vehicle signals reach in a direction, the distance to that junction
   * from the farthest of them
   *
   * @param vehicleSignals on a road but not on a junction node, with the junctions they reach
   * @return distance in metres, per segment arriving at a first junction
   */
  private static Map<MacroscopicLinkSegment, Double> findFarthestDistanceToFirstJunction(
      List<PendingVehicleSignal> vehicleSignals) {
    var farthest = new IdentityHashMap<MacroscopicLinkSegment, Double>();
    for (var pendingSignal : vehicleSignals) {
      for (var junctions : pendingSignal.junctionsPerDirection) {
        farthest.merge(junctions.get(0).arriving, junctions.get(0).distance, Math::max);
      }
    }
    return farthest;
  }

  /**
   * The segments carrying traffic past a signal that a look-ahead may travel, in each direction the signal applies to.
   * From a signal on a node, these are the segments starting at the node: they pass the signal and lie wholly ahead of
   * it; a segment ending at the node is behind it. From a signal inside links, these are the segments of those links,
   * since each passes it. Each link is a piece of an OSM way, and its A to B segment is the way's forward direction.
   *
   * @param location of the signal
   * @param direction the signal applies to, relative to the OSM way of each link, null for both, see
   *                  {@link PlanitLinkOsmUtils#isInOsmDirection(MacroscopicLinkSegment, String)}
   * @param travels the segments a look-ahead may travel
   * @return the segments passing the signal
   */
  private static List<MacroscopicLinkSegment> findSegmentsPassing(SignalLocation location, String direction,
      Predicate<? super MacroscopicLinkSegment> travels) {
    var passing = new ArrayList<MacroscopicLinkSegment>(2);
    for (var link : location.getLinks()) {
      for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
        if (segment != null && travels.test(segment) && PlanitLinkOsmUtils.isInOsmDirection(segment, direction) &&
            (!location.isOnNode() || segment.getUpstreamVertex() == location.node)) {
          passing.add(segment);
        }
      }
    }
    return passing;
  }

  /**
   * Record where a vehicle signal stands: on the segment carrying traffic past it, which lies wholly ahead of a signal
   * on a node
   *
   * @param signal the OSM node of the signal
   * @param passing the segment carrying traffic past the signal
   * @param positionsByOsmWayId to record it in, per OSM way id
   */
  private static void recordWhereVehicleSignalStands(OsmNode signal, MacroscopicLinkSegment passing,
      Map<String, List<VehicleSignalPosition>> positionsByOsmWayId) {
    positionsByOsmWayId.computeIfAbsent(passing.getParent().getExternalId(), id -> new ArrayList<>(1))
        .add(new VehicleSignalPosition(OsmNodeUtils.createCoordinate(signal), passing.isDirectionAb()));
  }

  /**
   * Find where the recorded vehicle signals standing on a link segment are: they lie inside the segment's link, or on
   * the node the segment starts at, and apply to the segment's direction. It holds for the pieces of links broken after
   * they were recorded too
   *
   * @param positionsByOsmWayId where the vehicle signals stand, per OSM way id
   * @param segment to check
   * @return the positions of the signals standing on it
   */
  private static Stream<Coordinate> findWhereVehicleSignalsStandOn(
      Map<String, List<VehicleSignalPosition>> positionsByOsmWayId, MacroscopicLinkSegment segment) {
    var positions = positionsByOsmWayId.get(segment.getParent().getExternalId());
    if (positions == null) {
      return Stream.empty();
    }
    var coordinates = segment.getParent().getGeometry().getCoordinates();
    var upstream = segment.getUpstreamNode().getPosition().getCoordinate();
    return positions.stream().filter(signal -> signal.directionAb == segment.isDirectionAb())
        .map(signal -> signal.position).filter(position -> position.equals2D(upstream) ||
            IntStream.range(1, coordinates.length - 1).anyMatch(index -> position.equals2D(coordinates[index])));
  }

  /**
   * First pass. Each recorded signal is discarded, postponed to a later pass, or its intersection is constructed, see
   * {@link #discardPostponeOrConstructIntersection(long, FirstPassResult)}. Then the same for each crossing signal
   * tagged only on the crossing way through its node, see
   * {@link #integrateCrossingSignalTaggedOnlyOnCrossingWay(long, FirstPassResult)}.
   *
   * @return the postponed signals and the junction nodes with a vehicle signal on them, for the passes after it
   */
  private FirstPassResult discardPostponeOrConstructIntersections() {
    var firstPass = new FirstPassResult();
    for (long osmNodeId : networkData.getTrafficSignalOsmNodeIds()) {
      discardPostponeOrConstructIntersection(osmNodeId, firstPass);
    }
    for (long osmNodeId : networkData.getOsmNodeIdsOnSignalisedCrossingWayOnly()) {
      integrateCrossingSignalTaggedOnlyOnCrossingWay(osmNodeId, firstPass);
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
    var direction = OsmTrafficSignalTags.getRecognisedDirection(tags);
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
      if (location.isOnNode() && isJunctionForVehicleSignals(location.node)) {
        // attempt case (i)
        integrateSignalOnNode(osmNode, location.node, IntersectionType.JUNCTION);
        firstPass.junctionNodesWithVehicleSignal.add(location.node);
        trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_JUNCTION_NODE, osmNode);
        if (isRoadEndOutsideParsedArea(location.node)) {
          trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA, osmNode);
        }
      } else if (location.getLinks().stream().anyMatch(MacroscopicLinkUtils.permitsMotorisedRoadModeOnAnySegment())) {
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
      constructOrPostponeCrossingSignal(osmNode, location, firstPass);
    }
  }

  /**
   * Construct the intersection of a crossing signal on a junction node, case (iii), or postpone a crossing signal not
   * on a junction node to the third pass, case (v), see {@link #discardPostponeOrConstructIntersection(long,
   * FirstPassResult)}
   *
   * @param osmNode of the crossing signal
   * @param location of the crossing signal
   * @param firstPass to collect the postponed crossing signals in
   */
  private void constructOrPostponeCrossingSignal(OsmNode osmNode, SignalLocation location, FirstPassResult firstPass) {
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

  /**
   * Handle a crossing signal tagged only on the crossing way, a footway, cycleway or path, as if it were tagged on the
   * node where the crossing way crosses a road, see {@link #constructOrPostponeCrossingSignal(OsmNode, SignalLocation,
   * FirstPassResult)}. That node is recorded as a signal from here on. Any other node of the crossing way, e.g. where
   * it meets the kerb, is not on a road and is no signal, so it is passed over.
   * <p>
   * The crossing way is trusted over the node. Where the node's own tags say the crossing has no signals, e.g.
   * crossing=zebra, the crossing is still taken as signalised. The inconsistency is noted and logged with the ids of
   * the node and the way, so it can be checked in OSM.
   * </p>
   *
   * @param osmNodeId of a node on a signalised crossing way without crossing signal tags of its own
   * @param firstPass to collect the postponed crossing signals in
   */
  private void integrateCrossingSignalTaggedOnlyOnCrossingWay(long osmNodeId, FirstPassResult firstPass) {
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
    var location = locate(osmNode);
    if (location == null ||
        location.getLinks().stream().noneMatch(MacroscopicLinkUtils.permitsMotorisedRoadModeOnAnySegment())) {
      return;
    }

    networkData.registerTrafficSignalOsmNodeId(osmNodeId);
    trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ONLY_ON_CROSSING_WAY, osmNode);
    var tags = OsmModelUtil.getTagsAsMap(osmNode);
    if (OsmTrafficSignalTags.isCrossingWithoutSignals(tags)) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_SIGNAL_TAGS_INCONSISTENT, osmNode);
      var keySayingNoSignals = OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(
          tags, OsmTrafficSignalTags.CROSSING_SIGNALS, OsmTags.NO) ?
          OsmTrafficSignalTags.CROSSING_SIGNALS : OsmTrafficSignalTags.CROSSING;
      LOGGER.warning(String.format(
          "Crossing way %d is tagged with traffic signals, but node %d where it crosses the road says %s=%s; " +
              "taken as signalised, check in OSM",
          networkData.getSignalisedCrossingWayId(osmNodeId), osmNodeId, keySayingNoSignals,
          tags.get(keySayingNoSignals)));
    }
    constructOrPostponeCrossingSignal(osmNode, location, firstPass);
  }

  /**
   * Collect a vehicle signal on a road but not on a junction node, with the junctions it reaches along the road, see
   * {@link #findSegmentsArrivingAtTargetsAlongRoad(SignalLocation, String, double, LookAhead)}. A special case is a
   * node where two OSM ways end and the road simply continues. If those ways disagree on the tagged direction, the
   * conflict is counted. The signal then applies to one direction without knowing which, as if untagged.
   *
   * @param location of the signal
   * @param direction the signal applies to, null when not tagged
   * @return the signal with the junctions it reaches, not yet assigned
   */
  private PendingVehicleSignal collectVehicleSignal(SignalLocation location, String direction) {
    var maxDistance = settings.getMaximumDistanceVehicleSignalToJunctionInMeters();
    if (OsmDirectionTags.isForwardOrBackward(direction) && location.isOnNode() &&
        PlanitLinkOsmUtils.waysDisagreeOnOrientation(location.node, location.getLinks().stream()
            .filter(MacroscopicLinkUtils.permitsMotorisedRoadModeOnAnySegment()).collect(Collectors.toList()))) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.DIRECTION_CONFLICT, location.signal);
      return new PendingVehicleSignal(location.signal,
          findSegmentsArrivingAtTargetsAlongRoad(location, null, maxDistance, lookAheadForJunctions), true);
    }
    return new PendingVehicleSignal(location.signal,
        findSegmentsArrivingAtTargetsAlongRoad(location, direction, maxDistance, lookAheadForJunctions),
        direction == null);
  }

  /**
   * Make a node an intersection, for a signal on it or reaching it. Every segment entering the node that a vehicle the
   * signal controls can use becomes an approach, see {@link #addApproach(Intersection, MacroscopicLinkSegment, OsmNode)}.
   *
   * @param signal the OSM node of the signal
   * @param node the signal is on or reaches
   * @param type kind of intersection the signal makes
   */
  private void integrateSignalOnNode(OsmNode signal, Node node, IntersectionType type) {
    addEnteringSegmentsAsApproaches(
        getOrCreateIntersection(PlanitOsmUtils.parseOsmId(node), node, type), node, signal);
  }

  /**
   * Add the segments entering a node as approaches of its intersection, for a signal, see
   * {@link #addApproach(Intersection, MacroscopicLinkSegment, OsmNode)}
   *
   * @param intersection to add the approaches to
   * @param node whose entering segments to add
   * @param signal the OSM node of the signal
   */
  private void addEnteringSegmentsAsApproaches(Intersection intersection, Node node, OsmNode signal) {
    for (MacroscopicLinkSegment entering : node.<MacroscopicLinkSegment>getEntryLinkSegments()) {
      addApproach(intersection, entering, signal);
    }
  }

  /**
   * Handle a vehicle signal with no road at its location: on a tram or light rail track, or on a cycleway. Its way is
   * followed to the nearest node where a road meets it. On a cycleway, only segments that allow bicycles are followed.
   * From a track, within the railway settings' maximum distance, that node becomes a signalised junction: car traffic
   * is held there for the tram. From a cycleway, within the crossing distance, it becomes a signalised crossing. Every
   * segment entering it that a vehicle the signal controls can use is an approach. If the way branches before the
   * road, and more than one branch reaches a road, the branch node becomes the intersection instead, see
   * {@link #findRoadCrossingOrBranch(SegmentArrivingAtTarget, double, LookAhead)}. Its only approach is then the
   * segment arriving from the signal on a track; on a cycleway it has none. No road node is joined. The signal is
   * discarded when no road is in reach, or when it is on
   * another kind of way, such as a footway. The outcome is recorded either way, and no link is broken.
   *
   * @param location of the signal
   */
  private void integrateSignalOnRailOrActiveModeWay(SignalLocation location) {
    var signal = location.signal;
    var links = location.getLinks();
    Predicate<Mode> isBicycleMode = mode -> mode.isPredefinedModeType(PredefinedModeType.BICYCLE);
    boolean onTrack = links.stream().anyMatch(link -> link.isAnyModeAllowedOnAnySegment(Mode::isRailMode));
    if (!onTrack && links.stream().noneMatch(link -> link.isAnyModeAllowedOnAnySegment(isBicycleMode))) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.TRAFFIC_SIGNAL_NOT_ON_ROAD_TRACK_OR_CYCLEWAY, signal);
      return;
    }
    Predicate<MacroscopicLinkSegment> travels = onTrack ?
        segment -> segment.isAnyModeAllowed(Mode::isRailMode) :
        segment -> segment.isAnyModeAllowed(isBicycleMode);
    var lookAheadForRoadOrBranch = new LookAhead(travels,
        node -> MacroscopicLinkUtils.isUsedByModeMatching(node, Mode::isMotorisedRoadMode) ||
            isBranchNode(node, travels),
        false);
    double maxDistance = onTrack ?
        settings.getRailwaySettings().getMaximumDistanceTrackSignalToRoadCrossingInMeters() :
        settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var reached = findSegmentsArrivingAtTargetsAlongRoad(location, null, maxDistance, lookAheadForRoadOrBranch);
    var nearest = reached.stream()
        .map(targets -> findRoadCrossingOrBranch(targets.get(0), maxDistance, lookAheadForRoadOrBranch))
        .filter(Objects::nonNull).min(Comparator.comparingDouble(target -> target.distance));
    if (nearest.isEmpty()) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING, signal);
      return;
    }
    var type = onTrack ? IntersectionType.JUNCTION : IntersectionType.CROSSING;
    if (!MacroscopicLinkUtils.isUsedByModeMatching(nearest.get().getTargetNode(), Mode::isMotorisedRoadMode)) {
      addArrivingSegmentAsApproach(nearest.get(), signal, type);
      trackAsDiagnostic(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH, signal);
      return;
    }
    integrateSignalOnNode(signal, nearest.get().getTargetNode(), type);
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
  private SegmentArrivingAtTarget findRoadCrossingOrBranch(SegmentArrivingAtTarget reached, double maxDistance,
      LookAhead lookAhead) {
    var node = reached.getTargetNode();
    if (MacroscopicLinkUtils.isUsedByModeMatching(node, Mode::isMotorisedRoadMode)) {
      return reached;
    }
    SegmentArrivingAtTarget alongOnlyBranch = null;
    for (var leaving : MacroscopicLinkSegmentUtils.findContinuations(reached.arriving, lookAhead.travels)) {
      var targets = findSegmentsArrivingAtTargetsAhead(
          leaving, reached.distance + leaving.getParent().getLengthMeters(), maxDistance, lookAhead);
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
   * distance beyond it, or when a vehicle signal past it faces back towards it, see
   * {@link #findIsolatedCrossingsByJunctionArrival(FirstPassResult, VehicleSignalEvidence, SecondPassResult)}.</li>
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
   * {@link #resolveJunction(List, VehicleSignalEvidence)}. A signal that reaches no junction goes to the third
   * pass.</li>
   * <li>Register the intersections created so far.</li>
   * </ol>
   * Steps 1 to 3 still count every signal as evidence, stop lines of isolated crossings included. That is because which
   * signals are such stop lines is only known once those steps are done.
   *
   * @param firstPass holds the vehicle signals not on a junction node, the junction nodes with a vehicle signal on
   *                  them, and the crossing signals, from the first pass
   * @return the stop lines of isolated crossings, the vehicle signals without a junction ahead, and the crossings
   *         isolated between their own stop lines, for the third pass
   */
  private SecondPassResult assignVehicleSignalsToJunctions(FirstPassResult firstPass) {
    var secondPass = new SecondPassResult();

    /* step 1: find the isolated crossings. The evidence still includes the signals that may turn out to be stop
     * lines */
    var evidenceWithIsolatedCrossingStopLines = new VehicleSignalEvidence(
        firstPass.junctionNodesWithVehicleSignal, firstPass.vehicleSignalsNotOnJunctionNode);
    var isolatedCrossings =
        findIsolatedCrossingsByJunctionArrival(firstPass, evidenceWithIsolatedCrossingStopLines, secondPass);

    /* steps 2 and 3: keep one direction when it is still unknown, then drop the directions past an isolated
     * crossing. A signal with no direction left is that crossing's stop line. It is deferred to the third pass,
     * which adds it as an approach of the crossing's intersection, when there is one */
    var vehicleSignalsExceptIsolatedCrossingStopLines =
        new ArrayList<PendingVehicleSignal>(firstPass.vehicleSignalsNotOnJunctionNode.size());
    for (var pendingSignal : firstPass.vehicleSignalsNotOnJunctionNode) {
      var firstTargets = keepNearestDirectionWhenUnknown(pendingSignal,
          findFirstTargets(pendingSignal, isolatedCrossings, evidenceWithIsolatedCrossingStopLines));
      var directionsToJunctions = firstTargets.stream().filter(target -> !target.isCrossing)
          .map(target -> target.junctions).collect(Collectors.toList());
      if (!firstTargets.isEmpty() && directionsToJunctions.isEmpty()) {
        if (firstTargets.stream().allMatch(target -> target.isCrossingAtEdgeOfParsedArea)) {
          /* the stop line of crossings the third pass ignores for lying at the edge of the parsed area: ignored with
           * them, rather than assigned past them to a junction */
          trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA, pendingSignal.signal);
        } else {
          /* every direction left leads to an isolated crossing first: the signal is the stop line of that crossing.
           * The third pass joins it there */
          trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING, pendingSignal.signal);
          secondPass.isolatedCrossingStopLines.add(pendingSignal.signal);
        }
      } else {
        boolean stillOneDirectionUnknown =
            pendingSignal.oneDirectionUnknown && pendingSignal.junctionsPerDirection.size() < 2;
        vehicleSignalsExceptIsolatedCrossingStopLines.add(
            new PendingVehicleSignal(pendingSignal.signal, directionsToJunctions, stillOneDirectionUnknown));
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
      } else {
        /* no junction ahead: the third pass handles it */
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
   * not a lowest order road. Every other road there is one, see {@link #crossingRoadLinks(SegmentArrivingAtTarget)}.
   * And their capacity is below that of the segment arriving.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @return true when only lowest order roads of lower capacity join there, false otherwise
   */
  private boolean isJoinedOnlyByLowestOrderRoads(SegmentArrivingAtTarget junctionAhead) {
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
  private boolean countsAsJunction(SegmentArrivingAtTarget junctionAhead, VehicleSignalEvidence evidence) {
    return evidence.hasOwnSignals(junctionAhead) || !isJoinedOnlyByLowestOrderRoads(junctionAhead);
  }

  /**
   * The junction a vehicle signal is assigned to in one direction: the first junction reached that counts, see
   * {@link #countsAsJunction(SegmentArrivingAtTarget, VehicleSignalEvidence)}. So a junction where only service lanes
   * or living streets without signals join is passed over, when one that counts follows within reach. A junction that
   * counts is never passed over, even when a road of higher capacity crosses further along. If none counts, the first
   * junction reached is used.
   *
   * @param junctions reached in the direction, nearest first
   * @param evidence of the junctions' own signals
   * @return the junction assigned to
   */
  private SegmentArrivingAtTarget resolveJunction(List<SegmentArrivingAtTarget> junctions,
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

  /**
   * Find the isolated signalised crossings. A crossing is isolated when no junction lies within the crossing distance
   * beyond it. A junction joined only by lowest order roads of lower capacity does not count here, unless it has
   * signals of its own. For each isolated crossing, the result keeps the junctions it reaches along the road, within
   * the maximum distance from a vehicle signal to a junction, up to and including the first one that counts. They are
   * kept per segment arriving at each junction, with the distance to it. A crossing not on a junction node that lies
   * at the edge of the parsed area is kept as well, marked as such: the third pass ignores it, see
   * {@link #isAtEdgeOfParsedArea(OsmNode, double)}. A crossing that is isolated only because such a lowest order
   * junction lies within the crossing distance is counted, unless it lies at the edge of the parsed area.
   * <p>
   * A crossing is also isolated in a direction, whatever the junctions there, when it lies between its own stop lines:
   * a vehicle signal passes it in that direction, and past it, before the first junction, a vehicle signal faces back
   * towards it, see {@link #isFacedFromBeyond(OsmNode, SegmentArrivingAtTarget, Map)}. It is counted too, unless it
   * lies at the edge of the parsed area, and kept for the third pass, which never makes it part of a signalised
   * junction nearby.
   *
   * @param firstPass holds the crossing signals not on a junction node, and those on a junction node where the road
   *                  does not branch, and the vehicle signals on a road but not on a junction node
   * @param evidence of the junctions' own signals, from every vehicle signal collected, stop lines of isolated
   *                 crossings included
   * @param secondPass to collect the crossings isolated between their own stop lines in
   * @return isolated crossings before the junctions they reach, per segment arriving there
   */
  private Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> findIsolatedCrossingsByJunctionArrival(
      FirstPassResult firstPass,
      VehicleSignalEvidence evidence,
      SecondPassResult secondPass) {

    var crossingDistance = settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var reach = settings.getMaximumDistanceVehicleSignalToJunctionInMeters();
    var whereVehicleSignalsTaggedWithOneDirectionStand =
        findWhereVehicleSignalsTaggedWithOneDirectionStand(firstPass.vehicleSignalsNotOnJunctionNode);
    var farthestVehicleSignalByFirstArrival =
        findFarthestDistanceToFirstJunction(firstPass.vehicleSignalsNotOnJunctionNode);
    var notOnJunctionNode = Collections.newSetFromMap(new IdentityHashMap<OsmNode, Boolean>());
    notOnJunctionNode.addAll(firstPass.crossingSignalsNotOnJunctionNode);

    var isolated = new IdentityHashMap<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>>();
    for (var crossing : Stream.concat(firstPass.crossingSignalsNotOnJunctionNode.stream(),
        firstPass.crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch.stream()).collect(Collectors.toList())) {

      boolean atEdgeOfParsedArea =
          notOnJunctionNode.contains(crossing) && isAtEdgeOfParsedArea(crossing, crossingDistance);
      boolean isolatedPastLowestOrderRoad = false;
      boolean isolatedBetweenItsStopLines = false;
      for (var junctions : findSegmentsArrivingAtTargetsAlongRoad(crossing, null, reach, lookAheadForJunctions)) {

        var passed = new ArrayList<SegmentArrivingAtTarget>(1);
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
        var firstJunction = junctions.get(0);
        boolean betweenItsStopLines = !isIsolated && Precision.greater(
            farthestVehicleSignalByFirstArrival.getOrDefault(firstJunction.arriving, Double.NEGATIVE_INFINITY),
            firstJunction.distance, Precision.EPSILON_6) &&
            isFacedFromBeyond(crossing, firstJunction, whereVehicleSignalsTaggedWithOneDirectionStand);
        if (isIsolated || betweenItsStopLines) {
          passed.forEach(
              junction -> isolated.computeIfAbsent(junction.arriving, segment -> new ArrayList<>(1))
              .add(new IsolatedCrossingBeforeJunction(junction.distance, atEdgeOfParsedArea)));
          isolatedPastLowestOrderRoad |= isIsolated && lowestOrderRoadWithinCrossingDistance;
          isolatedBetweenItsStopLines |= betweenItsStopLines;
        }
      }
      if (isolatedPastLowestOrderRoad && !atEdgeOfParsedArea) {
        trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_ISOLATED_PAST_LOWEST_ORDER_ROAD, crossing);
      }
      if (isolatedBetweenItsStopLines) {
        secondPass.crossingsBetweenTheirStopLines.add(crossing);
        if (!atEdgeOfParsedArea) {
          trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_ISOLATED_BETWEEN_ITS_STOP_LINES, crossing);
        }
      }
    }
    return isolated;
  }

  /**
   * Where the vehicle signals tagged with one direction, forward or backward, stand on the segments carrying traffic
   * past them, see {@link #recordWhereVehicleSignalStands(OsmNode, MacroscopicLinkSegment, Map)}. A signal whose
   * direction the OSM ways through it disagree on is left out.
   *
   * @param vehicleSignals on a road but not on a junction node
   * @return per OSM way id, where those tagged with one direction stand
   */
  private Map<String, List<VehicleSignalPosition>> findWhereVehicleSignalsTaggedWithOneDirectionStand(
      List<PendingVehicleSignal> vehicleSignals) {
    var positionsByOsmWayId = new HashMap<String, List<VehicleSignalPosition>>();
    for (var pendingSignal : vehicleSignals) {
      var direction = OsmTrafficSignalTags.getRecognisedDirection(OsmModelUtil.getTagsAsMap(pendingSignal.signal));
      if (pendingSignal.oneDirectionUnknown || !OsmDirectionTags.isForwardOrBackward(direction)) {
        continue;
      }
      for (var passing : findSegmentsPassing(locate(pendingSignal.signal), direction, lookAheadForJunctions.travels)) {
        recordWhereVehicleSignalStands(pendingSignal.signal, passing, positionsByOsmWayId);
      }
    }
    return positionsByOsmWayId;
  }

  /**
   * Verify if a vehicle signal for the traffic coming the other way stands on a segment followed away from a crossing,
   * past the crossing: on the segment's opposite segment, nearer the segment's end than the crossing is
   *
   * @param segment followed away from the crossing
   * @param crossingToItsEnd distance from the crossing to the segment's downstream end, along the way, in metres
   * @param whereVehicleSignalsStand per OSM way id, where the vehicle signals that count stand
   * @return true when such a signal stands there, false otherwise
   */
  private boolean isVehicleSignalFacingBackTowardsCrossingOn(MacroscopicLinkSegment segment, double crossingToItsEnd,
      Map<String, List<VehicleSignalPosition>> whereVehicleSignalsStand) {
    var comingTheOtherWay = (MacroscopicLinkSegment) segment.getOppositeDirectionSegment();
    return comingTheOtherWay != null && findWhereVehicleSignalsStandOn(whereVehicleSignalsStand, comingTheOtherWay)
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
   * @param whereVehicleSignalsStand per OSM way id, where the vehicle signals that count stand
   * @return true when such a signal faces it, false otherwise
   */
  private boolean isFacedFromBeyond(OsmNode crossing, SegmentArrivingAtTarget firstJunction,
      Map<String, List<VehicleSignalPosition>> whereVehicleSignalsStand) {
    var start = firstJunction.passing;
    double crossingToStartEnd = PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(
        start, OsmNodeUtils.createPoint(crossing), geoUtils);
    var faced = MacroscopicLinkSegmentUtils.<Boolean>followOnlySegmentsOnwardUntilAnswered(
        start,
        true,
        crossingToStartEnd,
        settings.getMaximumDistanceVehicleSignalToJunctionInMeters(),
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
   * The first target of a signal in one direction: the junction it would be assigned to there, see
   * {@link #resolveJunction(List, VehicleSignalEvidence)}, or an isolated signalised crossing before it. When there is
   * such a crossing, it also tells whether the nearest one lies at the edge of the parsed area.
   *
   * @param junctions reached in the direction, nearest first
   * @param isolatedCrossings isolated crossings before the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @return the first target
   */
  private FirstTargetInDirection findFirstTarget(List<SegmentArrivingAtTarget> junctions,
      Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> isolatedCrossings,
      VehicleSignalEvidence evidence) {
    var junction = resolveJunction(junctions, evidence);
    double crossingDistance = isolatedCrossingDistance(junctions, junction, isolatedCrossings, crossing -> true);
    if (!Precision.smaller(crossingDistance, junction.distance, Precision.EPSILON_6)) {
      return new FirstTargetInDirection(junctions, junction, junction.distance, false, false);
    }
    double crossingAtEdgeDistance = isolatedCrossingDistance(
        junctions, junction, isolatedCrossings, crossing -> crossing.atEdgeOfParsedArea);
    boolean nearestAtEdge = !Precision.greater(crossingAtEdgeDistance, crossingDistance, Precision.EPSILON_6);
    return new FirstTargetInDirection(junctions, junction, crossingDistance, true, nearestAtEdge);
  }

  /**
   * The first target of a vehicle signal in each direction it applies to, see
   * {@link #findFirstTarget(List, Map, VehicleSignalEvidence)}. Each direction is resolved once here.
   *
   * @param pending the signal with the junctions it reaches
   * @param isolatedCrossings isolated crossings before the junctions they reach, per segment arriving there
   * @param evidence of the junctions' own signals
   * @return per direction, its first target, in the order of the signal's directions
   */
  private List<FirstTargetInDirection> findFirstTargets(PendingVehicleSignal pending,
      Map<MacroscopicLinkSegment, List<IsolatedCrossingBeforeJunction>> isolatedCrossings,
      VehicleSignalEvidence evidence) {
    return pending.junctionsPerDirection.stream()
        .map(junctions -> findFirstTarget(junctions, isolatedCrossings, evidence)).collect(Collectors.toList());
  }

  /**
   * Keep only one direction for a vehicle signal whose direction is unknown: the one whose first target is nearest.
   * There is one exception. If that target is a junction, and another direction reaches a junction at most
   * {@link #NEAR_TIE_DELTA_DISTANCE_M} farther where a road of higher capacity crosses, that direction is kept instead.
   *
   * @param pending the signal with the junctions it reaches
   * @param firstTargets per direction, its first target, see
   *                     {@link #findFirstTargets(PendingVehicleSignal, Map, VehicleSignalEvidence)}
   * @return the first targets as they are when the direction is known, or when there is only one. Otherwise only the
   *         first target of the kept direction
   */
  private List<FirstTargetInDirection> keepNearestDirectionWhenUnknown(PendingVehicleSignal pending,
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
            Precision.greater(crossingCapacity(other.junction), crossingCapacity(chosen.junction), Precision.EPSILON_6)) {
          chosen = other;
        }
      }
    }
    trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN, pending.signal);
    return List.of(chosen);
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
    boolean lowestOrderRoadJunctionPassed = false;
    boolean roadEndOutsideParsedAreaChosen = false;
    for (var junctions : pending.junctionsPerDirection) {
      var chosen = resolveJunction(junctions, evidence);
      lowestOrderRoadJunctionPassed |= chosen != junctions.get(0);
      roadEndOutsideParsedAreaChosen |= isRoadEndOutsideParsedArea(chosen.getTargetNode());
      addArrivingSegmentAsApproach(chosen, pending.signal, IntersectionType.JUNCTION);
      recordWhereVehicleSignalStands(pending.signal, chosen.passing, vehicleSignalPositionsByOsmWayId);
    }
    if (lowestOrderRoadJunctionPassed) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.LOWEST_ORDER_ROAD_JUNCTION_PASSED, pending.signal);
    }
    if (roadEndOutsideParsedAreaChosen) {
      trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA, pending.signal);
    }
    return !pending.junctionsPerDirection.isEmpty();
  }

  /**
   * Verify if a node is a junction for vehicle signals: the road branches there, see {@link #isRoadBranchNode(Node)},
   * a tram or light rail track meets the road there, or the road ends there outside the parsed area, see
   * {@link #isRoadEndOutsideParsedArea(Node)}. A vehicle signal before a tram or light rail track has its stop line
   * before the track, so the node where the track meets the road is its junction, not the road junction beyond it, see
   * {@link MacroscopicLinkUtils#isWhereTramOrLightRailTrackMeetsRoad(Node)}
   *
   * @param node to check
   * @return true when a junction for vehicle signals, false otherwise
   */
  private boolean isJunctionForVehicleSignals(Node node) {
    return isRoadBranchNode(node) || MacroscopicLinkUtils.isWhereTramOrLightRailTrackMeetsRoad(node) ||
        isRoadEndOutsideParsedArea(node);
  }

  /**
   * Verify if the road ends at a node outside the parsed area: only one link motorised road traffic can use meets
   * there, and the node lies outside the parsed area. The parsed road leads to a junction cut off by the edge, so the
   * node where it ends stands in for that junction. Inside the parsed area such a node is a real dead end, no junction.
   *
   * @param node to check
   * @return true when the road ends there outside the parsed area, false otherwise, also when the area is not bounded
   */
  private boolean isRoadEndOutsideParsedArea(Node node) {
    if (parsedArea.isEmpty() ||
        node.getNumberOfEdges(MacroscopicLinkUtils.permitsMotorisedRoadModeOnAnySegment()) != 1 ||
        !node.hasExternalId()) {
      return false;
    }
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(PlanitOsmUtils.parseOsmId(node));
    return osmNode != null && !parsedArea.isWhollyWithinBoundaryArea(OsmNodeUtils.createPoint(osmNode), true);
  }

  /**
   * Verify if a signal is at the edge of the parsed area. It is when it lies outside the area, on a way running out of
   * it. It also is when it lies inside, but no farther from the edge than the reach of the signal to its junction: the
   * maximum distance from a vehicle signal to its junction, or from a crossing to its junction. A junction it belongs to
   * is then likely cut off.
   *
   * @param signal the OSM node of the signal
   * @param reach from the signal to its junction, in metres
   * @return true when at the edge of the parsed area, false otherwise, also when the area is not bounded
   */
  private boolean isAtEdgeOfParsedArea(OsmNode signal, double reach) {
    if (parsedArea.isEmpty()) {
      return false;
    }
    var position = OsmNodeUtils.createPoint(signal);
    return !parsedArea.isWhollyWithinBoundaryArea(position, true) ||
        parsedArea.calculateProjectedDistanceToBoundingPolygon(position, true) <= reach;
  }

  /**
   * Verify if a vehicle signal lies within the maximum distance from a crossing to its junction of a crossing ignored
   * for lying at the edge of the parsed area, measured as the crow flies. It is then likely that crossing's stop line.
   *
   * @param vehicleSignal the OSM node of the vehicle signal
   * @param crossingsAtEdge the crossing signals ignored for lying at the edge of the parsed area
   * @return true when near such a crossing, false otherwise
   */
  private boolean isNearCrossingAtEdgeOfParsedArea(OsmNode vehicleSignal, List<OsmNode> crossingsAtEdge) {
    var crossingDistance = settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var position = OsmNodeUtils.createPoint(vehicleSignal);
    return crossingsAtEdge.stream().anyMatch(crossing -> !Precision.greater(
        geoUtils.getDistanceInMetres(position, OsmNodeUtils.createPoint(crossing)), crossingDistance,
        Precision.EPSILON_6));
  }

  /**
   * Third pass, in three steps. First, each collected crossing signal is part of a signalised junction nearby, unless
   * it is isolated between its own stop lines. If not, it is ignored when at the edge of the parsed area, see
   * {@link #isAtEdgeOfParsedArea(OsmNode, double)}, since a junction it belongs to may be cut off there. Otherwise it
   * is an isolated crossing. Second, each stop line of an isolated crossing joins that crossing, when it has an
   * intersection. Third, each vehicle signal left without a junction joins the signalised crossing ahead. If there is
   * none, it is ignored when at the edge of the parsed area, or near a crossing ignored there, see
   * {@link #isNearCrossingAtEdgeOfParsedArea(OsmNode, List)}. Otherwise it becomes an isolated crossing.
   * Isolated crossings are broken at and registered one at a time. Breaking a link through the layer modifier keeps the
   * intersections registered so far consistent, and the record of signals per approach follows along.
   *
   * @param firstPass holds the crossing signals not on a junction node, postponed by the first pass
   * @param secondPass holds the stop lines of isolated crossings, the vehicle signals without a junction ahead, and the
   *                   crossings isolated between their own stop lines, left by the second pass
   */
  private void integrateCrossingsAndUnassignedVehicleSignals(FirstPassResult firstPass,
      SecondPassResult secondPass) {
    DirectedGraphModifierListener followBreaks = new SignalsFollowBrokenApproachesHandler();
    networkLayer.getLayerModifier().addListener(followBreaks);
    try {
      var crossingsAtEdge = new ArrayList<OsmNode>();
      for (var crossingSignal : firstPass.crossingSignalsNotOnJunctionNode) {
        if (!secondPass.crossingsBetweenTheirStopLines.contains(crossingSignal) &&
            isPartOfSignalisedJunction(crossingSignal)) {
          /* nothing more to do, now or later: the crossing lies on an approach of a signalised junction nearby, so
           * that junction's intersection already holds it. No link is broken and nothing is added; only the outcome
           * is recorded */
          trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION, crossingSignal);
        } else if (isAtEdgeOfParsedArea(
            crossingSignal, settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters())) {
          /* whether it is isolated is unknown: a junction it belongs to may be cut off by the edge. Nothing is added
           * and no link is broken */
          trackAsDiagnostic(OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA, crossingSignal);
          crossingsAtEdge.add(crossingSignal);
        } else {
          trackAsDiagnostic(constructIntersectionAtSignalPosition(crossingSignal) ?
              OsmTrafficSignalOutcome.ISOLATED_CROSSING_OWN_INTERSECTION :
              OsmTrafficSignalOutcome.ISOLATED_CROSSING_NO_LINK_BROKEN, crossingSignal);
        }
      }
      /* stop lines of isolated crossings join them where they have an intersection */
      secondPass.isolatedCrossingStopLines.forEach(this::addApproachToSignalisedCrossingAhead);

      /* vehicle signals without a junction in reach join the signalised crossing ahead, all before any of them becomes
       * a crossing itself so the outcome does not depend on their order. Of the others, those at the edge of the
       * parsed area are ignored, their junction likely cut off, and so are those near a crossing ignored there, likely
       * its stop line; the rest are isolated crossings too: what crosses there is unknown, but not only cars */
      var isolated = new ArrayList<OsmNode>();
      for (var vehicleSignal : secondPass.vehicleSignalsWithoutJunction) {
        if (addApproachToSignalisedCrossingAhead(vehicleSignal)) {
          trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD, vehicleSignal);
        } else if (isAtEdgeOfParsedArea(vehicleSignal, settings.getMaximumDistanceVehicleSignalToJunctionInMeters()) ||
            isNearCrossingAtEdgeOfParsedArea(vehicleSignal, crossingsAtEdge)) {
          trackAsDiagnostic(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA, vehicleSignal);
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
   * within the crossing distance is signalised, and the segment arriving there is one of its approaches. It also is
   * when the first junction behind it, against the traffic, within the crossing distance is signalised: the crossing
   * then lies on one of that junction's exit arms, such as past it along a one-way road.
   *
   * @param signal the OSM node of the crossing signal
   * @return true when part of a signalised junction, false otherwise
   */
  private boolean isPartOfSignalisedJunction(OsmNode signal) {
    var maxDistance = settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    boolean onApproach = findFirstSegmentsArrivingAtTargetsAlongRoad(signal, null, maxDistance, lookAheadForJunctions)
        .stream().anyMatch(first -> {
          var intersection = intersectionsByOsmNodeId.get(PlanitOsmUtils.parseOsmId(first.getTargetNode()));
          return intersection != null && intersection.isSignalised() &&
              intersection.getApproachSegment(first.arriving.getId()) != null;
        });
    return onApproach || findFirstJunctionsBehind(signal, maxDistance).stream().anyMatch(junction -> {
      var intersection = intersectionsByOsmNodeId.get(PlanitOsmUtils.parseOsmId(junction));
      return intersection != null && intersection.isSignalised();
    });
  }

  /**
   * The first junction for vehicle signals behind a signal, going back along the road against the traffic, in each
   * direction motorised road traffic passes it, within a maximum distance, see
   * {@link #isJunctionForVehicleSignals(Node)}. From a signal on a node, it goes back along the segments entering the
   * node. From a signal inside links, it goes back along every segment of them, measured from the signal's position. It
   * passes through nodes where the road simply continues, and stops where it does not continue clearly, at a node
   * already passed, or beyond the maximum distance.
   *
   * @param signal the OSM node of the signal
   * @param maxDistance from the signal a junction may be at, along the road, in metres
   * @return the junction nodes reached, empty when none within reach
   */
  private List<Node> findFirstJunctionsBehind(OsmNode signal, double maxDistance) {
    var location = locate(signal);
    if (location == null) {
      return List.of();
    }
    var travels = MacroscopicLinkSegmentUtils.permitsMotorisedRoadMode();
    var passingSegments = new ArrayList<MacroscopicLinkSegment>(2);
    var distancesBehind = new ArrayList<Double>(2);
    if (location.isOnNode()) {
      for (MacroscopicLinkSegment entering : location.node.<MacroscopicLinkSegment>getEntryLinkSegments()) {
        passingSegments.add(entering);
        distancesBehind.add(entering.getParent().getLengthMeters());
      }
    } else {
      var signalPosition = OsmNodeUtils.createPoint(signal);
      for (var link : location.links) {
        for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
          if (segment != null) {
            passingSegments.add(segment);
            distancesBehind.add(segment.getParent().getLengthMeters() -
                PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(segment, signalPosition, geoUtils));
          }
        }
      }
    }

    var junctions = new ArrayList<Node>(2);
    for (int index = 0; index < passingSegments.size(); ++index) {
      if (!travels.test(passingSegments.get(index))) {
        continue;
      }
      var junction = MacroscopicLinkSegmentUtils.<Node>followOnlySegmentsOnwardUntilAnswered(
          passingSegments.get(index), false, distancesBehind.get(index), maxDistance, travels,
          (current, distance) ->
              isJunctionForVehicleSignals(current.getUpstreamNode()) ? current.getUpstreamNode() : null);
      if (junction != null) {
        junctions.add(junction);
      }
    }
    return junctions;
  }

  /**
   * Verify if a node is a signalised crossing: it has an intersection of kind crossing
   *
   * @param node to check
   * @return true when a signalised crossing, false otherwise
   */
  private boolean isSignalisedCrossingNode(Node node) {
    var intersection = intersectionsByOsmNodeId.get(PlanitOsmUtils.parseOsmId(node));
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
    var direction = OsmTrafficSignalTags.getRecognisedDirection(OsmModelUtil.getTagsAsMap(signal));
    var reached = findFirstSegmentsArrivingAtTargetsAlongRoad(signal, direction,
        settings.getMaximumDistanceSignalisedCrossingToJunctionInMeters(), lookAheadForSignalisedCrossings);
    for (var crossing : reached) {
      addApproach(intersectionsByOsmNodeId.get(PlanitOsmUtils.parseOsmId(crossing.getTargetNode())),
          crossing.arriving, signal);
      recordWhereVehicleSignalStands(signal, crossing.passing, vehicleSignalPositionsByOsmWayId);
    }
    return !reached.isEmpty();
  }

  /**
   * Construct an intersection at the signal's own position, for a signal with no junction to belong to: an isolated
   * crossing signal, or a vehicle signal without a junction in reach. This is only done when breaking links at
   * isolated crossings is on. Otherwise nothing is created. If the signal is not on a PLANit node yet, the link is
   * broken there first. The intersection is of kind crossing: road traffic is held there for something crossing the
   * road. Every segment entering it that a vehicle the signal controls can use becomes an approach, see
   * {@link #addApproach(Intersection, MacroscopicLinkSegment, OsmNode)}. A newly created intersection is registered
   * straight away.
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
    addEnteringSegmentsAsApproaches(intersection, node, signal);
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
    return findFirstSegmentsArrivingAtTargetsAlongRoad(
        signal, null, OsmNearestJunctionBucket.MAX_DISTANCE_M, lookAheadForJunctions)
        .stream().map(first -> first.distance).min(Double::compare).orElse(null);
  }

  /**
   * Remove the intersections without an approach. An approach is for traffic a signal controls, see
   * {@link Mode#isMotorisedRoadOrRailMode()}, so such an intersection controls none, such as a crossing where only a
   * footway and a cycleway meet. This is done once all signals are handled. Only then is an intersection complete: a
   * later signal can still add approaches to one registered earlier. The layer's intersections are those registered
   * here. Each is removed through the layer modifier and recorded as an issue. A node of one that no motorised road or
   * rail mode uses keeps its signal for the footway and cycleway traffic entering it, see
   * {@link #hasSignalOfItsOwn(MacroscopicLinkSegment)}
   */
  private void removeIntersectionsWithoutApproach() {
    var withoutApproach = networkLayer.getIntersections().stream()
        .filter(intersection -> intersection.getApproachSegments().isEmpty()).collect(Collectors.toList());
    var planitEntityDiagnostics = networkData.getPlanitEntityDiagnostics();
    for (var intersection : withoutApproach) {
      intersection.getMemberNodes().stream().filter(MacroscopicLinkUtils::isUsedByNoMotorisedRoadOrRailMode)
          .forEach(nodesWithSignalWhereNoRoadOrRailPasses::add);
      planitEntityDiagnostics.registerIssue(OsmPlanitEntityIssue.INTERSECTION_REMOVED_WITHOUT_CONTROLLED_APPROACH,
          intersection.hasExternalId() ? intersection.getExternalId() :
              intersection.getMemberNodes().get(0).getExternalId());
      networkLayer.getLayerModifier().removeIntersection(intersection);
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
   * {@link #findSegmentsArrivingAtTargetsAlongRoad(SignalLocation, String, double, LookAhead)}.
   *
   * @param signal the OSM node of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the targets reached per direction, nearest first, no direction without one
   */
  private List<List<SegmentArrivingAtTarget>> findSegmentsArrivingAtTargetsAlongRoad(
      OsmNode signal, String direction, double maxDistance, LookAhead lookAhead) {
    var location = locate(signal);
    return location == null ? List.of() :
        findSegmentsArrivingAtTargetsAlongRoad(location, direction, maxDistance, lookAhead);
  }

  /**
   * The targets a look-ahead reaches along the road from a signal, in each direction it applies to, within a maximum
   * distance, as the segments arriving at the nodes the look-ahead looks for, its targets, such as a junction node or
   * the node of a signalised crossing, see {@link SegmentArrivingAtTarget}. From a
   * signal on a node, it follows the segments starting at the node. Those pass the signal and lie
   * wholly ahead of it; a segment ending at the node is behind it. From a signal inside links, it follows every segment
   * of them, since each passes it. The distance to a segment's end is then measured from the signal's position.
   *
   * @param location of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @param lookAhead segments travelled and targets looked for
   * @return the targets reached per direction, nearest first, no direction without one
   */
  private List<List<SegmentArrivingAtTarget>> findSegmentsArrivingAtTargetsAlongRoad(
      SignalLocation location, String direction, double maxDistance, LookAhead lookAhead) {
    var signalPosition = OsmNodeUtils.createPoint(location.signal);
    var found = new ArrayList<List<SegmentArrivingAtTarget>>(2);
    for (var passing : findSegmentsPassing(location, direction, lookAhead.travels)) {
      double distanceToItsEnd = location.isOnNode() ? passing.getParent().getLengthMeters() :
          PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(passing, signalPosition, geoUtils);
      var targets = findSegmentsArrivingAtTargetsAhead(passing, distanceToItsEnd, maxDistance, lookAhead);
      if (!targets.isEmpty()) {
        found.add(targets);
      }
    }
    return found;
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
  private List<SegmentArrivingAtTarget> findFirstSegmentsArrivingAtTargetsAlongRoad(
      OsmNode signal, String direction, double maxDistance, LookAhead lookAhead) {
    var reached = findSegmentsArrivingAtTargetsAlongRoad(signal, direction, maxDistance, lookAhead);
    return reached.stream().map(targets -> targets.get(0)).collect(Collectors.toList());
  }

  /**
   * Follow the way downstream from the segment passing a signal and collect the segments arriving at the nodes the
   * look-ahead looks for, its targets, without changing anything. It passes through nodes where the way simply
   * continues, crosswalks included. If the look-ahead carries on past targets, it does so along the same OSM way, or a
   * way with the same name. It stops at a dead end, where the way cannot be travelled in this direction, or where it
   * does not continue clearly, see
   * {@link PlanitLinkOsmUtils#findContinuingLinkSegment(MacroscopicLinkSegment, Predicate, boolean)}. It also stops
   * when it gets back to a node already passed, or beyond the maximum distance, measured along the way.
   *
   * @param passing segment carrying traffic past the signal
   * @param distanceToItsEnd from the signal to the passing segment's downstream end, along the way, in metres
   * @param maxDistance from the signal a target may be at, along the way, in metres
   * @param lookAhead segments travelled and target nodes looked for
   * @return the segments arriving at the targets reached, nearest first
   */
  private List<SegmentArrivingAtTarget> findSegmentsArrivingAtTargetsAhead(
      MacroscopicLinkSegment passing, double distanceToItsEnd, double maxDistance, LookAhead lookAhead) {
    var arrivingAtTargets = new ArrayList<SegmentArrivingAtTarget>(1);
    MacroscopicLinkSegmentUtils.followSegmentsUntilAnswered(
        passing,
        true,
        distanceToItsEnd,
        maxDistance,
        (current, distance) -> {
          if (!lookAhead.isTargetNode.test(current.getDownstreamNode())) {
            return null;
          }
          arrivingAtTargets.add(new SegmentArrivingAtTarget(current, distance, passing));
          return lookAhead.beyondTarget ? null : current;
        },
        current -> PlanitLinkOsmUtils.findContinuingLinkSegment(
            current, lookAhead.travels, lookAhead.isTargetNode.test(current.getDownstreamNode())));
    return arrivingAtTargets;
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
    intersection = networkLayer.getIntersections().getFactory().createNonSpatial(
        node, IntersectionControlType.SIGNALISED, type);
    intersection.setXmlId(String.valueOf(intersection.getId()));
    intersectionsByOsmNodeId.put(osmNodeId, intersection);
    created.add(intersection);
    return intersection;
  }

  /**
   * Add a segment as an approach of an intersection, for a signal. The signal's OSM node id is added to the
   * intersection's external ids, comma separated, each signal once. Only a segment a vehicle the signal controls can
   * use becomes an approach, see {@link Mode#isMotorisedRoadOrRailMode()}: a footway or cycleway entering a signalised
   * crossing does not, though it has a light of its own. A newly added approach is counted, and the signal is recorded
   * for it.
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
    if (!approach.isAnyModeAllowed(Mode::isMotorisedRoadOrRailMode)) {
      return;
    }
    if (intersection.addApproachSegment(approach)) {
      networkData.getPlanitEntityDiagnostics().registerApproachCreated();
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
  private void addArrivingSegmentAsApproach(SegmentArrivingAtTarget reached, OsmNode signal, IntersectionType type) {
    var node = reached.getTargetNode();
    addApproach(getOrCreateIntersection(PlanitOsmUtils.parseOsmId(node), node, type), reached.arriving, signal);
  }

  /**
   * Register an intersection on the layer, and count it as created
   *
   * @param intersection to register
   */
  private void register(Intersection intersection) {
    networkLayer.getIntersections().getFactory().register(intersection);
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
    var parsedAreaBoundary = networkData.getBoundingArea() != null ?
        networkData.getBoundingArea() : networkData.getInputFileBounds();
    parsedArea = OsmProjectedBoundingAreaHelper.ofNullable(parsedAreaBoundary, settings.getSourceCRS(),
        settings.getCountryName(), settings.getMaximumDistanceFerryOutsideBoundingPolygonInMeters());

    // pass one
    var firstPassResult = discardPostponeOrConstructIntersections();
    // pass two
    var secondPassResult = assignVehicleSignalsToJunctions(firstPassResult);
    // pass three
    integrateCrossingsAndUnassignedVehicleSignals(firstPassResult, secondPassResult);

    // intersections that control no road or rail traffic
    removeIntersectionsWithoutApproach();
    lowestOrderRoadOsmWayIds.clear();
    intersectionsByOsmNodeId.clear();
  }

  /**
   * Verify if a vehicle signal made into an approach stands on a link segment: it lies inside the segment's link, or
   * on the node the segment starts at, and applies to the segment's direction. It holds for the pieces of links broken
   * after the signals were integrated too. A signal on a junction node stands on no segment
   *
   * @param segment to check
   * @return true when a vehicle signal stands on it, false otherwise
   */
  public boolean isVehicleSignalStandingOn(MacroscopicLinkSegment segment) {
    return findWhereVehicleSignalsStandOn(vehicleSignalPositionsByOsmWayId, segment).findAny().isPresent();
  }

  /**
   * Verify if a link segment has a signal of its own: a vehicle signal made into an approach stands on it, see
   * {@link #isVehicleSignalStandingOn(MacroscopicLinkSegment)}, or it enters a node with a signal where no road or rail
   * passes, such as a crossing of a footway and a cycleway. Such a node is no intersection, since no traffic a signal
   * controls has an approach there, but its light counts all the same once consolidation makes the segment internal
   *
   * @param segment to check
   * @return true when it has a signal of its own, false otherwise
   */
  public boolean hasSignalOfItsOwn(MacroscopicLinkSegment segment) {
    return isVehicleSignalStandingOn(segment) ||
        nodesWithSignalWhereNoRoadOrRailPasses.contains(segment.getDownstreamNode());
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

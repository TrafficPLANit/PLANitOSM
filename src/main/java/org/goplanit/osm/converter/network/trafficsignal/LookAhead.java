package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.LinkSegmentUtils;
import org.goplanit.utils.network.layer.physical.LinkUtils;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.network.layer.physical.NodeUtils;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: how to look ahead from a signal
 * along its way, and the search itself. It says which segments may be travelled, which nodes are targets, and whether
 * to carry on past a target to find more. Carrying on only follows the same OSM way, or a way of the same name. It
 * searches the link segments and nodes of the PLANit layer, onward from where the signal sits on it, see
 * {@link SignalOnPlanitLayer}. Each use has a factory of its own: for junctions, for signalised crossings, and for the
 * road or branch ahead of a signal on a track or cycleway.
 *
 * @author markr
 */
final class LookAhead {

  /**
   * One target a look-ahead reached. It is the link segment arriving at a node the look-ahead looks for, such as a
   * junction node or the node of a signalised crossing; from a track or cycleway also a road node or a branch node. It
   * holds the distance from the signal to that node along the way, and the segment passing the signal where the
   * look-ahead started. Seen from the road it arrived on, it also tells what the node reached is like: the other roads
   * crossing there, their capacity, and a tram or light rail track meeting it.
   */
  public static final class TargetReached {

    /** the segment arriving at the node */
    public final MacroscopicLinkSegment arriving;

    /** from the signal to the node, along the way, in metres */
    public final double distance;

    /** the segment carrying traffic past the signal, where the look-ahead started */
    public final MacroscopicLinkSegment passing;

    /**
     * Constructor
     *
     * @param arriving the segment arriving at the node
     * @param distance from the signal to the node, along the way, in metres
     * @param passing the segment carrying traffic past the signal, where the look-ahead started
     */
    private TargetReached(MacroscopicLinkSegment arriving, double distance, MacroscopicLinkSegment passing) {
      this.arriving = arriving;
      this.distance = distance;
      this.passing = passing;
    }

    /**
     * Collect the node reached
     *
     * @return node reached
     */
    public Node getTargetNode() {
      return arriving.getDownstreamNode();
    }

    /**
     * The links of the other roads at the node reached. Motorised road traffic can use them in at least one direction.
     * They are not on the OSM way arrived along, nor on a way with the same name, see
     * {@link PlanitLinkOsmUtils#isSameOsmWayOrName(Link, Link)}.
     *
     * @return links of the other roads, empty when none
     */
    public List<MacroscopicLink> findCrossingRoadLinks() {
      var arrivingLink = arriving.getParent();
      var permitsMotorisedRoadMode = LinkUtils.permitsMotorisedRoadModeOnAnySegment();
      return getTargetNode().<MacroscopicLink>getLinks().stream()
          .filter(link -> link != arrivingLink && permitsMotorisedRoadMode.test(link) &&
              !PlanitLinkOsmUtils.isSameOsmWayOrName(link, arrivingLink))
          .collect(Collectors.toList());
    }

    /**
     * Capacity of the road crossing at the node reached. It is the highest capacity of any segment of the other roads
     * there that motorised road traffic can use, in either direction, see {@link #findCrossingRoadLinks()}. A
     * segment's capacity is its capacity per lane times its lanes.
     *
     * @return capacity in pcu/h, 0 when no other road meets it
     */
    public double computeCrossingRoadCapacity() {
      double capacity = 0;
      var permitsMotorisedRoadMode = LinkSegmentUtils.permitsMotorisedRoadMode();
      for (var link : findCrossingRoadLinks()) {
        for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
          if (segment != null && permitsMotorisedRoadMode.test(segment)) {
            capacity = Math.max(capacity, segment.getCapacityOrDefaultPcuH());
          }
        }
      }
      return capacity;
    }

    /**
     * Verify if a tram or light rail track meets the road at the node reached, other than a track along the road
     * arrived on, which seen from a crossing is the crossing's own road: one of the tracks there, see
     * {@link LinkUtils#isTramOrLightRailTrack()}, is on another OSM way than the segment arriving, and on a way of
     * another name. Example: a side street's track, named after the side street, counts; a track along the road the
     * crossing is on, named after that road, does not.
     *
     * @return true when such a track meets the road there, false otherwise
     */
    public boolean isWhereTrackNotAlongArrivingRoadMeetsRoad() {
      return getTargetNode().getEdges().stream().filter(LinkUtils.isTramOrLightRailTrack())
          .anyMatch(track -> !PlanitLinkOsmUtils.isSameOsmWayOrName((Link) track, arriving.getParent()));
    }
  }

  /** to measure from the signal to the end of the segment passing it */
  private final PlanitJtsCrsUtils geoUtils;

  /** the parsing data of the layer, to find where a signal sits on the PLANit layer */
  private final OsmNetworkReaderLayerData layerData;

  /**
   * Constructor
   *
   * @param travels the segments it may travel, both the ones it starts on and the ones it continues on
   * @param isTargetNode the nodes looked for
   * @param beyondTarget true to carry on past a target, along the same OSM way or a way of the same name, false to
   *                     stop at the first target
   * @param geoUtils to measure distances along links
   * @param layerData the parsing data of the layer, to find where a signal sits on the PLANit layer
   */
  private LookAhead(Predicate<? super MacroscopicLinkSegment> travels, Predicate<Node> isTargetNode,
      boolean beyondTarget, PlanitJtsCrsUtils geoUtils, OsmNetworkReaderLayerData layerData) {
    this.travels = travels;
    this.isTargetNode = isTargetNode;
    this.beyondTarget = beyondTarget;
    this.geoUtils = geoUtils;
    this.layerData = layerData;
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
   * @return the segments arriving at the targets reached, nearest first
   */
  private List<TargetReached> findSegmentsArrivingAtTargetsOnwardFrom(
      MacroscopicLinkSegment passing, double distanceToItsEnd, double maxDistance) {
    var arrivingAtTargets = new ArrayList<TargetReached>(1);
    LinkSegmentUtils.followSegmentsUntilAnswered(
        passing,
        true,
        distanceToItsEnd,
        maxDistance,
        (current, distance) -> {
          if (!isTargetNode.test(current.getDownstreamNode())) {
            return null;
          }
          arrivingAtTargets.add(new TargetReached(current, distance, passing));
          return beyondTarget ? null : current;
        },
        current -> PlanitLinkOsmUtils.findContinuingLinkSegment(
            current, travels, isTargetNode.test(current.getDownstreamNode())));
    return arrivingAtTargets;
  }

  /**
   * Create the look-ahead for junctions, from a vehicle signal. It travels segments motorised road traffic can use,
   * looks for junctions for vehicle signals, and carries on past them along the same OSM way or a way of the same name.
   * A node is a junction for vehicle signals when the road branches there, see
   * {@link NodeUtils#isWhereThreeOrMoreMotorisedRoadLinksMeet(Node)}, a tram or light rail track meets the road there,
   * or the road ends there outside the parsed area, see
   * {@link OsmProjectedBoundingAreaOrExtentHelper#isRoadEndOutside(Node)}. A vehicle signal before a tram or light
   * rail track has its stop line before the track, so the node where the track meets the road is its junction, not the
   * road junction beyond it, see {@link LinkUtils#isWhereTramOrLightRailTrackMeetsRoad(Node)}
   *
   * @param parsedArea the bounding area, or else the bounds the input file declares
   * @param geoUtils to measure distances along links
   * @param layerData the parsing data of the layer, to find where a signal sits on the PLANit layer
   * @return the look-ahead
   */
  public static LookAhead createForJunctionsForVehicleSignals(OsmProjectedBoundingAreaOrExtentHelper parsedArea,
      PlanitJtsCrsUtils geoUtils, OsmNetworkReaderLayerData layerData) {
    return new LookAhead(
        LinkSegmentUtils.permitsMotorisedRoadMode(),
        node -> NodeUtils.isWhereThreeOrMoreMotorisedRoadLinksMeet(node) ||
            LinkUtils.isWhereTramOrLightRailTrackMeetsRoad(node) ||
            parsedArea.isRoadEndOutside(node),
        true,
        geoUtils,
        layerData);
  }

  /**
   * Create the look-ahead for signalised crossings, from a vehicle signal. It travels segments motorised road traffic
   * can use, and stops at the first signalised crossing
   *
   * @param isSignalisedCrossingNode the check whether a node is a signalised crossing
   * @param geoUtils to measure distances along links
   * @param layerData the parsing data of the layer, to find where a signal sits on the PLANit layer
   * @return the look-ahead
   */
  public static LookAhead createForSignalisedCrossings(Predicate<Node> isSignalisedCrossingNode,
      PlanitJtsCrsUtils geoUtils, OsmNetworkReaderLayerData layerData) {
    return new LookAhead(
        LinkSegmentUtils.permitsMotorisedRoadMode(), isSignalisedCrossingNode, false, geoUtils, layerData);
  }

  /**
   * Create the look-ahead for the road or branch ahead of a signal on a track or cycleway. It travels the track or
   * cycleway, and stops at the first node where a road meets it, or where it branches, see
   * {@link NodeUtils#isWhereThreeOrMoreLinksWithSegmentMatchingMeet(Node, Predicate)} and
   * {@link #findRoadCrossingOrBranch(TargetReached, double)}
   *
   * @param travels the track or cycleway segments it may travel
   * @param geoUtils to measure distances along links
   * @param layerData the parsing data of the layer, to find where a signal sits on the PLANit layer
   * @return the look-ahead
   */
  public static LookAhead createForRoadOrBranch(Predicate<? super MacroscopicLinkSegment> travels,
      PlanitJtsCrsUtils geoUtils, OsmNetworkReaderLayerData layerData) {
    return new LookAhead(travels,
        node -> LinkUtils.isUsedByModeMatching(node, Mode::isMotorisedRoadMode) ||
            NodeUtils.isWhereThreeOrMoreLinksWithSegmentMatchingMeet(node, travels),
        false, geoUtils, layerData);
  }

  /** the segments it may travel, both the ones it starts on and the ones it continues on */
  public final Predicate<? super MacroscopicLinkSegment> travels;

  /** the nodes looked for */
  public final Predicate<Node> isTargetNode;

  /** true to carry on past a target, along the same OSM way or a way of the same name, false to stop at the first
   * target */
  public final boolean beyondTarget;

  /**
   * The targets this look-ahead reaches onward from a signal, in each direction it applies to, within a maximum
   * distance. The signal is located first, see
   * {@link #findSegmentsArrivingAtTargetsOnwardFromSignal(SignalOnPlanitLayer, String, double)}.
   *
   * @param signal the OSM node of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @return the targets reached per direction, nearest first, no direction without one
   */
  public List<List<TargetReached>> findSegmentsArrivingAtTargetsOnwardFromSignal(
      OsmNode signal, String direction, double maxDistance) {
    var location = SignalOnPlanitLayer.locateAndCreate(signal, layerData);
    return location == null ? List.of() :
        findSegmentsArrivingAtTargetsOnwardFromSignal(location, direction, maxDistance);
  }

  /**
   * The targets this look-ahead reaches onward from a signal, in each direction it applies to, within a maximum
   * distance, as the segments arriving at the nodes the look-ahead looks for, its targets, such as a junction node or
   * the node of a signalised crossing, see {@link TargetReached}. From a signal on a node, it follows the
   * segments starting at the node. Those pass the signal and lie wholly ahead of it; a segment ending at the node is
   * behind it. From a signal inside links, it follows every segment
   * of them, since each passes it. The distance to a segment's end is then measured from the signal's position.
   *
   * @param location of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @return the targets reached per direction, nearest first, no direction without one
   */
  public List<List<TargetReached>> findSegmentsArrivingAtTargetsOnwardFromSignal(
      SignalOnPlanitLayer location, String direction, double maxDistance) {
    var signalPosition = OsmNodeUtils.createPoint(location.signal);
    var found = new ArrayList<List<TargetReached>>(2);
    for (var passing : location.findSegmentsPassing(direction, travels)) {
      double distanceToItsEnd = location.isOnNode() ? passing.getParent().getLengthMeters() :
          PlanitGraphGeoUtils.getDistanceToDownstreamVertexInMetres(passing, signalPosition, geoUtils);
      var targets = findSegmentsArrivingAtTargetsOnwardFrom(passing, distanceToItsEnd, maxDistance);
      if (!targets.isEmpty()) {
        found.add(targets);
      }
    }
    return found;
  }

  /**
   * The first target this look-ahead reaches onward from a signal, in each direction it applies to, within a maximum
   * distance
   *
   * @param signal the OSM node of the signal
   * @param direction the signal applies to, null for both
   * @param maxDistance to look ahead, in metres
   * @return the first target reached per direction, empty when none within reach
   */
  public List<TargetReached> findFirstSegmentsArrivingAtTargetsOnwardFromSignal(
      OsmNode signal, String direction, double maxDistance) {
    var reached = findSegmentsArrivingAtTargetsOnwardFromSignal(signal, direction, maxDistance);
    return reached.stream().map(targets -> targets.get(0)).collect(Collectors.toList());
  }

  /**
   * Find where the intersection of a signal on a track or cycleway goes, given the node its look-ahead reached. If a
   * road meets that node, it is that node. If the way branches there instead, each branch is followed on within the
   * maximum distance. If only one branch reaches a road, the result is where that branch leads. If more than one does,
   * it is the branch node itself.
   *
   * @param reached the node reached, and the segment arriving there
   * @param maxDistance from the signal a road may be at, along the way, in metres
   * @return the road node or the branch node, with the segment arriving there, null when no branch reaches a road
   */
  public TargetReached findRoadCrossingOrBranch(TargetReached reached, double maxDistance) {
    var node = reached.getTargetNode();
    if (LinkUtils.isUsedByModeMatching(node, Mode::isMotorisedRoadMode)) {
      return reached;
    }
    TargetReached alongOnlyBranch = null;
    for (var leaving : LinkSegmentUtils.findContinuations(reached.arriving, travels)) {
      var targets = findSegmentsArrivingAtTargetsOnwardFrom(
          leaving, reached.distance + leaving.getParent().getLengthMeters(), maxDistance);
      var alongBranch = targets.isEmpty() ? null : findRoadCrossingOrBranch(targets.get(0), maxDistance);
      if (alongBranch != null) {
        if (alongOnlyBranch != null) {
          return reached;
        }
        alongOnlyBranch = alongBranch;
      }
    }
    return alongOnlyBranch;
  }
}

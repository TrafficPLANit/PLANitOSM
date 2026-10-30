package org.goplanit.osm.converter.network.trafficsignal;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.network.OsmIntersectionSettings;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.osm.util.OsmTrafficSignalUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.physical.LinkSegmentUtils;
import org.goplanit.utils.network.layer.physical.LinkUtils;
import org.goplanit.utils.network.layer.physical.NodeUtils;

import org.locationtech.jts.geom.Coordinate;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the first of its three passes.
 * It goes through every recorded signal once, and settles it at once when its own node or way decides: discarded, or
 * its intersection constructed, for a vehicle signal on a junction node, a track or cycleway signal at the road ahead,
 * or a crossing signal on a junction node. Otherwise it postpones it: a vehicle signal before a junction to the second
 * pass, a crossing signal not on a junction node to the third. Example: a signal on the node where three roads meet
 * becomes that junction at once; a signal 30 m before it waits for the second pass.
 *
 * @author markr
 */
final class OsmTrafficSignalFirstPass {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmTrafficSignalFirstPass.class.getCanonicalName());

  /** the settings, holding the maximum distances */
  private final OsmNetworkReaderSettings settings;

  /** the parsing data of the network: the recorded traffic signals, the OSM nodes and the diagnostics */
  private final OsmNetworkReaderData networkData;

  /** the parsing data of the layer, used to find the PLANit node or links an OSM node ended up on */
  private final OsmNetworkReaderLayerData layerData;

  /** to measure distances along links */
  private final PlanitJtsCrsUtils geoUtils;

  /** the parsed area: the bounding area, or else the bounds the input file declares, see
   * {@link OsmProjectedBoundingAreaOrExtentHelper} */
  private final OsmProjectedBoundingAreaOrExtentHelper parsedArea;

  /** looking ahead along the road from a signal for junctions for vehicle signals, see
   * {@link LookAhead#createForJunctionsForVehicleSignals}, which says what a junction for vehicle signals is */
  private final LookAhead lookAheadForJunctions;

  /** the intersections signals make, built before they are registered on the layer */
  private final OsmSignalIntersectionPreRegistration intersectionPreRegistration;

  /**
   * Find the first OSM node inside the link of a segment that matches, going from a position on the link along the
   * segment's direction or against it, within a distance. It stops at the ends of the link: the nodes there are PLANit
   * nodes, not inside it
   *
   * @param segment whose link to search
   * @param from position on the link's geometry to start at, itself left out
   * @param withTraffic true to go along the segment's direction, false against it
   * @param maxDistanceMeters farthest from the start to look, along the link
   * @param matches true for the node sought
   * @return the node found, null when none
   */
  private OsmNode findFirstOsmNodeInsideLink(MacroscopicLinkSegment segment, Coordinate from, boolean withTraffic,
      double maxDistanceMeters, Predicate<OsmNode> matches) {
    var coordinates = segment.getParent().getGeometry().getCoordinates();
    int start = IntStream.range(0, coordinates.length).filter(index -> coordinates[index].equals2D(from))
        .findFirst().orElse(-1);
    int step = segment.isDirectionAb() == withTraffic ? 1 : -1;
    double meters = 0;
    for (int index = start + step; start >= 0 && index > 0 && index < coordinates.length - 1; index += step) {
      meters += geoUtils.getDistanceInMetres(coordinates[index - step], coordinates[index]);
      if (Precision.greater(meters, maxDistanceMeters, Precision.EPSILON_6)) {
        return null;
      }
      var osmNode = layerData.getOsmNodeInternalToLinkByLocation(PlanitJtsUtils.createPoint(coordinates[index]));
      if (osmNode != null && matches.test(osmNode)) {
        return osmNode;
      }
    }
    return null;
  }

  /**
   * Take each crossing node without signal tags that a vehicle signal not on a junction node reaches first along its
   * link, within the crossing distance, as a crossing signal: the vehicle signal stands there for the crossing, most
   * likely a signalised crossing tagged without its signal tags. A crossing node is one tagged
   * {@code highway=crossing}, or one where a footway, cycleway or similar way meets the road, passing through or ending
   * there, whether such ways are parsed or not, see {@link OsmNetworkReaderData#isOsmNodeOfWayThatCanCrossRoad(long)}.
   * It is recorded as a signal, noted to check in OSM, each kind with a note of its own, and postponed to the third
   * pass like any crossing signal not on a junction node. A node whose kind the settings override is left as
   * overridden, see {@link OsmIntersectionSettings#overrideTrafficSignalKind(long, OsmTrafficSignalKind)}
   *
   * @param firstPass holding the vehicle signals not on a junction node, to collect the crossing signals in
   */
  private void takeCrossingNodesAfterVehicleSignalsAsSignalised(OsmTrafficSignalFirstPassResult firstPass) {
    double crossingDistance =
        settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    Predicate<OsmNode> crossingNodeWithoutSignals = osmNode ->
        (OsmTrafficSignalTags.isCrossingNode(OsmModelUtil.getTagsAsMap(osmNode)) ||
            networkData.isOsmNodeOfWayThatCanCrossRoad(osmNode.getId())) &&
            !networkData.getTrafficSignalOsmNodeIds().contains(osmNode.getId()) &&
            settings.getIntersectionSettings().getTrafficSignalKindOverride(osmNode.getId()) == null;
    var crossingNodes = new LinkedHashSet<OsmNode>();
    for (var pending : firstPass.vehicleSignalsNotOnJunctionNode) {
      var position = OsmNodeUtils.createCoordinate(pending.signal);
      var direction = OsmTrafficSignalUtils.getDirectionOf(
          pending.signal, OsmModelUtil.getTagsAsMap(pending.signal), settings.getIntersectionSettings());
      for (var passing : SignalOnPlanitLayer.locateAndCreate(pending.signal, layerData).findSegmentsPassing(
          direction, LinkSegmentUtils.permitsMotorisedRoadMode())) {
        var crossingNode = findFirstOsmNodeInsideLink(
            passing, position, true, crossingDistance, crossingNodeWithoutSignals);
        if (crossingNode != null) {
          crossingNodes.add(crossingNode);
        }
      }
    }
    for (var crossingNode : crossingNodes) {
      networkData.registerTrafficSignalOsmNodeId(crossingNode.getId());
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalTags.isCrossingNode(OsmModelUtil.getTagsAsMap(crossingNode)) ?
          OsmTrafficSignalOutcome.CROSSING_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED :
          OsmTrafficSignalOutcome.CROSSING_WAY_NODE_AFTER_VEHICLE_SIGNAL_TAKEN_AS_SIGNALISED, crossingNode);
      constructOrPostponeCrossingSignal(
          crossingNode, SignalOnPlanitLayer.locateAndCreate(crossingNode, layerData), firstPass);
    }
  }

  /**
   * Drop each crossing signal not on a junction node whose road traffic faces a stop or give way sign between the
   * crossing and the first junction it reaches, within the crossing distance. The sign shows the traffic there is not
   * signal controlled, so the crossing is taken as not signalised: it gets no intersection, and it shows no signal at
   * the junction. A sign faces the traffic when its direction tag, forward or backward along its OSM way, matches it,
   * or when it has none. Recorded as the crossing signal's outcome, to check in OSM
   *
   * @param firstPass holding the crossing signals not on a junction node, to drop them from
   */
  private void dropCrossingSignalsBeforeStopOrGiveWaySign(OsmTrafficSignalFirstPassResult firstPass) {
    double crossingDistance =
        settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    for (var crossing : List.copyOf(firstPass.crossingSignalsNotOnJunctionNode)) {
      boolean beforeSign = lookAheadForJunctions.findSegmentsArrivingAtTargetsOnwardFromSignal(crossing, null,
          crossingDistance).stream().map(junctions -> junctions.get(0)).anyMatch(first ->
          !Precision.greater(first.distance, crossingDistance, Precision.EPSILON_6) && findFirstOsmNodeInsideLink(
              first.arriving, first.getTargetNode().getPosition().getCoordinate(), false, first.distance,
              osmNode -> OsmTrafficSignalTags.isStopOrGiveWaySign(OsmModelUtil.getTagsAsMap(osmNode)) &&
                  PlanitLinkOsmUtils.isInOsmDirection(
                      first.arriving, OsmModelUtil.getTagsAsMap(osmNode).get(OsmDirectionTags.DIRECTION))) != null);
      if (beforeSign) {
        firstPass.crossingSignalsNotOnJunctionNode.remove(crossing);
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.CROSSING_SIGNAL_BEFORE_STOP_OR_GIVE_WAY_SIGN, crossing);
      }
    }
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
   * every junction. A vehicle signal there tagged with direction both stops both directions at one point, which is what
   * a crossing does, so it is handled as a crossing signal instead, case (v), and noted to check in OSM. (v) A crossing
   * signal not on a junction node. It goes to the third pass. Whether it belongs to a signalised junction nearby
   * depends on the intersections the first two passes construct. (vi) A ramp meter, whatever else its tags say. It
   * goes to the third pass, once the other signals are done, so no look-ahead of theirs meets the node its intersection
   * may need.</li>
   * </ul>
   * The kind of signal is what its tags say, unless the settings override it, see
   * {@link OsmIntersectionSettings#overrideTrafficSignalKind(long, OsmTrafficSignalKind)}.
   *
   * @param osmNodeId of the signal
   * @param firstPass to collect the postponed signals, and the junction nodes with a vehicle or crossing signal, in
   */
  private void discardPostponeOrConstructIntersection(long osmNodeId, OsmTrafficSignalFirstPassResult firstPass) {
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
    var tags = OsmModelUtil.getTagsAsMap(osmNode);
    var intersectionSettings = settings.getIntersectionSettings();
    if (intersectionSettings.getTrafficSignalKindOverride(osmNodeId) == null &&
        OsmTrafficSignalTags.isNonControllingTrafficSignals(tags)) {
      networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.SIGNAL_NOT_CONTROLLING_TRAFFIC, osmNode);
      return;
    }
    boolean vehicleSignal =
        intersectionSettings.isHandledAsTrafficSignalOfKind(osmNodeId, tags, OsmTrafficSignalKind.VEHICLE_SIGNAL);
    boolean crossingSignal =
        intersectionSettings.isHandledAsTrafficSignalOfKind(osmNodeId, tags, OsmTrafficSignalKind.CROSSING_SIGNAL);
    var direction = OsmTrafficSignalUtils.getDirectionOf(osmNode, tags, intersectionSettings);
    if (direction == null && tags.containsKey(OsmTrafficSignalTags.TRAFFIC_SIGNALS_DIRECTION) && vehicleSignal) {
      /* noted only: from here on the signal is handled as untagged, its direction being null */
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalOutcome.VEHICLE_SIGNAL_DIRECTION_UNRECOGNISED, osmNode);
    }
    var location = SignalOnPlanitLayer.locateAndCreate(osmNode, layerData);
    if (location == null) {
      networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.SIGNAL_NOT_IN_LAYER, osmNode);
      return;
    }
    if (intersectionSettings.isHandledAsTrafficSignalOfKind(osmNodeId, tags, OsmTrafficSignalKind.RAMP_METER)) {
      // case (vi), postponed: a ramp meter, collected for the third pass
      firstPass.rampMeters.add(osmNode);
      return;
    }

    if (vehicleSignal) {
      if (location.isOnNode() && lookAheadForJunctions.isTargetNode.test(location.node)) {
        // attempt case (i)
        intersectionPreRegistration.integrateSignalOnNode(osmNode, location.node, IntersectionType.JUNCTION);
        firstPass.junctionNodesWithVehicleSignal.add(location.node);
        networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.VEHICLE_SIGNAL_ON_JUNCTION_NODE, osmNode);
        if (parsedArea.isRoadEndOutside(location.node)) {
          networkData.getParseDiagnostics().register(
              OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_ROAD_END_OUTSIDE_PARSED_AREA, osmNode);
        }
      } else if (location.getLinks().stream().anyMatch(LinkUtils.permitsMotorisedRoadModeOnAnySegment())) {
        if (OsmDirectionTags.BOTH.equals(direction)) {
          /* stopping both directions at one point between junctions is what a crossing does: handled as a crossing
           * signal, case (v), not as a stop line of a junction ahead. With crossing tags it is one anyway, below */
          if (!crossingSignal) {
            networkData.getParseDiagnostics().register(
                OsmTrafficSignalOutcome.VEHICLE_SIGNAL_BOTH_DIRECTIONS_TAKEN_AS_CROSSING, osmNode);
            constructOrPostponeCrossingSignal(osmNode, location, firstPass);
          }
        } else {
          /* case (iv), postponed: vehicle signal on a road but not on a junction node, junctions ahead collected for
           * the second pass */
          firstPass.vehicleSignalsNotOnJunctionNode.add(collectVehicleSignal(location, direction));
        }
      } else {
        /* attempt case (ii) of construction: no road at the signal: on a track or cycleway, the road crossing or
         * branch ahead becomes its intersection; on any other way, or with no road in reach, it is discarded */
        integrateSignalOnRailOrActiveModeWay(location);
      }
    }
    if (crossingSignal) {
      constructOrPostponeCrossingSignal(osmNode, location, firstPass);
    }
  }

  /**
   * Construct the intersection of a crossing signal on a junction node, case (iii), or postpone a crossing signal not
   * on a junction node to the third pass, case (v), see {@link #discardPostponeOrConstructIntersection(long,
   * OsmTrafficSignalFirstPassResult)}
   *
   * @param osmNode of the crossing signal
   * @param location of the crossing signal
   * @param firstPass to collect the crossing signal in, postponed or on a junction node
   */
  private void constructOrPostponeCrossingSignal(
      OsmNode osmNode, SignalOnPlanitLayer location, OsmTrafficSignalFirstPassResult firstPass) {
    if (location.isOnNode() && NodeUtils.isWhereThreeOrMoreLinksMeet(location.node)) {
      // attempt case (iii)
      intersectionPreRegistration.integrateSignalOnNode(osmNode, location.node, IntersectionType.CROSSING);
      networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ON_JUNCTION_NODE, osmNode);
      if (!NodeUtils.isWhereThreeOrMoreMotorisedRoadLinksMeet(location.node)) {
        firstPass.crossingSignalsOnJunctionNodeWhereRoadDoesNotBranch.add(osmNode);
      } else {
        firstPass.junctionNodesWithCrossingSignal.add(location.node);
      }
    } else {
      /* case (v), postponed: crossing signal not on a junction node, collected for the third pass */
      firstPass.crossingSignalsNotOnJunctionNode.add(osmNode);
    }
  }

  /**
   * Handle a crossing signal tagged only on the crossing way, a footway, cycleway, path, bridleway or pedestrian way,
   * as if it were tagged on the node where the crossing way crosses a road, see
   * {@link #constructOrPostponeCrossingSignal(OsmNode, SignalOnPlanitLayer, OsmTrafficSignalFirstPassResult)}. That
   * node is recorded as a signal from here on. Any other node of the crossing way, e.g. where it meets the kerb, is not
   * on a road and is no signal, so it is passed over.
   * <p>
   * The crossing way is trusted over the node. Where the node's own tags say the crossing has no signals, e.g.
   * crossing=zebra, the crossing is still taken as signalised. The inconsistency is noted and logged with the ids of
   * the node and the way, so it can be checked in OSM.
   * </p>
   *
   * @param osmNodeId of a node on a signalised crossing way without crossing signal tags of its own
   * @param firstPass to collect the crossing signal in, postponed or on a junction node
   */
  private void integrateCrossingSignalTaggedOnlyOnCrossingWay(
      long osmNodeId, OsmTrafficSignalFirstPassResult firstPass) {
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
    var location = SignalOnPlanitLayer.locateAndCreate(osmNode, layerData);
    if (location == null ||
        location.getLinks().stream().noneMatch(LinkUtils.permitsMotorisedRoadModeOnAnySegment())) {
      return;
    }

    networkData.registerTrafficSignalOsmNodeId(osmNodeId);
    networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.CROSSING_SIGNAL_ONLY_ON_CROSSING_WAY, osmNode);
    var tags = OsmModelUtil.getTagsAsMap(osmNode);
    if (OsmTrafficSignalTags.isCrossingWithoutSignals(tags)) {
      networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.CROSSING_SIGNAL_TAGS_INCONSISTENT, osmNode);
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
   * {@link LookAhead#findSegmentsArrivingAtTargetsOnwardFromSignal(SignalOnPlanitLayer, String, double)}. A special
   * case is a node where two OSM ways end and the road simply continues. If those ways disagree on the tagged
   * direction, the conflict is counted. The signal then applies to one direction without knowing which, as if untagged.
   *
   * @param location of the signal
   * @param direction the signal applies to, null when not tagged
   * @return the signal with the junctions it reaches, not yet assigned
   */
  private PendingOsmVehicleSignal collectVehicleSignal(SignalOnPlanitLayer location, String direction) {
    var maxDistance = settings.getIntersectionSettings().getMaximumDistanceVehicleSignalToJunctionInMeters();
    if (OsmDirectionTags.isForwardOrBackward(direction) && location.isOnNode() &&
        PlanitLinkOsmUtils.waysDisagreeOnOrientation(location.node, location.getLinks().stream()
            .filter(LinkUtils.permitsMotorisedRoadModeOnAnySegment()).collect(Collectors.toList()))) {
      networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.DIRECTION_CONFLICT, location.signal);
      return new PendingOsmVehicleSignal(location.signal,
          lookAheadForJunctions.findSegmentsArrivingAtTargetsOnwardFromSignal(location, null, maxDistance), true);
    }
    return new PendingOsmVehicleSignal(location.signal,
        lookAheadForJunctions.findSegmentsArrivingAtTargetsOnwardFromSignal(location, direction, maxDistance),
        direction == null);
  }

  /**
   * Handle a vehicle signal with no road at its location: on a tram or light rail track, or on a cycleway. Its way is
   * followed to the nearest node where a road meets it. On a cycleway, only segments that allow bicycles are followed.
   * From a track, within the railway settings' maximum distance, that node becomes a signalised junction: car traffic
   * is held there for the tram. From a cycleway, within the crossing distance, it becomes a signalised crossing. Every
   * segment entering it that a vehicle the signal controls can use is an approach. If the way branches before the
   * road, and more than one branch reaches a road, the branch node becomes the intersection instead, see
   * {@link LookAhead#findRoadCrossingOrBranch(LookAhead.TargetReached, double)}. Its only approach is then the
   * segment arriving from the signal on a track; on a cycleway it has none. No road node is joined. The signal is
   * discarded when no road is in reach, or when it is on another kind of way, such as a footway. The outcome is
   * recorded either way, and no link is broken.
   *
   * @param location of the signal
   */
  private void integrateSignalOnRailOrActiveModeWay(SignalOnPlanitLayer location) {
    var signal = location.signal;
    var links = location.getLinks();
    Predicate<Mode> isBicycleMode = mode -> mode.isPredefinedModeType(PredefinedModeType.BICYCLE);
    boolean onTrack = links.stream().anyMatch(link -> link.isAnyModeAllowedOnAnySegment(Mode::isRailMode));
    if (!onTrack && links.stream().noneMatch(link -> link.isAnyModeAllowedOnAnySegment(isBicycleMode))) {
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalOutcome.TRAFFIC_SIGNAL_NOT_ON_ROAD_TRACK_OR_CYCLEWAY, signal);
      return;
    }
    Predicate<MacroscopicLinkSegment> travels = onTrack ?
        segment -> segment.isAnyModeAllowed(Mode::isRailMode) :
        segment -> segment.isAnyModeAllowed(isBicycleMode);
    var lookAheadForRoadOrBranch = LookAhead.createForRoadOrBranch(travels, geoUtils, layerData);
    double maxDistance = onTrack ?
        settings.getRailwaySettings().getMaximumDistanceTrackSignalToRoadCrossingInMeters() :
        settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var reached = lookAheadForRoadOrBranch.findSegmentsArrivingAtTargetsOnwardFromSignal(location, null, maxDistance);
    var nearest = reached.stream()
        .map(targets -> lookAheadForRoadOrBranch.findRoadCrossingOrBranch(targets.get(0), maxDistance))
        .filter(Objects::nonNull).min(Comparator.comparingDouble(target -> target.distance));
    if (nearest.isEmpty()) {
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING, signal);
      return;
    }
    var type = onTrack ? IntersectionType.JUNCTION : IntersectionType.CROSSING;
    if (!LinkUtils.isUsedByModeMatching(nearest.get().getTargetNode(), Mode::isMotorisedRoadMode)) {
      intersectionPreRegistration.addArrivingSegmentAsApproach(nearest.get(), signal, type);
      networkData.getParseDiagnostics().register(OsmTrafficSignalOutcome.TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH, signal);
      return;
    }
    intersectionPreRegistration.integrateSignalOnNode(signal, nearest.get().getTargetNode(), type);
    networkData.getParseDiagnostics().register(onTrack ? OsmTrafficSignalOutcome.TRACK_SIGNAL_AT_ROAD_CROSSING :
        OsmTrafficSignalOutcome.CYCLEWAY_SIGNAL_AT_ROAD_CROSSING, signal);
  }

  /**
   * Constructor
   *
   * @param settings holding the maximum distances
   * @param networkData holding the recorded traffic signals, the OSM nodes and the diagnostics
   * @param layerData of the layer
   * @param geoUtils to measure distances along links
   * @param parsedArea the bounding area, or else the bounds the input file declares
   * @param lookAheadForJunctions looking ahead along the road for junctions for vehicle signals
   * @param intersectionPreRegistration the intersections signals make, built before they are registered on the layer
   */
  public OsmTrafficSignalFirstPass(OsmNetworkReaderSettings settings, OsmNetworkReaderData networkData,
      OsmNetworkReaderLayerData layerData, PlanitJtsCrsUtils geoUtils,
      OsmProjectedBoundingAreaOrExtentHelper parsedArea, LookAhead lookAheadForJunctions,
      OsmSignalIntersectionPreRegistration intersectionPreRegistration) {
    this.settings = settings;
    this.networkData = networkData;
    this.layerData = layerData;
    this.geoUtils = geoUtils;
    this.parsedArea = parsedArea;
    this.lookAheadForJunctions = lookAheadForJunctions;
    this.intersectionPreRegistration = intersectionPreRegistration;
  }

  /**
   * First pass. Each recorded signal is discarded, postponed to a later pass, or its intersection is constructed, see
   * {@link #discardPostponeOrConstructIntersection(long, OsmTrafficSignalFirstPassResult)}. Then the same for each
   * crossing signal tagged only on the crossing way through its node, see
   * {@link #integrateCrossingSignalTaggedOnlyOnCrossingWay(long, OsmTrafficSignalFirstPassResult)}. Then crossing nodes
   * right after a vehicle signal become crossing signals, see
   * {@link #takeCrossingNodesAfterVehicleSignalsAsSignalised}, and crossing signals before a stop or give way sign are
   * dropped, see {@link #dropCrossingSignalsBeforeStopOrGiveWaySign}.
   *
   * @return what the passes after it need, see {@link OsmTrafficSignalFirstPassResult}
   */
  public OsmTrafficSignalFirstPassResult discardPostponeOrConstructIntersections() {
    var firstPass = new OsmTrafficSignalFirstPassResult();
    for (long osmNodeId : networkData.getTrafficSignalOsmNodeIds()) {
      discardPostponeOrConstructIntersection(osmNodeId, firstPass);
    }
    for (long osmNodeId : networkData.getOsmNodeIdsOnSignalisedCrossingWayOnly()) {
      integrateCrossingSignalTaggedOnlyOnCrossingWay(osmNodeId, firstPass);
    }
    takeCrossingNodesAfterVehicleSignalsAsSignalised(firstPass);
    dropCrossingSignalsBeforeStopOrGiveWaySign(firstPass);
    return firstPass;
  }
}

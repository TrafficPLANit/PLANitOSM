package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.List;

import org.goplanit.osm.converter.diagnostics.OsmNearestJunctionBucket;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmTrafficSignalUtils;
import org.goplanit.osm.util.PlanitNetworkLayerModifierUtils;
import org.goplanit.osm.util.PlanitOsmNetworkLayerUtils;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.Intersection;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.physical.LinkSegmentUtils;
import org.goplanit.utils.network.layer.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the last of its three passes.
 * It integrates what the first two passes left, once their intersections exist. A crossing signal not on a junction
 * node joins a signalised junction nearby, is ignored near or beyond the boundary of the parsed area, or else becomes
 * an isolated crossing. Stop lines of isolated crossings join their crossing. A vehicle signal without a junction joins
 * the signalised crossing ahead, is ignored near or beyond the boundary, or else becomes an isolated crossing. Example:
 * a pedestrian crossing 20 m before a signalised junction becomes part of it; one 200 m from any junction gets its own
 * intersection.
 *
 * @author markr
 */
final class OsmTrafficSignalThirdPass {

  /** the settings, holding the maximum distances */
  private final OsmNetworkReaderSettings settings;

  /** the parsing data of the network: the recorded traffic signals, the OSM nodes and the diagnostics */
  private final OsmNetworkReaderData networkData;

  /** the layer to add intersections to */
  private final MacroscopicNetworkLayer networkLayer;

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

  /** looking ahead along the road from a vehicle signal to the first signalised crossing, see
   * {@link LookAhead#createForSignalisedCrossings} */
  private final LookAhead lookAheadForSignalisedCrossings;

  /** the intersections signals make, built before they are registered on the layer */
  private final OsmSignalIntersectionPreRegistration intersectionPreRegistration;

  /** where the vehicle signals made into an approach stand, kept across link breaks */
  private final OsmVehicleSignalPositionsWithDirection vehicleSignalPositions;

  /**
   * Verify if a vehicle signal lies within the maximum distance from a crossing to its junction of a crossing ignored
   * for lying near or beyond the boundary of the parsed area, measured as the crow flies. It is then likely that
   * crossing's stop line.
   *
   * @param vehicleSignal the OSM node of the vehicle signal
   * @param crossingsIgnoredAtBoundary the crossing signals ignored for lying near or beyond the boundary of the parsed
   *                                   area
   * @return true when near such a crossing, false otherwise
   */
  private boolean isWithinCrossingDistanceOfCrossingIgnoredAtBoundary(
      OsmNode vehicleSignal, List<OsmNode> crossingsIgnoredAtBoundary) {
    var crossingDistance = settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    var position = OsmNodeUtils.createPoint(vehicleSignal);
    return crossingsIgnoredAtBoundary.stream().anyMatch(crossing -> !Precision.greater(
        geoUtils.getDistanceInMetres(position, OsmNodeUtils.createPoint(crossing)), crossingDistance,
        Precision.EPSILON_6));
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
    var maxDistance = settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters();
    boolean onApproach = lookAheadForJunctions.findFirstSegmentsArrivingAtTargetsOnwardFromSignal(
        signal, null, maxDistance).stream().anyMatch(first -> {
          var intersection = intersectionPreRegistration.findIntersectionCreatedAt(first.getTargetNode());
          return intersection != null && intersection.isSignalised() &&
              intersection.getApproachSegment(first.arriving.getId()) != null;
        });
    return onApproach || findFirstJunctionsBehind(signal, maxDistance).stream().anyMatch(junction -> {
      var intersection = intersectionPreRegistration.findIntersectionCreatedAt(junction);
      return intersection != null && intersection.isSignalised();
    });
  }

  /**
   * The first junction for vehicle signals behind a signal, going back along the road against the traffic, in each
   * direction motorised road traffic passes it, within a maximum distance, see the target check of
   * {@link #lookAheadForJunctions}. From a signal on a node, it goes back along the segments entering the node. From a
   * signal inside links, it goes back along every segment of them, measured from the signal's position. It passes
   * through nodes where the road simply continues, and stops where it does not continue clearly, at a node already
   * passed, or beyond the maximum distance.
   *
   * @param signal the OSM node of the signal
   * @param maxDistance from the signal a junction may be at, along the road, in metres
   * @return the junction nodes reached, empty when none within reach
   */
  private List<Node> findFirstJunctionsBehind(OsmNode signal, double maxDistance) {
    var location = SignalOnPlanitLayer.locateAndCreate(signal, layerData);
    if (location == null) {
      return List.of();
    }
    var travels = LinkSegmentUtils.permitsMotorisedRoadMode();
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
      Node junction = LinkSegmentUtils.followOnlySegmentsOnwardUntilAnswered(
          passingSegments.get(index), false, distancesBehind.get(index), maxDistance, travels,
          (current, distance) ->
              lookAheadForJunctions.isTargetNode.test(current.getUpstreamNode()) ?
                  current.getUpstreamNode() : null);
      if (junction != null) {
        junctions.add(junction);
      }
    }
    return junctions;
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
    var direction = OsmTrafficSignalUtils.getDirectionOf(
        signal, OsmModelUtil.getTagsAsMap(signal), settings.getIntersectionSettings());
    var reached = lookAheadForSignalisedCrossings.findFirstSegmentsArrivingAtTargetsOnwardFromSignal(signal, direction,
        settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters());
    for (var crossing : reached) {
      intersectionPreRegistration.addApproach(
          intersectionPreRegistration.findIntersectionCreatedAt(crossing.getTargetNode()), crossing.arriving, signal);
      vehicleSignalPositions.recordWhereVehicleSignalStands(signal, crossing.passing);
    }
    return !reached.isEmpty();
  }

  /**
   * Construct an intersection at the signal's own position, for a signal with no junction to belong to: an isolated
   * crossing signal, or a vehicle signal without a junction in reach. This is only done when breaking links at
   * isolated crossings is on. Otherwise nothing is created. If the signal is not on a PLANit node yet, the link is
   * broken there first. The intersection is of kind crossing: road traffic is held there for something crossing the
   * road. Every segment entering it that a vehicle the signal controls can use becomes an approach, see
   * {@link OsmSignalIntersectionPreRegistration#addApproach(Intersection, MacroscopicLinkSegment, OsmNode)}. A newly
   * created intersection is registered straight away. That keeps it consistent when links are broken for later
   * crossings.
   *
   * @param signal the OSM node of the signal
   * @return true when the intersection was constructed, false when breaking links at isolated crossings is off
   */
  private boolean constructIntersectionAtSignalPosition(OsmNode signal) {
    if (!settings.getIntersectionSettings().isBreakLinksAtIsolatedSignalisedCrossings()) {
      return false;
    }
    var node = PlanitOsmNetworkLayerUtils.getOrCreatePopulateAndRegisterNode(
        signal, networkLayer, layerData, settings.isRetainOsmTags());
    PlanitNetworkLayerModifierUtils.breakLinksWithInternalNode(
        networkLayer, layerData, node, geoUtils.getCoordinateReferenceSystem());
    var intersection =
        intersectionPreRegistration.getOrPreRegisterIntersection(signal.getId(), node, IntersectionType.CROSSING);
    intersectionPreRegistration.addEnteringSegmentsAsApproaches(intersection, node, signal);
    intersectionPreRegistration.registerIfStillPreRegistered(intersection);
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
    return lookAheadForJunctions.findFirstSegmentsArrivingAtTargetsOnwardFromSignal(
        signal, null, OsmNearestJunctionBucket.MAX_DISTANCE_M)
        .stream().map(first -> first.distance).min(Double::compare).orElse(null);
  }

  /**
   * Constructor
   *
   * @param settings holding the maximum distances
   * @param networkData holding the recorded traffic signals, the OSM nodes and the diagnostics
   * @param networkLayer to add intersections to
   * @param layerData of the layer
   * @param geoUtils to measure distances along links
   * @param parsedArea the bounding area, or else the bounds the input file declares
   * @param lookAheadForJunctions looking ahead along the road for junctions for vehicle signals
   * @param intersectionPreRegistration the intersections signals make, built before they are registered on the layer
   * @param vehicleSignalPositions where the vehicle signals made into an approach stand
   */
  public OsmTrafficSignalThirdPass(OsmNetworkReaderSettings settings, OsmNetworkReaderData networkData,
      MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData, PlanitJtsCrsUtils geoUtils,
      OsmProjectedBoundingAreaOrExtentHelper parsedArea, LookAhead lookAheadForJunctions,
      OsmSignalIntersectionPreRegistration intersectionPreRegistration,
      OsmVehicleSignalPositionsWithDirection vehicleSignalPositions) {
    this.settings = settings;
    this.networkData = networkData;
    this.networkLayer = networkLayer;
    this.layerData = layerData;
    this.geoUtils = geoUtils;
    this.parsedArea = parsedArea;
    this.lookAheadForJunctions = lookAheadForJunctions;
    this.intersectionPreRegistration = intersectionPreRegistration;
    this.vehicleSignalPositions = vehicleSignalPositions;
    this.lookAheadForSignalisedCrossings = LookAhead.createForSignalisedCrossings(
        intersectionPreRegistration::isSignalisedCrossingNode, geoUtils, layerData);
  }

  /**
   * Third pass, in three steps. First, a collected crossing signal that is part of a signalised junction nearby, see
   * {@link #isPartOfSignalisedJunction(OsmNode)}, is left to that junction, unless it is isolated between its own stop
   * lines. Otherwise it is ignored when near or beyond the boundary of the parsed area, see
   * {@link OsmProjectedBoundingAreaOrExtentHelper#isNearOrBeyondBoundary(OsmNode, double)}, since a junction it belongs
   * to may be cut off there, or else it is an isolated crossing. Second, each stop line of an isolated crossing joins
   * that crossing, when it has an intersection. Third, each vehicle signal left without a junction joins the signalised
   * crossing ahead. If there is none, it is ignored when near or beyond the boundary of the parsed area, or near a
   * crossing ignored there, see {@link #isWithinCrossingDistanceOfCrossingIgnoredAtBoundary(OsmNode, List)}. Otherwise
   * it becomes an isolated crossing. Links are broken at isolated crossings, and their intersections registered, one at
   * a time. Breaking a link through the layer modifier keeps the intersections registered so far consistent.
   *
   * @param firstPass holds the crossing signals not on a junction node, postponed by the first pass
   * @param secondPass holds the stop lines of isolated crossings, the vehicle signals without a junction ahead, and the
   *                   crossings isolated between their own stop lines, left by the second pass
   */
  public void integrateCrossingsAndUnassignedVehicleSignals(OsmTrafficSignalFirstPassResult firstPass,
      OsmTrafficSignalSecondPassResult secondPass) {
    var crossingsIgnoredAtBoundary = new ArrayList<OsmNode>();
    for (var crossingSignal : firstPass.crossingSignalsNotOnJunctionNode) {
      if (!secondPass.crossingsBetweenTheirStopLines.contains(crossingSignal) &&
          isPartOfSignalisedJunction(crossingSignal)) {
        /* nothing more to do, now or later: the crossing lies on an approach or an exit arm of a signalised junction
         * nearby, so that junction's intersection already holds it. No link is broken and nothing is added; only the
         * outcome is recorded */
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.CROSSING_PART_OF_SIGNALISED_JUNCTION, crossingSignal);
      } else if (parsedArea.isNearOrBeyondBoundary(crossingSignal,
          settings.getIntersectionSettings().getMaximumDistanceSignalisedCrossingToJunctionInMeters())) {
        /* whether it is isolated is unknown: a junction it belongs to may be cut off by the boundary. Nothing is
         * added and no link is broken */
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.CROSSING_AT_EDGE_OF_PARSED_AREA, crossingSignal);
        crossingsIgnoredAtBoundary.add(crossingSignal);
      } else {
        networkData.getParseDiagnostics().register(constructIntersectionAtSignalPosition(crossingSignal) ?
            OsmTrafficSignalOutcome.ISOLATED_CROSSING_OWN_INTERSECTION :
            OsmTrafficSignalOutcome.ISOLATED_CROSSING_NO_LINK_BROKEN, crossingSignal);
      }
    }
    /* stop lines of isolated crossings join them where they have an intersection */
    secondPass.isolatedCrossingStopLines.forEach(this::addApproachToSignalisedCrossingAhead);

    /* vehicle signals without a junction in reach join the signalised crossing ahead, all before any of them becomes
     * a crossing itself so the outcome does not depend on their order. Of the others, those near or beyond the
     * boundary of the parsed area are ignored, their junction likely cut off, and so are those near a crossing
     * ignored there, likely its stop line; the rest are isolated crossings too: what crosses there is unknown, but
     * not only cars */
    var isolated = new ArrayList<OsmNode>();
    for (var vehicleSignal : secondPass.vehicleSignalsWithoutJunction) {
      if (addApproachToSignalisedCrossingAhead(vehicleSignal)) {
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_CROSSING_AHEAD, vehicleSignal);
      } else if (parsedArea.isNearOrBeyondBoundary(
          vehicleSignal, settings.getIntersectionSettings().getMaximumDistanceVehicleSignalToJunctionInMeters()) ||
          isWithinCrossingDistanceOfCrossingIgnoredAtBoundary(vehicleSignal, crossingsIgnoredAtBoundary)) {
        networkData.getParseDiagnostics().register(
            OsmTrafficSignalOutcome.VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA, vehicleSignal);
      } else {
        isolated.add(vehicleSignal);
      }
    }
    for (var vehicleSignal : isolated) {
      networkData.getParseDiagnostics().register(
          OsmTrafficSignalOutcome.VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD, vehicleSignal);
      networkData.getParseDiagnostics().registerNearestJunction(
          vehicleSignal, findNearestJunctionDistance(vehicleSignal));
      constructIntersectionAtSignalPosition(vehicleSignal);
    }
  }
}

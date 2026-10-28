package org.goplanit.osm.converter.network.trafficsignal;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.network.OsmIntersectionSettings;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.LinkUtils;
import org.goplanit.utils.network.layer.physical.Node;

/**
 * Turns the traffic signals recorded during pre-processing into intersections of one network layer. It runs once the
 * layer's links are complete and broken at every junction node. A signal on a junction node makes that node an
 * intersection. A vehicle signal before a junction makes the segment arriving at that junction one of its approaches.
 * It does so in three passes, see {@link OsmTrafficSignalFirstPass}, {@link OsmTrafficSignalSecondPass} and
 * {@link OsmTrafficSignalThirdPass}, then removes the intersections left without an approach.
 *
 * @author markr
 */
public class OsmTrafficSignalIntegrator {

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

  /** the lowest order roads parsed, which the layer parser hands over while it creates the links */
  private final OsmLowestOrderRoads lowestOrderRoads = new OsmLowestOrderRoads();

  /** where the vehicle signals made into an approach stand, kept across link breaks, see
   * {@link #isVehicleSignalStandingOn(LinkSegment)} */
  private final OsmVehicleSignalPositionsWithDirection vehicleSignalPositions =
      new OsmVehicleSignalPositionsWithDirection();

  /** the nodes with a signal where no road or rail passes, such as a crossing of a footway and a cycleway. Their
   * intersection is removed, since it has no approach: an approach is for traffic a signal controls, see
   * {@link #removeIntersectionsWithoutApproach()}. The nodes are tracked so their light is not lost. When consolidation
   * later joins such a node into a larger intersection, for example a footway node between two crossings, the segments
   * entering it become internal segments with a signal rather than plain ones, see
   * {@link #hasSignalOfItsOwn(LinkSegment)}. Kept by identity, because node ids are recreated after
   * parsing */
  private final Set<Node> nodesWithSignalWhereNoRoadOrRailPasses = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * Remove the intersections without an approach. An approach is for traffic a signal controls, see
   * {@link Mode#isMotorisedRoadOrRailMode()}, so such an intersection controls none, such as a crossing where only a
   * footway and a cycleway meet. This is done once all signals are handled. Only then is an intersection complete: a
   * later signal can still add approaches to one registered earlier. The layer's intersections are those the passes
   * registered. Each is removed through the layer modifier and recorded as an issue. A node of one that no motorised
   * road or rail mode uses keeps its signal for the footway and cycleway traffic entering it, see
   * {@link #hasSignalOfItsOwn(LinkSegment)}
   */
  private void removeIntersectionsWithoutApproach() {
    var withoutApproach = networkLayer.getIntersections().stream()
        .filter(intersection -> intersection.getApproachSegments().isEmpty()).collect(Collectors.toList());
    var planitEntityDiagnostics = networkData.getPlanitEntityDiagnostics();
    for (var intersection : withoutApproach) {
      intersection.getMemberNodes().stream().filter(LinkUtils::isUsedByNoMotorisedRoadOrRailMode)
          .forEach(nodesWithSignalWhereNoRoadOrRailPasses::add);
      planitEntityDiagnostics.registerIssue(OsmPlanitEntityIssue.INTERSECTION_REMOVED_WITHOUT_CONTROLLED_APPROACH,
          intersection.hasExternalId() ? intersection.getExternalId() :
              intersection.getMemberNodes().get(0).getExternalId());
      networkLayer.getLayerModifier().removeIntersection(intersection);
    }
  }

  /**
   * Verify if this layer has an OSM node with a traffic signal override in the settings: as a traffic signal, or, for a
   * node overridden as not a signal, as a node on its ways
   *
   * @param osmNodeId the OSM node id with the override
   * @return true when this layer has it, false otherwise
   */
  private boolean hasOsmNodeOverridden(long osmNodeId) {
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
    boolean overriddenAsNotASignal =
        settings.getIntersectionSettings().getTrafficSignalKindOverride(osmNodeId) == OsmTrafficSignalKind.NOT_A_SIGNAL;
    return osmNode != null &&
        (overriddenAsNotASignal || networkData.getTrafficSignalOsmNodeIds().contains(osmNodeId)) &&
        SignalOnPlanitLayer.locateAndCreate(osmNode, layerData) != null;
  }

  /**
   * Verify if a vehicle signal made into an approach stands on a link segment: it lies inside the segment's link, or
   * on the node the segment starts at, and applies to the segment's direction. It holds too for the new link segments
   * made by breaking a link at a node after the signals were integrated. A signal on a junction node stands on no
   * segment
   *
   * @param segment to check
   * @return true when a vehicle signal stands on it, false otherwise
   */
  private boolean isVehicleSignalStandingOn(LinkSegment segment) {
    return vehicleSignalPositions.findWhereVehicleSignalsStandOn(segment).findAny().isPresent();
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
   * {@link OsmLowestOrderRoads}. It is kept by OSM way id, so the links the way is broken into later are covered too.
   *
   * @param link created
   * @param tags of the OSM way it is created from
   */
  public void registerIfLowestOrderRoad(MacroscopicLink link, Map<String, String> tags) {
    lowestOrderRoads.registerIfLowestOrderRoad(link, tags);
  }

  /**
   * Turn the recorded traffic signals into intersections of the layer, creating and registering them. Each signal is
   * handled by the first case that applies, cheapest check first. There are three passes, because later cases depend on
   * what earlier ones create: {@link OsmTrafficSignalFirstPass#discardPostponeOrConstructIntersections()},
   * {@link OsmTrafficSignalSecondPass#assignVehicleSignalsToJunctions(OsmTrafficSignalFirstPassResult)} and
   * {@link OsmTrafficSignalThirdPass#integrateCrossingsAndUnassignedVehicleSignals(OsmTrafficSignalFirstPassResult,
   * OsmTrafficSignalSecondPassResult)}.
   */
  public void execute() {
    var parsedArea = OsmProjectedBoundingAreaOrExtentHelper.of(networkData, settings);
    var lookAheadForJunctions = LookAhead.createForJunctionsForVehicleSignals(parsedArea, geoUtils, layerData);
    var intersectionPreRegistration = new OsmSignalIntersectionPreRegistration(networkLayer, networkData);

    // pass one
    var firstPassResult = new OsmTrafficSignalFirstPass(
        settings,
        networkData,
        layerData,
        geoUtils,
        parsedArea,
        lookAheadForJunctions,
        intersectionPreRegistration).discardPostponeOrConstructIntersections();

    // pass two
    var isolatedCrossingFinder = new OsmIsolatedSignalisedCrossingFinder(
        settings.getIntersectionSettings(),
        networkData,
        layerData,
        geoUtils,
        parsedArea,
        lookAheadForJunctions);

    var junctionResolver = new VehicleSignalJunctionResolver(lowestOrderRoads, settings);

    var secondPassResult = new OsmTrafficSignalSecondPass(
        isolatedCrossingFinder,
        networkData,
        parsedArea,
        junctionResolver,
        intersectionPreRegistration,
        vehicleSignalPositions).assignVehicleSignalsToJunctions(firstPassResult);

    // pass three
    new OsmTrafficSignalThirdPass(
        settings,
        networkData,
        networkLayer,
        layerData,
        geoUtils,
        parsedArea,
        lookAheadForJunctions,
        intersectionPreRegistration,
        vehicleSignalPositions).integrateCrossingsAndUnassignedVehicleSignals(firstPassResult, secondPassResult);

    // intersections that control no road or rail traffic
    removeIntersectionsWithoutApproach();
  }

  /**
   * Free what the layer parser handed over while it created the links, the lowest order roads, see
   * {@link #registerIfLowestOrderRoad(MacroscopicLink, Map)}. Done when the layer parser is reset, not when executing,
   * so executing never empties what it reads
   */
  public void reset() {
    lowestOrderRoads.clear();
  }

  /**
   * Verify if a link segment has a signal of its own: a vehicle signal made into an approach stands on it, see
   * {@link #isVehicleSignalStandingOn(LinkSegment)}, or it enters a node with a signal where no road or rail
   * passes, such as a crossing of a footway and a cycleway. Such a node is no intersection, since no traffic a signal
   * controls has an approach there, but its light counts all the same once consolidation makes the segment internal
   *
   * @param segment to check
   * @return true when it has a signal of its own, false otherwise
   */
  public boolean hasSignalOfItsOwn(LinkSegment segment) {
    return isVehicleSignalStandingOn(segment) ||
        nodesWithSignalWhereNoRoadOrRailPasses.contains(segment.getDownstreamNode());
  }

  /**
   * Get the OSM node ids with a traffic signal override in the settings that this layer does not have: as a traffic
   * signal, or, for a node overridden as not a signal, as a node on its ways, see
   * {@link OsmIntersectionSettings#getOsmNodeIdsWithTrafficSignalOverride()}. A layer holds only the nodes on ways of
   * its own modes, so a node of one layer is not found in the others
   *
   * @return the node ids in increasing order, empty when none
   */
  public List<String> getOsmNodeIdsOverriddenNotFound() {
    return settings.getIntersectionSettings().getOsmNodeIdsWithTrafficSignalOverride().stream()
        .filter(osmNodeId -> !hasOsmNodeOverridden(osmNodeId)).map(String::valueOf)
        .collect(Collectors.toList());
  }
}

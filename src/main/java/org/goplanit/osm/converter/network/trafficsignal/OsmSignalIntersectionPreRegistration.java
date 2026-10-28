package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.intersection.Intersection;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionControlType;
import org.goplanit.utils.network.layer.macroscopic.intersection.IntersectionType;
import org.goplanit.utils.network.layer.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: builds the intersections signals
 * make before they are registered on the layer, and registers them in the right order. An intersection is built raw and
 * unregistered, and registered once complete, since the index of the layer's intersections only sees what an
 * intersection holds when it is registered. Until then it is found by the OSM node id of its signal or junction node.
 * Only a segment a motorised road or rail mode can use becomes an approach, and the signals are listed in the
 * intersection's external ids.
 *
 * @author markr
 */
final class OsmSignalIntersectionPreRegistration {

  /** the layer to add intersections to */
  private final MacroscopicNetworkLayer networkLayer;

  /** the parsing data of the network, holding the diagnostics that count the approaches and intersections created */
  private final OsmNetworkReaderData networkData;

  /** intersections created but not yet registered on the layer, in the order created */
  private final List<Intersection> preRegistered = new ArrayList<>();

  /** intersections created, by the OSM node id of their signal or junction node. Only kept while executing: later
   * changes to the layer, such as pruning, could leave it pointing at removed intersections */
  private final Map<Long, Intersection> intersectionsByOsmNodeId = new HashMap<>();

  /**
   * Register an intersection on the layer, and count it as created
   *
   * @param intersection to register
   */
  private void registerOnLayer(Intersection intersection) {
    networkLayer.getIntersections().getFactory().register(intersection);
    networkData.getPlanitEntityDiagnostics().registerIntersectionCreated();
  }

  /**
   * Constructor
   *
   * @param networkLayer to add intersections to
   * @param networkData of the network
   */
  public OsmSignalIntersectionPreRegistration(MacroscopicNetworkLayer networkLayer, OsmNetworkReaderData networkData) {
    this.networkLayer = networkLayer;
    this.networkData = networkData;
  }

  /**
   * Get the intersection created for an OSM node, or create and pre-register it, unregistered, with the given kind. It
   * is signalised. Its external id lists the signals defining it, added as they come.
   *
   * @param osmNodeId of the node or signal the intersection is created for
   * @param node its member node
   * @param type kind to create it with, or to add to one already created
   * @return intersection
   */
  public Intersection getOrPreRegisterIntersection(long osmNodeId, Node node, IntersectionType type) {
    var intersection = intersectionsByOsmNodeId.get(osmNodeId);
    if (intersection != null) {
      intersection.addType(type);
      return intersection;
    }
    intersection = networkLayer.getIntersections().getFactory().createNonSpatial(
        node, IntersectionControlType.SIGNALISED, type);
    intersection.setXmlId(String.valueOf(intersection.getId()));
    intersectionsByOsmNodeId.put(osmNodeId, intersection);
    preRegistered.add(intersection);
    return intersection;
  }

  /**
   * Add a segment as an approach of an intersection, for a signal. The signal's OSM node id is added to the
   * intersection's external ids, comma separated, each signal once. Only a segment a vehicle the signal controls can
   * use becomes an approach, see {@link Mode#isMotorisedRoadOrRailMode()}: a footway or cycleway entering a signalised
   * crossing does not, though it has a light of its own. A newly added approach is counted.
   *
   * @param intersection to add the approach to
   * @param approach segment to add
   * @param signal the OSM node of the signal
   */
  public void addApproach(Intersection intersection, MacroscopicLinkSegment approach, OsmNode signal) {
    var signalId = String.valueOf(signal.getId());
    if (!intersection.containsExternalId(signalId)) {
      intersection.appendExternalId(signalId);
    }
    if (!approach.isAnyModeAllowed(Mode::isMotorisedRoadOrRailMode)) {
      return;
    }
    if (intersection.addNewApproachSegment(approach)) {
      networkData.getPlanitEntityDiagnostics().registerApproachCreated();
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
  public void addArrivingSegmentAsApproach(LookAhead.TargetReached reached, OsmNode signal, IntersectionType type) {
    var node = reached.getTargetNode();
    addApproach(getOrPreRegisterIntersection(PlanitOsmUtils.parseOsmId(node), node, type), reached.arriving, signal);
  }

  /**
   * Add the segments entering a node as approaches of its intersection, for a signal, see
   * {@link #addApproach(Intersection, MacroscopicLinkSegment, OsmNode)}
   *
   * @param intersection to add the approaches to
   * @param node whose entering segments to add
   * @param signal the OSM node of the signal
   */
  public void addEnteringSegmentsAsApproaches(Intersection intersection, Node node, OsmNode signal) {
    for (MacroscopicLinkSegment entering : node.<MacroscopicLinkSegment>getEntryLinkSegments()) {
      addApproach(intersection, entering, signal);
    }
  }

  /**
   * Make a node an intersection, for a signal on it or reaching it. Every segment entering the node that a vehicle the
   * signal controls can use becomes an approach, see
   * {@link #addApproach(Intersection, MacroscopicLinkSegment, OsmNode)}.
   *
   * @param signal the OSM node of the signal
   * @param node the signal is on or reaches
   * @param type kind of intersection the signal makes
   */
  public void integrateSignalOnNode(OsmNode signal, Node node, IntersectionType type) {
    addEnteringSegmentsAsApproaches(
        getOrPreRegisterIntersection(PlanitOsmUtils.parseOsmId(node), node, type), node, signal);
  }

  /**
   * Find the intersection created for a node, by the node's OSM node id, whether still pre-registered or registered
   * already
   *
   * @param node to find the intersection of
   * @return the intersection, null when none was created for it
   */
  public Intersection findIntersectionCreatedAt(Node node) {
    return intersectionsByOsmNodeId.get(PlanitOsmUtils.parseOsmId(node));
  }

  /**
   * Verify if a node is a signalised crossing: it has an intersection of kind crossing
   *
   * @param node to check
   * @return true when a signalised crossing, false otherwise
   */
  public boolean isSignalisedCrossingNode(Node node) {
    var intersection = findIntersectionCreatedAt(node);
    return intersection != null && intersection.getTypes().contains(IntersectionType.CROSSING);
  }

  /**
   * Register every pre-registered intersection on the layer, in the order created, see
   * {@link #registerOnLayer(Intersection)}
   */
  public void registerAllPreRegistered() {
    preRegistered.forEach(this::registerOnLayer);
    preRegistered.clear();
  }

  /**
   * Register an intersection on the layer straight away when it is still pre-registered, so it stays consistent when
   * links are broken for later crossings. One registered already is left as it is
   *
   * @param intersection to register
   */
  public void registerIfStillPreRegistered(Intersection intersection) {
    if (preRegistered.remove(intersection)) {
      registerOnLayer(intersection);
    }
  }
}

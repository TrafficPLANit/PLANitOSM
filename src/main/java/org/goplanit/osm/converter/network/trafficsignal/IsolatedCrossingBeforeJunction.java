package org.goplanit.osm.converter.network.trafficsignal;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}, made by
 * {@link OsmIsolatedSignalisedCrossingFinder}: an isolated signalised crossing as seen from a junction it reaches. It
 * holds how far before that junction it lies, and whether it is ignored for lying near or beyond the boundary of the
 * parsed area, see {@link OsmProjectedBoundingAreaOrExtentHelper#isNearOrBeyondBoundary(OsmNode, double)}.
 *
 * @author markr
 */
final class IsolatedCrossingBeforeJunction {

  /** from the crossing to the junction, along the road, in metres */
  public final double distanceToJunction;

  /** true when the crossing lies near or beyond the boundary of the parsed area, so it is ignored, false otherwise */
  public final boolean nearOrBeyondBoundary;

  /**
   * Constructor
   *
   * @param distanceToJunction from the crossing to the junction, along the road, in metres
   * @param nearOrBeyondBoundary true when the crossing lies near or beyond the boundary of the parsed area, so it is
   *                             ignored
   */
  public IsolatedCrossingBeforeJunction(double distanceToJunction, boolean nearOrBeyondBoundary) {
    this.distanceToJunction = distanceToJunction;
    this.nearOrBeyondBoundary = nearOrBeyondBoundary;
  }
}

package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: what the second pass,
 * {@link OsmTrafficSignalSecondPass}, leaves for the third: the vehicle signals it could not assign to a junction.
 * These are the stop lines of isolated crossings, and the vehicle signals without a junction ahead. Also the crossings
 * isolated between their own stop lines.
 *
 * @author markr
 */
final class OsmTrafficSignalSecondPassResult {

  /** vehicle signals at the stop line of an isolated signalised crossing */
  public final List<OsmNode> isolatedCrossingStopLines = new ArrayList<>();

  /** vehicle signals without a junction ahead */
  public final List<OsmNode> vehicleSignalsWithoutJunction = new ArrayList<>();

  /** crossing signals isolated between their own stop lines, see
   * {@link OsmIsolatedSignalisedCrossingFinder#findIsolatedCrossingsByJunctionArrival(OsmTrafficSignalFirstPassResult,
   * VehicleSignalEvidence)}. Such a crossing is never part of a signalised junction nearby. Kept by identity */
  public final Set<OsmNode> crossingsBetweenTheirStopLines = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * Constructor, nothing collected yet
   */
  public OsmTrafficSignalSecondPassResult() {
  }
}

package org.goplanit.osm.converter.network.trafficsignal;

import java.util.List;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: a vehicle signal on a road but
 * not on a junction node, with the junctions it reaches in each direction it applies to. The first pass postpones it to
 * the second, which assigns it to its junctions.
 *
 * @author markr
 */
final class PendingOsmVehicleSignal {

  /** the OSM node of the signal */
  public final OsmNode signal;

  /** per direction the signal applies to, the junctions reached along the road, nearest first. No list is empty */
  public final List<List<LookAhead.TargetReached>> junctionsPerDirection;

  /** true when the signal applies to one direction, but it is unknown which. That is the case when no direction is
   * tagged, or when the OSM ways through it disagree on the tagged one */
  public final boolean oneDirectionUnknown;

  /**
   * Constructor
   *
   * @param signal the OSM node of the signal
   * @param junctionsPerDirection per direction, the junctions reached along the road, nearest first. No list is empty
   * @param oneDirectionUnknown true when the signal applies to one direction, but it is unknown which
   */
  public PendingOsmVehicleSignal(OsmNode signal, List<List<LookAhead.TargetReached>> junctionsPerDirection,
      boolean oneDirectionUnknown) {
    this.signal = signal;
    this.junctionsPerDirection = junctionsPerDirection;
    this.oneDirectionUnknown = oneDirectionUnknown;
  }
}

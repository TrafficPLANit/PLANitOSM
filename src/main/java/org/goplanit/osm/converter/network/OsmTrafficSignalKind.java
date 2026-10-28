package org.goplanit.osm.converter.network;

/**
 * The kind of traffic signal a node is handled as when the user overrides it, see
 * {@link OsmIntersectionSettings#overrideTrafficSignalKind(long, OsmTrafficSignalKind)}, in place of what its tags say
 *
 * @author markr
 */
public enum OsmTrafficSignalKind {

  /** a signal controlling vehicles at a junction, on its node or at a stop line before it, as tagged
   * highway=traffic_signals */
  VEHICLE_SIGNAL,

  /** a signal of a signalised crossing, as tagged crossing=traffic_signals */
  CROSSING_SIGNAL,

  /** no signal at all, whatever the node's tags */
  NOT_A_SIGNAL
}

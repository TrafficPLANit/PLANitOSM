package org.goplanit.osm.converter.network.data;

/**
 * Why the settings skip a road or rail way, so it gets no link: kept for the ways a turn restriction may refer to
 *
 * @author markr
 */
public enum OsmWaySkipReason {

  /** the settings exclude the way by its OSM way id */
  WAY_EXCLUDED_BY_ID,

  /** the settings do not activate the way's type, such as a footway in a network for road traffic */
  WAY_TYPE_NOT_ACTIVATED,

  /** no activated mode may use the way, its access tags denying each of them, such as a service road tagged
   * access=private */
  NO_ACTIVATED_MODE_ON_WAY
}

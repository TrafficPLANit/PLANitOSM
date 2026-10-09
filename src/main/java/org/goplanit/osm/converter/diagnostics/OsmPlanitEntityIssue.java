package org.goplanit.osm.converter.diagnostics;

/**
 * What can become of a PLANit entity derived from OSM after it is created, or be found wrong with it once the network
 * is complete
 *
 * @author markr
 */
public enum OsmPlanitEntityIssue {

  /** an intersection removed again when pruning dangling subnetworks, its infrastructure unusable by any mode */
  INTERSECTION_LOST_BY_PRUNING("intersections removed by pruning"),

  /** an approach no motorised road or rail mode can use, e.g. a footway approaching a crossing */
  APPROACH_WITHOUT_CONTROLLED_TRAFFIC("approaches no motorised road or rail mode can use"),

  /** an intersection without an approach a motorised road or rail mode can use, e.g. a crossing between paths */
  INTERSECTION_WITHOUT_CONTROLLED_APPROACH("intersections without an approach a motorised road or rail mode can use");

  /** label used when logging */
  private final String label;

  /**
   * Constructor
   *
   * @param label used when logging
   */
  OsmPlanitEntityIssue(String label) {
    this.label = label;
  }

  /**
   * Collect the label used when logging
   *
   * @return label
   */
  public String getLabel() {
    return label;
  }
}

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
  INTERSECTION_WITHOUT_CONTROLLED_APPROACH("intersections without an approach a motorised road or rail mode can use"),

  /** an intersection replaced by consolidation with the spatial intersection of its junction */
  INTERSECTION_REPLACED_BY_CONSOLIDATION("intersections replaced by a spatial intersection by consolidation"),

  /** an intersection cluster with more parsed intersections than consolidation subdivides, kept as it was; recorded
   * with its intersection of lowest id */
  INTERSECTION_CLUSTER_TOO_LARGE_TO_SUBDIVIDE("intersection clusters too large to subdivide, kept as they were"),

  /** a node made a member of an intersection by consolidation, without a signal of its own */
  NODE_JOINED_BY_CONSOLIDATION("nodes joined to an intersection by consolidation"),

  /** an approach made internal by consolidation, now starting at a member node, e.g. a stop line between the
   * carriageways of a divided road */
  APPROACH_MADE_INTERNAL_BY_CONSOLIDATION("approaches made internal by consolidation"),

  /** an internal link longer than consolidation's warning length */
  INTERNAL_LINK_LONGER_THAN_WARNING_LENGTH("internal links longer than the warning length"),

  /** a link made internal by consolidation, because road traffic only reaches it by driving through the intersection */
  LINK_REACHED_ONLY_THROUGH_INTERSECTION("links made internal, reached only through their intersection"),

  /** a crossing intersection joined to a junction by consolidation, along the road */
  CROSSING_JOINED_TO_JUNCTION("crossings joined to a junction"),

  /** a crossing intersection on a slip lane joined to its junction by consolidation */
  CROSSING_ON_SLIP_LANE_JOINED_TO_JUNCTION("crossings on a slip lane joined to a junction"),

  /** a road segment entering a signalised junction that is not an approach, counted by consolidation, not added */
  MISSING_APPROACH("road segments entering a signalised junction that are not approaches"),

  /** a signalised junction where fewer than half of the road approaches have a signal of their own */
  JUNCTION_WITH_FEW_LIT_ARMS("signalised junctions where fewer than half of the road approaches have a signal");

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

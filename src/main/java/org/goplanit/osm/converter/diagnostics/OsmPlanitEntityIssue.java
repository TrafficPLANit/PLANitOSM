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

  /** an intersection removed once every signal is handled, for having no approach a motorised road or rail mode can
   * use, e.g. a crossing between paths */
  INTERSECTION_REMOVED_WITHOUT_CONTROLLED_APPROACH(
      "intersections removed for having no approach a motorised road or rail mode can use"),

  /** an intersection replaced by consolidation with the spatial intersection of its junction */
  INTERSECTION_REPLACED_BY_CONSOLIDATION("intersections replaced by a spatial intersection by consolidation"),

  /** an intersection cluster with more parsed intersections than consolidation tries every subdivision for, subdivided
   * step by step instead; recorded with its intersection of lowest id */
  INTERSECTION_CLUSTER_SUBDIVIDED_STEP_BY_STEP(
      "intersection clusters too large to try every subdivision, subdivided step by step"),

  /** a junction pulled into a consolidated intersection whose radius, plus the margin, it lies within */
  JUNCTION_PULLED_IN_WITHIN_RADIUS("junctions pulled into a consolidated intersection whose radius they lie within"),

  /** a junction pulled into a consolidated intersection because the road of one of its approaches without a signal
   * leads back to it */
  JUNCTION_PULLED_IN_TO_SALVAGE_APPROACH_WITHOUT_SIGNAL(
      "junctions pulled into a consolidated intersection to salvage an approach without a signal"),

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

  /** a road segment entering a signalised junction from a slip lane, made an approach without a signal of its own by
   * consolidation; a slip lane often has no signal of its own */
  APPROACH_ON_SLIP_LANE_ADDED_WITHOUT_SIGNAL("approaches on a slip lane added without a signal of their own"),

  /** a road segment entering a signalised junction, not from a slip lane, made an approach without a signal of its own
   * by consolidation; a road entering a signalised junction without a signal is unusual, so a signal may be missing */
  APPROACH_NOT_ON_SLIP_LANE_ADDED_WITHOUT_SIGNAL("approaches not on a slip lane added without a signal of their own"),

  /** a road segment entering a signalised junction, made an approach with a signal by consolidation: it carries on the
   * road of an approach whose signal was tagged on the node it entered, which became a member node */
  APPROACH_ADDED_WITH_SIGNAL_TAGGED_ON_NODE_AHEAD("approaches added with the signal tagged on the node ahead"),

  /** a signalised junction where fewer than half of the road approaches have a signal of their own */
  JUNCTION_WITH_FEW_SIGNALISED_ARMS("signalised junctions where fewer than half of the road approaches have a signal"),

  /** a signalised intersection whose green ratios could not be computed, since its phases leave no usable green time
   * in the cycle; recorded with that usable green time */
  INTERSECTION_GREEN_RATIOS_NOT_COMPUTABLE("signalised intersections whose green ratios could not be computed"),

  /** a node listed to join or to keep apart by its OSM node id that no layer of the network has */
  NODE_LISTED_TO_JOIN_OR_KEEP_APART_NOT_FOUND("nodes listed to join or keep apart, not in the network"),

  /** a node excluded from collapse by its OSM node id that no layer of the network has */
  NODE_EXCLUDED_FROM_COLLAPSE_NOT_FOUND("nodes excluded from collapse, not in the network");

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

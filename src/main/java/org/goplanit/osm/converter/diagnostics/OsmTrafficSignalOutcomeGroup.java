package org.goplanit.osm.converter.diagnostics;

/**
 * Which part of the traffic signal summary an outcome is logged in, and so what its count is a share of. A signal has
 * one outcome per kind it is, vehicle signal or crossing signal, one as a ramp meter, or a single one when it is
 * ignored before its kind matters; notes are recorded alongside those outcomes and are a share of nothing
 *
 * @author markr
 */
public enum OsmTrafficSignalOutcomeGroup {

  /** outcomes of a signal ignored before its kind matters, a share of all signals recorded */
  SIGNAL_OF_ANY_KIND("signals ignored before their kind matters"),

  /** outcomes of a vehicle signal, one per vehicle signal, a share of the vehicle signals */
  VEHICLE_SIGNAL("vehicle signals"),

  /** outcomes of a crossing signal, one per crossing signal, a share of the crossing signals */
  CROSSING_SIGNAL("crossing signals"),

  /** outcomes of a ramp meter, one per ramp meter, a share of the ramp meters */
  RAMP_METER("ramp meters"),

  /** recorded alongside a signal's outcome, counted only */
  NOTE_ALONGSIDE_OUTCOME("noted alongside the outcomes above");

  /** label used as the title of the group's part of the summary */
  private final String label;

  /**
   * Constructor
   *
   * @param label used as the title of the group's part of the summary
   */
  OsmTrafficSignalOutcomeGroup(String label) {
    this.label = label;
  }

  /**
   * Collect the label used as the title of the group's part of the summary
   *
   * @return label
   */
  public String getLabel() {
    return label;
  }
}

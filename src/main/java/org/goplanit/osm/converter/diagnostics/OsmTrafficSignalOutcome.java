package org.goplanit.osm.converter.diagnostics;

/**
 * What became of a recorded traffic signal when integrating it into a network layer
 *
 * @author markr
 */
public enum OsmTrafficSignalOutcome {

  /** a vehicle signal made its junction node an intersection */
  VEHICLE_SIGNAL_ON_JUNCTION_NODE(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "vehicle signals on a junction node"),

  /** a crossing signal made its junction node an intersection */
  CROSSING_SIGNAL_ON_JUNCTION_NODE(
      OsmTrafficSignalOutcomeGroup.CROSSING_SIGNAL, "crossing signals on a junction node"),

  /** a vehicle signal added an approach to at least one junction ahead */
  VEHICLE_SIGNAL_SNAPPED(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "vehicle signals snapped to a junction ahead"),

  /** a vehicle signal found no junction ahead in any direction, nor a signalised crossing within reach, handled as
   * an isolated crossing: its own crossing intersection when breaking links at isolated crossings is on */
  VEHICLE_SIGNAL_WITHOUT_JUNCTION_AHEAD(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "vehicle signals without a junction ahead"),

  /** a vehicle signal found no junction ahead, but a signalised crossing ahead within the maximum distance from a
   * crossing to a junction: the segment arriving there is its approach, and it makes no crossing of its own */
  VEHICLE_SIGNAL_AT_CROSSING_AHEAD(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "vehicle signals joining the signalised crossing ahead"),

  /** a vehicle signal reaches a signalised crossing with no junction within the maximum distance from a crossing to a
   * junction beyond it: the signal is that crossing's stop line, joining it when the crossing has an intersection, and
   * is assigned to no junction past it */
  VEHICLE_SIGNAL_BEFORE_ISOLATED_CROSSING(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL,
      "vehicle signals at the stop line of an isolated signalised crossing"),

  /** a signalised crossing counts as isolated although a junction lies within the maximum distance from a crossing to a
   * junction beyond it, since only service lanes or living streets of lower capacity join the road there; recorded
   * besides the crossing's own outcome */
  CROSSING_ISOLATED_PAST_LOWEST_ORDER_ROAD(
      OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME,
      "crossing signals isolated with only a service lane or living street joining"),

  /** a vehicle signal without a usable direction, no direction tagged or one the OSM ways through it disagree on,
   * reaches targets in more than one direction: it keeps the direction whose first target is nearest; recorded besides
   * its assignment outcome */
  VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN(
      OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME,
      "vehicle signals without a usable direction kept to one direction"),

  /** a vehicle signal found no junction ahead and lies outside the parsed area or within the junction reach of its
   * edge, where the junction it controls is likely cut off: ignored */
  VEHICLE_SIGNAL_AT_EDGE_OF_PARSED_AREA(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL,
      "vehicle signals without a junction ahead at the edge of the parsed area"),

  /** a crossing signal not on a junction node lies within reach of a signalised junction, along one of its approaches,
   * and is part of it: nothing is added and no link is broken */
  CROSSING_PART_OF_SIGNALISED_JUNCTION(
      OsmTrafficSignalOutcomeGroup.CROSSING_SIGNAL, "crossing signals part of a signalised junction nearby"),

  /** an isolated signalised crossing made its own crossing intersection, the link broken at it where needed */
  ISOLATED_CROSSING_OWN_INTERSECTION(
      OsmTrafficSignalOutcomeGroup.CROSSING_SIGNAL, "isolated crossings given their own intersection"),

  /** an isolated signalised crossing left without intersection, breaking links at isolated crossings being off */
  ISOLATED_CROSSING_NO_LINK_BROKEN(
      OsmTrafficSignalOutcomeGroup.CROSSING_SIGNAL, "isolated crossings where no link is broken"),

  /** a vehicle signal whose direction is none of forward, backward or both, e.g. a compass bearing, is handled as
   * untagged */
  VEHICLE_SIGNAL_DIRECTION_UNRECOGNISED(
      OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME,
      "vehicle signals with a direction other than forward, backward or both, taken as untagged"),

  /** a vehicle signal tagged with a direction where OSM ways of opposite orientation meet, so its direction is not
   * usable: it keeps one direction, see {@link #VEHICLE_SIGNAL_ONE_DIRECTION_CHOSEN} */
  DIRECTION_CONFLICT(
      OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME,
      "vehicle signals with a direction the ways disagree on, nearest junction taken"),

  /** a vehicle signal passed over a junction without signals of its own for one further along its road where a
   * road of higher capacity crosses, in at least one direction */
  HIGHER_CLASS_JUNCTION_CHOSEN(
      OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME,
      "vehicle signals passing a junction without signals for a higher class one"),

  /** a signal on a track made the node where the track crosses a road ahead a signalised junction */
  TRACK_SIGNAL_AT_ROAD_CROSSING(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "signals on a track making the road crossing ahead a junction"),

  /** a signal on a cycleway made the node where it meets a road ahead a signalised crossing */
  CYCLEWAY_SIGNAL_AT_ROAD_CROSSING(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "signals on a cycleway making where it meets a road a crossing"),

  /** a signal on a track or cycleway branching before a road, more than one branch reaching a road within reach, made
   * the node where it branches the intersection, no road node joined */
  TRACK_OR_CYCLEWAY_SIGNAL_AT_BRANCH(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL,
      "signals on a track or cycleway branching before a road, making the branch an intersection"),

  /** a signal on a track or cycleway with no road within reach along it is ignored */
  TRACK_OR_CYCLEWAY_SIGNAL_WITHOUT_ROAD_CROSSING(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL, "signals on a track or cycleway without a road in reach"),

  /** a traffic signal on a way that is not a road, track or cycleway, such as a footway, is ignored */
  TRAFFIC_SIGNAL_NOT_ON_ROAD_TRACK_OR_CYCLEWAY(
      OsmTrafficSignalOutcomeGroup.VEHICLE_SIGNAL,
      "traffic signals on a way that is not a road, track or cycleway, ignored"),

  /** a signal controlling no junction or crossing, such as a ramp meter, is ignored */
  SIGNAL_NOT_CONTROLLING_TRAFFIC(
      OsmTrafficSignalOutcomeGroup.SIGNAL_OF_ANY_KIND, "signals controlling no junction or crossing"),

  /** the signal's node is neither a node of the layer nor internal to one of its links */
  SIGNAL_NOT_IN_LAYER(
      OsmTrafficSignalOutcomeGroup.SIGNAL_OF_ANY_KIND, "signals not in the layer");

  /** part of the summary the outcome is logged in */
  private final OsmTrafficSignalOutcomeGroup group;

  /** label used when logging the count */
  private final String label;

  /**
   * Constructor
   *
   * @param group part of the summary the outcome is logged in
   * @param label used when logging the count
   */
  OsmTrafficSignalOutcome(OsmTrafficSignalOutcomeGroup group, String label) {
    this.group = group;
    this.label = label;
  }

  /**
   * Collect the part of the summary the outcome is logged in
   *
   * @return group
   */
  public OsmTrafficSignalOutcomeGroup getGroup() {
    return group;
  }

  /**
   * Collect the label used when logging the count
   *
   * @return label
   */
  public String getLabel() {
    return label;
  }
}

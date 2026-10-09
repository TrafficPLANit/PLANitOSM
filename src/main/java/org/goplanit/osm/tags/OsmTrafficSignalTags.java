package org.goplanit.osm.tags;

import java.util.Map;
import java.util.Set;

import org.goplanit.osm.util.OsmTagUtils;

/**
 * Traffic signal tags, which OSM spreads over several keys: {@code highway=traffic_signals} marks the node,
 * {@code traffic_signals=*} its sub-type and {@code traffic_signals:direction} the direction it applies to, and
 * {@code crossing=traffic_signals} or {@code crossing:signals=yes} a signalised pedestrian crossing; with the checks
 * for which kind of traffic signal a node's tags mark
 *
 * @author markr
 */
public class OsmTrafficSignalTags {

  /** value of {@code highway=traffic_signals}, a node with traffic signals */
  public static final String TRAFFIC_SIGNALS = "traffic_signals";

  /** key limiting the direction a traffic signal applies to, e.g. {@code traffic_signals:direction=forward} */
  public static final String TRAFFIC_SIGNALS_DIRECTION =
      OsmTagUtils.createCompositeOsmKey(TRAFFIC_SIGNALS, OsmDirectionTags.DIRECTION);

  /** key {@code crossing}, of which {@code crossing=traffic_signals} marks a signalised pedestrian crossing */
  public static final String CROSSING = OsmPedestrianTags.CROSSING;

  /** value of {@code crossing=traffic_signals}, a crossing controlled by traffic signals */
  public static final String CROSSING_TRAFFIC_SIGNALS = TRAFFIC_SIGNALS;

  /** sub key of {@code crossing}, stating whether a crossing has traffic signals */
  public static final String SIGNALS = "signals";

  /** key stating whether a crossing has traffic signals, e.g. {@code crossing:signals=yes} */
  public static final String CROSSING_SIGNALS = OsmTagUtils.createCompositeOsmKey(CROSSING, SIGNALS);

  /** value of {@code traffic_signals=pedestrian_crossing}, signals of a signalised pedestrian crossing */
  public static final String PEDESTRIAN_CROSSING_TRAFFIC_SIGNALS = "pedestrian_crossing";

  /** value of {@code traffic_signals=crossing}, signals of a signalised crossing */
  public static final String CROSSING_TYPE_TRAFFIC_SIGNALS = CROSSING;

  /** value of {@code traffic_signals=ramp_meter}, a signal metering traffic onto a motorway */
  public static final String RAMP_METER = "ramp_meter";

  /** value of {@code traffic_signals=emergency}, a signal stopping traffic for emergency vehicles, e.g. a fire
   * station */
  public static final String EMERGENCY = "emergency";

  /** value of {@code traffic_signals=blinker}, a flashing signal that controls nothing */
  public static final String BLINKER = "blinker";

  /** value of {@code traffic_signals=gate}, signals at a gate */
  public static final String GATE = "gate";

  /** value of {@code traffic_signals=control_box}, the cabinet controlling traffic signals rather than a signal */
  public static final String CONTROL_BOX = "control_box";

  /** value of {@code traffic_signals=level_crossing}, signals at a railway level crossing */
  public static final String LEVEL_CROSSING = "level_crossing";

  /** values of {@code traffic_signals=*} on a {@code highway=traffic_signals} node that mark a signalised pedestrian
   * crossing rather than a vehicle signal */
  public static final Set<String> CROSSING_TRAFFIC_SIGNAL_TYPES =
      Set.of(PEDESTRIAN_CROSSING_TRAFFIC_SIGNALS, CROSSING_TYPE_TRAFFIC_SIGNALS);

  /** values of {@code traffic_signals=*} on a {@code highway=traffic_signals} node marking signals that control no
   * junction or crossing, such as ramp meters and fire station signals; such signals are ignored */
  public static final Set<String> NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES =
      Set.of(RAMP_METER, EMERGENCY, BLINKER, GATE, CONTROL_BOX, LEVEL_CROSSING);

  /** Verify if the tags carry {@code highway=traffic_signals} with a {@code traffic_signals=*} value in the given set
   *
   * @param tags to check
   * @param subTypes values to check against
   * @return true when such a value is present, false otherwise
   */
  private static boolean hasTrafficSignalsSubTypeIn(Map<String, String> tags, Set<String> subTypes) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, TRAFFIC_SIGNALS) &&
        OsmTagUtils.keyMatchesAnyValueTag(tags, TRAFFIC_SIGNALS, subTypes.toArray(String[]::new));
  }

  /** Verify if the tags mark a vehicle signal: {@code highway=traffic_signals}, unless its {@code traffic_signals=*}
   * value marks a crossing or a signal controlling no junction or crossing
   *
   * @param tags to check
   * @return true when a vehicle signal, false otherwise
   */
  public static boolean isVehicleTrafficSignal(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, TRAFFIC_SIGNALS) &&
        !hasTrafficSignalsSubTypeIn(tags, CROSSING_TRAFFIC_SIGNAL_TYPES) &&
        !hasTrafficSignalsSubTypeIn(tags, NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES);
  }

  /** Verify if the tags mark a crossing signal, a signalised pedestrian crossing: {@code crossing=traffic_signals},
   * {@code crossing:signals=yes}, or {@code highway=traffic_signals} with a {@code traffic_signals=*} value in
   * {@link #CROSSING_TRAFFIC_SIGNAL_TYPES}
   *
   * @param tags to check
   * @return true when a crossing signal, false otherwise
   */
  public static boolean isCrossingTrafficSignals(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, CROSSING, CROSSING_TRAFFIC_SIGNALS) ||
        OsmTagUtils.keyMatchesAnyValueTag(tags, CROSSING_SIGNALS, OsmTags.YES) ||
        hasTrafficSignalsSubTypeIn(tags, CROSSING_TRAFFIC_SIGNAL_TYPES);
  }

  /** Verify if the tags mark a signal that controls no junction or crossing: {@code highway=traffic_signals} with a
   * {@code traffic_signals=*} value in {@link #NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES}
   *
   * @param tags to check
   * @return true when such a signal, false otherwise
   */
  public static boolean isNonControllingTrafficSignals(Map<String, String> tags) {
    return hasTrafficSignalsSubTypeIn(tags, NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES);
  }

  /** Verify if the tags mark a traffic signal of any kind: a vehicle signal, a crossing signal, or a signal
   * controlling no junction or crossing, which is recorded so what becomes of it can be accounted for
   *
   * @param tags to check
   * @return true when a traffic signal of any kind, false otherwise
   */
  public static boolean isTrafficSignals(Map<String, String> tags) {
    return isVehicleTrafficSignal(tags) || isCrossingTrafficSignals(tags) || isNonControllingTrafficSignals(tags);
  }
}

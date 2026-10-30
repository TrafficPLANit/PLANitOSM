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

  /** Verify if the tags carry a {@code highway=*} value of a way that can be a crossing way, see
   * {@link #CROSSING_WAY_HIGHWAY_TYPES}
   *
   * @param tags of the OSM way to check
   * @return true when such a value is present, false otherwise
   */
  private static boolean hasCrossingWayHighwayType(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(
        tags, OsmHighwayTags.HIGHWAY, CROSSING_WAY_HIGHWAY_TYPES.toArray(String[]::new));
  }

  /** Verify if the tags mark a traffic signal on a barrier, such as a bollard or gate: {@code highway=traffic_signals}
   * with a {@code barrier=*} value other than {@code kerb} or {@code no}. It holds traffic at the barrier
   *
   * @param tags to check
   * @return true when a traffic signal on a barrier, false otherwise
   */
  private static boolean isTrafficSignalOnBarrier(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, TRAFFIC_SIGNALS) &&
        tags.containsKey(BARRIER) && !OsmTagUtils.keyMatchesAnyValueTag(tags, BARRIER, KERB, OsmTags.NO);
  }

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

  /** value of {@code crossing=uncontrolled}, a crossing without signals */
  public static final String UNCONTROLLED = "uncontrolled";

  /** value of {@code crossing=unmarked}, a crossing without markings or signals */
  public static final String UNMARKED = "unmarked";

  /** value of {@code crossing=zebra}, a zebra crossing, without signals */
  public static final String ZEBRA = "zebra";

  /** value of {@code crossing=informal}, a place people cross without any crossing facilities */
  public static final String INFORMAL = "informal";

  /** values of {@code highway=*} of a way that can be a crossing way, crossing a road where it meets it */
  public static final Set<String> CROSSING_WAY_HIGHWAY_TYPES = Set.of(OsmHighwayTags.FOOTWAY, OsmHighwayTags.CYCLEWAY,
      OsmHighwayTags.PATH, OsmHighwayTags.BRIDLEWAY, OsmHighwayTags.PEDESTRIAN);

  /** values of {@code crossing=*} stating a crossing has no signals */
  public static final Set<String> CROSSING_VALUES_WITHOUT_SIGNALS =
      Set.of(UNCONTROLLED, UNMARKED, ZEBRA, INFORMAL, OsmTags.NO);

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
   * junction or crossing, such as fire station signals; such signals are ignored. A ramp meter is a kind of its own,
   * see {@link #isRampMeter(Map)} */
  public static final Set<String> NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES =
      Set.of(EMERGENCY, BLINKER, GATE, CONTROL_BOX, LEVEL_CROSSING);

  /** key {@code barrier}, a barrier on a node, such as a bollard or gate */
  public static final String BARRIER = "barrier";

  /** value of {@code barrier=kerb}, a kerb, common on crossing nodes, which holds no traffic */
  public static final String KERB = "kerb";

  /** value of {@code highway=stop}, a node where a stop sign stands */
  public static final String STOP = "stop";

  /** value of {@code highway=give_way}, a node where a give way sign stands */
  public static final String GIVE_WAY = "give_way";

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
   * value marks a crossing, a ramp meter or a signal controlling no junction or crossing
   *
   * @param tags to check
   * @return true when a vehicle signal, false otherwise
   */
  public static boolean isVehicleTrafficSignal(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, TRAFFIC_SIGNALS) &&
        !hasTrafficSignalsSubTypeIn(tags, CROSSING_TRAFFIC_SIGNAL_TYPES) && !isRampMeter(tags) &&
        !hasTrafficSignalsSubTypeIn(tags, NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES);
  }

  /** Verify if the tags mark a crossing signal, a signalised pedestrian crossing: {@code crossing=traffic_signals},
   * {@code crossing:signals=yes}, or {@code highway=traffic_signals} with a {@code traffic_signals=*} value in
   * {@link #CROSSING_TRAFFIC_SIGNAL_TYPES}. A traffic signal on a barrier, such as a bollard or gate, is one too, since
   * it holds traffic at the barrier as a crossing does; being a vehicle signal as well, it has both kinds
   *
   * @param tags to check
   * @return true when a crossing signal, false otherwise
   */
  public static boolean isCrossingTrafficSignals(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(tags, CROSSING, CROSSING_TRAFFIC_SIGNALS) ||
        OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(tags, CROSSING_SIGNALS, OsmTags.YES) ||
        hasTrafficSignalsSubTypeIn(tags, CROSSING_TRAFFIC_SIGNAL_TYPES) || isTrafficSignalOnBarrier(tags);
  }

  /** Verify if the tags mark a signalised crossing way: a footway, cycleway, path, bridleway or pedestrian way, see
   * {@link #CROSSING_WAY_HIGHWAY_TYPES}, tagged {@code crossing=traffic_signals} or {@code crossing:signals=yes}
   *
   * @param tags of the OSM way to check
   * @return true when a signalised crossing way, false otherwise
   */
  public static boolean isSignalisedCrossingWay(Map<String, String> tags) {
    return hasCrossingWayHighwayType(tags) &&
        (OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(tags, CROSSING, CROSSING_TRAFFIC_SIGNALS) ||
            OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(tags, CROSSING_SIGNALS, OsmTags.YES));
  }

  /** Verify if the tags mark a way that can cross a road where it meets it, whatever its crossing tags: a footway,
   * cycleway, path, bridleway or pedestrian way, unless it is an area, {@code area=yes}, such as a plaza's outline
   *
   * @param tags of the OSM way to check
   * @return true when such a way, false otherwise
   */
  public static boolean isWayThatCanCrossRoad(Map<String, String> tags) {
    return hasCrossingWayHighwayType(tags) && !OsmTags.isArea(tags);
  }

  /** Verify if the tags mark a crossing node: {@code highway=crossing}, whatever its other crossing tags
   *
   * @param tags to check
   * @return true when a crossing node, false otherwise
   */
  public static boolean isCrossingNode(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, CROSSING);
  }

  /** Verify if the tags mark a stop or give way sign: {@code highway=stop} or {@code highway=give_way}
   *
   * @param tags to check
   * @return true when a stop or give way sign, false otherwise
   */
  public static boolean isStopOrGiveWaySign(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, STOP, GIVE_WAY);
  }

  /** Verify if the tags state the crossing has no signals: {@code crossing:signals=no}, or a {@code crossing=*} value
   * in {@link #CROSSING_VALUES_WITHOUT_SIGNALS}. {@code crossing=marked} says nothing about signals
   *
   * @param tags to check
   * @return true when the tags state the crossing has no signals, false otherwise
   */
  public static boolean isCrossingWithoutSignals(Map<String, String> tags) {
    return OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(tags, CROSSING_SIGNALS, OsmTags.NO) ||
        OsmTagUtils.keyMatchesAnyValueTagAmongMultipleValues(
            tags, CROSSING, CROSSING_VALUES_WITHOUT_SIGNALS.toArray(String[]::new));
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

  /** Verify if the tags mark a ramp meter: {@code highway=traffic_signals} with {@code traffic_signals=ramp_meter}, a
   * signal holding traffic back on its own, letting vehicles onto a motorway one or two at a time
   *
   * @param tags to check
   * @return true when a ramp meter, false otherwise
   */
  public static boolean isRampMeter(Map<String, String> tags) {
    return hasTrafficSignalsSubTypeIn(tags, Set.of(RAMP_METER));
  }

  /** Verify if the tags mark a traffic signal of any kind: a vehicle signal, a crossing signal, a ramp meter, or a
   * signal controlling no junction or crossing, which is recorded so what becomes of it can be accounted for
   *
   * @param tags to check
   * @return true when a traffic signal of any kind, false otherwise
   */
  public static boolean isTrafficSignals(Map<String, String> tags) {
    return isVehicleTrafficSignal(tags) || isCrossingTrafficSignals(tags) || isRampMeter(tags) ||
        isNonControllingTrafficSignals(tags);
  }

  /** The direction a signal applies to, relative to its OSM way: a {@code traffic_signals:direction} of forward,
   * backward or both. Any other value, such as a compass bearing, is not recognised
   *
   * @param tags of the signal
   * @return the direction tagged, null when not tagged or not recognised
   */
  public static String getRecognisedDirection(Map<String, String> tags) {
    var direction = tags.get(TRAFFIC_SIGNALS_DIRECTION);
    return OsmDirectionTags.isForwardOrBackward(direction) || OsmDirectionTags.BOTH.equals(direction) ? direction : null;
  }
}

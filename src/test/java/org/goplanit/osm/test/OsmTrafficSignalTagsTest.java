package org.goplanit.osm.test;

import java.util.List;
import java.util.Map;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests which OSM tagging marks a vehicle signal or a crossing signal, and that recorded traffic signals are kept in
 * OSM node id order
 *
 * @author markr
 */
public class OsmTrafficSignalTagsTest {

  /** tags from alternating keys and values */
  private static Map<String, String> tags(String... keyValues) {
    var tags = new java.util.HashMap<String, String>();
    for (int i = 0; i < keyValues.length; i += 2) {
      tags.put(keyValues[i], keyValues[i + 1]);
    }
    return tags;
  }

  @Test
  public void carTrafficSignal() {
    var tags = tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS);

    assertTrue(OsmTrafficSignalTags.isVehicleTrafficSignal(tags));
    assertFalse(OsmTrafficSignalTags.isCrossingTrafficSignals(tags));
    assertTrue(OsmTrafficSignalTags.isTrafficSignals(tags));
  }

  @Test
  public void crossingSignal() {
    for (var tags : List.of(
        tags(OsmTrafficSignalTags.CROSSING, OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNALS),
        tags(OsmTrafficSignalTags.CROSSING_SIGNALS, OsmTags.YES))) {
      assertTrue(OsmTrafficSignalTags.isCrossingTrafficSignals(tags));
      assertFalse(OsmTrafficSignalTags.isVehicleTrafficSignal(tags));
      assertTrue(OsmTrafficSignalTags.isTrafficSignals(tags));
    }
  }

  @Test
  public void bothKinds() {
    var tags = tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS,
        OsmTrafficSignalTags.CROSSING, OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNALS);

    assertTrue(OsmTrafficSignalTags.isVehicleTrafficSignal(tags));
    assertTrue(OsmTrafficSignalTags.isCrossingTrafficSignals(tags));
  }

  /** A vehicle signal tagged as a pedestrian crossing is a crossing signal */
  @Test
  public void crossingSubTypeIsCrossingSignal() {
    for (var subType : OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNAL_TYPES) {
      var tags = tags(
          OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.TRAFFIC_SIGNALS, subType);
      assertTrue(OsmTrafficSignalTags.isCrossingTrafficSignals(tags), subType);
      assertFalse(OsmTrafficSignalTags.isVehicleTrafficSignal(tags), subType);
    }
  }

  /** Signals controlling no junction or crossing, such as ramp meters, are neither kind, but still recorded */
  @Test
  public void nonControllingSubTypeIsNeitherKind() {
    for (var subType : OsmTrafficSignalTags.NON_CONTROLLING_TRAFFIC_SIGNAL_TYPES) {
      var tags = tags(
          OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.TRAFFIC_SIGNALS, subType);
      assertFalse(OsmTrafficSignalTags.isVehicleTrafficSignal(tags), subType);
      assertFalse(OsmTrafficSignalTags.isCrossingTrafficSignals(tags), subType);
      assertTrue(OsmTrafficSignalTags.isNonControllingTrafficSignals(tags), subType);
      assertTrue(OsmTrafficSignalTags.isTrafficSignals(tags), subType);
    }
  }

  /** Any other sub-type, or an unknown one, is a vehicle signal */
  @Test
  public void otherSubTypesAreVehicleSignals() {
    for (var subType : List.of("signal", "traffic_lights", "stop_line", "continuous_green", "taxi_priority", "unknown")) {
      var tags = tags(
          OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.TRAFFIC_SIGNALS, subType);
      assertTrue(OsmTrafficSignalTags.isVehicleTrafficSignal(tags), subType);
    }
  }

  @Test
  public void otherTaggingIsNoTrafficSignal() {
    for (var tags : List.of(
        tags(OsmHighwayTags.HIGHWAY, "stop"),
        tags(OsmHighwayTags.HIGHWAY, "give_way"),
        tags(OsmTrafficSignalTags.CROSSING, "zebra"),
        tags(OsmTrafficSignalTags.CROSSING_SIGNALS, OsmTags.NO),
        tags())) {
      assertFalse(OsmTrafficSignalTags.isTrafficSignals(tags), tags.toString());
    }
  }
}

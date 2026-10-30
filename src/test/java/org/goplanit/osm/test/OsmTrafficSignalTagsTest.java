package org.goplanit.osm.test;

import java.util.List;
import java.util.Map;

import org.goplanit.osm.tags.OsmDirectionTags;
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

  /** A crossing value listing several values, separated by semicolons, is a crossing signal when one of them is */
  @Test
  public void crossingSignalAmongSeveralValues() {
    for (var value : List.of("traffic_signals;marked", "marked;traffic_signals", "marked; traffic_signals")) {
      assertTrue(OsmTrafficSignalTags.isCrossingTrafficSignals(tags(OsmTrafficSignalTags.CROSSING, value)), value);
    }
    assertFalse(OsmTrafficSignalTags.isCrossingTrafficSignals(tags(OsmTrafficSignalTags.CROSSING, "marked;zebra")));
  }

  /** A footway, cycleway or path tagged with crossing signals is a signalised crossing way, a road is not */
  @Test
  public void signalisedCrossingWay() {
    for (var highway : List.of(OsmHighwayTags.FOOTWAY, OsmHighwayTags.CYCLEWAY, OsmHighwayTags.PATH)) {
      assertTrue(OsmTrafficSignalTags.isSignalisedCrossingWay(tags(OsmHighwayTags.HIGHWAY, highway,
          OsmTrafficSignalTags.CROSSING, OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNALS)), highway);
      assertTrue(OsmTrafficSignalTags.isSignalisedCrossingWay(tags(OsmHighwayTags.HIGHWAY, highway,
          OsmTrafficSignalTags.CROSSING_SIGNALS, OsmTags.YES)), highway);
      assertFalse(OsmTrafficSignalTags.isSignalisedCrossingWay(tags(OsmHighwayTags.HIGHWAY, highway,
          OsmTrafficSignalTags.CROSSING, "zebra")), highway);
    }
    assertFalse(OsmTrafficSignalTags.isSignalisedCrossingWay(tags(OsmHighwayTags.HIGHWAY, OsmHighwayTags.PRIMARY,
        OsmTrafficSignalTags.CROSSING, OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNALS)));
  }

  /** Crossing values stating the crossing has no signals, while marked or no crossing value says nothing about them */
  @Test
  public void crossingWithoutSignals() {
    for (var tags : List.of(
        tags(OsmTrafficSignalTags.CROSSING, "zebra"),
        tags(OsmTrafficSignalTags.CROSSING, "uncontrolled"),
        tags(OsmTrafficSignalTags.CROSSING, "unmarked"),
        tags(OsmTrafficSignalTags.CROSSING, "informal"),
        tags(OsmTrafficSignalTags.CROSSING, OsmTags.NO),
        tags(OsmTrafficSignalTags.CROSSING_SIGNALS, OsmTags.NO))) {
      assertTrue(OsmTrafficSignalTags.isCrossingWithoutSignals(tags), tags.toString());
    }
    for (var tags : List.of(
        tags(OsmTrafficSignalTags.CROSSING, "marked"),
        tags(OsmTrafficSignalTags.CROSSING, OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNALS),
        tags(OsmHighwayTags.HIGHWAY, "crossing"),
        tags())) {
      assertFalse(OsmTrafficSignalTags.isCrossingWithoutSignals(tags), tags.toString());
    }
  }

  @Test
  public void bothKinds() {
    var tags = tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS,
        OsmTrafficSignalTags.CROSSING, OsmTrafficSignalTags.CROSSING_TRAFFIC_SIGNALS);

    assertTrue(OsmTrafficSignalTags.isVehicleTrafficSignal(tags));
    assertTrue(OsmTrafficSignalTags.isCrossingTrafficSignals(tags));
  }

  /** A traffic signal on a barrier, such as a bollard or gate, is both kinds, since it holds traffic at the barrier;
   * one on a kerb, or tagged barrier=no, is a vehicle signal only */
  @Test
  public void signalOnBarrierIsBothKinds() {
    for (var barrier : List.of("bollard", "gate")) {
      var tags = tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.BARRIER,
          barrier);
      assertTrue(OsmTrafficSignalTags.isVehicleTrafficSignal(tags), barrier);
      assertTrue(OsmTrafficSignalTags.isCrossingTrafficSignals(tags), barrier);
    }
    for (var barrier : List.of(OsmTrafficSignalTags.KERB, OsmTags.NO)) {
      var tags = tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.BARRIER,
          barrier);
      assertTrue(OsmTrafficSignalTags.isVehicleTrafficSignal(tags), barrier);
      assertFalse(OsmTrafficSignalTags.isCrossingTrafficSignals(tags), barrier);
    }
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

  /** A ramp meter is a signal of its own kind: no vehicle signal, no crossing signal and no signal controlling no
   * traffic, but still recorded. A plain vehicle signal is no ramp meter */
  @Test
  public void rampMeterIsItsOwnKind() {
    var tags = tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS,
        OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.RAMP_METER);
    assertTrue(OsmTrafficSignalTags.isRampMeter(tags));
    assertFalse(OsmTrafficSignalTags.isVehicleTrafficSignal(tags));
    assertFalse(OsmTrafficSignalTags.isCrossingTrafficSignals(tags));
    assertFalse(OsmTrafficSignalTags.isNonControllingTrafficSignals(tags));
    assertTrue(OsmTrafficSignalTags.isTrafficSignals(tags));
    assertFalse(OsmTrafficSignalTags.isRampMeter(tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS)));
  }

  /** Signals controlling no junction or crossing, such as fire station signals, are neither kind, but still recorded */
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

  /** a signal's direction is recognised when forward, backward or both, not when e.g. a compass bearing or untagged */
  @Test
  public void recognisedDirectionIsForwardBackwardOrBoth() {
    for (var direction : List.of(OsmDirectionTags.FORWARD, OsmDirectionTags.BACKWARD, OsmDirectionTags.BOTH)) {
      assertEquals(direction, OsmTrafficSignalTags.getRecognisedDirection(
          tags(OsmTrafficSignalTags.TRAFFIC_SIGNALS_DIRECTION, direction)));
    }
    assertNull(OsmTrafficSignalTags.getRecognisedDirection(tags(OsmTrafficSignalTags.TRAFFIC_SIGNALS_DIRECTION, "45")));
    assertNull(OsmTrafficSignalTags.getRecognisedDirection(
        tags(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS)));
  }
}

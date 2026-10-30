package org.goplanit.osm.converter.network;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidatorSettings;
import org.junit.jupiter.api.Test;

/**
 * Tests the intersection settings of the OSM network reader: active by default with the defaults intersections are
 * parsed with, switched on and off like the highway, railway and waterway settings, and reset to their defaults
 *
 * @author markr
 */
public class OsmIntersectionSettingsTest {

  /**
   * The intersection settings are active by default: a vehicle signal controls a junction at most 50 m away, a
   * signalised crossing at most 30 m from a junction is part of it, links are not broken at isolated signalised
   * crossings, intersections are not consolidated and no mode is collapsed
   */
  @Test
  public void intersectionSettingsAreActiveByDefaultWithTheirDefaults() {
    var settings = new OsmNetworkReaderSettings(CountryNames.AUSTRALIA);
    var intersectionSettings = settings.getIntersectionSettings();

    assertTrue(settings.isIntersectionParserActive());
    assertTrue(intersectionSettings.isParserActive());
    assertEquals(50, intersectionSettings.getMaximumDistanceVehicleSignalToJunctionInMeters());
    assertEquals(30, intersectionSettings.getMaximumDistanceSignalisedCrossingToJunctionInMeters());
    assertFalse(intersectionSettings.isBreakLinksAtIsolatedSignals());
    assertFalse(intersectionSettings.isConsolidateIntersections());
    assertFalse(intersectionSettings.getIntersectionCollapserSettings().hasCollapsedModes());
  }

  /** Deactivating the intersection parser deactivates the intersection settings it returns, and activating it again
   * switches them back on */
  @Test
  public void activateIntersectionParserSwitchesTheSettingsOnAndOff() {
    var settings = new OsmNetworkReaderSettings(CountryNames.AUSTRALIA);

    var intersectionSettings = settings.activateIntersectionParser(false);

    assertSame(settings.getIntersectionSettings(), intersectionSettings);
    assertFalse(settings.isIntersectionParserActive());
    assertFalse(intersectionSettings.isParserActive());
    settings.activateIntersectionParser(true);
    assertTrue(settings.isIntersectionParserActive());
  }

  /** Reset puts every intersection setting back to its default, including the consolidation and collapse settings */
  @Test
  public void resetRestoresTheDefaults() {
    var intersectionSettings = new OsmNetworkReaderSettings(CountryNames.AUSTRALIA).getIntersectionSettings();
    intersectionSettings.activateParser(false);
    intersectionSettings.setMaximumDistanceVehicleSignalToJunctionInMeters(80);
    intersectionSettings.setBreakLinksAtIsolatedSignals(true);
    intersectionSettings.setConsolidateIntersections(true);
    intersectionSettings.getIntersectionConsolidatorSettings().setMaximumJoiningReachMeters(60);
    intersectionSettings.getIntersectionCollapserSettings().setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);

    intersectionSettings.reset();

    assertTrue(intersectionSettings.isParserActive());
    assertEquals(50, intersectionSettings.getMaximumDistanceVehicleSignalToJunctionInMeters());
    assertFalse(intersectionSettings.isBreakLinksAtIsolatedSignals());
    assertFalse(intersectionSettings.isConsolidateIntersections());
    assertEquals(IntersectionConsolidatorSettings.DEFAULT_MAXIMUM_JOINING_REACH_METERS,
        intersectionSettings.getIntersectionConsolidatorSettings().getMaximumJoiningReachMeters());
    assertFalse(intersectionSettings.getIntersectionCollapserSettings().hasCollapsedModes());
  }

  /** No traffic signal direction is overridden by default; a direction overridden is kept per OSM node id, overriding
   * it again replaces it, and reset clears them */
  @Test
  public void trafficSignalDirectionOverridesAreKeptAndClearedByReset() {
    var intersectionSettings = new OsmNetworkReaderSettings(CountryNames.AUSTRALIA).getIntersectionSettings();
    assertTrue(intersectionSettings.getTrafficSignalDirectionOverrides().isEmpty());

    intersectionSettings.overrideTrafficSignalDirection(12, OsmTrafficSignalDirection.FORWARD);
    intersectionSettings.overrideTrafficSignalDirection(13, OsmTrafficSignalDirection.FORWARD);
    intersectionSettings.overrideTrafficSignalDirection(13, OsmTrafficSignalDirection.BACKWARD);

    assertEquals(OsmTrafficSignalDirection.FORWARD, intersectionSettings.getTrafficSignalDirectionOverride(12));
    assertEquals(OsmTrafficSignalDirection.BACKWARD, intersectionSettings.getTrafficSignalDirectionOverride(13));
    assertNull(intersectionSettings.getTrafficSignalDirectionOverride(14));
    intersectionSettings.reset();
    assertTrue(intersectionSettings.getTrafficSignalDirectionOverrides().isEmpty());
  }

  /** No traffic signal kind is overridden by default; a kind overridden is kept per OSM node id, overriding it again
   * replaces it, the node ids with any override are those of both kinds of override, each once, and reset clears
   * them */
  @Test
  public void trafficSignalKindOverridesAreKeptAndClearedByReset() {
    var intersectionSettings = new OsmNetworkReaderSettings(CountryNames.AUSTRALIA).getIntersectionSettings();
    assertTrue(intersectionSettings.getTrafficSignalKindOverrides().isEmpty());

    intersectionSettings.overrideTrafficSignalKind(12, OsmTrafficSignalKind.VEHICLE_SIGNAL);
    intersectionSettings.overrideTrafficSignalKind(13, OsmTrafficSignalKind.VEHICLE_SIGNAL);
    intersectionSettings.overrideTrafficSignalKind(13, OsmTrafficSignalKind.NOT_A_SIGNAL);
    intersectionSettings.overrideTrafficSignalDirection(13, OsmTrafficSignalDirection.FORWARD);
    intersectionSettings.overrideTrafficSignalDirection(14, OsmTrafficSignalDirection.FORWARD);

    assertEquals(OsmTrafficSignalKind.VEHICLE_SIGNAL, intersectionSettings.getTrafficSignalKindOverride(12));
    assertEquals(OsmTrafficSignalKind.NOT_A_SIGNAL, intersectionSettings.getTrafficSignalKindOverride(13));
    assertNull(intersectionSettings.getTrafficSignalKindOverride(14));
    assertEquals(Set.of(12L, 13L, 14L), intersectionSettings.getOsmNodeIdsWithTrafficSignalOverride());
    intersectionSettings.reset();
    assertTrue(intersectionSettings.getTrafficSignalKindOverrides().isEmpty());
    assertTrue(intersectionSettings.getOsmNodeIdsWithTrafficSignalOverride().isEmpty());
  }

  /** A node tagged as a ramp meter is handled as one; a node overridden as a ramp meter is one whatever its tags, and
   * a node tagged as a ramp meter but overridden as another kind is that kind */
  @Test
  public void rampMeterKindFromTagsUnlessOverridden() {
    var intersectionSettings = new OsmNetworkReaderSettings(CountryNames.AUSTRALIA).getIntersectionSettings();
    var rampMeterTags = Map.of(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS,
        OsmTrafficSignalTags.TRAFFIC_SIGNALS, OsmTrafficSignalTags.RAMP_METER);
    var vehicleSignalTags = Map.of(OsmHighwayTags.HIGHWAY, OsmTrafficSignalTags.TRAFFIC_SIGNALS);
    intersectionSettings.overrideTrafficSignalKind(2, OsmTrafficSignalKind.RAMP_METER);
    intersectionSettings.overrideTrafficSignalKind(3, OsmTrafficSignalKind.VEHICLE_SIGNAL);

    assertTrue(intersectionSettings.isHandledAsTrafficSignalOfKind(1, rampMeterTags, OsmTrafficSignalKind.RAMP_METER));
    assertFalse(
        intersectionSettings.isHandledAsTrafficSignalOfKind(1, rampMeterTags, OsmTrafficSignalKind.VEHICLE_SIGNAL));
    assertFalse(
        intersectionSettings.isHandledAsTrafficSignalOfKind(1, vehicleSignalTags, OsmTrafficSignalKind.RAMP_METER));
    assertTrue(
        intersectionSettings.isHandledAsTrafficSignalOfKind(2, vehicleSignalTags, OsmTrafficSignalKind.RAMP_METER));
    assertFalse(
        intersectionSettings.isHandledAsTrafficSignalOfKind(2, vehicleSignalTags, OsmTrafficSignalKind.VEHICLE_SIGNAL));
    assertFalse(intersectionSettings.isHandledAsTrafficSignalOfKind(3, rampMeterTags, OsmTrafficSignalKind.RAMP_METER));
    assertTrue(
        intersectionSettings.isHandledAsTrafficSignalOfKind(3, rampMeterTags, OsmTrafficSignalKind.VEHICLE_SIGNAL));
  }
}

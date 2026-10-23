package org.goplanit.osm.converter.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertFalse(intersectionSettings.isBreakLinksAtIsolatedSignalisedCrossings());
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
    intersectionSettings.setBreakLinksAtIsolatedSignalisedCrossings(true);
    intersectionSettings.setConsolidateIntersections(true);
    intersectionSettings.getIntersectionConsolidatorSettings().setMaximumJoiningReachMeters(60);
    intersectionSettings.getIntersectionCollapserSettings().setCollapsedModeTypes(PredefinedModeType.PEDESTRIAN);

    intersectionSettings.reset();

    assertTrue(intersectionSettings.isParserActive());
    assertEquals(50, intersectionSettings.getMaximumDistanceVehicleSignalToJunctionInMeters());
    assertFalse(intersectionSettings.isBreakLinksAtIsolatedSignalisedCrossings());
    assertFalse(intersectionSettings.isConsolidateIntersections());
    assertEquals(IntersectionConsolidatorSettings.DEFAULT_MAXIMUM_JOINING_REACH_METERS,
        intersectionSettings.getIntersectionConsolidatorSettings().getMaximumJoiningReachMeters());
    assertFalse(intersectionSettings.getIntersectionCollapserSettings().hasCollapsedModes());
  }
}

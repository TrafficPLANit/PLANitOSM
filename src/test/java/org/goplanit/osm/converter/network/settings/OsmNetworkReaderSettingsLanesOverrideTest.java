package org.goplanit.osm.converter.network.settings;

import org.goplanit.osm.converter.network.settings.override.OsmWayLaneOverride;
import org.goplanit.osm.converter.settings.OsmReaderSettingsSchema;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.SettingsYamlLoader;
import org.goplanit.utils.settings.UserOverride;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the lanes override of OSM ways on the network reader settings alone: an override replaced by the next one for
 * the same way, every override given with its reason in increasing way id order, overrides it cannot use refused
 * without touching what was stored, and the overrides written as a key of settings text
 *
 * @author markr
 */
public class OsmNetworkReaderSettingsLanesOverrideTest {

  /**
   * Fresh settings, without any override
   *
   * @return the settings
   */
  private static OsmNetworkReaderSettings createSettings() {
    return new OsmNetworkReaderSettings(CountryNames.AUSTRALIA);
  }

  /** Overriding a way again replaces the override given before */
  @Test
  public void overridingAgainReplacesTheOverride() {
    var settings = createSettings();
    settings.overrideOsmWayLanes(7, UserOverride.of(new OsmWayLaneOverride(2, null)));
    settings.overrideOsmWayLanes(7, UserOverride.of(new OsmWayLaneOverride(3, null)));

    assertEquals(3, settings.getOsmWayLaneOverride(7).getLanesForward());
    assertNull(settings.getOsmWayLaneOverride(7).getLanesBackward());
    assertNull(settings.getOsmWayLaneOverride(8));
  }

  /** Every override is given with its reason, in increasing way id order */
  @Test
  public void everyOverrideIsGivenWithItsReason() {
    var settings = createSettings();
    settings.overrideOsmWayLanes(2, UserOverride.of(new OsmWayLaneOverride(2, 2), "checked on imagery"));
    settings.overrideOsmWayLanes(1, UserOverride.of(new OsmWayLaneOverride(null, 1)));

    var overrides = settings.getOsmWayLaneOverrides();
    assertEquals(List.of(1L, 2L), List.copyOf(overrides.keySet()));
    assertFalse(overrides.get(1L).hasReason());
    assertEquals("checked on imagery", overrides.get(2L).getReason());
    assertTrue(overrides.get(2L).getValue().hasLanesForwardAndBackward());
    assertFalse(overrides.get(1L).getValue().hasLanesForwardAndBackward());
  }

  /** Zero lanes in a direction are refused, and nothing is stored */
  @Test
  public void zeroLanesAreRefused() {
    var settings = createSettings();
    assertThrows(IllegalArgumentException.class,
        () -> settings.overrideOsmWayLanes(5, UserOverride.of(new OsmWayLaneOverride(0, null))));
    assertNull(settings.getOsmWayLaneOverride(5));
  }

  /** An override with neither direction given is refused, and nothing is stored */
  @Test
  public void noLanesAreRefused() {
    var settings = createSettings();
    assertThrows(IllegalArgumentException.class,
        () -> settings.overrideOsmWayLanes(5, UserOverride.of(new OsmWayLaneOverride(null, null))));
    assertThrows(IllegalArgumentException.class,
        () -> settings.overrideOsmWayLanes(5, UserOverride.of(new OsmWayLaneOverride())));
    assertNull(settings.getOsmWayLaneOverride(5));
  }

  /** A refused override leaves the override stored before for the way as it was */
  @Test
  public void refusedOverrideKeepsTheStoredOne() {
    var settings = createSettings();
    settings.overrideOsmWayLanes(5, UserOverride.of(new OsmWayLaneOverride(2, null)));
    assertThrows(IllegalArgumentException.class,
        () -> settings.overrideOsmWayLanes(5, UserOverride.of(new OsmWayLaneOverride(0, null))));
    assertEquals(2, settings.getOsmWayLaneOverride(5).getLanesForward());
  }

  /** The overrides are a key of settings text, each entry written plainly or with a reason */
  @Test
  public void overridesAreAKeyOfSettingsText() {
    var text = "planitOsmConverterSettings: 1\nnetwork:\n  osmWayLaneOverrides:\n" +
        "    1: {lanesForward: 2}\n" +
        "    2: {value: {lanesForward: 2, lanesBackward: 1}, reason: \"checked on imagery\"}\n";
    var settings = new SettingsYamlLoader(OsmReaderSettingsSchema.SCHEMA, List.of(SettingsSource.ofText(text)))
        .create(OsmNetworkReaderSettings.class);

    assertEquals(2, settings.getOsmWayLaneOverride(1).getLanesForward());
    assertNull(settings.getOsmWayLaneOverride(1).getLanesBackward());
    assertEquals(1, settings.getOsmWayLaneOverride(2).getLanesBackward());
    assertEquals("checked on imagery", settings.getOsmWayLaneOverrides().get(2L).getReason());
  }
}

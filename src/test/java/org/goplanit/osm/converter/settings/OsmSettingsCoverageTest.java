package org.goplanit.osm.converter.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.utils.settings.SettingsCoverage;
import org.junit.jupiter.api.Test;

/**
 * Tests that the annotations of every settings class the OSM intermodal reader settings reach cover those settings:
 * every method changing settings is a key, is covered by one, or is marked as no key; every getter of nested settings
 * is a section, the read of a key, or marked as no key; every key has a read; and every key a convenience is covered
 * by exists
 * <p>
 * The checks are those of {@link SettingsCoverage}, which the tests of every converter's settings share. The classes
 * checked are found by following, from the intermodal reader settings, every getter returning settings, such as the
 * getter of the lane defaults, so no class hides from the checks.
 * </p>
 *
 * @author markr
 */
public class OsmSettingsCoverageTest {

  /** the coverage of every settings class the OSM intermodal reader settings reach */
  private static final SettingsCoverage COVERAGE = SettingsCoverage.of(OsmIntermodalReaderSettings.class);

  /**
   * Every method changing settings has exactly one of {@code SettingsWrite}, {@code CoveredBySettingsKey},
   * {@code DelegatedToSyncedInternalSettingsKeys} or {@code NoSettingsKey}
   */
  @Test
  public void everyMethodChangingSettingsHasExactlyOneMarker() {
    assertEquals(List.of(), COVERAGE.findUnmarkedMethodsChangingSettings());
  }

  /**
   * Every getter of nested settings is a section, the read of a key, or marked as no key, or returns settings naming
   * their own section at the top level, such as the network settings the intermodal settings hold
   */
  @Test
  public void everyGetterOfNestedSettingsIsMarked() {
    assertEquals(List.of(), COVERAGE.findUnmarkedGettersOfNestedSettings());
  }

  /**
   * Every key has a read of the same key in its class, the {@code +} and {@code -} forms of a list that of the whole
   * list, so settings can be written out
   */
  @Test
  public void everyKeyHasARead() {
    assertEquals(List.of(), COVERAGE.findKeysWithoutRead());
  }

  /**
   * Every key or section a convenience is covered by exists on the class of the convenience, a key of a nested section
   * found through the path of its sections
   */
  @Test
  public void everyCoveringKeyExists() {
    assertEquals(List.of(), COVERAGE.findUnknownCoveringKeys());
  }
}

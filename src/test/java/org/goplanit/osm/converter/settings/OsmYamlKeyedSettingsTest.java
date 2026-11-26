package org.goplanit.osm.converter.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.goplanit.osm.converter.network.settings.OsmHighwaySettings;
import org.goplanit.osm.converter.network.settings.OsmHighwayYamlKeyedSettings;
import org.goplanit.osm.converter.network.settings.OsmIntersectionCollapserSettings;
import org.goplanit.osm.converter.network.settings.OsmIntersectionCollapserYamlKeyedSettings;
import org.goplanit.osm.converter.network.settings.OsmIntersectionSettings;
import org.goplanit.osm.converter.network.settings.OsmIntersectionYamlKeyedSettings;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderYamlKeyedSettings;
import org.goplanit.osm.converter.network.settings.OsmRailwaySettings;
import org.goplanit.osm.converter.network.settings.OsmRailwayYamlKeyedSettings;
import org.goplanit.osm.converter.network.settings.OsmWaterwaySettings;
import org.goplanit.osm.converter.network.settings.OsmWaterwayYamlKeyedSettings;
import org.goplanit.osm.converter.network.settings.OsmWaySettings;
import org.goplanit.osm.converter.network.settings.OsmWayYamlKeyedSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderYamlKeyedSettings;
import org.goplanit.osm.defaults.OsmLaneDefaults;
import org.goplanit.osm.defaults.OsmLaneYamlKeyedDefaults;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidatorSettings;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidatorYamlKeyedSettings;
import org.goplanit.utils.settings.SettingsCoverage;
import org.junit.jupiter.api.Test;

/**
 * Tests that each settings class the OSM settings reach, such as the consolidation settings of PLANitUtils, whose keys
 * sit in a YAML keyed class keeps them apart: only the key and section methods, with the reset and the logging of
 * those keys, on the YAML keyed class it extends, everything else on the settings class itself, and no key method
 * overridden.
 * <p>
 * A settings file can set exactly the keys of the YAML keyed classes, so the YAML keyed class of a settings class shows
 * all a file can say about it. These checks keep that true as keys are added.
 * </p>
 *
 * @author markr
 */
public class OsmYamlKeyedSettingsTest {

  /** each settings class with keys, and the YAML keyed class it extends that holds them */
  private static final Map<Class<?>, Class<?>> YAML_KEYED_CLASS_BY_SETTINGS_CLASS = Map.ofEntries(
      Map.entry(OsmReaderSettings.class, OsmReaderYamlKeyedSettings.class),
      Map.entry(OsmNetworkReaderSettings.class, OsmNetworkReaderYamlKeyedSettings.class),
      Map.entry(OsmIntersectionSettings.class, OsmIntersectionYamlKeyedSettings.class),
      Map.entry(OsmIntersectionCollapserSettings.class, OsmIntersectionCollapserYamlKeyedSettings.class),
      Map.entry(OsmLaneDefaults.class, OsmLaneYamlKeyedDefaults.class),
      Map.entry(OsmWaySettings.class, OsmWayYamlKeyedSettings.class),
      Map.entry(OsmHighwaySettings.class, OsmHighwayYamlKeyedSettings.class),
      Map.entry(OsmRailwaySettings.class, OsmRailwayYamlKeyedSettings.class),
      Map.entry(OsmWaterwaySettings.class, OsmWaterwayYamlKeyedSettings.class),
      Map.entry(IntersectionConsolidatorSettings.class, IntersectionConsolidatorYamlKeyedSettings.class),
      Map.entry(OsmPublicTransportReaderSettings.class, OsmPublicTransportReaderYamlKeyedSettings.class));

  /**
   * Each settings class extends its YAML keyed class directly, so its keys are inherited as they are
   */
  @Test
  public void eachSettingsClassExtendsItsYamlKeyedClass() {
    assertEquals(List.of(),
        SettingsCoverage.findSettingsClassesNotExtendingTheirYamlKeyedClassDirectly(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
  }

  /**
   * A settings class declares no key or section of its own: they are all on its YAML keyed class
   */
  @Test
  public void keysAreDeclaredOnTheYamlKeyedClassOnly() {
    assertEquals(List.of(),
        SettingsCoverage.findKeysAndSectionsDeclaredOnSettingsClasses(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
  }

  /**
   * Every public method a YAML keyed class declares is a key or a section, its reset, which puts exactly those keys
   * back to their defaults, or its logSettings, which logs exactly those keys: plain getters, conveniences and
   * everything else for code are on the settings class
   */
  @Test
  public void everyPublicMethodOfAYamlKeyedClassIsAKey() {
    assertEquals(List.of(),
        SettingsCoverage.findMethodsOfYamlKeyedClassesNotKeysOrSections(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
  }

  /**
   * No settings class overrides a key or section method of its YAML keyed class, as an override would hide the method's
   * annotations from the settings files
   */
  @Test
  public void noKeyMethodIsOverridden() {
    assertEquals(List.of(),
        SettingsCoverage.findKeysAndSectionsOverriddenBySettingsClasses(YAML_KEYED_CLASS_BY_SETTINGS_CLASS));
  }
}

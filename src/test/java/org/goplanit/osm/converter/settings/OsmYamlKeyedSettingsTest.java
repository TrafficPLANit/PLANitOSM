package org.goplanit.osm.converter.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.goplanit.utils.reflection.AnnotationUtils;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsSection;
import org.goplanit.utils.settings.annotations.SettingsWrite;
import org.junit.jupiter.api.Test;

/**
 * Tests that each settings class the OSM settings reach, such as the consolidation settings of PLANitUtils, whose keys
 * sit in a YAML keyed class keeps them apart: only the key and section
 * methods on the YAML keyed class it extends, everything else on the settings class itself, and no key method
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

  /** the annotations of a key or section */
  private static final List<Class<? extends Annotation>> KEY_ANNOTATIONS =
      List.of(SettingsWrite.class, SettingsRead.class, SettingsSection.class);

  /** name of the method putting the keys of a YAML keyed class back to their defaults */
  private static final String RESET = "reset";

  /**
   * Each settings class extends its YAML keyed class directly, so its keys are inherited as they are
   */
  @Test
  public void eachSettingsClassExtendsItsYamlKeyedClass() {
    YAML_KEYED_CLASS_BY_SETTINGS_CLASS.forEach((settingsClass, yamlKeyedClass) ->
        assertSame(yamlKeyedClass, settingsClass.getSuperclass(), settingsClass.getSimpleName()));
  }

  /**
   * A settings class declares no key or section of its own: they are all on its YAML keyed class
   */
  @Test
  public void keysAreDeclaredOnTheYamlKeyedClassOnly() {
    YAML_KEYED_CLASS_BY_SETTINGS_CLASS.keySet().forEach(settingsClass -> assertEquals(List.of(),
        AnnotationUtils.getDeclaredMethodsWithAnyAnnotation(settingsClass, KEY_ANNOTATIONS),
        settingsClass.getSimpleName()));
  }

  /**
   * Every public method a YAML keyed class declares is a key or a section, or its reset, which puts exactly those keys
   * back to their defaults: plain getters, conveniences and everything else for code are on the settings class
   */
  @Test
  public void everyPublicMethodOfAYamlKeyedClassIsAKey() {
    YAML_KEYED_CLASS_BY_SETTINGS_CLASS.values().forEach(yamlKeyedClass -> assertEquals(List.of(),
        Arrays.stream(yamlKeyedClass.getDeclaredMethods()).filter(method -> !method.isSynthetic()
            && Modifier.isPublic(method.getModifiers()) && !AnnotationUtils.hasAnyAnnotation(method, KEY_ANNOTATIONS)
            && !(method.getName().equals(RESET) && method.getParameterCount() == 0))
            .collect(Collectors.toList()), yamlKeyedClass.getSimpleName()));
  }

  /**
   * No settings class overrides a key or section method of its YAML keyed class, as an override would hide the method's
   * annotations from the settings files
   *
   * @throws NoSuchMethodException never, each key method being public
   */
  @Test
  public void noKeyMethodIsOverridden() throws NoSuchMethodException {
    for (var entry : YAML_KEYED_CLASS_BY_SETTINGS_CLASS.entrySet()) {
      for (var keyMethod : AnnotationUtils.getDeclaredMethodsWithAnyAnnotation(entry.getValue(), KEY_ANNOTATIONS)) {
        var found = entry.getKey().getMethod(keyMethod.getName(), keyMethod.getParameterTypes());
        assertSame(entry.getValue(), found.getDeclaringClass(), found.toString());
      }
    }
  }
}

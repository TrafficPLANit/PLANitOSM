package org.goplanit.osm.converter.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.utils.exceptions.SettingsProblemsException;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.problem.SettingsProblemKind;
import org.junit.jupiter.api.function.Executable;

/**
 * Settings made from the text of a file, and the checks on them, shared by the tests of the OSM settings files
 * <p>
 * The tests of each part of a file make settings from a few lines of text and read the values back, or check that a
 * mistake gives exactly the problem expected, such as a value refused, so both are written once here
 * </p>
 *
 * @author markr
 */
public final class OsmSettingsTestUtils {

  /**
   * Network reader settings made from the text of one file, after its header
   *
   * @param body the lines after the header
   * @return the settings
   */
  public static OsmNetworkReaderSettings networkSettingsOf(String body) {
    return OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(
        List.of(SettingsSource.ofText(OsmReaderSettingsSchema.SCHEMA, body)));
  }

  /**
   * Intermodal reader settings made from the text of one file, after its header
   *
   * @param body the lines after the header
   * @return the settings
   */
  public static OsmIntermodalReaderSettings intermodalSettingsOf(String body) {
    return OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(
        List.of(SettingsSource.ofText(OsmReaderSettingsSchema.SCHEMA, body)));
  }

  /**
   * Assert that making settings fails with exactly one problem, of the kind given
   *
   * @param kind of the problem
   * @param creation making the settings, such as from the text of a file
   */
  public static void assertOneProblemOf(SettingsProblemKind kind, Executable creation) {
    var problems = assertThrows(SettingsProblemsException.class, creation).getProblems();
    assertEquals(1, problems.size());
    assertEquals(kind, problems.get(0).getKind());
  }
}

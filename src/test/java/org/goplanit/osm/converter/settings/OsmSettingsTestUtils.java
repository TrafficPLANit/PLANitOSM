package org.goplanit.osm.converter.settings;

import java.util.List;

import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.utils.settings.SettingsSource;

/**
 * Settings made from the text of a file, shared by the tests of the OSM settings files
 * <p>
 * The tests of each part of a file make settings from a few lines of text and read the values back, or check that a
 * mistake gives exactly the problem expected, such as a value refused, with
 * {@code SettingsFileAssertionUtils#assertThrowsOneSettingsProblem}; making the settings is written once here
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
}

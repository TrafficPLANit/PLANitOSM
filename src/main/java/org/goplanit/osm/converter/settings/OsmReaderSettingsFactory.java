package org.goplanit.osm.converter.settings;

import java.net.URL;
import java.util.List;

import org.goplanit.osm.converter.intermodal.OsmIntermodalReaderSettings;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.utils.exceptions.SettingsProblemsException;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.SettingsYamlLoader;

/**
 * Makes the settings of each OSM reader, in code or from YAML settings files and text
 * <p>
 * For example, network reader settings for Australia made in code, and made from files, the input set in code:
 * </p>
 * <pre>
 * var inCode = OsmReaderSettingsFactory.createNetworkReaderSettings(CountryNames.AUSTRALIA);
 * var fromFiles = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(
 *     SettingsSource.ofFile(Path.of("gma_network.yaml")), SettingsSource.ofFile(Path.of("sydney_corrections.yaml"))));
 * fromFiles.setInputSource(pbfFile);
 * var reader = OsmNetworkReaderFactory.create(fromFiles);
 * </pre>
 * <p>
 * It is the one place to find how OSM reader settings are made. Without a country the settings use the global
 * defaults. Settings made from files follow {@link OsmReaderSettingsSchema} and the sources apply in order: the
 * country first, from the key {@code country} at the root, the last source giving one winning, since only a
 * constructor sets it; then every other key, a later source winning for a replacing key. A source holds the section
 * {@code network} for the network settings and {@code publicTransport} for the public transport settings, so one
 * source serves both readers; the network reader settings refuse a {@code publicTransport} section. Every problem in
 * the sources is reported together, see {@link SettingsProblemsException}. Either way the settings are ordinary
 * settings, so nothing that takes settings changes, and code changing them afterwards wins. The constructors of the
 * settings stay public, so code making settings with them keeps working.
 * </p>
 *
 * @author markr
 */
public final class OsmReaderSettingsFactory {

  /** Not instantiable, everything is static */
  private OsmReaderSettingsFactory() {
  }

  /**
   * Network reader settings on the global defaults
   *
   * @return the settings
   */
  public static OsmNetworkReaderSettings createNetworkReaderSettings() {
    return new OsmNetworkReaderSettings();
  }

  /**
   * Network reader settings on the defaults of a country
   *
   * @param countryName whose defaults to use, such as {@code CountryNames.AUSTRALIA}
   * @return the settings
   */
  public static OsmNetworkReaderSettings createNetworkReaderSettings(String countryName) {
    return new OsmNetworkReaderSettings(countryName);
  }

  /**
   * Network reader settings reading a local file, on the defaults of a country
   *
   * @param inputFile the local file to read, absolute or relative
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmNetworkReaderSettings createNetworkReaderSettings(String inputFile, String countryName) {
    return new OsmNetworkReaderSettings(inputFile, countryName);
  }

  /**
   * Network reader settings reading a location, on the defaults of a country
   *
   * @param inputSource the location to read, a file or on the web
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmNetworkReaderSettings createNetworkReaderSettings(URL inputSource, String countryName) {
    return new OsmNetworkReaderSettings(inputSource, countryName);
  }

  /**
   * Network reader settings made from YAML sources
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources
   */
  public static OsmNetworkReaderSettings createNetworkReaderSettingsFromYaml(List<SettingsSource> sources) {
    return new SettingsYamlLoader(OsmReaderSettingsSchema.SCHEMA, sources).create(OsmNetworkReaderSettings.class);
  }

  /**
   * Public transport reader settings on the global defaults
   *
   * @return the settings
   */
  public static OsmPublicTransportReaderSettings createPublicTransportReaderSettings() {
    return new OsmPublicTransportReaderSettings();
  }

  /**
   * Public transport reader settings on the defaults of a country
   *
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmPublicTransportReaderSettings createPublicTransportReaderSettings(String countryName) {
    return new OsmPublicTransportReaderSettings(countryName);
  }

  /**
   * Public transport reader settings reading a local file, on the defaults of a country
   *
   * @param inputFile the local file to read, absolute or relative
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmPublicTransportReaderSettings createPublicTransportReaderSettings(String inputFile,
      String countryName) {
    return new OsmPublicTransportReaderSettings(inputFile, countryName);
  }

  /**
   * Public transport reader settings reading a location, on the defaults of a country
   *
   * @param inputSource the location to read, a file or on the web
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmPublicTransportReaderSettings createPublicTransportReaderSettings(URL inputSource,
      String countryName) {
    return new OsmPublicTransportReaderSettings(inputSource, countryName);
  }

  /**
   * Intermodal reader settings on the global defaults
   *
   * @return the settings
   */
  public static OsmIntermodalReaderSettings createIntermodalReaderSettings() {
    return new OsmIntermodalReaderSettings();
  }

  /**
   * Intermodal reader settings on the defaults of a country
   *
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmIntermodalReaderSettings createIntermodalReaderSettings(String countryName) {
    return new OsmIntermodalReaderSettings(countryName);
  }

  /**
   * Intermodal reader settings reading a local file or a web location, on the defaults of a country
   *
   * @param inputSource the file or web location to read
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmIntermodalReaderSettings createIntermodalReaderSettings(String inputSource, String countryName) {
    return new OsmIntermodalReaderSettings(inputSource, countryName);
  }

  /**
   * Intermodal reader settings reading a location, on the defaults of a country
   *
   * @param inputSource the location to read, a file or on the web
   * @param countryName whose defaults to use
   * @return the settings
   */
  public static OsmIntermodalReaderSettings createIntermodalReaderSettings(URL inputSource, String countryName) {
    return new OsmIntermodalReaderSettings(inputSource, countryName);
  }

  /**
   * Intermodal reader settings made from YAML sources, the network section onto its network settings and the public
   * transport section onto its public transport settings
   *
   * @param sources in the order they apply
   * @return the settings
   * @throws SettingsProblemsException holding every problem found in the sources
   */
  public static OsmIntermodalReaderSettings createIntermodalReaderSettingsFromYaml(List<SettingsSource> sources) {
    return new SettingsYamlLoader(OsmReaderSettingsSchema.SCHEMA, sources).create(OsmIntermodalReaderSettings.class);
  }
}

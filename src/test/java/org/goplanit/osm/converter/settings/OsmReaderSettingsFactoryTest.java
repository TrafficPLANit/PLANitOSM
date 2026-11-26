package org.goplanit.osm.converter.settings;

import static org.goplanit.osm.test.OsmLayerTestUtils.LANES_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.converter.network.OsmTrafficSignalDirection;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.settings.override.OsmWayLaneOverride;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.test.WarningRecorder;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.settings.SettingsFileContentType;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.problem.SettingsProblemKind;
import org.goplanit.utils.test.SettingsFileAssertionUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link OsmReaderSettingsFactory}: OSM reader settings made in code, and made from settings text, the
 * country taken first, changed in code afterwards like settings made in code, one shape of text for the network and
 * intermodal readers, the corrections of the OSM data as keys with optional reasons, the lanes of a way and the
 * kind and direction of a traffic signal, and settings written to a settings file made again from it: the file of
 * every key at its global default, and the file of every key not at its default, both written out unchanged
 *
 * @author markr
 */
public class OsmReaderSettingsFactoryTest {

  /** folder of the settings files kept with the tests */
  private static final Path SETTINGS_FILES_DIR = Path.of("src", "test", "resources", "settings");

  /** every key of the intermodal settings at its global default, with descriptions */
  private static final Path GLOBAL_DEFAULTS_FILE = SETTINGS_FILES_DIR.resolve("osm_settings_global_defaults.yaml");

  /** every key of the intermodal settings not at its default, with descriptions */
  private static final Path EVERY_KEY_NOT_DEFAULT_FILE =
      SETTINGS_FILES_DIR.resolve("osm_settings_every_key_not_default.yaml");

  /** the logger, giving the log output PLANit's format */
  private static Logger LOGGER = null;

  /**
   * A source of settings text, the header followed by the lines given
   *
   * @param body the lines after the header
   * @return the source
   */
  private static SettingsSource text(String body) {
    return SettingsSource.ofText(OsmReaderSettingsSchema.SCHEMA, body);
  }

  /**
   * Create the logger once for all tests of the class
   *
   * @throws Exception when the log file cannot be opened
   */
  @BeforeAll
  public static void setUpLogger() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(OsmReaderSettingsFactoryTest.class);
    }
  }

  /** Close the logger after all tests of the class */
  @AfterAll
  public static void tearDownLogger() {
    Logging.closeLogger(LOGGER);
  }

  /** folder for the test files */
  @TempDir
  public Path tempDir;

  /**
   * Reset the ids before each test
   */
  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  /**
   * Reset the ids after each test
   */
  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** Settings made in code take the country and input given, for each reader, the global defaults without a country */
  @Test
  public void settingsMadeInCode() throws Exception {
    var file = LANES_SCENARIO_DIR.resolve("way_lane_overrides.osm").toString();
    assertEquals(CountryNames.GLOBAL, OsmReaderSettingsFactory.createNetworkReaderSettings().getCountryName());
    var network = OsmReaderSettingsFactory.createNetworkReaderSettings(file, CountryNames.AUSTRALIA);
    assertEquals(CountryNames.AUSTRALIA, network.getCountryName());
    assertNotNull(network.getInputSource());

    var publicTransport = OsmReaderSettingsFactory.createPublicTransportReaderSettings(file, CountryNames.GERMANY);
    assertEquals(CountryNames.GERMANY, publicTransport.getCountryName());
    assertNotNull(publicTransport.getInputSource());

    var intermodal = OsmReaderSettingsFactory.createIntermodalReaderSettings(
        Path.of(file).toUri().toURL(), CountryNames.AUSTRALIA);
    assertEquals(CountryNames.AUSTRALIA, intermodal.getNetworkSettings().getCountryName());
    assertEquals(CountryNames.AUSTRALIA, intermodal.getPublicTransportSettings().getCountryName());
    assertNotNull(intermodal.getPublicTransportSettings().getInputSource());
    assertEquals(CountryNames.GLOBAL,
        OsmReaderSettingsFactory.createIntermodalReaderSettings().getNetworkSettings().getCountryName());
  }

  /**
   * Network settings written to a settings file with the keys differing from the defaults are made again from it, with
   * their country and the key changed
   */
  @Test
  public void networkSettingsMadeAgainFromTheirSettingsFile() throws Exception {
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettings(CountryNames.AUSTRALIA);
    settings.setConsolidateLinkSegmentTypes(false);
    var file = tempDir.resolve("network_settings.yaml");
    settings.writeSettingsFile(file, SettingsFileContentType.KEYS_DIFFERING_FROM_DEFAULTS, true);

    var madeAgain = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(SettingsSource.ofFile(file)));
    assertEquals(CountryNames.AUSTRALIA, madeAgain.getCountryName());
    assertFalse(madeAgain.isConsolidateLinkSegmentTypes());
  }

  /**
   * Intermodal settings written to a settings file with the keys differing from the defaults are made again from it,
   * with their country and the keys changed in their network and public transport settings
   */
  @Test
  public void intermodalSettingsMadeAgainFromTheirSettingsFile() throws Exception {
    var settings = OsmReaderSettingsFactory.createIntermodalReaderSettings(CountryNames.AUSTRALIA);
    settings.getNetworkSettings().setConsolidateLinkSegmentTypes(false);
    settings.getPublicTransportSettings().setRemoveDanglingZones(false);
    var file = tempDir.resolve("intermodal_settings.yaml");
    settings.writeSettingsFile(file, SettingsFileContentType.KEYS_DIFFERING_FROM_DEFAULTS, true);

    var madeAgain =
        OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(List.of(SettingsSource.ofFile(file)));
    assertEquals(CountryNames.AUSTRALIA, madeAgain.getNetworkSettings().getCountryName());
    assertEquals(CountryNames.AUSTRALIA, madeAgain.getPublicTransportSettings().getCountryName());
    assertFalse(madeAgain.getNetworkSettings().isConsolidateLinkSegmentTypes());
    assertFalse(madeAgain.getPublicTransportSettings().isRemoveDanglingZones());
  }

  /**
   * Intermodal settings at their global defaults write the file of the global defaults, every key with its
   * description, and so do settings made from that file
   */
  @Test
  public void fileWrittenBySettingsAtTheirDefaults() throws Exception {
    SettingsFileAssertionUtils.assertFileWrittenBySettingsAtTheirDefaults(
        OsmReaderSettingsFactory::createIntermodalReaderSettingsFromYaml,
        OsmReaderSettingsFactory.createIntermodalReaderSettings(), GLOBAL_DEFAULTS_FILE, tempDir);
  }

  /**
   * Settings made from the file of every key not at its default write that file again, with every key and with only
   * the keys differing from the defaults, as every key differs; written without descriptions, they are made again into
   * settings writing the same file
   */
  @Test
  public void fileWrittenAgainBySettingsMadeFromIt() throws Exception {
    SettingsFileAssertionUtils.assertFileWrittenAgainBySettingsMadeFromIt(
        OsmReaderSettingsFactory::createIntermodalReaderSettingsFromYaml, EVERY_KEY_NOT_DEFAULT_FILE, tempDir);
  }

  /** Intermodal settings made of network settings made from text and public transport settings hold those settings */
  @Test
  public void intermodalSettingsOfSettingsMadeBefore() {
    var network = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(
        text("country: Australia\nnetwork:\n  osmWayLaneOverrides:\n    1: {lanesForward: 2}\n")));
    var publicTransport = OsmReaderSettingsFactory.createPublicTransportReaderSettings(CountryNames.AUSTRALIA);

    var intermodal = OsmReaderSettingsFactory.createIntermodalReaderSettings(network, publicTransport);
    assertSame(network, intermodal.getNetworkSettings());
    assertSame(publicTransport, intermodal.getPublicTransportSettings());
  }

  /** The country comes from the last source giving one, with its defaults, such as its speed limits */
  @Test
  public void countryFromTheLastSourceGivingOne() {
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(
        text("country: Australia\n"), text("network:\n  osmWayLaneOverrides:\n    1: {lanesForward: 2}\n")));

    assertEquals(CountryNames.AUSTRALIA, settings.getCountryName());
    assertEquals(new OsmNetworkReaderSettings(CountryNames.AUSTRALIA).getHighwaySettings()
            .getDefaultSpeedLimitByOsmHighwayType(OsmHighwayTags.PRIMARY),
        settings.getHighwaySettings().getDefaultSpeedLimitByOsmHighwayType(OsmHighwayTags.PRIMARY));
    assertEquals(CountryNames.GERMANY, OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(
        List.of(text("country: Australia\n"), text("country: Germany\n"))).getCountryName());
  }

  /**
   * A country that is not a known country name, such as a misspelt one, still makes settings, with one warning, the
   * intermodal settings too
   */
  @Test
  public void unknownCountryIsWarnedAbout() {
    var warnings = new WarningRecorder();
    var settingsLogger = Logger.getLogger(OsmNetworkReaderSettings.class.getCanonicalName());
    settingsLogger.addHandler(warnings);
    try {
      var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(text("country: Austrlia\n")));
      assertEquals("Austrlia", settings.getCountryName());
      assertEquals(1, warnings.getCount());
      OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(text("country: Australia\n")));
      assertEquals(1, warnings.getCount());
      OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(List.of(text("country: Austrlia\n")));
      assertEquals(2, warnings.getCount());
    } finally {
      settingsLogger.removeHandler(warnings);
    }
  }

  /** Without a country in any source, the settings use the global defaults */
  @Test
  public void noCountry() {
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(text("network: {}\n")));

    assertEquals(CountryNames.GLOBAL, settings.getCountryName());
    assertEquals(CountryNames.GLOBAL, OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(
        List.of(text("network: {}\n"))).getNetworkSettings().getCountryName());
  }

  /** A reader given settings made from text converts as it does with the same settings made in code */
  @Test
  public void readerFromSettingsMadeFromText() {
    var scenario = LANES_SCENARIO_DIR.resolve("way_lane_overrides.osm").toString();
    var fromText = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(
        text("country: Australia\nnetwork:\n  osmWayLaneOverrides:\n    60: {lanesForward: 2, lanesBackward: 2}\n")));
    fromText.setInputSource(scenario);
    fromText.deactivateRemoveDanglingSubnetworks();
    var inCode = new OsmNetworkReaderSettings(scenario, CountryNames.AUSTRALIA);
    inCode.deactivateRemoveDanglingSubnetworks();
    inCode.overrideOsmWayLanes(60, UserOverride.of(new OsmWayLaneOverride(2, 2)));

    var layerFromText = OsmNetworkReaderFactory.create(fromText).read().getTransportLayers().getFirst();
    IdGenerator.reset();
    var layerInCode = OsmNetworkReaderFactory.create(inCode).read().getTransportLayers().getFirst();

    assertEquals(layerInCode.getNumberOfLinkSegments(), layerFromText.getNumberOfLinkSegments());
    assertEquals(layerInCode.getNumberOfNodes(), layerFromText.getNumberOfNodes());
    assertEquals(2, segment(layerFromText, 1, 2).getNumberOfLanes());
    assertEquals(2, segment(layerFromText, 2, 1).getNumberOfLanes());
  }

  /** Code setting other settings afterwards keeps the corrections the text gave */
  @Test
  public void correctionsKeptWhenCodeSetsOtherSettings() {
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(
        text("country: Australia\nnetwork:\n  osmWayLaneOverrides:\n    7: {lanesForward: 2}\n")));
    settings.setInputSource(LANES_SCENARIO_DIR.resolve("way_lane_overrides.osm").toString());
    settings.getHighwaySettings().deactivateOsmHighwayType(OsmHighwayTags.TRACK);

    assertEquals(2, settings.getOsmWayLaneOverride(7).getLanesForward());
    assertNotNull(settings.getInputSource());
    assertTrue(settings.getHighwaySettings().isOsmHighwayTypeDeactivated(OsmHighwayTags.TRACK));
  }

  /** Code changing a setting the text gave replaces it: a way overridden by the text and again in code */
  @Test
  public void codeAfterTheText() {
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(
        text("network:\n  osmWayLaneOverrides:\n    7: {lanesForward: 2}\n")));
    settings.overrideOsmWayLanes(7, UserOverride.of(new OsmWayLaneOverride(3, null)));

    assertEquals(3, settings.getOsmWayLaneOverride(7).getLanesForward());
  }

  /** One shape of text serves both readers: a public transport section is refused for the network reader, with one
   * problem naming it, and taken by the intermodal reader */
  @Test
  public void publicTransportSectionForTheNetworkReader() {
    var source = text("country: Australia\nnetwork:\n  osmWayLaneOverrides:\n    1: {lanesForward: 2}\n" +
        "publicTransport: {}\n");
    var problem = SettingsFileAssertionUtils.assertThrowsOneSettingsProblem(SettingsProblemKind.UNKNOWN_KEY,
        () -> OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(source)));
    assertEquals("publicTransport", problem.getKeyPath());

    var intermodal = OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(List.of(source));
    assertEquals(CountryNames.AUSTRALIA, intermodal.getNetworkSettings().getCountryName());
    assertEquals(2, intermodal.getNetworkSettings().getOsmWayLaneOverride(1).getLanesForward());
  }

  /** The kind and direction of a traffic signal are keys of the intersections, each entry with an optional reason: the
   * node is handled as the kind given, whatever its tags say */
  @Test
  public void signalOverridesFromText() {
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(text("network:\n" +
        "  intersections:\n" +
        "    trafficSignalKindOverrides:\n      123: {value: RAMP_METER, reason: \"on site 2026-10-30\"}\n" +
        "    trafficSignalDirectionOverrides:\n      456: BACKWARD\n")));
    var intersectionSettings = settings.getIntersectionSettings();

    assertEquals(OsmTrafficSignalKind.RAMP_METER, intersectionSettings.getTrafficSignalKindOverride(123));
    assertTrue(intersectionSettings.isHandledAsTrafficSignalOfKind(123, Map.of(), OsmTrafficSignalKind.RAMP_METER));
    assertEquals("on site 2026-10-30", intersectionSettings.getTrafficSignalKindOverrides().get(123L).getReason());
    assertEquals(OsmTrafficSignalDirection.BACKWARD, intersectionSettings.getTrafficSignalDirectionOverride(456));
  }
}

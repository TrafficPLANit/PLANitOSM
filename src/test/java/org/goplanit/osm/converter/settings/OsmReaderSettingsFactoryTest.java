package org.goplanit.osm.converter.settings;

import static org.goplanit.osm.test.OsmLayerTestUtils.LANES_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.network.OsmNetworkReaderFactory;
import org.goplanit.osm.converter.network.OsmTrafficSignalDirection;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.settings.override.OsmWayLaneOverride;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.utils.exceptions.SettingsProblemsException;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.settings.SettingsSource;
import org.goplanit.utils.settings.UserOverride;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link OsmReaderSettingsFactory}: OSM reader settings made in code, and made from settings text, the
 * country taken first, changed in code afterwards like settings made in code, one shape of text for the network and
 * intermodal readers, and the corrections of the OSM data as keys with optional reasons, the lanes of a way and the
 * kind and direction of a traffic signal
 *
 * @author markr
 */
public class OsmReaderSettingsFactoryTest {

  /** the logger, giving the log output PLANit's format */
  private static Logger LOGGER = null;

  /**
   * A source of settings text, the header followed by the lines given
   *
   * @param body the lines after the header
   * @return the source
   */
  private static SettingsSource text(String body) {
    return SettingsSource.ofText("planitOsmConverterSettings: 1\n" + body);
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
    var warnings = new ArrayList<LogRecord>();
    var handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        if (record.getLevel() == Level.WARNING) {
          warnings.add(record);
        }
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    var settingsLogger = Logger.getLogger(OsmNetworkReaderSettings.class.getCanonicalName());
    settingsLogger.addHandler(handler);
    try {
      var settings = OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(text("country: Austrlia\n")));
      assertEquals("Austrlia", settings.getCountryName());
      assertEquals(1, warnings.size());
      OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(text("country: Australia\n")));
      assertEquals(1, warnings.size());
      OsmReaderSettingsFactory.createIntermodalReaderSettingsFromYaml(List.of(text("country: Austrlia\n")));
      assertEquals(2, warnings.size());
    } finally {
      settingsLogger.removeHandler(handler);
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
    var problems = assertThrows(SettingsProblemsException.class,
        () -> OsmReaderSettingsFactory.createNetworkReaderSettingsFromYaml(List.of(source))).getProblems();
    assertEquals(1, problems.size());
    assertEquals("publicTransport", problems.get(0).getKeyPath());

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

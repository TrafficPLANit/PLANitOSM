package org.goplanit.osm.converter.network.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.goplanit.algorithms.intersection.GreenRatioAlgorithmType;
import org.goplanit.osm.converter.settings.OsmReaderSettingsFactory;
import org.goplanit.osm.converter.settings.OsmSettingsTestUtils;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.osm.tags.OsmWaterModeTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.osm.test.WarningRecorder;
import org.goplanit.utils.graph.directed.Connectivity;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.problem.SettingsProblemKind;
import org.junit.jupiter.api.Test;

/**
 * Tests the keys of the network section of a settings file, those of {@link OsmNetworkReaderYamlKeyedSettings}: each
 * key set by a file reads back through its getter on the network reader settings.
 * <p>
 * The lanes override and the intersection keys have tests of their own; the keys here are the rest of the network
 * section. Each value set differs from its default, so the test fails when a key does not reach the settings.
 * </p>
 *
 * @author markr
 */
public class OsmNetworkReaderYamlKeyedSettingsTest {

  /** The defaults of a new way type with every value given */
  private static final String NEW_OSM_WAY_TYPE_DEFAULTS = "{lanes: 2, speedLimitKmh: 30, capacityPerLanePcuH: 500, " +
      "maxDensityPerLanePcuKm: 180, allowedOsmModes: [motorcar]}";

  /**
   * Network reader settings made from the network section given, after the header
   *
   * @param networkSection the lines of the network section, each indented by two spaces
   * @return the settings
   */
  private static OsmNetworkReaderSettings fromNetworkSection(String networkSection) {
    return OsmSettingsTestUtils.networkSettingsOf("network:\n" + networkSection);
  }

  /**
   * Assert the network section given has one problem, a value its settings refuse
   *
   * @param networkSection the lines of the network section, each indented by two spaces
   */
  private static void assertOneValueRefused(String networkSection) {
    OsmSettingsTestUtils.assertOneProblemOf(SettingsProblemKind.VALUE_REFUSED, () -> fromNetworkSection(networkSection));
  }

  /** The keys holding one value each: the two flags off, both on by default, and a ferry distance of 250 m, not 2 km */
  @Test
  public void singleValuesReadBack() {
    var settings = fromNetworkSection("  consolidateLinkSegmentTypes: false\n  alwaysKeepLargestSubnetwork: false\n" +
        "  maximumDistanceFerryOutsideBoundingPolygonMeters: 250\n");

    assertFalse(settings.isConsolidateLinkSegmentTypes());
    assertFalse(settings.isAlwaysKeepLargestSubnetwork());
    assertEquals(250, settings.getMaximumDistanceFerryOutsideBoundingPolygonMeters());
  }

  /**
   * The keys correcting single OSM ways, nodes and relations by id, each written plainly or with a reason: each reads
   * back with its reason, the relations in increasing id order, and the getters for one id see it
   */
  @Test
  public void osmIdCorrectionsReadBack() {
    var settings = fromNetworkSection(
        "  osmRelationsExcludedFromParsing: [34, {value: 12, reason: no left turn for through traffic only}]\n" +
        "  osmWaysExcludedFromParsing: [12, {value: 34, reason: duplicate carriageway}]\n" +
        "  osmWaysKeptOutsideBoundingPolygon: [56]\n" +
        "  osmNodesKeptOutsideBoundingPolygon: [{value: 78, reason: station entrance}]\n" +
        "  osmWayModeAccessOverrides:\n" +
        "    90: [foot, bicycle]\n" +
        "    91: {value: [bus], reason: bus only}\n");

    assertEquals(Map.of(12L, UserOverride.of(12L, "no left turn for through traffic only"), 34L, UserOverride.of(34L)),
        settings.getOsmRelationsExcludedFromParsing());
    assertEquals(List.of(12L, 34L), List.copyOf(settings.getOsmRelationsExcludedFromParsing().keySet()));
    assertEquals(Map.of(12L, UserOverride.of(12L), 34L, UserOverride.of(34L, "duplicate carriageway")),
        settings.getOsmWaysExcludedFromParsing());
    assertEquals(Map.of(56L, UserOverride.of(56L)), settings.getOsmWaysKeptOutsideBoundingPolygon());
    assertEquals(Map.of(78L, UserOverride.of(78L, "station entrance")),
        settings.getOsmNodesKeptOutsideBoundingPolygon());
    assertEquals(Map.of(90L, UserOverride.of(Set.of("foot", "bicycle")),
        91L, UserOverride.of(Set.of("bus"), "bus only")), settings.getOsmWayModeAccessOverrides());

    assertTrue(settings.isOsmWayExcluded(34));
    assertTrue(settings.isKeepOsmWayOutsideBoundingPolygon(56));
    assertTrue(settings.isKeepOsmNodeOutsideBoundingPolygon(78));
    assertEquals(Set.of("bus"), settings.getOsmWayModeAccessOverride(91));
    assertNull(settings.getOsmWayModeAccessOverride(12));
  }

  /** An OSM way id of zero or less is refused: no OSM way has one */
  @Test
  public void invalidOsmWayIdToExcludeIsRefused() {
    assertOneValueRefused("  osmWaysExcludedFromParsing: [0]\n");
  }

  /** A relation excluded again keeps only its second reason */
  @Test
  public void osmRelationExcludedAgainKeepsTheLastReason() {
    var settings = fromNetworkSection("  osmRelationsExcludedFromParsing: [{value: 12, reason: checked on imagery}, " +
        "{value: 12, reason: changed in OSM}]\n");

    assertEquals(Map.of(12L, UserOverride.of(12L, "changed in OSM")), settings.getOsmRelationsExcludedFromParsing());
  }

  /** An OSM relation id of zero or less is refused, and nothing is stored: no OSM relation has one */
  @Test
  public void invalidOsmRelationIdToExcludeIsRefused() {
    assertOneValueRefused("  osmRelationsExcludedFromParsing: [0]\n");
    var settings = OsmReaderSettingsFactory.createNetworkReaderSettings();
    assertThrows(IllegalArgumentException.class, () -> settings.excludeOsmRelationFromParsing(UserOverride.of(0L)));
    assertTrue(settings.getOsmRelationsExcludedFromParsing().isEmpty());
  }

  /**
   * The access value classification: each access value reads back as classified, and the access values parsing
   * applies follow it. By default private denies access and permissive grants it; here they swap
   */
  @Test
  public void accessValueClassificationReadsBack() {
    var settings = fromNetworkSection(
        "  accessValueClassification:\n    private: POSITIVE\n    permissive: NEGATIVE\n");

    assertEquals(Map.of("private", AccessValueClassification.POSITIVE,
        "permissive", AccessValueClassification.NEGATIVE),
        settings.getAccessValueClassification());
    assertTrue(Arrays.asList(settings.getPositiveAccessValueTags()).contains("private"));
    assertFalse(Arrays.asList(settings.getNegativeAccessValueTags()).contains("private"));
    assertTrue(Arrays.asList(settings.getNegativeAccessValueTags()).contains("permissive"));
    assertFalse(Arrays.asList(settings.getPositiveAccessValueTags()).contains("permissive"));
  }

  /**
   * An access value classified after parsing has asked for the access values once is applied too: the access values
   * are worked out again
   */
  @Test
  public void accessValueClassifiedAfterUseIsApplied() {
    var settings = new OsmNetworkReaderSettings();
    assertFalse(Arrays.asList(settings.getPositiveAccessValueTags()).contains("private"));

    settings.classifyAccessValueTagAsPositive("private");
    assertTrue(Arrays.asList(settings.getPositiveAccessValueTags()).contains("private"));
  }

  /** A blank access value is refused: no OSM access value is blank */
  @Test
  public void blankAccessValueIsRefused() {
    assertOneValueRefused("  accessValueClassification:\n    \" \": POSITIVE\n");
  }

  /**
   * The modes whose dangling subnetworks are removed replace the default of car only, and a size or connectivity left
   * out keeps its default: here pedestrian with a size and weak connectivity, and bicycle with nothing given
   */
  @Test
  public void removeDanglingSubnetworksReplacesTheDefault() {
    var settings = fromNetworkSection("  removeDanglingSubnetworks:\n" +
        "    PEDESTRIAN: {belowSize: 20, connectivity: WEAK}\n" +
        "    BICYCLE: {}\n");

    assertEquals(Set.of(PredefinedModeType.PEDESTRIAN, PredefinedModeType.BICYCLE),
        settings.getRemoveDanglingSubnetworkModes().keySet());
    var pedestrian = settings.getRemoveDanglingSubnetworksConfig(PredefinedModeType.PEDESTRIAN);
    assertEquals(20, pedestrian.getBelowSize());
    assertEquals(Integer.MAX_VALUE, pedestrian.getAboveSize());
    assertEquals(Connectivity.WEAK, pedestrian.getConnectivity());
    var bicycle = settings.getRemoveDanglingSubnetworksConfig(PredefinedModeType.BICYCLE);
    assertEquals(OsmNetworkReaderSettings.DEFAULT_MINIMUM_SUBNETWORK_SIZE, bicycle.getBelowSize());
    assertEquals(Integer.MAX_VALUE, bicycle.getAboveSize());
    assertEquals(OsmNetworkReaderSettings.DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY, bicycle.getConnectivity());
  }

  /** No modes given removes no dangling subnetworks at all, not even those of car */
  @Test
  public void noModesToRemoveDanglingSubnetworksOfTurnsItOff() {
    var settings = fromNetworkSection("  removeDanglingSubnetworks: {}\n");
    assertFalse(settings.isRemoveDanglingSubnetworks());
  }

  /** Activating a mode in code keeps the modes already set, and deactivating one keeps the others */
  @Test
  public void removeDanglingSubnetworksOfOneModeKeepsTheOthers() {
    var settings = new OsmNetworkReaderSettings();
    settings.activateRemoveDanglingSubnetworks(PredefinedModeType.PEDESTRIAN);
    settings.deactivateRemoveDanglingSubnetworks(PredefinedModeType.CAR);
    assertEquals(Set.of(PredefinedModeType.PEDESTRIAN), settings.getRemoveDanglingSubnetworkModes().keySet());
  }

  /**
   * The lane defaults: primary given 3 lanes where it has 2 built in, motorway left out keeps its 2, and the railway
   * tracks and waterway lanes per direction set to 2, both 1 by default
   */
  @Test
  public void laneDefaultsReadBack() {
    var settings = fromNetworkSection("  laneDefaults:\n    lanesByHighwayType: {primary: 3}\n" +
        "    railwayTracksPerDirection: 2\n    waterwayLanesPerDirection: 2\n");

    var laneDefaults = settings.getLaneDefaults();
    assertEquals(3, laneDefaults.getLanesByHighwayType().get(OsmHighwayTags.PRIMARY));
    assertEquals(2, laneDefaults.getLanesByHighwayType().get(OsmHighwayTags.MOTORWAY));
    assertEquals(2, laneDefaults.getRailwayTracksPerDirection());
    assertEquals(2, laneDefaults.getWaterwayLanesPerDirection());
  }

  /** Fewer than one lane for a highway type is refused: a road has at least one */
  @Test
  public void noLanesForAHighwayTypeIsRefused() {
    assertOneValueRefused("  laneDefaults:\n    lanesByHighwayType: {primary: 0}\n");
  }

  /**
   * A new way type reads back with its defaults, and parsing takes ways of the type with them: highway=proposed, not
   * parsed by default, with no lanes, speed limit, capacity or modes of its own
   */
  @Test
  public void newOsmWayTypeReadsBackAndIsActivated() {
    var settings = fromNetworkSection("  newOsmWayTypes:\n    highway=proposed: " + NEW_OSM_WAY_TYPE_DEFAULTS + "\n");

    var defaults = settings.getNewOsmWayTypes().get("highway=proposed");
    assertEquals(2, defaults.getLanes());
    assertEquals(30.0, defaults.getSpeedLimitKmh());
    assertEquals(500.0, defaults.getCapacityPerLanePcuH());
    assertEquals(180.0, defaults.getMaxDensityPerLanePcuKm());
    assertEquals(Set.of(OsmRoadModeTags.MOTOR_CAR), defaults.getAllowedOsmModes());

    var highwaySettings = settings.getHighwaySettings();
    assertTrue(highwaySettings.isOsmHighwayTypeActivated(OsmHighwayTags.PROPOSED));
    assertEquals(2, settings.getLaneDefaults().getLanesByHighwayType().get(OsmHighwayTags.PROPOSED));
    assertEquals(30.0, highwaySettings.getDefaultSpeedLimitByOsmHighwayType(OsmHighwayTags.PROPOSED));
    var capacityMaxDensity = highwaySettings.getOverwrittenCapacityMaxDensityByOsmHighwayType(OsmHighwayTags.PROPOSED);
    assertEquals(500.0, capacityMaxDensity.first());
    assertEquals(180.0, capacityMaxDensity.second());
    assertTrue(highwaySettings.collectAllowedOsmHighwayModes(OsmHighwayTags.PROPOSED)
        .contains(OsmRoadModeTags.MOTOR_CAR));
  }

  /** A highway type parsing supports already is refused as a new one: primary has its defaults built in */
  @Test
  public void newOsmWayTypeOfSupportedHighwayTypeIsRefused() {
    assertOneValueRefused("  newOsmWayTypes:\n    highway=primary: " + NEW_OSM_WAY_TYPE_DEFAULTS + "\n");
  }

  /** A new way type under a key that is not highway, railway or waterway is refused: no layer parses it */
  @Test
  public void newOsmWayTypeOfUnknownKeyIsRefused() {
    assertOneValueRefused("  newOsmWayTypes:\n    building=house: " + NEW_OSM_WAY_TYPE_DEFAULTS + "\n");
  }

  /** A new way type not written as key=value is refused: the key it belongs to is not known */
  @Test
  public void newOsmWayTypeNotKeyEqualsValueIsRefused() {
    assertOneValueRefused("  newOsmWayTypes:\n    proposed: " + NEW_OSM_WAY_TYPE_DEFAULTS + "\n");
  }

  /** A new way type with a value left out is refused: it has no default to fall back on */
  @Test
  public void newOsmWayTypeWithoutSpeedLimitIsRefused() {
    assertOneValueRefused("  newOsmWayTypes:\n    highway=proposed: {lanes: 2, capacityPerLanePcuH: 500, " +
        "maxDensityPerLanePcuKm: 180, allowedOsmModes: [motorcar]}\n");
  }

  /** A new way type with a mode that is no OSM mode is refused, storing nothing: no lanes, the type still not parsed */
  @Test
  public void newOsmWayTypeWithAnUnknownModeIsRefusedStoringNothing() {
    var settings = new OsmNetworkReaderSettings();
    assertThrows(IllegalArgumentException.class, () -> settings.registerNewOsmWayType("highway=proposed",
        new NewOsmWayTypeDefaults(2, 30.0, 500.0, 180.0, Set.of("hovercraft"))));

    assertFalse(settings.getHighwaySettings().isOsmHighwayTypeActivated(OsmHighwayTags.PROPOSED));
    assertFalse(settings.getLaneDefaults().getLanesByHighwayType().containsKey(OsmHighwayTags.PROPOSED));
    assertTrue(settings.getNewOsmWayTypes().isEmpty());
  }

  /** Each parser is switched by the active key of its section: highway off, railway and waterway on, the reverse of
   * the defaults */
  @Test
  public void parsersSwitchedByActive() {
    var settings = fromNetworkSection("  highway:\n    active: false\n  railway:\n    active: true\n" +
        "  waterway:\n    active: true\n");

    assertFalse(settings.isHighwayParserActive());
    assertTrue(settings.isRailwayParserActive());
    assertTrue(settings.isWaterwayParserActive());
  }

  /**
   * Changing the types never switches a parser, in code as in a file: adding a railway type leaves the railway parser
   * off, and removing every highway type leaves the highway parser on
   */
  @Test
  public void typesDoNotSwitchTheParser() {
    var settings = new OsmNetworkReaderSettings();
    settings.getRailwaySettings().activateOsmRailwayType(OsmRailwayTags.TRAM);
    settings.getHighwaySettings().deactivateAllOsmHighwayTypes();

    assertFalse(settings.isRailwayParserActive());
    assertTrue(settings.isHighwayParserActive());
  }

  /** While its parser is off a family has no modes, and its modes are back once the parser is on again */
  @Test
  public void modesAnswerNoneWhileTheParserIsOff() {
    var railwaySettings = new OsmNetworkReaderSettings().getRailwaySettings();
    assertNull(railwaySettings.getMappedPlanitRailMode(OsmRailModeTags.TRAIN));
    assertTrue(railwaySettings.getActivatedOsmModes().isEmpty());

    railwaySettings.activateParser(true);
    assertEquals(PredefinedModeType.TRAIN, railwaySettings.getMappedPlanitRailMode(OsmRailModeTags.TRAIN));
    assertTrue(railwaySettings.getActivatedOsmModes().contains(OsmRailModeTags.TRAIN));
  }

  /** The urban speed limit switch reads back: off here, on by default */
  @Test
  public void urbanSpeedLimitSwitchReadsBack() {
    var settings = fromNetworkSection("  highway:\n    speedLimitDefaultsBasedOnUrbanArea: false\n");
    assertFalse(settings.getHighwaySettings().isSpeedLimitDefaultsBasedOnUrbanArea());
  }

  /** The furthest a track signal may be from the road it crosses reads back: 30 m here, 50 m by default */
  @Test
  public void trackSignalDistanceReadsBack() {
    var settings = fromNetworkSection("  railway:\n    maximumDistanceTrackSignalToRoadCrossingMeters: 30\n");
    assertEquals(30, settings.getRailwaySettings().getMaximumDistanceTrackSignalToRoadCrossingMeters());
  }

  /** A whole list of types replaces the defaults of its family: here highway, railway and waterway */
  @Test
  public void typesListReplacesTheDefaults() {
    var settings = fromNetworkSection("  highway:\n    types: [primary, secondary]\n" +
        "  railway:\n    types: [rail]\n  waterway:\n    types: [ferry]\n");

    assertEquals(Set.of(OsmHighwayTags.PRIMARY, OsmHighwayTags.SECONDARY),
        settings.getHighwaySettings().getActivatedOsmHighwayTypes());
    assertEquals(Set.of(OsmRailwayTags.RAIL), settings.getRailwaySettings().getActivatedOsmRailwayTypes());
    assertEquals(Set.of(OsmWaterwayTags.FERRY), settings.getWaterwaySettings().getActivatedOsmWaterwayTypes());
  }

  /** Two types left out of the defaults leave every other default type, so a default added later is followed */
  @Test
  public void defaultsWithoutTwoTypes() {
    var settings = fromNetworkSection("  highway:\n    types-: [pedestrian, track]\n");

    var expected = new TreeSet<>(new OsmNetworkReaderSettings().getHighwaySettings().getActivatedOsmHighwayTypes());
    expected.removeAll(Set.of(OsmHighwayTags.PEDESTRIAN, OsmHighwayTags.TRACK));
    assertEquals(expected, settings.getHighwaySettings().getActivatedOsmHighwayTypes());
  }

  /** A type added to the defaults is parsed besides them: funicular, not a railway type parsed by default */
  @Test
  public void typeAddedToTheDefaults() {
    var settings = fromNetworkSection("  railway:\n    types+: [funicular]\n");

    var activated = settings.getRailwaySettings().getActivatedOsmRailwayTypes();
    assertTrue(activated.contains(OsmRailwayTags.FUNICULAR));
    assertTrue(activated.contains(OsmRailwayTags.RAIL));
  }

  /**
   * A type of another family in a whole list of highway types is refused, in code and in a file, and the highway types
   * stay as they were
   */
  @Test
  public void notARoadType() {
    var highwaySettings = new OsmNetworkReaderSettings().getHighwaySettings();
    var before = new TreeSet<>(highwaySettings.getActivatedOsmHighwayTypes());

    assertThrows(IllegalArgumentException.class, () -> highwaySettings.deactivateAllOsmHighwayTypesExcept(
        List.of(OsmHighwayTags.PRIMARY, OsmRailwayTags.RAIL)));
    assertEquals(before, highwaySettings.getActivatedOsmHighwayTypes());
    assertOneValueRefused("  highway:\n    types: [primary, rail]\n");
  }

  /**
   * A type registered as new can be listed in the whole list of its family, though written before it in the file: the
   * keys of the network apply before its sections
   */
  @Test
  public void registeredNewTypeInATypesList() {
    var settings = fromNetworkSection("  highway:\n    types: [primary, escape]\n" +
        "  newOsmWayTypes:\n    highway=escape: " + NEW_OSM_WAY_TYPE_DEFAULTS + "\n");

    assertEquals(Set.of(OsmHighwayTags.PRIMARY, "escape"), settings.getHighwaySettings().getActivatedOsmHighwayTypes());
  }

  /**
   * A type registered as new gets its modes, and modes allowed on it after, without a warning: highway=escape, not a
   * type parsing supports, with motorcar when registered and bus allowed after
   */
  @Test
  public void registeredNewTypeGetsItsModesWithoutAWarning() {
    var warnings = new WarningRecorder();
    var modeAccessLogger = Logger.getLogger(OsmModeAccessDefaultsCategory.class.getCanonicalName());
    modeAccessLogger.addHandler(warnings);
    try {
      var highwaySettings = fromNetworkSection("  highway:\n    allowedModesByType:\n      escape: [bus]\n" +
          "  newOsmWayTypes:\n    highway=escape: " + NEW_OSM_WAY_TYPE_DEFAULTS + "\n").getHighwaySettings();

      assertTrue(highwaySettings.collectAllowedOsmHighwayModes("escape")
          .containsAll(Set.of(OsmRoadModeTags.MOTOR_CAR, OsmRoadModeTags.BUS)));
      assertEquals(0, warnings.getCount());
    } finally {
      modeAccessLogger.removeHandler(warnings);
    }
  }

  /** A parser set off stays off when its types are added to after: the file says it is off */
  @Test
  public void parserOffStaysOff() {
    var settings = fromNetworkSection("  railway:\n    types+: [tram]\n    active: false\n");

    assertFalse(settings.isRailwayParserActive());
    assertTrue(settings.getRailwaySettings().getActivatedOsmRailwayTypes().contains(OsmRailwayTags.TRAM));
  }

  /**
   * A whole list of modes replaces the defaults of its family, switching on a mode off by default: taxi on highways
   */
  @Test
  public void modesListReplacesTheDefaults() {
    var settings = fromNetworkSection("  highway:\n    modes: [motorcar, taxi]\n" +
        "  railway:\n    modes: [tram]\n  waterway:\n    modes: [ferry]\n");

    assertEquals(Set.of(OsmRoadModeTags.MOTOR_CAR, OsmRoadModeTags.TAXI),
        settings.getHighwaySettings().getActivatedOsmRoadModes());
    assertEquals(Set.of(OsmRailModeTags.TRAM), settings.getRailwaySettings().getActivatedOsmRailModes());
    assertEquals(Set.of(OsmWaterModeTags.FERRY), settings.getWaterwaySettings().getActivatedOsmWaterModes());
  }

  /** A mode left out of the defaults leaves every other default mode, and a mode added comes on top of them */
  @Test
  public void modesRemovedFromAndAddedToTheDefaults() {
    var settings = fromNetworkSection("  highway:\n    modes-: [bus]\n    modes+: [taxi]\n");

    var expected = new TreeSet<>(new OsmNetworkReaderSettings().getHighwaySettings().getActivatedOsmRoadModes());
    expected.remove(OsmRoadModeTags.BUS);
    expected.add(OsmRoadModeTags.TAXI);
    assertEquals(expected, settings.getHighwaySettings().getActivatedOsmRoadModes());
  }

  /**
   * A mode no PLANit mode is mapped to is refused, as parsing could not take it: moped, an OSM road mode with no
   * mapping by default; and a mode of another family in a whole list leaves the modes as they were
   */
  @Test
  public void modeTheFamilyDoesNotKnowIsRefused() {
    assertOneValueRefused("  highway:\n    modes+: [moped]\n");

    var highwaySettings = new OsmNetworkReaderSettings().getHighwaySettings();
    var before = new TreeSet<>(highwaySettings.getActivatedOsmRoadModes());
    assertThrows(IllegalArgumentException.class, () -> highwaySettings.deactivateAllOsmRoadModesExcept(
        List.of(OsmRoadModeTags.MOTOR_CAR, OsmRailModeTags.TRAIN)));
    assertEquals(before, highwaySettings.getActivatedOsmRoadModes());
  }

  /** The modes of a family read back whether its parser is on or not: the railway parser is off by default */
  @Test
  public void modesReadBackWithTheParserOff() {
    var railwaySettings = new OsmNetworkReaderSettings().getRailwaySettings();

    assertFalse(railwaySettings.isParserActive());
    assertTrue(railwaySettings.getActivatedOsmRailModes().contains(OsmRailModeTags.TRAIN));
  }

  /**
   * A mode mapped to a PLANit mode reads back and can then be parsed: moped, an OSM road mode with no mapping by
   * default, mapped to motor bike and added to the modes after
   */
  @Test
  public void modeMappingLetsAModeBeParsed() {
    var settings = fromNetworkSection("  highway:\n    modeMappings:\n      moped: MOTOR_BIKE\n    modes+: [moped]\n");

    var highwaySettings = settings.getHighwaySettings();
    assertEquals(PredefinedModeType.MOTOR_BIKE, highwaySettings.getOsmRoadModeMappings().get(OsmRoadModeTags.MOPED));
    assertTrue(highwaySettings.getActivatedOsmRoadModes().contains(OsmRoadModeTags.MOPED));
    assertEquals(PredefinedModeType.MOTOR_BIKE, highwaySettings.getMappedPlanitRoadMode(OsmRoadModeTags.MOPED));
  }

  /**
   * Mapping a mode does not switch it on, as no setting switches another; and the mappings read back whether their
   * modes are on or not, taxi being mapped but off by default
   */
  @Test
  public void modeMappingDoesNotSwitchTheModeOn() {
    var highwaySettings =
        fromNetworkSection("  highway:\n    modeMappings:\n      moped: MOTOR_BIKE\n").getHighwaySettings();

    assertFalse(highwaySettings.getActivatedOsmRoadModes().contains(OsmRoadModeTags.MOPED));
    assertEquals(PredefinedModeType.TAXI, highwaySettings.getOsmRoadModeMappings().get(OsmRoadModeTags.TAXI));
    assertFalse(highwaySettings.getActivatedOsmRoadModes().contains(OsmRoadModeTags.TAXI));
  }

  /** Mapping a mode that is on to another PLANit mode applies at once, the mode staying on */
  @Test
  public void remappingAModeThatIsOn() {
    var highwaySettings =
        fromNetworkSection("  highway:\n    modeMappings:\n      motorcar: TAXI\n").getHighwaySettings();

    assertEquals(PredefinedModeType.TAXI, highwaySettings.getMappedPlanitRoadMode(OsmRoadModeTags.MOTOR_CAR));
    assertTrue(highwaySettings.getActivatedOsmRoadModes().contains(OsmRoadModeTags.MOTOR_CAR));
  }

  /** A mode of another family is refused in the mode mappings of highways: train is a rail mode */
  @Test
  public void mappingAModeOfAnotherFamilyIsRefused() {
    assertOneValueRefused("  highway:\n    modeMappings:\n      train: TRAIN\n");
  }

  /**
   * The capacity and maximum density per lane of a way type read back, and parsing takes them, for each family:
   * residential, rail and ferry
   */
  @Test
  public void capacityMaxDensityReadsBack() {
    var settings = fromNetworkSection("  highway:\n    capacityMaxDensity:\n" +
        "      residential: {capacityPerLanePcuH: 1000, maxDensityPerLanePcuKm: 150}\n" +
        "  railway:\n    capacityMaxDensity:\n      rail: {capacityPerLanePcuH: 2000, maxDensityPerLanePcuKm: 100}\n" +
        "  waterway:\n    capacityMaxDensity:\n      ferry: {capacityPerLanePcuH: 300, maxDensityPerLanePcuKm: 50}\n");

    var residential =
        settings.getHighwaySettings().getCapacityMaxDensityByOsmHighwayType().get(OsmHighwayTags.RESIDENTIAL);
    assertEquals(1000.0, residential.getCapacityPerLanePcuH());
    assertEquals(150.0, residential.getMaxDensityPerLanePcuKm());
    var parsedResidential =
        settings.getHighwaySettings().getOverwrittenCapacityMaxDensityByOsmHighwayType(OsmHighwayTags.RESIDENTIAL);
    assertEquals(1000.0, parsedResidential.first());
    assertEquals(150.0, parsedResidential.second());
    assertEquals(2000.0, settings.getRailwaySettings().getCapacityMaxDensityByOsmRailwayType()
        .get(OsmRailwayTags.RAIL).getCapacityPerLanePcuH());
    assertEquals(50.0, settings.getWaterwaySettings().getCapacityMaxDensityByOsmWaterwayType()
        .get(OsmWaterwayTags.FERRY).getMaxDensityPerLanePcuKm());
  }

  /** The capacity and maximum density of a type the family does not know are refused: rail is no highway type */
  @Test
  public void capacityMaxDensityOfAnUnknownTypeIsRefused() {
    assertOneValueRefused(
        "  highway:\n    capacityMaxDensity:\n      rail: {capacityPerLanePcuH: 1000, maxDensityPerLanePcuKm: 150}\n");
  }

  /** A capacity or maximum density left out, or not above zero, is refused: both are needed and a lane holds some */
  @Test
  public void capacityMaxDensityLeftOutOrNotAboveZeroIsRefused() {
    assertOneValueRefused("  highway:\n    capacityMaxDensity:\n      residential: {capacityPerLanePcuH: 1000}\n");
    assertOneValueRefused("  highway:\n    capacityMaxDensity:\n" +
        "      residential: {capacityPerLanePcuH: 0, maxDensityPerLanePcuKm: 150}\n");
  }

  /**
   * A mode allowed on a highway type besides the country defaults reads back without them, and parsing allows it:
   * tram on service roads, a rail mode on a track in the road
   */
  @Test
  public void allowedModesByTypeReadBack() {
    var highwaySettings =
        fromNetworkSection("  highway:\n    allowedModesByType:\n      service: [tram]\n").getHighwaySettings();

    assertEquals(Map.of(OsmHighwayTags.SERVICE, Set.of(OsmRailModeTags.TRAM)),
        highwaySettings.getAllowedOsmHighwayModesBeyondDefaults());
    assertTrue(highwaySettings.collectAllowedOsmHighwayModes(OsmHighwayTags.SERVICE).contains(OsmRailModeTags.TRAM));
  }

  /** Modes allowed on a type the highways do not know, or a mode that is no OSM mode, are refused */
  @Test
  public void allowedModesOfAnUnknownTypeOrModeAreRefused() {
    assertOneValueRefused("  highway:\n    allowedModesByType:\n      rail: [bus]\n");
    assertOneValueRefused("  highway:\n    allowedModesByType:\n      service: [hovercraft]\n");
  }

  /**
   * The intersection settings of one value each read back, every one away from its default: the parser off, both
   * signal distances, and breaking links, consolidating and computing green ratios on
   */
  @Test
  public void intersectionSingleValuesReadBack() {
    var intersectionSettings = fromNetworkSection("  intersections:\n    active: false\n" +
        "    maximumDistanceVehicleSignalToJunctionMeters: 60\n" +
        "    maximumDistanceSignalisedCrossingToJunctionMeters: 20\n    breakLinksAtIsolatedSignals: true\n" +
        "    consolidateIntersections: true\n    computeGreenRatiosAndSaturationFlowFactors: true\n")
        .getIntersectionSettings();

    assertFalse(intersectionSettings.isParserActive());
    assertEquals(60, intersectionSettings.getMaximumDistanceVehicleSignalToJunctionMeters());
    assertEquals(20, intersectionSettings.getMaximumDistanceSignalisedCrossingToJunctionMeters());
    assertTrue(intersectionSettings.isBreakLinksAtIsolatedSignals());
    assertTrue(intersectionSettings.isConsolidateIntersections());
    assertTrue(intersectionSettings.isComputeGreenRatiosAndSaturationFlowFactors());
  }

  /** The collapse settings read back: the mode types collapsed, and the nodes excluded, one with its reason */
  @Test
  public void intersectionCollapserReadsBack() {
    var collapserSettings = fromNetworkSection("  intersections:\n    intersectionCollapser:\n" +
        "      collapsedModeTypes: [PEDESTRIAN, BICYCLE]\n" +
        "      osmNodeIdsExcludedFromCollapse: [12, {value: 34, reason: footways kept in full}]\n")
        .getIntersectionSettings().getIntersectionCollapserSettings();

    assertEquals(Set.of(PredefinedModeType.PEDESTRIAN, PredefinedModeType.BICYCLE),
        collapserSettings.getCollapsedModeTypes());
    assertEquals(Set.of(12L, 34L), collapserSettings.getOsmNodeIdsExcludedFromCollapse().keySet());
    assertEquals("footways kept in full", collapserSettings.getOsmNodeIdsExcludedFromCollapse().get(34L).getReason());
  }

  /** The consolidation settings are a section of the intersections: a value given there reads back */
  @Test
  public void intersectionConsolidatorIsASectionOfTheIntersections() {
    var consolidatorSettings = fromNetworkSection("  intersections:\n    intersectionConsolidator:\n" +
        "      maximumJoiningReachMeters: 50\n").getIntersectionSettings().getIntersectionConsolidatorSettings();

    assertEquals(50, consolidatorSettings.getMaximumJoiningReachMeters());
  }

  /** The groups of OSM nodes to join and to keep apart read back, in the order given, one with its reason */
  @Test
  public void nodeGroupsToJoinAndToKeepApartReadBack() {
    var intersectionSettings = fromNetworkSection("  intersections:\n" +
        "    osmNodeIdsToJoinIntoOneIntersection:\n      - [101, 102]\n      - value: [103, 104]\n" +
        "        reason: one controller\n    osmNodeIdsToKeepInSeparateIntersections: [[201, 202]]\n")
        .getIntersectionSettings();

    assertEquals(List.of(UserOverride.of(Set.of(101L, 102L)), UserOverride.of(Set.of(103L, 104L), "one controller")),
        intersectionSettings.getOsmNodeIdsToJoinIntoOneIntersection());
    assertEquals(List.of(UserOverride.of(Set.of(201L, 202L))),
        intersectionSettings.getOsmNodeIdsToKeepInSeparateIntersections());
  }

  /** A group of fewer than two different OSM nodes, or with an id no OSM node has, zero, is refused */
  @Test
  public void nodeGroupOfOneNodeOrWithIdZeroIsRefused() {
    assertOneValueRefused("  intersections:\n    osmNodeIdsToJoinIntoOneIntersection: [[101, 101]]\n");
    assertOneValueRefused("  intersections:\n    osmNodeIdsToKeepInSeparateIntersections: [[0, 202]]\n");
  }

  /** The green ratio settings are a section of the intersections: a value given there reads back, and a value that
   * makes no sense is refused */
  @Test
  public void greenRatioAndSaturationFlowFactorIsASectionOfTheIntersections() {
    var greenRatioSettings = fromNetworkSection("  intersections:\n    greenRatioAndSaturationFlowFactor:\n" +
        "      algorithmType: EQUAL_SPLIT\n").getIntersectionSettings().getGreenRatioAndSaturationFlowFactorSettings();

    assertEquals(GreenRatioAlgorithmType.EQUAL_SPLIT, greenRatioSettings.getAlgorithmType());
    assertOneValueRefused("  intersections:\n    greenRatioAndSaturationFlowFactor:\n      cycleTimeSeconds: 0\n");
  }

  /** A node id to exclude from collapse that no OSM node has, zero, is refused */
  @Test
  public void invalidOsmNodeIdToExcludeFromCollapseIsRefused() {
    assertOneValueRefused("  intersections:\n    intersectionCollapser:\n      osmNodeIdsExcludedFromCollapse: [0]\n");
  }

  /** Deactivating every way type at once leaves no type of any family, waterway types included */
  @Test
  public void allWayTypesDeactivatedAtOnce() {
    var settings = new OsmNetworkReaderSettings();
    settings.deactivateAllOsmWayTypes();

    assertTrue(settings.getHighwaySettings().getActivatedOsmHighwayTypes().isEmpty());
    assertTrue(settings.getRailwaySettings().getActivatedOsmRailwayTypes().isEmpty());
    assertTrue(settings.getWaterwaySettings().getActivatedOsmWaterwayTypes().isEmpty());
  }
}

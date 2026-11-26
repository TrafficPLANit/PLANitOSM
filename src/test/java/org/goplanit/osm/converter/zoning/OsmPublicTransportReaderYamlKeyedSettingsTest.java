package org.goplanit.osm.converter.zoning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.goplanit.osm.converter.settings.OsmSettingsTestUtils;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.problem.SettingsProblemKind;
import org.goplanit.utils.test.SettingsFileAssertionUtils;
import org.junit.jupiter.api.Test;

import de.topobyte.osm4j.core.model.iface.EntityType;

/**
 * Tests the keys of the public transport section of a settings file, those of
 * {@link OsmPublicTransportReaderYamlKeyedSettings}: each key set by a file reads back through its getter on the
 * public transport reader settings, and a value that makes no sense is refused.
 * <p>
 * The keys at the top level of a file have tests of their own; the keys here are those of the public transport section.
 * Each value set differs from its default, so the test fails when a key does not reach the settings.
 * </p>
 *
 * @author markr
 */
public class OsmPublicTransportReaderYamlKeyedSettingsTest {

  /**
   * Every key holding one value, each away from its default: the parser, the two dangling flags and the ferry route
   * flag off, all on by default, and each distance away from its default (25, 35, 35, 10, 5 and 100 m)
   */
  private static final String SINGLE_VALUES = "  active: false\n  removeDanglingZones: false\n" +
      "  removeDanglingTransferZoneGroups: false\n  stopToWaitingAreaSearchRadiusMeters: 30\n" +
      "  stationToWaitingAreaSearchRadiusMeters: 40\n  stationToParallelTracksSearchRadiusMeters: 45\n" +
      "  maxEndNodeToIdealStopPointMeters: 12\n  maxShapePointToIdealStopPointMeters: 6\n" +
      "  connectDanglingFerryStopToNearbyFerryRoute: false\n  ferryStopToFerryRouteSearchRadiusMeters: 150\n";

  /**
   * Public transport reader settings made from the public transport section given, after the header
   *
   * @param publicTransportSection the lines of the public transport section, each indented by two spaces
   * @return the settings
   */
  private static OsmPublicTransportReaderSettings fromPublicTransportSection(String publicTransportSection) {
    return OsmSettingsTestUtils.intermodalSettingsOf("publicTransport:\n" + publicTransportSection)
        .getPublicTransportSettings();
  }

  /**
   * Assert the public transport section given has one problem, a value its settings refuse
   *
   * @param publicTransportSection the lines of the public transport section, each indented by two spaces
   */
  private static void assertOneValueRefused(String publicTransportSection) {
    SettingsFileAssertionUtils.assertThrowsOneSettingsProblem(SettingsProblemKind.VALUE_REFUSED,
        () -> fromPublicTransportSection(publicTransportSection));
  }

  /** The keys holding one value each read back, see {@link #SINGLE_VALUES} */
  @Test
  public void singleValuesReadBack() {
    var settings = fromPublicTransportSection(SINGLE_VALUES);

    assertFalse(settings.isParserActive());
    assertFalse(settings.isRemoveDanglingZones());
    assertFalse(settings.isRemoveDanglingTransferZoneGroups());
    assertEquals(30, settings.getStopToWaitingAreaSearchRadiusMeters());
    assertEquals(40, settings.getStationToWaitingAreaSearchRadiusMeters());
    assertEquals(45, settings.getStationToParallelTracksSearchRadiusMeters());
    assertEquals(12, settings.getMaxEndNodeToIdealStopPointMeters());
    assertEquals(6, settings.getMaxShapePointToIdealStopPointMeters());
    assertFalse(settings.isConnectDanglingFerryStopToNearbyFerryRoute());
    assertEquals(150, settings.getFerryStopToFerryRouteSearchRadiusMeters());
  }

  /** A reset puts each key holding one value back to its default */
  @Test
  public void resetPutsEachSingleValueBackToItsDefault() {
    var settings = fromPublicTransportSection(SINGLE_VALUES);
    settings.reset();

    assertTrue(settings.isParserActive());
    assertTrue(settings.isRemoveDanglingZones());
    assertTrue(settings.isRemoveDanglingTransferZoneGroups());
    assertEquals(25, settings.getStopToWaitingAreaSearchRadiusMeters());
    assertEquals(35, settings.getStationToWaitingAreaSearchRadiusMeters());
    assertEquals(35, settings.getStationToParallelTracksSearchRadiusMeters());
    assertEquals(10, settings.getMaxEndNodeToIdealStopPointMeters());
    assertEquals(5, settings.getMaxShapePointToIdealStopPointMeters());
    assertTrue(settings.isConnectDanglingFerryStopToNearbyFerryRoute());
    assertEquals(100, settings.getFerryStopToFerryRouteSearchRadiusMeters());
  }

  /**
   * The access and egress section reaches the settings PLANit connects the stops with, read through the section and
   * through the conveniences of the public transport settings: bus stops not connected, at a radius of 40 m instead
   * of 25 m, and a ferry stop to land network radius of 300 m instead of 500 m
   */
  @Test
  public void accessEgressInjectionSectionReadsBack() {
    var settings = fromPublicTransportSection("  accessEgressInjection:\n" +
        "    connectBusBasedStopsToPassengerNetwork: false\n" +
        "    busBasedStopToPassengerNetworkSearchRadiusMeters: 40\n" +
        "    ferryStopToLandNetworkSearchRadiusMeters: 300\n");

    assertFalse(settings.isConnectBusBasedStopsToPassengerNetwork());
    assertEquals(40, settings.getAccessEgressInjectionSettings().getBusBasedStopToPassengerNetworkSearchRadiusMeters());
    assertEquals(300, settings.getFerryStopToLandNetworkSearchRadiusMeters());
  }

  /** A reset puts the access and egress section back to its defaults */
  @Test
  public void resetPutsTheAccessEgressInjectionSectionBack() {
    var settings = fromPublicTransportSection("  accessEgressInjection:\n" +
        "    connectBusBasedStopsToPassengerNetwork: false\n" +
        "    busBasedStopToPassengerNetworkSearchRadiusMeters: 40\n");
    settings.reset();

    assertTrue(settings.isConnectBusBasedStopsToPassengerNetwork());
    assertEquals(25, settings.getAccessEgressInjectionSettings().getBusBasedStopToPassengerNetworkSearchRadiusMeters());
  }

  /**
   * The keys listing OSM ids, each written plainly or with a reason: each reads back with its reason, and the checks
   * for one id see it
   */
  @Test
  public void osmIdListsReadBack() {
    var settings = fromPublicTransportSection(
        "  osmNodesExcludedFromParsing: [12, {value: 34, reason: ferry stop without a terminal}]\n" +
        "  osmWaysExcludedFromParsing: [{value: 56, reason: platform drawn twice}]\n" +
        "  suppressStopAreaLoggingOfOsmRelations: [78]\n");

    assertEquals(Map.of(12L, UserOverride.of(12L), 34L, UserOverride.of(34L, "ferry stop without a terminal")),
        settings.getOsmNodesExcludedFromParsing());
    assertEquals(Map.of(56L, UserOverride.of(56L, "platform drawn twice")), settings.getOsmWaysExcludedFromParsing());
    assertEquals(Map.of(78L, UserOverride.of(78L)), settings.getSuppressStopAreaLoggingOfOsmRelations());

    assertTrue(settings.isExcludedOsmNode(34));
    assertFalse(settings.isExcludedOsmNode(56));
    assertTrue(settings.isExcludedOsmWay(56));
    assertFalse(settings.isExcludedOsmWay(12));
    assertTrue(settings.isSuppressOsmRelationStopAreaLogging(78));
  }

  /** A reset empties the keys listing OSM ids */
  @Test
  public void resetEmptiesTheOsmIdLists() {
    var settings = fromPublicTransportSection("  osmNodesExcludedFromParsing: [12]\n" +
        "  osmWaysExcludedFromParsing: [56]\n  suppressStopAreaLoggingOfOsmRelations: [78]\n");
    settings.reset();

    assertTrue(settings.getOsmNodesExcludedFromParsing().isEmpty());
    assertTrue(settings.getOsmWaysExcludedFromParsing().isEmpty());
    assertTrue(settings.getSuppressStopAreaLoggingOfOsmRelations().isEmpty());
  }

  /** An OSM id of zero or less is refused in each list: no OSM entity has one */
  @Test
  public void osmIdNotAboveZeroIsRefused() {
    for (var key : List.of(
        "osmNodesExcludedFromParsing", "osmWaysExcludedFromParsing", "suppressStopAreaLoggingOfOsmRelations")) {
      assertOneValueRefused("  " + key + ": [0]\n");
    }
  }

  /**
   * The waiting area keys, each nested under the OSM entity type of the waiting area and written plainly or with a
   * reason: each reads back with its reason, and the lookups the parser makes see it
   */
  @Test
  public void waitingAreaOverridesReadBack() {
    var settings = fromPublicTransportSection("  waitingAreaOfStopLocationOverrides:\n" +
        "    Node: {12: 13}\n    Way: {14: {value: 15, reason: platform beside the stop}}\n" +
        "  osmWayOfWaitingAreaOverrides:\n    Node: {16: 17}\n" +
        "  waitingAreaModeAccessOverrides:\n    Way: {18: [light_rail, tram]}\n");

    assertEquals(Map.of(EntityType.Node, Map.of(12L, UserOverride.of(13L)),
        EntityType.Way, Map.of(14L, UserOverride.of(15L, "platform beside the stop"))),
        settings.getWaitingAreaOfStopLocationOverrides());
    assertEquals(Map.of(EntityType.Node, Map.of(16L, UserOverride.of(17L))),
        settings.getOsmWayOfWaitingAreaOverrides());
    assertEquals(Map.of(EntityType.Way, Map.of(18L, UserOverride.of(Set.of("light_rail", "tram")))),
        settings.getWaitingAreaModeAccessOverrides());

    assertEquals(EntityType.Way, settings.getOverwrittenWaitingAreaOfStopLocation(14).first());
    assertEquals(15L, settings.getOverwrittenWaitingAreaOfStopLocation(14).second());
    assertTrue(settings.isWaitingAreaOfStopLocationOverwritten(EntityType.Node, 13));
    assertFalse(settings.isWaitingAreaOfStopLocationOverwritten(EntityType.Way, 13));
    assertEquals(17L, settings.getWaitingAreaNominatedOsmWayForStopLocation(16, EntityType.Node));
    assertFalse(settings.hasWaitingAreaNominatedOsmWayForStopLocation(16, EntityType.Way));
    assertEquals(new TreeSet<>(Set.of("light_rail", "tram")),
        settings.getOverwrittenWaitingAreaModeAccess(18, EntityType.Way));
    assertFalse(settings.isOverwriteWaitingAreaModeAccess(18, EntityType.Node));
  }

  /**
   * A stop location has one waiting area, so a stop location given again under the other entity type keeps only the
   * waiting area given last
   */
  @Test
  public void stopLocationGivenAgainKeepsTheWaitingAreaGivenLast() {
    var settings = fromPublicTransportSection("  waitingAreaOfStopLocationOverrides:\n" +
        "    Node: {12: 13}\n    Way: {12: 15}\n");

    assertEquals(Map.of(EntityType.Way, Map.of(12L, UserOverride.of(15L))),
        settings.getWaitingAreaOfStopLocationOverrides());
    assertEquals(EntityType.Way, settings.getOverwrittenWaitingAreaOfStopLocation(12).first());
  }

  /** A reset empties the waiting area keys */
  @Test
  public void resetEmptiesTheWaitingAreaOverrides() {
    var settings = fromPublicTransportSection("  waitingAreaOfStopLocationOverrides: {Node: {12: 13}}\n" +
        "  osmWayOfWaitingAreaOverrides: {Node: {16: 17}}\n  waitingAreaModeAccessOverrides: {Way: {18: [tram]}}\n");
    settings.reset();

    assertTrue(settings.getWaitingAreaOfStopLocationOverrides().isEmpty());
    assertTrue(settings.getOsmWayOfWaitingAreaOverrides().isEmpty());
    assertTrue(settings.getWaitingAreaModeAccessOverrides().isEmpty());
  }

  /**
   * A waiting area is an OSM node or way, so a relation is refused, as is an OSM id of zero or less, which no OSM
   * entity has
   */
  @Test
  public void waitingAreaOverrideOfARelationOrAnIdNotAboveZeroIsRefused() {
    assertOneValueRefused("  waitingAreaModeAccessOverrides: {Relation: {18: [tram]}}\n");
    assertOneValueRefused("  osmWayOfWaitingAreaOverrides: {Node: {0: 17}}\n");
    assertOneValueRefused("  waitingAreaOfStopLocationOverrides: {Way: {14: -1}}\n");
  }

  /** A search radius of 0 m finds nothing, so it is refused for each search */
  @Test
  public void searchRadiusNotAboveZeroIsRefused() {
    for (var key : List.of("stopToWaitingAreaSearchRadiusMeters", "stationToWaitingAreaSearchRadiusMeters",
        "stationToParallelTracksSearchRadiusMeters", "ferryStopToFerryRouteSearchRadiusMeters")) {
      assertOneValueRefused("  " + key + ": 0\n");
    }
  }

  /**
   * A distance to the ideal stop point below 0 m is refused, while 0 m is kept: an end node or shape point is then only
   * used when it is the ideal stop point itself
   */
  @Test
  public void distanceToIdealStopPointBelowZeroIsRefused() {
    assertOneValueRefused("  maxEndNodeToIdealStopPointMeters: -1\n");
    assertOneValueRefused("  maxShapePointToIdealStopPointMeters: -1\n");

    var settings = fromPublicTransportSection(
        "  maxEndNodeToIdealStopPointMeters: 0\n  maxShapePointToIdealStopPointMeters: 0\n");
    assertEquals(0, settings.getMaxEndNodeToIdealStopPointMeters());
    assertEquals(0, settings.getMaxShapePointToIdealStopPointMeters());
  }
}

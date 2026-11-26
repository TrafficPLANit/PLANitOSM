package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.goplanit.converter.zoning.AccessEgressInjectionSettings;
import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.osm.converter.settings.OsmReaderSettings;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.containers.MapUtils;
import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsSection;
import org.goplanit.utils.settings.annotations.SettingsWrite;

import de.topobyte.osm4j.core.model.iface.EntityType;

/**
 * The public transport settings a settings file can set: only the methods of its keys, each write with the read of
 * the same key. {@link OsmPublicTransportReaderSettings} extends it with what code needs on top.
 * <p>
 * The values here say whether stops, platforms and stations are parsed, how far the parser searches to match them to
 * each other and to the network, and what it keeps of what it cannot connect. A settings file gives each of them by
 * its key, and code changes them through the same methods
 * </p>
 *
 * @author markr
 */
public abstract class OsmPublicTransportReaderYamlKeyedSettings extends OsmReaderSettings {

  /** see {@link #activateParser(boolean)} */
  private boolean isParserActive = DEFAULT_TRANSFER_PARSER_ACTIVE;

  /** see {@link #setRemoveDanglingZones(boolean)} */
  private boolean removeDanglingZones = DEFAULT_REMOVE_DANGLING_ZONES;

  /** see {@link #setRemoveDanglingTransferZoneGroups(boolean)} */
  private boolean removeDanglingTransferZoneGroups = DEFAULT_REMOVE_DANGLING_TRANSFER_ZONE_GROUPS;

  /** see {@link #setStopToWaitingAreaSearchRadiusMeters(Number)} */
  private double stopToWaitingAreaSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M;

  /** see {@link #setStationToWaitingAreaSearchRadiusMeters(Number)} */
  private double stationToWaitingAreaSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;

  /** see {@link #setStationToParallelTracksSearchRadiusMeters(Number)} */
  private double stationToParallelTracksSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M;

  /** see {@link #setMaxEndNodeToIdealStopPointMeters(Number)} */
  private double maxEndNodeToIdealStopPointMeters = ZoningConverterUtils.DEFAULT_MAX_END_NODE_TO_IDEAL_STOP_POINT_METERS;

  /** see {@link #setMaxShapePointToIdealStopPointMeters(Number)} */
  private double maxShapePointToIdealStopPointMeters =
      ZoningConverterUtils.DEFAULT_MAX_SHAPE_POINT_TO_IDEAL_STOP_POINT_METERS;

  /** see {@link #setConnectDanglingFerryStopToNearbyFerryRoute(boolean)} */
  private boolean connectDanglingFerryStopToNearbyFerryRoute = DEFAULT_CONNECT_DANGLING_FERRY_STOP_TO_FERRY_ROUTE;

  /** see {@link #setFerryStopToFerryRouteSearchRadiusMeters(Number)} */
  private double ferryStopToFerryRouteSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_FERRY_STOP_TO_FERRY_ROUTE_M;

  /** see {@link #getAccessEgressInjectionSettings()} */
  private AccessEgressInjectionSettings accessEgressInjectionSettings = new AccessEgressInjectionSettings();

  /** see {@link #excludeOsmNodeFromParsing(UserOverride)}, by OSM node id, none by default */
  private final Map<Long, UserOverride<Long>> osmNodesExcludedFromParsing = new TreeMap<>();

  /** see {@link #excludeOsmWayFromParsing(UserOverride)}, by OSM way id, none by default */
  private final Map<Long, UserOverride<Long>> osmWaysExcludedFromParsing = new TreeMap<>();

  /** see {@link #suppressStopAreaLoggingOfOsmRelation(UserOverride)}, by OSM relation id, none by default */
  private final Map<Long, UserOverride<Long>> suppressStopAreaLoggingOfOsmRelations = new TreeMap<>();

  /** see {@link #overrideWaitingAreaOfStopLocations(EntityType, Map)}, by waiting area type, none by default */
  private final Map<EntityType, Map<Long, UserOverride<Long>>> waitingAreaOfStopLocationOverrides =
      new EnumMap<>(EntityType.class);

  /** see {@link #overrideOsmWayOfWaitingAreas(EntityType, Map)}, by waiting area type, none by default */
  private final Map<EntityType, Map<Long, UserOverride<Long>>> osmWayOfWaitingAreaOverrides =
      new EnumMap<>(EntityType.class);

  /** see {@link #overrideWaitingAreaModeAccess(EntityType, Map)}, by waiting area type, none by default */
  private final Map<EntityType, Map<Long, UserOverride<Set<String>>>> waitingAreaModeAccessOverrides =
      new EnumMap<>(EntityType.class);

  /**
   * Constructor, every key at its default
   *
   * @param inputSource to use, null when not known yet
   * @param countryName the full country name to base the defaults on
   */
  protected OsmPublicTransportReaderYamlKeyedSettings(URL inputSource, String countryName) {
    super(inputSource, countryName);
  }

  /** by default the transfer parser is activated */
  public static boolean DEFAULT_TRANSFER_PARSER_ACTIVE = true;

  /** by default we are removing dangling zones */
  public static boolean DEFAULT_REMOVE_DANGLING_ZONES = true;

  /** by default we are removing dangling transfer zone groups */
  public static boolean DEFAULT_REMOVE_DANGLING_TRANSFER_ZONE_GROUPS = true;

  /**
   * default search radius in meters for mapping stops/platforms to stop positions on the networks when they
   * have no explicit reference
   */
  public static double DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M = 25;

  /**
   * default search radius in meters for mapping stations to platforms on the networks when they have no
   * explicit reference
   */
  public static double DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M = 35;

  /**
   * Default search radius in meters when trying to find parallel lines (tracks) for stand-alone stations
   */
  public static double DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;

  /** by default, we connect dangling ferry stops to the nearest ferry route */
  public static boolean DEFAULT_CONNECT_DANGLING_FERRY_STOP_TO_FERRY_ROUTE = true;

  /**
   * default search radius in meters for mapping ferry stops to ferry routes. When found and
   * {@link #isConnectDanglingFerryStopToNearbyFerryRoute()} is true then a new link to the nearest waterway
   * running the ferry is created to avoid the ferry stop to be dangling
   */
  public static double DEFAULT_SEARCH_RADIUS_FERRY_STOP_TO_FERRY_ROUTE_M = 100;

  /**
   * Put every key held here back to its default
   */
  @Override
  public void reset() {
    super.reset();
    isParserActive = DEFAULT_TRANSFER_PARSER_ACTIVE;
    removeDanglingZones = DEFAULT_REMOVE_DANGLING_ZONES;
    removeDanglingTransferZoneGroups = DEFAULT_REMOVE_DANGLING_TRANSFER_ZONE_GROUPS;
    stopToWaitingAreaSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M;
    stationToWaitingAreaSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;
    stationToParallelTracksSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M;
    maxEndNodeToIdealStopPointMeters = ZoningConverterUtils.DEFAULT_MAX_END_NODE_TO_IDEAL_STOP_POINT_METERS;
    maxShapePointToIdealStopPointMeters = ZoningConverterUtils.DEFAULT_MAX_SHAPE_POINT_TO_IDEAL_STOP_POINT_METERS;
    connectDanglingFerryStopToNearbyFerryRoute = DEFAULT_CONNECT_DANGLING_FERRY_STOP_TO_FERRY_ROUTE;
    ferryStopToFerryRouteSearchRadiusMeters = DEFAULT_SEARCH_RADIUS_FERRY_STOP_TO_FERRY_ROUTE_M;
    accessEgressInjectionSettings = new AccessEgressInjectionSettings();
    osmNodesExcludedFromParsing.clear();
    osmWaysExcludedFromParsing.clear();
    suppressStopAreaLoggingOfOsmRelations.clear();
    waitingAreaOfStopLocationOverrides.clear();
    osmWayOfWaitingAreaOverrides.clear();
    waitingAreaModeAccessOverrides.clear();
  }

  /** set the flag whether the public transport infrastructure should be parsed or not
   * @param activate when true activate, when false do not
   */
  @SettingsWrite(key = "active", level = SettingsKeyLevel.STANDARD,
      description = "Parse the public transport infrastructure: stops, platforms and stations")
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }

  /** verifies if the parser for these settings is active or not
   * @return true if active false otherwise
   */
  @SettingsRead(key = "active")
  public boolean isParserActive() {
    return this.isParserActive;
  }

  /**
   * indicate whether to remove dangling zones or not
   * @param removeDanglingZones yes or no
   */
  @SettingsWrite(key = "removeDanglingZones", level = SettingsKeyLevel.COMMON,
      description = "Remove transfer zones with no connectoid on the network")
  public void setRemoveDanglingZones(boolean removeDanglingZones) {
    this.removeDanglingZones = removeDanglingZones;
  }

  /** verify if dangling zones are removed from the final zoning
   * @return true when we are removing them, false otherwise
   */
  @SettingsRead(key = "removeDanglingZones")
  public boolean isRemoveDanglingZones() {
    return this.removeDanglingZones;
  }

  /**
   * indicate whether to remove dangling transfer zone groups or not
   * @param removeDanglingTransferZoneGroups yes or no
   */
  @SettingsWrite(key = "removeDanglingTransferZoneGroups", level = SettingsKeyLevel.COMMON,
      description = "Remove transfer zone groups, such as stations, that hold no transfer zone")
  public void setRemoveDanglingTransferZoneGroups(boolean removeDanglingTransferZoneGroups) {
    this.removeDanglingTransferZoneGroups = removeDanglingTransferZoneGroups;
  }

  /** verify if dangling transfer zone groups are removed from the final zoning
   *
   * @return true when we are removing them, false otherwise
   */
  @SettingsRead(key = "removeDanglingTransferZoneGroups")
  public boolean isRemoveDanglingTransferZoneGroups() {
    return this.removeDanglingTransferZoneGroups;
  }

  /** Set the maximum search radius used when trying to map stops/platforms to stop positions on the networks,
   * when no explicit relation is made known by OSM
   *
   * @param searchRadiusMeters to use, above 0
   * @throws IllegalArgumentException naming the value, the setting unchanged, when it is missing or not above 0
   */
  @SettingsWrite(key = "stopToWaitingAreaSearchRadiusMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "Furthest a stop location may be from the waiting area, such as a platform, it is matched to " +
          "when OSM does not relate them, in metres")
  public void setStopToWaitingAreaSearchRadiusMeters(Number searchRadiusMeters) {
    ExceptionUtils.throwIllegalSettingValueIf(searchRadiusMeters == null || !(searchRadiusMeters.doubleValue() > 0),
        "Stop to waiting area search radius", searchRadiusMeters, "above 0 m");
    this.stopToWaitingAreaSearchRadiusMeters = searchRadiusMeters.doubleValue();
  }

  /** Collect the maximum search radius set when trying to map stops/platforms to stop positions on the networks,
   * when no explicit relation is made known by OSM
   *
   * @return search radius in meters
   */
  @SettingsRead(key = "stopToWaitingAreaSearchRadiusMeters")
  public double getStopToWaitingAreaSearchRadiusMeters() {
    return this.stopToWaitingAreaSearchRadiusMeters;
  }

  /** Set the maximum search radius used when trying to map stations to platforms on the networks,
   * when no explicit relation is made known by OSM
   *
   * @param searchRadiusMeters to use, above 0
   * @throws IllegalArgumentException naming the value, the setting unchanged, when it is missing or not above 0
   */
  @SettingsWrite(key = "stationToWaitingAreaSearchRadiusMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "Furthest a waiting area may be from the station it is matched to when OSM does not relate " +
          "them, in metres")
  public void setStationToWaitingAreaSearchRadiusMeters(Number searchRadiusMeters) {
    ExceptionUtils.throwIllegalSettingValueIf(searchRadiusMeters == null || !(searchRadiusMeters.doubleValue() > 0),
        "Station to waiting area search radius", searchRadiusMeters, "above 0 m");
    this.stationToWaitingAreaSearchRadiusMeters = searchRadiusMeters.doubleValue();
  }

  /** Collect the maximum search radius set when trying to map stations to platforms on the networks,
   * when no explicit relation is made known by OSM
   *
   * @return search radius in meters
   */
  @SettingsRead(key = "stationToWaitingAreaSearchRadiusMeters")
  public double getStationToWaitingAreaSearchRadiusMeters() {
    return this.stationToWaitingAreaSearchRadiusMeters;
  }

  /** Set the maximum search radius used when trying to find parallel lines (tracks) for stand-alone stations
   *
   * @param searchRadiusMeters to use, above 0
   * @throws IllegalArgumentException naming the value, the setting unchanged, when it is missing or not above 0
   */
  @SettingsWrite(key = "stationToParallelTracksSearchRadiusMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "Furthest the tracks may be from a station without platforms that they are matched to, in metres")
  public void setStationToParallelTracksSearchRadiusMeters(Number searchRadiusMeters) {
    ExceptionUtils.throwIllegalSettingValueIf(searchRadiusMeters == null || !(searchRadiusMeters.doubleValue() > 0),
        "Station to parallel tracks search radius", searchRadiusMeters, "above 0 m");
    this.stationToParallelTracksSearchRadiusMeters = searchRadiusMeters.doubleValue();
  }

  /** Collect the maximum search radius set when trying to find parallel lines (tracks) for stand-alone stations
   *
   * @return search radius in meters
   */
  @SettingsRead(key = "stationToParallelTracksSearchRadiusMeters")
  public double getStationToParallelTracksSearchRadiusMeters() {
    return this.stationToParallelTracksSearchRadiusMeters;
  }

  /** Set the distance within which an end node of the link chosen for a stop without a known stop location is reused
   * rather than breaking the link, measured from the stop's ideal stop point: the point of the link closest to the
   * waiting area's centre. At 0 an end node is only reused when it is the ideal stop point itself
   *
   * @param maxEndNodeToIdealStopPointMeters to use, 0 or more
   * @throws IllegalArgumentException naming the value, the setting unchanged, when it is missing or below 0
   */
  @SettingsWrite(key = "maxEndNodeToIdealStopPointMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "Distance from its ideal stop point within which an end node of a link is used for a stop " +
          "without a known stop location, rather than breaking the link, in metres")
  public void setMaxEndNodeToIdealStopPointMeters(Number maxEndNodeToIdealStopPointMeters) {
    ExceptionUtils.throwIllegalSettingValueIf(
        maxEndNodeToIdealStopPointMeters == null || !(maxEndNodeToIdealStopPointMeters.doubleValue() >= 0),
        "End node to ideal stop point distance", maxEndNodeToIdealStopPointMeters, "0 m or more");
    this.maxEndNodeToIdealStopPointMeters = maxEndNodeToIdealStopPointMeters.doubleValue();
  }

  /** Collect the distance within which an end node of the link chosen for a stop without a known stop location is
   * reused rather than breaking the link, measured from the stop's ideal stop point
   *
   * @return distance in meters
   */
  @SettingsRead(key = "maxEndNodeToIdealStopPointMeters")
  public double getMaxEndNodeToIdealStopPointMeters() {
    return this.maxEndNodeToIdealStopPointMeters;
  }

  /** Set the distance within which an inner shape point of the link chosen for a stop without a known stop location
   * is used to break the link at, rather than breaking it right next to it, measured from the stop's ideal stop point.
   * At 0 a shape point is only used when it is the ideal stop point itself
   *
   * @param maxShapePointToIdealStopPointMeters to use, 0 or more
   * @throws IllegalArgumentException naming the value, the setting unchanged, when it is missing or below 0
   */
  @SettingsWrite(key = "maxShapePointToIdealStopPointMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "Distance from its ideal stop point within which a shape point of a link is used to break the " +
          "link for a stop without a known stop location, in metres")
  public void setMaxShapePointToIdealStopPointMeters(Number maxShapePointToIdealStopPointMeters) {
    ExceptionUtils.throwIllegalSettingValueIf(
        maxShapePointToIdealStopPointMeters == null || !(maxShapePointToIdealStopPointMeters.doubleValue() >= 0),
        "Shape point to ideal stop point distance", maxShapePointToIdealStopPointMeters, "0 m or more");
    this.maxShapePointToIdealStopPointMeters = maxShapePointToIdealStopPointMeters.doubleValue();
  }

  /** Collect the distance within which an inner shape point of the link chosen for a stop without a known stop
   * location is used to break the link at, measured from the stop's ideal stop point
   *
   * @return distance in meters
   */
  @SettingsRead(key = "maxShapePointToIdealStopPointMeters")
  public double getMaxShapePointToIdealStopPointMeters() {
    return this.maxShapePointToIdealStopPointMeters;
  }

  /** Decide whether to keep ferry stops (terminals) that are identified as dangling, i.e., do not reside on
   * any exiting road or waterway and connect them to the nearest ferry route (within reason).
   *
   * @param connectDanglingFerryStopToNearbyFerryRoute when true do this, when false do not
   */
  @SettingsWrite(key = "connectDanglingFerryStopToNearbyFerryRoute", level = SettingsKeyLevel.UNCOMMON,
      description = "Connect a ferry stop on no road or waterway to the nearest ferry route by a new link")
  public void setConnectDanglingFerryStopToNearbyFerryRoute(boolean connectDanglingFerryStopToNearbyFerryRoute) {
    this.connectDanglingFerryStopToNearbyFerryRoute = connectDanglingFerryStopToNearbyFerryRoute;
  }

  /**
   * Is flag for connecting dangling ferry stops to nearby ferry route set or not
   * @return true when active, false otherwise
   */
  @SettingsRead(key = "connectDanglingFerryStopToNearbyFerryRoute")
  public boolean isConnectDanglingFerryStopToNearbyFerryRoute() {
    return connectDanglingFerryStopToNearbyFerryRoute;
  }

  /**
   * Set the search radius for ferry stop to ferry route
   *
   * @param searchRadiusMeters to use, above 0
   * @throws IllegalArgumentException naming the value, the setting unchanged, when it is missing or not above 0
   */
  @SettingsWrite(key = "ferryStopToFerryRouteSearchRadiusMeters", level = SettingsKeyLevel.UNCOMMON,
      description = "Furthest a ferry route may be from a ferry stop it is connected to, in metres")
  public void setFerryStopToFerryRouteSearchRadiusMeters(Number searchRadiusMeters) {
    ExceptionUtils.throwIllegalSettingValueIf(searchRadiusMeters == null || !(searchRadiusMeters.doubleValue() > 0),
        "Ferry stop to ferry route search radius", searchRadiusMeters, "above 0 m");
    this.ferryStopToFerryRouteSearchRadiusMeters = searchRadiusMeters.doubleValue();
  }

  /**
   * Access to search radius for ferry stop to ferry route
   * @return search radius
   */
  @SettingsRead(key = "ferryStopToFerryRouteSearchRadiusMeters")
  public double getFerryStopToFerryRouteSearchRadiusMeters() {
    return ferryStopToFerryRouteSearchRadiusMeters;
  }

  /**
   * Exclude an OSM node from parsing as public transport infrastructure, such as a stop position, platform or station
   * whose tags are wrong in a way no setting can fix. Excluding the node again replaces the reason given before
   *
   * @param osmNodeId the OSM node id, with the reason when there is one
   * @throws IllegalArgumentException naming the id, storing nothing, when it is zero or less, which no OSM node has
   */
  @SettingsWrite(key = "osmNodesExcludedFromParsing", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON, description = "OSM nodes not to parse as public transport, by node id")
  public void excludeOsmNodeFromParsing(UserOverride<Long> osmNodeId) {
    PlanitOsmUtils.throwIfNotPositiveOsmId(osmNodeId.getValue());
    osmNodesExcludedFromParsing.put(osmNodeId.getValue(), osmNodeId);
  }

  /**
   * Every OSM node excluded from parsing with its reason, see {@link #excludeOsmNodeFromParsing(UserOverride)}
   *
   * @return the excluded nodes by OSM node id, in increasing node id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmNodesExcludedFromParsing")
  public Map<Long, UserOverride<Long>> getOsmNodesExcludedFromParsing() {
    return Collections.unmodifiableMap(osmNodesExcludedFromParsing);
  }

  /**
   * Exclude an OSM way from parsing as public transport infrastructure, such as a platform or station drawn as a line
   * or area whose tags are wrong in a way no setting can fix. Excluding the way again replaces the reason given before
   *
   * @param osmWayId the OSM way id, with the reason when there is one
   * @throws IllegalArgumentException naming the id, storing nothing, when it is zero or less, which no OSM way has
   */
  @SettingsWrite(key = "osmWaysExcludedFromParsing", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON, description = "OSM ways not to parse as public transport, by way id")
  public void excludeOsmWayFromParsing(UserOverride<Long> osmWayId) {
    PlanitOsmUtils.throwIfNotPositiveOsmId(osmWayId.getValue());
    osmWaysExcludedFromParsing.put(osmWayId.getValue(), osmWayId);
  }

  /**
   * Every OSM way excluded from parsing with its reason, see {@link #excludeOsmWayFromParsing(UserOverride)}
   *
   * @return the excluded ways by OSM way id, in increasing way id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmWaysExcludedFromParsing")
  public Map<Long, UserOverride<Long>> getOsmWaysExcludedFromParsing() {
    return Collections.unmodifiableMap(osmWaysExcludedFromParsing);
  }

  /**
   * Log nothing about an OSM stop area relation, such as one on the edge of the bounding area whose members outside it
   * give warnings that are valid but cannot be fixed. Suppressing the relation again replaces the reason given before
   *
   * @param osmRelationId the OSM relation id, with the reason when there is one
   * @throws IllegalArgumentException naming the id, storing nothing, when it is zero or less, which no OSM relation has
   */
  @SettingsWrite(key = "suppressStopAreaLoggingOfOsmRelations", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.RARE, description = "OSM stop area relations nothing is logged about, by relation id")
  public void suppressStopAreaLoggingOfOsmRelation(UserOverride<Long> osmRelationId) {
    PlanitOsmUtils.throwIfNotPositiveOsmId(osmRelationId.getValue());
    suppressStopAreaLoggingOfOsmRelations.put(osmRelationId.getValue(), osmRelationId);
  }

  /**
   * Every OSM stop area relation nothing is logged about, with its reason, see
   * {@link #suppressStopAreaLoggingOfOsmRelation(UserOverride)}
   *
   * @return the relations by OSM relation id, in increasing relation id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "suppressStopAreaLoggingOfOsmRelations")
  public Map<Long, UserOverride<Long>> getSuppressStopAreaLoggingOfOsmRelations() {
    return Collections.unmodifiableMap(suppressStopAreaLoggingOfOsmRelations);
  }

  /**
   * Set the waiting area, such as a platform, of OSM stop locations, where the parser would otherwise pick a wrong one
   * or none. A stop location has one waiting area, so a stop location given before, under either type, keeps only the
   * waiting area given here
   *
   * @param waitingAreaType the OSM entity type of the waiting areas, node or way
   * @param waitingAreaOsmIdByStopLocationOsmNodeId the OSM id of each waiting area, with the reason when there is one,
   *                                                by the OSM node id of its stop location
   * @throws IllegalArgumentException storing nothing, when the type is neither node nor way, or an OSM id is zero or
   * less, which no OSM entity has
   */
  @SettingsWrite(key = "waitingAreaOfStopLocationOverrides", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON, description = "The waiting area of OSM stop locations, by stop location " +
      "node id, under the waiting area's OSM entity type")
  public void overrideWaitingAreaOfStopLocations(EntityType waitingAreaType,
      Map<Long, UserOverride<Long>> waitingAreaOsmIdByStopLocationOsmNodeId) {
    PlanitOsmUtils.throwIfNeitherNodeNorWay(waitingAreaType);
    waitingAreaOsmIdByStopLocationOsmNodeId.forEach((stopLocationOsmNodeId, waitingAreaOsmId) -> {
      PlanitOsmUtils.throwIfNotPositiveOsmId(stopLocationOsmNodeId);
      PlanitOsmUtils.throwIfNotPositiveOsmId(waitingAreaOsmId.getValue());
    });

    /* a stop location has one waiting area, so one given under the other type before is dropped */
    waitingAreaOfStopLocationOverrides.values().forEach(
        waitingAreaOsmIds -> waitingAreaOsmIds.keySet().removeAll(waitingAreaOsmIdByStopLocationOsmNodeId.keySet()));
    waitingAreaOfStopLocationOverrides.values().removeIf(Map::isEmpty);
    waitingAreaOfStopLocationOverrides.computeIfAbsent(waitingAreaType, type -> new TreeMap<>())
        .putAll(waitingAreaOsmIdByStopLocationOsmNodeId);
  }

  /**
   * Every waiting area set for an OSM stop location with its reason, see
   * {@link #overrideWaitingAreaOfStopLocations(EntityType, Map)}
   *
   * @return the waiting area OSM ids by stop location OSM node id, in increasing id order, under the waiting area's
   * OSM entity type, only the types with an entry, unmodifiable at both levels, empty when none
   */
  @SettingsRead(key = "waitingAreaOfStopLocationOverrides")
  public Map<EntityType, Map<Long, UserOverride<Long>>> getWaitingAreaOfStopLocationOverrides() {
    return MapUtils.unmodifiableMapOfMaps(waitingAreaOfStopLocationOverrides);
  }

  /**
   * Set the OSM way the stop of each waiting area goes on, where the parser would otherwise pick a wrong one or none,
   * such as a platform between two roads. Giving a waiting area again replaces the way given before
   *
   * @param waitingAreaType the OSM entity type of the waiting areas, node or way
   * @param osmWayIdByWaitingAreaOsmId the OSM way id, with the reason when there is one, by waiting area OSM id
   * @throws IllegalArgumentException storing nothing, when the type is neither node nor way, or an OSM id is zero or
   * less, which no OSM entity has
   */
  @SettingsWrite(key = "osmWayOfWaitingAreaOverrides", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON, description = "The OSM way the stop of a waiting area goes on, by waiting " +
      "area id, under its OSM entity type")
  public void overrideOsmWayOfWaitingAreas(EntityType waitingAreaType,
      Map<Long, UserOverride<Long>> osmWayIdByWaitingAreaOsmId) {
    PlanitOsmUtils.throwIfNeitherNodeNorWay(waitingAreaType);
    osmWayIdByWaitingAreaOsmId.forEach((waitingAreaOsmId, osmWayId) -> {
      PlanitOsmUtils.throwIfNotPositiveOsmId(waitingAreaOsmId);
      PlanitOsmUtils.throwIfNotPositiveOsmId(osmWayId.getValue());
    });
    osmWayOfWaitingAreaOverrides.computeIfAbsent(waitingAreaType, type -> new TreeMap<>())
        .putAll(osmWayIdByWaitingAreaOsmId);
  }

  /**
   * Every OSM way set for the stop of a waiting area with its reason, see
   * {@link #overrideOsmWayOfWaitingAreas(EntityType, Map)}
   *
   * @return the OSM way ids by waiting area OSM id, in increasing id order, under the waiting area's OSM entity type,
   * only the types with an entry, unmodifiable at both levels, empty when none
   */
  @SettingsRead(key = "osmWayOfWaitingAreaOverrides")
  public Map<EntityType, Map<Long, UserOverride<Long>>> getOsmWayOfWaitingAreaOverrides() {
    return MapUtils.unmodifiableMapOfMaps(osmWayOfWaitingAreaOverrides);
  }

  /**
   * Set the only OSM modes of waiting areas, where their tags give wrong ones or none. Giving a waiting area again
   * replaces the modes given before
   *
   * @param waitingAreaType the OSM entity type of the waiting areas, node or way
   * @param osmModesByWaitingAreaOsmId the OSM modes, with the reason when there is one, by waiting area OSM id
   * @throws IllegalArgumentException storing nothing, when the type is neither node nor way, or an OSM id is zero or
   * less, which no OSM entity has
   */
  @SettingsWrite(key = "waitingAreaModeAccessOverrides", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON, description = "The only OSM modes of waiting areas, by waiting area id, " +
      "under its OSM entity type")
  public void overrideWaitingAreaModeAccess(EntityType waitingAreaType,
      Map<Long, UserOverride<Set<String>>> osmModesByWaitingAreaOsmId) {
    PlanitOsmUtils.throwIfNeitherNodeNorWay(waitingAreaType);
    osmModesByWaitingAreaOsmId.keySet().forEach(PlanitOsmUtils::throwIfNotPositiveOsmId);
    waitingAreaModeAccessOverrides.computeIfAbsent(waitingAreaType, type -> new TreeMap<>())
        .putAll(osmModesByWaitingAreaOsmId);
  }

  /**
   * Every set of OSM modes set for a waiting area with its reason, see
   * {@link #overrideWaitingAreaModeAccess(EntityType, Map)}
   *
   * @return the OSM modes by waiting area OSM id, in increasing id order, under the waiting area's OSM entity type,
   * only the types with an entry, unmodifiable at both levels, empty when none
   */
  @SettingsRead(key = "waitingAreaModeAccessOverrides")
  public Map<EntityType, Map<Long, UserOverride<Set<String>>>> getWaitingAreaModeAccessOverrides() {
    return MapUtils.unmodifiableMapOfMaps(waitingAreaModeAccessOverrides);
  }

  /**
   * The settings of connecting the stops to the network for access and egress, each at its default until changed
   *
   * @return the settings, to change in place
   */
  @SettingsSection(key = "accessEgressInjection", level = SettingsKeyLevel.UNCOMMON, description =
      "Linking stops to the nearby network so passengers can reach them, such as ferry stops to the land network")
  public AccessEgressInjectionSettings getAccessEgressInjectionSettings() {
    return accessEgressInjectionSettings;
  }
}

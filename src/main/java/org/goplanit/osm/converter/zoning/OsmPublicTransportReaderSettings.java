package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsSection;

import de.topobyte.osm4j.core.model.iface.EntityType;
import org.goplanit.utils.misc.Triple;
import org.goplanit.utils.misc.UrlUtils;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and transfer related infrastructure
 * 
 * @author markr
 *
 */
@SettingsSection(key = "publicTransport", level = SettingsKeyLevel.COMMON, description =
    "The settings of parsing public transport: stops, platforms and stations, how they are matched, and corrections")
public class OsmPublicTransportReaderSettings extends OsmPublicTransportReaderYamlKeyedSettings {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmPublicTransportReaderSettings.class.getCanonicalName());

  /**
   * The default buffer distance when looking for edges within a distance of the closest edge to create connectoids
   * (stop_locations) on for transfer zones. When multiple candidates are close, just selecting the closest can lead to
   * problems. It is only used to exclude clearly inadequate options.
   */
  public static final double DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M = 8;
    
  /** Constructor using default (Global) locale
  */
  public OsmPublicTransportReaderSettings() {
    this((URL) null, CountryNames.GLOBAL);
  }   
  
  /** Constructor with user defined source locale 
   * @param countryName to base source locale on
   */
  public OsmPublicTransportReaderSettings(String countryName) {
    this((URL) null, countryName);
  }

  /**
   /** Constructor with user defined source locale
   *
   * @param inputSource to use, expected local file location
   * @param countryName the full country name to use speed limit data for, see also the
   *                    OsmSpeedLimitDefaultsByCountry class
   */
  public OsmPublicTransportReaderSettings(String inputSource, String countryName) {
    this(UrlUtils.createFromLocalAbsoluteOrRelativePath(Path.of(inputSource)), countryName);
  }
  
  /** Constructor with user defined source locale 
   * 
   * @param inputSource to extract OSM pt information from
   * @param countryName to base source locale on
   */
  public OsmPublicTransportReaderSettings(URL inputSource, String countryName) {
    super(inputSource, countryName);
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("OSM Public Transport Reader Settings"));
    super.logSettings(level);
  }
  // USER CONFIGURATION

  /** Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a stop_position, station, platform, etc.
   * tagged as a node
   * 
   * @param osmIds to exclude (int or long)
   * @throws IllegalArgumentException naming the first id that is zero or less, the nodes before it excluded
   */
  @CoveredBySettingsKey("osmNodesExcludedFromParsing")
  public void excludeOsmNodesById(final Number... osmIds) {
    excludeOsmNodesById(Arrays.asList(osmIds));
  }

  /** Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example
   * when we know the original coding or tagging is problematic for a stop_position, station, platform, etc.
   * tagged as a node
   *
   * @param osmIds to exclude (int or long)
   * @throws IllegalArgumentException naming the first id that is zero or less, the nodes before it excluded
   */
  @CoveredBySettingsKey("osmNodesExcludedFromParsing")
  public void excludeOsmNodesById(final Collection<Number> osmIds) {
    osmIds.forEach(this::excludeOsmNodeById);
  }

  /** Same as {@link #excludeOsmNodeFromParsing(UserOverride)}, without a reason
   *
   * @param osmId to exclude (int or long)
   */
  @CoveredBySettingsKey("osmNodesExcludedFromParsing")
  public void excludeOsmNodeById(final Number osmId) {
    excludeOsmNodeFromParsing(UserOverride.of(osmId.longValue()));
  }

  /** Provide OSM ids of ways that we are not to parse as public transport infrastructure, for example
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way
   * (line, or polygon)
   *
   * @param osmIds to exclude (int or long)
   * @throws IllegalArgumentException naming the first id that is zero or less, the ways before it excluded
   */
  @CoveredBySettingsKey("osmWaysExcludedFromParsing")
  public void excludeOsmWaysById(final Number... osmIds) {
    excludeOsmWaysById(Arrays.asList(osmIds));
  }

  /** Provide OSM ids of ways that we are not to parse as public transport infrastructure, for example
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way
   * (line or polygon)
   *
   * @param osmIds to exclude (int or long)
   * @throws IllegalArgumentException naming the first id that is zero or less, the ways before it excluded
   */
  @CoveredBySettingsKey("osmWaysExcludedFromParsing")
  public void excludeOsmWaysById(final Collection<Number> osmIds) {
    osmIds.forEach(this::excludeOsmWayById);
  }

  /** Same as {@link #excludeOsmWayFromParsing(UserOverride)}, without a reason
   *
   * @param osmId to exclude (int or long)
   */
  @CoveredBySettingsKey("osmWaysExcludedFromParsing")
  public void excludeOsmWayById(final Number osmId) {
    excludeOsmWayFromParsing(UserOverride.of(osmId.longValue()));
  }

  /** Verify if osm id is an excluded node for pt infrastructure parsing
   *
   * @param osmId to verify (int or long)
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmNode(Number osmId) {
    return getOsmNodesExcludedFromParsing().containsKey(osmId.longValue());
  }

  /** Verify if osm id is an excluded way for pt infrastructure parsing
   *
   * @param osmId to verify (int or long)
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmWay(Number osmId) {
    return getOsmWaysExcludedFromParsing().containsKey(osmId.longValue());
  }

  /**
   * Provide explicit mapping for stop_locations (by osm node id) to the waiting area, e.g., platform, pole, station,
   * halt, stop, etc. (by entity type and osm id). This overrides the parser's mapping and maps the stop location to
   * this osm entity straight away, which can avoid warnings or wrong mapping of stop locations with tagging errors.
   * 
   * @param stopLocationOsmNodeId osm node id of stop location (int or long)
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param waitingAreaOsmId osm id of waiting area (platform, pole, etc.) (int or long)
   * @throws IllegalArgumentException storing nothing, when the type is neither node nor way, or an OSM id is zero or
   * less, which no OSM entity has
   */
  @CoveredBySettingsKey("waitingAreaOfStopLocationOverrides")
  public void overwriteWaitingAreaOfStopLocation(
          final Number stopLocationOsmNodeId, final EntityType waitingAreaEntityType, final Number waitingAreaOsmId) {
    overrideWaitingAreaOfStopLocations(waitingAreaEntityType,
        Map.of(stopLocationOsmNodeId.longValue(), UserOverride.of(waitingAreaOsmId.longValue())));
  }

  /** multiples in triple form for {@link #overwriteWaitingAreaOfStopLocation(Number, EntityType, Number)}
   * @param overwriteTriples triples to provide (stopLocationOsmId, waitingAreaEntityType, waitingAreasOsmId)
   */
  @SafeVarargs
  @CoveredBySettingsKey("waitingAreaOfStopLocationOverrides")
  public final void overwriteWaitingAreaOfStopLocations(Triple<Number, EntityType, Number>... overwriteTriples) {
    Arrays.stream(overwriteTriples).forEach(
        t -> overwriteWaitingAreaOfStopLocation(t.first(), t.second(), t.third()));
  }

  /** Verify if stop location's OSM id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify (int or long)
   * @return true when present, false otherwise
   */
  public boolean isOverwriteWaitingAreaOfStopLocation(final Number stopLocationOsmNodeId) {
    return getOverwrittenWaitingAreaOfStopLocation(stopLocationOsmNodeId) != null;
  }
  
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify (int or long)
   * @return pair reflecting the entity type and waiting area osm id, null if not present
   */
  public Pair<EntityType,Long> getOverwrittenWaitingAreaOfStopLocation(final Number stopLocationOsmNodeId) {
    if(stopLocationOsmNodeId == null){
      return null;
    }
    for (var waitingAreaOsmIdsOfType : getWaitingAreaOfStopLocationOverrides().entrySet()) {
      var waitingAreaOsmId = waitingAreaOsmIdsOfType.getValue().get(stopLocationOsmNodeId.longValue());
      if (waitingAreaOsmId != null) {
        return Pair.of(waitingAreaOsmIdsOfType.getKey(), waitingAreaOsmId.getValue());
      }
    }
    return null;
  }
  
  /** Verify if the waiting area is used as the designated waiting area for a stop location by means of a user
   * explicitly stating it as such
   * 
   * @param waitingAreaType of the waiting area
   * @param osmWaitingAreaId to use (int or long)
   * @return true when waiting area is defined for a stop location as designated waiting area, false otherwise
   */
  public boolean isWaitingAreaOfStopLocationOverwritten(
          final EntityType waitingAreaType, final Number osmWaitingAreaId) {
    if(osmWaitingAreaId == null){
      return false;
    }
    return getWaitingAreaOfStopLocationOverrides().getOrDefault(waitingAreaType, Map.of()).values().stream().anyMatch(
        waitingAreaOsmId -> waitingAreaOsmId.getValue() == osmWaitingAreaId.longValue());
  }
  
  /**
   * Provide explicit mapping for waiting areas (platform, bus_stop, pole, station) to a nominated osm way (by OSM id).
   * This overrides the parser's mapping and forces it to create a stop location on the nominated OSM way. Only use
   * when the platform has no stop_location and no stop_location maps to this waiting area. A waiting area given here
   * cannot also be used in overriding stop_location mappings.
   * 
   * @param waitingAreaOsmId osm node id of stop location (int or long)
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param osmWayId osm id of waiting area (platform, pole, etc.) (int or long)
   * @throws IllegalArgumentException storing nothing, when a parameter is null, the type is neither node nor way, or
   * an OSM id is zero or less, which no OSM entity has
   */
  @CoveredBySettingsKey("osmWayOfWaitingAreaOverrides")
  public void overwriteWaitingAreaNominatedOsmWayForStopLocation(
      final Number waitingAreaOsmId, final EntityType waitingAreaEntityType, final Number osmWayId) {
    ExceptionUtils.throwIllegalArgumentIf(osmWayId == null || waitingAreaOsmId == null || waitingAreaEntityType == null,
        "Unable to overwrite waiting area nominated OsmWay for stop location as one of the parameters is null");
    overrideOsmWayOfWaitingAreas(waitingAreaEntityType,
        Map.of(waitingAreaOsmId.longValue(), UserOverride.of(osmWayId.longValue())));
  }
  
  /** Verify if waiting area's osm id is marked for overwritten osm way mapping
   * 
   * @param waitingAreaOsmId to verify (int or long)
   * @param waitingAreaEntityType type of waiting area
   * @return true when present, false otherwise
   */
  public boolean hasWaitingAreaNominatedOsmWayForStopLocation(
          final Number waitingAreaOsmId, final EntityType waitingAreaEntityType) {
    return getWaitingAreaNominatedOsmWayForStopLocation(waitingAreaOsmId, waitingAreaEntityType) != null;
  }
  
  /** collect waiting area's osm way id to use for identifying most logical stop_location (connectoid)
   * 
   * @param waitingAreaOsmId to collect (int or long)
   * @param waitingAreaEntityType type of waiting area
   * @return osm way id, null if not available
   */
  public Long getWaitingAreaNominatedOsmWayForStopLocation(
          final Number waitingAreaOsmId, EntityType waitingAreaEntityType) {
    if(waitingAreaOsmId == null || waitingAreaEntityType == null){
      return null;
    }
    return UserOverride.getValueOrNull(getOsmWayOfWaitingAreaOverrides()
        .getOrDefault(waitingAreaEntityType, Map.of()).get(waitingAreaOsmId.longValue()));
  }
  
  /**
   * Suppress any logging for given stop area relation ids
   *
   * @param osmStopAreaRelationIds relation ids to suppress logging for
   * @throws IllegalArgumentException naming the first id that is zero or less, the relations before it suppressed
   */
  @CoveredBySettingsKey("suppressStopAreaLoggingOfOsmRelations")
  public void suppressOsmRelationStopAreaLogging(Number... osmStopAreaRelationIds) {
    suppressOsmRelationStopAreaLogging(Arrays.asList(osmStopAreaRelationIds));
  }

  /**
   * Same as {@link #suppressStopAreaLoggingOfOsmRelation(UserOverride)} for each relation, without reasons; null ids
   * are skipped
   *
   * @param osmStopAreaRelationIds relation ids to suppress logging for, nothing when null
   * @throws IllegalArgumentException naming the first id that is zero or less, the relations before it suppressed
   */
  @CoveredBySettingsKey("suppressStopAreaLoggingOfOsmRelations")
  public void suppressOsmRelationStopAreaLogging(List<Number> osmStopAreaRelationIds) {
    if(osmStopAreaRelationIds == null){
      return;
    }

    for(var osmStopAreaRelationId : osmStopAreaRelationIds) {
      if(osmStopAreaRelationId == null){
        continue;
      }
      suppressStopAreaLoggingOfOsmRelation(UserOverride.of(osmStopAreaRelationId.longValue()));
    }
  }

  /**
   * Check if stop area relation id logging is suppressed
   *
   * @param osmStopAreaRelationId to check
   * @return  true when suppressed, false otherwise
   */
  public boolean isSuppressOsmRelationStopAreaLogging(Number osmStopAreaRelationId) {
    if(osmStopAreaRelationId == null){
      return false;
    }
    return getSuppressStopAreaLoggingOfOsmRelations().containsKey(osmStopAreaRelationId.longValue());
  }

  /**
   * flag for connecting ferry stops to nearby land network if not already connected
   * @return true when active, false otherwise
   */
  public boolean isConnectFerryStopsToNearbyLandNetwork() {
    return getAccessEgressInjectionSettings().isConnectFerryStopsToNearbyLandNetwork();
  }

  /** Decide whether to connect ferry stops to nearby land network if not already connected
   *
   * @param connectFerryStopToNearbyLandNetwork when true do this, when false do not
   */
  @CoveredBySettingsKey("accessEgressInjection.connectFerryStopsToNearbyLandNetwork")
  public void setConnectFerryStopsToNearbyLandNetwork(boolean connectFerryStopToNearbyLandNetwork) {
    getAccessEgressInjectionSettings().setConnectFerryStopsToNearbyLandNetwork(connectFerryStopToNearbyLandNetwork);
  }

  /**
   * Access to search radius for ferry stop to land network
   * @return search radius
   */
  public double getFerryStopToLandNetworkSearchRadiusMeters() {
    return getAccessEgressInjectionSettings().getFerryStopToLandNetworkSearchRadiusMeters();
  }

  /**
   * flag for connecting rail based stops to nearby road network if not already connected
   * @return true when active, false otherwise
   */
  public boolean isConnectRailBasedStopsToPassengerNetwork() {
    return getAccessEgressInjectionSettings().isConnectRailBasedStopsToPassengerNetwork();
  }

  /** Decide whether to connect rail based stops to nearby road network if not already connected
   *
   * @param connectRailBasedStopToPassengerNetwork when true do this, when false do not
   */
  @CoveredBySettingsKey("accessEgressInjection.connectRailBasedStopsToPassengerNetwork")
  public void setConnectRailBasedStopsToPassengerNetwork(boolean connectRailBasedStopToPassengerNetwork) {
    getAccessEgressInjectionSettings().setConnectRailBasedStopsToPassengerNetwork(
        connectRailBasedStopToPassengerNetwork);
  }

  /**
   * Access to search radius for rail based stop to road network
   * @return search radius
   */
  public double getRailBasedStopToPassengerNetworkSearchRadiusMeters() {
    return getAccessEgressInjectionSettings().getRailBasedStopToPassengerNetworkSearchRadiusMeters();
  }

  /**
   * flag for connecting bus based stops to nearby passenger network if not already connected
   * @return true when active, false otherwise
   */
  public boolean isConnectBusBasedStopsToPassengerNetwork() {
    return getAccessEgressInjectionSettings().isConnectBusBasedStopsToPassengerNetwork();
  }

  /** Decide whether to connect bus based stops to nearby road network if not already connected for access egress modes
   *
   * @param connectBusBasedStopToPassengerNetwork when true do this, when false do not
   */
  @CoveredBySettingsKey("accessEgressInjection.connectBusBasedStopsToPassengerNetwork")
  public void setConnectBusBasedStopsToPassengerNetwork(boolean connectBusBasedStopToPassengerNetwork) {
    getAccessEgressInjectionSettings().setConnectBusBasedStopsToPassengerNetwork(
        connectBusBasedStopToPassengerNetwork);
  }


  /**
   * Overwrite the mode access for a given waiting area
   *
   * @param osmId to overwrite for
   * @param osmEntityType to use
   * @param osmModes to set as eligible
   * @throws IllegalArgumentException storing nothing, when the type is neither node nor way, or the OSM id is zero or
   * less, which no OSM entity has
   */
  @CoveredBySettingsKey("waitingAreaModeAccessOverrides")
  public void overwriteWaitingAreaModeAccess(Number osmId, EntityType osmEntityType, String... osmModes){
    overrideWaitingAreaModeAccess(osmEntityType,
        Map.of(osmId.longValue(), UserOverride.of(new TreeSet<>(Arrays.asList(osmModes)))));
  }

  /**
   * Verify if the mode access for a given waiting area is overwritten
   *
   * @param osmId to verify
   * @param osmEntityType to use
   * @return true when present false otherwise
   */
  public boolean isOverwriteWaitingAreaModeAccess(Number osmId, EntityType osmEntityType){
    return getOverwrittenWaitingAreaModeAccess(osmId, osmEntityType) != null;
  }

  /**
   * Get the overwritten OSM modes for a given waiting area (if defined)
   *
   * @param osmId to collect for
   * @param osmEntityType of the OSM id
   * @return overwritten OSM modes to apply, null if not present
   */
  public SortedSet<String> getOverwrittenWaitingAreaModeAccess(Number osmId, EntityType osmEntityType){
    var osmModes = UserOverride.getValueOrNull(
        getWaitingAreaModeAccessOverrides().getOrDefault(osmEntityType, Map.of()).get(osmId.longValue()));
    return osmModes != null ? new TreeSet<>(osmModes) : null;
  }

}

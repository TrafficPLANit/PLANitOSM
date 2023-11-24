package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.goplanit.osm.converter.OsmReaderSettings;
import org.goplanit.utils.misc.Pair;

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
public class OsmPublicTransportReaderSettings extends OsmReaderSettings {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmPublicTransportReaderSettings.class.getCanonicalName());

  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private boolean isParserActive = DEFAULT_TRANSFER_PARSER_ACTIVE;
  
  /** flag indicating if we should remove (transfer) zones that are dangling, i.e., all (transfer) zones that do not have any
   * registered connectoids to an underlying network (layer) */
  private boolean removeDanglingZones = DEFAULT_REMOVE_DANGLING_ZONES;
  
  /** flag indicating if we should remove transfer zone groups that are dangling, i.e., all transfer zone groups that do not have any
   * registered transfer zones */
  private boolean removeDanglingTransferZoneGroups = DEFAULT_REMOVE_DANGLING_TRANSFER_ZONE_GROUPS;  

  /** the maximum search radius used when trying to map stops/platforms to stop positions on the networks, when no explicit
   * relation is made known by OSM */
  private double searchRadiusPlatformToStopInMeters = DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M;
  
  /** the maximum search radius used when trying to map stations to platforms on the networks, when no explicit
   * relation is made known by OSM */
  private double searchRadiusStationToPlatformInMeters = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;
  
  /** the maximum search radius used when trying to to find parallel platforms for stand-alone stations */
  private double searchRadiusStationToParallelTracksInMeters = DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M;
  
  /**
   * exclude osm node, ways from parsing when identified as problematic for some reason. they can be stops, platforms, stations, etc.
   */
  private final Map<EntityType,Set<Long>> excludedPtOsmEntities = new HashMap<EntityType,Set<Long>>();
  
  /**
   * Provide explicit mapping for stop_locations (by osm node id) to the waiting area, e.g., platform, pole, station, halt, stop, etc. (by entity type and osm id).
   * This overrides the parser's mapping functionality and immediately maps the stop location to this osm entity. 
   */
  private final Map<Long,Pair<EntityType,Long>> overwritePtStopLocation2WaitingAreaMapping = new HashMap<>();

  /**
   * Provide explicit mapping for waiting areas, e.g. platforms, poles, stations (by osm node id) to the osm way (by osm id) to place stop_locations on (connectoids)
   * This overrides the parser's functionality to automatically attempt to identify the correct stop_location. Note that this should only be used for waiting areas that
   * could not successfully be mapped to a stop_location and therefore have no known stop_location in the network. 
   * Further one cannot override a waiting area here that is also part of a stop_location to waiting area override. 
   */
  private final Map<EntityType, Map<Long,Long>> overwritePtWaitingArea2OsmWayMapping = new HashMap<>();

  /**
   * track overwritten mode access values for specific OSM waiting areas. Can be used in case the OSM file is incorrectly tagged which causes problems
   * in the memory model. Here one can be manually overwrite the allowable modes for this particular waiting area.
   */
  protected final Map<EntityType, Map<Long, SortedSet<String>>> overwriteWaitingAreaModeAccess = new HashMap<>(
      Map.ofEntries(
          Map.entry(EntityType.Node, new HashMap<>()),
          Map.entry(EntityType.Way, new HashMap<>())
  ));

  /** all registered osmRelation ids will not trigger any logging */
  private final Set<Long> suppressStopAreaLogging = new HashSet<>();

  /** Option to keep ferry stops (terminals) that are identified as dangling, i.e., do not reside on
   * any exiting road or waterway.
   * <p> </p>
   */
  private boolean connectDanglingFerryStopToNearbyFerryRoute = DEFAULT_CONNECT_DANGLING_FERRY_STOP_TO_FERRY_ROUTE;

  /**
   * Search radius in meters for mapping ferry stops to ferry routes. When found and {@link #isConnectDanglingFerryStopToNearbyFerryRoute()} is true
   * then a new link to the nearest waterway running the ferry is created to avoid the ferry stop to be dangling
   */
  private double  searchRadiusFerryStopToFerryRouteMeters = DEFAULT_SEARCH_RADIUS_FERRY_STOP_TO_FERRY_ROUTE_M;
    
  /** by default the transfer parser is activated */
  public static boolean DEFAULT_TRANSFER_PARSER_ACTIVE = true;
  
  /** by default we are removing dangling zones */
  public static boolean DEFAULT_REMOVE_DANGLING_ZONES = true;
  
  /** by default we are removing dangling transfer zone groups */
  public static boolean DEFAULT_REMOVE_DANGLING_TRANSFER_ZONE_GROUPS = true;
    
  /**
   * default search radius in meters for mapping stops/platforms to stop positions on the networks when they have no explicit reference
   */
  public static double DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M = 25;
  
  /**
   * default search radius in meters for mapping stations to platforms on the networks when they have no explicit reference
   */
  public static double DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M = 35;  
  
  /**
   * Default search radius in meters when trying to find parallel lines (tracks) for stand-alone stations 
   */
  public static double DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;
  
  /**
   * The default buffer distance when looking for edges within a distance of the closest edge to create connectoids (stop_locations) on for transfer zones. 
   * In case multiple candidates are close just selecting the closest can lead to problems.This is only used as a way to exclude clearly inadequate options.
   */
  public static double DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M = 8;

  /** by default we connect dangling ferry stops to the nearest ferry route */
  public static boolean DEFAULT_CONNECT_DANGLING_FERRY_STOP_TO_FERRY_ROUTE = true;

  /**
   * default search radius in meters for mapping ferry stops to ferry routes. When found and {@link #isConnectDanglingFerryStopToNearbyFerryRoute()} is true
   * then a new link to the nearest waterway running the ferry is created to avoid the ferry stop to be dangling
   */
  public static double DEFAULT_SEARCH_RADIUS_FERRY_STOP_TO_FERRY_ROUTE_M = 100;
            
  
  /** Constructor using default (Global) locale
  */
  public OsmPublicTransportReaderSettings() {
    super();
  }   
  
  /** Constructor with user defined source locale 
   * @param countryName to base source locale on
   */
  public OsmPublicTransportReaderSettings(String countryName) {
    super(countryName);
  }

  /**
   /** Constructor with user defined source locale
   *
   * @param inputSource to use, expected local file location
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   */
  public OsmPublicTransportReaderSettings(String inputSource, String countryName) {
    this(UrlUtils.createFromLocalPath(Path.of(inputSource)), countryName);
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
  public void reset() {
    //todo
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    LOGGER.info(String.format("Public transport infrastructure parser activated: %s", isParserActive()));

    if(isParserActive()) {
      LOGGER.info(String.format("OSM (transfer) zoning input file: %s", getInputSource()));
      if(hasBoundingPolygon()) {
        LOGGER.info(String.format("Bounding polygon set to: %s",getBoundingPolygon().toString()));
      }

      LOGGER.info(String.format("Stop location to waiting area search radius: %.2fm", getStopToWaitingAreaSearchRadiusMeters()));
      LOGGER.info(String.format("Station location to waiting area search radius: %.2fm", getStationToWaitingAreaSearchRadiusMeters()));
      LOGGER.info(String.format("Station location to parallel tracks search radius: %.2fm", getStationToParallelTracksSearchRadiusMeters()));
      LOGGER.info(String.format("Remove dangling transfer zones: %s", isRemoveDanglingZones()));
      LOGGER.info(String.format("Remove dangling transfer zone groups: %s", isRemoveDanglingTransferZoneGroups()));
      LOGGER.info(String.format("Connect dangling ferry stops to nearby ferry routes (if present): %s", connectDanglingFerryStopToNearbyFerryRoute));
      LOGGER.info(String.format("Ferry stop to ferry route search radius: %.2fm", getFerryStopToFerryRouteSearchRadiusMeters()));
    }
  }

  // USER CONFIGURATION

  /** set the flag whether the public transport infrastructure should be parsed or not
   * @param activate when true activate, when false do not
   */
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }  
  
  /** verifies if the parser for these settings is active or not
   * @return true if active false otherwise
   */
  public boolean isParserActive() {
    return this.isParserActive;
  }   
  
  /** Set the maximum search radius used when trying to map stops/platforms to stop positions on the networks, 
   * when no explicit relation is made known by OSM 
   * 
   * @param searchRadiusInMeters to use
   */
  public void setStopToWaitingAreaSearchRadiusMeters(Number searchRadiusInMeters) {
    this.searchRadiusPlatformToStopInMeters = searchRadiusInMeters.doubleValue();
  }
  
  /** Collect the maximum search radius set when trying to map stops/platforms to stop positions on the networks, 
   * when no explicit relation is made known by OSM 
   * 
   * @return search radius in meters
   */
  public double getStopToWaitingAreaSearchRadiusMeters() {
    return this.searchRadiusPlatformToStopInMeters;
  }  
  
  /** Set the maximum search radius used when trying to map stations to platforms on the networks, 
   * when no explicit relation is made known by OSM 
   * 
   * @param searchRadiusInMeters to use
   */
  public void setStationToWaitingAreaSearchRadiusMeters(Number searchRadiusInMeters) {
    this.searchRadiusStationToPlatformInMeters = searchRadiusInMeters.doubleValue();
  }
  
  /** Collect the maximum search radius set when trying to map stations to platforms on the networks, 
   * when no explicit relation is made known by OSM 
   * 
   * @return search radius in meters
   */
  public double getStationToWaitingAreaSearchRadiusMeters() {
    return this.searchRadiusStationToPlatformInMeters;
  }  
  
  /** Set the maximum search radius used when trying to find parallel lines (tracks) for stand-alone stations 
   * 
   * @param searchRadiusInMeters to use
   */
  public void setStationToParallelTracksSearchRadiusMeters(Number searchRadiusInMeters) {
    this.searchRadiusStationToParallelTracksInMeters = searchRadiusInMeters.doubleValue();
  }
  
  /** Collect the maximum search radius set when trying to find parallel lines (tracks) for stand-alone stations 
   * 
   * @return search radius in meters
   */
  public double getStationToParallelTracksSearchRadiusMeters() {
    return this.searchRadiusStationToParallelTracksInMeters;
  }      
    
  /** Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a stop_position, station, platform, etc. tagged as a node
   * 
   * @param osmIds to exclude (int or long)
   */
  public void excludeOsmNodesById(final Number... osmIds) {
    excludeOsmNodesById(Arrays.asList(osmIds));
  }
  
  /** Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a stop_position, station, platform, etc. tagged as a node
   * 
   * @param osmIds to exclude (int or long)
   */
  public void excludeOsmNodesById(final Collection<Number> osmIds) {
    osmIds.forEach( osmId -> excludeOsmNodeById(osmId));
  }
  
  /** Provide OSM id of node that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a stop_position, station, platform, etc. tagged as a node
   * 
   * @param osmId to exclude (int or long)
   */
  public void excludeOsmNodeById(final Number osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Node, new HashSet<>());
    excludedPtOsmEntities.get(EntityType.Node).add(osmId.longValue());
  }  
  
  /** Provide OSM ids of ways that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way (line, or polygon)
   * 
   * @param osmIds to exclude (int or long)
   */
  public void excludeOsmWaysById(final Number... osmIds) {
    excludeOsmWaysById(Arrays.asList(osmIds));
  }
  
  /** Provide OSM ids of ways that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way (line or polygon)
   * 
   * @param osmIds to exclude (int or long)
   */
  public void excludeOsmWaysById(final Collection<Number> osmIds) {
    osmIds.forEach( osmId -> excludeOsmWayById(osmId));
  }   
  
  /** Provide OSM id of way that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way (line or polygon)
   * 
   * @param osmId to exclude
   */
  public void excludeOsmWayById(final Number osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Way, new HashSet<>());
    excludedPtOsmEntities.get(EntityType.Way).add(osmId.longValue());
  }    
  
  /** Verify if osm id is an excluded node for pt infrastructure parsing
   * 
   * @param osmId to verify (int or long)
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmNode(Number osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Node, new HashSet<>());
    return excludedPtOsmEntities.get(EntityType.Node).contains(osmId.longValue());
  }   
  
  /** Verify if osm id is an excluded node for pt infrastructure parsing
   * 
   * @param osmId to verify (int or long)
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmWay(Number osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Way, new HashSet<>());
    return excludedPtOsmEntities.get(EntityType.Way).contains(osmId.longValue());
  }

  /**
   * Provide explicit mapping for stop_locations (by osm node id) to the waiting area, e.g., platform, pole, station, halt, stop, etc. (by entity type and osm id).
   * This overrides the parser's mapping functionality and immediately maps the stop location to this osm entity. Can be useful to avoid warnings or wrong mapping of
   * stop locations in case of tagging errors.
   * 
   * @param stopLocationOsmNodeId osm node id of stop location (int or long)
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param waitingAreaOsmId osm id of waiting area (platform, pole, etc.) (int or long)
   */
  public void overwriteWaitingAreaOfStopLocation(final Number stopLocationOsmNodeId, final EntityType waitingAreaEntityType, final Number waitingAreaOsmId) {
    overwritePtStopLocation2WaitingAreaMapping.put(stopLocationOsmNodeId.longValue(), Pair.of(waitingAreaEntityType, waitingAreaOsmId.longValue()));    
  }

  /** multiples in triple form for {@link #overwriteWaitingAreaOfStopLocation(Number, EntityType, Number)}
   * @param overwriteTriples triples to provide (stopLocationOsmId, waitingAreaEntityType, waitingAreasOsmId)
   */
  public void overwriteWaitingAreaOfStopLocations(Triple<Number, EntityType, Number>... overwriteTriples) {
    Arrays.stream(overwriteTriples).forEach(t -> overwriteWaitingAreaOfStopLocation(t.first(), t.second(), t.third()));
  }

  /** Verify if stop location's OSM id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify (int or long)
   * @return true when present, false otherwise
   */
  public boolean isOverwriteWaitingAreaOfStopLocation(final Number stopLocationOsmNodeId) {
    return overwritePtStopLocation2WaitingAreaMapping.containsKey(stopLocationOsmNodeId.longValue());
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
    return overwritePtStopLocation2WaitingAreaMapping.get(stopLocationOsmNodeId.longValue());
  }  
  
  /** Verify if the waiting area is used as the designated waiting area for a stop location by means of a user explicitly stating it as such
   * 
   * @param waitingAreaType of the waiting area
   * @param osmWaitingAreaId to use (int or long)
   * @return true when waiting area is defined for a stop location as designated waiting area, false otherwise
   */
  public boolean isWaitingAreaOfStopLocationOverwritten(final EntityType waitingAreaType, final Number osmWaitingAreaId) {
    if(osmWaitingAreaId == null){
      return false;
    }

    for( Entry<Long, Pair<EntityType, Long>> entry : overwritePtStopLocation2WaitingAreaMapping.entrySet()) {
      if(entry.getValue().first().equals(waitingAreaType) && entry.getValue().second().equals(osmWaitingAreaId.longValue())) {
        return true;
      }
    }
    return false;
  }  
  
  /**
   * Provide explicit mapping for waiting areas (platform, bus_stop, pole, station) to a nominated osm way (by OSM id).
   * This overrides the parser's mapping functionality and forces the parser to create a stop location on the nominated OSM way.
   * Only use in case the platform has no stop_location and no stop_location maps to this waiting area.
   * One cannot use this method and also use this waiting area in overriding stop_location mappings.
   * 
   * @param waitingAreaOsmId osm node id of stop location (int or long)
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param osmWayId osm id of waiting area (platform, pole, etc.) (int or long)
   */
  public void overwriteWaitingAreaNominatedOsmWayForStopLocation(
      final Number waitingAreaOsmId, final EntityType waitingAreaEntityType, final Number osmWayId) {
    if(osmWayId == null || waitingAreaOsmId == null || waitingAreaEntityType==null){
      LOGGER.severe("unable to overwrite waiting area nominated OsmWay for stop location as one of the parameters is null");
    }

    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<>());
    overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).put(waitingAreaOsmId.longValue(), osmWayId.longValue());
  }  
  
  /** Verify if waiting area's osm id is marked for overwritten osm way mapping
   * 
   * @param waitingAreaOsmId to verify (int or long)
   * @param waitingAreaEntityType type of waiting area
   * @return true when present, false otherwise
   */
  public boolean hasWaitingAreaNominatedOsmWayForStopLocation(final Number waitingAreaOsmId, final EntityType waitingAreaEntityType) {
    if(waitingAreaOsmId == null || waitingAreaEntityType == null){
      return false;
    }
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<>());
    return overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).containsKey(waitingAreaOsmId.longValue());
  } 
  
  /** collect waiting area's osm way id to use for identifying most logical stop_location (connectoid)
   * 
   * @param waitingAreaOsmId to collect (int or long)
   * @param waitingAreaEntityType type of waiting area
   * @return osm way id, null if not available
   */
  public Long getWaitingAreaNominatedOsmWayForStopLocation(final Number waitingAreaOsmId, EntityType waitingAreaEntityType) {
    if(waitingAreaOsmId == null || waitingAreaEntityType == null){
      return null;
    }
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<>());
    return overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).get(waitingAreaOsmId.longValue());
  }   
  
  /**
   * indicate whether to remove dangling zones or not
   * @param removeDanglingZones yes or no
   */
  public void setRemoveDanglingZones(boolean removeDanglingZones) {
    this.removeDanglingZones = removeDanglingZones;
  }
  
  /** verify if dangling zones are removed from the final zoning
   * @return true when we are removing them, false otherwise
   */
  public boolean isRemoveDanglingZones() {
    return this.removeDanglingZones;
  } 
  
  /**
   * indicate whether to remove dangling transfer zone groups or not
   * @param removeDanglingTransferZoneGroups yes or no
   */
  public void setRemoveDanglingTransferZoneGroups(boolean removeDanglingTransferZoneGroups) {
    this.removeDanglingTransferZoneGroups = removeDanglingTransferZoneGroups;
  }
  
  /** verify if dangling transfer zone groups are removed from the final zoning
   * 
   * @return true when we are removing them, false otherwise 
   */
  public boolean isRemoveDanglingTransferZoneGroups() {
    return this.removeDanglingTransferZoneGroups;
  }


  /**
   * Suppress any logging for given stop area relation ids
   *
   * @param osmStopAreaRelationIds relation ids to suppress logging for
   */
  public void suppressOsmRelationStopAreaLogging(Number... osmStopAreaRelationIds) {
    suppressOsmRelationStopAreaLogging(Arrays.asList(osmStopAreaRelationIds));
  }

  /**
   * Suppress any logging for given stop area relation ids
   *
   * @param osmStopAreaRelationIds relation ids to suppress logging for
   */
  public void suppressOsmRelationStopAreaLogging(List<Number> osmStopAreaRelationIds) {
    if(osmStopAreaRelationIds == null){
      return;
    }

    for(var osmStopAreaRelationId : osmStopAreaRelationIds) {
      if(osmStopAreaRelationId == null){
        continue;
      }
      suppressStopAreaLogging.add(osmStopAreaRelationId.longValue());
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
    return suppressStopAreaLogging.contains(osmStopAreaRelationId.longValue());
  }


  /**
   * Is flag for connecting dangling ferry stops to nearby ferry route set or not
   * @return true when active, false otherwise
   */
  public boolean isConnectDanglingFerryStopToNearbyFerryRoute() {
    return connectDanglingFerryStopToNearbyFerryRoute;
  }

  /** Decide whether to keep ferry stops (terminals) that are identified as dangling, i.e., do not reside on
   * any exiting road or waterway and connect them to the nearest ferry route (within reason).
   *
   * @param connectDanglingFerryStopToNearbyFerryRoute when true do this, when false do not
   */
  public void setConnectDanglingFerryStopToNearbyFerryRoute(boolean connectDanglingFerryStopToNearbyFerryRoute) {
    this.connectDanglingFerryStopToNearbyFerryRoute = connectDanglingFerryStopToNearbyFerryRoute;
  }

  public double getFerryStopToFerryRouteSearchRadiusMeters() {
    return searchRadiusFerryStopToFerryRouteMeters;
  }

  public void setFerryStopToFerryRouteSearchRadiusMeters(Number searchRadiusFerryStopToFerryRouteMeters) {
    if(searchRadiusFerryStopToFerryRouteMeters == null){
      LOGGER.severe("Unable to set ferry stop to ferry route search radius as parameter is null");
      return;
    }
    this.searchRadiusFerryStopToFerryRouteMeters = searchRadiusFerryStopToFerryRouteMeters.doubleValue();
  }

  /**
   * Overwrite the mode access for a given waiting area
   *
   * @param osmId to overwrite for
   * @param osmEntityType to use
   * @param osmModes to set as eligible
   */
  public void overwriteWaitingAreaModeAccess(Number osmId, EntityType osmEntityType, String... osmModes){
    var overwritesByType = overwriteWaitingAreaModeAccess.get(osmEntityType);
    if(overwritesByType == null){
      LOGGER.severe(String.format("IGNORE: Unsupported OSM entity type (%s) for registering overwritten modes access for waiting areas", osmEntityType.toString()));
    }
    overwritesByType.put(osmId.longValue(), new TreeSet(Arrays.asList(osmModes)));
  }

  /**
   * Verify if the mode access for a given waiting area is overwritten
   *
   * @param osmId to verify
   * @param osmEntityType to use
   * @return true when present false otherwise
   */
  public boolean isOverwriteWaitingAreaModeAccess(Number osmId, EntityType osmEntityType){
    var overwritesByType = overwriteWaitingAreaModeAccess.get(osmEntityType);
    if(overwritesByType == null){
      return false;
    }
    return overwritesByType.containsKey(osmId.longValue());
  }

  /**
   * Get the overwritten OSM modes for a given waiting area (if defined)
   *
   * @param osmId to collect for
   * @param osmEntityType of the OSM id
   * @return overwritten OSM modes to apply, null if not present
   */
  public SortedSet<String> getOverwrittenWaitingAreaModeAccess(Number osmId, EntityType osmEntityType){
    var overwritesByType = overwriteWaitingAreaModeAccess.get(osmEntityType);
    if(overwritesByType == null){
      return null;
    }
    return overwritesByType.get(osmId.longValue());
  }

}

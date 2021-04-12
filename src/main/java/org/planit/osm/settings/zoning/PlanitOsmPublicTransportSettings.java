package org.planit.osm.settings.zoning;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.planit.utils.misc.Pair;

import de.topobyte.osm4j.core.model.iface.EntityType;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and tranfer related infrastructure 
 * 
 * @author markr
 *
 */
public class PlanitOsmPublicTransportSettings {
  
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
  private final Map<Long,Pair<EntityType,Long>> overwritePtStopLocation2WaitingAreaMapping = new HashMap<Long,Pair<EntityType,Long>>();
    
  /**
   * Provide explicit mapping for waiting areas, e.g. platforms, poles, stations (by osm node id) to the osm way (by osm id) to place stop_locations on (connectoids)
   * This overrides the parser's functionality to automatically attempt to identify the correct stop_location. Note that this should only be used for waiting areas that
   * could not successfully be mapped to a stop_location and therefore have no known stop_location in the network. 
   * Further one cannot override a waiting area here that is also part of a stop_location to waiting area override. 
   */
  private final Map<EntityType, Map<Long,Long>> overwritePtWaitingArea2OsmWayMapping = new HashMap<EntityType, Map<Long,Long>>();
    
  /** by default the transfer parser is deactivated */
  public static boolean DEFAULT_TRANSFER_PARSER_ACTIVE = false;
  
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
   * In case candidates are so close just selecting the closest can lead to problems. By identifying multiple candidates via this buffer, we can then use more sophisticated ways than proximity
   * to determine the best candidate 
   */
  public static double DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M = 5;
            
  
  /** Constructor
  */
  public PlanitOsmPublicTransportSettings() {
  }    
  
  /** set the flag whether or not the highways should be parsed or not
   * @param activate
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
  public void setStopToWaitingAreaSearchRadiusMeters(double searchRadiusInMeters) {
    this.searchRadiusPlatformToStopInMeters = searchRadiusInMeters;
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
  public void setStationToWaitingAreaSearchRadiusMeters(double searchRadiusInMeters) {
    this.searchRadiusStationToPlatformInMeters = searchRadiusInMeters;
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
  public void setStationToParallelTracksSearchRadiusMeters(double searchRadiusInMeters) {
    this.searchRadiusStationToParallelTracksInMeters = searchRadiusInMeters;
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
   * @param osmIds to exlude
   */
  public void excludeOsmNodesById(Long... osmIds) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Node, new HashSet<Long>());
    excludedPtOsmEntities.get(EntityType.Node).addAll(Arrays.asList(osmIds));
  }
  
  /** Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a stop_position, station, platform, etc. tagged as a node
   * 
   * @param osmIds to exclude
   */
  public void excludeOsmNodesById(Set<Long> osmIds) {
    excludeOsmNodesById(osmIds.toArray(new Long[osmIds.size()]));
  }  
  
  /** Provide OSM ids of ways that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way (line, or polygon)
   * 
   * @param osmIds to exlude
   */
  public void excludeOsmWaysById(Long... osmIds) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Way, new HashSet<Long>());
    excludedPtOsmEntities.get(EntityType.Way).addAll(Arrays.asList(osmIds));
  }
  
  /** Provide OSM ids of nodes that we are not to parse as public transport infrastructure, for example 
   * when we know the original coding or tagging is problematic for a station, platform, etc. tagged as a way (line or polygon)
   * 
   * @param osmIds to exclude
   */
  public void excludeOsmWaysById(Set<Long> osmIds) {
    excludeOsmWaysById(osmIds.toArray(new Long[osmIds.size()]));
  }   
  
  /** Verify if osm id is an excluded node for pt infrastructure parsing
   * 
   * @param osmId to verify
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmNode(long osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Node, new HashSet<Long>());
    return excludedPtOsmEntities.get(EntityType.Node).contains(osmId);
  }   
  
  /** Verify if osm id is an excluded node for pt infrastructure parsing
   * 
   * @param osmId to verify
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmWay(long osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Way, new HashSet<Long>());
    return excludedPtOsmEntities.get(EntityType.Way).contains(osmId);
  }

  /**
   * Provide explicit mapping for stop_locations (by osm node id) to the waiting area, e.g., platform, pole, station, halt, stop, etc. (by entity type and osm id).
   * This overrides the parser's mapping functionality and immediately maps the stop location to this osm entity. Can be useful to avoid warnings or wrong mapping of
   * stop locations in case of tagging errors.
   * 
   * @param stopLocationOsmNodeId osm node id of stop location
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param waitingAreaOsmId osm id of waiting area (platform, pole, etc.)
   */
  public void overwriteStopLocationWaitingArea(Long stopLocationOsmNodeId, EntityType waitingAreaEntityType, Long waitingAreaOsmId) {
    overwritePtStopLocation2WaitingAreaMapping.put(stopLocationOsmNodeId, Pair.of(waitingAreaEntityType, waitingAreaOsmId));    
  }  
  
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify
   * @return true when present, false otherwise
   */
  public boolean isOverwriteStopLocationWaitingArea(Long stopLocationOsmNodeId) {
    return overwritePtStopLocation2WaitingAreaMapping.containsKey(stopLocationOsmNodeId);    
  } 
  
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify
   * @return pair reflecting the entity type and waiting area osm id, null if not present
   */
  public Pair<EntityType,Long> getOverwrittenStopLocationWaitingArea(Long stopLocationOsmNodeId) {
    return overwritePtStopLocation2WaitingAreaMapping.get(stopLocationOsmNodeId);    
  }  
  
  /** Verify if the witing area is used as the designated waiting area for a stop location by means of a user explicitly stating it as such
   * 
   * @param waitingAreaType of the waiting area
   * @return true when waiting area is defined for a stop location as designated waiting area, false otherwise
   */
  public boolean isWaitingAreaStopLocationOverwritten(EntityType waitingAreaType, Long osmWaitingAreaId) {
    for( Entry<Long, Pair<EntityType, Long>> entry : overwritePtStopLocation2WaitingAreaMapping.entrySet()) {
      if(entry.getValue().first().equals(waitingAreaType) && entry.getValue().second().equals(osmWaitingAreaId)) {
        return true;
      }
    }
    return false;
  }  
  
  /**
   * Provide explicit mapping for waiting areas (platform, bus_stop, pole, station) to a nominated osm way (by osm id).
   * This overrides the parser's mapping functionality and forces the parser to create a stop location on the nominated osm way. Only use in case the platform has no stop_location
   * and no stop_location maps to this waiting area. One cannot use this method and also use this waiting area in overriding stop_location mappings.
   * 
   * @param stopLocationOsmNodeId osm node id of stop location
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param waitingAreaOsmId osm id of waiting area (platform, pole, etc.)
   */
  public void overwriteWaitingAreaNominatedOsmWayForStopLocation(Long waitingAreaOsmId, EntityType waitingAreaEntityType, Long OsmWayId) {
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<Long,Long>());
    overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).put(waitingAreaOsmId, OsmWayId);    
  }  
  
  /** Verify if waiting area's osm id is marked for overwritten osm way mapping
   * 
   * @param waitingAreaOsmId to verify
   * @param waitingAreaEntityType type of waiting area
   * @return true when present, false otherwise
   */
  public boolean hasWaitingAreaNominatedOsmWayForStopLocation(Long waitingAreaOsmId, EntityType waitingAreaEntityType) {
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<Long,Long>());
    return overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).containsKey(waitingAreaOsmId);    
  } 
  
  /** collect waiting area's osm way id to use for identifying most logical stop_location (connectoid)
   * 
   * @param waitingAreaOsmId to collect
   * @param waitingAreaEntityType type of waiting area
   * @return osm way id, null if not available
   */
  public Long getWaitingAreaNominatedOsmWayForStopLocation(Long waitingAreaOsmId, EntityType waitingAreaEntityType) {
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<Long,Long>());
    return overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).get(waitingAreaOsmId);    
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
  
}

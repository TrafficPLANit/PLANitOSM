package org.planit.osm.settings.zoning;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
  private final Map<Long,Pair<EntityType,Long>> overwritePtStopMapping = new HashMap<Long,Pair<EntityType,Long>>();  
  
  /** by default the transfer parser is deactivated */
  public static boolean DEFAULT_TRANSFER_PARSER_ACTIVE = false;
    
  /**
   * default search radius in meters for mapping stops/platforms to stop positions on the networks when they have no explicit reference
   */
  public static double DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M = 20;
  
  /**
   * default search radius in meters for mapping stations to platforms on the networks when they have no explicit reference
   */
  public static double DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M = 30;  
  
  /**
   * Default search radius in meters when trying to find parallel lines (tracks) for stand-alone stations 
   */
  public static double DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;
            
  
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
    overwritePtStopMapping.put(stopLocationOsmNodeId, Pair.of(waitingAreaEntityType, waitingAreaOsmId));    
  }  
  
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify
   */
  public boolean isOverwriteStopLocationWaitingArea(Long stopLocationOsmNodeId) {
    return overwritePtStopMapping.containsKey(stopLocationOsmNodeId);    
  } 
  
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify
   */
  public Pair<EntityType,Long> getOverwrittenStopLocationWaitingArea(Long stopLocationOsmNodeId) {
    return overwritePtStopMapping.get(stopLocationOsmNodeId);    
  }   

}

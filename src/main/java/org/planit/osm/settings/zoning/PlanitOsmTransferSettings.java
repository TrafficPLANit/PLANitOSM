package org.planit.osm.settings.zoning;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and tranfer related infrastructure 
 * 
 * @author markr
 *
 */
public class PlanitOsmTransferSettings {
  
  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private boolean isParserActive = DEFAULT_TRANSFER_PARSER_ACTIVE;

  /** the maximum search radius used when trying to map stops/platforms to stop positions on the networks, when no explicit
   * relation is made known by OSM */
  protected double searchRadiusPlatformToStopInMeters = DEFAULT_SEARCH_RADIUS_PLATFORM2STOP_M;
  
  /** the maximum search radius used when trying to map stations to platforms on the networks, when no explicit
   * relation is made known by OSM */
  protected double searchRadiusStationToPlatformInMeters = DEFAULT_SEARCH_RADIUS_STATION2PLATFORM_M;
  
  /** the maximum search radius used when trying to to find parallel platforms for stand-alone stations */
  protected double searchRadiusStationToParallelTracksInMeters = DEFAULT_SEARCH_RADIUS_STATION_PARALLEL_TRACKS_M;  
  
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
  
  /** allow users to provide OSM node ids that we are not to parse as stop_positions (including inferred stop_positinos not tagged as such), for example when we know the original coding or tagging is problematic */
  protected final Set<Long>  excludedStopPositions = new HashSet<Long>();
        
  
  /** Constructor
  */
  public PlanitOsmTransferSettings() {
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
  
  /** Provide OSM ids of stop_positions that we are not to parse, for example 
   * when we know the original coding or tagging is problematic
   * @param osmIds
   */
  public void excludeStopPositions(Long... osmIds) {
    excludedStopPositions.addAll(Arrays.asList(osmIds));
  }
  
  /** Provide OSM ids of stop_positions that we are not to parse, for example 
   * when we know the original coding or tagging is problematic
   * @param osmIds
   */
  public void excludeStopPositions(Set<Long> osmIds) {
    excludedStopPositions.addAll(osmIds);
  }  
  
  /** Verify if osm id is an excluded stop_position
   * @param osmId
   * @return true when excluded false otherwise
   */
  public boolean isExcludedStopPosition(long osmId) {
    return excludedStopPositions.contains(osmId);
  }     

}

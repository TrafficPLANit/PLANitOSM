package org.goplanit.osm.converter.zoning;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import org.goplanit.osm.converter.OsmReaderSettings;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.utils.misc.Pair;

import de.topobyte.osm4j.core.model.iface.EntityType;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Point;

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
  
  // transferred data/settings from network reader

  /**
   * the network data required to perform successful parsing of zones, to be obtained from the osm network reader
   * after parsing the reference network
   */
  private OsmNetworkToZoningReaderData network2ZoningData;  
  
  // configuration settings
  
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
   * In case multiple candidates are close just selecting the closest can lead to problems.This is only used as a way to exclude clearly inadequate options.
   */
  public static double DEFAULT_CLOSEST_EDGE_SEARCH_BUFFER_DISTANCE_M = 8;
            
  
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
    this(UrlUtils.createFromPath(Path.of(inputSource)), countryName);
  }
  
  /** Constructor with user defined source locale 
   * 
   * @param inputSource to extract OSM pt information from
   * @param countryName to base source locale on
   */
  public OsmPublicTransportReaderSettings(URL inputSource, String countryName) {
    super(inputSource, countryName);
  }  
  

  /** Constructor with user defined source locale
   * 
   * @param inputSource to use
   * @param countryName to base source locale on
   * @param network2ZoningData to use
   */
  public OsmPublicTransportReaderSettings(URL inputSource, String countryName, OsmNetworkToZoningReaderData network2ZoningData) {
    super(inputSource, countryName);
    setNetworkDataForZoningReader(network2ZoningData);
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    //TODO
  }
  
  // TRANSFERRED FROM NETWORK READER


  /** allow one to set the network data required for parsing osm zoning data on the zoning reader
   * 
   * @param network2ZoningData to use based on network reader that parsed the used reference network
   */
  public void setNetworkDataForZoningReader(OsmNetworkToZoningReaderData network2ZoningData) {
    this.network2ZoningData = network2ZoningData;
  }   
  
  /** collect the network data required for parsing osm zoning data on the zoning reader
   * 
   * @return network2ZoningData based on network reader that parsed the used reference network
   */
  public OsmNetworkToZoningReaderData  getNetworkDataForZoningReader() {
    return this.network2ZoningData;
  }  
  
  // USER CONFIGURATION

  /** set the flag whether or not the public transport infrastructure should be parsed or not
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
    excludedPtOsmEntities.putIfAbsent(EntityType.Node, new HashSet<Long>());
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
    return excludedPtOsmEntities.get(EntityType.Node).contains(osmId);
  }   
  
  /** Verify if osm id is an excluded node for pt infrastructure parsing
   * 
   * @param osmId to verify (int or long)
   * @return true when excluded false otherwise
   */
  public boolean isExcludedOsmWay(Number osmId) {
    excludedPtOsmEntities.putIfAbsent(EntityType.Way, new HashSet<>());
    return excludedPtOsmEntities.get(EntityType.Way).contains(osmId);
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
  public void overwriteStopLocationWaitingArea(final Number stopLocationOsmNodeId, final EntityType waitingAreaEntityType, final Number waitingAreaOsmId) {
    overwritePtStopLocation2WaitingAreaMapping.put(stopLocationOsmNodeId.longValue(), Pair.of(waitingAreaEntityType, waitingAreaOsmId.longValue()));    
  } 
    
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify (int or long)
   * @return true when present, false otherwise
   */
  public boolean isOverwriteStopLocationWaitingArea(final Number stopLocationOsmNodeId) {
    return overwritePtStopLocation2WaitingAreaMapping.containsKey(stopLocationOsmNodeId);    
  } 
  
  /** Verify if stop location's osm id is marked for overwritten platform mapping
   * 
   * @param stopLocationOsmNodeId to verify (int or long)
   * @return pair reflecting the entity type and waiting area osm id, null if not present
   */
  public Pair<EntityType,Long> getOverwrittenStopLocationWaitingArea(final Number stopLocationOsmNodeId) {
    return overwritePtStopLocation2WaitingAreaMapping.get(stopLocationOsmNodeId);    
  }  
  
  /** Verify if the witing area is used as the designated waiting area for a stop location by means of a user explicitly stating it as such
   * 
   * @param waitingAreaType of the waiting area
   * @param osmWaitingAreaId to use (int or long)
   * @return true when waiting area is defined for a stop location as designated waiting area, false otherwise
   */
  public boolean isWaitingAreaStopLocationOverwritten(final EntityType waitingAreaType, final Number osmWaitingAreaId) {
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
   * @param waitingAreaOsmId osm node id of stop location (int or long)
   * @param waitingAreaEntityType entity type of waiting area to map to
   * @param OsmWayId osm id of waiting area (platform, pole, etc.) (int or long)
   */
  public void overwriteWaitingAreaNominatedOsmWayForStopLocation(final Number waitingAreaOsmId, final EntityType waitingAreaEntityType, final Number OsmWayId) {
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<>());
    overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).put(waitingAreaOsmId.longValue(), OsmWayId.longValue());    
  }  
  
  /** Verify if waiting area's osm id is marked for overwritten osm way mapping
   * 
   * @param waitingAreaOsmId to verify (int or long)
   * @param waitingAreaEntityType type of waiting area
   * @return true when present, false otherwise
   */
  public boolean hasWaitingAreaNominatedOsmWayForStopLocation(final Number waitingAreaOsmId, final EntityType waitingAreaEntityType) {
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<>());
    return overwritePtWaitingArea2OsmWayMapping.get(waitingAreaEntityType).containsKey(waitingAreaOsmId);    
  } 
  
  /** collect waiting area's osm way id to use for identifying most logical stop_location (connectoid)
   * 
   * @param waitingAreaOsmId to collect (int or long)
   * @param waitingAreaEntityType type of waiting area
   * @return osm way id, null if not available
   */
  public Long getWaitingAreaNominatedOsmWayForStopLocation(final Number waitingAreaOsmId, EntityType waitingAreaEntityType) {
    overwritePtWaitingArea2OsmWayMapping.putIfAbsent(waitingAreaEntityType, new HashMap<>());
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

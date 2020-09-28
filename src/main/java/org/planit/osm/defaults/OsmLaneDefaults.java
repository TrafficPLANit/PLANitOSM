package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRailWayTags;
import org.planit.utils.misc.Pair;

/**
 * configure and retrieve the default configuration for the number of lanes for various osm way types (these are the total lanes on a link covering both directions.
 * The "default" defaults for highway tags originate from https://wiki.openstreetmap.org/wiki/Key:lanes, while the "default" defaults for railways, i.e., the number of tracks
 * is based on https://wiki.openstreetmap.org/wiki/Key:railway#Tracks.
 * 
 * @author markr
 *
 */
public class OsmLaneDefaults implements Cloneable {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmLaneDefaults.class.getCanonicalName());  
  
  /** store the road based defaults */
  protected static Map<String, Integer> defaultRoadLanesPerDirection = new HashMap<String, Integer>();  
    
  /** store the defaults  for class instance */
  protected Map<String, Integer> lanesPerDirection;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>> speedLimitDefaultsByCountry = new HashMap<String,Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>>();
  
  /** lanes per direction if not configured */
  protected int lanesPerDirectionIfUnspecified  = DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED;
  
  /** railway tracks per direction if not configured */
  protected int tracksPerDirectionIfUnspecified  = DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED;  
      
  /* initialise */
  static {    
    populateDefaultLanesPerDirection();       
  }
  
  /**
   * Initialise the defaults to use based on "common sense" as outlined in https://wiki.openstreetmap.org/wiki/Key:lanes
   */
  protected static void populateDefaultLanesPerDirection(){
    /* 2 lanes for larger roads */
    defaultRoadLanesPerDirection.put(OsmHighwayTags.MOTORWAY, 2);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.MOTORWAY_LINK, 2);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.TRUNK, 2);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.TRUNK_LINK, 2);   
    /* 1 lane for not too large roads */
    defaultRoadLanesPerDirection.put(OsmHighwayTags.RESIDENTIAL, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.TERTIARY, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.TERTIARY_LINK, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.SECONDARY, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.SECONDARY_LINK, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.PRIMARY, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.PRIMARY_LINK, 1);
    /* 1 lane for even smaller roads, while the specification also lists 1 lane in total, PLANit has no use for this, so it is ignored */
    defaultRoadLanesPerDirection.put(OsmHighwayTags.UNCLASSIFIED, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.PEDESTRIAN, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.SERVICE, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.TRACK, 1);
    defaultRoadLanesPerDirection.put(OsmHighwayTags.PATH, 1);
  }
  
  /** in case no mapping between highway type and number of lanes is present, use this */
  public static final int DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED = 1;
  
  /** store the rail default */
  public static final int DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED = 1;  
  
  /**
   * Constructor
   */
  public OsmLaneDefaults() {
    /* clone so adjustments can be made locally if so desired */
    this.lanesPerDirection = new HashMap<String, Integer>(defaultRoadLanesPerDirection);
  }
  
  /** Copy constructor
   * 
   * @param osmLaneDefaults to copy
   */
  public OsmLaneDefaults(OsmLaneDefaults osmLaneDefaults) {
    this.lanesPerDirection = new HashMap<String, Integer>(osmLaneDefaults.lanesPerDirection);
    this.lanesPerDirectionIfUnspecified = osmLaneDefaults.lanesPerDirectionIfUnspecified;
    this.tracksPerDirectionIfUnspecified = osmLaneDefaults.tracksPerDirectionIfUnspecified;
  }

  /** Overwrite current default
   * @param type highway type
   * @param defaultNumberOfLanesPerDirection the new default
   * @return old value if any, null if not present
   */
  public Integer setDefaultDirectionalLanesByHighwayType(String type, Integer defaultNumberOfLanesPerDirection) {
    return lanesPerDirection.put(type,defaultNumberOfLanesPerDirection);
  }
  
  /** collect the number of lanes based on the highway type, e.g. highway=type, for any direction (not total).
   * In case no number of lanes is specified for the type, we revert to the missing default
   * 
   * @param osmWayValue highway type value
   * @return number of lanes for this type (if any), otherwise null is returned
   */
  public Integer getDefaultDirectionalLanesByHighwayType(String osmWayValue) {
    if(lanesPerDirection.containsKey(osmWayValue)) {
      return lanesPerDirection.get(osmWayValue); 
    }else {
      LOGGER.warning(
          String.format("highway type %s has no number of default lanes associated with it, reverting to missing default: %d", osmWayValue, DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED));
      return DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED;
    }
  }
  
  /** Overwrite current default
   * @param defaultNumberOfTracksPerDirection the new default
   */
  public void setDefaultDirectionalRailwayTracks(Integer defaultNumberOfTracksPerDirection) {
    this.tracksPerDirectionIfUnspecified = defaultNumberOfTracksPerDirection;
  }    
  
  /** collect the default number of tracks for railways (in one direction) 
   * @return defaultNumberOfTracksPerDirection in case not explicitly specified
   */
  public Integer getDefaultDirectionalRailwayTracks() {
    return this.tracksPerDirectionIfUnspecified;
  }   
  
  /** collect the number of lanes based on the highway type, e.g. highway=type, for any direction (not total).
   * In case no number of lanes is specified for the type, we revert to the missing default
   * 
   * @param type highway type value
   * @return number of lanes for this type (if any), otherwise null is returned
   */
  public Integer getDefaultDirectionalLanesByWayType(String osmWayKey, String osmWayValue) {
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      return getDefaultDirectionalLanesByHighwayType(osmWayValue);
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey)) {
      return getDefaultDirectionalRailwayTracks();
    }else {
      LOGGER.warning(String.format("unrecognised OSM way key %s, cannot collect default directional lanes",osmWayKey));
    }
    return null;
  }  
  
  /** collect the number of lanes based on the highway type, e.g. highway=type, in total (both directions)
   * 
   * @param type highway type value
   * @return number of lanes for this type (if any), otherwise null is returned
   */
  public Integer getDefaultTotalLanesByHighwayType(String type) {
    return lanesPerDirection.get(type)*2;
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public OsmLaneDefaults clone() throws CloneNotSupportedException {
    return new OsmLaneDefaults(this);
  }

  
}

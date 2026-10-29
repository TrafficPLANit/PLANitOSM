package org.goplanit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.osm.util.OsmLaneUtils;

/**
 * Configure and retrieve the default configuration for the number of lanes for various osm way types, used when the
 * tags of a way give none. A highway default is the total lanes of a way, the same as the OSM lanes tag: all of them
 * in its one direction when it is one-way, by its tag or by implication such as a roundabout, half of them, an odd
 * total rounded up, in each direction when it is two-way. For example primary has 2: 2 lanes on a one-way carriageway
 * and 1 lane in each direction of a two-way road. Railway tracks and waterway lanes are per direction. The "default"
 * defaults for highway tags originate from <a href="https://wiki.openstreetmap.org/wiki/Key:lanes">OSM key:lanes</a>,
 * checked against the lanes tagged on each highway type in New South Wales, while the "default" defaults for
 * railways, i.e., the number of tracks is based on <a href="https://wiki.openstreetmap.org/wiki/Key:railway#Tracks">
 * OSM key:railways tracks</a>. Waterways are given a single lane equivalent.
 *
 * @author markr
 *
 */
public class OsmLaneDefaults {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmLaneDefaults.class.getCanonicalName());  
  
  /** store the road-based defaults, the total lanes of a way by highway type */
  protected static Map<String, Integer> defaultRoadLanes = new HashMap<>();

  /* initialise */
  static {
    populateDefaultRoadLanes();
  }

  /**
   * Initialise the defaults to use, the total lanes of a way by highway type, based on "common sense" as outlined in
   * <a href="https://wiki.openstreetmap.org/wiki/Key:lanes">OSM key:lanes</a>, each the number of lanes most often
   * tagged on that highway type in New South Wales
   */
  protected static void populateDefaultRoadLanes(){
    /* 2 lanes for larger roads: on a one-way carriageway 2 lanes, on a two-way road 1 lane in each direction */
    defaultRoadLanes.put(OsmHighwayTags.MOTORWAY, 2);
    defaultRoadLanes.put(OsmHighwayTags.TRUNK, 2);
    defaultRoadLanes.put(OsmHighwayTags.PRIMARY, 2);
    /* 1 lane for links between roads and for not too large roads, also 1 lane in each direction of a two-way road */
    defaultRoadLanes.put(OsmHighwayTags.MOTORWAY_LINK,  MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TRUNK_LINK,     MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.RESIDENTIAL,    MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TERTIARY,       MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TERTIARY_LINK,  MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.BUSWAY,         MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.SECONDARY,      MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.SECONDARY_LINK, MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.PRIMARY_LINK,   MINIMUM_LANES);
    /* 1 lane for even smaller roads */
    defaultRoadLanes.put(OsmHighwayTags.UNCLASSIFIED,   MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.PEDESTRIAN,     MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.FOOTWAY,        MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.STEPS,          MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.CYCLEWAY,       MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.LIVING_STREET,  MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.SERVICE,        MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TRACK,          MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.PATH,           MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.ROAD,           MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.BRIDLEWAY,      MINIMUM_LANES);
  }

  /** store the defaults  for class instance, the total lanes of a way by highway type */
  protected Map<String, Integer> roadLanes;

  /** total lanes of a way if not configured for its highway type */
  protected int roadLanesIfUnspecified  = DEFAULT_LANES_IF_UNSPECIFIED;

  /** railway tracks per direction if not configured */
  protected int tracksPerDirectionIfUnspecified  = DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED;

  /** waterway lanes per direction if not configured */
  protected int waterwayLanesPerDirectionIfUnspecified  = DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED;

  /** minimum number of lanes, of a way or in one direction, default is 1*/
  public static final int MINIMUM_LANES = 1;

  /** in case no mapping between highway type and number of lanes is present, use this as the total lanes of the way.
   * Default set to {@code  MINIMUM_LANES} */
  public static final int DEFAULT_LANES_IF_UNSPECIFIED = MINIMUM_LANES;

  /** store the rail default, per direction. Default set to {@code  MINIMUM_LANES} */
  public static final int DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED = MINIMUM_LANES;

  /**
   * Constructor
   */
  public OsmLaneDefaults() {
    /* clone so adjustments can be made locally if so desired */
    this.roadLanes = new HashMap<String, Integer>(defaultRoadLanes);
  }
  
  /** Copy constructor
   * 
   * @param osmLaneDefaults to copy
   */
  public OsmLaneDefaults(OsmLaneDefaults osmLaneDefaults) {
    this.roadLanes = new HashMap<>(osmLaneDefaults.roadLanes);
    this.roadLanesIfUnspecified = osmLaneDefaults.roadLanesIfUnspecified;
    this.tracksPerDirectionIfUnspecified = osmLaneDefaults.tracksPerDirectionIfUnspecified;
    this.waterwayLanesPerDirectionIfUnspecified = osmLaneDefaults.waterwayLanesPerDirectionIfUnspecified;
  }

  /** Overwrite current default
   * @param type highway type
   * @param defaultNumberOfLanes the new default, the total lanes of a way
   * @return old value if any, null if not present
   */
  public Integer setDefaultLanesByHighwayType(String type, Integer defaultNumberOfLanes) {
    return roadLanes.put(type,defaultNumberOfLanes);
  }

  /** Check if the number of lanes based on the way key value, e.g. highway=primary, has a default available
   *
   * @param osmWayKey key to use
   * @param osmWayValue way type value
   * @return true when available, false otherwise
   */
  public Boolean containsDefaultLanes(String osmWayKey, String osmWayValue) {
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      return roadLanes.containsKey(osmWayValue);
    }else if(OsmRailwayTags.isRailwayKeyTag(osmWayKey) || OsmWaterwayTags.isAnyWaterwayKeyTag(osmWayKey)){
      return true;
    }else{
      LOGGER.warning(String.format(
              "Unknown OSM way key tag [%s], unable to determine if lane defaults have been set", osmWayKey));
      return false;

    }
  }
  
  /** collect the number of lanes based on the highway type, e.g. highway=type, the total lanes of a way.
   * In case no number of lanes is specified for the type, we revert to the missing default
   *
   * @param osmWayValue highway type value
   * @return total number of lanes of a way of this type
   */
  public Integer getDefaultLanesByHighwayType(String osmWayValue) {
    if(roadLanes.containsKey(osmWayValue)) {
      return roadLanes.get(osmWayValue);
    }else {
      LOGGER.warning(
          String.format("Highway type [%s] has no number of default lanes associated with it, " +
              "reverting to missing default: %d", osmWayValue, DEFAULT_LANES_IF_UNSPECIFIED));
      return DEFAULT_LANES_IF_UNSPECIFIED;
    }
  }
  
  /** Overwrite current default
   * @param defaultNumberOfTracksPerDirection the new default
   */
  public void setDefaultDirectionalRailwayTracks(Integer defaultNumberOfTracksPerDirection) {
    this.tracksPerDirectionIfUnspecified = defaultNumberOfTracksPerDirection;
  }    
  
  /** Collect the default number of tracks for railways (in one direction)
   * @return defaultNumberOfTracksPerDirection in case not explicitly specified
   */
  public Integer getDefaultDirectionalRailwayTracks() {
    return this.tracksPerDirectionIfUnspecified;
  }

  /** Collect the default number of water way lanes for water ways (in one direction)
   * @return waterwayLanesPerDirectionIfUnspecified in case not explicitly specified
   */
  public Integer getDefaultDirectionalWaterwayLanes() {
    return this.waterwayLanesPerDirectionIfUnspecified;
  }
  
  /** collect the default number of lanes in each direction of a way based on its type, e.g. highway=type. For a
   * highway it is the total lanes of the way, all of them when it is one-way, half of them, an odd total rounded up,
   * when it is two-way. For a railway or waterway it is the default per direction. In case no number of lanes is
   * specified for the type, we revert to the missing default
   *
   * @param osmKey OSM key to identify the way type (highway, rail way, or water way)
   * @param osmValue OSM key's value
   * @param oneWay true when the way is one-way, by its tag or by implication such as a roundabout
   * @return number of lanes in each direction for this type (if any), otherwise null is returned
   */
  public Integer getDefaultLanesPerDirection(String osmKey, String osmValue, boolean oneWay) {
    if(OsmHighwayTags.isHighwayKeyTag(osmKey)) {
      int totalLanes = getDefaultLanesByHighwayType(osmValue);
      return oneWay ? totalLanes : OsmLaneUtils.findLanesPerDirectionOfTwoWayTotal(totalLanes);
    }else if(OsmRailwayTags.isRailwayKeyTag(osmKey)) {
      return getDefaultDirectionalRailwayTracks();
    }else if(OsmWaterwayTags.isWaterBasedWay(osmKey, osmValue)) {
      return getDefaultDirectionalWaterwayLanes();
    }else {
      LOGGER.warning(String.format("unrecognised OSM way key [%s], cannot collect default directional lanes",osmKey));
    }
    return null;
  }
  
  /**
   * Shallow copy
   *
   * @return shallow copy
   */
  public OsmLaneDefaults shallowClone() {
    return new OsmLaneDefaults(this);
  }

  
}

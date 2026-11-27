package org.goplanit.osm.defaults;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The lane defaults a settings file can set: only the methods of its keys, each write with the read of the same key.
 * {@link OsmLaneDefaults} extends it with what parsing asks of the defaults.
 * <p>
 * Keeping the keys in a class of their own shows in one place all a settings file can say in its lane defaults
 * section, while the questions parsing asks, such as the lanes in each direction of a way, stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmLaneYamlKeyedDefaults {

  /** store the road-based defaults, the total lanes of a way by highway type */
  private static final Map<String, Integer> defaultRoadLanes = new HashMap<>();

  /* initialise */
  static {
    populateDefaultRoadLanes();
  }

  /**
   * Initialise the defaults to use, the total lanes of a way by highway type, based on "common sense" as outlined in
   * <a href="https://wiki.openstreetmap.org/wiki/Key:lanes">OSM key:lanes</a>, benchmarked against Sydney data: each
   * the number of lanes most often found on that highway type, one-way and two-way, in the through lanes of the
   * Transport for NSW Sydney Region Carriageway data and in the lanes tagged in New South Wales
   */
  private static void populateDefaultRoadLanes(){
    /* 2 lanes for larger roads: on a one-way carriageway 2 lanes, on a two-way road or a roundabout 1 lane in each
     * direction */
    defaultRoadLanes.put(OsmHighwayTags.MOTORWAY, 2);
    defaultRoadLanes.put(OsmHighwayTags.TRUNK, 2);
    defaultRoadLanes.put(OsmHighwayTags.PRIMARY, 2);
    defaultRoadLanes.put(OsmHighwayTags.SECONDARY, 2);
    /* 1 lane for links between roads and for not too large roads, also 1 lane in each direction of a two-way road */
    defaultRoadLanes.put(OsmHighwayTags.MOTORWAY_LINK,  MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TRUNK_LINK,     MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.RESIDENTIAL,    MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TERTIARY,       MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.TERTIARY_LINK,  MINIMUM_LANES);
    defaultRoadLanes.put(OsmHighwayTags.BUSWAY,         MINIMUM_LANES);
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

  /**
   * Refuse fewer than one lane or track
   *
   * @param lanes given
   * @param what they are given for, for the message
   * @throws IllegalArgumentException naming what they are given for, when fewer than one or none is given
   */
  private static void throwIfFewerThanOneLane(Integer lanes, String what) {
    ExceptionUtils.throwIllegalArgumentIf(lanes == null || lanes < MINIMUM_LANES,
        "Fewer than one lane or track given for %s: %s", what, lanes);
  }

  /** see {@link #setLanesByHighwayType(String, Integer)} */
  private final Map<String, Integer> roadLanes;

  /** see {@link #setRailwayTracksPerDirection(Integer)} */
  private int tracksPerDirectionIfUnspecified = DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED;

  /** see {@link #setWaterwayLanesPerDirection(Integer)} */
  private int waterwayLanesPerDirectionIfUnspecified = DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED;

  /**
   * Constructor with the built-in defaults
   */
  protected OsmLaneYamlKeyedDefaults() {
    this.roadLanes = new TreeMap<>(defaultRoadLanes);
  }

  /**
   * Copy constructor
   *
   * @param other to copy
   */
  protected OsmLaneYamlKeyedDefaults(OsmLaneYamlKeyedDefaults other) {
    this.roadLanes = new TreeMap<>(other.roadLanes);
    this.tracksPerDirectionIfUnspecified = other.tracksPerDirectionIfUnspecified;
    this.waterwayLanesPerDirectionIfUnspecified = other.waterwayLanesPerDirectionIfUnspecified;
  }

  /** minimum number of lanes, of a way or in one direction, default is 1*/
  public static final int MINIMUM_LANES = 1;

  /** in case no mapping between highway type and number of lanes is present, use this as the total lanes of the way.
   * Default set to {@code  MINIMUM_LANES} */
  public static final int DEFAULT_LANES_IF_UNSPECIFIED = MINIMUM_LANES;

  /** store the rail default, per direction. Default set to {@code  MINIMUM_LANES} */
  public static final int DEFAULT_TRACKS_PER_DIRECTION_IF_UNSPECIFIED = MINIMUM_LANES;

  /**
   * Set the total lanes of a way of a highway type, used when its tags give none. As the OSM lanes tag, it is all the
   * lanes of the way, see {@link OsmLaneDefaults} for how they split over its directions. Setting a type again
   * replaces its lanes
   *
   * @param highwayType the OSM highway type, such as primary
   * @param lanes the total lanes of a way of this type, at least one
   * @throws IllegalArgumentException naming the type, storing nothing, when fewer than one lane is given
   */
  @SettingsWrite(key = "lanesByHighwayType", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "Total lanes of a way by highway type, used when its tags give none, in place of the built-in " +
          "ones")
  public void setLanesByHighwayType(String highwayType, Integer lanes) {
    throwIfFewerThanOneLane(lanes, "highway type " + highwayType);
    roadLanes.put(highwayType, lanes);
  }

  /**
   * The total lanes of a way by highway type, see {@link #setLanesByHighwayType(String, Integer)}
   *
   * @return the lanes by highway type, in alphabetical order, unmodifiable
   */
  @SettingsRead(key = "lanesByHighwayType")
  public Map<String, Integer> getLanesByHighwayType() {
    return Collections.unmodifiableMap(roadLanes);
  }

  /**
   * Set the tracks in each direction of a railway whose tags give none
   *
   * @param tracks in each direction, at least one
   * @throws IllegalArgumentException storing nothing, when fewer than one track is given
   */
  @SettingsWrite(key = "railwayTracksPerDirection", level = SettingsKeyLevel.UNCOMMON,
      description = "Tracks in each direction of a railway whose tags give none")
  public void setRailwayTracksPerDirection(Integer tracks) {
    throwIfFewerThanOneLane(tracks, "railway tracks per direction");
    this.tracksPerDirectionIfUnspecified = tracks;
  }

  /**
   * The tracks in each direction of a railway whose tags give none
   *
   * @return tracks per direction
   */
  @SettingsRead(key = "railwayTracksPerDirection")
  public Integer getRailwayTracksPerDirection() {
    return this.tracksPerDirectionIfUnspecified;
  }

  /**
   * Set the lanes in each direction of a waterway whose tags give none
   *
   * @param lanes in each direction, at least one
   * @throws IllegalArgumentException storing nothing, when fewer than one lane is given
   */
  @SettingsWrite(key = "waterwayLanesPerDirection", level = SettingsKeyLevel.UNCOMMON,
      description = "Lanes in each direction of a waterway whose tags give none")
  public void setWaterwayLanesPerDirection(Integer lanes) {
    throwIfFewerThanOneLane(lanes, "waterway lanes per direction");
    this.waterwayLanesPerDirectionIfUnspecified = lanes;
  }

  /**
   * The lanes in each direction of a waterway whose tags give none
   *
   * @return lanes per direction
   */
  @SettingsRead(key = "waterwayLanesPerDirection")
  public Integer getWaterwayLanesPerDirection() {
    return this.waterwayLanesPerDirectionIfUnspecified;
  }
}

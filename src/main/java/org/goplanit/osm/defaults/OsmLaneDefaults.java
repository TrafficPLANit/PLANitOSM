package org.goplanit.osm.defaults;

import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmJunctionTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.osm.util.OsmLaneUtils;
import org.goplanit.osm.util.OsmWayUtils;

/**
 * Configure and retrieve the default configuration for the number of lanes for various osm way types, used when the
 * tags of a way give none. A highway default is the total lanes of a way, the same as the OSM lanes tag: all of them
 * in its one direction when it is one-way, by its tag or by implication such as a motorway, half of them, an odd total
 * rounded up, in each direction when it is two-way, and on a roundabout, since most roundabouts have a single lane,
 * also on roads whose one-way carriageways have two. For example primary has 2: 2 lanes on a one-way carriageway and 1
 * lane in each direction of a two-way road or on a roundabout. Railway tracks and waterway lanes are per direction.
 * The "default" defaults for highway tags originate from
 * <a href="https://wiki.openstreetmap.org/wiki/Key:lanes">OSM key:lanes</a>, benchmarked against Sydney data: the
 * through lanes of the Transport for NSW Sydney Region Carriageway data (state roads, 2020) and the lanes tagged on
 * each highway type in New South Wales, while the "default" defaults for
 * railways, i.e., the number of tracks is based on <a href="https://wiki.openstreetmap.org/wiki/Key:railway#Tracks">
 * OSM key:railways tracks</a>. Waterways are given a single lane equivalent. The keys a settings file can set are on
 * {@link OsmLaneYamlKeyedDefaults}.
 * <p>
 * Many OSM ways carry no lanes tag, yet every link segment needs a number of lanes for its capacity. These defaults
 * fill that gap by way type, so a residential street without a lanes tag still gets one lane each way, and a primary
 * road two lanes on a one-way carriageway. They are a fallback only: a lanes tag on the way, or a lanes override in
 * the settings, always comes first.
 * </p>
 *
 * @author markr
 *
 */
public class OsmLaneDefaults extends OsmLaneYamlKeyedDefaults {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmLaneDefaults.class.getCanonicalName());

  /**
   * Constructor
   */
  public OsmLaneDefaults() {
    super();
  }

  /** Copy constructor
   *
   * @param osmLaneDefaults to copy
   */
  public OsmLaneDefaults(OsmLaneDefaults osmLaneDefaults) {
    super(osmLaneDefaults);
  }

  /** Check if the number of lanes based on the way key value, e.g. highway=primary, has a default available
   *
   * @param osmWayKey key to use
   * @param osmWayValue way type value
   * @return true when available, false otherwise
   */
  public Boolean containsDefaultLanes(String osmWayKey, String osmWayValue) {
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      return getLanesByHighwayType().containsKey(osmWayValue);
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
    if(getLanesByHighwayType().containsKey(osmWayValue)) {
      return getLanesByHighwayType().get(osmWayValue);
    }else {
      LOGGER.warning(
          String.format("Highway type [%s] has no number of default lanes associated with it, " +
              "reverting to missing default: %d", osmWayValue, DEFAULT_LANES_IF_UNSPECIFIED));
      return DEFAULT_LANES_IF_UNSPECIFIED;
    }
  }
  
  /** collect the default number of lanes in each direction of a way based on its type, e.g. highway=type. For a
   * highway it is the total lanes of the way, all of them when it is one-way, by its tag or by implication such as a
   * motorway, half of them, an odd total rounded up, when it is two-way or a roundabout. For a railway or waterway it
   * is the default per direction. In case no number of lanes is specified for the type, we revert to the missing
   * default
   *
   * @param osmKey OSM key to identify the way type (highway, rail way, or water way)
   * @param tags of the way, holding the value of the OSM key
   * @return number of lanes in each direction for this type (if any), otherwise null is returned
   */
  public Integer getDefaultLanesPerDirection(String osmKey, Map<String, String> tags) {
    var osmValue = tags.get(osmKey);
    if(OsmHighwayTags.isHighwayKeyTag(osmKey)) {
      int totalLanes = getDefaultLanesByHighwayType(osmValue);
      boolean allLanesInOneDirection = !OsmJunctionTags.isPartOfCircularWayJunction(tags) &&
          (OsmWayUtils.isOneWay(tags) || OsmWayUtils.isImpliedOneWay(tags));
      return allLanesInOneDirection ? totalLanes : OsmLaneUtils.findLanesPerDirectionOfTwoWayTotal(totalLanes);
    }else if(OsmRailwayTags.isRailwayKeyTag(osmKey)) {
      return getRailwayTracksPerDirection();
    }else if(OsmWaterwayTags.isWaterBasedWay(osmKey, osmValue)) {
      return getWaterwayLanesPerDirection();
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

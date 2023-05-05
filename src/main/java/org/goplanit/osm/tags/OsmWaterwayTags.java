package org.goplanit.osm.tags;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Most OSM water way tags that could be of value for a network. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:route(=ferry). Modes for water ways are then indicated by Ptv1 scheme, e.g. foot=yes/no.
 * 
 * @author markr
 *
 */
public class OsmWaterwayTags {
  
  /** all currently available water way tags that represent ways accessible to a water based mode by means of its route key tag*/
  private static final Set<String> WATERWAY_OSM_ROUTE_VALUE_TAGS = new HashSet<>();

  /** all currently supported water way tags that represent geographic areas */
  private static final Set<String> AREABASED_OSM_WATERWAY_VALUE_TAGS = new HashSet<>();


  /**
   * populate the available value tags for key railway= that represent rail tracks of some sort
   * 
   * <ul>
   * <li>ferry</li>
   * </ul>
   */
  private static void populateWaterBasedOsmwWayValueTags() {
    WATERWAY_OSM_ROUTE_VALUE_TAGS.add(FERRY);
  }

  /**
   * populate the available railway tags
   */
  static {
    populateWaterBasedOsmwWayValueTags();
  }
  
  /* key */
  public static final String ROUTE = "route";
  
  /* values of types of ways with tag route */
  
  public static final String FERRY = OsmWaterModeTags.FERRY;

  /** Verify if passed in tag is indeed a water way mode value tag
   * 
   * @param waterWayTagValue to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isWaterBasedWay(String waterwayKey, String waterWayTagValue) {
    return isWaterwayKeyTag(waterwayKey) && WATERWAY_OSM_ROUTE_VALUE_TAGS.contains(waterWayTagValue);
  }

  /** Verify if passed in tags contain keys that might point to this being a water way
   * 
   * @param tags to verify from
   * @return true when potential keys are present (but value may not be), otherwise false
   */
  public static boolean hasWaterwayKeyTag(Map<String, String> tags) {
    return tags.containsKey(getWaterwayKeyTag());
  }

  /**
   * Collect the water way key tag (note that unlike road and rail this does not guarantee the way is water based since
   * it utilises the more generic route tag, e.g. route=ferry
   *
   * @return water way key tag
   */
  public static String getWaterwayKeyTag() {
    return OsmWaterwayTags.ROUTE;
  }

  /**
   * Verify if tags indicates this is a water way by checking for keys and values being consistent with a water way,
   * e.g., route=ferry.
   *
   * @param tags to verify
   * @return true when route=a_water_mode is present, false otherwise
   */
  public static boolean isWaterway(Map<String, String> tags) {
    return hasWaterwayKeyTag(tags) && isWaterBasedWay(getWaterwayKeyTag(), tags.get(getWaterwayKeyTag()));
  }


  /**
   * Verify if given key is the key tag used to identify if this eprtains to a (potential) water way
   * @param osmKey
   * @return
   */
  public static boolean isWaterwayKeyTag(String osmKey) {
    return OsmWaterwayTags.ROUTE.equals(osmKey);
  }

}

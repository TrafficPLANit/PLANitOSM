package org.goplanit.osm.tags;

import org.goplanit.osm.defaults.OsmHighwayTypeConfiguration;

import java.util.*;
import java.util.logging.Logger;

/**
 * Most OSM waterway tags that could be of value for a network. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:route(=ferry). Modes for waterways are then indicated by Ptv1 scheme, e.g. ferry=yes/no.
 * <p>
 *   there are two ways that ferries are indicated either via the generic route=ferry tag (for which we use a dummy key to avoid
 *   issues if we would ever use route otherwise, but also ferry=_some_highway_type_ is possible, e.g., ferry=primary when it replaces
 *   as piece of road for example. Both are to be supported and this makes this class and its use a bit mroe complicated
 * </p>
 * 
 * @author markr
 *
 */
public class OsmWaterwayTags {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmWaterwayTags.class.getCanonicalName());
  
  /** all currently available waterway tags that represent ways accessible to a water based mode by means of its route key tag*/
  static final Set<String> DEFAULT_ACTIVATED_WATERWAY_OSM_ROUTE_VALUE_TAGS = new HashSet<>();

  /** all available waterway tags that represent ways accessible to ferry replacing a road where the values may correspond
   * to all eligible highway tags, e.g. ferry=primary.  */
  static final Set<String> DEFAULT_ACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS = new HashSet<>();

  /** all available waterway tags that represent ways accessible to ferry replacing a road where the values may correspond
   * to all eligible highway tags, e.g. ferry=primary.  */
  static final Set<String> DEFAULT_DEACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS = new HashSet<>();

  /** waterways support two possible key tags unlike road or rail */
  static final Set<String> WATERWAY_KEY_TAGS = Set.of(OsmWaterwayTags.ROUTE, OsmWaterwayTags.FERRY);

  /**
   * populate the (un)available value tags for key route=_watermode_ that represent waterways of some sort
   * 
   * <ul>
   * <li>ferry</li>
   * </ul>
   */
  private static void populateWaterBasedOsmWayValueTags() {
    /* route=ferry */
    DEFAULT_ACTIVATED_WATERWAY_OSM_ROUTE_VALUE_TAGS.add(FERRY);

    /* ferry=_highway_type */
    DEFAULT_ACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS.addAll(OsmHighwayTypeConfiguration.DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES);
    DEFAULT_DEACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS.addAll(OsmHighwayTypeConfiguration.DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES);
  }

  /**
   * populate the available railway tags
   */
  static {
    populateWaterBasedOsmWayValueTags();
  }
  
  /* key */
  public static final String ROUTE = "route";

  /* ferry is a complicated tag because it may be used to indicate a type of route, e.g., route=ferry, as well as used as a
   * a way to indicate a ferry that replaces a type of highway, in which case it may be ferr=primary, indicating a highway section
   * that is a ferry. The latter may or may not have the route=ferry tagging, so, we should support it separately as another valid
   * ferry key
   */
  public static final String FERRY = OsmWaterModeTags.FERRY;

  /** Verify if passed in tag is indeed a waterway mode value tag
   * 
   * @param waterWayTagValue to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isWaterBasedWay(String waterwayKey, String waterWayTagValue) {
    return isAnyWaterwayKeyTag(waterwayKey) &&
        (DEFAULT_ACTIVATED_WATERWAY_OSM_ROUTE_VALUE_TAGS.contains(waterWayTagValue) ||
        DEFAULT_ACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS.contains(waterWayTagValue));
  }

  /**
   * Verify if tags indicates this is a waterway by checking for keys and values being consistent with a water way,
   * e.g., route=ferry.
   *
   * @param tags to verify
   * @return true when route=a_water_mode is present, false otherwise
   */

  public static boolean isWaterBasedWay(Map<String, String> tags) {
    return hasAnyWaterwayKeyTag(tags) &&
        (FERRY.equals(tags.get(ROUTE)) || isWaterWayTaggedAsHighway(tags));
  }

  /** Verify if passed in tags contain any keys that might point to this being a waterway, e.g. route or ferry as both
   * can be used for this purpose
   * 
   * @param tags to verify from
   * @return true when potential keys are present (but value may not be), otherwise false
   */
  public static boolean hasAnyWaterwayKeyTag(Map<String, String> tags) {
    return tags.containsKey(ROUTE) || tags.containsKey(FERRY);
  }

  /**
   * Specifically check if the tags indicate the waterway is tagged as a highway via ferry=_highway_type_
   *
   * @param tags to check
   * @return true when tagged as highway, false otherwise
   */
  public static boolean isWaterWayTaggedAsHighway(Map<String, String> tags){
    return tags.containsKey(FERRY) && DEFAULT_ACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS.contains(tags.get(FERRY));
  }

  /**
   * Specifically check if the tags indicate the waterway is tagged as a route=ferry
   *
   * @param tags to check
   * @return true when tagged as ferry route, false otherwise
   */
  public static boolean isWaterWayTaggedFerryRoute(Map<String, String> tags){
    return tags.containsKey(ROUTE) && tags.get(ROUTE).equals(FERRY);
  }

  /**
   * Find the used key tag for waterways assuming it has a water way supported key
   *
   * @param tags to search
   * @return found key tag
   */
  public static String getUsedKeyTag(Map<String, String> tags){
    if(isWaterWayTaggedFerryRoute(tags)){
      return ROUTE;
    }else if(isWaterWayTaggedAsHighway(tags)){
      return FERRY;
    }
    LOGGER.severe(String.format("Water way is neither tagged as route=ferry or ferry=<some_highway_tag> for tags: %s",tags));
    return null;
  }

  /**
   * Find the key tag for a given waterway type, e.g., when 'ferry' is provided, the key would be 'route', when primary is provided
   * is would return 'ferry' as key
   *
   * @param waterwayType to search
   * @return found key tag, null if no match
   */
  public static String getKeyForValueType(String waterwayType){
    if(FERRY.equals(waterwayType)){
      return ROUTE;
    }else if(getAllSupportedHighwayTypesAsWaterWayTypes().contains(waterwayType)){
      return FERRY;
    }
    return null;
  }

  /**
   * Verify if a key tag for a given waterway type exists, e.g., when 'ferry' is provided, a valid key would be 'route', when 'primary' is provided
   * a valid key would be 'ferry', if no valid key exists, false is returned
   *
   * @param waterwayType to search
   * @return if found true, false otherwise
   */
  public static boolean hasKeyForValueType(String waterwayType){
    return getKeyForValueType(waterwayType) != null;
  }

  /**
   * Verify if given key is a (potential) waterway, we check for 'route' and 'ferry'
   * 
   * @param osmKey to verify
   * @return true when a(potential) waterway key, false otherwise
   */
  public static boolean isAnyWaterwayKeyTag(String osmKey) {
    return ROUTE.equals(osmKey) || FERRY.equals(osmKey);
  }

  /**
   * Provide access to all highway types that are supported as values for ferry=_highway_type_
   *
   * @return result as set
   */
  public static Set<String> getAllSupportedHighwayTypesAsWaterWayTypes(){
    return Collections.unmodifiableSet(DEFAULT_ACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS);
  }

  /**
   * Provide access to all highway types that are unsupported as values for ferry=_highway_type_
   *
   * @return result as set
   */
  public static Set<String> getAllUnsupportedHighwayTypesAsWaterWayTypes(){
    return Collections.unmodifiableSet(DEFAULT_DEACTIVATED_WATERWAY_OSM_FERRY_VALUE_TAGS);
  }

  /**
   * The waterway key tags that may reflect a waterway
   *
   * @return key tags
   */
  public static Collection<String> getKeyTags() {
    return WATERWAY_KEY_TAGS;
  }
}

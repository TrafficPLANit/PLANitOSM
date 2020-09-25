package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.utils.misc.Pair;

/**
 * configure and retrieve the default configuration for the number of lanes for various osm way types (these are the total lanes on a link covering both directions.
 * The "default" defaults originate from https://wiki.openstreetmap.org/wiki/Key:lanes
 * 
 * @author markr
 *
 */
public class OsmLaneDefaults implements Cloneable {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmLaneDefaults.class.getCanonicalName());  
  
  /** store the defaults */
  protected static Map<String, Integer> defaultLanesPerDirection = new HashMap<String, Integer>();
  
  /** store the defaults  for class instance */
  protected Map<String, Integer> lanesPerDirection;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>> speedLimitDefaultsByCountry = new HashMap<String,Pair<OsmSpeedLimitDefaults,OsmSpeedLimitDefaults>>();
  
  /** lanes per direction if not configured */
  protected int lanesPerDirectionIfUnspecified  = DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED;
      
  /* initialise */
  static {    
    populateDefaultLanesPerDirection();       
  }
  
  /**
   * Initialise the defaults to use based on "common sense" as outlined in https://wiki.openstreetmap.org/wiki/Key:lanes
   */
  protected static void populateDefaultLanesPerDirection(){
    /* 2 lanes for larger roads */
    defaultLanesPerDirection.put(OsmHighwayTags.MOTORWAY, 2);
    defaultLanesPerDirection.put(OsmHighwayTags.MOTORWAY_LINK, 2);
    defaultLanesPerDirection.put(OsmHighwayTags.TRUNK, 2);
    defaultLanesPerDirection.put(OsmHighwayTags.TRUNK_LINK, 2);   
    /* 1 lane for not too large roads */
    defaultLanesPerDirection.put(OsmHighwayTags.RESIDENTIAL, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.TERTIARY, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.TERTIARY_LINK, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.SECONDARY, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.SECONDARY_LINK, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.PRIMARY, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.PRIMARY_LINK, 1);
    /* 1 lane for even smaller roads, while the specification also lists 1 lane in total, PLANit has no use for this, so it is ignored */
    defaultLanesPerDirection.put(OsmHighwayTags.UNCLASSIFIED, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.PEDESTRIAN, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.SERVICE, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.TRACK, 1);
    defaultLanesPerDirection.put(OsmHighwayTags.PATH, 1);
  }
  
  /** in case no mapping between highway type and number of lanes is present, use this */
  public static final int DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED = 1;
  
  /**
   * Constructor
   */
  public OsmLaneDefaults() {
    /* clone so adjustments can be made locally if so desired */
    this.lanesPerDirection = new HashMap<String, Integer>(defaultLanesPerDirection);
  }
  
  /** Copy constructor
   * 
   * @param osmLaneDefaults to copy
   */
  public OsmLaneDefaults(OsmLaneDefaults osmLaneDefaults) {
    this.lanesPerDirection = new HashMap<String, Integer>(osmLaneDefaults.lanesPerDirection);
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
   * @param type highway type value
   * @return number of lanes for this type (if any), otherwise null is returned
   */
  public Integer getDefaultDirectionalLanesByHighwayType(String type) {
    if(lanesPerDirection.containsKey(type)) {
      return lanesPerDirection.get(type); 
    }else {
      LOGGER.warning(
          String.format("highway type has no number of default lanes associated with it, reverting to missing default: %d",DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED));
      return DEFAULT_LANES_PER_DIRECTION_IF_UNSPECIFIED;
    }
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

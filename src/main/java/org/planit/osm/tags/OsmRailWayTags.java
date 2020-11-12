package org.planit.osm.tags;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Most OSM rail way tags that could be of value for a network. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:railway 
 * 
 * @author markr
 *
 */
public class OsmRailWayTags {
  
  /** all currently available rail way tags that represent tracks accessible to a rail mode*/
  private static final Set<String> RAILBASED_OSM_RIALWAY_VALUE_TAGS = new HashSet<String>();
  
  /** all currently supported railway tags that represent geographic areas, e.g. stations, or platforms next or alongside tracks, this is a subset of
   * the {@code NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS} */
  private static final Set<String> AREABASED_OSM_RAILWAY_VALUE_TAGS = new HashSet<String>();    
    
  /**
   * the OSM railway values that are marked as non-rail types, i.e., they can never be activated to be converted into links
   */
  protected static final Set<String> NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS = new HashSet<String>();
  
  
  /**
   * populate the available railway mode tags
   */
  private static void populateRailBasedOsmRailwayValueTags() {
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(FUNICULAR);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(LIGHT_RAIL);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(MONO_RAIL);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(NARROW_GAUGE);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(MINIATURE);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(RAZED);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(TURNTABLE);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(RAIL);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(SUBWAY);
    RAILBASED_OSM_RIALWAY_VALUE_TAGS.add(TRAM);
  }
  
  /**
   * populate the available railway area tags. These area tags are here to identify ways that are in fact NOT
   * railways in the traditional sense, yet they are provided as such and have tags to indicate they represent something
   * else than tracks
   * 
   * <ul>
   * <li>platform</li>
   * <li>station</li>
   * <li>fuel</li>
   * <li>traverser</li>
   * <li>wash</li>
   * <li>roundhouse</li>
   * <li>yard</li>
   * <li>signal_box</li>
   * <li>tram_stop</li>
   * <li>workshop</li>
   * </ul>
   */
  private static void populateAreaBasedOsmRailwayValueTags() {
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(PLATFORM);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(STATION);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(FUEL);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(TRAVERSER);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(WASH);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(ROUNDHOUSE);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(YARD);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(SIGNAL_BOX);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(TRAM_STOP);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(WORKSHOP);    
  }  
  
  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM railway types to macroscopic link segment types that we explicitly do include, i.e., support.
   * 
   * <ul>
   * <li>LIGHT_RAIL</li>
   * <li>RAIL</li>
   * <li>SUBWAY</li>
   * <li>TRAM</li>
   * </ul>
   * 
   * @return the default created supported types 
   */
  private static void populateNonRailBasedOsmRailwayValueTags(){
    /* copy area based */
    NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS.addAll(AREABASED_OSM_RAILWAY_VALUE_TAGS);
    /* add other non rail track based */
    NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(PLATFORM_EDGE);
  }  
  
  /**
   * populate the available railway tags
   */
  static {
    populateRailBasedOsmRailwayValueTags();
    populateAreaBasedOsmRailwayValueTags();
    populateNonRailBasedOsmRailwayValueTags();
  }
  
  /* key */
  public static final String RAILWAY = "railway";
  
  /* values of types of railways */
  
  public static final String ABANDONED = "abandoned";
  
  public static final String CONSTRUCTION = "construction";
  
  public static final String PROPOSED = "proposed";
  
  public static final String DISUSED = "disused";
    
  public static final String FUNICULAR = "funicular";
  
  public static final String LIGHT_RAIL = "light_rail";
  
  public static final String MONO_RAIL = "monorail";
   
  public static final String NARROW_GAUGE = "narrow_gauge";
  
  public static final String PRESERVED = "preserved";  
  
  public static final String RAIL = "rail";
  
  public static final String SUBWAY = "subway";
  
  public static final String TRAM = "tram";  
    
  /** miniature railways, not actual railways */
  public static final String MINIATURE = "miniature";
  
  /** turn table is a circular railway that we do not yet support */
  public static final String TURNTABLE = "turntable";
  
  /** built on top of, no longer a real track */
  public static final String RAZED = "razed";
    
  /* areas */
  
  /** a platform is a separate (disconnected) way or area to indicate a platform */
  public static final String PLATFORM = "platform";

  /** a station is a separate (disconnected) area to indicate a public transport station*/
  public static final String STATION = "station";
  
  private static final String FUEL = "fuel";

  private static final String TRAVERSER = "traverser";

  private static final String WASH = "wash";

  private static final String ROUNDHOUSE = "roundahouse";

  private static final String YARD = "yard";
  
  private static final String SIGNAL_BOX = "signal_box";
  
  private static final String WORKSHOP = "workshop";
  
  /** sometimes railways are tagged as tram_stop to indicate an area for stopping, i.e., a platform. sometimes used in conjunction with public_transport=platform, see https://wiki.openstreetmap.org/wiki/Key:public_transport */
  private static final String TRAM_STOP = "tram_stop";
  
  /* other ways that do not reflect tracks (and are not areas) */
  
  public static final String PLATFORM_EDGE = "platform_edge";  
   
      

  /** verify if passed in tag is indeed a railway mode value tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailBasedRailway(String railwayTagValue) {
    return RAILBASED_OSM_RIALWAY_VALUE_TAGS.contains(railwayTagValue);
  }
  
  /** some rail based ways can be areas when tagged in a certain way. currently we do this for both stations and platforms
   * although technically platforms can be ways, but since we do not model them (as ways), we regard them as areas in all cases for now

   * @param osmWay the way
   * @param tags the tags
   * @return is the way an area and not a line based railway
   */
  public static boolean isAreaBasedRailway(String railwayTagValue) {
    return AREABASED_OSM_RAILWAY_VALUE_TAGS.contains(railwayTagValue);
  } 
  
  /** some railways are in fact valid railway value tags but do not represent a rail track. any such value tags that our parser is aware of can be verified through this method
   * using the {@code NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS}
   * 
   * @param osmWay the way
   * @param tags the tags
   * @return is the way an area and not a line based railway
   */
  public static boolean isNonRailBasedRailway(String railwayTagValue) {
    return NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS.contains(railwayTagValue);
  }   
  
  /** verify if passed in tag is indeed the railway key tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailwayKeyTag(String railwayTag) {
    return RAILWAY.equals(railwayTag);
  }  

  /**
   * provide a copy of all supported rail mode tags
   * @return all supported road modes
   */
  public static Collection<String> getSupportedRailModeTags() {
    return new HashSet<String>(RAILBASED_OSM_RIALWAY_VALUE_TAGS);
  }

  /**
   * Verify if tags indicates this is a railway
   * @param tags to verify
   * @return true when railway=* is present, false otherwise
   */
  public static boolean hasRailwayKeyTag(Map<String, String> tags) {
    return tags.containsKey(OsmRailWayTags.RAILWAY);
  }
   
    
}

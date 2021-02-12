package org.planit.osm.tags;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Most OSM rail way tags that could be of value for a network. Based on list found on
 * https://wiki.openstreetmap.org/wiki/Key:railway. Tags specific to the Ptv1 scheme are collected via the OsmPtv1 tags class
 * and integrated in the collections managed by this class. 
 * 
 * @author markr
 *
 */
public class OsmRailwayTags {
  
  /** all currently available rail way tags that represent tracks accessible to a rail mode*/
  private static final Set<String> RAILBASED_OSM_RAILWAY_VALUE_TAGS = new HashSet<String>();
    
  /** all currently supported railway tags that represent geographic areas, e.g. stations, or platforms next or alongside tracks, this is a subset of
   * the {@code NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS} */
  private static final Set<String> AREABASED_OSM_RAILWAY_VALUE_TAGS = new HashSet<String>();    
    
  /**
   * the OSM railway values that are marked as non-rail types, i.e., they can never be activated to be converted into links
   */
  protected static final Set<String> NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS = new HashSet<String>();
  
  
  /**
   * populate the available value tags for key railway= that represent rail tracks of some sort
   * 
   * <ul>
   * <li>funicular</li>
   * <li>light_rail</li>
   * <li>mono_rail</li>
   * <li>narrow_gauge</li>
   * <li>miniature</li>
   * <li>razed</li>
   * <li>turntable</li>
   * <li>rail</li>
   * <li>subway</li>
   * <li>tram</li>
   * </ul>
   */
  private static void populateRailBasedOsmRailwayValueTags() {
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(FUNICULAR);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(LIGHT_RAIL);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(MONO_RAIL);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(NARROW_GAUGE);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(MINIATURE);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(RAZED);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(TURNTABLE);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(RAIL);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(SUBWAY);
    RAILBASED_OSM_RAILWAY_VALUE_TAGS.add(TRAM);
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
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(FUEL);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(TRAVERSER);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(WASH);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(ROUNDHOUSE);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(YARD);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(SIGNAL_BOX);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.add(WORKSHOP);
    AREABASED_OSM_RAILWAY_VALUE_TAGS.addAll(OsmPtv1Tags.getAreaBasedRailwayValueTags());
  }  
  
  /**
   * all non rail based value tags with key railway=*
   * 
   * @return the default created supported types 
   */
  private static void populateNonRailBasedOsmRailwayValueTags(){
    /* copy area based */
    NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS.addAll(AREABASED_OSM_RAILWAY_VALUE_TAGS);
    NON_RAILBASED_OSM_RAILWAY_VALUE_TAGS.addAll(OsmPtv1Tags.getRailwayValueTags());    
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
    
  /* areas non public_transport, but still valid railway tags*/
    
  public static final String FUEL = "fuel";

  public static final String TRAVERSER = "traverser";

  public static final String WASH = "wash";

  public static final String ROUNDHOUSE = "roundahouse";

  public static final String YARD = "yard";
  
  public static final String SIGNAL_BOX = "signal_box";
  
  public static final String WORKSHOP = "workshop";
        

  /** verify if passed in tag is indeed a railway mode value tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailBasedRailway(String railwayTagValue) {
    return RAILBASED_OSM_RAILWAY_VALUE_TAGS.contains(railwayTagValue);
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
   * Verify if tags indicates this is a railway
   * @param tags to verify
   * @return true when railway=* is present, false otherwise
   */
  public static boolean hasRailwayKeyTag(Map<String, String> tags) {
    return tags.containsKey(OsmRailwayTags.RAILWAY);
  }
   
    
}

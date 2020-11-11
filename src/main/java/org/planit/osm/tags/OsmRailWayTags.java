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
  private static final Set<String> railway = new HashSet<String>();
  
  /** all currently supported railway tags that represent stations, or platforms next or alongside tracks */
  private static final Set<String> railwayArea = new HashSet<String>();
  
  /**
   * populate the available railway mode tags
   */
  private static void populateRailwayModeTags() {
    railway.add(FUNICULAR);
    railway.add(LIGHT_RAIL);
    railway.add(MONO_RAIL);
    railway.add(NARROW_GAUGE);
    railway.add(RAIL);
    railway.add(SUBWAY);
    railway.add(TRAM);
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
   * </ul>
   */
  private static void populateRailwayAreaTags() {
    railwayArea.add(PLATFORM);
    railwayArea.add(STATION);
    railwayArea.add(FUEL);
    railwayArea.add(TRAVERSER);
    railwayArea.add(WASH);
    railwayArea.add(ROUNDHOUSE);
    railwayArea.add(YARD);
    railwayArea.add(SIGNAL_BOX);
    railwayArea.add(TRAM_STOP);
  }  
  
  /**
   * populate the available railway tags
   */
  static {
    populateRailwayModeTags();
    populateRailwayAreaTags();
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
  
  /* other railway values that do not reflect a railway but are used as a way in combination with the railway key */
  
  /** miniature railways, not actual railways */
  public static final String MINIATURE = "miniature";
  
  /** turn table is a circular railway that we do not yet support */
  public static final String TURNTABLE = "turntable";
  
  /** built on top of, no longer a real track */
  public static final String RAZED = "razed";
  
  /* other ways that do not reflect tracks */
  
  public static final String PLATFORM_EDGE = "platform_edge";
  
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
  
  /** sometimes railways are tagged as tram_stop to indicate an area for stopping, i.e., a platform. sometimes used in conjunction with public_transport=platform, see https://wiki.openstreetmap.org/wiki/Key:public_transport */
  private static final String TRAM_STOP = "tram_stop";  
   
      

  /** verify if passed in tag is indeed a railway mode value tag
   * @param railwayTag to verify
   * @return true when valid tag, otherwise false
   */
  public static boolean isRailwayModeValueTag(String railwayTagValue) {
    return railway.contains(railwayTagValue);
  }
  
  /** some rail based ways can be areas when tagged in a certain way. currently we do this for both stations and platforms
   * although technically platforms can be ways, but since we do not model them (as ways), we regard them as areas in all cases for now

   * @param osmWay the way
   * @param tags the tags
   * @return is the way an area and not a line based railway
   */
  public static boolean isRailBasedArea(String railwayTagValue) {
    return railwayArea.contains(railwayTagValue);
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
    return new HashSet<String>(railway);
  }

  /**
   * Verify if tags indicates this is a railway
   * @param tags to verify
   * @return true when railway=* is present, false otherwise
   */
  public static boolean isRailway(Map<String, String> tags) {
    return tags.containsKey(OsmRailWayTags.RAILWAY);
  }
   
    
}

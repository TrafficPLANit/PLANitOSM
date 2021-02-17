package org.planit.osm.tags;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tags related specifically to public transport infrastructure and services for the OSM PTv1 tagging scheme. 
 * this taggin scheme relies on existing railway=* or highway=* keys which is confusing. Here we store and manage
 * those tags used by the Ptv1 scheme, whereas the OsmRailwayTags and OsmHighwayTags classes will extract the value tags identified
 * here to populate some of the larger tagging collections that comprises these tags as well as other (unrelated to public transport)
 * 
 * @author markr
 *
 */
public class OsmPtv1Tags {
  
  /**
   * all highway key related value tags that pertain to public transport and may reflect an area
   */
  private static final Set<String> AREA_BASED_PT_HIGHWAY_VALUE_TAGS = new HashSet<String>();
  
  /**
   * all highway key related value tags that pertain to public transport
   */
  private static final Set<String> PT_HIGHWAY_VALUE_TAGS = new HashSet<String>();

  /**
   * all highway key related value tags that pertain to public transport and may reflect an area
   */  
  private static final Set<String> AREA_BASED_PT_RAILWAY_VALUE_TAGS = new HashSet<String>();
  
  /**
   * all railway key related value tags that pertain to public transport
   */
  private static final Set<String> PT_RAILWAY_VALUE_TAGS = new HashSet<String>();  
  
  /**
   * populate the pt highway value tags
   */
  private static void populateOsmHighwayPublicTransportValueTags() {
    PT_HIGHWAY_VALUE_TAGS.add(BUS_STOP);
    PT_HIGHWAY_VALUE_TAGS.addAll(AREA_BASED_PT_HIGHWAY_VALUE_TAGS);
  }  
  
  /**
   * populate the pt railway value tags
   */
  private static void populateOsmRailwayPublicTransportValueTags() {
    PT_RAILWAY_VALUE_TAGS.add(HALT);
    PT_RAILWAY_VALUE_TAGS.add(STOP);
    PT_RAILWAY_VALUE_TAGS.add(PLATFORM_EDGE);
    PT_RAILWAY_VALUE_TAGS.add(SUBWAY_ENTRANCE);  
    PT_RAILWAY_VALUE_TAGS.addAll(AREA_BASED_PT_RAILWAY_VALUE_TAGS);
  }    

  
  /**
   * populate the area based pt highway tags
   */
  private static void populateAreaBasedOsmHighwayPublicTransportValueTags() {
    AREA_BASED_PT_HIGHWAY_VALUE_TAGS.add(PLATFORM);
  }
  
  /**
   * populate the available railway area value tags specifically aimed at the public transport aspect of railways. For general railway
   * tags see OsmRailwayTags
   * 
   * <ul>
   * <li>platform</li>
   * <li>station</li>
   * <li>tram_stop</li>
   * </ul>
   */
  private static void populateAreaBasedOsmRailwayPublicTransportValueTags() {
    AREA_BASED_PT_RAILWAY_VALUE_TAGS.add(PLATFORM);
    AREA_BASED_PT_RAILWAY_VALUE_TAGS.add(STATION);
    AREA_BASED_PT_RAILWAY_VALUE_TAGS.add(TRAM_STOP);    
  }  
  
  static {
    populateAreaBasedOsmHighwayPublicTransportValueTags();
    populateOsmHighwayPublicTransportValueTags();
    populateAreaBasedOsmRailwayPublicTransportValueTags();
    populateOsmRailwayPublicTransportValueTags();
  }  
  
  /* generic (used across multiple keys)*/
  
  /** a platform tag used frequently under PTv1 i.c.w. railway/highway/public_transport=platform (can be an area, way, or node)*/
  public static final String PLATFORM = "platform";
  
  /* highway=* */
  
  /** a bus_stop tag used frequently under PTv1 i.c.w. highway=bus_stop */
  public static final String BUS_STOP = "bus_stop";
    
  /* railway=* */
  
  /** a halt tag used frequently under PTv1 i.c.w. railway=halt */
  public static final String HALT = "halt";
  
  /** a halt tag used frequently under PTv1 i.c.w. railway=halt */
  public static final String STOP = "stop";
  
  /** a tram_stop tag used frequently under PTv1 i.c.w. railway=tram_stop */  
  public static final String TRAM_STOP = "tram_stop";
  
  /** an entrance to a station for pedestrians (and or cyclists) */
  public static final String SUBWAY_ENTRANCE = "subway_entrance"; 
  
  
  /* railway areas */
  
  /** a station is a separate (disconnected) area to indicate a public transport station*/
  public static final String STATION = "station";
    
  /* railway ways that are not tracks and not areas */
    
  public static final String PLATFORM_EDGE = "platform_edge";    
  
  

  /** the area based public transport value tags that can occur i.c.w. the key highway=* 
   * 
   * @return all highway oriented public transport tags in existence that may signify an area
   */
  public static final Set<String> getAreaBasedHighwayValueTags() {
    return AREA_BASED_PT_HIGHWAY_VALUE_TAGS;
  }
  
  /** the area based public transport tags that can occur i.c.w. the key railway=*
   * 
   * @return all railway oriented public transport tags in existence that may signify an area
   */
  public static final Set<String> getAreaBasedRailwayValueTags() {
    return AREA_BASED_PT_RAILWAY_VALUE_TAGS;
  }  
  
  /** collect all known railway value tags that are non-way related
   * 
   * @return non-way related railway=* value tags
   */
  public static final Set<String> getRailwayValueTags() {
    return PT_RAILWAY_VALUE_TAGS;
  }
  
  /** collect all known highway value tags that are non-way related
   * 
   * @return non-way related highway=* value tags
   */
  public static final Set<String> getHighwayValueTags() {
    return PT_HIGHWAY_VALUE_TAGS;
  }

  /** Verify if any of the tags is a macth for the currently supported Ptv1 scheme tags
   * @param tags to check against
   * @return true when present, false otherwise
   */
  public static boolean hasPtv1ValueTag(final Map<String, String> tags) {
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      return PT_HIGHWAY_VALUE_TAGS.contains(tags.get(OsmHighwayTags.HIGHWAY));
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      return PT_RAILWAY_VALUE_TAGS.contains(tags.get(OsmRailwayTags.RAILWAY));
    }
    return false;
  }  
}

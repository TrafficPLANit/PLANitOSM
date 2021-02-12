package org.planit.osm.tags;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tags related specifically to public transport infrastructure and services for the OSM PTv2 tagging scheme. 
 * This tagging scheme mainly relies on public_transport=* key although it is compatible with the Ptv1 scheme. 
 * 
 * @author markr
 *
 */
public class OsmPtv2Tags {
  
  /**
   * all public_transport=* related value tags that pertain to public transport Ptv2 scheme
   */
  private static final Set<String> PTV2_VALUE_TAGS = new HashSet<String>();
      
  /**
   * populate the possible pt v2 value tags that we support
   */
  private static void populateOsmPublicTransportValueTags() {
    PTV2_VALUE_TAGS.add(PLATFORM);
    PTV2_VALUE_TAGS.add(STATION);
    PTV2_VALUE_TAGS.add(STOP_AREA);
    PTV2_VALUE_TAGS.add(STOP_POSITION);
    PTV2_VALUE_TAGS.add(CONSTRUCTION);
  }  
  
  
  static {
    populateOsmPublicTransportValueTags();
  }     
  
  /* KEY */ 
  
  /** all Ptv2 scheme tags use the public_transport=* key */
  public static final String PUBLIC_TRANSPORT = "public_transport";
  
  /* VALUES */
  
  
  /** The place where passengers are waiting for the public transport vehicles, identical to the Ptv1 tag value */
  public static final String PLATFORM = OsmPtv1Tags.PLATFORM;
  
  /** A station is an area designed to access public transport, identical to Ptv1 tag value */
  public static final String STATION = OsmPtv1Tags.STATION;
  
  /** general tag indication this is under construction, parser should ignore this*/
  public static final String CONSTRUCTION = OsmRailwayTags.CONSTRUCTION;    
  
  /** A relation that contains all elements of a train, subway, monorail, tram, bus, trolleybus, aerialway, or ferry stop */
  public static final String STOP_AREA = "stop_area";
  
  /** The position on the street or rails where a public transport vehicle stops */
  public static final String STOP_POSITION = "stop_position";
  
  /* role names when part of stop_area relation */
  
  /** stop_position members in a stop_area relation take on this role identifier */
  public static final String STOP_POSITION_ROLE = "stop";
  
  /** platform members in a stop_area relation take on this role identifier */
  public static final String PLATFORM_ROLE = "platform";    
        
  /** collect the value tags as they are currently registered
   * 
   * @return value tags
   */
  public static final Set<String> getPtv2ValueTags(){
    return PTV2_VALUE_TAGS;
  }
  
  /** Verify if any of the tags is a match for the supported Ptv2 scheme tags
   * 
   * @param tags to check against
   * @return true when present, false otherwise
   */
  public static boolean hasPtv2ValueTag(final Map<String, String> tags) {
    if(OsmPtv2Tags.hasPublicTransportKeyTag(tags)) {
      return PTV2_VALUE_TAGS.contains(tags.get(PUBLIC_TRANSPORT));
    }
    return false;
  }

  /** Verify if tags contain "public_transport" key tag
   * 
   * @param tags to check
   * @return true when present, false otherwise
   */
  public static boolean hasPublicTransportKeyTag(Map<String, String> tags) {
    return tags.containsKey(PUBLIC_TRANSPORT);
  }  
}

package org.planit.osm.util;

import java.util.Map;

/**
 * common OSM tags that we might need to refer to
 *
 */
public class OsmTags {
      
    /*  tags related to nodes */
    
    public static final String JUNCTION = "junction";
    
    public static final String TRAFFIC_SINGALS = "traffic_signals";

    public static final String CROSSING = "crossing";    
                     
    /* misc */    
    
    public static final String NAME = "name";
    
    public static final String NO = "no";
    
    public static final String YES = "yes";

    public static final Object AREA = "area";

    /** check if tags indicate the entity is in fact an area
     * @param tags to verify
     * @return true when area, false otherwise
     */
    public static boolean isArea(Map<String, String> tags) {
      return tags.containsKey(AREA) && !tags.get(AREA).equals(NO);
    }
}

package org.planit.osm.tags;

import java.util.Map;

/**
 * common OSM tags that we might need to refer to
 *
 */
public class OsmTags {
      
    /*  tags related to nodes */
        
    public static final String TRAFFIC_SINGALS = "traffic_signals";

    public static final String CROSSING = "crossing";    
                     
    /* misc */    
    
    public static final String NAME = "name";
    
    public static final String NO = "no";
    
    public static final String NONE = "none";    
    
    public static final String YES = "yes";

    public static final Object AREA = "area";
    
    public static final String LEFT = "left";
    
    public static final String RIGHT = "right";
    
    public static final String BOTH = "both";

    public static final String PROPOSED = "proposed";
    
    public static final String TYPE = "type";
    
    public static final String REF = "ref";
    
    public static final String LOC_REF = "loc_ref";
    
    public static final String LOCAL_REF = "local_ref";


    /** Check if tags indicate the entity is in fact an area
     * 
     * @param tags to verify
     * @return true when area, false otherwise
     */
    public static boolean isArea(Map<String, String> tags) {
      return tags.containsKey(AREA) && !tags.get(AREA).equals(NO);
    }
}

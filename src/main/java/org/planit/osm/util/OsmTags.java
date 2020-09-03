package org.planit.osm.util;

/**
 * common OSM tags that we might need to refer to
 *
 */
public class OsmTags {
      
    /*  tags related to nodes */
    
    public static final String JUNCTION = "junction";
    
    public static final String TRAFFIC_SINGALS = "traffic_signals";

    public static final String CROSSING = "crossing";    
    
    /* tags related to links of a certain type */

    public static final String MAXSPEED = "maxspeed";
    
    /* bicycle specific */

    public static final String BICYCLE = "bicycle";    

    public final static String ONEWAYBICYCLE = "oneway:bicycle";
    
    /* other keys */
    
    public static final String DIRECTION = "direction";

    /* units related */
    
    public static final String MPH = "mph";
    
    /* misc */    

    public static final String TYPE = "type";

    public static final String RESTRICTION = "restriction";
}

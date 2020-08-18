package org.planit.osm.util;

/**
 * common OSM tags that we might need to refer to
 *
 */
public class PlanitOSMTags {
  
    /* main tag for roads */
  
    public static final String HIGHWAY = "highway";
    
    /* typically sub tags for types of roads
     * in order of size, i.e. more significant infrastructure is listed first */

    public static final String MOTORWAY = "motorway";

    public static final String MOTORWAY_LINK = "motorway_link";

    public static final String TRUNK = "trunk";

    public static final String TRUNK_LINK = "trunk_link";

    public static final String PRIMARY = "primary";

    public static final String PRIMARY_LINK = "primary_link";

    public static final String SECONDARY = "secondary";

    public static final String SECONDARY_LINK = "secondary_link";

    public static final String TERTIARY = "tertiary";

    public static final String TERTIARY_LINK = "tertiary_link";

    public static final String UNCLASSIFIED = "unclassified";

    public static final String RESIDENTIAL = "residential";

    public static final String LIVING_STREET = "living_street";
    
    public static final String PEDESTRIAN = "pedestrian";    

    public static final String TRACK = "track";
    
    public static final String ROAD = "road";
    
    /* end of "normal" highway types */

    public static final String CYCLEWAY = "cycleway";

    public static final String SERVICE = "service";

    public static final String PATH = "path";

    public static final String STEPS = "steps";

    /* special tags related to nodes */
    
    public static final String ROUNDABOUT = "roundabout";

    public static final String JUNCTION = "junction";
    
    public static final String TRAFFIC_SINGALS = "traffic_signals";

    public static final String CROSSING = "crossing";    
    
    /* tags related to links of a certain type */

    public static final String MAXSPEED = "maxspeed";

    public static final String ONEWAY = "oneway";

    public static final String LANES = "lanes";

    public static final String LANES_FORWARD = "lanes:forward";

    public static final String LANES_BACKWARD = "lanes:backward";

    
    /* bicycle specific */

    public static final String BICYCLE = "bicycle";

    public final static String ONEWAYBICYCLE = "oneway:bicycle";

    /* units related */
    
    public static final String MPH = "mph";
    
    /* misc */    

    public static final String TYPE = "type";

    public static final String RESTRICTION = "restriction";
}

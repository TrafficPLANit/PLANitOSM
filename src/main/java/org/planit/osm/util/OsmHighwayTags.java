package org.planit.osm.util;

import java.util.HashSet;
import java.util.Set;

/**
 * OSM "highway" values, e.g. highway=<option>
 * 
 * @author markr
 *
 */
public class OsmHighwayTags {
  
    /** all currently available osm highway tags */
    private static final Set<String> highwayTags = new HashSet<String>();
    
    /**
     * populate the available highway tags
     */
    private static void populateHighwayTags() {
      highwayTags.add(MOTORWAY);
      highwayTags.add(MOTORWAY_LINK);
      highwayTags.add(TRUNK);
      highwayTags.add(TRUNK_LINK);
      highwayTags.add(PRIMARY);
      highwayTags.add(PRIMARY_LINK);
      highwayTags.add(SECONDARY);
      highwayTags.add(SECONDARY_LINK);
      highwayTags.add(TERTIARY);
      highwayTags.add(TERTIARY_LINK);
      highwayTags.add(UNCLASSIFIED);
      highwayTags.add(RESIDENTIAL);
      highwayTags.add(LIVING_STREET);
      highwayTags.add(PEDESTRIAN);
      highwayTags.add(TRACK);
      highwayTags.add(ROAD);
      highwayTags.add(SERVICE);
      highwayTags.add(FOOTWAY);
      highwayTags.add(BRIDLEWAY);
      highwayTags.add(STEPS);
      highwayTags.add(CORRIDOR);
      highwayTags.add(CYCLEWAY);
      highwayTags.add(PATH);
      highwayTags.add(ELEVATOR);
      highwayTags.add(PLATFORM);
      highwayTags.add(PROPOSED);
      highwayTags.add(CONSTRUCTION);
      highwayTags.add(TURNING_CIRCLE);
    }
    
    static {
      populateHighwayTags();      
    }
  
    /* key */
    public static final String HIGHWAY = "highway";
  
    /* values */
    
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
    
    public static final String SERVICE = "service";    
    
    /* (typically) non-vehicle highway types */

    public static final String FOOTWAY = "footway";
    
    public static final String BRIDLEWAY = "bridleway";
    
    public static final String STEPS = "steps";
    
    public static final String CORRIDOR = "corridor";
       
    public static final String CYCLEWAY = "cycleway";
    
    public static final String PATH = "path";
    
    public static final String ELEVATOR = "elevator";
    
    public static final String PLATFORM = "platform";
    
    /* other highway types */
    
    public static final String PROPOSED = "proposed";
    
    public static final String CONSTRUCTION = "construction";
    
    public static final String TURNING_CIRCLE = "turning_circle";
    
    /** verify if passed in tag is indeed a highway tag
     * @param highwayTag to verify
     * @return true when valid tag, otherwise false
     */
    public static boolean isHighwayValueTag(String highwayTag) {
      return highwayTags.contains(highwayTag);
    }
    
    /** verify if passed in tag is indeed the highway key tag
     * @param highwayTag to verify
     * @return true when valid tag, otherwise false
     */
    public static boolean isHighwayKeyTag(String highwayTag) {
      return HIGHWAY.equals(highwayTag);
    }    

}

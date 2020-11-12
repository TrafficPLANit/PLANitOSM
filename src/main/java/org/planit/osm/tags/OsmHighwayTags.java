package org.planit.osm.tags;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * OSM "highway" values, e.g. highway=<option>
 * 
 * @author markr
 *
 */
public class OsmHighwayTags {
  
    /** all currently available osm highway tags that can represent a road link */
    private static final Set<String> ROADBASED_HIGHWAY_VALUE_TAGS = new HashSet<String>();
    
    /** all currently supported highway tags that represent geographic areas, e.g. rest areas on top or alongside a road , this is a subset of
     * the {@code NON_ROADBASED_HIGHWAY_VALUE_TAGS} */
    private static final Set<String> AREABASED_OSM_HIGHWAY_VALUE_TAGS = new HashSet<String>();    
    
    /**
     * the OSM highway values that are marked as non-road types, i.e., they can never be activated to be converted into links
     */
    protected static final Set<String> NON_ROADBASED_HIGHWAY_VALUE_TAGS = new HashSet<String>();
    
    
    /**
     * populate the available highway tags
     */
    private static void populateRoadBasedOsmHighwayTags() {
      ROADBASED_HIGHWAY_VALUE_TAGS.add(MOTORWAY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(MOTORWAY_LINK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(TRUNK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(TRUNK_LINK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(PRIMARY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(PRIMARY_LINK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(SECONDARY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(SECONDARY_LINK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(TERTIARY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(TERTIARY_LINK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(UNCLASSIFIED);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(RESIDENTIAL);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(LIVING_STREET);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(PEDESTRIAN);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(TRACK);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(ROAD);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(SERVICE);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(FOOTWAY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(BRIDLEWAY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(STEPS);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(CORRIDOR);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(CYCLEWAY);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(PATH);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(ELEVATOR);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(PROPOSED);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(CONSTRUCTION);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(TURNING_CIRCLE);
      ROADBASED_HIGHWAY_VALUE_TAGS.add(RACEWAY);
    }
    
    /**
     * populate the available highway area tags. These area tags are here to identify ways that are in fact NOT
     * highways in the traditional sense, yet they are provided as such and have tags to indicate they represent something
     * else than a road. This is a subset of the non road based highway value tags
     * 
     * <ul>
     * <li>platform</li>
     * <li>services</li>
     * <li>rest_area</li>
     * </ul>
     */
    private static void populateAreaBasedOsmHighwayValueTags() {
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.add(PLATFORM);
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.add(SERVICES);
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.add(REST_AREA);    
    }      
    
    /**
     * Since we are building a macroscopic network based on OSM, but some OSM highway types are in fact not roads at all we list such 
     * non-road types as well so we can avoid generating warning messages in case a highway type cannot be matched to either an activated or
     * deactivated type, i.e., when neither it is an unknown type or a non-road type, in the latter case these can be filtered out using this
     * listing. Note that area based highway value tags are a subset of the non road based highway tags.
     * 
     * <ul>
     * <li>BUS_STOP</li>
     * </ul>
     * 
     * @return the default created unsupported types
     */
    private static void populateNonRoadBasedOsmHighwayTags(){
      NON_ROADBASED_HIGHWAY_VALUE_TAGS.addAll(AREABASED_OSM_HIGHWAY_VALUE_TAGS);
      
      NON_ROADBASED_HIGHWAY_VALUE_TAGS.add(OsmHighwayTags.BUS_STOP);
    }      
    
    static {
      populateRoadBasedOsmHighwayTags();  
      populateAreaBasedOsmHighwayValueTags();
      populateNonRoadBasedOsmHighwayTags();
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
    
    /* (typically) non-vehicle highway types that still can be used as a link*/

    /** footway can be used as highway=footway, or footway=sidewalk/crossing */
    public static final String FOOTWAY ="footway";
    
    public static final String BRIDLEWAY = "bridleway";
    
    public static final String STEPS = "steps";
    
    public static final String CORRIDOR = "corridor";
       
    public static final String CYCLEWAY = "cycleway";
    
    public static final String PATH = "path";
    
    public static final String ELEVATOR = "elevator";
    
    public static final String PROPOSED = "proposed";
    
    public static final String CONSTRUCTION = "construction";
    
    public static final String TURNING_CIRCLE = "turning_circle";

    public static final String RACEWAY = "raceway";    
    
    /* area based highway value tags */
    
    private static final String SERVICES = "services";

    private static final String REST_AREA = "rest_area";
    
    public static final String PLATFORM =  OsmPublicTransportTags.PLATFORM;    
        
    /* other highway types that do not signify a road or link but are valid values*/    
 
    public static final String BUS_STOP = OsmPublicTransportTags.BUS_STOP;
    
    /** verify if passed in tag is indeed a highway tag that represents a road like piece of infrastructure
     * 
     * @param highwayTag to verify
     * @return true when valid tag, otherwise false
     */
    public static boolean isRoadBasedHighwayValueTag(String highwayTag) {
      return ROADBASED_HIGHWAY_VALUE_TAGS.contains(highwayTag);
    }
    
    /** some rail based ways can be areas when tagged in a certain way. currently we do this for both stations and platforms
     * although technically platforms can be ways, but since we do not model them (as ways), we regard them as areas in all cases for now

     * @param osmWay the way
     * @param tags the tags
     * @return is the way an area and not a line based railway
     */
    public static boolean isAreaBasedHighway(String highwayTag) {
      return AREABASED_OSM_HIGHWAY_VALUE_TAGS.contains(highwayTag);
    }     
    
    /** verify if passed in tag is indeed a highway tag that represents a non-road like piece of infrastructure
     * 
     * @param highwayTag to verify
     * @return true when valid tag, otherwise false
     */
    public static boolean isNonRoadBasedHighwayValueTag(String highwayTag) {
      return NON_ROADBASED_HIGHWAY_VALUE_TAGS.contains(highwayTag);
    }    
    
    /** verify if passed in tag is indeed the highway key tag
     * @param highwayTag to verify
     * @return true when valid tag, otherwise false
     */
    public static boolean isHighwayKeyTag(String highwayTag) {
      return HIGHWAY.equals(highwayTag);
    }

    /** Verify if tags contain the highway key
     * 
     * @param tags to verify
     * @return true if highway=* exists, false otherwise
     */
    public static boolean hasHighwayKeyTag(Map<String, String> tags) {
      return tags.containsKey(OsmHighwayTags.HIGHWAY);
    }    

}

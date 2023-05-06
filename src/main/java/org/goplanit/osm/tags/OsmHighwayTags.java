package org.goplanit.osm.tags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * OSM "highway" values, e.g. {@code highway=<option>}. Tags specific to the Ptv1 scheme are collected via the OsmPtv1 tags class
 * and integrated in the collections managed by this class.
 * 
 * @author markr
 *
 */
public class OsmHighwayTags {
  
    /** all currently available osm highway tags that can represent a road link, the number is used for ordering, so we can compare importance */
    private static final Map<String,Integer> ROADBASED_HIGHWAY_VALUE_TAGS = new HashMap<String, Integer>();
    
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
      ROADBASED_HIGHWAY_VALUE_TAGS.put(MOTORWAY,1);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(MOTORWAY_LINK,2);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(TRUNK,3);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(TRUNK_LINK,4);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(PRIMARY,5);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(PRIMARY_LINK,6);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(SECONDARY,7);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(SECONDARY_LINK,8);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(TERTIARY,9);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(TERTIARY_LINK,10);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(SERVICE,11);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(RESIDENTIAL,12);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(LIVING_STREET,13);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(TRACK,14);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(ROAD,15);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(TURNING_CIRCLE,16);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(RACEWAY,17);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(CYCLEWAY,18);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(PEDESTRIAN,19);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(FOOTWAY,20);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(STEPS,21);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(CORRIDOR,22);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(ELEVATOR,23);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(BRIDLEWAY,24);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(PATH,25);      
      ROADBASED_HIGHWAY_VALUE_TAGS.put(PROPOSED,26);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(CONSTRUCTION,27);
      ROADBASED_HIGHWAY_VALUE_TAGS.put(UNCLASSIFIED,28);      
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
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.add(SERVICES);
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.add(REST_AREA);
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.addAll(OsmPtv1Tags.getAreaBasedHighwayValueTags());
      AREABASED_OSM_HIGHWAY_VALUE_TAGS.addAll(OsmPtv1Tags.getAreaBasedRailwayValueTags());
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
    
    /** area based non-road tag */
    private static final String SERVICES = "services";

    /** area based non-road tag */
    private static final String REST_AREA = "rest_area";
    
            
    /** Verify if passed in tag is indeed a highway tag that represents a road like piece of infrastructure
     * 
     * @param highwayTag to verify
     * @return true when valid tag, otherwise false
     */
    public static boolean isRoadBasedHighwayValueTag(String highwayTag) {
      return ROADBASED_HIGHWAY_VALUE_TAGS.containsKey(highwayTag);
    }
    
    /** Some rail based ways can be areas when tagged in a certain way. currently we do this for both stations and platforms
     * although technically platforms can be ways, but since we do not model them (as ways), we regard them as areas in all cases for now

     * @param highwayTag the way
     * @return is the way an area and not a line based railway
     */
    public static boolean isAreaBasedHighway(String highwayTag) {
      return AREABASED_OSM_HIGHWAY_VALUE_TAGS.contains(highwayTag);
    }     
    
    /** Verify if passed in tag is indeed a highway tag that represents a non-road like piece of infrastructure
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
      return tags.containsKey(getHighwayKeyTag());
    }

    /**
     * Collect the highway key tag
     *
     * @return highway key tag
     */
    public static String getHighwayKeyTag() {
      return OsmHighwayTags.HIGHWAY;
    }

    /** Compare highway types where we return a negative, zero, or positive value when highwayType is less, equal, or more important than the other
     * 
     * @param highwayType to compare to other
     * @param otherHighwayType to compare to highway type
     * @return negative, equal, or zero depending on if highway type is less, equal, or more important than other 
     */
    public static int compareHighwayType(String highwayType, String otherHighwayType) {
      return ROADBASED_HIGHWAY_VALUE_TAGS.get(otherHighwayType) - ROADBASED_HIGHWAY_VALUE_TAGS.get(highwayType);
    }    

}

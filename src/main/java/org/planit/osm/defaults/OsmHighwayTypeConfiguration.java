package org.planit.osm.defaults;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.utils.exceptions.PlanItException;

/**
 * Configuration options regarding the activation/deactivation of specific OSM highway types in the parser   
 * 
 * @author markr
 *
 */
public class OsmHighwayTypeConfiguration extends OsmInfrastructureConfiguration {
  
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmHighwayTypeConfiguration.class.getCanonicalName());
  
  /**
   * the OSM highway types that are marked as activated OSM types, i.e., will be processed when parsing
   */
  protected static final Set<String> DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES = new HashSet<String>();
  
  /**
   * the OSM highway  types that are marked as deactivated OSM types, i.e., will be ignored when parsing
   */
  protected static final Set<String> DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES = new HashSet<String>();
     
    
  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM highway types to macroscopic link segment types that we explicitly do include, i.e., support.
   * 
   * <ul>
   * <li>MOTORWAY</li>
   * <li>MOTORWAY_LINK</li>
   * <li>TRUNK</li>
   * <li>TRUNK_LINK</li>
   * <li>PRIMARY</li>
   * <li>PRIMARY_LINK</li>
   * <li>SECONDARY</li>
   * <li>SECONDARY_LINK</li>
   * <li>TERTIARY</li>
   * <li>TERTIARY_LINK</li>
   * <li>UNCLASSIFIED</li>
   * <li>RESIDENTIAL</li>
   * <li>LIVING_STREET</li>
   * <li>SERVICE</li>
   * <li>PEDESTRIAN</li>
   * <li>TRACK</li>
   * <li>ROAD</li>
   * </ul>
   * 
   * @return the default created supported types 
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultActivatedOsmHighwayTypes() throws PlanItException {
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.MOTORWAY);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.MOTORWAY_LINK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.TRUNK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.TRUNK_LINK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.PRIMARY);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.PRIMARY_LINK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.SECONDARY);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.SECONDARY_LINK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.TERTIARY);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.TERTIARY_LINK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.UNCLASSIFIED);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.RESIDENTIAL);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.LIVING_STREET);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.SERVICE);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.PEDESTRIAN);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.TRACK);
    DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.ROAD);
  }
  
  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM highway types to macroscopic link segment types that we explicitly do not include
   * 
   * <ul>
   * <li>FOOTWAY</li>
   * <li>BRIDLEWAY</li>
   * <li>STEPS</li>
   * <li>CORRIDOR</li>
   * <li>CYCLEWAY</li>
   * <li>PATH</li>
   * <li>ELEVATOR</li>
   * <li>PROPOSED</li>
   * <li>CONSTRUCTION</li>
   * <li>TURNING_CIRCLE</li>
   * <li>RACE_WAY</li>
   * </ul>
   * 
   * @return the default created unsupported types
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultDeactivatedOsmHighwayTypes() throws PlanItException {
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.FOOTWAY);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.BRIDLEWAY);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.STEPS);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.CORRIDOR);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.CYCLEWAY);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.PATH);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.ELEVATOR);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.PLATFORM);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.PROPOSED);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.CONSTRUCTION);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.TURNING_CIRCLE);
    DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES.add(OsmHighwayTags.RACEWAY);
  }   
  
  
  /**
   * conduct general initialisation for any instance of this class
   * 
   * @param planitModes to populate based on (default) mapping
   */
  static {
    try {
      /* the highway types that will be parsed by default, i.e., supported. */
      initialiseDefaultActivatedOsmHighwayTypes();
      /* the highway types that will not be parsed by default, i.e., unsupported. */
      initialiseDefaultDeactivatedOsmHighwayTypes();
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default activated and/or deactivated OSM highway types for this network");
    }     
  }
  
  /**
   * default constructor
   */
  public OsmHighwayTypeConfiguration() {
    super(OsmHighwayTags.HIGHWAY, DEFAULT_ACTIVATED_OSM_HIGHWAY_TYPES, DEFAULT_DEACTIVATED_OSM_HIGHWAY_TYPES);
  }
   
}

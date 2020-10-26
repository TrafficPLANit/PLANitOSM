package org.planit.osm.defaults;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
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
   * the OSM types that are marked as supported OSM types, i.e., will be processed when parsing
   */
  protected static final Set<String> defaultActivatedOsmHighwayTypes = new HashSet<String>();
  
  /**
   * the OSM types that are marked as unsupported OSM types, i.e., will be ignored when parsing
   */
  protected static final Set<String> defaultDeactivatedOsmHighwayTypes = new HashSet<String>();  
    
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
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.MOTORWAY);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.MOTORWAY_LINK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.TRUNK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.TRUNK_LINK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.PRIMARY);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.PRIMARY_LINK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.SECONDARY);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.SECONDARY_LINK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.TERTIARY);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.TERTIARY_LINK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.UNCLASSIFIED);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.RESIDENTIAL);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.LIVING_STREET);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.SERVICE);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.PEDESTRIAN);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.TRACK);
    defaultActivatedOsmHighwayTypes.add(OsmHighwayTags.ROAD);
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
   * <li>PLATFORM</li>
   * <li>PROPOSED</li>
   * <li>CONSTRUCTION</li>
   * <li>TURNING_CIRCLE</li>
   * </ul>
   * 
   * @return the default created unsupported types
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultDeactivatedOsmHighwayTypes() throws PlanItException {
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.FOOTWAY);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.BRIDLEWAY);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.STEPS);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.CORRIDOR);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.CYCLEWAY);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.PATH);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.ELEVATOR);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.PLATFORM);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.PROPOSED);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.CONSTRUCTION);
    defaultDeactivatedOsmHighwayTypes.add(OsmHighwayTags.TURNING_CIRCLE);
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
    super(OsmHighwayTags.HIGHWAY, defaultActivatedOsmHighwayTypes, defaultDeactivatedOsmHighwayTypes);
  }
   
}

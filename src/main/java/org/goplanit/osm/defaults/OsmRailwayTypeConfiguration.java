package org.goplanit.osm.defaults;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.utils.exceptions.PlanItException;

/**
 * Configuration options regarding the activation/deactivation of specific OSM railway types in the parser  
 * 
 * @author markr
 *
 */
public class OsmRailwayTypeConfiguration extends OsmInfrastructureConfigurationImpl {
  
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmRailwayTypeConfiguration.class.getCanonicalName());  
  
  /**
   * the OSM railway types that are marked as activated OSM types, i.e., will be processed when parsing
   */
  protected static final Set<String> DEFAULT_ACTIVATED_OSM_RAILWAY_TYPES = new HashSet<>();
  
  /**
   * the OSM railway types that are marked as deactivated OSM types, i.e., will be ignored when parsing
   */
  protected static final Set<String> DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES = new HashSet<>();


  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM railway types to macroscopic link segment types that we explicitly do include, i.e., support.
   * 
   * <ul>
   * <li>LIGHT_RAIL</li>
   * <li>RAIL</li>
   * <li>SUBWAY</li>
   * <li>TRAM</li>
   * </ul>
   * 
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultActivatedOsmRailwayTypes() throws PlanItException {
    DEFAULT_ACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.LIGHT_RAIL);
    DEFAULT_ACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.RAIL);
    DEFAULT_ACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.SUBWAY);
    DEFAULT_ACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.TRAM);    
  }
  
  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM railway types to macroscopic link segment types that we explicitly do not activate either because
   * they are not used in general assignment (miniature) or because we cannot properly convert them (turn tables, razed), or because
   * they do not represent a railway as such (platform as a way)
   * 
   * <ul>
   * <li>FUNICULAR</li>
   * <li>MONO_RAIL</li>
   * <li>NARROW_GAUGE</li>
   * <li>ABANDONED</li>
   * <li>CONSTRUCTION</li> 
   * <li>DISUSED</li>
   * <li>MINIATURE</li>
   * <li>RAZED</li>
   * <li>TURNTABLE</li>
   * <li>PROPOSED</li>
   * </ul>
   * 
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultDeactivatedOsmRailwayTypes() throws PlanItException {
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.FUNICULAR);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.MONO_RAIL);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.NARROW_GAUGE);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.ABANDONED);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.CONSTRUCTION);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.DISUSED);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.MINIATURE);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.RAZED);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.TURNTABLE);
    DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES.add(OsmRailwayTags.PROPOSED);
  }
    
  
  /**
   * conduct general initialisation for any instance of this class
   * 
   * @param planitModes to populate based on (default) mapping
   */
  static {
    try {
      /* the railway types that will be parsed by default, i.e., supported. */
      initialiseDefaultActivatedOsmRailwayTypes();
      /* the railway types that will not be parsed by default, i.e., unsupported. */
      initialiseDefaultDeactivatedOsmRailwayTypes();      
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default supported and/or unsupported OSM railway types for this network");
    }     
  }  
  
  /**
   * Default constructor
   */
  public OsmRailwayTypeConfiguration(){
    super(OsmRailwayTags.RAILWAY, DEFAULT_ACTIVATED_OSM_RAILWAY_TYPES, DEFAULT_DEACTIVATED_OSM_RAILWAY_TYPES);    
  }

}

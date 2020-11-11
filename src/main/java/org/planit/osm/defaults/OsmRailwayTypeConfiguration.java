package org.planit.osm.defaults;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmRailWayTags;
import org.planit.utils.exceptions.PlanItException;

/**
 * Configuration options regarding the activation/deactivation of specific OSM railway types in the parser  
 * 
 * @author markr
 *
 */
/**
 * @author markr
 *
 */
/**
 * @author markr
 *
 */
public class OsmRailwayTypeConfiguration extends OsmInfrastructureConfiguration {
  
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmRailwayTypeConfiguration.class.getCanonicalName());  
  
  /**
   * the OSM railway types that are marked as supported OSM types, i.e., will be processed when parsing
   */
  protected static final Set<String> defaultSupportedOsmRailWayTypes = new HashSet<String>();
  
  /**
   * the OSM railway types that are marked as unsupported OSM types, i.e., will be ignored when parsing
   */
  protected static final Set<String> defaultUnsupportedOsmRailWayTypes = new HashSet<String>(); 
    
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
   * @return the default created supported types 
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultSupportedOsmRailwayTypes() throws PlanItException {
    defaultSupportedOsmRailWayTypes.add(OsmRailWayTags.LIGHT_RAIL);
    defaultSupportedOsmRailWayTypes.add(OsmRailWayTags.RAIL);
    defaultSupportedOsmRailWayTypes.add(OsmRailWayTags.SUBWAY);
    defaultSupportedOsmRailWayTypes.add(OsmRailWayTags.TRAM);    
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
   * <li>PLATFORM</li>
   * <li>ABANDONED</li>
   * <li>CONSTRUCTION</li> 
   * <li>DISUSED</li>
   * <li>MINIATURE</li>
   * <li>RAZED</li>
   * <li>TURNTABLE</li>
   * <li>PROPOSED</li>
   * <li>PLATFORM_EDGE</li>
   * </ul>
   * 
   * @return the default created unsupported types
   * @throws PlanItException thrown when error
   */
  protected static void initialiseDefaultUnsupportedOsmRailwayTypes() throws PlanItException {
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.FUNICULAR);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.MONO_RAIL);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.NARROW_GAUGE);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.PLATFORM);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.ABANDONED);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.CONSTRUCTION);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.DISUSED);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.MINIATURE);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.RAZED);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.TURNTABLE);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.PROPOSED);
    defaultUnsupportedOsmRailWayTypes.add(OsmRailWayTags.PLATFORM_EDGE);
  }
  
  /**
   * conduct general initialisation for any instance of this class
   * 
   * @param planitModes to populate based on (default) mapping
   */
  static {
    try {
      /* the railway types that will be parsed by default, i.e., supported. */
      initialiseDefaultSupportedOsmRailwayTypes();
      /* the railway types that will not be parsed by default, i.e., unsupported. */
      initialiseDefaultUnsupportedOsmRailwayTypes();      
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default supported and/or unsupported OSM railway types for this network");
    }     
  }  
  
  /**
   * Default constructor
   */
  public OsmRailwayTypeConfiguration(){
    super(OsmRailWayTags.RAILWAY, defaultSupportedOsmRailWayTypes, defaultUnsupportedOsmRailWayTypes);    
  }

}

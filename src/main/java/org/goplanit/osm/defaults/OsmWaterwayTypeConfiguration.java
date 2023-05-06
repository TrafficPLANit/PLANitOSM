package org.goplanit.osm.defaults;

import org.goplanit.osm.tags.OsmWaterwayTags;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Configuration options regarding the activation/deactivation of specific OSM waterway route types in the parser.
 * Currently no such differentiation exists as a water way is directly attached to a mode only, but we provide a
 * implementation for water modes for consistency reasons.
 * 
 * @author markr
 *
 */
public class OsmWaterwayTypeConfiguration extends OsmInfrastructureConfiguration {

  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmWaterwayTypeConfiguration.class.getCanonicalName());

  /**
   * the OSM waterway types that are marked as activated OSM types, i.e., will be processed when parsing
   */
  protected static final Set<String> DEFAULT_ACTIVATED_OSM_WATERWAY_ROUTE_TYPES = new HashSet<>();

  /**
   * the OSM waterway types that are marked as deactivated OSM types, i.e., will be ignored when parsing
   */
  protected static final Set<String> DEFAULT_DEACTIVATED_OSM_WATERWAY_ROUTE_TYPES = new HashSet<>();


  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM water ways (route=ferry) to macroscopic link segment types that we explicitly do include, i.e., support.
   *
   * <ul>
   * <li>FERRY</li>
   * </ul>
   *
   */
  protected static void initialiseDefaultActivatedOsmRailwayTypes(){
    DEFAULT_ACTIVATED_OSM_WATERWAY_ROUTE_TYPES.add(OsmWaterwayTags.FERRY);
  }

  /**
   * Default constructor
   */
  public OsmWaterwayTypeConfiguration(){
    super(OsmWaterwayTags.getWaterwayKeyTag(), DEFAULT_ACTIVATED_OSM_WATERWAY_ROUTE_TYPES, DEFAULT_DEACTIVATED_OSM_WATERWAY_ROUTE_TYPES);
  }

}

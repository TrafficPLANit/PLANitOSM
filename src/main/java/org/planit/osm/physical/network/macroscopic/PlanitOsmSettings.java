package org.planit.osm.physical.network.macroscopic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitGeoUtils;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;

/**
 * Settings for the OSM reader
 * 
 * @author markr
 *
 */
public class PlanitOsmSettings {
    
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmSettings.class.getCanonicalName());  
   
  /**
   * the OSM highway types that are marked as supported OSM types, i.e., will be processed when parsing
   */
  protected final Set<String> supportedOSMLinkSegmentTypes = new HashSet<String>();
  
  /**
   * track overwrite values for OSM highway types where we want different defaults for capacity and max density
   */
  protected Map<String, Pair<Double,Double>> overwriteByOSMHighwayType = new HashMap<String, Pair<Double,Double>>();
  
  /* SETTINGS */
  
  /**
   * the OSM highway types that are marked as unsupported OSM types, i.e., will be ignored when parsing
   */
  protected final Set<String> unsupportedOSMLinkSegmentTypes = new HashSet<String>();
    
    
  /** the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS;
    
  /**
   * When the user has activated a highway type for which the reader has no support, this alternative will be used, default is
   * set to PlanitOSMTags.TERTIARY. Note in case this is also not available on the reader, the type will be ignored altogether
   */
  protected String defaultOSMHighwayTypeWhenUnsupported = OsmHighwayTags.TERTIARY;  
    
  /**
   * option to track the geometry of an OSM way, i.e., extract the line string for link segments from the nodes
   * (default is false). When set to true parsing will be somewhat slower 
   */
  protected boolean parseOsmWayGeometry = false; 
 
  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM highway types to macroscopic link segment types that we explicitly do include, i.e., support.
   * 
   * @return the default created supported types 
   * @throws PlanItException thrown when error
   */
  protected void initialiseDefaultSupportedOSMHighwayTypes() throws PlanItException {
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TRUNK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TRUNK_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.PRIMARY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.PRIMARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SECONDARY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TERTIARY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TERTIARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.UNCLASSIFIED);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.RESIDENTIAL);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.LIVING_STREET);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SERVICE);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.PEDESTRIAN);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TRACK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.ROAD);
  }
  
  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM highway types to macroscopic link segment types that we explicitly do not include
   * 
   * @return the default created unsupported types
   * @throws PlanItException thrown when error
   */
  protected void initialiseDefaultUnsupportedOSMHighwayTypes() throws PlanItException {
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.FOOTWAY);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.BRIDLEWAY);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.STEPS);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.CORRIDOR);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.CYCLEWAY);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.PATH);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.ELEVATOR);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.PLATFORM);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.PROPOSED);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.CONSTRUCTION);
    unsupportedOSMLinkSegmentTypes.add(OsmHighwayTags.TURNING_CIRCLE);
  }   
    
   
    
  /**
   * Constructor
   */
  public PlanitOsmSettings() {    
    try {
      /* the highway types that will be parsed by default, i.e., supported. */
      initialiseDefaultSupportedOSMHighwayTypes();
      /* the highway types that will not be parsed by default, i.e., unsupported. */
      initialiseDefaultUnsupportedOSMHighwayTypes();
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default supported and/or unsupported OSM link segment types for this network");
    }    
  }   

  /**
   * chosen crs, default is {@code PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS}
   * @return
   */
  public final CoordinateReferenceSystem getSourceCRS() {
    return sourceCRS;
  }

  /**
   * Override source CRS
   * 
   * @param sourceCRS
   */
  public void setSourceCRS(final CoordinateReferenceSystem sourceCRS) {
    this.sourceCRS = sourceCRS;
  }
  
  /**
   * 
   * When the parsed way has a type that is not supported, this alternative will be used
   * @return chosen default
   * 
   **/
  public final String getOSMHighwayTypeWhenUnsupported() {
    return defaultOSMHighwayTypeWhenUnsupported;
  }

  /**
   * set the default to be used when we encounter an unsupported type.
   * 
   * @param defaultOSMHighwayValueWhenUnsupported the default to use, should be a type that is supported.
   */
  public void setDefaultOSMHighwayTypeeWhenUnsupported(String defaultOSMHighwayValueWhenUnsupported) {
    this.defaultOSMHighwayTypeWhenUnsupported = defaultOSMHighwayValueWhenUnsupported;
  } 
  
  /**
   * 
   * check if a default type is set when the activate type is not supported
   * @return true when available false otherwise
   **/
  public final boolean hasOSMHighwayTypeWhenUnsupported() {
    return defaultOSMHighwayTypeWhenUnsupported==null;
  }  
  
  /**
   * remove default type in case activate type is not supported by the reader
   */
  public final void removeOSMHighwayTypeWhenUnsupported() {
    defaultOSMHighwayTypeWhenUnsupported=null;
  }    
  
  /**
   * Verify if the passed in OSM highway type is explicitly unsupported. Unsupported types will be ignored
   * when processing ways.
   * 
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOSMHighwayTypeUnsupported(String osmHighwayType) {
    return unsupportedOSMLinkSegmentTypes.contains(osmHighwayType);
  }
  
  /**
   * Verify if the passed in OSM highway type is explicitly supported. Supported types will be processed 
   * and converted into link(segments).
   * 
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOSMHighwayTypeSupported(String osmHighwayType) {
    return supportedOSMLinkSegmentTypes.contains(osmHighwayType);
  }
  
  /**
   * Choose to not parse the given highway type
   * 
   * @param osmHighwayType
   */
  public void excludeOSMHighwayType(String osmHighwayType) {
    supportedOSMLinkSegmentTypes.remove(osmHighwayType);
    unsupportedOSMLinkSegmentTypes.add(osmHighwayType);
  }
  
  /**
   * Choose to add given highway type to parsed types on top of the defaults
   * 
   * @param osmHighwayType
   */
  public void includeOSMHighwayType(String osmHighwayType) {
    supportedOSMLinkSegmentTypes.add(osmHighwayType);
    unsupportedOSMLinkSegmentTypes.remove(osmHighwayType);
  }  
  
  /**
   * Choose to overwrite the given highway type defaults with the given values
   * 
   * @param osmHighwayType the type to set these values for
   * @param capacityPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPerLane new value pcu/km/lane
   * @param modeProperties new values per mode
   */
  public void overwriteOSMHighwayTypeDefaults(
      String osmHighwayType, 
      double capacityPerLanePerHour, 
      double maxDensityPerLane) {
    supportedOSMLinkSegmentTypes.add(osmHighwayType);
    overwriteByOSMHighwayType.put(osmHighwayType, new Pair<Double,Double>(capacityPerLanePerHour,maxDensityPerLane));    
  }    
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmHighwayType
   * @return true when new defaults are provided, false otherwise
   */
  public boolean isOSMHighwayTypeDefaultOverwritten(String osmHighwayType) {
    return overwriteByOSMHighwayType.containsKey(osmHighwayType);
  }
  
  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmHighwayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOSMHighwayTypeOverwrite(String osmHighwayType) {
    return overwriteByOSMHighwayType.get(osmHighwayType);
  }  
  
  /**
   * Verify if we are parsing the line geometry of Osm ways into link segments
   * @return true when parsing otherwise false
   */
  public boolean isParseOsmWayGeometry() {
    return parseOsmWayGeometry;
  }

  /**
   * indicate whether or not to parse the geometry of osm ways
   * 
   * @param parseOsmWayGeometry when set to true it will be parsed inot link segments
   */
  public void setParseOsmWayGeometry(boolean parseOsmWayGeometry) {
    this.parseOsmWayGeometry = parseOsmWayGeometry;
  }  

}

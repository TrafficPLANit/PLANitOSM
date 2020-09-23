package org.planit.osm.physical.network.macroscopic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitGeoUtils;
import org.planit.osm.defaults.OsmLaneDefaults;
import org.planit.osm.defaults.OsmModeAccessDefaults;
import org.planit.osm.defaults.OsmModeAccessDefaultsByCountry;
import org.planit.osm.defaults.OsmSpeedLimitDefaults;
import org.planit.osm.defaults.OsmSpeedLimitDefaultsByCountry;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRailModeTags;
import org.planit.osm.util.OsmRoadModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.Modes;
import org.planit.utils.mode.PredefinedModeType;

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
  
  /** the default speed limits used in case no explicit information is available on the osmway's tags */
  protected final OsmSpeedLimitDefaults speedLimitConfiguration;
  
  /** the default mode access configuration used in case no explicit access information is available on the osmway's tags */
  protected final OsmModeAccessDefaults modeAccessConfiguration;  
  
  /** the default number of lanes used in case no explicit information is available on the osmway's tags */
  protected final OsmLaneDefaults laneConfiguration = new OsmLaneDefaults();
  
  /** mapping from each supported osm road mode to a PLANit mode */
  protected final Map<String, Mode> osmRoadMode2PlanitModeMap = new HashMap<String, Mode>();
  
  /** mapping from each supported osm rail mode to a PLANit mode */
  protected final Map<String, Mode> osmRailMode2PlanitModeMap = new HashMap<String, Mode>();  
   
  /**
   * track overwrite values for OSM highway types where we want different defaults for capacity and max density
   */
  protected Map<String, Pair<Double,Double>> overwriteByOSMHighwayType = new HashMap<String, Pair<Double,Double>>();    
  
  /* SETTINGS */
  
  /** the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS;  
  
  /**
   * the OSM highway types that are marked as unsupported OSM types, i.e., will be ignored when parsing
   */
  protected final Set<String> unsupportedOSMLinkSegmentTypes = new HashSet<String>();
            
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
  
  /**  when speed limit information is missing, use predefined speed limits for highway types mapped to urban area speed limits (or non-urban), default is true */
  protected boolean speedLimitDefaultsBasedOnUrbanArea = true;  
  
  /**
   * conduct general initialisation for any instance of this class
   * 
   * @param planitModes to populate based on (default) mapping
   */
  protected void initialise(Modes planitModes) {
    try {
      /* the highway types that will be parsed by default, i.e., supported. */
      initialiseDefaultSupportedOSMHighwayTypes();
      /* the highway types that will not be parsed by default, i.e., unsupported. */
      initialiseDefaultUnsupportedOSMHighwayTypes();
      /* the default mapping from OSM modes to PLANit modes */
      initialiseDefaultMappingFromOsmModes2PlanitModes(planitModes);
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default supported and/or unsupported OSM link segment types for this network");
    }     
  }
  
  /**
   * each OSM road mode is mapped to a PLANit mode by default so that the memory model's modes
   * are user configurable yet linked to the original format. Note that when the reader is used
   * i.c.w. a network writer to convert one network to the other. It is paramount that the PLANit modes
   * that are mapped here are also mapped by the writer to the output format to ensure a correct I/O mapping of modes
   * 
   * The default mapping is provided below. It is important to realise that modes that are marked as N/A have no predefined
   * equivalent in PLANit, as a result they are ignored. One could add them to a known predefined mode, e.g., MOPED -> MotorBikeMode, 
   * however, this would mean that a restriction on for example mopeds would also be imposed on motor bikes and this is something you 
   * likely do not want. If you must include mopeds, then add a custom mapping to a custom mode afterwards, so it is modelled 
   * separately. Again, when also persisting using a network converter, make sure the custom mode is also used in the writer to 
   * include the mode in the network output.
   * 
   * <ul>
   * <li>FOOT         -> PedestrianMode </li>
   * <li>DOG          -> N/A            </li>
   * <li>HORSE        -> N/A            </li>
   * <li>BICYCLE      -> BicycleMode    </li>
   * <li>CARRIAGE     -> N/A            </li>
   * <li>TRAILER      -> N/A            </li>
   * <li>CARAVAN      -> N/A            </li>
   * <li>MOTOR_CYCLE  -> MotorBike      </li>
   * <li>MOPED        -> N/A            </li>
   * <li>MOFA         -> N/A            </li>
   * <li>MOTOR_CAR    -> CarMode        </li>
   * <li>MOTOR_HOME   -> N/A            </li>
   * <li>TOURIST_BUS  -> N/A            </li>
   * <li>COACH        -> N/A            </li>
   * <li>AGRICULTURAL -> N/A            </li>
   * <li>GOLF_CART    -> N/A            </li>
   * <li>ATV          -> N/A            </li>
   * <li>GOODS        -> GoodsMode      </li>
   * <li>HEAVY_GOODS  -> HeavyGoodsMode </li>
   * <li>HEAVY_GOODS_ARTICULATED  -> LargeHeavyGoodsMode </li>
   * <li>BUS          -> BusMode </li>
   * <li>TAXI         -> N/A </li>
   * <li>SHARE_TAXI   -> N/A </li>
   * <li>MINI_BUS     -> N/A </li>
   * </ul>
   * 
   * @param planitModes to populate based on (default) mapping
   * @throws PlanItException thrown if error
   */  
  protected void initialiseDefaultMappingFromOsmRoadModes2PlanitModes(Modes planitModes) throws PlanItException {
    /* initialise road modes on planit side that we are about to map */
    {
      planitModes.registerNew(PredefinedModeType.PEDESTRIAN);
      planitModes.registerNew(PredefinedModeType.BICYCLE);
      planitModes.registerNew(PredefinedModeType.MOTOR_BIKE);
      planitModes.registerNew(PredefinedModeType.CAR);
      planitModes.registerNew(PredefinedModeType.GOODS_VEHICLE);
      planitModes.registerNew(PredefinedModeType.HEAVY_GOODS_VEHICLE);
      planitModes.registerNew(PredefinedModeType.LARGE_HEAVY_GOODS_VEHICLE);
      planitModes.registerNew(PredefinedModeType.BUS);
    }
    
    /* add default mapping */
    {
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.FOOT, planitModes.getPredefinedMode(PredefinedModeType.PEDESTRIAN));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.BICYCLE, planitModes.getPredefinedMode(PredefinedModeType.BICYCLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.MOTOR_CYCLE, planitModes.getPredefinedMode(PredefinedModeType.MOTOR_BIKE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.MOTOR_CAR, planitModes.getPredefinedMode(PredefinedModeType.CAR));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.GOODS, planitModes.getPredefinedMode(PredefinedModeType.GOODS_VEHICLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.HEAVY_GOODS, planitModes.getPredefinedMode(PredefinedModeType.HEAVY_GOODS_VEHICLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.HEAVY_GOODS_ARTICULATED, planitModes.getPredefinedMode(PredefinedModeType.LARGE_HEAVY_GOODS_VEHICLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.BUS, planitModes.getPredefinedMode(PredefinedModeType.BUS));
    }
  }
  
  /**
   * each OSM rail mode is mapped (or not) to a PLANit mode by default so that the memory model's modes
   * are user configurable yet linked to the original format. Note that when the reader is used
   * i.c.w. a network writer to convert one network to the other. It is paramount that the PLANit modes
   * that are mapped here are also mapped by the writer to the output format to ensure a correct I/O mapping of modes
   * 
   * The default mapping is provided below. In contrast to road modes, rail modes do not have specific restrictions. Hence, we can
   * map more exotic OSM rail modes to more common PLANit rail modes, without imposing its restrictions on this common mode.  
   * 
   * <ul>
   * <li>FUNICULAR      -> TramMode       </li>
   * <li>LIGHT_RAIL     -> LightRailMode  </li>
   * <li>MONO_RAIL      -> TramMode       </li>
   * <li>NARROW_GAUGE   -> TrainMode      </li>
   * <li>RAIL           -> TrainMode      </li>
   * <li>SUBWAY         -> SubWayMode     </li>
   * <li>TRAM           -> TramMode       </li>
   * </ul>
   * 
   * 
   * @param planitModes to populate based on (default) mapping
   * @throws PlanItException thrown if error
   */   
  protected void initialiseDefaultMappingFromOsmRailModes2PlanitModes(Modes planitModes) throws PlanItException {
    /* initialise rail modes on planit side that we are about to map */
    {
      planitModes.registerNew(PredefinedModeType.TRAM);
      planitModes.registerNew(PredefinedModeType.LIGHTRAIL);
      planitModes.registerNew(PredefinedModeType.TRAIN);
      planitModes.registerNew(PredefinedModeType.SUBWAY);
    }
    
    /* add default mapping */
    {
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.FUNICULAR, planitModes.getPredefinedMode(PredefinedModeType.TRAM));
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.LIGHT_RAIL, planitModes.getPredefinedMode(PredefinedModeType.LIGHTRAIL));
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.MONO_RAIL, planitModes.getPredefinedMode(PredefinedModeType.TRAM));
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.NARROW_GAUGE, planitModes.getPredefinedMode(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.RAIL, planitModes.getPredefinedMode(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.SUBWAY, planitModes.getPredefinedMode(PredefinedModeType.SUBWAY));
      osmRailMode2PlanitModeMap.put(OsmRailModeTags.TRAM, planitModes.getPredefinedMode(PredefinedModeType.TRAM));
    }           
  }  
  
  /**
   * Map both road and rail modes from OSM modes to PLANit modes
   * 
   * @param planitModes to populate based on (default) mapping
   * @throws PlanItException thrown if error
   */
  protected void initialiseDefaultMappingFromOsmModes2PlanitModes(Modes planitModes) throws PlanItException {
    initialiseDefaultMappingFromOsmRoadModes2PlanitModes(planitModes);
    initialiseDefaultMappingFromOsmRailModes2PlanitModes(planitModes);
  }


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
   * Constructor with country to base (i) default speed limits and (ii) mode access on, 
   * for various osm highway types in case maximum speed limit information is missing
   * 
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   * @param planitModes to populate based on (default) mapping
   */
  public PlanitOsmSettings(String countryName, Modes planitModes) {
    this.speedLimitConfiguration = OsmSpeedLimitDefaultsByCountry.create(countryName);
    this.modeAccessConfiguration = OsmModeAccessDefaultsByCountry.create(countryName);
    initialise(planitModes);    
  }    

  /**
   * Default constructor. Here no specific locale is provided, meaning that all defaults will use global settings. This is especially relevant for
   * speed limits and mdoe access restrictions (unless manually adjusted by the user)
   * 
   * @param planitModes to populate based on (default) mapping
   * 
   */  
  public PlanitOsmSettings(Modes planitModes) {
    this.speedLimitConfiguration = OsmSpeedLimitDefaultsByCountry.create();
    this.modeAccessConfiguration = OsmModeAccessDefaultsByCountry.create();
    initialise(planitModes);
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
  
  /** collect state of flag indicating to use urban or non-urban default speed limits when speed limit information is missing
   * 
   * @return flag value
   */
  public boolean isSpeedLimitDefaultsBasedOnUrbanArea() {
    return speedLimitDefaultsBasedOnUrbanArea;
  }

  /** set state of flag indicating to use urban or non-urban default speed limits when speed limit information is missing
   * 
   * @param speedLimitDefaultsBasedOnUrbanArea flag value
   */  
  public void setSpeedLimitDefaultsBasedOnUrbanArea(boolean speedLimitDefaultsBasedOnUrbanArea) {
    this.speedLimitDefaultsBasedOnUrbanArea = speedLimitDefaultsBasedOnUrbanArea;
  }  
  
  /** collect the current configuration setup for applying speed limits in case the maxspeed tag is not available on the parsed osmway
   * @return speed limit configuration
   */
  public OsmSpeedLimitDefaults getSpeedLimitConfiguration() {
    return this.speedLimitConfiguration;
  }
  
  /** collect the current configuration setup for applying mode access restrictions/allowances in case no specific access restrictions 
   * are specified on the OSM way
   * 
   * @return mode access configuration
   */
  public OsmModeAccessDefaults getModeAccessConfiguration() {
    return this.modeAccessConfiguration;
  }  
  
  /** collect the current configuration setup for applying number of lanes in case the lanes tag is not available on the parsed osmway
   * @return lane configuration containing all defaults for various osm highway types
   */
  public OsmLaneDefaults getLaneConfiguration() {
    return this.laneConfiguration;
  }  

  /** Collect the speed limit for a given highway tag value, e.g. highway=type, based on the defaults provided (typically set by country)
   * 
   * @param type highway type to collect default speed limit for
   * @return speedLimit in km/h outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea
   * @throws PlanItException thrown if error
   */
  public double getDefaultSpeedLimitByHighwayType(String type) throws PlanItException {
    return speedLimitConfiguration.getSpeedLimit(type, !isSpeedLimitDefaultsBasedOnUrbanArea());  
  }

  /** Collect the number of lanes for a given highway tag value for either direction (not total), e.g. highway=type, based on the defaults provided
   * 
   * @param type highway type to collect default lanes for
   * @return number of default lanes
   * @throws PlanItException thrown if error
   */
  public Integer getDefaultDirectionalLanesByHighwayType(String type) {
    return laneConfiguration.getDefaultDirectionalLanesByHighwayType(type);
  }
  
  /** add/overwrite a mapping from OSM road mode to PLANit mode. This means that the osmMode will be added to the PLANit network, but also any of its restrictions
   *  will be imposed on the planit mode that is provided. 
   * 
   * @param osmRoadMode to set
   * @param planitMode to map it to
   */
  public void setOsmRoadMode2PlanitModeMapping(String osmRoadMode, Mode planitMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("osm road mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    if(planitMode == null) {
      LOGGER.warning(String.format("planit mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode %s, ignored", osmRoadMode));
      return;
    }
    osmRoadMode2PlanitModeMap.put(osmRoadMode, planitMode);
  }  
  
  /** remove a mapping from OSM road mode to PLANit mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added, either manually or through the default mapping
   * 
   * @param osmRoadMode to remove
   */
  public void removeOsmRoadModesPlanitModeMapping(String osmRoadMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("osm road mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    osmRoadMode2PlanitModeMap.remove(osmRoadMode);
  }
  
  /** add/overwrite a mapping from OSM rail mode to PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmRoadMode to set
   * @param planitMode to map it to
   */
  public void setOsmRailMode2PlanitModeMapping(String osmRailMode, Mode planitMode) {
    if(!OsmRailModeTags.isRailModeTag(osmRailMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    if(planitMode == null) {
      LOGGER.warning(String.format("planit mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode %s, ignored", osmRailMode));
      return;
    }
    osmRailMode2PlanitModeMap.put(osmRailMode, planitMode);
  }   
  
  /** remove a mapping from OSM road mode to PLANit mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added, either manually or through the default mapping
   * 
   * @param osmRoadMode to remove
   */
  public void removeOsmRailMode2PlanitModeMapping(String osmRailMode) {
    if(!OsmRailModeTags.isRailModeTag(osmRailMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    osmRailMode2PlanitModeMap.remove(osmRailMode);
  }
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getMappedPlanitMode(final String osmMode) {
    if(OsmRoadModeTags.isRoadModeTag(osmMode)) {
      return this.osmRoadMode2PlanitModeMap.get(osmMode);
    }else if(OsmRailModeTags.isRailModeTag(osmMode)) {
      return this.osmRailMode2PlanitModeMap.get(osmMode);
    }else {
      LOGGER.warning(String.format("unknown osmMode tag %s found when collecting mapped PLANit modes, ignored",osmMode));
    }
    return null;
  }  

  /** convenience method that provides an overview of all PLANit modes that are currently mapped by any of the passed in OsmModes
   * 
   * @param osmModes to verify
   * @return mapped PLANit modes
   */
  public Collection<Mode> collectMappedPlanitModes(Collection<String> osmModes) {
    HashSet<Mode> mappedPlaniotModes = new HashSet<Mode>();
    for (String osmMode : osmModes) {
      Mode mappedMode = getMappedPlanitMode(osmMode);
      if(mappedMode != null) {
        mappedPlaniotModes.add(mappedMode);
      }
    }
    return mappedPlaniotModes;
  }
 

}

package org.planit.osm.physical.network.macroscopic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitJtsUtils;
import org.planit.geo.PlanitOpenGisUtils;
import org.planit.osm.defaults.OsmHighwayTypeConfiguration;
import org.planit.osm.defaults.OsmLaneDefaults;
import org.planit.osm.defaults.OsmModeAccessDefaults;
import org.planit.osm.defaults.OsmModeAccessDefaultsByCountry;
import org.planit.osm.defaults.OsmRailwayTypeConfiguration;
import org.planit.osm.defaults.OsmSpeedLimitDefaults;
import org.planit.osm.defaults.OsmSpeedLimitDefaultsByCountry;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRailWayTags;
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
  
  /** the country we are importing for (if any) */
  protected final String countryName;
  
  /**
   * Configuration options regarding the activation/deactivation of specific OSM highway types in the parser
   */
  protected final OsmHighwayTypeConfiguration highwayTypeConfiguration = new OsmHighwayTypeConfiguration();
  
  /**
   * Configuration options regarding the activation/deactivation of specific OSM railway types in the parser
   */
  protected final OsmRailwayTypeConfiguration railwayTypeConfiguration = new OsmRailwayTypeConfiguration();  
       
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
  protected CoordinateReferenceSystem sourceCRS = PlanitOpenGisUtils.DEFAULT_GEOGRAPHIC_CRS;  
              
  /**
   * When the user has activated a highway type for which the reader has no support, this alternative will be used, default is
   * set to PlanitOSMTags.TERTIARY. Note in case this is also not available on the reader, the type will be ignored altogether
   */
  protected String defaultOsmHighwayTypeWhenUnsupported = DEFAULT_HIGHWAY_TYPE_WHEN_UNSUPPORTED;  
    
  /**
   * option to track the geometry of an OSM way, i.e., extract the line string for link segments from the nodes
   * (default is false). When set to true parsing will be somewhat slower 
   */
  protected boolean parseOsmWayGeometry = DEFAULT_PARSE_OSMWAY_GEOMETRY;
  
  /**  when speed limit information is missing, use predefined speed limits for highway types mapped to urban area speed limits (or non-urban), default is true */
  protected boolean speedLimitDefaultsBasedOnUrbanArea = DEFAULT_SPEEDLIMIT_BASED_ON_URBAN_AREA;  
  
  /** flag indicating if dangling subnetworks should be removed after parsing the network
   * OSM network often have small roads that appear to be connected to larger roads, but in fact are not. 
   * All subnetworks that are not part of the largest subnetwork that is parsed will be removed by default. 
   * */
  protected boolean removeDanglingSubNetworks = DEFAULT_REMOVE_DANGLING_SUBNETWORK;
  
  /**
   * When dangling subnetworks are marked for removal, this threshold determines the minimum subnetwork size for it NOT to be removed.
   * In other words, all subnetworks below this number will be removed
   */
  protected int discardSubNetworkBelowSize = DEFAULT_MINIMUM_SUBNETWORK_SIZE;
  
  /**
   * When dangling subnetworks are marked for removal, this threshold determines the maximum subnetwork size for it NOT to be removed.
   * In other words, all subnetworks above this number will be removed, including the largest one if it does not match the value
   */  
  protected int discardSubNetworkAbovesize = Integer.MAX_VALUE;
  
  /**
   * indicate whether or not to keep the largest subnetwork when {@code removeDanglingSubNetworks} is set to true even when it does
   * not adhere to the criteria of {@code discardSubNetworkBelowSize} and/or {@code discardSubNetworkAbovesize} 
   */
  protected boolean alwaysKeepLargestsubNetwork = DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK;
    
  /**
   * conduct general initialisation for any instance of this class
   * 
   * @param planitModes to populate based on (default) mapping
   */
  protected void initialise(Modes planitModes) {
    try {  
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
   * <li>PRESERVED      -> TrainMode      </li>
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
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.FUNICULAR, planitModes.getPredefinedMode(PredefinedModeType.TRAM));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.LIGHT_RAIL, planitModes.getPredefinedMode(PredefinedModeType.LIGHTRAIL));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.MONO_RAIL, planitModes.getPredefinedMode(PredefinedModeType.TRAM));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.NARROW_GAUGE, planitModes.getPredefinedMode(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.PRESERVED, planitModes.getPredefinedMode(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.RAIL, planitModes.getPredefinedMode(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.SUBWAY, planitModes.getPredefinedMode(PredefinedModeType.SUBWAY));
      osmRailMode2PlanitModeMap.put(OsmRailWayTags.TRAM, planitModes.getPredefinedMode(PredefinedModeType.TRAM));
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
   * explicitly exclude all osmWay type:value in case none of the passed in osmModes is marked as mapped
   * 
   * @param osmWayKey to check
   * @param osmWayValue to check
   * @param osmModes of which at least one should be active on the key:value pair
   */
  protected void excludeOsmWayTypesWithoutModes(String osmWayKey, String osmWayValue, Collection<String> osmModes) {
    boolean hasMappedMode = false;
    for(String osmMode : osmModes) {
      if(hasMappedPlanitMode(osmMode)) {
        hasMappedMode = true;
        break;
      }
    }
    if(!hasMappedMode) {
      deactivateOsmWayType(osmWayKey, osmWayValue);
    } 
  }   
  
  /**
   * Log all de-activated OSM way types
   */  
  protected void logUnsupportedOsmWayTypes() {
    highwayTypeConfiguration.logUnsupportedTypes();
    railwayTypeConfiguration.logUnsupportedTypes();        
  }  
  
  /** the default crs is set to {@code  PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS} */
  public static CoordinateReferenceSystem DEFAULT_SOURCE_CRS = PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS;  
              
  /**
   * Default is OSM highway type when the type is not supported is set to PlanitOSMTags.TERTIARY.
   */
  public static String DEFAULT_HIGHWAY_TYPE_WHEN_UNSUPPORTED = OsmHighwayTags.TERTIARY;  
    
  /** default value for parsing OSM way geometry: false */
  public static boolean DEFAULT_PARSE_OSMWAY_GEOMETRY = false;
  
  /**  default value whether or not speed limits are based on urban area defaults: true */
  public static boolean DEFAULT_SPEEDLIMIT_BASED_ON_URBAN_AREA = true;  
  
  /** Default whether or not we are removing dangling subnetworks after parsing: true */
  public static boolean DEFAULT_REMOVE_DANGLING_SUBNETWORK = true;
  
  /** Default minimum size of subnetwork for it not to be removed when dangling subnetworks are removed, size indicates number of vertices: 20 */
  public static int DEFAULT_MINIMUM_SUBNETWORK_SIZE= 20;  
  
  /** by default we always keep the largest subnetwork */
  public static boolean DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK = true;
    
  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on, 
   * for various osm highway types in case maximum speed limit information is missing
   * 
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   * @param planitModes to populate based on (default) mapping
   */
  public PlanitOsmSettings(String countryName, Modes planitModes) {
    this.countryName = countryName;
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
    this( "", planitModes);
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
   * When the parsed way has a type that is not supported but also not explicitly excluded, this alternative will be used
   * @return chosen default
   * 
   **/
  public final String getDefaultOsmHighwayTypeWhenDeactivated() {
    return defaultOsmHighwayTypeWhenUnsupported;
  }

  /**
   * set the default to be used when we encounter an unsupported type.
   * 
   * @param defaultOsmHighwayValueWhenUnsupported the default to use, should be a type that is supported.
   */
  public void setApplyDefaultWhenOsmHighwayTypeDeactivated(String defaultOsmHighwayValueWhenUnsupported) {
    this.defaultOsmHighwayTypeWhenUnsupported = defaultOsmHighwayValueWhenUnsupported;
  } 
  
  /**
   * 
   * check if a default type is set when the activate type is not supported
   * @return true when available false otherwise
   **/
  public final boolean isApplyDefaultWhenOsmHighwayTypeDeactivated() {
    return defaultOsmHighwayTypeWhenUnsupported==null;
  }  
  
  /**
   * remove default type in case activate type is not supported by the reader
   */
  public final void removeOsmHighwayTypeWhenDeactivated() {
    defaultOsmHighwayTypeWhenUnsupported=null;
  }    
  
  /**
   * Verify if the passed in OSM way type is explicitly deactivated. Deactivated types will be ignored
   * when processing ways.
   * 
   * @param osmWayKey way key, e.g. highway, railway, etc.
   * @param osmWayValue, e.g. primary, road, rail
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmWayTypeDeactivated(String osmWayKey, String osmWayValue) {
    switch (osmWayKey) {
    case OsmHighwayTags.HIGHWAY:      
      return highwayTypeConfiguration.isUnsupported(osmWayValue);
    case OsmRailWayTags.RAILWAY:
      return railwayTypeConfiguration.isUnsupported(osmWayValue);
    default:
      return true;
    }    
  }
  
  /**
   * Verify if the passed in OSM highway type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayKey way key, e.g. highway, railway, etc.
   * @param osmWayValue, e.g. primary, road, rail
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmWayTypeActivated(String osmWayKey, String osmWayValue) {
    switch (osmWayKey) {
    case OsmHighwayTags.HIGHWAY:
      return highwayTypeConfiguration.isSupported(osmWayValue);
    case OsmRailWayTags.RAILWAY:
      return railwayTypeConfiguration.isSupported(osmWayValue);
    default:
      return false;
    }            
  }
  
  
  /**
   * Choose to not parse the given combination of way and subtype, e.g. highway=road, railway=rail.
   * 
   * @param osmWayKey to use
   * @param osmWayValue to use
   */
  public void deactivateOsmWayType(String osmWayKey, String osmWayValue) {
    switch (osmWayKey) {
    case OsmHighwayTags.HIGHWAY:
      highwayTypeConfiguration.deactivate(osmWayValue);
      break;
    case OsmRailWayTags.RAILWAY:
      railwayTypeConfiguration.deactivate(osmWayValue);      
      break;
    default:
      LOGGER.fine(String.format("excluding osm way irrelevant, provided combination %s:%s was already unsupported or unknown",osmWayKey, osmWayValue));
    }      
  }
  
  /**
   * explicitly exclude all osmWay types that are included but have no more activated modes due to deactiavtoin of their default assigned modes.
   * Doing so avoids the reader to log warnings that supported way types cannot be injected in the network because they
   * have no viable modes attached
   */
  public void excludeOsmWayTypesWithoutActivatedModes() {
    Set<String> originallySupportedTypes = railwayTypeConfiguration.setOfActivatedTypes();
    for(String supportedRailWayType : originallySupportedTypes) {
      Collection<String> allowedOsmModes = getModeAccessConfiguration().collectAllowedModes(OsmRailWayTags.RAILWAY, supportedRailWayType);
      excludeOsmWayTypesWithoutModes(OsmRailWayTags.RAILWAY, supportedRailWayType, allowedOsmModes);      
    }
    originallySupportedTypes = highwayTypeConfiguration.setOfActivatedTypes();
    for(String supportedHighWayType : originallySupportedTypes) {
      Collection<String> allowedOsmModes = getModeAccessConfiguration().collectAllowedModes(OsmHighwayTags.HIGHWAY, supportedHighWayType);
      excludeOsmWayTypesWithoutModes(OsmHighwayTags.HIGHWAY, supportedHighWayType, allowedOsmModes);      
    }
  }      
  
  /**
   * Choose to add given way type to parsed types on top of the defaults, e.g. highway=road, railway=rail.
   * 
   * @param osmWayKey to use
   * @param osmWayValue to use
   */
  public void activateOsmWayType(String osmWayKey, String osmWayValue) {
    switch (osmWayKey) {
    case OsmHighwayTags.HIGHWAY:
      highwayTypeConfiguration.activate(osmWayValue);
      break;
    case OsmRailWayTags.RAILWAY:
      railwayTypeConfiguration.activate(osmWayValue);
      break;
    default:
      LOGGER.fine(String.format("excluding osm way irrelevant, provided combination %s:%s was already unsupported or unknown",osmWayKey, osmWayValue));
    }          
  }  
  
  /**
   * activate all known OSM highway types 
   */
  public void activateAllOsmHighwayTypes() {
    highwayTypeConfiguration.setOfDeactivatedTypes().forEach( unsupportedType -> activateOsmWayType(OsmHighwayTags.HIGHWAY, unsupportedType));    
  }
  
  /**
   * activate all known OSM railway types 
   */
  public void activateAllOsmRailwayTypes() {
    railwayTypeConfiguration.setOfDeactivatedTypes().forEach( unsupportedType -> activateOsmWayType(OsmRailWayTags.RAILWAY, unsupportedType));    
  }  
    
  /**
   * Choose to overwrite the given highway type defaults with the given values
   * 
   * @param osmHighwayType the type to set these values for
   * @param capacityPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPerLane new value pcu/km/lane
   * @param modeProperties new values per mode
   */
  public void overwriteOsmHighwayTypeDefaults(String osmHighwayType, double capacityPerLanePerHour, double maxDensityPerLane) {
    highwayTypeConfiguration.activate(osmHighwayType);
    overwriteByOSMHighwayType.put(osmHighwayType, new Pair<Double,Double>(capacityPerLanePerHour,maxDensityPerLane));
    LOGGER.info(String.format("overwriting defaults for osm road type highway:%s to capacity: %.2f (pcu/h/lane), max density %.2f (pcu/km)",osmHighwayType, capacityPerLanePerHour, maxDensityPerLane));
  }  
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmHighwayType
   * @return true when new defaults are provided, false otherwise
   */
  public boolean isOsmHighwayTypeDefaultOverwritten(String osmHighwayType) {
    return overwriteByOSMHighwayType.containsKey(osmHighwayType);
  }
  
  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmHighwayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOsmHighwayTypeOverwrite(String osmHighwayType) {
    return overwriteByOSMHighwayType.get(osmHighwayType);
  }  
   
  /**
   * indicate whether to remove dangling subnetworks or not
   * @param removeDanglingSubnetworks yes or no
   */
  public void setRemoveDanglingSubnetworks(boolean removeDanglingSubnetworks) {
    this.removeDanglingSubNetworks = removeDanglingSubnetworks;
  }
  
  /** verify if dangling subnetworks are removed from the final network
   * @return
   */
  public boolean isRemoveDanglingSubnetworks() {
    return this.removeDanglingSubNetworks;
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

  /** Collect the speed limit for a given highway or railway tag value, e.g. railway=typeValue, highway=typeValue, based on the defaults provided (typically set by country)
   * 
   * @param osmWaykey way key to collect default speed limit for the wayValue for (highway, railway)
   * @param osmWayValue way value type to collect default speed limit for
   * @return speedLimit in km/h (for highway types, the outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea is collected)
   * @throws PlanItException thrown if error
   */
  public double getDefaultSpeedLimitByOsmWayType(String osmWayKey, String osmWayValue) throws PlanItException {
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      return speedLimitConfiguration.getHighwaySpeedLimit(osmWayValue, !isSpeedLimitDefaultsBasedOnUrbanArea());
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey)){
      return speedLimitConfiguration.getRailwaySpeedLimit(osmWayValue);
    }else {
      throw new PlanItException(String.format("unknown osmWayKey %s provided", osmWayKey));
    }
  }
  
  /** Collect the default speed limit for a given highway or railway tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h (for highway types, the outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea is collected)
   * @throws PlanItException thrown if error
   */  
  public Double getDefaultSpeedLimitByOsmWayType(Map<String, String> tags) throws PlanItException {
    String osmWayKey = null;
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmWayKey = OsmHighwayTags.HIGHWAY;
    }else if(tags.containsKey(OsmRailWayTags.RAILWAY)){
      osmWayKey = OsmRailWayTags.RAILWAY;      
    }else {
      throw new PlanItException("no osmWay key that is currently supported contained in provided osmTags when collecting default speed limit by OsmWayType");
    }
    return getDefaultSpeedLimitByOsmWayType(osmWayKey,tags.get(osmWayKey));
  }  

  /** Collect the number of lanes/tracks for a given OSM way key/value for either direction (not total), 
   * e.g. highway=value, railway=value based on the defaults provided
   * 
   * @param type highway type to collect default lanes for
   * @return number of default lanes
   * @throws PlanItException thrown if error
   */
  public Integer getDefaultDirectionalLanesByWayType(String osmWayKey, String osmWayValue) {
    return this.laneConfiguration.getDefaultDirectionalLanesByWayType(osmWayKey, osmWayValue);    
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
  public void removeOsmRoadModePlanitModeMapping(String osmRoadMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("osm road mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    LOGGER.info(String.format("osm road mode %s is deactivated", osmRoadMode));
    osmRoadMode2PlanitModeMap.remove(osmRoadMode);
  }
  
  /** add/overwrite a mapping from OSM rail mode to PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmRoadMode to set
   * @param planitMode to map it to
   */
  public void setOsmRailMode2PlanitModeMapping(String osmRailMode, Mode planitMode) {
    if(!OsmRailWayTags.isRailwayModeValueTag(osmRailMode)) {
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
    if(!OsmRailWayTags.isRailwayModeValueTag(osmRailMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    LOGGER.info(String.format("osm rail mode %s is deactivated", osmRailMode));
    osmRailMode2PlanitModeMap.remove(osmRailMode);
  }
  
  /** remove all rail modes from mapping
   * 
   */
  public void removeAllRailModes() {
    Collection<String> allRailModes = OsmRailWayTags.getSupportedRailModeTags();
    for(String osmRailMode : allRailModes) {
      removeOsmRailMode2PlanitModeMapping(osmRailMode);
    }
  }  
  
  /** remove all road modes from mapping except for the passed in ones
   * 
   * @param remainingOsmRoadModes to explicitly keep if present
   */
  public void removeAllRoadModesExcept(String... remainingOsmRoadModes) {
    Collection<String> allRoadModes = OsmRoadModeTags.getSupportedRoadModeTags();
    Set<String> remainingRoadModes = remainingOsmRoadModes==null ? new HashSet<String>() : Set.of(remainingOsmRoadModes);
    for(String osmRoadMode: allRoadModes) {
      /* remove when not retained */
      if(!remainingRoadModes.contains(osmRoadMode)) {
        removeOsmRoadModePlanitModeMapping(osmRoadMode);
      }
    }
  }   
  
  /**
   * remove all road modes from the network when parsing
   */
  public void removeAllRoadModes() {
    removeAllRoadModesExcept((String[])null);
  }  
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getMappedPlanitMode(final String osmMode) {
    if(OsmRoadModeTags.isRoadModeTag(osmMode)) {
      return this.osmRoadMode2PlanitModeMap.get(osmMode);
    }else if(OsmRailWayTags.isRailwayModeValueTag(osmMode)) {
      return this.osmRailMode2PlanitModeMap.get(osmMode);
    }else {
      LOGGER.warning(String.format("unknown osmMode tag %s found when collecting mapped PLANit modes, ignored",osmMode));
    }
    return null;
  }  
  
  /** Verify if the passed in osmMode is mapped, i.e., if it is actively included when reading the network
   * @param osmMode to verify
   * @return true if mapped, false otherwise
   */
  public boolean hasMappedPlanitMode(final String osmMode) {
    return getMappedPlanitMode(osmMode) != null;
  }

  /** convenience method that provides an overview of all PLANit modes that are currently mapped by any of the passed in OsmModes
   * 
   * @param osmModes to verify
   * @return mapped PLANit modes
   */
  public Collection<Mode> collectMappedPlanitModes(Collection<String> osmModes) {
    HashSet<Mode> mappedPlanitModes = new HashSet<Mode>();
    for (String osmMode : osmModes) {
      Mode mappedMode = getMappedPlanitMode(osmMode);
      if(mappedMode != null) {
        mappedPlanitModes.add(mappedMode);
      }
    }
    return mappedPlanitModes;
  }

  /** When country is set for settings this will return the chosen country
   * @return countryName
   */
  public final String getCountryName() {
    return this.countryName;
  }

  /** the minimum size an identified dangling network must have for it to NOT be removed when danlging networks are removed
   * 
   * @param discardBelow this number of vertices
   */
  public void setDiscardDanglingNetworksBelow(int discardBelow) {
    this.discardSubNetworkBelowSize = discardBelow;
  }
  
  /** allows you to set a maximum size for dangling subnetwork. Practically only useful for debugging purposes
   * 
   * @param discardAbove this number of vertices
   */
  public void setDiscardDanglingNetworksAbove(int discardAbove) {
    this.discardSubNetworkAbovesize = discardAbove;
  }  
  
  /** collect the size above which dangling networks are kept even if they are smaller than the largest connected network
   * @return danlging network size
   */
  public Integer getDiscardDanglingNetworkBelowSize() {
    return discardSubNetworkBelowSize;
  }  
  
  /** collect the size below which networks are removed 
   * @return dangling network size
   */
  public Integer getDiscardDanglingNetworkAboveSize() {
    return discardSubNetworkAbovesize;
  }    
  
  /** Verify if the largest subnetwork is always kept when we are removing dangling subnetworks
   * 
   * @return true when kept false otherwise
   */
  public boolean isAlwaysKeepLargestsubNetwork() {
    return alwaysKeepLargestsubNetwork;
  }

  /** indicate to keep the largest subnetwork always even when removing dangling subnetworks and the largest one
   * does not fit the set criteria
   * 
   * @param alwaysKeepLargestsubNetwork when true we always keep it, otherwise not
   */
  public void setAlwaysKeepLargestsubNetwork(boolean alwaysKeepLargestsubNetwork) {
    this.alwaysKeepLargestsubNetwork = alwaysKeepLargestsubNetwork;
  }  

  /**
   * deactivate all types for both rail and highway
   */
  public void deactivateAllOsmWayTypes() {
    highwayTypeConfiguration.deactivateAll();
    railwayTypeConfiguration.deactivateAll();
  }

  /** activate all passed in highway types
   * @param osmHighwayValueType
   */
  public void activateOsmHighwayWayTypes(String... osmHighwayValueType) {
    highwayTypeConfiguration.activate(osmHighwayValueType);
  }
 
}

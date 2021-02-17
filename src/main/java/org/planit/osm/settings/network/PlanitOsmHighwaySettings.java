package org.planit.osm.settings.network;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.osm.defaults.OsmHighwayTypeConfiguration;
import org.planit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.planit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.Modes;
import org.planit.utils.mode.PredefinedModeType;

/**
 * All highway specific settings
 * 
 * @author markr
 *
 */
public class PlanitOsmHighwaySettings {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmHighwaySettings.class.getCanonicalName());
  
  /**
   * Configuration options regarding the activation/deactivation of specific OSM highway types in the parser
   */
  protected final OsmHighwayTypeConfiguration highwayTypeConfiguration = new OsmHighwayTypeConfiguration();
  
  /** urban speed limit defaults */
  OsmSpeedLimitDefaultsCategory urbanSpeedLimitDefaults;
  
  /** non-urban speed limit defaults */
  OsmSpeedLimitDefaultsCategory nonUrbanSpeedLimitDefaults;
  
  /** mode access defaults for highways */
  OsmModeAccessDefaultsCategory osmModeAccessHighwayDefaults;
  
  /** mapping from each supported osm road mode to a PLANit mode */
  protected final Map<String, Mode> osmRoadMode2PlanitModeMap = new HashMap<String, Mode>();
  
  /**
   * track overwrite values for OSM highway types where we want different defaults for capacity and max density
   */
  protected final Map<String, Pair<Double,Double>> overwriteByOsmHighwayType = new HashMap<String, Pair<Double,Double>>();
  
  /**
   * When the user has activated a highway type for which the reader has no support, this alternative will be used, default is
   * set to PlanitOSMTags.TERTIARY. Note in case this is also not available on the reader, the type will be ignored altogether
   */
  protected String defaultOsmHighwayTypeWhenUnsupported = DEFAULT_HIGHWAY_TYPE_WHEN_UNSUPPORTED;  
  
  /**  when speed limit information is missing, use predefined speed limits for highway types mapped to urban area speed limits (or non-urban), default is true */
  protected boolean speedLimitDefaultsBasedOnUrbanArea = DEFAULT_SPEEDLIMIT_BASED_ON_URBAN_AREA;  
  
  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private boolean isParserActive = DEFAULT_HIGHWAYS_PARSER_ACTIVE;  
  
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
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.FOOT, planitModes.get(PredefinedModeType.PEDESTRIAN));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.BICYCLE, planitModes.get(PredefinedModeType.BICYCLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.MOTOR_CYCLE, planitModes.get(PredefinedModeType.MOTOR_BIKE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.MOTOR_CAR, planitModes.get(PredefinedModeType.CAR));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.GOODS, planitModes.get(PredefinedModeType.GOODS_VEHICLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.HEAVY_GOODS, planitModes.get(PredefinedModeType.HEAVY_GOODS_VEHICLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.HEAVY_GOODS_ARTICULATED, planitModes.get(PredefinedModeType.LARGE_HEAVY_GOODS_VEHICLE));
      osmRoadMode2PlanitModeMap.put(OsmRoadModeTags.BUS, planitModes.get(PredefinedModeType.BUS));
      
      /* ensure external id is set based on OSM name */
      osmRoadMode2PlanitModeMap.forEach( (osmMode, planitMode) -> PlanitOsmNetworkSettings.addToModeExternalId(planitMode, osmMode));
    }
  }  
  
  /** collect the current configuration setup for applying speed limits in case the maxspeed tag is not available on the parsed osm highway
   * 
   * @param urban or non-urban speed limit configuration to fetch, when tru urban defaults are fetched, when false, non-urban
   * @return speed limit configuration
   */
  protected OsmSpeedLimitDefaultsCategory getSpeedLimitConfiguration(boolean isUrban) {
    return isUrban ? this.urbanSpeedLimitDefaults : this.nonUrbanSpeedLimitDefaults;
  }  
  
  /** Constructor
   * @param osmModeAccessDefaultsCategory highway mode access configuration
   * @param urbanHighwayDefaults configuration
   * @param nonUrbanHighwayDefaults configuration
   */
  protected PlanitOsmHighwaySettings(OsmSpeedLimitDefaultsCategory urbanSpeedLimitDefaults,
      OsmSpeedLimitDefaultsCategory nonUrbanSpeedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessHighwayDefaults) {
    this.urbanSpeedLimitDefaults = urbanSpeedLimitDefaults;
    this.nonUrbanSpeedLimitDefaults = nonUrbanSpeedLimitDefaults;
    this.osmModeAccessHighwayDefaults = osmModeAccessHighwayDefaults;
  }  
  
  /**
   * Default is OSM highway type when the type is not supported is set to PlanitOSMTags.TERTIARY.
   */
  public static String DEFAULT_HIGHWAY_TYPE_WHEN_UNSUPPORTED = OsmHighwayTags.TERTIARY;  
  
  /**  default value whether or not speed limits are based on urban area defaults: true */
  public static boolean DEFAULT_SPEEDLIMIT_BASED_ON_URBAN_AREA = true;   
  
  /** by default the highway parser is activated */
  public static boolean DEFAULT_HIGHWAYS_PARSER_ACTIVE = true;  
  
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
   * Verify if the passed in OSM high way type is explicitly deactivated. Deactivated types will be ignored
   * when processing ways.
   * 
   * @param osmHighwayValue, e.g. primary, road
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmHighWayTypeDeactivated(final String osmHighwayValue) {
    return highwayTypeConfiguration.isDeactivated(osmHighwayValue);      
  }
  
  /** collect all activated highway types as a set (copy)
   * @param osmWayKey to collect activate types for
   * @return set of currently activated osm way types, modifications to this set have no effect on configuration
   */
  public final Set<String> getSetOfActivatedOsmHighwayTypes(){    
    return highwayTypeConfiguration.setOfActivatedTypes();    
  }
  
  /**
   * Verify if the passed in OSM highway type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmHighwayTypeActivated(String osmWayValue) {
    return highwayTypeConfiguration.isActivated(osmWayValue);            
  }
    
  /**
   * Choose to not parse the given highway type, e.g. highway=road.
   * 
   * @param osmWayValue to use
   */
  public void deactivateOsmHighwayType(String osmWayValue) {
    highwayTypeConfiguration.deactivate(osmWayValue);
  }  
  
  /**
   * Choose to add given highway type to parsed types on top of the defaults, e.g. highway=road
   * 
   * @param osmWayValue to use
   */
  public void activateOsmHighwayType(String osmWayValue) {
    highwayTypeConfiguration.activate(osmWayValue);
  }  
  
  /**
   * activate all known OSM highway types 
   */
  public void activateAllOsmHighwayTypes() {
    highwayTypeConfiguration.setOfDeactivatedTypes().forEach( unsupportedType -> activateOsmHighwayType(unsupportedType));    
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
    if(!highwayTypeConfiguration.isActivated(osmHighwayType)) {
      highwayTypeConfiguration.activate(osmHighwayType);
    }
    overwriteByOsmHighwayType.put(osmHighwayType, Pair.create(capacityPerLanePerHour,maxDensityPerLane));
    LOGGER.info(String.format("overwriting defaults for osm road type highway:%s to capacity: %.2f (pcu/h/lane), max density %.2f (pcu/km)",osmHighwayType, capacityPerLanePerHour, maxDensityPerLane));
  }  
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmHighwayType
   * @return true when new defaults are provided, false otherwise
   */
  public boolean isOsmHighwayTypeDefaultOverwritten(String osmHighwayType) {
    return overwriteByOsmHighwayType.containsKey(osmHighwayType);
  }
  
  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmHighwayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOsmHighwayTypeOverwrite(String osmHighwayType) {
    return overwriteByOsmHighwayType.get(osmHighwayType);
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
  
  /** Collect the speed limit for a given highway tag value, e.g. highway=typeValue, based on the defaults provided (typically set by country)
   * 
   * @param osmWayValue way value type to collect default speed limit for
   * @return speedLimit in km/h (for highway types, the outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea is collected)
   * @throws PlanItException thrown if error
   */
  public double getDefaultSpeedLimitByOsmHighwayType(String osmWayValue) throws PlanItException {
    return getSpeedLimitConfiguration(isSpeedLimitDefaultsBasedOnUrbanArea()).getSpeedLimit(osmWayValue); 
  }
  
  /** Collect the default speed limit for a given highway tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h (for highway types, the outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea is collected)
   * @throws PlanItException thrown if error
   */  
  public Double getDefaultSpeedLimitByOsmHighwayType(Map<String, String> tags) throws PlanItException {
    String osmWayKey = null;
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmWayKey = OsmHighwayTags.HIGHWAY;      
    }else {
      throw new PlanItException("no osm highway key contained in provided osmTags when collecting default speed limit by OsmHighwayType");
    }
    return getDefaultSpeedLimitByOsmHighwayType(tags.get(osmWayKey));
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
    PlanitOsmNetworkSettings.addToModeExternalId(planitMode, osmRoadMode);
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
    LOGGER.fine(String.format("osm road mode %s is deactivated", osmRoadMode));
    
    Mode planitMode = osmRoadMode2PlanitModeMap.remove(osmRoadMode);
    PlanitOsmNetworkSettings.removeFromModeExternalId(planitMode,osmRoadMode);
  }
  
  
  /** remove all road modes from mapping except for the passed in ones
   * 
   * @param remainingOsmRoadModes to explicitly keep if present
   */
  public void deactivateAllRoadModesExcept(final String... remainingOsmRoadModes) {
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
    deactivateAllRoadModesExcept((String[])null);
  }
  
  /** convenience method that collects the currently mapped PLANit road mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getMappedPlanitRoadMode(final String osmMode) {
    if(OsmRoadModeTags.isRoadModeTag(osmMode)) {
      return this.osmRoadMode2PlanitModeMap.get(osmMode);
    }
    return null;
  }
  
  /** verify if a mapped planit mode exists at this point
   * 
   * @param osmMode to verify
   * @return true when available false otherwise
   */
  public boolean hasMappedPlanitMode(String osmMode) {
    return getMappedPlanitRoadMode(osmMode)!=null;
  } 
  
  /**
   * Collect all Osm modes that are allowed for the given osmHighway type as configured by the user
   * 
   * @param osmHighwayValueType to use
   * @return allowed OsmModes
   */
  public Collection<String> collectAllowedOsmHighwayModes(String osmHighwayValueType) {
    Set<String> allowedModes = null; 
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmHighwayValueType)){
      /* collect all rail and road modes that are allowed, try all because the mode categories make it difficult to collect individual modes otherwise */
      Set<String> allowedRoadModesOnRoad =  OsmRoadModeTags.getSupportedRoadModeTags().stream().filter( roadModeTag -> osmModeAccessHighwayDefaults.isAllowed(osmHighwayValueType, roadModeTag)).collect(Collectors.toSet());
      Set<String> allowedRailModesOnRoad =  OsmRailModeTags.getSupportedRailModeTags().stream().filter( railModeTag -> osmModeAccessHighwayDefaults.isAllowed(osmHighwayValueType, railModeTag)).collect(Collectors.toSet());      
      allowedModes = new HashSet<String>();
      allowedModes.addAll(allowedRoadModesOnRoad);
      allowedModes.addAll(allowedRailModesOnRoad);
    }else {
      LOGGER.warning(String.format("unrecognised osm highway key value type highway=%s, no allowed modes can be identified", osmHighwayValueType));
    }
    return allowedModes;
  }  
  
  /** add an allowed osm mode to highwaytype
   * 
   * @param osmHighwayType to use
   * @param osmMode to allow
   */
  public void addAllowedHighwayModes(final String osmHighwayType, final String osmMode) {
    osmModeAccessHighwayDefaults.addAllowedModes(osmHighwayType, osmMode);
  }
  
  /** activate all passed in highway types
   * @param osmHighwayValueType
   */
  public void activateOsmHighwayWayTypes(String... osmHighwayValueType) {
    highwayTypeConfiguration.activate(osmHighwayValueType);
  }
  
  /**
   * Log all de-activated OSM highwayway types
   */  
  public void logUnsupportedOsmHighwayTypes() {
    highwayTypeConfiguration.logDeactivatedTypes();
  }
  
  /** set the flag whether or not the highways should be parsed or not
   * @param activate
   */
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }  
  
  /** verifies if the parser for these settings is active or not
   * @return true if active false otherwise
   */
  public boolean isParserActive() {
    return this.isParserActive;
  }

  

}

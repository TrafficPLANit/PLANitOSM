package org.planit.osm.converter.network;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

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
public class PlanitOsmHighwaySettings extends PlanitOsmWaySettings {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmHighwaySettings.class.getCanonicalName());
     
  /** non-urban speed limit defaults (urban defaults placed on base class) */
  OsmSpeedLimitDefaultsCategory nonUrbanSpeedLimitDefaults;
     
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
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.FOOT, planitModes.get(PredefinedModeType.PEDESTRIAN));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.BICYCLE, planitModes.get(PredefinedModeType.BICYCLE));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.MOTOR_CYCLE, planitModes.get(PredefinedModeType.MOTOR_BIKE));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.MOTOR_CAR, planitModes.get(PredefinedModeType.CAR));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.GOODS, planitModes.get(PredefinedModeType.GOODS_VEHICLE));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.HEAVY_GOODS, planitModes.get(PredefinedModeType.HEAVY_GOODS_VEHICLE));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.HEAVY_GOODS_ARTICULATED, planitModes.get(PredefinedModeType.LARGE_HEAVY_GOODS_VEHICLE));
      addOsmMode2PlanitModeMapping(OsmRoadModeTags.BUS, planitModes.get(PredefinedModeType.BUS));
      
      /* ensure external id is set based on OSM name */
      setModeExternalIdsBasedOnMappedOsmModes();
    }
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  protected Collection<String> collectAllowedOsmWayModes(String osmWayValueType) {
    Set<String> allowedModes = null; 
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayValueType)){      
      /* collect all rail and road modes that are allowed, try all because the mode categories make it difficult to collect individual modes otherwise */
      Set<String> allowedRoadModesOnRoad =  collectAllowedOsmWayModes(osmWayValueType, OsmRoadModeTags.getSupportedRoadModeTags());
      Set<String> allowedRailModesOnRoad =  collectAllowedOsmWayModes(osmWayValueType, OsmRailModeTags.getSupportedRailModeTags());      
      allowedModes = new HashSet<String>();
      allowedModes.addAll(allowedRoadModesOnRoad);
      allowedModes.addAll(allowedRailModesOnRoad);
    }else {
      LOGGER.warning(String.format("unrecognised osm highway key value type highway=%s, no allowed modes can be identified", osmWayValueType));
    }
    return allowedModes;
  }  
    
  /** Constructor
   * @param osmModeAccessDefaultsCategory highway mode access configuration
   * @param urbanHighwayDefaults configuration
   * @param nonUrbanHighwayDefaults configuration
   */
  protected PlanitOsmHighwaySettings(OsmSpeedLimitDefaultsCategory urbanSpeedLimitDefaults,
      OsmSpeedLimitDefaultsCategory nonUrbanSpeedLimitDefaults, 
      OsmModeAccessDefaultsCategory osmModeAccessHighwayDefaults) {
    super(new OsmHighwayTypeConfiguration(), urbanSpeedLimitDefaults, osmModeAccessHighwayDefaults);
    activateParser(DEFAULT_HIGHWAYS_PARSER_ACTIVE);
    /* urban speed limit defaults are place on base class, we track non_urban additional defaults here */
    this.nonUrbanSpeedLimitDefaults = nonUrbanSpeedLimitDefaults;
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
    return isOsmWayTypeDeactivated(osmHighwayValue);      
  }
    
  /**
   * Verify if the passed in OSM highway type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmHighwayTypeActivated(String osmWayValue) {
    return isOsmWayTypeActivated(osmWayValue);            
  }
    
  /**
   * Choose to not parse the given highway type, e.g. highway=road.
   * 
   * @param osmWayValue to use
   */
  public void deactivateOsmHighwayType(String osmWayValue) {
    deactivateOsmWayType(osmWayValue);
  }  
  
  /**
   * deactivate all types for highway
   */
  public void deactivateAllOsmHighWayTypes() {
    deactivateAllOsmWayTypes();
  }   
  
  /** deactivate all types for highway except the ones provides
   * 
   * @param osmHighwayTypes to not deactivate
   */
  public void deactivateAllOsmHighwayTypesExcept(String... osmHighwayTypes) {
    deactivateAllOsmHighWayTypes();
    for(String osmWayType : Arrays.asList(osmHighwayTypes)) {
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayType)) {
       activateOsmHighwayWayTypes(osmWayType);
      }
    }
  }  
  
  /**
   * Choose to add given highway type to parsed types on top of the defaults, e.g. highway=road
   * 
   * @param osmWayValue to use
   */
  public void activateOsmHighwayType(String osmWayValue) {
    activateOsmWayType(osmWayValue);
  }  
  
  /** activate all passed in highway types
   * @param osmHighwayValueTypes
   */
  public void activateOsmHighwayWayTypes(final String... osmHighwayValueTypes) {
    activateOsmWayTypes(osmHighwayValueTypes);
  }  
  
  /**
   * activate all known OSM highway types 
   */
  public void activateAllOsmHighwayTypes() {
    activateAllOsmWayTypes();    
  }   
    
  /**
   * Choose to overwrite the given highway type defaults with the given values
   * 
   * @param osmHighwayType the type to set these values for
   * @param capacityPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPerLane new value pcu/km/lane
   * @param modeProperties new values per mode
   */
  public void overwriteOsmHighwayTypeDefaultsCapacityMaxDensity(final String osmHighwayType, double capacityPerLanePerHour, double maxDensityPerLane) {
    overwriteOsmWayTypeDefaultCapacityMaxDensity(OsmHighwayTags.HIGHWAY, osmHighwayType, capacityPerLanePerHour, maxDensityPerLane);
  }  
  
  /** set the mode access for the given osm way id
   * 
   * @param osmWayId this mode access will be applied on
   * @param allowedModes to set as the only modes allowed
   */
  protected void overwriteModeAccessByOsmHighwayId(Long osmWayId, String...allowedModes) {
    if(!Set.of(allowedModes).stream().allMatch( osmMode -> OsmRoadModeTags.isRoadModeTag(osmMode) || OsmRailModeTags.isRailModeTag(osmMode))) {
      LOGGER.warning(String.format("one or more of the passed in allowed osm modes to overwrite access for osm highway %d, are not a valid osm mode, ignored request",osmWayId));
    }
    overwriteModeAccessByOsmHighwayId(osmWayId, allowedModes);
  }  
  
  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmWayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmHighwayType(final String osmWayType) {
    return getOverwrittenCapacityMaxDensityByOsmWayType(osmWayType);
  }
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmWayType to check
   * @return true when new defaults are provided, false otherwise
   */
  public boolean isDefaultCapacityOrMaxDensityOverwrittenByOsmHighwayType(final String osmWayType) {
    return isDefaultCapacityOrMaxDensityOverwrittenByOsmWayType(osmWayType);
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
  public double getDefaultSpeedLimitByOsmHighwayType(final String osmWayValue) throws PlanItException {
    if(isSpeedLimitDefaultsBasedOnUrbanArea()) {
      return getDefaultSpeedLimitByOsmWayType(osmWayValue);
    }else {
      return nonUrbanSpeedLimitDefaults.getSpeedLimit(osmWayValue);
    }     
  }
  
  /** Collect the default speed limit for a given highway tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h (for highway types, the outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea is collected)
   * @throws PlanItException thrown if error
   */  
  public Double getDefaultSpeedLimitByOsmHighwayType(final Map<String, String> tags) throws PlanItException {
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
  public void setOsmRoadMode2PlanitModeMapping(final String osmRoadMode, final Mode planitMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("osm road mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    setOsmMode2PlanitModeMapping(osmRoadMode, planitMode);
  }  
  
  /** remove a mapping from OSM road mode to PLANit mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added, either manually or through the default mapping
   * 
   * @param osmRoadMode to remove
   */
  public void removeOsmRoadModePlanitModeMapping(final String osmRoadMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("osm road mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    removeOsmMode2PlanitModeMapping(osmRoadMode);
  }
  
  
  /** remove all road modes from mapping except for the passed in ones
   * 
   * @param remainingOsmRoadModes to explicitly keep if present
   */
  public void deactivateAllRoadModesExcept(final String... remainingOsmRoadModes) {
    Collection<String> toBeRemovedModes = OsmRoadModeTags.getSupportedRoadModeTags();
    deactivateAllModesExcept(toBeRemovedModes, remainingOsmRoadModes);
  }   
  
  /** deactivate provided road modes
   * 
   * @param osmRoadModes to explicitly deactivate
   */
  public void deactivateRoadModes(final String... osmRoadModes) {
    deactivateOsmModes(Arrays.asList(osmRoadModes));
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
      return getMappedPlanitMode(osmMode);
    }
    return null;
  }
  
  /** convenience method that collects the currently mapped osm road modes for the given planit mode
   * 
   * @param planitMode to collect mapped mode for (if any)
   * @return mapped osm modes, if not available empty collection is returned
   */  
  public final Collection<String> getMappedOsmRoadModes(final Mode planitMode) {    
    return getMappedOsmModes(planitMode);
  }      

  /**
   * Collect all Osm modes that are allowed for the given osmHighway type as configured by the user
   * 
   * @param osmHighwayValueType to use
   * @return allowed OsmModes
   */
  public Collection<String> collectAllowedOsmHighwayModes(final String osmHighwayValueType) {
    return collectAllowedOsmWayModes(osmHighwayValueType);
  }  
  
  /** add allowed osm modes to highwaytype
   * 
   * @param osmHighwayType to use
   * @param osmModes to allow
   */
  public void addAllowedHighwayModes(final String osmHighwayType, final String... osmModes) {
    addAllowedOsmWayModes(osmHighwayType, osmModes);
  }
    
  /**
   * Log all de-activated OSM highwayway types
   */  
  public void logUnsupportedOsmHighwayTypes() {
    logUnsupportedOsmWayTypes();
  }
 

}

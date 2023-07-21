package org.goplanit.osm.converter.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.goplanit.osm.defaults.OsmHighwayTypeConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * All highway specific settings
 * 
 * @author markr
 *
 */
public class OsmHighwaySettings extends OsmWaySettings {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmHighwaySettings.class.getCanonicalName());
     
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
   * Each OSM road mode is mapped to a PLANit mode by default so that the memory model's modes
   * are user configurable yet linked to the original format. Note that when the reader is used
   * i.c.w. a network writer to convert one network to the other. It is paramount that the PLANit modes
   * that are mapped here are also mapped by the writer to the output format to ensure a correct I/O mapping of modes
   * 
   * The default mapping is provided below. It is important to realise that modes that are marked as N/A have no predefined
   * equivalent in PLANit, as a result they are ignored. One could add them to a known predefined mode, e.g., MOPED to MotorBikeMode, 
   * however, this would mean that a restriction on for example mopeds would also be imposed on motor bikes and this is something you 
   * likely do not want. If you must include mopeds, then add a custom mapping to a custom mode afterwards, so it is modelled 
   * separately. Again, when also persisting using a network converter, make sure the custom mode is also used in the writer to 
   * include the mode in the network output.
   * 
   * <ul>
   * <li>FOOT         to PedestrianMode </li>
   * <li>DOG          to N/A            </li>
   * <li>HORSE        to N/A            </li>
   * <li>BICYCLE      to BicycleMode    </li>
   * <li>CARRIAGE     to N/A            </li>
   * <li>TRAILER      to N/A            </li>
   * <li>CARAVAN      to N/A            </li>
   * <li>MOTOR_CYCLE  to MotorBike      </li>
   * <li>MOPED        to N/A            </li>
   * <li>MOFA         to N/A            </li>
   * <li>MOTOR_CAR    to CarMode        </li>
   * <li>MOTOR_HOME   to N/A            </li>
   * <li>TOURIST_BUS  to N/A            </li>
   * <li>COACH        to N/A            </li>
   * <li>AGRICULTURAL to N/A            </li>
   * <li>GOLF_CART    to N/A            </li>
   * <li>ATV          to N/A            </li>
   * <li>GOODS        to GoodsMode      </li>
   * <li>HEAVY_GOODS  to HeavyGoodsMode </li>
   * <li>HEAVY_GOODS_ARTICULATED  to LargeHeavyGoodsMode </li>
   * <li>BUS          to BusMode </li>
   * <li>TAXI         to N/A </li>
   * <li>SHARE_TAXI   to N/A </li>
   * <li>MINI_BUS     to N/A </li>
   * </ul>
   * 
   */
  protected void initialiseDefaultMappingFromOsmRoadModes2PlanitModes() {

    /* add default mapping */
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.FOOT, PredefinedModeType.PEDESTRIAN);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.BICYCLE, PredefinedModeType.BICYCLE);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.MOTOR_CYCLE, PredefinedModeType.MOTOR_BIKE);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.MOTOR_CAR, PredefinedModeType.CAR);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.GOODS, PredefinedModeType.GOODS_VEHICLE);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.HEAVY_GOODS,PredefinedModeType.HEAVY_GOODS_VEHICLE);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.HEAVY_GOODS_ARTICULATED, PredefinedModeType.LARGE_HEAVY_GOODS_VEHICLE);
    addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmRoadModeTags.BUS, PredefinedModeType.BUS);
      
    /* activate all defaults */
    activateOsmMode(OsmRoadModeTags.FOOT);
    activateOsmMode(OsmRoadModeTags.BICYCLE);
    activateOsmMode(OsmRoadModeTags.MOTOR_CYCLE);
    activateOsmMode(OsmRoadModeTags.MOTOR_CAR);
    activateOsmMode(OsmRoadModeTags.GOODS);
    activateOsmMode(OsmRoadModeTags.HEAVY_GOODS);
    activateOsmMode(OsmRoadModeTags.HEAVY_GOODS_ARTICULATED);
    activateOsmMode(OsmRoadModeTags.BUS);

  }

  /**
   * Collect all OSM modes from the passed in OSM way value for highways
   *
   * @param osmWayValueType to use
   * @return allowed OsmModes found
   */
  protected Collection<String> collectAllowedOsmWayModes(String osmWayValueType) {
    Set<String> allowedModes = null; 
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayValueType)){      
      /* collect all rail and road modes that are allowed, try all because the mode categories make it difficult to collect individual modes otherwise */
      Set<String> allowedRoadModesOnRoad =  collectAllowedOsmWayModes(OsmHighwayTags.getHighwayKeyTag(), osmWayValueType, OsmRoadModeTags.getSupportedRoadModeTags());
      Set<String> allowedRailModesOnRoad =  collectAllowedOsmWayModes(OsmHighwayTags.getHighwayKeyTag(), osmWayValueType, OsmRailModeTags.getSupportedRailModeTags());
      allowedModes = new HashSet<>();
      allowedModes.addAll(allowedRoadModesOnRoad);
      allowedModes.addAll(allowedRailModesOnRoad);
    }else {
      LOGGER.warning(String.format("Unrecognised osm highway key value type %s=%s, no allowed modes can be identified",
          OsmHighwayTags.getHighwayKeyTag(), osmWayValueType));
    }
    return allowedModes;
  }  
    
  /** Constructor
   * 
   * @param urbanSpeedLimitDefaults urban limit defaults to use
   * @param nonUrbanSpeedLimitDefaults non-urban defaults to use 
   * @param osmModeAccessHighwayDefaults mode configuration
   */
  protected OsmHighwaySettings(OsmSpeedLimitDefaultsCategory urbanSpeedLimitDefaults,
      OsmSpeedLimitDefaultsCategory nonUrbanSpeedLimitDefaults, 
      OsmModeAccessDefaultsCategory osmModeAccessHighwayDefaults) {
    super(new OsmHighwayTypeConfiguration(), urbanSpeedLimitDefaults, osmModeAccessHighwayDefaults);
    activateParser(DEFAULT_HIGHWAYS_PARSER_ACTIVE);
    /* urban speed limit defaults are place on base class, we track non_urban additional defaults here */
    this.nonUrbanSpeedLimitDefaults = nonUrbanSpeedLimitDefaults;
  }  
  
  /**
   * Default is OSM highway type when the type is not supported is set to PlanitOsmTags.TERTIARY.
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
  public final String getDefaultOsmHighwayTypeWhenUnsupported() {
    return defaultOsmHighwayTypeWhenUnsupported;
  }

  /**
   * set the default to be used when we encounter an unsupported type.
   * 
   * @param defaultOsmHighwayValueWhenUnsupported the default to use, should be a type that is supported.
   */
  public void setDefaultWhenOsmHighwayTypeUnsupported(String defaultOsmHighwayValueWhenUnsupported) {
    this.defaultOsmHighwayTypeWhenUnsupported = defaultOsmHighwayValueWhenUnsupported;
  } 
  
  /**
   * check if a default type is set when the activate type is not supported
   * @return true when available false otherwise
   **/
  public final boolean isApplyDefaultWhenOsmHighwayTypeDeactivated() {
    return defaultOsmHighwayTypeWhenUnsupported==null;
  }  
  
  /**
   * remove default type in case activate type is not supported by the reader
   */
  public final void removeOsmHighwayTypeWhenUnsupported() {
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
  public void deactivateAllOsmHighwayTypes() {
    deactivateAllOsmWayTypes();
  }   
  
  /** deactivate all types for highway except the ones provides
   * 
   * @param osmHighwayTypes to not deactivate
   */
  public void deactivateAllOsmHighwayTypesExcept(String... osmHighwayTypes) {
    deactivateAllOsmHighwayTypesExcept(List.of(osmHighwayTypes));
  } 
  
  /** deactivate all types for highway except the ones provides
   * 
   * @param osmHighwayTypes to not deactivate
   */
  public void deactivateAllOsmHighwayTypesExcept(List<String> osmHighwayTypes) {
    deactivateAllOsmHighwayTypes();
    for(String osmWayType : osmHighwayTypes) {
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayType)) {
       activateOsmHighwayTypes(osmWayType);
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
  
  /** Activate all passed in highway types
   * 
   * @param osmHighwayValueTypes to activate
   */
  public void activateOsmHighwayTypes(final String... osmHighwayValueTypes) {
    activateOsmWayTypes(osmHighwayValueTypes);
  }  
  
  /** Activate all passed in highway types
   * 
   * @param osmHighwayValueTypes to activate
   */
  public void activateOsmHighwayTypes(final List<String> osmHighwayValueTypes) {
    activateOsmWayTypes(osmHighwayValueTypes);
  }   
  
  /**
   * Activate all known OSM highway types 
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
   */
  public void overwriteCapacityMaxDensityDefaults(final String osmHighwayType, Number capacityPerLanePerHour, Number maxDensityPerLane) {
    overwriteOsmWayTypeDefaultCapacityMaxDensity(OsmHighwayTags.HIGHWAY, osmHighwayType, capacityPerLanePerHour.doubleValue(), maxDensityPerLane.doubleValue());
  }    
  
  /**
   * Collect the overwrite type values that should be used
   * 
   * @param osmWayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmHighwayType(final String osmWayType) {
    return getOverwrittenCapacityMaxDensityByOsmWayType(osmWayType);
  }
  
  /**
   * Check if defaults should be overwritten
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
   */
  public double getDefaultSpeedLimitByOsmHighwayType(final String osmWayValue){
    if(isSpeedLimitDefaultsBasedOnUrbanArea()) {
      return getDefaultSpeedLimitByOsmTypeValue(OsmHighwayTags.getHighwayKeyTag(), osmWayValue);
    }else {
      return nonUrbanSpeedLimitDefaults.getSpeedLimit(OsmHighwayTags.getHighwayKeyTag(), osmWayValue);
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
      throw new PlanItException("no OSM highway key contained in provided osmTags when collecting default speed limit by OsmHighwayType");
    }
    return getDefaultSpeedLimitByOsmHighwayType(tags.get(osmWayKey));
  }  
  
  /** activate OSM road mode based on its default mapping to PLANit mode. This means that the osmMode will be added to the PLANit network, but also any of its restrictions
   *  will be imposed on the PLANit mode that is provided. 
   * 
   * @param osmRoadMode to set
   */
  public void activateOsmRoadMode(final String osmRoadMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("OSM road mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    activateOsmMode(osmRoadMode);
  }  
  
  /** deactivate an OSM road mode from parsing. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added.
   * 
   * @param osmRoadMode to remove
   */
  public void deactivateOsmRoadMode(final String osmRoadMode) {
    if(!OsmRoadModeTags.isRoadModeTag(osmRoadMode)) {
      LOGGER.warning(String.format("osm road mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRoadMode));
      return;
    }
    deactivateOsmMode(osmRoadMode);
  }
  
  /** Remove a mapping from OSM road modes to PLANit modes. This means that the osmModes will not be added to the PLANit network.
   * You can only remove modes when they are already added, either manually or through the default mapping
   * 
   * @param osmRoadModes to remove
   */
  public void deactivateOsmRoadModes(final List<String> osmRoadModes) {
    if(osmRoadModes == null) {
      return;
    }
    osmRoadModes.forEach( osmRoadMode -> deactivateOsmRoadMode(osmRoadMode));
  }  
  
  
  /** Remove all road modes from mapping except for the passed in ones
   * 
   * @param deactivateAllRoadModesExcept to explicitly keep if present
   */
  public void deactivateAllOsmRoadModesExcept(final String... deactivateAllRoadModesExcept) {
    List<String> exceptionList = null;
    if(deactivateAllRoadModesExcept==null) {
      exceptionList = new ArrayList<>(0);
    }else {
      exceptionList = Arrays.asList(deactivateAllRoadModesExcept);
    }    
    deactivateAllOsmRoadModesExcept(exceptionList);
  }   
  
  /** remove all road modes from mapping except for the passed in ones
   * 
   * @param remainingOsmRoadModes to explicitly keep if present
   */
  public void deactivateAllOsmRoadModesExcept(final List<String> remainingOsmRoadModes) {
    Collection<String> toBeRemovedModes = OsmRoadModeTags.getSupportedRoadModeTags();
    deactivateAllModesExcept(toBeRemovedModes, remainingOsmRoadModes);
  }  
  
  /** deactivate provided road modes
   * 
   * @param osmRoadModes to explicitly deactivate
   */
  public void deactivateOsmRoadModes(final String... osmRoadModes) {
    deactivateOsmRoadModes(Arrays.asList(osmRoadModes));
  } 
    
  /**
   * remove all road modes from the network when parsing
   */
  public void removeAllRoadModes() {
    deactivateAllOsmRoadModesExcept((String[])null);
  }
  
  /** convenience method that collects the currently mapped PLANit road mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public PredefinedModeType getMappedPlanitRoadMode(final String osmMode) {
    if(OsmRoadModeTags.isRoadModeTag(osmMode)) {
      return getPlanitModeTypeIfActivated(osmMode);
    }
    return null;
  }
  
  /** convenience method that collects the currently mapped osm road modes for the given planit mode
   * 
   * @param planitModeType to collect mapped mode for (if any)
   * @return mapped osm modes, if not available empty collection is returned
   */  
  public final Collection<String> getMappedOsmRoadModes(final PredefinedModeType planitModeType) {
    return getAcivatedOsmModes(planitModeType);
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
    addAllowedHighwayModes(osmHighwayType, Arrays.asList(osmModes));
  }
  
  /** add allowed osm modes to highwaytype
   * 
   * @param osmHighwayType to use
   * @param osmModes to allow
   */
  public void addAllowedHighwayModes(final String osmHighwayType, final List<String> osmModes) {
    addAllowedOsmWayModes(OsmHighwayTags.getHighwayKeyTag(), osmHighwayType, osmModes);
  }  
    
  /**
   * Log all de-activated OSM highwayway types
   */  
  public void logUnsupportedOsmHighwayTypes() {
    logUnsupportedOsmWayTypes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings() {
    LOGGER.info(String.format("Highway (road) parser activated: %s", isParserActive()));
  }

}

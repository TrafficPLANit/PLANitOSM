package org.planit.osm.settings.network;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.planit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.planit.osm.defaults.OsmRailwayTypeConfiguration;
import org.planit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.Modes;
import org.planit.utils.mode.PredefinedModeType;

/**
 * Settings specific to railways when parsing OSM files and converting them to a PLANit memory model
 * 
 * @author markr
 *
 */
public class PlanitOsmRailwaySettings extends PlanitOsmWaySettings {
  
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmRailwaySettings.class.getCanonicalName());
    
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
      addOsmMode2PlanitModeMapping(OsmRailModeTags.FUNICULAR, planitModes.get(PredefinedModeType.TRAM));
      addOsmMode2PlanitModeMapping(OsmRailModeTags.LIGHT_RAIL, planitModes.get(PredefinedModeType.LIGHTRAIL));
      addOsmMode2PlanitModeMapping(OsmRailModeTags.MONO_RAIL, planitModes.get(PredefinedModeType.TRAM));
      addOsmMode2PlanitModeMapping(OsmRailModeTags.NARROW_GAUGE, planitModes.get(PredefinedModeType.TRAIN));
      addOsmMode2PlanitModeMapping(OsmRailModeTags.TRAIN, planitModes.get(PredefinedModeType.TRAIN));
      addOsmMode2PlanitModeMapping(OsmRailModeTags.SUBWAY, planitModes.get(PredefinedModeType.SUBWAY));
      addOsmMode2PlanitModeMapping(OsmRailModeTags.TRAM, planitModes.get(PredefinedModeType.TRAM));
      
      /* ensure external id is set based on OSM name */
      setModeExternalIdsBasedOnMappedOsmModes();
    }           
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  protected Collection<String> collectAllowedOsmWayModes(String osmValueType) {
    Set<String> allowedModes = null; 
    if(OsmRailwayTags.isRailBasedRailway(osmValueType)) {
      allowedModes = collectAllowedOsmWayModes(osmValueType, OsmRailModeTags.getSupportedRailModeTags());
    }else {
      LOGGER.warning(String.format("unrecognised osm railway railway=%s, no allowed modes can be identified", osmValueType));
    }
    return allowedModes;
  }  
    
  /** by default the railway parser is deactivated */
  public static boolean DEFAULT_RAILWAYS_PARSER_ACTIVE = false;
  
  /**
   * Constructor 
   * 
   * @param railwaySpeedLimitDefaults as they are initially provided
   * @param osmModeAccessRailwayDefaults configuration
   */
  public PlanitOsmRailwaySettings(OsmSpeedLimitDefaultsCategory railwaySpeedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessRailwayDefaults) {
    super(new OsmRailwayTypeConfiguration(), railwaySpeedLimitDefaults, osmModeAccessRailwayDefaults);
    activateParser(DEFAULT_RAILWAYS_PARSER_ACTIVE);
  }
  
  /**
   * Verify if the passed in OSM rail way type is explicitly deactivated. Deactivated types will be ignored
   * when processing ways.
   * 
   * @param osmRailWayValue, e.g. rail
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmRailwayTypeDeactivated(final String osmRailWayValue) {
      return isOsmRailwayTypeDeactivated(osmRailWayValue);
  }
    
  /**
   * Verify if the passed in OSM railway type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmRailwayTypeActivated(String osmWayValue) {
    return isOsmWayTypeActivated(osmWayValue);
  }  
  
  /**
   * Choose to not parse the given railway type, e.g. railway=rail.
   * 
   * @param osmWayValue to use
   */
  public void deactivateOsmRailwayType(String osmWayValue) {
    deactivateOsmWayType(osmWayValue);      
  } 
  
  /** deactivate all types for highway except the ones provides
   * 
   * @param osmHighwayTypes to not deactivate
   */
  public void deactivateAllOsmRailwayTypesExcept(String... osmRailwayTypes) {
    deactivateAllOsmRailwayTypes();
    for(String osmWayType : Arrays.asList(osmRailwayTypes)) {
      if(OsmRailwayTags.isRailBasedRailway(osmWayType)) {
       activateOsmRailwayType(osmWayType);
      }
    }
  }  
  
  /**
   * Choose to add given railway type to parsed types on top of the defaults, e.g. railway=rail.
   * 
   * @param osmWayValue to use
   */
  public void activateOsmRailwayType(String osmWayValue) {
    activateOsmWayType(osmWayValue);
  }  
  
  /** activate all passed in highway types
   * @param osmRailwayValueTypes
   */
  public void activateOsmRailwayTypes(String... osmRailwayValueTypes) {
    activateOsmWayTypes(osmRailwayValueTypes);
  }  
  
  /**
   * activate all known OSM railway types 
   */
  public void activateAllOsmRailwayTypes() {
    activateAllOsmWayTypes();    
  } 
  
  /**
   * deactivate all types for rail
   */
  public void deactivateAllOsmRailwayTypes() {
    deactivateAllOsmWayTypes();
  } 
  
  /**
   * Log all de-activated OSM railway types
   */  
  public void logUnsupportedOsmRailwayTypes() {
    logUnsupportedOsmWayTypes();
  }  
  
  /* overwrite */
  
  /**
   * Choose to overwrite the given railway type defaults with the given values
   * 
   * @param osmRailwayType the type to set these values for
   * @param capacityPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPerLane new value pcu/km/lane
   * @param modeProperties new values per mode
   */
  public void overwriteOsmRailwayTypeDefaultsCapacityMaxDensity(String osmRailwayType, double capacityPerLanePerHour, double maxDensityPerLane) {
    overwriteOsmWayTypeDefaultCapacityMaxDensity(OsmRailwayTags.RAILWAY, osmRailwayType, capacityPerLanePerHour, maxDensityPerLane);
  }  
  
  /** set the mode access for the given osm way id
   * 
   * @param osmWayId this mode access will be applied on
   * @param allowedModes to set as the only modes allowed
   */
  protected void overwriteModeAccessByOsmHighwayId(Long osmWayId, String...allowedModes) {
    if(!Set.of(allowedModes).stream().allMatch( osmMode -> OsmRailModeTags.isRailModeTag(osmMode))) {
      LOGGER.warning(String.format("one or more of the passed in allowed osm modes to overwrite access for osm rail way %d, are not a valid osm mode, ignored request",osmWayId));
    }
    overwriteModeAccessByOsmHighwayId(osmWayId, allowedModes);
  }   
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmWayType to check
   * @return true when new defaults are provided, false otherwise
   */
  public boolean isDefaultCapacityOrMaxDensityOverwrittenByOsmRailwayType(final String osmWayType) {
    return isDefaultCapacityOrMaxDensityOverwrittenByOsmWayType(osmWayType);
  }  
  
  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmWayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmRailwayType(String osmWayType) {
    return getOverwrittenCapacityMaxDensityByOsmWayType(osmWayType);
  }  
    
  /* speed limit */
  
  /** Collect the speed limit for a given railway tag value, e.g. railway=typeValue, based on the defaults provided (typically set by country)
   * 
   * @param osmRailwayValue way value type to collect default speed limit for
   * @return speedLimit in km/h
   * @throws PlanItException thrown if error
   */
  public double getDefaultSpeedLimitByOsmRailwayType(String osmWayValue) throws PlanItException {
    return getDefaultSpeedLimitByOsmWayType(osmWayValue);    
  }  
  
  /** Collect the default speed limit for a given railway tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h 
   * @throws PlanItException thrown if error
   */  
  public Double getDefaultSpeedLimitByOsmRailwayType(Map<String, String> tags) throws PlanItException {
    return getDefaultSpeedLimitByOsmWayType(OsmRailwayTags.RAILWAY, tags);
  }   
  
  /* mode */
  
  /** add/overwrite a mapping from OSM rail mode to PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmRoadMode to set
   * @param planitMode to map it to
   */
  public void setOsmRailMode2PlanitModeMapping(String osmRailMode, Mode planitMode) {
    String convertedOsmMode = OsmRailModeTags.convertModeToRailway(osmRailMode);
    if(!OsmRailwayTags.isRailBasedRailway(convertedOsmMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    setOsmMode2PlanitModeMapping(osmRailMode, planitMode);
  }   
  
  /** remove a mapping from OSM road mode to PLANit mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added, either manually or through the default mapping
   * 
   * @param osmRoadMode to remove
   */
  public void removeOsmRailMode2PlanitModeMapping(String osmRailMode) {
    String convertedOsmMode = OsmRailModeTags.convertModeToRailway(osmRailMode);
    if(!OsmRailwayTags.isRailBasedRailway(convertedOsmMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    removeOsmMode2PlanitModeMapping(osmRailMode);
  }
  
  /** remove all rail modes from mapping
   * 
   */
  public void deactivateAllRailModes() {
    deactivateOsmModes(OsmRailModeTags.getSupportedRailModeTags());
  }    
  
  /** remove all rail modes from mapping except for the passed in ones
   * 
   * @param remainingOsmRailModes to explicitly keep if present
   */
  public void deactivateAllRailModesExcept(final String... remainingOsmRailModes) {
    Collection<String> toBeRemovedModes = OsmRailModeTags.getSupportedRailModeTags();
    deactivateAllModesExcept(toBeRemovedModes, remainingOsmRailModes);
  }     
  
  /** deactivate provided rail modes
   * 
   * @param osmRailModes to explicitly deactivate
   */
  public void deactivateRailMode(final String... osmRailModes) {
    deactivateOsmModes(Arrays.asList(osmRailModes));
  }    
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getMappedPlanitRailMode(final String osmMode) {
    if(OsmRailModeTags.isRailModeTag(osmMode)) {
      return getMappedPlanitMode(osmMode);
    }
    return null;
  }  
  
  /** convenience method that collects the currently mapped osm rail modes for the given planit mode
   * 
   * @param planitMode to collect mapped mode for (if any)
   * @return mapped osm modes, if not available empty collection is returned
   */  
  public final Collection<String> getMappedOsmRailModes(final Mode planitMode) {    
    return getMappedOsmModes(planitMode);
  }   
    
  /**
   * Collect all Osm modes that are allowed for the given osmRailway type as configured by the user
   * 
   * @param osmRailwayValueType to use
   * @return allowed OsmModes found
   */
  public Collection<String> collectAllowedOsmRailwayModes(String osmRailwayValueType) {
    return collectAllowedOsmWayModes(osmRailwayValueType);    
  }
    
}

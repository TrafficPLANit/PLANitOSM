package org.planit.osm.settings;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.planit.osm.defaults.OsmRailwayTypeConfiguration;
import org.planit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.Modes;
import org.planit.utils.mode.PredefinedModeType;

/**
 * Settings specific to railways when parsing OSM files and converting them to a PLANit memory model
 * 
 * @author markr
 *
 */
public class PlanitOsmRailwaySettings {
  
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmRailwaySettings.class.getCanonicalName());

  /**
   * Configuration options regarding the activation/deactivation of specific OSM railway types in the parser
   */
  protected final OsmRailwayTypeConfiguration railwayTypeConfiguration = new OsmRailwayTypeConfiguration(); 
  
  /**
   * speed limit defaults for railways
   */
  OsmSpeedLimitDefaultsCategory railwaySpeedLimitDefaults; 
  
  /**
   * mode access defaults for railways
   */
  OsmModeAccessDefaultsCategory osmModeAccessRailwayDefaults;
  
  /** mapping from each supported osm rail mode to a PLANit mode */
  protected final Map<String, Mode> osmRailMode2PlanitModeMap = new HashMap<String, Mode>();

  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private boolean isParserActive = DEFAULT_RAILWAYS_PARSER_ACTIVE;
  
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
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.FUNICULAR, planitModes.get(PredefinedModeType.TRAM));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.LIGHT_RAIL, planitModes.get(PredefinedModeType.LIGHTRAIL));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.MONO_RAIL, planitModes.get(PredefinedModeType.TRAM));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.NARROW_GAUGE, planitModes.get(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.PRESERVED, planitModes.get(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.RAIL, planitModes.get(PredefinedModeType.TRAIN));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.SUBWAY, planitModes.get(PredefinedModeType.SUBWAY));
      osmRailMode2PlanitModeMap.put(OsmRailwayTags.TRAM, planitModes.get(PredefinedModeType.TRAM));
      
      /* ensure external id is set based on OSM name */
      osmRailMode2PlanitModeMap.forEach( (osmMode, planitMode) -> PlanitOsmSettings.addToModeExternalId(planitMode, osmMode));
    }           
  }  
  
  /** collect the current configuration setup for applying speed limits in case the maxspeed tag is not available on the parsed osm railway
   * 
   * @return speed limit configuration
   */
  protected OsmSpeedLimitDefaultsCategory getRailwaySpeedLimitConfiguration() {
    return railwaySpeedLimitDefaults;
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
    this.railwaySpeedLimitDefaults = railwaySpeedLimitDefaults;
    this.osmModeAccessRailwayDefaults = osmModeAccessRailwayDefaults;
  }
  
  /**
   * Verify if the passed in OSM rail way type is explicitly deactivated. Deactivated types will be ignored
   * when processing ways.
   * 
   * @param osmRailWayValue, e.g. rail
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmRailwayTypeDeactivated(final String osmRailWayValue) {
      return railwayTypeConfiguration.isDeactivated(osmRailWayValue);
  }
  
  /** collect all activated types as a set (copy)
   * @return set of currently activated osm railway types, modifications to this set have no effect on configuration
   */
  public final Set<String> getSetOfActivatedOsmRailwayTypes(){
    return railwayTypeConfiguration.setOfActivatedTypes();    
  }  
  
  /**
   * Verify if the passed in OSM railway type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmRailwayTypeActivated(String osmWayValue) {
    return railwayTypeConfiguration.isActivated(osmWayValue);
  }  
  
  /**
   * Choose to not parse the given railway type, e.g. railway=rail.
   * 
   * @param osmWayValue to use
   */
  public void deactivateOsmRailwayType(String osmWayValue) {
      railwayTypeConfiguration.deactivate(osmWayValue);      
  } 
  
  /**
   * Choose to add given railway type to parsed types on top of the defaults, e.g. railway=rail.
   * 
   * @param osmWayValue to use
   */
  public void activateOsmRailwayType(String osmWayValue) {
      railwayTypeConfiguration.activate(osmWayValue);
  }  
  
  /**
   * activate all known OSM railway types 
   */
  public void activateAllOsmRailwayTypes() {
    railwayTypeConfiguration.setOfDeactivatedTypes().forEach( unsupportedType -> activateOsmRailwayType(unsupportedType));    
  } 
  
  /** Collect the speed limit for a given railway tag value, e.g. railway=typeValue, based on the defaults provided (typically set by country)
   * 
   * @param osmRailwayValue way value type to collect default speed limit for
   * @return speedLimit in km/h
   * @throws PlanItException thrown if error
   */
  public double getDefaultSpeedLimitByOsmRailwayType(String osmWayValue) throws PlanItException {
    return railwaySpeedLimitDefaults.getSpeedLimit(osmWayValue);    
  }  
  
  /** Collect the default speed limit for a given railway tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h 
   * @throws PlanItException thrown if error
   */  
  public Double getDefaultSpeedLimitByOsmRailwayType(Map<String, String> tags) throws PlanItException {
    String osmWayKey = null;
    if(tags.containsKey(OsmRailwayTags.RAILWAY)){
      osmWayKey = OsmRailwayTags.RAILWAY;      
    }else {
      throw new PlanItException("no railway key contained in provided osmTags when collecting default speed limit by OsmRailwayType");
    }
    return getDefaultSpeedLimitByOsmRailwayType(tags.get(osmWayKey));
  }   
  
  /** add/overwrite a mapping from OSM rail mode to PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmRoadMode to set
   * @param planitMode to map it to
   */
  public void setOsmRailMode2PlanitModeMapping(String osmRailMode, Mode planitMode) {
    if(!OsmRailwayTags.isRailBasedRailway(osmRailMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    if(planitMode == null) {
      LOGGER.warning(String.format("planit mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode %s, ignored", osmRailMode));
      return;
    }
    osmRailMode2PlanitModeMap.put(osmRailMode, planitMode);
    PlanitOsmSettings.addToModeExternalId(planitMode,osmRailMode);
  }   
  
  /** remove a mapping from OSM road mode to PLANit mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added, either manually or through the default mapping
   * 
   * @param osmRoadMode to remove
   */
  public void removeOsmRailMode2PlanitModeMapping(String osmRailMode) {
    if(!OsmRailwayTags.isRailBasedRailway(osmRailMode)) {
      LOGGER.warning(String.format("osm rail mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmRailMode));
      return;
    }
    LOGGER.fine(String.format("osm rail mode %s is deactivated", osmRailMode));
    
    Mode planitMode = osmRailMode2PlanitModeMap.remove(osmRailMode);
    PlanitOsmSettings.removeFromModeExternalId(planitMode,osmRailMode);
  }
  
  /** remove all rail modes from mapping
   * 
   */
  public void deactivateAllRailModes() {
    Collection<String> allRailModes = OsmRailwayTags.getSupportedRailModeTags();
    for(String osmRailMode : allRailModes) {
      removeOsmRailMode2PlanitModeMapping(osmRailMode);
    }
  }    
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getMappedPlanitRailMode(final String osmMode) {
    if(OsmRailwayTags.isRailBasedRailway(osmMode)) {
      return this.osmRailMode2PlanitModeMap.get(osmMode);
    }
    return null;
  }  
  
  /** Verify if the passed in osmMode is mapped to a rail mode, i.e., if it is actively included when reading the network
   * @param osmMode to verify
   * @return true if mapped, false otherwise
   */
  public boolean hasMappedPlanitMode(final String osmMode) {
    return getMappedPlanitRailMode(osmMode) != null;    
  }  
  
  /**
   * Collect all Osm modes that are allowed for the given osmRailway type as configured by the user
   * 
   * @param osmRailwayValueType to use
   * @return allowed OsmModes found
   */
  public Collection<String> collectAllowedOsmRailwayModes(String osmRailwayValueType) {
    Set<String> allowedModes = null; 
    if(OsmRailwayTags.isRailBasedRailway(osmRailwayValueType)) {
      /* while rail has no categories that complicate identifying mode support, we utilise the same approach for consistency and future flexibility */
      allowedModes =  OsmRailwayTags.getSupportedRailModeTags().stream().filter( railModeTag -> osmModeAccessRailwayDefaults.isAllowed(osmRailwayValueType, railModeTag)).collect(Collectors.toSet());
    }else {
      LOGGER.warning(String.format("unrecognised osm railway railway=%s, no allowed modes can be identified", osmRailwayValueType));
    }
    return allowedModes;
  }  
  
  /**
   * deactivate all types for rail
   */
  public void deactivateAllOsmRailWayTypes() {
    railwayTypeConfiguration.deactivateAll();
  } 
  
  /**
   * Log all de-activated OSM railway types
   */  
  public void logUnsupportedOsmRailwayTypes() {
    railwayTypeConfiguration.logDeactivatedTypes();
  }

  /** set the flag whether or not the railways should be parsed or not
   * @param activate
   */
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }

  /** verifies if the parser for these settings is active or not
   * @return
   */
  public boolean isParserActive() {
    return this.isParserActive;
  }   
    
}

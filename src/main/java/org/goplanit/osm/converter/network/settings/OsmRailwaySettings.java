package org.goplanit.osm.converter.network.settings;

import java.util.*;
import java.util.logging.Logger;

import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmRailwayTypeConfiguration;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;

/**
 * Settings specific to railways when parsing OSM files and converting them to a PLANit memory model. The keys a
 * settings file can set are on {@link OsmRailwayYamlKeyedSettings}.
 * <p>
 * Rail in OSM ranges from main lines to trams in the street and disused track. These settings say which railway types
 * and rail modes are parsed, how OSM rail modes such as light_rail map to PLANit modes, and how far a signal on a
 * tram track may be from the road it crosses to hold car traffic there
 * </p>
 * 
 * @author markr
 *
 */
public class OsmRailwaySettings extends OsmRailwayYamlKeyedSettings {
  
  private static final Logger LOGGER = Logger.getLogger(OsmRailwaySettings.class.getCanonicalName());

  /** immutable default rail mode mappings used to initialise each instance */
  private static final Map<String, PredefinedModeType> DEFAULT_OSM_RAIL_MODE_MAPPINGS =
      Collections.unmodifiableMap(createDefaultOsmRailModeMappings());

  /** by default the railway parser is deactivated */
  public static boolean DEFAULT_RAILWAYS_PARSER_ACTIVE = false;
    
  /**
   * Create the immutable default OSM rail mode mappings used to initialise each instance.
   *
   * @return immutable default mapping catalogue
   */
  private static Map<String, PredefinedModeType> createDefaultOsmRailModeMappings() {
    Map<String, PredefinedModeType> defaultMappings = new LinkedHashMap<>();
    defaultMappings.put(OsmRailModeTags.FUNICULAR, PredefinedModeType.TRAM);
    defaultMappings.put(OsmRailModeTags.LIGHT_RAIL, PredefinedModeType.LIGHTRAIL);
    defaultMappings.put(OsmRailModeTags.MONO_RAIL, PredefinedModeType.TRAM);
    defaultMappings.put(OsmRailModeTags.NARROW_GAUGE, PredefinedModeType.TRAIN);
    defaultMappings.put(OsmRailModeTags.TRAIN, PredefinedModeType.TRAIN);
    defaultMappings.put(OsmRailModeTags.SUBWAY, PredefinedModeType.SUBWAY);
    defaultMappings.put(OsmRailModeTags.TRAM, PredefinedModeType.TRAM);
    return defaultMappings;
  }

  /**
   * Initialise this instance with the default OSM rail mode mappings.
   * <p>
   * Some defaults are coarse approximations because PLANit does not expose dedicated predefined modes for all OSM
   * rail subtypes, for example {@code funicular} and {@code monorail}.
   * </p>
   */
  protected void initialiseOsmRailModeMappings() {
    setOsmMode2PlanitPredefinedModeTypeMappings(DEFAULT_OSM_RAIL_MODE_MAPPINGS);
  }

  /**
   * Initialise the rail modes that are activated by default on this instance.
   */
  protected void initialiseActivatedOsmRailModes() {
    activateOsmModes(DEFAULT_OSM_RAIL_MODE_MAPPINGS.keySet());
  }

  /**
   * Collect all OSM modes from the passed in OSM way value for railways
   *
   * @param osmWayValueType to use
   * @return allowed OsmModes found
   */
  protected Collection<String> collectAllowedOsmWayModes(String osmWayValueType) {
    Set<String> allowedModes = null; 
    if(OsmRailwayTags.isRailBasedRailway(osmWayValueType)) {
      allowedModes = collectAllowedOsmWayModes(
          OsmRailwayTags.getRailwayKeyTag(), osmWayValueType, OsmRailModeTags.getSupportedRailModeTags());
    }else {
      LOGGER.warning(String.format("Unrecognised OSM railway %s=%s, no allowed modes can be identified",
          OsmRailwayTags.getRailwayKeyTag(), osmWayValueType));
    }
    return allowedModes;
  }  
    
  /**
   * Constructor 
   * 
   * @param railwaySpeedLimitDefaults as they are initially provided
   * @param osmModeAccessRailwayDefaults configuration
   */
  public OsmRailwaySettings(
      OsmSpeedLimitDefaultsCategory railwaySpeedLimitDefaults,
      OsmModeAccessDefaultsCategory osmModeAccessRailwayDefaults) {
    super(new OsmRailwayTypeConfiguration(), railwaySpeedLimitDefaults, osmModeAccessRailwayDefaults);
    initialiseOsmRailModeMappings();
    initialiseActivatedOsmRailModes();
    activateParser(DEFAULT_RAILWAYS_PARSER_ACTIVE);
  }
  
  /**
   * Verify if the passed in OSM railway type is explicitly deactivated. Deactivated types will be ignored
   * when processing ways.
   * 
   * @param osmRailWayValue, e.g. rail
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmRailwayTypeDeactivated(final String osmRailWayValue) {
      return isOsmWayTypeDeactivated(osmRailWayValue);
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
  
  /** deactivate all types for railway except the ones provides
   *
   * @param osmRailwayTypes to not deactivate
   */
  @CoveredBySettingsKey("types")
  public void deactivateAllOsmRailwayTypesExcept(String... osmRailwayTypes) {
    deactivateAllOsmRailwayTypesExcept(Arrays.asList(osmRailwayTypes));
  }

  /** activate all passed in railway types
   * @param osmRailwayValueTypes to activate
   */
  @CoveredBySettingsKey("types+")
  public void activateOsmRailwayTypes(String... osmRailwayValueTypes) {
    activateOsmWayTypes(osmRailwayValueTypes);
  }

  /** activate all passed in railway types
   * @param osmRailwayValueTypes to activate
   */
  @CoveredBySettingsKey("types+")
  public void activateOsmRailwayTypes(List<String> osmRailwayValueTypes) {
    activateOsmWayTypes(osmRailwayValueTypes);
  }

  /**
   * activate all known OSM railway types
   */
  @CoveredBySettingsKey("types")
  public void activateAllOsmRailwayTypes() {
    activateAllOsmWayTypes();
  }

  /**
   * deactivate all types for rail
   */
  @CoveredBySettingsKey("types")
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
   */
  @CoveredBySettingsKey("capacityMaxDensity")
  public void overwriteCapacityMaxDensityDefaults(
      String osmRailwayType, Number capacityPerLanePerHour, Number maxDensityPerLane) {
    overwriteCapacityMaxDensityDefaults(osmRailwayType, new OsmWayTypeCapacityMaxDensity(
        capacityPerLanePerHour.doubleValue(), maxDensityPerLane.doubleValue()));
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
   * collect the overwritten type values that should be used
   * 
   * @param osmWayType to collect overwrite values for
   * @return the new values' capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmRailwayType(String osmWayType) {
    return getOverwrittenCapacityMaxDensityByOsmWayType(osmWayType);
  }  
    
  /* speed limit */
  
  /** Collect the speed limit for a given railway tag value, e.g. railway=typeValue, based on the defaults
   *  provided (typically set by country)
   * 
   * @param osmWayValue way value type to collect default speed limit for
   * @return speedLimit in km/h
   */
  public double getDefaultSpeedLimitByOsmRailwayType(String osmWayValue){
    return getDefaultSpeedLimitByOsmTypeValue(OsmRailwayTags.getRailwayKeyTag(), osmWayValue);
  }  

  /* mode */

  /** remove all rail modes from mapping except for the passed in ones
   *
   * @param remainingOsmRailModes to explicitly keep if present
   */
  @CoveredBySettingsKey("modes")
  public void deactivateAllOsmRailModesExcept(final String... remainingOsmRailModes) {
    deactivateAllOsmRailModesExcept(Arrays.asList(remainingOsmRailModes));
  }
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode type for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public PredefinedModeType getMappedPlanitRailMode(final String osmMode) {
    if(OsmRailModeTags.isRailModeTag(osmMode)) {
      return getPlanitModeTypeIfActivated(osmMode);
    }
    return null;
  }  
  
  /** convenience method that collects the currently mapped osm rail modes for the given planit mode
   * 
   * @param planitModeType to collect mapped mode for (if any)
   * @return mapped osm modes, if not available empty collection is returned
   */  
  public final Set<String> getMappedOsmRailModes(final PredefinedModeType planitModeType) {
    return getActivatedOsmModes(planitModeType);
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue("Railway parser activated", isParserActive(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum track signal to road crossing distance (m)",
        getMaximumDistanceTrackSignalToRoadCrossingMeters(), level));
    logModeMappings(level + 1);
  }
}


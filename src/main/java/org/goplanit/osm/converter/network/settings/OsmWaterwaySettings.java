package org.goplanit.osm.converter.network.settings;

import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.defaults.OsmWaterwayTypeConfiguration;
import org.goplanit.osm.tags.OsmWaterModeTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.NoSettingsKey;

import java.util.*;

/**
 * Settings specific to waterways when parsing OSM files and converting them to a PLANit memory model. The keys a
 * settings file can set are on {@link OsmWaterwayYamlKeyedSettings}.
 * <p>
 * Ferries are tagged in OSM as a ferry route, or as a highway type carried by a ferry, such as ferry=primary. These
 * settings say which of those are parsed, how the OSM water mode ferry maps to a PLANit mode, and what to assume where
 * a ferry's tags give no speed limit or capacity
 * </p>
 * 
 * @author markr
 *
 */
public class OsmWaterwaySettings extends OsmWaterwayYamlKeyedSettings {

  /** immutable default water mode mappings used to initialise each instance */
  private static final Map<String, PredefinedModeType> DEFAULT_OSM_WATER_MODE_MAPPINGS =
      Collections.unmodifiableMap(createDefaultOsmWaterModeMappings());

  /**
   * Create the immutable default OSM water mode mappings used to initialise each instance.
   *
   * @return immutable default mapping catalogue
   */
  private static Map<String, PredefinedModeType> createDefaultOsmWaterModeMappings() {
    Map<String, PredefinedModeType> defaultMappings = new LinkedHashMap<>();
    defaultMappings.put(OsmWaterModeTags.FERRY, PredefinedModeType.FERRY);
    return defaultMappings;
  }

  /**
   * Initialise this instance with the default OSM water mode mappings.
   */
  protected void initialiseOsmWaterModeMappings() {
    setOsmMode2PlanitPredefinedModeTypeMappings(DEFAULT_OSM_WATER_MODE_MAPPINGS);
  }

  /**
   * Initialise the water modes that are activated by default on this instance.
   */
  protected void initialiseActivatedOsmWaterModes() {
    activateOsmModes(DEFAULT_OSM_WATER_MODE_MAPPINGS.keySet());
  }

  /** by default the ferry parser is deactivated */
  public static final boolean DEFAULT_WATERWAYS_PARSER_ACTIVE = false;

  /**
   * Constructor
   *
   * @param waterwaySpeedLimitDefaults as they are initially provided
   * @param osmModeAccessWaterwayDefaults configuration
   */
  public OsmWaterwaySettings(
      OsmSpeedLimitDefaultsCategory waterwaySpeedLimitDefaults,
      OsmModeAccessDefaultsCategory osmModeAccessWaterwayDefaults) {
    super(new OsmWaterwayTypeConfiguration(), waterwaySpeedLimitDefaults, osmModeAccessWaterwayDefaults);
    initialiseOsmWaterModeMappings();
    initialiseActivatedOsmWaterModes();
    activateParser(DEFAULT_WATERWAYS_PARSER_ACTIVE);
  }

  /**
   * Verify if the passed in OSM waterway type is explicitly deactivated. Deactivated route types will be ignored
   * when processing ways.
   *
   * @param osmWaterWayValue, e.g. ferry (waterways are directly linked to modes) or a highway type (Assuming the
   *                          key was ferry, e.g. ferry=_a_highway_type_
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmWaterwayTypeDeactivated(final String osmWaterWayValue) {
      return isOsmWayTypeDeactivated(osmWaterWayValue);
  }

  /**
   * Verify if the passed in OSM waterway type is explicitly activated. Activated types will be processed
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. ferry (waterways are directly linked to modes) or a highway type (Assuming the key was
   *                     ferry, e.g. ferry=_a_highway_type_
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmWaterwayTypeActivated(String osmWayValue) {
    return isOsmWayTypeActivated(osmWayValue);
  }


  /** deactivate all types for waterway except the ones provides
   *
   * @param osmWaterwayTypes to not deactivate
   */
  @CoveredBySettingsKey("types")
  public void deactivateAllOsmWaterwayTypesExcept(String... osmWaterwayTypes) {
    deactivateAllOsmWaterwayTypesExcept(Arrays.asList(osmWaterwayTypes));
  }

  /** activate all passed in waterway types
   * @param osmWaterwayValueTypes to activate
   */
  @CoveredBySettingsKey("types+")
  public void activateOsmWaterwayTypes(String... osmWaterwayValueTypes) {
    activateOsmWayTypes(osmWaterwayValueTypes);
  }

  /** activate all passed in waterway types
   * @param osmWaterwayValueTypes to activate
   */
  @CoveredBySettingsKey("types+")
  public void activateOsmWaterwayTypes(List<String> osmWaterwayValueTypes) {
    activateOsmWayTypes(osmWaterwayValueTypes);
  }

  /**
   * activate all known OSM waterway types
   */
  @CoveredBySettingsKey("types")
  public void activateAllOsmWaterwayTypes() {
    activateAllOsmWayTypes();
  }

  /**
   * deactivate all types for waterways
   */
  @CoveredBySettingsKey("types")
  public void deactivateAllOsmWaterwayTypes() {
    deactivateAllOsmWayTypes();
  }

  /**
   * Log all de-activated OSM waterway types
   */
  @NoSettingsKey(reason = "logs the OSM way types not parsed")
  public void logUnsupportedOsmWaterwayTypes() {
    logUnsupportedOsmWayTypes();
  }

  /* overwrite */
  
  /**
   * Choose to overwrite the given waterway route type defaults with the given values
   * 
   * @param osmWaterwayType the type to set these values for
   * @param capacityPcuPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPcuPerLane new value pcu/km/lane
   */
  @CoveredBySettingsKey("capacityMaxDensity")
  public void overwriteCapacityMaxDensityDefaults(
      String osmWaterwayType, Number capacityPcuPerLanePerHour, Number maxDensityPcuPerLane) {
    overwriteCapacityMaxDensityDefaults(osmWaterwayType, new OsmWayTypeCapacityMaxDensity(
        capacityPcuPerLanePerHour.doubleValue(), maxDensityPcuPerLane.doubleValue()));
  }
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmWayType to check
   * @return true when new defaults are provided, false otherwise
   */
  public boolean isDefaultCapacityOrMaxDensityOverwrittenByOsmWaterwayRouteType(final String osmWayType) {
    return isDefaultCapacityOrMaxDensityOverwrittenByOsmWayType(osmWayType);
  }  
  
  /**
   * Collect the overwritten type values that should be used
   * 
   * @param osmWayRouteType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmWaterwayRouteType(String osmWayRouteType) {
    return getOverwrittenCapacityMaxDensityByOsmWayType(osmWayRouteType);
  }  
    
  /* speed limit */

  /** Collect the default speed limit for waterways
   *
   * @param waterwayValue value to use
   * @return speedLimit in km/h 
   */  
  public Double getDefaultSpeedLimitByOsmWaterwayType(String waterwayValue){
    return getDefaultSpeedLimitByOsmTypeValue(OsmWaterwayTags.getKeyForValueType(waterwayValue), waterwayValue);
  }   
  
  /* mode */

  /** remove all water modes from mapping except for the passed in ones
   *
   * @param remainingOsmWaterModes to explicitly keep if present
   */
  @CoveredBySettingsKey("modes")
  public void deactivateAllOsmWaterModesExcept(final String... remainingOsmWaterModes) {
    deactivateAllOsmWaterModesExcept(Arrays.asList(remainingOsmWaterModes));
  }
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode type for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public PredefinedModeType getMappedPlanitWaterMode(final String osmMode) {
    if(OsmWaterModeTags.isWaterModeTag(osmMode)) {
      return getPlanitModeTypeIfActivated(osmMode);
    }
    return null;
  }  
  
  /** Convenience method that collects the currently mapped OSM water modes for the given PLANit mode
   * 
   * @param planitModeType to collect mapped mode for (if any)
   * @return mapped OSM modes, if not available empty collection is returned
   */  
  public final Collection<String> getMappedOsmWaterModes(final PredefinedModeType planitModeType) {
    return getActivatedOsmModes(planitModeType);
  }   
    
  /**
   * Collect all OSM modes that are allowed for the given OSM waterway type as configured by the user. Note we allow
   * tagging values related to the key route=_mode_, e.g., ferry, as well as the de-facto standard where the 'ferry' is
   * used as keyword and the way type reflects the equivalent of highway options, e.g., trunk, as in ferry=trunk.
   * 
   * @param osmWaterwayType to use
   * @return allowed OsmModes found, empty if none
   */
  public Collection<String> collectAllowedOsmWaterwayModes(String osmWaterwayType) {
    if(!OsmWaterwayTags.hasKeyForValueType(osmWaterwayType)){
      return Collections.emptyList();
    }

    return collectAllowedOsmWayModes(
        OsmWaterwayTags.getKeyForValueType(
                osmWaterwayType), osmWaterwayType, OsmWaterModeTags.getSupportedWaterModeTags());
  }
}


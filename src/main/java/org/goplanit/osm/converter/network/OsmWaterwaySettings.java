package org.goplanit.osm.converter.network;

import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.defaults.OsmWaterwayTypeConfiguration;
import org.goplanit.osm.tags.OsmWaterModeTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;

import java.util.*;
import java.util.logging.Logger;

/**
 * Settings specific to waterways when parsing OSM files and converting them to a PLANit memory model
 * 
 * @author markr
 *
 */
public class OsmWaterwaySettings extends OsmWaySettings {

  private static final Logger LOGGER = Logger.getLogger(OsmWaterwaySettings.class.getCanonicalName());

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
  public static boolean DEFAULT_WATERWAYS_PARSER_ACTIVE = false;

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


  /**
   * Choose to not parse the given waterway type, e.g. ferry=primary.
   *
   * @param osmWayValue to use
   */
  public void deactivateOsmWaterwayType(String osmWayValue) {
    deactivateOsmWayType(osmWayValue);
  }

  /** deactivate all types for railway except the ones provides
   *
   * @param osmWaterwayTypes to not deactivate
   */
  public void deactivateAllOsmWaterwayTypesExcept(String... osmWaterwayTypes) {
    deactivateAllOsmWaterwayTypesExcept(Arrays.asList(osmWaterwayTypes));
  }

  /** deactivate all types for waterway except the ones provides
   *
   * @param osmWaterwayTypes to not deactivate
   */
  public void deactivateAllOsmWaterwayTypesExcept(List<String> osmWaterwayTypes) {
    deactivateAllOsmWaterwayTypes();
    for(String osmWayType : osmWaterwayTypes) {
      if(OsmWaterwayTags.hasKeyForValueType(osmWayType) &&
          OsmWaterwayTags.isWaterBasedWay(OsmWaterwayTags.getKeyForValueType(osmWayType), osmWayType)) {
        activateOsmWaterwayType(osmWayType);
      }
    }
  }

  /**
   * Choose to add given waterway type to parsed types on top of the defaults, e.g. ferry=primary.
   *
   * @param osmWayValue to use
   */
  public void activateOsmWaterwayType(String osmWayValue) {
    activateOsmWayType(osmWayValue);
  }

  /** activate all passed in waterway types
   * @param osmWaterwayValueTypes to activate
   */
  public void activateOsmWaterwayTypes(String... osmWaterwayValueTypes) {
    activateOsmWayTypes(osmWaterwayValueTypes);
  }

  /** activate all passed in waterway types
   * @param osmWaterwayValueTypes to activate
   */
  public void activateOsmWaterwayTypes(List<String> osmWaterwayValueTypes) {
    activateOsmWayTypes(osmWaterwayValueTypes);
  }

  /**
   * activate all known OSM waterway types
   */
  public void activateAllOsmWaterwayTypes() {
    activateAllOsmWayTypes();
  }

  /**
   * deactivate all types for waterways
   */
  public void deactivateAllOsmWaterwayTypes() {
    deactivateAllOsmWayTypes();
  }

  /**
   * Log all de-activated OSM waterway types
   */
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
  public void overwriteCapacityMaxDensityDefaults(
      String osmWaterwayType, Number capacityPcuPerLanePerHour, Number maxDensityPcuPerLane) {
    String keyForType = OsmWaterwayTags.getKeyForValueType(osmWaterwayType);
    if(keyForType == null){
      LOGGER.warning(String.format("IGNORE: Unsupported waterway type %s encountered, unable to overwrite " +
          "capacity.max density", osmWaterwayType));
    }
    overwriteOsmWayTypeDefaultCapacityMaxDensity(
        keyForType, osmWaterwayType, capacityPcuPerLanePerHour.doubleValue(), maxDensityPcuPerLane.doubleValue());
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
  
  /** activate an OSM water way mode based on its (default) mapping to a PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmWaterMode to activate
   */
  public void activateOsmWaterMode(String osmWaterMode) {
    if(!OsmWaterModeTags.isWaterModeTag(osmWaterMode)) {
      LOGGER.warning(String.format("OSM water based mode %s is not recognised when adding it to OSM to PLANit mode " +
              "mapping, ignored", osmWaterMode));
      return;
    }
    activateOsmMode(osmWaterMode);
  }   
  
  /** Remove a mapping from OSM water mode to PLANit mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added, either manually or through the default mapping
   * 
   * @param osmWaterMode to remove
   */
  public void deactivateOsmWaterMode(String osmWaterMode) {
    if(!OsmWaterModeTags.isWaterModeTag(osmWaterMode)) {
      LOGGER.warning(String.format("OSM water mode %s is not recognised when removing it from OSM to PLANit mode " +
              "mapping, ignored", osmWaterMode));
      return;
    }
    deactivateOsmMode(osmWaterMode);
  }
  
  /** remove a mapping from OSM water modes to PLANit modes. This means that the osmModes will not be added to the PLANit network
   * You can only remove modes when they are already added, either manually or through the default mapping
   * 
   * @param osmWaterModes to remove
   */
  public void deactivateOsmWaterModes(final List<String> osmWaterModes) {
    if(osmWaterModes == null) {
      return;
    }
    osmWaterModes.forEach(this::deactivateOsmWaterMode);
  }   
  
  /** deactivate provided water modes
   * 
   * @param osmWaterModes to explicitly deactivate
   */
  public void deactivateOsmWaterModes(final String... osmWaterModes) {
    deactivateOsmWaterModes(Arrays.asList(osmWaterModes));
  }

  /** remove all water modes from mapping
   * 
   */
  public void deactivateAllOsmWaterModes() {
    deactivateOsmModes(OsmWaterModeTags.getSupportedWaterModeTags());
  }    
  
  /** remove all water modes from mapping except for the passed in ones
   * 
   * @param remainingOsmWaterModes to explicitly keep if present
   */
  public void deactivateAllOsmWaterModesExcept(final String... remainingOsmWaterModes) {
    deactivateAllOsmWaterModesExcept(Arrays.asList(remainingOsmWaterModes));
  } 
  
  /** remove all water modes from mapping except for the passed in ones
   * 
   * @param remainingOsmWaterModes to explicitly keep if present
   */
  public void deactivateAllOsmWaterModesExcept(final List<String> remainingOsmWaterModes) {
    Collection<String> toBeRemovedModes = OsmWaterModeTags.getSupportedWaterModeTags();
    deactivateAllModesExcept(toBeRemovedModes, remainingOsmWaterModes);
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
    return getAcivatedOsmModes(planitModeType);
  }   
    
  /**
   * Collect all OSM modes that are allowed for the given OSM waterway type as configured by the user. Note we allow
   * tagging values related to the key route=_mode_, e.g., ferry, as well as the de-facto standard where the 'ferry' is used
   * as keyword and the way type reflects the equivalent of highway options, e.g., trunk, as in ferry=trunk.
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue("Waterway parser activated", isParserActive(), level));
    logModeMappings(level + 1);
  }
}


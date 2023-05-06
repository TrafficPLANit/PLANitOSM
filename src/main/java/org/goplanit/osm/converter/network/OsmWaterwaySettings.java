package org.goplanit.osm.converter.network;

import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.defaults.OsmWaterwayTypeConfiguration;
import org.goplanit.osm.tags.OsmWaterModeTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
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

  /**
   * each OSM water based mode is mapped (or not) to a PLANit mode by default so that the memory model's modes
   * are user configurable yet linked to the original format. Note that when the reader is used
   * i.c.w. a network writer to convert one network to the other. It is paramount that the PLANit modes
   * that are mapped here are also mapped by the writer to the output format to ensure a correct I/O mapping of modes
   *
   * The default mapping is provided below. In contrast to road modes, rail modes do not have specific restrictions. Hence, we can
   * map more exotic OSM rail modes to more common PLANit rail modes, without imposing its restrictions on this common mode.
   *
   * <ul>
   * <li>FERRY      to FerryMode       </li>
   * </ul>
   */
  protected void initialiseDefaultMappingFromOsmWaterModes2PlanitModes(){

    /* add default mapping */
    {
      addDefaultOsmMode2PlanitPredefinedModeTypeMapping(OsmWaterModeTags.FERRY, PredefinedModeType.FERRY);

      /* activate all defaults */
      activateOsmMode(OsmWaterModeTags.FERRY);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Collection<String> collectAllowedOsmWayModes(String osmValueType) {
    Set<String> allowedModes = null;
    if(OsmWaterModeTags.isWaterModeTag(osmValueType)) {
      allowedModes = collectAllowedOsmWayModes(osmValueType, OsmWaterModeTags.getSupportedWaterModeTags());
    }else {
      LOGGER.warning(String.format("unrecognised OSM waterway mode %s, no allowed modes can be identified", osmValueType));
    }
    return allowedModes;
  }

  /** by default the railway parser is deactivated */
  public static boolean DEFAULT_WATERWAYS_PARSER_ACTIVE = false;

  /**
   * Constructor
   *
   * @param waterwaySpeedLimitDefaults as they are initially provided
   * @param osmModeAccessWaterwayDefaults configuration
   */
  public OsmWaterwaySettings(OsmSpeedLimitDefaultsCategory waterwaySpeedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessWaterwayDefaults) {
    super(new OsmWaterwayTypeConfiguration(), waterwaySpeedLimitDefaults, osmModeAccessWaterwayDefaults);
    activateParser(DEFAULT_WATERWAYS_PARSER_ACTIVE);
  }
  
  /**
   * Verify if the passed in OSM water way route type is explicitly deactivated. Deactivated route types will be ignored
   * when processing ways.
   *
   * @param osmWaterWayRouteValue, e.g. ferry
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isOsmWaterwayRouteTypeDeactivated(final String osmWaterWayRouteValue) {
      return isOsmWaterwayRouteTypeDeactivated(osmWaterWayRouteValue);
  }

  /**
   * Verify if the passed in OSM water way route type is explicitly activated. Activated types will be processed
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. ferry (water ways are directly linked to modes)
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isOsmWaterwayRouteTypeActivated(String osmWayValue) {
    return isOsmWayTypeActivated(osmWayValue);
  }  

  /* overwrite */
  
  /**
   * Choose to overwrite the given water way route type defaults with the given values
   * 
   * @param osmWaterwayRouteType the type to set these values for
   * @param capacityPcuPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPcuPerLane new value pcu/km/lane
   */
  public void overwriteCapacityMaxDensityDefaults(String osmWaterwayRouteType, Number capacityPcuPerLanePerHour, Number maxDensityPcuPerLane) {
    overwriteOsmWayTypeDefaultCapacityMaxDensity(OsmWaterwayTags.getWaterwayKeyTag(), osmWaterwayRouteType, capacityPcuPerLanePerHour.doubleValue(), maxDensityPcuPerLane.doubleValue());
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
   * collect the overwritten type values that should be used
   * 
   * @param osmWayRouteType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  public final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmWaterwayRouteType(String osmWayRouteType) {
    return getOverwrittenCapacityMaxDensityByOsmWayType(osmWayRouteType);
  }  
    
  /* speed limit */
  
  /** Collect the speed limit for a given railway tag value, e.g. railway=typeValue, based on the defaults provided (typically set by country)
   * 
   * @param osmWayRouteValue way value type to collect default speed limit for
   * @return speedLimit in km/h
   */
  public double getDefaultSpeedLimitByOsmWaterwayRouteType(String osmWayRouteValue){
    return getDefaultSpeedLimitByOsmTypeValue(osmWayRouteValue);
  }  
  
  /** Collect the default speed limit for given water way tags, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (waterway keys currently supported)
   * @return speedLimit in km/h 
   */  
  public Double getDefaultSpeedLimitByOsmWaterwayRouteType(Map<String, String> tags){
    return getDefaultSpeedLimitByOsmWayType(OsmWaterwayTags.getWaterwayKeyTag(), tags);
  }   
  
  /* mode */
  
  /** activate an OSM water way mode based on its (default) mapping to a PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmWaterMode to activate
   */
  public void activateOsmWaterMode(String osmWaterMode) {
    if(!OsmWaterModeTags.isWaterModeTag(osmWaterMode)) {
      LOGGER.warning(String.format("OSM water based mode %s is not recognised when adding it to OSM to PLANit mode mapping, ignored", osmWaterMode));
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
      LOGGER.warning(String.format("OSM water mode %s is not recognised when removing it from OSM to PLANit mode mapping, ignored", osmWaterMode));
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
    osmWaterModes.forEach( osmRailMode -> deactivateOsmWaterMode(osmRailMode));
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
  public void deactivateAllWaterModes() {
    deactivateOsmModes(OsmWaterModeTags.getSupportedWaterModeTags());
  }    
  
  /** remove all water modes from mapping except for the passed in ones
   * 
   * @param remainingOsmWaterModes to explicitly keep if present
   */
  public void deactivateAllWaterModesExcept(final String... remainingOsmWaterModes) {
    deactivateAllWaterModesExcept(Arrays.asList(remainingOsmWaterModes));
  } 
  
  /** remove all water modes from mapping except for the passed in ones
   * 
   * @param remainingOsmWaterModes to explicitly keep if present
   */
  public void deactivateAllWaterModesExcept(final List<String> remainingOsmWaterModes) {
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
   * Collect all OSM modes that are allowed for the given osmRailway type as configured by the user
   * 
   * @param osmWaterwayRouteValueType to use
   * @return allowed OsmModes found
   */
  public Collection<String> collectAllowedOsmWaterwayModes(String osmWaterwayRouteValueType) {
    return collectAllowedOsmWayModes(osmWaterwayRouteValueType);
  }
    
}

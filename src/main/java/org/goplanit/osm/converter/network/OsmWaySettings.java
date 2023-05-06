package org.goplanit.osm.converter.network;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.osm.defaults.OsmInfrastructureConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * Base class with shared settings across different types of Osm ways (highway, railway)
 * 
 * @author markr
 *
 */
public abstract class OsmWaySettings {

  private static final Logger LOGGER = Logger.getLogger(OsmWaySettings.class.getCanonicalName());
  
  /* defaults config */

  /**
   * speed limit defaults for the ways represented by these settings
   */
  private OsmSpeedLimitDefaultsCategory speedLimitDefaults; 
  
  /**
   * mode access defaults for the ways represented by these settings
   */
  private OsmModeAccessDefaultsCategory osmModeAccessDefaults;
  
  /**
   * Configuration options regarding the activation/deactivation of specific OSM way types in the parser
   */
  private final OsmInfrastructureConfiguration infrastructureTypeConfiguration;
  
  /* mode mapping */
    
  /** mapping from each supported OSM mode to a PLANit predefined mode type on this instance */
  private  final Map<String, PredefinedModeType> activatedOsmMode2PlanitModeTypeMap = new HashMap<>();
  
  /** Default mapping (specific to this network) from each supported OSM mode to an available PLANit mode. Can be used
   * to re-activate OSM modes if needed */
  //private  final Map<String, Mode> defaultOsmMode2PlanitModeMap= new HashMap<>();

  /** Default mapping (specific to this network) from each supported OSM mode to an available PLANit (predefined mode type. Can be used
   * to re-activate OSM modes if needed */
  private  final Map<String, PredefinedModeType> defaultOsmMode2PlanitPredefinedModeTypeMap= new HashMap<>();
  
  /* overwriting of defaults */
  
  /**
   * track overwrite values for OSM way types where we want different defaults for capacity and max density
   */
  protected final Map<String, Pair<Double,Double>> overwriteOsmWayTypeCapacityDensityDefaults = new HashMap<>();
    
  /* other */

  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private Boolean isParserActive = null;
  
  /* protected */

  /**
   * explicitly exclude all osmWay type:value in case none of the passed in osmModes is marked as mapped
   * 
   * @param osmWayValue to check
   * @param osmModes of which at least one should be active on the key:value pair
   */
  private void excludeOsmWayTypesWithoutModes(String osmWayValue, Collection<String> osmModes) {
    
    boolean hasMappedMode = false;
    if(osmModes != null) {
      for(String osmMode : osmModes) {
        if(isOsmModeActivated(osmMode)) {
          hasMappedMode = true;
          break;
        }
      } 
    }
    
    if(!hasMappedMode) {
      deactivateOsmWayType(osmWayValue);      
    } 
  }    
  
  
  /** Constructor
   * 
   * @param infrastructureTypeConfiguration to use
   * @param speedLimitDefaults to use
   * @param osmModeAccessDefaults to use
   */
  protected OsmWaySettings(OsmInfrastructureConfiguration infrastructureTypeConfiguration, OsmSpeedLimitDefaultsCategory speedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessDefaults) {
    this.infrastructureTypeConfiguration = infrastructureTypeConfiguration;
    this.speedLimitDefaults = speedLimitDefaults;
    this.osmModeAccessDefaults = osmModeAccessDefaults;
  }

  /**
   * explicitly exclude all osmWay types that are included but have no more activated modes due to deactivation of their default assigned modes.
   * Doing so avoids the reader to log warnings that supported way types cannot be injected in the network because they
   * have no viable modes attached
   * 
   */
  public void excludeOsmWayTypesWithoutActivatedModes() {
    Set<String> originallySupportedTypes = getSetOfActivatedOsmWayLikeTypes();
    if(originallySupportedTypes != null) {
      for(String supportedWayType : originallySupportedTypes) {
        Collection<String> allowedOsmModes = collectAllowedOsmWayModes(supportedWayType);
        excludeOsmWayTypesWithoutModes(supportedWayType, allowedOsmModes);      
      }
    }
  }    
    
  /* way types */
  
  /**
   * Verify if the passed in OSM way type is explicitly deactivated. Deactivated types will be ignored
   * when processing ways.
   * 
   * @param osmWayValueType railway value type of representing way key (railway, highway, etc)
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  protected boolean isOsmWayTypeDeactivated(final String osmWayValueType) {
      return !isOsmWayTypeActivated(osmWayValueType);
  }
    
  /**
   * Verify if the passed in OSM way value type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  protected boolean isOsmWayTypeActivated(String osmWayValue) {
    return isParserActive() && infrastructureTypeConfiguration.isActivated(osmWayValue);
  }  
  
  /**
   * Choose to not parse the given way valuetype, e.g. railway=rail, highway=primary, etc.
   * 
   * @param osmWayValue to use
   */
  protected void deactivateOsmWayType(String osmWayValue) {
    infrastructureTypeConfiguration.deactivate(osmWayValue);      
  } 
  
  /**
   * Choose to add given way value type to parsed types on top of the defaults, e.g. railway=rail, highway=primary, etc.
   * Activates parser implicitly as well as it is assumed any activation of a type requires parsing it
   * 
   * @param osmWayValue to use
   */
  protected void activateOsmWayType(String osmWayValue) {
    infrastructureTypeConfiguration.activate(osmWayValue);
    activateParser(true);
  }  
  
  /** Activate all passed in way types
   * 
   * @param osmWayValueTypes to activate
   */
  protected void activateOsmWayTypes(String... osmWayValueTypes) {
    activateOsmWayTypes(Arrays.asList(osmWayValueTypes));
  }   
  
  /** Activate all passed in way types
   * 
   * @param osmWayValueTypes to activate
   */
  protected void activateOsmWayTypes(List<String> osmWayValueTypes) {
    osmWayValueTypes.forEach(type -> activateOsmWayType(type));
  }    
  
  /**
   * Activate all known OSM railway types 
   */
  protected void activateAllOsmWayTypes() {
    infrastructureTypeConfiguration.setOfDeactivatedTypes().forEach( unsupportedType -> activateOsmWayType(unsupportedType));
    activateParser(true);
  }   
  
  /**
   * Deactivate all types for the infrastructure type we represent.
   * Also deactivates the parser since if no types are activate, the parser should not parse anything
   */
  protected void deactivateAllOsmWayTypes() {
    infrastructureTypeConfiguration.deactivateAll();
    activateParser(false);
  } 
  
  /* overwrite */
  
  /**
   * Log all de-activated OSM way types (irrespective of parser being active or not)
   */  
  protected void logUnsupportedOsmWayTypes() {
    infrastructureTypeConfiguration.logDeactivatedTypes();
  }
  
  /* overwrite */  

  /**
   * Choose to overwrite the given highway type defaults with the given values. Activates the parser implicitly since it is assumed
   * this type is to be parsed
   * 
   * @param osmWayKey the way key
   * @param osmWayType the value type to set these values for
   * @param capacityPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPerLane new value pcu/km/lane
   */
  protected void overwriteOsmWayTypeDefaultCapacityMaxDensity(String osmWayKey, String osmWayType, double capacityPerLanePerHour, double maxDensityPerLane) {
    if(!isOsmWayTypeActivated(osmWayType)) {
      activateOsmWayType(osmWayType);
    }
    overwriteOsmWayTypeCapacityDensityDefaults.put(osmWayType, Pair.of(capacityPerLanePerHour,maxDensityPerLane));
    LOGGER.info(String.format("Overwriting defaults for osm road type %s:%s to capacity: %.2f (pcu/h/lane), max density %.2f (pcu/km)",osmWayKey, osmWayType, capacityPerLanePerHour, maxDensityPerLane));
  }          
  
  /**
   * Check if defaults should be overwritten
   * 
   * @param osmWayType to check
   * @return true when new defaults are provided, false otherwise
   */
  protected boolean isDefaultCapacityOrMaxDensityOverwrittenByOsmWayType(String osmWayType) {
    return overwriteOsmWayTypeCapacityDensityDefaults.containsKey(osmWayType);
  }

  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmWayType to collect overwrite values for
   * @return the new values capacity (pcu/lane/h) and maxDensity (pcu/km/lane)
   */
  protected final Pair<Double,Double> getOverwrittenCapacityMaxDensityByOsmWayType(String osmWayType) {
    return overwriteOsmWayTypeCapacityDensityDefaults.get(osmWayType);
  }    
  
  /* speed limits */
    
  /** Collect the speed limit for a given railway tag value, e.g. railway=typeValue, based on the defaults provided (typically set by country)
   * 
   * @param osmWayValue way value type to collect default speed limit for
   * @return speedLimit in km/h
   */
  protected double getDefaultSpeedLimitByOsmTypeValue(String osmWayValue){
    return speedLimitDefaults.getSpeedLimit(osmWayValue);    
  }  
  
  /** Collect the default speed limit for a given way tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param osmWayKey that is considered valid and should be used to collect way type value
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h 
   */  
  protected Double getDefaultSpeedLimitByOsmWayType(String osmWayKey, Map<String, String> tags){
    if(tags.containsKey(osmWayKey)){
      return getDefaultSpeedLimitByOsmTypeValue(tags.get(osmWayKey));
    }else {
      throw new PlanItRunTimeException("No key %s contained in provided osmTags when collecting default speed limit by OsmRailwayType", osmWayKey);
    }    
  }   
  
  /* modes */
  
  /** add mapping from osm mode to PLANit mode (only predefined modes supported for now)
   * @param osmMode to map from
   * @param planitModeType mode to map to
   */
  protected void addDefaultOsmMode2PlanitPredefinedModeTypeMapping(String osmMode, PredefinedModeType planitModeType) {
    defaultOsmMode2PlanitPredefinedModeTypeMap.put(osmMode, planitModeType);
  } 
  
  /** Activate an OSM mode based on its default mapping to a PLANit mode
   * 
   * @param osmMode to map from
   */
  protected void activateOsmMode(String osmMode) {
    activatedOsmMode2PlanitModeTypeMap.put(osmMode, defaultOsmMode2PlanitPredefinedModeTypeMap.get(osmMode));
  }   
  
  /** Add/overwrite a mapping from OSM mode to PLANit mode type. This means that the osmMode will be added to the PLANit network once parsing commences
   * 
   * @param osmMode to set
   * @param planitModeType to map it to
   */
  protected void setOsmMode2PlanitModeTypeMapping(String osmMode, PredefinedModeType planitModeType) {
    if(osmMode == null) {
      LOGGER.warning("OSM mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode, ignored");
      return;
    }
    activatedOsmMode2PlanitModeTypeMap.put(osmMode, planitModeType);
  }   
  
  /** Deactivate an OSM mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added.
   * 
   * @param osmMode to remove
   */
  protected void deactivateOsmMode(String osmMode) {
    if(osmMode == null) {
      LOGGER.warning("OSM mode is null, cannot deactivate, ignored");
      return;
    }
    LOGGER.fine(String.format("OSM mode %s is deactivated", osmMode));
    
    var predefinedModeType = activatedOsmMode2PlanitModeTypeMap.remove(osmMode);
  }
  
  /**Remove all provided modes from mapping
   * 
   * @param osmModes to deactive
   */
  protected void deactivateOsmModes(Collection<String> osmModes) {
    for(String osmMode : osmModes) {
      deactivateOsmMode(osmMode);
    }
  } 
  
  /** remove all road modes from mapping except for the passed in ones
   * 
   * @param toBeRemovedModes remove all these modes, except...
   * @param remainingOsmRoadModes to explicitly keep from the osmModesToRemove
   */
  protected void deactivateAllModesExcept(final Collection<String> toBeRemovedModes, final List<String> remainingOsmRoadModes) {
    Collection<String> remainingRoadModes = remainingOsmRoadModes==null ? new ArrayList<>() : remainingOsmRoadModes;
    Collection<String> finalToBeRemovedModes = new TreeSet<>(toBeRemovedModes);
    finalToBeRemovedModes.removeAll(remainingRoadModes);
    deactivateOsmModes(finalToBeRemovedModes);
  }     
  
  /** Convenience method that collects the currently mapped PLANit mode for the given OSM mode if the parser is active
   * otherwise null is returned
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode type, if not available or parser is not active null is returned
   */
  protected PredefinedModeType getPlanitModeTypeIfActivated(final String osmMode) {
    // todo move this to reader as mode instances should not be part of the settings
    if(!isParserActive()) {
      return null;
    }
    return this.activatedOsmMode2PlanitModeTypeMap.get(osmMode);
  }  
  
  /** convenience method that collects the currently mapped OSM modes for the given PLANit mode
   * 
   * @param planitModeType to collect mapped OSM modes for this type (if any)
   * @return mapped osm modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */  
  protected Collection<String> getAcivatedOsmModes(final PredefinedModeType planitModeType) {
    Set<String> mappedOsmModes = new HashSet<>();
    if(!isParserActive()) {
      return mappedOsmModes;
    }
    
    for( var entry : activatedOsmMode2PlanitModeTypeMap.entrySet()) {
      if(entry.getValue().equals(planitModeType)) {
        mappedOsmModes.add(entry.getKey());
      }
    }
    return mappedOsmModes;
  }
    
  /** verify if a particular osm mode is allowed on the provided osm way type
   *  e.g. is train allowed on rail?
   *  
   * @param osmWayTypeValue to use
   * @param osmMode to check
   * @return true when allowed, falseo otherwise
   */
  protected boolean isModeAllowedOnOsmWay(String osmWayTypeValue, String osmMode) {
    return osmModeAccessDefaults.isAllowed(osmWayTypeValue, osmMode);
  }
  
  /** collect all allowed modes on the given OSM way
   * 
   * @param osmWayValueType to collect osm modes for
   * @return allowed OSM modes
   */
  protected abstract Collection<String> collectAllowedOsmWayModes(String osmWayValueType);
  
  /**
   * Collect all Osm modes from the passed in options that are allowed for the given osmWay type
   * 
   * @param osmWayValueType to use
   * @param osmModesToCheck modes to select from
   * @return allowed OsmModes found
   */
  protected Set<String> collectAllowedOsmWayModes(String osmWayValueType, Collection<String> osmModesToCheck) {
    return osmModesToCheck.stream().filter( osmMode -> isModeAllowedOnOsmWay(osmWayValueType, osmMode)).collect(Collectors.toSet());
  }  
  
  /** add allowed osm modes to osm way type
   * 
   * @param osmWayTypeValue to use
   * @param osmModes to allow
   */
  protected void addAllowedOsmWayModes(final String osmWayTypeValue, final List<String> osmModes) {
    osmModeAccessDefaults.addAllowedModes(osmWayTypeValue, osmModes);
  }       
  
  /* public */
  
  /** Determine whether or not the ways represented by these settings should be parsed or not. It has no impact
   * on the settings themselves, except that all queries related to whether or not modes or types are activated
   * will respond negatively when the parser is deactived, despite the underlying settings remaining in memory and
   * will be reinstated when the parser is re-activated.
   * 
   * @param activate parser when true, deactivate otherwise
   */
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }

  /** verifies if the parser for these settings is active or not
   * @return true when active, false otherwise
   */
  public boolean isParserActive() {
    return this.isParserActive;
  }
  
  /** Verify if the passed in osmMode is mapped to a mode, i.e., if it is actively included when reading the network. When
   * the parser is not active false is returned in all cases
   * 
   * @param osmMode to verify
   * @return true if mapped and parser is active, false otherwise
   */
  public boolean isOsmModeActivated(final String osmMode) {
    return isParserActive() && getPlanitModeTypeIfActivated(osmMode) != null;
  }  
  
  /** Verify if any mode other than the passed in OSM mode is active (in case the parser is active)
   * @param osmMode to check
   * @return true when other mapped mode is present (and parser is active), false otherwise
   */
  public boolean hasActivatedOsmModeOtherThan(final String osmMode) {
    return isParserActive() && this.activatedOsmMode2PlanitModeTypeMap.keySet().stream().filter(mode -> (!mode.equals(osmMode))).findFirst().isPresent();
  }   
    
  /** collect all activated types as a set (copy) in case the parser is active
   * 
   * @return set of currently activated osm way types (when parser is active), modifications to this set have no effect on configuration, null if not applicable
   */
  public final Set<String> getSetOfActivatedOsmWayLikeTypes(){
    return isParserActive() ? infrastructureTypeConfiguration.setOfActivatedTypes() : null;    
  }

  /**
   * Create a stream of  currently activated planit mode types
   *
   * @return activated PLANitModeTypes, i.e., they are mapped and activated
   */
  public Stream<PredefinedModeType> getActivatedPlanitModeTypesStream() {
    return activatedOsmMode2PlanitModeTypeMap.values().stream().distinct();
  }
}

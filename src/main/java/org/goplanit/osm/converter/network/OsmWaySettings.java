package org.goplanit.osm.converter.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.osm.defaults.OsmInfrastructureConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;

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
    
  /** mapping from each supported osm mode to a PLANit mode on this instance */
  private  final Map<String, Mode> activatedOsmMode2PlanitModeMap = new HashMap<String, Mode>();
  
  /** Default mapping (specific to this network) from each supported OSM mode to an available PLANit mode. Can be used
   * to re-activate OSM modes if needed */
  private  final Map<String, Mode> defaultOsmMode2PlanitModeMap= new HashMap<String, Mode>();  
  
  /* overwriting of defaults */
  
  /**
   * track overwrite values for OSM way types where we want different defaults for capacity and max density
   */
  protected final Map<String, Pair<Double,Double>> overwriteOsmWayTypeCapacityDensityDefaults = new HashMap<String, Pair<Double,Double>>();
    
  /* other */

  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private Boolean isParserActive = null;
  
  /* protected */
  
  /** add osmModeId to planit external id (in case multiple osm modes are mapped to the same planit mode)
   * @param planitMode to update external id for
   * @param osModeId to use
   */
  private static void addToModeExternalId(Mode planitMode, String osModeId){
    if(planitMode != null) {
      if(planitMode.hasExternalId()) {
        planitMode.setExternalId(planitMode.getExternalId().concat(";").concat(osModeId));
      }else {
        planitMode.setExternalId(osModeId);
      }
    }
  }
  
  /** Remove osmModeId to PLANit external id (in case multiple osm modes are mapped to the same PLANit mode)
   * 
   * @param planitMode to update external id for
   * @param osModeId to use
   */  
  private static void removeFromModeExternalId(Mode planitMode, String osModeId){
    if(planitMode!= null && planitMode.hasExternalId()) {
      int startIndex = planitMode.getExternalId().indexOf(osModeId);
      if(startIndex == -1) {
        /* not present */
        return;
      }
      if(startIndex==0) {
        /* first */
        planitMode.setExternalId(planitMode.getExternalId().substring(startIndex+osModeId.length()));
      }else {
        /* not first, so preceded by underscore "*_<name>" */
        String before = planitMode.getExternalId().substring(0,startIndex-1);
        String after = planitMode.getExternalId().substring(startIndex+osModeId.length());
        planitMode.setExternalId(before.concat(after));
      }
    }
  }  
  
  /**
   * explicitly exclude all osmWay type:value in case none of the passed in osmModes is marked as mapped
   * 
   * @param osmWayKey to check
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
  
  /** set osmModeId's to planit external id (accounting for the possibility multiple osm modes are mapped to the same planit mode).
   * We do this based on the existing mapping of osmModes to planitmodes
   */
  protected void setModeExternalIdsBasedOnMappedOsmModes(){
    activatedOsmMode2PlanitModeMap.forEach( (osmMode, planitMode) -> addToModeExternalId(planitMode, osmMode));
  }   
  
  /**
   * explicitly exclude all osmWay types that are included but have no more activated modes due to deactivation of their default assigned modes.
   * Doing so avoids the reader to log warnings that supported way types cannot be injected in the network because they
   * have no viable modes attached
   * 
   */
  public void excludeOsmWayTypesWithoutActivatedModes() {
    if(isParserActive()) {
      Set<String> originallySupportedTypes = getSetOfActivatedOsmWayTypes();
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
      return infrastructureTypeConfiguration.isDeactivated(osmWayValueType);
  }
    
  /**
   * Verify if the passed in OSM way value type is explicitly activated. Activated types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  protected boolean isOsmWayTypeActivated(String osmWayValue) {
    return infrastructureTypeConfiguration.isActivated(osmWayValue);
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
   * Choose to add given way  value type to parsed types on top of the defaults, e.g. railway=rail, highway=primary, etc.
   * 
   * @param osmWayValue to use
   */
  protected void activateOsmWayType(String osmWayValue) {
    infrastructureTypeConfiguration.activate(osmWayValue);
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
    infrastructureTypeConfiguration.activate(osmWayValueTypes);
  }    
  
  /**
   * Activate all known OSM railway types 
   */
  protected void activateAllOsmWayTypes() {
    infrastructureTypeConfiguration.setOfDeactivatedTypes().forEach( unsupportedType -> activateOsmWayType(unsupportedType));    
  }   
  
  /**
   * Deactivate all types for the infrastructure type we represent
   */
  protected void deactivateAllOsmWayTypes() {
    infrastructureTypeConfiguration.deactivateAll();
  } 
  
  /* overwrite */
  
  /**
   * Log all de-activated OSM way types
   */  
  protected void logUnsupportedOsmWayTypes() {
    infrastructureTypeConfiguration.logDeactivatedTypes();
  }
  
  /* overwrite */  

  /**
   * Choose to overwrite the given highway type defaults with the given values
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
    LOGGER.info(String.format("overwriting defaults for osm road type %s:%s to capacity: %.2f (pcu/h/lane), max density %.2f (pcu/km)",osmWayKey, osmWayType, capacityPerLanePerHour, maxDensityPerLane));
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
   * @throws PlanItException thrown if error
   */
  protected double getDefaultSpeedLimitByOsmWayType(String osmWayValue) throws PlanItException {
    return speedLimitDefaults.getSpeedLimit(osmWayValue);    
  }  
  
  /** Collect the default speed limit for a given way tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param osmWayKey that is considered valid and should be used to collect way type value
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h 
   * @throws PlanItException thrown if error
   */  
  protected Double getDefaultSpeedLimitByOsmWayType(String osmWayKey, Map<String, String> tags) throws PlanItException {
    if(tags.containsKey(osmWayKey)){
      return getDefaultSpeedLimitByOsmWayType(tags.get(osmWayKey));      
    }else {
      throw new PlanItException("no key %s contained in provided osmTags when collecting default speed limit by OsmRailwayType", osmWayKey);
    }    
  }   
  
  /* modes */
  
  /** add mapping from osm mode to PLANit mode
   * @param osmMode to map from
   * @param planitMode mode to map to
   */
  protected void addDefaultOsmMode2PlanitModeMapping(String osmMode, Mode planitMode) {
    defaultOsmMode2PlanitModeMap.put(osmMode, planitMode);
  } 
  
  /** activate an OSM mode based on its default mapping to a PLANit mode
   * @param osmMode to map from
   * @param planitMode mode to map to
   */
  protected void activateOsmMode(String osmMode) {
    activatedOsmMode2PlanitModeMap.put(osmMode, defaultOsmMode2PlanitModeMap.get(osmMode));
  }   
  
  /** Add/overwrite a mapping from OSM mode to PLANit mode. This means that the osmMode will be added to the PLANit network
   * 
   * @param osmMode to set
   * @param planitMode to map it to
   */
  protected void setOsmMode2PlanitModeMapping(String osmMode, Mode planitMode) {
    if(osmMode == null) {
      LOGGER.warning("osm mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode, ignored");
      return;
    }
    if(planitMode == null) {
      LOGGER.warning(String.format("planit mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode %s, ignored", osmMode));
      return;
    }
    activatedOsmMode2PlanitModeMap.put(osmMode, planitMode);
    addToModeExternalId(planitMode,osmMode);
  }   
  
  /** Deactivate an OSM mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added.
   * 
   * @param osmMode to remove
   */
  protected void deactivateOsmMode(String osmMode) {
    if(osmMode == null) {
      LOGGER.warning("osm mode is null, cannot add it to OSM to PLANit mode mapping for OSM mode, ignored");
      return;
    }
    LOGGER.fine(String.format("osm mode %s is deactivated", osmMode));
    
    Mode planitMode = activatedOsmMode2PlanitModeMap.remove(osmMode);
    removeFromModeExternalId(planitMode,osmMode);
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
    Collection<String> remainingRoadModes = remainingOsmRoadModes==null ? new ArrayList<String>() : remainingOsmRoadModes;
    Collection<String> finalToBeRemovedModes = new TreeSet<String>(toBeRemovedModes);
    finalToBeRemovedModes.removeAll(remainingRoadModes);
    deactivateOsmModes(finalToBeRemovedModes);
  }     
  
  /** convenience method that collects the currently mapped PLANit mode for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  protected Mode getPlanitModeIfActivated(final String osmMode) {
    return this.activatedOsmMode2PlanitModeMap.get(osmMode);
  }  
  
  /** convenience method that collects the currently mapped OSM modes for the given PLANit mode
   * 
   * @param planitMode to collect mapped mode for (if any)
   * @return mapped osm modes, if not available empty collection is returned
   */  
  protected Collection<String> getAcivatedOsmModes(final Mode planitMode) {
    Set<String> mappedOsmModes = new HashSet<String>();
    for( Entry<String, Mode> entry : activatedOsmMode2PlanitModeMap.entrySet()) {
      if(entry.getValue().idEquals(planitMode)) {
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
   * @return allowed osm modes
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
  
  /** set the flag whether or not the ways represented by these settings should be parsed or not
   * @param activate actate when true, deactivate otherwise
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
  
  /** Verify if the passed in osmMode is mapped to a mode, i.e., if it is actively included when reading the network
   * 
   * @param osmMode to verify
   * @return true if mapped, false otherwise
   */
  public boolean isOsmModeActivated(final String osmMode) {
    return getPlanitModeIfActivated(osmMode) != null;    
  }  
  
  /** Verify if any mode other than the passed in OSM mode is active
   * @param osmMode to check
   * @return true when other mapped mode is present false otherwise
   */
  public boolean hasActivatedOsmModeOtherThan(final String osmMode) {
    return this.activatedOsmMode2PlanitModeMap.keySet().stream().filter( mode -> (!mode.equals(osmMode))).findFirst().isPresent();
  }   
    
  /** collect all activated types as a set (copy)
   * 
   * @return set of currently activated osm way types, modifications to this set have no effect on configuration
   */
  public final Set<String> getSetOfActivatedOsmWayTypes(){
    return infrastructureTypeConfiguration.setOfActivatedTypes();    
  }    
  
}

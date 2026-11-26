package org.goplanit.osm.converter.network.settings;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.converter.utils.ExternalToPlanitModeMapping;
import org.goplanit.osm.defaults.OsmInfrastructureConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * Base class with shared settings across different types of Osm ways (highway, railway). The keys a settings file can
 * set for every family are on {@link OsmWayYamlKeyedSettings}.
 * <p>
 * Highways, railways and waterways are read the same way: which types are parsed, which OSM modes they carry and how
 * those map to PLANit modes, and what to assume where tags are missing, such as speed limits and capacity. This class
 * holds that once, so each family adds only what is its own, such as the urban speed limit switch for highways
 * </p>
 *
 * @author markr
 *
 */
public abstract class OsmWaySettings extends OsmWayYamlKeyedSettings {

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

  /** track mapping from OSM mode string to PLANit defined modes */
  private final ExternalToPlanitModeMapping osmModeMapping = new ExternalToPlanitModeMapping();

  /* overwriting of defaults */
  
  /**
   * track overwrite values for OSM way types where we want different defaults for capacity and max density
   */
  protected final Map<String, Pair<Double,Double>> overwriteOsmWayTypeCapacityDensityDefaults = new TreeMap<>();

  /* protected */

  /** Add a new default the speed limit for a currently unregistered key value way tag value, e.g. railway=typeValue.
   * Only to be used for custom tags that are added on-the-fly by the user, otherwise we should go through the
   * "normal" default initialisation procedure.
   *
   * @param osmWayKey key to use
   * @param osmWayValue way value type to collect default speed limit for
   * @param speedLimitKmH in km/h
   * @return true when added, false otherwise
   */
  private boolean addDefaultSpeedLimit(String osmWayKey, String osmWayValue, double speedLimitKmH){
    if(speedLimitDefaults.containsSpeedLimit(osmWayKey, osmWayValue)){
      LOGGER.warning("unable to add default speed limit because a default is already available, consider " +
              "overwriting the default via settings instead");
      return false;
    }
    speedLimitDefaults.setSpeedLimitDefault(osmWayKey, osmWayValue, speedLimitKmH);
    return true;
  }


  /** Constructor
   * 
   * @param infrastructureTypeConfiguration to use
   * @param speedLimitDefaults to use
   * @param osmModeAccessDefaults to use
   */
  protected OsmWaySettings(OsmInfrastructureConfiguration infrastructureTypeConfiguration,
                           OsmSpeedLimitDefaultsCategory speedLimitDefaults,
                           OsmModeAccessDefaultsCategory osmModeAccessDefaults) {
    this.infrastructureTypeConfiguration = infrastructureTypeConfiguration;
    this.speedLimitDefaults = speedLimitDefaults;
    this.osmModeAccessDefaults = osmModeAccessDefaults;
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
      return !isParserActive() || !isOsmWayTypeActivated(osmWayValueType);
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
   * Whether these settings know an OSM way type: one of the family's OSM tags, such as highway=primary, whether
   * parsed or not, or a type registered as new ({@code newOsmWayTypes})
   *
   * @param osmWayValue the type, such as primary
   * @return true when known
   */
  protected boolean isKnownOsmWayType(String osmWayValue) {
    return infrastructureTypeConfiguration.isActivated(osmWayValue) ||
        infrastructureTypeConfiguration.isDeactivated(osmWayValue);
  }

  /**
   * Refuse an OSM way type these settings do not know, see {@link #isKnownOsmWayType(String)}
   *
   * @param osmWayValue the type
   * @throws IllegalArgumentException naming the type, when it is not known
   */
  protected void throwIfUnknownOsmWayType(String osmWayValue) {
    ExceptionUtils.throwIllegalArgumentIf(!isKnownOsmWayType(osmWayValue), "Not a known OSM way type of this kind: %s",
        osmWayValue);
  }

  /**
   * Choose to not parse the given way valuetype, e.g. railway=rail, highway=primary, etc.
   *
   * @param osmWayValue to use
   */
  protected void deactivateOsmWayType(String osmWayValue) {
    throwIfUnknownOsmWayType(osmWayValue);
    infrastructureTypeConfiguration.deactivate(osmWayValue);
  }

  /**
   * Choose to add given way value type to parsed types on top of the defaults, e.g. railway=rail, highway=primary, etc.
   *
   * @param osmWayValue to use
   */
  protected void activateOsmWayType(String osmWayValue) {
    throwIfUnknownOsmWayType(osmWayValue);
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
    osmWayValueTypes.forEach(this::throwIfUnknownOsmWayType);
    osmWayValueTypes.forEach(this::activateOsmWayType);
  }

  /**
   * Parse only the OSM way types given, every one of them known, or none when one is not
   *
   * @param osmWayValueTypes to parse
   * @throws IllegalArgumentException naming the first type not known, the types left as they were
   */
  protected void replaceActivatedOsmWayTypes(List<String> osmWayValueTypes) {
    osmWayValueTypes.forEach(this::throwIfUnknownOsmWayType);
    deactivateAllOsmWayTypes();
    osmWayValueTypes.forEach(this::activateOsmWayType);
  }

  /**
   * The OSM way types parsed, whether the parser is active or not
   *
   * @return the types, such as primary, in alphabetical order
   */
  protected SortedSet<String> getActivatedOsmWayTypes() {
    return infrastructureTypeConfiguration.getActivatedTypes().values().stream().flatMap(Collection::stream)
        .collect(Collectors.toCollection(TreeSet::new));
  }
  
  /**
   * Activate all known OSM railway types 
   */
  protected void activateAllOsmWayTypes() {
    infrastructureTypeConfiguration.getDeactivatedTypes().values().stream().flatMap(Collection::stream).forEach(
            this::activateOsmWayType);
  }

  /**
   * Deactivate all types for the infrastructure type we represent.
   */
  protected void deactivateAllOsmWayTypes() {
    infrastructureTypeConfiguration.deactivateAll();
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
   * Choose to overwrite the given highway type defaults with the given values.
   * 
   * @param osmWayKey the way key
   * @param osmWayType the value type to set these values for
   * @param capacityPerLanePerHour new value in pcu/lane/h
   * @param maxDensityPerLane new value pcu/km/lane
   */
  protected void overwriteOsmWayTypeDefaultCapacityMaxDensity(
          String osmWayKey, String osmWayType, double capacityPerLanePerHour, double maxDensityPerLane) {
    overwriteOsmWayTypeCapacityDensityDefaults.put(osmWayType, Pair.of(capacityPerLanePerHour,maxDensityPerLane));
    LOGGER.info(String.format(
        "Custom defaults for OSM road type %s:%s to capacity: %.2f (pcu/h/lane), max density %.2f (pcu/km)",
        osmWayKey, osmWayType, capacityPerLanePerHour, maxDensityPerLane));
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

  /**
   * Overwrite the capacity and maximum density per lane of a known OSM way type, both given and above zero
   *
   * @param osmWayKey the way key, such as highway
   * @param osmWayType a type these settings know, see {@link #isKnownOsmWayType(String)}
   * @param capacityMaxDensity to use for the type
   * @throws IllegalArgumentException naming the type, storing nothing, when it is not known, or a value is left out or
   *     not above zero
   */
  protected void overwriteCapacityMaxDensityOfKnownType(
      String osmWayKey, String osmWayType, OsmWayTypeCapacityMaxDensity capacityMaxDensity) {
    throwIfUnknownOsmWayType(osmWayType);
    var capacity = capacityMaxDensity.getCapacityPerLanePcuH();
    var maxDensity = capacityMaxDensity.getMaxDensityPerLanePcuKm();
    ExceptionUtils.throwIllegalArgumentIf(capacity == null || maxDensity == null || capacity <= 0 || maxDensity <= 0,
        "Capacity and maximum density of OSM way type %s not both given and above zero: %s", osmWayType,
        capacityMaxDensity);
    overwriteOsmWayTypeDefaultCapacityMaxDensity(osmWayKey, osmWayType, capacity, maxDensity);
  }

  /**
   * The capacity and maximum density per lane overwritten, by OSM way type
   *
   * @return the values by type, in alphabetical order of the type
   */
  protected SortedMap<String, OsmWayTypeCapacityMaxDensity> getCapacityMaxDensityOverwrites() {
    var overwrites = new TreeMap<String, OsmWayTypeCapacityMaxDensity>();
    overwriteOsmWayTypeCapacityDensityDefaults.forEach((osmWayType, capacityMaxDensity) -> overwrites.put(
        osmWayType, new OsmWayTypeCapacityMaxDensity(capacityMaxDensity.first(), capacityMaxDensity.second())));
    return overwrites;
  }
  
  /* speed limits */
    
  /** Collect the speed limit for a given way tag value, e.g. railway=typeValue, based on the defaults provided
   * (typically set by country)
   *
   * @param osmWayKey key to use
   * @param osmWayValue way value type to collect default speed limit for
   * @return speedLimit in km/h
   */
  protected double getDefaultSpeedLimitByOsmTypeValue(String osmWayKey, String osmWayValue){
    return speedLimitDefaults.getSpeedLimit(osmWayKey, osmWayValue);
  }  
  
  /** Collect the default speed limit for a given way tag value, where we extract the key and value from the
   * passed in tags, if available
   * 
   * @param osmWayKey that is considered valid and should be used to collect way type value
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h 
   */  
  protected Double getDefaultSpeedLimitByOsmWayType(String osmWayKey, Map<String, String> tags){
    if(tags.containsKey(osmWayKey)){
      return getDefaultSpeedLimitByOsmTypeValue(osmWayKey, tags.get(osmWayKey));
    }else {
      throw new PlanItRunTimeException("No key %s contained in provided osmTags when collecting default speed limit",
          osmWayKey);
    }    
  }   
  
  /* modes */
  
  /** set mapping from OSM mode to PLANit mode (only predefined modes supported for now), these are not automatically
   * activated, that is done separately
   *
   * @param osmMode to map from
   * @param planitModeType mode to map to
   */
  protected void setOsmMode2PlanitPredefinedModeTypeMapping(String osmMode, PredefinedModeType planitModeType) {
    osmModeMapping.setDefaultMapping(osmMode, planitModeType);
  } 

  /**
   * Set mappings from OSM modes to PLANit predefined mode types in bulk.
   *
   * @param osmModeMappings mappings to set
   */
  protected void setOsmMode2PlanitPredefinedModeTypeMappings(Map<String, PredefinedModeType> osmModeMappings) {
    osmModeMappings.forEach(this::setOsmMode2PlanitPredefinedModeTypeMapping);
  }
  
  /**
   * Whether these settings know an OSM mode: one a PLANit mode is mapped to, whether activated or not, such as
   * motorcar for highways, or a mode mapped by the user
   *
   * @param osmMode the mode
   * @return true when known
   */
  protected boolean isKnownOsmMode(String osmMode) {
    return osmMode != null && osmModeMapping.hasMapping(osmMode);
  }

  /**
   * Refuse an OSM mode these settings do not know, see {@link #isKnownOsmMode(String)}
   *
   * @param osmMode the mode
   * @throws IllegalArgumentException naming the mode, when it is not known
   */
  protected void throwIfUnknownOsmMode(String osmMode) {
    ExceptionUtils.throwIllegalArgumentIf(!isKnownOsmMode(osmMode),
        "Not a known OSM mode of this kind, no PLANit mode is mapped to it: %s", osmMode);
  }

  /** Activate an OSM mode based on its default mapping to a PLANit mode
   *
   * @param osmMode to map from
   */
  protected void activateOsmMode(String osmMode) {
    throwIfUnknownOsmMode(osmMode);
    osmModeMapping.activate(osmMode);
  }

  /**
   * Activate the provided OSM modes.
   *
   * @param osmModes to activate
   */
  protected void activateOsmModes(Collection<String> osmModes) {
    osmModes.forEach(this::activateOsmMode);
  }
  
  /** Deactivate an OSM mode. This means that the osmMode will not be added to the PLANit network
   * You can only remove a mode when it is already added.
   * 
   * @param osmMode to remove
   */
  protected void deactivateOsmMode(String osmMode) {
    throwIfUnknownOsmMode(osmMode);
    osmModeMapping.deactivate(osmMode);
  }

  /**
   * Parse only the OSM modes given, switching on those that were off, every one of them known, or none when one is
   * not
   *
   * @param osmModes to parse
   * @throws IllegalArgumentException naming the first mode not known, the modes left as they were
   */
  protected void replaceActivatedOsmModes(List<String> osmModes) {
    osmModes.forEach(this::throwIfUnknownOsmMode);
    osmModeMapping.deactivateAll();
    osmModes.forEach(osmModeMapping::activate);
  }

  /**
   * The OSM modes activated, whether the parser is active or not
   *
   * @return the modes, in alphabetical order
   */
  protected SortedSet<String> getActivatedOsmModesIgnoringParser() {
    return new TreeSet<>(osmModeMapping.getActiveExternalModes());
  }

  /**
   * Map an OSM mode to a PLANit mode, used once the mode is activated, replacing the mapping it had. The mode is not
   * switched on or off; a mode that is on maps to the new PLANit mode at once
   *
   * @param osmMode to map, a mode of the family
   * @param planitModeType to map it to
   * @param isModeOfFamily whether an OSM mode is one of the family, such as a road mode for highways
   * @throws IllegalArgumentException naming the mode, when it is not of the family or no PLANit mode is given
   */
  protected void mapOsmMode(String osmMode, PredefinedModeType planitModeType, Predicate<String> isModeOfFamily) {
    ExceptionUtils.throwIllegalArgumentIf(osmMode == null || !isModeOfFamily.test(osmMode) || planitModeType == null,
        "OSM mode %s cannot be mapped to PLANit mode %s here", osmMode, planitModeType);
    var activated = osmModeMapping.isMapped(osmMode);
    osmModeMapping.setDefaultMapping(osmMode, planitModeType);
    if (activated) {
      osmModeMapping.activate(osmMode);
    }
  }

  /**
   * The PLANit mode each OSM mode maps to once activated, whether activated or not
   *
   * @return the mappings by OSM mode, in alphabetical order, unmodifiable
   */
  protected SortedMap<String, PredefinedModeType> getOsmModeMappings() {
    return osmModeMapping.getDefaultMappings();
  }
  
  /** Convenience method that collects the currently mapped PLANit mode for the given OSM mode if the parser is active
   * otherwise null is returned
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode type, if not available or parser is not active null is returned
   */
  protected PredefinedModeType getPlanitModeTypeIfActivated(final String osmMode) {
    // todo move this to reader as mode instances should not be part of the settings
    return isParserActive() ? osmModeMapping.getMappedMode(osmMode) : null;
  }
  
  /** Convenience method that collects the currently mapped OSM modes for the given PLANit mode
   * 
   * @param planitModeType to collect mapped OSM modes for this type (if any)
   * @return mapped osm modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */  
  protected Set<String> getActivatedOsmModes(final PredefinedModeType planitModeType) {
    return isParserActive() ? osmModeMapping.getActiveExternalModes(planitModeType) : new TreeSet<>();
  }

  /** Verify if a particular osm mode is allowed on the provided osm way type
   *  e.g., is train allowed on rail?
   *
   * @param osmWayKey to use
   * @param osmWayTypeValue to use
   * @param osmMode to check
   * @return true when allowed, false, otherwise
   */
  protected boolean isModeAllowedOnOsmWay(String osmWayKey, String osmWayTypeValue, String osmMode) {
    return osmModeAccessDefaults.isAllowed(osmWayKey, osmWayTypeValue, osmMode);
  }

  /**
   * Collect all Osm modes from the passed in options that are allowed for the given osmWay type
   *
   * @param osmWayKey to use
   * @param osmWayValueType to use
   * @param osmModesToCheck modes to select from
   * @return allowed OsmModes found
   */
  protected Set<String> collectAllowedOsmWayModes(
      String osmWayKey, String osmWayValueType, Collection<String> osmModesToCheck) {
    return osmModesToCheck.stream().filter( osmMode ->
        isModeAllowedOnOsmWay(osmWayKey, osmWayValueType, osmMode)).collect(Collectors.toSet());
  }  
  
  /** Add allowed osm modes to OSM way type
   *
   * @param osmWayKey to use
   * @param osmWayTypeValue to use
   * @param osmModes to allow
   * @throws IllegalArgumentException naming the first mode that is not an OSM mode, allowing none of the modes
   */
  protected void addAllowedOsmWayModes(String osmWayKey, String osmWayTypeValue, final List<String> osmModes) {
    osmModeAccessDefaults.addAllowedModes(osmWayKey, osmWayTypeValue, osmModes);
  }

  /** register a custom new way key-value pair with defaults to parse outside the default supported tags. It is
   * assumed it has been verified that this combination does not exist in the regular tagging that is supported
   * beforehand, so it will just be added without further checking at this point.
   *
   * @param osmWayKey to use
   * @param osmWayTypeValue to use
   * @param speedLimitKmh default to use
   * @param capacityPerLanePcuH default to use
   * @param maxDensityPerLanePcuH default to use
   * @param allowedOsmModes default allowed mode(s) to use
   * @return true when registration was successful, false otherwise
   * @throws IllegalArgumentException naming the first allowed mode that is not an OSM mode, registering nothing
   */
  protected boolean registerNewSupportedOsmWayType(
          String osmWayKey, String osmWayTypeValue, double speedLimitKmh, double capacityPerLanePcuH,
          double maxDensityPerLanePcuH, String... allowedOsmModes) {

    addAllowedOsmWayModes(osmWayKey, osmWayTypeValue, Arrays.asList(allowedOsmModes));
    this.infrastructureTypeConfiguration.activate(osmWayTypeValue);
    boolean success = addDefaultSpeedLimit(osmWayKey, osmWayTypeValue, speedLimitKmh);
    overwriteOsmWayTypeDefaultCapacityMaxDensity(
        osmWayKey, osmWayTypeValue, capacityPerLanePcuH, maxDensityPerLanePcuH);
    return success;
  }
  
  /* public */

  /**
   * Log the active mode mappings for this way category.
   *
   * @param level indentation level to apply
   */
  protected void logModeMappings(int level) {
    if (isParserActive()) {
      osmModeMapping.logActiveMapping("Mode mappings", level);
    } else {
      LOGGER.info(LoggingUtils.settingsSection("Mode mappings", level));
      LOGGER.info(LoggingUtils.settingsEntry("Mapping inactive", level + 1));
    }
  }

  /** Verify if the passed in osmMode is mapped to a mode, i.e., if it is actively included when
   * reading the network. When the parser is not active false is returned in all cases
   * 
   * @param osmMode to verify
   * @return true if mapped and parser is active, false otherwise
   */
  public boolean isOsmModeActivated(final String osmMode) {
    return getPlanitModeTypeIfActivated(osmMode) != null;
  }

  /** convenience method that collects all currently activated OSM modes
   *
   * @return mapped osm modes, if not available (due to lack of mapping or inactive parser) empty collection is returned
   */
  public Collection<String> getActivatedOsmModes() {
    return isParserActive() ? osmModeMapping.getActiveExternalModes() : Collections.emptySet();
  }
  
  /** Verify if any mode other than the passed in OSM mode is active (in case the parser is active)
   * @param osmMode to check
   * @return true when other mapped mode is present (and parser is active), false otherwise
   */
  public boolean hasActivatedOsmModeOtherThan(final String osmMode) {
    return getActivatedOsmModes().stream().anyMatch(mode -> (!mode.equals(osmMode)));
  }   
    
  /** Collect all activated types (by key) as a set (copy) in case the parser is active
   * 
   * @return map of currently activated OSM way types (when parser is active), modifications to this
   * set have no effect on configuration, null if not applicable
   */
  public final SortedMap<String, SortedSet<String>> getSetOfActivatedOsmWayLikeTypes(){
    return isParserActive() ? infrastructureTypeConfiguration.getActivatedTypes() : null;
  }

  /**
   * Create a stream of  currently activated planit mode types
   *
   * @return activated PLANitModeTypes, i.e., they are mapped and activated
   */
  public Stream<PredefinedModeType> getActivatedPlanitModeTypesStream() {
    return osmModeMapping.getActivePlanitModesStream();
  }

}

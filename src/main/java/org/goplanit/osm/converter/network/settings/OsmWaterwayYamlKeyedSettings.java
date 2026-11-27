package org.goplanit.osm.converter.network.settings;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import org.goplanit.osm.defaults.OsmInfrastructureConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.tags.OsmWaterModeTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The waterway settings a settings file can set: only the methods of its keys, each write with the read of the same
 * key. {@link OsmWaterwaySettings} extends it with what code needs on top.
 * <p>
 * A settings file gives these keys in its waterway section, beside the keys every way family takes
 * ({@link OsmWayYamlKeyedSettings}), while the methods made for code stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmWaterwayYamlKeyedSettings extends OsmWaySettings {

  /**
   * Constructor
   *
   * @param infrastructureTypeConfiguration to use
   * @param speedLimitDefaults to use
   * @param osmModeAccessDefaults to use
   */
  protected OsmWaterwayYamlKeyedSettings(OsmInfrastructureConfiguration infrastructureTypeConfiguration,
      OsmSpeedLimitDefaultsCategory speedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessDefaults) {
    super(infrastructureTypeConfiguration, speedLimitDefaults, osmModeAccessDefaults);
  }

  /**
   * Choose to not parse the given waterway type, e.g. ferry=primary.
   *
   * @param osmWayValue to use
   * @throws IllegalArgumentException naming the type, when it is not a waterway type these settings know
   */
  @SettingsWrite(key = "types-", effect = SettingsKeyEffect.REMOVE_EACH, level = SettingsKeyLevel.STANDARD,
      description = "Waterway types not to parse")
  public void deactivateOsmWaterwayType(String osmWayValue) {
    deactivateOsmWayType(osmWayValue);
  }

  /** deactivate all types for waterway except the ones provides
   *
   * @param osmWaterwayTypes to not deactivate
   * @throws IllegalArgumentException naming the first type that is not a waterway type these settings know, the types
   *     left as they were
   */
  @SettingsWrite(key = "types", level = SettingsKeyLevel.STANDARD,
      description = "The waterway types to parse, replacing the defaults, such as [ferry]")
  public void deactivateAllOsmWaterwayTypesExcept(List<String> osmWaterwayTypes) {
    replaceActivatedOsmWayTypes(osmWaterwayTypes);
  }

  /**
   * Choose to add given waterway type to parsed types on top of the defaults, e.g. ferry=primary.
   *
   * @param osmWayValue to use
   * @throws IllegalArgumentException naming the type, when it is not a waterway type these settings know
   */
  @SettingsWrite(key = "types+", effect = SettingsKeyEffect.ADD_EACH, level = SettingsKeyLevel.STANDARD,
      description = "Waterway types to parse besides those already parsed")
  public void activateOsmWaterwayType(String osmWayValue) {
    activateOsmWayType(osmWayValue);
  }

  /** activate an OSM water way mode based on its (default) mapping to a PLANit mode. This means that the osmMode
   * will be added to the PLANit network
   *
   * @param osmWaterMode to activate
   * @throws IllegalArgumentException naming the mode, when no PLANit mode is mapped to it
   */
  @SettingsWrite(key = "modes+", effect = SettingsKeyEffect.ADD_EACH, level = SettingsKeyLevel.STANDARD,
      description = "OSM water modes to parse besides those already parsed")
  public void activateOsmWaterMode(String osmWaterMode) {
    activateOsmMode(osmWaterMode);
  }

  /** Remove a mapping from OSM water mode to PLANit mode. This means that the osmMode will not be added to the
   * PLANit network
   *
   * @param osmWaterMode to remove
   * @throws IllegalArgumentException naming the mode, when no PLANit mode is mapped to it
   */
  @SettingsWrite(key = "modes-", effect = SettingsKeyEffect.REMOVE_EACH, level = SettingsKeyLevel.STANDARD,
      description = "OSM water modes not to parse")
  public void deactivateOsmWaterMode(String osmWaterMode) {
    deactivateOsmMode(osmWaterMode);
  }

  /** Parse only the water modes given, switching on those that were off and off every other water mode
   *
   * @param remainingOsmWaterModes to parse
   * @throws IllegalArgumentException naming the first mode no PLANit mode is mapped to, the modes left as they were
   */
  @SettingsWrite(key = "modes", level = SettingsKeyLevel.STANDARD,
      description = "The OSM water modes to parse, replacing the defaults, such as [ferry]")
  public void deactivateAllOsmWaterModesExcept(final List<String> remainingOsmWaterModes) {
    replaceActivatedOsmModes(remainingOsmWaterModes);
  }

  /** The OSM water modes activated, whether the waterway parser is active or not
   *
   * @return the modes, such as ferry, in alphabetical order
   */
  @SettingsRead(key = "modes")
  public SortedSet<String> getActivatedOsmWaterModes() {
    return getActivatedOsmModesIgnoringParser();
  }

  /**
   * Map an OSM water mode to a PLANit mode, used once the water mode is activated, replacing the mapping it had. It
   * does not switch the water mode on: a water mode with no mapping by default is mapped here and then added to the
   * modes, after the mapping
   *
   * @param osmWaterMode to map, such as ferry
   * @param planitModeType to map it to
   * @throws IllegalArgumentException naming the mode, when it is not an OSM water mode or no PLANit mode is given
   */
  @SettingsWrite(key = "modeMappings", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "The PLANit mode each OSM water mode maps to once parsed, such as ferry: FERRY; a mode mapped " +
          "is not switched on, give it under modes+ after the mapping")
  public void setOsmWaterModeMapping(String osmWaterMode, PredefinedModeType planitModeType) {
    mapOsmMode(osmWaterMode, planitModeType, OsmWaterModeTags::isWaterModeTag);
  }

  /** The PLANit mode each OSM water mode maps to once activated, whether activated or not
   *
   * @return the mappings by OSM water mode, in alphabetical order, unmodifiable
   */
  @SettingsRead(key = "modeMappings")
  public SortedMap<String, PredefinedModeType> getOsmWaterModeMappings() {
    return getOsmModeMappings();
  }

  /**
   * Choose to overwrite the given waterway type defaults with the given values
   *
   * @param osmWaterwayType the type to set these values for, a waterway type these settings know
   * @param capacityMaxDensity the capacity and maximum density per lane, both above zero
   * @throws IllegalArgumentException naming the type, storing nothing, when it is not known, or a value is left out or
   *     not above zero
   */
  @SettingsWrite(key = "capacityMaxDensity", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "Capacity and maximum density per lane by waterway type, in place of the defaults, such as " +
          "ferry: {capacityPerLanePcuH: 300, maxDensityPerLanePcuKm: 50}")
  public void overwriteCapacityMaxDensityDefaults(String osmWaterwayType,
      OsmWayTypeCapacityMaxDensity capacityMaxDensity) {
    overwriteCapacityMaxDensityOfKnownType(
        OsmWaterwayTags.getKeyForValueType(osmWaterwayType), osmWaterwayType, capacityMaxDensity);
  }

  /** The capacity and maximum density per lane overwritten, by waterway type
   *
   * @return the values by type, such as ferry, in alphabetical order of the type
   */
  @SettingsRead(key = "capacityMaxDensity")
  public SortedMap<String, OsmWayTypeCapacityMaxDensity> getCapacityMaxDensityByOsmWaterwayType() {
    return getCapacityMaxDensityOverwrites();
  }

  /** The waterway types parsed, whether the waterway parser is active or not
   *
   * @return the types, such as ferry, in alphabetical order
   */
  @SettingsRead(key = "types")
  public SortedSet<String> getActivatedOsmWaterwayTypes() {
    return getActivatedOsmWayTypes();
  }
}

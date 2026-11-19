package org.goplanit.osm.converter.network.settings;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.goplanit.osm.defaults.OsmInfrastructureConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The highway settings a settings file can set: only the methods of its keys, each write with the read of the same
 * key. {@link OsmHighwaySettings} extends it with what code needs on top.
 * <p>
 * A settings file gives these keys in its highway section, beside the keys every way family takes
 * ({@link OsmWayYamlKeyedSettings}), while the methods made for code stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmHighwayYamlKeyedSettings extends OsmWaySettings {

  /**  when speed limit information is missing, use predefined speed limits for highway types mapped to urban area
   * speed limits (or non-urban), default is true */
  private boolean speedLimitDefaultsBasedOnUrbanArea = DEFAULT_SPEEDLIMIT_BASED_ON_URBAN_AREA;

  /** the OSM modes allowed by highway type besides those the country defaults allow */
  private final SortedMap<String, SortedSet<String>> allowedOsmModesBeyondDefaultsByType = new TreeMap<>();

  /**
   * Constructor
   *
   * @param infrastructureTypeConfiguration to use
   * @param speedLimitDefaults to use
   * @param osmModeAccessDefaults to use
   */
  protected OsmHighwayYamlKeyedSettings(OsmInfrastructureConfiguration infrastructureTypeConfiguration,
      OsmSpeedLimitDefaultsCategory speedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessDefaults) {
    super(infrastructureTypeConfiguration, speedLimitDefaults, osmModeAccessDefaults);
  }

  /**  default value whether speed limits are based on urban area defaults: true */
  public static boolean DEFAULT_SPEEDLIMIT_BASED_ON_URBAN_AREA = true;

  /**
   * Choose to not parse the given highway type, e.g. highway=road.
   *
   * @param osmWayValue to use
   * @throws IllegalArgumentException naming the type, when it is not a highway type these settings know
   */
  @SettingsWrite(key = "types-", effect = SettingsKeyEffect.REMOVE_EACH, level = SettingsKeyLevel.STANDARD,
      description = "Highway types not to parse")
  public void deactivateOsmHighwayType(String osmWayValue) {
    deactivateOsmWayType(osmWayValue);
  }

  /** deactivate all types for highway except the ones provides
   *
   * @param osmHighwayTypes to not deactivate
   * @throws IllegalArgumentException naming the first type that is not a highway type these settings know, the types
   *     left as they were
   */
  @SettingsWrite(key = "types", level = SettingsKeyLevel.STANDARD,
      description = "The highway types to parse, replacing the defaults, such as [primary, secondary]")
  public void deactivateAllOsmHighwayTypesExcept(List<String> osmHighwayTypes) {
    replaceActivatedOsmWayTypes(osmHighwayTypes);
  }

  /**
   * Choose to add given highway type to parsed types on top of the defaults, e.g. highway=road
   *
   * @param osmWayValue to use
   * @throws IllegalArgumentException naming the type, when it is not a highway type these settings know
   */
  @SettingsWrite(key = "types+", effect = SettingsKeyEffect.ADD_EACH, level = SettingsKeyLevel.STANDARD,
      description = "Highway types to parse besides those already parsed")
  public void activateOsmHighwayType(String osmWayValue) {
    activateOsmWayType(osmWayValue);
  }

  /** activate OSM road mode based on its default mapping to PLANit mode. This means that the osmMode
   * will be added to the PLANit network, but also any of its restrictions
   *  will be imposed on the PLANit mode that is provided.
   *
   * @param osmRoadMode to set
   * @throws IllegalArgumentException naming the mode, when no PLANit mode is mapped to it
   */
  @SettingsWrite(key = "modes+", effect = SettingsKeyEffect.ADD_EACH, level = SettingsKeyLevel.STANDARD,
      description = "OSM road modes to parse besides those already parsed")
  public void activateOsmRoadMode(final String osmRoadMode) {
    activateOsmMode(osmRoadMode);
  }

  /** deactivate an OSM road mode from parsing. This means that the osmMode will not be added to the PLANit network
   *
   * @param osmRoadMode to remove
   * @throws IllegalArgumentException naming the mode, when no PLANit mode is mapped to it
   */
  @SettingsWrite(key = "modes-", effect = SettingsKeyEffect.REMOVE_EACH, level = SettingsKeyLevel.STANDARD,
      description = "OSM road modes not to parse")
  public void deactivateOsmRoadMode(final String osmRoadMode) {
    deactivateOsmMode(osmRoadMode);
  }

  /** Parse only the road modes given, switching on those that were off and off every other road mode
   *
   * @param remainingOsmRoadModes to parse
   * @throws IllegalArgumentException naming the first mode no PLANit mode is mapped to, the modes left as they were
   */
  @SettingsWrite(key = "modes", level = SettingsKeyLevel.STANDARD,
      description = "The OSM road modes to parse, replacing the defaults, such as [motorcar, bus]")
  public void deactivateAllOsmRoadModesExcept(final List<String> remainingOsmRoadModes) {
    replaceActivatedOsmModes(remainingOsmRoadModes);
  }

  /** The OSM road modes activated, whether the highway parser is active or not
   *
   * @return the modes, such as motorcar, in alphabetical order
   */
  @SettingsRead(key = "modes")
  public SortedSet<String> getActivatedOsmRoadModes() {
    return getActivatedOsmModesIgnoringParser();
  }

  /**
   * Map an OSM road mode to a PLANit mode, used once the road mode is activated, replacing the mapping it had. It does
   * not switch the road mode on: a road mode with no mapping by default, such as moped, is mapped here and then added
   * to the modes, after the mapping
   *
   * @param osmRoadMode to map, such as moped
   * @param planitModeType to map it to
   * @throws IllegalArgumentException naming the mode, when it is not an OSM road mode or no PLANit mode is given
   */
  @SettingsWrite(key = "modeMappings", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "The PLANit mode each OSM road mode maps to once parsed, such as moped: MOTOR_BIKE; a mode " +
          "mapped is not switched on, give it under modes+ after the mapping")
  public void setOsmRoadModeMapping(String osmRoadMode, PredefinedModeType planitModeType) {
    mapOsmMode(osmRoadMode, planitModeType, OsmRoadModeTags::isRoadModeTag);
  }

  /** The PLANit mode each OSM road mode maps to once activated, whether activated or not
   *
   * @return the mappings by OSM road mode, in alphabetical order, unmodifiable
   */
  @SettingsRead(key = "modeMappings")
  public SortedMap<String, PredefinedModeType> getOsmRoadModeMappings() {
    return getOsmModeMappings();
  }

  /**
   * Choose to overwrite the given highway type defaults with the given values
   *
   * @param osmHighwayType the type to set these values for, a highway type these settings know
   * @param capacityMaxDensity the capacity and maximum density per lane, both above zero
   * @throws IllegalArgumentException naming the type, storing nothing, when it is not known, or a value is left out or
   *     not above zero
   */
  @SettingsWrite(key = "capacityMaxDensity", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "Capacity and maximum density per lane by highway type, in place of the defaults, such as " +
          "residential: {capacityPerLanePcuH: 1000, maxDensityPerLanePcuKm: 150}")
  public void overwriteCapacityMaxDensityDefaults(String osmHighwayType,
      OsmWayTypeCapacityMaxDensity capacityMaxDensity) {
    overwriteCapacityMaxDensityOfKnownType(OsmHighwayTags.HIGHWAY, osmHighwayType, capacityMaxDensity);
  }

  /** The capacity and maximum density per lane overwritten, by highway type
   *
   * @return the values by type, such as residential, in alphabetical order of the type
   */
  @SettingsRead(key = "capacityMaxDensity")
  public SortedMap<String, OsmWayTypeCapacityMaxDensity> getCapacityMaxDensityByOsmHighwayType() {
    return getCapacityMaxDensityOverwrites();
  }

  /**
   * Allow OSM modes on a highway type besides those its country defaults allow, such as tram on service roads where
   * a track runs in the road
   *
   * @param osmHighwayType to allow the modes on, a highway type these settings know
   * @param osmModes to allow, each an OSM mode
   * @throws IllegalArgumentException naming the type or mode, allowing none of the modes, when the type is not known
   *     or a mode is not an OSM mode
   */
  @SettingsWrite(key = "allowedModesByType", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "OSM modes to allow by highway type besides those the country defaults allow, such as " +
          "service: [tram]")
  public void addAllowedOsmHighwayModes(final String osmHighwayType, final List<String> osmModes) {
    throwIfUnknownOsmWayType(osmHighwayType);
    addAllowedOsmWayModes(OsmHighwayTags.getHighwayKeyTag(), osmHighwayType, osmModes);
    allowedOsmModesBeyondDefaultsByType.computeIfAbsent(osmHighwayType, type -> new TreeSet<>()).addAll(osmModes);
  }

  /** The OSM modes allowed by highway type besides those the country defaults allow, see
   * {@link #addAllowedOsmHighwayModes(String, List)}
   *
   * @return the modes by type, such as service: [tram], types and modes in alphabetical order
   */
  @SettingsRead(key = "allowedModesByType")
  public SortedMap<String, SortedSet<String>> getAllowedOsmHighwayModesBeyondDefaults() {
    var allowedOsmModesByType = new TreeMap<String, SortedSet<String>>();
    allowedOsmModesBeyondDefaultsByType.forEach(
        (osmHighwayType, osmModes) -> allowedOsmModesByType.put(osmHighwayType, new TreeSet<>(osmModes)));
    return allowedOsmModesByType;
  }

  /** The highway types parsed, whether the highway parser is active or not
   *
   * @return the types, such as primary, in alphabetical order
   */
  @SettingsRead(key = "types")
  public SortedSet<String> getActivatedOsmHighwayTypes() {
    return getActivatedOsmWayTypes();
  }

  /** collect state of flag indicating to use urban or non-urban default
   * speed limits when speed limit information is missing
   *
   * @return flag value
   */
  @SettingsRead(key = "speedLimitDefaultsBasedOnUrbanArea")
  public boolean isSpeedLimitDefaultsBasedOnUrbanArea() {
    return speedLimitDefaultsBasedOnUrbanArea;
  }

  /** set state of flag indicating to use urban or non-urban default speed limits when speed limit information is
   * missing
   *
   * @param speedLimitDefaultsBasedOnUrbanArea flag value
   */
  @SettingsWrite(key = "speedLimitDefaultsBasedOnUrbanArea", level = SettingsKeyLevel.COMMON,
      description = "Use the urban speed limit defaults, rather than those outside urban areas, for highways whose " +
          "tags give no speed limit")
  public void setSpeedLimitDefaultsBasedOnUrbanArea(boolean speedLimitDefaultsBasedOnUrbanArea) {
    this.speedLimitDefaultsBasedOnUrbanArea = speedLimitDefaultsBasedOnUrbanArea;
  }
}

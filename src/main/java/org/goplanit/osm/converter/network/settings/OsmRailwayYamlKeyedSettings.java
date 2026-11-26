package org.goplanit.osm.converter.network.settings;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.goplanit.osm.defaults.OsmInfrastructureConfiguration;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsCategory;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsCategory;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.annotations.NoSettingsKey;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The railway settings a settings file can set: only the methods of its keys, each write with the read of the same
 * key. {@link OsmRailwaySettings} extends it with what code needs on top.
 * <p>
 * A settings file gives these keys in its railway section, beside the keys every way family takes
 * ({@link OsmWayYamlKeyedSettings}), while the methods made for code stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmRailwayYamlKeyedSettings extends OsmWaySettings {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmRailwayYamlKeyedSettings.class.getCanonicalName());

  /** see {@link #setMaximumDistanceTrackSignalToRoadCrossingMeters(double)} */
  private double maximumDistanceTrackSignalToRoadCrossingMeters = DEFAULT_MAX_TRACK_SIGNAL_TO_ROAD_CROSSING_DISTANCE_M;

  /**
   * Constructor
   *
   * @param infrastructureTypeConfiguration to use
   * @param speedLimitDefaults to use
   * @param osmModeAccessDefaults to use
   */
  protected OsmRailwayYamlKeyedSettings(OsmInfrastructureConfiguration infrastructureTypeConfiguration,
      OsmSpeedLimitDefaultsCategory speedLimitDefaults, OsmModeAccessDefaultsCategory osmModeAccessDefaults) {
    super(infrastructureTypeConfiguration, speedLimitDefaults, osmModeAccessDefaults);
  }

  /** default maximum distance from a traffic signal on a tram or light rail track to the road it crosses, in metres */
  public static final double DEFAULT_MAX_TRACK_SIGNAL_TO_ROAD_CROSSING_DISTANCE_M = 50;

  /**
   * Choose to not parse the given railway type, e.g. railway=rail.
   *
   * @param osmWayValue to use
   * @throws IllegalArgumentException naming the type, when it is not a railway type these settings know
   */
  @SettingsWrite(key = "types-", effect = SettingsKeyEffect.REMOVE_EACH, level = SettingsKeyLevel.STANDARD,
      description = "Railway types not to parse")
  public void deactivateOsmRailwayType(String osmWayValue) {
    deactivateOsmWayType(osmWayValue);
  }

  /** deactivate all types for railway except the ones provides
   *
   * @param osmRailwayTypes to not deactivate
   * @throws IllegalArgumentException naming the first type that is not a railway type these settings know, the types
   *     left as they were
   */
  @SettingsWrite(key = "types", level = SettingsKeyLevel.STANDARD,
      description = "The railway types to parse, replacing the defaults, such as [rail, tram]")
  public void deactivateAllOsmRailwayTypesExcept(List<String> osmRailwayTypes) {
    replaceActivatedOsmWayTypes(osmRailwayTypes);
  }

  /**
   * Choose to add given railway type to parsed types on top of the defaults, e.g. railway=rail.
   *
   * @param osmWayValue to use
   * @throws IllegalArgumentException naming the type, when it is not a railway type these settings know
   */
  @SettingsWrite(key = "types+", effect = SettingsKeyEffect.ADD_EACH, level = SettingsKeyLevel.STANDARD,
      description = "Railway types to parse besides those already parsed")
  public void activateOsmRailwayType(String osmWayValue) {
    activateOsmWayType(osmWayValue);
  }

  /** activate an OSM rail mode based on its (default) mapping to a PLANit mode. This means that the osmMode
   *  will be added to the PLANit network
   *
   * @param osmRailMode to activate
   * @throws IllegalArgumentException naming the mode, when no PLANit mode is mapped to it
   */
  @SettingsWrite(key = "modes+", effect = SettingsKeyEffect.ADD_EACH, level = SettingsKeyLevel.STANDARD,
      description = "OSM rail modes to parse besides those already parsed")
  public void activateOsmRailMode(String osmRailMode) {
    activateOsmMode(osmRailMode);
  }

  /** Remove a mapping from OSM rail mode to PLANit mode. This means that the osmMode will not be added to
   *  the PLANit network
   *
   * @param osmRailMode to remove
   * @throws IllegalArgumentException naming the mode, when no PLANit mode is mapped to it
   */
  @SettingsWrite(key = "modes-", effect = SettingsKeyEffect.REMOVE_EACH, level = SettingsKeyLevel.STANDARD,
      description = "OSM rail modes not to parse")
  public void deactivateOsmRailMode(String osmRailMode) {
    deactivateOsmMode(osmRailMode);
  }

  /** Parse only the rail modes given, switching on those that were off and off every other rail mode
   *
   * @param remainingOsmRailModes to parse
   * @throws IllegalArgumentException naming the first mode no PLANit mode is mapped to, the modes left as they were
   */
  @SettingsWrite(key = "modes", level = SettingsKeyLevel.STANDARD,
      description = "The OSM rail modes to parse, replacing the defaults, such as [train, tram]")
  public void deactivateAllOsmRailModesExcept(final List<String> remainingOsmRailModes) {
    replaceActivatedOsmModes(remainingOsmRailModes);
  }

  /** The OSM rail modes activated, whether the railway parser is active or not
   *
   * @return the modes, such as train, in alphabetical order
   */
  @SettingsRead(key = "modes")
  public SortedSet<String> getActivatedOsmRailModes() {
    return getActivatedOsmModesIgnoringParser();
  }

  /**
   * Map an OSM rail mode to a PLANit mode, used once the rail mode is activated, replacing the mapping it had. It does
   * not switch the rail mode on: a rail mode with no mapping by default is mapped here and then added to the modes,
   * after the mapping
   *
   * @param osmRailMode to map, such as funicular
   * @param planitModeType to map it to
   * @throws IllegalArgumentException naming the mode, when it is not an OSM rail mode or no PLANit mode is given
   */
  @SettingsWrite(key = "modeMappings", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "The PLANit mode each OSM rail mode maps to once parsed, such as funicular: TRAM; a mode mapped " +
          "is not switched on, give it under modes+ after the mapping")
  public void setOsmRailModeMapping(String osmRailMode, PredefinedModeType planitModeType) {
    mapOsmMode(osmRailMode, planitModeType, OsmRailModeTags::isRailModeTag);
  }

  /** The PLANit mode each OSM rail mode maps to once activated, whether activated or not
   *
   * @return the mappings by OSM rail mode, in alphabetical order, unmodifiable
   */
  @SettingsRead(key = "modeMappings")
  public SortedMap<String, PredefinedModeType> getOsmRailModeMappings() {
    return getOsmModeMappings();
  }

  /**
   * Choose to overwrite the given railway type defaults with the given values
   *
   * @param osmRailwayType the type to set these values for, a railway type these settings know
   * @param capacityMaxDensity the capacity and maximum density per lane, both above zero
   * @throws IllegalArgumentException naming the type, storing nothing, when it is not known, or a value is left out or
   *     not above zero
   */
  @SettingsWrite(key = "capacityMaxDensity", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "Capacity and maximum density per track by railway type, in place of the defaults, such as " +
          "rail: {capacityPerLanePcuH: 2000, maxDensityPerLanePcuKm: 100}")
  public void overwriteCapacityMaxDensityDefaults(String osmRailwayType,
      OsmWayTypeCapacityMaxDensity capacityMaxDensity) {
    overwriteCapacityMaxDensityOfKnownType(OsmRailwayTags.RAILWAY, osmRailwayType, capacityMaxDensity);
  }

  /** The capacity and maximum density per track overwritten, by railway type
   *
   * @return the values by type, such as rail, in alphabetical order of the type
   */
  @SettingsRead(key = "capacityMaxDensity")
  public SortedMap<String, OsmWayTypeCapacityMaxDensity> getCapacityMaxDensityByOsmRailwayType() {
    return getCapacityMaxDensityOverwrites();
  }

  /** The railway types parsed, whether the railway parser is active or not
   *
   * @return the types, such as rail, in alphabetical order
   */
  @SettingsRead(key = "types")
  public SortedSet<String> getActivatedOsmRailwayTypes() {
    return getActivatedOsmWayTypes();
  }

  /** Get the maximum distance from a traffic signal on a tram or light rail track to the road it crosses
   *
   * @return distance in metres
   */
  @SettingsRead(key = "maximumDistanceTrackSignalToRoadCrossingMeters")
  public double getMaximumDistanceTrackSignalToRoadCrossingMeters() {
    return maximumDistanceTrackSignalToRoadCrossingMeters;
  }

  /** Set the maximum distance from a traffic signal on a tram or light rail track to the road it crosses. Such a
   * signal, with no road through its node, makes the node where its track crosses a road within this distance along
   * the track a signalised junction, car traffic being held there for the tram; beyond it the signal is ignored
   *
   * @param distanceMeters distance to use
   */
  @SettingsWrite(key = "maximumDistanceTrackSignalToRoadCrossingMeters", level = SettingsKeyLevel.RARE,
      description = "Furthest a traffic signal on a tram or light rail track may be from the road it crosses, along " +
          "the track, in metres")
  public void setMaximumDistanceTrackSignalToRoadCrossingMeters(double distanceMeters) {
    this.maximumDistanceTrackSignalToRoadCrossingMeters = distanceMeters;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NoSettingsKey(reason = "logs the settings")
  public void logSettings(int level) {
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue("Maximum track signal to road crossing distance (m)",
        getMaximumDistanceTrackSignalToRoadCrossingMeters(), level));
  }
}

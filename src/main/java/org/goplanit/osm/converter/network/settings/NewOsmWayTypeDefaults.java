package org.goplanit.osm.converter.network.settings;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.goplanit.osm.defaults.OsmLaneDefaults;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The defaults of a new OSM way type, for the ways of the type whose tags give none, see
 * {@link OsmNetworkReaderYamlKeyedSettings#registerNewOsmWayType(String, NewOsmWayTypeDefaults)}. Made without
 * arguments it holds no values: a new type has no defaults to fall back on, so each is to be set.
 * <p>
 * Parsing only takes way types it knows, each with built-in defaults for lanes, speed limit, capacity and the modes
 * allowed. A type it does not know, such as highway=escape, needs all of these before its ways can become links, so
 * they are given together, one set per new type
 * </p>
 *
 * @author markr
 */
public class NewOsmWayTypeDefaults {

  /** see {@link #setLanes(Integer)} */
  private Integer lanes;

  /** see {@link #setSpeedLimitKmh(Double)} */
  private Double speedLimitKmh;

  /** see {@link #setCapacityPerLanePcuH(Double)} */
  private Double capacityPerLanePcuH;

  /** see {@link #setMaxDensityPerLanePcuKm(Double)} */
  private Double maxDensityPerLanePcuKm;

  /** see {@link #setAllowedOsmModes(Set)} */
  private final Set<String> allowedOsmModes = new TreeSet<>();

  /**
   * Constructor with no values
   */
  public NewOsmWayTypeDefaults() {
  }

  /**
   * Constructor
   *
   * @param lanes see {@link #setLanes(Integer)}
   * @param speedLimitKmh see {@link #setSpeedLimitKmh(Double)}
   * @param capacityPerLanePcuH see {@link #setCapacityPerLanePcuH(Double)}
   * @param maxDensityPerLanePcuKm see {@link #setMaxDensityPerLanePcuKm(Double)}
   * @param allowedOsmModes see {@link #setAllowedOsmModes(Set)}
   */
  public NewOsmWayTypeDefaults(Integer lanes, Double speedLimitKmh, Double capacityPerLanePcuH,
      Double maxDensityPerLanePcuKm, Set<String> allowedOsmModes) {
    this.lanes = lanes;
    this.speedLimitKmh = speedLimitKmh;
    this.capacityPerLanePcuH = capacityPerLanePcuH;
    this.maxDensityPerLanePcuKm = maxDensityPerLanePcuKm;
    this.allowedOsmModes.addAll(allowedOsmModes);
  }

  /**
   * Lanes of a way of the type
   *
   * @return lanes, null when not set
   */
  @SettingsRead(key = "lanes")
  public Integer getLanes() {
    return lanes;
  }

  /**
   * Set the lanes of a way of the type: for a highway type its total lanes, see {@link OsmLaneDefaults}, for a railway
   * or waterway type those in each direction
   *
   * @param lanes to use
   */
  @SettingsWrite(key = "lanes", level = SettingsKeyLevel.UNCOMMON,
      description = "Lanes of a way of the type: for a highway type its total lanes, for a railway or waterway type " +
          "those in each direction")
  public void setLanes(Integer lanes) {
    this.lanes = lanes;
  }

  /**
   * Speed limit of a way of the type
   *
   * @return speed limit in km/h, null when not set
   */
  @SettingsRead(key = "speedLimitKmh")
  public Double getSpeedLimitKmh() {
    return speedLimitKmh;
  }

  /**
   * Set the speed limit of a way of the type
   *
   * @param speedLimitKmh in km/h
   */
  @SettingsWrite(key = "speedLimitKmh", level = SettingsKeyLevel.UNCOMMON,
      description = "Speed limit of a way of the type, in km/h")
  public void setSpeedLimitKmh(Double speedLimitKmh) {
    this.speedLimitKmh = speedLimitKmh;
  }

  /**
   * Capacity of a lane of a way of the type
   *
   * @return capacity in pcu per hour, null when not set
   */
  @SettingsRead(key = "capacityPerLanePcuH")
  public Double getCapacityPerLanePcuH() {
    return capacityPerLanePcuH;
  }

  /**
   * Set the capacity of a lane of a way of the type
   *
   * @param capacityPerLanePcuH in pcu per hour
   */
  @SettingsWrite(key = "capacityPerLanePcuH", level = SettingsKeyLevel.UNCOMMON,
      description = "Capacity of a lane of a way of the type, in pcu per hour")
  public void setCapacityPerLanePcuH(Double capacityPerLanePcuH) {
    this.capacityPerLanePcuH = capacityPerLanePcuH;
  }

  /**
   * Most vehicles a lane of a way of the type holds
   *
   * @return maximum density in pcu per km, null when not set
   */
  @SettingsRead(key = "maxDensityPerLanePcuKm")
  public Double getMaxDensityPerLanePcuKm() {
    return maxDensityPerLanePcuKm;
  }

  /**
   * Set the most vehicles a lane of a way of the type holds
   *
   * @param maxDensityPerLanePcuKm in pcu per km
   */
  @SettingsWrite(key = "maxDensityPerLanePcuKm", level = SettingsKeyLevel.UNCOMMON,
      description = "Most vehicles a lane of a way of the type holds, in pcu per km")
  public void setMaxDensityPerLanePcuKm(Double maxDensityPerLanePcuKm) {
    this.maxDensityPerLanePcuKm = maxDensityPerLanePcuKm;
  }

  /**
   * The OSM modes allowed on a way of the type
   *
   * @return the OSM modes, in alphabetical order, unmodifiable, empty when not set
   */
  @SettingsRead(key = "allowedOsmModes")
  public Set<String> getAllowedOsmModes() {
    return Collections.unmodifiableSet(allowedOsmModes);
  }

  /**
   * Set the OSM modes allowed on a way of the type, replacing those set before
   *
   * @param allowedOsmModes such as motorcar and bus
   */
  @SettingsWrite(key = "allowedOsmModes", level = SettingsKeyLevel.UNCOMMON,
      description = "The OSM modes allowed on a way of the type, such as motorcar and bus")
  public void setAllowedOsmModes(Set<String> allowedOsmModes) {
    this.allowedOsmModes.clear();
    this.allowedOsmModes.addAll(allowedOsmModes);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format("%s lanes, %s km/h, %s pcu/h and %s pcu/km per lane, OSM modes %s",
        lanes, speedLimitKmh, capacityPerLanePcuH, maxDensityPerLanePcuKm, allowedOsmModes);
  }
}

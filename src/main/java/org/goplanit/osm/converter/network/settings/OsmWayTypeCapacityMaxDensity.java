package org.goplanit.osm.converter.network.settings;

import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The capacity and maximum density per lane given for an OSM way type, in place of the defaults, see the
 * capacityMaxDensity key of each way family, such as {@link OsmHighwayYamlKeyedSettings}. Made without arguments it
 * holds no values, each to be set.
 * <p>
 * Capacity and maximum density together decide how much traffic a lane carries and how it jams, so a study that
 * knows a road type behaves differently from the defaults gives both for the type, such as residential streets at
 * 1000 pcu/h and 150 pcu/km per lane
 * </p>
 *
 * @author markr
 */
public class OsmWayTypeCapacityMaxDensity {

  /** see {@link #setCapacityPerLanePcuH(Double)} */
  private Double capacityPerLanePcuH;

  /** see {@link #setMaxDensityPerLanePcuKm(Double)} */
  private Double maxDensityPerLanePcuKm;

  /**
   * Constructor with no values
   */
  public OsmWayTypeCapacityMaxDensity() {
  }

  /**
   * Constructor
   *
   * @param capacityPerLanePcuH see {@link #setCapacityPerLanePcuH(Double)}
   * @param maxDensityPerLanePcuKm see {@link #setMaxDensityPerLanePcuKm(Double)}
   */
  public OsmWayTypeCapacityMaxDensity(Double capacityPerLanePcuH, Double maxDensityPerLanePcuKm) {
    this.capacityPerLanePcuH = capacityPerLanePcuH;
    this.maxDensityPerLanePcuKm = maxDensityPerLanePcuKm;
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
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format("%s pcu/h and %s pcu/km per lane", capacityPerLanePcuH, maxDensityPerLanePcuKm);
  }
}

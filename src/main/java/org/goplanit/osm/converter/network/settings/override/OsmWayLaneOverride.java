package org.goplanit.osm.converter.network.settings.override;

import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The lanes of one OSM way in each direction along it, in place of what its tags or the defaults give
 * <p>
 * Forward is the direction of the way's nodes, as {@code lanes:forward} counts it, and backward the other. A direction
 * left null keeps what the tags or the defaults give. For example, 2 forward and null backward gives the way's forward
 * link segments 2 lanes and leaves its backward ones as tagged. It is the value of a lanes override, see
 * {@link OsmNetworkReaderSettings#overrideOsmWayLanes(long, org.goplanit.utils.settings.UserOverride)}, which checks
 * the counts when the override is stored; this class takes any count, so a settings file can fill it one key at a
 * time.
 * </p>
 *
 * @author markr
 */
public class OsmWayLaneOverride {

  /** lanes forward along the way, null when not given */
  private Integer lanesForward;

  /** lanes backward along the way, null when not given */
  private Integer lanesBackward;

  /** Constructor without lanes, given afterwards through the setters, as a settings file does */
  public OsmWayLaneOverride() {
  }

  /**
   * Constructor
   *
   * @param lanesForward lanes forward along the way, null to keep what the tags or the defaults give
   * @param lanesBackward lanes backward along the way, null to keep what the tags or the defaults give
   */
  public OsmWayLaneOverride(Integer lanesForward, Integer lanesBackward) {
    this.lanesForward = lanesForward;
    this.lanesBackward = lanesBackward;
  }

  /**
   * Lanes forward along the way
   *
   * @return the lanes, null when not given
   */
  @SettingsRead(key = "lanesForward")
  public Integer getLanesForward() {
    return lanesForward;
  }

  /**
   * Set the lanes forward along the way
   *
   * @param lanesForward the lanes, null to keep what the tags or the defaults give
   */
  @SettingsWrite(key = "lanesForward", level = SettingsKeyLevel.UNCOMMON,
      description = "Lanes forward along the way, in the direction of its nodes")
  public void setLanesForward(Integer lanesForward) {
    this.lanesForward = lanesForward;
  }

  /**
   * Lanes backward along the way
   *
   * @return the lanes, null when not given
   */
  @SettingsRead(key = "lanesBackward")
  public Integer getLanesBackward() {
    return lanesBackward;
  }

  /**
   * Set the lanes backward along the way
   *
   * @param lanesBackward the lanes, null to keep what the tags or the defaults give
   */
  @SettingsWrite(key = "lanesBackward", level = SettingsKeyLevel.UNCOMMON,
      description = "Lanes backward along the way, against the direction of its nodes")
  public void setLanesBackward(Integer lanesBackward) {
    this.lanesBackward = lanesBackward;
  }

  /**
   * Whether lanes are given in both directions, so the tagged total of the way is not used at all
   *
   * @return true when both are given
   */
  public boolean hasLanesForwardAndBackward() {
    return lanesForward != null && lanesBackward != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format("forward %s, backward %s", lanesForward == null ? "as tagged" : lanesForward,
        lanesBackward == null ? "as tagged" : lanesBackward);
  }
}

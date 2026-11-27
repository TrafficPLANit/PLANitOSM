package org.goplanit.osm.converter.network.settings.override;

import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The lanes of one OSM way in each direction along it, in place of what its tags or the defaults give
 * <p>
 * Forward and backward are the two directions along the way as it is drawn in OSM, not compass directions. Forward
 * runs from the first node of the way to its last, in the order OSM lists its nodes, which is the direction the OSM
 * tags {@code lanes:forward} and {@code oneway=yes} refer to; backward runs the other way. For example, for a way drawn
 * from south to north, 2 forward and 3 backward gives 2 lanes northbound and 3 southbound. Two ways of one road can be
 * drawn in opposite directions, so the same lanes can be forward on one way and backward on the next. The page of a
 * way on openstreetmap.org lists its nodes from first to last; in PLANit, forward is from node A to node B of the
 * links made from the way.
 * </p>
 * <p>
 * A direction left null keeps what the tags or the defaults give. For example, 2 forward and null backward gives the
 * way's forward link segments 2 lanes and leaves its backward ones as tagged. It is the value of a lanes override, see
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
      description = "Lanes from the first node of the way to its last, in the order OSM lists its nodes, as " +
          "lanes:forward; not a compass direction")
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
      description = "Lanes from the last node of the way to its first, against the order OSM lists its nodes, as " +
          "lanes:backward; not a compass direction")
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

package org.goplanit.osm.converter.network.settings;

import org.goplanit.utils.graph.directed.Connectivity;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * How a single mode's dangling subnetworks are to be dealt with, see
 * {@link OsmNetworkReaderYamlKeyedSettings#setRemoveDanglingSubnetworks(java.util.Map)}. Made without arguments it
 * holds the defaults: a single subnetwork the mode can route across in full, see
 * {@link OsmNetworkReaderYamlKeyedSettings#DEFAULT_MINIMUM_SUBNETWORK_SIZE}
 * <p>
 * Each mode is judged on its own network, so each mode whose dangling subnetworks are removed gets its own sizes and
 * notion of connectivity. A settings file gives one per mode, such as a pedestrian network kept unless it is smaller
 * than 20 vertices
 * </p>
 *
 * @author markr
 */
public class DanglingSubnetworkConfig {

  /** see {@link #setBelowSize(int)} */
  private int belowSize = OsmNetworkReaderYamlKeyedSettings.DEFAULT_MINIMUM_SUBNETWORK_SIZE;

  /** see {@link #setAboveSize(int)} */
  private int aboveSize = Integer.MAX_VALUE;

  /** see {@link #setConnectivity(Connectivity)} */
  private Connectivity connectivity = OsmNetworkReaderYamlKeyedSettings.DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY;

  /**
   * Constructor with the defaults
   */
  public DanglingSubnetworkConfig() {
  }

  /**
   * Constructor
   *
   * @param belowSize withdraw the mode's access on subnetworks smaller than this
   * @param aboveSize withdraw the mode's access on subnetworks larger than this
   * @param connectivity what constitutes a subnetwork of the mode
   */
  public DanglingSubnetworkConfig(int belowSize, int aboveSize, Connectivity connectivity) {
    this.belowSize = belowSize;
    this.aboveSize = aboveSize;
    this.connectivity = connectivity;
  }

  /**
   * Size below which a subnetwork loses the mode's access
   *
   * @return size
   */
  @SettingsRead(key = "belowSize")
  public int getBelowSize() {
    return belowSize;
  }

  /**
   * Set the size below which a subnetwork loses the mode's access
   *
   * @param belowSize in vertices
   */
  @SettingsWrite(key = "belowSize", level = SettingsKeyLevel.UNCOMMON,
      description = "Size below which a subnetwork loses the mode's access, in vertices, by default max, the largest " +
          "possible")
  public void setBelowSize(int belowSize) {
    this.belowSize = belowSize;
  }

  /**
   * Size above which a subnetwork loses the mode's access
   *
   * @return size
   */
  @SettingsRead(key = "aboveSize")
  public int getAboveSize() {
    return aboveSize;
  }

  /**
   * Set the size above which a subnetwork loses the mode's access
   *
   * @param aboveSize in vertices
   */
  @SettingsWrite(key = "aboveSize", level = SettingsKeyLevel.UNCOMMON,
      description = "Size above which a subnetwork loses the mode's access, in vertices, by default max, the largest " +
          "possible")
  public void setAboveSize(int aboveSize) {
    this.aboveSize = aboveSize;
  }

  /**
   * What constitutes a subnetwork of this mode
   *
   * @return connectivity
   */
  @SettingsRead(key = "connectivity")
  public Connectivity getConnectivity() {
    return connectivity;
  }

  /**
   * Set what constitutes a subnetwork of this mode, see
   * {@link OsmNetworkReaderYamlKeyedSettings#setRemoveDanglingSubnetworks(java.util.Map)}. Strong connectivity honours
   * the parsed turn restrictions, see
   * {@link OsmNetworkReaderYamlKeyedSettings#DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY}
   *
   * @param connectivity to use
   */
  @SettingsWrite(key = "connectivity", level = SettingsKeyLevel.UNCOMMON,
      description = "What makes a subnetwork of the mode: STRONG, what it can both reach and leave, or WEAK, what " +
          "hangs together when direction is ignored")
  public void setConnectivity(Connectivity connectivity) {
    this.connectivity = connectivity;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return String.format("below %s, above %s, %s connectivity",
        belowSize == Integer.MAX_VALUE ? "infinite" : String.valueOf(belowSize),
        aboveSize == Integer.MAX_VALUE ? "infinite" : String.valueOf(aboveSize),
        connectivity.name().toLowerCase());
  }
}

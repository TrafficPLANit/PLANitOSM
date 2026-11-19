package org.goplanit.osm.converter.network.settings;

import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The keys the settings of every OSM way family take, highway, railway and waterway alike: only the methods of its
 * keys, each write with the read of the same key. {@link OsmWaySettings} extends it with what the families share in
 * code.
 * <p>
 * A settings file gives these keys in each of the sections highway, railway and waterway, such as
 * {@code railway: {active: true}} to parse railways. A parser is switched here only: changing the types or modes of a
 * family never switches its parser, so a file saying a parser is off is never overruled by its own other keys
 * </p>
 *
 * @author markr
 */
public abstract class OsmWayYamlKeyedSettings {

  /** see {@link #activateParser(boolean)} */
  private boolean parserActive;

  /**
   * Constructor
   */
  protected OsmWayYamlKeyedSettings() {
  }

  /** Determine whether the ways represented by these settings should be parsed or not. It has no impact
   * on the settings themselves, except that all queries related to whether or not modes or types are activated
   * will respond negatively when the parser is deactived, despite the underlying settings remaining in memory and
   * will be reinstated when the parser is re-activated.
   *
   * @param activate parser when true, deactivate otherwise
   */
  @SettingsWrite(key = "active", level = SettingsKeyLevel.STANDARD, description = "Parse the ways of this kind")
  public void activateParser(boolean activate) {
    this.parserActive = activate;
  }

  /** verifies if the parser for these settings is active or not
   * @return true when active, false otherwise
   */
  @SettingsRead(key = "active")
  public boolean isParserActive() {
    return parserActive;
  }
}

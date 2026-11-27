package org.goplanit.osm.converter.network.settings;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.NoSettingsKey;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The intersection collapse settings a settings file can set: only the methods of its keys, each write with the read
 * of the same key. {@link OsmIntersectionCollapserSettings} extends it with what code needs on top.
 * <p>
 * A settings file gives these keys in the intersectionCollapser section of the intersections, while the methods made
 * for code, such as excluding several nodes at once without reasons, stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmIntersectionCollapserYamlKeyedSettings {

  /** logger to use */
  private static final Logger LOGGER =
      Logger.getLogger(OsmIntersectionCollapserYamlKeyedSettings.class.getCanonicalName());

  /** see {@link #setCollapsedModeTypes(PredefinedModeType...)} */
  private final Set<PredefinedModeType> collapsedModeTypes = new TreeSet<>();

  /** see {@link #excludeOsmNodeFromCollapse(UserOverride)}, by OSM node id, none by default */
  private final Map<Long, UserOverride<Long>> osmNodeIdsExcludedFromCollapse = new TreeMap<>();

  /**
   * Constructor, nothing collapsed
   */
  protected OsmIntersectionCollapserYamlKeyedSettings() {
  }

  /** Get the mode types collapsed onto one node per consolidated intersection
   *
   * @return collapsed mode types, unmodifiable, empty for none
   */
  @SettingsRead(key = "collapsedModeTypes")
  public Set<PredefinedModeType> getCollapsedModeTypes() {
    return Collections.unmodifiableSet(collapsedModeTypes);
  }

  /** Set the mode types to collapse onto one node per consolidated intersection, replacing those set before. For each
   * spatial intersection, a centre node is added; links used only by these modes end there,
   * and the links inside the intersection lose access for them. A mode type not part of the parsed network is ignored.
   * None by default, so the parsed detail is kept
   *
   * @param modeTypes to collapse, none to collapse nothing
   */
  @SettingsWrite(key = "collapsedModeTypes", level = SettingsKeyLevel.STANDARD,
      description = "PLANit mode types to collapse onto one node per consolidated intersection, such as " +
          "[PEDESTRIAN, BICYCLE], replacing those set before, none by default")
  public void setCollapsedModeTypes(PredefinedModeType... modeTypes) {
    collapsedModeTypes.clear();
    collapsedModeTypes.addAll(List.of(modeTypes));
  }

  /** Exclude an OSM node from collapse. A consolidated intersection with this node as a member node is not collapsed
   * at all, whatever its other member nodes. Example: a junction whose footways are worth keeping in full while the
   * others collapse pedestrians. A node in no consolidated intersection changes nothing, and one no layer has is
   * recorded as not found, with the reason as its detail. Excluding the node again replaces the reason given before
   *
   * @param osmNodeId the OSM node id, with the reason when there is one
   * @throws IllegalArgumentException naming the id, storing nothing, when it is zero or less, which no OSM node has
   */
  @SettingsWrite(key = "osmNodeIdsExcludedFromCollapse", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "OSM nodes whose consolidated intersection is not collapsed, by node id")
  public void excludeOsmNodeFromCollapse(UserOverride<Long> osmNodeId) {
    PlanitOsmUtils.throwIfNotPositiveOsmId(osmNodeId.getValue());
    osmNodeIdsExcludedFromCollapse.put(osmNodeId.getValue(), osmNodeId);
  }

  /** Every OSM node excluded from collapse with its reason, see {@link #excludeOsmNodeFromCollapse(UserOverride)}
   *
   * @return the excluded nodes by OSM node id, in increasing node id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmNodeIdsExcludedFromCollapse")
  public Map<Long, UserOverride<Long>> getOsmNodeIdsExcludedFromCollapse() {
    return Collections.unmodifiableMap(osmNodeIdsExcludedFromCollapse);
  }

  /**
   * Log the settings
   *
   * @param level to indent the values at
   */
  @NoSettingsKey(reason = "logs the settings")
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue("Modes collapsed onto one node per intersection",
        LoggingUtils.noneIfEmpty(getCollapsedModeTypes().stream().map(PredefinedModeType::value)
            .collect(Collectors.toList())), level));
    LOGGER.info(LoggingUtils.settingsValue("OSM nodes excluded from collapse",
        LoggingUtils.noneIfEmpty(getOsmNodeIdsExcludedFromCollapse().keySet()), level));
  }
}

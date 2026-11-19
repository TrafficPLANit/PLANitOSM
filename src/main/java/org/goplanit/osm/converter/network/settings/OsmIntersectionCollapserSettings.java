package org.goplanit.osm.converter.network.settings;

import java.util.Objects;
import java.util.stream.Collectors;

import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionCollapserSettings;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;

/**
 * Settings of collapsing the consolidated intersections of an OSM network onto one node for some modes: the mode
 * types to collapse and the OSM nodes excluded from collapse. The modes of the network do not exist yet when settings
 * are made, so the PLANit collapse settings are built from these once they do, see
 * {@link #createIntersectionCollapserSettings(Modes)}. All are empty by default, so nothing is collapsed. The keys a
 * settings file can set are on {@link OsmIntersectionCollapserYamlKeyedSettings}
 * <p>
 * Collapsing trades the detail of a junction's crossings and footways for one node per junction, for modes whose way
 * through a junction a study does not need, such as pedestrians in a car model; the nodes excluded keep that detail
 * at the junctions where it matters
 * </p>
 *
 * @author markr
 */
public class OsmIntersectionCollapserSettings extends OsmIntersectionCollapserYamlKeyedSettings {

  /**
   * Constructor, nothing collapsed
   */
  public OsmIntersectionCollapserSettings() {
  }

  /** Verify if any mode type is set to collapse
   *
   * @return true when any is set, false otherwise
   */
  public boolean hasCollapsedModes() {
    return !getCollapsedModeTypes().isEmpty();
  }

  /** Same as {@link #excludeOsmNodeFromCollapse(UserOverride)} for each node, without reasons. Each call adds to the
   * nodes excluded before
   *
   * @param osmNodeIds the OSM node ids to exclude from collapse
   * @throws IllegalArgumentException naming the first id that is zero or less, the nodes before it excluded
   */
  @CoveredBySettingsKey("osmNodeIdsExcludedFromCollapse")
  public void addOsmNodeIdsExcludedFromCollapse(long... osmNodeIds) {
    for (long osmNodeId : osmNodeIds) {
      excludeOsmNodeFromCollapse(UserOverride.of(osmNodeId));
    }
  }

  /** Build the PLANit collapse settings from these, once the modes of the network exist: the modes of the mode types
   * the network has, and the nodes excluded by the external ids PLANit gives the nodes it parses from OSM, their OSM
   * node ids
   *
   * @param networkModes the modes of the parsed network
   * @return the PLANit collapse settings
   */
  public IntersectionCollapserSettings createIntersectionCollapserSettings(Modes networkModes) {
    var intersectionCollapserSettings = new IntersectionCollapserSettings();
    intersectionCollapserSettings.setCollapsedModes(
        getCollapsedModeTypes().stream().map(networkModes::get).filter(Objects::nonNull).collect(Collectors.toList()));
    /* the external id PLANit gives a node it parses from OSM is its OSM node id */
    intersectionCollapserSettings.addNodeIdsExcludedFromCollapse(StringUtils.toStrings(
        getOsmNodeIdsExcludedFromCollapse().keySet().stream().mapToLong(Long::longValue).toArray()),
        IdMapperType.EXTERNAL_ID);
    return intersectionCollapserSettings;
  }
}

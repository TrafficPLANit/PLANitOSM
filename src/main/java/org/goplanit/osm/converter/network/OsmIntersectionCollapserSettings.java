package org.goplanit.osm.converter.network;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Modes;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionCollapserSettings;

/**
 * Settings of collapsing the consolidated intersections of an OSM network onto one node for some modes: the modes to
 * collapse, given as predefined mode types, as OSM modes or both, and the OSM nodes excluded from collapse. The modes
 * of the network do not exist yet when settings are made, so the PLANit collapse settings are built from these once
 * they do, see {@link #createIntersectionCollapserSettings(Function, Modes)}. All are empty by default, so nothing is
 * collapsed
 *
 * @author markr
 */
public class OsmIntersectionCollapserSettings {

  /** see {@link #setCollapsedModeTypes(PredefinedModeType...)} */
  private final Set<PredefinedModeType> collapsedModeTypes = new TreeSet<>();

  /** see {@link #setCollapsedOsmModes(String...)} */
  private final Set<String> collapsedOsmModes = new TreeSet<>();

  /** see {@link #addOsmNodeIdsExcludedFromCollapse(long...)} */
  private final Set<Long> osmNodeIdsExcludedFromCollapse = new TreeSet<>();

  /**
   * Constructor, nothing collapsed
   */
  public OsmIntersectionCollapserSettings() {
  }

  /** Get the mode types collapsed onto one node per consolidated intersection
   *
   * @return collapsed mode types, unmodifiable, empty for none
   */
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
  public void setCollapsedModeTypes(PredefinedModeType... modeTypes) {
    collapsedModeTypes.clear();
    collapsedModeTypes.addAll(List.of(modeTypes));
  }

  /** Get the OSM modes collapsed onto one node per consolidated intersection
   *
   * @return collapsed OSM modes, unmodifiable, empty for none
   */
  public Set<String> getCollapsedOsmModes() {
    return Collections.unmodifiableSet(collapsedOsmModes);
  }

  /** Set the OSM modes to collapse onto one node per consolidated intersection, replacing those set before, as
   * {@link #setCollapsedModeTypes(PredefinedModeType...)} does for mode types. Each OSM mode collapses the PLANit mode
   * it is mapped to in the network settings, such as foot to pedestrians. An OSM mode mapped to no mode of the parsed
   * network is ignored. None by default
   *
   * @param osmModes to collapse, none to collapse nothing
   */
  public void setCollapsedOsmModes(String... osmModes) {
    collapsedOsmModes.clear();
    collapsedOsmModes.addAll(List.of(osmModes));
  }

  /** Verify if any mode is set to collapse, as a mode type or as an OSM mode
   *
   * @return true when any is set, false otherwise
   */
  public boolean hasCollapsedModes() {
    return !collapsedModeTypes.isEmpty() || !collapsedOsmModes.isEmpty();
  }

  /** Get the OSM node ids excluded from collapse
   *
   * @return OSM node ids, unmodifiable, empty for none
   */
  public Set<Long> getOsmNodeIdsExcludedFromCollapse() {
    return Collections.unmodifiableSet(osmNodeIdsExcludedFromCollapse);
  }

  /** Exclude OSM nodes from collapse. A consolidated intersection with one of these nodes as a member node is not
   * collapsed at all, whatever its other member nodes. Example: a junction whose footways are worth keeping in full
   * while the others collapse pedestrians. A node not in the network, or in no consolidated intersection, changes
   * nothing. None by default. Each call adds to the nodes excluded before
   *
   * @param osmNodeIds the OSM node ids to exclude from collapse
   */
  public void addOsmNodeIdsExcludedFromCollapse(long... osmNodeIds) {
    for (long osmNodeId : osmNodeIds) {
      osmNodeIdsExcludedFromCollapse.add(osmNodeId);
    }
  }

  /** Build the PLANit collapse settings from these, once the modes of the network exist: the modes of the mode types
   * and of the OSM modes, those the network has, and the nodes excluded by the external ids PLANit gives the nodes it
   * parses from OSM, their OSM node ids
   *
   * @param modeTypeOfOsmMode the mode type an OSM mode is mapped to, null when none
   * @param networkModes the modes of the parsed network
   * @return the PLANit collapse settings
   */
  public IntersectionCollapserSettings createIntersectionCollapserSettings(
      Function<String, PredefinedModeType> modeTypeOfOsmMode, Modes networkModes) {
    var modeTypes = new TreeSet<>(collapsedModeTypes);
    collapsedOsmModes.stream().map(modeTypeOfOsmMode).filter(Objects::nonNull).forEach(modeTypes::add);
    var intersectionCollapserSettings = new IntersectionCollapserSettings();
    intersectionCollapserSettings.setCollapsedModes(
        modeTypes.stream().map(networkModes::get).filter(Objects::nonNull).collect(Collectors.toList()));
    /* the external id PLANit gives a node it parses from OSM is its OSM node id */
    intersectionCollapserSettings.addNodeIdsExcludedFromCollapse(StringUtils.toStrings(
        osmNodeIdsExcludedFromCollapse.stream().mapToLong(Long::longValue).toArray()), IdMapperType.EXTERNAL_ID);
    return intersectionCollapserSettings;
  }
}

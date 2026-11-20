package org.goplanit.osm.converter.network.settings;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.goplanit.algorithms.intersection.GreenRatioAndSaturationFlowFactorSettings;
import org.goplanit.osm.converter.network.OsmTrafficSignalDirection;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidatorSettings;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsSection;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The intersection settings a settings file can set: only the methods of its keys, each write with the read of the
 * same key. {@link OsmIntersectionSettings} extends it with what code needs on top.
 * <p>
 * Keeping the keys in a class of their own shows in one place all a settings file can say about intersections, while
 * the methods made for code, such as an override without a reason, stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmIntersectionYamlKeyedSettings {

  /**
   * The lists of nodes of the consolidation settings given by external id, as groups of OSM node ids with their
   * reasons: the external id PLANit gives a node it parses from OSM is its OSM node id
   *
   * @param nodeIdLists of the consolidation settings, of any kind of id
   * @return the groups of the lists given by external id, in the order listed
   */
  private static List<UserOverride<Set<Long>>> toOsmNodeIdGroupsOfListsByExternalId(
      List<UserOverride<Pair<Set<String>, IdMapperType>>> nodeIdLists) {
    return nodeIdLists.stream().filter(list -> list.getValue().second() == IdMapperType.EXTERNAL_ID)
        .map(list -> list.<Set<Long>>map(nodeIds -> nodeIds.first().stream().map(Long::valueOf)
            .collect(Collectors.toCollection(LinkedHashSet::new))))
        .collect(Collectors.toList());
  }

  /** parse intersections from traffic signals, see {@link #activateParser(boolean)} */
  private boolean isParserActive = DEFAULT_INTERSECTIONS_PARSER_ACTIVE;

  /** maximum distance from a vehicle signal to the junction it controls, see
   * {@link #setMaximumDistanceVehicleSignalToJunctionMeters(double)} */
  private double maximumDistanceVehicleSignalToJunctionMeters = DEFAULT_MAX_VEHICLE_SIGNAL_TO_JUNCTION_DISTANCE_M;

  /** maximum distance for a signalised pedestrian crossing to count as part of a signalised junction, see
   * {@link #setMaximumDistanceSignalisedCrossingToJunctionMeters(double)} */
  private double maximumDistanceSignalisedCrossingToJunctionMeters =
      DEFAULT_MAX_SIGNALISED_CROSSING_TO_JUNCTION_DISTANCE_M;

  /** break links at isolated signals, see {@link #setBreakLinksAtIsolatedSignals(boolean)} */
  private boolean breakLinksAtIsolatedSignals = DEFAULT_BREAK_LINKS_AT_ISOLATED_SIGNALS;

  /** consolidate the parsed intersections, see {@link #setConsolidateIntersections(boolean)} */
  private boolean consolidateIntersections = DEFAULT_CONSOLIDATE_INTERSECTIONS;

  /** compute the green ratios and saturation flow factors of the approaches, see
   * {@link #setComputeGreenRatiosAndSaturationFlowFactors(boolean)} */
  private boolean computeGreenRatiosAndSaturationFlowFactors = DEFAULT_COMPUTE_GREEN_RATIOS_AND_SATURATION_FLOW_FACTORS;

  /** see {@link #getIntersectionConsolidatorSettings()} */
  private IntersectionConsolidatorSettings intersectionConsolidatorSettings = new IntersectionConsolidatorSettings();

  /** see {@link #getIntersectionCollapserSettings()} */
  private OsmIntersectionCollapserSettings intersectionCollapserSettings = new OsmIntersectionCollapserSettings();

  /** see {@link #getGreenRatioAndSaturationFlowFactorSettings()} */
  private GreenRatioAndSaturationFlowFactorSettings greenRatioAndSaturationFlowFactorSettings =
      new GreenRatioAndSaturationFlowFactorSettings();

  /** see {@link #overrideTrafficSignalDirection(long, UserOverride)}, by OSM node id, none by default */
  private final Map<Long, UserOverride<OsmTrafficSignalDirection>> trafficSignalDirectionOverrides = new TreeMap<>();

  /** see {@link #overrideTrafficSignalKind(long, UserOverride)}, by OSM node id, none by default */
  private final Map<Long, UserOverride<OsmTrafficSignalKind>> trafficSignalKindOverrides = new TreeMap<>();

  /**
   * Constructor, every key at its default
   */
  protected OsmIntersectionYamlKeyedSettings() {
  }

  /** Put every key held here back to its default
   */
  protected void reset() {
    isParserActive = DEFAULT_INTERSECTIONS_PARSER_ACTIVE;
    maximumDistanceVehicleSignalToJunctionMeters = DEFAULT_MAX_VEHICLE_SIGNAL_TO_JUNCTION_DISTANCE_M;
    maximumDistanceSignalisedCrossingToJunctionMeters = DEFAULT_MAX_SIGNALISED_CROSSING_TO_JUNCTION_DISTANCE_M;
    breakLinksAtIsolatedSignals = DEFAULT_BREAK_LINKS_AT_ISOLATED_SIGNALS;
    consolidateIntersections = DEFAULT_CONSOLIDATE_INTERSECTIONS;
    computeGreenRatiosAndSaturationFlowFactors = DEFAULT_COMPUTE_GREEN_RATIOS_AND_SATURATION_FLOW_FACTORS;
    intersectionConsolidatorSettings = new IntersectionConsolidatorSettings();
    intersectionCollapserSettings = new OsmIntersectionCollapserSettings();
    greenRatioAndSaturationFlowFactorSettings = new GreenRatioAndSaturationFlowFactorSettings();
    trafficSignalDirectionOverrides.clear();
    trafficSignalKindOverrides.clear();
  }

  /** by default intersections are parsed from traffic signals */
  public static final boolean DEFAULT_INTERSECTIONS_PARSER_ACTIVE = true;

  /** by default a vehicle signal controls a junction at most 50 m away */
  public static final double DEFAULT_MAX_VEHICLE_SIGNAL_TO_JUNCTION_DISTANCE_M = 50;

  /** by default a signalised pedestrian crossing at most 30 m before a signalised junction is part of it */
  public static final double DEFAULT_MAX_SIGNALISED_CROSSING_TO_JUNCTION_DISTANCE_M = 30;

  /** by default links are not broken at isolated signals, so parsing does not change the network */
  public static final boolean DEFAULT_BREAK_LINKS_AT_ISOLATED_SIGNALS = false;

  /** by default the parsed intersections are not consolidated, so each signalised node keeps its own intersection */
  public static final boolean DEFAULT_CONSOLIDATE_INTERSECTIONS = false;

  /** by default no green ratio or saturation flow factor is computed, so the approaches have no values */
  public static final boolean DEFAULT_COMPUTE_GREEN_RATIOS_AND_SATURATION_FLOW_FACTORS = false;

  /** Activate or deactivate parsing intersections from traffic signals: junctions controlled by vehicle signals, and
   * signalised pedestrian crossings. When deactivated, the parsed network has no intersections and is otherwise the
   * same
   *
   * @param activate true to parse intersections, false otherwise
   */
  @SettingsWrite(key = "active", level = SettingsKeyLevel.STANDARD, description = "Parse intersections from the " +
      "traffic signals: junctions controlled by vehicle signals, and signalised pedestrian crossings")
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }

  /** Verify if intersections are parsed from traffic signals
   *
   * @return true when parsed, false otherwise
   */
  @SettingsRead(key = "active")
  public boolean isParserActive() {
    return isParserActive;
  }

  /** Get the maximum distance from a vehicle signal to the junction it controls
   *
   * @return distance in meters
   */
  @SettingsRead(key = "maximumDistanceVehicleSignalToJunctionMeters")
  public double getMaximumDistanceVehicleSignalToJunctionMeters() {
    return maximumDistanceVehicleSignalToJunctionMeters;
  }

  /** Set the maximum distance from a vehicle signal to the junction it controls, along the road. A signal farther
   * from any junction is ignored
   *
   * @param distanceMeters distance to use
   */
  @SettingsWrite(key = "maximumDistanceVehicleSignalToJunctionMeters", level = SettingsKeyLevel.RARE,
      description = "Furthest a vehicle signal may be from the junction it controls, along the road, in metres")
  public void setMaximumDistanceVehicleSignalToJunctionMeters(double distanceMeters) {
    this.maximumDistanceVehicleSignalToJunctionMeters = distanceMeters;
  }

  /** Get the maximum distance for a signalised pedestrian crossing to count as part of a signalised junction
   *
   * @return distance in meters
   */
  @SettingsRead(key = "maximumDistanceSignalisedCrossingToJunctionMeters")
  public double getMaximumDistanceSignalisedCrossingToJunctionMeters() {
    return maximumDistanceSignalisedCrossingToJunctionMeters;
  }

  /** Set the maximum distance for a signalised pedestrian crossing to count as part of a signalised junction. A
   * crossing whose walking or cycling infrastructure is not parsed, on a road leading within this distance to a
   * signalised junction along one of its approaches, is part of that junction and ignored. Beyond it, the crossing is
   * isolated, see {@link #setBreakLinksAtIsolatedSignals(boolean)}
   *
   * @param distanceMeters distance to use
   */
  @SettingsWrite(key = "maximumDistanceSignalisedCrossingToJunctionMeters", level = SettingsKeyLevel.RARE,
      description = "Furthest a signalised pedestrian crossing may be from a signalised junction, along one of its " +
          "approaches, to count as part of it, in metres")
  public void setMaximumDistanceSignalisedCrossingToJunctionMeters(double distanceMeters) {
    this.maximumDistanceSignalisedCrossingToJunctionMeters = distanceMeters;
  }

  /** Verify if links are broken at isolated signals, see {@link #setBreakLinksAtIsolatedSignals(boolean)}
   *
   * @return true when broken, false otherwise
   */
  @SettingsRead(key = "breakLinksAtIsolatedSignals")
  public boolean isBreakLinksAtIsolatedSignals() {
    return breakLinksAtIsolatedSignals;
  }

  /** Break links at isolated signals, so each becomes an intersection of its own: a signalised pedestrian crossing
   * that is isolated becomes its own crossing intersection. A crossing is isolated when it is not part of a
   * signalised junction: its walking or cycling infrastructure is not
   * parsed, and it is farther than {@link #getMaximumDistanceSignalisedCrossingToJunctionMeters()} from any
   * signalised junction along one of its approaches. Crossings that are part of a signalised junction are never
   * broken at, and crossings whose walking or cycling infrastructure is parsed are always their own intersection,
   * whatever this setting. A vehicle signal with no junction within
   * {@link #getMaximumDistanceVehicleSignalToJunctionMeters()} controls something other than cars crossing, and
   * counts as an isolated crossing too. An isolated crossing already on a node needs no link broken, but only becomes
   * its own intersection when this is on. A ramp meter is an isolated signal too: the link is broken at it, and it
   * becomes its own ramp meter intersection, which consolidation may join into a junction within reach, as it does a
   * crossing. When off, isolated crossings and ramp meters are ignored and counted
   *
   * @param flag to set
   */
  @SettingsWrite(key = "breakLinksAtIsolatedSignals", level = SettingsKeyLevel.UNCOMMON,
      description = "Break links at isolated signals, so each becomes an intersection of its own: crossings away " +
          "from any signalised junction, and ramp meters")
  public void setBreakLinksAtIsolatedSignals(boolean flag) {
    this.breakLinksAtIsolatedSignals = flag;
  }

  /** Verify if the parsed intersections are consolidated
   *
   * @return true when consolidated, false otherwise
   */
  @SettingsRead(key = "consolidateIntersections")
  public boolean isConsolidateIntersections() {
    return consolidateIntersections;
  }

  /** Consolidate the parsed intersections: the intersections of the signalised nodes of one junction are joined into
   * one, crossings next to it join it, and links reached only through it become internal. It runs after dangling
   * subnetworks are removed, and only when the intersection parser is active, see {@link #activateParser(boolean)}.
   * The settings of {@link #getIntersectionConsolidatorSettings()} tune it
   *
   * @param flag to set
   */
  @SettingsWrite(key = "consolidateIntersections", level = SettingsKeyLevel.COMMON,
      description = "Join the parsed intersections of one junction into one, with the crossings next to it")
  public void setConsolidateIntersections(boolean flag) {
    this.consolidateIntersections = flag;
  }

  /** Verify if the green ratios and saturation flow factors of the approaches are computed
   *
   * @return true when computed, false otherwise
   */
  @SettingsRead(key = "computeGreenRatiosAndSaturationFlowFactors")
  public boolean isComputeGreenRatiosAndSaturationFlowFactors() {
    return computeGreenRatiosAndSaturationFlowFactors;
  }

  /** Compute the green ratios and saturation flow factors of the approaches of the signalised intersections, as an
   * estimate from each junction's layout, since OSM holds no signal timing: opposite approaches share a phase, and the
   * phases share the usable green time of the cycle. For example, at a crossing of two equal roads each approach gets
   * a green ratio of about 0.44 of a 90 s cycle. It runs once intersections are consolidated and before they are
   * collapsed, and only when the intersection parser is active, see {@link #activateParser(boolean)}. Without
   * consolidation it still runs, with one warning: a junction parsed as several intersections is timed as several
   * junctions, so its values can be far off. For example, a crossing of two divided roads parsed as four intersections
   * has two approaches at each, one of them the carriageway between two of its nodes, so traffic through it meets two
   * reduced approaches instead of one. The settings of
   * {@link #getGreenRatioAndSaturationFlowFactorSettings()} tune it. Off by default
   *
   * @param flag to set
   */
  @SettingsWrite(key = "computeGreenRatiosAndSaturationFlowFactors", level = SettingsKeyLevel.COMMON,
      description = "Estimate the green ratios and saturation flow factors of the approaches of signalised " +
          "intersections from their layout")
  public void setComputeGreenRatiosAndSaturationFlowFactors(boolean flag) {
    this.computeGreenRatiosAndSaturationFlowFactors = flag;
  }

  /** The settings that tune consolidation: reach, scoring values and warning lengths, each at its default until
   * changed. What to collapse once consolidated is set apart, see {@link #getIntersectionCollapserSettings()}. The
   * nodes to join into one intersection, or to keep in separate intersections, are added with
   * {@link OsmIntersectionSettings#addOsmNodeIdsToJoinIntoOneIntersection(long...)} and
   * {@link OsmIntersectionSettings#addOsmNodeIdsToKeepInSeparateIntersections(long...)}
   *
   * @return consolidation settings, to change in place
   */
  @SettingsSection(key = "intersectionConsolidator", level = SettingsKeyLevel.UNCOMMON,
      description = "How the nodes of one junction are found and joined into one intersection")
  public IntersectionConsolidatorSettings getIntersectionConsolidatorSettings() {
    return intersectionConsolidatorSettings;
  }

  /** Join OSM nodes into one intersection when consolidating, in place of the grouping consolidation chooses, see
   * {@link IntersectionConsolidatorSettings#addNodeIdsToJoinIntoOneIntersection(java.util.Collection, IdMapperType,
   * String)}. The intersections holding the nodes are joined, so any node of an intersection will do. A node no layer
   * has is recorded as not found, with the reason as its detail. Each call adds one group, to the consolidation
   * settings held, see {@link #getIntersectionConsolidatorSettings()}
   *
   * @param osmNodeIds the OSM node ids of the nodes to join, with the reason when there is one
   * @throws IllegalArgumentException naming the ids, storing nothing, when there are fewer than two different ones or
   *     one is zero or less, which no OSM node has
   */
  @SettingsWrite(key = "osmNodeIdsToJoinIntoOneIntersection", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "Groups of OSM nodes to join into one intersection when consolidating, one group per entry")
  public void joinOsmNodesIntoOneIntersection(UserOverride<Set<Long>> osmNodeIds) {
    osmNodeIds.getValue().forEach(PlanitOsmUtils::throwIfNotPositiveOsmId);
    intersectionConsolidatorSettings.addNodeIdsToJoinIntoOneIntersection(
        StringUtils.toStrings(osmNodeIds.getValue().toArray()), IdMapperType.EXTERNAL_ID, osmNodeIds.getReason());
  }

  /** The groups of OSM nodes to join into one intersection, each with its reason, see
   * {@link #joinOsmNodesIntoOneIntersection(UserOverride)}
   *
   * @return the groups in the order added, empty when none
   */
  @SettingsRead(key = "osmNodeIdsToJoinIntoOneIntersection")
  public List<UserOverride<Set<Long>>> getOsmNodeIdsToJoinIntoOneIntersection() {
    return toOsmNodeIdGroupsOfListsByExternalId(intersectionConsolidatorSettings.getNodeIdsToJoinIntoOneIntersection());
  }

  /** Keep OSM nodes in separate intersections when consolidating, see
   * {@link IntersectionConsolidatorSettings#addNodeIdsToKeepInSeparateIntersections(java.util.Collection,
   * IdMapperType, String)}. No grouping step puts two of the nodes into one intersection. A node no layer has is
   * recorded as not found, with the reason as its detail. Each call adds one group, to the consolidation settings
   * held, see {@link #getIntersectionConsolidatorSettings()}
   *
   * @param osmNodeIds the OSM node ids of the nodes to keep apart, with the reason when there is one
   * @throws IllegalArgumentException naming the ids, storing nothing, when there are fewer than two different ones or
   *     one is zero or less, which no OSM node has
   */
  @SettingsWrite(key = "osmNodeIdsToKeepInSeparateIntersections", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "Groups of OSM nodes to keep in separate intersections when consolidating, one group per entry")
  public void keepOsmNodesInSeparateIntersections(UserOverride<Set<Long>> osmNodeIds) {
    osmNodeIds.getValue().forEach(PlanitOsmUtils::throwIfNotPositiveOsmId);
    intersectionConsolidatorSettings.addNodeIdsToKeepInSeparateIntersections(
        StringUtils.toStrings(osmNodeIds.getValue().toArray()), IdMapperType.EXTERNAL_ID, osmNodeIds.getReason());
  }

  /** The groups of OSM nodes to keep in separate intersections, each with its reason, see
   * {@link #keepOsmNodesInSeparateIntersections(UserOverride)}
   *
   * @return the groups in the order added, empty when none
   */
  @SettingsRead(key = "osmNodeIdsToKeepInSeparateIntersections")
  public List<UserOverride<Set<Long>>> getOsmNodeIdsToKeepInSeparateIntersections() {
    return toOsmNodeIdGroupsOfListsByExternalId(
        intersectionConsolidatorSettings.getNodeIdsToKeepInSeparateIntersections());
  }

  /** The settings of collapsing the consolidated intersections onto one node for some modes: the mode types to
   * collapse and the OSM nodes excluded from collapse, nothing collapsed until changed
   *
   * @return collapse settings, to change in place
   */
  @SettingsSection(key = "intersectionCollapser", level = SettingsKeyLevel.UNCOMMON,
      description = "The modes whose joined intersections are collapsed into one node, and the nodes never collapsed")
  public OsmIntersectionCollapserSettings getIntersectionCollapserSettings() {
    return intersectionCollapserSettings;
  }

  /** The settings that tune computing the green ratios and saturation flow factors: the algorithm, the cycle time,
   * the time lost at each phase change and the values used for isolated crossings and approaches without a signal of
   * their own, each at its default until changed
   *
   * @return green ratio and saturation flow factor settings, to change in place
   */
  @SettingsSection(key = "greenRatioAndSaturationFlowFactor", level = SettingsKeyLevel.UNCOMMON,
      description = "How the green ratio and saturation flow factor of each signalised turn are worked out")
  public GreenRatioAndSaturationFlowFactorSettings getGreenRatioAndSaturationFlowFactorSettings() {
    return greenRatioAndSaturationFlowFactorSettings;
  }

  /** Override the direction of travel along its OSM way that a traffic signal applies to, in place of the one tagged
   * with traffic_signals:direction, or of none tagged. For a signal that faces the wrong way, such as one tagged
   * backward where its stop line faces forward, or an untagged one whose nearest junction is the wrong one. Overriding
   * it again replaces the direction overridden before, its reason included. An OSM node id that no layer has as a
   * traffic signal is recorded as not found, with the reason as its detail
   *
   * @param osmNodeId the OSM node id of the traffic signal
   * @param direction it applies to, forward or backward along its OSM way, with the reason when there is one
   */
  @SettingsWrite(key = "trafficSignalDirectionOverrides", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "Directions traffic signals apply to, by OSM node id, in place of their tags")
  public void overrideTrafficSignalDirection(long osmNodeId, UserOverride<OsmTrafficSignalDirection> direction) {
    trafficSignalDirectionOverrides.put(osmNodeId, direction);
  }

  /** The directions of travel overridden for traffic signals, each with its reason, see
   * {@link #overrideTrafficSignalDirection(long, UserOverride)}
   *
   * @return the directions overridden by OSM node id, unmodifiable, empty when none
   */
  @SettingsRead(key = "trafficSignalDirectionOverrides")
  public Map<Long, UserOverride<OsmTrafficSignalDirection>> getTrafficSignalDirectionOverrides() {
    return Collections.unmodifiableMap(trafficSignalDirectionOverrides);
  }

  /** Override the kind of signal a node is handled as: a vehicle signal, a crossing signal or a ramp meter only, or no
   * signal at all, in place of what its tags say. For example, a ramp meter tagged as a plain signal, overridden as a
   * ramp meter, is handled as one. It also works for a node without any signal tags, to add a signal OSM lacks, such as
   * a missing stop line, and it reduces a signal tagged as both kinds to one. For example, a plain vehicle signal that
   * only stops traffic for trams crossing the road, overridden as a crossing signal, gets the crossing's green ratio,
   * rather than making the junction beyond it signalised. Overriding it again replaces the kind overridden before, its
   * reason included. An OSM node id that no layer has as a node on its ways is recorded as not found, with the reason
   * as its detail
   *
   * @param osmNodeId the OSM node id of the node
   * @param kind to handle it as, with the reason when there is one
   */
  @SettingsWrite(key = "trafficSignalKindOverrides", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "Kinds of traffic signal nodes are handled as, by OSM node id, in place of their tags")
  public void overrideTrafficSignalKind(long osmNodeId, UserOverride<OsmTrafficSignalKind> kind) {
    trafficSignalKindOverrides.put(osmNodeId, kind);
  }

  /** The kinds of signal overridden for nodes, each with its reason, see
   * {@link #overrideTrafficSignalKind(long, UserOverride)}
   *
   * @return the kinds overridden by OSM node id, unmodifiable, empty when none
   */
  @SettingsRead(key = "trafficSignalKindOverrides")
  public Map<Long, UserOverride<OsmTrafficSignalKind>> getTrafficSignalKindOverrides() {
    return Collections.unmodifiableMap(trafficSignalKindOverrides);
  }
}

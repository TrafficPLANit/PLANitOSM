package org.goplanit.osm.converter.network;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.algorithms.intersection.GreenRatioAndSaturationFlowFactorSettings;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.goplanit.utils.id.IdMapperType;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidatorSettings;

/**
 * Settings of parsing intersections from the traffic signals of an OSM network: junctions controlled by vehicle
 * signals, and signalised pedestrian crossings. They say how far a signal may lie from the junction it controls,
 * whether links are broken at isolated signalised crossings, and whether the parsed intersections are consolidated and
 * then collapsed for some modes. Activated like the highway, railway and waterway settings, and active by default
 *
 * @author markr
 */
public class OsmIntersectionSettings {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmIntersectionSettings.class.getCanonicalName());

  /** The lists of node ids of the consolidation settings as logged, "none" when empty
   *
   * @param nodeIdLists the lists of node ids, each with the kind of id they are
   * @return the node ids, or none
   */
  private static Object toLoggedNodeIds(List<Pair<Set<String>, IdMapperType>> nodeIdLists) {
    return nodeIdLists.isEmpty() ? "none" : nodeIdLists.stream().map(Pair::first).collect(Collectors.toList());
  }

  /** parse intersections from traffic signals, see {@link #activateParser(boolean)} */
  private boolean isParserActive = DEFAULT_INTERSECTIONS_PARSER_ACTIVE;

  /** maximum distance from a vehicle signal to the junction it controls, see
   * {@link #setMaximumDistanceVehicleSignalToJunctionInMeters(double)} */
  private double maximumDistanceVehicleSignalToJunctionInMeters = DEFAULT_MAX_VEHICLE_SIGNAL_TO_JUNCTION_DISTANCE_M;

  /** maximum distance for a signalised pedestrian crossing to count as part of a signalised junction, see
   * {@link #setMaximumDistanceSignalisedCrossingToJunctionInMeters(double)} */
  private double maximumDistanceSignalisedCrossingToJunctionInMeters =
      DEFAULT_MAX_SIGNALISED_CROSSING_TO_JUNCTION_DISTANCE_M;

  /** break links at isolated signalised pedestrian crossings, see
   * {@link #setBreakLinksAtIsolatedSignalisedCrossings(boolean)} */
  private boolean breakLinksAtIsolatedSignalisedCrossings = DEFAULT_BREAK_LINKS_AT_ISOLATED_SIGNALISED_CROSSINGS;

  /** consolidate the parsed intersections, see {@link #setConsolidateIntersections(boolean)} */
  private boolean consolidateIntersections = DEFAULT_CONSOLIDATE_INTERSECTIONS;

  /** see {@link #getIntersectionConsolidatorSettings()} */
  private IntersectionConsolidatorSettings intersectionConsolidatorSettings = new IntersectionConsolidatorSettings();

  /** see {@link #getIntersectionCollapserSettings()} */
  private OsmIntersectionCollapserSettings intersectionCollapserSettings = new OsmIntersectionCollapserSettings();

  /** compute the green ratios and saturation flow factors of the approaches, see
   * {@link #setComputeGreenRatiosAndSaturationFlowFactors(boolean)} */
  private boolean computeGreenRatiosAndSaturationFlowFactors = DEFAULT_COMPUTE_GREEN_RATIOS_AND_SATURATION_FLOW_FACTORS;

  /** see {@link #getGreenRatioAndSaturationFlowFactorSettings()} */
  private GreenRatioAndSaturationFlowFactorSettings greenRatioAndSaturationFlowFactorSettings =
      new GreenRatioAndSaturationFlowFactorSettings();

  /** see {@link #overrideTrafficSignalDirection(long, OsmTrafficSignalDirection)}, by OSM node id, none by default */
  private final Map<Long, OsmTrafficSignalDirection> trafficSignalDirectionOverrides = new TreeMap<>();

  /** see {@link #overrideTrafficSignalKind(long, OsmTrafficSignalKind)}, by OSM node id, none by default */
  private final Map<Long, OsmTrafficSignalKind> trafficSignalKindOverrides = new TreeMap<>();

  /** by default intersections are parsed from traffic signals */
  public static final boolean DEFAULT_INTERSECTIONS_PARSER_ACTIVE = true;

  /** by default a vehicle signal controls a junction at most 50 m away */
  public static final double DEFAULT_MAX_VEHICLE_SIGNAL_TO_JUNCTION_DISTANCE_M = 50;

  /** by default a signalised pedestrian crossing at most 30 m before a signalised junction is part of it */
  public static final double DEFAULT_MAX_SIGNALISED_CROSSING_TO_JUNCTION_DISTANCE_M = 30;

  /** by default links are not broken at isolated signalised pedestrian crossings, so parsing does not change the
   * network */
  public static final boolean DEFAULT_BREAK_LINKS_AT_ISOLATED_SIGNALISED_CROSSINGS = false;

  /** by default the parsed intersections are not consolidated, so each signalised node keeps its own intersection */
  public static final boolean DEFAULT_CONSOLIDATE_INTERSECTIONS = false;

  /** by default no green ratio or saturation flow factor is computed, so the approaches have no values */
  public static final boolean DEFAULT_COMPUTE_GREEN_RATIOS_AND_SATURATION_FLOW_FACTORS = false;

  /**
   * Constructor, every setting at its default
   */
  public OsmIntersectionSettings() {
  }

  /** Activate or deactivate parsing intersections from traffic signals: junctions controlled by vehicle signals, and
   * signalised pedestrian crossings. When deactivated, the parsed network has no intersections and is otherwise the
   * same
   *
   * @param activate true to parse intersections, false otherwise
   */
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }

  /** Verify if intersections are parsed from traffic signals
   *
   * @return true when parsed, false otherwise
   */
  public boolean isParserActive() {
    return isParserActive;
  }

  /**
   * Log the settings, the consolidation and collapse settings each as a section of its own
   *
   * @param level to indent the values at
   */
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue("Intersection parser activated", isParserActive(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum vehicle signal to junction distance (m)",
        getMaximumDistanceVehicleSignalToJunctionInMeters(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum signalised crossing to junction distance (m)",
        getMaximumDistanceSignalisedCrossingToJunctionInMeters(), level));
    LOGGER.info(LoggingUtils.settingsValue("Break links at isolated signalised crossings",
        isBreakLinksAtIsolatedSignalisedCrossings(), level));
    LOGGER.info(LoggingUtils.settingsValue("Traffic signal directions overridden",
        trafficSignalDirectionOverrides.isEmpty() ? "none" : trafficSignalDirectionOverrides, level));
    LOGGER.info(LoggingUtils.settingsValue("Traffic signal kinds overridden",
        trafficSignalKindOverrides.isEmpty() ? "none" : trafficSignalKindOverrides, level));
    LOGGER.info(LoggingUtils.settingsValue("Consolidate intersections", isConsolidateIntersections(), level));
    if(isConsolidateIntersections()){
      int consolidationLevel = level + 2;
      var consolidatorSettings = getIntersectionConsolidatorSettings();
      LOGGER.info(LoggingUtils.settingsSection("Intersection consolidation", level + 1));
      LOGGER.info(LoggingUtils.settingsValue("Maximum joining reach (m)",
          consolidatorSettings.getMaximumJoiningReachMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Minimum coverage gain per member",
          consolidatorSettings.getMinimumCoverageGainPerMember(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Coverage tie margin",
          consolidatorSettings.getCoverageTieMargin(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum parsed intersections to try every subdivision",
          consolidatorSettings.getMaximumParsedIntersectionsToTryEverySubdivision(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Subdivisions kept per search step",
          consolidatorSettings.getSubdivisionsKeptPerSearchStep(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Pull-in margin beyond radius (m)",
          consolidatorSettings.getPullInMarginBeyondRadiusMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum pull-in connection length (m)",
          consolidatorSettings.getMaximumPullInConnectionLengthMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Internal link length warning (m)",
          consolidatorSettings.getInternalLinkLengthWarningMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum distance reached only through intersection (m)",
          consolidatorSettings.getMaximumDistanceReachedOnlyThroughIntersectionMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum distance slip lane crossing to junction (m)",
          consolidatorSettings.getMaximumDistanceSlipLaneCrossingToJunctionMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum distance between crossings side by side (m)",
          consolidatorSettings.getMaximumDistanceBetweenCrossingsSideBySideMeters(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Nodes to join into one intersection",
          toLoggedNodeIds(consolidatorSettings.getNodeIdsToJoinIntoOneIntersection()), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Nodes to keep in separate intersections",
          toLoggedNodeIds(consolidatorSettings.getNodeIdsToKeepInSeparateIntersections()), consolidationLevel));
    }
    int collapseLevel = level + 2;
    var collapserSettings = getIntersectionCollapserSettings();
    LOGGER.info(LoggingUtils.settingsSection("Intersection collapse", level + 1));
    LOGGER.info(LoggingUtils.settingsValue("Modes collapsed onto one node per intersection",
        collapserSettings.getCollapsedModeTypes().isEmpty() ? "none" : collapserSettings.getCollapsedModeTypes()
            .stream().map(PredefinedModeType::value).collect(Collectors.joining(", ")), collapseLevel));
    LOGGER.info(LoggingUtils.settingsValue("OSM modes collapsed onto one node per intersection",
        collapserSettings.getCollapsedOsmModes().isEmpty() ? "none" : collapserSettings.getCollapsedOsmModes(),
        collapseLevel));
    LOGGER.info(LoggingUtils.settingsValue("OSM nodes excluded from collapse",
        collapserSettings.getOsmNodeIdsExcludedFromCollapse().isEmpty() ? "none" :
            collapserSettings.getOsmNodeIdsExcludedFromCollapse(), collapseLevel));
    LOGGER.info(LoggingUtils.settingsValue("Compute green ratios and saturation flow factors",
        isComputeGreenRatiosAndSaturationFlowFactors(), level));
    if(isComputeGreenRatiosAndSaturationFlowFactors()){
      int greenRatioLevel = level + 2;
      var greenRatioSettings = getGreenRatioAndSaturationFlowFactorSettings();
      LOGGER.info(LoggingUtils.settingsSection("Green ratios and saturation flow factors", level + 1));
      LOGGER.info(LoggingUtils.settingsValue("Algorithm", greenRatioSettings.getAlgorithmType(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Cycle time (s)",
          greenRatioSettings.getCycleTimeSeconds(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Lost time per phase change (s)",
          greenRatioSettings.getLostTimePerPhaseChangeSeconds(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Isolated crossing green ratio",
          greenRatioSettings.getIsolatedCrossingGreenRatio(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Same axis approach tolerance (degrees)",
          greenRatioSettings.getSameAxisApproachToleranceDegrees(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum number of phases",
          greenRatioSettings.getMaximumNumberOfPhases(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Approach without signal saturation flow factor",
          greenRatioSettings.getApproachWithoutSignalSaturationFlowFactor(), greenRatioLevel));
    }
  }

  /** Put every setting back to its default, the consolidation and collapse settings included
   */
  public void reset() {
    this.isParserActive = DEFAULT_INTERSECTIONS_PARSER_ACTIVE;
    this.maximumDistanceVehicleSignalToJunctionInMeters = DEFAULT_MAX_VEHICLE_SIGNAL_TO_JUNCTION_DISTANCE_M;
    this.maximumDistanceSignalisedCrossingToJunctionInMeters = DEFAULT_MAX_SIGNALISED_CROSSING_TO_JUNCTION_DISTANCE_M;
    this.breakLinksAtIsolatedSignalisedCrossings = DEFAULT_BREAK_LINKS_AT_ISOLATED_SIGNALISED_CROSSINGS;
    this.consolidateIntersections = DEFAULT_CONSOLIDATE_INTERSECTIONS;
    this.intersectionConsolidatorSettings = new IntersectionConsolidatorSettings();
    this.intersectionCollapserSettings = new OsmIntersectionCollapserSettings();
    this.computeGreenRatiosAndSaturationFlowFactors = DEFAULT_COMPUTE_GREEN_RATIOS_AND_SATURATION_FLOW_FACTORS;
    this.greenRatioAndSaturationFlowFactorSettings = new GreenRatioAndSaturationFlowFactorSettings();
    this.trafficSignalDirectionOverrides.clear();
    this.trafficSignalKindOverrides.clear();
  }

  /** Get the maximum distance from a vehicle signal to the junction it controls
   *
   * @return distance in meters
   */
  public double getMaximumDistanceVehicleSignalToJunctionInMeters() {
    return maximumDistanceVehicleSignalToJunctionInMeters;
  }

  /** Set the maximum distance from a vehicle signal to the junction it controls, along the road. A signal farther
   * from any junction is ignored
   *
   * @param distanceMeters distance to use
   */
  public void setMaximumDistanceVehicleSignalToJunctionInMeters(double distanceMeters) {
    this.maximumDistanceVehicleSignalToJunctionInMeters = distanceMeters;
  }

  /** Get the maximum distance for a signalised pedestrian crossing to count as part of a signalised junction
   *
   * @return distance in meters
   */
  public double getMaximumDistanceSignalisedCrossingToJunctionInMeters() {
    return maximumDistanceSignalisedCrossingToJunctionInMeters;
  }

  /** Set the maximum distance for a signalised pedestrian crossing to count as part of a signalised junction. A
   * crossing whose walking or cycling infrastructure is not parsed, on a road leading within this distance to a
   * signalised junction along one of its approaches, is part of that junction and ignored. Beyond it, the crossing is
   * isolated, see {@link #setBreakLinksAtIsolatedSignalisedCrossings(boolean)}
   *
   * @param distanceMeters distance to use
   */
  public void setMaximumDistanceSignalisedCrossingToJunctionInMeters(double distanceMeters) {
    this.maximumDistanceSignalisedCrossingToJunctionInMeters = distanceMeters;
  }

  /** Verify if links are broken at isolated signalised pedestrian crossings
   *
   * @return true when broken, false otherwise
   */
  public boolean isBreakLinksAtIsolatedSignalisedCrossings() {
    return breakLinksAtIsolatedSignalisedCrossings;
  }

  /** Break links at isolated signalised pedestrian crossings, so each becomes its own crossing intersection. A
   * crossing is isolated when it is not part of a signalised junction: its walking or cycling infrastructure is not
   * parsed, and it is farther than {@link #getMaximumDistanceSignalisedCrossingToJunctionInMeters()} from any
   * signalised junction along one of its approaches. Crossings that are part of a signalised junction are never
   * broken at, and crossings whose walking or cycling infrastructure is parsed are always their own intersection,
   * whatever this setting. A vehicle signal with no junction within
   * {@link #getMaximumDistanceVehicleSignalToJunctionInMeters()} controls something other than cars crossing, and
   * counts as an isolated crossing too. An isolated crossing already on a node needs no link broken, but only becomes
   * its own intersection when this is on. When off, isolated crossings are ignored and counted
   *
   * @param flag to set
   */
  public void setBreakLinksAtIsolatedSignalisedCrossings(boolean flag) {
    this.breakLinksAtIsolatedSignalisedCrossings = flag;
  }

  /** Verify if the parsed intersections are consolidated
   *
   * @return true when consolidated, false otherwise
   */
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
  public void setConsolidateIntersections(boolean flag) {
    this.consolidateIntersections = flag;
  }

  /** Consolidate the parsed intersections or not, see {@link #setConsolidateIntersections(boolean)}, with the given
   * settings in place of those held
   *
   * @param flag to set
   * @param consolidatorSettings to tune consolidation with
   */
  public void setConsolidateIntersections(boolean flag, IntersectionConsolidatorSettings consolidatorSettings) {
    setConsolidateIntersections(flag);
    this.intersectionConsolidatorSettings = consolidatorSettings;
  }

  /** The settings that tune consolidation: reach, scoring values and warning lengths, each at its default until
   * changed. What to collapse once consolidated is set apart, see {@link #getIntersectionCollapserSettings()}. The
   * nodes to join into one intersection, or to keep in separate intersections, are added with
   * {@link #addOsmNodeIdsToJoinIntoOneIntersection(long...)} and
   * {@link #addOsmNodeIdsToKeepInSeparateIntersections(long...)}
   *
   * @return consolidation settings, to change in place
   */
  public IntersectionConsolidatorSettings getIntersectionConsolidatorSettings() {
    return intersectionConsolidatorSettings;
  }

  /** The settings of collapsing the consolidated intersections onto one node for some modes: the modes to collapse and
   * the OSM nodes excluded from collapse, nothing collapsed until changed
   *
   * @return collapse settings, to change in place
   */
  public OsmIntersectionCollapserSettings getIntersectionCollapserSettings() {
    return intersectionCollapserSettings;
  }

  /** Verify if the green ratios and saturation flow factors of the approaches are computed
   *
   * @return true when computed, false otherwise
   */
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
   * reduced approaches instead of one. The settings of {@link #getGreenRatioAndSaturationFlowFactorSettings()} tune
   * it. Off by default
   *
   * @param flag to set
   */
  public void setComputeGreenRatiosAndSaturationFlowFactors(boolean flag) {
    this.computeGreenRatiosAndSaturationFlowFactors = flag;
  }

  /** The settings that tune computing the green ratios and saturation flow factors: the algorithm, the cycle time,
   * the time lost at each phase change and the values used for isolated crossings and approaches without a signal of
   * their own, each at its default until changed
   *
   * @return green ratio and saturation flow factor settings, to change in place
   */
  public GreenRatioAndSaturationFlowFactorSettings getGreenRatioAndSaturationFlowFactorSettings() {
    return greenRatioAndSaturationFlowFactorSettings;
  }

  /** Add a list of OSM nodes to join into one intersection when consolidating, see
   * {@link IntersectionConsolidatorSettings#addNodeIdsToJoinIntoOneIntersection(java.util.Collection, IdMapperType)}.
   * The intersections holding the nodes are joined, so any node of an intersection will do. A list needs at least two
   * different nodes. Each call adds one list, to the consolidation settings held, see
   * {@link #getIntersectionConsolidatorSettings()}
   *
   * @param osmNodeIds the OSM node ids of the nodes to join
   */
  public void addOsmNodeIdsToJoinIntoOneIntersection(long... osmNodeIds) {
    /* the external id PLANit gives a node it parses from OSM is its OSM node id */
    intersectionConsolidatorSettings.addNodeIdsToJoinIntoOneIntersection(
        StringUtils.toStrings(osmNodeIds), IdMapperType.EXTERNAL_ID);
  }

  /** Add a list of OSM nodes to keep in separate intersections when consolidating, see
   * {@link IntersectionConsolidatorSettings#addNodeIdsToKeepInSeparateIntersections(java.util.Collection,
   * IdMapperType)}. A list needs at least two different nodes. Each call adds one list, to the consolidation settings
   * held, see {@link #getIntersectionConsolidatorSettings()}
   *
   * @param osmNodeIds the OSM node ids of the nodes to keep in separate intersections
   */
  public void addOsmNodeIdsToKeepInSeparateIntersections(long... osmNodeIds) {
    /* the external id PLANit gives a node it parses from OSM is its OSM node id */
    intersectionConsolidatorSettings.addNodeIdsToKeepInSeparateIntersections(
        StringUtils.toStrings(osmNodeIds), IdMapperType.EXTERNAL_ID);
  }

  /** Override the direction of travel along its OSM way that a traffic signal applies to, in place of the one tagged
   * with traffic_signals:direction, or of none tagged. For a signal that faces the wrong way, such as one tagged
   * backward where its stop line faces forward, or an untagged one whose nearest junction is the wrong one. Overriding
   * it again replaces the direction overridden before. An OSM node id that no layer has as a traffic signal is recorded
   * as not found
   *
   * @param osmNodeId the OSM node id of the traffic signal
   * @param direction it applies to, forward or backward along its OSM way
   */
  public void overrideTrafficSignalDirection(long osmNodeId, OsmTrafficSignalDirection direction) {
    trafficSignalDirectionOverrides.put(osmNodeId, direction);
  }

  /** The direction of travel overridden for a traffic signal, see
   * {@link #overrideTrafficSignalDirection(long, OsmTrafficSignalDirection)}
   *
   * @param osmNodeId the OSM node id of the traffic signal
   * @return the direction overridden, null when not overridden
   */
  public OsmTrafficSignalDirection getTrafficSignalDirectionOverride(long osmNodeId) {
    return trafficSignalDirectionOverrides.get(osmNodeId);
  }

  /** The directions of travel overridden for traffic signals, see
   * {@link #overrideTrafficSignalDirection(long, OsmTrafficSignalDirection)}
   *
   * @return the directions overridden by OSM node id, unmodifiable, empty when none
   */
  public Map<Long, OsmTrafficSignalDirection> getTrafficSignalDirectionOverrides() {
    return Collections.unmodifiableMap(trafficSignalDirectionOverrides);
  }

  /** Override the kind of signal a node is handled as: a vehicle signal or a crossing signal only, or no signal at all,
   * in place of what its tags say. It also works for a node without any signal tags, to add a signal OSM lacks, such as
   * a missing stop line, and it reduces a signal tagged as both kinds to one. For example, a plain vehicle signal that
   * only stops traffic for trams crossing the road, overridden as a crossing signal, gets the crossing's green ratio,
   * rather than making the junction beyond it signalised. Overriding it again replaces the kind overridden before. An
   * OSM node id that no layer has as a node on its ways is recorded as not found
   *
   * @param osmNodeId the OSM node id of the node
   * @param kind to handle it as
   */
  public void overrideTrafficSignalKind(long osmNodeId, OsmTrafficSignalKind kind) {
    trafficSignalKindOverrides.put(osmNodeId, kind);
  }

  /** The kind of signal overridden for a node, see {@link #overrideTrafficSignalKind(long, OsmTrafficSignalKind)}
   *
   * @param osmNodeId the OSM node id of the node
   * @return the kind overridden, null when not overridden
   */
  public OsmTrafficSignalKind getTrafficSignalKindOverride(long osmNodeId) {
    return trafficSignalKindOverrides.get(osmNodeId);
  }

  /** The kinds of signal overridden for nodes, see {@link #overrideTrafficSignalKind(long, OsmTrafficSignalKind)}
   *
   * @return the kinds overridden by OSM node id, unmodifiable, empty when none
   */
  public Map<Long, OsmTrafficSignalKind> getTrafficSignalKindOverrides() {
    return Collections.unmodifiableMap(trafficSignalKindOverrides);
  }

  /** Verify if a node is handled as a kind of traffic signal: as its kind is overridden, see
   * {@link #overrideTrafficSignalKind(long, OsmTrafficSignalKind)}, otherwise as its tags say, see
   * {@link OsmTrafficSignalTags#isVehicleTrafficSignal(Map)},
   * {@link OsmTrafficSignalTags#isCrossingTrafficSignals(Map)} and, for no signal,
   * {@link OsmTrafficSignalTags#isTrafficSignals(Map)}. A signal tagged as both a vehicle signal and a crossing signal
   * is handled as both, unless overridden
   *
   * @param osmNodeId the OSM node id of the node
   * @param tags of the node
   * @param kind to verify
   * @return true when handled as that kind, false otherwise
   */
  public boolean isHandledAsTrafficSignalOfKind(long osmNodeId, Map<String, String> tags, OsmTrafficSignalKind kind) {
    var overridden = trafficSignalKindOverrides.get(osmNodeId);
    if (overridden != null) {
      return overridden == kind;
    }
    switch (kind) {
      case VEHICLE_SIGNAL:
        return OsmTrafficSignalTags.isVehicleTrafficSignal(tags);
      case CROSSING_SIGNAL:
        return OsmTrafficSignalTags.isCrossingTrafficSignals(tags);
      default:
        return !OsmTrafficSignalTags.isTrafficSignals(tags);
    }
  }

  /** The OSM node ids with a traffic signal override of any kind, see
   * {@link #overrideTrafficSignalDirection(long, OsmTrafficSignalDirection)} and
   * {@link #overrideTrafficSignalKind(long, OsmTrafficSignalKind)}
   *
   * @return the node ids in increasing order, each once, empty when none
   */
  public Set<Long> getOsmNodeIdsWithTrafficSignalOverride() {
    var osmNodeIds = new TreeSet<Long>(trafficSignalDirectionOverrides.keySet());
    osmNodeIds.addAll(trafficSignalKindOverrides.keySet());
    return Collections.unmodifiableSet(osmNodeIds);
  }
}

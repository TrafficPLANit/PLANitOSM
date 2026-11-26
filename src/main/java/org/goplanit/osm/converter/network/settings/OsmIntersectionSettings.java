package org.goplanit.osm.converter.network.settings;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.osm.converter.network.OsmTrafficSignalDirection;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.tags.OsmTrafficSignalTags;
import org.goplanit.utils.misc.CollectionUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.NoSettingsKey;

/**
 * Settings of parsing intersections from the traffic signals of an OSM network: junctions controlled by vehicle
 * signals, and signalised pedestrian crossings. They say how far a signal may lie from the junction it controls,
 * whether links are broken at isolated signals, and whether the parsed intersections are consolidated and
 * then collapsed for some modes. Activated like the highway, railway and waterway settings, and active by default.
 * The keys a settings file can set are on {@link OsmIntersectionYamlKeyedSettings}
 * <p>
 * OSM maps a traffic signal as a node, often a short way before the junction it controls, and now and then facing the
 * wrong way or missing. These settings say how far from a junction a signal still counts, what happens at signals on
 * their own, and how the intersections found are joined and simplified; the overrides fix single signals by OSM node
 * where the map is wrong
 * </p>
 *
 * @author markr
 */
public class OsmIntersectionSettings extends OsmIntersectionYamlKeyedSettings {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmIntersectionSettings.class.getCanonicalName());

  /**
   * Constructor, every setting at its default
   */
  public OsmIntersectionSettings() {
  }

  /**
   * Log the settings, the consolidation and collapse settings each as a section of its own
   *
   * @param level to indent the values at
   */
  @NoSettingsKey(reason = "logs the settings")
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsValue("Intersection parser activated", isParserActive(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum vehicle signal to junction distance (m)",
        getMaximumDistanceVehicleSignalToJunctionMeters(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum signalised crossing to junction distance (m)",
        getMaximumDistanceSignalisedCrossingToJunctionMeters(), level));
    LOGGER.info(LoggingUtils.settingsValue("Break links at isolated signals",
        isBreakLinksAtIsolatedSignals(), level));
    LOGGER.info(LoggingUtils.settingsValue("Traffic signal directions overridden",
        LoggingUtils.noneIfEmpty(getTrafficSignalDirectionOverrides()), level));
    LOGGER.info(LoggingUtils.settingsValue("Traffic signal kinds overridden",
        LoggingUtils.noneIfEmpty(getTrafficSignalKindOverrides()), level));
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
      LOGGER.info(LoggingUtils.settingsValue("Include active modes in movements",
          consolidatorSettings.isIncludeActiveModesInMovements(), consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Nodes to join into one intersection",
          LoggingUtils.noneIfEmpty(UserOverride.valuesOf(getOsmNodeIdsToJoinIntoOneIntersection())),
          consolidationLevel));
      LOGGER.info(LoggingUtils.settingsValue("Nodes to keep in separate intersections",
          LoggingUtils.noneIfEmpty(UserOverride.valuesOf(getOsmNodeIdsToKeepInSeparateIntersections())),
          consolidationLevel));
    }
    int collapseLevel = level + 2;
    var collapserSettings = getIntersectionCollapserSettings();
    LOGGER.info(LoggingUtils.settingsSection("Intersection collapse", level + 1));
    LOGGER.info(LoggingUtils.settingsValue("Modes collapsed onto one node per intersection",
        LoggingUtils.noneIfEmpty(collapserSettings.getCollapsedModeTypes().stream().map(PredefinedModeType::value)
            .collect(Collectors.toList())), collapseLevel));
    LOGGER.info(LoggingUtils.settingsValue("OSM nodes excluded from collapse",
        LoggingUtils.noneIfEmpty(collapserSettings.getOsmNodeIdsExcludedFromCollapse().keySet()), collapseLevel));
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
      LOGGER.info(LoggingUtils.settingsValue("Ramp meter green ratio",
          greenRatioSettings.getRampMeterGreenRatio(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Same axis approach tolerance (degrees)",
          greenRatioSettings.getSameAxisApproachToleranceDegrees(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Maximum number of phases",
          greenRatioSettings.getMaximumNumberOfPhases(), greenRatioLevel));
      LOGGER.info(LoggingUtils.settingsValue("Approach without signal saturation flow factor",
          greenRatioSettings.getApproachWithoutSignalSaturationFlowFactor(), greenRatioLevel));
    }
  }

  /** Put every setting back to its default, the consolidation, collapse and green ratio settings included
   */
  @Override
  @NoSettingsKey(reason = "puts every setting back to its default")
  public void reset() {
    super.reset();
  }

  /** Same as {@link #joinOsmNodesIntoOneIntersection(UserOverride)}, without a reason
   *
   * @param osmNodeIds the OSM node ids of the nodes to join
   * @throws IllegalArgumentException naming the ids, storing nothing, when there are fewer than two different ones or
   *     one is zero or less
   */
  @CoveredBySettingsKey("osmNodeIdsToJoinIntoOneIntersection")
  public void addOsmNodeIdsToJoinIntoOneIntersection(long... osmNodeIds) {
    joinOsmNodesIntoOneIntersection(UserOverride.of(CollectionUtils.toOrderedSet(osmNodeIds)));
  }

  /** Same as {@link #keepOsmNodesInSeparateIntersections(UserOverride)}, without a reason
   *
   * @param osmNodeIds the OSM node ids of the nodes to keep in separate intersections
   * @throws IllegalArgumentException naming the ids, storing nothing, when there are fewer than two different ones or
   *     one is zero or less
   */
  @CoveredBySettingsKey("osmNodeIdsToKeepInSeparateIntersections")
  public void addOsmNodeIdsToKeepInSeparateIntersections(long... osmNodeIds) {
    keepOsmNodesInSeparateIntersections(UserOverride.of(CollectionUtils.toOrderedSet(osmNodeIds)));
  }

  /** Same as {@link #overrideTrafficSignalDirection(long, UserOverride)}, without a reason
   *
   * @param osmNodeId the OSM node id of the traffic signal
   * @param direction it applies to, forward or backward along its OSM way
   */
  @CoveredBySettingsKey("trafficSignalDirectionOverrides")
  public void overrideTrafficSignalDirection(long osmNodeId, OsmTrafficSignalDirection direction) {
    overrideTrafficSignalDirection(osmNodeId, UserOverride.of(direction));
  }

  /** The direction of travel overridden for a traffic signal, see
   * {@link #overrideTrafficSignalDirection(long, UserOverride)}
   *
   * @param osmNodeId the OSM node id of the traffic signal
   * @return the direction overridden, without its reason, null when not overridden
   */
  public OsmTrafficSignalDirection getTrafficSignalDirectionOverride(long osmNodeId) {
    return UserOverride.getValueOrNull(getTrafficSignalDirectionOverrides().get(osmNodeId));
  }

  /** Same as {@link #overrideTrafficSignalKind(long, UserOverride)}, without a reason
   *
   * @param osmNodeId the OSM node id of the node
   * @param kind to handle it as
   */
  @CoveredBySettingsKey("trafficSignalKindOverrides")
  public void overrideTrafficSignalKind(long osmNodeId, OsmTrafficSignalKind kind) {
    overrideTrafficSignalKind(osmNodeId, UserOverride.of(kind));
  }

  /** The kind of signal overridden for a node, see {@link #overrideTrafficSignalKind(long, UserOverride)}
   *
   * @param osmNodeId the OSM node id of the node
   * @return the kind overridden, without its reason, null when not overridden
   */
  public OsmTrafficSignalKind getTrafficSignalKindOverride(long osmNodeId) {
    return UserOverride.getValueOrNull(getTrafficSignalKindOverrides().get(osmNodeId));
  }

  /** Verify if a node is handled as a kind of traffic signal: as its kind is overridden, see
   * {@link #overrideTrafficSignalKind(long, OsmTrafficSignalKind)}, otherwise as its tags say, see
   * {@link OsmTrafficSignalTags#isVehicleTrafficSignal(Map)},
   * {@link OsmTrafficSignalTags#isCrossingTrafficSignals(Map)}, {@link OsmTrafficSignalTags#isRampMeter(Map)} and, for
   * no signal, {@link OsmTrafficSignalTags#isTrafficSignals(Map)}. A signal tagged as both a vehicle signal and a crossing signal
   * is handled as both, unless overridden
   *
   * @param osmNodeId the OSM node id of the node
   * @param tags of the node
   * @param kind to verify
   * @return true when handled as that kind, false otherwise
   */
  public boolean isHandledAsTrafficSignalOfKind(long osmNodeId, Map<String, String> tags, OsmTrafficSignalKind kind) {
    var overridden = getTrafficSignalKindOverride(osmNodeId);
    if (overridden != null) {
      return overridden == kind;
    }
    switch (kind) {
      case VEHICLE_SIGNAL:
        return OsmTrafficSignalTags.isVehicleTrafficSignal(tags);
      case CROSSING_SIGNAL:
        return OsmTrafficSignalTags.isCrossingTrafficSignals(tags);
      case RAMP_METER:
        return OsmTrafficSignalTags.isRampMeter(tags);
      default:
        return !OsmTrafficSignalTags.isTrafficSignals(tags);
    }
  }

  /** The OSM node ids with a traffic signal override of any kind, see
   * {@link #overrideTrafficSignalDirection(long, UserOverride)} and
   * {@link #overrideTrafficSignalKind(long, UserOverride)}
   *
   * @return the node ids in increasing order, each once, empty when none
   */
  public Set<Long> getOsmNodeIdsWithTrafficSignalOverride() {
    var osmNodeIds = new TreeSet<Long>(getTrafficSignalDirectionOverrides().keySet());
    osmNodeIds.addAll(getTrafficSignalKindOverrides().keySet());
    return Collections.unmodifiableSet(osmNodeIds);
  }

  /**
   * The reasons given for overriding a node's traffic signal, its kind first, then its direction, such as for a record
   * of the node not found
   *
   * @param osmNodeId the OSM node id
   * @return the reasons, joined by "; ", null when neither override has one
   */
  public String getTrafficSignalOverrideReason(long osmNodeId) {
    return UserOverride.joinReasons(Stream.of(getTrafficSignalKindOverrides().get(osmNodeId),
        getTrafficSignalDirectionOverrides().get(osmNodeId)));
  }

  /** The reasons given for the groups of nodes to join, or to keep apart, that hold an OSM node, such as for a record
   * of the node not found
   *
   * @param osmNodeId the OSM node id
   * @return the reasons, each once, joined by "; ", null when no group holding it has one
   */
  public String getReasonOfOsmNodeListedToJoinOrKeepApart(long osmNodeId) {
    return UserOverride.joinReasons(Stream.concat(getOsmNodeIdsToJoinIntoOneIntersection().stream(),
        getOsmNodeIdsToKeepInSeparateIntersections().stream()).filter(group -> group.getValue().contains(osmNodeId)));
  }
}

package org.goplanit.osm.converter.network;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.algorithms.intersection.GreenRatioAlgorithmFactory;
import org.goplanit.converter.network.NetworkReader;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.MacroscopicNetworkLayerConfigurator;
import org.goplanit.network.MacroscopicNetworkModifierUtils;
import org.goplanit.osm.converter.OsmBoundaryManager;
import org.goplanit.osm.converter.OsmBoundingBoundaryPreProcessingHandler;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.network.handler.OsmNetworkMainProcessingHandler;
import org.goplanit.osm.converter.network.handler.OsmNetworkPreProcessingHandler;
import org.goplanit.osm.converter.network.trafficsignal.OsmTrafficSignalIntegrator;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.Osm4JUtils;
import org.goplanit.osm.util.OsmHandlerUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerUtils;
import org.goplanit.utils.graph.modifier.event.DirectedGraphModifierListener;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidationModifierResult;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidationModifierResult.ApproachOutcome;
import org.goplanit.utils.network.layer.modifier.consolidator.IntersectionConsolidationModifierResult.IntersectionOutcome;
import org.goplanit.utils.network.layer.modifier.event.LinkSegmentTypeModifierListener;
import org.goplanit.utils.network.layers.MacroscopicNetworkLayers;
import org.goplanit.zoning.Zoning;
import org.goplanit.zoning.modifier.event.handler.UpdateConnectoidsOnLinkSegmentChangeHandler;
import org.goplanit.zoning.modifier.event.handler.UpdateConnectoidsOnLinkSegmentChangeResult;
import org.goplanit.zoning.modifier.event.handler.UpdateConnectoidsOnLinkSegmentChangeResult.ConnectoidAccessLoss;
import org.goplanit.zoning.modifier.event.handler.UpdateConnectoidsOnVertexRemovalHandler;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import de.topobyte.osm4j.core.access.OsmReader;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit network instance
 * 
 * @author markr
 *
 */
public class OsmNetworkReader implements NetworkReader {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReader.class.getCanonicalName());
      
  /** the issue each intersection the consolidation lists is recorded as, by what happened to it */
  private static final Map<IntersectionOutcome, OsmPlanitEntityIssue> ISSUE_OF_INTERSECTION_OUTCOME = Map.of(
      IntersectionOutcome.CROSSING_JOINED, OsmPlanitEntityIssue.CROSSING_JOINED_TO_JUNCTION,
      IntersectionOutcome.CROSSING_JOINED_FROM_SLIP_LANE, OsmPlanitEntityIssue.CROSSING_ON_SLIP_LANE_JOINED_TO_JUNCTION,
      IntersectionOutcome.CROSSING_JOINED_SIDE_BY_SIDE, OsmPlanitEntityIssue.CROSSING_JOINED_TO_CROSSING_SIDE_BY_SIDE,
      IntersectionOutcome.JUNCTION_PULLED_IN_WITHIN_RADIUS, OsmPlanitEntityIssue.JUNCTION_PULLED_IN_WITHIN_RADIUS,
      IntersectionOutcome.JUNCTION_PULLED_IN_TO_SALVAGE_APPROACH_WITHOUT_SIGNAL,
      OsmPlanitEntityIssue.JUNCTION_PULLED_IN_TO_SALVAGE_APPROACH_WITHOUT_SIGNAL,
      IntersectionOutcome.JUNCTION_WITH_FEW_SIGNALISED_ARMS, OsmPlanitEntityIssue.JUNCTION_WITH_FEW_SIGNALISED_ARMS);

  /** the issue each approach segment the consolidation lists is recorded as, by what happened to it */
  private static final Map<ApproachOutcome, OsmPlanitEntityIssue> ISSUE_OF_APPROACH_OUTCOME = Map.of(
      ApproachOutcome.MADE_INTERNAL, OsmPlanitEntityIssue.APPROACH_MADE_INTERNAL_BY_CONSOLIDATION,
      ApproachOutcome.ADDED_WITHOUT_SIGNAL_ON_SLIP_LANE, OsmPlanitEntityIssue.APPROACH_ON_SLIP_LANE_ADDED_WITHOUT_SIGNAL,
      ApproachOutcome.ADDED_WITHOUT_SIGNAL_NOT_ON_SLIP_LANE,
      OsmPlanitEntityIssue.APPROACH_NOT_ON_SLIP_LANE_ADDED_WITHOUT_SIGNAL,
      ApproachOutcome.ADDED_WITH_SIGNAL_TAGGED_ON_NODE_AHEAD,
      OsmPlanitEntityIssue.APPROACH_ADDED_WITH_SIGNAL_TAGGED_ON_NODE_AHEAD);

  /** the issue each connectoid access loss of pruning is recorded as, by what was lost */
  private static final Map<ConnectoidAccessLoss.Kind, OsmPlanitEntityIssue> ISSUE_OF_CONNECTOID_ACCESS_LOSS = Map.of(
      ConnectoidAccessLoss.Kind.ALLOWED_MODE, OsmPlanitEntityIssue.CONNECTOID_ALLOWED_MODE_LOST_BY_PRUNING,
      ConnectoidAccessLoss.Kind.ACCESS_LINK_SEGMENT,
      OsmPlanitEntityIssue.CONNECTOID_ACCESS_LINK_SEGMENT_LOST_BY_PRUNING,
      ConnectoidAccessLoss.Kind.ACCESS_ZONE_ENTRY, OsmPlanitEntityIssue.CONNECTOID_ACCESS_ZONE_ENTRY_LOST_BY_PRUNING,
      ConnectoidAccessLoss.Kind.CONNECTOID, OsmPlanitEntityIssue.CONNECTOID_LOST_BY_PRUNING);

  /** network reader data tracked during parsing */
  private final OsmNetworkReaderData networkData;

  /** the network to populate */
  private final PlanitOsmNetwork osmNetworkToPopulate;
  
  /** settings to use */
  private final OsmNetworkReaderSettings settings;

  /** when set, this reader does not log its own settings, see {@link #suppressSettingsLogging()} */
  private boolean suppressSettingsLogging = false;
  
  /**
   * Helper to create an OSM4jReader, handler for a given stage and perform the parsing
   *
   * @param stage to apply
   */
  private void createHandlerAndRead(OsmNetworkPreProcessingHandler.Stage stage){
    /* reader to parse the actual file or source location */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(settings.getInputSource());
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for preprocessing network, aborting");
      return;
    }
    var osmHandler = new OsmNetworkPreProcessingHandler(
        stage,
        getOsmNetworkToPopulate(),
        networkData,
        settings);
    OsmHandlerUtils.readWithHandler(osmReader, osmHandler);
  }

  /**
   * Log some information about this reader's configuration
   */
  private void logInfo() {

    if(suppressSettingsLogging){
      /* a composing reader reports the configuration on our behalf */
      return;
    }
    getSettings().logSettings();

  }

  /**
   * If a bounding area has been configured, preform preprocessing to extract the extent of the boundary for which
   * we need to perform the actual parsing, before doing any other parsing
   *
   */
  private void performBoundingAreaExtraction() {

    OsmBoundaryManager boundaryManager = new OsmBoundaryManager(getSettings().getBoundingArea());
    if(!getSettings().hasBoundingBoundary()){
      return;
    }

    /* identify OSM relation by name if bounding area is specified by name rather than an explicit bounding box */
    if(boundaryManager.isConfigured() && !boundaryManager.isComplete()) {

      /* STAGE 1 - BOUNDARY RELATION IDENTIFICATION */
      {
        LOGGER.info(String.format("Boundary identification: Locating bounding boundary \"%s\"",
            settings.getBoundingArea().getBoundaryName()));
        OsmBoundingBoundaryPreProcessingHandler.createHandlerAndRead(
            settings.getInputSource(),
            OsmBoundingBoundaryPreProcessingHandler.Stage.ONE_IDENTIFY_BOUNDARY_BY_NAME,
            boundaryManager);
      }

      /* STAGE 2 - REGULAR PREPROCESSING */
      {
        LOGGER.info("Boundary identification: Locating OSM ways part of network bounding boundary");
        OsmBoundingBoundaryPreProcessingHandler.createHandlerAndRead(
            settings.getInputSource(),
            OsmBoundingBoundaryPreProcessingHandler.Stage.TWO_IDENTIFY_WAYS_FOR_BOUNDARY,
            boundaryManager);
      }

      /* STAGE 3 - FINALISE BOUNDING BOUNDARY */
      {
        LOGGER.info("Boundary identification: Finalising network bounding boundary, constructing polygon " +
            "from OSM ways' OSM nodes");
        OsmBoundingBoundaryPreProcessingHandler.createHandlerAndRead(
            settings.getInputSource(),
            OsmBoundingBoundaryPreProcessingHandler.Stage.THREE_FINALISE_BOUNDARY_BY_NAME,
            boundaryManager);
      }
    }


    if(boundaryManager.isConfigured() && !boundaryManager.isComplete()){
      LOGGER.severe("Boundary identification: User configured bounding area, but no valid boundary " +
          "could be constructed during pre-processing, this shouldn't happen");
    }

    networkData.setBoundingArea(boundaryManager.getCompleteBoundingArea());
  }

  /** Perform preprocessing if needed, identify the OSM entities eligible for processing
   */
  private void doPreprocessing(){

    /* STAGE 1 -
     * identify OSM ways that are eligible from a network perspective (are they roads etc.). If a bounding area
     * is specified then
     * they should at least have one node within the bounding area to be considered */
    LOGGER.info("Pre-processing: Identifying spatially eligible network OSM entities");
    createHandlerAndRead(OsmNetworkPreProcessingHandler.Stage.ONE_PREPROCESSING_SPATIALLY_NODES_WAYS);

    /* STAGE 2 - Identify any OSM ways/nodes that are considered special cases even if they fall outside
    bounding polygon
    * e.g., ferries. */
    if(getSettings().hasBoundingBoundary())
    {
      LOGGER.info("Preprocessing: Identifying special cases to keep outside bounding area (if any)");
      createHandlerAndRead(OsmNetworkPreProcessingHandler.Stage.TWO_PREPROCESS_SPECIAL_CASE_NODES_WAYS);
    }else{
      LOGGER.info("Preprocessing: Skip stage 2 no bounding area defined");
    }

    /* STAGE 3 - add nodes that are part of OSM ways that were deemed eligible for parsing in STAGE 1 */
    {
      LOGGER.info("Preprocessing: reducing memory footprint, finalising OSM nodes and ways required for " +
          "network building");
      createHandlerAndRead(OsmNetworkPreProcessingHandler.Stage.THREE_FINALISE_PREPROCESSING_NODES_WAYS);
    }

  }

  /** Perform main processing of OSM network reader
   */
  private void doMainProcessing() {

    OsmReader osmReader = Osm4JUtils.createOsm4jReader(settings.getInputSource());
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for network, aborting");
      return;
    }   
    OsmNetworkMainProcessingHandler osmHandler =
        new OsmNetworkMainProcessingHandler(getOsmNetworkToPopulate(), networkData, settings);
    OsmHandlerUtils.readWithHandler(osmReader, osmHandler);
  }

  /**
   * Record what consolidating the intersections of a layer did with the PLANit entity diagnostics, each entity once
   *
   * @param result of the consolidation
   */
  private void registerConsolidationResult(IntersectionConsolidationModifierResult result) {
    var diagnostics = networkData.getPlanitEntityDiagnostics();
    result.getReplacedIntersections().forEach(replaced -> diagnostics.registerIssue(
        OsmPlanitEntityIssue.INTERSECTION_REPLACED_BY_CONSOLIDATION, replaced.first().getExternalId(),
        "by " + replaced.second().getExternalId()));
    result.getIntersectionClustersSubdividedStepByStep().forEach(cluster -> diagnostics.registerIssue(
        OsmPlanitEntityIssue.INTERSECTION_CLUSTER_SUBDIVIDED_STEP_BY_STEP, cluster.get(0).getExternalId(),
        cluster.size() + " parsed intersections"));
    result.getJoinedNodes().forEach(node -> diagnostics.registerIssue(
        OsmPlanitEntityIssue.NODE_JOINED_BY_CONSOLIDATION, node.getExternalId()));
    result.getInternalLinksLongerThanWarningLength().forEach(link -> diagnostics.registerIssue(
        OsmPlanitEntityIssue.INTERNAL_LINK_LONGER_THAN_WARNING_LENGTH, link.getExternalId(),
        String.format("%.0f m", link.getLengthMeters())));
    result.getLinksReachedOnlyThroughIntersection().forEach(reached -> diagnostics.registerIssue(
        OsmPlanitEntityIssue.LINK_REACHED_ONLY_THROUGH_INTERSECTION,
        reached.getEnteringSegment().getParent().getExternalId(),
        String.format("%.0f m%s", reached.getDistanceMeters(), reached.isWithSignal() ? ", with a signal" : "")));
    for (var outcome : IntersectionOutcome.values()) {
      result.getIntersections(outcome).forEach(intersection -> diagnostics.registerIssue(
          ISSUE_OF_INTERSECTION_OUTCOME.get(outcome), intersection.getExternalId()));
    }
    for (var outcome : ApproachOutcome.values()) {
      result.getApproaches(outcome).forEach(segment -> diagnostics.registerIssue(ISSUE_OF_APPROACH_OUTCOME.get(outcome),
          segment.getParent().getExternalId(), outcome == ApproachOutcome.MADE_INTERNAL ?
              "from node " + segment.getUpstreamNode().getExternalId() :
              "into node " + segment.getDownstreamNode().getExternalId()));
    }
  }

  /**
   * Record what the connectoids lost when pruning dangling subnetworks with the PLANit entity diagnostics, each loss
   * once, by the connectoid's external id, the OSM node of the stop
   *
   * @param result what the connectoids lost, see {@link UpdateConnectoidsOnLinkSegmentChangeHandler}
   */
  private void registerConnectoidAccessLosses(UpdateConnectoidsOnLinkSegmentChangeResult result) {
    var diagnostics = networkData.getPlanitEntityDiagnostics();
    for (var loss : result.getAccessLosses()) {
      var detail = new StringBuilder("zone ").append(loss.getAccessZoneEntry().getAccessZone().getExternalId())
          .append(", ").append(loss.getAccessZoneEntry().getType());
      if (loss.getMode() != null) {
        detail.append(", mode ").append(loss.getMode().getName());
      }
      if (loss.getAccessLinkSegment() != null && loss.getAccessLinkSegment().getParent() != null) {
        detail.append(", link ").append(loss.getAccessLinkSegment().getParent().getExternalId());
      }
      diagnostics.registerIssue(ISSUE_OF_CONNECTOID_ACCESS_LOSS.get(loss.getKind()),
          loss.getConnectoid().getExternalId(), detail.toString());
    }
  }

  /**
   * Record, with the PLANit entity diagnostics, each OSM node id that every layer lists as not found. Each layer holds
   * only the nodes of its own modes, so a node of one layer is not found in the others
   *
   * @param issue to record each with
   * @param notFoundOfEachLayer per layer, the OSM node ids it did not find
   */
  private void registerNotFoundInEveryLayer(OsmPlanitEntityIssue issue, List<List<String>> notFoundOfEachLayer) {
    if(notFoundOfEachLayer.isEmpty()) {
      return;
    }
    var notFoundInEveryLayer = new LinkedHashSet<>(notFoundOfEachLayer.get(0));
    notFoundOfEachLayer.forEach(notFoundInEveryLayer::retainAll);
    var diagnostics = networkData.getPlanitEntityDiagnostics();
    notFoundInEveryLayer.forEach(osmNodeId -> diagnostics.registerIssue(issue, osmNodeId));
  }

  /**
   * Record, with the PLANit entity diagnostics, each OSM node id with a traffic signal override in the settings that no
   * layer has, see {@link OsmTrafficSignalIntegrator#getOsmNodeIdsOverriddenNotFound()}. Nothing is recorded when
   * intersections are not parsed
   */
  private void registerTrafficSignalOverridesNotFound() {
    if(!settings.isIntersectionParserActive()) {
      return;
    }
    registerNotFoundInEveryLayer(OsmPlanitEntityIssue.TRAFFIC_SIGNAL_OVERRIDE_NOT_FOUND,
        networkData.getLayerParsers().values().stream()
            .map(layerParser -> layerParser.getTrafficSignalIntegrator().getOsmNodeIdsOverriddenNotFound())
            .collect(Collectors.toList()));
  }

  /**
   * Collapse the modes the settings set onto one node per spatial intersection of each layer, once consolidated, see
   * {@link OsmIntersectionSettings#getIntersectionCollapserSettings()}. The modes now exist, so the PLANit collapse
   * settings are built from the OSM ones, mapping OSM modes as the network settings do. What it did is logged, and a
   * node excluded from collapse is recorded as not found with the PLANit entity diagnostics only when no layer has it
   *
   * @param crs of the network
   */
  private void collapseIntersections(CoordinateReferenceSystem crs) {
    var osmCollapserSettings = settings.getIntersectionSettings().getIntersectionCollapserSettings();
    if(!osmCollapserSettings.hasCollapsedModes()) {
      return;
    }
    var collapserSettings = osmCollapserSettings.createIntersectionCollapserSettings(
        settings::getMappedPlanitModeType, getOsmNetworkToPopulate().getModes());
    var notFoundOfEachLayer = new ArrayList<List<String>>();
    for(var layer : networkData.getLayerParsers().keySet()) {
      var collapseResult = layer.getLayerModifier().collapseIntersectionsToNonSpatialForModes(
          layer.getIntersections().streamSpatial().collect(Collectors.toList()), collapserSettings, crs);
      LOGGER.info(String.format("Collapsed intersections: %s", collapseResult));
      notFoundOfEachLayer.add(collapseResult.getExcludedNodeIdsNotFound());
    }
    registerNotFoundInEveryLayer(OsmPlanitEntityIssue.NODE_EXCLUDED_FROM_COLLAPSE_NOT_FOUND, notFoundOfEachLayer);
  }

  /**
   * Consolidate the intersections of each layer, see
   * {@link OsmIntersectionSettings#setConsolidateIntersections(boolean)}. A segment turning internal gets a signal when
   * it has a signal of its own, see {@link OsmTrafficSignalIntegrator#hasSignalOfItsOwn}, or when it is a footway or
   * cycleway segment into a signalised crossing. What it did is logged, and recorded with the PLANit entity
   * diagnostics. A node listed in the settings is recorded as not found only when no layer has it
   *
   * @param crs of the network
   */
  private void consolidateIntersections(CoordinateReferenceSystem crs) {
    var notFoundOfEachLayer = new ArrayList<List<String>>();
    for(var entry : networkData.getLayerParsers().entrySet()) {
      var trafficSignalIntegrator = entry.getValue().getTrafficSignalIntegrator();
      var result = entry.getKey().getLayerModifier().consolidateIntersections(crs,
          settings.getIntersectionSettings().getIntersectionConsolidatorSettings(),
          trafficSignalIntegrator::hasSignalOfItsOwn);
      registerConsolidationResult(result);
      LOGGER.info(String.format("Consolidated intersections: %s", result));
      notFoundOfEachLayer.add(result.getListedNodeIdsNotFound());
    }
    registerNotFoundInEveryLayer(OsmPlanitEntityIssue.NODE_LISTED_TO_JOIN_OR_KEEP_APART_NOT_FOUND, notFoundOfEachLayer);
  }

  /**
   * Compute the green ratios and saturation flow factors of the approaches of each layer's intersections when the
   * settings ask for it, see {@link OsmIntersectionSettings#setComputeGreenRatiosAndSaturationFlowFactors(boolean)}.
   * Without consolidation it warns once, since a junction parsed as several intersections is timed as several
   * junctions. What it did is logged on one line per layer, and each intersection that could not be computed is
   * recorded with the PLANit entity diagnostics
   *
   * @param crs of the network, to measure the directions of the approaches in
   */
  private void computeGreenRatiosAndSaturationFlowFactors(CoordinateReferenceSystem crs) {
    var intersectionSettings = settings.getIntersectionSettings();
    if(!intersectionSettings.isComputeGreenRatiosAndSaturationFlowFactors()) {
      return;
    }
    if(!intersectionSettings.isConsolidateIntersections()) {
      LOGGER.warning("Green ratios are computed without consolidating intersections, so a junction parsed as " +
          "several intersections is timed as several junctions and its green ratios can be far off");
    }
    var algorithm =
        GreenRatioAlgorithmFactory.create(intersectionSettings.getGreenRatioAndSaturationFlowFactorSettings());
    var crsUtils = new PlanitJtsCrsUtils(crs);
    var diagnostics = networkData.getPlanitEntityDiagnostics();
    for(var layer : networkData.getLayerParsers().keySet()) {
      var result = algorithm.computeGreenRatiosAndSaturationFlowFactors(
          layer.getIntersections().stream().collect(Collectors.toList()), crsUtils);
      LOGGER.info(String.format("Green ratios and saturation flow factors: %s", result));
      result.getNotComputableIntersections().forEach((intersection, usableGreenSeconds) -> diagnostics.registerIssue(
          OsmPlanitEntityIssue.INTERSECTION_GREEN_RATIOS_NOT_COMPUTABLE, intersection.getExternalId(),
          String.format("%.0f s usable green time", usableGreenSeconds)));
    }
  }

  /**
   * Call this BEFORE we parse the OSM network to initialise the handler(s) properly
   */
  public void initialiseBeforeParsing() {
    PlanItRunTimeException.throwIf(getOsmNetworkToPopulate().getTransportLayers() != null &&
            getOsmNetworkToPopulate().getTransportLayers().size()>0,
        "Network is expected to be empty at start of parsing OSM network, but it has layers already");
    var resolvedSettings = new OsmNetworkReaderSettingsResolved(settings);
    resolvedSettings.logExcludedOsmWayTypes();
    
    /* gis initialisation */
    PlanitJtsCrsUtils geoUtils = new PlanitJtsCrsUtils(settings.getSourceCRS());
    try {
      getOsmNetworkToPopulate().transform(settings.getSourceCRS());
    }catch(PlanItRunTimeException e) {
      LOGGER.severe(String.format("Unable to update network to CRS %s", settings.getSourceCRS().getName()));
    }

    /* initialise the modes on the network based on the settings chosen */
    getOsmNetworkToPopulate().createAndRegisterOsmCompatiblePlanitPredefinedModes(getSettings());
    if(getOsmNetworkToPopulate().getModes().firstMatch( m -> !m.isPredefinedModeType()) != null){
      // todo if we support custom modes, then all locations where we determine the mapping from OSM mode to PLANit
      //  mode needs revisiting to account for such custom mode mappings + settings need updating to support this
      //  activation somehow !!
      throw new PlanItRunTimeException("OSM based PLANit networks currently support only predefined mode mappings, " +
          "but found custom PLANit mode, this is not allowed");
    }
    //todo: make the configuration configurable again via the settings, however this requires changing the configurator
    // to work with predefined mode types rather than actual mode instances
    var planitInfrastructureLayerConfiguration =
        MacroscopicNetworkLayerConfigurator.createAllInOneConfiguration(osmNetworkToPopulate.getModes());

    /* (default) link segment types (on the network) */
    getOsmNetworkToPopulate().createAndRegisterLayers(planitInfrastructureLayerConfiguration);
    getOsmNetworkToPopulate().createAndRegisterOsmCompatibleLinkSegmentTypes(getSettings(), resolvedSettings);

    /* initialise layer specific parsers and bounding area*/
    networkData.initialiseLayerParsers(getOsmNetworkToPopulate(), settings, geoUtils);
  }

  /**
   * Suppress the logging of this reader's settings. Intended for a composing reader that logs the configuration
   * on this reader's behalf, so it appears once and in one place rather than piecemeal per sub-reader. Package
   * private so only the factory can apply it at creation time.
   */
  protected void suppressSettingsLogging() {
    this.suppressSettingsLogging = true;
  }


  /** Collect the network data gathered
   * 
   * @return network data
   */
  protected OsmNetworkReaderData getNetworkReaderData() {
    return networkData;
  }
  
  /** Remove dangling subnetworks when settings dictate it
   *
   * @param recreateManagedIds when true recreate ids, otherwise not
   */
  protected void removeDanglingSubNetworks(boolean recreateManagedIds) {
    removeDanglingSubNetworks(null, recreateManagedIds);
  }
  
  /**
   * Remove dangling subnetworks when settings dictate it. In case the removal of subnetworks causes zones to
   * become dangling the user is required to remove those afterwards themselves, by providing the zoning, only
   * the directly impacted connectoids are removed if affected.
   * 
   * @param zoning to also remove connectoids from when they reference removed road/rail subnetworks
   * @param recreateManagedIds when true recreate ids, otherwise not
   */  
  public void removeDanglingSubNetworks(Zoning zoning, boolean recreateManagedIds) {
    if(settings.isRemoveDanglingSubnetworks()) {

      boolean keepLargest = settings.isAlwaysKeepLargestSubnetwork();
      
      /* logging stats  - before */
      MacroscopicNetworkLayers layers = getOsmNetworkToPopulate().getTransportLayers();
      {
        if(zoning == null) {
          LOGGER.info(String.format("Original number of nodes %d, links %d, link segments %d",
                  layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments()));
        }else {
          LOGGER.info(String.format("Original number of nodes %d, links %d, link segments %d, connectoids %d",
                  layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments(),
                  zoning.getTransferConnectoids().size()));
        }
      }      
           
      if(layers.size()!=1) {
        LOGGER.warning("Currently OSM networks only support a single infrastructure layer in PLANit");
      }
      
      /* account for the connectoids that are to be removed as well in case they reside on a dangling network 
       * TODO: refactor this listener and instead make sure it is automatically dealt with by the zoning as an
       *  internal listener in some way
       * as this always needs to happen not only in OSM 
       * TODO: this listener and zoning in general does not properly support layers since vertices across layers
       *  might have the same id whereas
       * zone connectoids are now globally stored on the zoning and not per layer. This should be changed to
       *  avoid this problem possibly easier when this functionality
       * is not separate but dealt with within the zoning */
      DirectedGraphModifierListener listener = null;
      /* connectoids also follow the access link segments they lose, and the modes withdrawn from them */
      UpdateConnectoidsOnLinkSegmentChangeHandler linkSegmentChangeListener = null;
      if(zoning != null) {
        listener = new UpdateConnectoidsOnVertexRemovalHandler(zoning);
        layers.getFirst().getLayerModifier().addListener(listener);
        linkSegmentChangeListener = new UpdateConnectoidsOnLinkSegmentChangeHandler(zoning);
        layers.getFirst().getLayerModifier().addListener((DirectedGraphModifierListener) linkSegmentChangeListener);
        layers.getFirst().getLayerModifier().addListener((LinkSegmentTypeModifierListener) linkSegmentChangeListener);
      }

      /* one pass per activated mode, since being able to both reach and leave is a property of a mode rather
       * than of infrastructure. A one way street usually still permits pedestrians against the flow, so judging
       * this per track type cannot see a car trap at all. Access is withdrawn rather than infrastructure removed,
       * because the link a car cannot use is typically carrying another mode perfectly well */
      var layer = layers.getFirst();
      for(var entry : settings.getRemoveDanglingSubnetworkModes().entrySet()) {
        var mode = getOsmNetworkToPopulate().getModes().get(entry.getKey());
        if(mode == null) {
          /* the defaults cover modes a given parse may not have activated at all, which is not worth reporting:
           * there is simply no network of that mode to judge. Logged at fine for anyone chasing why a mode they
           * did configure explicitly appears to have been skipped */
          LOGGER.fine(String.format(
              "Dangling subnetworks configured for mode %s which is not part of this network, skipped",
              entry.getKey().value()));
          continue;
        }
        var config = entry.getValue();
        var result = MacroscopicNetworkLayerUtils.restrictModeAccessToConnectedSubNetworks(
            layer, mode, config.getBelowSize(), config.getAboveSize(), keepLargest,
            config.getConnectivity(), null);
        LOGGER.info(String.format("Dangling subnetworks (%s): %s", config, result));
        /* the size distribution rather than the totals alone, since it is what makes a size threshold choosable
         * from a single run instead of requiring one run per candidate value. Indented beneath the line reporting
         * the pruning itself, a line per bin, rather than run together on one line which becomes unreadable as
         * soon as more than a couple of bins are populated */
        for(var binLine : result.getSizeBinSummaryLines()) {
          LOGGER.info(String.format("  %s", binLine));
        }
      }

      /* only once every mode has been dealt with is it settled what nothing can use any more; the intersections this
       * removes are recorded as lost */
      var cleanup = layer.getLayerModifier().removeInfrastructureWithoutModeAccess();
      var planitEntityDiagnostics = networkData.getPlanitEntityDiagnostics();
      cleanup.getRemovedIntersections().forEach(intersection -> planitEntityDiagnostics.registerIssue(
          OsmPlanitEntityIssue.INTERSECTION_LOST_BY_PRUNING, intersection.getExternalId()));
      if(linkSegmentChangeListener != null) {
        registerConnectoidAccessLosses(linkSegmentChangeListener.getResult());
      }
      if(!cleanup.isEmpty()) {
        LOGGER.info(String.format("Dangling subnetworks: %s", cleanup));
      }
      /* withdrawing access creates a link segment type variant per original type and mode withdrawn, and many of
       * those end up identical to each other or to a type already present. Consolidation only runs on the default
       * types at initialisation, so without repeating it here every variant persists into the written network */
      if(settings.isConsolidateLinkSegmentTypes()) {
        getOsmNetworkToPopulate().consolidateFunctionallyEquivalentLinkSegmentTypes();
      }
      if(recreateManagedIds) {
        layer.getLayerModifier().recreateManagedIdEntities();
      }
      
      /* remove listener as it is currently meant for local use only due to expensive initialisation which is also
      not kept up to date */
      if(zoning != null) {
        var layerModifier = layers.getFirst().getLayerModifier();
        layerModifier.removeListener(listener);
        layerModifier.removeListener((DirectedGraphModifierListener) linkSegmentChangeListener);
        layerModifier.removeListener((LinkSegmentTypeModifierListener) linkSegmentChangeListener);
      }
      
      /* logging stats  - after */
      {
        if(zoning == null) {
          LOGGER.info(String.format("Remaining number of nodes %d, links %d, link segments %d",
                  layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments()));
        }else {
          LOGGER.info(String.format("Remaining number of nodes %d, links %d, link segments %d, connectoids %d",
                  layers.getNumberOfNodes(), layers.getNumberOfLinks(),layers.getNumberOfLinkSegments(),
                  zoning.getTransferConnectoids().size()));
        }
      }
            
    }
  }

  /**
   * Complete the parsed intersections of each layer as the settings ask: consolidate them, see
   * {@link OsmIntersectionSettings#setConsolidateIntersections(boolean)}, compute the green ratios and saturation flow
   * factors of their approaches, see
   * {@link OsmIntersectionSettings#setComputeGreenRatiosAndSaturationFlowFactors(boolean)}, and collapse the
   * consolidated intersections for the modes chosen, see
   * {@link OsmIntersectionSettings#getIntersectionCollapserSettings()}, in that order: collapsing can move the end of a
   * link to a centre node, which changes the direction an approach arrives in. Meant to run once dangling subnetworks
   * are removed, so only what remains is completed, and before ids are synced. Nothing runs when intersections are not
   * parsed, with a warning when consolidation was asked for
   */
  public void consolidateIntersectionsComputeGreenRatiosAndCollapse() {
    var intersectionSettings = settings.getIntersectionSettings();
    if(!settings.isIntersectionParserActive()) {
      if(intersectionSettings.isConsolidateIntersections()) {
        LOGGER.warning("Intersections are not consolidated, since they are not parsed");
      }
      return;
    }
    var crs = getOsmNetworkToPopulate().getCoordinateReferenceSystem();
    if(intersectionSettings.isConsolidateIntersections()) {
      consolidateIntersections(crs);
    }
    computeGreenRatiosAndSaturationFlowFactors(crs);
    if(intersectionSettings.isConsolidateIntersections()) {
      collapseIntersections(crs);
    }
  }

  /** Collect the osm network to populate
   *
   * @return osm network
   */
  protected PlanitOsmNetwork getOsmNetworkToPopulate() {
    return this.osmNetworkToPopulate;
  }
  
  /**
   * Constructor 
   * 
   * @param osmNetwork network to populate 
   */
  protected OsmNetworkReader(final PlanitOsmNetwork osmNetwork){
    this(CountryNames.GLOBAL, osmNetwork);
  }  
  
  /**
   * Constructor 
   * 
   * @param countryName to use
   * @param osmNetwork network to populate
   */
  protected OsmNetworkReader(final String countryName, final PlanitOsmNetwork osmNetwork){
    this(null, countryName, osmNetwork);
  }  
  
  /**
   * Constructor 
   * 
   * @param inputSource to use
   * @param countryName to use
   * @param osmNetworkToPopulate network to populate
   */
  protected OsmNetworkReader(
      final URL inputSource, final String countryName, final PlanitOsmNetwork osmNetworkToPopulate){
    this(new OsmNetworkReaderSettings(inputSource, countryName), osmNetworkToPopulate);
  }    
    
  /**
   * Constructor 
   *  
   * @param settings for populating the network
   * @param osmNetworkToPopulate network to populate
   */
  protected OsmNetworkReader(OsmNetworkReaderSettings settings, final PlanitOsmNetwork osmNetworkToPopulate){
    this.settings = settings;   
    this.networkData = new OsmNetworkReaderData();
    this.osmNetworkToPopulate = osmNetworkToPopulate;
  }  
     
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network
   * given the configuration options that have been set
   * 
   * @return macroscopic network that has been parsed
   */
  @Override
  public MacroscopicNetwork read() {
    PlanItRunTimeException.throwIfNull(getSettings().getInputSource(),
        "Input source not set for OSM network to parse");
    PlanItRunTimeException.throwIf(StringUtils.isNullOrBlank(getSettings().getCountryName()),
        "Country name not set for OSM network to parse");
    PlanItRunTimeException.throwIfNull(getOsmNetworkToPopulate(),
        "PLANit network to populate not set for OSM network to parse");

    logInfo();
    
    /* initialise */
    initialiseBeforeParsing();

    // boundary identification and construction (if needed)
    performBoundingAreaExtraction();
    
    /* preprocessing (if needed)*/
    doPreprocessing();
    
    /* main processing  (always)*/
    doMainProcessing();
    registerTrafficSignalOverridesNotFound();
      
    /* dangling subnetworks */
    boolean recreateIds = false; // postpone to sync xml ids at same time afterwards
    if(getSettings().isRemoveDanglingSubnetworks()) {
      removeDanglingSubNetworks(recreateIds);
    }

    /* intersections, once pruning has settled what remains */
    consolidateIntersectionsComputeGreenRatiosAndCollapse();

    // clean up ids to be contiguous
    MacroscopicNetworkModifierUtils.updateAndSyncManagedIdEntitiesContainerXmlIdsToIds(osmNetworkToPopulate);

    /* what became of the OSM entities read, and of the PLANit entities derived from them */
    if(getSettings().isIntersectionParserActive()) {
      networkData.getParseDiagnostics().logSummary(LOGGER, "[STATS] OSM traffic signals",
          networkData.getTrafficSignalOsmNodeIds().size());
      networkData.getPlanitEntityDiagnostics().logSummary(LOGGER, "[STATS] PLANit intersections");
    }
    LOGGER.info("OSM full network parsing...DONE");
    
    /* return result */
    return osmNetworkToPopulate;
  }  
    
  /**
   * Collect the settings which can be used to configure the reader
   * 
   * @return the settings
   */
  public OsmNetworkReaderSettings getSettings() {
    return settings;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
  }

  /** Factory method to create bridging data required for an OSM zoning reader to successfully parse the Pt zones
   *  based on the osm network parsed by this network reader. Without this data it is not possible to relate the two
   *  properly
   *  
   * @return created network to zoning reader data to use
   */
  public OsmNetworkToZoningReaderData createNetworkToZoningReaderData() {
    if(getOsmNetworkToPopulate().getTransportLayers().size()==0 ||
        getOsmNetworkToPopulate().getTransportLayers().getFirst().isEmpty()) {
      LOGGER.warning("Can only perform network->zoning data transfer when network has been " +
          "populated by OSM network reader, i.e., first invoke the read() method before this call");
      return null;
    }

    /* DTO */
    OsmNetworkToZoningReaderData network2zoningData = new OsmNetworkToZoningReaderData(networkData, getSettings());
        
    /* layer specific data references */
    for(Entry<MacroscopicNetworkLayer, OsmNetworkLayerParser> entry : networkData.getLayerParsers().entrySet()){
      OsmNetworkLayerParser layerHandler = entry.getValue();
      network2zoningData.registerLayerData(entry.getKey(), layerHandler.getLayerData());
    }
    
    return network2zoningData;
  }

}

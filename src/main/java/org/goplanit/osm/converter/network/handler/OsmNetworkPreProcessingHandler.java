package org.goplanit.osm.converter.network.handler;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.OsmNodeUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Preprocessing Handler that has two stages:
 * <p>
 * REGULAR_PREPROCESSING_WAYS: identify ways (and their nodes) that are eligible since they form the network. Only do so for ways that
 * fall within the bounding area for at least one node (if a bounding area is especified)
 * </p>
 * <p>
 * REGULAR_PREPROCESSING_NODES: identify and register nodes that are part of the ways that make
 * up the network.
 * </p>
 *
 * @author markr
 * 
 *
 */
public class OsmNetworkPreProcessingHandler extends OsmNetworkBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkPreProcessingHandler.class.getCanonicalName());

  /** pre-processing stage to apply */
  private final Stage stage;

  private final LongAdder osmNodeCounter;
  private final LongAdder osmWayCounter;

  /**
   * Deal with situation if for this OSM way a manual override was provided
   *
   * @param osmWay to process
   * @param tags of the OSM way
   * @return true when manual override present, false otherwise
   */
  protected boolean handleEligibleOsmWayManualOverrides(OsmWay osmWay, Map<String,String> tags) {
    var settings = getSettings();
    if(settings.isKeepOsmWayOutsideBoundingPolygon(osmWay.getId())) {

      if(!settings.hasBoundingBoundary()){
        LOGGER.warning("OSM way %d is marked for inclusion beyond bounding polygon but no boundary was set, " +
            "verify correctness");
      }
      if(settings.isOsmWayExcluded(osmWay.getId())) {
        LOGGER.warning("OSM way %d is marked for exclusion as well as keeping it, this is conflicting, OSM way " +
            "exclusion takes precedence");
        return true;
      }

      /* mark all nodes for keeping, since we determine availability based on the tracked OSM nodes */
      for(int index=0;index<osmWay.getNumberOfNodes();++index) {
        //todo ugly since we are modifying user settings, this should be tracked in network internal data structure
        settings.setKeepOsmNodeOutsideBoundingPolygon(osmWay.getNodeId(index));
      }
      return true;
    }
    return false;
  }

  /**
   * Handle special cases OSM ways that were identified in stage one but without lack of knowledge on underlying node
   * locations. Now this has been processed in stage two handling of nodes, so we can decide what OSM ways to keep and
   * (pre)register all their nodes so the data is available when finalising in stage three.
   *
   * @param osmWay to process and see if it is a special case
   * @param tags of the OSM way
   */
  protected void handleOsmWaySpecialCasesStageTwo(OsmWay osmWay, Map<String,String> tags) {
    var settings = getSettings();
    if(settings.hasBoundingBoundary() && isActivatedWaterwayBasedInfrastructure(tags) &&
        getNetworkData().getOsmSpatialEligibilityData().isOsmWayPotentiallySpatiallyEligibleAsSpecialCase(
            osmWay.getId())){
      // at this point if any node was found to be within acceptable distance of the bounding polygon, it would have
      // been marked as spatially eligible in handlnig of nodes in stage two, despite not being spatially eligible
      // given the bounding area. So we can now follow the same procedure as we do for any node to see if the way
      // is eligible
      boolean osmWaySpatiallyEligible =
          getNetworkData().getOsmSpatialEligibilityData().markOsmWaySpatiallyEligibleIfHasSpatiallyEligibleNode(osmWay);
      if(osmWaySpatiallyEligible) {
        getNetworkData().getOsmNodeData().preregisterOsmWayNodes(osmWay);
      }
    }
  }

  /** Mark all nodes of (spatially) eligible OSM ways (e.g., road, rail, etc.) to be parsed during the main processing
   * phase. Special detection for manual overrides or special cases that may fall outside bounding area but would still
   * be deemed legal to import
   * 
   * @param osmWay to handle
   * @param tags of the OSM way
   */
  protected void handleEligibleOsmWayStageOne(OsmWay osmWay, Map<String,String> tags) {
    var settings = getSettings();

    if(osmWay.getId() == 151911703L){
      int bla = 4;
    }

    // MANUAL OVERRIDES
    boolean isManualOverride = handleEligibleOsmWayManualOverrides(osmWay, tags);
    if(isManualOverride){
      return;
    }

    // SPATIAL RESTRICTIONS
    // filter based on required presence of at least one pre-registered OSM node within bounding area given it is set
    boolean osmWaySpatiallyEligible =
        getNetworkData().getOsmSpatialEligibilityData().markOsmWaySpatiallyEligibleIfHasSpatiallyEligibleNode(osmWay);
    if(osmWaySpatiallyEligible) {
      /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that is deemed eligible
      (road, rail, or boundary) */
      getNetworkData().getOsmNodeData().preregisterOsmWayNodes(osmWay);

      if(getNetworkData().getOsmSpatialEligibilityData().countSpatiallyEligibleWays() % 100000 == 0 ){
        LOGGER.info(String.format("Ways preprocessing has identified %d (out of %d) spatially eligible OSM ways (%s)",
            getNetworkData().getOsmSpatialEligibilityData().countSpatiallyEligibleWays(), osmWayCounter.sum(), stage));
      }
    }
    // SPECIAL CASES
    else if(settings.hasBoundingBoundary())
    {
      // WATERWAYS
      // special case for waterways, since none of its nodes fall inside area, we do not know yet if it is close enough
      // to any bounding area to be eligible, so for now, just pre-register the osm way and we will verify in stage
      // two. The reason for doing this is that bounding areas based on zoning systems often exclude water areas but
      // ferries running on them near zones should still likely be included, so do so if in close enough proximity
      if(isActivatedWaterwayBasedInfrastructure(tags)){
        getNetworkData().getOsmSpatialEligibilityData().markOsmWayAndNodesPotentiallySpatiallyEligibleAsSpecialCase(
            osmWay);
      }
    }
  }

  /**
   * Preprocessing of network has at maximum three stages identified by this enum.
   *
   */
  public enum Stage {
    ONE_PREPROCESSING_SPATIALLY_NODES_WAYS,
    TWO_PREPROCESS_SPECIAL_CASE_NODES_WAYS,
    THREE_FINALISE_PREPROCESSING_NODES_WAYS,
  }

  /**
   * Constructor
   *
   * @param preProcessStage        the preProcess stage to apply tot his preProcessing
   * @param networkToPopulate      the network to populate
   * @param networkData            to populate
   * @param settings               for the handler
   */
  public OsmNetworkPreProcessingHandler(
      Stage preProcessStage,
      final PlanitOsmNetwork networkToPopulate,
      final OsmNetworkReaderData networkData,
      final OsmNetworkReaderSettings settings) {
    super(networkToPopulate, networkData, settings);
    this.osmNodeCounter = new LongAdder();
    this.osmWayCounter = new LongAdder();
    this.stage = preProcessStage;
  }

  /**
   * Count total eligible number of nodes in OSM file, and register those eligible as such based on OSM nodes
   * being part of OSM ways that are found to be relevant for parsing
   */
  @Override
  public void handle(OsmNode node) {

    if(node.getId() == 458705435L){
      int bla = 4;
    }

    if(stage.equals(Stage.ONE_PREPROCESSING_SPATIALLY_NODES_WAYS)){
      boolean spatiallyEligible =
          !getNetworkData().hasBoundingArea() ||
              getProjectedBoundingAreaHelper().getPreparedBoundingPolygon().contains(OsmNodeUtils.createPoint(node));

      // mark as spatially eligible if bounding area is present and it falls within this area, or
      // if no bounding area all are eligible. Only OSM ways with at least one spatially eligible nodes will be considered
      // for parsing
      if(spatiallyEligible){

        getNetworkData().getOsmSpatialEligibilityData().markOsmNodeSpatiallyEligible(node.getId());
        if(getNetworkData().getOsmSpatialEligibilityData().countSpatiallyEligibleNodes() % 1000000 == 0 ){
          LOGGER.info(String.format("Node preprocessing has identified %d (out of %d) spatially eligible OSM nodes (%s)",
              getNetworkData().getOsmSpatialEligibilityData().countSpatiallyEligibleNodes(), osmNodeCounter.sum(), stage));
        }
      }

    }else if(stage.equals(Stage.TWO_PREPROCESS_SPECIAL_CASE_NODES_WAYS)){

      // SPECIAL CASES
      // Check marked special case non-spatially eligible nodes to include if they fall within distance to bounding area
      // if valid special case is found, pre-register, so it can be processed as usual from here on forward
      if(getNetworkData().getOsmSpatialEligibilityData().
          isOsmNodePartOfPotentiallySpatiallyEligibleWayAsSpecialCase(node.getId())){
        double projectedDistance = getProjectedBoundingAreaHelper().calculateProjectedDistanceToBoundingPolygon(
            OsmNodeUtils.createPoint(node), true);
        // NOTE: currently the only special case is for water related entities, if we get more types make our check
        // aware of the type and less implicitly baked in as there is no specific check on waterways here currently
        // (as not needed)
        if(projectedDistance < getSettings().getMaximumDistanceFerryOutsideBoundingPolygonInMeters()){
          getNetworkData().getOsmSpatialEligibilityData().markOsmNodeSpatiallyEligible(node.getId());
        }
      }

    }else if(stage.equals(Stage.THREE_FINALISE_PREPROCESSING_NODES_WAYS)){

      var osmNodeData = getNetworkData().getOsmNodeData();
      if(osmNodeData.containsPreregisteredOsmNode(node.getId())){
        // 1. register all OSM nodes that are deemed eligible and have been pre-registered based on identified
        // OSM ways' nodes or special cases
        osmNodeData.registerEligibleOsmNode(node);
      }
    }

    if(osmNodeCounter.sum() % 10000000 == 0  && (stage.equals(Stage.THREE_FINALISE_PREPROCESSING_NODES_WAYS) ||
        stage.equals(Stage.TWO_PREPROCESS_SPECIAL_CASE_NODES_WAYS))){
      LOGGER.info(String.format("Node preprocessing has processed %d OSM nodes (%s)",
          osmNodeCounter.sum(),stage));
    }

    osmNodeCounter.increment();
  }


  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon or otherwise
   * we extract their nodes and mark them for inclusion as exceptions to the bounding polygon filter that is
   * applied during the main parsing pass in the regular PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) {

    if(stage.equals(Stage.ONE_PREPROCESSING_SPATIALLY_NODES_WAYS)) {
      wrapHandleInfrastructureOsmWay(osmWay, this::handleEligibleOsmWayStageOne);
      osmWayCounter.increment();
    }else if(stage.equals(Stage.TWO_PREPROCESS_SPECIAL_CASE_NODES_WAYS)){
      wrapHandleInfrastructureOsmWay(osmWay, this::handleOsmWaySpecialCasesStageTwo);
      osmWayCounter.increment();
    }
  }

  /**
   * PRe-process OSM relations solely for the purpose in case a bounding boundary has been specified by name in which case
   * we extract it and convert it into a bounding polygon to use. If it is not found then we log a severe indicating the issue
   * and proceed without a bounding polygon/restriction in what we parse
   */
  @Override
  public void handle(OsmRelation osmRelation) {
  }

  /** Log total number of parsed nodes and percentage retained
   */
  @Override
  public void complete() throws IOException {
    super.complete();

    if(stage.equals(Stage.ONE_PREPROCESSING_SPATIALLY_NODES_WAYS)) {
      // STAGE 1
      // nothing final, so not logging yet
    }else if(stage.equals(Stage.TWO_PREPROCESS_SPECIAL_CASE_NODES_WAYS)) {
      // STAGE 2
      // OSM ways final
      int eligibleOsmWays = getNetworkData().getOsmSpatialEligibilityData().countSpatiallyEligibleWays();
      LOGGER.info(String.format("Total OSM ways in source: %d",osmWayCounter.sum()));
      LOGGER.info(String.format("Total OSM ways identified as part of network: %d (%.2f%%)",
          eligibleOsmWays, eligibleOsmWays*100/(double) osmWayCounter.sum()));
    }else if(stage.equals(Stage.THREE_FINALISE_PREPROCESSING_NODES_WAYS)){
      // STAGE 3
      // all final
      int preRegisteredOsmNodes = getNetworkData().getOsmNodeData().getRegisteredOsmNodes().size();
      LOGGER.info(String.format("Total OSM nodes in source: %d",osmNodeCounter.sum()));
      LOGGER.info(String.format("Total OSM nodes identified as part of network: %d (%.2f%%)",
          preRegisteredOsmNodes, preRegisteredOsmNodes*100/(double) osmNodeCounter.sum()));
    }
  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    super.reset();
    osmNodeCounter.reset();
    osmWayCounter.reset();

    /* data and settings are to be kept for main parsing loop */
  }  
  
}

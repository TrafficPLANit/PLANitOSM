package org.goplanit.osm.converter.network;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.converter.OsmBoundaryManager;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.OsmNodeUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Preprocessing Handler that has two stages:
 *<p>
 *IDENTIFY_BOUNDARY_BY_NAME: only required when bounding area of network is defined by a name only. Here we only register
 *all the members of the relation for parsing during the regular pre-processing stage. Required to be able to construct the
 *spatial boundry during main processing but requires two passes during preprocessing.
 *</p>
 * <p>
 *   IDENTIFY_WAYS_FOR_BOUNDARY: preregister the nodes and register the ways part of the bounding boundary
 * </p>
 * <p>
 *   FINALISE_BOUNDARY_BY_NAME: register the nodes marked for network or boundary and finalise boundary by converting them
 *   into a polygon.
 * </p>
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

  /** Builder to keep track of constructing bounding boundary */
  private OsmBoundaryManager boundaryManager;

  /** track all OSM nodes that we tentatively preregister but not(yet) as the
   * officially pre-registered ones. We do this two stage approach to avoid checking against these secondary nodes
   * that are not in the bounding box but only added to avoid having partial OSM ways nodes in the end. In complete() they
   * will be added fully though.
   */
  private Set<Long> secondaryPreregisteredOsmNodes = new TreeSet<>();

  /** pre-processing stage to apply */
  private final Stage stage;

  /**
   * Preprocessing of network has at maximum six stages (three for a bounding boundary and three for
   * regular preprocessing, identified by this enum.
   *
   */
  public enum Stage {
    ONE_IDENTIFY_BOUNDARY_BY_NAME,
    TWO_IDENTIFY_WAYS_FOR_BOUNDARY,
    THREE_FINALISE_BOUNDARY_BY_NAME,
    FOUR_REGULAR_PREPROCESSING_WAYS,
    FIVE_REGULAR_PREPROCESSING_NODES,
  }
  
  private final LongAdder osmNodeCounter;
  private final LongAdder osmWayCounter;

  /** Mark all nodes of eligible OSM ways (e.g., road, rail, etc.) to be parsed during the main processing phase
   * 
   * @param osmWay to handle
   * @param tags of the OSM way
   */
  protected void handleEligibleOsmWay(OsmWay osmWay, Map<String,String> tags) {
    var settings = getSettings();
     
    if(settings.isKeepOsmWayOutsideBoundingPolygon(osmWay.getId())) {

      if(!settings.hasBoundingBoundary()){
        LOGGER.warning("OSM way %d is marked for inclusion beyond bounding polygon but no boundary was set, verify correctness");
      }
      if(settings.isOsmWayExcluded(osmWay.getId())) {
        LOGGER.warning("OSM way %d is marked for exclusion as well as keeping it, this is conflicting, OSM way exclusion takes precedence");
        return;
      }

      /* mark all nodes for keeping, since we determine availability based on the tracked OSM nodes */
      for(int index=0;index<osmWay.getNumberOfNodes();++index) {
        //todo ugly since we are modifying user settings, this should be tracked in network internal data structure
        settings.setKeepOsmNodeOutsideBoundingPolygon(osmWay.getNodeId(index));
      }
      return;
    }

    // REGULAR PROCESS
    // filter based on required presence of at least one pre-registered OSM node within bounding area given it is set
    boolean osmWayEligible = false;
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      if(getNetworkData().getOsmNodeData().containsPreregisteredOsmNode(osmWay.getNodeId(index))){
        osmWayEligible = true;
        getNetworkData().registerSpatialInfraEligibleOsmWayId(osmWay.getId());
        break;
      }
    }

    if(osmWayEligible) {
      /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that is deemed eligible (road, rail, or boundary) */
      for (int index = 0; index < osmWay.getNumberOfNodes(); ++index) {
        // to be preregistered in full in complete() to avoid checking against these in above check regarding bounding boundary
        secondaryPreregisteredOsmNodes.add(osmWay.getNodeId(index));
      }
    }
  }

  /**
   * Constructor
   *
   * @param preProcessStage        the preProcess stage to apply tot his preProcessing
   * @param boundaryManager        to track boundary information
   * @param networkToPopulate      the network to populate
   * @param networkData            to populate
   * @param settings               for the handler
   */
  public OsmNetworkPreProcessingHandler(
      Stage preProcessStage,
      final OsmBoundaryManager boundaryManager,
      final PlanitOsmNetwork networkToPopulate, final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings settings) {
    super(networkToPopulate, networkData, settings);
    this.boundaryManager = boundaryManager;
    this.osmNodeCounter = new LongAdder();
    this.osmWayCounter = new LongAdder();
    this.stage = preProcessStage;
  }

  /**
   * Count total number of nodes in OSM file, or register them for the bounding boundary to be constructed
   */
  @Override
  public void handle(OsmNode node) {

    if(stage.equals(Stage.THREE_FINALISE_BOUNDARY_BY_NAME)){

      // register actual instance so available during complete() where we construct the boundary from the OSM ways and nodes
      // that make up the actual bounding boundary polygon
      //todo: instead of using the osmNodeData transfer this to the boundaryManager
      // (do the same for the zoning handler version of this)
      if(getNetworkData().getOsmNodeData().containsPreregisteredOsmNode(node.getId())){
        getNetworkData().getOsmNodeData().registerEligibleOsmNode(node);
      }

    }else if(stage.equals(Stage.FOUR_REGULAR_PREPROCESSING_WAYS)){

      // pre-register if bounding area is present and it falls within this area, if no bounding area all are eligible
      if(!getNetworkData().hasBoundingArea() ||
          OsmNodeUtils.createPoint(node).within(getNetworkData().getBoundingArea().getBoundingPolygon())){

        getNetworkData().getOsmNodeData().preRegisterEligibleOsmNode(node.getId());
      }

    }else if(stage.equals(Stage.FIVE_REGULAR_PREPROCESSING_NODES)){

      // register any remaining OSM nodes that are deemed eligible based on identified OSM ways
      var osmNodeData = getNetworkData().getOsmNodeData();
      if(osmNodeData.containsPreregisteredOsmNode(node.getId())){
        getNetworkData().getOsmNodeData().registerEligibleOsmNode(node);
      }

      osmNodeCounter.increment();
    }
  }


  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon we
   * extract their nodes and mark them for inclusion as exceptions to the bounding polygon filter that is
   * applied during the main parsing pass in the regular PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) {


    if(stage.equals(Stage.TWO_IDENTIFY_WAYS_FOR_BOUNDARY)) {
      // boundary stage identifying ways

      // update registered OSM way ids with actual OSM way containing geometry (if needed)
      boolean match = boundaryManager.stepTwoAttachBoundaryRelationMemberOsmWays(osmWay);
      if (match) {
        handleEligibleOsmWay(osmWay, OsmModelUtil.getTagsAsMap(osmWay));
      }

    }else if(stage.equals(Stage.FOUR_REGULAR_PREPROCESSING_WAYS)) {

      // regular stage for ways identifying network links
      wrapHandleInfrastructureOsmWay(osmWay, this::handleEligibleOsmWay);
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
    // no action unless stage is Stage.IDENTIFY_BOUNDARY_BY_NAME
    if(!stage.equals(Stage.ONE_IDENTIFY_BOUNDARY_BY_NAME)){
      return;
    }

    /* identify relations that might carry bounding area information */
    boundaryManager.stepOneIdentifyAndRegisterBoundingAreaRelationMembers(osmRelation);
  }

  /** Log total number of parsed nodes and percentage retained
   */
  @Override
  public void complete() throws IOException {
    super.complete();

    /* if we had any secondary preregistered osm nodes, this is the time to preregister them properly
     * used both in REGULAR_PREPROCESSING_WAYS as well as IDENTIFY_WAYS_FOR_BOUNDARY
     */
    this.secondaryPreregisteredOsmNodes.forEach(osmNodeId -> getNetworkData().getOsmNodeData().preRegisterEligibleOsmNode(osmNodeId));
    secondaryPreregisteredOsmNodes.clear();

    if(stage.equals(Stage.ONE_IDENTIFY_BOUNDARY_BY_NAME) && boundaryManager.isConfigured()){
      // BOUNDARY STAGE 1

      boolean success = boundaryManager.hasRegisteredRelationMembers();
      String boundaryName = getSettings().getBoundingArea().getBoundaryName();
      LOGGER.info((success ? "Registered relation members for boundary " : "Unable to identify bounding area for name ") + boundaryName );
    }else if(stage.equals(Stage.THREE_FINALISE_BOUNDARY_BY_NAME)) {
      // BOUNDARY STAGE 3

      // finalise bounding boundary now that OSM nodes are registered and available for polygon building
      boundaryManager.stepThreeCompleteConstructionBoundingBoundary(getNetworkData().getOsmNodeData().getRegisteredOsmNodes());
    }else if(stage.equals(Stage.FIVE_REGULAR_PREPROCESSING_NODES)) {

      // regular pre-registration for networks
      int preRegisteredOsmNodes = getNetworkData().getOsmNodeData().getRegisteredOsmNodes().size();
      int eligibleOsmWays = getNetworkData().getNumSpatialInfraEligibleOsmWays();
      LOGGER.info(String.format("Total OSM nodes in source: %d",osmNodeCounter.sum()));
      LOGGER.info(String.format("Total OSM nodes identified as part of network: %d (%.2f%%)",preRegisteredOsmNodes, preRegisteredOsmNodes/(double) osmNodeCounter.sum()));

    }else if(stage.equals(Stage.FOUR_REGULAR_PREPROCESSING_WAYS)) {

      int eligibleOsmWays = getNetworkData().getNumSpatialInfraEligibleOsmWays();
      LOGGER.info(String.format("Total OSM ways in source: %d",osmWayCounter.sum()));
      LOGGER.info(String.format("Total OSM ways identified as part of network: %d (%.2f%%)",eligibleOsmWays, eligibleOsmWays/(double) osmWayCounter.sum()));
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

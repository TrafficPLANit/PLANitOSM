package org.goplanit.osm.converter.network;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
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

  /**
   * Preprocessing of network has at maximum two stages identified by this enum.
   *
   */
  public enum Stage {
    ONE_REGULAR_PREPROCESSING_WAYS,
    TWO_REGULAR_PREPROCESSING_NODES,
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

    // filter based on required presence of at least one pre-registered OSM node within bounding area given it is set
    boolean osmWayEligible = getNetworkData().getOsmSpatialEligibilityData().markOsmWaySpatiallyEligibleIfHasSpatiallyEligibleNode(osmWay);
    if(osmWayEligible) {
      getNetworkData().registerSpatialInfraEligibleOsmWayId(osmWay.getId());
      /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that is deemed eligible (road, rail, or boundary) */
      getNetworkData().getOsmNodeData().preregisterOsmWayNodes(osmWay);
    }
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

    if(stage.equals(Stage.ONE_REGULAR_PREPROCESSING_WAYS)){

      // mark as spatially eligible if bounding area is present and it falls within this area, or
      // if no bounding area all are eligible. Only OSM ways with at least one spatially eligible nodes will be considered
      // for parsing
      if(!getNetworkData().hasBoundingArea() ||
          OsmNodeUtils.createPoint(node).within(getNetworkData().getBoundingArea().getBoundingPolygon())){

        getNetworkData().getOsmSpatialEligibilityData().markOsmNodeSpatiallyEligible(node.getId());
      }

    }else if(stage.equals(Stage.TWO_REGULAR_PREPROCESSING_NODES)){

      // register all OSM nodes that are deemed eligible and have been pre-registered based on identified OSM ways' nodes
      var osmNodeData = getNetworkData().getOsmNodeData();
      if(osmNodeData.containsPreregisteredOsmNode(node.getId())){
        osmNodeData.registerEligibleOsmNode(node);
      }

      osmNodeCounter.increment();
    }
  }


  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon or otherwise
   * we extract their nodes and mark them for inclusion as exceptions to the bounding polygon filter that is
   * applied during the main parsing pass in the regular PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) {

    if(stage.equals(Stage.ONE_REGULAR_PREPROCESSING_WAYS)) {

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
  }

  /** Log total number of parsed nodes and percentage retained
   */
  @Override
  public void complete() throws IOException {
    super.complete();

    if(stage.equals(Stage.ONE_REGULAR_PREPROCESSING_WAYS)) {
      // STAGE 4

      int eligibleOsmWays = getNetworkData().getNumSpatialInfraEligibleOsmWays();
      LOGGER.info(String.format("Total OSM ways in source: %d",osmWayCounter.sum()));
      LOGGER.info(String.format("Total OSM ways identified as part of network: %d (%.2f%%)",eligibleOsmWays, eligibleOsmWays*100/(double) osmWayCounter.sum()));
    }else if(stage.equals(Stage.TWO_REGULAR_PREPROCESSING_NODES)) {
      // STAGE 5

      // regular pre-registration for networks
      int preRegisteredOsmNodes = getNetworkData().getOsmNodeData().getRegisteredOsmNodes().size();
      LOGGER.info(String.format("Total OSM nodes in source: %d",osmNodeCounter.sum()));
      LOGGER.info(String.format("Total OSM nodes identified as part of network: %d (%.2f%%)",preRegisteredOsmNodes, preRegisteredOsmNodes*100/(double) osmNodeCounter.sum()));

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

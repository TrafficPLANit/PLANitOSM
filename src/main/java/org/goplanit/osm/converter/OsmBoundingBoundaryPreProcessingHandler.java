package org.goplanit.osm.converter;

import de.topobyte.osm4j.core.access.OsmReader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.goplanit.osm.converter.network.OsmNetworkBaseHandler;
import org.goplanit.osm.util.Osm4JUtils;
import org.goplanit.osm.util.OsmHandlerUtils;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Preprocessing Handler that has three stages to extract a bounding boundary polygon based on an OSM relation name
 *<p>
 *  ONE_IDENTIFY_BOUNDARY_BY_NAME: We only register all the members of the boundary relation matching the boundary name
 *  for parsing in the next stage (as these are OSM ways). Required to be able to construct the spatial boundary.
 *</p>
 * <p>
 *   IDENTIFY_WAYS_FOR_BOUNDARY: preregister the nodes of the identified OSM boundary relation ways and register the
 *   ways part of the bounding boundary
 * </p>
 * <p>
 *   FINALISE_BOUNDARY_BY_NAME: register the nodes marked for network or boundary and finalise boundary by converting them
 *   into a polygon.
 * </p>
 * @author markr
 */
public class OsmBoundingBoundaryPreProcessingHandler extends OsmNetworkBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmBoundingBoundaryPreProcessingHandler.class.getCanonicalName());

  /** Builder to keep track of constructing bounding boundary */
  private final OsmBoundaryManager boundaryManager;

  /** pre-processing stage to apply */
  private final Stage stage;

  /**
   * Helper to create an OSM4jReader for bounding area identification (before any pre-processing), handler for a given stage and perform the parsing
   *
   * @param stage to apply
   * @param boundaryManager to use
   */
  public static void createHandlerAndRead(URL osmInputSource,
                                          OsmBoundingBoundaryPreProcessingHandler.Stage stage, OsmBoundaryManager boundaryManager){
    /* reader to parse the actual file or source location */
    OsmReader osmReader = Osm4JUtils.createOsm4jReader(osmInputSource);
    if(osmReader == null) {
      LOGGER.severe("Unable to create OSM reader for identifying bounding boundary, aborting");
      return;
    }
    var osmHandler = new OsmBoundingBoundaryPreProcessingHandler(
        stage,
        boundaryManager);
    OsmHandlerUtils.readWithHandler(osmReader, osmHandler);
  }

  /**
   * Preprocessing of bounding boundary always has three stages.
   *
   */
  public enum Stage {
    ONE_IDENTIFY_BOUNDARY_BY_NAME,
    TWO_IDENTIFY_WAYS_FOR_BOUNDARY,
    THREE_FINALISE_BOUNDARY_BY_NAME,
  }

  /**
   * Constructor
   *
   * @param preProcessStage        the preProcess stage to apply tot his preProcessing
   * @param boundaryManager        to track boundary information
   */
  public OsmBoundingBoundaryPreProcessingHandler(
      Stage preProcessStage,
      final OsmBoundaryManager boundaryManager) {
    super(null, null, null);
    this.boundaryManager = boundaryManager;
    this.stage = preProcessStage;
  }

  /**
   * When OSM ways for boundary are known, identify their OSM nodes for constructing the polygon
   *
   * @param node OSM node to handle
   */
  @Override
  public void handle(OsmNode node) {

    if(!stage.equals(Stage.THREE_FINALISE_BOUNDARY_BY_NAME)) {
      return;
    }

    // register actual instance so available during complete() where we construct the boundary from the OSM ways and nodes
    // that make up the actual bounding boundary polygon
    if(boundaryManager.isPreregisteredBoundaryOsmNode(node.getId())){
      boundaryManager.registerBoundaryOsmNode(node);
    }

  }


  /**
   * For all OSM ways part of the boundary relation, pre-register nodes for next pass and track the OSM ways as part of
   * the boundary
   *
   * @param osmWay to handle
   */
  @Override
  public void handle(OsmWay osmWay) {

    if(!stage.equals(Stage.TWO_IDENTIFY_WAYS_FOR_BOUNDARY)) {
      return;
    }

    // update registered OSM way ids with actual OSM way containing geometry (if needed) as well as preregister its nodes
    boundaryManager.stepTwoAttachBoundaryOsmWaysAndPreregisterItsNodes(osmWay);

  }

  /**
   * Identify the OSM relation that reflects the bounding boundary by name and mark its OSM ways for tracking in the
   * next pass
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

  /** Finalise construction of bounding boundary in stage 3, and do logging where appropriate
   */
  @Override
  public void complete() throws IOException {
    super.complete();

    if(stage.equals(Stage.ONE_IDENTIFY_BOUNDARY_BY_NAME) && boundaryManager.isConfigured()){
      // BOUNDARY STAGE 1
      boundaryManager.logStepOneRegisteredRelationMemberStats();

    }else if(stage.equals(Stage.THREE_FINALISE_BOUNDARY_BY_NAME)) {
      // BOUNDARY STAGE 3

      // finalise bounding boundary now that OSM nodes are registered and available for polygon building
      boundaryManager.stepThreeCompleteConstructionBoundingBoundary();
      boundaryManager.logStepThreeCompletedBoundingBoundaryStats();
      boundaryManager.freeResourcesAfterCompletion();
    }
  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    super.reset();

    /* data and settings are to be kept for main parsing loop */
  }  
  
}

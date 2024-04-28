package org.goplanit.osm.converter.network;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.converter.OsmBoundaryManager;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Preprocessing Handler that has two stages:
 *<p>
 *IDENTIFY_BOUNDARY_BY_NAME: only required when bounding area of network is defined by a name only. Here we only register
 *all the members of the relation for parsing during the regular pre-processing stage. Required to be able to construct the
 *spatial boundry during main processing but requires two passes during preprocessing.
 *</p><p>
 *REGULAR_PREPROCESSING: regular preprocessing where we register nodes of ways for eligibility of the network and extract
 *the ways part of a bounding boundary that was identified in the IDENTIFY_BOUNDARY_BY_NAME stage
 * identifies which nodes of osm ways  - that are marked for inclusion even if they fall (partially) outside the bounding polygon -
 * are to be kept. Since we only know what nodes these are after parsing OSM ways (and nodes are parsed before the ways), this pre-processing is the only way
 * that we can identify these nodes before the main parsing pass.
 * </p>
 * <p>
 *   FINALISE_BOUNDARY_BY_NAME: register the nodes marked for network or boundary and finalise boundary by converting them
 *   into a polygon.
 * </p>
 * TODO: split in three classes, one per stage so code is less entangled than it is now.
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

  /** pre-processing stage to apply */
  private final Stage stage;

  /**
   * Preprocessing of network has two stages, identified by this enum.
   *
   */
  public enum Stage {
    IDENTIFY_BOUNDARY_BY_NAME,
    REGULAR_PREPROCESSING,
    FINALISE_BOUNDARY_BY_NAME
  }
  
  private final LongAdder nodeCounter;

  /** Mark all nodes of eligible OSM ways (e.g., road, rail, etc.) to be parsed during the main processing phase
   * 
   * @param osmWay to handle
   * @param tags of the OSM way
   */
  protected void handleEligibleOsmWay(OsmWay osmWay, Map<String,String> tags) {
    var settings = getSettings();
     
    if(settings.isKeepOsmWayOutsideBoundingPolygon(osmWay.getId())) {

      if(!settings.hasBoundingBoundary()){
        LOGGER.warning("OSM way %d is marked for inclusion beyond bounding polygon but not boundary was set, verify correctness");
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
    }
    
    /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that is deemed eligible (road, rail, or boundary) */
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      getNetworkData().getOsmNodeData().preRegisterEligibleOsmNode(osmWay.getNodeId(index));
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
    this.nodeCounter = new LongAdder();
    this.stage = preProcessStage;
  }

  /**
   * Count total number of nodes in OSM file, or register them for the bounding boundary to be constructed
   */
  @Override
  public void handle(OsmNode node) {
    switch (stage){
      case REGULAR_PREPROCESSING:
        nodeCounter.increment();
        return;
      case FINALISE_BOUNDARY_BY_NAME:
        // register actual instance so available during complete() where we construct the boundary from the OSM ways and nodes
        // that make up the actual bounding boundary polygon
        //todo: instead of using the osmNodeData transfer this to the boundaryManager
        // (do the same for the zoning handler version of this)
        if(getNetworkData().getOsmNodeData().containsPreregisteredOsmNode(node.getId())){
          getNetworkData().getOsmNodeData().registerEligibleOsmNode(node);
        }
        return;
      default:
        return;
    }
  }


  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon we
   * extract their nodes and mark them for inclusion as exceptions to the bounding polygon filter that is
   * applied during the main parsing pass in the regular PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) {
    // no action if stage is Stage.IDENTIFY_BOUNDARY_BY_NAME or Stage.FINALISE_BOUNDARY_BY_NAME
    if(!stage.equals(Stage.REGULAR_PREPROCESSING)) {
      return;
    }

    // update registered OSM way ids with actual OSM way containing geometry (if needed)
    boolean match = boundaryManager.stepTwoAttachBoundaryRelationMemberOsmWays(osmWay);
    if(match){
      handleEligibleOsmWay(osmWay, OsmModelUtil.getTagsAsMap(osmWay));
    }

    // regular OSM way parsing representing infrastructure for networks
    wrapHandleInfrastructureOsmWay(osmWay, this::handleEligibleOsmWay);
  }

  /**
   * PRe-process OSM relations solely for the purpose in case a bounding boundary has been specified by name in which case
   * we extract it and convert it into a bounding polygon to use. If it is not found then we log a severe indicating the issue
   * and proceed without a bounding polygon/restriction in what we parse
   */
  @Override
  public void handle(OsmRelation osmRelation) {
    // no action unless stage is Stage.IDENTIFY_BOUNDARY_BY_NAME
    if(!stage.equals(Stage.IDENTIFY_BOUNDARY_BY_NAME)){
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

    if(stage.equals(Stage.REGULAR_PREPROCESSING)) {
      // regular pre-registration for networks
      int totalOsmNodes = (int) nodeCounter.sum();
      int preRegisteredOsmNodes = getNetworkData().getOsmNodeData().getRegisteredOsmNodes().size();
      LOGGER.info(String.format("Total OSM nodes in source: %d",totalOsmNodes));
      LOGGER.info(String.format("Total OSM nodes identified as part of network: %d (%.2f%%)",preRegisteredOsmNodes, preRegisteredOsmNodes/(double)totalOsmNodes));
    }else if(stage.equals(Stage.FINALISE_BOUNDARY_BY_NAME)) {
      // finalise bounding boundary now that OSM nodes are registered and available for polygon building
      boundaryManager.stepThreeCompleteConstructionBoundingBoundary(getNetworkData().getOsmNodeData().getRegisteredOsmNodes());
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

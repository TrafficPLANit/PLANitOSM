package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.*;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.*;

import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.OsmPtVersionScheme;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.zoning.Zoning;

/**
 * Handler that is applied before we conduct the actual handling of the zones by exploring the OSM relations
 * in the file and highlighting a subset of ways that we are supposed to retain even though they are not tagged
 * by themselves in a way that warrants keeping them in the context of OSM zoning. However, because they are
 * vital to the OSM relations that are considered as part of the zoning parsing we should  keep them. In addition,
 * we only consider such relations and their member entities if they fall at least partly within the bounds
 * specified (if any) by mean sof being marked as spatially eligible.
 * <p>
 *     For pre-processing the relations relevant to the zoning parsing, three stages are conducted,
 *     where we start with IDENTIFY_PLATFORM_AS_RELATIONS, only spatially eligible platform relations are considered
 *     by marking nodes and ways spatially eligible based on the bounding polygon (if any), then run again
 *     with PREREGISTER_ZONING_WAY_NODES which will pre-register all nodes for the ways that were identified as platform
 *     AND will pre-register all remaining unregistered OSM nodes part of relations that are not part of the physical network,
 *     such as station nodes. In the last stage we register the full node instances themselves
 * </p>
 *
 * @author markr
 * 
 *
 */
public class OsmZoningPreProcessingHandler extends OsmZoningHandlerBase {

  /**
   * Preprocessing of platform relations has three stages, identified by this enum
   */
  public enum Stage {
    ONE_IDENTIFY_ZONING_RELATION_MEMBERS,
    TWO_PREREGISTER_ZONING_WAY_NODES,
    THREE_REGISTER_ZONING_NODES_AND_WAYS,
  }

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningPreProcessingHandler.class.getCanonicalName());

  /** track processing stage within pre-processor */
  private final Stage stage;

  /** Determine if relation represents a platform worth retaining
   * @param osmRelation to verify
   * @param tags of relation
   */
  private void identifyRelationAsComplexPlatform(OsmRelation osmRelation, Map<String, String> tags) {

    /* conditions to pre-process/mark for keeping a relation representing a pt platform by its outer-role geometry */
    boolean preserveOuterRole = false;
    boolean multiPolygonFound = false;
    if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.MULTIPOLYGON) &&
            tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
      /* only consider multi-polygons representing public_transport=platform to be parsed as relation based pt platforms*/
      multiPolygonFound = true;
      preserveOuterRole = true;
    }else if( tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.PUBLIC_TRANSPORT) &&
            tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
      /* alternatively mark relations that represent a complex platform to keep the outer role geometry to be parsed */
      preserveOuterRole = true;
    }

    /* preserve information is outer role OSM way, so we can parse it as a transfer zone if needed in post_processing */
    if(preserveOuterRole) {

      int numberOfMembers = osmRelation.getNumberOfMembers();
      for(int index = 0 ;index < numberOfMembers ; ++ index) {
        OsmRelationMember member = osmRelation.getMember(index);

        /* skip if explicitly excluded */
        if(skipOsmPtEntity(member)) {
          continue;
        }

        /* only collect outer area, mapped as ways */
        if(member.getType() == EntityType.Way && member.getRole().equals(OsmMultiPolygonTags.OUTER_ROLE)) {

          /* mark for keeping in regular handler despite not having specific PT tags */
          getZoningReaderData().getOsmData().markOsmRelationOuterRoleOsmWayToKeep(member.getId());

          if(multiPolygonFound){
            getProfiler().incrementMultiPolygonPlatformCounter();
          }else{
            getProfiler().incrementPlatformRelationCounter();
          }
        }
      }

    }
  }

  /** pre-register all nodes of given OSM way on data to be kept in memory during main parsing phase
   *
   * @param osmWay to pre-register nodes for
   */
  private void registerOsmWayNodes(final OsmWay osmWay) {
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      getZoningReaderData().getOsmData().getOsmNodeData().preregisterEligibleOsmNode(osmWay.getNodeId(index));
    }
  }

  /** pre-register all nodes of given OSM way on data to be kept in memory during main parsing phase (may be delayed)
   * Invoked wrapped only so we know the osm way is Pt infrastructure based otherwise the marking of these OSM ways
   * upgrading them form spatial to spatial AND PT enabled is not correct
   *
   * @param osmWay to pre-register nodes for
   */
  private void preRegistrationOfSpatialAndPtCompatibleOsmWayNodes(final OsmWay osmWay){
    if(getZoningReaderData().getOsmData().getOsmSpatialEligibilityData().isOsmWaySpatiallyEligible(osmWay.getId())) {
      getZoningReaderData().getOsmData().getOsmNodeData().preregisterOsmWayNodes(osmWay);
    }
  }

  /**
   * Identify ways identified as platforms part of a relation and pre-register its nodes for in-memory retainment
   *
   * @param osmWay to check
   */
  private void identifyPlatformOuterRoleNodes(final OsmWay osmWay) {
    if(getZoningReaderData().getOsmData().shouldOsmRelationOuterRoleOsmWayBeKept(osmWay)){

      /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that will be parsed as platform */
      registerOsmWayNodes(osmWay);
    }
  }

  /**
   * Register OSM way as spatially eligible if it has at least one spatially eligible member (node or way)
   *
   * @param osmRelation to check
   * @param tags  tags of relation
   */
  private void markOsmRelationAndMembersSpatiallyEligibleIfHasSpatiallyEligibleMember(
      OsmRelation osmRelation, Map<String, String> tags) {
    var osmBoundaryData = getZoningReaderData().getOsmData().getOsmSpatialEligibilityData();

    /* lastly make sure at least one member of relation is spatially eligible */
    boolean relationSpatiallyEligible = false;
    for(int index = 0; index <osmRelation.getNumberOfMembers(); ++index){
      var member = osmRelation.getMember(index);
      if(member.getType() == EntityType.Node && osmBoundaryData.isOsmNodeSpatiallyEligible(member.getId())){
        relationSpatiallyEligible = true;
        break;
      }
      if(member.getType() == EntityType.Way && osmBoundaryData.isOsmWaySpatiallyEligible(member.getId())){
        relationSpatiallyEligible = true;
        break;
      }
    }
    if(relationSpatiallyEligible){
      osmBoundaryData.markOsmRelationSpatiallyEligible(osmRelation.getId());

      /* in addition, also mark all nodes and ways spatially eligible so entire relation can be parsed if available */
      for(int index = 0; index <osmRelation.getNumberOfMembers(); ++index){
        var member = osmRelation.getMember(index);
        if(member.getType() == EntityType.Node){
          osmBoundaryData.markOsmNodeSpatiallyEligible(member.getId());
        }
        if(member.getType() == EntityType.Way && osmBoundaryData.isOsmWaySpatiallyEligible(member.getId())){
          osmBoundaryData.markOsmWaySpatiallyEligible(member.getId());
        }
      }
    }
  }

  /** given the OSM way determine if its nodes are to be pre-registered such that are kept in-memory during the main processing pass
   *
   * @param osmWay to check
   * @param osmPtVersionScheme to use
   * @param tags tags
   */
  private void preRegisterNodesOfSpatiallyEligiblePtWay(
      OsmWay osmWay, OsmPtVersionScheme osmPtVersionScheme, Map<String, String> tags) {
    // unused parameters are present to make it callable from generic wraphandler...

    // all within boundary nodes of eligible OSM ways are to be pre-registered for in-memory storage during main pass
    preRegistrationOfSpatialAndPtCompatibleOsmWayNodes(osmWay);
  }

  private void preRegisterEligiblePtNodesOfRelation(OsmRelation osmRelation, Map<String, String> stringStringMap) {

    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);

      if (skipOsmPtEntity(member)) {
        continue;
      }

      if (member.getType().equals(EntityType.Node) && StringUtils.isNullOrBlank(member.getRole())) {
        // node member without role, but possibly  representing a platform, pole, or other supported infrastructure
        // so pre-register for retaining in memory such that it is accessible in main pass when revisiting/salvaging by inferring role
        // based on underlying tagging
        getZoningReaderData().getOsmData().getOsmNodeData().preregisterEligibleOsmNode(member.getId());
      }

      /* platform */
//      if(member.getRole().equals(OsmPtv2Tags.PLATFORM_ROLE)) {
//      }
    }
  }


  /**
   * Constructor
   *
   * @param referenceNetwork   to use
   * @param zoningToPopulate   to populate
   * @param transferSettings   for the handler
   * @param zoningReaderData   to use for storage of temporary information, or data that is to be made available to later handlers
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @param stage              indicating what stage this pre-processing is in.Depending on the stage different pre-processing actinos are undertaken
   * @param profiler           to use
   */
  public OsmZoningPreProcessingHandler(
      final PlanitOsmNetwork referenceNetwork,
      final Zoning zoningToPopulate,
      final OsmPublicTransportReaderSettings transferSettings,
      OsmZoningReaderData zoningReaderData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      Stage stage,
      OsmZoningHandlerProfiler profiler) {
    super(transferSettings, zoningReaderData, network2ZoningData, referenceNetwork,zoningToPopulate, profiler);
    this.stage = stage;
  }
  
  /**
   * Call this BEFORE we apply the handler
   * 
   */
  public void initialiseBeforeParsing(){
    reset(); 
  }

  /**
   * Register eligible nodes for the bounding boundary to be constructed
   */
  @Override
  public void handle(OsmNode node) {

    if(stage == Stage.ONE_IDENTIFY_ZONING_RELATION_MEMBERS) {

      // mark spatially eligible if bounding area is present and it falls within this area, if no bounding area then all are eligible
      // used to exclude identifying relations that do not have any preregistered nodes available
      if(!getZoningReaderData().hasBoundingArea() ||
          OsmNodeUtils.createPoint(node).within(getZoningReaderData().getBoundingArea().getBoundingPolygon())){

        getZoningReaderData().getOsmData().getOsmSpatialEligibilityData().markOsmNodeSpatiallyEligible(node.getId());
      }

    }else if(stage == Stage.THREE_REGISTER_ZONING_NODES_AND_WAYS){

      // finalise as now all remaining pre-registered nodes can be captured (for example those part of ways not fully
      // in bounding area)
      var osmNodeData = getZoningReaderData().getOsmData().getOsmNodeData();
      if(osmNodeData.containsPreregisteredOsmNode(node.getId())) {
        osmNodeData.registerEligibleOsmNode(node);
      }
    }

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handle(OsmWay osmWay) {

    if(stage == Stage.ONE_IDENTIFY_ZONING_RELATION_MEMBERS){

      // only when an OSM way has at least one pre-registered node we mark it as eligible because if not then it falls
      // outside the bounding area, or no nodes are available. We utilise preregistered nodes and ways to identify which
      // relations are worth keeping, i.e., fall at least partially within bounding area
      var spatialEligibilityData = getZoningReaderData().getOsmData().getOsmSpatialEligibilityData();
      if(!getZoningReaderData().hasBoundingArea()){
        spatialEligibilityData.markOsmWaySpatiallyEligible(osmWay.getId());
      }else{
        spatialEligibilityData.markOsmWaySpatiallyEligibleIfHasSpatiallyEligibleNode(osmWay);
      }

    }else if(stage == Stage.TWO_PREREGISTER_ZONING_WAY_NODES) {

      /* identify nodes of way that would normally not be considered PT but has been identified as such in preceding pre-processing pass */
      identifyPlatformOuterRoleNodes(osmWay);

      /* regular OSM way handling of eligible PT identified OSM ways */
      wrapHandleSpatialAndPtCompatibleOsmWay(osmWay, this::preRegisterNodesOfSpatiallyEligiblePtWay);
    }
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {

    if (stage == Stage.ONE_IDENTIFY_ZONING_RELATION_MEMBERS) {
      /* STAGE 1: identify members of bounding boundary if the OSM relation matches and it is spatially eligible  */


      /* mark as spatially eligible relation if applicable to filter remaining action below */
      wrapHandlePtCompatibleOsmRelation(osmRelation, this::markOsmRelationAndMembersSpatiallyEligibleIfHasSpatiallyEligibleMember);

      //todo: bottom two calls can be combined if we create method that calls both in succession

      /* delegate to identifyPlatformAsRelation when eligible both spatially */
      wrapHandleSpatialAndPtCompatibleOsmRelation(osmRelation, this::identifyRelationAsComplexPlatform);

      /* delegate to identify OSM nodes to be collected in memory for when processing relations they are contained in later on */
      wrapHandleSpatialAndPtCompatibleOsmRelation(osmRelation, this::preRegisterEligiblePtNodesOfRelation);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // nothing yet
  }
  
}

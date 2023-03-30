package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.*;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.*;

import org.goplanit.osm.util.OsmPtVersionScheme;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.zoning.Zoning;

/**
 * Handler that is applied before we conduct the actual handling of the zones by exploring the OSM relations
 * in the file and highlighting a subset of ways that we are supposed to retain even though they are not tagged
 * by themselves in a way that warrants keeping them. However, because they are vital to the OSM relations we should
 * keep them.
 * <p>
 * To avoid keeping all ways and nodes in memory, we preprocess by first identifying which nodes/ways we must keep to be able
 * to properly parse the OSM relations (that are always parsed last).
 * </p>
 * <p>
 *     Run pre-processing twice, first stage with IDENTIFY_PLATFORM_AS_RELATIONS, then when these platforms have been identified
 *     run again with IDENTIFY_PT_NODES which will pre-register all nodes for the ways that were identified as platform AND
 *     will pre-register all remaining unregistered OSM nodes part of relations that are not part of the physical network, such as
 *     station nodes
 * </p>
 * 
 * @author markr
 * 
 *
 */
public class OsmZoningPreProcessingHandler extends OsmZoningHandlerBase {

  /**
   * Preprocessing of platform relations has two stages, identified by this enum
   */
  public enum Stage {
    IDENTIFY_PLATFORM_AS_RELATIONS,
    IDENTIFY_PT_NODES,
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
  private void identifyPlatformAsRelation(OsmRelation osmRelation, Map<String, String> tags) {

    /* conditions to pre-process/mark for keeping a relation representing a pt platform by its outer-role geometry */
    boolean preserveOuterRole = false;
    if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.MULTIPOLYGON) &&
            tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
      /* only consider multi-polygons representing public_transport=platform to be parsed as relation based pt platforms*/
      getProfiler().incrementMultiPolygonPlatformCounter();
      preserveOuterRole = true;
    }else if( tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.PUBLIC_TRANSPORT) &&
            tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {
      /* alternatively mark relations that represent a complex platform to keep the outer role geometry to be parsed */
      getProfiler().incrementPlatformRelationCounter();
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
        }
      }
    }
  }

  /** pre-register all nodes of given OSM way on data to be kept in memory during main parsing phase
   *
   * @param osmWay to pre-register nodes for
   */
  private void preRegisterPtNodes(final OsmWay osmWay) {
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      getZoningReaderData().getOsmData().getOsmNodeData().preRegisterEligibleOsmNode(osmWay.getNodeId(index));
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
      preRegisterPtNodes(osmWay);
    }
  }

  /** given the OSM way determine if its nodes are to be pre-registered such that are kept in-memory during the main processing pass
   *
   * @param osmWay to check
   * @param osmPtVersionScheme to use
   * @param tags tags
   */
  private void preRegisterEligiblePtNodesOfWay(OsmWay osmWay, OsmPtVersionScheme osmPtVersionScheme, Map<String, String> tags) {
    // all nodes of eligible OSM ways are to be pre-registered for in-memory storage during main pass
    preRegisterPtNodes(osmWay);
  }

  private void preRegisterEligiblePtNodesOfRelation(OsmRelation osmRelation, Map<String, String> stringStringMap) {

    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);

      if (skipOsmPtEntity(member)) {
        continue;
      }

      if (member.getType().equals(EntityType.Node) && StringUtils.isNullOrBlank(member.getRole())) {
        // node member without role, but possibly  representing a platform, pole, or other supported infrastructure
        // so pre-register for retaining in memory such that it accessible in main pass when revisiting/salvaging by inferring role
        // based on underlying tagging
        getZoningReaderData().getOsmData().getOsmNodeData().preRegisterEligibleOsmNode(member.getId());
      }

      /* platform */
      if (member.getRole().equals(OsmPtv2Tags.PLATFORM_ROLE)) {

      }
    }
  }


  /**
   * Constructor
   *
   * @param referenceNetwork to use
   * @param zoningToPopulate to populate
   * @param transferSettings for the handler
   * @param zoningReaderData to use for storage of temporary information, or data that is to be made available to later handlers
   * @param stage indicating what stage this pre-processing is in.Depending on the stage different pre-processing actinos are undertaken
   * @param profiler to use
   */
  public OsmZoningPreProcessingHandler(
      final PlanitOsmNetwork referenceNetwork,
      final Zoning zoningToPopulate,
      final OsmPublicTransportReaderSettings transferSettings,
      OsmZoningReaderData zoningReaderData,
      Stage stage,
      OsmZoningHandlerProfiler profiler) {
    super(transferSettings, zoningReaderData, referenceNetwork,zoningToPopulate, profiler);
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
   * {@inheritDoc}
   */
  @Override
  public void handle(OsmWay osmWay) {

    if(stage != Stage.IDENTIFY_PT_NODES){
      return;
    }

    /* identify nodes of way that would normally not be considered PT but has been identified as such in preceding pre-processing pass */
    identifyPlatformOuterRoleNodes(osmWay);

    /* regular OSM way handling of eligible PT identified OSM ways */
    wrapHandlePtOsmWay(osmWay, this::preRegisterEligiblePtNodesOfWay);
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {

    if(stage != Stage.IDENTIFY_PLATFORM_AS_RELATIONS){
      return;

    }

    /* delegate to identifyPlatformAsRelation when eligible */
    wrapHandlePtOsmRelation(osmRelation, this::identifyPlatformAsRelation);

    /* delegate to identify OSM nodes to be collected in memory for when processing relations they are contained in later on */
    wrapHandlePtOsmRelation(osmRelation, this::preRegisterEligiblePtNodesOfRelation);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {         
    
    LOGGER.fine(" OSM zone pre-parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // nothing yet
  }
  
}

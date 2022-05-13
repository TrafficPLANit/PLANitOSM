package org.goplanit.osm.converter.zoning.handler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.tags.*;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.util.OsmPtVersionScheme;

/**
 * Handler that is applied before we conduct the actual handling of the zones by exploring the OSM relations
 * in the file and highlighting a subset of ways that we are supposed to retain even though they are not tagged
 * by themselves in a way that warrants keeping them. However, because they are vital to the OSM relations we should
 * keep them.
 * 
 * To avoid keeping all ways and nodes in memory, we preprocess by first identifying which nodes/ways we must keep to be able
 * to properly parse the OSM relations (that are always parsed last).
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

  /** determine if relation represents a platform worth retaining
   *
   */
  private void identifyPlatformAsRelation(OsmRelation osmRelation) {

    boolean preserveOuterRole = false;
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmRelation);
    /* only parse when parser is active and type is available */
    if(getSettings().isParserActive() && tags.containsKey(OsmRelationTypeTags.TYPE)) {

      /* multi_polygons can represent public transport platforms */
      if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.MULTIPOLYGON)) {

        /* only consider public_transport=platform multi-polygons */
        if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {

          getProfiler().incrementMultiPolygonPlatformCounter();
          preserveOuterRole = true;

        }
      }else if( tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.PUBLIC_TRANSPORT) &&
              OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.PLATFORM_ROLE)) {

        getProfiler().incrementPlatformRelationCounter();
        preserveOuterRole = true;
      }
    }

    /* preserve information is outer role OSM way so we can parse it as a transfer zone if needed in post_processing */
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
          /* mark for keeping in regular handler */
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
      getNetworkToZoningData().preRegisterEligibleOsmNode(osmWay.getNodeId(index));
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
  private void identifyEligiblePublicTransportNodes(OsmWay osmWay, OsmPtVersionScheme osmPtVersionScheme, Map<String, String> tags) {
    // all nodes of eligible OSM ways are to be pre-registered for in-memory storage during main pass
    preRegisterPtNodes(osmWay);
  }

  /**
   * Constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData to use for storage of temporary information, or data that is to be made available to later handlers
   * @param profiler to use
   */
  public OsmZoningPreProcessingHandler(final OsmPublicTransportReaderSettings transferSettings, OsmZoningReaderData zoningReaderData, Stage stage, OsmZoningHandlerProfiler profiler) {
    super(transferSettings, zoningReaderData, null, profiler);
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
  public void handle(OsmWay osmWay) throws IOException {

    if(stage != Stage.IDENTIFY_PT_NODES){
      return;
    }

    /* identify nodes of way that would normally not be considered PT but has been identified as such in preceding pre-processing pass */
    identifyPlatformOuterRoleNodes(osmWay);

    /* regular OSM way handling of eligible PT identified OSM ways */
    wrapHandlePtOsmWay(osmWay, this::identifyEligiblePublicTransportNodes);

  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {

    if(stage != Stage.IDENTIFY_PLATFORM_AS_RELATIONS){
      return;
    }

    identifyPlatformAsRelation(osmRelation);
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

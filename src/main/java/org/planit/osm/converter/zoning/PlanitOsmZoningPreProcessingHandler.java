package org.planit.osm.converter.zoning;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.tags.*;
import org.planit.utils.exceptions.PlanItException;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

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
public class PlanitOsmZoningPreProcessingHandler extends PlanitOsmZoningBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningPreProcessingHandler.class.getCanonicalName());         

  /**
   * Constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData to use for storage of temporary information, or data that is to be made available to later handlers
   * @param profiler to use
   */
  public PlanitOsmZoningPreProcessingHandler(final PlanitOsmPublicTransportReaderSettings transferSettings, PlanitOsmZoningReaderData zoningReaderData, PlanitOsmZoningHandlerProfiler profiler) {   
    super(transferSettings, zoningReaderData, null, null, profiler);    
  }
  
  /**
   * Call this BEFORE we apply the handler
   * 
   * @throws PlanItException thrown if error
   */
  public void initialiseBeforeParsing() throws PlanItException {
    reset(); 
  } 
    
  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    
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
    
    /* preserve information is outer role osm way so we can parse it as a transfer zone if needed in post_processing */
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

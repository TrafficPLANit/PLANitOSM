package org.goplanit.osm.converter.network;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.tags.*;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Link;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations. We parse these entities in distinct order, first all nodes, then all ways, and then all relations. this allows
 * us to incrementally construct the network without backtracking or requiring the entire file to be in memory in addition to the memory model we're creating.
 * 
 * @author markr
 * 
 *
 */
public class OsmNetworkMainProcessingHandler extends OsmNetworkBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkMainProcessingHandler.class.getCanonicalName());
  
       
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a planit link
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   */
  private boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId){
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId, getSettings().getOsmNetworkToPopulate(), getNetworkData());
  }                                           
       
  
  /**
   * now parse the remaining circular osmWays, which by default are converted into multiple links/linksegments for each part of
   * the circular way in between connecting in and outgoing links/linksegments that were parsed during the regular parsing phase
   * 
   * @param circularOsmWay the circular osm way to parse 
   * @throws PlanItException thrown if error
   */
  private void handleRawCircularWay(final OsmWay circularOsmWay) throws PlanItException {
        
    Map<NetworkLayer, Set<Link>> createdLinksByLayer = null;    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(circularOsmWay);
    if(isActivatedRoadOrRailwayBasedInfrastructure(tags)) {
      
      /* only process circular ways that are complete, e.g. not near bounding box causing some nodes to be missing
       * in which case we do not parse the entire circular way to avoid issues */
      if(!OsmWayUtils.isAllOsmWayNodesAvailable(circularOsmWay, getNetworkData().getOsmNodeData().getRegisteredOsmNodes())){
        return;
      }
      
      createdLinksByLayer = handleRawCircularWay(circularOsmWay, tags, 0 /* start at initial index */);
      
      if(createdLinksByLayer!=null) {
        /* register that osm way has multiple planit links mapped (needed in case of subsequent break link actions on nodes of the osm way */
        for( Entry<NetworkLayer, Set<Link>> entry : createdLinksByLayer.entrySet()) {
          OsmNetworkReaderLayerData layerData = getNetworkData().getLayerParsers().get(entry.getKey()).getLayerData();
          layerData.updateOsmWaysWithMultiplePlanitLinks(circularOsmWay.getId(), entry.getValue());
        }
      }

    }
  }  
  
  /** Recursive method that processes osm ways that have at least one circular section in it, but this might not be perfect, i.e., the final node might
   * not connect to the initial node. to deal with this, we first identify the non-circular section(s), extract separate links for them, and then process
   * the remaining (perfectly) circular component of the OSM way via {@code handlePerfectCircularWay}
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param initialNodeIndex offset for starting point, part of the recursion
   * @return set of created links per layer for this circular way if any, empty set if none
   * @throws PlanItException thrown if error
   */
  private Map<NetworkLayer, Set<Link>> handleRawCircularWay(final OsmWay circularOsmWay, final Map<String, String> osmWayTags, int initialNodeIndex) throws PlanItException {
    Map<NetworkLayer, Set<Link>> createdLinksByLayer = new HashMap<NetworkLayer, Set<Link>>();  
    int finalNodeIndex = (circularOsmWay.getNumberOfNodes()-1);
        
    /* when circular road is not perfect, i.e., its end node is not the start node, we first split it
     * in a perfect circle and a regular non-circular osmWay */
    Pair<Integer,Integer> firstCircularIndices = OsmWayUtils.findIndicesOfFirstLoop(circularOsmWay, initialNodeIndex);            
    if(firstCircularIndices != null) {    
      /* unprocessed circular section exists */

      if(firstCircularIndices.first() > initialNodeIndex ) {
        /* create separate link for the lead up part that is NOT circular, if supporting multiple modes mapped to different layers we get multiple links */         
        Map<NetworkLayer,Link> newLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, initialNodeIndex, firstCircularIndices.first(), false /* not a circular section */);
        if(newLinkByLayer != null) {
          newLinkByLayer.forEach( (layer, link) -> { 
            createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>());
            createdLinksByLayer.get(layer).add(link);} );
        }
        /* update offsets for circular part */
        initialNodeIndex = firstCircularIndices.first();
      }
      
      /* continue with the remainder (if any) starting at the end point of the circular component 
       * this is done first because we want all non-circular components to be available as regular links before processing the circular parts*/
      if(firstCircularIndices.second() < finalNodeIndex) {
        Map<NetworkLayer, Set<Link>> newLinksByLayer = handleRawCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.second());
        if(newLinksByLayer != null) {
          newLinksByLayer.forEach( (layer, links) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>()); 
            createdLinksByLayer.get(layer).addAll(links);} );
        }
      }      
        
      /* extract the identified perfectly circular component */
      Map<NetworkLayer,Set<Link>> newLinksByLayer = handlePerfectCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.first(), firstCircularIndices.second());
      if(newLinksByLayer != null) {
        newLinksByLayer.forEach( (layer, link) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>());
          createdLinksByLayer.get(layer).addAll(link);} );
      }
      
    }else if(initialNodeIndex < finalNodeIndex) {
      /* last section is not circular, so extract partial link for it */
      Map<NetworkLayer,Link> newLinksByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, initialNodeIndex, finalNodeIndex, false /* not a circular section */);
      if(newLinksByLayer != null) {
        newLinksByLayer.forEach( (layer, link) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>());
          createdLinksByLayer.get(layer).add(link);} );
      }     
    }  
    
    return createdLinksByLayer;
  }

  /** Process a circular way that is assumed to be perfect for the given start and end node, i.e., its end node is the same as its start node
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param initialNodeIndex where the circular section starts
   * @param finalNodeIndex where the circular section ends (at the start)
   * @return set of created links per layer with supported modes for this circular way if any, empty set if none
   * @throws PlanItException thrown if error
   */
  private Map<NetworkLayer,Set<Link>> handlePerfectCircularWay(OsmWay circularOsmWay, Map<String, String> osmWayTags, int initialNodeIndex, int finalNodeIndex) throws PlanItException {

    
    Map<NetworkLayer,Set<Link>> createdLinksByLayer = new HashMap<>();
    int firstPartialLinkStartNodeIndex = -1;
    int partialLinkStartNodeIndex = -1;
    int partialLinkEndNodeIndex = -1;
    int numberOfConsideredNodes = finalNodeIndex-initialNodeIndex;
    boolean partialLinksPartOfCircularWay = true;
    
    /* construct partial links based on nodes on the geometry that are an extreme node of an already parsed link or are an internal node of an already parsed link */
    for(int index = initialNodeIndex ; index <= finalNodeIndex ; ++index) {
      long osmNodeId = circularOsmWay.getNodeId(index);
              
      if(hasNetworkLayersWithActiveOsmNode(osmNodeId)) {                            
        if(partialLinkStartNodeIndex < 0) {
          /* set first node to earlier realised node */
          partialLinkStartNodeIndex = index;
          firstPartialLinkStartNodeIndex = partialLinkStartNodeIndex;
        }else if(!(index==finalNodeIndex && partialLinkStartNodeIndex==firstPartialLinkStartNodeIndex)) {            
          /* identified valid partial link (statement above makes sure that in case the one duplicate node (first=last) is chosen as partial link, we do not accept is as a partial link as it represents  the entire loop, otherwise
           * create link from start node to the intermediate node that attaches to an already existing planit link on the circular way */          
          Map<NetworkLayer, Link> createdLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, index, partialLinksPartOfCircularWay);
          if(createdLinkByLayer != null) {
            createdLinkByLayer.forEach( (layer, link) -> {
              createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>());
              createdLinksByLayer.get(layer).add(link);} );
            
            /* update first node to last node of this link for next partial link */
            partialLinkEndNodeIndex = index;
            partialLinkStartNodeIndex = partialLinkEndNodeIndex;                
          }                         
        }
      }
    }
    
    if(partialLinkStartNodeIndex < 0) {
      /* nothing parsed yet... */
      Map<MacroscopicNetworkLayerImpl,Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayer = extractLinkSegmentTypes(circularOsmWay, osmWayTags);
      if(linkSegmentTypesByLayer!=null && !linkSegmentTypesByLayer.isEmpty() && linkSegmentTypesByLayer.values().stream().findAny().get().anyIsNotNull()) {
        /* yet circular way is of a viable type, i.e., it has mapped link segment type(s), but not a single connection to currently parsed network exists, this may indicate a problem */
        LOGGER.fine(String.format("circular way %d appears to have no connections to activated OSM way types ", circularOsmWay.getId()));
        /* still we continue parsing it by simply creating a new planit node, marked by setting partialLinkStartNodeIndex to 0  and continue */ 
        partialLinkStartNodeIndex = 0;
      }
    }
    
    Map<NetworkLayer, Link> createdLinkByLayer = null;
    if (partialLinkStartNodeIndex>= 0) {
      if (partialLinkEndNodeIndex < 0){        
        /* first partial link is not created either, only single connection point exists, so:
         * 1) when partialLinkStartNodeIndex = initial node -> take the halfway point as the dummy node, and the final node as the end point, if not then...
         * 2) reset partialLinkStartNodeIndex to initial node and the earlier found partialLinkStartNodeIndex as the midway point and then the final node as the end point */
        if(partialLinkStartNodeIndex == initialNodeIndex) {
          partialLinkEndNodeIndex = partialLinkStartNodeIndex + (numberOfConsideredNodes/2);  
        }else {
          partialLinkEndNodeIndex = partialLinkStartNodeIndex; 
          partialLinkStartNodeIndex = initialNodeIndex;
        }
        createdLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
        if(createdLinkByLayer != null) {
          createdLinkByLayer.forEach( (layer, link) -> {
            createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>());
            createdLinksByLayer.get(layer).add(link);} );
        }
        
        partialLinkStartNodeIndex = partialLinkEndNodeIndex;
        partialLinkEndNodeIndex = finalNodeIndex;
        createdLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
      }else if(partialLinkEndNodeIndex != finalNodeIndex){            
        /* last partial link did not end at end of circular way but later, i.e., first partial link did not start at node zero.
         * finalise by creating the final partial link to the first partial links start node*/
        partialLinkEndNodeIndex = firstPartialLinkStartNodeIndex;       
        createdLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, partialLinkEndNodeIndex, partialLinksPartOfCircularWay);
      }    
      
      /* possibly no links created, for example when circular way is not of a viable type, or access is private, or some other valid reason*/
      if(createdLinkByLayer != null) {
        createdLinkByLayer.forEach( (layer, link) -> createdLinksByLayer.get(layer).add(link));
      }
    }
    return createdLinksByLayer;    
  }
    

  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param osmWay the way
   * @param tags the tags of this way
   * @return the link segment types per layer if available, otherwise null is returned
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> getDefaultLinkSegmentTypeByOsmWayType(OsmWay osmWay, Map<String, String> tags) {
    String osmTypeKeyToUse = null;
    
    /* exclude ways that are areas and in fact not ways */
    boolean isExplicitArea = OsmTags.isArea(tags);   
    if(isExplicitArea) {
      return null;
    }
    
    var settings = getSettings();
        
    /* highway (road) or railway (rail) */
    boolean isHighway = true;
    if (OsmHighwayTags.hasHighwayKeyTag(tags) && settings.isHighwayParserActive()) {
      osmTypeKeyToUse = OsmHighwayTags.HIGHWAY;      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags) && settings.isRailwayParserActive()) {
      osmTypeKeyToUse = OsmRailwayTags.RAILWAY;
      isHighway = false;
    }
    
    /* without mapping no type */
    if(osmTypeKeyToUse==null) {
      return null;
    }
        
    String osmTypeValueToUse = tags.get(osmTypeKeyToUse);        
    Map<NetworkLayer,MacroscopicLinkSegmentType> linkSegmentTypes = settings.getOsmNetworkToPopulate().getDefaultLinkSegmentTypeByOsmTag(osmTypeValueToUse);
    if(linkSegmentTypes != null) {
      linkSegmentTypes.forEach( (layer, linkSegmentType)  -> {
        if(linkSegmentType != null) {
          getNetworkData().getLayerParser((MacroscopicNetworkLayerImpl)layer).getLayerData().getProfiler().incrementOsmTagCounter(osmTypeValueToUse);
        } });
    }
    /* determine if we should inform the user on not finding a mapped type, i.e., is this of concern or legitimate because we do not want or it cannot be mapped in the first place*/
    else {
      boolean isWayTypeDeactived = isHighway ?
          settings.getHighwaySettings().isOsmHighWayTypeDeactivated(osmTypeValueToUse) : 
          (!settings.isRailwayParserActive() || settings.getRailwaySettings().isOsmRailwayTypeDeactivated(osmTypeValueToUse));
      if(!isWayTypeDeactived) {
        boolean typeConfigurationMissing = isHighway ? OsmHighwayTags.isNonRoadBasedHighwayValueTag(osmTypeValueToUse) : OsmRailwayTags.isNonRailBasedRailway(osmTypeValueToUse);         
        
        /*... not available event though it is not marked as deactivated AND it appears to be a type that can be converted into a link, so something is not properly configured*/
        if(typeConfigurationMissing) {            
          LOGGER.warning(String.format(
              "no link segment type available for OSM way: %s:%s (id:%d) --> ignored. Consider explicitly supporting or unsupporting this type", osmTypeKeyToUse, osmTypeValueToUse, osmWay.getId()));
        }
      }
      
    }
        
    return linkSegmentTypes;
  }  
  
  /** process all registered circular ways after parsing of basic nodes and ways is complete. Because circular ways are transformed into multiple
   * links, they in effect yield multiple links per original OSM way (id). In case such an OSMway is referenced later it no longer maps to a single 
   * PLANit link, hence we return how each OSMway is mapped to the set of links created for the circular way
   *   
   */
  protected  void processCircularWays() {
    
    LOGGER.info("Converting OSM circular ways into multiple link topologies...");
    
    /* process circular ways*/    
    for(Entry<Long,OsmWay> entry : getNetworkData().getOsmCircularWays().entrySet()) {
      try {        
        
        handleRawCircularWay(entry.getValue());
                
      }catch (PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("unable to process circular way OSM id: %d",entry.getKey()));
      }        
    }
    
    LOGGER.info(String.format("Processed %d circular ways...DONE",getNetworkData().getOsmCircularWays().size()));
    getNetworkData().clearOsmCircularWays();
  }
    
  /**
   * extract OSM way's PLANit infrastructure for the entire way, i.e., link, nodes, and link segments where applicable. 
   * The parser will try to infer missing/default data by using defaults set by the user
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   */
  protected void extractOsmWay(OsmWay osmWay, Map<String, String> tags){
    /* parse entire OSM way (0-endNodeIndex), and not part of a circular piece of infrastructure */
    extractPartialOsmWay(osmWay, tags, 0, osmWay.getNumberOfNodes()-1, false /*not part of circular infrastructure */);    
  }

  /**
   * Extract OSM way's PLANit infrastructure for the part of the way that is indicated. When it is marked as being a (partial) section of a circular way, then
   * we only allow the presumed one way direction applicable when creating directional link segments. The result is a newly registered link, its nodes, and linksegment(s) on
   * the network. The parser will try to infer missing/default data by using defaults set by the user.
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @param startNodeIndex to start parsing nodes from
   * @param endNodeIndex to end parsing nodes from
   * @param isPartOfCircularWay indicates if it is part of a circular way or not
   * @return created link (if any), if no link could be created null is returned
   */  
  protected Map<NetworkLayer,Link> extractPartialOsmWay(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex, boolean isPartOfCircularWay) {
        
    Map<NetworkLayer,Link> linksByLayer = null;
    
    Map<MacroscopicNetworkLayerImpl, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayer = extractLinkSegmentTypes(osmWay,tags);
    for(Entry<MacroscopicNetworkLayerImpl, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> entry : linkSegmentTypesByLayer.entrySet()) {
      MacroscopicNetworkLayerImpl networkLayer = entry.getKey();
      Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes = entry.getValue();
      
      if(linkSegmentTypes != null && linkSegmentTypes.anyIsNotNull()) {
        OsmNetworkLayerParser layerHandler = getNetworkData().getLayerParser(networkLayer);
        if(layerHandler == null) {
          throw new PlanItRunTimeException("Layer handler not available, should have been instantiated in PlanitOsmHandler constructor");
        }
        /* delegate to layer handler */
        Link link = layerHandler.extractPartialOsmWay(osmWay, tags, startNodeIndex, endNodeIndex, isPartOfCircularWay, linkSegmentTypes);
        if(link != null) {
          if(linksByLayer==null) {
            linksByLayer = new HashMap<NetworkLayer, Link>();
          }
          linksByLayer.put(networkLayer, link);        
        }
      }
    }    
    
    return linksByLayer;
  }  
    
  /** actual handling of OSM way assuming it is eligible for processing
   * 
   * @param osmWay to parse
   * @param tags of the OSM way
   */
  protected void handleOsmWay(OsmWay osmWay, Map<String, String> tags) {
    
    /* circular ways special case filter */
    if(OsmWayUtils.isCircularOsmWay(osmWay, tags, false)) {          
      
      /* postpone creation of link(s) for activated OSM highways that have a circular component and are not areas (areas cannot become roads) */
      /* Note: in OSM roundabouts are a circular way, in PLANit, they comprise several one-way link connecting exists and entries to the roundabout */
      getNetworkData().addOsmCircularWay(osmWay);
      
    }else{
      
      /* extract regular OSM way; convert to PLANit infrastructure */          
      extractOsmWay(osmWay, tags);                    
                  
    }
  }

  /**
   * Constructor
   * 
   * @param networkData the data used for populating the network
   * @param settings for the handler
   */
  public OsmNetworkMainProcessingHandler(final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings settings) {
    super(networkData, settings);       
  }
   

  /**
   * construct PLANit nodes from OSM nodes
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {
    var settings = getSettings();
    
    /* only track nodes when they are pre-registered (i.e. from features deemed relevant for this parser AND they are 
     * within bounding polygon (if any is defined), or alternatively marked to keep even if falling outside the bounding polygon */
    boolean keepOutsideBoundingPolygon = settings.isKeepOsmNodeOutsideBoundingPolygon(osmNode.getId());    
    if(getNetworkData().containsPreRegisteredOsmNode(osmNode.getId()) &&
        (   !settings.hasBoundingPolygon() ||
            keepOutsideBoundingPolygon ||
            OsmNodeUtils.createPoint(osmNode).within(settings.getBoundingPolygon()))) {
      
      /* store actual OSM node for later processing in memory */
      getNetworkData().registerEligibleOsmNode(osmNode);
      
      if(!keepOutsideBoundingPolygon) {
        /* track bounding box of OSM nodes within bounding polygon (if any) */
        getNetworkData().updateBoundingBox(osmNode);
      }
    }
  }

  /**
   * parse an OSM way to extract link and link segments (including type). If insufficient information
   * is available the handler will try to infer the missing data by using defaults set by the user
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
                
    wrapHandleOsmWay(osmWay, this::handleOsmWay);    
            
  }


  /** extract the correct link segment type based on the configuration of supported modes, the defaults for the given osm way and any 
   * modifications to the mode access based on the passed in tags of the OSM way
   * 
   * @param osmWay the way this type extraction is executed for 
   * @param tags tags belonging to the OSM way
   * @return appropriate link segment types for forward and backward direction per network layer. If no modes are allowed in a direction, the link segment type will be null
   */
  protected Map<MacroscopicNetworkLayerImpl, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> extractLinkSegmentTypes(OsmWay osmWay, Map<String, String> tags){
    Map<MacroscopicNetworkLayerImpl, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayerByDirection = new HashMap<>(); 
    /* a default link segment type should be available as starting point*/
    Map<NetworkLayer, MacroscopicLinkSegmentType> linkSegmentTypesByLayer = getDefaultLinkSegmentTypeByOsmWayType(osmWay, tags);
    if(linkSegmentTypesByLayer != null) {      
      
      /* per layer identify the directional link segment types based on additional access changes from osm tags */      
      for(Entry<NetworkLayer, MacroscopicLinkSegmentType> entry : linkSegmentTypesByLayer.entrySet()) {
        MacroscopicNetworkLayerImpl networkLayer = (MacroscopicNetworkLayerImpl) entry.getKey();
        MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
        
        /* collect possibly modified type (per direction) */
        Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> typesPerdirectionPair = getNetworkData().getLayerParser(networkLayer).updatedLinkSegmentTypeBasedOnOsmWay(osmWay, tags, linkSegmentType);                
        if(typesPerdirectionPair != null) {
          linkSegmentTypesByLayerByDirection.put(networkLayer, typesPerdirectionPair);
        }
      }
    }
    return linkSegmentTypesByLayerByDirection;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {
    
    /* process circular ways */
    processCircularWays();    
            
    /* delegate to each layer handler present */
    for(Entry<MacroscopicNetworkLayer, OsmNetworkLayerParser> entry : getNetworkData().getLayerParsers().entrySet()) {
      OsmNetworkLayerParser networkLayerHandler = entry.getValue();
      
      /* break links on layer with internal connections to multiple osm ways */
      networkLayerHandler.complete();      
    }                 
        
    LOGGER.info(" OSM basic network parsing...DONE");

  }
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    getNetworkData().reset();    
  }  
  
}

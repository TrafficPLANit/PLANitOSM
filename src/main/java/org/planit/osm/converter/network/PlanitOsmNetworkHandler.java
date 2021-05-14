package org.planit.osm.converter.network;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
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
public class PlanitOsmNetworkHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkHandler.class.getCanonicalName());
  
  /** the network data tracking all relevant data during parsing of the osm network */
  private final PlanitOsmNetworkReaderData networkData;

  /** the settings to adhere to */
  private final PlanitOsmNetworkReaderSettings settings;        
    
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a planit link
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  private boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {    
    OsmNode osmNode = networkData.getOsmNode(osmNodeId);
    if(osmNode != null) {      
      for(InfrastructureLayer networkLayer : settings.getOsmNetworkToPopulate().infrastructureLayers) {
        PlanitOsmNetworkLayerParser layerHandler = networkData.getLayerParser((MacroscopicPhysicalNetwork) networkLayer);
        if(layerHandler.getLayerData().isLocationPresentInLayer(PlanitOsmNodeUtils.createPoint(osmNode))){
          return true;
        }        
      }
    }
    return false;
  }                                           
    
  /** verify if tags represent an highway or railway that is specifically aimed at road based or rail based infrastructure, e.g.,
   * asphalt or tracks and NOT an area, platform, stops, etc. and is also activated for parsing based on the settings
   * 
   * @param tags to verify
   * @return true when activated and highway or railway (not an area), false otherwise
   */
  private boolean isActivatedRoadOrRailwayBasedInfrastructure(Map<String, String> tags) {
    
    if(!OsmTags.isArea(tags)) {
      if(settings.isHighwayParserActive() && OsmHighwayTags.hasHighwayKeyTag(tags)) {
        return settings.getHighwaySettings().isOsmHighwayTypeActivated(tags.get(OsmHighwayTags.HIGHWAY));
      }else if(settings.isRailwayParserActive() && OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return settings.getRailwaySettings().isOsmRailwayTypeActivated(tags.get(OsmRailwayTags.RAILWAY));
      }
    }
    return false;
  }  
   
  
  /**
   * now parse the remaining circular osmWays, which by default are converted into multiple links/linksegments for each part of
   * the circular way in between connecting in and outgoing links/linksegments that were parsed during the regular parsing phase
   * 
   * @param circularOsmWay the circular osm way to parse 
   * @throws PlanItException thrown if error
   */
  private void handleRawCircularWay(final OsmWay circularOsmWay) throws PlanItException {
        
    Map<InfrastructureLayer, Set<Link>> createdLinksByLayer = null;    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(circularOsmWay);
    if(isActivatedRoadOrRailwayBasedInfrastructure(tags)) {
      
      /* only process circular ways that are complete, e.g. not near bounding box causing some nodes to be missing
       * in which case we do not parse the entire circular way to avoid issues */
      if(!PlanitOsmWayUtils.isAllOsmWayNodesAvailable(circularOsmWay, networkData.getOsmNodes())){
        return;
      }
      
      createdLinksByLayer = handleRawCircularWay(circularOsmWay, tags, 0 /* start at initial index */);
      
      if(createdLinksByLayer!=null) {
        /* register that osm way has multiple planit links mapped (needed in case of subsequent break link actions on nodes of the osm way */
        for( Entry<InfrastructureLayer, Set<Link>> entry : createdLinksByLayer.entrySet()) {
          PlanitOsmNetworkReaderLayerData layerData = networkData.getLayerParsers().get(entry.getKey()).getLayerData();
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
   * @param finalNodeIndex offset of the final point, part of the recursion
   * @return set of created links per layer for this circular way if any, empty set if none
   * @throws PlanItException thrown if error
   */
  private Map<InfrastructureLayer, Set<Link>> handleRawCircularWay(final OsmWay circularOsmWay, final Map<String, String> osmWayTags, int initialNodeIndex) throws PlanItException {
    Map<InfrastructureLayer, Set<Link>> createdLinksByLayer = new HashMap<InfrastructureLayer, Set<Link>>();  
    int finalNodeIndex = (circularOsmWay.getNumberOfNodes()-1);
        
    /* when circular road is not perfect, i.e., its end node is not the start node, we first split it
     * in a perfect circle and a regular non-circular osmWay */
    Pair<Integer,Integer> firstCircularIndices = PlanitOsmWayUtils.findIndicesOfFirstLoop(circularOsmWay, initialNodeIndex);            
    if(firstCircularIndices != null) {    
      /* unprocessed circular section exists */

      if(firstCircularIndices.first() > initialNodeIndex ) {
        /* create separate link for the lead up part that is NOT circular, if supporting multiple modes mapped to different layers we get multiple links */         
        Map<InfrastructureLayer,Link> newLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, initialNodeIndex, firstCircularIndices.first(), false /* not a circular section */);
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
        Map<InfrastructureLayer, Set<Link>> newLinksByLayer = handleRawCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.second());
        if(newLinksByLayer != null) {
          newLinksByLayer.forEach( (layer, links) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>()); 
            createdLinksByLayer.get(layer).addAll(links);} );
        }
      }      
        
      /* extract the identified perfectly circular component */
      Map<InfrastructureLayer,Set<Link>> newLinksByLayer = handlePerfectCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.first(), firstCircularIndices.second());
      if(newLinksByLayer != null) {
        newLinksByLayer.forEach( (layer, link) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<Link>());
          createdLinksByLayer.get(layer).addAll(link);} );
      }
      
    }else if(initialNodeIndex < finalNodeIndex) {
      /* last section is not circular, so extract partial link for it */
      Map<InfrastructureLayer,Link> newLinksByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, initialNodeIndex, finalNodeIndex, false /* not a circular section */);
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
  private Map<InfrastructureLayer,Set<Link>> handlePerfectCircularWay(OsmWay circularOsmWay, Map<String, String> osmWayTags, int initialNodeIndex, int finalNodeIndex) throws PlanItException {

    
    Map<InfrastructureLayer,Set<Link>> createdLinksByLayer = new HashMap<>();
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
          Map<InfrastructureLayer, Link> createdLinkByLayer = extractPartialOsmWay(circularOsmWay, osmWayTags, partialLinkStartNodeIndex, index, partialLinksPartOfCircularWay);
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
      Map<MacroscopicPhysicalNetwork,Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayer = extractLinkSegmentTypes(circularOsmWay, osmWayTags);
      if(linkSegmentTypesByLayer!=null && !linkSegmentTypesByLayer.isEmpty() && linkSegmentTypesByLayer.values().stream().findAny().get().anyIsNotNull()) {
        /* yet circular way is of a viable type, i.e., it has mapped link segment type(s), but not a single connection to currently parsed network exists, this may indicate a problem */
        LOGGER.fine(String.format("circular way %d appears to have no connections to activated OSM way types ", circularOsmWay.getId()));
        /* still we continue parsing it by simply creating a new planit nodes, marked by setting partialLinkStartNodeIndex to 0  and continue */ 
        partialLinkStartNodeIndex = 0;
      }
    }
    
    Map<InfrastructureLayer, Link> createdLinkByLayer = null;
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
        createdLinkByLayer.forEach( (layer, link) -> { createdLinksByLayer.get(layer).add(link);} );
      }
    }
    return createdLinksByLayer;    
  }
    

  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param way the way
   * @param tags the tags of this way
   * @return the link segment types per layer if available, otherwise null is returned
   */
  protected Map<InfrastructureLayer, MacroscopicLinkSegmentType> getDefaultLinkSegmentTypeByOsmWayType(OsmWay osmWay, Map<String, String> tags) {
    String osmTypeKeyToUse = null;
    
    /* exclude ways that are areas and in fact not ways */
    boolean isExplicitArea = OsmTags.isArea(tags);
    boolean isHighway = true;
    if(isExplicitArea) {
      return null;
    }
      
    /* highway (road) or railway (rail) */
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
    Map<InfrastructureLayer,MacroscopicLinkSegmentType> linkSegmentTypes = settings.getOsmNetworkToPopulate().getDefaultLinkSegmentTypeByOsmTag(osmTypeValueToUse);
    if(linkSegmentTypes != null) {
      linkSegmentTypes.forEach( (layer, linkSegmentType)  -> {
        if(linkSegmentType != null) {
          networkData.getLayerParser((MacroscopicPhysicalNetwork)layer).getProfiler().incrementOsmTagCounter(osmTypeValueToUse);
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
    for(Entry<Long,OsmWay> entry : networkData.getOsmCircularWays().entrySet()) {
      try {        
        
        handleRawCircularWay(entry.getValue());
                
      }catch (PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("unable to process circular way OSM id: %d",entry.getKey()));
      }        
    }
    
    LOGGER.info(String.format("Processed %d circular ways...DONE",networkData.getOsmCircularWays().size()));
    networkData.clearOsmCircularWays();
  }
    
  /**
   * extract OSM way's PLANit infrastructure for the entire way, i.e., link, nodes, and link segments where applicable. 
   * The parser will try to infer missing/default data by using defaults set by the user
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @throws PlanItException thrown if error
   */
  protected void extractOsmWay(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    /* parse entire OSM way (0-endNodeIndex), and not part of a circular piece of infrastructure */
    extractPartialOsmWay(osmWay, tags, 0, osmWay.getNumberOfNodes()-1, false /*not part of circular infrastructure */);    
  }

  /**
   * extract OSM way's PLANit infrastructure for the part of the way that is indicated. When it is marked as being a (partial) section of a circular way, then
   * we only allow the presumed one way direction applicable when creating directional link segments. The result is a newly registered link, its nodes, and linksegment(s) on
   * the network. The parser will try to infer missing/default data by using defaults set by the user.
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @return created link (if any), if no link could be created null is returned
   * @throws PlanItException thrown if error
   */  
  protected Map<InfrastructureLayer,Link> extractPartialOsmWay(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex, boolean isPartOfCircularWay) throws PlanItException {
        
    Map<InfrastructureLayer,Link> linksByLayer = null;
    
    Map<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayer = extractLinkSegmentTypes(osmWay,tags);
    for(Entry<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> entry : linkSegmentTypesByLayer.entrySet()) {
      MacroscopicPhysicalNetwork networkLayer = entry.getKey();
      Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes = entry.getValue();
      
      if(linkSegmentTypes != null && linkSegmentTypes.anyIsNotNull()) {
        PlanitOsmNetworkLayerParser layerHandler = networkData.getLayerParser(networkLayer);
        if(layerHandler == null) {
          throw new PlanItException("Layer handler not available, should have been instantiated in PlanitOsmHandler constructor");
        }
        /* delegate to layer handler */
        Link link = layerHandler.extractPartialOsmWay(osmWay, tags, startNodeIndex, endNodeIndex, isPartOfCircularWay, linkSegmentTypes);
        if(link != null) {
          if(linksByLayer==null) {
            linksByLayer = new HashMap<InfrastructureLayer, Link>();
          }
          linksByLayer.put(networkLayer, link);        
        }
      }
    }    
    
    return linksByLayer;
  }         

  /**
   * constructor
   * 
   * @param networkData the data used for populating the network
   * @param settings for the handler
   */
  public PlanitOsmNetworkHandler(final PlanitOsmNetworkReaderData networkData, final PlanitOsmNetworkReaderSettings settings) {
    this.networkData = networkData;
    this.settings = settings;       
  }
   

  /**
   * construct PLANit nodes from OSM nodes
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {

    /* only track nodes within bounding polygon (if any is defined), or if it is marked to keep even if it outside the bounding polygon */
    boolean keepOutsideBoundingPolygon = settings.isKeepOsmNodeOutsideBoundingPolygon(osmNode.getId());    
    if(!settings.hasBoundingPolygon() ||
        keepOutsideBoundingPolygon ||
        PlanitOsmNodeUtils.createPoint(osmNode).within(settings.getBoundingPolygon())) {
      
      /* store for later processing */
      networkData.addOsmNode(osmNode);
      
      if(!keepOutsideBoundingPolygon) {
        /* track bounding box of osm nodes within bounding polygon (if any) */
        networkData.updateBoundingBox(osmNode);
      }
    }
  }

  /**
   * parse an osm way to extract link and link segments (including type). If insufficient information
   * is available the handler will try to infer the missing data by using defaults set by the user
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
            
    if(!settings.isOsmWayExcluded(osmWay.getId())) {
      
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
      try {                      
        
        /* only parse ways that are potentially road infrastructure */
        if(isActivatedRoadOrRailwayBasedInfrastructure(tags)) {
          
          /* circular ways special case filter */
          if(PlanitOsmWayUtils.isCircularOsmWay(osmWay, tags, false)) {          
            
            /* postpone creation of link(s) for activated OSM highways that have a circular component and are not areas (areas cannot become roads) */
            /* Note: in OSM roundabouts are a circular way, in PLANit, they comprise several one-way link connecting exists and entries to the roundabout */
            networkData.addOsmCircularWay(osmWay);
            
          }else{
            
            /* extract regular OSM way; convert to PLANit infrastructure */          
            extractOsmWay(osmWay, tags);                    
                        
          }
        }
        
      } catch (PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
      }      
    }
            
  }


  /** extract the correct link segment type based on the configuration of supported modes, the defaults for the given osm way and any 
   * modifications to the mode access based on the passed in tags of the OSM way
   * 
   * @param osmWay the way this type extraction is executed for 
   * @param tags tags belonging to the OSM way
   * @return appropriate link segment types for forward and backward direction per network layer. If no modes are allowed in a direction, the link segment type will be null
   * @throws PlanItException thrown if error
   */
  protected Map<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> extractLinkSegmentTypes(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    Map<MacroscopicPhysicalNetwork, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>> linkSegmentTypesByLayerByDirection = new HashMap<>(); 
    /* a default link segment type should be available as starting point*/
    Map<InfrastructureLayer, MacroscopicLinkSegmentType> linkSegmentTypesByLayer = getDefaultLinkSegmentTypeByOsmWayType(osmWay, tags);
    if(linkSegmentTypesByLayer != null) {      
      
      /* per layer identify the directional link segment types based on additional access changes from osm tags */      
      for(Entry<InfrastructureLayer, MacroscopicLinkSegmentType> entry : linkSegmentTypesByLayer.entrySet()) {
        MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) entry.getKey();
        MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
        
        /* collect possibly modified type (per direction) */
        Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> typesPerdirectionPair = networkData.getLayerParser(networkLayer).updatedLinkSegmentTypeBasedOnOsmWay(osmWay, tags, linkSegmentType);                
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
    for(Entry<MacroscopicPhysicalNetwork, PlanitOsmNetworkLayerParser> entry : networkData.getLayerParsers().entrySet()) {
      PlanitOsmNetworkLayerParser networkLayerHandler = entry.getValue();
      
      /* break links on layer with internal connections to multiple osm ways */
      networkLayerHandler.complete();      
    }                 
        
    LOGGER.info(" OSM basic network parsing...DONE");

  }
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    networkData.reset();    
  }  
  
}

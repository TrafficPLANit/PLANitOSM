package org.goplanit.osm.converter.network;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.OsmBoundaryTags;
import org.goplanit.osm.util.OsmTagUtils;

/**
 * Preprocessing Handler that identifies which nodes of osm ways  - that are marked for inclusion even if they fall (partially) outside the bounding polygon -
 * are to be kept. Since we only know what nodes these are after parsing OSM ways (and nodes are parsed before the ways), this pre-processing is the only way
 * that we can identify these nodes before the main parsing pass.
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
    
    /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that is deemed eligible (road, rail) */
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      getNetworkData().getOsmNodeData().preRegisterEligibleOsmNode(osmWay.getNodeId(index));
    }
  }
    

  /**
   * Constructor
   *
   * @param networkToPopulate the network to populate
   * @param networkData to populate
   * @param settings for the handler
   */
  public OsmNetworkPreProcessingHandler(
          final PlanitOsmNetwork networkToPopulate, final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings settings) {
    super(networkToPopulate, networkData, settings);
    this.nodeCounter = new LongAdder();
  }

  /**
   * Count total number of nodes in OSM file
   */
  @Override
  public void handle(OsmNode node) {
    nodeCounter.increment();
  }


  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon we
   * extract their nodes and mark them for inclusion as exceptions to the bounding polygon filter that is
   * applied during the main parsing pass in the regular PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) {
    
    wrapHandleOsmWay(osmWay, this::handleEligibleOsmWay);
  }

  /**
   * PRe-process OSM relations solely for the purpose in case a bounding boundary has been specified by name in which case
   * we extract it and convert it into a bounding polygon to use. If it is not found then we log a severe indicating the issue
   * and proceed without a bounding polygon/restriction in what we parse
   */
  @Override
  public void handle(OsmRelation osmRelation) {

    /* only keep going when boundary is active */
    if(!getSettings().hasBoundingBoundary()){
      return;
    }

    /* check for boundary tags on relation */
    var tags = OsmModelUtil.getTagsAsMap(osmRelation);
    if(!tags.containsKey(OsmBoundaryTags.getBoundaryKeyTag())){
      return;
    }

    if(!OsmTagUtils.matchesAnyValueTag(
            tags.get(OsmBoundaryTags.getBoundaryKeyTag()), OsmBoundaryTags.getBoundaryValues())){
      return;
    }

    /* boundary compatible relation - now check against settings  */
    //TODO CONTINUE HERE --> populate initialiseBoundingArea in basenetwork reader to have polygon
    // then compare against this relation...
  }
  
  /** Log total number of parsed nodes and percentage retained
   */
  @Override
  public void complete() throws IOException {
    super.complete();
    int totalOsmNodes = (int) nodeCounter.sum();
    int preRegisteredOsmNodes = getNetworkData().getOsmNodeData().getRegisteredOsmNodes().size();
    LOGGER.info(String.format("Total OSM nodes in source: %d",totalOsmNodes));
    LOGGER.info(String.format("Total OSM nodes identified as part of network: %d (%.2f%%)",preRegisteredOsmNodes, preRegisteredOsmNodes/(double)totalOsmNodes));
  }


  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    super.reset();
    /* data and settings are to be kept for main parsing loop */
  }  
  
}

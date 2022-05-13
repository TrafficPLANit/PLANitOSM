package org.goplanit.osm.converter.network;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

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
   * @param osmWay
   * @param tags
   */
  protected void handleEligibleOsmWay(OsmWay osmWay, Map<String,String> tags) {
    var settings = getSettings();
     
    if(settings.hasBoundingPolygon() && settings.isKeepOsmWayOutsideBoundingPolygon(osmWay.getId())) {
      
      if(settings.isOsmWayExcluded(osmWay.getId())) {
        LOGGER.warning("OSM way %d is marked for exclusion as well as keeping it, this is conflicting, OSM way exclusion takes precedence");
        return;
      }

      /* mark all nodes for keeping, since we determine availability based on the tracked OSM nodes */
      for(int index=0;index<osmWay.getNumberOfNodes();++index) {
        settings.setKeepOsmNodeOutsideBoundingPolygon(osmWay.getNodeId(index));
      }
    }
    
    /* mark all nodes as potentially eligible for keeping, since they reside on an OSM way that is deemed eligible (road, rail) */
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      getNetworkData().preRegisterEligibleOsmNode(osmWay.getNodeId(index));
    }
  }
    

  /**
   * Constructor
   * 
   * @param networkData to populate
   * @param settings for the handler
   */
  public OsmNetworkPreProcessingHandler(final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings settings) { 
    super(networkData, settings);           
    this.nodeCounter = new LongAdder();
  }  
  
  /**
   * Count total number of nodes in OSM file
   */
  @Override
  public void handle(OsmNode node) throws IOException {
    nodeCounter.increment();
  }


  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon we extract their nodes and mark
   * them for inclusion as exceptions to the bounding polygon filter that is applied during the main parsing pass in the regular
   * PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
    
    wrapHandleOsmWay(osmWay, this::handleEligibleOsmWay);
                        
  }
  
  /** Log total number of parsed nodes and percentage retained
   */
  @Override
  public void complete() throws IOException {
    super.complete();
    int totalOsmNodes = (int) nodeCounter.sum();
    int preRegisteredOsmNodes = getNetworkData().getRegisteredOsmNodes().size();
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

package org.goplanit.osm.converter.network;

import java.io.IOException;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
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
public class OsmNetworkPreProcessingHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkPreProcessingHandler.class.getCanonicalName());
  
  /** the settings to adhere to */
  private final OsmNetworkReaderSettings settings;         
    

  /**
   * constructor
   * 
   * @param settings for the handler
   */
  public OsmNetworkPreProcessingHandler(final OsmNetworkReaderSettings settings) { 
    this.settings = settings;       
  }
  
  /**
   * for all OSM ways that are explicitly marked for inclusion despite falling outside the bounding polygon we extract their nodes and mark
   * them for inclusion as exceptions to the bounding polygon filter that is applied during the main parsing pass in the regular
   * PlanitOsmNetworkHandler
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
    
    if(settings.hasBoundingPolygon() &&
        settings.isKeepOsmWayOutsideBoundingPolygon(osmWay.getId())) {
      
      if(settings.isOsmWayExcluded(osmWay.getId())) {
        LOGGER.warning("OSM way %d is marked for exclusion as well as keeping it, this is conficting, OSM way exclusion takes precedence");
        return;
      }
      
      /* mark all nodes for keeping, since we determine availability based on the tracked OSM nodes */
      for(int index=0;index<osmWay.getNumberOfNodes();++index) {
        settings.setKeepOsmNodeOutsideBoundingPolygon(osmWay.getNodeId(index));
      }
    }
                
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {
                
    if(settings.hasBoundingPolygon() && (settings.hasKeepOsmWaysOutsideBoundingPolygon() || settings.hasKeepOsmNodesOutsideBoundingPolygon())) {
      LOGGER.info(String.format("Identified %d OSM ways and %d nodes to keep even if (partially) outside bounding polygon", 
          settings.getNumberOfKeepOsmWaysOutsideBoundingPolygon(), settings.getNumberOfKeepOsmNodesOutsideBoundingPolygon()));
      LOGGER.info("OSM preprocessing network ...DONE");
    }

  }
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    /* data and settings are to be kept for main parsing loop */
  }  
  
}

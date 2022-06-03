package org.goplanit.osm.converter.network;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import org.goplanit.osm.tags.*;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Base handler for networks with common functionality. Requires derived hanlder for concrete implementation.
 * 
 * @author markr
 * 
 *
 */
public abstract class OsmNetworkBaseHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkBaseHandler.class.getCanonicalName());
  
  /** the network data tracking all relevant data during parsing of the osm network */
  private final OsmNetworkReaderData networkData;  
  
  /** the settings to adhere to */
  private final OsmNetworkReaderSettings settings;  
  
  /**
   * Constructor
   * 
   * @param settings for the handler
   */
  protected OsmNetworkBaseHandler(final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings settings) {
    this.settings = settings;       
    this.networkData = networkData;
  }


  /** verify if tags represent an highway or railway that is specifically aimed at road based or rail based infrastructure, e.g.,
   * asphalt or tracks and NOT an area, platform, stops, etc. and is also activated for parsing based on the settings
   * 
   * @param tags to verify
   * @return true when activated and highway or railway (not an area), false otherwise
   */
  protected boolean isActivatedRoadOrRailwayBasedInfrastructure(Map<String, String> tags) {
    
    if(!OsmTags.isArea(tags)) {
      if(settings.isHighwayParserActive() && OsmHighwayTags.hasHighwayKeyTag(tags)) {
        return settings.getHighwaySettings().isOsmHighwayTypeActivated(tags.get(OsmHighwayTags.HIGHWAY));
      }else if(settings.isRailwayParserActive() && OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return settings.getRailwaySettings().isOsmRailwayTypeActivated(tags.get(OsmRailwayTags.RAILWAY));
      }
    }
    return false;
  }


  /** Wrap the handling of OSM way by checking if it is eligible and catch any run time PLANit exceptions, if eligible delegate to consumer.
   * 
   * @param osmWay to parse
   * @param osmWayConsumer to apply to eligible OSM way
   */
  protected void wrapHandleOsmWay(OsmWay osmWay, BiConsumer<OsmWay, Map<String, String>> osmWayConsumer) {
        
    if(!settings.isOsmWayExcluded(osmWay.getId())) {
      
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
      try {                      
        
        /* only parse ways that are potentially road infrastructure */
        if(isActivatedRoadOrRailwayBasedInfrastructure(tags)) {          
          osmWayConsumer.accept(osmWay, tags);
        }
        
      } catch (PlanItRunTimeException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
      }      
    }
  }
 

  protected OsmNetworkReaderSettings getSettings() {
    return settings;
  }
  
  protected OsmNetworkReaderData getNetworkData() {
    return networkData;
  }  


  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    // nothing yet
  }  
  
}
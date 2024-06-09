package org.goplanit.osm.converter.network;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
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

  /** the network to populate */
  private final PlanitOsmNetwork networkToPopulate;
  
  /** the network data tracking all relevant data during parsing of the osm network */
  private final OsmNetworkReaderData networkData;  
  
  /** the settings to adhere to */
  private final OsmNetworkReaderSettings settings;

  /**
   * Constructor
   *
   * @param networkToPopulate to populate
   * @param networkData to use
   * @param settings for the handler
   */
  protected OsmNetworkBaseHandler(final PlanitOsmNetwork networkToPopulate, final OsmNetworkReaderData networkData, final OsmNetworkReaderSettings settings) {
    this.networkToPopulate = networkToPopulate;
    this.settings = settings;
    this.networkData = networkData;
  }

  /** verify if tags represent an highway or railway that is specifically aimed at road based or rail based infrastructure, e.g.,
   * asphalt or tracks and NOT an area, platform, stops, etc. and is also activated for parsing based on the settings
   * 
   * @param tags to verify
   * @return true when activated and highway or railway (not an area), false otherwise
   */
  protected boolean isActivatedRoadRailOrWaterwayBasedInfrastructure(Map<String, String> tags) {
    
    if(!OsmTags.isArea(tags)) {
      if(settings.isHighwayParserActive() && OsmHighwayTags.hasHighwayKeyTag(tags)) {
        return settings.getHighwaySettings().isOsmHighwayTypeActivated(tags.get(OsmHighwayTags.getHighwayKeyTag()));
      }else if(settings.isRailwayParserActive() && OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return settings.getRailwaySettings().isOsmRailwayTypeActivated(tags.get(OsmRailwayTags.getRailwayKeyTag()));
      }else if(settings.isWaterwayParserActive() && OsmWaterwayTags.isWaterBasedWay(tags)) {
        return settings.getWaterwaySettings().isOsmWaterwayTypeActivated(tags.get(OsmWaterwayTags.getUsedKeyTag(tags)));
      }
    }
    return false;
  }


  /** Wrap the handling of OSM way by checking if it is eligible and catch any run time PLANit exceptions, if eligible delegate to consumer.
   * 
   * @param osmWay to parse
   * @param osmWayConsumer to apply to eligible OSM way
   */
  protected void wrapHandleInfrastructureOsmWay(OsmWay osmWay, BiConsumer<OsmWay, Map<String, String>> osmWayConsumer) {
        
    if(!settings.isOsmWayExcluded(osmWay.getId())) {
      
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
      try {                      
        
        /* only parse ways that are potentially road/rail/ferry infrastructure */
        if(isActivatedRoadRailOrWaterwayBasedInfrastructure(tags)) {
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

  protected PlanitOsmNetwork getNetwork(){
    return this.networkToPopulate;
  }


  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    // nothing yet
  }  
  
}

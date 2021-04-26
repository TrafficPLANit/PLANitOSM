package org.planit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;

/**
 * Track statistics on Osm network handler
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkHandlerProfiler {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkHandlerProfiler.class.getCanonicalName());  
  
  /**
   * track a counter by highway tag of the encountered entities
   */
  private final Map<String, LongAdder> counterBywayTag = new HashMap<String, LongAdder>();
  
  /** track how many osmways have no explicit speed limit defined */
  private LongAdder missingSpeedLimitCounter = new LongAdder();
  
  /** track how many osmways have no lane defined */
  private LongAdder missingLaneCounter = new LongAdder();  
    
  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */
  private long moduloLoggingCounterLinks = 500;

  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingCounterLinkSegments = 500;
  
  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingCounterNodes = 500;

  /**
   * Increment counter for passed in osm tag 
   * 
   * @param tagType to increment counter for
   */
  public void incrementOsmTagCounter(String tagType) {
    counterBywayTag.putIfAbsent(tagType, new LongAdder());
    counterBywayTag.get(tagType).increment();    
  }

  /**
   * log counters
   * 
   * @param networkLayer for which information  was tracked
   */
  public void logProfileInformation(MacroscopicPhysicalNetwork networkLayer) {
    for(Entry<String, LongAdder> entry : counterBywayTag.entrySet()) {
      long count = entry.getValue().longValue();
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(entry.getKey())) {
        LOGGER.info(String.format("%s [STATS] processed highway:%s count:%d", InfrastructureLayer.createLayerLogPrefix(networkLayer), entry.getKey(), count));
      }else if(OsmRailwayTags.isRailBasedRailway(entry.getKey())) {
        LOGGER.info(String.format("%s [STATS] processed railway:%s count:%d", InfrastructureLayer.createLayerLogPrefix(networkLayer), entry.getKey(), count));
      }
    }
    
    /* stats on exact number of created PLANit network objects */
    LOGGER.info(String.format("%s [STATS] created PLANit %d nodes",InfrastructureLayer.createLayerLogPrefix(networkLayer), networkLayer.nodes.size()));
    LOGGER.info(String.format("%s [STATS] created PLANit %d links",InfrastructureLayer.createLayerLogPrefix(networkLayer), networkLayer.links.size()));
    LOGGER.info(String.format("%s [STATS] created PLANit %d links segments ",InfrastructureLayer.createLayerLogPrefix(networkLayer), networkLayer.linkSegments.size()));    
    
    double numberOfParsedLinks = (double)networkLayer.links.size();
    double percentageDefaultspeedLimits = 100*(missingSpeedLimitCounter.longValue()/numberOfParsedLinks);
    double percentageDefaultLanes = 100*(missingLaneCounter.longValue()/numberOfParsedLinks);
    LOGGER.info(String.format("%s [STATS] applied default speed limits to %.1f%% of link(segments) -  %.1f%% explicitly set", InfrastructureLayer.createLayerLogPrefix(networkLayer), percentageDefaultspeedLimits, 100-percentageDefaultspeedLimits));
    LOGGER.info(String.format("%s [STATS] applied default lane numbers to %.1f%% of link(segments) -  %.1f%% explicitly set", InfrastructureLayer.createLayerLogPrefix(networkLayer), percentageDefaultLanes, 100-percentageDefaultLanes));
  }

  /**
   * log user information based on currently number of registered link segments
   * 
   * @param numberOfLinkSegments registered link segments so far
   */
  public void logLinkSegmentStatus(long numberOfLinkSegments) {
    if(numberOfLinkSegments == moduloLoggingCounterLinkSegments) {
      LOGGER.info(String.format("Created %d linksegments out of OSM ways",numberOfLinkSegments));
      moduloLoggingCounterLinkSegments *=2;    
    }     
  }

  /**
   * log user information based on currently number of registered links 
   * 
   * @param numberOfLinks registered links so far
   */
  public void logLinkStatus(int numberOfLinks) {
    if(numberOfLinks == moduloLoggingCounterLinks) {
      LOGGER.info(String.format("Created %d links out of OSM ways",numberOfLinks));
      moduloLoggingCounterLinks *=2;
    }  
  }

  /**
   * log user information based on currently number of registered nodes
   * 
   * @param numberOfNodes registered number of nodes so far
   */
  public void logNodeStatus(int numberOfNodes) {
    if(numberOfNodes >= moduloLoggingCounterNodes) {
      LOGGER.info(String.format("Created %d nodes out of OSM nodes",numberOfNodes));
      moduloLoggingCounterNodes *=2;
    }  
  }

  /**
   * increment counter for missing speed limit information on parsed osmways/links
   */
  public void incrementMissingSpeedLimitCounter() {
    missingSpeedLimitCounter.increment();    
  }

  /**
   * increment counter for missing lane information on parsed osmways/links
   */
  public void incrementMissingLaneCounter() {
    missingLaneCounter.increment();    
  }

}

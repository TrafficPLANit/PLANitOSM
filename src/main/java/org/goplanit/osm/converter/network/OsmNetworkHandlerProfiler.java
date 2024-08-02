package org.goplanit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;

/**
 * Track statistics on OSM network handler
 * 
 * @author markr
 *
 */
public class OsmNetworkHandlerProfiler {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkHandlerProfiler.class.getCanonicalName());  
  
  /**
   * track a counter by way key value tags, e.g. highway=primary tag of the encountered entities
   */
  private final SortedMap<String, SortedMap<String, LongAdder>> counterByWayKeyValueTag = new TreeMap<>();
  
  /** track how many OSM ways have no explicit speed limit defined */
  private LongAdder missingSpeedLimitCounter = new LongAdder();
  
  /** track how many OSM ways have no lane defined */
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
   * @param keyTag to increment counter for
   * @param valueTag to increment counter for
   */
  public void incrementOsmTagCounter(String keyTag, String valueTag) {
    counterByWayKeyValueTag.putIfAbsent(keyTag, new TreeMap<>());
    var keyEntry = counterByWayKeyValueTag.get(keyTag);
    keyEntry.putIfAbsent(valueTag, new LongAdder());
    keyEntry.get(valueTag).increment();
  }

  /**
   * log counters
   *
   * @param networkLayer for which information  was tracked
   */
  public void logOsmProfileInformation(MacroscopicNetworkLayer networkLayer) {
    long totalCount = 0;
    for(var outerEntry : counterByWayKeyValueTag.entrySet()) {
      for(var innerEntry : outerEntry.getValue().entrySet()) {
      long count = innerEntry.getValue().longValue();
      totalCount += count;
        LOGGER.info(String.format("%s [STATS] processed %-7s=%-20s count:%-4d",
            NetworkLayer.createLayerLogPrefix(networkLayer), outerEntry.getKey(), innerEntry.getKey(), count));
      }
    }

    double percentageDefaultspeedLimits = 100*((double) missingSpeedLimitCounter.longValue() /totalCount);
    double percentageDefaultLanes = 100*((double) missingLaneCounter.longValue() /totalCount);
    LOGGER.info(String.format("%s [STATS] Applied default speed limits to %.1f%% of link(segments) -  %.1f%% explicitly set", NetworkLayer.createLayerLogPrefix(networkLayer), percentageDefaultspeedLimits, 100-percentageDefaultspeedLimits));
    LOGGER.info(String.format("%s [STATS] Applied default lane numbers to %.1f%% of link(segments) -  %.1f%% explicitly set", NetworkLayer.createLayerLogPrefix(networkLayer), percentageDefaultLanes, 100-percentageDefaultLanes));
  }

  /**
   * log counters
   * 
   * @param networkLayer for which information  was tracked
   */
  public void logPlanitStats(MacroscopicNetworkLayer networkLayer) {
    /* stats on exact number of created PLANit network objects */
    LOGGER.info(String.format("%s [STATS] created %d PLANit nodes",NetworkLayer.createLayerLogPrefix(networkLayer), networkLayer.getNumberOfNodes()));
    LOGGER.info(String.format("%s [STATS] created %d PLANit links",NetworkLayer.createLayerLogPrefix(networkLayer), networkLayer.getNumberOfLinks()));
    LOGGER.info(String.format("%s [STATS] created %d PLANit links segments ",NetworkLayer.createLayerLogPrefix(networkLayer), networkLayer.getNumberOfLinkSegments()));
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
  public void logLinkStatus(long numberOfLinks) {
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
  public void logNodeStatus(long numberOfNodes) {
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

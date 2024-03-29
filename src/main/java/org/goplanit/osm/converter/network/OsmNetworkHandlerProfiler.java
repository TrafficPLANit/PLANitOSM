package org.goplanit.osm.converter.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
  public void logOsmProfileInformation(MacroscopicNetworkLayer networkLayer) {
    long totalCount = 0;
    for(Entry<String, LongAdder> entry : counterBywayTag.entrySet()) {
      long count = entry.getValue().longValue();
      totalCount += count;
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(entry.getKey())) {
        LOGGER.info(String.format("%s [STATS] processed highway:%s count:%d",
            NetworkLayer.createLayerLogPrefix(networkLayer), entry.getKey(), count));
      }else if(OsmRailwayTags.isRailBasedRailway(entry.getKey())) {
        LOGGER.info(String.format("%s [STATS] processed railway:%s count:%d",
            NetworkLayer.createLayerLogPrefix(networkLayer), entry.getKey(), count));
      }else if(OsmWaterwayTags.isAnyWaterwayKeyTag(entry.getKey())) {
        LOGGER.info(String.format("%s [STATS] processed water way %s=%s count:%d",
            NetworkLayer.createLayerLogPrefix(networkLayer), OsmWaterwayTags.getKeyForValueType(entry.getKey()), entry.getKey(), count));
      }
    }

    double percentageDefaultspeedLimits = 100*(missingSpeedLimitCounter.longValue()/totalCount);
    double percentageDefaultLanes = 100*(missingLaneCounter.longValue()/totalCount);
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

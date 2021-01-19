package org.planit.osm.physical.network.macroscopic;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Track statistics on Osm handler
 * 
 * @author markr
 *
 */
public class PlanitOsmHandlerProfiler {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmHandlerProfiler.class.getCanonicalName());  
  
  /**
   * track a counter by highway tag of the encountered entities
   */
  private final Map<String, LongAdder> counterByHighwayTag = new HashMap<String, LongAdder>();
  
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
    counterByHighwayTag.putIfAbsent(tagType, new LongAdder());
    counterByHighwayTag.get(tagType).increment();    
  }

  /**
   * log counters
   * 
   * @param network for which information  was tracked
   */
  public void logProfileInformation(PlanitOsmNetwork network) {
    for(Entry<String, LongAdder> entry : counterByHighwayTag.entrySet()) {
      long count = entry.getValue().longValue();
      LOGGER.info(String.format(" [STATS] processed highway:%s count:%d", entry.getKey(), count));
    }
    
    /* stats on exact number of created PLANit network objects */
    LOGGER.info(String.format(" [STATS] created PLANit %d nodes",network.getDefaultNetworkLayer().nodes.size()));
    LOGGER.info(String.format(" [STATS] created PLANit %d links",network.getDefaultNetworkLayer().links.size()));
    LOGGER.info(String.format(" [STATS] created PLANit %d links segments ",network.getDefaultNetworkLayer().linkSegments.size()));    
    
    double numberOfParsedLinks = (double)network.getDefaultNetworkLayer().links.size();
    double percentageDefaultspeedLimits = 100*(missingSpeedLimitCounter.longValue()/numberOfParsedLinks);
    double percentageDefaultLanes = 100*(missingLaneCounter.longValue()/numberOfParsedLinks);
    LOGGER.info(String.format(" [STATS] applied default speed limits to %.1f%% of link(segments) -  %.1f%% explicitly set", percentageDefaultspeedLimits, 100-percentageDefaultspeedLimits));
    LOGGER.info(String.format(" [STATS] applied default lane numbers to %.1f%% of link(segments) -  %.1f%% explicitly set", percentageDefaultLanes, 100-percentageDefaultLanes));
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

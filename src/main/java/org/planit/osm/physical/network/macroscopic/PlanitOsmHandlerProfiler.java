package org.planit.osm.physical.network.macroscopic;

import java.util.HashMap;
import java.util.Map;
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

  public void logTagCounters() {
    counterByHighwayTag.forEach( 
        (type,counter) -> LOGGER.info(String.format(" [PROCESSED] highway:%s count:%d", type, counter.longValue())));
  }

  /**
   * log user information based on currently number of registered link segments
   * 
   * @param numberOfLinkSegments registered link segments so far
   */
  public void logLinkSegmentStatus(int numberOfLinkSegments) {
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

}

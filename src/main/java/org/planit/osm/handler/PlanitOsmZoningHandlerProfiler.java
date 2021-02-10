package org.planit.osm.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import org.planit.zoning.Zoning;

/**
 * Track statistics on Osm zoning handler
 * 
 * @author markr
 *
 */
public class PlanitOsmZoningHandlerProfiler {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningHandlerProfiler.class.getCanonicalName());  
  
  /**
   * track a counter by Ptv1 value tag of the encountered entities
   */
  private final Map<String, LongAdder> counterByPtv1Tag = new HashMap<String, LongAdder>();
        
  /**
   * for logging we log each x number of entities parsed, this is done to minimise number of logging lines
   * while still providing information, hence the modulo use is dynamic
   */
  private long moduloLoggingTransferZones = 500;

  /**
   * for logging we log each x number of entities parsed, this is done to minimise number of logging
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingConnectoids = 500;
  
  /**
   * for logging we log each x number of entities parsed, this is done smartly to minimise number of lines
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingCounterNodes = 10;  
  

  /**
   * Increment counter for passed in osm tag regarding a Ptv1 value tag
   * 
   * @param tagType to increment counter for
   */
  public void incrementOsmPtv1TagCounter(String tagType) {
    counterByPtv1Tag.putIfAbsent(tagType, new LongAdder());
    counterByPtv1Tag.get(tagType).increment();    
  }

  /**
   * log counters
   * 
   * @param zoning for which information  was tracked
   */
  public void logProfileInformation(Zoning zoning) {
    for(Entry<String, LongAdder> entry : counterByPtv1Tag.entrySet()) {
      long count = entry.getValue().longValue();
      LOGGER.info(String.format("[STATS] processed %s count:%d", entry.getKey(), count));
    }
    
    /* stats on exact number of created PLANit network objects */
    LOGGER.info(String.format("[STATS] created PLANit %d transfer zones", zoning.transferZones.size()));
    LOGGER.info(String.format("[STATS] created PLANit %d connectoids",zoning.connectoids.size()));
  }

  /**
   * log user information based on currently number of registered transfer zones
   * 
   * @param numberOfTransferZones registered transfer zones so far
   */
  public void logTransferZoneStatus(long numberOfTransferZones) {
    if(numberOfTransferZones >= moduloLoggingTransferZones) {
      LOGGER.info(String.format("Created %d transfer zones out of OSM nodes/ways",moduloLoggingTransferZones));
      moduloLoggingTransferZones *=2;    
    }     
  }

  /**
   * log user information based on currently number of registered connectoids 
   * 
   * @param numberOfConnectoids registered connectoids so far
   */
  public void logConnectoidStatus(int numberOfConnectoids) {
    if(numberOfConnectoids >= moduloLoggingConnectoids) {
      LOGGER.info(String.format("Created %d connectoids out of OSM nodes/ways",moduloLoggingConnectoids));
      moduloLoggingConnectoids *=2;
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

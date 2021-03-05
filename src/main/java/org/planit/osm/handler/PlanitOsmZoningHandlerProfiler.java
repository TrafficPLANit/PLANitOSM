package org.planit.osm.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.util.OsmPtVersionScheme;
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
   * track a counter by Ptv2 value tag of the encountered entities
   */
  private final Map<String, LongAdder> counterByPtv2Tag = new HashMap<String, LongAdder>();  
  
  /**
   * track number of multipolygons eligible as PT platforms that we encountered
   */
  private final LongAdder multiPolygonCount = new LongAdder();   
        
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
   * offset for existing number of nodes in network, such that we can accurately profile the additional
   * number of nodes created by the zoning reader
   */
  private long nodesInNetworkOffset;
  
  /**
   * @param nodesInNetworkOffset
   */
  public PlanitOsmZoningHandlerProfiler(long nodesInNetworkOffset) {
    this.nodesInNetworkOffset = nodesInNetworkOffset;
  }
  
  /**
   * increment the counter that tracks the number of multi polygons identified as PT platforms
   */
  public void incrementMultiPolygonPlatformCounter() {
    this.multiPolygonCount.increment();
  }

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
   * Increment counter for passed in osm tag regarding a Ptv2 value tag
   * 
   * @param tagType to increment counter for
   */
  public void incrementOsmPtv2TagCounter(String tagType) {
    counterByPtv2Tag.putIfAbsent(tagType, new LongAdder());
    counterByPtv2Tag.get(tagType).increment();    
  }  
  
  /**
   * Increment counter for passed in osm tag regarding Ptv value tag
   * @param version of pt tag
   * @param tagType tagvalue
   */
  public void incrementOsmTagCounter(OsmPtVersionScheme version, String tagType) {
    if(version == OsmPtVersionScheme.VERSION_1) {
      incrementOsmPtv1TagCounter(tagType);
    }else if(version == OsmPtVersionScheme.VERSION_2) {
      incrementOsmPtv2TagCounter(tagType);
    }else {
      LOGGER.severe("Unknown pt version");
    }    
  }  
  
  /**
   * log stats related to the zoning pre-processing phase
   * 
   * @param planitOsmZoningReaderData to extract stats from
   */
  public void logPreProcessingStats(PlanitOsmZoningReaderData planitOsmZoningReaderData) {
    
    LOGGER.info(String.format("[STATS] identified %d multipolygons as PT platforms",multiPolygonCount.longValue()));
    
    LOGGER.info(String.format("[STATS] marked %d osm ways as part of multipolygon relations",planitOsmZoningReaderData.getUnprocessedMultiPolygonOsmWays().size()));    
    
  }  

  /**
   * log counters regarding main processing phase
   * 
   * @param zoning for which information  was tracked
   */
  public void logProcessingStats(Zoning zoning) {  
    
    /* stats on exact number of created PLANit network objects */
    LOGGER.info(String.format("[STATS] created PLANit %d transfer zones", zoning.transferZones.size()));
  }
  
  /**
   * log stats regarding post-processing steo
   * 
   * @param zoning for which information was tracked
   */
  public void logPostProcessingStats(Zoning zoning) {
    
    for(Entry<String, LongAdder> entry : counterByPtv1Tag.entrySet()) {
      long count = entry.getValue().longValue();
      LOGGER.info(String.format("[STATS] [Ptv1] processed %s count:%d", entry.getKey(), count));
    }
    
    for(Entry<String, LongAdder> entry : counterByPtv2Tag.entrySet()) {
      long count = entry.getValue().longValue();
      LOGGER.info(String.format("[STATS] [Ptv2] processed %s count:%d", entry.getKey(), count));
    }     
    
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
   * reset the profiler
   */
  public void reset() {
    this.counterByPtv1Tag.clear();
    this.counterByPtv2Tag.clear();
    this.multiPolygonCount.reset();
  }


 

}

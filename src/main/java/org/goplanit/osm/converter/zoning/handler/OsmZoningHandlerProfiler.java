package org.goplanit.osm.converter.zoning.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.util.OsmPtVersionScheme;
import org.goplanit.zoning.Zoning;

/**
 * Track statistics on Osm zoning handler
 * 
 * @author markr
 *
 */
public class OsmZoningHandlerProfiler {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningHandlerProfiler.class.getCanonicalName());  
  
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
   * track number of platforms tagged as relations eligible as PT platforms that we encountered
   */
  private final LongAdder platformRelationCount = new LongAdder();  
        
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
   * for logging we log each x number of entities parsed, this is done to minimise number of logging
   * while still providing information, hence the modulo use is dynamic
   */  
  private long moduloLoggingTransferZoneGroups = 500;  
       
  /**
   * Default constructor
   */
  public OsmZoningHandlerProfiler() {
  }
  
  /**
   * increment the counter that tracks the number of multi polygons identified as PT platforms
   */
  public void incrementMultiPolygonPlatformCounter() {
    this.multiPolygonCount.increment();
  }
  
  /**
   * increment the counter that tracks the number of platform relations identified as PT platforms
   */  
  public void incrementPlatformRelationCounter() {
    this.platformRelationCount.increment();
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
   * log counters regarding main processing phase
   * 
   * @param planitOsmZoningReaderData to extract stats from
   * @param zoning for which information  was tracked
   */
  public void logProcessingStats(OsmZoningReaderData planitOsmZoningReaderData, Zoning zoning) {
    
    /* pre-processing */
    {
      if(multiPolygonCount.longValue()>0) {
        LOGGER.info(String.format("[STATS] identified %d multipolygons as PT platforms",multiPolygonCount.longValue()));
      }
      
      if(platformRelationCount.longValue()>0) {
        LOGGER.info(String.format("[STATS] identified %d platforms tagged as relations ",platformRelationCount.longValue()));
      }
      
      if(!planitOsmZoningReaderData.getOsmData().hasOsmRelationOuterRoleOsmWays()) {
        LOGGER.info(String.format("[STATS] marked %d osm ways that are outer roles of osm relations and eligible to be converted to platforms",planitOsmZoningReaderData.getOsmData().getNumberOfOuterRoleOsmWays()));
      }  
    }   
    
    /* main processing */
    {
      for(Entry<String, LongAdder> entry : counterByPtv1Tag.entrySet()) {
        long count = entry.getValue().longValue();
        LOGGER.info(String.format("[STATS] [Ptv1] processed %s count:%d", entry.getKey(), count));
      }
      
      for(Entry<String, LongAdder> entry : counterByPtv2Tag.entrySet()) {
        long count = entry.getValue().longValue();
        LOGGER.info(String.format("[STATS] [Ptv2] processed %s count:%d", entry.getKey(), count));
      }
    }

    /* post-processing */
    {
      LOGGER.info(String.format("[STATS] created PLANit %d transfer zone groups",zoning.getTransferZoneGroups().size()));
      LOGGER.info(String.format("[STATS] created PLANit %d transfer zones", zoning.getTransferZoneGroups().size()));    
      LOGGER.info(String.format("[STATS] created PLANit %d transfer connectoids",zoning.getTransferConnectoids().size())); 
    }
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
   * log user information based on currently number of registered transfer zone groups 
   * 
   * @param numberOfTransferZoneGroups registered transfer zone groups so far
   */  
  public void logTransferZoneGroupStatus(int numberOfTransferZoneGroups) {
    if(numberOfTransferZoneGroups >= moduloLoggingTransferZoneGroups) {
      LOGGER.info(String.format("Created %d transfer zone groups out of OSM stop_areas",moduloLoggingTransferZoneGroups));
      moduloLoggingTransferZoneGroups *=2;
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

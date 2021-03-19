package org.planit.osm.converter.reader;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.planit.osm.util.Osm4JUtils;
import org.planit.osm.util.OsmPtVersionScheme;
import org.planit.utils.misc.Pair;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Data specifically required in the zoning reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class PlanitOsmZoningReaderOsmData {
  
  /** logeger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningReaderOsmData.class.getCanonicalName());   
  
  /* UNPROCESSED OSM */

  /** track unprocessed but identified Ptv1 station nodes */
  private final Map<EntityType, Map<Long, OsmEntity>> unprocessedPtv1Stations = new TreeMap<EntityType, Map<Long, OsmEntity>>();
  
  /** track unprocessed but identified Ptv2 station nodes/ways */
  private final Map<EntityType, Map<Long, OsmEntity>> unprocessedPtv2Stations = new TreeMap<EntityType, Map<Long, OsmEntity>>();
      
  /** track unprocessed but identified Ptv2 stop positions by their osm node id */
  private final Set<Long> unprocessedPtv2StopPositions= new TreeSet<Long>();
  
  /** the registered osm ways that we kept based on osmWaysToKeep that were provided, and are processed at a later stage */
  private final Map<Long, OsmWay> unprocessedMultiPolygonOsmWays = new TreeMap<Long, OsmWay>();
  
  /* INVALID OSM */
  
  /** Stop positions found to be invalid and to be excluded from post-processing when converting stop positions to connectoids */
  private final Map<EntityType, Set<Long>> invalidStopAreaStopPositions = new TreeMap<EntityType, Set<Long>>();
      
    
  /* UNPROCESSED RELATED METHODS */  

  /** Collect an unprocessed station if it exists
   * 
   * @param entityType to collect for
   * @param osmId id to collect for
   * @return pair of version and unprocessed station, null otherwise
   */
  public Pair<OsmPtVersionScheme,OsmEntity> getUnprocessedStation(EntityType entityType, long osmId) {    
    OsmPtVersionScheme version = OsmPtVersionScheme.NONE;
    OsmEntity osmEntity = getUnprocessedPtv1Stations(entityType).get(osmId);
    if(osmEntity == null) {
      osmEntity = getUnprocessedPtv2Stations(entityType).get(osmId);
      version = OsmPtVersionScheme.VERSION_2;
    }else {
      version = OsmPtVersionScheme.VERSION_1;
    }
    
    return osmEntity==null ? null : Pair.of(version, osmEntity); 
  }
 
  /** collect the Ptv1 stations that have been identified but not processed yet (unmodifiable)
   * 
   * @param entityType to collect them for
   * @return unprocess ptv1 stations
   */
  public Map<Long, OsmEntity> getUnprocessedPtv1Stations(EntityType entityType) {
    unprocessedPtv1Stations.putIfAbsent(entityType, new TreeMap<Long, OsmEntity>());
    return Collections.unmodifiableMap(unprocessedPtv1Stations.get(entityType));
  }
  
  /** add unprocessed ptv1 station
   * @param osmEntity to add
   */
  public void addUnprocessedPtv1Station(OsmEntity osmEntity) {
    EntityType type = null;
    if(osmEntity instanceof OsmNode) {
      type = EntityType.Node;
    }else if(osmEntity instanceof OsmWay) {
      type = EntityType.Way;
    }else {
      LOGGER.severe(String.format("unknown entity type when adding unprocessed Ptv1 station wit osm id %d, ignored"));
    }
       
    unprocessedPtv1Stations.putIfAbsent(type, new TreeMap<Long,OsmEntity>());
    unprocessedPtv1Stations.get(type).put(osmEntity.getId(), osmEntity);
  }  
  
  /** add unprocessed ptv2 station
   * @param osmEntity to add
   */
  public void addUnprocessedPtv2Station(OsmEntity osmEntity) {
    EntityType type = null;
    if(osmEntity instanceof OsmNode) {
      type = EntityType.Node;
    }else if(osmEntity instanceof OsmWay) {
      type = EntityType.Way;
    }else {
      LOGGER.severe(String.format("unknown entity type when adding unprocessed Ptv2 station wit osm id %d, ignored"));
    }
       
    unprocessedPtv2Stations.putIfAbsent(type, new TreeMap<Long,OsmEntity>());
    unprocessedPtv2Stations.get(type).put(osmEntity.getId(), osmEntity);
  }   

  /** collect unprocces ptv2 stations (unmodifiable) 
   * @param entityType to collect for (node, way)
   * @return unprocessed stations
   */
  public Map<Long, OsmEntity> getUnprocessedPtv2Stations(EntityType entityType) {
    unprocessedPtv2Stations.putIfAbsent(entityType, new TreeMap<Long, OsmEntity>());
    return Collections.unmodifiableMap(unprocessedPtv2Stations.get(entityType));
  }

  /** collect unprocessed Ptv2 stop positions
   * 
   * @return unprocessed stop positions (unmodifiable)
   */
  public Set<Long> getUnprocessedPtv2StopPositions() {
    return Collections.unmodifiableSet(unprocessedPtv2StopPositions);
  }
  
  /** remove unprocessed stop position
   * @param osmId to remove
   */
  public void removeUnprocessedPtv2StopPosition(long osmId) {
    unprocessedPtv2StopPositions.remove(osmId);
  }
  
  /** add unprocessed stop position
   * @param osmId to add
   */
  public void addUnprocessedPtv2StopPosition(long osmId) {
    unprocessedPtv2StopPositions.add(osmId);
  } 
  
  /** Verify if unprocessed stop_position is registered
   * @param osmId to verify
   * @return true when registered, false otherwise
   */
  public boolean hasUnprocessedPtv2StopPosition(long osmId) {
    return unprocessedPtv2StopPositions.contains(osmId);
  }  
  

  /** remove an unprocessed station
   * @param ptVersion pt version this
   * @param osmEntity to remove
   */
  public void removeUnproccessedStation(OsmPtVersionScheme ptVersion, OsmEntity osmEntity) {
    EntityType type = Osm4JUtils.getEntityType(osmEntity);
    switch (ptVersion) {
      case VERSION_1:
        unprocessedPtv1Stations.get(type).remove(osmEntity.getId());
        break;
      case VERSION_2:
        unprocessedPtv2Stations.get(type).remove(osmEntity.getId());
        break;
      default:
        LOGGER.warning(String.format("could not remove station %d from earlier identified unprocessed stations, this should not happen", osmEntity.getId()));
    }
  } 
  
  /** Remove all unprocessed station of a particular pt version that are currently still registered
   * 
   * @param ptVersion to remove all unprocessed station for
   */
  public void removeAllUnproccessedStations(OsmPtVersionScheme ptVersion) {
    switch (ptVersion) {
      case VERSION_1:
        unprocessedPtv1Stations.clear();    
      case VERSION_2:
        unprocessedPtv2Stations.clear();
      default:
        LOGGER.warning(String.format("could not remove stations, invalid pt version provided"));        
    }
  }  
  
  /** Remove all unprocessed station of a particular pt version and type that are currently still registered
   * 
   * @param ptVersion to remove stations for
   * @param type to remove all stations for
   */
  public void removeAllUnproccessedStations(OsmPtVersionScheme ptVersion, EntityType type) {
    switch (ptVersion) {
      case VERSION_1:
        getUnprocessedPtv1Stations(type).clear();    
      case VERSION_2:
        getUnprocessedPtv2Stations(type).clear();
      default:
        LOGGER.warning(String.format("could not remove stations, invalid pt version provided"));        
    }
  }   
  
  /** mark an osm way to be kept in unprocessed fashion even if it is not recognised as
   * as valid PT supporting way. This occurs when a way is part of for example a multi-polygon relation
   * where the way itself has no tags, but the relation is a PT supporting entity. In that case the way member
   * still holds information that we require when parsing the relation.
   * 
   * @param osmWayId to mark
   */
  public void markMultiPolygonOsmWayToKeepUnprocessed(long osmWayId) {
    /* include in unprocessed way, but without way itself, that is to be added later (we do not know it here)*/
    unprocessedMultiPolygonOsmWays.put(osmWayId, null);
  }  
  
  /** verify if the passed in osm way should be kept (even if it is not converted to a PLANit link
   * based on its current tags
   * 
   * @param osmWay to verify
   * @return true when it should, false otherwise
   */
  public boolean shouldMultiPolygonOsmWayBeKept(OsmWay osmWay) {
    return unprocessedMultiPolygonOsmWays.containsKey(osmWay.getId());
  }
  
  /** add osm way to keep. Should be based on a positive result from {@link shouldOsmWayBeKept}
   * 
   * @param osmWay to keep
   * @return osm way that was located in positino of new osmWay, or null if none
   */
  public OsmWay addUnprocessedMultiPolygonOsmWay(OsmWay osmWay) {
    /* now add way itself to map, as we know it must be kept and we have access to it */
    return unprocessedMultiPolygonOsmWays.put(osmWay.getId(), osmWay);
  }
  
  /** collect an unprocessed osm way 
   * @param osmWayId to verify
   * @return the way, null if not marked/available
   */
  public OsmWay getUnprocessedMultiPolygonOsmWay(long osmWayId) {
    return unprocessedMultiPolygonOsmWays.get(osmWayId);
  }
  
  /** collect the currently marked unprocessed Osm ways
   * @return unprocessed osm ways
   */
  public Map<Long, OsmWay> getUnprocessedMultiPolygonOsmWays() {
    return unprocessedMultiPolygonOsmWays;
  }
  
  /** add identified osm entity as invalid stop_position. When converting stop_positions to connectoids
   * it will be skipped without further issue or warning
   * 
   * @param type entity type
   * @param osmId osm entity to mark as invalid stop_position
   */
  public void addInvalidStopAreaStopPosition(EntityType type, long osmId) {
    invalidStopAreaStopPositions.putIfAbsent(type, new TreeSet<Long>());
    invalidStopAreaStopPositions.get(type).add(osmId);
  }
  
  /** Verify if marked as invalid
   * 
   * @param type entity type
   * @param osmId osm entity id to verify for invalidity
   * @return  true when marked invalid, false otherwise
   */
  public boolean isInvalidStopAreaStopPosition(EntityType type, long osmId) {
    if(type != null) {
      invalidStopAreaStopPositions.putIfAbsent(type, new TreeSet<Long>());
      return invalidStopAreaStopPositions.get(type).contains(osmId);
    }
    return false;
  }  

  /**
   * reset the handler
   */
  public void reset() {
    removeAllUnproccessedStations(OsmPtVersionScheme.VERSION_1);
    removeAllUnproccessedStations(OsmPtVersionScheme.VERSION_2);
    unprocessedPtv2StopPositions.clear();
    unprocessedMultiPolygonOsmWays.clear();     
  }

}

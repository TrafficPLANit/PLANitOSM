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
  private final Set<Long> unprocessedStopPositions= new TreeSet<Long>();
  
  /** the registered osm ways that we kept based on osmWaysToKeep that were provided, and are processed at a later stage */
  private final Map<Long, Long> osmOuterRoleOsmWaysByOsmRelationId = new TreeMap<Long, Long>();
  /** the registered osm ways to keep based on their outer_role identification in a multi_polygon */
  private final Map<Long, OsmWay> osmOuterRoleOsmWaysToKeep = new TreeMap<Long,OsmWay>();
  
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

  /** collect unprocessed stop positions
   * 
   * @return unprocessed stop positions (unmodifiable)
   */
  public Set<Long> getUnprocessedStopPositions() {
    return Collections.unmodifiableSet(unprocessedStopPositions);
  }
  
  /** remove unprocessed stop position
   * @param osmId to remove
   */
  public void removeUnprocessedStopPosition(long osmId) {
    unprocessedStopPositions.remove(osmId);
  }
  
  /** add unprocessed stop position
   * @param osmId to add
   */
  public void addUnprocessedStopPosition(long osmId) {
    unprocessedStopPositions.add(osmId);
  } 
  
  /** Verify if unprocessed stop_position is registered
   * @param osmId to verify
   * @return true when registered, false otherwise
   */
  public boolean hasUnprocessedStopPosition(long osmId) {
    return unprocessedStopPositions.contains(osmId);
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
  
  /** mark an osm way to be kept even if it is not recognised as
   * as valid PT supporting way. This occurs when a way is part of for example a multi-polygon relation
   * where the way itself has no tags, but the relation is a PT supporting entity. In that case the way member
   * still holds information that we require when parsing the relation.
   * 
   * @param osmWayId to mark
   */
  public void markOsmRelationOuterRoleOsmWayToKeep(long osmRelationId, long osmWayId) {
    osmOuterRoleOsmWaysByOsmRelationId.put(osmRelationId, osmWayId);
    osmOuterRoleOsmWaysToKeep.put(osmWayId,null);
  }  
  
  /** verify if the passed in osm way should be kept (even if it is not converted to a PLANit link
   * based on its current tags
   * 
   * @param osmWay to verify
   * @return true when it should, false otherwise
   */
  public boolean shouldOsmRelationOuterRoleOsmWayBeKept(OsmWay osmWay) {
    return osmOuterRoleOsmWaysToKeep.containsKey(osmWay.getId());
  }
  
  /** add osm way to keep. Should be based on a positive result from {@link shouldOsmWayBeKept}
   * 
   * @param osmWay to keep
   * @return osm way that was located in positino of new osmWay, or null if none
   */
  public OsmWay addOsmRelationOuterRoleOsmWay(OsmWay osmWay) {
    return osmOuterRoleOsmWaysToKeep.put(osmWay.getId(), osmWay);
  }
  
  /** Verify if osm way id is registered of osm relation id that qualifies as an outer role on that relation
   * @param osmRelationId to verify
   * @return true when present false otherwise
   */
  public boolean hasOuterRoleOsmWayByOsmRelationId(long osmRelationId) {
    return osmOuterRoleOsmWaysByOsmRelationId.containsKey(osmRelationId);
  }

  /** collect an unprocessed osm way that is identified as an outer role that is eligible as a platform/transfer zone
   * @param osmWayId to verify
   * @return the way, null if not marked/available
   */
  public OsmWay getOuterRoleOsmWayByOsmRelationId(long osmRelationId ) {
    Long osmWayId = osmOuterRoleOsmWaysByOsmRelationId.get(osmRelationId);
    if(osmWayId != null) {
      return osmOuterRoleOsmWaysToKeep.get(osmWayId);
    }
    return null;
  }  
  
  /** collect an unprocessed osm way that is identified as an outer role that is eligible as a platform/transfer zone
   * @param osmWayId to verify
   * @return the way, null if not marked/available
   */
  public OsmWay getOuterRoleOsmWayByOsmWayId(long osmWayId) {
    return osmOuterRoleOsmWaysToKeep.get(osmWayId);
  }
  
  /** check if outer role marked osm ways exist
   * @return true when present false otherwise
   */
  public boolean hasOsmRelationOuterRoleOsmWays() {
    return !osmOuterRoleOsmWaysToKeep.isEmpty();
  }  
  
  /** collect number of oter role osm ways that we kepts
   * @return number of outer role osm ways present
   */
  public long getNumberOfOuterRoleOsmWays() {
    return osmOuterRoleOsmWaysToKeep.size();
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
    unprocessedStopPositions.clear();
    osmOuterRoleOsmWaysByOsmRelationId.clear();
    osmOuterRoleOsmWaysToKeep.clear();
  }

}

package org.goplanit.osm.converter.zoning;

import java.util.*;
import java.util.logging.Logger;

import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.util.Osm4JUtils;
import org.goplanit.osm.util.OsmPtVersionScheme;
import org.goplanit.utils.misc.Pair;

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
public class OsmZoningReaderOsmData {
  
  /** logeger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningReaderOsmData.class.getCanonicalName());   
  
  /* UNPROCESSED OSM */

  /** track unprocessed but identified ferry terminals */
  private final SortedMap<Long, OsmNode> unprocessedFerryTerminals = new TreeMap<>();


  /** track unprocessed but identified Ptv1 station nodes */
  private final SortedMap<EntityType, SortedMap<Long, OsmEntity>> unprocessedPtv1Stations = new TreeMap<>();
  
  /** track unprocessed but identified Ptv2 station nodes/ways */
  private final SortedMap<EntityType, SortedMap<Long, OsmEntity>> unprocessedPtv2Stations = new TreeMap<>();
      
  /** track unprocessed but identified Ptv2 stop positions by their osm node id */
  private final SortedMap<Long, OsmNode> unprocessedStopPositions= new TreeMap<>();
  
  /** the registered osm ways to keep based on their outer_role identification in a multi_polygon */
  private final Map<Long, OsmWay> osmOuterRoleOsmWaysToKeep = new TreeMap<Long,OsmWay>();
  
  /* INVALID OSM */
  
  /** Stop positions found to be invalid or not needed due to inactive modes and are therefore to be ignored */
  private final Map<EntityType, Set<Long>> ignoreStopAreaStopPositions = new TreeMap<EntityType, Set<Long>>();
  
  /** osm waiting areas (platform, poles) found to be valid but not mapped to a planit mode, used to avoid logging user warnings when referenced by other 
   * osm entities such as stop_areas */  
  private final Map<EntityType, Set<Long>> waitingAreaWithoutMappedPlanitMode = new TreeMap<EntityType, Set<Long>>();

  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final OsmNodeData osmNodeData = new OsmNodeData();

  /* temporary storage of tracking eligible osmWays by id (based on whether they fall partially within boundary of parsing */
  private final Set<Long> spatiallyEligibleOsmWays = new HashSet<>();

  /* temporary storage of tracking eligible osmNodes by id based on whether they fall partially within boundary of parsing*/
  private final Set<Long> spatiallyEligibleOsmNodes = new HashSet<>();

  /* temporary storage of tracking eligible osmRelations by id based on whether they fall partially within boundary*/
  private final Set<Long> spatiallyEligibleOsmRelations = new HashSet<>();


  /**
   * Access to OSM node data
   * @return OSM node data
   */
  public OsmNodeData getOsmNodeData(){
    return osmNodeData;
  }

  public boolean isOsmWaySpatiallyEligible(long osmWayId){
    return spatiallyEligibleOsmWays.contains(osmWayId);
  }

  public void markOsmWaySpatiallyEligible(long osmWayId){
    spatiallyEligibleOsmWays.add(osmWayId);
  }

  public boolean isOsmNodeSpatiallyEligible(long osmNodeId){
    return spatiallyEligibleOsmNodes.contains(osmNodeId);
  }

  public void markOsmNodeSpatiallyEligible(long osmNodeId){
    spatiallyEligibleOsmNodes.add(osmNodeId);
  }

  public void markOsmRelationSpatiallyEligible(long osmRelationId) {
    spatiallyEligibleOsmRelations.add(osmRelationId);
  }

  public boolean isOsmRelationSpatiallyEligible(long osmRelationId) {
    return spatiallyEligibleOsmRelations.contains(osmRelationId);
  }

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

  /** Collect an unprocessed ferry terminal if it exists
   *
   * @param osmId id to collect for
   * @return unprocessed ferry terminal found, null otherwise
   */
  public OsmNode getUnprocessedPtv1FerryTerminal(long osmId) {
    return getUnprocessedPtv1FerryTerminals().get(osmId);
  }

  /** Verify existence of an unprocessed ferry terminal
   *
   * @param osmId id to collect for
   * @return true when unprocessed ferry terminal exists, false otherwise
   */
  public boolean hasUnprocessedPtv1FerryTerminal(long osmId) {
    return getUnprocessedPtv1FerryTerminal(osmId) != null;
  }
 
  /** collect the Ptv1 stations that have been identified but not processed yet (unmodifiable)
   * 
   * @param entityType to collect them for
   * @return unprocessed ptv1 stations
   */
  public SortedMap<Long, OsmEntity> getUnprocessedPtv1Stations(EntityType entityType) {
    unprocessedPtv1Stations.putIfAbsent(entityType, new TreeMap<>());
    return Collections.unmodifiableSortedMap(unprocessedPtv1Stations.get(entityType));
  }

  /** collect the ferry terminal that have been identified but not processed yet (unmodifiable)
   *
   * @return unprocessed ferry terminal
   */
  public SortedMap<Long, OsmNode> getUnprocessedPtv1FerryTerminals() {
    return Collections.unmodifiableSortedMap(unprocessedFerryTerminals);
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
      LOGGER.severe(String.format("Unknown entity type when adding unprocessed Ptv1 station wit OSM id %d, ignored"));
    }
       
    unprocessedPtv1Stations.putIfAbsent(type, new TreeMap<>());
    unprocessedPtv1Stations.get(type).put(osmEntity.getId(), osmEntity);
  }

  /** add unprocessed ferry terminal
   * @param osmEntity to add
   */
  public void addUnprocessedPtv1FerryTerminal(OsmEntity osmEntity) {
    if(!(osmEntity instanceof OsmNode)) {
      LOGGER.severe(String.format("Ferry terminal should be node, found otherwise (%d), tagging error, ignored", osmEntity.getId()));
    }
    unprocessedFerryTerminals.put(osmEntity.getId(), (OsmNode) osmEntity);
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
      LOGGER.severe(String.format("Unknown entity type when adding unprocessed Ptv2 station wit OSM id %d, ignored"));
    }
       
    unprocessedPtv2Stations.putIfAbsent(type, new TreeMap<Long,OsmEntity>());
    unprocessedPtv2Stations.get(type).put(osmEntity.getId(), osmEntity);
  }   

  /** collect unprocces ptv2 stations (unmodifiable) 
   * @param entityType to collect for (node, way)
   * @return unprocessed stations
   */
  public SortedMap<Long, OsmEntity> getUnprocessedPtv2Stations(EntityType entityType) {
    unprocessedPtv2Stations.putIfAbsent(entityType, new TreeMap<>());
    return Collections.unmodifiableSortedMap(unprocessedPtv2Stations.get(entityType));
  }

  /** collect unprocessed stop positions
   * 
   * @return unprocessed stop positions (unmodifiable)
   */
  public SortedMap<Long, OsmNode> getUnprocessedStopPositions() {
    return Collections.unmodifiableSortedMap(unprocessedStopPositions);
  }
  
  /** remove unprocessed stop position
   * @param osmId to remove
   */
  public void removeUnprocessedStopPosition(long osmId) {
    unprocessedStopPositions.remove(osmId);
  }

  /** remove unprocessed ferry terminal
   * @param osmId to remove
   */
  public void removeUnprocessedPtv1FerryTerminal(long osmId) {
    unprocessedFerryTerminals.remove(osmId);
  }
  
  /** add unprocessed stop position
   * @param osmNode to add
   */
  public void addUnprocessedStopPosition(OsmNode osmNode) {
    unprocessedStopPositions.put(osmNode.getId(), osmNode);
  } 
  
  /** Verify if unprocessed stop_position is registered
   * @param osmId to verify
   * @return true when registered, false otherwise
   */
  public boolean hasUnprocessedStopPosition(long osmId) {
    return unprocessedStopPositions.containsKey(osmId);
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
        break;
      case VERSION_2:
        unprocessedPtv2Stations.clear();
        break;
      default:
        LOGGER.warning(String.format("could not remove stations, invalid pt version provided"));        
    }
  }

  /** Remove all unprocessed ferry terminals
   *
   */
  public void removeAllUnprocessedPtv1FerryTerminals() {
    unprocessedFerryTerminals.clear();
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
        break;
      case VERSION_2:
        getUnprocessedPtv2Stations(type).clear();
        break;
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
  public void markOsmRelationOuterRoleOsmWayToKeep(long osmWayId) {
    osmOuterRoleOsmWaysToKeep.put(osmWayId,null);
  } 
  
  /** Remove marked entry for keeping
   * @param osmWayId to remove from being flagged
   */
  public void removeOsmRelationOuterRoleOsmWay(long osmWayId) {
    osmOuterRoleOsmWaysToKeep.remove(osmWayId);
  }
  
  /** verify if the passed in OSM way should be kept (even if it is not converted to a PLANit link
   * based on its current tags
   * 
   * @param osmWay to verify
   * @return true when it should, false otherwise
   */
  public boolean shouldOsmRelationOuterRoleOsmWayBeKept(OsmWay osmWay) {
    return osmOuterRoleOsmWaysToKeep.containsKey(osmWay.getId());
  }
  
  /** Add OSM way to keep. Should be based on a positive result from shouldOsmWayBeKept
   * 
   * @param osmWay to keep
   * @return osm way that was located in position of new osmWay, or null if none
   */
  public OsmWay addOsmRelationOuterRoleOsmWay(OsmWay osmWay) {
    return osmOuterRoleOsmWaysToKeep.put(osmWay.getId(), osmWay);
  }
  
  /** Verify if osm way id is registered that qualifies as an outer role on that relation
   * @param osmWayId to verify
   * @return true when present false otherwise
   */
  public boolean hasOuterRoleOsmWay(long osmWayId) {
    return osmOuterRoleOsmWaysToKeep.containsKey(osmWayId);
  }
  
  /** collect an unprocessed osm way that is identified as an outer role that is eligible as a platform/transfer zone
   * @param osmWayId to verify
   * @return the way, null if not marked/available
   */
  public OsmWay getOuterRoleOsmWay(long osmWayId) {
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
    
  /** add identified OSM entity to be ignored if it pops up as a stop_area stop_position. For example, it was deemed
   *  incorrectly tagged already, or it pertains to a mode that is not being supported. The latter might not be verifiable
   *  when encountered as part of a relation, hence mark it here as such. It will then be skipped without further
   *  issue or warning
   * 
   * @param type entity type
   * @param osmId osm entity to mark as invalid stop_position
   */
  public void addIgnoreStopAreaStopPosition(EntityType type, long osmId) {
    ignoreStopAreaStopPositions.putIfAbsent(type, new TreeSet<>());
    ignoreStopAreaStopPositions.get(type).add(osmId);
  }
  
  /** Verify if marked as to be ignored
   * 
   * @param type entity type
   * @param osmId OSM entity id to verify for invalidity
   * @return  true when marked invalid, false otherwise
   */
  public boolean isIgnoreStopAreaStopPosition(EntityType type, long osmId) {
    if(type != null) {
      ignoreStopAreaStopPositions.putIfAbsent(type, new TreeSet<>());
      return ignoreStopAreaStopPositions.get(type).contains(osmId);
    }
    return false;
  }  
  
  /** Add identified OSM entity as valid waiting area (platform, pole), but it has no mapped PLANit mode, e.g. a ferry stop
   * when ferries are not included. By registering this one can check that if a waiting area cannot be collected later on, for example
   * when a waiting area is part of a stop_area and it cannot be found a warning is issued, unless it is registered here
   * 
   * @param type entity type
   * @param osmId OSM entity to mark as waiting area without mapped PLANit mode
   */  
  public void addWaitingAreaWithoutMappedPlanitMode(EntityType type, long osmId) {
    waitingAreaWithoutMappedPlanitMode.putIfAbsent(type, new TreeSet<>());
    waitingAreaWithoutMappedPlanitMode.get(type).add(osmId);
  }
  
  /** Verify if marked as waiting area without a mapped planit mode
   * 
   * @param type entity type
   * @param osmId OSM entity id to verify for if the wainting area is marked as a valid one but simply not mapped to a planit mode
   * @return  true when marked invalid, false otherwise
   */  
  public boolean isWaitingAreaWithoutMappedPlanitMode(EntityType type, long osmId) {
    if(type != null) {
      waitingAreaWithoutMappedPlanitMode.putIfAbsent(type, new TreeSet<>());
      return waitingAreaWithoutMappedPlanitMode.get(type).contains(osmId);
    }
    return false;
  }   

  /**
   * reset the handler
   */
  public void reset() {
    removeAllUnproccessedStations(OsmPtVersionScheme.VERSION_1);
    removeAllUnproccessedStations(OsmPtVersionScheme.VERSION_2);
    removeAllUnprocessedPtv1FerryTerminals();
    unprocessedStopPositions.clear();
    osmOuterRoleOsmWaysToKeep.clear();
    waitingAreaWithoutMappedPlanitMode.clear();    
    ignoreStopAreaStopPositions.clear();
    osmNodeData.reset();
    spatiallyEligibleOsmRelations.clear();
    spatiallyEligibleOsmWays.clear();
    spatiallyEligibleOsmNodes.clear();
  }

}

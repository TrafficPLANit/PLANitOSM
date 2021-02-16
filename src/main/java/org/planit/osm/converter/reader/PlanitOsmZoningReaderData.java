package org.planit.osm.converter.reader;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.util.Osm4JUtils;
import org.planit.utils.geo.PlanitJtsIntersectItemVisitor;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;

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
public class PlanitOsmZoningReaderData {
  
  /* UNPROCESSED OSM */

  /** track unprocessed but identified Ptv1 station nodes */
  private final Set<OsmNode> unprocessedPtv1Stations = new TreeSet<OsmNode>(Osm4JUtils.createOsmEntityComparator());
  
  /** track unprocessed but identified Ptv2 station nodes/ways */
  private final Map<EntityType,Set<OsmEntity>> unprocessedPtv2Stations = new TreeMap<EntityType,Set<OsmEntity>>();
      
  /** track unprocessed but identified Ptv2 stop positions by their osm node id */
  private final Set<Long> unprocessedPtv2StopPositions= new HashSet<Long>();
  
  /** the registered osm ways that we kept based on osmWaysToKeep that were provided, and are processed at a later stage */
  private final Map<Long, OsmWay> unprocessedOsmWays = new HashMap<Long, OsmWay>();
  
  /* OSM <-> TRANSFER ZONE TRACKING */
  
  /** track transfer zones without connectoids yet that were extracted from an OsmNode or way (osm id is key) */
  private final Map<EntityType, Map<Long, TransferZone>> transferZoneWithoutConnectoidByOsmEntityId = new TreeMap<EntityType,Map<Long,TransferZone>>();
  
  /** in addition to tracking transfer zones by their Osm entity id, we also track them spatially, to be able to map them to close by stop positions if needed */
  
  private final Map<EntityType, Quadtree> transferZoneWithoutConnectoidBySpatialIndex = new TreeMap<EntityType, Quadtree>();
  
  /* OSM <-> CONNECTOID TRACKING */
  
  /** track created connectoids by their osm node id and layer they reside on, needed to avoid creating duplicates when dealing with multiple modes/layers */
  private final Map<InfrastructureLayer,Map<Long, Set<DirectedConnectoid>>> directedConnectoidsByOsmNodeId = new HashMap<InfrastructureLayer,Map<Long, Set<DirectedConnectoid>>>();
  
  /* OSM <-> TRANSFER ZONE GROUP TRACKING */
  
  private final Map<Long, TransferZoneGroup> transferZoneGroupsByOsmId = new HashMap<Long, TransferZoneGroup>();
  
  
  /* UNPROCESSED RELATED METHODS */
 
  /** collect the Ptv1 stations that have been identified but not processed yet
   * 
   * @return unprocess ptv1 stations
   */
  public Set<OsmNode> getUnprocessedPtv1Stations() {
    return unprocessedPtv1Stations;
  }

  /** collect unprocces ptv2 stations 
   * @param entityType to collect for (node, way)
   * @return unprocessed stations
   */
  public Set<OsmEntity> getUnprocessedPtv2Stations(EntityType entityType) {
    unprocessedPtv2Stations.putIfAbsent(entityType, new TreeSet<OsmEntity>(Osm4JUtils.createOsmEntityComparator()));
    return unprocessedPtv2Stations.get(entityType);
  }

  public Set<Long> getUnprocessedPtv2StopPositions() {
    return unprocessedPtv2StopPositions;
  }
  
  /** mark an osm way to be kept in unprocessed fashion even if it is not recognised as
   * as valid PT supporting way. This occurs when a way is part of for example a multi-polygon relation
   * where the way itself has no tags, but the relation is a PT supporting entity. In that case the way member
   * still holds information that we require when parsing the relation.
   * 
   * @param osmWayId to mark
   */
  public void markOsmWayToKeepUnprocessed(long osmWayId) {
    /* include in unprocessed way, but without way itself, that is to be added later (we do not know it here)*/
    unprocessedOsmWays.put(osmWayId, null);
  }  
  
  /** verify if the passed in osm way should be kept (even if it is not converted to a PLANit link
   * based on its current tags
   * 
   * @param osmWay to verify
   * @return true when it should, false otherwise
   */
  public boolean shouldOsmWayBeKept(OsmWay osmWay) {
    return unprocessedOsmWays.containsKey(osmWay.getId());
  }
  
  /** add osm way to keep. Should be based on a positive result from {@link shouldOsmWayBeKept}
   * 
   * @param osmWay to keep
   * @return osm way that was located in positino of new osmWay, or null if none
   */
  public OsmWay addUnprocessedOsmWay(OsmWay osmWay) {
    /* now add way itself to map, as we know it must be kept and we have access to it */
    return unprocessedOsmWays.put(osmWay.getId(), osmWay);
  }
  
  /** collect an unprocessed osm way 
   * @param osmWayId to verify
   * @return the way, null if not marked/available
   */
  public OsmWay getUnprocessedOsmWay(long osmWayId) {
    return unprocessedOsmWays.get(osmWayId);
  }
  
  /** collect the currently marked unprocessed Osm ways
   * @return unprocessed osm ways
   */
  public Map<Long, OsmWay> getUnprocessedOsmWays() {
    return unprocessedOsmWays;
  }  
  
  /* TRANSFER ZONE RELATED METHODS */  

  /** collect the transfer zone without connectoid by entity type and entity id
   * @param entityType to collect for (node, way)
   * @param osm id (node id/way id)
   * @return transfer zone registered, null if not present
   */
  public TransferZone getTransferZoneWithoutConnectoid(EntityType entityType, long osmEntityId) {
    transferZoneWithoutConnectoidByOsmEntityId.putIfAbsent(entityType, new HashMap<Long,TransferZone>());
    return transferZoneWithoutConnectoidByOsmEntityId.get(entityType).get(osmEntityId);
  }
  
  /** collect the transfer zone without connectoid by entity type and a spatial bounding box. Collect all transfer zones
   * that fall within or intersect with this bounding box
   * 
   * @param entityType to collect for (node, way)
   * @param boundingBox to identify transfer zones spatially
   * @return list of found transfer zones, caller needs to cast entries to TransferZone type
   */
  public Collection<TransferZone> getTransferZonesWithoutConnectoid(Envelope boundingBox) {
    
    final Set<TransferZone> correctZones = new HashSet<TransferZone>();
    final PlanitJtsIntersectItemVisitor<TransferZone> spatialZoneFilterVisitor = new PlanitJtsIntersectItemVisitor<TransferZone>(boundingBox, correctZones);
        
    /* query the spatially indexed entries AND apply the visitor that filteres out false positives due to the coarseness of the quadtrees grid */
    for( Entry<EntityType, Quadtree> entry : transferZoneWithoutConnectoidBySpatialIndex.entrySet()) {
      transferZoneWithoutConnectoidBySpatialIndex.get(entry.getKey()).query(boundingBox, spatialZoneFilterVisitor);
    }
    
    return spatialZoneFilterVisitor.getResult();
  }  
  
  /** add a transfer zone without connectoids to the tracking container
   * 
   * @param entityType to register for
   * @param osmEntityId osm id
   * @param transferZone the transfer zone
   * @return previous entry in container, if any
   */
  public TransferZone addTransferZoneWithoutConnectoid(EntityType entityType, long osmEntityId, TransferZone transferZone) {
    transferZoneWithoutConnectoidByOsmEntityId.putIfAbsent(entityType, new HashMap<Long,TransferZone>());
    transferZoneWithoutConnectoidBySpatialIndex.putIfAbsent(entityType, new Quadtree());    
    /* spatial index */
    transferZoneWithoutConnectoidBySpatialIndex.get(entityType).insert(transferZone.getEnvelope(), transferZone);
    /* id index */
    return transferZoneWithoutConnectoidByOsmEntityId.get(entityType).put(osmEntityId, transferZone);
  }  
  
  /** remove all provided transfer zones by Osm id
   * @param type entity type
   * @param transferZones to remove by Osm entity id
   */
  public void removeTransferZonesWithoutConnectoids(EntityType type, Set<Long> transferZones) {
    Map<Long,TransferZone> transferzones = transferZoneWithoutConnectoidByOsmEntityId.get(type);
    if(transferzones != null) {
      transferZones.forEach( transferZoneId -> transferzones.remove(transferZoneId));      
    }
  }   
  
  /* CONNECTOID RELATED METHODS */  

  /** collect the registered connectoids by their osm id for a given network layer
   * @param networkLayer to use
   * @return registered directed connectoids by OsmId
   */
  public Map<Long, Set<DirectedConnectoid>> getDirectedConnectoidsByOsmNodeId(MacroscopicPhysicalNetwork networkLayer) {
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer,  new HashMap<Long, Set<DirectedConnectoid>>());
    return directedConnectoidsByOsmNodeId.get(networkLayer);
  }
  
  /* TRANSFER ZONE GROUP RELATED METHODS */  
  
  /** collect a parsed transfer zone group by osm id
   * @param osmId
   * @return transfer zone group
   */
  public TransferZoneGroup getTransferZoneGroupByOsmId(long osmId) {
    return transferZoneGroupsByOsmId.get(osmId);
  }  
  
  /** add a transfer zone group by its osm id
   * @param osmId to use
   * @param transferZoneGroup group to add
   * @return group in container location before this one was added, null if none existed
   */
  public TransferZoneGroup addTransferZoneGroupByOsmId(long osmId, TransferZoneGroup transferZoneGroup) {
    return transferZoneGroupsByOsmId.put(osmId, transferZoneGroup);
  }

  /**
   * reset the handler
   */
  public void reset() {
    unprocessedPtv1Stations.clear();
    unprocessedPtv2Stations.clear();
    unprocessedPtv2StopPositions.clear();
    unprocessedOsmWays.clear();
    transferZoneWithoutConnectoidByOsmEntityId.clear();
    directedConnectoidsByOsmNodeId.clear();     
  }

 
}

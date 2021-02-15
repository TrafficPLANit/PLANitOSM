package org.planit.osm.handler;

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

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Data specifically required in the zoning handler while parsing OSM data
 * 
 * @author markr
 *
 */
class PlanitOsmZoningHandlerData {

  /** track unprocessed but identified Ptv1 station nodes */
  private final Set<OsmNode> unprocessedPtv1Stations = new TreeSet<OsmNode>(Osm4JUtils.createOsmEntityComparator());
  
  /** track unprocessed but identified Ptv2 station nodes/ways */
  private final Map<EntityType,Set<OsmEntity>> unprocessedPtv2Stations = new TreeMap<EntityType,Set<OsmEntity>>();  
  
  /** track unprocessed but identified Ptv2 stop positions by their osm node id */
  private final Set<Long> unprocessedPtv2StopPositions= new HashSet<Long>();
  
  /** track transfer zones without connectoids yet that were extracted from an OsmNode or way (osm id is key) */
  private final Map<EntityType, Map<Long, TransferZone>> transferZoneWithoutConnectoidByOsmEntityId = new TreeMap<EntityType,Map<Long,TransferZone>>();
  
  /** in addition to tracking transfer zones by their Osm entity id, we also track them spatially, to be able to map them to close by stop positions if needed */
  
  private final Map<EntityType, Quadtree> transferZoneWithoutConnectoidBySpatialIndex = new TreeMap<EntityType, Quadtree>();  
  
  /** track created connectoids by their osm node id and layer they reside on, needed to avoid creating duplicates when dealing with multiple modes/layers */
  private final Map<InfrastructureLayer,Map<Long, Set<DirectedConnectoid>>> directedConnectoidsByOsmNodeId = new HashMap<InfrastructureLayer,Map<Long, Set<DirectedConnectoid>>>();
 
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

  /** collect the registered connectoids by their osm id for a given network layer
   * @param networkLayer to use
   * @return registered directed connectoids by OsmId
   */
  public Map<Long, Set<DirectedConnectoid>> getDirectedConnectoidsByOsmNodeId(MacroscopicPhysicalNetwork networkLayer) {
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer,  new HashMap<Long, Set<DirectedConnectoid>>());
    return directedConnectoidsByOsmNodeId.get(networkLayer);
  }

  public void reset() {
    unprocessedPtv1Stations.clear();
    unprocessedPtv2Stations.clear();
    unprocessedPtv2StopPositions.clear();
    transferZoneWithoutConnectoidByOsmEntityId.clear();
    directedConnectoidsByOsmNodeId.clear();    
  }

 
}

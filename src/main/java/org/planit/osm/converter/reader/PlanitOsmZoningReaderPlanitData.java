package org.planit.osm.converter.reader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.geo.PlanitJtsIntersectZoneVisitor;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;

import de.topobyte.osm4j.core.model.iface.EntityType;

/**
 * Data specifically required in the zoning reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class PlanitOsmZoningReaderPlanitData {
  
  /** logeger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningReaderData.class.getCanonicalName());
    
  /* OSM <-> TRANSFER ZONE TRACKING */
  
  /** track (potentially) incomplete transfer zones (not all connectoids parsed) by their osm id that were extracted from an OsmNode or way (osm id is key) */
  private final Map<EntityType, Map<Long, TransferZone>> incompleteTransferZonesByOsmEntityId = new TreeMap<EntityType,Map<Long,TransferZone>>();
  
  /** in addition to tracking (potentially) incomplete transfer zones by their Osm entity id, we also track them spatially, to be able to map them to close by stop positions if needed */  
  private final Map<EntityType, Quadtree> incompleteTransferZonesBySpatialIndex = new TreeMap<EntityType, Quadtree>();
  
  /* OSM <-> CONNECTOID TRACKING */
  
  /** track created connectoids by their location and layer they reside on, needed to avoid creating duplicates when dealing with multiple modes/layers */
  private final Map<InfrastructureLayer,Map<Point, List<DirectedConnectoid>>> directedConnectoidsByOsmNodeId = new HashMap<InfrastructureLayer,Map<Point, List<DirectedConnectoid>>>();
  
  
  /* TRANSFER ZONE <-> CONNECTOID TRACKING */
  
  /** track mapping from osm stop_area (transfer zone) to connectoids that refer to it (stop_position), track this because planit only tracks the other way around */
  private final Map<TransferZone, List<DirectedConnectoid> > connectoidsByTransferZone = new HashMap<TransferZone, List<DirectedConnectoid>>();
  
  /* OSM <-> TRANSFER ZONE GROUP TRACKING */
  
  /** track mapping from osm stop_area id to the transfer zone group that goes with it on the planit side */
  private final Map<Long, TransferZoneGroup> transferZoneGroupsByOsmId = new HashMap<Long, TransferZoneGroup>();  
        
  /* TRANSFER ZONE RELATED METHODS */  
  
  /** collect all potentially incomplete transfer zones based on Osm entity type they originated from
   * @param entityType os the transfer zone origin
   * @return all transfer zones up until now by their original OsmEntityId
   */
  public Map<Long, TransferZone> getIncompleteTransferZonesByOsmId(EntityType entityType) {
    return Collections.unmodifiableMap(incompleteTransferZonesByOsmEntityId.get(entityType));
  }  

  /** collect the potentially incomplete transfer zone by entity type and osm id
   * @param entityType to collect for (node, way)
   * @param osm id (node id/way id)
   * @return transfer zone registered, null if not present
   */
  public TransferZone getIncompleteTransferZoneByOsmId(EntityType entityType, long osmEntityId) {
    incompleteTransferZonesByOsmEntityId.putIfAbsent(entityType, new HashMap<Long,TransferZone>());
    return incompleteTransferZonesByOsmEntityId.get(entityType).get(osmEntityId);
  }
  
  /** collect the potentially incomplete transfer zones by entity type and a spatial bounding box. Collect all created transfer zones
   * that fall within or intersect with this bounding box.
   * 
   * @param entityType to collect for (node, way)
   * @param boundingBox to identify transfer zones spatially
   * @return list of found transfer zones, caller needs to cast entries to TransferZone type
   */
  public Collection<TransferZone> getIncompleteTransferZonesSpatially(Envelope boundingBox) {
    
    final Set<TransferZone> correctZones = new HashSet<TransferZone>();
    final PlanitJtsIntersectZoneVisitor<TransferZone> spatialZoneFilterVisitor = 
        new PlanitJtsIntersectZoneVisitor<TransferZone>(PlanitJtsUtils.create2DPolygon(boundingBox), correctZones);          
    
    /* query the spatially indexed entries AND apply the visitor that filteres out false positives due to the coarseness of the quadtrees grid */
    for( Entry<EntityType, Quadtree> entry : incompleteTransferZonesBySpatialIndex.entrySet()) {
      incompleteTransferZonesBySpatialIndex.get(entry.getKey()).query(boundingBox, spatialZoneFilterVisitor);
    }
    
    return spatialZoneFilterVisitor.getResult();
  }  
  
  /** add a incomplete transfer zone to the tracking container
   * 
   * @param entityType to register for
   * @param osmEntityId osm id
   * @param transferZone the transfer zone
   * @return previous entry in container, if any
   */
  public TransferZone addIncompleteTransferZone(EntityType entityType, long osmEntityId, TransferZone transferZone) {
    incompleteTransferZonesByOsmEntityId.putIfAbsent(entityType, new HashMap<Long,TransferZone>());
    incompleteTransferZonesBySpatialIndex.putIfAbsent(entityType, new Quadtree());    
    
    /* spatial index */
    Envelope transferZoneBoundingBox = transferZone.getEnvelope();
    if(transferZoneBoundingBox == null) {
      LOGGER.warning(String.format("unable to track transfer zone %d while parsing, unknown spatial features, ignored", osmEntityId));
      return null;
    }    
    incompleteTransferZonesBySpatialIndex.get(entityType).insert(transferZone.getEnvelope(), transferZone);
    
    /* id index */
    return incompleteTransferZonesByOsmEntityId.get(entityType).put(osmEntityId, transferZone);
  }  
  
  /** remove all provided transfer zones by Osm id as they are deemed complete (have the appropriate connectoid(s))
   * 
   * @param type entity type
   * @param transferZonesToRemove to remove by Osm entity id
   */
  public void removeIncompleteTransferZones(EntityType type, Set<Long> transferZonesToRemove) {    
    for(long transferZoneOsmId : transferZonesToRemove) {
      removeIncompleteTransferZones(type, transferZoneOsmId);
    }
  }   
  
  /** remove all provided transfer zones by Osm id as they are deemed complete (have the appropriate connectoid(s))
   * 
   * @param type entity type
   * @param transferZones to remove by Osm entity id
   */
  public void removeIncompleteTransferZones(EntityType type, Long... transferZoneOsmIds) {
    removeIncompleteTransferZones(type, Set.of(transferZoneOsmIds));
  }  
  
  /** remove provided transfer zone by Osm id (unique within type) as it is deemed complete (have the appropriate connectoid(s))
   *  
   * @param type entity type
   * @param transferZoneOsmId to remove by Osm entity id
   * @return removed transfer zone if any, null otherwise
   */
  public TransferZone removeIncompleteTransferZone(EntityType type, long transferZoneOsmId) {
    Map<Long,TransferZone> theTransferZones = incompleteTransferZonesByOsmEntityId.get(type);
    TransferZone removedTransferZone = theTransferZones.remove(transferZoneOsmId);
    if(removedTransferZone!=null) {
      incompleteTransferZonesBySpatialIndex.get(type).remove(removedTransferZone.getEnvelope(), removedTransferZone);
    }
    return removedTransferZone;
  }    
  
  /* CONNECTOID RELATED METHODS */  

  /** collect the registered connectoids indexed by their locations for a given network layer (unmodifiable)
   * 
   * @param networkLayer to use
   * @return registered directed connectoids indexed by location
   */
  public Map<Point, List<DirectedConnectoid>> getDirectedConnectoidsByLocation(MacroscopicPhysicalNetwork networkLayer) {
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer,  new HashMap<Point, List<DirectedConnectoid>>());
    return Collections.unmodifiableMap(directedConnectoidsByOsmNodeId.get(networkLayer));
  }
  
  /**collect the registered connectoids by given locations and network layer (unmodifiable)
   * 
   * @param nodeLocation to verify
   * @param networkLayerto extract from
   * @return found connectoids (if any), otherwise null or empty set
   */
  public List<DirectedConnectoid> getDirectedConnectoidsByLocation(Point nodeLocation, MacroscopicPhysicalNetwork networkLayer) {
    return getDirectedConnectoidsByLocation(networkLayer).get(nodeLocation);
  }  
  
  /** add a connectoid to the registered connectoids indexed by their osm id
   * 
   * @param networkLayer to register for
   * @param osmAccessNodeid this connectoid relates to
   * @param connectoid to add
   * @return true when successful, false otherwise
   */
  public boolean addDirectedConnectoidByLocation(MacroscopicPhysicalNetwork networkLayer, Point connectoidLocation , DirectedConnectoid connectoid) {
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer,  new HashMap< Point, List<DirectedConnectoid>>());
    Map<Point, List<DirectedConnectoid>> connectoidsForLayer = directedConnectoidsByOsmNodeId.get(networkLayer);
    connectoidsForLayer.putIfAbsent(connectoidLocation, new ArrayList<DirectedConnectoid>(1));
    List<DirectedConnectoid> connectoids = connectoidsForLayer.get(connectoidLocation);
    if(!connectoids.contains(connectoid)) {
      return connectoids.add(connectoid);
    }
    return false;
  }
  
  /** check if any connectoids have been registered for the given location on any layer
   * @param location to verify
   * @return true when present, false otherwise
   */
  public boolean hasAnyDirectedConnectoidsForLocation(Point location) {
    for( Entry<InfrastructureLayer, Map<Point, List<DirectedConnectoid>>> entry : directedConnectoidsByOsmNodeId.entrySet()) {
      if(hasDirectedConnectoidForLocation(entry.getKey(), location)) {
        return true;
      }
    }
    return false;
  }  
  
  /** check if any connectoid has been registered for the given location for this layer
   * @param location to verify
   * @param networkLayer to check for
   * @return true when present, false otherwise
   */  
  public boolean hasDirectedConnectoidForLocation(InfrastructureLayer networkLayer, Point createPoint) {
    Map<Point, List<DirectedConnectoid>>  connectoidsForLayer = directedConnectoidsByOsmNodeId.get(networkLayer);
    return connectoidsForLayer != null && connectoidsForLayer.get(createPoint) != null && !connectoidsForLayer.get(createPoint).isEmpty();
  }  
  
  /** register a known mapping from transfer zone to connectoid
   * 
   * @param transferZone to map to...
   * @param newConnectoid ...this connectoid
   */
  public void addConnectoidByTransferZone(TransferZone transferZone, DirectedConnectoid connectoid) {
    connectoidsByTransferZone.putIfAbsent(transferZone, new ArrayList<DirectedConnectoid>(1));
    List<DirectedConnectoid> connectoids = connectoidsByTransferZone.get(transferZone);
    if(!connectoids.contains(connectoid)) {
      connectoids.add(connectoid);
    }
  }  
  
  /** Verify if transfer zone has connectoids present
   * @param transferZone
   * @return true when there exist connectoids that reference this transfer zone, false otherwise
   */
  public boolean hasConnectoids(TransferZone transferZone) {
    return getConnectoidsByTransferZone(transferZone) != null && !getConnectoidsByTransferZone(transferZone).isEmpty();
  }
  
  /** register a known mapping from transfer zone to connectoid
   * 
   * @param transferZone to map to...
   * @param newConnectoid ...this connectoid
   */
  public Collection<DirectedConnectoid> getConnectoidsByTransferZone(TransferZone transferZone) {
    if(transferZone == null) {
      return null;
    }
    connectoidsByTransferZone.putIfAbsent(transferZone, new ArrayList<DirectedConnectoid>(1));
    return connectoidsByTransferZone.get(transferZone);
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
   * reset the planit data tracking containers
   */
  public void reset() {
    incompleteTransferZonesByOsmEntityId.clear();
    directedConnectoidsByOsmNodeId.clear();     
    connectoidsByTransferZone.clear();
  }


}

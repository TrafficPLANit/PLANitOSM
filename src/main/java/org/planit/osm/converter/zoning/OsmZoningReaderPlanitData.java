package org.planit.osm.converter.zoning;

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
import org.planit.network.layer.macroscopic.MacroscopicPhysicalLayerImpl;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitGraphGeoUtils;
import org.planit.utils.geo.PlanitJtsIntersectZoneVisitor;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.Edges;
import org.planit.utils.network.layer.TransportLayer;
import org.planit.utils.network.layer.physical.Link;
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
public class OsmZoningReaderPlanitData {
  
  /** logeger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningReaderData.class.getCanonicalName());
    
  /* OSM <-> TRANSFER ZONE TRACKING */
  
  /** track created transfer zones by their osm id that were extracted from an OsmNode or way (osm id is key) */
  private final Map<EntityType, Map<Long, TransferZone>> transferZonesByOsmEntityId = new TreeMap<EntityType,Map<Long,TransferZone>>();
  
  /** in addition to tracking transfer zones by their Osm entity id, we also track them spatially, to be able to map them to close by stop positions if needed */  
  private final Map<EntityType, Quadtree> transferZonesBySpatialIndex = new TreeMap<EntityType, Quadtree>();
    
  /* OSM <-> CONNECTOID TRACKING */
  
  /** track created connectoids by their location and layer they reside on, needed to avoid creating duplicates when dealing with multiple modes/layers */
  private final Map<TransportLayer,Map<Point, List<DirectedConnectoid>>> directedConnectoidsByOsmNodeId = new HashMap<TransportLayer,Map<Point, List<DirectedConnectoid>>>();
  
  
  /* TRANSFER ZONE <-> CONNECTOID TRACKING */
  
  /** track mapping from osm stop_area (transfer zone) to connectoids that refer to it (stop_position), track this because planit only tracks the other way around */
  private final Map<TransferZone, List<DirectedConnectoid> > connectoidsByTransferZone = new HashMap<TransferZone, List<DirectedConnectoid>>();
  
  /* OSM <-> TRANSFER ZONE GROUP TRACKING */
  
  /** track mapping from osm stop_area id to the transfer zone group that goes with it on the planit side */
  private final Map<Long, TransferZoneGroup> transferZoneGroupsByOsmId = new HashMap<Long, TransferZoneGroup>();
  
  /* SPATIAL LINK TRACKING */
  
  /** to be able to map stand-alone stations and platforms to connectoids in the network, we must be able to spatially find close by created
   * links, this is what we do here. */
  private Quadtree spatiallyIndexedPlanitLinks = null; 
  
  
  /** initialise based on links in provided network
   * 
   * @param osmNetwork to use
   */
  protected void initialiseSpatiallyIndexedLinks(PlanitOsmNetwork osmNetwork) {
    Collection<Edges<Link>> linksCollection = new ArrayList<Edges<Link>>();
    for(MacroscopicPhysicalLayerImpl layer : osmNetwork.transportLayers) {
      linksCollection.add(layer.links);
    }
    spatiallyIndexedPlanitLinks = PlanitGraphGeoUtils.createSpatiallyIndexedPlanitEdges(linksCollection);
  }
      
        
  /* TRANSFER ZONE RELATED METHODS */    

  /** Collect the potentially incomplete transfer zone by entity type and osm id
   * 
   * @param entityType to collect for (node, way)
   * @param osmEntityId id (node id/way id)
   * @return transfer zone registered, null if not present
   */
  public TransferZone getIncompleteTransferZoneByOsmId(EntityType entityType, long osmEntityId) {
    TransferZone transferZone = getTransferZoneByOsmId(entityType, osmEntityId);
    if(!hasConnectoids(transferZone)) {
      return transferZone;
    }else {
      return null;
    }
  }
  
  /** Collect the complete transfer zone by entity type and osm id
   * 
   * @param entityType to collect for (node, way)
   * @param osmEntityId id (node id/way id)
   * @return transfer zone registered, null if not present
   */
  public TransferZone getCompleteTransferZoneByOsmId(EntityType entityType, long osmEntityId) {
    TransferZone transferZone = getTransferZoneByOsmId(entityType, osmEntityId);
    if(hasConnectoids(transferZone)) {
      return transferZone;
    }else {
      return null;
    }
  }  
  
  /** Find transfer zone either incomplete or complete by osm is
   * @param type OSM entity type
   * @param osmId OSM id of transfer zone
   * @return transfer zone if present as incomplete or complete, null otherwise
   */
  public TransferZone getTransferZoneByOsmId(EntityType type, long osmId) {
    transferZonesByOsmEntityId.putIfAbsent(type, new TreeMap<Long,TransferZone>());
    return transferZonesByOsmEntityId.get(type).get(osmId);
  }  
  

  /** Collect the transfer zones by entity type, unmodifiable
   * 
   * @param entityType to collect for
   * @return available transfer zones by osm id
   * @throws PlanItException thrown if error
   */
  public Collection<TransferZone> getTransferZonesByOsmId(EntityType entityType) throws PlanItException {
    switch (entityType) {
      case Node:
          transferZonesByOsmEntityId.putIfAbsent(entityType, new TreeMap<Long,TransferZone>());
          return Collections.unmodifiableCollection(transferZonesByOsmEntityId.get(entityType).values());
      case Way:
        transferZonesByOsmEntityId.putIfAbsent(entityType, new TreeMap<Long,TransferZone>());
        return Collections.unmodifiableCollection(transferZonesByOsmEntityId.get(entityType).values());
      default:
        throw new PlanItException("Unspported entity type encountered for transfer zone tracked in zoning reader, this shoudn't happen");
    }
  }  
  
  /** Collect the transfer zones by spatial bounding box. Collect all created transfer zones
   * that fall within or intersect with this bounding box. They might or might not have connectoids at this point.
   * 
   * @param boundingBox to identify transfer zones spatially
   * @return list of found transfer zones, caller needs to cast entries to TransferZone type
   */
  public Collection<TransferZone> getTransferZonesSpatially(Envelope boundingBox) {
    
    final Set<TransferZone> correctZones = new HashSet<TransferZone>();
    final PlanitJtsIntersectZoneVisitor<TransferZone> spatialZoneFilterVisitor = 
        new PlanitJtsIntersectZoneVisitor<TransferZone>(PlanitJtsUtils.create2DPolygon(boundingBox), correctZones);          
    
    /* query the spatially indexed entries AND apply the visitor that filteres out false positives due to the coarseness of the quadtrees grid */
    for( Entry<EntityType, Quadtree> entry : transferZonesBySpatialIndex.entrySet()) {
      transferZonesBySpatialIndex.get(entry.getKey()).query(boundingBox, spatialZoneFilterVisitor);
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
  public TransferZone addTransferZoneByOsmId(EntityType entityType, long osmEntityId, TransferZone transferZone) {
    transferZonesByOsmEntityId.putIfAbsent(entityType, new HashMap<Long,TransferZone>());
    transferZonesBySpatialIndex.putIfAbsent(entityType, new Quadtree());    
    
    /* spatial index */
    Envelope transferZoneBoundingBox = transferZone.getEnvelope();
    if(transferZoneBoundingBox == null) {
      LOGGER.warning(String.format("unable to track transfer zone %d while parsing, unknown spatial features, ignored", osmEntityId));
      return null;
    }    
    transferZonesBySpatialIndex.get(entityType).insert(transferZone.getEnvelope(), transferZone);
    
    /* id index */
    return transferZonesByOsmEntityId.get(entityType).put(osmEntityId, transferZone);
  }  
    
  /* CONNECTOID RELATED METHODS */  

  /** collect the registered connectoids indexed by their locations for a given network layer (unmodifiable)
   * 
   * @param networkLayer to use
   * @return registered directed connectoids indexed by location
   */
  public Map<Point, List<DirectedConnectoid>> getDirectedConnectoidsByLocation(MacroscopicPhysicalLayerImpl networkLayer) {
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer,  new HashMap<Point, List<DirectedConnectoid>>());
    return Collections.unmodifiableMap(directedConnectoidsByOsmNodeId.get(networkLayer));
  }
  
  /** Collect the registered connectoids by given locations and network layer (unmodifiable)
   * 
   * @param nodeLocation to verify
   * @param networkLayer to extract from
   * @return found connectoids (if any), otherwise null or empty set
   */
  public List<DirectedConnectoid> getDirectedConnectoidsByLocation(Point nodeLocation, MacroscopicPhysicalLayerImpl networkLayer) {
    return getDirectedConnectoidsByLocation(networkLayer).get(nodeLocation);
  }  
  
  /** Add a connectoid to the registered connectoids indexed by their OSM id
   * 
   * @param networkLayer to register for
   * @param connectoidLocation this connectoid relates to
   * @param connectoid to add
   * @return true when successful, false otherwise
   */
  public boolean addDirectedConnectoidByLocation(MacroscopicPhysicalLayerImpl networkLayer, Point connectoidLocation , DirectedConnectoid connectoid) {
    directedConnectoidsByOsmNodeId.putIfAbsent(networkLayer,  new HashMap< Point, List<DirectedConnectoid>>());
    Map<Point, List<DirectedConnectoid>> connectoidsForLayer = directedConnectoidsByOsmNodeId.get(networkLayer);
    connectoidsForLayer.putIfAbsent(connectoidLocation, new ArrayList<DirectedConnectoid>(1));
    List<DirectedConnectoid> connectoids = connectoidsForLayer.get(connectoidLocation);
    if(!connectoids.contains(connectoid)) {
      return connectoids.add(connectoid);
    }
    return false;
  }
  
  /** Check if any connectoids have been registered for the given location on any layer
   * 
   * @param location to verify
   * @return true when present, false otherwise
   */
  public boolean hasAnyDirectedConnectoidsForLocation(Point location) {
    for( Entry<TransportLayer, Map<Point, List<DirectedConnectoid>>> entry : directedConnectoidsByOsmNodeId.entrySet()) {
      if(hasDirectedConnectoidForLocation(entry.getKey(), location)) {
        return true;
      }
    }
    return false;
  }  
  
  /** Check if any connectoid has been registered for the given location for this layer
   * 
   * @param networkLayer to check for
   * @param point to use
   * @return true when present, false otherwise
   */  
  public boolean hasDirectedConnectoidForLocation(TransportLayer networkLayer, Point point) {
    Map<Point, List<DirectedConnectoid>>  connectoidsForLayer = directedConnectoidsByOsmNodeId.get(networkLayer);
    return connectoidsForLayer != null && connectoidsForLayer.get(point) != null && !connectoidsForLayer.get(point).isEmpty();
  }  
  
  /** Register a known mapping from transfer zone to connectoid
   * 
   * @param transferZone to map to...
   * @param connectoid ...this connectoid
   */
  public void addConnectoidByTransferZone(TransferZone transferZone, DirectedConnectoid connectoid) {    
    connectoidsByTransferZone.putIfAbsent(transferZone, new ArrayList<DirectedConnectoid>(1));
    List<DirectedConnectoid> connectoids = connectoidsByTransferZone.get(transferZone);
    if(!connectoids.contains(connectoid)) {
      connectoids.add(connectoid);
    }
  }  
  
  /** Verify if transfer zone has connectoids present
   * 
   * @param transferZone to check for
   * @return true when there exist connectoids that reference this transfer zone, false otherwise
   */
  public boolean hasConnectoids(TransferZone transferZone) {
    return getConnectoidsByTransferZone(transferZone) != null && !getConnectoidsByTransferZone(transferZone).isEmpty();
  }
  
  /** Collect transfer zone's registered connectoids
   * 
   * @param transferZone to map to...
   * @return connectoids found
   */
  public Collection<DirectedConnectoid> getConnectoidsByTransferZone(TransferZone transferZone) {
    if(transferZone == null) {
      return null;
    }
    connectoidsByTransferZone.putIfAbsent(transferZone, new ArrayList<DirectedConnectoid>(1));
    return connectoidsByTransferZone.get(transferZone);
  }  
  
  /* TRANSFER ZONE GROUP RELATED METHODS */  
  
  /** collect a parsed transfer zone group by OSM id
   * @param osmId to use
   * @return transfer zone group
   */
  public TransferZoneGroup getTransferZoneGroupByOsmId(long osmId) {
    return transferZoneGroupsByOsmId.get(osmId);
  }  
  
  /** Add a transfer zone group by its OSM id
   * 
   * @param osmId to use
   * @param transferZoneGroup group to add
   * @return group in container location before this one was added, null if none existed
   */
  public TransferZoneGroup addTransferZoneGroupByOsmId(long osmId, TransferZoneGroup transferZoneGroup) {
    return transferZoneGroupsByOsmId.put(osmId, transferZoneGroup);
  }

  /**
   * Reset the PLANit data tracking containers
   */
  public void reset() {
    transferZonesByOsmEntityId.clear();
    directedConnectoidsByOsmNodeId.clear();     
    connectoidsByTransferZone.clear();
    spatiallyIndexedPlanitLinks = new Quadtree();
  }

  /* SPATIAL LINK INDEX RELATED METHODS */
    
  /** Remove provided links from local spatial index based on links
   * 
   * @param links to remove
   */
  public void removeLinksFromSpatialLinkIndex(Collection<Link> links) {
    if(links != null) {
      links.forEach( link -> spatiallyIndexedPlanitLinks.remove(link.getEnvelope(), link));
    }
  }  
  
  /** Add provided links to local spatial index based on their bounding box
   * 
   * @param links to add
   */  
  public void addLinksToSpatialLinkIndex(Collection<Link> links) {
    if(links != null) {
      links.forEach( link -> spatiallyIndexedPlanitLinks.insert(link.getEnvelope(), link));
    }
  }   
    
  /** Find links spatially based on the provided bounding box
   * 
   * @param searchBoundingBox to use
   * @return links found intersecting or within bounding box provided
   */
  public Collection<Link> findLinksSpatially(Envelope searchBoundingBox) {
    return PlanitGraphGeoUtils.<Link>findEdgesSpatially(searchBoundingBox,spatiallyIndexedPlanitLinks);    
  }
  

}

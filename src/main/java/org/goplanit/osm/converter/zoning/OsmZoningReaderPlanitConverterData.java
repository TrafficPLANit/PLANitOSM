package org.goplanit.osm.converter.zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import org.goplanit.converter.zoning.ZoningConverterCommonData;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsIntersectZoneVisitor;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneGroup;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * Data specifically required in the zoning reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class OsmZoningReaderPlanitConverterData extends ZoningConverterCommonData {
  
  /** logeger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningReaderData.class.getCanonicalName());
    
  /* OSM <-> TRANSFER ZONE TRACKING */
  
  /** track created transfer zones by their osm id that were extracted from an OsmNode or way (osm id is key) */
  private final Map<EntityType, Map<Long, TransferZone>> transferZonesByOsmEntityId = new TreeMap<>();

  /** track transfer zone OSM layer index, if absent it is expected to reflect default layer of 0 */
  private final Map<TransferZone, Integer> transferZonesOsmLayerIndex = new TreeMap<>();
  
  /** in addition to tracking transfer zones by their Osm entity id, we also track them spatially, to be able to map
   * them to close by stop positions if needed */
  private final Map<EntityType, Quadtree> transferZonesBySpatialIndex = new TreeMap<>();

  /* OSM <-> TRANSFER ZONE GROUP TRACKING */
  
  /** track mapping from osm stop_area id to the transfer zone group that goes with it on the planit side */
  private final Map<Long, TransferZoneGroup> transferZoneGroupsByOsmId = new HashMap<>();

  /**
   * Constructor
   * @param network to use
   * @param zoning to use
   */
  public OsmZoningReaderPlanitConverterData(MacroscopicNetwork network, Zoning zoning){
    super(network, zoning);
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
    if(!getConnectoidData().hasConnectoids(transferZone)) {
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
    if(getConnectoidData().hasConnectoids(transferZone)) {
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
   */
  public SortedSet<TransferZone> getTransferZonesByOsmId(EntityType entityType) {
    transferZonesByOsmEntityId.putIfAbsent(entityType, new TreeMap<>());
    switch (entityType) {
      case Node:
      case Way:
          return new TreeSet<>(transferZonesByOsmEntityId.get(entityType).values());
      default:
        throw new PlanItRunTimeException(
            "Unsupported entity type encountered for transfer zone tracked in zoning reader, this shouldn't happen");
    }
  }  
  
  /** Collect the transfer zones by spatial bounding box. Collect all created transfer zones
   * that fall within or intersect with this bounding box. They might or might not have connectoids at this point.
   * 
   * @param boundingBox to identify transfer zones spatially
   * @return list of found transfer zones, caller needs to cast entries to TransferZone type
   */
  public Collection<TransferZone> getTransferZonesSpatially(Envelope boundingBox) {
    
    final Set<TransferZone> correctZones = new HashSet<>();
    final PlanitJtsIntersectZoneVisitor<TransferZone> spatialZoneFilterVisitor =
            new PlanitJtsIntersectZoneVisitor<>(PlanitJtsUtils.create2DPolygon(boundingBox), correctZones);
    
    /* query the spatially indexed entries AND apply the visitor that filters out false positives due to the
     coarseness of the quadtrees grid */
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
    transferZonesByOsmEntityId.putIfAbsent(entityType, new HashMap<>());
    transferZonesBySpatialIndex.putIfAbsent(entityType, new Quadtree());    
    
    /* spatial index */
    Envelope transferZoneBoundingBox = transferZone.getEnvelope();
    if(transferZoneBoundingBox == null) {
      LOGGER.warning(String.format("unable to track transfer zone %d while parsing, unknown spatial features, " +
          "ignored", osmEntityId));
      return null;
    }    
    transferZonesBySpatialIndex.get(entityType).insert(transferZone.getEnvelope(), transferZone);
    
    /* id index */
    return transferZonesByOsmEntityId.get(entityType).put(osmEntityId, transferZone);
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
  }

  /**
   * Given a transfer zone and the OSM entity it is based on (including tags), we register its vertical layer index if
   * explicitly tagged. Used to filter eligible road/rail infrastructure when mapping waiting areas (transfer zones) to
   * the network via connectoids
   *
   * @param transferZone to extract layer information for
   * @param osmEntity the OSM entity the transfer zone is based on
   * @param tags to extract the layer information from
   */
  public void registerTransferZoneOsmVerticalLayerIndex(
      TransferZone transferZone, OsmEntity osmEntity, Map<String, String> tags) {

    if(transferZonesOsmLayerIndex.containsKey(transferZone)){
      LOGGER.warning(String.format("Layer index already registered for transfer zone %s, this shouldn't happen",
          transferZone.getIdsAsString()));
    }

    if(!OsmTagUtils.containsAnyKey(tags, OsmTags.LAYER)){
      /* no layer tag, so default applies, which we do not explicitly store */
      return;
    }

    var layerValue = OsmTagUtils.getValueAsInt(tags, OsmTags.LAYER);
    if(layerValue != null) {
      transferZonesOsmLayerIndex.put(transferZone, layerValue);
    }
  }

  /**
   * Collect vertical layer index for this transfer zone
   *
   * @param transferZone to collect layer index for
   * @return found layer index, when nothing is registered, null is returned, this may indicate the default level
   * or absence of information that should be obtained otherwise and does not reflect the default layer
   */
  public Integer getTransferZoneOsmVerticalLayerIndex(TransferZone transferZone) {
    return transferZonesOsmLayerIndex.get(transferZone);
  }
}

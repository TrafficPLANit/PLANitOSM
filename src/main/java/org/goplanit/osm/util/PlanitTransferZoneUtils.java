package org.goplanit.osm.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.goplanit.osm.tags.OsmPtv1Tags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneGroup;
import org.goplanit.utils.zoning.TransferZoneType;
import org.goplanit.utils.zoning.Zone;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LinearLocation;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Static helper methods to support parsing/functionality of PLANit TransferZones
 * 
 * @author markr
 *
 */
public class PlanitTransferZoneUtils {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitTransferZoneUtils.class.getCanonicalName());
  
  /** to be able to retain the supported osm modes on a planit transfer zone, we place tham on the zone as an input property under this key.
   *  This avoids having to store all osm tags, while still allowing to leverage the information in the rare cases it is needed when this information is lacking
   *  on stop_positions that use this transfer zone
   */
  private static final String TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY = "osmmodes";  
  
  /** When known, transfer zones are provided with a station name extracted from the osm station entity (if possible). Its name is stored under
   * this key as input property
   */
  private static final String TRANSFERZONE_STATION_INPUT_PROPERTY_KEY = "station";    

  /** Find the zone closest to the passed in OSM Entity
   * 
   * @param osmEntity to find closest zone for
   * @param zones to check against
   * @param osmNodes to extract geo information from if needed
   * @param geoUtils used to compute distances
   * @return closest zone found
   * @throws PlanItException thrown if error
   */
  private static Zone findZoneClosest(OsmEntity osmEntity, Collection<? extends Zone> zones, Map<Long,OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    EntityType type = Osm4JUtils.getEntityType(osmEntity);
    switch (type) {
    case Node:
      return OsmNodeUtils.findZoneClosest((OsmNode)osmEntity, zones, geoUtils);
    case Way:
      return OsmWayUtils.findZoneClosest((OsmWay)osmEntity, zones, osmNodes, geoUtils);      
    default:
      LOGGER.warning(String.format("unsupported osm entity type when finding closest zone to %d",osmEntity.getId()));
      break;
    }
    return null;
  }

  /** Set the station name for a transfer zone
   * 
   * @param transferZone to use
   * @param stationName to set
   */
  private static void  setTransferZoneStationName(TransferZone transferZone, String stationName) {
    transferZone.addInputProperty(TRANSFERZONE_STATION_INPUT_PROPERTY_KEY, stationName);
  }

  /** Verify if the transfer zone has a station name set
   * 
   * @param transferZone to verify
   * @return true when present, false otherwise
   */  
  private static boolean hasTransferZoneStationName(TransferZone transferZone) {
    return getTransferZoneStationName(transferZone) != null;
  }
  
  /** Find the zone (within any of the transfer zone groups) closest to the passed in OSM Entity
   * 
   * @param osmEntity to find closest zone for
   * @param transferZoneGroups to check against
   * @param osmNodes to extract geo information from if needed
   * @param geoUtils used to compute distances
   * @return closest zone found
   * @throws PlanItException thrown if error
   */
  public static TransferZone findTransferZoneClosestByTransferGroup(
      OsmEntity osmEntity, 
      Collection<? extends TransferZoneGroup> transferZoneGroups, 
      Map<Long,OsmNode> osmNodes, 
      PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    Set<TransferZone> closestPerGroup = new HashSet<TransferZone>();
    for(TransferZoneGroup group : transferZoneGroups) {
      TransferZone closestOfGroup = (TransferZone) findZoneClosest(osmEntity, group.getTransferZones(), osmNodes, geoUtils);
      closestPerGroup.add(closestOfGroup);
    }
    /* now find closest across all groups */
    return (TransferZone) findZoneClosest(osmEntity, closestPerGroup, osmNodes, geoUtils);
  }   

  /** Verify if the geometry of the transfer zone equates to the provided location
   * @param transferZone to verify
   * @param location to verify against
   * @return true when residing at the exact same location at the reference location, false otherwise
   * @throws PlanItException thrown if error
   */
  public static boolean isTransferZoneAtLocation(TransferZone transferZone, Point location) throws PlanItException {
    PlanItException.throwIfNull(transferZone, "Transfer zone is null, unable to verify location");
      
    if(transferZone.hasCentroid() && transferZone.getCentroid().hasPosition()) {
      return location.equals(transferZone.getCentroid().getPosition());
    }else if(transferZone.hasGeometry()) {
      if(transferZone.getGeometry() instanceof Point) {
        return location.equals(transferZone.getGeometry());
      }
    }else { 
      throw new PlanItException("Transferzone representing platform/pole %s has no valid geometry attached, unable to verify location", transferZone.getExternalId());
    }
      
    return false;
  }

  /** Verify of the transfer zone resides left of the line coordA to coordB
   * 
   * @param transferZone to check
   * @param coordA of line 
   * @param coordB of line
   * @param geoUtils to use
   * @return true when left, false otherwise
   * @throws PlanItException thrown if error
   */
  public static boolean isTransferZoneLeftOf(TransferZone transferZone, Coordinate coordA, Coordinate coordB, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    Geometry transferzoneGeometry = null; 
    if(transferZone.hasCentroid() && transferZone.getCentroid().hasPosition()) {
      transferzoneGeometry = transferZone.getCentroid().getPosition();
    }else if(transferZone.hasGeometry()) {
      transferzoneGeometry = transferZone.getGeometry();
    }else { 
      throw new PlanItException("Transferzone representing platform/pole %s has no valid geometry attached, unable to determine on which side of line AB (%s, %s) is resides", transferZone.getExternalId(), coordA.toString(), coordB.toString());
    }
    
    return geoUtils.isGeometryLeftOf(transferzoneGeometry, coordA, coordB);   
  }

  /** Find the access link segments ineligible given the intended location of the to be created connectoid, the transfer zone provided, and the access mode.
   * When transfer zone location differs from the connectoid location determine on which side of the infrastructure it exists and based on the country's driving direction
   * and access mode determine the access link segments
   * 
   * @param accessLinkSegments to filter
   * @param transferZone to create connectoid(s) for
   * @param planitMode that is accessible
   * @param leftHandDrive is infrastructure left hand drive or not
   * @param geoUtils to use for determining geographic eligibility
   * @return ineligible link segments to be access link segments for connectoid at this location
   * @throws PlanItException thrown if error
   */
  public static Collection<EdgeSegment> identifyInvalidTransferZoneAccessLinkSegmentsBasedOnRelativeLocationToInfrastructure(
      final Collection<EdgeSegment> accessLinkSegments, final TransferZone transferZone, final Mode planitMode, boolean leftHandDrive, final PlanitJtsCrsUtils geoUtils) throws PlanItException {                    
    
    Collection<EdgeSegment> invalidAccessLinkSegments = new ArrayList<EdgeSegment>(accessLinkSegments.size());
    /* use line geometry closest to connectoid location */
    for(EdgeSegment linkSegment : accessLinkSegments) {
      LineSegment finalLineSegment = PlanitGraphGeoUtils.extractClosestLineSegmentTo(transferZone.getGeometry(), linkSegment, geoUtils);
      /* determine location relative to infrastructure */
      boolean isTransferZoneLeftOfInfrastructure = isTransferZoneLeftOf(transferZone, finalLineSegment.p0, finalLineSegment.p1, geoUtils);      
      if(isTransferZoneLeftOfInfrastructure!=leftHandDrive) {
        /* not viable opposite traffic directions needs to be crossed on the link to get to stop location --> remove */
        invalidAccessLinkSegments.add(linkSegment);
      }
    }    
            
    return invalidAccessLinkSegments;
  }
  
  /** process an osm entity that is classified as a (train) station. For this to register on the transfer zone, we try to utilise its name and use it for the zone
   * name if it is empty. We also record it as an input property for future reference, e.g. key=station and value the name of the osm station
   *   
   * @param transferZone the osm station relates to 
   * @param tags of the osm entity representation a station
   */  
  public static void updateTransferZoneStationName(TransferZone transferZone, Map<String, String> tags) {
    
    String stationName = tags.get(OsmTags.NAME);
    if(!transferZone.hasName()) {      
      if(stationName!=null) {
        transferZone.setName(stationName);
      }
    }
    /* only set when not already set, because when already set it is likely the existing station name is more accurate */
    if(!hasTransferZoneStationName(transferZone)) {
      setTransferZoneStationName(transferZone, stationName);
    }
  }

  /** While PLANit does not require access modes on transfer zones because it is handled by connectoids, OSM stop_positions (connectoids) might lack the required
   * tagging to identify their mode access in which case we revert to the related transfer zone to deduce it. Therefore, we store OSM mode information on a transfer zone
   * via the generic input properties to be able to retrieve it if needed later
   * 
   * @param transferZone to use
   * @param eligibleOsmModes to add
   */
  public static void registerOsmModesOnTransferZone(final TransferZone transferZone, Collection<String> eligibleOsmModes) {
    if(transferZone != null && eligibleOsmModes!= null) {
      /* register identified eligible access modes */
      transferZone.addInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY, eligibleOsmModes);
    }
  }

  /** Collect any prior registered eligible OSM modes on a PLANit transfer zone (unmodifiable)
   * 
   * @param transferZone to collect from
   * @return eligible OSM modes, null if none
   */
  @SuppressWarnings("unchecked")
  public static Collection<String> getRegisteredOsmModesForTransferZone(final TransferZone transferZone){
    Collection<String> eligibleOsmModes = (Collection<String>) transferZone.getInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY);
    if(eligibleOsmModes != null)
    {
      return Collections.unmodifiableCollection(eligibleOsmModes);
    }
    return null;
  }
  
  /** Collect the station name for a transfer zone (if any)
   * 
   * @param transferZone to collect for
   * @return station name
   */
  public static String getTransferZoneStationName(TransferZone transferZone) {
    return (String)transferZone.getInputProperty(TRANSFERZONE_STATION_INPUT_PROPERTY_KEY);
  }

  /** Extract the OSM entity type from a PLANit Transfer zone
   * 
   * @param transferZone to identify entity type for
   * @return the entity type
   * @throws PlanItException thrown if error
   */
  public static EntityType extractOsmEntityType(TransferZone transferZone) throws PlanItException {
    if( transferZone.getGeometry() instanceof Point) {
      return EntityType.Node;
    }else if(transferZone.getGeometry() instanceof Polygon || transferZone.getGeometry() instanceof LineString) {
      return EntityType.Way;
    }else {
      throw new PlanItException("unknown geometry type encountered for transfer zone (osm id %s)",transferZone.getExternalId());
    }
  }

  /** Find the linear location reflecting the closest projected location between the transfer zone and link geometries. For the transfer zone geometry we use existing coordinates
   * rather than projected ones
   * 
   * @param transferZone to use
   * @param accessEdge to use
   * @param geoUtils to use
   * @return closest projected linear location on link geometry
   * @throws PlanItException thrown if error
   */
  public static LinearLocation extractClosestProjectedLinearLocationOnEdgeForTransferZone(TransferZone transferZone, Edge accessEdge, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    LinearLocation projectedLinearLocationOnLink = null;
    EntityType transferZoneGeometryType = PlanitTransferZoneUtils.extractOsmEntityType(transferZone);
    if(transferZoneGeometryType.equals(EntityType.Node)) {
      projectedLinearLocationOnLink = geoUtils.getClosestProjectedLinearLocationOnGeometry((Point)transferZone.getGeometry(),accessEdge.getGeometry());
    }else if (transferZoneGeometryType.equals(EntityType.Way)){
      projectedLinearLocationOnLink = geoUtils.getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(transferZone.getGeometry(),accessEdge.getGeometry());
    }else {
      throw new PlanItException("Unsupported osm entity type encountered for transfer zone (osm waiting area %s)",transferZone.getExternalId());
    }
    return projectedLinearLocationOnLink;    
  }  
  
  /** Collect the transfer zone type based on the tags
   * 
   * @param osmNode node 
   * @param tags tags of the node
   * @return transfer zone type, unknown if not able to map 
   */
  public static TransferZoneType extractTransferZoneTypeFromPtv1Tags(OsmNode osmNode, Map<String, String> tags) {
    if(OsmPtv1Tags.isBusStop(tags)) {
      return TransferZoneType.POLE;
    }else if(OsmPtv1Tags.isTramStop(tags) ) {
      return TransferZoneType.PLATFORM;
    }else if(OsmPtv1Tags.isHalt(tags)) {
      return TransferZoneType.SMALL_STATION;
    }else if(OsmPtv1Tags.isStation(tags)) {
      return TransferZoneType.STATION;
    }else {
      LOGGER.severe(String.format("unable to map node %d to Ptv1 transferzone type", osmNode.getId()));
      return TransferZoneType.UNKNOWN;
    }
  }  

}

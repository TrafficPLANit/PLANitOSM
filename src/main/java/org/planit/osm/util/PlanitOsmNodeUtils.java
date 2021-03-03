package org.planit.osm.util;

import java.util.Collection;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.Edge;
import org.planit.utils.misc.Pair;
import org.planit.utils.zoning.Zone;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Utilities in relation to parsing osm nodes while constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class PlanitOsmNodeUtils {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNodeUtils.class.getCanonicalName());
  
  /** find the closest distance to the node for some planit entity with a supported geometry from the provided collection.
   * This method computes the actual distance between any location on any line segment of the (outer) boundary
   * of the planit entities geometry (or its point location if no polygon/linestring is available) and the reference node and it is therefore very precise. 
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   * 
   * @param osmId reference to where point originated from
   * @param osmNode reference node location
   * @param planitEntities to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param geoUtils to compute projected distances
   * @return planitEntity closest and distance in meters, null if none matches criteria
   * @throws PlanItException thrown if error
   */  
  static <T> Pair<T, Double> findPlanitEntityClosest(OsmNode osmNode, Collection<? extends T> planitEntities, double maxDistanceMeters, PlanitJtsUtils geoUtils) throws PlanItException {
    double minDistanceMeters = Double.POSITIVE_INFINITY;
    double distanceMeters = minDistanceMeters;    
    T closestEntity = null;         
    for(T entity : planitEntities) {
      /* supported planit entity types */
      if(entity instanceof Zone) {
        distanceMeters = getDistanceToZone(osmNode, (Zone)entity, geoUtils);
      }else if(entity instanceof Edge) {
        distanceMeters = getDistanceToEdge(osmNode, (Edge)entity, geoUtils);
      }else {
        LOGGER.warning(String.format("unsupported planit entity to compute closest distance to %s",entity.getClass().getCanonicalName()));
      }      
     
      if(distanceMeters < minDistanceMeters) {
        minDistanceMeters = distanceMeters;
        if(minDistanceMeters < maxDistanceMeters) {
          closestEntity = entity;
        }
      }      
    }
    
    if(closestEntity!=null) {
      return Pair.of(closestEntity, minDistanceMeters);
    }
    return null;
  }
  
  
  /** find the distance from the zone to the node. This method computes the actual distance between any location on any line segment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and the reference point and it is therefore very precise. 
   * 
   * @param osmNode used
   * @param zone to check against using its geometry
   * @param geoUtils to compute projected distances
   * @return distance to zone, if not possible to compute positive infinity is returned
   * @throws PlanItException thrown if error
   */
  public static double getDistanceToZone(final OsmNode osmNode, final Zone zone, final PlanitJtsUtils geoUtils) throws PlanItException {
    Point point = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getX(osmNode), PlanitOsmNodeUtils.getY(osmNode));    
    if(zone.hasGeometry()) {
      return geoUtils.getClosestDistanceInMeters(point,zone.getGeometry());
    }else if(zone.getCentroid().hasPosition()) {
      return geoUtils.getDistanceInMetres(point.getCoordinate(), zone.getCentroid().getPosition().getCoordinate());
    }else {
      LOGGER.warning(String.format("zone has no geographic information to determine closesness to osm entity %d",osmNode.getId()));
    }
    return Double.POSITIVE_INFINITY;
  }
  
  /** find the distance from the edge to the point. This method computes the actual distance between any location on any line segment of the edge 
   * and the reference node and it is therefore very precise. 
   * 
   * @param osmNode used
   * @param edge to check against using its geometry
   * @param geoUtils to compute projected distances
   * @return distance to edge, if not possible to compute positive infinity is returned
   * @throws PlanItException thrown if error
   */
  static double getDistanceToEdge(final OsmNode osmNode, final Edge edge, final PlanitJtsUtils geoUtils) throws PlanItException {
    Point point = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getX(osmNode), PlanitOsmNodeUtils.getY(osmNode));
    if(edge.hasGeometry()) {
      return geoUtils.getClosestDistanceInMeters(point,edge.getGeometry());
    }else {
      LOGGER.warning(String.format("edge has no geographic information to determine closesness to osm entity %d",osmNode.getId()));
    }
    return Double.POSITIVE_INFINITY;    
  }  
  
  /** collect the coordinate from the osm node information
   * @param osmNode
   * @return
   */
  public static Coordinate getCoordinate(OsmNode osmNode) {
    return new Coordinate(getX(osmNode), getY(osmNode));
  }
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getX(final OsmNode osmNode) {
    return osmNode.getLongitude();
  }
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getY(final OsmNode osmNode) {
    return osmNode.getLatitude();
  }

  /** identical to findZoneWithClosest coordinate that requires a maximum search distance. Here this distance is set to inifinite
   * 
   * @param osmNode reference
   * @param zones to check against
   * @param geoUtils to compute distance
   * @return zone with the geometry coordinate (or centroid) closest to the osmNode
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneWithClosestCoordinateToNode(OsmNode osmNode, Collection<? extends Zone> zones, PlanitJtsUtils geoUtils) throws PlanItException {
    return findZoneWithClosestCoordinateToNode(osmNode, zones, Double.POSITIVE_INFINITY, geoUtils);
  }   
  
  /** find the closest zone to the node location. Note that this method is NOT perfect, it utilises the closest coordinate on
   * the geometry of the zone, but it is likely the closest point lies on a line of the geometry rather than an extreme point. Therefore
   * it is possible that the found zone is not actually closest. So use with caution!
   * 
   * @param osmNode reference
   * @param zones to check against
   * @param geoUtils to compute distance
   * @param maxDistanceMeters maximum allowable distance to search for
   * @return zone with the geometry coordinate (or centroid) closest to the osmNode
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneWithClosestCoordinateToNode(OsmNode osmNode, Collection<? extends Zone> zones, double maxDistanceMeters, PlanitJtsUtils geoUtils) throws PlanItException {
    Zone closestZone = null; 
    double minDistanceMeters = Double.POSITIVE_INFINITY;    
    Point point = PlanitJtsUtils.createPoint(getX(osmNode), getY(osmNode));
    for(Zone zone : zones) {
      double distanceMeters = Double.POSITIVE_INFINITY;
      if(zone.hasGeometry()) {
        Geometry zoneGeometry = zone.getGeometry();
        distanceMeters = geoUtils.getClosestExistingCoordinateDistanceInMeters(point,zoneGeometry);
      }else if(zone.getCentroid().hasPosition()) {
        distanceMeters = geoUtils.getDistanceInMetres(point.getCoordinate(), zone.getCentroid().getPosition().getCoordinate());
      }else {
        LOGGER.warning(String.format("zone has no geographic information to determine closesness to osm node %d",osmNode.getId()));
      }
      
      /* update if closer */
      if(distanceMeters < minDistanceMeters) {
        minDistanceMeters = distanceMeters;
        if(minDistanceMeters < maxDistanceMeters) {
          closestZone = zone;
        }
      }
    }
    return closestZone;
  }
  
  /** find the closest zone to the node location. This method computes the actual distance between any location on any line segment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and the reference point and it therefore very precise
   * 
   * 
   * @param osmNode reference node
   * @param zones to check against using their geometries
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosest(OsmNode osmNode, Collection<? extends Zone> zones, PlanitJtsUtils geoUtils) throws PlanItException {
    return findZoneClosest(osmNode, zones, Double.POSITIVE_INFINITY, geoUtils);    
  }  

  /** find the closest zone to the node location. This method computes the actual distance between any location on any line segment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and the reference point and it is therefore very precise. 
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   * 
   * @param osmNode reference node
   * @param zones to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosest(final OsmNode osmNode, final Collection<? extends Zone> zones, double maxDistanceMeters, final PlanitJtsUtils geoUtils) throws PlanItException {        
    Pair<Zone,Double> result = findPlanitEntityClosest(osmNode, zones, maxDistanceMeters, geoUtils);
    if(result!=null) {
      return result.first();
    }
    return null;
  }
  
  /** create a coordinate at the location that represents the closest point between the osmNode and the passed in geometry
   * 
   * @param osmNode reference node
   * @param geometry geometry to find closest location to node on
   * @param geoUtils used for computing the distances
   * @return projected coordinate
   * @throws PlanItException thrown if error
   */
  public static Coordinate findClosestProjectedCoordinateTo(OsmNode osmNode, LineString geometry, PlanitJtsUtils geoUtils) throws PlanItException {
    Point osmNodeLocation = PlanitJtsUtils.createPoint(getX(osmNode), getY(osmNode));
    return geoUtils.getClosestProjectedCoordinateTo(osmNodeLocation, geometry);
  }  
  
  /** find the closest link to the node location. This method computes the actual distance between any location on any line segment of the geometry of the link
   * and the reference point (osm node) and it is therefore very precise.
   * 
   * 
   * @param osmNode reference node
   * @param zones to check against using their geometries
   * @param geoUtils to compute projected distances
   * @return edge closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Edge findEdgeClosest(OsmNode osmNode, Collection<? extends Edge> edges, PlanitJtsUtils geoUtils) throws PlanItException {
    return findEdgeClosest(osmNode, edges, Double.POSITIVE_INFINITY, geoUtils);    
  }    
  
  /** find the closest edge to the node location. This method computes the actual distance between any location on any line segment of geometry
   * of the link and the reference point (osm node) and it therefore very precise. A cap is placed on how far a zone is allowed to be to still be regarded as closest
   * via maxDistanceMeters.
   * 
   * @param osmNode reference node
   * @param zones to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param geoUtils to compute projected distances
   * @return edge closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */  
  public static Edge findEdgeClosest(OsmNode osmNode, Collection<? extends Edge> edges, double maxDistanceMeters, PlanitJtsUtils geoUtils) throws PlanItException {
    Pair<Edge,Double> result = findPlanitEntityClosest(osmNode, edges, maxDistanceMeters, geoUtils);
    if(result!=null) {
      return result.first();
    }
    return null;
  }    
  
  /** create a (Rectangular) bounding box around the osm node geometry based on the provided offset
   * 
   * @param osmNode to create bounding box around
   * @param offsetInMeters of the bounding box with node location in centre
   * @param geoUtils to properly create bounding box based on crs
   * @return bounding box
   */
  public static Envelope createBoundingBox(OsmNode osmNode, double offsetInMeters, PlanitJtsUtils geoUtils) {
    double xCoord = PlanitOsmNodeUtils.getX(((OsmNode)osmNode));
    double yCoord = PlanitOsmNodeUtils.getY(((OsmNode)osmNode));
    return geoUtils.createBoundingBox(xCoord, yCoord, offsetInMeters);
  }

}

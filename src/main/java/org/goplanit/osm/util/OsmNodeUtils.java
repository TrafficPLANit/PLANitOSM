package org.goplanit.osm.util;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.zoning.Zone;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import de.topobyte.osm4j.core.model.iface.OsmNode;

import static org.goplanit.utils.geo.PlanitEntityGeoUtils.findPlanitEntityClosest;

/**
 * Utilities in relation to parsing OSM nodes while constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class OsmNodeUtils {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmNodeUtils.class.getCanonicalName());

  /** Collect the coordinate from the osm node information
   * 
   * @param osmNode to use
   * @return created coordinate
   */
  public static Coordinate createCoordinate(OsmNode osmNode) {
    return new Coordinate(getX(osmNode), getY(osmNode));
  }
  
  /** Create a point from the node
   * 
   * @param osmNode to create point for
   * @return point created
   */
  public static Point createPoint(OsmNode osmNode) {
    return PlanitJtsUtils.createPoint(createCoordinate(osmNode));
  }  
  
  /** Create a point based on the OSMnode
   *  
   * @param osmNodeId to create point for
   * @param osmNodes to collect node from
   * @return created node, or null of osmNodeId is unknown is passed in osmNodes
   * @throws PlanItException thrown if error
   */  
  public static Point createPoint(long osmNodeId, Map<Long, OsmNode> osmNodes) throws PlanItException {
    OsmNode osmNode = osmNodes.get(osmNodeId);
    if(osmNode != null) {
      return createPoint(osmNode);
    }
    return null;
  }  
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getX(final OsmNode osmNode) {
    if(osmNode == null){
      int bla = 4;
    }
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
  public static Zone findZoneWithClosestCoordinateToNode(OsmNode osmNode, Collection<? extends Zone> zones, PlanitJtsCrsUtils geoUtils) throws PlanItException {
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
  public static Zone findZoneWithClosestCoordinateToNode(OsmNode osmNode, Collection<? extends Zone> zones, double maxDistanceMeters, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    Zone closestZone = null; 
    double minDistanceMeters = Double.POSITIVE_INFINITY;    
    Point point = PlanitJtsUtils.createPoint(getX(osmNode), getY(osmNode));
    for(Zone zone : zones) {
      double distanceMeters = Double.POSITIVE_INFINITY;
      if(zone.hasGeometry()) {
        Geometry zoneGeometry = zone.getGeometry();
        distanceMeters = geoUtils.getClosestExistingCoordinateDistanceInMeters(point.getCoordinate(),zoneGeometry);
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
   */
  public static Zone findZoneClosest(OsmNode osmNode, Collection<? extends Zone> zones, PlanitJtsCrsUtils geoUtils) {
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
   */
  public static Zone findZoneClosest(final OsmNode osmNode, final Collection<? extends Zone> zones, double maxDistanceMeters, final PlanitJtsCrsUtils geoUtils) {
    Pair<Zone,Double> result = findPlanitEntityClosest(OsmNodeUtils.createCoordinate(osmNode), zones, maxDistanceMeters, geoUtils);
    if(result!=null) {
      return result.first();
    }
    return null;
  }
  
  /** Create a coordinate at the location that represents the closest point between the osmNode and the passed in geometry
   * 
   * @param osmNode reference node
   * @param geometry geometry to find closest location to node on
   * @param geoUtils used for computing the distances
   * @return projected coordinate
   */
  public static Coordinate findClosestProjectedCoordinateTo(OsmNode osmNode, LineString geometry, PlanitJtsCrsUtils geoUtils){
    return geoUtils.getClosestProjectedCoordinateOnLineString( OsmNodeUtils.createCoordinate(osmNode), geometry);
  }  
  
  /** Find the closest link to the node location. This method computes the actual distance between any location on any line segment of the geometry of the link
   * and the reference point (OSM node) and it is therefore very precise.
   * 
   * 
   * @param osmNode reference node
   * @param edges to check against using their geometries
   * @param geoUtils to compute projected distances
   * @return edge closest, null if none matches criteria
   */
  public static Edge findEdgeClosest(OsmNode osmNode, Collection<? extends Edge> edges, PlanitJtsCrsUtils geoUtils){
    return findEdgeClosest(osmNode, edges, Double.POSITIVE_INFINITY, geoUtils);    
  }    
  
  /** Find the closest edge to the node location. This method computes the actual distance between any location on any line segment of geometry
   * of the link and the reference point (OSM node) and it therefore very precise. A cap is placed on how far a zone is allowed to be to still be regarded as closest
   * via maxDistanceMeters.
   * 
   * @param osmNode reference node
   * @param edges to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param geoUtils to compute projected distances
   * @return edge closest, null if none matches criteria
   */
  public static Edge findEdgeClosest(OsmNode osmNode, Collection<? extends Edge> edges, double maxDistanceMeters, PlanitJtsCrsUtils geoUtils){
    Pair<Edge,Double> result = findPlanitEntityClosest( OsmNodeUtils.createCoordinate(osmNode), edges, maxDistanceMeters, geoUtils);
    if(result!=null) {
      return result.first();
    }
    return null;
  }    
  

  /** find (first) node who's location coincides with the provided coordinate from the collection of eligible nodes passed in
   * @param coordinate to match
   * @param osmNodes to match against
   * @return found node that matches, null if no match found
   */
  public static OsmNode findOsmNodeWithCoordinate2D(Coordinate coordinate, Collection<OsmNode> osmNodes) {
    for(OsmNode osmNode : osmNodes) {
      if(nodeLocationEquals2D(osmNode, coordinate)) {
        return osmNode;
      }
    }
    return null;
  }


  /** Verify if the location of the provided coordinate equals the node's location
   * @param osmNode osmNode to check
   * @param coordinate to check against
   * @return true when a match, false otherwise
   */
  public static boolean nodeLocationEquals2D(OsmNode osmNode, Coordinate coordinate) {
    return createCoordinate(osmNode).equals2D(coordinate);
  }



}

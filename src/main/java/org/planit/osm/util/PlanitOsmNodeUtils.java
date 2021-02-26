package org.planit.osm.util;

import java.util.Collection;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
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
  
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getXCoordinate(final OsmNode osmNode) {
    return osmNode.getLongitude();
  }
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getYCoordinate(final OsmNode osmNode) {
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
    Point point = PlanitJtsUtils.createPoint(getXCoordinate(osmNode), getYCoordinate(osmNode));
    for(Zone zone : zones) {
      double distanceMeters = Double.POSITIVE_INFINITY;
      if(zone.hasGeometry()) {
        Geometry zoneGeometry = zone.getGeometry();
        distanceMeters = geoUtils.getClosestCoordinateDistanceInMeters(point,zoneGeometry);
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
  
  /** find the closest zone to the node location. This method computes the actual distance between any location on any linesegment of the outer boundary
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

  /** find the closest zone to the node location. This method computes the actual distance between any location on any linesegment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and the reference point and it therefore very precise. A cap is placed on how far a zone is allowed to be to still be regarded as closest
   * via maxDistanceMeters.
   * 
   * @param osmNode reference node
   * @param zones to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosest(final OsmNode osmNode, final Collection<? extends Zone> zones, double maxDistanceMeters, final PlanitJtsUtils geoUtils) throws PlanItException {
    Zone closestZone = null; 
    double minDistanceMeters = Double.POSITIVE_INFINITY;    
    Point point = PlanitJtsUtils.createPoint(getXCoordinate(osmNode), getYCoordinate(osmNode));
    for(Zone zone : zones) {
      double distanceMeters = Double.POSITIVE_INFINITY;
      if(zone.hasGeometry()) {
        Geometry zoneGeometry = zone.getGeometry();
        distanceMeters = geoUtils.getClosestDistanceInMeters(point,zoneGeometry);
      }else if(zone.getCentroid().hasPosition()) {
        distanceMeters = geoUtils.getDistanceInMetres(point.getCoordinate(), zone.getCentroid().getPosition().getCoordinate());
      }else {
        LOGGER.warning(String.format("zone has no geographic information to determine closesness to osm node %d",osmNode.getId()));
      }
     
      if(distanceMeters < minDistanceMeters) {
        minDistanceMeters = distanceMeters;
        if(minDistanceMeters < maxDistanceMeters) {
          closestZone = zone;
        }
      }      
    }
    return closestZone;    
  } 
  
  /** create a (Rectangular) bounding box around the osm node geometry based on the provided offset
   * 
   * @param osmNode to create bounding box around
   * @param offsetInMeters of the bounding box with node location in centre
   * @param geoUtils to properly create bounding box based on crs
   * @return bounding box
   */
  public static Envelope createBoundingBox(OsmNode osmNode, double offsetInMeters, PlanitJtsUtils geoUtils) {
    double xCoord = PlanitOsmNodeUtils.getXCoordinate(((OsmNode)osmNode));
    double yCoord = PlanitOsmNodeUtils.getYCoordinate(((OsmNode)osmNode));
    return geoUtils.createBoundingBox(xCoord, yCoord, offsetInMeters);
  }  

}

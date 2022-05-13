package org.goplanit.osm.util;

import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.osm.converter.network.OsmNetworkReaderData;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities regarding the use of a bounding box when parsing OSM data
 * 
 * @author markr
 *
 */
public class OsmBoundingAreaUtils {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmBoundingAreaUtils.class.getCanonicalName());

  
  /** Create a (Rectangular) bounding box around the OSM ways geometry based on the provided offset
   * 
   * @param osmWay to collect bounding box for
   * @param offsetInMeters around extreme points of osmWay
   * @param osmNodes potentially references by osmWay
   * @param geoUtils to extract length based on crs
   * @return created bounding box as Envelope
   */
  private static Envelope createBoundingBox(final OsmWay osmWay, double offsetInMeters, final Map<Long,OsmNode> osmNodes, final PlanitJtsCrsUtils geoUtils) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for(int index = 0 ; index < osmWay.getNumberOfNodes(); ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode != null) {
        minX = Math.min(minX, OsmNodeUtils.getX(osmNode) );
        minY = Math.min(minY, OsmNodeUtils.getY(osmNode) );
        maxX = Math.max(maxX, OsmNodeUtils.getX(osmNode) );
        maxY = Math.max(maxY, OsmNodeUtils.getY(osmNode) );        
      }
    }
    return geoUtils.createBoundingBox(minX, minY, maxX, maxY, offsetInMeters);      
  }

  /** log the given warning message but only when it is not too close to the bounding box, because then it is too likely that it is discarded due to missing
   * infrastructure or other missing assets that could not be parsed fully as they pass through the bounding box barrier. Therefore the resulting warning message is likely 
   * more confusing than helpful in those situation and is therefore ignored
   * 
   * @param message to log if not too close to bounding box
   * @param geometry to determine distance to bounding box to
   * @param boundingBox to use
   * @param geoUtils to use
   */
  public static void logWarningIfNotNearBoundingBox(String message, Geometry geometry, Envelope boundingBox, PlanitJtsCrsUtils geoUtils) {
    if(!isNearNetworkBoundingBox(geometry, boundingBox, geoUtils)) {
      LOGGER.warning(message);
    }
  }  
  
  /** check if geometry is near network bounding box using buffer based on PlanitOsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS
   * 
   * @param geometry to check
   * @param networkBoundingBox to consider
   * @param geoUtils to use
   * @return true when near, false otherwise
   */
  public static boolean isNearNetworkBoundingBox(Geometry geometry, Envelope networkBoundingBox, PlanitJtsCrsUtils geoUtils){    
    return geoUtils.isGeometryNearBoundingBox(geometry, networkBoundingBox, OsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS);
  }
  
  /** Verify if node resides on or within the zoning bounding polygon. If no bounding area is defined
   * or if no node is provided (null), false is returned by definition
   * 
   * @param osmNode to verify
   * @param boundingPolygon defining the area
   * @return true when no bounding area, or covered by bounding area, false otherwise
   */
  public static boolean isCoveredByZoningBoundingPolygon(OsmNode osmNode, Polygon boundingPolygon) {    
    
    if(osmNode==null || boundingPolygon==null) {
      return false;
    }    
    
    /* within or on bounding polygon yields true, false otherwise */
    return OsmNodeUtils.createPoint(osmNode).coveredBy(boundingPolygon);  
  }  
  
  /** Verify if OSM way has at least one node that resides within the zoning bounding polygon. If no bounding area is defined
   * or OSM way is null false is returned by definition
   * 
   * @param osmWay to verify
   * @param osmNodes to collect nodes of way from
   * @param boundingPolygon defining the area 
   * @return true when covered by bounding area, false otherwise
   */
  public static boolean isCoveredByZoningBoundingPolygon(OsmWay osmWay, Map<Long, OsmNode> osmNodes, Polygon boundingPolygon) {
    if(osmWay==null || boundingPolygon==null) {
      return false;
    }
        
    /* check if at least a single node of the OSM way is present within bounding box of zoning, implicitly assuming
     * that zoning bounding box is smaller than that of network, since only nodes within network bounding box are checked
     * otherwise the node is considered not available by definition */
    boolean coveredByBoundingPolygon = false;
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      long osmNodeId = osmWay.getNodeId(index);
      OsmNode osmNode = osmNodes.get(osmNodeId);
      if(osmNode!=null && isCoveredByZoningBoundingPolygon(osmNode, boundingPolygon)) {
        coveredByBoundingPolygon = true;
        break;
      }
    }
    
    return coveredByBoundingPolygon;  
  }   
  
  /** create a bounding box based on the provided offset and OSM entity geometry. The bounding box adds the offset to the extremes of the geometry
   * 
   * @param osmEntity to create bounding box for
   * @param offsetInMeters buffer in meters
   * @param osmNodes used in case osm entity is not a node and we require to obtain geometry information from underlying referenced nodes
   * @param geoUtils used to extract distances based on underlying crs
   * @return bounding box
   */
  public static Envelope createBoundingBoxForOsmWay(OsmEntity osmEntity, double offsetInMeters, Map<Long, OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils) {
    /* search bounding box */
    Envelope boundingBox = null; 
    switch (Osm4JUtils.getEntityType(osmEntity)) {
    case Node:
      boundingBox = createBoundingBox((OsmNode)osmEntity,offsetInMeters, geoUtils);
      break;
    case Way:
      boundingBox = createBoundingBox((OsmWay)osmEntity, offsetInMeters, osmNodes, geoUtils);
      break;  
    default:
      LOGGER.severe(String.format("unknown entity type %s when identifying bounding box for osm entity %s",Osm4JUtils.getEntityType(osmEntity).toString(), osmEntity.getId()));
      break;
    }
    return boundingBox;
  }
  
  /** create a (Rectangular) bounding box around the osm node geometry based on the provided offset
   * 
   * @param osmNode to create bounding box around
   * @param offsetInMeters of the bounding box with node location in centre
   * @param geoUtils to properly create bounding box based on crs
   * @return bounding box
   */
  public static Envelope createBoundingBox(OsmNode osmNode, double offsetInMeters, PlanitJtsCrsUtils geoUtils) {
    double xCoord = OsmNodeUtils.getX(((OsmNode)osmNode));
    double yCoord = OsmNodeUtils.getY(((OsmNode)osmNode));
    return geoUtils.createBoundingBox(xCoord, yCoord, offsetInMeters);
  }  
 
}

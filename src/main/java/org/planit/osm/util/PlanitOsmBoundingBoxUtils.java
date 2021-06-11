package org.planit.osm.util;

import java.util.Map;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderData;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;

import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities regarding the use of a bounding box when parsing OSM data
 * 
 * @author markr
 *
 */
public class PlanitOsmBoundingBoxUtils {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmBoundingBoxUtils.class.getCanonicalName());

  
  /** log the given warning message but only when it is not too close to the bounding box, because then it is too likely that it is discarded due to missing
   * infrastructure or other missing assets that could not be parsed fully as they pass through the bounding box barrier. Therefore the resulting warning message is likely 
   * more confusing than helpful in those situation and is therefore ignored
   * 
   * @param message to log if not too close to bounding box
   * @param geometry to determine distance to bounding box to
   * @param logger to log on
   * @param geoUtils to use
   * @throws PlanItException thrown if error
   */
  public static void logWarningIfNotNearBoundingBox(String message, Geometry geometry, Envelope boundingBox, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    if(!isNearNetworkBoundingBox(geometry, boundingBox, geoUtils)) {
      LOGGER.warning(message);
    }
  }  
  
  /** check if geometry is near network bounding box using buffer based on PlanitOsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS
   * 
   * @param geometry to check
   * @param boundingBox to consider
   * @param geoUtils to use
   * @return true when near, false otherwise
   * @throws PlanItException thrown if error
   */
  public static boolean isNearNetworkBoundingBox(Geometry geometry, Envelope networkBoundingBox, PlanitJtsCrsUtils geoUtils) throws PlanItException {    
    return geoUtils.isGeometryNearBoundingBox(geometry, networkBoundingBox, PlanitOsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS);
  }
  
  /** create a bounding box based on the provided offset and osm entity geometry. The bounding box adds the offset to the extremes of the geometry
   * 
   * @param osmEntity to create bounding box fos
   * @param offsetInMeters buffer in meters
   * @param osmNodes used in case osm entity is not a node and we require to obtain geometry information from underlying referenced nodes
   * @param geoUtils used to extract distances based on underlying crs
   * @return bounding box
   */
  public static Envelope createBoundingBox(OsmEntity osmEntity, double offsetInMeters, Map<Long, OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils) {
    /* search bounding box */
    Envelope boundingBox = null; 
    switch (Osm4JUtils.getEntityType(osmEntity)) {
    case Node:
      boundingBox = PlanitOsmNodeUtils.createBoundingBox((OsmNode)osmEntity,offsetInMeters, geoUtils);
      break;
    case Way:
      boundingBox = PlanitOsmWayUtils.createBoundingBox((OsmWay)osmEntity, offsetInMeters, osmNodes, geoUtils);
      break;  
    default:
      LOGGER.severe(String.format("unknown entity type %s when identifying bounding box for osm entity %s",Osm4JUtils.getEntityType(osmEntity).toString(), osmEntity.getId()));
      break;
    }
    return boundingBox;
  }
 
}

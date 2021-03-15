package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.planit.osm.tags.OsmDirectionTags;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.function.PlanitExceptionConsumer;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.Edge;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;
import org.planit.utils.zoning.Zone;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities in relation to parsing osm ways and constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class PlanitOsmWayUtils {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmWayUtils.class.getCanonicalName());
  
  /** find the closest planit entity to the way from the available entities. This method computes the actual distance between any location on any line segment of the 
   * geometry of the entity and any node on the way and it is therefore is very precise.
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   * 
   * @param osmWay reference way
   * @param planitEntities to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param osmNodes the way might refer to
   * @param geoUtils to compute projected distances
   * @return closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  protected static <T> T findPlanitEntityClosest(final OsmWay osmWay, final Collection<? extends T> planitEntities, double maxDistanceMeters, Map<Long,OsmNode> osmNodes, final PlanitJtsUtils geoUtils) throws PlanItException {
    T closestPlanitEntity = null; 
    double minDistanceMeters = Double.POSITIVE_INFINITY;
    for(int index=0; index<osmWay.getNumberOfNodes(); index++) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode != null) {
        Pair<T,Double> result = PlanitOsmNodeUtils.findPlanitEntityClosest(osmNode, planitEntities, maxDistanceMeters, geoUtils);
        if(result!=null && result.second() < minDistanceMeters) {
          closestPlanitEntity = result.first();
          minDistanceMeters = result.second();
        }        
      }
    }
    return closestPlanitEntity;
  }
 
  
  /**
   * Verify if passed osmWay is in fact circular in nature, e.g., , a type of roundabout. The way must be of type highway or railway as well
   * 
   * @param osmWay the way to verify 
   * @param tags of this OSM way
   * @param mustEndAtstart, when true only circular roads where the end node is the start node are identified, when false, any node that appears twice results in
   * a positive result (true is returned)
   * @return true if circular, false otherwise
   */
  public static boolean isCircularOsmWay(final OsmWay osmWay, final Map<String, String> tags, final boolean mustEndAtstart) {
    /* a circular road, has:
     * -  more than two nodes...
     * -  ...any node that appears at least twice (can be that a way is both circular but the circular component 
     *    is only part of the geometry 
     */
    if(tags.containsKey(OsmHighwayTags.HIGHWAY) || tags.containsKey(OsmRailwayTags.RAILWAY) && osmWay.getNumberOfNodes() > 2) {
      if(mustEndAtstart) {
        return PlanitOsmWayUtils.isOsmWayPerfectLoop(osmWay);
      }else {
        return findIndicesOfFirstLoop(osmWay, 0 /*consider entire way */)!=null;        
      }
    }
    return false;
  }  
  
  /** Verify if the osm way is a perfect loop, i.e., its first node equates to the last node
   *
   * @param osmWay to check
   * @return true when circular way, i.e., enclosed area, false otherwise
   */
  public static boolean isOsmWayPerfectLoop(OsmWay osmWay) {
    return osmWay.getNodeId(0) == osmWay.getNodeId(osmWay.getNumberOfNodes()-1);
  }

  /** find the start and end index of the first circular component of the passed in way (if any).
   * 
   * @param circularOsmWay to check
   * @param initialNodeIndex offset to use, when set it uses it as the starting point to start looking
   * @return pair of indices demarcating the first two indices with the same node conditional on the offset, null if not found 
   */
  public static Pair<Integer, Integer> findIndicesOfFirstLoop(final OsmWay osmWay, final int initialNodeIndex) {
    for(int index = initialNodeIndex ; index < osmWay.getNumberOfNodes() ; ++index) {
      long nodeIdToCheck = osmWay.getNodeId(index);
      for(int index2 = index+1 ; index2 < osmWay.getNumberOfNodes() ; ++index2) {
        if(nodeIdToCheck == osmWay.getNodeId(index2)) {
          return Pair.of(index, index2);
        }
      }
    }
    return null;
  }  
      
  /** the OSM default driving direction on a roundabout is either anticlockwise (right hand drive countries) or
   * clockwise (left hand drive countries), here we verify, based on the country name, if the default is
   * clockwise or not (anticlockwise)
   * 
   * @param countryName to check
   * @return true when lockwise direction is default, false otherwise
   */
  public static boolean isCircularWayDefaultDirectionClockwise(String countryName) {
    return DrivingDirectionDefaultByCountry.isLeftHandDrive(countryName) ? true : false;
  }

  /** assuming the tags represent an OSM way that is tagged as a junction=roundabout or circular, this method
   * verifies the driving direction on this way based on the country it resides in or an explicit override
   * of the clockwise or anticlockwise direction tags. Because OSM implicitly assumes these ways are one way and they
   * comply with country specific direction defaults we must utilise this method to find out what the actual driving
   * direction is
   * 
   * @param tags to use
   * @param isForwardDirection the direction that we want to verify if it is closed
   * @param countryName country name we determine the driving direction from in case it is not explicitly tagged
   * @return true when isForwardDirection is closed, false otherwise
   */
  public static boolean isCircularWayDirectionClosed(Map<String, String> tags, boolean isForwardDirection, String countryName) {
    Boolean isClockWise = null;
    if(OsmDirectionTags.isDirectionExplicitClockwise(tags)) {
      isClockWise = true;
    }else if(OsmDirectionTags.isDirectionExplicitAntiClockwise(tags)) {
      isClockWise = false;
    }else {
      isClockWise = PlanitOsmWayUtils.isCircularWayDefaultDirectionClockwise(countryName);
    }
    /* clockwise stands for forward direction, so when they do not match, all modes are to be excluded, the direction is closed */
    return isClockWise!=isForwardDirection;
  } 
  
  /** Based on the passed in osmWay collect the coordinates on that way as a coordinate array. In case something goes wrong and missing
   * nodes are found, the passed in consumer is called to deal with it. User can decide to throw an exception or do something else
   * entirely. If no exception is thrown, the nodes that could be parsed will be returned
   * 
   * @param osmWay to extract node coordinates from
   * @param osmNodes to collect nodes from by reference node ids in the way
   * @return coordinate array found, empty when no nodes were found available
   * @throws PlanItException thrown if error
   */
  public static Coordinate[] createCoordinateArray(OsmWay osmWay, Map<Long,OsmNode> osmNodes, PlanitExceptionConsumer<Set<Long>> missingNodeconsumer) throws PlanItException{
    Set<Long> missingNodes = null;
    Coordinate[] coordArray = new Coordinate[osmWay.getNumberOfNodes()];
    for(int index = 0 ; index < osmWay.getNumberOfNodes() ; ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode==null) {
        if(missingNodes==null) {
          missingNodes = new HashSet<Long>();
        }
        missingNodes.add(osmWay.getNodeId(index));
        continue;
      }
      coordArray[index] = new Coordinate(PlanitOsmNodeUtils.getX(osmNode), PlanitOsmNodeUtils.getY(osmNode));
    }
    
    /* call consumer */
    if(missingNodes!=null && missingNodeconsumer != null) {
      missingNodeconsumer.accept(missingNodes);
      
      /* resize based on missing nodes*/
      coordArray = PlanitJtsUtils.copyWithoutNullEntries(coordArray);
    }        
        
    return coordArray;
  }   
 
  /** Based on the passed in osmWay collect the coordinates on that way as a coordinate array. In case there are missing
   * nodes or something else goes wrong a PlanitException is thrown
   * 
   * @param osmWay to extract node coordinates from
   * @param osmNodes to collect nodes from by reference node ids in the way
   * @return coordinate array
   * @throws PlanItException thrown if error
   */
  public static Coordinate[] createCoordinateArray(OsmWay osmWay, Map<Long,OsmNode> osmNodes) throws PlanItException{
    
    /* throw when issue */
    PlanitExceptionConsumer<Set<Long>> missingNodeconsumer = (missingNodes) -> {
      if(missingNodes!=null) {
        throw new PlanItException(String.format("Missing osm nodes when extracting coordinate array for OSM way %d: %s",osmWay.getId(), missingNodes.toString()));
      }
    };
    
    return createCoordinateArray(osmWay, osmNodes, missingNodeconsumer);
  }  
  
  /** Based on the passed in osmWay collect the coordinates on that way as a coordinate array. In case there are missing
   * nodes we log this but retain as much of the information in the returned coordinate array as possible
   * 
   * @param osmWay to extract node coordinates from
   * @param osmNodes to collect nodes from by reference node ids in the way
   * @return coordinate array
   */
  public static Coordinate[] createCoordinateArrayNoThrow(OsmWay osmWay, Map<Long,OsmNode> osmNodes){
    
    /* log -> no throw */
    PlanitExceptionConsumer<Set<Long>> missingNodeConsumer = (missingNodes) -> {
      if(missingNodes!=null) {
        LOGGER.warning(String.format("Missing osm nodes when extracting coordinate array for OSM way %d: %s",osmWay.getId(), missingNodes.toString()));
      }
    };
    
    Coordinate[] coordArray = null;  
    try {
      coordArray =  createCoordinateArray(osmWay, osmNodes, missingNodeConsumer);
    }catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
    }
    return coordArray;
  }  
  

  /** extract geometry from the osm way which can either be a line string or polygon
   * 
   * @param osmWay to extract geometry for
   * @param osmNodes to extract geo features from
   * @return created gemoetry
   */
  public static Geometry extractGeometry(OsmWay osmWay, Map<Long, OsmNode> osmNodes) {
    Geometry geometry = null;
    if(PlanitOsmWayUtils.isOsmWayPerfectLoop(osmWay)) {
      /* area, so extract polygon geometry, in case of missing nodes, we log this but do not throw an exception, instead we keep the best possible shape that remains */
      geometry = PlanitOsmWayUtils.extractPolygonNoThrow(osmWay, osmNodes); 
    }else {
      /* (open) line string */
      geometry = PlanitOsmWayUtils.extractLineStringNoThrow(osmWay, osmNodes);        
    }
    return geometry;
  }   
  
  /**
   * Extract the geometry for the passed in way as line string
   * 
   * @param osmWay way to extract geometry from
   * @return line string instance representing the shape of the way
   * @throws PlanItException thrown if error
   */
  public static LineString extractLineString(OsmWay osmWay, Map<Long,OsmNode> osmNodes) throws PlanItException {
    Coordinate[] coordArray = createCoordinateArray(osmWay, osmNodes);
    return  PlanitJtsUtils.createLineString(coordArray);
  }

  /** identical to {@link extractLineString}, except it does not throw exceptions, but simply logs any issues found
   * @param osmWay to extract geometry for
   * @param osmNodes to collect from
   * @return parsed geometry, can be null if not valid for some reason
   */
  public static LineString extractLineStringNoThrow(OsmWay osmWay, Map<Long, OsmNode> osmNodes) {
    try {
      Coordinate[] coordArray = createCoordinateArrayNoThrow(osmWay, osmNodes);
      /* create line string when valid number of coordinates is still present */
      if(coordArray!= null && coordArray.length>=2) {
        if(coordArray.length < osmWay.getNumberOfNodes() ) {
          /* inform user that osm way is corrupted but it was salvaged to some degree */
          LOGGER.info(String.format("SALVAGED: linestring for OSM way %d, truncated to available nodes",osmWay.getId()));
        }
        return  PlanitJtsUtils.createLineString(coordArray);
      }
      
    }catch(Exception e) {
      LOGGER.warning(String.format("Unable to create line string for OSM way %d",osmWay.getId()));
    }
    return null;
  }
  
  /** Extract the geometry for the passed in way as polygon (assumed it has been identified as such already)
   * 
   * @param osmWay to extract geometry for
   * @param osmNodes to collect from
   * @return parsed geometry
   * @throws PlanItException thrown if error
   */
  public static Polygon extractPolygon(OsmWay osmWay, Map<Long, OsmNode> osmNodes) throws PlanItException {
    Coordinate[] coordArray = createCoordinateArray(osmWay, osmNodes);
    return PlanitJtsUtils.createPolygon(coordArray);
  }   
  
  /** identical to {@link extractPolygon}, except it does not throw exceptions, but simply logs any issues found
   * and tries to salvage the polygon by creating it out of the coordinates that are available as lnog as we can still create
   * a closed 2D shape.
   * 
   * @param osmWay to extract geometry for
   * @param osmNodes to collect from
   * @return parsed geometry
   */
  public static Polygon extractPolygonNoThrow(OsmWay osmWay, Map<Long, OsmNode> osmNodes) {   
    try {
      Coordinate[] coordArray = createCoordinateArrayNoThrow(osmWay, osmNodes);
      /* create polygon when valid number of nodes present */
      if(coordArray!= null && coordArray.length>=2) {
        if(coordArray.length < osmWay.getNumberOfNodes() ) {
          /* create closed ring in case nodes are missing but we still have a viable polygon shape */
          coordArray = PlanitJtsUtils.makeClosed2D(coordArray);
          LOGGER.info(String.format("SALVAGED: polygon for OSM way %d, truncated to available nodes",osmWay.getId())); 
        }
        return PlanitJtsUtils.createPolygon(coordArray);
      }
    }catch(Exception e) {
      LOGGER.warning(String.format("Unable to create polygon for OSM way %d",osmWay.getId()));
    }
    return null;
  }
  
  /** create a (Rectangular) bounding box around the osm ways geometry based on the provided offset
   * 
   * @param osmWay to collect bounding box for
   * @param offsetInMeters around extreme points of osmWay
   * @param osmNodes potentially references by osmWay
   * @param geoUtils to extract length based on crs
   * @return
   */
  public static Envelope createBoundingBox(OsmWay osmWay, double offsetInMeters, Map<Long,OsmNode> osmNodes, PlanitJtsUtils geoUtils) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for(int index = 0 ; index < osmWay.getNumberOfNodes(); ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode != null) {
        minX = Math.min(minX, PlanitOsmNodeUtils.getX(osmNode) );
        minY = Math.min(minY, PlanitOsmNodeUtils.getY(osmNode) );
        maxX = Math.max(maxX, PlanitOsmNodeUtils.getX(osmNode) );
        maxY = Math.max(maxY, PlanitOsmNodeUtils.getY(osmNode) );        
      }
    }
    return geoUtils.createBoundingBox(minX, minY, maxX, maxY, offsetInMeters);      
  }   
  
  /** find the closest zone to the way . This method computes the actual distance between any location on any linesegment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and any node on the way and it is therefore is very precise
   * 
   * 
   * @param osmWay reference way
   * @param zones to check against using their geometries
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosest(final OsmWay osmWay, final Collection<? extends Zone> zones, Map<Long,OsmNode> osmNodes, final PlanitJtsUtils geoUtils) throws PlanItException {
    return findZoneClosest(osmWay, zones, Double.POSITIVE_INFINITY, osmNodes, geoUtils);    
  }  

  /** find the closest zone to the way . This method computes the actual distance between any location on any line segment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and any node on the way and it is therefore is very precise.
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   * 
   * @param osmWay reference way
   * @param zones to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param osmNodes the way might refer to
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosest(final OsmWay osmWay, final Collection<? extends Zone> zones, double maxDistanceMeters, Map<Long,OsmNode> osmNodes, final PlanitJtsUtils geoUtils) throws PlanItException {
    return findPlanitEntityClosest(osmWay, zones, maxDistanceMeters, osmNodes, geoUtils);   
  }   
  
  /** find the closest edge to the way from the available edges. This method computes the actual distance between any location on any line segment of the 
   * geometry of the edge and any node on the way and it is therefore is very precise
   * 
   * 
   * @param osmWay reference way
   * @param edges to check against using their geometries
   * @param osmNodes the way might refer to
   * @param geoUtils to compute projected distances
   * @return edge closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Edge findEdgeClosest(final OsmWay osmWay, final Collection<? extends Edge> edges, Map<Long,OsmNode> osmNodes, final PlanitJtsUtils geoUtils) throws PlanItException {
    return findEdgeClosest(osmWay, edges, Double.POSITIVE_INFINITY, osmNodes, geoUtils);    
  }   
  
  /** find the closest edge to the way from the available edges. This method computes the actual distance between any location on any line segment of the 
   * geometry of the edge and any node on the way and it is therefore is very precise.
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   * 
   * @param osmWay reference way
   * @param edges to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param osmNodes the way might refer to
   * @param geoUtils to compute projected distances
   * @return edge closest, null if none matches criteria
   * @throws PlanItException thrown if error
   */
  public static Edge findEdgeClosest(final OsmWay osmWay, final Collection<? extends Edge> edges, double maxDistanceMeters, Map<Long,OsmNode> osmNodes, final PlanitJtsUtils geoUtils) throws PlanItException {
    return findPlanitEntityClosest(osmWay, edges, maxDistanceMeters, osmNodes, geoUtils);        
  }


  /** find the minimum distance line segment that connects the osmWay to the passed in line string geometry
   * 
   * @param osmWay to use
   * @param geometry to find minimum line segment to
   * @param osmNodes to use for extracting geo information regarding the osm way
   * @param geoUtils to compute distances
   * @return line segment with minimum distance connecting the way and the geometry
   * @throws PlanItException thrown if error
   */
  public static LineSegment findMinimumLineSegmentBetween(OsmWay osmWay, LineString geometry, Map<Long, OsmNode> osmNodes, PlanitJtsUtils geoUtils) throws PlanItException {
    double minDistanceMeters = Double.POSITIVE_INFINITY;
    Coordinate osmWayMinDistanceCoordinate = null;
    Coordinate geometryMinDistanceCoordinate = null;
    for(int index=0; index<osmWay.getNumberOfNodes(); index++) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      Coordinate osmNodeCoordinate = new Coordinate(PlanitOsmNodeUtils.getX(osmNode),PlanitOsmNodeUtils.getY(osmNode));
      if(osmNode != null) {
        Coordinate closestCoordinate = PlanitOsmNodeUtils.findClosestProjectedCoordinateTo(osmNode, geometry, geoUtils);
        double distanceMeters = geoUtils.getDistanceInMetres(osmNodeCoordinate, closestCoordinate);
        if(distanceMeters < minDistanceMeters) {
          minDistanceMeters = distanceMeters;
          osmWayMinDistanceCoordinate = osmNodeCoordinate;
          geometryMinDistanceCoordinate = closestCoordinate;
        }
      }
    }
    
    if(minDistanceMeters < Double.POSITIVE_INFINITY) {
      return PlanitJtsUtils.createLineSegment(osmWayMinDistanceCoordinate, geometryMinDistanceCoordinate);
    }
    return null;
  }


}

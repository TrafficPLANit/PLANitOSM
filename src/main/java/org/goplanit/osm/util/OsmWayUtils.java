package org.goplanit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.goplanit.osm.converter.network.OsmNetworkHandlerHelper;
import org.goplanit.osm.converter.network.OsmNetworkReaderData;
import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.function.PlanitExceptionConsumer;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.locale.DrivingDirectionDefaultByCountry;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.zoning.Zone;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities in relation to parsing osm ways and constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class OsmWayUtils {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmWayUtils.class.getCanonicalName());
  
  /** find the closest PLANit entity to the way from the available entities. This method computes the actual distance between any location on any line segment of the 
   * geometry of the entity and any node on the way and it is therefore is very precise.
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   *
   * @param <T> type of the PLANit entity
   * @param osmWay reference way
   * @param planitEntities to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param osmNodes the way might refer to
   * @param geoUtils to compute projected distances
   * @return closest, null if none matches criteria
   */
  protected static <T> T findPlanitEntityClosest(
      final OsmWay osmWay, final Collection<? extends T> planitEntities, double maxDistanceMeters, Map<Long,OsmNode> osmNodes, final PlanitJtsCrsUtils geoUtils){
    T closestPlanitEntity = null; 
    double minDistanceMeters = Double.POSITIVE_INFINITY;
    for(int index=0; index<osmWay.getNumberOfNodes(); index++) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode != null) {
        Pair<T,Double> result = PlanitEntityGeoUtils.findPlanitEntityClosest(OsmNodeUtils.createCoordinate(osmNode), planitEntities, maxDistanceMeters, geoUtils);
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
   * @param mustEndAtStart, when true only circular roads where the end node is the start node are identified, when false, any node that appears twice results in
   * a positive result (true is returned)
   * @return true if circular, false otherwise
   */
  public static boolean isCircularOsmWay(final OsmWay osmWay, final Map<String, String> tags, final boolean mustEndAtStart) {
    /* a circular road, has:
     * -  more than two nodes...
     * -  ...any node that appears at least twice (can be that a way is both circular but the circular component 
     *    is only part of the geometry 
     */
    if(tags.containsKey(OsmHighwayTags.HIGHWAY) || tags.containsKey(OsmRailwayTags.RAILWAY) && osmWay.getNumberOfNodes() > 2) {
      if(mustEndAtStart) {
        return OsmWayUtils.isOsmWayPerfectLoop(osmWay);
      }else {
        return findIndicesOfFirstLoop(osmWay, 0 /*consider entire way */)!=null;        
      }
    }
    return false;
  }  
  
  /** Verify if the OSM way is a perfect loop, i.e., its first node equates to the last node
   *
   * @param osmWay to check
   * @return true when circular way, i.e., enclosed area, false otherwise
   */
  public static boolean isOsmWayPerfectLoop(OsmWay osmWay) {
    return osmWay.getNodeId(0) == osmWay.getNodeId(osmWay.getNumberOfNodes()-1);
  }

  /** Find the start and end index of the first circular component of the passed in way (if any).
   * 
   * @param osmWay to check
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
      
  /** The OSM default driving direction on a roundabout is either anticlockwise (right hand drive countries) or
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
      isClockWise = OsmWayUtils.isCircularWayDefaultDirectionClockwise(countryName);
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
   * @param startNodeIndex reference
   * @param endNodeIndex reference
   * @param missingNodeConsumer callback in case of missing node found
   * @return coordinate array found, empty when no nodes were found available
   * @throws PlanItException thrown if error
   */
  public static Coordinate[] createCoordinateArray(OsmWay osmWay, Map<Long,OsmNode> osmNodes, int startNodeIndex, int endNodeIndex, PlanitExceptionConsumer<Set<Long>> missingNodeConsumer) throws PlanItException{
    Set<Long> missingNodes = null;
        
    /* in the special case the end node index is smaller than start node index (circular way) we "loop around" to accommodate this */
    Coordinate[] coordArray = null;
    int stopIndex = endNodeIndex;
    if(endNodeIndex < startNodeIndex) {
      stopIndex = osmWay.getNumberOfNodes()-1;
      coordArray = new Coordinate[stopIndex - startNodeIndex + 1 + endNodeIndex + 1];
    }else {
      coordArray = new Coordinate[endNodeIndex - startNodeIndex + 1];
    }
    
    for(int index = startNodeIndex ; index <= stopIndex ; ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode==null) {
        if(missingNodes==null) {
          missingNodes = new HashSet<Long>();
        }
        missingNodes.add(osmWay.getNodeId(index));
        continue;
      }
      coordArray[index - startNodeIndex] = new Coordinate(OsmNodeUtils.getX(osmNode), OsmNodeUtils.getY(osmNode));
    }
    
    if(endNodeIndex < startNodeIndex) {
      /* supplement with coordinates from start to end node index */
      int offsetIndex = stopIndex - startNodeIndex + 1;
      for(int index = 0 ; index <= endNodeIndex ; ++index) {
        OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
        if(osmNode==null) {
          if(missingNodes==null) {
            missingNodes = new HashSet<Long>();
          }
          missingNodes.add(osmWay.getNodeId(index));
          continue;
        }
        coordArray[ offsetIndex + index] = new Coordinate(OsmNodeUtils.getX(osmNode), OsmNodeUtils.getY(osmNode));
      }      
    }
    
    /* call consumer */
    if(missingNodes!=null && missingNodeConsumer != null) {
      missingNodeConsumer.accept(missingNodes);
      
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
    return createCoordinateArray(osmWay, 0, osmWay.getNumberOfNodes()-1, osmNodes);
  }  
  
  /** Based on the passed in osmWay collect the coordinates on that way as a coordinate array for the given range. In case there are missing
   * nodes or something else goes wrong a PlanitException is thrown
   * 
   * @param osmWay to extract node coordinates from
   * @param osmNodes to collect nodes from by reference node ids in the way
   * @param startNodeIndex to use
   * @param endNodeIndex to use
   * @return coordinate array
   * @throws PlanItException thrown if error
   */  
  public static Coordinate[] createCoordinateArray(OsmWay osmWay, int startNodeIndex, int endNodeIndex, Map<Long, OsmNode> osmNodes) throws PlanItException {
    
    /* throw when issue */
    PlanitExceptionConsumer<Set<Long>> missingNodeConsumer = (missingNodes) -> {
      if(missingNodes!=null) {
        throw new PlanItException(String.format("Missing OSM nodes for OSM way %d: %s",osmWay.getId(), missingNodes.toString()));
      }
    };
    
    return createCoordinateArray(osmWay, osmNodes, startNodeIndex, endNodeIndex, missingNodeConsumer);
  }  
  
  /** Based on the passed in osmWay collect the coordinates on that way as a coordinate array for the given range. In case there are missing
   * nodes we log this but retain as much of the information in the returned coordinate array as possible
   * 
   * @param osmWay to extract node coordinates from
   * @param osmNodes to collect nodes from by reference node ids in the way
   * @param startNodeIndex to use
   * @param endNodeIndex to use
   * @return coordinate array
   * @throws PlanItException thrown if error
   */  
  public static Coordinate[] createCoordinateArrayNoThrow(OsmWay osmWay, int startNodeIndex, int endNodeIndex, Map<Long, OsmNode> osmNodes) throws PlanItException {
    
    /* log -> no throw */
    PlanitExceptionConsumer<Set<Long>> missingNodeConsumer = (missingNodes) -> {
      if(missingNodes!=null) {
        LOGGER.warning(String.format("Missing OSM nodes for OSM way %d: %s",osmWay.getId(), missingNodes));
      }
    };
    
    return createCoordinateArray(osmWay, osmNodes, startNodeIndex, endNodeIndex, missingNodeConsumer);
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
        LOGGER.warning(String.format("Missing OSM nodes for for OSM way %d: %s",osmWay.getId(), missingNodes.toString()));
      }
    };
    
    Coordinate[] coordArray = null;  
    try {
      coordArray =  createCoordinateArray(osmWay, osmNodes, 0, osmWay.getNumberOfNodes()-1, missingNodeConsumer);
    }catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
    }
    return coordArray;
  }  
  

  /** extract geometry from the osm way which can either be a line string or polygon
   * 
   * @param osmWay to extract geometry for
   * @param osmNodes to extract geo features from
   * @return created geometry
   * @throws PlanItException thrown if error
   */
  public static Geometry extractGeometry(OsmWay osmWay, Map<Long, OsmNode> osmNodes) throws PlanItException {
    return extractGeometry(osmWay, osmNodes, LOGGER.getLevel());
  }   
  
  /** extract geometry from the OSM way which can either be a line string or polygon
   * 
   * @param osmWay to extract geometry for
   * @param osmNodes to extract geo features from
   * @param logLevel logLevel
   * @return created geometry
   */
  public static Geometry extractGeometry(OsmWay osmWay, Map<Long, OsmNode> osmNodes, Level logLevel){

    Level originalLevel = LOGGER.getLevel();
    LOGGER.setLevel(logLevel);
    Geometry geometry = null;
    if(isOsmWayPerfectLoop(osmWay)) {
      /* area, so extract polygon geometry, in case of missing nodes, we log this but do not throw an exception, instead we keep the best possible shape that remains */
      geometry = extractPolygonNoThrow(osmWay, osmNodes); 
    }
    
    if(geometry== null) {
      /* (open) line string (or unable to salvage polygon in case of missing nodes, so try to create line string instead)*/
      geometry = extractLineStringNoThrow(osmWay, osmNodes);        
    }
    
    if(geometry== null) {
      /* unable to salvage line string in case of missing nodes, so try to create point instead*/
      geometry = extractPoint(osmWay, osmNodes);        
    }
    LOGGER.setLevel(originalLevel);
    return geometry;
  }   
   
  

  /**
   * Extract the geometry for the passed in way as line string
   * 
   * @param osmWay way to extract geometry from
   * @param osmNodes to consider
   * @return line string instance representing the shape of the way
   * @throws PlanItException thrown if error
   */
  public static LineString extractLineString(OsmWay osmWay, Map<Long,OsmNode> osmNodes) throws PlanItException {
    Coordinate[] coordArray = createCoordinateArray(osmWay, osmNodes);
    return  PlanitJtsUtils.createLineString(coordArray);
  }
  
  /**
   * Extract the geometry for the passed in way as line string for the given nodes
   * 
   * @param osmWay way to extract geometry from
   * @param startNodeIndex to use
   * @param endNodeIndex to use
   * @param osmNodes to consider
   * @return line string instance representing the shape of the way
   * @throws PlanItException thrown if error
   */  
  public static LineString extractLineString(OsmWay osmWay, int startNodeIndex, int endNodeIndex, Map<Long, OsmNode> osmNodes) throws PlanItException {
    Coordinate[] coordArray = createCoordinateArray(osmWay, startNodeIndex, endNodeIndex, osmNodes);
    return  PlanitJtsUtils.createLineString(coordArray);
  }
  
  /**
   * Identical to {@link OsmWayUtils#extractLineString}, except it does not throw exceptions, but simply logs any issues found
   * 
   * @param osmWay way to extract geometry from
   * @param startNodeIndex to use
   * @param endNodeIndex to use
   * @param osmNodes to consider
   * @return line string instance representing the shape of the way
   * @throws PlanItException thrown if error
   */  
  public static LineString extractLineStringNoThrow(OsmWay osmWay, int startNodeIndex, int endNodeIndex, Map<Long, OsmNode> osmNodes) throws PlanItException {
    Coordinate[] coordArray = createCoordinateArrayNoThrow(osmWay, startNodeIndex, endNodeIndex, osmNodes);
    return  PlanitJtsUtils.createLineString(coordArray);
  }  

  /** Identical to {@link OsmWayUtils#extractLineString}, except it does not throw exceptions, but simply logs any issues found
   * @param osmWay to extract geometry for
   * @param osmNodes to collect from
   * @return parsed geometry, can be null if not valid for some reason
   */
  public static LineString extractLineStringNoThrow(OsmWay osmWay, Map<Long, OsmNode> osmNodes) {
    LineString lineString = null;
    try {
      Coordinate[] coordArray = createCoordinateArrayNoThrow(osmWay, osmNodes);
      /* create line string when valid number of coordinates is still present */
      if(coordArray!= null && coordArray.length>=2) {
        lineString = PlanitJtsUtils.createLineString(coordArray);
        if(lineString!=null && coordArray.length < osmWay.getNumberOfNodes()) {
          /* inform user that osm way is corrupted but it was salvaged to some degree */
          LOGGER.info(String.format("SALVAGED: linestring for OSM way %d, truncated to available nodes",osmWay.getId()));
        }
      }
      
    }catch(Exception e) {
    }
    return lineString;
  }
  
  /** creates a point geometry using the first available node from the nodes on the OSM way. Only to be used when no line string or polygon could be extract
   * due to missing nodes for example
   * 
   * @param osmWay to extract point geometry for
   * @param osmNodes to collect from
   * @return parsed geometry, can be null if not valid for some reason
   */
  public static Point extractPoint(OsmWay osmWay, Map<Long, OsmNode> osmNodes){
    Coordinate[] coordArray = createCoordinateArrayNoThrow(osmWay, osmNodes);
    /* create point when enough coordinates are available */
    if(coordArray!= null && coordArray.length>=1) {
      return  PlanitJtsUtils.createPoint(coordArray[0]);
    }else {
      LOGGER.severe(String.format("Unable to extract a single location from nodes references by osm way %d",osmWay.getId()));
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
  
  /** identical to {@link OsmWayUtils#extractPolygon(OsmWay, Map)}, except it does not throw exceptions, but simply logs any issues found
   * and tries to salvage the polygon by creating it out of the coordinates that are available as lnog as we can still create
   * a closed 2D shape.
   * 
   * @param osmWay to extract geometry for
   * @param osmNodes to collect from
   * @return parsed geometry
   */
  public static Polygon extractPolygonNoThrow(OsmWay osmWay, Map<Long, OsmNode> osmNodes) {   
    Polygon polygon = null;
    boolean salvaged = false;
    try {
      Coordinate[] coordArray = createCoordinateArrayNoThrow(osmWay, osmNodes);      
      /* create polygon when valid number of nodes present */
      if(coordArray!= null && coordArray.length>=2) {
        if(coordArray.length < osmWay.getNumberOfNodes() ) {
          /* create closed ring in case nodes are missing but we still have a viable polygon shape */
          coordArray = PlanitJtsUtils.makeClosed2D(coordArray); 
          salvaged = true;
        }
        polygon = PlanitJtsUtils.createPolygon(coordArray);
        if(polygon != null && salvaged) {
          /* only log now, because it can still fail after closing the polygon */
          LOGGER.info(String.format("SALVAGED: polygon for OSM way %d, truncated to available nodes",osmWay.getId()));
        }
      }
    }catch(Exception e) {
    }
    return null;
  }   
  
  /** Find the closest zone to the way . This method computes the actual distance between any location on any line segment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and any node on the way and it is therefore is very precise
   * 
   * 
   * @param osmWay reference way
   * @param zones to check against using their geometries
   * @param osmNodes to consider
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   */
  public static Zone findZoneClosest(final OsmWay osmWay, final Collection<? extends Zone> zones, Map<Long,OsmNode> osmNodes, final PlanitJtsCrsUtils geoUtils){
    return findZoneClosest(osmWay, zones, Double.POSITIVE_INFINITY, osmNodes, geoUtils);    
  }  

  /** Find the closest zone to the way . This method computes the actual distance between any location on any line segment of the outer boundary
   * of the zones (or its centroid if no polygon/linestring is available) and any node on the way and it is therefore is very precise.
   * A cap is placed on how far a zone is allowed to be to still be regarded as closest via maxDistanceMeters.
   * 
   * @param osmWay reference way
   * @param zones to check against using their geometries
   * @param maxDistanceMeters maximum allowedDistance to be eligible
   * @param osmNodes the way might refer to
   * @param geoUtils to compute projected distances
   * @return zone closest, null if none matches criteria
   */
  public static Zone findZoneClosest(final OsmWay osmWay, final Collection<? extends Zone> zones, double maxDistanceMeters, Map<Long,OsmNode> osmNodes, final PlanitJtsCrsUtils geoUtils){
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
  public static Edge findEdgeClosest(final OsmWay osmWay, final Collection<? extends Edge> edges, final Map<Long,OsmNode> osmNodes, final PlanitJtsCrsUtils geoUtils) throws PlanItException {
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
  public static Edge findEdgeClosest(final OsmWay osmWay, final Collection<? extends Edge> edges, double maxDistanceMeters, final Map<Long,OsmNode> osmNodes, final PlanitJtsCrsUtils geoUtils) throws PlanItException {
    return findPlanitEntityClosest(osmWay, edges, maxDistanceMeters, osmNodes, geoUtils);        
  }


  /** find the minimum distance line segment that connects the osmWay to the passed in line string geometry
   * 
   * @param osmWay to use
   * @param geometry to find minimum line segment to
   * @param osmNodes to use for extracting geo information regarding the osm way
   * @param geoUtils to compute distances
   * @return line segment with minimum distance connecting the way and the geometry
   */
  public static LineSegment findMinimumLineSegmentBetween(
      OsmWay osmWay, LineString geometry, Map<Long, OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils){
    double minDistanceMeters = Double.POSITIVE_INFINITY;
    Coordinate osmWayMinDistanceCoordinate = null;
    Coordinate geometryMinDistanceCoordinate = null;
    for(int index=0; index<osmWay.getNumberOfNodes(); index++) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      Coordinate osmNodeCoordinate = new Coordinate(OsmNodeUtils.getX(osmNode),OsmNodeUtils.getY(osmNode));
      if(osmNode != null) {
        Coordinate closestCoordinate = OsmNodeUtils.findClosestProjectedCoordinateTo(osmNode, geometry, geoUtils);
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


  /** find the most prominent (important) of the edges based on the osm highway type they carry
   * @param edges to check
   * @return osm highway type found to be the most prominent
   */
  public static String findMostProminentOsmHighWayType(Set<? extends Edge> edges) {
    String mostProminent = OsmHighwayTags.UNCLASSIFIED; /* lowest priority option */
    for(Edge edge : edges) {
      String osmWayType = OsmNetworkHandlerHelper.getLinkOsmWayType((Link)edge);
      if(OsmHighwayTags.compareHighwayType(mostProminent,osmWayType)<0) {
        mostProminent = osmWayType;
      }
    }
    return mostProminent;
  }


  /** Remove all edges with osm way types that are deemed less prominent than the one provided based on the OSM highway tags ordering
   * @param osmHighwayType to use as a reference
   * @param edgesToFilter the collection being filtered
   */
  public static void removeEdgesWithOsmHighwayTypesLessImportantThan(String osmHighwayType, Set<? extends Edge> edgesToFilter) {
    Iterator<? extends Edge> iterator = edgesToFilter.iterator();
    while(iterator.hasNext()) {
      Edge edge = iterator.next();
      String osmWayType = OsmNetworkHandlerHelper.getLinkOsmWayType((Link)edge);
      if(OsmHighwayTags.compareHighwayType(osmHighwayType,osmWayType)>0) {
        iterator.remove();
      }
    }
  }
  
  /** finds the first available osm node index on the osm way
   * @param offsetIndex to start search from
   * @param osmWay to collect from
   * @param osmNodes to check existence of osm way nodes
   * @return index of first available osm node, null if not found
   */
  public static Integer findFirstAvailableOsmNodeIndexAfter(int offsetIndex, final OsmWay osmWay, final Map<Long, OsmNode> osmNodes){
    for(int nodeIndex = offsetIndex+1; nodeIndex< osmWay.getNumberOfNodes(); ++nodeIndex) {
      var node = osmNodes.get(osmWay.getNodeId(nodeIndex));
      if(node == null) {
        continue;
      }
      return nodeIndex;
    }
    return null;
  }

  /** Finds the last consecutive available OSM node index after the offset, i.e. the index before the first unavailable node
   *
   * @param offsetIndex to start search from
   * @param osmWay to collect from
   * @param osmNodes to check existence of osm way nodes
   * @return last index of node that is available, null otherwise
   */
  public static Integer findLastAvailableOsmNodeIndexAfter(int offsetIndex, final OsmWay osmWay, final Map<Long, OsmNode> osmNodes){
    for(int nodeIndex =  osmWay.getNumberOfNodes()-1; nodeIndex>offsetIndex; --nodeIndex) {
      var node = osmNodes.get(osmWay.getNodeId(nodeIndex));
      if(node == null) {
        continue;
      }
      return nodeIndex;
    }
    return null;
  }
  
  /** Verify that all OSM nodes in the OSM way are available
   * 
   * @param osmWay to verify
   * @param osmNodes to check existence of OSM way nodes
   * @return true when complete, false otherwise
   */  
  public static boolean isAllOsmWayNodesAvailable(OsmWay osmWay, Map<Long, OsmNode> osmNodes) {
    for(int nodeIndex = 0; nodeIndex< osmWay.getNumberOfNodes(); ++nodeIndex) {      
      if(osmNodes.get(osmWay.getNodeId(nodeIndex))  == null ) {
        return false;
      }
    }
    return true;
  }  
  
  /** Collect index by location within the way. first collect node from all nodes and then extract location because
   * if duplicate nodes in the same location exist, collecting by location directly from layer data could yield the wrong node. this way
   * we are certain to extract the location from the right OSM node
   * 
   * @param osmWay way to use
   * @param nodePosition node position to find
   * @param networkData to use
   * @return the index, null if nothing is found
   */
  public static Integer getOsmWayNodeIndexByLocation(OsmWay osmWay, Point nodePosition, OsmNetworkReaderData networkData){
    for(int nodeIndex = 0; nodeIndex< osmWay.getNumberOfNodes(); ++nodeIndex) {
      long osmNodeId = osmWay.getNodeId(nodeIndex);
      OsmNode osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
      if(osmNode != null && OsmNodeUtils.nodeLocationEquals2D(osmNode, nodePosition.getCoordinate())) {
        return nodeIndex;
      }
    }
    return null;
  }


  /**
   * Verify existence of any of the supported way keys (highway=, railway=, or a waterway one (route=ferry, ferry=_highway_type) and
   * return the value
   *
   * @param tags to check
   * @return value found or log warning
   */
  public static String findWayTypeValueForEligibleKey(Map<String, String> tags) {
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      return tags.get(OsmHighwayTags.getHighwayKeyTag());
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)){
      return tags.get(OsmRailwayTags.getRailwayKeyTag());
    }else if(OsmWaterwayTags.isWaterBasedWay(tags)){
      return tags.get(OsmWaterwayTags.getUsedKeyTag(tags));
    }else{
      LOGGER.warning(String.format("No acceptable OSM way key found in provided tags (%s)", tags));
    }
    return null;
  }
}

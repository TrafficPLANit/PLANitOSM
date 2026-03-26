package org.goplanit.osm.converter.helper;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.utils.epsg.ProjectedEpsgCodesByCountry;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.utils.geo.PlanitGeometryOperationUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

import javax.annotation.Nullable;
import java.util.logging.Logger;

/**
 * Utilities for projected bounding area instances for fast checks shared between network and zoning
 * readers
 */
public class OsmProjectedBoundingAreaHelper {

  private static final Logger LOGGER = Logger.getLogger(OsmProjectedBoundingAreaHelper.class.getCanonicalName());

  /** spatially indexed version of bounding polygon if any for quick comparisons */
  private final PreparedPolygon preppedBoundingPolygonWgs84;

  /** be able to transform from source to projected destination Crs */
  private final MathTransform mathTransformSourceToProjection;

  /** indexed distance facet for fast calculating of distances to bounding polygon in projected CRS, make sure any calcs
   * feed in geometries that are also projected so NOT Wgs84 */
  private final IndexedFacetDistance indexedBoundingPolygonDistProjected;

  public OsmProjectedBoundingAreaHelper(){
    preppedBoundingPolygonWgs84 = null;
    mathTransformSourceToProjection = null;
    indexedBoundingPolygonDistProjected = null;
  }

  public OsmProjectedBoundingAreaHelper(
      OsmBoundary boundingArea, CoordinateReferenceSystem originalCrs, String destinationCountryName){
    // prepare polygon for faster checks
    this.preppedBoundingPolygonWgs84 = PlanitGeometryOperationUtils.extractPreparedPolygonForQuickSpatialComparisons(
        boundingArea.getBoundingPolygon());
    // prepare indexed distance faced for fast distance to calcs (in projection so it is not in degrees)
    var projectedCrs =
        PlanitCrsUtils.createCoordinateReferenceSystem(ProjectedEpsgCodesByCountry.getEpsg(destinationCountryName));
    this.mathTransformSourceToProjection = PlanitJtsUtils.findMathTransform(originalCrs, projectedCrs);
    var projectedBoundingPolygon = PlanitJtsUtils.transformGeometrySafe(
        boundingArea.getBoundingPolygon(),mathTransformSourceToProjection);
    this.indexedBoundingPolygonDistProjected = new IndexedFacetDistance(projectedBoundingPolygon);
  }

  public static OsmProjectedBoundingAreaHelper of(
      OsmBoundary boundingArea, CoordinateReferenceSystem originalCrs, String destinationCountryName) {
    return new OsmProjectedBoundingAreaHelper(boundingArea, originalCrs, destinationCountryName);
  }

  public static OsmProjectedBoundingAreaHelper empty() {
    return new OsmProjectedBoundingAreaHelper();
  }

  public boolean isEmpty(){
    return preppedBoundingPolygonWgs84 == null;
  }

  public PreparedPolygon getPreparedBoundingPolygon(){
    return preppedBoundingPolygonWgs84;
  }

  /**
   * Calculate distance to bounding polygon (assumes one is present otherwise undefined behaviour) for a
   * given point
   * @param point to calculate distance to bounding polygon
   * @param applyProjection when true transform point to projection (destination Crs of converter assumed to
   *                        be a projected), when false it is assumed to already be projected and calculated as is
   * @return distance in destination Crs units (typically meters)
   */
  public double calculateProjectedDistanceToBoundingPolygon(Point point, boolean applyProjection){
    return indexedBoundingPolygonDistProjected.distance(
        applyProjection ? PlanitJtsUtils.transformGeometrySafe(point, mathTransformSourceToProjection): point);
  }

  /**
   * Verify if OSM entity (node or way) is within boundary provided.
   *
   * @param entity to check
   * @param type  entity type
   * @param nodeData registered node information
   * @param osmBoundary to use for verification
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      OsmEntity entity,
      EntityType type,
      OsmNodeData nodeData,
      @Nullable OsmBoundary osmBoundary,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){

    if(type ==  EntityType.Node){
      return isNearPartlyOrWhollyWithinBoundaryArea(
          (OsmNode) entity, osmBoundary, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary);
    }else if(type == EntityType.Way){
      return isNearPartlyOrWhollyWithinBoundaryArea(
          (OsmWay) entity, nodeData, osmBoundary, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary);
    }
    LOGGER.severe(String.format("Unsupported OSM entity type for OSM entity(%d) when determining if entity falls " +
        "within %2f of boundary", entity.getId(), maxProjectedDistanceToBoundary));
    return false;
  }

  /**
   * Verify if OSM entity (node or way) is within boundary provided.
   *
   * @param entity to check
   * @param type  entity type
   * @param nodeData registered node information
   * @param osmBoundary to use for verification
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      OsmEntity entity,
      EntityType type,
      OsmNodeData nodeData,
      @Nullable OsmBoundary osmBoundary,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        entity, type, nodeData, osmBoundary, 0.0, isWithinWhenNoBoundary);
  }

  /**
   * Verify if node is within boundary provided.
   *
   * @param osmNode to check
   * @param osmBoundary to use for verification
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      OsmNode osmNode,
      OsmBoundary osmBoundary,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        OsmNodeUtils.createPoint(osmNode), osmBoundary, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary);
  }

  /**
   * Verify if node is within boundary provided.
   *
   * @param osmNode to check
   * @param osmBoundary to use for verification
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      OsmNode osmNode,
      OsmBoundary osmBoundary,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        osmNode, osmBoundary, 0.0, isWithinWhenNoBoundary);
  }

  /**
   * Verify if OSM way is within boundary provided by checking any available OSM node individually (not efficient)
   *
   * @param osmWay to check
   * @param nodeData registered node information
   * @param osmBoundary to use for verification
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      OsmWay osmWay,
      OsmNodeData nodeData,
      OsmBoundary osmBoundary,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        osmWay, nodeData, osmBoundary, 0.0, isWithinWhenNoBoundary);
  }

  /**
   * Verify if OSM way is within boundary provided by checking any available OSM node individually (not efficient)
   *
   * @param osmWay to check
   * @param nodeData registered node information
   * @param osmBoundary to use for verification
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      OsmWay osmWay,
      OsmNodeData nodeData,
      OsmBoundary osmBoundary,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){

    boolean anyWithinBoundary = false;
    for(int index = 0; index < osmWay.getNumberOfNodes(); ++index){
      var osmNode = nodeData.getRegisteredOsmNode(osmWay.getNodeId(index));
      if(osmNode != null &&
          isNearPartlyOrWhollyWithinBoundaryArea(
              osmNode, osmBoundary, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary)){
        anyWithinBoundary = true;
        break;
      }
    }
    return anyWithinBoundary;
  }

  /**
   * Verify if geometry is (partly) within boundary provided.
   *
   * @param geometry to check
   * @param osmBoundary to use for verification (may be null)
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      Geometry geometry,
      @Nullable OsmBoundary osmBoundary,
      boolean isWithinWhenNoBoundary){
    if(osmBoundary == null || !osmBoundary.hasBoundingPolygon()){
      return isWithinWhenNoBoundary;
    }

    return preppedBoundingPolygonWgs84.intersects(geometry);
  }

  /**
   * Verify if geometry is (partly) within boundary provided.
   *
   * @param point to check
   * @param osmBoundary to use for verification (may be null)
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      Point point,
      @Nullable OsmBoundary osmBoundary,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){
    if(osmBoundary == null || !osmBoundary.hasBoundingPolygon()){
      return isWithinWhenNoBoundary;
    }

    boolean success = isPartlyOrWhollyWithinBoundaryArea(point, osmBoundary, isWithinWhenNoBoundary);
    if(!success && maxProjectedDistanceToBoundary > 0){
      success = maxProjectedDistanceToBoundary <
          this.calculateProjectedDistanceToBoundingPolygon(point, false);
    }
    return success;
  }

  /**
   * Verify if geometry is (partly) within boundary provided.
   *
   * @param lineString to check
   * @param osmBoundary to use for verification (may be null)
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      LineString lineString,
      @Nullable OsmBoundary osmBoundary,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){
    if(osmBoundary == null || !osmBoundary.hasBoundingPolygon()){
      return isWithinWhenNoBoundary;
    }

    boolean success = isPartlyOrWhollyWithinBoundaryArea(lineString, osmBoundary, isWithinWhenNoBoundary);
    if(!success && maxProjectedDistanceToBoundary > 0){
      for(int index=0; index < lineString.getNumPoints();++index){
        var currPoint = lineString.getPointN(index);
        success = maxProjectedDistanceToBoundary <
            this.calculateProjectedDistanceToBoundingPolygon(currPoint, false);
        if(success){
          break;
        }
      }
    }
    return success;
  }




}

package org.goplanit.osm.converter.helper;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.goplanit.converter.utils.ProjectedBoundingAreaHelper;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.tags.OsmPtv1Tags;
import org.goplanit.osm.tags.OsmWaterModeTags;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.utils.epsg.ProjectedEpsgCodesByCountry;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.locationtech.jts.geom.Polygon;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Supplement ProjectedBoundingAreaHelper with some OSM specific helper methods
 *
 * @author markr
 */
public class OsmProjectedBoundingAreaHelper extends ProjectedBoundingAreaHelper {

  private static final Logger LOGGER = Logger.getLogger(OsmProjectedBoundingAreaHelper.class.getCanonicalName());

  /**
   * Constructor
   */
  protected OsmProjectedBoundingAreaHelper(){
    super();
  }

  /**
   * Constructor
   *
   * @param osmBoundary to consider
   * @param originalCrs to consider
   * @param destinationCrs to consider
   * @param maximumDistanceFerryOutsideBoundingPolygonInMeters to use for water leniency
   */
  protected OsmProjectedBoundingAreaHelper(
      OsmBoundary osmBoundary,
      CoordinateReferenceSystem originalCrs,
      CoordinateReferenceSystem destinationCrs,
      double maximumDistanceFerryOutsideBoundingPolygonInMeters ){
    super(osmBoundary.getBoundingPolygon(), originalCrs, destinationCrs,
        maximumDistanceFerryOutsideBoundingPolygonInMeters);
  }

  /**
   * Factory method
   * @param originalCrs to consider
   * @param destinationCrs to consider
   * @param maximumDistanceFerryOutsideBoundingPolygonInMeters to consider
   * @return helper created
   */
  public static OsmProjectedBoundingAreaHelper of(
      OsmBoundary osmBoundary,
      CoordinateReferenceSystem originalCrs,
      CoordinateReferenceSystem destinationCrs,
      double maximumDistanceFerryOutsideBoundingPolygonInMeters) {
    return new OsmProjectedBoundingAreaHelper(
        osmBoundary, originalCrs, destinationCrs, maximumDistanceFerryOutsideBoundingPolygonInMeters);
  }

  /**
   * Factory method
   * @param osmBoundary to consider
   * @param originalCrs to consider
   * @param destinationCountryName to consider
   * @param maximumDistanceFerryOutsideBoundingPolygonInMeters to consider
   * @return helper created
   */
  public static OsmProjectedBoundingAreaHelper of(
      OsmBoundary osmBoundary,
      CoordinateReferenceSystem originalCrs,
      String destinationCountryName,
      double maximumDistanceFerryOutsideBoundingPolygonInMeters) {
    return OsmProjectedBoundingAreaHelper.of(
        osmBoundary,
        originalCrs,
        PlanitCrsUtils.createCoordinateReferenceSystem(ProjectedEpsgCodesByCountry.getEpsg(destinationCountryName)),
        maximumDistanceFerryOutsideBoundingPolygonInMeters);
  }

  /**
   * Factory method for empty instance
   * @return empty instance
   */
  public static OsmProjectedBoundingAreaHelper empty() {
    return new OsmProjectedBoundingAreaHelper();
  }

  /**
   * Check if within bounding area if specified and use lenience for water based infra if so configured
   *
   * @param osmEntity to check
   * @param type of entity
   * @param tags of node
   * @return true when eligible, false otherwise
   */
  public boolean fallsWithinSpatiallyEligibleBoundingArea(
      OsmEntity osmEntity, EntityType type, Map<String, String> tags, OsmNodeData osmNodeData) {
    boolean useWaterLeniency =
        OsmPtv1Tags.isFerryTerminal(tags) || OsmWaterModeTags.supportsAnyPtv2WaterModeAccess(tags);
    return (!useWaterLeniency && isPartlyOrWhollyWithinBoundaryArea(
        osmEntity, type, osmNodeData, true)) ||
        isNearPartlyOrWhollyWithinBoundaryArea(
            osmEntity,
            type,
            osmNodeData,
            maximumDistanceWaterBasedOutsideBoundingPolygonInMeters,true);
  }

  /**
   * Verify if OSM entity (node or way) is within boundary provided.
   *
   * @param entity to check
   * @param type  entity type
   * @param nodeData registered node information
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      OsmEntity entity,
      EntityType type,
      OsmNodeData nodeData,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){

    if(type ==  EntityType.Node){
      return isNearPartlyOrWhollyWithinBoundaryArea(
          (OsmNode) entity, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary);
    }else if(type == EntityType.Way){
      return isNearPartlyOrWhollyWithinBoundaryArea(
          (OsmWay) entity, nodeData, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary);
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
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      OsmEntity entity,
      EntityType type,
      OsmNodeData nodeData,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        entity, type, nodeData, 0.0, isWithinWhenNoBoundary);
  }

  /**
   * Verify if node is within boundary provided.
   *
   * @param osmNode to check
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      OsmNode osmNode,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        OsmNodeUtils.createPoint(osmNode), maxProjectedDistanceToBoundary, isWithinWhenNoBoundary);
  }

  /**
   * Verify if node is within boundary provided.
   *
   * @param osmNode to check
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      OsmNode osmNode,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        osmNode, 0.0, isWithinWhenNoBoundary);
  }

  /**
   * Verify if OSM way is within boundary provided by checking any available OSM node individually (not efficient)
   *
   * @param osmWay to check
   * @param nodeData registered node information
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isPartlyOrWhollyWithinBoundaryArea(
      OsmWay osmWay,
      OsmNodeData nodeData,
      boolean isWithinWhenNoBoundary){
    return isNearPartlyOrWhollyWithinBoundaryArea(
        osmWay, nodeData, 0.0, isWithinWhenNoBoundary);
  }

  /**
   * Verify if OSM way is within boundary provided by checking any available OSM node individually (not efficient)
   *
   * @param osmWay to check
   * @param nodeData registered node information
   * @param maxProjectedDistanceToBoundary to allow
   * @param isWithinWhenNoBoundary when true, true is returned if provided boundary has no polygon defined,
   *                               false otherwise
   * @return true when within boundary, false otherwise
   */
  public boolean isNearPartlyOrWhollyWithinBoundaryArea(
      OsmWay osmWay,
      OsmNodeData nodeData,
      double maxProjectedDistanceToBoundary,
      boolean isWithinWhenNoBoundary){

    boolean anyWithinBoundary = false;
    for(int index = 0; index < osmWay.getNumberOfNodes(); ++index){
      var osmNode = nodeData.getRegisteredOsmNode(osmWay.getNodeId(index));
      if(osmNode != null &&
          isNearPartlyOrWhollyWithinBoundaryArea(
              osmNode, maxProjectedDistanceToBoundary, isWithinWhenNoBoundary)){
        anyWithinBoundary = true;
        break;
      }
    }
    return anyWithinBoundary;
  }
}

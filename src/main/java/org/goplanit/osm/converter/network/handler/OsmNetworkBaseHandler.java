package org.goplanit.osm.converter.network.handler;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import org.geotools.api.referencing.operation.MathTransform;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.*;
import org.goplanit.utils.epsg.ProjectedEpsgCodesByCountry;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.utils.geo.PlanitCrsUtils;
import org.goplanit.utils.geo.PlanitGeometryOperationUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

/**
 * Base handler for networks with common functionality. Requires derived hanlder for concrete implementation.
 * 
 * @author markr
 * 
 *
 */
public abstract class OsmNetworkBaseHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkBaseHandler.class.getCanonicalName());

  /** the network to populate */
  private final PlanitOsmNetwork networkToPopulate;
  
  /** the network data tracking all relevant data during parsing of the osm network */
  private final OsmNetworkReaderData networkData;
  
  /** the settings to adhere to */
  private final OsmNetworkReaderSettings settings;

  /** spatially indexed version of bounding polygon if any for quick comparisons */
  private final PreparedPolygon preppedBoundingPolygonWgs84;

  /** be able to transform from source to projected destination Crs */
  private final MathTransform mathTransformSourceToProjection;

  /** indexed distance facet for fast calculating of distances to bounding polygon in projected CRS, make sure any calcs
   * feed in geometries that are also projected so NOT Wgs84 */
  private final IndexedFacetDistance indexedBoundingPolygonDistProjected;

  /**
   * Constructor
   *
   * @param networkToPopulate to populate
   * @param networkData to use
   * @param settings for the handler
   */
  protected OsmNetworkBaseHandler(
      final PlanitOsmNetwork networkToPopulate,
      final OsmNetworkReaderData networkData,
      final OsmNetworkReaderSettings settings) {

    this.networkToPopulate = networkToPopulate;
    this.settings = settings;
    this.networkData = networkData;

    if(getNetworkData().hasBoundingArea()){
      // prepare polygon for faster checks
      this.preppedBoundingPolygonWgs84 = PlanitGeometryOperationUtils.extractPreparedPolygonForQuickSpatialComparisons(
          getNetworkData().getBoundingArea().getBoundingPolygon());
      // prepare indexed distance faced for fast distance to calcs (in projection so it is not in degrees)
      var projectedCrs =
          PlanitCrsUtils.createCoordinateReferenceSystem(ProjectedEpsgCodesByCountry.getEpsg(settings.getCountryName()));
      this.mathTransformSourceToProjection = PlanitJtsUtils.findMathTransform(settings.getSourceCRS(), projectedCrs);
      var projectedBoundingPolygon = PlanitJtsUtils.transformGeometrySafe(
          getNetworkData().getBoundingArea().getBoundingPolygon(),mathTransformSourceToProjection);
      this.indexedBoundingPolygonDistProjected = new IndexedFacetDistance(projectedBoundingPolygon);
    }else{
      this.preppedBoundingPolygonWgs84 = null;
      this.indexedBoundingPolygonDistProjected = null;
      this.mathTransformSourceToProjection = null;
    }
  }

  /** verify if tags represent a highway or railway that is specifically aimed at road based or rail based infrastructure, e.g.,
   * asphalt or tracks and NOT an area, platform, stops, etc. and is also activated for parsing based on the settings
   * 
   * @param tags to verify
   * @return true when activated and highway or railway (not an area), false otherwise
   */
  protected boolean isActivatedRoadRailOrWaterwayBasedInfrastructure(Map<String, String> tags) {
    
    if(!OsmTags.isArea(tags)) {
      if(settings.isHighwayParserActive() && OsmHighwayTags.hasHighwayKeyTag(tags)) {
        return settings.getHighwaySettings().isOsmHighwayTypeActivated(tags.get(OsmHighwayTags.getHighwayKeyTag()));
      }else if(settings.isRailwayParserActive() && OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return settings.getRailwaySettings().isOsmRailwayTypeActivated(tags.get(OsmRailwayTags.getRailwayKeyTag()));
      }else{
        return isActivatedWaterwayBasedInfrastructure(tags);
      }
    }
    return false;
  }

  protected boolean isActivatedWaterwayBasedInfrastructure(Map<String, String> tags) {
    if(!OsmTags.isArea(tags) && settings.isWaterwayParserActive() && OsmWaterwayTags.isWaterBasedWay(tags)) {
      return settings.getWaterwaySettings().isOsmWaterwayTypeActivated(tags.get(OsmWaterwayTags.getUsedKeyTag(tags)));
    }
    return false;
  }


  /** Wrap the handling of OSM way by checking if it is eligible and catch any run time PLANit exceptions, if eligible
   * delegate to consumer.
   * 
   * @param osmWay to parse
   * @param osmWayConsumer to apply to eligible OSM way
   */
  protected void wrapHandleInfrastructureOsmWay(OsmWay osmWay, BiConsumer<OsmWay, Map<String, String>> osmWayConsumer) {
        
    if(!settings.isOsmWayExcluded(osmWay.getId())) {
      
      Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
      try {                      
        
        /* only parse ways that are potentially road/rail/ferry infrastructure */
        if(isActivatedRoadRailOrWaterwayBasedInfrastructure(tags)) {
          osmWayConsumer.accept(osmWay, tags);
        }
        
      } catch (PlanItRunTimeException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
      }      
    }
  }
 

  protected OsmNetworkReaderSettings getSettings() {
    return settings;
  }
  
  protected OsmNetworkReaderData getNetworkData() {
    return networkData;
  }

  protected PlanitOsmNetwork getNetwork(){
    return this.networkToPopulate;
  }

  protected PreparedPolygon getPreparedBoundingPolygon(){
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
  protected double calculateProjectedDistanceToBoundingPolygon(Point point, boolean applyProjection){
    return indexedBoundingPolygonDistProjected.distance(
        applyProjection ? PlanitJtsUtils.transformGeometrySafe(point, mathTransformSourceToProjection): point);
  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    // nothing yet
  }  
  
}

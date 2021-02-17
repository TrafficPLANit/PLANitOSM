package org.planit.osm.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.planit.osm.handler.PlanitOsmHandlerHelper;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.osm.tags.OsmTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.zoning.Zone;

import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Utilities in relation to parsing osm nodes while constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class PlanitOsmNodeUtils {
  
  /** the logger */
  @SuppressWarnings("unused")
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

  /** find the closest zone to the node location. Note that this method is NOT perfect, it utilises the closest coordinate on
   * the geometry of the zone, but it is likely the closest point lies on a line of the geometry rather than an extreme point. Therefore
   * it is possible that the found zone is not actually closest. So use with caution!
   * 
   * @param osmNode reference
   * @param zones to check against
   * @return zone with the geometry coordinate (or centroid) closest to the osmNode
   * @throws PlanItException thrown if error
   */
  public static Zone findClosestCoordinateToNode(OsmNode osmNode, Set<? extends Zone> zones, PlanitJtsUtils geoUtils) throws PlanItException {
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
        closestZone = zone;
      }
    }
    return closestZone;
  }

  /** Collect the rail modes that are deemed eligible for this entity (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. bus=yes, or when none are marked explicitly we assume the the default (if provided) 
   * 
   * @param osmNode to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmRoadModesOnPtOsmEntity(OsmEntity osmEntity, Map<String, String> tags, String defaultOsmMode) {
    Collection<String> explicitlyIncludedOsmModes = PlanitOsmModeUtils.getOsmRoadModesWithAccessValue(tags, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Collection<String> explicitlyExcludedOsmModes = PlanitOsmModeUtils.getOsmRoadModesWithAccessValue(tags, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        PlanitOsmHandlerHelper.LOGGER.severe(String.format("we currently do not yet support explicitly excluded road modes for PT osm entity %d (platforms, etc.), ignored exclusion of %s", osmEntity.getId(), explicitlyExcludedOsmModes.toString()));
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = Collections.singleton(defaultOsmMode);
    }
    return explicitlyIncludedOsmModes;       
  }

  /** Collect the modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided) 
   * 
   * @param osmNode to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmModesOnPtOsmEntity(OsmEntity osmEntity, Map<String, String> tags, String defaultOsmMode) {
    String defaultRailMode = OsmRailModeTags.isRailModeTag(defaultOsmMode) ? defaultOsmMode : null;
    Collection<String> eligibleOsmModes = PlanitOsmNodeUtils.collectEligibleOsmRailModesOnPtOsmEntity(osmEntity, tags, defaultRailMode);
    String defaultRoadMode = OsmRoadModeTags.isRoadModeTag(defaultOsmMode) ? defaultOsmMode : null;
    Collection<String> eligibleOsmRoadModes = collectEligibleOsmRoadModesOnPtOsmEntity(osmEntity, tags, defaultRoadMode);
    if(eligibleOsmModes != null) {
      eligibleOsmModes.addAll(eligibleOsmRoadModes);
    }else {     
      eligibleOsmModes = eligibleOsmRoadModes;
    }
    return eligibleOsmModes;       
  }

  /** Collect the rail modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided) 
   * 
   * @param osmNode to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmRailModesOnPtOsmEntity(OsmEntity osmEntity, Map<String, String> tags, String defaultOsmMode) {
    Collection<String> explicitlyIncludedOsmModes = PlanitOsmModeUtils.getOsmRailModesWithAccessValue(tags, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Collection<String> explicitlyExcludedOsmModes = PlanitOsmModeUtils.getOsmRailModesWithAccessValue(tags, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        PlanitOsmHandlerHelper.LOGGER.severe(String.format("we currently do not yet support explicitly excluded rail modes for PT osm entity %d (platforms, etc.), ignored exclusion of %s", osmEntity.getId(), explicitlyExcludedOsmModes.toString()));
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = Collections.singleton(defaultOsmMode);
    }
    return explicitlyIncludedOsmModes;       
  }      
    

}

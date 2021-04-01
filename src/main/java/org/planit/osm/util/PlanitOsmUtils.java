package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.planit.osm.converter.reader.PlanitOsmNetworkReaderData;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.tags.OsmPtv2Tags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmSpeedTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.Edge;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.Zone;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities in relation to parsing osm data and constructing a PLANit model from it that are too general to
 * be moved to any of the more specific PlanitOSMXXXUtils classes.
 * 
 * @author markr
 *
 */
public class PlanitOsmUtils {
  
  /** the logger */
  static final Logger LOGGER = Logger.getLogger(PlanitOsmUtils.class.getCanonicalName());
  
  /**
   * convert the unit string to a multipler with respect to km/h (the default unit for speed in OSM)
   * 
   * @param unitString the string containing the unit, e.g. "mph", "knots", etc
   * @return multiplier the multiplier from x to km/h
   * @throws PlanItException thrown when conversion not available
   */
  protected static double determineMaxSpeedUnitMultiplierKmPerHour(final String unitString) throws PlanItException {
    switch (unitString) {
    case OsmSpeedTags.MILES_PER_HOUR:
      return 0.621371;
    case OsmSpeedTags.KNOTS:
      return 0.539957;      
    default:
      throw new PlanItException(String.format("unit conversion to km/h not available from %s",unitString));
    }
  }  
    
  /** regular expression pattern([^0-9]*)([0-9]*\\.?[0-9]+).*(km/h|kmh|kph|mph|knots)?.* used to extract decimal values and unit (if any) where the decimal value is in group two 
   * and the unit in group 3 (indicated by second section of round brackets)*/
  public static final Pattern SPEED_LIMIT_PATTERN = Pattern.compile("([^0-9]*)([0-9]*\\.?[0-9]+).*(km/h|kmh|kph|mph|knots)?.*");    
 
  /**
   * parse an OSM maxSpeedValue tag value and perform unit conversion to km/h if needed
   * @param maxSpeedValue string
   * @return parsed speed limit in km/h
   * @throws PlanItException thrown if error
   */
  public static double parseMaxSpeedValueKmPerHour(final String maxSpeedValue) throws PlanItException {
    PlanItException.throwIfNull(maxSpeedValue, "max speed value is null");
    
    double speedLimitKmh = -1;
    /* split in parts where all that are not a valid numeric speed limit are removed and retained are the potential speed limits */
    Matcher speedLimitMatcher = SPEED_LIMIT_PATTERN.matcher(maxSpeedValue);
    if (!speedLimitMatcher.matches()){
      throw new PlanItException(String.format("invalid value string encountered for maxSpeed: %s",maxSpeedValue));      
    }

    /* speed limit decimal value parsed is in group 2 */
    if(speedLimitMatcher.group(2) !=null) {
      speedLimitKmh = Double.parseDouble(speedLimitMatcher.group(2));
    }
    
    /* speed limit unit value parsed is in group 3 (if any) */
    if(speedLimitMatcher.group(3) != null){
        speedLimitKmh *= determineMaxSpeedUnitMultiplierKmPerHour(speedLimitMatcher.group(3));
    }  
    return speedLimitKmh;    
  }

  /**
   * parse an OSM maxSpeedValue tag value with speeds per lane separated by "|" and perform unit conversion to km/h if needed
   * 
   * @param maxSpeedLanes string, e.g. 100|100|100
   * @return parsed speed limit in km/h
   * @throws PlanItException thrown if error
   */
  public static double[] parseMaxSpeedValueLanesKmPerHour(final String maxSpeedLanes) throws PlanItException {
    PlanItException.throwIfNull(maxSpeedLanes, "max speed lanes value is null");
    
    String[] maxSpeedByLane = maxSpeedLanes.split("|");
    double[] speedLimitKmh = new double[maxSpeedByLane.length];
    for(int index=0;index<maxSpeedByLane.length;++index) {
      speedLimitKmh[index] = parseMaxSpeedValueKmPerHour(maxSpeedByLane[index]);
    }
    return speedLimitKmh;
  }  
  
  /**
   * check if tags contain entries compatible with the provided Pt scheme given that we are verifying an OSM way/node that might reflect
   * a platform, stop, etc.
   *  
   * @param scheme to check against
   * @param tags to verify
   * @return true when present, false otherwise
   */
  public static boolean isCompatibleWith(OsmPtVersionScheme scheme, Map<String, String> tags) {
    if(scheme.equals(OsmPtVersionScheme.VERSION_1)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags) || OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return OsmPtv1Tags.hasPtv1ValueTag(tags);
      }
    }else if(scheme.equals(OsmPtVersionScheme.VERSION_2)) {
      return OsmPtv2Tags.hasPtv2ValueTag(tags);
    }else {
     LOGGER.severe(String.format("unknown OSM public transport scheme %s provided to check compatibility with, ignored",scheme.value()));

    }
    return false;
  }
  
  /** find the zone closest to the passed in osm Entity
   * 
   * @param osmEntity to find closest zone for
   * @param matchedTransferZones to check against
   * @param osmNodes to extract geo information from if needed
   * @param geoUtils used to cmpute distances
   * @return closest zone found
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosestByTransferGroup(OsmEntity osmEntity, Collection<? extends TransferZoneGroup> transferZoneGroups, Map<Long,OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    Set<Zone> closestPerGroup = new HashSet<Zone>();
    for(TransferZoneGroup group : transferZoneGroups) {
      Zone closestOfGroup = findZoneClosest(osmEntity, group.getTransferZones(), osmNodes, geoUtils);
      closestPerGroup.add(closestOfGroup);
    }
    /* now find closest across all groups */
    return findZoneClosest(osmEntity, closestPerGroup, osmNodes, geoUtils);
  }    

  /** find the zone closest to the passed in osm Entity
   * 
   * @param osmEntity to find closest zone for
   * @param matchedTransferZones to check against
   * @param osmNodes to extract geo information from if needed
   * @param geoUtils used to compute distances
   * @return closest zone found
   * @throws PlanItException thrown if error
   */
  public static Zone findZoneClosest(OsmEntity osmEntity, Collection<? extends Zone> zones, Map<Long,OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    EntityType type = Osm4JUtils.getEntityType(osmEntity);
    switch (type) {
    case Node:
      return PlanitOsmNodeUtils.findZoneClosest((OsmNode)osmEntity, zones, geoUtils);
    case Way:
      return PlanitOsmWayUtils.findZoneClosest((OsmWay)osmEntity, zones, osmNodes, geoUtils);      
    default:
      LOGGER.warning(String.format("unsupported osm entity type when finding closest zone to %d",osmEntity.getId()));
      break;
    }
    return null;
  }
  
  /** find the link closest to the passed in osm Entity
   * 
   * @param osmEntity to find closest link for
   * @param edges to check against
   * @param osmNodes to extract geometry of osm entity from
   * @param geoUtils used to compute distances
   * @return closest edge found
   * @throws PlanItException thrown if error
   */
  public static Edge findEdgeClosest(OsmEntity osmEntity, Collection<? extends Edge> edges, Map<Long,OsmNode> osmNodes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    EntityType type = Osm4JUtils.getEntityType(osmEntity);
    switch (type) {
    case Node:
      return PlanitOsmNodeUtils.findEdgeClosest((OsmNode)osmEntity, edges, geoUtils);
    case Way:
      return PlanitOsmWayUtils.findEdgeClosest((OsmWay)osmEntity, edges, osmNodes, geoUtils);      
    default:
      LOGGER.warning(String.format("unsupported osm entity type when finding closest edge to %d",osmEntity.getId()));
      break;
    }
    return null;
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

  /** extract geometry from the osm entity, either a point, line string or polygon
   * 
   * @param osmEntity to extract from
   * @param osmNodes to extract geo information from referenced nodes from in entity
   * @return geometry created
   * @throws PlanItException thrown if error
   */
  public static Geometry extractGeometry(OsmEntity osmEntity, Map<Long, OsmNode> osmNodes) throws PlanItException {
    return extractGeometry(osmEntity, osmNodes, LOGGER.getLevel());
  } 
  
  /** extract geometry from the osm entity, either a point, line string or polygon
   * 
   * @param osmEntity to extract from
   * @param osmNodes to extract geo information from referenced nodes from in entity
   * @param logLevel change to this logLevel during the method call (reinstate original loglevel after)
   * @return geometry created
   * @throws PlanItException thrown if error
   */
  public static Geometry extractGeometry(OsmEntity osmEntity, Map<Long, OsmNode> osmNodes, Level logLevel) throws PlanItException {
    Level originalLogLevel = LOGGER.getLevel();
    LOGGER.setLevel(logLevel);  
    Geometry theGeometry = null;
    if(osmEntity instanceof OsmNode){
      OsmNode osmNode = OsmNode.class.cast(osmEntity);
      try {
        theGeometry = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getX(osmNode), PlanitOsmNodeUtils.getY(osmNode));
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node %d when creating transfer zone", osmNode.getId()));
      }
    }else if(osmEntity instanceof OsmWay) {
      /* either area or linestring */
      OsmWay osmWay = OsmWay.class.cast(osmEntity);
      theGeometry = PlanitOsmWayUtils.extractGeometry(osmWay, osmNodes, logLevel);       
    }
    LOGGER.setLevel(originalLogLevel);
    return theGeometry;
  }  
    

}

package org.goplanit.osm.util;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.goplanit.osm.tags.OsmSpeedTags;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.Edge;
import org.locationtech.jts.geom.Geometry;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities in relation to parsing OSM data and constructing a PLANit model from it that are too general to
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
  private static double determineMaxSpeedUnitMultiplierKmPerHour(final String unitString) throws PlanItException {
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
      return OsmNodeUtils.findEdgeClosest((OsmNode)osmEntity, edges, geoUtils);
    case Way:
      return OsmWayUtils.findEdgeClosest((OsmWay)osmEntity, edges, osmNodes, geoUtils);      
    default:
      LOGGER.warning(String.format("unsupported osm entity type when finding closest edge to %d",osmEntity.getId()));
      break;
    }
    return null;
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
  
  /** extract geometry from the OSM entity, either a point, line string or polygon
   * 
   * @param osmEntity to extract from
   * @param osmNodes to extract geo information from referenced nodes from in entity
   * @param logLevel change to this logLevel during the method call (reinstate original log level after)
   * @return geometry created
   */
  public static Geometry extractGeometry(OsmEntity osmEntity, Map<Long, OsmNode> osmNodes, Level logLevel){
    Level originalLogLevel = LOGGER.getLevel();
    LOGGER.setLevel(logLevel);  
    Geometry theGeometry = null;
    if(osmEntity instanceof OsmNode){
      OsmNode osmNode = OsmNode.class.cast(osmEntity);
      try {
        theGeometry = PlanitJtsUtils.createPoint(OsmNodeUtils.getX(osmNode), OsmNodeUtils.getY(osmNode));
      } catch (PlanItRunTimeException e) {
        LOGGER.severe(String.format("Unable to construct location information for osm node %d when creating transfer zone", osmNode.getId()));
      }
    }else if(osmEntity instanceof OsmWay) {
      /* either area or linestring */
      OsmWay osmWay = OsmWay.class.cast(osmEntity);
      theGeometry = OsmWayUtils.extractGeometry(osmWay, osmNodes, logLevel);       
    }
    LOGGER.setLevel(originalLogLevel);
    return theGeometry;
  }

}

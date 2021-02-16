package org.planit.osm.util;

import java.util.Map;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.planit.osm.tags.OsmDirectionTags;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;
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
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmWayUtils.class.getCanonicalName());
 
  
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
          return Pair.create(index, index2);
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
 
  /** Based on the passed in osmWay collect the coordinates on that way as a coordinate array
   * @param osmWay to extract node coordinates from
   * @param osmNodes to collect nodes from by reference node ids in the way
   * @return coordinate array
   * @throws PlanItException thrown if error
   */
  public static Coordinate[] createCoordinateArray(OsmWay osmWay, Map<Long,OsmNode> osmNodes) throws PlanItException {
    Coordinate[] coordArray = new Coordinate[osmWay.getNumberOfNodes()];
    for(int index = 0 ; index < osmWay.getNumberOfNodes() ; ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode==null) {
        throw new PlanItException(String.format("node %d not available when extracting coordinate array for OSM way %d",osmWay.getNodeId(index), osmWay.getId()));
      }
      coordArray[index] = new Coordinate(PlanitOsmNodeUtils.getXCoordinate(osmNode), PlanitOsmNodeUtils.getYCoordinate(osmNode));
    }
    return coordArray;
  }  

}

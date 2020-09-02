package org.planit.osm.util;

import org.planit.utils.exceptions.PlanItException;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities in relation to parsing osm data and constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class PlanitOsmUtils {
  
 
  /**
   * convert the unit string to a multipler with respect to km/h (the default unit for speed in OSM)
   * 
   * @param unitString the string containing the unit, e.g. "mph", "knots", etc
   * @return multiplier the multiplier from x to km/h
   * @throws PlanItException thrown when conversion not available
   */
  public static double determineMaxSpeedUnitMultiplierKmPerHour(String unitString) throws PlanItException {
    switch (unitString) {
    case OsmSpeedTags.MILES_PER_HOUR:
      return 0.621371;
    case OsmSpeedTags.KNOTS:
      return 0.539957;      
    default:
      throw new PlanItException(String.format("unit conversion to km/h not available from %s",unitString));
    }
  }

  /**
   * parse an OSM maxSpeedValue tag value and perform unit conversion to km/h if needed
   * @param maxSpeedValue string
   * @return parsed speed limit in km/h
   * @throws PlanItException thrown if error
   */
  public static double parseMaxSpeedValueKmPerHour(String maxSpeedValue) throws PlanItException {
    double speedLimitKmh = -1;
    String[] maxSpeedByUnit = maxSpeedValue.split(" ");
    if(maxSpeedByUnit.length>=1) {
      /* km/h */
      speedLimitKmh = Double.parseDouble(maxSpeedByUnit[0]);
      if(maxSpeedByUnit.length==2){
        speedLimitKmh *= determineMaxSpeedUnitMultiplierKmPerHour(maxSpeedByUnit[1]);
      }  
      return speedLimitKmh;
    }
    throw new PlanItException(String.format("invalid value string encountered for maxSpeed: %s",maxSpeedValue));
  }

  /**
   * parse an OSM maxSpeedValue tag value with speeds per lane separated by "|" and perform unit conversion to km/h if needed
   * 
   * @param maxSpeedLanes string, e.g. 100|100|100
   * @return parsed speed limit in km/h
   * @throws PlanItException thrown if error
   */
  public static double[] parseMaxSpeedValueLanesKmPerHour(String maxSpeedLanes) throws PlanItException {    
    String[] maxSpeedByLane = maxSpeedLanes.split("|");
    double[] speedLimitKmh = new double[maxSpeedByLane.length];
    for(int index=0;index<maxSpeedByLane.length;++index) {
      speedLimitKmh[index] = parseMaxSpeedValueKmPerHour(maxSpeedByLane[index]);
    }
    return speedLimitKmh;
  }
  
  /**
   * Verify if passed osmWay is in fact cicular in nature, i.e., a type of roundabout
   * 
   * @param osmWay the way to verify
   * @return true if circular, false otherwise
   */
  public static boolean isCircularWay(OsmWay osmWay) {
    /* a circular road, has:
     * -  more than two nodes...
     * -  ...an end node that is the same as its start node */
    if(osmWay.getNumberOfNodes() > 2 && osmWay.getNodeId(0) == osmWay.getNodeId(osmWay.getNumberOfNodes()-1)) {
      return true;
    }else {
      return false; 
    }
  }  
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getXCoordinate(OsmNode osmNode) {
    return osmNode.getLongitude();
  }
  
  /**
   * collect x coordinate based on mapping between long/lat/ x/y
   * @param osmNode node to collect from
   * @return x coordinate
   */
  public static double getYCoordinate(OsmNode osmNode) {
    return osmNode.getLatitude();
  }     
  

}

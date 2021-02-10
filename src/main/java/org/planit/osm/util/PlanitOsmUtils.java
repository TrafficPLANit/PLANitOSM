package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.planit.osm.tags.OsmDirectionTags;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.osm.tags.OsmSpeedTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Utilities in relation to parsing osm data and constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class PlanitOsmUtils {
  
  /** collect all OSM modes with either preFix:<OSM mode name>= or postFix:<OSM mode name>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param isprefix when true prefix applied, when false, postfix
   * @param alteration, the post or prefix alteration of the mode key
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */    
  protected static Collection<String> getPrefixedOrPostfixedOsmModesWithAccessValue(boolean isprefix, String alteration, Map<String, String> tags, final String... modeAccessValueTags) {
    Set<String> foundModes = new HashSet<String>();    
    
    /* osm modes extracted from road mode category */
    Collection<String> roadModeCategories = OsmRoadModeCategoryTags.getRoadModeCategories();
    for(String roadModeCategory : roadModeCategories) {
      String compositeKey = isprefix ? OsmTagUtils.createCompositeOsmKey(alteration, roadModeCategory) : OsmTagUtils.createCompositeOsmKey(roadModeCategory, alteration);      
      if(tags.containsKey(compositeKey)) {
        String valueTag = tags.get(roadModeCategory).replaceAll(OsmTagUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");        
        for(int index = 0 ; index < modeAccessValueTags.length ; ++index) {
          if(modeAccessValueTags[index].equals(valueTag)){
            foundModes.addAll(OsmRoadModeCategoryTags.getRoadModesByCategory(roadModeCategory));
          }
        }
      }
    }
    
    /* osm road mode */
    Collection<String> roadModes = OsmRoadModeTags.getSupportedRoadModeTags();
    for(String roadMode : roadModes) {
      String compositeKey = isprefix ? OsmTagUtils.createCompositeOsmKey(alteration, roadMode) : OsmTagUtils.createCompositeOsmKey(roadMode, alteration);      
      if(tags.containsKey(compositeKey)){
        String valueTag = tags.get(compositeKey).replaceAll(OsmTagUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");
        for(int index = 0 ; index < modeAccessValueTags.length ; ++index) {
          if(modeAccessValueTags[index].equals(valueTag)){
            foundModes.add(roadMode);
          }
        }
      }
    }    
    return foundModes;
  }   
  
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
        return (osmWay.getNodeId(0) == osmWay.getNodeId(osmWay.getNumberOfNodes()-1));
      }else {
        return findIndicesOfFirstLoop(osmWay, 0 /*consider entire way */)!=null;        
      }
    }
    return false;
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
      isClockWise = PlanitOsmUtils.isCircularWayDefaultDirectionClockwise(countryName);
    }
    /* clockwise stands for forward direction, so when they do not match, all modes are to be excluded, the direction is closed */
    return isClockWise!=isForwardDirection;
  } 
  
  /** collect all OSM modes with key=\<OSM mode name\> value=the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Collection<String> getOsmModesWithAccessValue(Map<String, String> tags, final String... modeAccessValueTags){
    return getPostfixedOsmModesWithAccessValue(null, tags, modeAccessValueTags);
  }
  
  /** collect all OSM modes with key=\<OSM mode name\>:postFix= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */  
  public static Collection<String> getPostfixedOsmModesWithAccessValue(String postFix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmModesWithAccessValue(false, postFix, tags, modeAccessValueTags);
  }  
  
  /** collect all OSM modes with key=preFix:\<OSM mode name\>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */  
  public static Collection<String> getPrefixedOsmModesWithAccessValue(String prefix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmModesWithAccessValue(true, prefix, tags, modeAccessValueTags);
  }  
   

}

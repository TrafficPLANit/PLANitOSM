package org.planit.osm.util;

import java.util.Map;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmSpeedTags;
import org.planit.utils.exceptions.PlanItException;
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
  
  /** regular expression used to identify non-word characters (a-z any case, 0-9 or _) or whitespace*/
  public static final String VALUETAG_SPECIALCHAR_STRIP_REGEX = "[^\\w\\s]";
  
 
  /**
   * convert the unit string to a multipler with respect to km/h (the default unit for speed in OSM)
   * 
   * @param unitString the string containing the unit, e.g. "mph", "knots", etc
   * @return multiplier the multiplier from x to km/h
   * @throws PlanItException thrown when conversion not available
   */
  public static double determineMaxSpeedUnitMultiplierKmPerHour(final String unitString) throws PlanItException {
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
  public static double parseMaxSpeedValueKmPerHour(final String maxSpeedValue) throws PlanItException {
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
  public static double[] parseMaxSpeedValueLanesKmPerHour(final String maxSpeedLanes) throws PlanItException {    
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
   * @param tags of this OSM way
   * @param mustEndAtstart, when true only circular roads where the end node is the start node are identified, when false, any node that appears twice results in
   * a positive result (true is returned)
   * @return true if circular, false otherwise
   */
  public static boolean isCircularRoad(final OsmWay osmWay, final Map<String, String> tags, final boolean mustEndAtstart) {
    /* a circular road, has:
     * -  more than two nodes...
     * -  ...any node that appears at least twice (can be that a way is both circular but the circular component 
     *    is only part of the geometry 
     */
    if(tags.containsKey(OsmHighwayTags.HIGHWAY) && osmWay.getNumberOfNodes() > 2) {
      if(mustEndAtstart) {
        return (osmWay.getNodeId(0) == osmWay.getNodeId(osmWay.getNumberOfNodes()-1));
      }else {
        return findIndicesOfFirstCircle(osmWay, 0 /*consider entire way */)!=null;        
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
  public static Pair<Integer, Integer> findIndicesOfFirstCircle(final OsmWay osmWay, final int initialNodeIndex) {
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
  
  /** verify if the passed in value tag is present in the list of value tags provided
   * 
   * @param valueTag to check
   * @param valueTags to check against
   * @return true when present, false otherwise
   */
  public static boolean matchesAnyValueTag(final String valueTag, final String... valueTags) {
    for(int index=0; index < valueTags.length;++ index) {
      if(valueTag.equals(valueTags[index])) {
        return true;
      }
    }
    return false;
  }
  
  /** verify if the passed in key matches and of the passed in values in the tags provided, all value tags are filtered by applying {@link VALUETAG_SPECIALCHAR_STRIP_REGEX}
   * 
   * @param tags to check existence from
   * @param keyTags to check 
   * @param valueTags to check
   * @return true when match is present, false otherwise
   */    
  public static boolean keyMatchesAnyValueTag(Map<String, String> tags, String keyTag, String... valueTags) {
    return anyKeyMatchesAnyValueTag(tags, new String[] {keyTag}, valueTags);
  }  
  
  /** verify if any of the passed in keys matches and of the passed in values in the tags provided, all value tags are filtered by applying {@link VALUETAG_SPECIALCHAR_STRIP_REGEX}
   * 
   * @param tags to check existence from
   * @param keyTags to check 
   * @param valueTags to check
   * @return true when match is present, false otherwise
   */  
  public static boolean anyKeyMatchesAnyValueTag(final Map<String,String> tags, final String[] keyTags, final String... valueTags) {
    return anyKeyMatchesAnyValueTag(tags, VALUETAG_SPECIALCHAR_STRIP_REGEX, keyTags, valueTags);
  }
  
  /** verify if any of the passed in keys matches and of the passed in values in the tags provided
   * 
   * @param tags to check existence from
   * @param regexFilter filter each value tag in tags by applying this regular expressions and replace matches with "", can be used to strip whitespaces or unwanted characters that cause a mistmach
   * @param keyTags to check 
   * @param valueTags to check
   * @return true when match is present, false otherwise
   */  
  public static boolean anyKeyMatchesAnyValueTag(final Map<String,String> tags, String regEx, final String[] keyTags, final String... valueTags) {
    if(containsAnyKey(tags, keyTags)) {
      for(int index=0; index < keyTags.length;++ index) {
        String currentKey = keyTags[index];
        if(tags.containsKey(currentKey) && matchesAnyValueTag(tags.get(currentKey).replaceAll(regEx, ""), valueTags)) {
          return true;
        }
      }
    }
    return false;
  }       

  /** construct composite key "currentKey:subTagCondition1:subTagCondition2:etc."
   * @param currentKey the currentKey
   * @param subTagConditions to add
   * @return composite version separated by colons;
   */
  public static String createCompositeOsmKey(final String currentKey, final String... subTagConditions) {
    String compositeKey = currentKey;
    if(subTagConditions != null) {    
      for(int index=0;index<subTagConditions.length;++index) {
        String subTag = subTagConditions[index];
        compositeKey  = !subTag.isBlank() ? compositeKey.concat(":").concat(subTag) : compositeKey; 
      }
    }
    return compositeKey;
  }

  /** determine if any of the potential keys is listed in the passed in tags
   * @param tags to check
   * @param potentialKeys to check
   * @return true when present, false otherwise
   */
  public static boolean containsAnyKey(final Map<String, String> tags, final String... potentialKeys) {
    for(int index=0;index<potentialKeys.length;++index) {
      String potentialKey = potentialKeys[index];
      if(tags.containsKey(potentialKey)) {
        return true;
      }
    }
    return false;
  } 

}

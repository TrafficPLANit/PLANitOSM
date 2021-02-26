package org.planit.osm.util;

import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.tags.OsmPtv2Tags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmSpeedTags;
import org.planit.utils.exceptions.PlanItException;

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
   

}

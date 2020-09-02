package org.planit.osm.util;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Class that wraps the direction information captured in the tags of a way
 * 
 * By default if the one way is marked as alternating or reversible then the oneWay flag is set to true
 * while the reverse direction is set to false.
 * 
 * @author markr
 *
 */
public class OsmDirection {
  
  private static final Logger LOGGER = Logger.getLogger(OsmDirection.class.getCanonicalName());  
  
  /** one way flag */
  boolean oneWay = false;
  
  /** reverse direction compared to geometry flag */
  boolean reverseDirection = false;
  
  /** is way reversible flag */
  boolean reversible = false;
  
  /** is reversible lane often alternating in direction flag*/
  boolean alternating = false;

  /** Constructor
   * 
   * @param tags
   */
  public OsmDirection(Map<String, String> tags) {
    boolean oneWayTagPresent = tags.containsKey(OsmTags.ONEWAY);    
    if(oneWayTagPresent) {
     /* determine type of one way */
      String value = tags.get(OsmTags.ONEWAY);
      
      if(!value.equals(OsmOneWayTags.ONE_WAY_NO)) {
        if( value.equals(OsmOneWayTags.ONE_WAY_YES)) {
          oneWay = true;
        }else if(value.equals(OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION)) {
          oneWay = true;
          reverseDirection = true;
        }else if(value.equals(OsmOneWayTags.ALTERNATING)) {
          oneWay = true;
        }else if(value.equals(OsmOneWayTags.REVERSIBLE)) {
          oneWay = true;
        }else {
          LOGGER.warning(String.format("unknown value encountered oneway:%s",value));
        }
      }
    }else {
      boolean junctionTagPresent = tags.containsKey(OsmTags.JUNCTION);
      if(junctionTagPresent) {
        /* determine type of one way */
        String value = tags.get(OsmTags.JUNCTION);
        if(value.equals(OsmJunctionTags.ROUNDABOUT) || value.equals(OsmJunctionTags.CIRCULAR)) {
          /* roundabout (or circular which is a roundabout with no right of way) implies one way */
          oneWay = true;
          
          /* extract direction on roundabout way */
          if(tags.containsKey(OsmTags.DIRECTION) && tags.get(OsmTags.DIRECTION).equals(OsmDirectionTags.CLOCKWISE)) {
            reverseDirection = false;    
          }else {
            /* direction default is anti-clock-wise */
            reverseDirection = true;
          }
          
        }
      }
    }
  }
  
  public boolean isOneWay() {
    return oneWay;
  }
  
  public boolean isReverseDirection() {
    return reverseDirection;
  }

  public boolean isReversible() {
    return reversible;
  }

  public boolean isAlternating() {
    return alternating;
  }  
}

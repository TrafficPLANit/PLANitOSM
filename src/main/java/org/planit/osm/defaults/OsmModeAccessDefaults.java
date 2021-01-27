package org.planit.osm.defaults;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailWayTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.utils.locale.CountryNames;

/**
 * Class representing the default mode access restrictions/allowance for modes for a given
 * highway type.
 * 
 * Disallowed modes take precedence over any other setting, allowed modes take precedence over mode category settings
 * and mode category settings define groups of allowed modes (when not present, it is assumed the category is not allowed as a whole)
 * 
 * @author markr
 *
 */
public class OsmModeAccessDefaults implements Cloneable {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmModeAccessDefaults.class.getCanonicalName());
  
  private final OsmModeAccessDefaultsCategory highwayModeAccessDefaults;
  
  private final OsmModeAccessDefaultsCategory railwayModeAccessDefaults;
  
  /** country for which these defaults hold */
  private String countryName;  
  
 
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaults() {
    this.countryName = CountryNames.GLOBAL;
    this.highwayModeAccessDefaults = new OsmModeAccessDefaultsCategory();
    this.railwayModeAccessDefaults = new OsmModeAccessDefaultsCategory();    
  }
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaults(String countryName) {
    this.countryName = countryName;
    this.highwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(countryName);
    this.railwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(countryName);        
  }  
  
  /**
   * Copy constructor
   */
  public OsmModeAccessDefaults(OsmModeAccessDefaults other) {
    this.countryName = other.countryName;
    this.highwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(other.highwayModeAccessDefaults);
    this.railwayModeAccessDefaults = new OsmModeAccessDefaultsCategory(other.railwayModeAccessDefaults);    
  }  
  
  /** The country for which these defaults hold. In absence of a country, it should return CountryNames.GLOBAL
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }
  
  /** set the country name
   * @param countryName to use
   */
  public void setCountry(String countryName) {
    this.countryName = countryName;
    this.highwayModeAccessDefaults.setCountry(countryName);
    this.railwayModeAccessDefaults.setCountry(countryName);
  }    
  

  /**
   * {@inheritDoc}
   */
  @Override
  public OsmModeAccessDefaults clone() throws CloneNotSupportedException {
    return new OsmModeAccessDefaults(this);
  }  
     

  /**
   * Verify if modes is allowed for given highway type. If none of the allowed/disallowed configuration options includes the passed in mode
   * it is assumed the mode is not allowed
   * 
   * @param osmWayValue to use
   * @param osmMode to verify
   * @return true when allowed, false when disallowed, false if unknown
   */
  public boolean isAllowed(final String osmWayKey, final String osmWayValue, final String osmMode) {
    Boolean isAllowed = Boolean.FALSE;
    
    /* only support recognised key/values */
    if(!(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && (OsmRoadModeTags.isRoadModeTag(osmMode) || OsmRailWayTags.isRailBasedRailway(osmMode))) && 
       !(OsmRailWayTags.isRailwayKeyTag(osmWayKey) && OsmRailWayTags.isRailBasedRailway(osmMode))){
      LOGGER.warning(String.format("unsupported way key:value tag (%s:%s) when checking if mode %s is allowed", osmWayKey, osmWayValue, osmMode));
      return isAllowed;
    }
      
    /* process for road way */
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      isAllowed = highwayModeAccessDefaults.isAllowed(osmWayValue, osmMode);      
    /* process rail mode */
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey)) {
      /* only rail way modes are allowed on dedicated rail ways */
      isAllowed = railwayModeAccessDefaults.isAllowed(osmWayValue, osmMode);
    }else {
      LOGGER.warning(String.format("unknown osm %s value tag %s when checking if modes %s is allowed", osmWayKey, osmWayValue, osmMode));
    }    
    
    return isAllowed;
  }

  /**
   * Collect all Osm modes that are allowed for the given osmHighway type as configured by the user
   * 
   * @param osmWayValueType to use
   * @return allowed OsmModes
   */
  public Collection<String> collectAllowedModes(final String osmWayKey, String osmWayValueType) {
    Set<String> allowedModes = null; 
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayValueType)){
      /* collect all rail and road modes that are allowed, try all because the mode categories make it difficult to collect individual modes otherwise */
      Set<String> allowedRoadModesOnRoad =  OsmRoadModeTags.getSupportedRoadModeTags().stream().filter( roadModeTag -> highwayModeAccessDefaults.isAllowed(osmWayValueType, roadModeTag)).collect(Collectors.toSet());
      Set<String> allowedRailModesOnRoad =  OsmRailWayTags.getSupportedRailModeTags().stream().filter( railModeTag -> highwayModeAccessDefaults.isAllowed(osmWayValueType, railModeTag)).collect(Collectors.toSet());      
      allowedModes = new HashSet<String>();
      allowedModes.addAll(allowedRoadModesOnRoad);
      allowedModes.addAll(allowedRailModesOnRoad);
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey) && OsmRailWayTags.isRailBasedRailway(osmWayValueType)) {
      /* while rail has no categories that complicate identifying mode support, we utilise the same approach for consistency and future flexibility */
      allowedModes =  OsmRailWayTags.getSupportedRailModeTags().stream().filter( railModeTag -> railwayModeAccessDefaults.isAllowed(osmWayValueType, railModeTag)).collect(Collectors.toSet());
    }else {
      LOGGER.warning(String.format("unrecognised osm way key value type %s:%s, no allowed modes can be identified", osmWayKey,osmWayValueType));
    }
    return allowedModes;
  }
  
  /** collect the defaults specifically for highways
   * @return highway mode access defaults
   */
  public OsmModeAccessDefaultsCategory getHighwayModeAccessDefaults() {
    return highwayModeAccessDefaults;
  }

  /** collect the defaults specifically for railways
   * @return railway mode access defaults
   */
  public OsmModeAccessDefaultsCategory getRailwayModeAccessDefaults() {
    return railwayModeAccessDefaults;
  }


}

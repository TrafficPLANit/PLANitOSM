package org.planit.osm.defaults;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRoadModeCategoryTags;
import org.planit.osm.util.OsmRoadModeTags;

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
  
  /** store the allowed mode categories by highway type (least important, after disallowed and allowed modes) */
  private final Map<String, Set<String>> allowedModeCategoriesByHighwayType;
  
  /** store the allowed modes by highway type  (after disallowed modes)*/
  private final Map<String, Set<String>> allowedModesByHighwayType;
  
  /** store the disallowed modes by highway type (most important)*/
  private final Map<String, Set<String>> disallowedModesByHighwayType;
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaults() { 
    this.allowedModeCategoriesByHighwayType = new TreeMap<String, Set<String>>();
    this.allowedModesByHighwayType = new TreeMap<String, Set<String>>();
    this.disallowedModesByHighwayType = new TreeMap<String, Set<String>>();
  }
  
  /**
   * Copy constructor
   */
  public OsmModeAccessDefaults(OsmModeAccessDefaults other) {
    this();
    other.allowedModeCategoriesByHighwayType.forEach( (k,v) -> {allowedModeCategoriesByHighwayType.put(k, new HashSet<String>(v));});
    other.allowedModesByHighwayType.forEach( (k,v) -> {allowedModesByHighwayType.put(k, new HashSet<String>(v));});
    other.disallowedModesByHighwayType.forEach( (k,v) -> {disallowedModesByHighwayType.put(k, new HashSet<String>(v));});
  }  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public OsmModeAccessDefaults clone() throws CloneNotSupportedException {
    return new OsmModeAccessDefaults(this);
  }  
    
  /**
   * add the passed in mode categories as allowed for all its child modes
   * 
   * @param highwayType to use
   * @param osmModeCategories to add
   */
  public void addAllowedModeCategories(String highwayType, String... osmModeCategories) {
    if(OsmHighwayTags.isHighwayTag(highwayType)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        allowedModeCategoriesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
        if(OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategories[index])){
          allowedModeCategoriesByHighwayType.get(highwayType).add(osmModeCategories[index]); 
        }else {
          LOGGER.warning(String.format("unknown mode category tag %s, ignored when adding mode categories to modes access defaults", osmModeCategories[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding mode categories to modes access defaults", highwayType));
    }
  }  
  
  /**
   * remove the passed in mode categories as allowed for all its child modes
   * 
   * @param highwayType to use
   * @param osmModeCategories to remove
   */
  public void removeAllowedModeCategories(String highwayType, String... osmModeCategories) {
    if(OsmHighwayTags.isHighwayTag(highwayType)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        if(OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategories[index])){
          allowedModeCategoriesByHighwayType.get(highwayType).remove(osmModeCategories[index]); 
        }else {
          LOGGER.warning(String.format("unknown mode category tag %s, ignored when removing mode categories from allowed modes access defaults", osmModeCategories[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing mode categories from allowed modes access defaults", highwayType));
    }
  }   
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories
   * 
   * @param highwayType to use
   * @param osmModes to add
   */
  public void addAllowedModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          allowedModesByHighwayType.get(highwayType).add(osmModes[index]); 
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to allowed modes access defaults", highwayType));
    }
  }
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given highway type
   * 
   * @param highwayType to use
   * @param osmModes to remove
   */
  public void removeAllowedModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          allowedModesByHighwayType.get(highwayType).remove(osmModes[index]); 
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from allowed modes access defaults", highwayType));
    }
  }  
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings
   * 
   * @param highwayType to use
   * @param osmModes to disallow
   */
  public void addDisallowedModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          disallowedModesByHighwayType.get(highwayType).add(osmModes[index]); 
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to disallowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to disallowed modes access defaults", highwayType));
    }
  }
  
  /**
   * remove the passed in modes as modes that are NOT allowed access for the given highway type
   * 
   * @param highwayType to use
   * @param osmModes to remove from disallowing
   */
  public void removeDisallowedModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          disallowedModesByHighwayType.get(highwayType).remove(osmModes[index]); 
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from disallowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from disallowed modes access defaults", highwayType));
    }
  }  
  

  /**
   * Verify if modes is allowed for given highway type. If none of the allowed/disallowed configuration options includes the passed in mode
   * it is assumed the mode is not allowed
   * 
   * @param highwayType to use
   * @param osmMode to verify
   * @return true when allowed, false when disallowed, false if unknown
   */
  public boolean isAllowed(String highwayType, String osmMode) {
    Boolean isAllowed = Boolean.FALSE;
    if(OsmHighwayTags.isHighwayTag(highwayType)) {     
      if(OsmRoadModeTags.isRoadModeTag(osmMode)){
        
        /* first verify if it is explicitly NOT allowed */
        if(disallowedModesByHighwayType.containsKey(highwayType) && disallowedModesByHighwayType.get(highwayType).contains(osmMode)) {
          isAllowed = Boolean.FALSE;
        }else if( allowedModesByHighwayType.containsKey(highwayType) && allowedModeCategoriesByHighwayType.get(highwayType).contains(osmMode)){
          /* verify if it is explicitly allowed */
          isAllowed = Boolean.TRUE;
        }else if( allowedModeCategoriesByHighwayType.containsKey(highwayType) && 
            !Collections.disjoint(OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode), allowedModeCategoriesByHighwayType.get(highwayType))) {
          /* verify if any of the categories the mode belongs to is explicitly allowed*/
          isAllowed = Boolean.TRUE;
        }else {
          /* mode is never explicitly allowed or disallowed for this highway type, then we assume it is not allowed */
          isAllowed = Boolean.FALSE;
        }
      }else {
        LOGGER.warning(String.format("unknown mode tag %s, when checking if mode is allowed on highway=%s", osmMode,highwayType));
      }
    }else {
      LOGGER.warning(String.format("unknown highway tag %s when checking if modes %s is allowed", highwayType, osmMode));
    } 
    return isAllowed;
  }
}

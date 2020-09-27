package org.planit.osm.defaults;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRailWayTags;
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
  
  /** store the allowed modes by railway type, railway modes must always be explicitly allowed on railways */
  private final Map<String, Set<String>> allowedModesByRailwayType;  
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaults() { 
    this.allowedModeCategoriesByHighwayType = new TreeMap<String, Set<String>>();
    this.allowedModesByHighwayType = new TreeMap<String, Set<String>>();
    this.disallowedModesByHighwayType = new TreeMap<String, Set<String>>();
    
    this.allowedModesByRailwayType = new TreeMap<String, Set<String>>();
  }
  
  /**
   * Copy constructor
   */
  public OsmModeAccessDefaults(OsmModeAccessDefaults other) {
    this();
    other.allowedModeCategoriesByHighwayType.forEach( (k,v) -> {this.allowedModeCategoriesByHighwayType.put(k, new HashSet<String>(v));});
    other.allowedModesByHighwayType.forEach(          (k,v) -> {this.allowedModesByHighwayType.put(         k, new HashSet<String>(v));});
    other.disallowedModesByHighwayType.forEach(       (k,v) -> {this.disallowedModesByHighwayType.put(      k, new HashSet<String>(v));});
    
    other.allowedModesByRailwayType.forEach(          (k,v) -> {this.allowedModesByRailwayType.put(         k, new HashSet<String>(v));});
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
  public void addAllowedHighwayModeCategories(String highwayType, String... osmModeCategories) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        allowedModeCategoriesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
        String osmModeCategoryValueTag = osmModeCategories[index];
        if(OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategoryValueTag)){
          allowedModeCategoriesByHighwayType.get(highwayType).add(osmModeCategoryValueTag); 
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
  public void removeAllowedHighwayModeCategories(String highwayType, String... osmModeCategories) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        if(allowedModeCategoriesByHighwayType.containsKey(highwayType) && OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategories[index])){
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
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories.
   * Note that rail modes can be explicitly allowed onto roads, e.g. tram, lightrail, indicating a track embedded in the road.
   * 
   * @param highwayType to use
   * @param osmModes to add
   */
  public void addAllowedHighwayModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){
          allowedModesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
          allowedModesByHighwayType.get(highwayType).add(osmModeValueTag); 
        }else if(OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
          /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can add an allowed rail mode to a highway type */
          allowedModeCategoriesByHighwayType.get(highwayType).add(osmModeValueTag); 
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
  public void removeAllowedHighwayModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(allowedModesByHighwayType.containsKey(highwayType)) {
          if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){
            allowedModesByHighwayType.get(highwayType).remove(osmModeValueTag); 
          }else if(OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
            /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can remove an allowed rail mode to a highway type */
            allowedModeCategoriesByHighwayType.get(highwayType).remove(osmModeValueTag); 
          }else {
            LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from allowed modes access defaults", osmModes[index]));
          }
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from allowed modes access defaults", highwayType));
    }
  }  
  
  /**
   * Passed in modes are explicitly allowed access onto the rail link type. Only rail modes are allowed access to railway types
   * 
   * @param railwayType to use
   * @param osmModes to add
   */
  public void addAllowedRailwayModes(String railwayType, String... osmModes) {
    if(OsmRailWayTags.isRailwayValueTag(railwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if( OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
          allowedModesByRailwayType.putIfAbsent(railwayType, new HashSet<>());
          allowedModesByRailwayType.get(railwayType).add(osmModeValueTag); 
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to allowed modes access defaults", railwayType));
    }
  }
  
  /**
   * Passed in modes as modes are no longer allowed access to the given railway type
   * 
   * @param railwayType to use
   * @param osmModes to remove
   */
  public void removeAllowedRailwayModes(String railwayType, String... osmModes) {
    if(OsmRailWayTags.isRailwayValueTag(railwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(allowedModesByRailwayType.containsKey(railwayType) && OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
          allowedModesByRailwayType.get(railwayType).remove(osmModeValueTag); 
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from allowed modes access defaults", railwayType));
    }
  }   
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added as disallowed modes.
   * 
   * @param highwayType to use
   * @param osmModes to disallow
   */
  public void addDisallowedHighwayModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          disallowedModesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
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
  public void removeDisallowedHighwayModes(String highwayType, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(disallowedModesByHighwayType.containsKey(highwayType) && OsmRoadModeTags.isRoadModeTag(osmModes[index])){
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
   * @param osmWayValue to use
   * @param osmMode to verify
   * @return true when allowed, false when disallowed, false if unknown
   */
  public boolean isAllowed(final String osmWayKey, final String osmWayValue, final String osmMode) {
    Boolean isAllowed = Boolean.FALSE;
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && OsmHighwayTags.isHighwayValueTag(osmWayValue)) {    
        
      if(OsmRoadModeTags.isRoadModeTag(osmMode)){          
        /* first verify if it is explicitly NOT allowed */
        if(disallowedModesByHighwayType.containsKey(osmWayValue) && disallowedModesByHighwayType.get(osmWayValue).contains(osmMode)) {
          isAllowed = Boolean.FALSE;
        }else if( allowedModesByHighwayType.containsKey(osmWayValue)) {
          if(allowedModeCategoriesByHighwayType.get(osmWayValue).contains(osmMode)){
            /* verify if it is explicitly allowed */
            isAllowed = Boolean.TRUE;            
          }else {
            Set<String> roadModeCategoriesOfMode = OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode);
            Set<String> allowedRoadModeCategoriesOfHighwayType = allowedModeCategoriesByHighwayType.get(osmWayValue);
            if(roadModeCategoriesOfMode != null && allowedRoadModeCategoriesOfHighwayType != null) {
              /* verify if any of the categories the mode belongs to is explicitly allowed*/
              isAllowed = !Collections.disjoint(OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode), allowedModeCategoriesByHighwayType.get(osmWayValue));
            }else {
              /* either highway type or mode belongs to category with no allowed modes, or does not belong to a category at all */
              isAllowed = Boolean.FALSE;    
            }
          }
        }else {
          /* mode is never explicitly allowed or disallowed for this highway type, then we assume it is not allowed */
          isAllowed = Boolean.FALSE;
        }
      }else if(OsmRailWayTags.isRailwayValueTag(osmMode)) {
        /* rail types on highway must always be explicitly allowed, otherwise they are not allowed */
        isAllowed = allowedModesByHighwayType.containsKey(osmWayValue) && allowedModeCategoriesByHighwayType.get(osmWayValue).contains(osmMode);
      }else {
        LOGGER.warning(String.format("unknown mode tag %s, when checking if mode is allowed on highway=%s", osmMode,osmWayValue));
      }
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey) && OsmRailWayTags.isRailwayValueTag(osmWayValue)) {
      /* only rail way modes are allowed on dedicated rail ways */
      isAllowed = allowedModesByRailwayType.containsKey(osmWayValue);
    }else {
      LOGGER.warning(String.format("unknown highway tag %s when checking if modes %s is allowed", osmWayValue, osmMode));
    } 
    return isAllowed;
  }

  /**
   * Collect all Osm modes that are allowed for the given osmHighway type as configured by the user
   * 
   * @param osmWayType to use
   * @return allowed OsmModes
   */
  public Collection<String> collectAllowedModes(final String osmWayKey, String osmWayType) {
    Set<String> allowedModes = null; 
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)){
      /* collect all rail and road modes that are allowed, try all because the mode categories make it difficult to collect individual modes otherwise */
      allowedModes =  OsmRoadModeTags.getSupportedRoadModeTags().stream().filter( roadModeTag -> this.isAllowed(OsmHighwayTags.HIGHWAY, osmWayType, roadModeTag)).collect(Collectors.toSet());
      Set<String> allowedRailModes  =  OsmRailWayTags.getSupportedRailModeTags().stream().filter( railModeTag -> this.isAllowed(OsmRailWayTags.RAILWAY, osmWayType, railModeTag)).collect(Collectors.toSet());
      allowedModes.addAll(allowedRailModes);      
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey)) {
      /* rail is always one-on-one allowed mode mapping, so simply copy the allowed modes */
      allowedModes = new HashSet<String>(allowedModesByRailwayType.get(osmWayKey));
    }
    return allowedModes;
  }
}

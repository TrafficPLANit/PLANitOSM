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

  the country name to use if set CONTINUE HERE FOLLOW APPROACH IN OSMSPEEDLImITSBYCOUNTRY -> THEN DEBUG IF ROUNDABOUTS ARE NO IN THE RIGHT DIRECTION
  private String countryName;  
  
  
  /**
   * same as {@code addAllowedHighwayModes} only we do not log the changes as these are default settings.
   * 
   * @param highwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  void addDefaultAllowedHighwayModes(String highwayType, String... osmModes) {
    addAllowedHighwayModes(highwayType, false /* do not log */, osmModes);
  }
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories.
   * Note that rail modes can be explicitly allowed onto roads, e.g. tram, lightrail, indicating a track embedded in the road.
   * 
   * @param highwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  protected void addAllowedHighwayModes(String highwayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){
          allowedModesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
          allowedModesByHighwayType.get(highwayType).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional road mode %s to highway:%s", osmModeValueTag, highwayType));
          }
        }else if(OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
          /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can add an allowed rail mode to a highway type */
          allowedModesByHighwayType.get(highwayType).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional railway mode %s to highway:%s", osmModeValueTag, highwayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to allowed modes access defaults", osmModeValueTag));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to allowed modes access defaults", highwayType));
    }
  }
  
  /**
   * add the default passed in mode categories as allowed for all its child modes, no logging
   * 
   * @param highwayType to use
   * @param osmModeCategories to add
   */
  void addDefaultAllowedHighwayModeCategories(String highwayType, String... osmModeCategories) {
    addAllowedHighwayModeCategories(highwayType, false, osmModeCategories);
  }  
  
  /**
   * add the passed in mode categories as allowed for all its child modes
   * 
   * @param highwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModeCategories to add
   */
  protected void addAllowedHighwayModeCategories(String highwayType, boolean logChanges, String... osmModeCategories) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        allowedModeCategoriesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
        String osmModeCategoryValueTag = osmModeCategories[index];
        if(OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategoryValueTag)){
          allowedModeCategoriesByHighwayType.get(highwayType).add(osmModeCategoryValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional allowed mode category %s to highway:%s", osmModeCategoryValueTag, highwayType));            
          }          
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
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModeCategories to remove
   */
  protected void removeAllowedHighwayModeCategories(String highwayType, boolean logChanges, String... osmModeCategories) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        if(allowedModeCategoriesByHighwayType.containsKey(highwayType) && OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategories[index])){
          allowedModeCategoriesByHighwayType.get(highwayType).remove(osmModeCategories[index]);
          if(logChanges) {
            LOGGER.info(String.format("removed allowed road mode category %s from highway:%s", osmModeCategories[index], highwayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode category tag %s, ignored when removing mode categories from allowed modes access defaults", osmModeCategories[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing mode categories from allowed modes access defaults", highwayType));
    }
  }   
  
  /**
   * remove the default modes from access for the given highway type
   * 
   * @param highwayType to use
   * @param osmModes to remove
   */
  void removeDefaultAllowedHighwayModes(String highwayType, boolean logChanges, String... osmModes) {
    removeAllowedHighwayModes(highwayType, false, osmModes);
  }
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given highway type
   * 
   * @param highwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove
   */
  protected void removeAllowedHighwayModes(String highwayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(allowedModesByHighwayType.containsKey(highwayType)) {
          if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){
            allowedModesByHighwayType.get(highwayType).remove(osmModeValueTag);
            if(logChanges) {
              LOGGER.info(String.format("removed allowed road mode %s from highway:%s", osmModeValueTag, highwayType));
            }
          }else if(OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
            /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can remove an allowed rail mode to a highway type */
            allowedModeCategoriesByHighwayType.get(highwayType).remove(osmModeValueTag);
            if(logChanges) {
              LOGGER.info(String.format("removed allowed rail mode %s from railway:%s", osmModeValueTag, highwayType));
            }
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
   * default modes are explicitly allowed access onto the rail link type. Only rail modes are allowed access to railway types (no logging)
   * 
   * @param railwayType to use
   * @param osmModes to add
   */
  void addDefaultAllowedRailwayModes(String railwayType, String... osmModes) {  
    addAllowedRailwayModes(railwayType, false, osmModes);
  }
  
  /**
   * Passed in modes are explicitly allowed access onto the rail link type. Only rail modes are allowed access to railway types
   * 
   * @param railwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  protected void addAllowedRailwayModes(String railwayType, boolean logChanges, String... osmModes) {
    if(OsmRailWayTags.isRailwayValueTag(railwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if( OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){
          allowedModesByRailwayType.putIfAbsent(railwayType, new HashSet<>());
          allowedModesByRailwayType.get(railwayType).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional allowed rail mode %s to railway:%s", osmModeValueTag, railwayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to allowed modes access defaults", railwayType));
    }
  }
  
  /**
   * Passed in default modes as modes are removed from access to the given railway type (no logging)
   * 
   * @param railwayType to use
   * @param osmModes to remove
   */
  void removeDefaultAllowedRailwayModes(String railwayType, String... osmModes) {
    removeAllowedRailwayModes(railwayType, false, osmModes);
  }
  
  /**
   * Passed in modes as modes are no longer allowed access to the given railway type
   * 
   * @param railwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove
   */
  protected void removeAllowedRailwayModes(String railwayType, boolean logChanges, String... osmModes) {
    if(OsmRailWayTags.isRailwayValueTag(railwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(allowedModesByRailwayType.containsKey(railwayType) && OsmRailWayTags.isRailwayValueTag(osmModeValueTag)){          
          allowedModesByRailwayType.get(railwayType).remove(osmModeValueTag); 
          if(logChanges) {
            LOGGER.info(String.format("removing allowed rail mode %s from railway:%s", osmModeValueTag, railwayType));
          }          
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from allowed modes access defaults", railwayType));
    }
  }
  
  /**
   * add the passed in default modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added as disallowed modes (no logging).
   * 
   * @param highwayType to use
   * @param osmModes to disallow
   */
  void addDefaultDisallowedHighwayModes(String highwayType, String... osmModes) {
    addDisallowedHighwayModes(highwayType, false, osmModes);
  }   
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added as disallowed modes.
   * 
   * @param highwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to disallow
   */
  protected void addDisallowedHighwayModes(String highwayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          disallowedModesByHighwayType.putIfAbsent(highwayType, new HashSet<String>());
          disallowedModesByHighwayType.get(highwayType).add(osmModes[index]);
          if(logChanges) {
            LOGGER.info(String.format("disallowed road mode %s from highway:%s", osmModes[index], highwayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to disallowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to disallowed modes access defaults", highwayType));
    }
  }  
  
  /**
   * remove the passed in default modes as modes that are NOT allowed access for the given highway type (no logging)
   * 
   * @param highwayType to use
   * @param osmModes to remove from disallowing
   */
  void removeDefaultDisallowedHighwayModes(String highwayType, String... osmModes) {
    removeDisallowedHighwayModes(highwayType, false, osmModes);
  }    
  
  /**
   * remove the passed in modes as modes that are NOT allowed access for the given highway type
   * 
   * @param highwayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove from disallowing
   */
  protected void removeDisallowedHighwayModes(String highwayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isHighwayValueTag(highwayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(disallowedModesByHighwayType.containsKey(highwayType) && OsmRoadModeTags.isRoadModeTag(osmModes[index])){
          disallowedModesByHighwayType.get(highwayType).remove(osmModes[index]);
          if(logChanges) {
            LOGGER.info(String.format("removed disallowed road mode %s from highway:%s", osmModes[index], highwayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from disallowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from disallowed modes access defaults", highwayType));
    }
  }   
  
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
   * Default constructor
   */
  public OsmModeAccessDefaults(String countryName) {
    this.countryName = countryName;
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
    this.countryName = other.getcountryName();
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
    addAllowedHighwayModeCategories(highwayType, true /* log changes */, osmModeCategories);
  }  
  
  /**
   * remove the passed in mode categories as allowed for all its child modes
   * 
   * @param highwayType to use
   * @param osmModeCategories to remove
   */
  public void removeAllowedHighwayModeCategories(String highwayType, String... osmModeCategories) {
    removeAllowedHighwayModeCategories(highwayType, true /* log changes*/ , osmModeCategories);
  }   
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories.
   * Note that rail modes can be explicitly allowed onto roads, e.g. tram, lightrail, indicating a track embedded in the road.
   * 
   * @param highwayType to use
   * @param osmModes to add
   */
  public void addAllowedHighwayModes(String highwayType, String... osmModes) {
    addAllowedHighwayModes(highwayType, true /* log changes*/, osmModes);    
  }
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given highway type
   * 
   * @param highwayType to use
   * @param osmModes to remove
   */
  public void removeAllowedHighwayModes(String highwayType, String... osmModes) {
    removeAllowedHighwayModes(highwayType, true /* log changes*/, osmModes);
  }  
  
  /**
   * Passed in modes are explicitly allowed access onto the rail link type. Only rail modes are allowed access to railway types
   * 
   * @param railwayType to use
   * @param osmModes to add
   */
  public void addAllowedRailwayModes(String railwayType, String... osmModes) {
    addAllowedRailwayModes(railwayType, true /* log changes */, osmModes);
  }
  
  /**
   * Passed in modes as modes are no longer allowed access to the given railway type
   * 
   * @param railwayType to use
   * @param osmModes to remove
   */
  public void removeAllowedRailwayModes(String railwayType, String... osmModes) {
    removeAllowedRailwayModes(railwayType, true /* log changes */, osmModes);
  }   
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added as disallowed modes.
   * 
   * @param highwayType to use
   * @param osmModes to disallow
   */
  public void addDisallowedHighwayModes(String highwayType, String... osmModes) {
    addDisallowedHighwayModes(highwayType, true /* log changes */, osmModes);
  }
  
  /**
   * remove the passed in modes as modes that are NOT allowed access for the given highway type
   * 
   * @param highwayType to use
   * @param osmModes to remove from disallowing
   */
  public void removeDisallowedHighwayModes(String highwayType, String... osmModes) {
    removeDisallowedHighwayModes(highwayType, true /* log changes */, osmModes);
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
    if(!(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && (OsmRoadModeTags.isRoadModeTag(osmMode) || OsmRailWayTags.isRailwayValueTag(osmMode))) && 
       !(OsmRailWayTags.isRailwayKeyTag(osmWayKey) && OsmRailWayTags.isRailwayValueTag(osmMode))){
      LOGGER.warning(String.format("unsupported way key:value tag (%s:%s) when checking if mode %s is allowed", osmWayKey, osmWayValue, osmMode));
      return isAllowed;
    }
      
    /* process for road way */
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      
      if(allowedModesByHighwayType.containsKey(osmWayValue)){
        /* verify if it is explicitly allowed */
        isAllowed = allowedModesByHighwayType.get(osmWayValue).contains(osmMode);            
      }
      
      /* when not yet explicitly allowed, verify if it is allowed via an umbrella mode category */
      if(!isAllowed) {
        Set<String> roadModeCategoriesOfMode = OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode);
        Set<String> allowedRoadModeCategoriesOfHighwayType = allowedModeCategoriesByHighwayType.get(osmWayValue);
        if(roadModeCategoriesOfMode != null && allowedRoadModeCategoriesOfHighwayType != null) {
          /* verify if any of the categories the mode belongs to is explicitly allowed*/
          isAllowed = !Collections.disjoint(OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode), allowedModeCategoriesByHighwayType.get(osmWayValue));
        }        
      }
      
      /* when allowed (via category), it can be that it is explicitly disallowed within its category overruling the category setting */
      if(isAllowed) {
          isAllowed = !(disallowedModesByHighwayType.containsKey(osmWayValue) && disallowedModesByHighwayType.get(osmWayValue).contains(osmMode));
      }  
      
    /* process rail mode */
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey)) {
      /* only rail way modes are allowed on dedicated rail ways */
      isAllowed = allowedModesByRailwayType.containsKey(osmWayValue);
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
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && OsmHighwayTags.isHighwayValueTag(osmWayValueType)){
      /* collect all rail and road modes that are allowed, try all because the mode categories make it difficult to collect individual modes otherwise */
      Set<String> allowedRoadModesOnRoad =  OsmRoadModeTags.getSupportedRoadModeTags().stream().filter( roadModeTag -> this.isAllowed(OsmHighwayTags.HIGHWAY, osmWayValueType, roadModeTag)).collect(Collectors.toSet());
      Set<String> allowedRailModesOnRoad =  OsmRailWayTags.getSupportedRailModeTags().stream().filter( railModeTag -> this.isAllowed(OsmHighwayTags.HIGHWAY, osmWayValueType, railModeTag)).collect(Collectors.toSet());      
      allowedModes = new HashSet<String>();
      allowedModes.addAll(allowedRoadModesOnRoad);
      allowedModes.addAll(allowedRailModesOnRoad);
    }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey) && OsmRailWayTags.isRailwayValueTag(osmWayValueType)) {
      /* rail is always one-on-one allowed mode mapping, so simply copy the allowed modes (if any)*/      
      allowedModes = new HashSet<String>(allowedModesByRailwayType.getOrDefault(osmWayValueType, new HashSet<String>()));
    }else {
      LOGGER.warning(String.format("unrecognised osm way key value type %s:%s, no allowed modes can be identified", osmWayKey,osmWayValueType));
    }
    return allowedModes;
  }
}

package org.planit.osm.defaults;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.utils.locale.CountryNames;

/**
 * Class representing the default mode access restrictions/allowance for modes for a given
 * category of OSM ways, e.g. highway, railway etc., although the class itself is agnostic to what category it belongs
 * 
 * Disallowed modes take precedence over any other setting, allowed modes take precedence over mode category settings
 * and mode category settings define groups of allowed modes (when not present, it is assumed the category is not allowed as a whole)
 * 
 * @author markr
 *
 */
public class OsmModeAccessDefaultsCategory implements Cloneable {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmModeAccessDefaultsCategory.class.getCanonicalName());
  
  /** store the disallowed modes by highway type (most important)*/
  private final Map<String, Set<String>> disallowedModesByType;
  
  /** store the allowed modes by highway type  (after disallowed modes)*/
  private final Map<String, Set<String>> allowedModesByType;  
  
  /** store the allowed mode categories by highway type (least important, after disallowed and allowed modes) */
  private final Map<String, Set<String>> allowedModeCategoriesByType;
    

  /** country for which these defaults hold */
  private String countryName;  
  
  
  /**
   * same as {@code addAlloweyModes} only we do not log the changes as these are default settings.
   * 
   * @paramtypee to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  void addDefaultAllowedModes(String type, String... osmModes) {
   addAllowedModes(type, false /* do not log */, Arrays.asList(osmModes));
  }
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories.
   * 
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  protected void addAllowedModes(String type, boolean logChanges, List<String> osmModes) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      for(String osmModeValueTag : osmModes) {
        if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){
         allowedModesByType.putIfAbsent(type, new HashSet<String>());
         allowedModesByType.get(type).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional road mode %s to highway:%s", osmModeValueTag,type));
          }
        }else if(OsmRailwayTags.isRailBasedRailway(osmModeValueTag)){
          /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can add an allowed rail mode to a highway type */
         allowedModesByType.get(type).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional railway mode %s to highway:%s", osmModeValueTag,type));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to allowed modes access defaults", osmModeValueTag));
        }
      } 
    }else if(OsmRailwayTags.isRailBasedRailway(type)) {
      for(String osmModeValueTag : osmModes) {
        if( OsmRailwayTags.isRailBasedRailway(OsmRailModeTags.convertModeToRailway(osmModeValueTag))){
          allowedModesByType.putIfAbsent(type, new HashSet<>());
          allowedModesByType.get(type).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional allowed rail mode %s to railway:%s", osmModeValueTag, type));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to allowed modes access defaults", osmModeValueTag));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown way tag key %s, ignored when adding modes to allowed modes access defaults", type));
    }
  }
  
  /**
   * set the passed in modes as the only modes that are explicitly allowed access regardless of the mode category, 
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  protected void setAllowedModes(String type, boolean logChanges, List<String> osmModes) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      
      /* reset before replacing */
      allowedModeCategoriesByType.remove(type);
      allowedModesByType.putIfAbsent(type, new HashSet<String>());
      allowedModesByType.get(type).clear();
      
      for(String osmModeValueTag : osmModes) {
        if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){                  
          allowedModesByType.get(type).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("set allowed road mode %s to highway:%s", osmModeValueTag,type));
          }
        }else if(OsmRailwayTags.isRailBasedRailway(osmModeValueTag)){
          /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can add an allowed rail mode to a highway type */
         allowedModesByType.get(type).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("set additional railway mode %s to highway:%s", osmModeValueTag,type));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when setting allowed modes for %s", osmModeValueTag, type));
        }
      } 
    }else if(OsmRailwayTags.isRailBasedRailway(type)) {
      
      /* reset before replacing */
      allowedModeCategoriesByType.remove(type);
      allowedModesByType.putIfAbsent(type, new HashSet<String>());
      allowedModesByType.get(type).clear();    
      
      for(String osmModeValueTag : osmModes) {
        if( OsmRailwayTags.isRailBasedRailway(OsmRailModeTags.convertModeToRailway(osmModeValueTag))){
          allowedModesByType.get(type).add(osmModeValueTag);
          if(logChanges) {
            LOGGER.info(String.format("set allowed rail mode %s to railway:%s", osmModeValueTag, type));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when setting modes to allowed modes access for %s", osmModeValueTag, type));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown way tag key %s, ignored when adding modes to allowed modes access defaults", type));
    }
  }  
  
  /**
   * add the default passed in mode categories as allowed for all its child modes, no logging
   * 
   * @param wayType to use
   * @param osmModeCategories to add
   */
  void addDefaultAllowedModeCategories(String wayType, String... osmModeCategories) {
    addAllowedModeCategories(wayType, false, osmModeCategories);
  }  
  
  /**
   * add the passed in mode categories as allowed for all its child modes
   * 
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModeCategories to add
   */
  protected void addAllowedModeCategories(String type, boolean logChanges, String... osmModeCategories) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
       allowedModeCategoriesByType.putIfAbsent(type, new HashSet<String>());
        String osmModeCategoryValueTag = osmModeCategories[index];
        if(OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategoryValueTag)){
         allowedModeCategoriesByType.get(type).add(osmModeCategoryValueTag);
          if(logChanges) {
            LOGGER.info(String.format("added additional allowed mode category %s to highway:%s", osmModeCategoryValueTag, type));            
          }          
        }else {
          LOGGER.warning(String.format("unknown mode category tag %s, ignored when adding mode categories to modes access defaults", osmModeCategories[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("mode categories only suited for highway tags at this point and %s is unknown, ignored when adding mode categories to modes access defaults", type));
    }
  }    
  
  /**
   * remove the passed in mode categories as allowed for all its child modes
   * 
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModeCategories to remove
   */
  protected void removeAllowedModeCategories(String type, boolean logChanges, String... osmModeCategories) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        if(allowedModeCategoriesByType.containsKey(type) && OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategories[index])){
         allowedModeCategoriesByType.get(type).remove(osmModeCategories[index]);
          if(logChanges) {
            LOGGER.info(String.format("removed allowed road mode category %s from highway:%s", osmModeCategories[index], type));
          }
        }else {
          LOGGER.warning(String.format("unknown mode category tag %s, ignored when removing mode categories from allowed modes access defaults", osmModeCategories[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing mode categories from allowed modes access defaults", type));
    }
  }   
  
  /**
   * remove the default modes from access for the given way type
   * 
   * @param wayType to use
   * @param osmModes to remove
   */
  void removeDefaultAllowedModes(String wayType, boolean logChanges, String... osmModes) {
    removeAllowedModes(wayType, false, osmModes);
  }
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given way type
   * 
   * @param wayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove
   */
  protected void removeAllowedModes(String wayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(wayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(allowedModesByType.containsKey(wayType)) {
          if(OsmRoadModeTags.isRoadModeTag(osmModeValueTag)){
           allowedModesByType.get(wayType).remove(osmModeValueTag);
            if(logChanges) {
              LOGGER.info(String.format("removed allowed road mode %s from highway:%s", osmModeValueTag, wayType));
            }
          }else if(OsmRailwayTags.isRailBasedRailway(osmModeValueTag)){
            /* in some cases a rail mode can be embedded in a street, e.g. tram tracks, in which case we can remove an allowed rail mode to a highway type */
           allowedModeCategoriesByType.get(wayType).remove(osmModeValueTag);
            if(logChanges) {
              LOGGER.info(String.format("removed allowed rail mode %s from railway:%s", osmModeValueTag, wayType));
            }
          }else {
            LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from allowed modes access defaults", osmModes[index]));
          }
        }
      } 
    }else if(OsmRailwayTags.isRailBasedRailway(wayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        String osmModeValueTag = osmModes[index];
        if(allowedModesByType.containsKey(wayType) && OsmRailwayTags.isRailBasedRailway(osmModeValueTag)){          
          allowedModesByType.get(wayType).remove(osmModeValueTag); 
          if(logChanges) {
            LOGGER.info(String.format("removing allowed rail mode %s from railway:%s", osmModeValueTag, wayType));
          }          
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from allowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown way tag %s, ignored when removing modes from allowed modes access defaults", wayType));
    }
  }
  
  /**
   * add the passed in default modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * 
   * @param wayType to use
   * @param osmModes to disallow
   */
  void addDefaultDisallowedModes(String wayType, String... osmModes) {
    addDisallowedModes(wayType, false, osmModes);
  }   
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added as disallowed modes.
   * 
   * @param wayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to disallow
   */
  protected void addDisallowedModes(String wayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(wayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(OsmRoadModeTags.isRoadModeTag(osmModes[index])){
         disallowedModesByType.putIfAbsent(wayType, new HashSet<String>());
         disallowedModesByType.get(wayType).add(osmModes[index]);
          if(logChanges) {
            LOGGER.info(String.format("disallowed road mode %s from highway:%s", osmModes[index], wayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when adding modes to disallowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when adding modes to disallowed modes access defaults", wayType));
    }
  }  
  
  /**
   * remove the passed in default modes as modes that are NOT allowed access for the given way type (no logging)
   * 
   * @param wayType to use
   * @param osmModes to remove from disallowing
   */
  void removeDefaultDisallowedModes(String wayType, String... osmModes) {
    removeDisallowedModes(wayType, false, osmModes);
  }    
  
  /**
   * remove the passed in modes as modes that are NOT allowed access for the given highway type
   * 
   * @param wayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove from disallowing
   */
  protected void removeDisallowedModes(String wayType, boolean logChanges, String... osmModes) {
    if(OsmHighwayTags.isRoadBasedHighwayValueTag(wayType)) {
      for(int index = 0; index < osmModes.length ; ++index) {
        if(disallowedModesByType.containsKey(wayType) && OsmRoadModeTags.isRoadModeTag(osmModes[index])){
         disallowedModesByType.get(wayType).remove(osmModes[index]);
          if(logChanges) {
            LOGGER.info(String.format("removed disallowed road mode %s from highway:%s", osmModes[index], wayType));
          }
        }else {
          LOGGER.warning(String.format("unknown mode tag %s, ignored when removing modes from disallowed modes access defaults", osmModes[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("unknown highway tag %s, ignored when removing modes from disallowed modes access defaults", wayType));
    }
  }   
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaultsCategory() { 
    this.countryName = CountryNames.GLOBAL;
    this.allowedModeCategoriesByType = new TreeMap<String, Set<String>>();
    this.allowedModesByType = new TreeMap<String, Set<String>>();
    this.disallowedModesByType = new TreeMap<String, Set<String>>();    
  }
  
  /**
   * Default constructor
   * 
   * @param countryName to use
   */
  public OsmModeAccessDefaultsCategory(String countryName) {
    this.countryName = countryName;
    this.allowedModeCategoriesByType = new TreeMap<String, Set<String>>();
    this.allowedModesByType = new TreeMap<String, Set<String>>();
    this.disallowedModesByType = new TreeMap<String, Set<String>>();
  }  
  
  /**
   * Copy constructor
   * 
   * @param other to copy from
   */
  public OsmModeAccessDefaultsCategory(OsmModeAccessDefaultsCategory other) {
    this();
    this.countryName = other.getCountry();
    other.allowedModeCategoriesByType.forEach( (k,v) -> {this.allowedModeCategoriesByType.put(k, new HashSet<String>(v));});
    other.allowedModesByType.forEach(          (k,v) -> {this.allowedModesByType.put(         k, new HashSet<String>(v));});
    other.disallowedModesByType.forEach(       (k,v) -> {this.disallowedModesByType.put(      k, new HashSet<String>(v));});    
  }  
  
  /** The country for which these defaults hold. In absence of a country, it should return CountryNames.GLOBAL
   * 
   * @return country name
   */
  public String getCountry() {
    return this.countryName;
  }
  
  /** Set the country name that represents these defaults
   * 
   * @param countryName to set
   */
  public void setCountry(String countryName) {
    this.countryName = countryName;
  }  

  /**
   * {@inheritDoc}
   */
  @Override
  public OsmModeAccessDefaultsCategory clone() throws CloneNotSupportedException {
    return new OsmModeAccessDefaultsCategory(this);
  }  
    
  /**
   * add the passed in mode categories as allowed for all its child modes
   * 
   * @param wayType to use
   * @param osmModeCategories to add
   */
  public void addAllowedModeCategories(String wayType, String... osmModeCategories) {
    addAllowedModeCategories(wayType, true /* log changes */, osmModeCategories);
  }  
  
  /**
   * remove the passed in mode categories as allowed for all its child modes
   * 
   * @param wayType to use
   * @param osmModeCategories to remove
   */
  public void removeAllowedModeCategories(String wayType, String... osmModeCategories) {
    removeAllowedModeCategories(wayType, true /* log changes*/ , osmModeCategories);
  }   
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories.
   * Note that rail modes can be explicitly allowed onto roads, e.g. tram, lightrail, indicating a track embedded in the road.
   * 
   * @param wayType to use
   * @param osmModes to add
   */
  public void addAllowedModes(String wayType, String... osmModes) {
   addAllowedModes(wayType, Arrays.asList(osmModes));    
  }
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e., this takes precedence over the categories.
   * Note that rail modes can be explicitly allowed onto roads, e.g. tram, lightrail, indicating a track embedded in the road.
   * 
   * @param wayType to use
   * @param osmModes to add
   */
  public void addAllowedModes(String wayType, List<String> osmModes) {
   addAllowedModes(wayType, true /* log changes*/, osmModes);    
  }  
  
  /**
   * set the passed in modes as the only modes that are explicitly allowed access and remove all categories
   * 
   * @param wayType to use
   * @param osmModes to add
   */
  public void setAllowedModes(String wayType, List<String> osmModes) {
   setAllowedModes(wayType, true /* log changes*/, osmModes);    
  }    
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given highway type
   * 
   * @param wayType to use
   * @param osmModes to remove
   */
  public void removeAllowedModes(String wayType, String... osmModes) {
    removeAllowedModes(wayType, true /* log changes*/, osmModes);
  }    
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added as disallowed modes.
   * 
   * @param wayType to use
   * @param osmModes to disallow
   */
  public void addDisallowedModes(String wayType, String... osmModes) {
    addDisallowedModes(wayType, true /* log changes */, osmModes);
  }
  
  /**
   * Remove the passed in modes as modes that are NOT allowed access for the given highway type
   * 
   * @param wayType to use
   * @param osmModes to remove from disallowing
   */
  public void removeDisallowedModes(String wayType, String... osmModes) {
    removeDisallowedModes(wayType, true /* log changes */, osmModes);
  }  
  

  /**
   * Verify if mode is allowed for given way type. If none of the allowed/disallowed configuration options includes the passed in mode
   * it is assumed the mode is not allowed
   * 
   * @paam osmWayValue to check for
   * @param osmMode to verify
   * @return true when allowed, false when disallowed, false if unknown
   */
  public boolean isAllowed(final String osmWayValue, final String osmMode) {
    Boolean isAllowed = Boolean.FALSE;    
               
    if(allowedModesByType.containsKey(osmWayValue)){
      /* verify if it is explicitly allowed */
      isAllowed =allowedModesByType.get(osmWayValue).contains(osmMode);            
    }
      
    /* when not yet explicitly allowed, verify if it is allowed via an umbrella mode category (for now these only exist for road modes)*/   
    if(!isAllowed) {
      Set<String> roadModeCategoriesOfMode = OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode);
      Set<String> allowedRoadModeCategoriesOfHighwayType =allowedModeCategoriesByType.get(osmWayValue);
      if(roadModeCategoriesOfMode != null && allowedRoadModeCategoriesOfHighwayType != null) {
        /* verify if any of the categories the mode belongs to is explicitly allowed*/
        isAllowed = !Collections.disjoint(OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode),allowedModeCategoriesByType.get(osmWayValue));
      }        
    }
      
    /* when allowed (via category), it can be that it is explicitly disallowed within its category overruling the category setting */
    if(isAllowed) {
        isAllowed = !(disallowedModesByType.containsKey(osmWayValue) && disallowedModesByType.get(osmWayValue).contains(osmMode));
    }        
            
    return isAllowed;
  }

}

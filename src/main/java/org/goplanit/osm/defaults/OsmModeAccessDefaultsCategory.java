package org.goplanit.osm.defaults;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.goplanit.osm.tags.*;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.Pair;

/**
 * Class representing the default mode access restrictions/allowance for modes for a given
 * category of OSM ways, e.g. highway, railway etc., although the class itself is agnostic to what category it belongs
 * 
 * Disallowed modes take precedence over any other setting, allowed modes take precedence over mode category settings
 * and mode category settings define groups of allowed modes (when not present, it is assumed the category is not
 * allowed as a whole)
 * 
 * @author markr
 *
 */
public class OsmModeAccessDefaultsCategory {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmModeAccessDefaultsCategory.class.getCanonicalName());
  
  /** store the disallowed modes by category (highway, railway), then by type (most important)*/
  private final Map<String, Map<String, Set<String>>> disallowedModesByKeyAndType;
  
  /** store the allowed modes by category (highway, railway), then by type  (after disallowed modes)*/
  private final Map<String, Map<String, Set<String>>> allowedModesByKeyAndType;
  
  /** store the allowed mode categories by category (highway, railway), then by type (least important, after
   * disallowed and allowed modes) */
  private final Map<String, Map<String, Set<String>>> allowedModeCategoriesByKeyAndType;
    

  /** country for which these defaults hold */
  private String countryName;

  private boolean isValidOsmModes(List<String> osmModes) {
    for(String osmModeValueTag : osmModes) {
      if(!(OsmRoadModeTags.isRoadModeTag(osmModeValueTag) ||
          OsmRailModeTags.isRailModeTag(osmModeValueTag) ||
          OsmWaterModeTags.isWaterModeTag(osmModeValueTag))){
        return false;
      }
    }
    return true;
  }

  private boolean isValidOsmKeyValueCombination(Pair<String,String> keyValue) {
    return isValidOsmKeyValueCombination(keyValue.first(), keyValue.second());
  }

  private boolean isValidOsmKeyValueCombination(String key, String type) {
    if(OsmHighwayTags.isHighwayKeyTag(key) && OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      return true;
    }else if(OsmRailwayTags.isRailwayKeyTag(key) && OsmRailwayTags.isRailBasedRailway(type)){
      return  true;
    }else if(OsmWaterwayTags.isWaterBasedWay(key, type)){
      return  true;
    }
    return false;
  }
  
  /**
   * same as {@code addAllowedModes} only we do not log the changes as these are default settings.
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to add
   */
  void addDefaultAllowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
   addAllowedModes(osmKeyValueType.first(), osmKeyValueType.second(), false /* do not log */, Arrays.asList(osmModes));
  }
  
  /**
   * Add the passed in modes as modes that are explicitly allowed access regardless of the mode category,
   * i.e., this takes precedence over the categories.
   * <p>
   *   We must use key and type, because type alone does not uniquely define a situation, e.g., ferry=primary and
   *   highway=primary both exist, but ref;ect different road/waterway types
   * </p>
   *
   * @param key to use
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  protected void addAllowedModes(String key, String type, boolean logChanges, List<String> osmModes) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, type);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format(
          "IGNORE: Unsupported way %s=%s when adding modes %s to allowed modes access defaults", key, type, osmModes));
    }

    boolean isValidModes = isValidOsmModes(osmModes);
    if(!isValidModes) {
      LOGGER.warning(String.format(
          "IGNORE: Unsupported mode(s) provided in %s when adding default modes for %s=%s", osmModes, key, type));
      return;
    }

    allowedModesByKeyAndType.putIfAbsent(key, new HashMap<>());
    var allowedModesByType = allowedModesByKeyAndType.get(key);
    for(String osmModeValueTag : osmModes) {
        allowedModesByType.putIfAbsent(type, new HashSet<>());
      allowedModesByType.get(type).add(osmModeValueTag);
        if (logChanges) LOGGER.info(String.format("Added additional mode %s to %s=%s", osmModeValueTag, key, type));
    }
  }

  /**
   * Set the passed in modes as the only modes that are explicitly allowed access regardless of the mode category,
   *
   * @param key to use
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to add
   */
  protected void setAllowedModes(String key, String type, boolean logChanges, List<String> osmModes) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, type);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format(
          "IGNORE: Unsupported way %s=%s when setting default allowed modes", key, type, osmModes));
      return;
    }

    boolean isValidModes = isValidOsmModes(osmModes);
    if(!isValidModes) {
      LOGGER.warning(String.format(
          "IGNORE: Unsupported mode(s) provided in [%s] when setting default modes for %s=%s", osmModes, key, type));
      return;
    }

    allowedModeCategoriesByKeyAndType.getOrDefault(key, new HashMap<>()).remove(type);
    allowedModesByKeyAndType.putIfAbsent(key, new HashMap<>());
    allowedModesByKeyAndType.get(key).putIfAbsent(type, new HashSet<>());
    allowedModesByKeyAndType.getOrDefault(key,new HashMap<>()).get(type).clear();

    var allowedModesByType = allowedModesByKeyAndType.get(key);
    for(String osmModeValueTag : osmModes) {
      allowedModesByType.get(type).add(osmModeValueTag);
    }
    if(logChanges) LOGGER.info(String.format("Setting allowed modes to [%s] for %s=%s", osmModes, key, type));
  }
  
  /**
   * add the default passed in mode categories as allowed for all its child modes, no logging
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModeCategories to add
   */
  void addDefaultAllowedModeCategories(Pair<String,String> osmKeyValueType, String... osmModeCategories) {
    addAllowedModeCategories(osmKeyValueType.first(), osmKeyValueType.second(), false, osmModeCategories);
  }  
  
  /**
   * Add the passed in mode categories as allowed for all its child modes (currently only road modes have categories)
   *
   * @param key to use
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModeCategories to add
   */
  protected void addAllowedModeCategories(String key, String type, boolean logChanges, String... osmModeCategories) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, type);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format(
          "IGNORE: Unsupported way %s=%s when setting allowed mode categories %s",
          key, type, Arrays.toString(osmModeCategories)));
      return;
    }

    if(OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      allowedModeCategoriesByKeyAndType.putIfAbsent(key, new HashMap<>());
      var allowedModeCategoriesByType = allowedModeCategoriesByKeyAndType.get(key);
      for (String osmModeCategory : osmModeCategories) {
        allowedModeCategoriesByType.putIfAbsent(type, new HashSet<>());
        String osmModeCategoryValueTag = osmModeCategory;
        if (OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategoryValueTag)) {
          allowedModeCategoriesByType.get(type).add(osmModeCategoryValueTag);
          if (logChanges) {
            LOGGER.info(String.format(
                "Added additional allowed mode category %s to %s=%s", osmModeCategoryValueTag, key, type));
          }
        } else {
          LOGGER.warning(String.format(
              "Unknown mode category tag %s, ignored when adding mode categories to modes access defaults",
              osmModeCategory));
        }
      } 
    }else {
      LOGGER.warning(String.format(
          "IGNORE: Mode categories only suited for road mode tags at this point and %s is unknown", type));
    }
  }    
  
  /**
   * remove the passed in mode categories as allowed for all its child modes
   *
   * @param key to use
   * @param type to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModeCategories to remove
   */
  protected void removeAllowedModeCategories(String key, String type, boolean logChanges, String... osmModeCategories) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, type);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format(
          "IGNORE: Unsupported %s=%s when removing allowed mode categories %s",
          key, type, Arrays.toString(osmModeCategories)));
      return;
    }

    if(OsmHighwayTags.isRoadBasedHighwayValueTag(type)) {
      var allowedModeCategoriesByType =
          allowedModeCategoriesByKeyAndType.getOrDefault(key, new HashMap<>());
      for(int index = 0; index < osmModeCategories.length ; ++index) {
        if(allowedModeCategoriesByType.containsKey(type) &&
            OsmRoadModeCategoryTags.isRoadModeCategoryTag(osmModeCategories[index])){
          var removed = allowedModeCategoriesByType.get(type).remove(osmModeCategories[index]);
          if(logChanges && removed) {
            LOGGER.info(String.format("Removed allowed road mode category %s from %s=%s",
                osmModeCategories[index], key, type));
          }
        }else {
          LOGGER.warning(String.format(
              "Unknown mode category tag %s, ignored when removing mode categories from allowed modes access defaults",
              osmModeCategories[index]));
        }
      } 
    }else {
      LOGGER.warning(String.format("IGNORE: Unknown road based tag %s=%s, ignored when removing mode categories " +
          "from allowed modes access defaults", key, type));
    }
  }   
  
  /**
   * remove the default modes from access for the given way type
   *
   * @param key to use
   * @param wayType to use
   * @param osmModes to remove
   */
  void removeDefaultAllowedModes(String key, String wayType, boolean logChanges, String... osmModes) {
    removeAllowedModes(key, wayType, false, osmModes);
  }
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given way type
   *
   * @param key to use
   * @param wayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove
   */
  protected void removeAllowedModes(String key, String wayType, boolean logChanges, String... osmModes) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, wayType);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format("IGNORE: Unsupported way %s=%s when removing allowed modes", key, wayType));
      return;
    }

    boolean isValidModes = isValidOsmModes(Arrays.asList(osmModes));
    if(!isValidModes) {
      LOGGER.warning(String.format(
          "IGNORE: Unsupported mode(s) provided in [%s] when setting default modes for %s=%s",
          Arrays.toString(osmModes), key, wayType));
      return;
    }

    var allowedModesByType = allowedModesByKeyAndType.getOrDefault(key, new HashMap<>());
    for (String osmModeValueTag : osmModes) {
      if (allowedModesByType.containsKey(wayType)) {
        var removed = allowedModesByType.get(wayType).remove(osmModeValueTag);
        if (logChanges && removed) {
          LOGGER.info(String.format("Removed allowed mode %s for %s=%s", osmModeValueTag, key, wayType));
        }
      }
    }
  }
  
  /**
   * add the passed in default modes as modes that are explicitly NOT allowed access regardless of the mode category
   * or allowed modes, i.e., this takes precedence over all other settings.
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to disallow
   */
  void addDefaultDisallowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
    addDisallowedModes(osmKeyValueType.first(), osmKeyValueType.second(), false, osmModes);
  }   
  
  /**
   * Add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode category or
   * allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added
   * as disallowed modes.
   *
   * @param key to use
   * @param wayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to disallow
   */
  protected void addDisallowedModes(String key, String wayType, boolean logChanges, String... osmModes) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, wayType);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format("IGNORE: Unsupported %s=%s when disallowing modes", key, wayType));
      return;
    }

    boolean isValidModes = isValidOsmModes(Arrays.asList(osmModes));
    if(!isValidModes) {
      LOGGER.warning(String.format("IGNORE: Unsupported OSM mode(s) provided in [%s] when disallowing modes for %s=%s",
          Arrays.toString(osmModes), key, wayType));
      return;
    }

    disallowedModesByKeyAndType.putIfAbsent(key, new HashMap<>());
    var disallowedModesByType = disallowedModesByKeyAndType.get(key);
    for(int index = 0; index < osmModes.length ; ++index) {
      disallowedModesByType.putIfAbsent(wayType, new HashSet<>());
      var added = disallowedModesByType.get(wayType).add(osmModes[index]);
      if (logChanges && added) {
        LOGGER.info(String.format("Disallowed OSM mode %s for %s=%s", osmModes[index], key, wayType));
      }
    }
  }
  
  /**
   * remove the passed in default modes as modes that are NOT allowed access for the given way type (no logging)
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to remove from disallowing
   */
  void removeDefaultDisallowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
    removeDisallowedModes(osmKeyValueType.first(), osmKeyValueType.second(), false, osmModes);
  }    
  
  /**
   * remove the passed in modes as modes that are NOT allowed access for the given highway type
   *
   * @param key to use
   * @param wayType to use
   * @param logChanges when true changes are logged, otherwise not
   * @param osmModes to remove from disallowing
   */
  protected void removeDisallowedModes(String key, String wayType, boolean logChanges, String... osmModes) {
    boolean validKeyValueCombination = isValidOsmKeyValueCombination(key, wayType);
    if(!validKeyValueCombination){
      LOGGER.warning(String.format("IGNORE: Unsupported %s=%s when removing disallowing modes", key, wayType));
      return;
    }

    boolean isValidModes = isValidOsmModes(Arrays.asList(osmModes));
    if(!isValidModes) {
      LOGGER.warning(String.format(
          "IGNORE: Unsupported OSM mode(s) provided in [%s] when removing disallowed modes for %s=%s",
          Arrays.toString(osmModes), key, wayType));
      return;
    }

    var disallowedModesByType = disallowedModesByKeyAndType.getOrDefault(key, new HashMap<>());
    for (String osmMode : osmModes) {
      if (disallowedModesByType.containsKey(wayType)) {
        var success = disallowedModesByType.get(wayType).remove(osmMode);
        if (logChanges && success) {
          LOGGER.info(String.format("Removed disallowed mode %s from %s=%s", osmMode, key, wayType));
        }
      } else {
        LOGGER.warning(String.format(
            "IGNORE: Unknown mode tag %s when removing modes from disallowed modes access defaults", osmMode));
      }
    }
  }   
  
  /**
   * Default constructor
   */
  public OsmModeAccessDefaultsCategory() { 
    this.countryName = CountryNames.GLOBAL;
    this.allowedModeCategoriesByKeyAndType = new TreeMap<>();
    this.allowedModesByKeyAndType = new TreeMap<>();
    this.disallowedModesByKeyAndType = new TreeMap<>();
  }
  
  /**
   * Default constructor
   * 
   * @param countryName to use
   */
  public OsmModeAccessDefaultsCategory(String countryName) {
    this.countryName = countryName;
    this.allowedModeCategoriesByKeyAndType = new TreeMap<>();
    this.allowedModesByKeyAndType = new TreeMap<>();
    this.disallowedModesByKeyAndType = new TreeMap<>();
  }  
  
  /**
   * Copy constructor
   * 
   * @param other to copy from
   */
  public OsmModeAccessDefaultsCategory(OsmModeAccessDefaultsCategory other) {
    this();
    this.countryName = other.getCountry();

    other.allowedModeCategoriesByKeyAndType.forEach( (k,v) -> {
          this.allowedModeCategoriesByKeyAndType.put(k, new HashMap<>());
          var toFill = this.allowedModeCategoriesByKeyAndType.get(k);
          v.forEach((k2, v2) ->
              toFill.put(k2, new HashSet<>(v2)));
        });

    other.allowedModesByKeyAndType.forEach( (k,v) -> {
      this.allowedModesByKeyAndType.put(k, new HashMap<>());
      var toFill = this.allowedModesByKeyAndType.get(k);
      v.forEach((k2, v2) ->
          toFill.put(k2, new HashSet<>(v2)));
    });

    other.disallowedModesByKeyAndType.forEach( (k,v) -> {
      this.disallowedModesByKeyAndType.put(k, new HashMap<>());
      var toFill = this.disallowedModesByKeyAndType.get(k);
      v.forEach((k2, v2) ->
          toFill.put(k2, new HashSet<>(v2)));
    });
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
   * Shallow copy
   *
   * @return shallow copy
   */
  public OsmModeAccessDefaultsCategory shallowClone() {
    return new OsmModeAccessDefaultsCategory(this);
  }  
    
  /**
   * add the passed in mode categories as allowed for all its child modes
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModeCategories to add
   */
  public void addAllowedModeCategories(Pair<String,String> osmKeyValueType, String... osmModeCategories) {
    addAllowedModeCategories(
        osmKeyValueType.first(), osmKeyValueType.second(), true /* log changes */, osmModeCategories);
  }  
  
  /**
   * remove the passed in mode categories as allowed for all its child modes
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModeCategories to remove
   */
  public void removeAllowedModeCategories(Pair<String,String> osmKeyValueType, String... osmModeCategories) {
    removeAllowedModeCategories(
        osmKeyValueType.first(), osmKeyValueType.second(), true /* log changes*/ , osmModeCategories);
  }   
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category, i.e.,
   * this takes precedence over the categories. Note that rail modes can be explicitly allowed onto roads, e.g. tram,
   * lightrail, indicating a track embedded in the road.
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to add
   */
  public void addAllowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
   addAllowedModes(osmKeyValueType.first(), osmKeyValueType.second(), Arrays.asList(osmModes));
  }
  
  /**
   * add the passed in modes as modes that are explicitly allowed access regardless of the mode category,
   * i.e., this takes precedence over the categories.
   * Note that rail modes can be explicitly allowed onto roads, e.g. tram, lightrail, indicating a track embedded
   * in the road.
   *
   * @param key to use
   * @param wayType to use
   * @param osmModes to add
   */
  public void addAllowedModes(String key, String wayType, List<String> osmModes) {
   addAllowedModes(key, wayType, true /* log changes*/, osmModes);
  }  
  
  /**
   * set the passed in modes as the only modes that are explicitly allowed access and remove all categories
   *
   * @param key to use
   * @param wayType to use
   * @param osmModes to add
   */
  public void setAllowedModes(String key, String wayType, List<String> osmModes) {
   setAllowedModes(key, wayType, true /* log changes*/, osmModes);
  }    
  
  /**
   * remove the passed in modes as modes that are no longer allowed access for the given highway type
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to remove
   */
  public void removeAllowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
    removeAllowedModes(osmKeyValueType.first(), osmKeyValueType.second(), true /* log changes*/, osmModes);
  }    
  
  /**
   * add the passed in modes as modes that are explicitly NOT allowed access regardless of the mode
   * category or allowed modes, i.e., this takes precedence over all other settings.
   * Note that rail modes are by definition disallowed unless explicitly allowed, so they need never be added
   * as disallowed modes.
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to disallow
   */
  public void addDisallowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
    addDisallowedModes(osmKeyValueType.first(), osmKeyValueType.second(), true /* log changes */, osmModes);
  }
  
  /**
   * Remove the passed in modes as modes that are NOT allowed access for the given highway type
   *
   * @param osmKeyValueType key value combination to use
   * @param osmModes to remove from disallowing
   */
  public void removeDisallowedModes(Pair<String,String> osmKeyValueType, String... osmModes) {
    removeDisallowedModes(osmKeyValueType.first(), osmKeyValueType.second(), true /* log changes */, osmModes);
  }  
  

  /**
   * Verify if mode is allowed for given way type. If none of the allowed/disallowed configuration options
   * includes the passed in mode it is assumed the mode is not allowed
   *
   * @param key to use
   * @param osmWayValue to check for
   * @param osmMode to verify
   * @return true when allowed, false when disallowed, false if unknown
   */
  public boolean isAllowed(String key, String osmWayValue, String osmMode) {
    Boolean isAllowed = Boolean.FALSE;    

    var allowedModesByType = allowedModesByKeyAndType.getOrDefault(key, new HashMap<>());
    if(allowedModesByType.containsKey(osmWayValue)){
      /* verify if it is explicitly allowed */
      isAllowed = allowedModesByType.get(osmWayValue).contains(osmMode);
    }
      
    /* when not yet explicitly allowed, verify if it is allowed via an umbrella mode category (for now these
    only exist for road modes)*/
    if(!isAllowed) {
      Set<String> roadModeCategoriesOfMode = OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode);
      var allowedModeCategoriesByType =
          allowedModeCategoriesByKeyAndType.getOrDefault(key, new HashMap<>());
      Set<String> allowedModeCategoriesOfKeyValueType = allowedModeCategoriesByType.get(osmWayValue);
      if(roadModeCategoriesOfMode != null && allowedModeCategoriesOfKeyValueType != null) {
        /* verify if any of the categories the mode belongs to is explicitly allowed*/
        isAllowed = !Collections.disjoint(
            OsmRoadModeCategoryTags.getRoadModeCategoriesByMode(osmMode),allowedModeCategoriesByType.get(osmWayValue));
      }        
    }
      
    /* when allowed (via category), it can be that it is explicitly disallowed within its category overruling
    the category setting */
    if(isAllowed) {
      var disallowedModesByType = disallowedModesByKeyAndType.getOrDefault(key, new HashMap<>());
      isAllowed = !(disallowedModesByType.containsKey(osmWayValue) &&
          disallowedModesByType.get(osmWayValue).contains(osmMode));
    }        
            
    return isAllowed;
  }

}

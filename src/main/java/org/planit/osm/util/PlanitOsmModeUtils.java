package org.planit.osm.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.handler.PlanitOsmHandlerHelper;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.osm.tags.OsmTags;

/**
 * Utilities in relation to parsing osm modes when constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class PlanitOsmModeUtils {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmModeUtils.class.getCanonicalName());
 
  
  /** collect all OSM modes with either preFix:<OSM mode name>= or postFix:<OSM mode name>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param isprefix when true prefix applied, when false, postfix
   * @param alteration, the post or prefix alteration of the mode key
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */    
  protected static Collection<String> getPrefixedOrPostfixedOsmRoadModesWithAccessValue(boolean isprefix, String alteration, Map<String, String> tags, final String... modeAccessValueTags) {
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
  
  /** collect all OSM road going modes with key=\<OSM mode name\> value=the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Collection<String> getOsmRoadModesWithAccessValue(Map<String, String> tags, final String... modeAccessValueTags){
    return getPostfixedOsmRoadModesWithAccessValue(null, tags, modeAccessValueTags);
  }
  
  /** collect all OSM rail modes with key=\<OSM mode name\> value=the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Collection<String> getOsmRailModesWithAccessValue(Map<String, String> tags, final String... modeAccessValueTags){
    Set<String> foundModes = new HashSet<String>();    
        
    /* osm rail mode */
    Collection<String> osmrailModes = OsmRailModeTags.getSupportedRailModeTags();
    for(String osmRailmode : osmrailModes) {     
      if(tags.containsKey(osmRailmode)){
        String valueTag = tags.get(osmRailmode).replaceAll(OsmTagUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");
        for(int index = 0 ; index < modeAccessValueTags.length ; ++index) {
          if(modeAccessValueTags[index].equals(valueTag)){
            foundModes.add(osmRailmode);
          }
        }
      }
    }    
    return foundModes;
    
  }  
  
  /** collect all OSM modes with key=\<OSM mode name\>:postFix= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param postFix to utilise
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */  
  public static Collection<String> getPostfixedOsmRoadModesWithAccessValue(String postFix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmRoadModesWithAccessValue(false, postFix, tags, modeAccessValueTags);
  }  
  
  /** collect all OSM modes with key=preFix:\<OSM mode name\>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */  
  public static Collection<String> getPrefixedOsmRoadModesWithAccessValue(String prefix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmRoadModesWithAccessValue(true, prefix, tags, modeAccessValueTags);
  }
  
  /** Collect the rail modes that are deemed eligible for this entity (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. bus=yes, or when none are marked explicitly we assume the the default (if provided) 
   * 
   * @param osmEntityId to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmRoadModesOnPtOsmEntity(long osmEntityId, Map<String, String> tags, String defaultOsmMode) {
    Collection<String> explicitlyIncludedOsmModes = getOsmRoadModesWithAccessValue(tags, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Collection<String> explicitlyExcludedOsmModes = getOsmRoadModesWithAccessValue(tags, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        PlanitOsmHandlerHelper.LOGGER.severe(String.format("we currently do not yet support explicitly excluded road modes for PT osm entity %d (platforms, etc.), ignored exclusion of %s", osmEntityId, explicitlyExcludedOsmModes.toString()));
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = Collections.singleton(defaultOsmMode);
    }
    return explicitlyIncludedOsmModes;       
  }

  /** Collect the modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided) 
   * 
   * @param osmEntityId to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmModesOnPtOsmEntity(long osmEntityId, Map<String, String> tags, String defaultOsmMode) {
    String defaultRailMode = OsmRailModeTags.isRailModeTag(defaultOsmMode) ? defaultOsmMode : null;
    Collection<String> eligibleOsmModes = collectEligibleOsmRailModesOnPtOsmEntity(osmEntityId, tags, defaultRailMode);
    String defaultRoadMode = OsmRoadModeTags.isRoadModeTag(defaultOsmMode) ? defaultOsmMode : null;
    Collection<String> eligibleOsmRoadModes = collectEligibleOsmRoadModesOnPtOsmEntity(osmEntityId, tags, defaultRoadMode);
    if(eligibleOsmModes != null) {
      eligibleOsmModes.addAll(eligibleOsmRoadModes);
    }else {     
      eligibleOsmModes = eligibleOsmRoadModes;
    }
    return eligibleOsmModes;       
  }

  /** Collect the rail modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided) 
   * 
   * @param osmEntityId to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmRailModesOnPtOsmEntity(long osmEntityId, Map<String, String> tags, String defaultOsmMode) {
    Collection<String> explicitlyIncludedOsmModes = getOsmRailModesWithAccessValue(tags, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Collection<String> explicitlyExcludedOsmModes = getOsmRailModesWithAccessValue(tags, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        PlanitOsmHandlerHelper.LOGGER.severe(String.format("we currently do not yet support explicitly excluded rail modes for PT osm entity %d (platforms, etc.), ignored exclusion of %s", osmEntityId, explicitlyExcludedOsmModes.toString()));
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = Collections.singleton(defaultOsmMode);
    }
    return explicitlyIncludedOsmModes;       
  }    

}
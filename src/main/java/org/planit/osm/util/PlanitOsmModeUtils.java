package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.osm.tags.OsmTags;
import org.planit.osm.tags.OsmWaterModeTags;

/**
 * Utilities in relation to parsing osm modes when constructing a PLANit model from it
 * 
 * @author markr
 *
 */
/**
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
  protected static Set<String> getPrefixedOrPostfixedOsmRoadModesWithValueTag(boolean isprefix, String alteration, Map<String, String> tags, final String... modeAccessValueTags) {
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
  
  /** collect all OSM modes with key=\<OSM mode name\> value=the access value tags that are passed in and available from the supported modes (also passed in). 
   * Note that the actual value of the tags will be stripped from special characters to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param supportedOsmModes supportedOsmModes to filer on 
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */  
  protected static Set<String> getOsmModesWithValueTag(Map<String, String> tags, Collection<String> supportedOsmModes, final String... modeAccessValueTags) {
    Set<String> foundModes = new HashSet<String>();    
    
    /* osm mode */
    for(String osmMode : supportedOsmModes) {     
      if(tags.containsKey(osmMode)){
        String valueTag = tags.get(osmMode).replaceAll(OsmTagUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");
        for(int index = 0 ; index < modeAccessValueTags.length ; ++index) {
          if(modeAccessValueTags[index].equals(valueTag)){
            foundModes.add(osmMode);
          }
        }
      }
    }    
    return foundModes;
  }  
  
  /** Collect the osm modes that are deemed eligible for this entity (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. ferry=yes, or when none are marked explicitly we assume the the default (if provided). When modes are marked
   * as non-accessible, they are removed from the explicitly included modes. We use a selected set of supported modes passed in to select from 
   * 
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  protected static Set<String> collectEligibleOsmModesOnPtOsmEntity(Map<String, String> tags, Set<String> selectableOsmModes, String defaultOsmMode) {
    Set<String> explicitlyIncludedOsmModes = getOsmModesWithValueTag(tags, selectableOsmModes, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Set<String> explicitlyExcludedOsmModes = getOsmModesWithValueTag(tags, selectableOsmModes, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        explicitlyIncludedOsmModes.removeAll(explicitlyExcludedOsmModes);
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = new HashSet<String>();
      explicitlyIncludedOsmModes.add(defaultOsmMode);       
    }
    return explicitlyIncludedOsmModes;       
  }  
  
  /** collect all OSM road going modes with key=\<OSM mode name\> value=the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Set<String> getOsmRoadModesWithValueTag(Map<String, String> tags, final String... modeAccessValueTags){
    return getPostfixedOsmRoadModesWithValueTag(null, tags, modeAccessValueTags);
  }
  
  /** collect all OSM rail modes with key=\<OSM mode name\> value=the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Set<String> getOsmRailModesWithValueTag(Map<String, String> tags, final String... modeAccessValueTags){
    return getOsmModesWithValueTag(tags, OsmRailModeTags.getSupportedRailModeTags(), modeAccessValueTags);        
  }  
  
  /** collect all OSM modes with key=\<OSM mode name\>:postFix= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param postFix to utilise
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */  
  public static Set<String> getPostfixedOsmRoadModesWithValueTag(String postFix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmRoadModesWithValueTag(false, postFix, tags, modeAccessValueTags);
  }  
  
  /** collect all OSM modes with key=preFix:\<OSM mode name\>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */  
  public static Collection<String> getPrefixedOsmRoadModesWithValueTag(String prefix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmRoadModesWithValueTag(true, prefix, tags, modeAccessValueTags);
  }
  
  
  /** Collect the water modes that are deemed eligible for this entity (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. ferry=yes, or when none are marked explicitly we assume the the default (if provided). When modes are marked
   * as non-accessible, they are removed from the explicitly included modes. 
   * 
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Set<String> collectEligibleOsmWaterModesOnPtOsmEntity(Map<String, String> tags, String defaultOsmMode) {
    return collectEligibleOsmModesOnPtOsmEntity(tags, OsmWaterModeTags.getSupportedWaterModeTags(), defaultOsmMode);       
  }
  
  /** Collect the rail modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided) 
   * 
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Set<String> collectEligibleOsmRailModesOnPtOsmEntity(Map<String, String> tags, String defaultOsmMode) {    
    return collectEligibleOsmModesOnPtOsmEntity(tags, OsmRailModeTags.getSupportedRailModeTags(), defaultOsmMode);       
  }       
  
  /** Collect the rail modes that are deemed eligible for this entity (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. bus=yes, or when none are marked explicitly we assume the the default (if provided). When modes are marked
   * as non-accessible, they are removed from the explicitly included modes. 
   * 
   * @param osmEntityId to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Set<String> collectEligibleOsmRoadModesOnPtOsmEntity(long osmEntityId, Map<String, String> tags, String defaultOsmMode) {
    Set<String> explicitlyIncludedOsmModes = getOsmRoadModesWithValueTag(tags, OsmTags.YES);
    if(explicitlyIncludedOsmModes != null && !explicitlyIncludedOsmModes.isEmpty()) {
      Set<String> explicitlyExcludedOsmModes = getOsmRoadModesWithValueTag(tags, OsmTags.NO);
      if(explicitlyExcludedOsmModes != null && !explicitlyExcludedOsmModes.isEmpty()) {
        explicitlyIncludedOsmModes.removeAll(explicitlyExcludedOsmModes);
      }
    }else if(defaultOsmMode != null){
      /* default if no explicit modes are mapped, is to map it to rail */
      explicitlyIncludedOsmModes = new HashSet<String>();
      explicitlyIncludedOsmModes.add(defaultOsmMode);       
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
  public static Set<String> collectEligibleOsmModesOnPtOsmEntity(long osmEntityId, Map<String, String> tags, String defaultOsmMode) {
    Set<String> eligibleOsmModes = null;
    
    /* rail modes */
    String defaultRailMode = OsmRailModeTags.isRailModeTag(defaultOsmMode) ? defaultOsmMode : null;
    Set<String> eligibleOsmRailModes = collectEligibleOsmRailModesOnPtOsmEntity(tags, defaultRailMode);
    if(eligibleOsmRailModes!=null && !eligibleOsmRailModes.isEmpty()) {
      eligibleOsmModes = eligibleOsmRailModes;
    }
    
    /* road modes */
    String defaultRoadMode = OsmRoadModeTags.isRoadModeTag(defaultOsmMode) ? defaultOsmMode : null;
    Set<String> eligibleOsmRoadModes = collectEligibleOsmRoadModesOnPtOsmEntity(osmEntityId, tags, defaultRoadMode);
    if(eligibleOsmRoadModes!=null && !eligibleOsmRoadModes.isEmpty()) {
      if(eligibleOsmModes!=null) {
        eligibleOsmModes.addAll(eligibleOsmRoadModes);
      }else {
        eligibleOsmModes = eligibleOsmRoadModes;
      }
    }
    
    /* water modes */
    String defaultWaterMode = OsmWaterModeTags.isWaterModeTag(defaultOsmMode) ? defaultOsmMode : null;    
    Set<String> eligibleOsmWaterModes = collectEligibleOsmWaterModesOnPtOsmEntity(tags, defaultWaterMode);
    if(eligibleOsmWaterModes!=null && !eligibleOsmWaterModes.isEmpty()) {
      if(eligibleOsmModes!=null) {
        eligibleOsmModes.addAll(eligibleOsmWaterModes);
      }else {
        eligibleOsmModes = eligibleOsmWaterModes;
      }
    }    
          
    return eligibleOsmModes;       
  }

  /** If the tags contain Ptv1 related tagging, we use it to identify the most likely mode that is expected to be supported,
   * <ul>
   * <li>highway=bus_stop gives bus</li>
   * <li>highway=station gives bus</li>
   * <li>highway=platform gives bus</li>
   * <li>highway=platform_edge gives bus</li>
   * <li>railway=station gives train</li>
   * <li>railway=platform gives train</li>
   * <li>railway=platform_edge gives train</li>
   * <li>railway=halt gives train</li>
   * <li>railway=tram_stop gives tram</li>
   * </ul> 
   * @param tags to extract information from
   * @return default mode, null if no match could be made
   */
  public static String identifyPtv1DefaultMode(Map<String, String> tags) {
    if(OsmPtv1Tags.hasPtv1ValueTag(tags)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
        /* bus_stop --> bus */
        if(OsmPtv1Tags.isBusStop(tags)) {
          return OsmRoadModeTags.BUS;
        }else if(OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, 
            OsmPtv1Tags.STATION, OsmPtv1Tags.PLATFORM, OsmPtv1Tags.PLATFORM_EDGE)) {
          return OsmRoadModeTags.BUS;
        }else {
          LOGGER.warning(String.format(
              "unsupported Ptv1 value tag highway=%s used when identifying default mode, ignored",tags.get(OsmHighwayTags.HIGHWAY)));
        }
      }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
        /* tram_stop -> tram */
        if(OsmPtv1Tags.isTramStop(tags)) {
          return OsmRailModeTags.TRAM;
        }else if(OsmTagUtils.keyMatchesAnyValueTag(tags, OsmRailwayTags.RAILWAY, 
            OsmPtv1Tags.STATION, OsmPtv1Tags.HALT, OsmPtv1Tags.PLATFORM, OsmPtv1Tags.PLATFORM_EDGE)) {
          return OsmRailModeTags.TRAIN;
        }else {
          LOGGER.warning(String.format(
              "unsupported Ptv1 value tag railway=%s used when identifying default mode, ignored",tags.get(OsmRailwayTags.RAILWAY)));  
        }
      }else {
        LOGGER.warning("unknown Ptv1 key tag used when identifying default mode, ignored");
      }
      
    }
    return null;
  }

  /** find out if modes to check are compatible with the reference osm modes. Mode compatible means at least one overlapping
   * osm mode. When one allows for psuedo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param osmModesToCheck the modes to check
   * @param referenceOsmModes modes to check against
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return compatible modes found
   */
  public static Collection<String> getCompatibleModes(Collection<String> osmModesToCheck, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Set<String> overlappingModes = new HashSet<String>();          
    if(referenceOsmModes !=null) {
      if(allowPseudoMatches) {
        /* retain all zone modes per overlapping type of modes */
        if(OsmRoadModeTags.containsAnyMode(referenceOsmModes)) {
          overlappingModes.addAll(OsmRoadModeTags.getModesFrom(osmModesToCheck));
        }
        if(OsmRailModeTags.containsAnyMode(referenceOsmModes)) {
          overlappingModes.addAll(OsmRailModeTags.getModesFrom(osmModesToCheck));
        }
        if(OsmWaterModeTags.containsAnyMode(referenceOsmModes)) {
          overlappingModes.addAll(OsmWaterModeTags.getModesFrom(osmModesToCheck));
        }        
      }else {                   
        /* get intersection of station and zone modes */
        overlappingModes.addAll(osmModesToCheck); 
        overlappingModes.retainAll(referenceOsmModes);
      }
    }
    return overlappingModes;
  }

}

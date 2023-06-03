package org.goplanit.osm.util;

import java.util.*;
import java.util.logging.Logger;

import org.goplanit.osm.tags.*;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * Utilities in relation to parsing OSM modes when constructing a PLANit model from it. All utility methods are static in that they do not require
 * any additional information regarding the configuration of the OSM reader, i.e., only generic utilities regarding OSM modes are included here.
 * 
 * @author markr
 *
 */
public class OsmModeUtils {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmModeUtils.class.getCanonicalName());
 
  
  /** Collect all OSM modes with either {@code preFix:<OSM mode name>=} or {@code postFix:<OSM mode name>= any of the modeAccessValueTags that are passed in}. 
   * Note that the actual value of the tags will be stripped from special characters to make it more universal to match the pre-specified mode 
   * access value tags that we expect to be passed in.
   * 
   * @param isprefix when true prefix applied, when false, postfix
   * @param alteration, the post or prefix alteration of the mode key
   * @param tags to find explicitly included/excluded (PLANit) modes from
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
        String valueTag = tags.get(compositeKey).replaceAll(OsmTagUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");        
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
  
  /** Collect all OSM modes with {@code key=<OSM mode name>} value=the access value tags that are passed in and available from the supported modes (also passed in). 
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
  
  /** Collect the OSM modes that are deemed eligible for this entity (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. ferry=yes, or when none are marked explicitly we assume the the default (if provided). When modes are marked
   * as non-accessible, they are removed from the explicitly included modes. We use a selected set of supported modes passed in to select from 
   * 
   * @param tags related to the node
   * @param selectableOsmModes to choose from
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
  
  /** collect all OSM road going modes with {@code key=<OSM mode name>} value=the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Set<String> getOsmRoadModesWithValueTag(Map<String, String> tags, final String... modeAccessValueTags){
    return getPostfixedOsmRoadModesWithValueTag(null, tags, modeAccessValueTags);
  }
  
  /** collect all OSM rail modes with {@code key=<OSM mode name> value=the access value tags that are passed in}. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  public static Set<String> getOsmRailModesWithValueTag(Map<String, String> tags, final String... modeAccessValueTags){
    return getOsmModesWithValueTag(tags, OsmRailModeTags.getSupportedRailModeTags(), modeAccessValueTags);        
  }  
  
  /** collect all OSM modes with {@code key=<OSM mode name>:postFix=} any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
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
  
  /** collect all OSM modes with {@code key=preFix:<OSM mode name>= any of the modeAccessValueTags} that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param prefix considered
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
   * marked as yes, e.g. subway=yes. 
   * 
   * @param osmEntityId to use
   * @param tags related to the node
   * @return list of eligible osm modes, can be empty if no modes are found
   */  
  public static Collection<String> collectEligibleOsmModesOnPtOsmEntity(long osmEntityId, Map<String, String> tags) {
    Collection<String> eligibleOsmModes = null;
    
    /* rail modes */
    Set<String> eligibleOsmRailModes = collectEligibleOsmRailModesOnPtOsmEntity(tags, null);
    if(eligibleOsmRailModes!=null && !eligibleOsmRailModes.isEmpty()) {
      eligibleOsmModes = eligibleOsmRailModes;
    }
    
    /* road modes */
    Set<String> eligibleOsmRoadModes = collectEligibleOsmRoadModesOnPtOsmEntity(osmEntityId, tags, null);
    if(eligibleOsmRoadModes!=null && !eligibleOsmRoadModes.isEmpty()) {
      if(eligibleOsmModes!=null) {
        eligibleOsmModes.addAll(eligibleOsmRoadModes);
      }else {
        eligibleOsmModes = eligibleOsmRoadModes;
      }
    }
    
    /* water modes */   
    Set<String> eligibleOsmWaterModes = collectEligibleOsmWaterModesOnPtOsmEntity(tags, null);
    if(eligibleOsmWaterModes!=null && !eligibleOsmWaterModes.isEmpty()) {
      if(eligibleOsmModes!=null) {
        eligibleOsmModes.addAll(eligibleOsmWaterModes);
      }else {
        eligibleOsmModes = eligibleOsmWaterModes;
      }
    }  
    
    return eligibleOsmModes;
  }  

  /** Collect the modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided). 
   * 
   * @param osmPtEntityId to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm modes, can be empty if no modes are found and default is null
   */
  public static Collection<String> collectEligibleOsmModesOnPtOsmEntity(long osmPtEntityId, Map<String, String> tags, String defaultOsmMode) {    
    
    Collection<String> eligibleOsmModes = collectEligibleOsmModesOnPtOsmEntity(osmPtEntityId, tags);            
    if((eligibleOsmModes==null || eligibleOsmModes.isEmpty()) && defaultOsmMode != null) {
      /* use default mode when no explicit modes are found across all mode types */
      eligibleOsmModes = Collections.singleton(defaultOsmMode);
    }
          
    return eligibleOsmModes;       
  }
  

  /** Collect the public transport modes that are deemed eligible for this node (platform, station, halt, etc.). A mode is eligible when
   * marked as yes, e.g. subway=yes, or when none are marked explicitly we assume the default (if provided). 
   * 
   * @param osmPtEntityId to use
   * @param tags related to the node
   * @param defaultOsmMode used when no explicit modes can be found (can be null)
   * @return list of eligible osm public transport modes, can be empty if no modes are found and default is null
   */  
  public static TreeSet<String> collectEligibleOsmPublicTransportModesOnPtOsmEntity(long osmPtEntityId, Map<String, String> tags, String defaultOsmMode) {
    TreeSet<String> eligibleOsmPtModes = extractPublicTransportModesFrom(collectEligibleOsmModesOnPtOsmEntity(osmPtEntityId, tags));
    
    if((eligibleOsmPtModes==null || eligibleOsmPtModes.isEmpty()) && defaultOsmMode != null) {
      /* use default mode when no modes are found across all pt modes*/
      eligibleOsmPtModes = eligibleOsmPtModes==null ? new TreeSet<>() : eligibleOsmPtModes;
      eligibleOsmPtModes.add(defaultOsmMode);
    }
          
    return eligibleOsmPtModes; 
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
   * <li>railway=stop gives train</li>
   * <li>railway=tram_stop gives tram</li>
   * <li>amenity=ferry_terminal gives ferry</li>
   * </ul> 
   *
   * @param osmId this relates to
   * @param tags to extract information from
   * @param suppressWarning when true suppress any warnings, do not otherwise
   * @return default mode found, null if nothing is found
   */
  public static String identifyPtv1DefaultMode(long osmId, Map<String, String> tags, boolean suppressWarning) {
   return identifyPtv1DefaultMode(osmId, tags, null, suppressWarning);
  }

  /**
   * Identical to {@link #identifyPtv1DefaultMode(long, Map, boolean)} without suppressing warnings
   *
   * @param osmId this relates to
   * @param tags to extract information from
   * @return default mode found, null if nothing is found
   */
  public static String identifyPtv1DefaultMode(long osmId, Map<String, String> tags) {
    return identifyPtv1DefaultMode(osmId, tags, null, false);
  }

  /** If the tags contain Ptv1 related tagging, we use it to identify the most likely mode that is expected to be supported.
   *  We are slightly more tolerant to ferries due to their high likelihood of erroneous or incomplete tagging. In those
   *  cases we verify a few more tags than one would normally do
   *
   * <ul>
   * <li>highway=bus_stop gives bus</li>
   * <li>highway=station gives bus</li>
   * <li>highway=platform gives bus</li>
   * <li>highway=platform_edge gives bus</li>
   * <li>railway=station gives train</li>
   * <li>railway=platform gives train</li>
   * <li>railway=platform_edge gives train</li>
   * <li>railway=halt gives train</li>
   * <li>railway=stop gives train</li>
   * <li>railway=tram_stop gives tram</li>
   * <li>railway=tram_stop gives tram</li>
   * <li>amenity=ferry_terminal gives ferry</li>
   * <li>ferry=yes gives ferry</li>
   * </ul> 
   *
   * @param osmId this relates to
   * @param tags to extract information from
   * @param backupDefaultMode if none can be found, this mode is used, may be null
   * @param suppressWarning when true do not log warning if no match could be found,
   * @return default mode, null if no match could be made
   */
  public static String identifyPtv1DefaultMode(long osmId, Map<String, String> tags, String backupDefaultMode, boolean suppressWarning) {
    String foundMode = null;
    if(OsmPtv1Tags.hasPtv1ValueTag(tags)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
        /* bus_stop --> bus */
        if(OsmPtv1Tags.isBusStop(tags)) {
          foundMode = OsmRoadModeTags.BUS;
        }else if(OsmTagUtils.keyMatchesAnyValueTag(tags, OsmHighwayTags.HIGHWAY, 
            OsmPtv1Tags.STATION, OsmPtv1Tags.PLATFORM, OsmPtv1Tags.PLATFORM_EDGE)) {
          foundMode = OsmRoadModeTags.BUS;
        }else if(!suppressWarning){
          LOGGER.warning(String.format(
              "Unsupported Ptv1 value tag highway=%s used when identifying default mode on OSM entity %d, ignored",
              tags.get(OsmHighwayTags.HIGHWAY), osmId));
        }
      }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
        /* tram_stop -> tram */
        if(OsmPtv1Tags.isTramStop(tags)) {
          foundMode = OsmRailModeTags.TRAM;
        }else if(OsmTagUtils.keyMatchesAnyValueTag(tags, OsmRailwayTags.RAILWAY, 
            OsmPtv1Tags.STATION, OsmPtv1Tags.HALT, OsmPtv1Tags.PLATFORM, OsmPtv1Tags.PLATFORM_EDGE, OsmPtv1Tags.STOP)) {
          foundMode = OsmRailModeTags.TRAIN;
        }else if(!suppressWarning){
          LOGGER.warning(String.format(
              "Unsupported Ptv1 value tag railway=%s used when identifying default mode on OSM entity %s, ignored",
              tags.get(OsmRailwayTags.RAILWAY), osmId));
        }
      }
    }else if(OsmTags.isAmenity(tags) || tags.containsKey(OsmWaterModeTags.FERRY)) {

      if(OsmPtv1Tags.isFerryTerminal(tags)) {
        /* amenity=ferry_terminal -> ferry */
        foundMode = OsmWaterModeTags.FERRY;
      }else if(tags.containsKey(OsmWaterModeTags.FERRY) && OsmTagUtils.keyMatchesAnyValueTag(tags, OsmWaterModeTags.FERRY, OsmTags.YES)){
        foundMode = OsmWaterModeTags.FERRY;
      }
    }else if(!suppressWarning){
      LOGGER.warning(String.format("Unable to extract expected OSM mode from OSM entity %d (Ptv1), potential incomplete tagging, tags(%s)",
          osmId, tags));
    }

    if(foundMode == null) {
      foundMode = backupDefaultMode;
    }
    return foundMode;
  }

  /** Identical to {@link #identifyPtv1DefaultMode(long, Map, String, boolean)} without suppressing warnings
   *
   * @param osmId to use
   * @param tags to extract information from
   * @param backupDefaultMode if none can be found, this mode is used, may be null
      * @return default mode, null if no match could be made
   */
  public static String identifyPtv1DefaultMode(long osmId, Map<String, String> tags, String backupDefaultMode) {
    return identifyPtv1DefaultMode(osmId, tags, backupDefaultMode, false);
  }

  /** Find out if modes to check are compatible with the reference OSM modes. Mode compatible means at least one overlapping
   * OSM mode. When one allows for pseudo compatibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param osmModesToCheck the modes to check
   * @param referenceOsmModes modes to check against
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return compatible modes found
   */
  public static Collection<String> extractCompatibleOsmModes(final Collection<String> osmModesToCheck, final Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Set<String> overlappingModes = new HashSet<>();
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
  
  /** Collect all pt modes available from the passed in modes across all major mode types (road, rail, water)
   * 
   * @param eligibleOsmModes to extract from
   * @return found public transport based modes, can be null
   */
  public static TreeSet<String> extractPublicTransportModesFrom(final Collection<String> eligibleOsmModes) {
    if(eligibleOsmModes == null) {
      return null;
    }
    TreeSet<String> railPtModes = OsmRailModeTags.getPublicTransportModesFrom(eligibleOsmModes);
    TreeSet<String> roadPtModes = OsmRoadModeTags.getPublicTransportModesFrom(eligibleOsmModes);
    TreeSet<String> waterPtModes = OsmWaterModeTags.getPublicTransportModesFrom(eligibleOsmModes);
    railPtModes.addAll(roadPtModes);
    railPtModes.addAll(waterPtModes);
    return railPtModes;
  }
  
  /** Check to see if pair with eligible modes contains any eligible OSM mode
   * 
   * @param modeResult of collectEligibleModes on zoning base handler
   * @return true when has at least one mapped PLANit mode present
   */
  public static boolean hasEligibleOsmMode(Pair<? extends SortedSet<String>, SortedSet<PredefinedModeType>> modeResult) {
    if(modeResult!= null && modeResult.first()!=null && !modeResult.first().isEmpty()) {
      /* eligible modes available */
      return true;
    }else {
      return false;
    }
  }  

  /** Check to see if pair with eligible modes contains any mapped PLANit mode
   * 
   * @param modeResult of collectEligibleModes on zoning base handler
   * @return true when has at least one mapped PLANit mode present
   */
  public static boolean hasMappedPlanitMode(Pair<? extends SortedSet<String>, SortedSet<PredefinedModeType>> modeResult) {
    if(modeResult!= null && modeResult.second()!=null && !modeResult.second().isEmpty()) {
      /* eligible modes mapped to planit mode*/
      return true;
    }else {
      return false;
    }
  }

}

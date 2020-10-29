package org.planit.osm.physical.network.macroscopic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.geo.PlanitJtsUtils;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.util.ModifiedLinkSegmentTypes;
import org.planit.osm.util.OsmAccessTags;
import org.planit.osm.util.OsmDirection;
import org.planit.osm.util.OsmDirectionTags;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmLaneTags;
import org.planit.osm.util.OsmOneWayTags;
import org.planit.osm.util.OsmPedestrianTags;
import org.planit.osm.util.OsmRailFeatureTags;
import org.planit.osm.util.OsmRailWayTags;
import org.planit.osm.util.OsmRoadModeCategoryTags;
import org.planit.osm.util.OsmRoadModeTags;
import org.planit.osm.util.OsmSpeedTags;
import org.planit.osm.util.OsmTags;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.utils.arrays.ArrayUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.graph.Edge;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations. We parse these entities in distinct order, first all nodes, then all ways, and then all relations. this allows
 * us to incrementally construct the network without backtracking or requiring the entire file to be in memory in addition to the memory model we're creating.
 * 
 * @author markr
 * 
 *
 */
public class PlanitOsmHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmHandler.class.getCanonicalName());
  
  /** regular expression used to identify non-word characters (a-z any case, 0-9 or _) or whitespace*/
  private static final String VALUETAG_SPECIALCHAR_STRIP_REGEX = "[^\\w\\s]";

  /** the network to populate */
  private final PlanitOsmNetwork network;

  /** the settings to adhere to */
  private final PlanitOsmSettings settings;

  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils;
  
  /** utility class for profiling this instance */
  private final PlanitOsmHandlerProfiler profiler;
  
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** Mapping from Osm node id to the links they are internal to. When done parsing, we verify if any
   * entry in the map contains more than one link in which case the two link intersect at a point other than the extremes
   * and we must break the link. Also, in case any existin link's extreme node is internal to any other link, the link where
   * this node is internal to must be split into two because a PLANit network requires all intersections of links to occur
   * at the end or start of a link
   */
  private final Map<Long, List<Link>> linkInternalOsmNodes;  
  
  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays;
  
  /** track all modified link segment types compared to the original defaults used in OSM, for efficient updates of the PLANit link segment types while parsing */
  private final ModifiedLinkSegmentTypes modifiedLinkSegmentTypes;
  
  /**
   * track the nodes by their external id so they can by looked up quickly while parsing ways
   */
  private final Map<Long,Node> nodesByExternalId = new HashMap<Long, Node>();
           
  /** Identify which links we should break that have the node to break at as one of its internal nodes
   * 
   * @param theNodeToBreakAt the node to break at
   * @param brokenLinksByOriginalExternalLinkId earlier broken links
   * @return the link to break, null if none could be found
   * @throws PlanItException thrown if error
   */
  private List<Link> findLinksToBreak(Node theNodeToBreakAt, Map<Long, Set<Link>> brokenLinksByOriginalExternalLinkId) throws PlanItException {
    List<Link> linksToBreak = null;
    if(theNodeToBreakAt != null && linkInternalOsmNodes.containsKey(theNodeToBreakAt.getExternalId())) {
      
      /* find the links to break assuming no links have been broken yet */
      linksToBreak = linkInternalOsmNodes.get(theNodeToBreakAt.getExternalId());
      
      /* find replacement links for the original link to break in case the original already has been broken and we should use 
       * one of the split off broken links instead of the original for the correct breaking for the given node (since it now resides on one of the broken
       * links rather than the original full link that no longer exists in that form */
      Set<Link> replacementLinks = new HashSet<Link>();
      Iterator<Link> linksToBreakIter = linksToBreak.iterator();
      while(linksToBreakIter.hasNext()) {
        Link orginalLinkToBreak = linksToBreakIter.next(); 
        
        if(brokenLinksByOriginalExternalLinkId.containsKey(orginalLinkToBreak.getExternalId())) {
          
          /* link has been broken before, find out in which of its broken links the node to break at resides on */
          Set<Link> earlierBrokenLinks = brokenLinksByOriginalExternalLinkId.get(orginalLinkToBreak.getExternalId());
          Link matchingEarlierBrokenLink = null;
          for(Link link : earlierBrokenLinks) {
            Optional<Integer> coordinatePosition = geoUtils.findCoordinatePosition(theNodeToBreakAt.getPosition().getCoordinate(),link.getGeometry());
            if(coordinatePosition.isPresent()) {
              matchingEarlierBrokenLink = link;
            }
          }
          
          /* remove original and mark found link as replacement link to break */
          linksToBreakIter.remove();          
          
          /* verify if match is valid (which it should be) */
          if(matchingEarlierBrokenLink==null) {
            LOGGER.warning(String.format("unable to locate broken sublink of OSM way %s (id:%d), likely malformed way encountered, ignored",
                orginalLinkToBreak.getExternalId(), orginalLinkToBreak.getId()));            
          }else {
            replacementLinks.add(matchingEarlierBrokenLink);
          }          
        }
      }
      linksToBreak.addAll(replacementLinks);
    }
    return linksToBreak;
  }  
  
  /** check if we should break the links on the passed in node and if so, do it
   * @param theNode to verify
   * @param brokenLinksByOriginalExternalLinkId track all broken links that originated from one original link, tracked by its external OSM id 
   * @throws PlanItException thrown if error
   */
  private void breakLinksWithInternalNode(Node theNode, Map<Long, Set<Link>> brokenLinksByOriginalExternalLinkId) throws PlanItException {
        
    /* find the link that we should break */
    List<Link> linksToBreak = findLinksToBreak(theNode, brokenLinksByOriginalExternalLinkId);
    if(linksToBreak != null) {
      /* performing breaking of links, returns the broken links by the original link's PLANit edge id */
      Map<Long, Set<Link>> localBrokenLinks = this.network.breakLinksAt(linksToBreak, theNode);           
      
      /* add newly broken links to the mapping from original external OSM link id, to the broken link that together form this entire original OSMway*/
      if(localBrokenLinks != null) {
        localBrokenLinks.forEach((id, links) -> {
          links.forEach( brokenLink -> {
            brokenLinksByOriginalExternalLinkId.putIfAbsent((Long) brokenLink.getExternalId(), new HashSet<Link>());
            brokenLinksByOriginalExternalLinkId.get(brokenLink.getExternalId()).add(brokenLink);
          });
        });        
      }  
    }     
  }
  
  /** verify from the passed in tags if a side walk or footway osmkey is present with any of these value tags
   * 
   * 
   * @param tags to verify
   * @return true when one or more of the tag values is found, false otherwise
   */
  private boolean hasExplicitSidewalkOrFootwayWithAccessValue(Map<String, String> tags, String... accessValueTags) {
    Set<String> osmPedestrianKeyTags = OsmPedestrianTags.getOsmPedestrianKeyTags();
    if(!Collections.disjoint(osmPedestrianKeyTags,tags.keySet())){           
      for(String osmKey : osmPedestrianKeyTags) {
        if(tags.containsKey(osmKey)) {
          if(PlanitOsmUtils.matchesAnyValueTag(tags.get(osmKey).replaceAll(VALUETAG_SPECIALCHAR_STRIP_REGEX, ""), accessValueTags)) {
            return true;
          }
        }
      }
    }
    return false;    
  }
  
  /** verify from the passed in tags if a side walk or footway is present that is accessible to pedestrians
   * 
   *  sidewalk=
   * <ul>
   * <li>yes</li>
   * <li>both</li>
   * <li>left</li>
   * <li>right</li>
   * </ul>
   * or footway=sidewalk
   * 
   * @param tags to verify
   * @return true when explicitly mentioned and available, false otherwise (could still support pedestrians if highway type suports it by default)
   */
  private boolean hasExplicitlyIncludedSidewalkOrFootway(Map<String, String> tags) {
    return hasExplicitSidewalkOrFootwayWithAccessValue(tags, OsmPedestrianTags.YES, OsmPedestrianTags.BOTH, OsmPedestrianTags.RIGHT, OsmPedestrianTags.LEFT, OsmPedestrianTags.SIDEWALK);
  }
  
  /** verify from the passed in tags if a side walk or footway is present that is not accesible to pedestrians based on
   * 
   *  sidewalk=
   * <ul>
   * <li>no</li>
   * <li>none</li>
   * <li>separate</li>
   * </ul>
   * 
   * @param tags to verify
   * @return true when explicitly mentioned and available, false otherwise (could still support pedestrians if highway type suports it by default)
   */
  private boolean hasExplicitlyExcludedSidewalkOrFootway(Map<String, String> tags) {
    return hasExplicitSidewalkOrFootwayWithAccessValue(tags, OsmPedestrianTags.SIDEWALK_NONE, OsmPedestrianTags.NO, OsmPedestrianTags.SEPARATE);
  }  
    
  
  /** collect all OSM modes with key=<OSM mode name> value: the access value tags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */
  private Collection<String> getOsmModesWithAccessValue(Map<String, String> tags, final String... modeAccessValueTags){
    return getPostfixedOsmModesWithAccessValue(null, tags, modeAccessValueTags);
  }
  
  /** collect all OSM modes with key=<OSM mode name>:postFix= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by (yes/no)
   * @return modes found with specified value tag
   */  
  private Collection<String> getPostfixedOsmModesWithAccessValue(String postFix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmModesWithAccessValue(false, postFix, tags, modeAccessValueTags);
  }  
  
  /** collect all OSM modes with key=preFix:<OSM mode name>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */  
  private Collection<String> getPrefixedOsmModesWithAccessValue(String prefix, Map<String, String> tags, final String... modeAccessValueTags) {
    return getPrefixedOrPostfixedOsmModesWithAccessValue(true, prefix, tags, modeAccessValueTags);
  }  
  
  /** collect all OSM modes with either preFix:<OSM mode name>= or postFix:<OSM mode name>= any of the modeAccessValueTags that are passed in. Note that the actual value of the tags will be stripped from special characters
   * to make it more universal to match the pre-specified mode access value tags that we expect to be passed in
   * 
   * @param isprefix when true prefix applied, when false, postfix
   * @param alteration, the post or prefix alteration of the mode key
   * @param tags to find explicitly included/excluded (planit) modes from
   * @param modeAccessValueTags used to filter the modes by
   * @return modes found with specified value tag
   */    
  private Collection<String> getPrefixedOrPostfixedOsmModesWithAccessValue(boolean isprefix, String alteration, Map<String, String> tags, final String... modeAccessValueTags) {
    Set<String> foundModes = new HashSet<String>();    
    
    /* osm modes extracted from road mode category */
    Collection<String> roadModeCategories = OsmRoadModeCategoryTags.getRoadModeCategories();
    for(String roadModeCategory : roadModeCategories) {
      String compositeKey = isprefix ? PlanitOsmUtils.createCompositeOsmKey(alteration, roadModeCategory) : PlanitOsmUtils.createCompositeOsmKey(roadModeCategory, alteration);      
      if(tags.containsKey(compositeKey)) {
        String valueTag = tags.get(roadModeCategory).replaceAll(VALUETAG_SPECIALCHAR_STRIP_REGEX, "");
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
      String compositeKey = isprefix ? PlanitOsmUtils.createCompositeOsmKey(alteration, roadMode) : PlanitOsmUtils.createCompositeOsmKey(roadMode, alteration);      
      if(tags.containsKey(compositeKey)){
        String valueTag = tags.get(roadMode).replaceAll(VALUETAG_SPECIALCHAR_STRIP_REGEX, "");
        for(int index = 0 ; index < modeAccessValueTags.length ; ++index) {
          if(modeAccessValueTags[index].equals(valueTag)){
            foundModes.add(roadMode);
          }
        }
      }
    }    
    return foundModes;
  }  
  
  /** Collect explicitly excluded modes from the passed in tags. 
   * Explicitly excluded is based on the following access mode specific tags are present:
   * 
   * mode_name=
   * <ul>
   * <li>no</li>
   * <li>use_sidepath</li>
   * <li>separate</li>
   * <li>delivery</li>
   * <li>customers</li>
   * <li>private</li>
   * <li>dismount</li>
   * <li>discouraged</li>
   * </ul>
   * or pedestrian specific exclusions see {@code hasExplicitlyExcludedSidewalkOrFootway}
   *  
   * @param tags to find explicitly excluded (planit) modes from
   * @param direction direction information that is already parsed
   * @param mainDirection  flag indicating if we are conducting this method in the main direction (forward) or the contraflow direction (backward)
   * @return the excluded planit modes supported by the parser in the designated direction
   */
  private Collection<Mode> getExplicitlyExcludedModes(Map<String, String> tags, boolean mainDirection) {    
    Set<Mode> excludedModes = null;
    
    /* exclusions can be implicitly tagged using the oneway tag so we must verify this first, i.e., oneway=yes equates to vehicle:backward=no and
     * oneway=-1 equates to vehicle:forward=no. So, when we do not explore the main direction, then a oneway=yes implies that the other direction is closed, so we identify those modes and excluded from that direction*/
    String osmDirectionValue = mainDirection ? OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION : OsmOneWayTags.YES;
    if(tags.containsKey(OsmOneWayTags.ONEWAY) && tags.get(OsmOneWayTags.ONEWAY).equals(osmDirectionValue)) {
      
      /* explored direction is not the oneway direction that is activated */
      excludedModes = settings.collectMappedPlanitModes(OsmRoadModeCategoryTags.getRoadModesByCategory(OsmRoadModeCategoryTags.VEHICLE)); 
    
    }else {
      
      /* specific exclusions are explored since the explored direction is available as a viable direction */
      final String[] accessTagsSignifyingExclusion = new String[]{OsmAccessTags.NO, OsmAccessTags.PRIVATE, OsmAccessTags.CUSTOMERS, OsmAccessTags.DISCOURAGED, OsmAccessTags.DELIVERY, OsmAccessTags.USE_SIDEPATH, OsmAccessTags.SEPARATE};
      /* first check for general exclusions of modes */
      excludedModes =  settings.collectMappedPlanitModes(getOsmModesWithAccessValue(tags, accessTagsSignifyingExclusion));
      
      /* now check direction specific exclusions of all modes
      /* see https://wiki.openstreetmap.org/wiki/Key:oneway */
      {
        /* check for exclusions in explicit directions matching our explored direction FORWARD/BACKWARD based*/    
        String osmDirectionCondition= mainDirection ? OsmDirectionTags.FORWARD : OsmDirectionTags.BACKWARD;
        Set<Mode> additionalExcludedModes = settings.collectMappedPlanitModes(getPostfixedOsmModesWithAccessValue(osmDirectionCondition, tags, accessTagsSignifyingExclusion));
        excludedModes.addAll(additionalExcludedModes);
        additionalExcludedModes = settings.collectMappedPlanitModes(getPostfixedOsmModesWithAccessValue(OsmDirectionTags.BOTH, tags, accessTagsSignifyingExclusion));
        excludedModes.addAll(additionalExcludedModes);
      }
      
      /* now check specific exclusions for modes with specific tags that cannot be applied to all modes*/
      if(settings.hasMappedPlanitMode(OsmRoadModeTags.FOOT) && hasExplicitlyExcludedSidewalkOrFootway(tags)) {
        excludedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.FOOT));
      }
       
    }
        
    return excludedModes;
  }  
  


  /** Collect explicitly included modes from the passed in tags.Explicitly included is based on the following access mode specific tags are present:
   * 
   * mode_name=
   * <ul>
   * <li>yes</li>
   * <li>designated</li>
   * <li>permissive</li>
   * </ul>
   * 
   * or pedestrian specific inclusions see {@code hasExplicitlyIncludedSidewalkOrFootway}
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param direction direction information that is already parsed
   * @param mainDirection  flag indicating if we are conducting this method in the main direction or the contraflow direction
   * @return the included planit modes supported by the parser in the designated direction
   */
  private Collection<Mode> getExplicitlyIncludedModes(Map<String, String> tags, boolean mainDirection) {
    
    Set<Mode> includedModes = null;
    
    /* inclusions can be implicitly related to the oneway tag so we verify this first, when oneway=yes, modes that are tagged with the "opposite" value indicate
     * the mode is accessible even though the default is closed. */
    String osmDirectionValue = mainDirection ? OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION : OsmOneWayTags.YES;
    if(tags.containsKey(OsmOneWayTags.ONEWAY) && tags.get(OsmOneWayTags.ONEWAY).equals(osmDirectionValue)) {
      /* exploring OPPOSITE direction of oneway OSM way*/ 
      
      /* all modes that are tagged with oneway:<mode> = no signify access in the non-oneway direction and should be included */
      includedModes = settings.collectMappedPlanitModes(getPrefixedOsmModesWithAccessValue(OsmOneWayTags.ONEWAY, tags, OsmOneWayTags.NO));
    
    }else {
      
      /* specific inclusions are explored since the explored direction is not generally tagged as one way, so it is considered only open to default modes unless indicated otherwise */
      final String[] accessTagsSignifyingInclusion = new String[]{OsmAccessTags.YES, OsmAccessTags.DESIGNATED, OsmAccessTags.PERMISSIVE};
      
      /* first check for general exclusions of modes */
      includedModes =  settings.collectMappedPlanitModes(getOsmModesWithAccessValue(tags, accessTagsSignifyingInclusion));
      
      CONTINUE HERE!
      
      /* now check direction specific inclusions across supported modes
      /* see https://wiki.openstreetmap.org/wiki/Key:oneway */
      {
        /* check for exclusions in explicit directions matching our explored direction FORWARD/BACKWARD based*/    
        String osmDirectionCondition= mainDirection ? OsmDirectionTags.FORWARD : OsmDirectionTags.BACKWARD;
        Set<Mode> additionalIncludedModes = settings.collectMappedPlanitModes(getConditionedOsmModesWithAccessValue(osmDirectionCondition, tags, accessTagsSignifyingInclusion));
        includedModes.addAll(additionalIncludedModes);
        additionalIncludedModes = settings.collectMappedPlanitModes(getConditionedOsmModesWithAccessValue(OsmDirectionTags.BOTH, tags, accessTagsSignifyingInclusion));
        includedModes.addAll(additionalIncludedModes);
      }
      
      /* pedestrian modes can also be added by means of defined sidewalks, footways, etc. This is covered separately as they might not specifically mention the OSM mode foot=x */
      if(settings.hasMappedPlanitMode(OsmRoadModeTags.FOOT) && hasExplicitlyIncludedSidewalkOrFootway(tags)) {
        includedModes.add(settings.getMappedPlanitMode(OsmRoadModeTags.FOOT));
      }
      
      /* bicycle modes can also be explicitly added through oneway:bicycle tags or cycleway = x tags, which are 
      if(settings.hasMappedPlanitMode(OsmRoadModeTags.BICYCLE)) {
        oneway:bicycle = shoulder/no (no means it allows for both directons)
        
        cycleway = share_busway/lane/track
          :left (lefthand drive)
          :right (righthand drive)
              :oneway = does not change anything
      }      
       
    }
        
    return includedModes;           
    
    /* OLD ONLY USE FOR REFERENCE */
    
// 
//    Collection<Mode> excludedModes =  settings.collectMappedPlanitModes(
//     getOsmModesWithAccessValue(tags, 
//         OsmAccessTags.NO, OsmAccessTags.USE_SIDEPATH, OsmAccessTags.DELIVERY,OsmAccessTags.SEPARATE, OsmAccessTags.CUSTOMERS, OsmAccessTags.PRIVATE, OsmAccessTags.DISMOUNT, OsmAccessTags.DISCOURAGED));    
  }    
  
  /** given the OSM way tags we construct or find the appropriate link segment types for both directions, if no better alternative could be found
   * than the one that is passed in is used, which is assumed to be the default link segment type for the OSM way.
   * <b>It is not assumed that changes to mode access are ALWAYS accompanied by an access=X. However when this tag is available we apply its umbrella result to either include or exclude all supported modes as a starting point</b>
   *  
   * @param osmWay the tags belong to
   * @param tags of the OSM way to extract the link segment type for
   * @param direction information already extracted from tags
   * @param linkSegmentType use thus far for this way
   * @return the link segment types for the main direction and contraflow direction
   * @throws PlanItException thrown if error
   */
  private Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> extractLinkSegmentTypeByOsmAccessTags(final OsmWay osmWay, final Map<String, String> tags, final OsmDirection direction, MacroscopicLinkSegmentType linkSegmentType) throws PlanItException {
    
    /* collect the link segment types for the two possible directions */
    boolean mainDirection = true;
    MacroscopicLinkSegmentType  mainDirectionLinkSegmentType = extractDirectionalLinkSegmentTypeByOsmAccessTags(osmWay, tags, direction, linkSegmentType, mainDirection);
    MacroscopicLinkSegmentType  contraFlowDirectionLinkSegmentType = extractDirectionalLinkSegmentTypeByOsmAccessTags(osmWay, tags, direction, linkSegmentType, !mainDirection);
    
    return new Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>(mainDirectionLinkSegmentType, contraFlowDirectionLinkSegmentType);    
  } 
  
  /** given the OSM way tags we construct or find the appropriate link segment type, if no better alternative could be found
   * than the one that is passed in is used, which is assumed to be the default link segment type for the OSM way.
   * <b>It is not assumed that changes to mode access are ALWAYS accompanied by an access=X. However when this tag is available we apply its umbrella result to either include or exclude all supported modes as a starting point</b>
   * 
   * The following access=X value tags correspond to a situation where all modes will be allowed unless specific exclusions are
   * provided:
   * 
   * access=
   *  <ul>
   *  <li>yes</li>
   *  </ul>
   *  
   *  If other access values are found it is assumed all modes are excluded unless specific inclusions are provided. Note that it is very well possible
   *  that due to the configuration chosen a modified link segment type is created that has no modes that are supported. In which case
   *  the link segment type is still created, but it is left to the user to identify the absence of supported planit modes and take appropriate action.
   *  
   * @param osmWay the tags belong to
   * @param tags of the OSM way to extract the link segment type for
   * @param direction information already extracted from tags
   * @param linkSegmentType use thus far for this way
   * @return the link segment types for the main direction and contraflow direction
   * @throws PlanItException thrown if error
   */  
  private MacroscopicLinkSegmentType extractDirectionalLinkSegmentTypeByOsmAccessTags(OsmWay osmWay, Map<String, String> tags, OsmDirection direction, MacroscopicLinkSegmentType linkSegmentType, boolean mainDirection) throws PlanItException {  
    
    /* identify explicitly excluded and included modes */
    Collection<Mode> excludedModes = getExplicitlyExcludedModes(tags, direction, mainDirection);
    Collection<Mode> includedModes = getExplicitlyIncludedModes(tags, direction, mainDirection);
        
    
    /* global access is defined for both ways, or only the main direction if one way */
    boolean accessTagAppliesToExploredDirection = !direction.isOneWay() || mainDirection;
    
    /* supplement with implicitly included modes for the explored direction */
    if(accessTagAppliesToExploredDirection && tags.containsKey(OsmAccessTags.ACCESS)) {
      String accessValue = tags.get(OsmAccessTags.ACCESS).replaceAll(VALUETAG_SPECIALCHAR_STRIP_REGEX, "");    
      if(accessValue.equals(OsmAccessTags.YES)) {
        includedModes.addAll(network.modes.getAll());
        includedModes.removeAll(excludedModes);
      }else {
        excludedModes.addAll(network.modes.getAll());
        excludedModes.removeAll(includedModes);
      }
    }
    
    /* identify differences with default link segment type in terms of mode access */
    Set<Mode> toBeAddedModes = linkSegmentType.getUnAvailableModesFrom(includedModes);
    Set<Mode> toBeRemovedModes = linkSegmentType.getAvailableModesFrom(excludedModes);    
    
    MacroscopicLinkSegmentType finalLinkSegmentType = linkSegmentType;
    if(!toBeAddedModes.isEmpty() || !toBeRemovedModes.isEmpty()) {
      
      finalLinkSegmentType = modifiedLinkSegmentTypes.getModifiedLinkSegmentType(linkSegmentType, toBeAddedModes, toBeRemovedModes);
      if(finalLinkSegmentType==null) {
        /* even though the segment type is modified, the modified version does not yet exist on the PLANit network, so create it */
        finalLinkSegmentType = network.registerUniqueCopyOf(linkSegmentType);
        
        /* update mode properties */
        String roadTypeKey = tags.containsKey(OsmHighwayTags.HIGHWAY) ? OsmHighwayTags.HIGHWAY : OsmRailWayTags.RAILWAY; 
        double osmHighwayTypeMaxSpeed = settings.getDefaultSpeedLimitByOsmWayType(roadTypeKey, tags.get(roadTypeKey));               
        network.addLinkSegmentTypeModeProperties(finalLinkSegmentType, toBeAddedModes, osmHighwayTypeMaxSpeed);
        finalLinkSegmentType.removeModeProperties(toBeRemovedModes);
        
        /* register modification */
        modifiedLinkSegmentTypes.addModifiedLinkSegmentType(linkSegmentType, finalLinkSegmentType, toBeAddedModes, toBeRemovedModes);
      }
      
    }
    
    return finalLinkSegmentType;
  } 

  /**
   * parse the maximum speed for the link segments
   * 
   * @param link on which link segments reside
   * @param direction osm direction information
   * @param tags osm tags
   * @throws PlanItException thrown if error
   */
  private void populateLinkSegmentsSpeed(Link link, OsmDirection direction, Map<String, String> tags) throws PlanItException {
    
    Double speedLimitAbKmh = null;
    Double speedLimitBaKmh = null;
    
    if(tags.containsKey(OsmSpeedTags.MAX_SPEED)) {
      /* regular speed limit for all available directions and across all modes */
      speedLimitAbKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED));
      speedLimitBaKmh = speedLimitAbKmh;
    }else if(tags.containsKey(OsmSpeedTags.MAX_SPEED_LANES)) {
      /* check for lane specific speed limit */
      double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_LANES));
      /* Note: PLANit does not support lane specific speeds at the moment, maximum speed across lanes is selected */      
      speedLimitAbKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);
      speedLimitBaKmh = speedLimitAbKmh;
    }else if( tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD) || tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)){
      /* check for forward speed limit */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD)) {
        speedLimitAbKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD));  
      }
      /* check for forward speed limit per lane */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)) {
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)); 
        speedLimitAbKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);   
      }
    }else if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)|| tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
      /* check for backward speed limit */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)) {
        speedLimitBaKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD));        
      }
      /* check for backward speed limit per lane */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
        double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)); 
        speedLimitBaKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);           
      }
    }else
    {
      /* no speed limit information, revert to defaults */
      speedLimitAbKmh = settings.getDefaultSpeedLimitByOsmWayType(tags);
      speedLimitBaKmh = speedLimitAbKmh;
      profiler.incrementMissingSpeedLimitCounter();
    }
    
    /* we assume the direction information is consistent with the speed information in the tags */
    
    if(!direction.isOneWay() || direction.isReverseDirection()) {
      PlanItException.throwIfNull(speedLimitBaKmh, "speed limit not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentBa()).setPhysicalSpeedLimitKmH(speedLimitBaKmh);
    }
    if(!direction.isOneWay() || !direction.isReverseDirection()) {
      PlanItException.throwIfNull(speedLimitAbKmh, "speed limit not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentAb()).setPhysicalSpeedLimitKmH(speedLimitAbKmh);
    }
    
    /* mode specific speed limits */
    //TODO
    
  }
  
  /**
   * parse the number of lanes on the link and link segments
   * 
   * @param link for which lanes are specified (and its link segments)
   * @param direction osm direction information for this link(segments)
   * @param tags containing lane information
   * @throws PlanItException 
   */
  private void populateLinkSegmentsLanes(Link link, OsmDirection direction, Map<String, String> tags) throws PlanItException {
    Integer totalLanes = null;
    Integer lanesAb = null;
    Integer lanesBa = null;    

    /* collect total and direction specific road based lane information */
    String osmWayKey = null;
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmWayKey = OsmHighwayTags.HIGHWAY;
      
      if(tags.containsKey(OsmLaneTags.LANES)) {
        totalLanes = Integer.parseInt(tags.get(OsmLaneTags.LANES));
      }    
      if(tags.containsKey(OsmLaneTags.LANES_FORWARD)) {
        lanesAb = Integer.parseInt(tags.get(OsmLaneTags.LANES_FORWARD));
      }
      if(tags.containsKey(OsmLaneTags.LANES_BACKWARD)) {
        lanesBa = Integer.parseInt(tags.get(OsmLaneTags.LANES_BACKWARD));
      }
      
      if( totalLanes!=null && lanesAb==null && lanesBa==null) {
          /* in case of one way link, total lanes = directional lanes, enforce this if explicit tag is missing */
        if(direction.isOneWay()) {
          lanesBa = direction.isReverseDirection() ? totalLanes : null;
          lanesAb = direction.isReverseDirection() ? null : totalLanes;
        }else if(totalLanes%2==0) {
          /* two directions, with equal number of lanes does not require directional tags, simply split in two */
          lanesBa = totalLanes/2;
          lanesAb = lanesBa;
        } 
      }
    /* convert number of tracks to lanes */
    }else if(tags.containsKey(OsmRailWayTags.RAILWAY)) {
      osmWayKey = OsmRailWayTags.RAILWAY;
      if(tags.containsKey(OsmRailFeatureTags.TRACKS)) {
        totalLanes = Integer.parseInt(tags.get(OsmRailFeatureTags.TRACKS));
      }   
    }
    
    /* we assume that only when both are not set something went wrong, otherwise it is assumed it is a one-way link and it is properly configured */
    if(lanesAb==null && lanesBa==null) {
      lanesAb = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      lanesBa = lanesAb;
      profiler.incrementMissingLaneCounter();
    }
        
    /* populate link segments */  
    if(!direction.isOneWay() || direction.isReverseDirection()) {
      PlanItException.throwIfNull(lanesBa, "number of lanes not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentBa()).setNumberOfLanes(lanesBa);
    }
    if(!direction.isOneWay() || !direction.isReverseDirection()) {
      PlanItException.throwIfNull(lanesAb, "number of lanes not available as expected for link segment");
      ((MacroscopicLinkSegment) link.getEdgeSegmentAb()).setNumberOfLanes(lanesAb);
    }
  }  
  
  /**
   * Extract the geometry for the passed in way
   * @param osmWay way to extract geometry from
   * @return line string instance representing the shape of the way
   * @throws PlanItException 
   */
  private LineString extractLinkGeometry(OsmWay osmWay) throws PlanItException {
    Coordinate[] coordinates = new Coordinate[osmWay.getNumberOfNodes()];
    int numberOfNodes = osmWay.getNumberOfNodes();
    for(int index = 0; index < numberOfNodes; ++index) {
      OsmNode osmNode = osmNodes.get(osmWay.getNodeId(index));
      if(osmNode == null) {
        throw new PlanItException(String.format("referenced osmNode %d in osmWay %d not available in OSM parser",osmWay.getNodeId(index), osmWay.getId()));
      }
      coordinates[index] = new Coordinate(PlanitOsmUtils.getXCoordinate(osmNode),PlanitOsmUtils.getYCoordinate(osmNode));
    }
    return  geoUtils.createLineStringFromCoordinates(coordinates);
  }  
  
  /**
   * Extract a PLANit node from the osmNode information
   * 
   * @param osmNodeId to convert
   * @return created or retrieved node
   * @throws PlanItException 
   */
  private Node extractNode(final long osmNodeId) throws PlanItException {
    
    Node node = nodesByExternalId.get(osmNodeId);
    if(node == null) {
      
      /* not yet created */      
      OsmNode osmNode = osmNodes.get(osmNodeId);
      if(osmNode==null) {
        throw new PlanItException(String.format("osmNodeId (%d) not provided by parser, unable to retrieve node",osmNodeId));
      }
      
      /* location info */
      Point geometry = null;
      try {
        geometry = geoUtils.createPoint(PlanitOsmUtils.getXCoordinate(osmNode), PlanitOsmUtils.getYCoordinate(osmNode));
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
      }

      /* create and register */
      node = network.nodes.registerNew(osmNodeId);
      node.setPosition(geometry);
      nodesByExternalId.put(osmNodeId, node);
     
      profiler.logNodeStatus(network.nodes.size());   
    }
    return node;
  }
  
  /** create and populate link if it does not already exists for the given two PLANit nodes based on the passed in osmWay information
   * 
   * @param nodeFirst extreme node of to be created link
   * @param nodeLast extreme node of to be created link
   * @param osmWay to populate link data with
   * @param tags to populate link data with
   * @return created or fetched link
   * @throws PlanItException thrown if error
   */
  private Link createAndPopulateLink(Node nodeFirst, Node nodeLast, OsmWay osmWay, Map<String, String> tags) throws PlanItException {

    LineString lineSring = extractLinkGeometry(osmWay);
    
    /* osm way is directional, link is not, check existence */
    Link link = null;
    if(nodeFirst != null) {
      Set<Edge> potentialEdges = nodeFirst.getEdges(nodeLast);
      for(Edge potentialEdge : potentialEdges) {
        Link potentialLink = ((Link)potentialEdge);
        if(link != null && potentialLink.getGeometry().equals(lineSring)) {
          /* matching start/end nodes, and geometry, so they are in indeed the same link*/
          link = potentialLink;
          break;
        }        
      }
    }
               
    if(link == null) {

      /* length and geometry */
      double linkLength = 0;      
      /* update the length based on the geometry */
      linkLength = geoUtils.getDistanceInKilometres(lineSring);
      
      /* create link */
      link = network.links.registerNew(nodeFirst, nodeLast, linkLength, true);      
      link.setGeometry(lineSring);      
      
      /* external id */
      link.setExternalId(osmWay.getId());
      
      if(tags.containsKey(OsmTags.NAME)) {
        link.setName(tags.get(OsmTags.NAME));
      }
    }
    return link;      
  }  
  
  /** extract a link from the way
   * @param osmWay the way to process
   * @param tags tags that belong to the way
   * @return the link corresponding to this way
   * @throws PlanItException thrown if error
   */
  private Link extractLink(OsmWay osmWay, Map<String, String> tags) throws PlanItException {
    
    if( osmWay.getId() == 52438419l) {
      int bla = 4;
    }     
    
    /* collect memory model nodes */
    int firstNodeIndex = 0;
    int lastNodeIndex = osmWay.getNumberOfNodes()-1;
    Node nodeFirst = extractNode(osmWay.getNodeId(firstNodeIndex));
    Node nodeLast = extractNode(osmWay.getNodeId(lastNodeIndex));
                          
    Link link = createAndPopulateLink(nodeFirst, nodeLast, osmWay, tags);   
    if(link != null) {
      registerLinkInternalOsmNodes(link,firstNodeIndex+1,lastNodeIndex-1, osmWay);                  
      profiler.logLinkStatus(network.links.size());
    }
    return link;
  }
  
  
  /** register all nodes within the provided (inclusive) range as link internal nodes for the passed in link
   * 
   * @param link to register link internal ndoes for
   * @param startIndex the start index
   * @param endIndex the end index
   * @param osmWay the link corresponds to
   */
  private void registerLinkInternalOsmNodes(Link link, int startIndex, int endIndex, OsmWay osmWay) {
    /* lay index on internal nodes of link to allow for splitting the link if needed due to intersecting internally with other links */
    for(int nodeIndex = startIndex; nodeIndex <= endIndex;++nodeIndex) {
      OsmNode internalNode = osmNodes.get(osmWay.getNodeId(nodeIndex));
      linkInternalOsmNodes.putIfAbsent(internalNode.getId(), new ArrayList<Link>());
      linkInternalOsmNodes.get(internalNode.getId()).add(link);
    }   
  }

  /** Extract a link segment from the way corresponding to the link and the indicated direction
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param linkSegmentType the link segment type corresponding to this way
   * @param directionAb the direction to create the segment for  
   * @return created link segment, or null if already exists
   * @throws PlanItException thrown if error
   */  
  private MacroscopicLinkSegment extractMacroscopicLinkSegment(OsmWay osmWay, Map<String, String> tags, Link link, MacroscopicLinkSegmentType linkSegmentType, boolean directionAb) throws PlanItException {
    MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) link.getEdgeSegment(directionAb);
    if(linkSegment == null) {
      linkSegment = network.linkSegments.registerNew(link, directionAb, true /*register on nodes and link*/);      
    }else{
      LOGGER.warning(String.format(
          "Already exists link segment (id:%d) between OSM nodes (%s, %s) of OSM way (%d), ignored entity",linkSegment.getId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), osmWay.getId()));
    }
    
    /* link segment type */
    linkSegment.setLinkSegmentType(linkSegmentType);
        
    profiler.logLinkSegmentStatus(network.linkSegments.size());      
    return linkSegment;
  }
  
  /** Extract one or two link segments from the way corresponding to the link
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param direction information already parsed based on the tags 
   * @param linkSegmentType the default link segment type corresponding to this way  
   * @return created link segment, or null if already exists
   * @throws PlanItException thrown if error
   */
  private void extractMacroscopicLinkSegments(OsmWay osmWay, Map<String, String> tags, Link link, OsmDirection direction, MacroscopicLinkSegmentType linkSegmentType) throws PlanItException {
            
    /* determine the initial direction to parse a link segment for in terms of the PLANit link's A->B direction */
    boolean directionAb = true;    
    /* when direction one way and not marked as reverse based on the OSM tags, but the osm geometry is in the B->A direction of the link, then we invert the link segment direction */
    if(direction.isOneWay() && !direction.isReverseDirection() && !link.isGeometryInAbDirection()) {
      directionAb = false;
    }
    
    /* direction 1 */
    directionAb = direction.isReverseDirection() ? !directionAb : directionAb;
    extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentType, directionAb);
        
    /* direction 2 */
    if(!direction.isOneWay()){
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentType, !directionAb);
    }
    
    /* speed */
    populateLinkSegmentsSpeed(link, direction, tags); //TODO for contraflow
    /* lanes */
    populateLinkSegmentsLanes(link, direction, tags); //TODO for contraflow
    
  }  
  
  /** extract a link for part of a circular way based on the two passed in PLANit nodes which are residing on the circular way's geometry
   * 
   * @param startNodeIndex first node somewhere on the circular geometry
   * @param endNodeIndex last node somewhere else on the circular geometry
   * @param linkSegmentType, the link segment type to apply
   * @param circularOsmWay the original circular way
   * @param osmWayTags tags of the circular way
   * @throws PlanItException thrown if error
   */
  private void extractPartialOsmWay(int startNodeIndex, int endNodeIndex, MacroscopicLinkSegmentType linkSegmentType, OsmWay circularOsmWay, Map<String, String> osmWayTags) throws PlanItException {
    Node nodeFirst = extractNode(circularOsmWay.getNodeId(startNodeIndex));
    Node nodeLast = extractNode(circularOsmWay.getNodeId(endNodeIndex));
    
    Link link = createAndPopulateLink(nodeFirst, nodeLast, circularOsmWay, osmWayTags);
    if(link != null) {
      registerLinkInternalOsmNodes(link,startNodeIndex+1,endNodeIndex-1, circularOsmWay);                  
      profiler.logLinkStatus(network.links.size());
    }    
    
    /* update geometry and length based on partial link start and end node */
    LineString updatedGeometry = geoUtils.createCopyWithoutCoordinatesBefore(startNodeIndex, link.getGeometry());
    if(endNodeIndex < startNodeIndex) {
      /* When the last node position is located before the first (possible because the way is circular)*/
      /* supplement with coordinates from the beginning of the original circular way */
      LineString overFlowGeometry = geoUtils.createCopyWithoutCoordinatesAfter(endNodeIndex, link.getGeometry());
      updatedGeometry = geoUtils.concatenate(updatedGeometry, overFlowGeometry);
    }else {
      /* present, so simply remove coordinates after */
      updatedGeometry = geoUtils.createCopyWithoutCoordinatesAfter(endNodeIndex-startNodeIndex, updatedGeometry);
    }
    link.setGeometry(updatedGeometry);
    link.setLengthKm(geoUtils.getDistanceInKilometres(updatedGeometry));          
    
    /* extract directed link segment */
    extractMacroscopicLinkSegments(circularOsmWay, osmWayTags, link, linkSegmentType);
  }  
  
  /**
   * now parse the remaining circular osmWays, which by default are converted into multiple links/linksegments for each part of
   * the circular way in between connecting in and outgoing links/linksegments that were parsed during the regular parsing phase
   * 
   * @param circularOsmWay the circular osm way to parse 
   * @throws PlanItException thrown if error
   */
  protected void handleRawCircularWay(final OsmWay circularOsmWay) throws PlanItException {
    
    if(circularOsmWay.getId()==824117639) {
      int bla = 4;
    }
    
    Map<String, String> osmWayTags = OsmModelUtil.getTagsAsMap(circularOsmWay);    
    MacroscopicLinkSegmentType linkSegmentType = extractLinkSegmentType(circularOsmWay, osmWayTags);
    if(linkSegmentType != null) { 
      /* consider the entire circular way initially */
      handleRawCircularWay(circularOsmWay, osmWayTags, linkSegmentType, 0 /* start at initial index */);      
    }    
  }  
  
  /** Recursive method that processes osm ways that have at least one circular section in it, but this might not be perfect, i.e., the final node might
   * not connect to the initial node. to deal with this, we first identify the non-circular section(s), extract separate links for them, and then process
   * the remaining (perfectly) circular component of the OSM way via {@code handlePerfectCircularWay}
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param linkSegmentType to apply
   * @param initialNodeIndex offset for starting point, part of the recursion
   * @param finalNodeIndex offset of the final point, part of the recursion
   * @throws PlanItException thrown if error
   */
  private void handleRawCircularWay(final OsmWay circularOsmWay, final Map<String, String> osmWayTags, MacroscopicLinkSegmentType linkSegmentType, int initialNodeIndex) throws PlanItException {
    int finalNodeIndex = (circularOsmWay.getNumberOfNodes()-1);
    
    /* when circular road is not perfect, i.e., its end node is not the start node, we first split it
     * in a perfect circle and a regular non-circular osmWay */
    Pair<Integer,Integer> firstCircularIndices = PlanitOsmUtils.findIndicesOfFirstCircle(circularOsmWay, initialNodeIndex);            
    if(firstCircularIndices != null) {    
      /* unprocessed circular section exists */

      if(firstCircularIndices.getFirst() > initialNodeIndex ) {
        /* create separate link for the lead up part that appears to not be circular */         
        extractPartialOsmWay(0, firstCircularIndices.getFirst(), linkSegmentType, circularOsmWay, osmWayTags);                
        /* update offsets for circular part */
        initialNodeIndex = firstCircularIndices.getFirst();
      }
      
      /* continue with the remainder (if any) starting at the end point of the circular component 
       * this is done first because we want all non-circular components to be available as regular links before processing the circular parts*/
      if(firstCircularIndices.getSecond() < finalNodeIndex) {
        handleRawCircularWay(circularOsmWay, osmWayTags, linkSegmentType, firstCircularIndices.getSecond());
      }      
        
      /* extract the identified perfectly circular component */
      handlePerfectCircularWay(circularOsmWay, osmWayTags, linkSegmentType, firstCircularIndices.getFirst(), firstCircularIndices.getSecond());
      
    }else if(initialNodeIndex < finalNodeIndex) {
      /* last section is not circular, so extract partial link for it */
      extractPartialOsmWay(initialNodeIndex, finalNodeIndex, linkSegmentType, circularOsmWay, osmWayTags);      
    }           
  }

  /** Process a circular way that is assumed to be perfect for the given start and end node, i.e., its end node is the same as its start node
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param linkSegmentType to apply
   * @param initialNodeIndex where the circular section starts
   * @param finalNodeIndex where the circular section ends (at the start)
   * @throws PlanItException thrown if error
   */
  private void handlePerfectCircularWay(OsmWay circularOsmWay, Map<String, String> osmWayTags, MacroscopicLinkSegmentType linkSegmentType, int initialNodeIndex,
      int finalNodeIndex) throws PlanItException {

    int firstPartialLinkStartNodeIndex = -1;
    int partialLinkStartNodeIndex = -1;
    int partialLinkEndNodeIndex = -1;
    int numberOfConsideredNodes = initialNodeIndex-finalNodeIndex;
    
    /* construct partial links based on nodes on the geometry that are an extreme node of an already parsed link or are an internal node of an already parsed link */
    for(int index = initialNodeIndex ; index <= finalNodeIndex ; ++index) {
      long osmNodeId = circularOsmWay.getNodeId(index);
              
      if(nodesByExternalId.containsKey(osmNodeId) || linkInternalOsmNodes.containsKey(osmNodeId)) {                    
        
        if(partialLinkStartNodeIndex < 0) {
          /* set first node to earlier realised node */
          partialLinkStartNodeIndex = index;
          firstPartialLinkStartNodeIndex = partialLinkStartNodeIndex;
        }else {            
          /* create link from start node to the intermediate node that attaches to an already existing planit link on the circular way */          

          partialLinkEndNodeIndex = index;
          extractPartialOsmWay(partialLinkStartNodeIndex, partialLinkEndNodeIndex, linkSegmentType, circularOsmWay, osmWayTags);
           
          /* update first node to last node of this link for next partial link */          partialLinkStartNodeIndex = partialLinkEndNodeIndex;                   
        }
      }
    }
    
    if(partialLinkStartNodeIndex < 0) {
      LOGGER.warning(String.format("circular way %d could not be split based on PLANit nodes, not even a single node appears connected to the network, way ignored", circularOsmWay.getId()));
    }else if(partialLinkEndNodeIndex != finalNodeIndex){
      
      if(partialLinkEndNodeIndex < 0){        
        /* first partial link is not created either, only single connection point exists: use midway node as dummy node and extract two links + link segments accordingly*/        
        partialLinkEndNodeIndex = initialNodeIndex + (partialLinkStartNodeIndex + (numberOfConsideredNodes/2)) % numberOfConsideredNodes;
        extractPartialOsmWay(partialLinkStartNodeIndex, partialLinkEndNodeIndex, linkSegmentType, circularOsmWay, osmWayTags);        
        int newPartialLinkStartNodeIndex = partialLinkEndNodeIndex;
        partialLinkEndNodeIndex = partialLinkStartNodeIndex;
        partialLinkStartNodeIndex = newPartialLinkStartNodeIndex;
      }
      
      /* last partial link did not end at end of circular way but later, i.e., first partial link did not start at node zero.
       * finalise by creating the final partial link to the first partial links start node*/
      partialLinkEndNodeIndex = firstPartialLinkStartNodeIndex;       
      extractPartialOsmWay(partialLinkStartNodeIndex, partialLinkEndNodeIndex, linkSegmentType, circularOsmWay, osmWayTags);
    }    
    
  }

  /**
   * whenever we find that internal nodes are used by more than one link OR a node is an extreme node
   * on an existing link but also an internal link on another node, we break the links where this node
   * is internal. the end result is a situations where all nodes used by more than one link are extreme 
   * nodes, i.e., start/end nodes
   * @throws PlanItException  thrown if error
   */
  protected void breakLinksWithInternalConnections() throws PlanItException {
    /* track the broken links of by original OSM link id (external id) */
    Map<Long, Set<Link>> brokenLinksByOriginalOsmLinkId = new HashMap<Long, Set<Link>>();
        
    long linkIndex = -1;
    long originalNumberOfLinks = this.network.links.size();
    
    //linkIndex = (originalNumberOfLinks / 2);
    //originalNumberOfLinks /= 2; 
    
    while(++linkIndex<originalNumberOfLinks) {
      Link link = this.network.links.get(linkIndex);       
      
      if( ((Long)link.getExternalId()).longValue() == 784968342l) {
        int bla = 4;
      }      
      
      // 1. break links when a link's internal node is another existing link's extreme node 
      breakLinksWithInternalNode(link.getNodeA(), brokenLinksByOriginalOsmLinkId);
      linkInternalOsmNodes.remove(link.getNodeA().getExternalId());
      
      /* apply to node B as well */
      breakLinksWithInternalNode(link.getNodeB(), brokenLinksByOriginalOsmLinkId);
      linkInternalOsmNodes.remove(link.getNodeB().getExternalId());
    }
    
    //2. break links where an internal node of multiple links is shared, but it is never an extreme node of a link
    for(Entry<Long, List<Link>> entry : linkInternalOsmNodes.entrySet()) {        
      /* only intersection of links when at least two links are registered */
      if(entry.getValue().size() > 1) {
        /* node does not yet exist in PLANit network because it was internal node so far, so create it first */
        Node planitIntersectionNode = extractNode(entry.getKey());
        breakLinksWithInternalNode(planitIntersectionNode, brokenLinksByOriginalOsmLinkId);
      }
    }
    
    linkInternalOsmNodes.clear();
  }  
    

  /**
   * @return the network
   */
  protected MacroscopicNetwork getNetwork() {
    return network;
  }
  
  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param way the way
   * @param tags the tags of this way
   * @return the link segment type if available, otherwise nullis returned
   */
  protected MacroscopicLinkSegmentType getDefaultLinkSegmentTypeByOsmHighwayType(OsmWay osmWay, Map<String, String> tags) {
    MacroscopicLinkSegmentType linkSegmentType = null;
    String osmTypeValueToUse = null;
    String osmTypeKeyToUse = null;
    boolean isArea = false;
    
    /* highway (road) or railway (rail) */
    if (tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      osmTypeKeyToUse = OsmHighwayTags.HIGHWAY;
      isArea = OsmTags.isArea(tags);
    }else if(tags.containsKey(OsmRailWayTags.RAILWAY)) {
      osmTypeKeyToUse = OsmRailWayTags.RAILWAY;
      isArea = OsmRailWayTags.isRailBasedArea(tags.get(osmTypeKeyToUse));
    }
    
    if(osmTypeKeyToUse != null && !isArea) {  
      osmTypeValueToUse = tags.get(osmTypeKeyToUse);           
      linkSegmentType = network.getDefaultLinkSegmentTypeByOsmTag(osmTypeValueToUse);            
      if(linkSegmentType != null) {
        profiler.incrementOsmTagCounter(osmTypeValueToUse);        
        return linkSegmentType;
      }
      
      /* determine the reason why we couldn't find it */
      if(!settings.isOsmWayTypeDeactivated(osmTypeKeyToUse, osmTypeValueToUse)) {
        /*... not unsupported so something is not properly configured, or the osm file is corrupt or not conform the standard*/
        LOGGER.warning(String.format(
            "no link segment type available for OSM way: %s:%s (id:%d) --> ignored. Consider explicitly supporting or unsupporting this type", 
            osmTypeKeyToUse, osmTypeValueToUse, osmWay.getId()));              
      }
    }
    
    return linkSegmentType;
  }  
  

  /**
   * constructor
   * 
   * @param settings for the handler
   */
  public PlanitOsmHandler(final PlanitOsmNetwork network, final PlanitOsmSettings settings) {
    this.network = network;
    this.geoUtils = new PlanitJtsUtils(settings.getSourceCRS());
    this.network.setCoordinateReferenceSystem(settings.getSourceCRS());
    
    this.settings = settings;
    this.profiler  = new PlanitOsmHandlerProfiler();
    
    this.osmNodes = new HashMap<Long, OsmNode>();
    this.linkInternalOsmNodes = new HashMap<Long, List<Link>>();
    this.osmCircularWays = new HashMap<Long, OsmWay>();
    
    this.modifiedLinkSegmentTypes = new ModifiedLinkSegmentTypes();
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(network.linkSegments.size()>0,"network is expected to be empty at start of parsing OSM network, but it has link segments");
    PlanItException.throwIf(network.links.size()>0,"network is expected to be empty at start of parsing OSM network, but it has links");
    PlanItException.throwIf(network.nodes.size()>0,"network is expected to be empty at start of parsing OSM network, but it has nodes");
    
    /* create the supported link segment types on the network */
    network.createOsmCompatibleLinkSegmentTypes(settings);
    /* when modes are deactivated causing supported osm way types to have no active modes, add them to unsupport way types to avoid warnings during parsing */
    settings.excludeOsmWayTypesWithoutActivatedModes();
    settings.logUnsupportedOsmWayTypes();
  }  


  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // not used
  }

  /**
   * construct PLANit nodes from OSM nodes
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {
    /* store for later processing */
    osmNodes.put(osmNode.getId(), osmNode);   
  }

  /**
   * parse an osm way to extract link and link segments (including type). If insufficient information
   * is available the handler will try to infer the missing data by using defaults set by the user
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
    
    if(osmWay.getId() == 824117639) {
      int bla = 4;
    }
    
    /* ignore all ways that are in fact areas */
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);
          
    try {        
      
      OsmDirection direction = new OsmDirection(tags, settings.getCountryName());
      MacroscopicLinkSegmentType linkSegmentType = extractLinkSegmentType(osmWay,tags, direction);
      if(linkSegmentType != null) {
        
        for(Entry<String,String> entry: tags.entrySet()) {
          if(entry.getKey().contains("oneway:bicycle") ) {
            int bla = 4;
          }
        }        
        
        if(PlanitOsmUtils.isCircularRoad(osmWay, tags, false)) {
          /* postpone creation of link(s) for ways that have a circular component */
          /* Note: in OSM roundabouts are a circular way, in PLANit, they comprise several one-way link connecting exists and entries to the roundabout */
          osmCircularWays.put(osmWay.getId(), osmWay);        
        }else{            
          
          /* a link only consists of start and end node, no direction and has no model information */
          Link link = extractLink(osmWay, tags);                
          
          /* a macroscopic link segment is directional and can have a shape, it also has model information */
          extractMacroscopicLinkSegments(osmWay, tags, link, direction, linkSegmentType);            
        }                    
      }
      
    } catch (PlanItException e) {
      if(e.getCause() != null && e.getCause() instanceof PlanItException) {
        LOGGER.severe(e.getCause().getMessage());
      }
      LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
    }
        
  }

  /** extract the correct link segment type based on the configruation of supported modes, the defaults for the given osm way and any 
   * modifications to the mode access based on the passed in tags
   * of the OSM way
   * @param osmWay the way this type extraction is executed for 
   * @param tags tags belonging to the OSM way
   * @param direction information already parsed based on the tags
   * @return appropriate link segment type (if any)
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType extractLinkSegmentType(OsmWay osmWay, Map<String, String> tags, OsmDirection direction) throws PlanItException {
    /* a default link segment type should be available as starting point*/
    MacroscopicLinkSegmentType linkSegmentType = getDefaultLinkSegmentTypeByOsmHighwayType(osmWay, tags);
    if(linkSegmentType != null) {      
      /* in case tags indicate changes from the default, we update the link segment type */
      linkSegmentType = extractLinkSegmentTypeByOsmAccessTags(osmWay, tags, direction, linkSegmentType);
      if(linkSegmentType.hasAvailableModes()) {
        return linkSegmentType;
      }
    }
    return null;
  }

  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    // delegate
  }

  @Override
  public void complete() throws IOException {
    
    /* process circular ways*/
    for(Entry<Long,OsmWay> entry : osmCircularWays.entrySet()) {
      try {        
        handleRawCircularWay(entry.getValue());        
      }catch (PlanItException e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("unable to process circular way OSM id: %d",entry.getKey()));
      }        
    }     
        
    /* break all links that have internal nodes that are extreme nodes of other links*/
    try {
      breakLinksWithInternalConnections();
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to break OSM links with internal intersections");
    }      
    
    // useful for debugging
    network.validate();
    
    /* stats*/
    profiler.logProfileInformation(network);
        
    LOGGER.info("DONE");

    /* free memory */
    osmCircularWays.clear();    
    osmNodes.clear();
    nodesByExternalId.clear();
  }

}

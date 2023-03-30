package org.goplanit.osm.converter.network;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.goplanit.osm.converter.OsmModeConversionBase;
import org.goplanit.osm.converter.helper.OsmLanesModeTaggingSchemeHelper;
import org.goplanit.osm.converter.helper.OsmModeLanesTaggingSchemeHelper;
import org.goplanit.osm.tags.OsmAccessTags;
import org.goplanit.osm.tags.OsmBicycleTags;
import org.goplanit.osm.tags.OsmBusWayTags;
import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmJunctionTags;
import org.goplanit.osm.tags.OsmLaneTags;
import org.goplanit.osm.tags.OsmOneWayTags;
import org.goplanit.osm.tags.OsmPedestrianTags;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmRoadModeCategoryTags;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.osm.util.OsmModeUtils;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.osm.util.OsmWayUtils;
import org.goplanit.utils.locale.DrivingDirectionDefaultByCountry;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;

/**
 * Class to support parsing of OSM modes on OSM ways given the configuration provided for each PLANit network layer
 * 
 * @author markr
 *
 */
public class OsmNetworkLayerModeConversion extends OsmModeConversionBase {
  
  /** the network layer to use */
  private final MacroscopicNetworkLayer networkLayer;  
    
  /** helper class to deal with parsing tags under the lanesMode tagging scheme for eligible modes */
  private OsmLanesModeTaggingSchemeHelper lanesModeSchemeHelper = null;
  
  /** helper class to deal with parsing tags under the modeLanes tagging scheme for eligible modes */
  private OsmModeLanesTaggingSchemeHelper modeLanesSchemeHelper = null;  

  /** All modes that are explicitly made (un)available in a particular direction (without any further details are identified via this method, e.g. bus:forward=yes
   * @param tags to verify
   * @param isForwardDirection forward when true, backward otherwise
   * @param included when true included modes are identified, otherwise excluded modes
   * @return the mapped PLANitModes found
   */
  private Collection<? extends Mode> getModesForDirection(Map<String, String> tags, boolean isForwardDirection, boolean included) {
    String osmDirectionCondition= isForwardDirection ? OsmDirectionTags.FORWARD : OsmDirectionTags.BACKWARD;
    String[] accessValueTags = included ?  OsmAccessTags.getPositiveAccessValueTags() : OsmAccessTags.getNegativeAccessValueTags();
    /* found modes with given access value tags in explored direction */
    Set<Mode> foundModes = getActivatedPlanitModes(OsmModeUtils.getPostfixedOsmRoadModesWithValueTag(osmDirectionCondition, tags, accessValueTags));
    return foundModes;
  }

  /** see {@link #getModesForDirection(Map, boolean, boolean) */
  private Collection<? extends Mode> getExplicitlyIncludedModesForDirection(Map<String, String> tags, boolean isForwardDirection) {
    return getModesForDirection(tags, isForwardDirection, true /*included*/);
  }

  /** see {@link #getModesForDirection(Map, boolean, boolean) */
  private Collection<? extends Mode> getExplicitlyExcludedModesForDirection(Map<String, String> tags, boolean isForwardDirection) {
    return getModesForDirection(tags, isForwardDirection, false /*excluded*/);
  }

  /** Whenever a mode is tagged as a /<mode/>:oneway=no it implies it is available in both directions. This is what is checked here. Typically used in conjunction with a oneway=yes
   * tag but not necessarily
   * 
   * @param tags to verify
   * @return the mapped PLANitModes found
   */
  private Set<Mode> getExplicitlyIncludedModesNonOneWay(Map<String, String> tags) {
    return getActivatedPlanitModes(OsmModeUtils.getPrefixedOsmRoadModesWithValueTag(OsmOneWayTags.ONEWAY, tags, OsmOneWayTags.NO));
  }

  /** Collect explicitly included modes for a bi-directional OSMway, i.e., so specifically NOT a one way. 
   * 
   * <ul>
   * <li>bicycle: when cycleway=/<positive access value/></li>
   * <li>bicycle: when cycleway:<driving_location>=/<positive access value/></li>
   * <li>bicycle: when cycleway:<driving_location>:oneway=/<negative access value/></li>
   * <li>bus: when busway:<driving_location>=/<positive access value/></li>
   * </ul>
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isDrivingDirectionLocationLeft  flag indicating if driving location that is explored corresponds to the left hand side of the way
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Set<Mode> getExplicitlyIncludedModesTwoWayForLocation(Map<String, String> tags, boolean isDrivingDirectionLocationLeft) {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {
      /*... bicycles --> include when inclusion indicating tag is present for correct location [explored direction]*/
      if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE)) {
          if(OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY)) {
            /* both directions implicit*/
            includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
          }else if(OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, isDrivingDirectionLocationLeft ? OsmBicycleTags.CYCLEWAY_LEFT : OsmBicycleTags.CYCLEWAY_RIGHT)) {
            /* cycleway scheme, location based (single) direction, see also https://wiki.openstreetmap.org/wiki/Bicycle example T4 */
            includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
          }else if(OsmBicycleTags.isNoOneWayCyclewayInAnyLocation(tags)) {
            /* location is explicitly in both directions (non-oneway on either left or right hand side, see also https://wiki.openstreetmap.org/wiki/Bicycle example T2 */
            includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
          }
      }      
            
      /*... buses --> include when inclusion indicating tag is present for correct location [explored direction], see https://wiki.openstreetmap.org/wiki/Bus_lanes */
      if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BUS) && OsmLaneTags.isLaneIncludedForAnyOf(tags, isDrivingDirectionLocationLeft ? OsmBusWayTags.BUSWAY_LEFT : OsmBusWayTags.BUSWAY_LEFT)) {
        includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BUS));
      }       
      
    }      
  
    return includedModes;
  }

  /** Collect explicitly excluded modes for a bi-directional OSMway, i.e., so specifically NOT a one way. 
   * 
   * <ul>
   * <li>bicycle: when cycleway:<driving_location>=/<negative access value/></li>
   * </ul>
   * 
   * @param tags to find explicitly excluded (planit) modes from
   * @param isDrivingDirectionLocationLeft  flag indicating if driving location that is explored corresponds to the left hand side of the way
   * @return the excluded planit modes supported by the parser in the designated direction
   */    
  private Collection<? extends Mode> getExplicitlyExcludedModesTwoWayForLocation(Map<String, String> tags, boolean isDrivingDirectionLocationLeft) {
    Set<Mode> excludedModes = new HashSet<Mode>();
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes are only included with tags when at least one lane is available, so not exclusions can be gathered from them*/      
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {
      /*... bicycles --> exclude when explicitly excluded in location of explored driving direction*/
      if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayExcludedForAnyOf(tags, isDrivingDirectionLocationLeft ? OsmBicycleTags.CYCLEWAY_LEFT : OsmBicycleTags.CYCLEWAY_RIGHT)) {
        excludedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
      }      
            
      /*... for buses --> left and/or right non-presence explicit exclusions hardly exist, so not worthwhile checking for */      
    } 
    return excludedModes; 
  }

  /** Collect explicitly included modes from the passed in tags but only for the oneway main direction.
   * 
   * A mode is considered explicitly included when
   * <ul>
   * <li>lanes:/<mode/>=*</li>
   * <li>/<mode/>:lanes=*</li>
   * <li>bicycle: when cycleway:\<any_location\>= see positive cycleway access values </li>
   * <li>bus: when busway:\<any_location\>=lane is present</li>
   * </ul>
   * 
   * cycleway positive access values=
   * <ul>
   * <li>lane</li>
   * <li>shared_lane</li>
   * <li>share_busway</li>
   * <li>share_busway</li>
   * <li>shoulder</li>
   * <li>track</li>
   * </ul>
   * </p>
   * <p>
   * 
   * @param tags to find explicitly included (planit) modes from
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Collection<? extends Mode> getExplicitlyIncludedModesOneWayMainDirection(Map<String, String> tags) {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes */
    {
      /* see example of both lanes:mode and mode:lanes schemes specific for bus on https://wiki.openstreetmap.org/wiki/Bus_lanes, but works the same way for other modes */
      if(lanesModeSchemeHelper!=null && lanesModeSchemeHelper.hasEligibleModes()) {
        /* lanes:<mode>=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        lanesModeSchemeHelper.getModesWithLanesWithoutDirection(tags).forEach(osmMode -> includedModes.add(getActivatedPlanitMode(osmMode)));
      }
      if(modeLanesSchemeHelper!=null && modeLanesSchemeHelper.hasEligibleModes()) {
        /* <mode>:lanes=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        modeLanesSchemeHelper.getModesWithLanesWithoutDirection(tags).forEach(osmMode -> includedModes.add(getActivatedPlanitMode(osmMode)));
      }          
    }        
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {      
      /* RIGHT and LEFT on a oneway street DO NOT imply direction (unless possibly in the value but not via the key)
      
      /*... bicycles explore location specific (left, right) presence [main direction]*/
      if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY_LEFT, OsmBicycleTags.CYCLEWAY_RIGHT)) {
        includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
      }          
      
      /*... for buses adopting the busway scheme approach [main direction] */   
      if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BUS) && OsmLaneTags.isLaneIncludedForAnyOf(tags, OsmBusWayTags.BUSWAY_LEFT, OsmBusWayTags.BUSWAY_RIGHT)) {
          includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BUS));
      }            
    }  
    
    return includedModes;
  }

  /** Collect explicitly excluded modes from the passed in tags but only for the oneway main direction.
   * 
   * A mode is considered explicitly excluded when
   * <ul>
   * <li>bicycle: when cycleway:<any_location>=/<negative_access_values/>see positive cycleway access values </li>
   * </ul>
   * 
   * @param tags to find explicitly excluded (planit) modes from
   * @return the excluded planit modes supported by the parser in the designated direction
   */    
  private Set<Mode> getExplicitlyExcludedModesOneWayMainDirection(Map<String, String> tags) {
    Set<Mode> excludedModes = new HashSet<Mode>();  
    
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes are only included with tags when at least one lane is available, so not exclusions can be gathered from them*/                
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY */
    {
      /* alternatively the busway or cycleway scheme can be used, which has to be checked separately by mode since it is less generic */
      
      /*... bicycles --> left or right non-presence [explored direction] */
      if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayExcludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY_LEFT, OsmBicycleTags.CYCLEWAY_RIGHT)) {
        excludedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
      }          
      
      /*... for buses --> left and/or right non-presence explicit exclusions hardly exist, so not worthwhile checking for */              
    } 
    return excludedModes;
  }

  /** Collect explicitly included modes from the passed in tags but only for modes explicitly included in the opposite direction of a one way OSM way
   * 
   * <ul>
   * <li>bicycle: when cycleway=opposite_X is present</li>
   * <li>bus: when busway=opposite_lane is present</li>
   * </ul>
   *  
   * @param tags to find explicitly included (planit) modes from
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Set<Mode> getExplicitlyIncludedModesOneWayOppositeDirection(Map<String, String> tags) {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* TAGGING SCHEMES - BUSWAY/CYCLEWAY is the only scheme with opposite direction specific tags for oneway OSM ways*/
  
    /*... bicycle location [opposite direction], as per https://wiki.openstreetmap.org/wiki/Key:cycleway cycleway:left=opposite or cycleway:right=opposite are not valid*/
    if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isOppositeCyclewayIncludedForAnyOf(tags, OsmBicycleTags.getCycleWayKeyTags(false /* no left right*/ ))) {
      includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
    }
    
    /*... buses for busway scheme [opposite direction], see https://wiki.openstreetmap.org/wiki/Bus_lanes */
    if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BUS) && OsmLaneTags.isOppositeLaneIncludedForAnyOf(tags, OsmBusWayTags.getBuswaySchemeKeyTags())) {
      includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BUS));
    }          
    
    return includedModes;
  }

  /** Collect explicitly excluded modes from the passed in tags but only for modes explicitly excluded in the opposite direction of a one way OSM way. In this case
   * These are always and only:
   * 
   * <ul>
   * <li>all vehicular modes</li>
   * </ul>
   *
   * @return the included planit modes supported by the parser in the designated direction
   */  
  private Collection<? extends Mode> getExplicitlyExcludedModesOneWayOppositeDirection() {
    /* vehicular modes are excluded, equates to vehicle:<direction>=no see https://wiki.openstreetmap.org/wiki/Key:oneway */
    Collection<String> excludedModes = OsmRoadModeCategoryTags.getRoadModesByCategory(OsmRoadModeCategoryTags.VEHICLE);
    /* in case rail way is marked as one way, we exclude all rail modes as well */
    excludedModes.addAll(OsmRailModeTags.getSupportedRailModeTags());
    return getActivatedPlanitModes(excludedModes);
  }

  /** Collect explicitly included modes from the passed in tags but only for tags that have the same meaning regarldess if the way is tagged as one way or not
   * 
   * 
   * Modes are included when tagged 
   * <ul>
   * <li>/<mode/>:oneway=no</li>
   * <li>/<mode/>:<explored_direction>=yes</li>
   * <li>lanes:/<mode/>:/<explored_direction/>=*</li>
   * <li>/<mode/>:lanes:/<explored_direction/>=*</li>
   * <li>cycleway=both if bicycles</li>
   * <li>footway is present if pedestrian</li>
   * <li>sidewalk is present if pedestrian</li>
   * </ul>
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @return the included planit modes supported by the parser in the designated direction
   */
  private Set<Mode> getExplicitlyIncludedModesOneWayAgnostic(Map<String, String> tags, boolean isForwardDirection)   
  {
    Set<Mode> includedModes = new HashSet<Mode>();
    
    /* ...all modes --> tagged with oneway:<mode>=no signify access BOTH directions and should be included. Regarded as generic because can be used on non-oneway streets */
    includedModes.addAll(getExplicitlyIncludedModesNonOneWay(tags));
    
    /* ...all modes --> inclusions in explicit directions matching our explored direction, e.g. bicycle:forward=yes based*/
    includedModes.addAll(getExplicitlyIncludedModesForDirection(tags, isForwardDirection));
    
    /* LANE TAGGING SCHEMES - LANES:<MODE> and <MODE>:lanes */
    {
      /* see example of both lanes:mode and mode:lanes schemes specific for bus on https://wiki.openstreetmap.org/wiki/Bus_lanes, but works the same way for other modes */
      if(lanesModeSchemeHelper!=null && lanesModeSchemeHelper.hasEligibleModes()) {
        /* lanes:<mode>:<direction>=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        lanesModeSchemeHelper.getModesWithLanesInDirection(tags, isForwardDirection).forEach(osmMode -> includedModes.add(getActivatedPlanitMode(osmMode)));
      }if(modeLanesSchemeHelper!=null && modeLanesSchemeHelper.hasEligibleModes()) {
        /* <mode>:lanes:<direction>=* scheme, collect the modes available this way, e.g. bicycle, hgv, bus if eligible */        
        modeLanesSchemeHelper.getModesWithLanesInDirection(tags, isForwardDirection).forEach(osmMode -> includedModes.add(getActivatedPlanitMode(osmMode)));
      }
    }
    
    /*...bicycle --> explicitly tagged cycleway being available in both directions */
    if(getSettings().hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayIncludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY_BOTH)) {
        includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
    }
      
    /* ...pedestrian modes can also be added by means of defined sidewalks, footways, etc. Note that pedestrians can always move in both direction is any infrastructure is present */
    if(getSettings().hasMappedPlanitModeType(OsmRoadModeTags.FOOT) && OsmPedestrianTags.hasExplicitlyIncludedSidewalkOrFootway(tags)) {
      includedModes.add(getActivatedPlanitMode(OsmRoadModeTags.FOOT));
    }
    
    return includedModes;
  }

  /** Collect explicitly excluded modes from the passed in tags but only for tags that have the same meaning regardless if the way is tagged as one way or not
   * 
   * Modes are excluded when tagged 
   * <ul>
   * <li>/<mode/>=/<negative_access_value/></li>
   * <li>/<mode/>:/<explored_direction/>==/<negative_access_value/></li>
   * <li>cycleway or cycleway:both=/<negative_access_value/> if bicycles</li>
   * <li>footway is excluded if pedestrian</li>
   * <li>sidewalk is excluded if pedestrian</li>
   * </ul>
   * 
   * @param tags to find explicitly excluded (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @param settings to access mapping from osm mdoes to planit modes
   * @return the excluded planit modes supported by the parser in the designated direction
   */
  private Set<Mode> getExplicitlyExcludedModesOneWayAgnostic(Map<String, String> tags, boolean isForwardDirection, OsmNetworkReaderSettings settings){
    Set<Mode> excludedModes = new HashSet<Mode>();
    
    /* ... roundabout is implicitly one way without being tagged as such, all modes in non-main direction are to be excluded */
    if(OsmJunctionTags.isPartOfCircularWayJunction(tags) && OsmWayUtils.isCircularWayDirectionClosed(tags, isForwardDirection, settings.getCountryName())) {
      excludedModes.addAll(networkLayer.getSupportedModes());
    }else {
  
      /* ... all modes --> general exclusion of modes */
      excludedModes =  getActivatedPlanitModes(OsmModeUtils.getOsmRoadModesWithValueTag(tags, OsmAccessTags.getNegativeAccessValueTags()));
      
      /* ...all modes --> exclusions in explicit directions matching our explored direction, e.g. bicycle:forward=no, FORWARD/BACKWARD/BOTH based*/
      excludedModes.addAll(getExplicitlyExcludedModesForDirection(tags, isForwardDirection));
      
      /*... busways are generaly not explicitly excluded when keys are present, hence no specific check */
      
      /*...bicycle --> cycleways explicitly tagged being not available at all*/
      if(settings.hasAnyMappedPlanitModeType(OsmRoadModeTags.BICYCLE) && OsmBicycleTags.isCyclewayExcludedForAnyOf(tags, OsmBicycleTags.CYCLEWAY,OsmBicycleTags.CYCLEWAY_BOTH)) {
        excludedModes.add(getActivatedPlanitMode(OsmRoadModeTags.BICYCLE));
      }
        
      /* ...pedestrian modes can also be excluded by means of excluded sidewalks, footways, etc. Note that pedestrians can always move in both direction is any infrastructure is present */
      if(settings.hasMappedPlanitModeType(OsmRoadModeTags.FOOT) && OsmPedestrianTags.hasExplicitlyExcludedSidewalkOrFootway(tags)) {
        excludedModes.add(getActivatedPlanitMode(OsmRoadModeTags.FOOT));
      }  
      
    }
  
    return excludedModes;
  }

  /** Constructor
   * 
   * @param settings to use
   * @param networkLayer this parser is applied to
   */
  public OsmNetworkLayerModeConversion(OsmNetworkReaderSettings settings, MacroscopicNetworkLayer networkLayer) {
    super(settings, networkLayer.getSupportedModes());
    
    this.networkLayer = networkLayer;
    
    /* initialise the tagging scheme helpers based on the registered modes */
    if(OsmLanesModeTaggingSchemeHelper.requireLanesModeSchemeHelper(settings, networkLayer)) {
      this.lanesModeSchemeHelper = new OsmLanesModeTaggingSchemeHelper(OsmLanesModeTaggingSchemeHelper.getEligibleLanesModeSchemeHelperModes(settings, networkLayer));
    }
    if(OsmModeLanesTaggingSchemeHelper.requireLanesModeSchemeHelper(settings, networkLayer)) {
      this.modeLanesSchemeHelper = new OsmModeLanesTaggingSchemeHelper(OsmModeLanesTaggingSchemeHelper.getEligibleModeLanesSchemeHelperModes(settings, networkLayer));
    }     
  }

  /** Collect explicitly included modes from the passed in tags. Given the many ways this can be tagged we distinguish between:
   * 
   * <ul>
   * <li>mode inclusions agnostic to the way being tagged as oneway or not {@link #getExplicitlyIncludedModesOneWayAgnostic(Map, boolean)}</li>
   * <li>mode inclusions when way is tagged as oneway and exploring the one way direction {@link #getExplicitlyIncludedModesOneWayMainDirection(Map)}</li>
   * <li>mode inclusions when way is tagged as oneway and exploring the opposite direction of oneway direction {@link #getExplicitlyIncludedModesOneWayOppositeDirection(Map)}</li>
   * <li>mode inclusions when we are NOT exploring the opposite direction of a oneway when present, e.g., either main direction of one way, or non-oneway {@link #getExplicitlyIncludedModesTwoWayForLocation(Map, boolean)}</li>
   * </ul>
   * 
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @param settings to access mapping from osm mdoes to planit modes
   * @return the included planit modes supported by the parser in the designated direction
   */
  public Set<Mode> getExplicitlyIncludedModes(Map<String, String> tags, boolean isForwardDirection, OsmNetworkReaderSettings settings) {          
    Set<Mode> includedModes = new HashSet<>();
    
    /* 1) generic mode inclusions INDEPENDENT of ONEWAY tags being present or not*/
    includedModes.addAll(getExplicitlyIncludedModesOneWayAgnostic(tags, isForwardDirection));
                          
    boolean exploreOneWayOppositeDirection = false;
    if(tags.containsKey(OsmOneWayTags.ONEWAY)){      
      /* mode inclusions for ONE WAY tagged way (RIGHT and LEFT on a oneway street DO NOT imply direction)*/
      
      final String oneWayValueTag = tags.get(OsmOneWayTags.ONEWAY);
      String osmDirectionValue = isForwardDirection ? OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION : OsmOneWayTags.YES;
  
      /* 2a) mode inclusions for ONE WAY OPPOSITE DIRECTION if explored */
      if(oneWayValueTag.equals(osmDirectionValue)) {        
        exploreOneWayOppositeDirection = true;        
        includedModes.addAll(getExplicitlyIncludedModesOneWayOppositeDirection(tags));                                                
      }
      /* 2b) mode inclusions for ONE WAY MAIN DIRECTION if explored*/
      else if(OsmTagUtils.matchesAnyValueTag(oneWayValueTag, OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION, OsmOneWayTags.YES)) {        
        includedModes.addAll(getExplicitlyIncludedModesOneWayMainDirection(tags));                                              
      }            
    }else {
      /* 2c) mode inclusions for BIDIRECTIONAL WAY in explored direction: RIGHT and LEFT now DO IMPLY DIRECTION, unless indicated otherwise, see https://wiki.openstreetmap.org/wiki/Bicycle */
      
      /* country settings matter : left hand drive -> left = forward direction, right hand drive -> left is opposite direction */
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(settings.getCountryName());
      boolean isDrivingDirectionLocationLeft = (isForwardDirection && isLeftHandDrive) ? true : false; 
      
      /* location means left or right hand side of the road specific tags pertaining to mode inclusions */
      includedModes.addAll(getExplicitlyIncludedModesTwoWayForLocation(tags, isDrivingDirectionLocationLeft));            
    }
    
    /* 3) mode inclusions for explored direction that is NOT ONE WAY OPPOSITE DIRECTION */
    if(!exploreOneWayOppositeDirection) {      
      /* ...all modes --> general inclusions in main or both directions <mode>= */
      includedModes.addAll(getActivatedPlanitModes(OsmModeUtils.getOsmRoadModesWithValueTag(tags, OsmAccessTags.getPositiveAccessValueTags())));
      /* ...all modes --> general inclusions in main or both directions access:<mode>= */
      includedModes.addAll(getActivatedPlanitModes(OsmModeUtils.getPrefixedOsmRoadModesWithValueTag(OsmAccessTags.ACCESS, tags, OsmAccessTags.getPositiveAccessValueTags())));
    }
           
    return includedModes;                  
  }

  /** Collect explicitly excluded modes from the passed in tags. Given the many ways this can be tagged we distinguish between:
   * 
   * <ul>
   * <li>mode exclusions agnostic to the way being tagged as oneway or not {@link #getExplicitlyExcludedModesOneWayAgnostic(Map, boolean, OsmNetworkReaderSettings)}</li>
   * <li>mode exclusions when way is tagged as oneway and exploring the one way direction {@link #getExplicitlyExcludedModesOneWayMainDirection(Map)}</li>
   * <li>mode exclusions when way is tagged as oneway and exploring the opposite direction of oneway direction {@link #getExplicitlyExcludedModesOneWayOppositeDirection()}</li>
   * </ul>
   * 
   * 
   * @param tags to find explicitly included (planit) modes from
   * @param isForwardDirection  flag indicating if we are conducting this method in forward direction or not (forward being in the direction of how the geometry is provided)
   * @param settings required for access to mode mapping between osm modes and PLANit modes
   * @return the included planit modes supported by the parser in the designated direction
   */
  public Set<Mode> getExplicitlyExcludedModes(final Map<String, String> tags, final boolean isForwardDirection, final OsmNetworkReaderSettings settings) {    
    Set<Mode> excludedModes = new HashSet<>();
    
    /* 1) generic mode exclusions INDEPENDENT of ONEWAY tags being present or not*/
    excludedModes.addAll(getExplicitlyExcludedModesOneWayAgnostic(tags, isForwardDirection, settings));               
    
    if(tags.containsKey(OsmOneWayTags.ONEWAY)){
      /* mode exclusions for ONE WAY tagged way (RIGHT and LEFT on a oneway street DO NOT imply direction)*/     
      
      final String oneWayValueTag = tags.get(OsmOneWayTags.ONEWAY);
      String osmDirectionValue = isForwardDirection ? OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION : OsmOneWayTags.YES;
  
      /* 2a) mode exclusions for ONE WAY OPPOSITE DIRECTION if explored */
      if(oneWayValueTag.equals(osmDirectionValue)) {        
        excludedModes.addAll(getExplicitlyExcludedModesOneWayOppositeDirection());                       
      }    
      /* 2b) mode inclusions for ONE WAY MAIN DIRECTION if explored*/
      else if(OsmTagUtils.matchesAnyValueTag(oneWayValueTag, OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION, OsmOneWayTags.YES)) {
        excludedModes.addAll(getExplicitlyExcludedModesOneWayMainDirection(tags));                                             
      }
    }else {
      /* 2c) mode inclusions for BIDIRECTIONAL WAY in explored direction: RIGHT and LEFT now DO IMPLY DIRECTION, unless indicated otherwise, see https://wiki.openstreetmap.org/wiki/Bicycle */
      
      /* country settings matter : left hand drive -> left = forward direction, right hand drive -> left is opposite direction */
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(settings.getCountryName());
      boolean isDrivingDirectionLocationLeft = (isForwardDirection && isLeftHandDrive) ? true : false;
      
      /* location means left or right hand side of the road specific tags pertaining to mode inclusions */
      excludedModes.addAll(getExplicitlyExcludedModesTwoWayForLocation(tags, isDrivingDirectionLocationLeft));                      
    }      
    
    return excludedModes;
  }
  
  /** Update the included and excluded mode sets passed in based on the key/value information available in the access=? tag.
   * 
   * @param tags where we extract the access information from
   * @param includedModesToUpdate the set to supplement with found access information of allowed modes, e.g. access=yes, access=bus, etc.
   * @param excludedModesToUpdate the set to supplement with found access information of disallowed modes, e.g. access=no, etc.
   */
  public void updateAccessKeyBasedModeRestrictions(final Map<String, String> tags, final Set<Mode> includedModesToUpdate, final Set<Mode> excludedModesToUpdate) {
    
    String accessValue = tags.get(OsmAccessTags.ACCESS).replaceAll(OsmTagUtils.VALUETAG_SPECIALCHAR_STRIP_REGEX, "");
    
    /* access=<positive>*/
    if(OsmTagUtils.matchesAnyValueTag(accessValue, OsmAccessTags.getPositiveAccessValueTags())) {
      
      /* collect all modes the type of road supports and are allowed upon...*/
      Collection<String> osmAllowedModesForWayType = null;
      if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
        osmAllowedModesForWayType = getSettings().getHighwaySettings().collectAllowedOsmHighwayModes(tags.get(OsmHighwayTags.HIGHWAY)); 
      }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
        osmAllowedModesForWayType = getSettings().getRailwaySettings().collectAllowedOsmRailwayModes(tags.get(OsmRailwayTags.RAILWAY));
      }else {
        /* no other major types yet supported */
      }
      
      /*... retain all that are supported by the layer */
      if(osmAllowedModesForWayType!= null) {
        Set<Mode> allowedModes =getActivatedPlanitModes(osmAllowedModesForWayType);
        allowedModes.retainAll(networkLayer.getSupportedModes());
        includedModesToUpdate.addAll(allowedModes);
      }
      includedModesToUpdate.removeAll(excludedModesToUpdate);       
    }
    /* access=<mode>*/
    else if(OsmTagUtils.matchesAnyValueTag(accessValue, OsmRoadModeTags.getSupportedRoadModeTagsAsArray()) || OsmTagUtils.matchesAnyValueTag(accessValue, OsmRailModeTags.getSupportedRailModeTagsAsArray())){
      /* access limited to a particular road/rail mode */
      includedModesToUpdate.add(getActivatedPlanitMode(accessValue));
      excludedModesToUpdate.addAll(networkLayer.getSupportedModes());
      excludedModesToUpdate.removeAll(includedModesToUpdate);
    }
    /* access=<negative>*/
    else if(OsmTagUtils.matchesAnyValueTag(accessValue, OsmAccessTags.getNegativeAccessValueTags())){
      excludedModesToUpdate.addAll(networkLayer.getSupportedModes());
      excludedModesToUpdate.removeAll(includedModesToUpdate);
    }
    
  }      
  
}

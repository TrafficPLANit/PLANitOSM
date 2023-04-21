package org.goplanit.osm.converter.network;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.graph.modifier.event.handler.SyncXmlIdToIdBreakEdgeHandler;
import org.goplanit.graph.directed.modifier.event.handler.SyncXmlIdToIdBreakEdgeSegmentHandler;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.osm.physical.network.macroscopic.ModifiedLinkSegmentTypes;
import org.goplanit.osm.tags.OsmAccessTags;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmLaneTags;
import org.goplanit.osm.tags.OsmOneWayTags;
import org.goplanit.osm.tags.OsmRailFeatureTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmSpeedTags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.util.OsmWayUtils;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.arrays.ArrayUtils;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.Edge;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Takes care of populating a PLANit layer based on the OSM way information that has been identified
 * as relevant to this layer by the {@link OsmNetworkMainProcessingHandler}
 * 
 * @author markr
 *
 */
public class OsmNetworkLayerParser {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkLayerParser.class.getCanonicalName());
  
  // local members only
         
  /** track all data that maps OSM entities to PLANit entities here */
  private final OsmNetworkReaderLayerData layerData; 
    
  /** track all modified link segment types compared to the original defaults used in OSM, for efficient updates of the PLANit link segment types while parsing */
  private final ModifiedLinkSegmentTypes modifiedLinkSegmentTypes = new ModifiedLinkSegmentTypes();  
  
  // references
  
  /** reference to network wide tracked network reader data */
  private final OsmNetworkReaderData networkData;    
    
  /** settings relevant to this parser */
  private final OsmNetworkReaderSettings settings;
    
  /** the network layer to use */
  private final MacroscopicNetworkLayer networkLayer;
  
  /** dedicated functionality to parse supported OSM modes */
  private final OsmNetworkLayerModeConversion modeParser;
  
  /** geo utility instance based on network wide crs this layer is part of */
  private final PlanitJtsCrsUtils geoUtils;
  
  /** listener with functionality to sync XML ids to unique internal id upon breaking a link, ensures that when persisting
   * OSM network by XML id,  we do not have duplicate ids */
  private final SyncXmlIdToIdBreakEdgeHandler syncXmlIdToIdOnBreakLink = new SyncXmlIdToIdBreakEdgeHandler();
  
  /** listener with functionality to sync XML ids to unique internal id upon breaking a link segment, ensures that when persisting
   * OSM network by XML id,  we do not have duplicate ids */
  private final SyncXmlIdToIdBreakEdgeSegmentHandler syncXmlIdToIdOnBreakLinkSegment = new SyncXmlIdToIdBreakEdgeSegmentHandler();  
  
  /**
   * Initialise the layer specific event listeners, for example when modifications are made to the underlying network and based on user configuration
   * additional action by this parser is required to maintain a consistent network layer result during parsing or after
   */
  private void initialiseEventListeners() {
    networkLayer.getLayerModifier().removeAllListeners();
    /* whenever a link(segment) is broken we ensure that its XML id is synced with the internal id to ensure it remains unique */
    networkLayer.getLayerModifier().addListener(syncXmlIdToIdOnBreakLink);
    networkLayer.getLayerModifier().addListener(syncXmlIdToIdOnBreakLinkSegment);
  }

  /** Check if geometry is near network bounding box
   * 
   * @param geometry to check
   * @param geoUtils to use
   * @return true when near, false otherwise
   */
  private boolean isNearNetworkBoundingBox(Geometry geometry, PlanitJtsCrsUtils geoUtils){
    return geoUtils.isGeometryNearBoundingBox(geometry, networkData.getBoundingBox(), OsmNetworkReaderData.BOUNDINGBOX_NEARNESS_DISTANCE_METERS);
  }

  /** update the passed in existing link segment type based on proposed changes in added and/or removed modes (if any) and possible changes to the default speeds based on
   * the available tags. The updated link segment type is returned, which in turn is registered properly on the network if it is indeed changed from the passed in existing one
   * 
   * @param toBeAddedModes modes to add
   * @param toBeRemovedModes modes to remove
   * @param tags to extract speed limit information from
   * @param linkSegmentType existing link segment type deemed appropriate
   * @return updated link segment type, which if different is not a modification of the existing one but a unique copy with the required changes that is considered a modification of the original, 
   * yet its own unique new type
   */
  private MacroscopicLinkSegmentType updateExistingLinkSegmentType(
      final Set<Mode> toBeAddedModes, final Set<Mode> toBeRemovedModes, Map<String, String> tags, MacroscopicLinkSegmentType linkSegmentType){
    
    if(toBeAddedModes.isEmpty() && toBeRemovedModes.isEmpty()) {
      return linkSegmentType;
    }

    if(linkSegmentType.getAllowedModes().size() + toBeAddedModes.size() - toBeRemovedModes.size() <= 0){
      return linkSegmentType;
    }

    MacroscopicLinkSegmentType finalLinkSegmentType = modifiedLinkSegmentTypes.getModifiedLinkSegmentType(linkSegmentType, toBeAddedModes, toBeRemovedModes);
    if(finalLinkSegmentType==null) {

      /* even though the segment type is modified, the modified version does not yet exist on the PLANit network, so create it */
      finalLinkSegmentType = networkLayer.getLinkSegmentTypes().getFactory().createUniqueDeepCopyOf(linkSegmentType);
      networkLayer.getLinkSegmentTypes().register(finalLinkSegmentType);
      /* XML id */
      finalLinkSegmentType.setXmlId(Long.toString(finalLinkSegmentType.getId()));

      final String MODIFIED = "_modified";
      /* External id */
      if(finalLinkSegmentType.hasExternalId()) {
        finalLinkSegmentType.setExternalId(finalLinkSegmentType.getExternalId() + MODIFIED);
      }

      /* name */
      if(finalLinkSegmentType.hasName()) {
        finalLinkSegmentType.setExternalId(finalLinkSegmentType.getName() + MODIFIED);
      }

      /* update mode properties */
      if(!toBeAddedModes.isEmpty()) {
        double osmWayTypeMaxSpeed = settings.getDefaultSpeedLimitByOsmWayType(tags);
        for(var newMode: toBeAddedModes) {
          double modeMaxSpeedOnLinkType = Math.min(newMode.getMaximumSpeedKmH(),osmWayTypeMaxSpeed);
          var accessGroup = AccessGroupPropertiesFactory.create(modeMaxSpeedOnLinkType, newMode);
          var matchedGroup = linkSegmentType.findEqualAccessPropertiesForAnyMode(accessGroup);
          if(matchedGroup != null){
            finalLinkSegmentType.registerModeOnAccessGroup(newMode, accessGroup);
          }else {
            AccessGroupPropertiesFactory.createOnLinkSegmentType(finalLinkSegmentType, newMode, modeMaxSpeedOnLinkType);
          }
        }

      }
      if(!toBeRemovedModes.isEmpty()) {
        finalLinkSegmentType.removeModeAccess(toBeRemovedModes);
      }

      /* register modification */
      modifiedLinkSegmentTypes.addModifiedLinkSegmentType(linkSegmentType, finalLinkSegmentType, toBeAddedModes, toBeRemovedModes);
    }
    
    return finalLinkSegmentType;
  }  
  
  /** register all nodes within the provided (inclusive) range as link internal nodes for the passed in link
   * 
   * @param link to register link internal nodes for
   * @param startIndex the start index
   * @param endIndex the end index
   * @param osmWay the link corresponds to
   */
  private void registerLinkInternalOsmNodes(MacroscopicLink link, int startIndex, int endIndex, OsmWay osmWay){
    /* lay index on internal nodes of link to allow for splitting the link if needed due to intersecting internally with other links */
    for(int internalLocationIndex = startIndex; internalLocationIndex <= endIndex;++internalLocationIndex) {
      OsmNode osmnode = networkData.getOsmNodeData().getRegisteredOsmNode(osmWay.getNodeId(internalLocationIndex));
      if(osmnode != null) {
        layerData.registerOsmNodeAsInternalToPlanitLink(osmnode,link);
      }else {
        LOGGER.fine(String.format("OSM node %d not available although internal to parseable osm way %d, possibly outside bounding box",osmWay.getNodeId(internalLocationIndex), osmWay.getId()));
      }
    }   
  }   
  
  /** create and populate link if it does not already exists for the given two PLANit nodes based on the passed in osmWay information. 
   * In case a new link is to be created but internal nodes of the geometry are missing due to the meandering road falling outside the boundaing box that is being parsed, null is returned 
   * and the link is not created
   * 
   * @param osmWay to populate link data with
   * @param tags to populate link data with
   * @param startNodeIndex of the OSM way that will represent start node of this link
   * @param endNodeIndex of the OSM way that will represent end node of this link
   * @param allowTruncationIfGeometryIncomplete when true we try to create the link with the part of the geometry that is available, when false, we discard it if not complete 
   * @return created or fetched link
   */
  private MacroscopicLink createAndPopulateLink(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex, boolean allowTruncationIfGeometryIncomplete){
    
    PlanItRunTimeException.throwIf(startNodeIndex < 0 || startNodeIndex >= osmWay.getNumberOfNodes(), String.format("invalid start node index %d when extracting link from Osm way %s",startNodeIndex, osmWay.getId()));
    PlanItRunTimeException.throwIf(endNodeIndex < 0 || endNodeIndex >= osmWay.getNumberOfNodes(), String.format("invalid end node index %d when extracting link from Osm way %s",startNodeIndex, osmWay.getId()));   

    /* collect memory model nodes */
    Pair<Node,Integer> nodeFirstResult = extractFirstNode(osmWay, startNodeIndex, allowTruncationIfGeometryIncomplete);

    Pair<Node,Integer> nodeLastResult = null;
    int foundStartNodeIndex = startNodeIndex;
    if(nodeFirstResult!= null && nodeFirstResult.first() != null) {
      foundStartNodeIndex = nodeFirstResult.second();
      nodeLastResult = extractLastNode(osmWay, foundStartNodeIndex, endNodeIndex, allowTruncationIfGeometryIncomplete);
    }

    /* entirely unavailable, ignore */
    if(nodeLastResult == null && nodeFirstResult == null) {
      networkData.registerProcessedOsmWayAsUnavailable(osmWay.getId());
      return null;
    }
    /* If truncated to a single node or not available (because fully/partially outside bounding box), it is not valid and mark as such */
    if(nodeLastResult == null || nodeFirstResult == null || nodeLastResult.first().idEquals(nodeFirstResult.first())) {
      LOGGER.fine(String.format("DISCARD: OSM way %d truncated to single node, unable to create PLANit link for it", osmWay.getId()));
      networkData.registerProcessedOsmWayAsUnavailable(osmWay.getId());
      return null;
    }

    Node nodeFirst = nodeFirstResult.first();
    Node nodeLast = nodeLastResult.first();

    /* parse geometry */
    LineString lineString = null;          
    try {
      lineString = extractPartialLinkGeometry(osmWay, nodeFirstResult.second(), nodeLastResult.second());
    }catch (PlanItException e) {
      LOGGER.fine(String.format("OSM way %s internal geometry incomplete, one or more internal nodes could not be created, likely outside bounding box",osmWay.getId()));
      return null;
    }

    MacroscopicLink link = null;
    /* osm way can be direction directional, PLANit link is never, check existence */
    if(nodeFirst != null) {
      var potentialEdges = nodeFirst.getEdges(nodeLast);
      for(Edge potentialEdge : potentialEdges) {
        MacroscopicLink potentialLink = ((MacroscopicLink)potentialEdge);
        if(link != null && potentialLink.getGeometry().equals(lineString)) {
          /* matching geometry, so they are in indeed the same link*/
          link = potentialLink;
          break;
        }        
      }
    }      
               
    /* when not present and valid geometry, create new link */
    if(link == null) {

      /* length and geometry */
      double linkLength = 0;      
      /* update the length based on the geometry */
      linkLength = geoUtils.getDistanceInKilometres(lineString);
      
      /* create link */
      link = networkLayer.getLinks().getFactory().registerNew(nodeFirst, nodeLast, linkLength, true);
      /* geometry */
      link.setGeometry(lineString);      
      /* XML id */
      link.setXmlId(Long.toString(link.getId()));
      /* external id */
      link.setExternalId(String.valueOf(osmWay.getId()));
      
      if(tags.containsKey(OsmTags.NAME)) {
        link.setName(tags.get(OsmTags.NAME));
      }
      
      /* store osm way type for future reference (used in zoning reader for example) */
      if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
        OsmNetworkHandlerHelper.setLinkOsmWayType(link, tags.get(OsmHighwayTags.HIGHWAY));
      }else if(OsmRailwayTags.hasRailwayKeyTag(tags)){
        OsmNetworkHandlerHelper.setLinkOsmWayType(link, tags.get(OsmRailwayTags.RAILWAY));
      }
    }
    return link;      
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
   * @param linkSegmentType use thus far for this way
   * @return the link segment types for the main direction and contra-flow direction
   */  
  private MacroscopicLinkSegmentType extractDirectionalLinkSegmentTypeByOsmWay(OsmWay osmWay, Map<String, String> tags, MacroscopicLinkSegmentType linkSegmentType, boolean forwardDirection){  

    Set<Mode> toBeAddedModes = null;
    Set<Mode> toBeRemovedModes = null;
    
    /* check if modes are overwritten by user settings directly */
    if(settings.isModeAccessOverwrittenByOsmWayId(osmWay.getId())) {
      
      /* use OSM modes given to identify to be added and removed modes */
      var allowedPlanitModes = modeParser.getActivatedPlanitModes(settings.getModeAccessOverwrittenByOsmWayId(osmWay.getId()));
      /* reduce included modes to only the predefined modes supported by the layer the link segment type resides on, expensive, but overwrites are rare so ok */
      if(!allowedPlanitModes.isEmpty()) {
        allowedPlanitModes.retainAll(networkLayer.getSupportedModes().stream().filter(m -> m.isPredefinedModeType()).collect(Collectors.toList()));
      }

      toBeAddedModes = linkSegmentType.getDisallowedModesFrom(allowedPlanitModes);
      toBeRemovedModes = linkSegmentType.getAllowedModesNotIn(allowedPlanitModes);
      
    }else {
      /*regular approach based on available tags */
      
      /* identify explicitly excluded and included modes with anything related to mode and direction specific key tags <?:>mode<:?>=<?> */
      Set<Mode> excludedModes = modeParser.getExplicitlyExcludedModes(tags, forwardDirection, settings);
      Set<Mode> includedModes = modeParser.getExplicitlyIncludedModes(tags, forwardDirection, settings);
          
      
      /* global access is defined for both ways, or explored direction coincides with the main direction of the one way */
      boolean isOneWay = OsmOneWayTags.isOneWay(tags);
      /*                                              two way || oneway->forward    || oneway->backward and reversed oneway */  
      boolean accessTagAppliesToExploredDirection =  !isOneWay || (forwardDirection || OsmOneWayTags.isReversedOneWay(tags));
      
      /* access=<?> related mode access */
      if(accessTagAppliesToExploredDirection && tags.containsKey(OsmAccessTags.ACCESS)) {
        
        modeParser.updateAccessKeyBasedModeRestrictions(tags, includedModes, excludedModes);
      }
      
      /* reduce included modes to only the modes supported by the layer the link segment type resides on*/
      if(!includedModes.isEmpty()) {
        includedModes.retainAll(networkLayer.getSupportedModes());
      }
      
      /* identify differences with default link segment type in terms of mode access */
      toBeAddedModes = linkSegmentType.getDisallowedModesFrom(includedModes);
      toBeRemovedModes = linkSegmentType.getAllowedModesFrom(excludedModes);        
    }

    /* use the identified changes to the modes to update the link segment type (and register it if needed) */
    MacroscopicLinkSegmentType finalLinkSegmentType = updateExistingLinkSegmentType(toBeAddedModes, toBeRemovedModes, tags, linkSegmentType);
    return finalLinkSegmentType;
  }  
      
  /** extract geometry from the OSM way based on the start and end node index, only the portion of geometry in between the two indices will be collected.
   * Note that it is possible to have a smaller end node index than start node index in which case, the geometry is constructed such that it overflows from the
   * end and starting back at the beginning.
   *  
   * @param osmWay to extract geometry from
   * @param startNodeIndex of geometry
   * @param endNodeIndex of the geometry
   * @return (partial) geometry
   * @throws PlanItException throw if error
   */
  private LineString extractPartialLinkGeometry(OsmWay osmWay, int startNodeIndex, int endNodeIndex) throws PlanItException {
    LineString lineString = OsmWayUtils.extractLineStringNoThrow(osmWay, startNodeIndex, endNodeIndex, networkData.getOsmNodeData().getRegisteredOsmNodes());
    lineString = PlanitJtsUtils.createCopyWithoutAdjacentDuplicateCoordinates(lineString);
    
    return lineString;
  }
    
  /** Determine the speed limits in the forward and backward direction (if any).
   * 
   * @param link pertaining to the osmway tags
   * @param tags assumed to contain speed limit information
   * @return speed limtis in forward and backward direction
   */
  private Pair<Double,Double> extractDirectionalSpeedLimits(Link link, Map<String, String> tags){
    Double speedLimitForwardKmh = null;
    Double speedLimitBackwardKmh = null;
    Double nonDirectionalSpeedLimitKmh = null;
    boolean useNonDirectionalDefault = false;
    
    try {
      /* (lane specific) backward or forward speed limits */
      if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)|| tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
        /* check for backward speed limit */
        if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD)) {
          speedLimitBackwardKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_BACKWARD));        
        }
        /* check for backward speed limit per lane */
        if(tags.containsKey(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)) {
          double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_BACKWARD_LANES)); 
          speedLimitBackwardKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);           
        }
      }
      if( tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD) || tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)){
        /* check for forward speed limit */
        if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD)) {
          speedLimitForwardKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD));  
        }
        /* check for forward speed limit per lane */
        if(tags.containsKey(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)) {
          double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_FORWARD_LANES)); 
          speedLimitForwardKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);   
        }
      }
      
      /* if any of the two are not yet found, find general speed limit information not tied to direction */
      if(speedLimitBackwardKmh==null || speedLimitForwardKmh==null) {
        if(tags.containsKey(OsmSpeedTags.MAX_SPEED)) {
          /* regular speed limit for all available directions and across all modes */
          nonDirectionalSpeedLimitKmh = PlanitOsmUtils.parseMaxSpeedValueKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED));
        }else if(tags.containsKey(OsmSpeedTags.MAX_SPEED_LANES)) {
          /* check for lane specific speed limit */
          double[] maxSpeedLimitLanes = PlanitOsmUtils.parseMaxSpeedValueLanesKmPerHour(tags.get(OsmSpeedTags.MAX_SPEED_LANES));
          /* Note: PLANit does not support lane specific speeds at the moment, maximum speed across lanes is selected */      
          nonDirectionalSpeedLimitKmh = ArrayUtils.getMaximum(maxSpeedLimitLanes);
        }else { 
          /* no speed limit information, revert to defaults */
          useNonDirectionalDefault = true;
        }        
      }
    }catch(PlanItException e) {
      LOGGER.warning(e.getMessage());
      // something went wrong revert to defaults
      LOGGER.info(String.format("Reverting to default speed limit for OSM way (id:%s)",link.getExternalId()));
      useNonDirectionalDefault = true;
    }
    
    if(useNonDirectionalDefault) {
      nonDirectionalSpeedLimitKmh = settings.getDefaultSpeedLimitByOsmWayType(tags);
      layerData.getProfiler().incrementMissingSpeedLimitCounter();
    }
    
    if(nonDirectionalSpeedLimitKmh!=null) {
      speedLimitForwardKmh = (speedLimitForwardKmh==null) ? nonDirectionalSpeedLimitKmh : speedLimitForwardKmh;
      speedLimitBackwardKmh = (speedLimitBackwardKmh==null) ? nonDirectionalSpeedLimitKmh : speedLimitBackwardKmh;
    }else if(speedLimitForwardKmh==null && speedLimitBackwardKmh==null) {
      throw new PlanItRunTimeException(String.format("no default speed limit available for OSM way %s",link.getExternalId()));
    }    
                    
    /* mode specific speed limits*/
    //TODO
    
    return Pair.of(speedLimitForwardKmh,speedLimitBackwardKmh);
  }  
  
  /**
   * parse the number of lanes on the link in forward and backward direction (if explicitly set), when not available defaults are used
   * 
   * @param link for which lanes are specified (and its link segments)
   * @param tags containing lane information
   * @param linkSegmentTypes identified in forward and backward direction based on tags, useful to assign a minimum number of lanes in case no explicit lanes could be found by type is present 
   * @throws PlanItException 
   */  
  private Pair<Integer, Integer> extractDirectionalLanes(Link link, Map<String, String> tags, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes ) {
    Integer totalLanes = null;
    Integer lanesForward = null;
    Integer lanesBackward = null;    

    String osmWayKey = null;
    try {
      /* collect total and direction specific road based lane information */
      if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
        osmWayKey = OsmHighwayTags.HIGHWAY;
        
        if(tags.containsKey(OsmLaneTags.LANES)) {
          totalLanes = Integer.parseInt(tags.get(OsmLaneTags.LANES));
        }    
        if(tags.containsKey(OsmLaneTags.LANES_FORWARD)) {
          lanesForward = Integer.parseInt(tags.get(OsmLaneTags.LANES_FORWARD));
        }
        if(tags.containsKey(OsmLaneTags.LANES_BACKWARD)) {
          lanesBackward = Integer.parseInt(tags.get(OsmLaneTags.LANES_BACKWARD));
        }
           
        /* one way exceptions or implicit directional lanes */
        if(totalLanes!=null && (lanesForward==null || lanesBackward==null)) {
          if(OsmOneWayTags.isOneWay(tags)) {
            boolean isReversedOneWay = OsmOneWayTags.isReversedOneWay(tags);
            if(isReversedOneWay && lanesBackward==null) {
              lanesBackward = totalLanes;
            }else if(!isReversedOneWay && lanesForward==null) {
              lanesForward = totalLanes;          
            }else if( (lanesForward==null && lanesBackward==null) && totalLanes%2==0) {
              /* two directions, with equal number of lanes does not require directional tags, simply split in two */
              lanesBackward = totalLanes/2;
              lanesForward = lanesBackward;
            }
          }
        }
              
      /* convert number of tracks to lanes */
      }else if(tags.containsKey(OsmRailwayTags.RAILWAY)) {
        osmWayKey = OsmRailwayTags.RAILWAY;
        if(tags.containsKey(OsmRailFeatureTags.TRACKS)) {
          /* assumption is that same rail is used in both directions */
          lanesForward = Integer.parseInt(tags.get(OsmRailFeatureTags.TRACKS));
          lanesBackward = lanesForward;
        }       
      }
    }catch(Exception e) {
      LOGGER.warning(String.format("Something went wrong when parsing number of lanes for OSM way (id:%s), possible tagging error, reverting to default bi-direactional configuration",link.getExternalId()));
      lanesForward = null;
      lanesBackward = null;
    }
    
    /* we assume that only when both are not set something went wrong, otherwise it is assumed it is a one-way link and it is properly configured */
    if(lanesForward==null && lanesBackward==null) {
      lanesForward = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      lanesBackward = lanesForward;
      layerData.getProfiler().incrementMissingLaneCounter();
    }
    
    /* when no lanes are allocated for vehicle modes, but direction has activated modes (for example due to presence of opposite lanes, or active modes -> assign 1 lane */
    boolean missingLaneInformation = false;
    if(lanesForward == null && linkSegmentTypes.first()!=null) {
      lanesForward = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      missingLaneInformation = true;
    }
    if(lanesBackward == null && linkSegmentTypes.second()!=null) {
      lanesBackward = settings.getDefaultDirectionalLanesByWayType(osmWayKey, tags.get(osmWayKey));
      missingLaneInformation = true;
    }
    if(missingLaneInformation) {
      layerData.getProfiler().incrementMissingLaneCounter();
    }
    
    return Pair.of(lanesForward, lanesBackward);
        
  }
  
  /** Extract a link segment from the way corresponding to the link and the indicated direction
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param linkSegmentType the link segment type corresponding to this way
   * @param directionAb the direction to create the segment for  
   * @return created link segment, or null if already exists
   */  
  private MacroscopicLinkSegment extractMacroscopicLinkSegment(OsmWay osmWay, Map<String, String> tags, MacroscopicLink link, MacroscopicLinkSegmentType linkSegmentType, boolean directionAb){
    MacroscopicLinkSegment linkSegment = (MacroscopicLinkSegment) link.getEdgeSegment(directionAb);
    if(linkSegment == null) {
      linkSegment = networkLayer.getLinkSegments().getFactory().registerNew(link, directionAb, true /*register on nodes and link*/);
      /* Xml id */
      linkSegment.setXmlId(Long.toString(linkSegment.getId()));
      /* external id, identical to link since OSM has no directional ids */
      linkSegment.setExternalId(link.getExternalId());
    }else{
      LOGGER.warning(String.format(
          "Already exists link segment (id:%d) between OSM nodes (%s, %s) of OSM way (%d), ignored entity",linkSegment.getId(), link.getVertexA().getExternalId(), link.getVertexB().getExternalId(), osmWay.getId()));
    }
    
    /* link segment type */
    linkSegment.setLinkSegmentType(linkSegmentType);
        
    layerData.getProfiler().logLinkSegmentStatus(networkLayer.getNumberOfLinkSegments());      
    return linkSegment;
  }  
  
  /** Extract one or two link segments from the way corresponding to the link
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param linkSegmentTypes the link segment types for the forward and backward direction of this way  
   * @return created link segment, or null if already exists
   */
  private void extractMacroscopicLinkSegments(OsmWay osmWay, Map<String, String> tags, MacroscopicLink link, Pair<MacroscopicLinkSegmentType,MacroscopicLinkSegmentType> linkSegmentTypes){
                
    /* match A->B of PLANit link to geometric forward/backward direction of OSM paradigm */
    boolean directionAbIsForward = link.isGeometryInAbDirection() ? true : false;
    if(!directionAbIsForward) {
      LOGGER.warning("directionAB is not forward in geometry SHOULD NOT HAPPEN!");
    }
    
    /* speed limits in forward and backward direction based on tags and defaults if missing*/
    Pair<Double,Double> speedLimits = extractDirectionalSpeedLimits(link, tags);
    /* lanes in forward and backward direction based on tags and defaults if missing */
    Pair<Integer,Integer> lanes = extractDirectionalLanes(link, tags, linkSegmentTypes);
    
    /* create link segment A->B when eligible */
    MacroscopicLinkSegmentType linkSegmentTypeAb = directionAbIsForward ? linkSegmentTypes.first() : linkSegmentTypes.second();
    if(linkSegmentTypeAb!=null) {
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentTypeAb, true /* A->B */);
      Double speedLimit = directionAbIsForward ? speedLimits.first() : speedLimits.second();
      link.getLinkSegmentAb().setPhysicalSpeedLimitKmH(speedLimit);
      link.getLinkSegmentAb().setNumberOfLanes(directionAbIsForward ? lanes.first() : lanes.second());
    }
    /* create link segment B->A when eligible */
    MacroscopicLinkSegmentType linkSegmentTypeBa = directionAbIsForward ? linkSegmentTypes.second() : linkSegmentTypes.first();
    if(linkSegmentTypeBa!=null) {
      extractMacroscopicLinkSegment(osmWay, tags, link, linkSegmentTypeBa, false /* B->A */);
      link.getLinkSegmentBa().setPhysicalSpeedLimitKmH(directionAbIsForward ? speedLimits.second() : speedLimits.first());
      link.getLinkSegmentBa().setNumberOfLanes(directionAbIsForward ? lanes.second() : lanes.first());
    }                 
    
  }   
 
  /** Extract the first available node from the osm way based on the provided start node index
   * 
   * @param osmWay to use
   * @param startNodeIndex to use
   * @param changeStartNodeIndexIfNotPresent when true it replaces the startNodeIndex to first available node index that we can find if startNodeIndex is not available, if false not
   * @return extracted node (first node to use for OSM way), and start nod index used. Null if not even a first node could be extracted, this only happens when no references osm node is available
   * likely due to user specifying an internal bounding box outside of which this osm way resides
   */
  private Pair<Node,Integer> extractFirstNode(OsmWay osmWay, Integer startNodeIndex, boolean changeStartNodeIndexIfNotPresent){
    Node nodeFirst = extractNode(osmWay.getNodeId(startNodeIndex));
    if(nodeFirst==null && changeStartNodeIndexIfNotPresent) {
      startNodeIndex = OsmWayUtils.findFirstAvailableOsmNodeIndexAfter(startNodeIndex, osmWay, networkData.getOsmNodeData().getRegisteredOsmNodes());
      if(startNodeIndex!=null) {
        nodeFirst = extractNode(osmWay.getNodeId(startNodeIndex));
        if(nodeFirst!= null && !isNearNetworkBoundingBox(nodeFirst.getPosition(), geoUtils)) {       
          /* quite far from bounding box, so log for user verification to be sure */
          LOGGER.warning(String.format("SALVAGED: OSM way %s geometry incomplete, likely cut-off by network bounding box, truncated at OSM node %s",osmWay.getId(), nodeFirst.getExternalId()));
        }
      }else {
        /* ignore, osm way likely completely outside user specified bounding box within input  and therefore this is most likely intended behaviour */
        return null;
      }
    }
    return Pair.of(nodeFirst,startNodeIndex);
  }

  /** Extract the last node of the osm way based on the provided end node index
   * 
   * @param osmWay to parse
   * @param startNodeIndex used for the preceding node, if not relevant just provide 0 
   * @param endNodeIndex to use for last node
   * @param changeEndNodeIndexIfNotPresent when true it replaces the endNodeIndex to first available node index that we can find if endNodeIndex is not available, if false not
   * @return extracted node and end node index used. Null if no node could be extracted, this only happens when no references osm node is available
   * likely due to user specifying an internal bounding box outside of which this osm way resides
   */
  private  Pair<Node,Integer> extractLastNode(OsmWay osmWay, final Integer startNodeIndex, Integer endNodeIndex, boolean changeEndNodeIndexIfNotPresent){    
    Node nodeLast = extractNode(osmWay.getNodeId(endNodeIndex));        
    if(nodeLast==null && changeEndNodeIndexIfNotPresent) {
      endNodeIndex = OsmWayUtils.findLastAvailableOsmNodeIndexAfter(startNodeIndex, osmWay, networkData.getOsmNodeData().getRegisteredOsmNodes());
      if(endNodeIndex != null) {
        nodeLast = extractNode(osmWay.getNodeId(endNodeIndex));
        if(nodeLast!= null && !isNearNetworkBoundingBox(nodeLast.getPosition(), geoUtils)) {
          //TODO: check across all available node locations if it is near bounding box, because likely this is just a long road/rail with few nodes and we're checking the "far" node only now on distance to bbox */
          LOGGER.fine(String.format("OSM way %s not fully available, likely due to network bounding box, please verify, truncated at osm node %s",osmWay.getId(), nodeLast.getExternalId()));
        }
      }else {
        /* ignore, osm way likely completely outside user specified bounding box within input */
        return null;
      }
    }
    return Pair.of(nodeLast,endNodeIndex);
  }

  /**
   * Extract a PLANit node from the osmNode information if available
   * 
   * @param osmNodeId to convert
   * @return created or retrieved node, null if not able to create node
   */
  private Node extractNode(final long osmNodeId){    
    
    /* osm node */
    OsmNode osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(osmNodeId);
    if(osmNode == null) {
      return null;
    }
    
    /* planit node */
    Node node = this.layerData.getPlanitNodeByOsmNode(osmNode);
    if(node == null) {      
      /* create and register */
      node = OsmNetworkHandlerHelper.createPopulateAndRegisterNode(osmNode, networkLayer, layerData);
    }
    
    return node;
  }   
  
  
  /** extract a link from the way
   * @param osmWay the way to process
   * @param tags tags that belong to the way
   * @param endNodeIndex for this link compared to the full OSM way
   * @param startNodeIndex for this link compared to the full OSM way
   * @param allowTruncationIfGeometryIncomplete when true we try to create the link with the part of the geometry that is available, when false, we discard it if not complete 
   * @return the link corresponding to this way
   */
  private MacroscopicLink extractLink(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex, boolean allowTruncationIfGeometryIncomplete){
    
    /* create the link */
    MacroscopicLink link = createAndPopulateLink(osmWay, tags, startNodeIndex, endNodeIndex, allowTruncationIfGeometryIncomplete);
    if(link != null) {
      
      /* if geometry might be truncated, update the actual used start and end indices used if needed to correctly register remaining internal nodes */
      if(allowTruncationIfGeometryIncomplete) {
        startNodeIndex = OsmWayUtils.getOsmWayNodeIndexByLocation(osmWay, link.getNodeA().getPosition(), networkData);
        endNodeIndex = OsmWayUtils.getOsmWayNodeIndexByLocation(osmWay, link.getNodeB().getPosition(), networkData);
      }
      
      /* register internal nodes for breaking links later on during parsing */
      registerLinkInternalOsmNodes(link,startNodeIndex+1,endNodeIndex-1, osmWay);                  
      layerData.getProfiler().logLinkStatus(networkLayer.getNumberOfLinks());
    }
    return link;
  }  
  
  /** given the OSM way tags and settings we construct or find the appropriate link segment types for both directions, if no better alternative could be found
   * than the one that is passed in is used, which is assumed to be the default link segment type for the OSM way.
   * <b>It is not assumed that changes to mode access are ALWAYS accompanied by an access=X. However when this tag is available we apply its umbrella result to either include or exclude all supported modes as a starting point</b>
   *  
   * @param osmWay the tags belong to
   * @param tags of the OSM way to extract the link segment type for
   * @param linkSegmentType use thus far for this way
   * @return the link segment types for the forward direction and backward direction as per OSM specification of forward and backward. When no allowed modes exist in a direction the link segment type is set to null
   */
  protected Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> updatedLinkSegmentTypeBasedOnOsmWay(final OsmWay osmWay, final Map<String, String> tags, final MacroscopicLinkSegmentType linkSegmentType){
    
    /* collect the link segment types for the two possible directions (forward, i.e., in direction of the geometry, and backward, i.e., the opposite of the geometry)*/
    boolean forwardDirection = true;
    MacroscopicLinkSegmentType  forwardDirectionLinkSegmentType = extractDirectionalLinkSegmentTypeByOsmWay(osmWay, tags, linkSegmentType, forwardDirection);
    MacroscopicLinkSegmentType  backwardDirectionLinkSegmentType = extractDirectionalLinkSegmentTypeByOsmWay(osmWay, tags, linkSegmentType, !forwardDirection);

    return Pair.of(forwardDirectionLinkSegmentType, backwardDirectionLinkSegmentType);    
  }    
  
  /** Constructor
   * 
   * @param networkLayer to use
   * @param networkData to use
   * @param settings used for this parser
   * @param geoUtils geometric utility class instance based on network wide crs
   */
  protected OsmNetworkLayerParser(MacroscopicNetworkLayer networkLayer, OsmNetworkReaderData networkData, OsmNetworkReaderSettings settings, PlanitJtsCrsUtils geoUtils) {
    this.networkLayer = networkLayer;           
    this.networkData = networkData;
    this.geoUtils = geoUtils;
    this.settings = settings;
    
    this.layerData = new OsmNetworkReaderLayerData();    
    this.modeParser = new OsmNetworkLayerModeConversion(settings, networkLayer);
    
    initialiseEventListeners();        
  }


  /**
   * extract OSM way's PLANit infrastructure for the part of the way that is indicated. When it is marked as being a (partial) section of a circular way, then
   * we only allow the presumed one way direction applicable when creating directional link segments. The result is a newly registered link, its nodes, and linksegment(s) on
   * the network layer. The parser will try to infer missing/default data by using defaults set by the user. The provided link segment types are based on the osmWay data
   * and are assumed to be readily available and provided by the PlanitOsmHandler when identifying the correct layer (this layer)
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @param startNodeIndex to start parsing nodes from
   * @param endNodeIndex to stop parsing nodes 
   * @param isPartOfCircularWay flag
   * @param linkSegmentTypes to use
   * @return created link (if any), if no link could be created null is returned
   */    
  public MacroscopicLink extractPartialOsmWay(OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex,
      boolean isPartOfCircularWay, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> linkSegmentTypes) {

    MacroscopicLink link  = null;
    if(linkSegmentTypes!=null && linkSegmentTypes.anyIsNotNull() ) {
      
      /* a link only consists of start and end node, no direction and has no model information, we allow truncation near bounding box but only if it is not a circular way */
      boolean allowGeometryTruncation = !isPartOfCircularWay;
      link = extractLink(osmWay, tags, startNodeIndex, endNodeIndex, allowGeometryTruncation);
      if(link != null) {
        
        if(isPartOfCircularWay) {
          /* when circular we only accept one direction as accessible regardless of what has been identified so far;
           * clockwise equates to forward direction while anti-clockwise equates to backward direction */
          if(OsmWayUtils.isCircularWayDefaultDirectionClockwise(settings.getCountryName())) {
            linkSegmentTypes = Pair.of(linkSegmentTypes.first(), null);
          }else {
            linkSegmentTypes = Pair.of(null, linkSegmentTypes.second());
          }
        }
        
        /* a macroscopic link segment is directional and can have a shape, it also has model information */
        extractMacroscopicLinkSegments(osmWay, tags, link, linkSegmentTypes);
      }                          
    }    
    return link;
  }
    
  /**
   * whenever we find that internal nodes are used by more than one link OR a node is an extreme node
   * on an existing link but also an internal link on another node, we break the links where this node
   * is internal. the end result is a situations where all nodes used by more than one link are extreme 
   * nodes, i.e., start/end nodes.
   * <p>
   * Osm ways with multiple PLANit links associated with them can cause problems because in the handler we only register
   * nodes internal to the original way to link mapping. If a link is broken we adjust the original link and create an additional link
   * causing the original mapping between internal nodes and PLANit link to be potentially incorrect. We require the osmWaysWithMultiplePlanitLinks
   * map to track these changes so that we can always identify which of multiple PLANit links an internal node currently resides on.  
   * 
   * @param thePlanitNode to break links for where it is internal to them (based on its OSM node id reference)
   * @return true when links were broken, false otherwise
   */
  protected boolean breakLinksWithInternalNode(final Node thePlanitNode){

    if(layerData.isLocationInternalToAnyLink(thePlanitNode.getPosition())) {
      /* links to break */
      List<MacroscopicLink> linksToBreak = layerData.findPlanitLinksWithInternalLocation(thePlanitNode.getPosition());
                  
      /* break links */
      Map<Long, Set<MacroscopicLink>> newOsmWaysWithMultipleLinks = networkLayer.getLayerModifier().breakAt(
          linksToBreak, thePlanitNode, geoUtils.getCoordinateReferenceSystem(), l -> Long.parseLong(l.getExternalId()));
      
      /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to be able to find the correct
       * planit links for which osm nodes are internal */
      layerData.updateOsmWaysWithMultiplePlanitLinks(newOsmWaysWithMultipleLinks);
      
      return true;
    }
    return false;
  }
  
  /**
   * whenever we find that internal nodes are used by more than one link OR a node is an extreme node
   * on an existing link but also an internal link on another node, we break the links where this node
   * is internal. the end result is a situations where all nodes used by more than one link are extreme 
   * nodes, i.e., start/end nodes.
   * <p>
   * Osm ways with multiple planit links associated with them can cause problems because in the handler we only register
   * nodes internal to the original way to link mapping. If a link is broken we adjust the original link and create an additional link
   * causing the original mapping between internal nodes and PLANit link to be potentially incorrect. We require the osmWaysWithMultiplePlanitLinks
   * map to track these changes so that we can always identify which of multiple PLANit links an internal node currently resides on.  
   * 
   */ 
  protected void breakLinksWithInternalConnections() {
    LOGGER.info("Breaking OSM ways with internal connections into multiple links ...");

    long nodeIndex = -1;
    long originalNumberOfNodes = networkLayer.getNumberOfNodes();

    HashSet<Long> processedOsmNodeIds = new HashSet<>();
    while(++nodeIndex<originalNumberOfNodes) {
      Node node = networkLayer.getNodes().get(nodeIndex);

      // 1. break links when a link's internal node is another existing link's extreme node
      boolean linksBroken = breakLinksWithInternalNode(node);
      if(linksBroken) {
        processedOsmNodeIds.add(Long.valueOf(node.getExternalId()));
      }
    }

    //2. break links where an internal node of multiple links is shared, but it is never an extreme node of a link. do it sorted for reproducibility of ids
    Set<OsmNode> osmNodesInternalToPlanitLinks = this.layerData.getRegisteredOsmNodesInternalToAnyPlanitLink(2 /* minimum 2 links node is internal to */);
    osmNodesInternalToPlanitLinks.stream().sorted(Comparator.comparing(OsmNode::getId)).forEach(osmNode -> {
      if(!processedOsmNodeIds.contains(osmNode.getId())) {
        /* node does not yet exist in PLANit network because it was internal node so far, so create it first */
        Node planitIntersectionNode = extractNode(osmNode.getId());
        if(planitIntersectionNode == null) {
          LOGGER.severe(String.format("OSM node %d internal to one or more OSM ways could not be extracted as PLANit node when breaking links at its location, this should not happen", osmNode.getId()));
        }
        breakLinksWithInternalNode(planitIntersectionNode);
      }
    });

    LOGGER.info(String.format("Broke %d OSM ways into multiple links...DONE", getLayerData().getNumberOfOsmWaysWithMultiplePlanitLinks()));
  }
  
  /**
   * log profile information gathered during parsing (so far)
   */
  public void logProfileInformation() {
    this.layerData.getProfiler().logProfileInformation(networkLayer);
  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    layerData.reset();
    modifiedLinkSegmentTypes.reset();  
    initialiseEventListeners();
  }
    

  /**
   * complete the parsing, invoked from parent handler complete method
   * 
   */
  public void complete() {
    
    /* break links */
    breakLinksWithInternalConnections();
    
    /* useful for debugging */
    networkLayer.validate();
    /* stats*/
    logProfileInformation();
        
  }

  /** collect the gathered data pertaining to Osm to Planit entity mapping that might be relevant to other parts of the reader
   * 
   * @return layer data
   */
  public OsmNetworkReaderLayerData getLayerData() {
    return this.layerData;
  }

}

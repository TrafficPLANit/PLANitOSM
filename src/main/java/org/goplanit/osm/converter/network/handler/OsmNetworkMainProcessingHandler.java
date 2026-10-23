package org.goplanit.osm.converter.network.handler;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.*;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.network.OsmNetworkLayerParser;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.tags.*;
import org.goplanit.osm.util.*;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Triple;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.VehicularModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;

import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.utils.network.layer.physical.Node;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations. We parse these entities in distinct order,
 * first all nodes, then all ways, and then all relations. this allows us to incrementally construct the network
 * without backtracking or requiring the entire file to be in memory in addition to the memory model we're creating.
 * 
 * @author markr
 * 
 *
 */
public class OsmNetworkMainProcessingHandler extends OsmNetworkBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkMainProcessingHandler.class.getCanonicalName());

  /**  Extract roles that define the relation restriction
   *
   * @param osmRelation to extract from
   * @return from, via, to triple
   */
  private static Triple<OsmRelationMember, List<OsmRelationMember>, OsmRelationMember> extractTurnRoles(
      OsmRelation osmRelation){
    OsmRelationMember fromMember = null;
    OsmRelationMember toMember = null;
    List<OsmRelationMember> viaMembers = new ArrayList<>();

    for(int i = 0 ; i< osmRelation.getNumberOfMembers() ; i++){
      var member = osmRelation.getMember(i);
      String role = member.getRole();
      if (role == null) {
        continue;
      }
      switch (role) {
        case OsmRelationMemberRoleTags.FROM:
          fromMember = member;
          break;
        case OsmRelationMemberRoleTags.TO:
          toMember = member;
          break;
        case OsmRelationMemberRoleTags.VIA:
          viaMembers.add(member);
        default:
          break; // ignore
      }
    }
    return Triple.of(fromMember, viaMembers, toMember);
  }

       
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   */
  private boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId){
    return PlanitOsmNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId, getNetwork(), getNetworkData());
  }

  /**
   * Verify if node is spatially eligible for processing, taking into account bounding polygong and other
   * user settings
   *
   * @param osmNode to verify
   * @return true when eligible, false otherwise
   */
  private boolean isNodeSpatiallyEligible(final OsmNode osmNode) {
    var networkData = getNetworkData();
    boolean noBoundingPolygon = !networkData.hasBoundingArea() || !networkData.getBoundingArea().hasBoundingPolygon();

    return networkData.getOsmNodeData().containsPreregisteredOsmNode(osmNode.getId())
            &&
            ( noBoundingPolygon || getSettings().isKeepOsmNodeOutsideBoundingPolygon(osmNode.getId()) ||
                getProjectedBoundingAreaHelper().getPreparedBoundingPolygonInOriginalCrs().contains(
                    OsmNodeUtils.createPoint(osmNode))
            );
  }
       
  
  /**
   * now parse the remaining circular osmWays, which by default are converted into multiple links/link segments for
   * each part of the circular way in between connecting in and outgoing links/link segments that were parsed during
   * the regular parsing phase
   * 
   * @param circularOsmWay the circular osm way to parse 
   */
  private void handleRawCircularWay(final OsmWay circularOsmWay){
        
    Map<NetworkLayer, Set<MacroscopicLink>> createdLinksByLayer;
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(circularOsmWay);
    if(isActivatedRoadRailOrWaterwayBasedInfrastructure(tags)) {
      
      /* only process circular ways that are complete, e.g. not near bounding box causing some nodes to be missing
       * in which case we do not parse the entire circular way to avoid issues */
      if(!OsmWayUtils.isAllOsmWayNodesAvailable(
          circularOsmWay, getNetworkData().getOsmNodeData().getRegisteredOsmNodes())){
        return;
      }
      
      createdLinksByLayer = handleRawCircularWay(circularOsmWay, tags, 0 /* start at initial index */);
      if(createdLinksByLayer!=null) {
        /* register that OSM way has multiple PLANit links mapped (needed in case of subsequent break link actions
        on nodes of the osm way */
        createdLinksByLayer.forEach((key, value) -> {
          OsmNetworkReaderLayerData layerData = getNetworkData().getLayerParsers().get(key).getLayerData();
          layerData.updateOsmWaysWithMultiplePlanitLinks(circularOsmWay.getId(), value);
        });
      }

    }
  }  
  
  /** Recursive method that processes osm ways that have at least one circular section in it, but this might not be
   * perfect, i.e., the final node might not connect to the initial node. to deal with this, we first identify
   * the non-circular section(s), extract separate links for them, and then process
   * the remaining (perfectly) circular component of the OSM way via {@code handlePerfectCircularWay}
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param initialNodeIndex offset for starting point, part of the recursion
   * @return created links per layer map for this circular way if any, empty set if none
   */
  private Map<NetworkLayer, Set<MacroscopicLink>> handleRawCircularWay(
      final OsmWay circularOsmWay, final Map<String, String> osmWayTags, int initialNodeIndex) {

    Map<NetworkLayer, Set<MacroscopicLink>> createdLinksByLayer = new TreeMap<>();
    int finalNodeIndex = (circularOsmWay.getNumberOfNodes()-1);
        
    /* when circular road is not perfect, i.e., its end node is not the start node, we first split it
     * in a perfect circle and a regular non-circular osmWay */
    Pair<Integer,Integer> firstCircularIndices = OsmWayUtils.findIndicesOfFirstLoop(circularOsmWay, initialNodeIndex);            
    if(firstCircularIndices != null) {    
      /* unprocessed circular section exists */

      if(firstCircularIndices.first() > initialNodeIndex ) {
        /* create separate link for the lead up part that is NOT circular, if supporting multiple modes mapped to
        different layers we get multiple links */
        Map<NetworkLayer,MacroscopicLink> newLinkByLayer = extractPartialOsmWay(
            circularOsmWay, osmWayTags, initialNodeIndex, firstCircularIndices.first(), false /* not a circular section */);
        if(newLinkByLayer != null) {
          newLinkByLayer.forEach( (layer, link) -> { 
            createdLinksByLayer.putIfAbsent(layer, new HashSet<>());
            createdLinksByLayer.get(layer).add(link);} );
        }
        /* update offsets for circular part */
        initialNodeIndex = firstCircularIndices.first();
      }
      
      /* continue with the remainder (if any) starting at the end point of the circular component 
       * this is done first because we want all non-circular components to be available as regular links before
       * processing the circular parts*/
      if(firstCircularIndices.second() < finalNodeIndex) {
        Map<NetworkLayer, Set<MacroscopicLink>> newLinksByLayer =
                handleRawCircularWay(circularOsmWay, osmWayTags, firstCircularIndices.second());
        if(newLinksByLayer != null) {
          newLinksByLayer.forEach( (layer, links) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<>());
            createdLinksByLayer.get(layer).addAll(links);} );
        }
      }      
        
      /* extract the identified perfectly circular component */
      Map<NetworkLayer,Set<MacroscopicLink>> newLinksByLayer = handlePerfectCircularWay(
          circularOsmWay, osmWayTags, firstCircularIndices.first(), firstCircularIndices.second());
      if(newLinksByLayer != null) {
        newLinksByLayer.forEach( (layer, link) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<>());
          createdLinksByLayer.get(layer).addAll(link);} );
      }
      
    }else if(initialNodeIndex < finalNodeIndex) {
      /* last section is not circular, so extract partial link for it */
      Map<NetworkLayer,MacroscopicLink> newLinksByLayer = extractPartialOsmWay(
          circularOsmWay, osmWayTags, initialNodeIndex, finalNodeIndex, false /* not a circular section */);
      if(newLinksByLayer != null) {
        newLinksByLayer.forEach( (layer, link) -> { createdLinksByLayer.putIfAbsent(layer, new HashSet<>());
          createdLinksByLayer.get(layer).add(link);} );
      }     
    }  
    
    return createdLinksByLayer;
  }

  /** Process a circular way that is assumed to be perfect for the given start and end node, i.e., its end node is
   * the same as its start node
   * 
   * @param circularOsmWay to process
   * @param osmWayTags tags of the way
   * @param initialNodeIndex where the circular section starts
   * @param finalNodeIndex where the circular section ends (at the start)
   * @return created links per layer map with supported modes for this circular way if any, empty set if none
   */
  private Map<NetworkLayer,Set<MacroscopicLink>> handlePerfectCircularWay(
      OsmWay circularOsmWay, Map<String, String> osmWayTags, int initialNodeIndex, int finalNodeIndex) {

    Map<NetworkLayer,Set<MacroscopicLink>> createdLinksByLayer = new HashMap<>();
    int firstPartialLinkStartNodeIndex = -1;
    int partialLinkStartNodeIndex = -1;
    int partialLinkEndNodeIndex = -1;
    int numberOfConsideredNodes = finalNodeIndex-initialNodeIndex;
    boolean partialLinksPartOfCircularWay = true;
    
    /* construct partial links based on nodes on the geometry that are an extreme node of an already parsed link or
    are an internal node of an already parsed link */
    for(int index = initialNodeIndex ; index <= finalNodeIndex ; ++index) {
      long osmNodeId = circularOsmWay.getNodeId(index);
              
      if(hasNetworkLayersWithActiveOsmNode(osmNodeId)) {                            
        if(partialLinkStartNodeIndex < 0) {
          /* set first node to earlier realised node */
          partialLinkStartNodeIndex = index;
          firstPartialLinkStartNodeIndex = partialLinkStartNodeIndex;
        }else if(!(index==finalNodeIndex && partialLinkStartNodeIndex==firstPartialLinkStartNodeIndex)) {            
          /* identified valid partial link (statement above makes sure that in case the one duplicate node
          (first=last) is chosen as partial link, we do not accept is as a partial link as it represents  the entire
          loop, otherwise create link from start node to the intermediate node that attaches to an already existing
          planit link on the circular way */
          Map<NetworkLayer, MacroscopicLink> createdLinkByLayer = extractPartialOsmWay(
              circularOsmWay, osmWayTags, partialLinkStartNodeIndex, index, partialLinksPartOfCircularWay);
          if(createdLinkByLayer != null) {
            createdLinkByLayer.forEach( (layer, link) -> {
              createdLinksByLayer.putIfAbsent(layer, new HashSet<>());
              createdLinksByLayer.get(layer).add(link);} );
            
            /* update first node to last node of this link for next partial link */
            partialLinkEndNodeIndex = index;
            partialLinkStartNodeIndex = partialLinkEndNodeIndex;                
          }                         
        }
      }
    }
    
    if(partialLinkStartNodeIndex < 0) {
      /* nothing parsed yet... */
      Map<MacroscopicNetworkLayerImpl,Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>>
          linkSegmentTypesByLayer = extractLinkSegmentTypes(circularOsmWay, osmWayTags);
      if(linkSegmentTypesByLayer!=null && !linkSegmentTypesByLayer.isEmpty() &&
          linkSegmentTypesByLayer.values().stream().findAny().get().anyIsNotNull()) {
        /* yet circular way is of a viable type, i.e., it has mapped link segment type(s), but not a single
         connection to currently parsed network exists, this may indicate a problem */
        LOGGER.fine(String.format("circular way %d appears to have no connections to activated OSM way types ",
            circularOsmWay.getId()));
        /* still we continue parsing it by simply creating a new planit node, marked by setting
        partialLinkStartNodeIndex to 0  and continue */
        partialLinkStartNodeIndex = 0;
      }
    }
    
    Map<NetworkLayer, MacroscopicLink> createdLinkByLayer = null;
    if (partialLinkStartNodeIndex>= 0) {
      if (partialLinkEndNodeIndex < 0){        
        /* first partial link is not created either, only single connection point exists, so:
         * 1) when partialLinkStartNodeIndex = initial node -> take the halfway point as the dummy node, and the
         * final node as the end point, if not then...
         * 2) reset partialLinkStartNodeIndex to initial node and the earlier found partialLinkStartNodeIndex as
         * the midway point and then the final node as the end point */
        if(partialLinkStartNodeIndex == initialNodeIndex) {
          partialLinkEndNodeIndex = partialLinkStartNodeIndex + (numberOfConsideredNodes/2);  
        }else {
          partialLinkEndNodeIndex = partialLinkStartNodeIndex; 
          partialLinkStartNodeIndex = initialNodeIndex;
        }
        createdLinkByLayer = extractPartialOsmWay(
            circularOsmWay,
            osmWayTags,
            partialLinkStartNodeIndex,
            partialLinkEndNodeIndex,
            partialLinksPartOfCircularWay);
        if(createdLinkByLayer != null) {
          createdLinkByLayer.forEach( (layer, link) -> {
            createdLinksByLayer.putIfAbsent(layer, new HashSet<>());
            createdLinksByLayer.get(layer).add(link);} );
        }
        
        partialLinkStartNodeIndex = partialLinkEndNodeIndex;
        partialLinkEndNodeIndex = finalNodeIndex;
        createdLinkByLayer = extractPartialOsmWay(
            circularOsmWay,
            osmWayTags,
            partialLinkStartNodeIndex,
            partialLinkEndNodeIndex,
            partialLinksPartOfCircularWay);
      }else if(partialLinkEndNodeIndex != finalNodeIndex){            
        /* last partial link did not end at end of circular way but later, i.e., first partial link did not
        start at node zero. finalise by creating the final partial link to the first partial links start node*/
        partialLinkEndNodeIndex = firstPartialLinkStartNodeIndex;       
        createdLinkByLayer = extractPartialOsmWay(
            circularOsmWay,
            osmWayTags,
            partialLinkStartNodeIndex,
            partialLinkEndNodeIndex,
            partialLinksPartOfCircularWay);
      }    
      
      /* possibly no links created, for example when circular way is not of a viable type, or access is private,
      or some other valid reason*/
      if(createdLinkByLayer != null) {
        createdLinkByLayer.forEach( (layer, link) -> createdLinksByLayer.get(layer).add(link));
      }
    }
    return createdLinksByLayer;    
  }
    

  /**
   * The activated PLANit modes of a layer that an OSM mode stands for, an OSM mode category such as psv standing for
   * each of its modes
   *
   * @param osmModeOrCategory to convert
   * @param layer whose modes count
   * @return the PLANit modes of the layer, empty when none
   */
  private Set<Mode> findActivatedLayerModes(String osmModeOrCategory, MacroscopicNetworkLayer layer) {
    var osmModes = OsmRoadModeCategoryTags.getRoadModesOfModeOrCategory(osmModeOrCategory);
    var modes = getNetworkData().getLayerParser(layer).getModeConversion().getActivatedPlanitModes(osmModes);
    modes.retainAll(layer.getSupportedModes());
    return modes;
  }

  /**
   * Find, per restriction type of an OSM turn restriction, the modes of the layer it bans. A restriction tag is for
   * vehicles: it bans every mode of the layer that is a vehicle, except those listed in the except tag and those with
   * a restriction tag of their own. A restriction tag for one mode, such as restriction:hgv, bans that mode only. A
   * type banning no activated mode of the layer is left out
   *
   * @param tags of the restriction
   * @param layer whose modes count
   * @return the banned modes per restriction type, in the order found
   */
  private Map<String, Set<Mode>> findBannedModesByRestrictionType(Map<String, String> tags,
      MacroscopicNetworkLayer layer) {
    var bannedModesByType = new LinkedHashMap<String, Set<Mode>>();
    var modesWithOwnRestriction = new HashSet<Mode>();
    for (var tag : tags.entrySet()) {
      var osmMode = OsmRelationRestrictionTags.getModeOfRestrictionForMode(tag.getKey());
      if (osmMode == null) {
        continue;
      }
      var modes = findActivatedLayerModes(osmMode, layer);
      modesWithOwnRestriction.addAll(modes);
      bannedModesByType.computeIfAbsent(tag.getValue(), type -> new HashSet<>()).addAll(modes);
    }
    var restrictionType = tags.get(OsmRelationRestrictionTags.RESTRICTION);
    if (restrictionType != null) {
      var modes = new HashSet<>(layer.getSupportedModes());
      modes.removeIf(mode ->
          mode.hasPhysicalFeatures() && mode.getPhysicalFeatures().getVehicularType() == VehicularModeType.NO_VEHICLE);
      var excepted = tags.get(OsmRelationRestrictionTags.EXCEPT);
      if (excepted != null) {
        Arrays.stream(excepted.split(";")).map(String::trim).filter(osmMode -> !osmMode.isEmpty())
            .forEach(osmMode -> modes.removeAll(findActivatedLayerModes(osmMode, layer)));
      }
      modes.removeAll(modesWithOwnRestriction);
      bannedModesByType.computeIfAbsent(restrictionType, type -> new HashSet<>()).addAll(modes);
    }
    bannedModesByType.values().removeIf(Set::isEmpty);
    return bannedModesByType;
  }

  /**
   * Extract PLANit turn restriction from the given OSM relation
   *
   * @param osmRelation to extract from (which is assumed to reflect a turn restriction of some kind)
   * @param tags of the relation
   */
  private void extractOsmTurnRestriction(OsmRelation osmRelation, Map<String, String> tags) {
    var fromViaToTriple = extractTurnRoles(osmRelation);
    OsmRelationMember osmFromMember = fromViaToTriple.first();
    OsmRelationMember osmToMember = fromViaToTriple.third();
    // check for faulty/unsupported tagging
    if(osmFromMember == null || osmToMember == null){
      return;
    }

    // check for spatial eligibility
    var spatialEligibility = getNetworkData().getOsmSpatialEligibilityData();
    if(!spatialEligibility.isOsmWaySpatiallyEligible(osmToMember.getId()) ||
        !spatialEligibility.isOsmWaySpatiallyEligible(osmFromMember.getId())){
      return;
    }

    //todo: initially identify if via relation is a way or multiple nodes/ways. If so we track how many we ditch for
    //  logging but we ignore them as banned turns pass one node only. Analyse what percentage of total this is
    //  to flag if we should try and parse/support this

    // check via support - currently we only support a single node as via point // todo: expand to support?
    if(fromViaToTriple.second() == null || fromViaToTriple.second().isEmpty() || fromViaToTriple.second().size() > 1){
      return;
    }
    OsmRelationMember osmViaMember = fromViaToTriple.second().get(0);

    // check via type - currently we only support the via to be a node and not a way // todo: expand to support?
    if(osmViaMember.getType().equals(EntityType.Way)){
      return;
    }
    // obtain PLANit parsed equivalents of banned turn elements
    var planitFromLink = getNetworkData().findPlanitLinkByOsmWayId(osmFromMember.getId());
    var planitToLink = getNetworkData().findPlanitLinkByOsmWayId(osmToMember.getId());
    var planitViaNode = getNetworkData().findPlanitNodeByOsmNode(
        getNetworkData().getOsmNodeData().getRegisteredOsmNode(osmViaMember.getId()));

    if(planitViaNode == null){
      LOGGER.warning(String.format(
          "OSM turn restriction via node (%d) not available in parser, this shouldn't happen", osmViaMember.getId()));
      return;
    }
    if(planitFromLink == null || planitToLink == null){
      LOGGER.severe(String.format("OSM turn restriction from (%d) or to (%d) link not available in parser " +
          "this shouldn't happen", osmFromMember.getId(), osmToMember.getId()));
      return;
    }

    boolean fromViaNodeIsInternal = !planitFromLink.hasVertex(planitViaNode);
    boolean toViaNodeIsInternal = !planitToLink.hasVertex(planitViaNode);
    if(fromViaNodeIsInternal && toViaNodeIsInternal){
      LOGGER.warning(String.format("OSM banned turn (%d) defined on two intersecting OSM ways, via node " +
          "internal on both, ambiguous, skip", osmRelation.getId()));
      return;
    }

    // obtain PLANit directional segments from link/node info
    var planitFromSegment = planitFromLink.getSegmentUpstreamOf(planitViaNode);
    if(fromViaNodeIsInternal || toViaNodeIsInternal){
      //        ^               |
      //        |               V
      //  ------------>  or ------------>
      //
      // todo: ambiguous unless we look at geometry --> support time permitting
      LOGGER.warning(String.format("OSM banned turn (%d) defined on non-terminating node of OSM way, " +
          "not yet supported, skip", osmRelation.getId()));
      return;
    }
    var planitToSegment = planitToLink.getSegmentDownstreamFrom(planitViaNode);

    if(planitFromSegment == null || planitToSegment == null){
      /* a link only carries a segment for a direction that permits at least one mode, so a one-way way yields a
       * single directional segment. When the restriction applies to a direction that has no segment, the turn it
       * bans is impossible regardless and there is nothing to register */
      LOGGER.fine(String.format("OSM turn restriction (%d) applies to a direction without a link segment, " +
          "turn is impossible regardless, skip", osmRelation.getId()));
      return;
    }

    var layer = getNetwork().getLayerByMode(((MacroscopicLinkSegment) planitFromSegment).getAnyAllowedMode());
    for (var bannedModesOfType : findBannedModesByRestrictionType(tags, layer).entrySet()) {
      var restrictionType = bannedModesOfType.getKey();
      var bannedModes = bannedModesOfType.getValue();
      switch (restrictionType) {
        case OsmRelationRestrictionTags.NO_LEFT_TURN:
        case OsmRelationRestrictionTags.NO_RIGHT_TURN:
        case OsmRelationRestrictionTags.NO_U_TURN:
        case OsmRelationRestrictionTags.NO_STRAIGHT_ON:
          extractOsmProhibitiveTurnBan(osmRelation, tags, planitFromSegment, planitViaNode, planitToSegment,
              restrictionType, bannedModes);
          break;

        case OsmRelationRestrictionTags.ONLY_LEFT_TURN:
        case OsmRelationRestrictionTags.ONLY_RIGHT_TURN:
        case OsmRelationRestrictionTags.ONLY_STRAIGHT_ON:
          extractBannedTurnsForMandatoryTurn(osmRelation, tags, planitFromSegment, planitViaNode, planitToSegment,
              restrictionType, bannedModes);
          break;

        case OsmRelationRestrictionTags.NO_EXIT:
        case OsmRelationRestrictionTags.NO_ENTRY:
        default:
          // Unknown or unsupported restriction type
          break;
      }
    }
  }

  /**
   * Extract the restricted movement based on the prohibited turn restriction in OSM, i.e., when a single turn is
   * restricted but other turns remain viable. Only modes that can make the turn are banned: those allowed on both its
   * segments. When none of them is banned, no banned turn is registered
   *
   * @param osmRelation     relation with the information to extract
   * @param tags            tags of the osm entity
   * @param planitFromSegment        the PLANit from link
   * @param planitViaNode        the PLANit via node
   * @param planitToSegment        the PLANit to link
   * @param restrictionType type of prohibitive restriction
   * @param bannedModes the modes the restriction bans
   */
  private void extractOsmProhibitiveTurnBan(
      OsmRelation osmRelation,
      Map<String, String> tags,
      EdgeSegment planitFromSegment,
      Node planitViaNode,
      EdgeSegment planitToSegment,
      String restrictionType,
      Set<Mode> bannedModes) {

    var fromSegment = (MacroscopicLinkSegment) planitFromSegment;
    var toSegment = (MacroscopicLinkSegment) planitToSegment;
    var modesThatCanMakeTurn = toSegment.getAllowedModesFrom(fromSegment.getAllowedModes());
    var bannedModesThatCanMakeTurn = new HashSet<>(modesThatCanMakeTurn);
    bannedModesThatCanMakeTurn.retainAll(bannedModes);
    if (bannedModesThatCanMakeTurn.isEmpty()) {
      return;
    }
    /* a ban for every mode that can make the turn names none, it then bans every mode */
    Collection<Mode> explicitlyBannedModes =
        bannedModesThatCanMakeTurn.containsAll(modesThatCanMakeTurn) ? List.of() : bannedModesThatCanMakeTurn;

    // construct PLANit banned turn
    var layer = getNetwork().getLayerByMode(fromSegment.getAnyAllowedMode());
    var newBannedTurn = layer.getBannedTurns().getFactory().registerNew(fromSegment, toSegment,
        explicitlyBannedModes);
    newBannedTurn.setExternalId(String.valueOf(osmRelation.getId()));

    // log
    getNetworkData().getLayerParsers().get(layer).getLayerData().getProfiler().logBannedTurnStatus(
        layer.getNumberOfBannedTurns());
  }

  /**
   * Extract the only allowed movement based on the OSM tagging, i.e., when only single turn remains viable but all
   * other turns are restricted.
   *
   * @param osmRelation     relation with the information to extract
   * @param tags            tags of the osm entity
   * @param planitFromSegment        the PLANit from link
   * @param planitViaNode        the PLANit via node
   * @param planitToSegment        the PLANit to link
   * @param restrictionType type of prohibitive restriction
   * @param bannedModes the modes the restriction bans on the other turns
   */
  private void extractBannedTurnsForMandatoryTurn(
      OsmRelation osmRelation,
      Map<String, String> tags,
      EdgeSegment planitFromSegment,
      Node planitViaNode,
      EdgeSegment planitToSegment,
      String restrictionType,
      Set<Mode> bannedModes) {

    // instead of marking this particular turn prohibited, we mark all other turns from this incoming segment as
    // prohibited
    for(var exitSegment : planitViaNode.getExitLinkSegments()){
      if(exitSegment.equals(planitToSegment)){
        continue;
      }
      extractOsmProhibitiveTurnBan(osmRelation, tags, planitFromSegment, planitViaNode, exitSegment, restrictionType,
          bannedModes);
    }
  }

  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param osmWay the way
   * @param tags the tags of this way
   * @return the link segment types per layer if available, otherwise null is returned
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> getDefaultLinkSegmentTypeByOsmWayType(
          OsmWay osmWay, Map<String, String> tags) {
    String osmTypeKeyToUse = null;
    
    /* exclude ways that are areas and in fact not ways */
    boolean isExplicitArea = OsmTags.isArea(tags);   
    if(isExplicitArea) {
      return null;
    }
    
    var settings = getSettings();
        
    /* highway (road), railway (rail), or waterway */
    Function<String, Boolean> isWayActivatedLambda = osmTypeValueToUse -> false;
    Function<String, Boolean> isTypeConfigurationMissingLambda = osmTypeValueToUse -> false;
    if (OsmHighwayTags.hasHighwayKeyTag(tags) && settings.isHighwayParserActive()) {
      osmTypeKeyToUse = OsmHighwayTags.getHighwayKeyTag();
      isWayActivatedLambda = osmTypeValueToUse ->
          settings.getHighwaySettings().isOsmHighwayTypeDeactivated(osmTypeValueToUse);
      isTypeConfigurationMissingLambda = OsmHighwayTags::isNonRoadBasedHighwayValueTag;
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags) && settings.isRailwayParserActive()) {
      osmTypeKeyToUse = OsmRailwayTags.getRailwayKeyTag();
      isWayActivatedLambda = osmTypeValueToUse ->
          settings.getRailwaySettings().isOsmRailwayTypeDeactivated(osmTypeValueToUse);
      isTypeConfigurationMissingLambda = OsmRailwayTags::isNonRailBasedRailway;
    }else if(OsmWaterwayTags.isWaterBasedWay(tags) && settings.isWaterwayParserActive()) {
      osmTypeKeyToUse = OsmWaterwayTags.getUsedKeyTag(tags);
      isWayActivatedLambda = osmTypeValueToUse ->
          settings.getWaterwaySettings().isOsmWaterwayTypeActivated(osmTypeValueToUse);
      isTypeConfigurationMissingLambda = osmTypeValueToUse -> true; // not yet aware of situations for waterways
      // where this happens
    }
    
    /* without mapping no type */
    if(osmTypeKeyToUse==null) {
      return null;
    }

    String osmTypeValueToUse = tags.get(osmTypeKeyToUse);        
    Map<NetworkLayer,MacroscopicLinkSegmentType> linkSegmentTypes =
        getNetwork().getDefaultLinkSegmentTypeByOsmTag( osmTypeKeyToUse, osmTypeValueToUse);
    if(linkSegmentTypes != null) {
      for(var entry : linkSegmentTypes.entrySet()){
        var layer = (MacroscopicNetworkLayerImpl) entry.getKey();
        var linkSegmentType = entry.getValue();
        if(linkSegmentType != null) {
            getNetworkData().getLayerParser(layer).getLayerData().getProfiler().incrementOsmTagCounter(
                osmTypeKeyToUse, osmTypeValueToUse);
          }
      }
    }
    /* determine if we should inform the user on not finding a mapped type, i.e., is this of concern or legitimate
    because we do not want or it cannot be mapped in the first place*/
    /*... not available even though it is not marked as deactivated AND it appears to be a type that can be converted
     into a link, so something is not properly configured*/
    else if(isWayActivatedLambda.apply(osmTypeValueToUse) && isTypeConfigurationMissingLambda.apply(osmTypeValueToUse)){
      LOGGER.warning(String.format(
          "No link segment type available for : %s:%s (id:%d) --> ignored. Consider explicitly supporting or " +
              "unsupporting this type", osmTypeKeyToUse, osmTypeValueToUse, osmWay.getId()));
    }
        
    return linkSegmentTypes;
  }  
  
  /** process all registered circular ways after parsing of basic nodes and ways is complete. Because circular
   * ways are transformed into multiple
   * links, they in effect yield multiple links per original OSM way (id). In case such an OSMway is referenced
   * later it no longer maps to a single
   * PLANit link, hence we return how each OSMway is mapped to the set of links created for the circular way
   *   
   */
  protected  void processCircularWays() {
    
    LOGGER.info("Converting OSM circular ways into multiple link topologies...");
    
    /* process circular ways in order of original OSM way ids, so it is a deterministic process and results are
    reproducible in terms of generated PLANit link/segment ids */
    getNetworkData().getOsmCircularWays().entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry -> {
      try {        
        
        handleRawCircularWay(entry.getValue());
                
      }catch (Exception e) {
        LOGGER.severe(e.getMessage());
        LOGGER.severe(String.format("Unable to process circular way OSM id: %d",entry.getKey()));
      }        
    });
    
    LOGGER.info(String.format("Processed %d circular ways...DONE",getNetworkData().getOsmCircularWays().size()));
    getNetworkData().clearOsmCircularWays();
  }
    
  /**
   * extract OSM way's PLANit infrastructure for the entire way, i.e., link, nodes, and link segments where applicable. 
   * The parser will try to infer missing/default data by using defaults set by the user
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   */
  protected void extractOsmWay(OsmWay osmWay, Map<String, String> tags){
    /* parse entire OSM way (0-endNodeIndex), and not part of a circular piece of infrastructure */
    extractPartialOsmWay(osmWay, tags, 0,
        osmWay.getNumberOfNodes()-1, false /*not part of circular infrastructure */);
  }

  /**
   * Extract OSM way's PLANit infrastructure for the part of the way that is indicated. When it is marked as being
   * a (partial) section of a circular way, then
   * we only allow the presumed one way direction applicable when creating directional link segments. The result is
   * a newly registered link, its nodes, and link segment(s) on
   * the network. The parser will try to infer missing/default data by using defaults set by the user.
   * 
   * @param osmWay to parse
   * @param tags related to the OSM way
   * @param startNodeIndex to start parsing nodes from
   * @param endNodeIndex to end parsing nodes from
   * @param isPartOfCircularWay indicates if it is part of a circular way or not
   * @return created link (if any), if no link could be created null is returned
   */  
  protected Map<NetworkLayer,MacroscopicLink> extractPartialOsmWay(
          OsmWay osmWay, Map<String, String> tags, int startNodeIndex, int endNodeIndex, boolean isPartOfCircularWay) {

    Map<NetworkLayer,MacroscopicLink> linksByLayer = null;
    var directionalLinkSegmentTypesByLayer = extractLinkSegmentTypes(osmWay, tags);
    for(var entry : directionalLinkSegmentTypesByLayer.entrySet()) {
      MacroscopicNetworkLayerImpl networkLayer = entry.getKey();
      var linkSegmentTypesPair = entry.getValue();
      
      if(linkSegmentTypesPair != null && linkSegmentTypesPair.anyIsNotNull()) {
        OsmNetworkLayerParser layerHandler = getNetworkData().getLayerParser(networkLayer);
        if(layerHandler == null) {
          throw new PlanItRunTimeException("Layer handler not available, should have been instantiated in " +
              "PlanitOsmHandler constructor");
        }
        /* delegate to layer handler */
        MacroscopicLink link = layerHandler.extractPartialOsmWay(
            osmWay, tags, startNodeIndex, endNodeIndex, isPartOfCircularWay, linkSegmentTypesPair);
        if(link != null) {
          if(linksByLayer == null) {
            linksByLayer = new HashMap<>();
          }
          linksByLayer.put(networkLayer, link);        
        }
      }
    }    
    
    return linksByLayer;
  }

  /** actual handling of OSM way assuming it is eligible for processing
   * 
   * @param osmWay to parse
   * @param tags of the OSM way
   */
  protected void handleOsmWay(OsmWay osmWay, Map<String, String> tags) {
    
    /* circular ways special case filter */
    if(OsmWayUtils.isCircularOsmWay(osmWay, tags, false)) {          
      
      /* postpone creation of link(s) for activated OSM highways that have a circular component and are not
      areas (areas cannot become roads) */
      /* Note: in OSM roundabouts are a circular way, in PLANit, they comprise several one-way link connecting
      exists and entries to the roundabout */
      getNetworkData().addOsmCircularWay(osmWay);
      
    }else{
      
      /* extract regular OSM way; convert to PLANit infrastructure */          
      extractOsmWay(osmWay, tags);                    
                  
    }
  }

  /**
   * Constructor
   *
   * @param networkToPopulate the network to populate
   * @param networkData the data used for populating the network
   * @param settings for the handler
   */
  public OsmNetworkMainProcessingHandler(
      final PlanitOsmNetwork networkToPopulate,
      final OsmNetworkReaderData networkData,
      final OsmNetworkReaderSettings settings) {
    super(networkToPopulate, networkData, settings);
  }
   

  /**
   * construct PLANit nodes from OSM nodes
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) {
    // no longer needed, pre-processing has dealt with full eligibility already (refactored 18/7/26)
  }

  /**
   * parse an OSM way to extract link and link segments (including type). If insufficient information
   * is available the handler will try to infer the missing data by using defaults set by the user
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {

    /* filter out OSMWays that have been spatially deemed ineligible */
    if(!getNetworkData().getOsmSpatialEligibilityData().isOsmWaySpatiallyEligible(osmWay.getId())){
      return;
    }

    wrapHandleInfrastructureOsmWay(osmWay, this::handleOsmWay);
            
  }

  /**
   * parse network relations, i.e., banned turn movements
   */
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    var tags = OsmModelUtil.getTagsAsMap(osmRelation);
    if(!tags.containsKey(OsmTags.TYPE)){
      return;
    }

    // process explicitly tagged (turn) restrictions
    if(OsmTagUtils.keyMatchesAnyValueTag(tags, OsmTags.TYPE, OsmRelationTypeTags.RESTRICTION) &&
        OsmRelationRestrictionTags.hasRestriction(tags)){
      extractOsmTurnRestriction(osmRelation, tags);
    }
  }


  /** extract the correct link segment type based on the configuration of supported modes, the defaults for the given
   * OSM way and any
   * modifications to the mode access based on the passed in tags of the OSM way
   * 
   * @param osmWay the way this type extraction is executed for 
   * @param tags tags belonging to the OSM way
   * @return appropriate link segment types for forward and backward direction per network layer. If no modes are
   * allowed in a direction, the link segment type will be null
   */
  protected Map<MacroscopicNetworkLayerImpl, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>>
  extractLinkSegmentTypes(OsmWay osmWay, Map<String, String> tags){

    Map<MacroscopicNetworkLayerImpl, Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType>>
        linkSegmentTypesByLayerByDirection = new TreeMap<>();

    /* a default link segment type should be available as starting point*/
    Map<NetworkLayer, MacroscopicLinkSegmentType> linkSegmentTypesByLayer =
        getDefaultLinkSegmentTypeByOsmWayType(osmWay, tags);
    if(linkSegmentTypesByLayer != null) {      
      
      /* per layer identify the directional link segment types based on additional access changes from OSM tags */
      for(var entry : linkSegmentTypesByLayer.entrySet()) {
        MacroscopicNetworkLayerImpl networkLayer = (MacroscopicNetworkLayerImpl) entry.getKey();
        var linkSegmentType = entry.getValue();
        
        /* collect possibly modified type (per direction) */
        Pair<MacroscopicLinkSegmentType, MacroscopicLinkSegmentType> typesPerDirectionPair =
            getNetworkData().getLayerParser(networkLayer).findOrConstructAlternativeLinkSegmentTypeBasedOnOsmWay(
                osmWay, tags, linkSegmentType);
        if(typesPerDirectionPair != null) {
          linkSegmentTypesByLayerByDirection.put(networkLayer, typesPerDirectionPair);
        }
      }
    }
    return linkSegmentTypesByLayerByDirection;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {

    /* process circular ways */
    processCircularWays();
            
    /* delegate to each layer handler present, do this in deterministic order to ensure any created
    PLANit links/segments
    * will obtain the same ids when running the same parser multiple times*/
    getNetworkData().getLayerParsers().entrySet().stream().sorted().forEach( entry -> {
      OsmNetworkLayerParser networkLayerHandler = entry.getValue();
      
      /* break links on layer with internal connections to multiple osm ways */
      networkLayerHandler.complete();      
    });
        
    LOGGER.info("OSM basic network parsing...DONE");
  }
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {
    getNetworkData().reset();    
  }  
  
}



package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Envelope;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.misc.StringUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that conducts final parsing round where all stop_positions in relatons are mapped to the now parsed transfer zones.
 * This is done separately because transfer zones are sometimes also part of relations and it is not guaranteed that all transfer zones
 * are available when encountering a stop_position in a relation. So we parse them in another pass.
 * <p>
 * Also, all unprocessed stations that are not part of any relatino are converted into transfer zones and connectoids here since
 * we can now guarantee they are not part of a relation, i.e., stop_area 
 * 
 * @author markr
 * 
 *
 */
public class PlanitOsmZoningPostProcessingHandler extends PlanitOsmZoningBaseHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningPostProcessingHandler.class.getCanonicalName());
    
  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils;  
      
  /**Attempt to find the transfer zones by the use of the passed in tags containing references via key tag:
   * 
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   * <p>
   * In case multiple zones are found with the exact same reference, we select the zone that is closest by. In case multiple zones are found with unique references 
   * (when the reference value contains multiple reference, e.g. 1;2), then we keep all zones, since each one represents the cloesest by unique reference
   * 
   * @param osmNode referring to zero or more transfer zones via its tags
   * @param tags to search for reference keys in
   * @param availableTransferZones to choose from
   * @param geoUtils to use in case of multiple matches requiring selection of closest entry spatially 
   * @return found transfer zones that have been parsed before, null if no match is found
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> findClosestTransferZoneByTagReference(OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> availableTransferZones, PlanitJtsUtils geoUtils) throws PlanItException {
    Map<String, TransferZone> foundTransferZones = null;
    /* ref value, can be a list of multiple values */
    String refValue = OsmTagUtils.getValueForSupportedRefKeys(tags);
    if(refValue != null) {
      String[] transferZoneRefValues = StringUtils.splitByAnythingExceptAlphaNumeric(refValue);
      for(int index=0; index < transferZoneRefValues.length; ++index) {
        boolean multipleMatchesForSameRef = false;
        String localRefValue = transferZoneRefValues[index];
        for(TransferZone transferZone : availableTransferZones) {
          Object refProperty = transferZone.getInputProperty(OsmTags.REF);
          if(refProperty != null && localRefValue.equals(String.class.cast(refProperty))) {
            /* match */
            if(foundTransferZones==null) {
              foundTransferZones = new HashMap<String,TransferZone>();
            }              
            
            TransferZone prevTransferZone = foundTransferZones.put(localRefValue,transferZone);
            if(prevTransferZone != null) {
              multipleMatchesForSameRef = true;
              /* choose closest of the two spatially */
              TransferZone closestZone = (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, Set.of(prevTransferZone, transferZone), geoUtils);
              foundTransferZones.put(localRefValue,closestZone);
            }
          }
        }
        if(multipleMatchesForSameRef == true ) {
          LOGGER.fine(String.format("Salvaged: non-unique reference (%s) on stop_position %d, selected spatially closest platform/pole %s", localRefValue, osmNode.getId(),foundTransferZones.get(localRefValue).getExternalId()));         
        }        
      }
    }
    return foundTransferZones!=null ? foundTransferZones.values() : null;
  } 
  
  /**Attempt to find the transfer zones by the use of the passed in name where the transfer zone (representing an osm platform) must have the exact same name to match.
   * 
   * @param osmId of the entity we are attempting to find a match for
   * @param nameToMatch to check for within eligible transfer zones
   * @param availableTransferZones to choose from
   * @return matches transfer zones, null if no match is found
   */  
  private Collection<TransferZone> findTransferZoneMatchByName(long osmId, String nameToMatch, Collection<TransferZone> availableTransferZones) {
    Set<TransferZone> foundTransferZones = null;
    for(TransferZone transferZone : availableTransferZones) {
      String transferZoneName  = transferZone.getName();
      if(transferZoneName != null && transferZoneName.equals(nameToMatch)) {
        /* match */
        if(foundTransferZones == null) {
          foundTransferZones = new HashSet<TransferZone>();
        }
        foundTransferZones.add(transferZone);            
      }
    }
    
  if(foundTransferZones!=null && foundTransferZones.size()>1) {
    LOGGER.fine(String.format("multiple platform/pole matches found for name %s and access point osm id %d",nameToMatch, osmId));
  }
  
  return foundTransferZones;
  }  
  
  /** find the closest and/or most likely transfer zone for the given osm node and its tags (with or without a reference
   * for additional information for mapping). Use the search radius from the settings to identify eligible transfer zones and then
   * use information on modes, references and spatial proximity to choose the most likely option. 
   * 
   * @param osmNode representing a stop position
   * @param tags of the node
   * @param planitModes the stop is compatible with
   * @return most likely transfer zone
   * @throws PlanItException thrown if error
   */
  private TransferZone findMostLikelyTransferZoneForPtv2StopPositionSpatially(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    TransferZone foundZone = null;
    
    /* collect potential transfer zones based on spatial search*/
    double searchRadiusMeters = getSettings().getStopToWaitingAreaSearchRadiusMeters();
    Envelope searchArea = geoUtils.createBoundingBox(osmNode.getLongitude(),osmNode.getLatitude(), searchRadiusMeters);
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getTransferZonesWithoutConnectoid(searchArea);
    
    if(potentialTransferZones==null || potentialTransferZones.isEmpty()) {
      LOGGER.fine(String.format("Unable to locate nearby transfer zone (search radius of %.2f (m)) when mapping stop position for osm node %d",searchRadiusMeters, osmNode.getId()));
      return null;
    }
        
    /* Ideally we relate via explicit references available on Osm tags */
    /* Occurs when: platform (zone) exists but is not included in stop_area. 
     * Note: This indicates poor tagging, yet occurs in reality, e.g. Sydney, circular quay for example */
    Collection<TransferZone> matchedTransferZones = findClosestTransferZoneByTagReference(osmNode, tags, potentialTransferZones, geoUtils);      
    if(matchedTransferZones == null) {
      /* no explicit reference or name match is found, we collect the closest by potential match */
      foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, potentialTransferZones, geoUtils);
      if(foundZone == null) {
        LOGGER.warning(String.format("Unable to match osm node %d to any existing transfer zone within search radius of %.2f (m), ignored",osmNode,searchRadiusMeters));
      }
    }
        
    return foundZone;
  }

  /** find the transfer zones that are accessible to the stop_position on the given node and given the already identified transfer zones
   * in the group it belongs to
   * 
   * @param osmNode node representing the stop_position
   * @param tags of the node
   * @param stopAreaTransferzones the transfer zones of the stop_area this stop_position belongs to
   * @throws PlanItException thrown if error
   */  
  private Collection<TransferZone> findAccessibleTransferZonesForPtv2StopPosition(final OsmNode osmNode, final Map<String, String> tags, final Collection<TransferZone> stopAreaTransferZones) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = null;
        
    /* reference to platform, i.e. transfer zone */
    matchedTransferZones = findClosestTransferZoneByTagReference(osmNode, tags, stopAreaTransferZones, geoUtils);    
    if(matchedTransferZones == null || matchedTransferZones.isEmpty() && tags.containsKey(OsmTags.NAME)) {
      /* try matching names, this should only result in single match */
      matchedTransferZones = findTransferZoneMatchByName(osmNode.getId(),tags.get(OsmTags.NAME), stopAreaTransferZones);
      if(matchedTransferZones!= null && matchedTransferZones.size()>1) {
        /* multiple match(es) found, find most likely spatially from this subset*/
        TransferZone foundTransferZone = (TransferZone) PlanitOsmNodeUtils.findZoneClosest(osmNode, matchedTransferZones, geoUtils);        
        matchedTransferZones = Collections.singleton(foundTransferZone);
      }      
    }
          
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* no matches found by name or reference tag, so it has no reference, or transfer zone is not part of the area. Either way 
       * we must still try to find the most likely match. We do so geographically */
      TransferZone foundTransferZone = findMostLikelyTransferZoneForPtv2StopPositionSpatially(osmNode, tags);
      if(foundTransferZone != null) {
        matchedTransferZones = Collections.singleton(foundTransferZone);
      }
    }
        
    return matchedTransferZones;
  }  
  
  /** create and/or update directed connectoids for the transfer zones and mode combinations when eligible, based on the passed in osm node 
   * where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param tags to use
   * @param transferZones connectoids are assumed to provide access to
   * @param transferZoneGroup the connectoids belong to
   * @param planitMode this connectoid is allowed access for
   * @throws PlanItException thrown if error
   */
  private void extractDirectedConnectoids(OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> transferZones, Set<Mode> planitModes, TransferZoneGroup transferZoneGroup) throws PlanItException {
    boolean success = false; 
    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    for(Mode planitMode : planitModes) {
      /* layer */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);
  
      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) */
        success = extractDirectedConnectoidsForMode(osmNode, tags, transferZone, networkLayer, planitMode);
        if(success && transferZoneGroup != null && !transferZone.isInTransferZoneGroup(transferZoneGroup)) {
          /* in some rare cases only the stop locations are part of the stop_area, but not the platforms next to the road/rail, only then this situation is triggered and we salve the situation */
          LOGGER.info(String.format("Salvaged: platform/pole %s identified for stop_position %d is not part of the stop_area %s, added it to transfer zone group",transferZone.getExternalId(), osmNode.getId(), transferZoneGroup.getExternalId()));
          transferZoneGroup.addTransferZone(transferZone);
        }
      }      
    }
  }

  /**
   * Try to extract a station from the entity. In case no existing platforms/stop_positions can be found nearby we create them fro this station because the station
   * represents both platform(s), station, and potentially stop_positions. In case existing platforms/stop_positinos can be found, it is likely the station is a legacy
   * tag that is not yet properly added to an existing stop_area, or the tagging itself only provides platforms without a stop_area. Either way, in this case the station is
   * to be discarded since appropriate infrastructure is already available.
   * 
   * @param osmEntity to identify non stop_area station for
   * @param eligibleSearchBoundingBox the search area to see if more detailed and related existing infrastructure can be found that is expected to be conected to the station
   */
  private void extractNonStopAreaStation(OsmEntity osmEntity, Envelope eligibleSearchBoundingBox) {
    LOGGER.info(String.format("******* %d *******",osmEntity.getId()));
        
    /* potential transfer zones in neighbourhood...*/
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getTransferZonesWithoutConnectoid(eligibleSearchBoundingBox);
    if(potentialTransferZones != null && !potentialTransferZones.isEmpty()) {
      Map<String,String> tags = OsmModelUtil.getTagsAsMap(osmEntity);
      
      /*...exist, so station relates to those transfer zones (platforms), meaning that it is NOT a standalone station and we do not
       * have to explicitly create platforms/transfer zones for it.*/
      for(TransferZone transferZone : potentialTransferZones) {
        
        /* verify if compatible with modes */
        
        
        Set<TransferZoneGroup> transferZoneGroups = transferZone.getTransferZoneGroups();
        if(transferZoneGroups!=null && !transferZoneGroups.isEmpty()) {
          /* part of stop_area -> process as such */
          for(TransferZoneGroup group : transferZoneGroups) {
            LOGGER.info(String.format("Salvaged: dangling station %d mapped to stop_area %s",osmEntity.getId(), group.getExternalId()));
            processTransferZoneGroupMemberStation(group, osmEntity, OsmModelUtil.getTagsAsMap(osmEntity));
          }              
        }                     
      }
      
    }
    
  }  
  
  /**
   * process any remaining unprocessed stations that are not part of any stop_area. This means the station reflects both a transfer zone and an
   * implicit stop_position at the nearest viable node 
   */
  private void extractRemainingNonStopAreaStations() {
      
    /* Ptv1 node station */
    if(!getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Node).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Node).values());
      unprocessedStations.forEach( (node) -> 
      { 
        extractNonStopAreaStation(node, PlanitOsmNodeUtils.createBoundingBox((OsmNode)node,getSettings().getStationToWaitingAreaSearchRadiusMeters(), this.geoUtils));
        getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.STATION);
      });
    }
    /* Ptv1 way station */
    if(!getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Way).isEmpty()) {
      Set<OsmEntity> unprocessedStations = new HashSet<OsmEntity>(getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Way).values());
      unprocessedStations.forEach( (way) -> 
      { 
        extractNonStopAreaStation(way, PlanitOsmWayUtils.createBoundingBox((OsmWay)way, getSettings().getStationToWaitingAreaSearchRadiusMeters(), getNetworkToZoningData().getOsmNodes(), this.geoUtils));
        getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.STATION);        
      });                  
    }     
    if(!getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED Ptv2 (node) STATIONS REMAIN -> TODO",getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).size()));
    }  
    if(!getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED Ptv2 (way) STATIONS REMAIN -> TODO",getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).size()));
    }       
  }
  
  private void extractRemainingNonStopAreaStopPositions() {
    Set<Long> unprocessedStopPositions = getZoningReaderData().getUnprocessedPtv2StopPositions();
    if(!unprocessedStopPositions.isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED STOP_POSITIONS REMAIN -> TODO",unprocessedStopPositions.size()));
    }
  }   
  
  /**
   * all transfer zones that were created without connectoids AND were found to not be linked to any stop_positions in a stop_area will still have no connectoids.
   * Connectoids need to be created based on implicit stop_position of vehicles which by OSM standards is defined as based on the nearest node. This is what we will do here.
   */
  private void extractRemainingNonStopAreaTransferZones() {
    if(!getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Node).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED (node) PLATFORMS REMAIN -> TODO",getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Node).size()));
    }  
    if(!getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Way).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED (way) PLATFORMS REMAIN -> TODO",getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Way).size()));
    } 
  }  
  
  /**
   * Process the remaining unprocessed Osm entities that were marked for processing, or were identified as not having explicit stop_position, i.e., no connectoids have yet been created. 
   * Now that we have successfully parsed all relations (stop_areas) and removed Osm entities part of relations, the remaining entities are guaranteed to not be part of any relation and require
   * stand-alone treatment to finalise them. 
   */
  private void extractRemainingNonStopAreaEntities() {
                
    /* unproccessed stations -> create transfer zone and connectoids (implicit stop_positions)*/
    extractRemainingNonStopAreaStations();
        
    /* unprocessed stop_positions -> ? */
    extractRemainingNonStopAreaStopPositions();
    
    /* transfer zones without connectoids, i.e., implicit stop_positions not part of any stop_area, --> create connectoids */
    extractRemainingNonStopAreaTransferZones();    
  }  
  
  /** extract a regular Ptv2 stop position that is part of a stop_area relation and is registered before as a stop_position in the main processing phase. 
   * Extract based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param osmNode node that is the stop_position in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @return 
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> extractKnownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {  
    Collection<TransferZone> matchedTransferZones = null;
        
    /* supported modes */
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmNode.getId(), tags, null);    
    Set<Mode> accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
    
    /* if at least one mapped mode is present continue */
    if(accessModes!=null && !accessModes.isEmpty()) {        
      matchedTransferZones = findAccessibleTransferZonesForPtv2StopPosition(osmNode, tags, transferZoneGroup.getTransferZones());
      
      if( (matchedTransferZones == null || matchedTransferZones.isEmpty()) && OsmPtv1Tags.isTramStop(tags)) {
        /* no match found, neither based on references, name, or geographically. Last possible option is that the stop_position is not only a stop_position, but also
         * the station/platform at the same time. This should not happen with Ptv2 tags. However in case a Ptv1 tag for a tram_stop is supplemented with a Ptv2 stop_position 
         * tag we get exactly this situation. The parser assumed a valid Ptv2 tagging and ignored the Ptv1 tag. However, it was only a valid Ptv1 tag and incomplete Ptv2 stop_area.
         * Since we can only detect this now, we must now identify this special situation and parse the tram_stop appropriately by creating a transfer zone for it before continuing
         * with the connectoids (technically this is a tagging error, we are just trying to fix it while parsing) */
        LOGGER.fine(String.format("Identified Ptv1 tram_stop (%d) that is also tagged as Ptv2 public_transport=stop_location, yet without Ptv2 platforms in the stop_area, parsing as Ptv1 entity instead", osmNode.getId()));
        TransferZone foundTransferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.PLATFORM, OsmRailModeTags.TRAM);
        if(foundTransferZone != null) {
          transferZoneGroup.addTransferZone(foundTransferZone);
          matchedTransferZones = Collections.singleton(foundTransferZone);
        }
      }
      
      if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
        /* still no match, issue warning */
        LOGGER.severe(String.format("Stop position %d has no valid pole, platform, station reference, nor closeby infrastructure that qualifies as such, ignored",osmNode.getId()));
      }else {               
      /* connectoids */
        extractDirectedConnectoids(osmNode, tags, matchedTransferZones, accessModes, transferZoneGroup);
      }      
            
    }                  
      
    return matchedTransferZones;
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation but not yet identified in the regular phase of parsing as a stop_position. Hence it is not properly
   * tagged. In this method we try to salvage it by inferring its properties (eligible modes). We do so, by mapping it to the nearest transfer zone available on the stop_area (if any)
   *  as a last resort attempt
   * 
   * @param osmNode node of unknown stop position
   * @param tags of the node
   * @param transferZoneGroup the group this stop position is part of
   * @throws PlanItException thrown if error
   */
  private Collection<TransferZone> extractUnknownPtv2StopAreaStopPosition(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = null;
    Set<Mode> accessModes = null;
    
    /* not a proper stop_position, so we must infer its properties (eligible modes). We do so, by mapping it to the nearest transfer zone available on the stop_area (if any)
     *  as a last resort attempt */
    TransferZone foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneWithClosestCoordinateToNode(osmNode, transferZoneGroup.getTransferZones(), getSettings().getStopToWaitingAreaSearchRadiusMeters(), geoUtils);
    if(foundZone == null) {
      LOGGER.warning(String.format("Discard: stop_position %d without proper tagging on OSM network could not be mapped to closeby transfer zone in stop_area", osmNode.getId()));
    }else {        
      Collection<String> eligibleOsmModes = getEligibleOsmModesForTransferZone(foundZone);
      accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
      if(accessModes == null) {
        LOGGER.warning(String.format("Discard: stop_position %d without proper tagging on OSM network, unable to identify access modes from closest transfer zone in stop_area", osmNode.getId()));
      }else {
        matchedTransferZones = Collections.singleton(foundZone);
      }
    }
    
    if(accessModes != null){               
      /* connectoids */
      LOGGER.warning(String.format("Salvaged: stop_position %d without proper tagging on OSM network, matched to closest transfer zone with osm id %s instead", osmNode.getId(), foundZone.getExternalId()));
      extractDirectedConnectoids(osmNode, tags, matchedTransferZones, accessModes, transferZoneGroup);
    }    
    
    return matchedTransferZones;
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param member member in stop_area relation
   * @param transferZoneGroup the group this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaStopPosition(final OsmRelationMember member, final TransferZoneGroup transferZoneGroup) throws PlanItException {
    PlanItException.throwIfNull(member, "Stop_area stop_position member null");
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_POSITION);
    
    /* only proceed when not user excluded or when marked as invalid earlier */
    if(getSettings().isExcludedStopPosition(member.getId()) || getZoningReaderData().isInvalidStopAreaStopPosition(member.getType(), member.getId())) {
      return;
    }      
    
    /* validate state and input */
    if(member.getType() != EntityType.Node) {
      throw new PlanItException("Stop_position %d encountered that it not an OSM node, this is not permitted",member.getId());
    }      
               
    Boolean isKnownPtv2StopPosition = null; 
    if(getZoningReaderData().getUnprocessedPtv2StopPositions().contains(member.getId())){
      /* registered as unprocessed --> known and available for processing */
      isKnownPtv2StopPosition = true;      
    }else if(getZoningReaderData().hasAnyDirectedConnectoidsForOsmNodeId(member.getId())) {
      /* already processed by an earlier relation --> stop_position resides in multiple stop_areas, this is strongly discouraged by OSM, but does occur still so we identify and skip (no need to process twice anyway)*/
      LOGGER.warning(String.format("Encountered stop_position %d in stop_area %s that has been processed by earlier stop_area already, skip",member.getId(), transferZoneGroup.getExternalId()));
      return;
    }else {
      /* stop-position not processed and not know, so it is not properly tagged and we must infer mode access from infrastructure it resides on to salvage it */      
      LOGGER.fine(String.format("Stop_position %d in stop_area not marked as such on OSM node, inferring transfer zone and access modes by geographically closest transfer zone in stop_area instead ",member.getId()));
      isKnownPtv2StopPosition = false;
    }    
    
    /* stop location via Osm node */
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);    
    if(isKnownPtv2StopPosition) {
      
      /* process as regular stop_position */
      extractKnownPtv2StopAreaStopPosition(osmNode, tags, transferZoneGroup);
      /* mark as processed */
      getZoningReaderData().getUnprocessedPtv2StopPositions().remove(osmNode.getId());
      
    }else {
      
      /* unknown, so node is not tagged properly, try to salvage */
      extractUnknownPtv2StopAreaStopPosition(osmNode, tags, transferZoneGroup);
            
    }
    
  }  
  
  /** extract stop area relation of Ptv2 scheme. We create connectoids for all now already present transfer zones.
   * 
   * @param osmRelation to extract stop_area for
   * @param tags of the stop_area relation
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopAreaStopPositions(OsmRelation osmRelation, Map<String, String> tags) throws PlanItException{
  
    /* transfer zone group */
    TransferZoneGroup transferZoneGroup = getZoningReaderData().getTransferZoneGroupByOsmId(osmRelation.getId());
    if(transferZoneGroup == null) {
      LOGGER.severe(String.format("found stop_area %d in post-processing for which not PLANit transfer zone group has been created, this should not happen",osmRelation.getId()));
    }
        
    /* process only stop_positions */
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
            
      /* stop_position */
      if(member.getRole().equals(OsmPtv2Tags.STOP_ROLE)) {        
        
        extractPtv2StopAreaStopPosition(member, transferZoneGroup);       
        
      }

    }     
       
  }  
  
 
  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param hanlderData the handler data gathered by preceding handlers for zoning parsing
   * @param network2ZoningData data collated from parsing network required to successfully popualte the zoning
   * @param zoningToPopulate to populate
   * @param profiler to use
   * are not of interest and would otherwise be discarded 
   */
  public PlanitOsmZoningPostProcessingHandler(
      final PlanitOsmTransferSettings transferSettings, 
      final PlanitOsmZoningReaderData handlerData, 
      final PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {
    super(transferSettings, handlerData, network2ZoningData, zoningToPopulate, profiler);
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsUtils(network2ZoningData.getOsmNetwork().getCoordinateReferenceSystem());
    
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    reset();
    
    PlanItException.throwIf(
        getNetworkToZoningData().getOsmNetwork().infrastructureLayers == null || getNetworkToZoningData().getOsmNetwork().infrastructureLayers.size()<=0,
          "network is expected to be populated at start of parsing OSM zoning");
  }  

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmRelation);          
    try {              
      
      /* only parse when parser is active and type is available */
      if(getSettings().isParserActive() && tags.containsKey(OsmRelationTypeTags.TYPE)) {
        
        /* public transport type */
        if(tags.get(OsmRelationTypeTags.TYPE).equals(OsmRelationTypeTags.PUBLIC_TRANSPORT)) {
          
          /* stop_area: stop_positions only */
          if(OsmPtv2Tags.hasPublicTransportKeyTag(tags) && tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT).equals(OsmPtv2Tags.STOP_AREA)) {
                        
            extractPtv2StopAreaStopPositions(osmRelation, tags);
            
          }else {
            /* anything else is not expected */
            LOGGER.info(String.format("Unknown public_transport relation %s encountered for relation %d, ignored",tags.get(OsmPtv2Tags.PUBLIC_TRANSPORT), osmRelation.getId()));          
          }          
          
        }
        
      }      
      
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM relation (id:%d) for transfer infrastructure", osmRelation.getId())); 
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {         
    
    /* process remaining unprocessed entities that are not part of a relation (stop_area) */
    extractRemainingNonStopAreaEntities();
    
    /* log stats */
    getProfiler().logPostProcessingStats(getZoning());
    
    LOGGER.info(" OSM (transfer) zone post-processing ...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {  
    // nothing yet
  }
  
}

package org.planit.osm.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
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
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
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
   * 
   * @param tags to search for reference keys in
   * @param availableTransferZones to choose from
   * @return found transfer zones that have been parsed before, null if no match is found
   */
  private Collection<TransferZone> findTransferZonesByTagReference(Map<String, String> tags, Collection<TransferZone> availableTransferZones) {
    Set<TransferZone> foundTransferZones = null;
    
    /* ref value, can be a list of multiple values */
    String refValue = OsmTagUtils.getValueForSupportedRefKeys(tags);
    if(refValue != null) {
      String[] transferZoneRefValues = StringUtils.splitByAnythingExceptAlphaNumeric(refValue);
      for(int index=0; index < transferZoneRefValues.length; ++index) {
        String localRefValue = transferZoneRefValues[index];
        boolean refValueFound = false;
        for(TransferZone transferZone : availableTransferZones) {
          Object refProperty = transferZone.getInputProperty(OsmTags.REF);
          if(refProperty != null && localRefValue.equals(String.class.cast(refProperty))) {
            /* match */
            if(!refValueFound) {
              refValueFound = true;
              if(foundTransferZones==null) {
                foundTransferZones = new HashSet<TransferZone>();
              }              
            }else {
              LOGGER.fine(String.format("referenced platform/pole %s found multiple times",transferZone.getExternalId()));
            }
            
            foundTransferZones.add(transferZone);            
          }
        }
      }
    }
    return foundTransferZones;
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
  private TransferZone findMostLikelyTransferZoneSpatially(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    TransferZone foundZone = null;
    
    /* collect potential transfer zones based on spatial search*/
    double searchRadiusMeters = getSettings().getStopToWaitingAreaSearchRadiusMeters();
    Envelope searchArea = geoUtils.createBoundingBox(osmNode.getLongitude(),osmNode.getLatitude(), searchRadiusMeters);
    Collection<TransferZone> potentialTransferZones = getZoningReaderData().getTransferZonesWithoutConnectoid(searchArea);
    
    if(potentialTransferZones==null || potentialTransferZones.isEmpty()) {
      LOGGER.warning(String.format("unable to locate nearby transfer zone (search radius of %.2f (m)) when mapping stop position for osm node %d, ignored",searchRadiusMeters, osmNode));
      return null;
    }
        
    /* Ideally we relate via explicit references available on Osm tags */
    /* Occurs when: platform (zone) exists but is not included in stop_area. 
     * Note: This indicates poor tagging, yet occurs in reality, e.g. Sydney, circular quay for example */
    Collection<TransferZone> matchedTransferZones = findTransferZonesByTagReference(tags, potentialTransferZones);
    if(matchedTransferZones != null) {
      if(matchedTransferZones.size()>1) {
        LOGGER.warning(String.format("found multiple transfer zones with reference to osm node %d based on search radius of %.2f (m), choosing closest match",osmNode,searchRadiusMeters));
        foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneWithClosestCoordinateToNode(osmNode, matchedTransferZones, geoUtils);        
      }else {
        foundZone = matchedTransferZones.iterator().next();
      }
    }    
    
    if(foundZone == null) {
      /* no explicit reference or name match is found, we collect the closest by potential match */
      foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneWithClosestCoordinateToNode(osmNode, potentialTransferZones, geoUtils);
      if(foundZone == null) {
        LOGGER.warning(String.format("unable to match osm node %d to any existing transfer zone within search radius of %.2f (m), ignored",osmNode,searchRadiusMeters));
      }
    }
        
    return foundZone;
  }
    
  
  /** create and/or update directed connectoids for the transfer zones and mode combinations when eligible, based on the passed in osm node 
   * where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param tags to use
   * @param transferZones connectoids are assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @throws PlanItException thrown if error
   */
  private void extractDirectedConnectoids(OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> transferZones, Set<Mode> planitModes) throws PlanItException {
    
    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    for(Mode planitMode : planitModes) {
      /* layer */
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(planitMode);

      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) */
        extractDirectedConnectoidsForMode(osmNode, tags, transferZone, networkLayer, planitMode);
      }      
    }
  }    
  
  /** find the transfer zones that are accessible to the stop_position on the given node
   * 
   * @param osmNode node represting the stop_position
   * @param tags of the node
   * @param stopAreaTransferzones the transfer zones of the stop_area this stop_position belongs to
   * @throws PlanItException thrown if error
   */  
  private Collection<TransferZone> findAccessibleTransferZonesForStopPosition(OsmNode osmNode, Map<String, String> tags, Collection<TransferZone> stopAreaTransferZones) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = null;
        
    /* reference to platform, i.e. transfer zone */
    matchedTransferZones = findTransferZonesByTagReference(tags, stopAreaTransferZones);    
    if(matchedTransferZones == null || matchedTransferZones.isEmpty() && tags.containsKey(OsmTags.NAME)) {
      /* try matching names, this should only result in single match */
      matchedTransferZones = findTransferZoneMatchByName(osmNode.getId(),tags.get(OsmTags.NAME), stopAreaTransferZones);
      if(matchedTransferZones!= null && matchedTransferZones.size()>1) {
        /* multiple match(es) found, find most likely spatially from this subset*/
        TransferZone foundTransferZone =  (TransferZone) PlanitOsmNodeUtils.findZoneWithClosestCoordinateToNode(osmNode, matchedTransferZones, geoUtils);
        matchedTransferZones = Collections.singleton(foundTransferZone);
      }      
    }
          
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* no matches found by name or reference tag, so it has no reference, or transfer zone is not part of the area. Either way 
       * we must still try to find the most likely match. We do so geographically as our last resort! */
      TransferZone foundTransferZone = findMostLikelyTransferZoneSpatially(osmNode, tags);
      matchedTransferZones = Collections.singleton(foundTransferZone);
    }
    
    if(matchedTransferZones == null || matchedTransferZones.isEmpty()) {
      /* still no match, issue warning */
      LOGGER.severe(String.format("stop position %d has no valid pole, platform, station reference, nor closeby infrastructure that qualifies as such, ignored",osmNode.getId()));
      return null;
    }
    
    return matchedTransferZones;
  }  
  
  /**
   * process any remaining unprocessed stations that are not part of any stop_area. This means the station reflects both a transfer zone and an
   * implicit stop_position at the nearest viable node 
   */
  private void processUnprocessedStationsWithImplicitStopPositions() {
      
    if(!getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Node).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED Ptv1 (node) STATIONS REMAIN -> TODO",getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Node).size()));
    }  
    if(!getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Way).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED Ptv1 (way) STATIONS REMAIN -> TODO",getZoningReaderData().getUnprocessedPtv1Stations(EntityType.Way).size()));
    }     
    if(!getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED Ptv2 (node) STATIONS REMAIN -> TODO",getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Node).size()));
    }  
    if(!getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED Ptv2 (way) STATIONS REMAIN -> TODO",getZoningReaderData().getUnprocessedPtv2Stations(EntityType.Way).size()));
    }       
  }
  
  private void processUnprocessedStopPositions() {
    Set<Long> unprocessedStopPositions = getZoningReaderData().getUnprocessedPtv2StopPositions();
    if(!unprocessedStopPositions.isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED STOP_POSITIONS REMAIN -> TODO",unprocessedStopPositions.size()));
    }
  }   
  
  /**
   * all transfer zones that were created without connectoids AND were found to not be linked to any stop_positions in a stop_area will still have no connectoids.
   * Connectoids need to be created based on implicit stop_position of vehicles which by OSM standards is defined as based on the nearest node. This is what we will do here.
   */
  private void processTransferZonesWithImplicitStopPositions() {
    if(!getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Node).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED (node) PLATFORMS REMAIN -> TODO",getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Node).size()));
    }  
    if(!getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Way).isEmpty()) {
      LOGGER.info(String.format("%d UNPROCESSED (way) PLATFORMS REMAIN -> TODO",getZoningReaderData().getTransferZonesWithoutConnectoid(EntityType.Way).size()));
    } 
  }  
  
  /**
   * Process the remaining unprocessed Osm entities that were marked for processing, or were identified as not having explicit stop_position, i.e., no connectoids have yet been created. 
   * Now that we have successfully parsed all relations and removed Osm entities part of relations, the remaining entities are guaranteed to not be part of any relation and require
   * stand-alone treatment to finalise them. 
   */
  private void processUnprocessed() {
                
    /* unproccessed stations -> create transfer zone and connectoids (implicit stop_positions)*/
    processUnprocessedStationsWithImplicitStopPositions();
        
    /* unprocessed stop_positions -> ? */
    processUnprocessedStopPositions();
    
    /* transfer zones without connectoids, i.e., implicit stop_positions not part of any stop_area, --> create connectoids */
    processTransferZonesWithImplicitStopPositions();    
  }  
  
  /** extract a Ptv2 stop position part of a stop_area relation. Based on description in https://wiki.openstreetmap.org/wiki/Tag:public_transport%3Dstop_position
   * 
   * @param member member in stop_area relation
   * @param availableTransferZones the transfer zones this stop position is allowed to relate to
   * @throws PlanItException thrown if error
   */
  private void extractPtv2StopPosition(final OsmRelationMember member, final Collection<TransferZone> availableTransferZones) throws PlanItException {
    
    /* validate state and input */
    PlanItException.throwIfNull(member, "Stop_area stop_position member null");
    if(member.getType() != EntityType.Node) {
      throw new PlanItException("Stop_position encountered that it not an OSM node, this is not permitted");
    }
    getProfiler().incrementOsmPtv2TagCounter(OsmPtv2Tags.STOP_POSITION);    
           
    boolean isProperPtv2StopPosition = true;
    if(!getZoningReaderData().getUnprocessedPtv2StopPositions().contains(member.getId())){      
      /* stop-position not marked as such on the node, we must infer mode access from infrastructure it resides on */      
      LOGGER.info(String.format("Stop_position %d in stop_area not marked as such on OSM node, inferring transfer zone and access modes by geographically closest transfer zone in stop_area instead ",member.getId()));
      isProperPtv2StopPosition = false;
    }        
    
    /* stop location via Osm node */
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(member.getId());
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmNode);
    
    /* now find matched transfer zone(s) */
    Collection<TransferZone> matchedTransferZones = null;
    Set<Mode> accessModes = null;
    if(isProperPtv2StopPosition) {
      
      /* supported modes */
      Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmNode.getId(), tags, null);    
      accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
      
      /* if at least one mapped mode is present continue */
      if(accessModes!=null && !accessModes.isEmpty()) {        
        matchedTransferZones = findAccessibleTransferZonesForStopPosition(osmNode, tags, availableTransferZones);
      }            
    
      /* mark as processed */
      getZoningReaderData().getUnprocessedPtv2StopPositions().remove(osmNode.getId());
      
    }else {
      
      /* not a proper stop_position, so we must infer its properties (eligible modes). We do so, by mapping it to the nearest transfer zone available on the stop_area (if any)
       *  as a last resort attempt */
      TransferZone foundZone =  (TransferZone) PlanitOsmNodeUtils.findZoneWithClosestCoordinateToNode(osmNode, availableTransferZones, geoUtils);
      if(foundZone == null) {
        LOGGER.warning(String.format("Unable to find closest transfer zone in stop_area  to stop_position %d without proper tagging on OSM network, stop_position ignored", osmNode.getId()));
      }else {        
        Collection<String> eligibleOsmModes = getEligibleOsmModesForTransferZone(foundZone);
        accessModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
        if(accessModes == null) {
          LOGGER.warning(String.format("Unable to identify access modes from closest transfer zone in stop_area to stop_position %d that has no proper tagging on OSM network, stop_position ignored", osmNode.getId()));
        }else {
          matchedTransferZones = Collections.singleton(foundZone);
        }
      }
      
    }
         
    /* connectoids */
    if(matchedTransferZones != null) {
      extractDirectedConnectoids(osmNode, tags, matchedTransferZones, accessModes);
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
      if(member.getRole().equals(OsmPtv2Tags.STOP_POSITION_ROLE)) {        
        
        extractPtv2StopPosition(member, transferZoneGroup.getTransferZones());       
        
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
    processUnprocessed();
    
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

package org.planit.osm.handler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkReaderLayerData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.settings.zoning.PlanitOsmPublicTransportSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.DirectedVertex;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.TrackModeType;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;
import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Base Handler for all zoning handlers. Contains shared functionality that is used across the different zoning handlers 
 * 
 * @author markr
 * 
 *
 */
public abstract class PlanitOsmZoningBaseHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningBaseHandler.class.getCanonicalName());
          
  // references
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;
  
  /** the settings to adhere to regarding the parsing of PLAnit transfer infrastructure from OSM */
  private final PlanitOsmPublicTransportSettings transferSettings;   
  
  /** network2ZoningData data collated from parsing network required to successfully popualte the zoning */
  private final PlanitOsmNetworkToZoningReaderData network2ZoningData;
  
  /** holds, add to all the tracking data required for parsing zones */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** profiler for this reader */
  private final PlanitOsmZoningHandlerProfiler profiler;   
    
  /** Verify if passed in tags reflect transfer based infrastructure that is eligible (and supported) to be parsed by this class, e.g.
   * tags related to original PT scheme stops ( railway=halt, railway=tram_stop, highway=bus_stop and highway=platform),
   * or the current v2 PT scheme (public_transport=stop_position, platform, station, stop_area)
   * 
   * @param tags
   * @return which scheme it is compatible with, NONE if none could be found
   */
  private static OsmPtVersionScheme isTransferBasedOsmInfrastructure(Map<String, String> tags) {
    if(PlanitOsmUtils.isCompatibleWith(OsmPtVersionScheme.VERSION_2, tags)){
      return OsmPtVersionScheme.VERSION_2;
    }else if(PlanitOsmUtils.isCompatibleWith(OsmPtVersionScheme.VERSION_1,tags)) {
      return OsmPtVersionScheme.VERSION_1;
    }
    return OsmPtVersionScheme.NONE;
  }  
  
  /** skip osm pt entity when marked for exclusion in settings
   * 
   * @param type of entity to verify
   * @param osmId id to verify
   * @return true when it should be skipped, false otherwise
   */  
  private boolean skipOsmPtEntity(EntityType entityType, long osmId) {
    return 
        (entityType.equals(EntityType.Node) && getSettings().isExcludedOsmNode(osmId)) 
        ||
        (entityType.equals(EntityType.Way) && getSettings().isExcludedOsmWay(osmId)); 
  }    
  
  /** find out if link osmModesToCheck are compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode. When one allows for pseudo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param referenceOsmModes to map against (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  private boolean isModeCompatible(Collection<String> osmModesToCheck, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    /* collect compatible modes */
    Collection<String> overlappingModes = PlanitOsmModeUtils.getCompatibleModes(osmModesToCheck, referenceOsmModes, allowPseudoMatches);    
    
    /* only proceed when there is a valid mapping based on overlapping between reference modes and zone modes, while in absence
     * of reference osm modes, we trust any nearby zone with mapped mode */
    if(getNetworkToZoningData().getSettings().hasAnyMappedPlanitMode(overlappingModes)) {
      /* no overlapping mapped modes while both have explicit osm modes available, not a match */
      return true;
    }
    return false;    

  }   
  
  /** create a new but unpopulated transfer zone
   * 
   * @param transferZoneType of the zone
   * @return created transfer zone
   */
  private TransferZone createEmptyTransferZone(TransferZoneType transferZoneType) {
    /* create */
    TransferZone transferZone = getZoning().transferZones.createNew();
    /* type */
    transferZone.setType(transferZoneType);
    
    getProfiler().logTransferZoneStatus(getZoning().transferZones.size());
    return transferZone;
  }   
  
  /** create a transfer zone based on the passed in osm entity, tags for feature extraction and access. Note that we attempt to also
   * parse its reference tags. Currently we look for keys:
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   *  to parse the reference for a transfer zone. If other keys are used, we are not (yet) able to pick them up.
   * 
   * @param osmEntity entity that is to be converted into a transfer zone
   * @param tags tags to extract features from
   * @param transferZoneType the type of the transfer zone 
   * @return transfer zone created
   */
  private TransferZone createAndPopulateTransferZone(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType) {
    TransferZone transferZone = null;
    
    /* geometry, either centroid location or polygon circumference */
    Geometry theGeometry = PlanitOsmUtils.extractGeometry(osmEntity, getNetworkToZoningData().getOsmNodes());        
    if(theGeometry != null && !theGeometry.isEmpty()) {
    
      /* create */
      transferZone = createEmptyTransferZone(transferZoneType);
      transferZone.setGeometry(theGeometry); 
      if(theGeometry instanceof Point) {
        transferZone.getCentroid().setPosition((Point) theGeometry);
      }
      
      /* XML id = internal id*/
      transferZone.setXmlId(Long.toString(osmEntity.getId()));
      /* external id  = osm node id*/
      transferZone.setExternalId(transferZone.getXmlId());
      
      /* name */
      if(tags.containsKey(OsmTags.NAME)) {
        transferZone.setName(tags.get(OsmTags.NAME));
      }    
      
      String refValue = OsmTagUtils.getValueForSupportedRefKeys(tags);
      /* ref (to allow other entities to refer to this transfer zone locally) */
      if(refValue != null) {
        transferZone.addInputProperty(OsmTags.REF, refValue);
      }
      
    }else {
      LOGGER.warning(String.format("Transfer zone not created, geometry incomplete (polygon, line string) for osm way %s, possibly nodes outside bounding box, or invalid OSM entity",osmEntity.getId()));
    }
        
    return transferZone;
  }    
   
  
  /** create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @return transfer zone created, null if something happenned making it impossible to create the zone
   */
  private TransferZone createAndRegisterTransferZoneWithoutConnectoids(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType) {
    /* create and register */
    TransferZone transferZone = createAndPopulateTransferZone(osmEntity,tags, transferZoneType);
    if(transferZone != null) {
      getZoning().transferZones.register(transferZone);
      EntityType entityType = Osm4JUtils.getEntityType(osmEntity);
    
      /* register locally */
      getZoningReaderData().getPlanitData().addIncompleteTransferZone(entityType, osmEntity.getId(), transferZone);
    }
    return transferZone;
  }  
    
  /** extract a tram stop since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dtram_stop
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */
  private void extractPtv1TramStop(OsmNode osmNode, Map<String, String> tags) throws PlanItException {    
        
    /* in contrast to (normal) highway=bus_stop this tag is placed on the track, so we can and will create connectoids immediately */
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.TRAM_STOP);
    extractPtv1TransferZoneWithConnectoidsAtStopPosition(osmNode, tags, OsmRailModeTags.TRAM);
  }  
  
  /** extract a halt since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dhalt
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */  
  private void extractPtv1Halt(OsmNode osmNode, Map<String, String> tags) throws PlanItException {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.HALT);
        
    String expectedDefaultMode = OsmRailModeTags.TRAIN;
    if(hasNetworkLayersWithActiveOsmNode(osmNode.getId())) {
      
      extractPtv1TransferZoneWithConnectoidsAtStopPosition(osmNode, tags, expectedDefaultMode);
      
    }else {
      /* halt not on railway, just create transfer zone at this point */
      
      String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
      if(!defaultMode.equals(expectedDefaultMode)) {
        LOGGER.warning(String.format("Unexpected osm mode identified for Ptv1 halt %s",defaultMode));
      }
      createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, TransferZoneType.SMALL_STATION, defaultMode);      
    }
  }
    
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=bus_stop on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1HighwayBusStop(OsmEntity osmEntity, Map<String, String> tags) throws PlanItException {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 bus_stop %s,",defaultOsmMode));
    }      
    
    Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmEntity.getId(), tags, defaultOsmMode);
    if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {
      
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.BUS_STOP);      
      if(Osm4JUtils.getEntityType(osmEntity).equals(EntityType.Node) && hasNetworkLayersWithActiveOsmNode(osmEntity.getId())){
        
        /* bus_stop on the road and NO Ptv2 tags (or Ptv2 tags assessed and decided they should be ignored), create both transfer zone and connectoids immediately */
        OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(osmEntity.getId());
        createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmNode, tags, OsmRoadModeTags.BUS);
        
      }else {
        
        /* bus_stop not on the road, only create transfer zone (waiting area), postpone creation of stop_location */
        createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.POLE, modeResult.first());
      }
    }
  }   
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag railway=* for an OsmNode
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key railway=
   * @throws PlanItException thrown if error
   */  
  private void extractTransferInfrastructurePtv1Railway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {
    PlanitOsmNetworkSettings networkSettings = getNetworkToZoningData().getSettings();
    
    /* tram stop */
    if(OsmPtv1Tags.TRAM_STOP.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasMappedPlanitMode(OsmRailwayTags.TRAM)) {
      
      extractPtv1TramStop(osmNode, tags);
    }
    
    /* train platform */
    if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)) {
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      extractPtv1RailwayPlatform(osmNode, tags);
    }          
    
    /* train halt (not for trams)*/
    if(OsmPtv1Tags.HALT.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      
      /* assumed to never be part of a Ptv2 stop_area relation, so we parse immediately */
      extractPtv1Halt(osmNode, tags);
    }
    
    /* train station (not for trams) */
    if(OsmPtv1Tags.STATION.equals(ptv1ValueTag) && networkSettings.getRailwaySettings().hasAnyMappedPlanitModeOtherThan(OsmRailwayTags.TRAM)) {
      /* stations of the Ptv1 variety are often part of Ptv2 stop_areas and sometimes even more than one Ptv1 station exists within the single stop_area
       * therefore, we can only distinguish between these situations after parsing the stop_area_relations. If after parsing stop_areas, stations identified here remain, i.e.,
       * are not part of a stop_area, then we can parse them as Ptv1 stations. So for now, we track them and postpone the parsing */
      getZoningReaderData().getOsmData().addUnprocessedPtv1Station(osmNode);
    }    
  }  
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=* on an osmNode (no Ptv2 tags)
   * 
   * @param osmNode the node to extract
   * @param tags all tags of the osm Node
   * @param ptv1ValueTag the value tag going with key highway=
   * @throws PlanItException thrown if error
   */
  private void extractTransferInfrastructurePtv1Highway(OsmNode osmNode, Map<String, String> tags, String ptv1ValueTag) throws PlanItException {       
      
    /* bus stop -> create transfer zone */
    if(OsmPtv1Tags.BUS_STOP.equals(ptv1ValueTag)){
      
      extractTransferInfrastructurePtv1HighwayBusStop(osmNode, tags);
      
    }
    /* platform -> create transfer zone */
    else if(OsmPtv1Tags.PLATFORM.equals(ptv1ValueTag)){ 
      
      extractTransferInfrastructurePtv1HighwayPlatform(osmNode, tags);
      
    }else {
      LOGGER.warning(String.format("unsupported Ptv1 higway=%s tag encountered, ignored",ptv1ValueTag));
    }
  }   
    
  
  /** skip osm relation member when marked for exclusion in settings
   * 
   * @param member to verify
   * @return true when it should be skipped, false otherwise
   */
  protected boolean skipOsmPtEntity(OsmRelationMember member) {
    return skipOsmPtEntity(member.getType(), member.getId()); 
  }  

  /** skip osm node when marked for exclusion in settings
   * 
   * @param osmNode to verify
   * @return true when it should be skipped, false otherwise
   */  
  protected boolean skipOsmNode(OsmNode osmNode) {
    return skipOsmPtEntity(EntityType.Node, osmNode.getId());
  }
  
  /** skip osm way when marked for exclusion in settings
   * 
   * @param osmWay to verify
   * @return true when it should be skipped, false otherwise
   */  
  protected boolean skipOsmWay(OsmWay osmWay) {
    return skipOsmPtEntity(EntityType.Way, osmWay.getId());
  }   
  
  /** collect the eligible modes both osm and mapped planit modes. If no default mode is found based on the tags, a default mode may be 
   * provided explicitly by the user which will then be added to the osm mode
   * 
   * @param osmEntityId to use
   * @param tags of the osm entity
   * @return pair containing eligible osm modes identified and their mapped planit counterparts
   */
  public Pair<Collection<String>, Collection<Mode>> collectEligibleModes(long osmEntityId, Map<String, String> tags, String defaultMode) {
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmEntityId, tags, defaultMode);
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);      
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a planit link
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) throws PlanItException {    
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(osmNodeId);
    if(osmNode != null) {
      for(InfrastructureLayer networkLayer : network2ZoningData.getOsmNetwork().infrastructureLayers) {        
        if(getNetworkToZoningData().getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }   
  
  /** find out if transfer zone is mode compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode.If the zone has no known modes, it is by definition not mode compatible. When one allows for psuedo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param transferZone to verify
   * @param referenceOsmModes to macth against
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  protected boolean isTransferZoneModeCompatible(TransferZone transferZone, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Collection<String> transferZoneSupportedModes = PlanitOsmHandlerHelper.getEligibleOsmModesForTransferZone(transferZone);
    if(transferZoneSupportedModes==null) {       
      /* zone has no known modes, not a trustworthy match */ 
      return false;
    } 
    
    /* check mode compatibility on extracted transfer zone supported modes*/
    return isModeCompatible(transferZoneSupportedModes, referenceOsmModes, allowPseudoMatches);    
  }
  
  /** create a subset of transfer zones from the passed in ones, removing all transfer zones for which we can be certain they are located on the wrong side of the road infrastructure.
   * This is verified by checking if the stop_location resides on a one-way link. If so, we can be sure (based on the driving direction of the country) if a transfer zone is located on
   * the near or far side of the road, i.e., do people have to cross the road to egt to the stop position. If so, it is not eligible and we remove it, otherwise we keep it.
   * 
   * @param osmNode representing the stop location
   * @param transferZones to create subset for
   * @param osmModes eligible for the stop
   * @param geoUtils to use
   * @return subset of transfer zones
   * @throws PlanItException thrown if error
   */
  protected Collection<TransferZone> removeTransferZonesOnWrongSideOfRoadOfStopLocation(OsmNode osmNode, Collection<TransferZone> transferZones, Collection<String> osmModes, PlanitJtsUtils geoUtils) throws PlanItException {
    Collection<TransferZone> matchedTransferZones = new HashSet<TransferZone>(transferZones);
    boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(getZoningReaderData().getCountryName());
    
    /* If stop_location is situated on a one way road, or only has one way roads as incoming and outgoing roads, we exclude the matches that lie on the wrong side of the road, i.e.,
     * would require passengers to cross the road to get to the stop position */
    osmModes = PlanitOsmModeUtils.getPublicTransportModesFrom(osmModes);
    for(String osmMode : osmModes) {
      Mode accessMode = getNetworkToZoningData().getSettings().getMappedPlanitMode(osmMode);
      if(accessMode==null) {
        continue;
      }
            
      /* remove all link's that are not reachable without experiencing cross-traffic */
      for(TransferZone transferZone : transferZones) { 
        if(isTransferZoneOnWrongSideOfRoadOfStopLocation(PlanitOsmNodeUtils.createPoint(osmNode),transferZone, isLeftHandDrive, accessMode, geoUtils)) {
          LOGGER.fine(String.format(
              "DISCARD: Platform/pole %s matched on name to stop_position %d, but discarded based on placement on the wrong side of the road",transferZone.getExternalId(), osmNode.getId()));
          matchedTransferZones.remove(transferZone);
        }
      }
    }
    
    return matchedTransferZones;
  }
  
  /** Verify based on the stop_position location that is assumed to be located on earlier parsed road infrastructure, if the transfer zone is located
   * on an eligible side of the road. Meaning that the closest experienced driving direction of the nearby road is the logical one, i.e., when
   * transfer zone is on the left the closest driving direction should be left hand drive and vice versa.
   * 
   * @param location representation stop_location
   * @param transferZone representing waiting area
   * @param isLeftHandDrive is driving direction left hand drive
   * @param accessMode to verify
   * @param geoUtils to use
   * @return true when not on the wrong side, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean isTransferZoneOnWrongSideOfRoadOfStopLocation(Point location, TransferZone transferZone, boolean isLeftHandDrive, Mode accessMode, PlanitJtsUtils geoUtils) throws PlanItException {
    
    /* first collect links that can access the connectoid location */
    Collection<Link> planitLinksToCheck = getLinksWithAccessToConnectoidLocation(location, accessMode);
        
    /* remove all link's that are not reachable without experiencing cross-traffic from the perspective of the transfer zone*/
    if(planitLinksToCheck!=null){
      Collection<Link> accessibleLinks = PlanitOsmHandlerHelper.removeLinksOnWrongSideOf(transferZone.getGeometry(), planitLinksToCheck, isLeftHandDrive, Collections.singleton(accessMode), geoUtils);
      if(accessibleLinks==null || accessibleLinks.isEmpty()) {
        /* all links experience cross-traffic, so not reachable */
        return true;
      }
    }
    
    /* reachable, not on wrong side */
    return false;    
    
  }    
  
  /** Find links that can access the stop_location by the given mode. if location is on extreme node, we provide all links attached, otherwise only the
   * link on which the location resides
   * 
   * @param location stop_location
   * @param accessMode for stop_location (not used for filteraing accessibility, only for lyaer identification)
   * @return links that can access the stop location.
   * @throws PlanItException
   */
  protected Collection<Link> getLinksWithAccessToConnectoidLocation(Point location, Mode accessMode) throws PlanItException {
    /* If stop_location is situated on a one way road, or only has one way roads as incoming and outgoing roads, we identify if the eligible link segments 
     * lie on the wrong side of the road, i.e., would require passengers to cross the road to get to the stop position */
    MacroscopicPhysicalNetwork networkLayer = getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(accessMode);
    PlanitOsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
    OsmNode osmNode =  layerData.getOsmNodeByLocation(location);
    
    /* links that can reach stop_location */
    Collection<Link> planitLinksToCheck = null;
    Node planitNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getPlanitNodeByLocation(location);
    if(planitNode != null) {        
      /* not internal to planit link, so regular match to planit node --> consider all incoming link segments as potentially usable  */
      planitLinksToCheck = planitNode.<Link>getLinks();              
    }else {      
      /* not an extreme node, must be a node internal to a link up until now --> consider only link in question the location resides on */ 
      planitLinksToCheck = getNetworkToZoningData().getNetworkLayerData(networkLayer).findPlanitLinksWithInternalLocation(location);  
      if(planitLinksToCheck!=null){
        if(planitLinksToCheck.size()>1) {
          throw new PlanItException("location is internal to multiple planit links, should not happen %s", osmNode!=null ? "osm node "+osmNode.getId() : "");  
        }                             
      }
    }
    return planitLinksToCheck;
  }

  /** find out if link is mode compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode. If the zone has no known modes, it is by definition not mode compatible. 
   * When one allows for pseudo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param referenceOsmModes to map agains (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  protected boolean isLinkModeCompatible(Link link, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Collection<String> osmLinkModes = new HashSet<String>(); 
    if(link.hasEdgeSegmentAb()) {      
      Collection<Mode> planitModes = ((MacroscopicLinkSegment)link.getEdgeSegmentAb()).getLinkSegmentType().getAvailableModes();
      osmLinkModes.addAll(getNetworkToZoningData().getSettings().getMappedOsmModes(planitModes));
    }
    if(link.hasEdgeSegmentBa()) {      
      Collection<Mode> planitModes = ((MacroscopicLinkSegment)link.getEdgeSegmentBa()).getLinkSegmentType().getAvailableModes();
      osmLinkModes.addAll(getNetworkToZoningData().getSettings().getMappedOsmModes(planitModes));
    }
    if(osmLinkModes==null || osmLinkModes.isEmpty()) {
      return false;
    }
    
    /* check mode compatibility on extracted link supported modes*/
    return isModeCompatible(osmLinkModes, referenceOsmModes, allowPseudoMatches);
  }  
                                                          

  /** verify if tags represent an infrastructure used for transfers between modes, for example PT platforms, stops, etc. 
   * and is also activated for parsing based on the related settings
   * 
   * @param tags to verify
   * @return which scheme it is compatible with, NONE if none could be found or if it is not active 
   */  
  protected OsmPtVersionScheme isActivatedTransferBasedInfrastructure(Map<String, String> tags) {
    if(transferSettings.isParserActive()) {
      return isTransferBasedOsmInfrastructure(tags);
    }
    return OsmPtVersionScheme.NONE;
  }  
  
   
  /** update an existing directed connectoid with new access zone and allowed modes. In case the link segment does not have any of the 
   * passed in modes listed as allowed, the connectoid is not updated with these modes for the given access zone as it would not be possible to utilise it. 
   * 
   * @param accessZone to relate connectoids to
   * @param allowedModes to add to the connectoid for the given access zone
   */  
  protected void updateDirectedConnectoid(DirectedConnectoid connectoidToUpdate, TransferZone accessZone, Set<Mode> allowedModes) {    
    final Set<Mode> realAllowedModes = ((MacroscopicLinkSegment)connectoidToUpdate.getAccessLinkSegment()).getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      if(!connectoidToUpdate.hasAccessZone(accessZone)) {
        connectoidToUpdate.addAccessZone(accessZone);
      }
      connectoidToUpdate.addAllowedModes(accessZone, realAllowedModes);   
    }
  }  
  
  /** create a dummy transfer zone without access to underlying osmNode or way and without any geometry or populated
   * content other than its ids and type
   * 
   * @param osmId to use
   * @param transferZoneType to use
   * @return created transfer zone
   */
  protected TransferZone createDummyTransferZone(long osmId, TransferZoneType transferZoneType) {
    TransferZone transferZone = createEmptyTransferZone(transferZoneType);
    transferZone.setXmlId(String.valueOf(osmId));
    transferZone.setExternalId(transferZone.getXmlId());
    return transferZone;
  }   
    
  /** attempt to create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside. Find eligible access modes as input properties as well which can be used later
   * to map stop_positions more easily. Note that one can provide a default osm mode that is deemed eligible in case no tags are provided on the osm entity. In case no mode information
   * can be extracted a warning is issued but the transfer zone is still created because this is a tagging error and we might be able to salvage later on. If there are osm modes
   * but none of them are mapped, then we should not create the zone since it will not be of use.
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param defaultOsmMode to apply
   * @return transfer zone created, null if something happened making it impossible or not useful to create the zone
   */
  protected TransferZone createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, String defaultOsmMode) {  
    
    TransferZone transferZone = null;
    
    /* tagged osm modes */        
    Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmEntity.getId(), tags, defaultOsmMode);
    if(!PlanitOsmHandlerHelper.hasEligibleOsmMode(modeResult)) {
      /* no information on modes --> tagging issue, transfer zone might still be needed and could be salvaged based on close by stop_positions with additional information 
       * log issue, yet still create transfer zone (without any osm modes) */
      LOGGER.fine(String.format("SALVAGED: Transfer zone of type %s found for osm entity %d without osm mode support, likely tagging mistake",transferZoneType.name(), osmEntity.getId()));
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType);
    }else if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)){  
      /* mapped planit modes are available and we should create the transfer zone*/
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType);
      PlanitOsmHandlerHelper.addOsmAccessModesToTransferZone(transferZone, modeResult.first());
    }  
    return transferZone;    
  }  
  
  /** attempt to create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside. Register the provided access modes as eligible by setting them on the input properties 
   * which can be used later to map stop_positions more easily. Note that one can provide a default osm mode that is deemed eligible in case no tags are provided on the osm entity
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param defaultOsmMode to apply
   * @return transfer zone created, null if something happenned making it impossible to create the zone
   */
  protected TransferZone createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, Collection<String> eligibleOsmModes) {
    TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM);
    if(transferZone != null) {
      PlanitOsmHandlerHelper.addOsmAccessModesToTransferZone(transferZone, eligibleOsmModes);
    }
    return transferZone;
  }   

  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the osm node. This is only relevant for very specific types
   * of osm pt nodes, such as tram_stop, some bus_stops that are tagged on the road, and potentially halts and/or stations
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultOsmMode that is to be expected here
   * @return created transfer zone (if not already in existence)
   * @throws PlanItException thrown if error
   */
  protected TransferZone createAndRegisterTransferZoneWithConnectoidsAtOsmNode(OsmNode osmNode, Map<String, String> tags, String defaultOsmMode) throws PlanItException {
    /* eligible modes */
    String foundDefaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(defaultOsmMode!=null && !foundDefaultMode.equals(defaultOsmMode)) {
      LOGGER.warning(String.format("unexpected default osm mode %s identified for Ptv1 osm node %d, overwrite with %s,",foundDefaultMode, osmNode.getId(), defaultOsmMode));
    }    
        
    Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmNode.getId(), tags, defaultOsmMode);
    if(!PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {    
      throw new PlanItException("Should not attempt to parse osm node %d when no planit modes are activated for it", osmNode.getId());
    }
  
    /* transfer zone */
    TransferZone transferZone = getZoningReaderData().getPlanitData().getIncompleteTransferZonesByEntityType(EntityType.Node).get(osmNode.getId());
    if(transferZone == null) {
      /* not created for other layer; create and register transfer zone */
      transferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, PlanitOsmHandlerHelper.getPtv1TransferZoneType(osmNode, tags), defaultOsmMode);
      if(transferZone == null) {
        throw new PlanItException("Unable to create transfer zone for osm node %d",osmNode.getId());
      }
    }
    
    /* connectoid(s) */
    for(Mode mode : modeResult.second()) {
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) getNetworkToZoningData().getOsmNetwork().infrastructureLayers.get(mode);             
      
      /* we can immediately create connectoids since Ptv1 tram stop is placed on tracks and no Ptv2 tag is present */
      /* railway generally has no direction, so create connectoid for both incoming directions (if present), so we can service any tram line using the tracks */        
      createAndRegisterDirectedConnectoidsOnTopOfTransferZone(transferZone, networkLayer, mode);      
    }    
    /* connectoids created, mark transfer zone as complete */
    getZoningReaderData().getPlanitData().removeIncompleteTransferZone(EntityType.Node, osmNode.getId());
    
    return transferZone;
  }

  /** create directed connectoid for the link segment provided, all related to the given transfer zone and with access modes provided. When the link segment does not have any of the 
   * passed in modes listed as allowed, no connectoid is created and null is returned
   * 
   * @param accessZone to relate connectoids to
   * @param linkSegment to create connectoid for
   * @param allowedModes used for the connectoid
   * @return created connectoid when at least one of the allowed modes is also allowed on the link segment
   * @throws PlanItException thrown if error
   */
  protected DirectedConnectoid createAndRegisterDirectedConnectoid(final TransferZone accessZone, final MacroscopicLinkSegment linkSegment, final Set<Mode> allowedModes) throws PlanItException {
    final Set<Mode> realAllowedModes = linkSegment.getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      DirectedConnectoid connectoid = zoning.connectoids.registerNew(linkSegment,accessZone);
      /* link connectoid to zone and register modes for access*/
      connectoid.addAllowedModes(accessZone, realAllowedModes);   
      return connectoid;
    }
    return null;
  }   
  
  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with access modes provided. connectoids are only created
   * when the access link segment has at least one of the allowed modes as an eligible mode
   * 
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @return created connectoids
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(final TransferZone transferZone, final MacroscopicPhysicalNetwork networkLayer, final Collection<EdgeSegment> linkSegments, final Set<Mode> allowedModes) throws PlanItException {
    Set<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(EdgeSegment linkSegment : linkSegments) {
      DirectedConnectoid newConnectoid = createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes);
      if(newConnectoid != null) {
        createdConnectoids.add(newConnectoid);
        
        /* update planit data tracking information */ 
        /* 1) index by access link segment's downstream node location */
        zoningReaderData.getPlanitData().addDirectedConnectoidByLocation(networkLayer, newConnectoid.getAccessLinkSegment().getDownstreamVertex().getPosition() ,newConnectoid);
        /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
        zoningReaderData.getPlanitData().addConnectoidByTransferZone(transferZone, newConnectoid);
      }
    }         
    
    return createdConnectoids;
  }
  
  /** Create directed connectoids for transfer zones that reside on osw ways. For such transfer zones, we simply create connectoids in both directions for all eligible incoming 
   * link segments. This is a special case because due to residing on the osm way it is not possible to distinguish what intended direction of the osm way is serviced (it is neither
   * left nor right of the way). Therefore any attempt to extract this information is bypassed here.
   * 
   * @param transferZone residing on an osm way
   * @param networkLayer related to the mode
   * @param planitMode the connectoid is accessible for
   * @return created connectoids, null if it was not possible to create any due to some reason
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoidsOnTopOfTransferZone(TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode) throws PlanItException {
    /* collect the osmNode for this transfer zone */
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(Long.valueOf(transferZone.getExternalId()));
    
    /* create/collect planit node with access link segment */
    Node planitNode = extractConnectoidAccessNodeByOsmNode(osmNode, networkLayer);
    if(planitNode == null) {
      LOGGER.warning(String.format("DISCARD: osm node (%d) could not be converted to access node for transfer zone osm entity %s at same location",osmNode.getId(), transferZone.getExternalId()));
      return null;
    }
    
    /* connectoid(s) */
        
    /* create connectoids on top of transfer zone */
    /* since located on osm way we cannot deduce direction of the stop, so create connectoid for both incoming directions (if present), so we can service any line using the way */        
    return createAndRegisterDirectedConnectoids(transferZone, networkLayer, planitNode.getEntryLinkSegments(), Collections.singleton(planitMode));    
  }  
  
  /** process an osm entity that is classified as a (train) station. For this to register on the group, we only see if we can utilise its name and use it for the group, but only
   * if the group does not already have a name
   *   
   * @param transferZoneGroup the osm station relates to 
   * @param osmEntityStation of the relation to process
   * @param tags of the osm entity representation a station
   */
  protected void updateTransferZoneGroupStationName(TransferZoneGroup transferZoneGroup, OsmEntity osmEntityStation, Map<String, String> tags) {
    
    if(!transferZoneGroup.hasName()) {
      String stationName = tags.get(OsmTags.NAME);
      if(stationName!=null) {
        transferZoneGroup.setName(stationName);
      }
    }
      
  }  
  
  /** process an osm entity that is classified as a (train) station. For this to register on the transfer zone, we try to utilise its name and use it for the zone
   * name if it is empty. We also record it as an input property for future reference, e.g. key=station and value the name of the osm station
   *   
   * @param transferZone the osm station relates to 
   * @param tags of the osm entity representation a station
   */  
  protected void updateTransferZoneStationName(TransferZone transferZone, Map<String, String> tags) {
    
    String stationName = tags.get(OsmTags.NAME);
    if(!transferZone.hasName()) {      
      if(stationName!=null) {
        transferZone.setName(stationName);
      }
    }
    /* only set when not already set, because when already set it is likely the existing station name is more accurate */
    if(!PlanitOsmHandlerHelper.hasTransferZoneStationName(transferZone)) {
      PlanitOsmHandlerHelper.setTransferZoneStationName(transferZone, stationName);
    }
  }      
  
  /** break a planit link at the planit node location while also updating all osm related tracking indices and/or planit network link and link segment reference 
   * that might be affected by this process:
   * <ul>
   * <li>tracking of osmways with multiple planit links</li>
   * <li>connectoid access link segments affected by breaking of link (if any)</li>
   * </ul>
   * 
   * @param planitNode to break link at
   * @param networkLayer the node and link(s) reside on
   * @param linksToBreak the links to break 
   * @throws PlanItException thrown if error
   */
  protected void breakLinksAtPlanitNode(Node planitNode, MacroscopicPhysicalNetwork networkLayer, List<Link> linksToBreak) throws PlanItException {
    PlanitOsmNetworkReaderLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);

    /* track original combinations of linksegment/downstream vertex for each connectoid possibly affected by the links we're about to break link (segments) 
     * if after breaking links this relation is modified, restore it by updating the connectoid to the correct access link segment directly upstream of the original 
     * downstream vertex identified */
    Map<DirectedConnectoid,DirectedVertex> connectoidsAccessLinkSegmentVerticesBeforeBreakLink = PlanitOsmHandlerHelper.collectAccessLinkSegmentDownstreamVerticesForConnectoids(
        linksToBreak, getZoningReaderData().getPlanitData().getDirectedConnectoidsByLocation(networkLayer));
    
    /* LOCAL TRACKING DATA CONSISTENCY  - BEFORE */    
    {      
      /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links exist with smaller geometries... insert those after as replacements*/
      getZoningReaderData().getPlanitData().removeLinksFromSpatialLinkIndex(linksToBreak); 
    }    
          
    /* break links */
    Map<Long, Set<Link>> newlyBrokenLinks = PlanitOsmHandlerHelper.breakLinksWithInternalNode(planitNode, linksToBreak, networkLayer, network2ZoningData.getOsmNetwork().getCoordinateReferenceSystem());
    
    /* in case due to breaking links the access link segments no longer represent the link segment directly upstream of the original vertex (downstream of the access link segment
     * before breaking the links, this method will update the directed connectoids to undo this and update their access link segments where needed */
    PlanitOsmHandlerHelper.updateAccessLinkSegmentsForDirectedConnectoids(connectoidsAccessLinkSegmentVerticesBeforeBreakLink);    

    /* TRACKING DATA CONSISTENCY - AFTER */
    {
      /* insert created/updated links and their geometries to spatial index instead */
      newlyBrokenLinks.forEach( (id, links) -> {getZoningReaderData().getPlanitData().addLinksToSpatialLinkIndex(links);});
                    
      /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to be able to find the correct planit links for (internal) osm nodes */
      layerData.updateOsmWaysWithMultiplePlanitLinks(newlyBrokenLinks);                            
    }
          
  }  
  
  /** extract the connectoid access node based on the given location. Either it already exists as a planit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the planit node
   *
   * @param osmNodeLocation to collect/create planit node for 
   * @param networkLayer to extract node on
   * @return planit node collected/created
   * @throws PlanItException thrown if error
   */  
  protected Node extractConnectoidAccessNodeByLocation(Point osmNodeLocation, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
    final PlanitOsmNetworkReaderLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
    
    /* check if already exists */
    Node planitNode = layerData.getPlanitNodeByLocation(osmNodeLocation);
    if(planitNode == null) {
      /* does not exist yet...create */
      
      /* find the links with the location registered as internal */
      List<Link> linksToBreak = layerData.findPlanitLinksWithInternalLocation(osmNodeLocation);
      if(linksToBreak != null) {
      
        /* location is internal to an existing link, create it based on osm node if possible, otherwise base it solely on location provided*/
        OsmNode osmNode = layerData.getOsmNodeByLocation(osmNodeLocation);
        if(osmNode != null) {
          /* all regular cases */
          planitNode = PlanitOsmHandlerHelper.createPlanitNodeForConnectoidAccess(osmNode, layerData, networkLayer);
        }else {
          /* special cases whenever parser decided that location required planit node even though there exists no osm node at this location */ 
          planitNode = PlanitOsmHandlerHelper.createPlanitNodeForConnectoidAccess(osmNodeLocation, layerData, networkLayer);
        }
        getProfiler().logConnectoidStatus(getZoning().connectoids.size());
                             
        /* now perform the breaking of links at the given node and update related tracking/reference information to broken link(segment)(s) where needed */
        breakLinksAtPlanitNode(planitNode, networkLayer, linksToBreak);
      }
    }
    return planitNode;
  }  
  
  /** extract the connectoid access node. either it already exists as a planit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the planit node
   * 
   * @param osmNode to collect planit node version for
   * @param networkLayer to extract node on
   * @return planit node collected/created
   * @throws PlanItException thrown if error
   */
  protected Node extractConnectoidAccessNodeByOsmNode(OsmNode osmNode, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {        
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);    
    return extractConnectoidAccessNodeByLocation(osmNodeLocation, networkLayer);
  }      

  /** create and/or update directed connectoids for the given mode and layer based on the passed in location where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone. Generally a single connectoid is created for the most likely link segment identified, i.e., if the transfer
   * zone is placed on the left of the infrastructure, the closest by incoming link segment to the given location is used. Since the geometry of a link applies to both link segments
   * we define closest based on the driving position of the country, so a left-hand drive country will use the incoming link segment where the transfer zone is placed on the left, etc. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param networkLayer this connectoid resides on
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode, PlanitJtsUtils geoUtils) throws PlanItException {    
    if(location == null || transferZone == null || networkLayer == null || planitMode == null || geoUtils == null) {
      return false;
    }
    
    OsmNode osmNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(location);
    
    /* road based modes must stop with the waiting area in the driving direction, i.e., must avoid cross traffic, because otherwise they 
     * have no doors at the right side, e.g., travellers have to cross the road to get to the vehicle, which should not happen... */
    boolean mustAvoidCrossingTraffic = true;
    if(planitMode.getPhysicalFeatures().getTrackType().equals(TrackModeType.RAIL)) {
      /* ... exception 1: train platforms because trains have doros on both sides */
      mustAvoidCrossingTraffic = false;
    }else if( osmNode != null && getSettings().isOverwriteStopLocationWaitingArea(osmNode.getId())) {
      /* ... exception 2: user override with mapping to this zone for this node */
      mustAvoidCrossingTraffic = Long.valueOf(transferZone.getExternalId()) == getSettings().getOverwrittenStopLocationWaitingArea(osmNode.getId()).second();
    }
    
    /* planit access node */
    Node planitNode = extractConnectoidAccessNodeByLocation(location, networkLayer);    
    if(planitNode==null) {
      if(osmNode != null) {
        LOGGER.warning(String.format("DISCARD: osm node %d could not be converted to access node for transfer zone representation of osm entity %s",osmNode.getId(), transferZone.getXmlId(), transferZone.getExternalId()));
      }else {
        LOGGER.warning(String.format("DISCARD: location (%s) could not be converted to access node for transfer zone representation of osm entity %s",location.toString(), transferZone.getXmlId(), transferZone.getExternalId()));
      }
      return false;
    }               
           
    /* accessible link segments for planit node based on location, mode availability, and if explicit mapping is forced or not */
    boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(getZoningReaderData().getCountryName());            
    Collection<EdgeSegment> accessLinkSegments = PlanitOsmHandlerHelper.findAccessibleLinkSegmentsForTransferZoneAtConnectoidLocation(planitNode, transferZone, planitMode, isLeftHandDrive, mustAvoidCrossingTraffic, geoUtils);
    if(accessLinkSegments == null || accessLinkSegments.isEmpty()) {
      LOGGER.warning(String.format("DISCARD: No accessible link segments found for platform/pole %s and mode %s at stop_position %s",transferZone.getExternalId(), planitMode.getExternalId(), planitNode.getExternalId()));
      return false;
    }
    
    /* update accessible link segments of already created connectoids (if any) */      
    if(zoningReaderData.getPlanitData().hasDirectedConnectoidForLocation(networkLayer, location)) {      
      
      /* existing connectoid: update model eligibility */
      Collection<DirectedConnectoid> connectoidsForNode = zoningReaderData.getPlanitData().getDirectedConnectoidsByLocation(location, networkLayer);        
      for(DirectedConnectoid connectoid : connectoidsForNode) {
        if(accessLinkSegments.contains(connectoid.getAccessLinkSegment())) {
          /* update mode eligibility */
          updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode));
          accessLinkSegments.remove(connectoid.getAccessLinkSegment());
        }
      }
    }
                  
    /* for remaining access link segments without connectoid -> create them */        
    if(!accessLinkSegments.isEmpty()) {
              
      /* create and register */
      Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, networkLayer, accessLinkSegments, Collections.singleton(planitMode));
      
      if(newConnectoids==null || newConnectoids.isEmpty()) {
        LOGGER.warning(String.format("Found eligible mode %s for stop_location of transferzone %s, but no access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
        return false;
      }
    }
    
    return true;
  }
  
  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node (location) where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param transferZone this connectoid is assumed to provide access to
   * @param networkLayer this connectoid resides on
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used to determine location of transfer zone relative to infrastructure to identify which link segment(s) are eligible for connectoids placement
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(OsmNode osmNode, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode, PlanitJtsUtils geoUtils) throws PlanItException {
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    return extractDirectedConnectoidsForMode(osmNodeLocation, transferZone, networkLayer, planitMode, geoUtils);
  }
  
  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the osm node, unless the user has overwritten the default behaviour
   * with a custom mapping of stop_location to waiting area. In that case, we mark the stop_position as unprocessed, because then it will be processed later in post processing where
   * the stop_position is converted into a connectoid and the appropriate user mapper waiting area (Transfer zone) is collected to match. 
   * This methodis only relevant for very specific types of osm pt nodes, such as tram_stop, some bus_stops, and potentially halts and/or stations, e.g., only when the
   * stop location and transfer zone are both tagged on the road for a Ptv1 tagged node.
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultMode that is to be expected here
   * @throws PlanItException thrown if error
   */  
  protected void extractPtv1TransferZoneWithConnectoidsAtStopPosition(OsmNode osmNode, Map<String, String> tags, String defaultMode) throws PlanItException {
    if(getSettings().isOverwriteStopLocationWaitingArea(osmNode.getId())) {       
      /* postpone processing of stop location when all transfer zones (waiting areas) have been created, but do mark this location as an unprocessed stop_position */
      getZoningReaderData().getOsmData().addUnprocessedPtv2StopPosition(osmNode.getId());
    }else {              
      /* In the special case a Ptv1 tag for a tram_stop, bus_stop, halt, or station is supplemented with a Ptv2 stop_position we must treat this as stop_position AND transfer zone in one and therefore 
       * create a transfer zone immediately */      
      createAndRegisterTransferZoneWithConnectoidsAtOsmNode(osmNode, tags, defaultMode);
    }
  }   
  
  /** extract a platform since it is deemed eligible for the planit network. Based on description in https://wiki.openstreetmap.org/wiki/Tag:railway%3Dplatform
   * 
   * @param osmEntity to extract from
   * @param tags all tags of the osm Node
   * @throws PlanItException thrown if error
   */    
  protected void extractPtv1RailwayPlatform(OsmEntity osmEntity, Map<String, String> tags) {
    getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
    
    /* node is not part of infrastructure, we must identify closest railway infrastructure (in reasonable range) to create connectoids, or
     * Ptv2 stop position reference is used, so postpone creating connectoids for now, and deal with it later when stop_positions have all been parsed */
    String defaultMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultMode.equals(OsmRailModeTags.TRAIN)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 railway platform %s,",defaultMode));
    }
    createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, defaultMode);
  }  
  
  /** Classic PT infrastructure based on original OSM public transport scheme, for the part related to the key tag highway=platform on an osmNode (no Ptv2 tags)
   * 
   * @param osmEntity the node to extract
   * @param tags all tags of the osm entity
   * @throws PlanItException thrown if error
   */  
  protected void extractTransferInfrastructurePtv1HighwayPlatform(OsmEntity osmEntity, Map<String, String> tags) {
    
    /* create transfer zone when at least one mode is supported */
    String defaultOsmMode = PlanitOsmModeUtils.identifyPtv1DefaultMode(tags);
    if(!defaultOsmMode.equals(OsmRoadModeTags.BUS)) {
      LOGGER.warning(String.format("unexpected osm mode identified for Ptv1 highway platform %s,",defaultOsmMode));
    }    

    Pair<Collection<String>, Collection<Mode>> modeResult = collectEligibleModes(osmEntity.getId(), tags, defaultOsmMode);
    if(PlanitOsmHandlerHelper.hasMappedPlanitMode(modeResult)) {               
      getProfiler().incrementOsmPtv1TagCounter(OsmPtv1Tags.PLATFORM);
      createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(osmEntity, tags, TransferZoneType.PLATFORM, modeResult.first());
    }
  }   
  
  
  /** Classic PT infrastructure based on original OSM public transport scheme (not Ptv2 tags) for osm node
   * 
   * @param osmNode to parse
   * @param tags of the node
   * @throws PlanItException thrown if error
   */
  protected void extractTransferInfrastructurePtv1(OsmNode osmNode, Map<String, String> tags) throws PlanItException {    
        
    if(OsmHighwayTags.hasHighwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmHighwayTags.HIGHWAY);
      extractTransferInfrastructurePtv1Highway(osmNode, tags, ptv1ValueTag);
      
    }else if(OsmRailwayTags.hasRailwayKeyTag(tags)) {
      
      String ptv1ValueTag = tags.get(OsmRailwayTags.RAILWAY);      
      extractTransferInfrastructurePtv1Railway(osmNode, tags, ptv1ValueTag);     
      
    }else {
      throw new PlanItException("parsing transfer infrastructure (Ptv1) for osm node %s, but no compatible key tags found",osmNode.getId());
    }  
        
  }    
    
  /** get profiler
   * @return profiler
   */
  protected final PlanitOsmZoningHandlerProfiler getProfiler() {
    return profiler;
  }
  
  /** get zoning reader data
   * @return data
   */
  protected final PlanitOsmZoningReaderData getZoningReaderData() {
    return this.zoningReaderData;
  }
  
  /** collect zoning
   * @return zoning;
   */
  protected final Zoning getZoning() {
    return this.zoning;
  }
  
  /** get network to zoning data
   * @return network to zoning data
   */
  protected final PlanitOsmNetworkToZoningReaderData getNetworkToZoningData() {
    return this.network2ZoningData;
  }
  
  /** get settings
   * @return settings
   */
  protected PlanitOsmPublicTransportSettings getSettings() {
    return this.transferSettings;
  }  
  
  /**
   * constructor
   * 
   * @param transferSettings for the handler
   * @param zoningReaderData gather data during parsing and utilise available data from pre-processing
   * @param network2ZoningData data collated from parsing network required to successfully popualte the zoning
   * @param zoningToPopulate to populate
   * @param profiler to keep track of created/parsed entities across zone handlers
   */
  public PlanitOsmZoningBaseHandler(
      final PlanitOsmPublicTransportSettings transferSettings, 
      PlanitOsmZoningReaderData zoningReaderData, 
      final PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      final Zoning zoningToPopulate,
      final PlanitOsmZoningHandlerProfiler profiler) {

    /* profiler */
    this.profiler = profiler;
    
    /* references */
    this.network2ZoningData = network2ZoningData;
    this.zoning = zoningToPopulate;       
    this.transferSettings = transferSettings;
    this.zoningReaderData = zoningReaderData;
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public abstract void initialiseBeforeParsing() throws PlanItException;
  
  /**
   * reset the contents, mainly to free up unused resources 
   */
  public abstract void reset();  
  

  
}

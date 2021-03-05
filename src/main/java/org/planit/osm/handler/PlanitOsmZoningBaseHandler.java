package org.planit.osm.handler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.reader.PlanitOsmNetworkLayerReaderData;
import org.planit.osm.converter.reader.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.converter.reader.PlanitOsmZoningReaderData;
import org.planit.osm.settings.zoning.PlanitOsmTransferSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.DirectedVertex;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.mode.Mode;
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
  private final PlanitOsmTransferSettings transferSettings;   
  
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
    Geometry theGeometry = null;
    boolean isNode = false;
    if(osmEntity instanceof OsmNode){
      isNode = true;
      OsmNode osmNode = OsmNode.class.cast(osmEntity);
      try {
        theGeometry = PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.getX(osmNode), PlanitOsmNodeUtils.getY(osmNode));
      } catch (PlanItException e) {
        LOGGER.severe(String.format("unable to construct location information for osm node %d when creating transfer zone", osmNode.getId()));
      }
    }else if(osmEntity instanceof OsmWay) {
      /* either area or linestring */
      OsmWay osmWay = OsmWay.class.cast(osmEntity);
      theGeometry = PlanitOsmWayUtils.extractGeometry(osmWay, getNetworkToZoningData().getOsmNodes());       
    }
    
    if(theGeometry != null && !theGeometry.isEmpty()) {
    
      /* create */
      transferZone = createEmptyTransferZone(transferZoneType);
      transferZone.setGeometry(theGeometry); 
      if(isNode) {
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
      getZoningReaderData().addTransferZoneWithoutConnectoid(entityType, osmEntity.getId(), transferZone);
    }
    return transferZone;
  }  
  
  /** to be able to retain the supported osm modes on a planit transfer zone, we place tham on the zone as an input property under this key.
   *  This avoids having to store all osm tags, while still allowing to leverage the information in the rare cases it is needed when this information is lacking
   *  on stop_positions that use this transfer zone
   */
  protected static final String TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY = "osmmodes";
  
  /** When known, transfer zones are provided with a station name extracted from the osm station entity (if possible). Its name is stored under
   * this key as input property
   */
  protected static final String TRANSFERZONE_STATION_INPUT_PROPERTY_KEY = "station";   
    
  /** while PLANit does not require access modes on transfer zones because it is handled by connectoids, OSM stop_positions (connectoids) might lack the required
   * tagging to identify their mode access in which case we revert to the related transfer zone to deduce it. Therefore, we store osm mode information on a transfer zone
   * via the generic input properties to be able to retrieve it if needed later
   * 
   * @param transferZone to use
   * @param eligibleOsmModes to add
   */
  protected static void addOsmAccessModesToTransferZone(final TransferZone transferZone, Collection<String> eligibleOsmModes) {
    if(transferZone != null && eligibleOsmModes!= null) {
      /* register identified eligible access modes */
      transferZone.addInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY, eligibleOsmModes);
    }
  }    
    
  /** collect any prior registered eligible osm modes on a Planit transfer zone (unmodifiable)
   * 
   * @param transferZone to collect from
   * @return eligible osm modes, null if none
   */
  @SuppressWarnings("unchecked")
  protected static Collection<String> getEligibleOsmModesForTransferZone(final TransferZone transferZone){
    Collection<String> eligibleOsmModes = (Collection<String>) transferZone.getInputProperty(TRANSFERZONE_SERVICED_OSM_MODES_INPUT_PROPERTY_KEY);
    if(eligibleOsmModes != null)
    {
      return Collections.unmodifiableCollection(eligibleOsmModes);
    }
    return null;
  }
  
  /** collect the station name for a transfer zone (if any)
   * @param transferZone to collect for
   * @return station name
   */
  protected static String getTransferZoneStationName(TransferZone transferZone) {
    return (String)transferZone.getInputProperty(TRANSFERZONE_STATION_INPUT_PROPERTY_KEY);
  }
  
  /** collect the station name for a transfer zone (if any)
   * @param transferZone to collect for
   * @return station name
   */
  protected static void  setTransferZoneStationName(TransferZone transferZone, String stationName) {
    transferZone.addInputProperty(TRANSFERZONE_STATION_INPUT_PROPERTY_KEY, stationName);
  }  
  
  /** Verify if the transfer zone has a station name set
   * @param transferZone to verify
   * @return true when present, false otherwise
   */  
  protected static boolean hasTransferZoneStationName(TransferZone transferZone) {
    return getTransferZoneStationName(transferZone) != null;
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
    Collection<String> transferZoneSupportedModes = getEligibleOsmModesForTransferZone(transferZone);
    if(transferZoneSupportedModes==null) {       
      /* zone has no known modes, not a trustworthy match */ 
      return false;
    } 
    
    /* check mode compatibility on extracted transfer zone supported modes*/
    return isModeCompatible(transferZoneSupportedModes, referenceOsmModes, allowPseudoMatches);    
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
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @return created connectoids
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(TransferZone transferZone, Set<EdgeSegment> linkSegments, Set<Mode> allowedModes) throws PlanItException {
    Set<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(EdgeSegment linkSegment : linkSegments) {
      DirectedConnectoid newConnectoid = createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes);
      if(newConnectoid != null) {
        createdConnectoids.add(newConnectoid);
      }
    } 
    return createdConnectoids;
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
    if(!hasTransferZoneStationName(transferZone)) {
      setTransferZoneStationName(transferZone, stationName);
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
    PlanitOsmNetworkLayerReaderData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
    
    /* track original combinations of linksegment/downstream vertex for each connectoid possibly affected by the links we're about to break link (segments) 
     * if after breaking links this relation is modified, restore it by updating the connectoid to the correct access link segment directly upstream of the original 
     * downstream vertex identified */
    Map<DirectedConnectoid,DirectedVertex> connectoidsAccessLinkSegmentVerticesBeforeBreakLink = 
        PlanitOsmHandlerHelper.collectAccessLinkSegmentDownstreamVerticesForConnectoids(linksToBreak, getZoningReaderData().getDirectedConnectoidsByLocation(networkLayer));
          
    /* break links */
    Map<Long, Set<Link>> newlyBrokenLinks = PlanitOsmHandlerHelper.breakLinksWithInternalNode(planitNode, linksToBreak, networkLayer, network2ZoningData.getOsmNetwork().getCoordinateReferenceSystem());
    /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to be able to find the correct planit links for (internal) osm nodes */
    layerData.updateOsmWaysWithMultiplePlanitLinks(newlyBrokenLinks);    
    
    /* in case due to breaking links the access link segments no longer represent the link segment directly upstream of the original vertex (downstream of the access link segment
     * before breaking the links, this method will update the directed connectoids to undo this and update their access link segments where needed */
    PlanitOsmHandlerHelper.updateAccessLinkSegmentsForDirectedConnectoids(connectoidsAccessLinkSegmentVerticesBeforeBreakLink);
          
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
    final PlanitOsmNetworkLayerReaderData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
    
    /* check if already exists */
    Node planitNode = layerData.getPlanitNodeByLocation(osmNodeLocation);
    if(planitNode == null) {
      /* does not exist yet...create */
      
      /* find the links with the location registered as internal */
      List<Link> linksToBreak = layerData.findPlanitLinksWithInternalLocation(osmNodeLocation);
      if(linksToBreak != null) {
      
        /* location is internal to an existing link, create it based on osm node if possible, otherwise base it solely on location provided*/
        OsmNode osmNode = layerData.getOsmNodeByPlanitNodeLocation(osmNodeLocation);
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
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param location to create the access point for as planit node
   * @param tags to use
   * @param transferZone this connectoid is assumed to provide access to
   * @param networkLayer this connectoid resides on
   * @param planitMode this connectoid is allowed access for
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(Point location, Map<String, String> tags, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode) throws PlanItException {
    boolean success = true;
    /* access node */
    Node planitNode = extractConnectoidAccessNodeByLocation(location, networkLayer);    
    if(planitNode==null) {
      LOGGER.warning(String.format("Discard: location (%s) could not be converted to access node for transfer zone representation of osm entity %s",location.toString(), transferZone.getXmlId(), transferZone.getExternalId()));
      success= false;
    }
    
    if(success) {
      /* already created connectoids */      
      if(zoningReaderData.hasDirectedConnectoidForLocation(networkLayer, location)) {      
        
        /* update: connectoid already exists */
        Set<DirectedConnectoid> connectoidsForNode = zoningReaderData.getDirectedConnectoidsByLocation(location, networkLayer);
        connectoidsForNode.forEach( connectoid -> updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode)));        
      }else {
        
        /* new connectoid, create and register */
        Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, planitNode.getEntryEdgeSegments(), Collections.singleton(planitMode));      
        if(newConnectoids==null || newConnectoids.isEmpty()) {
          LOGGER.warning(String.format("Found eligible mode %s for stop_location of transferzone %s, but no access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
          success = false;
        }else {
          newConnectoids.forEach( connectoid -> zoningReaderData.addDirectedConnectoidByLocation(networkLayer, location ,connectoid));
        }        
      }
    }
    
    return success;
  }
  
  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node (location) where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param tags to use
   * @param transferZone this connectoid is assumed to provide access to
   * @param networkLayer this connectoid resides on
   * @param planitMode this connectoid is allowed access for
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(OsmNode osmNode, Map<String, String> tags, TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode) throws PlanItException {
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    return extractDirectedConnectoidsForMode(osmNodeLocation, tags, transferZone, networkLayer, planitMode);
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
    Set<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(eligibleOsmModes == null || eligibleOsmModes.isEmpty()) {
      /* no information on modes --> tagging issue, transfer zone might still be needed and could be salvaged based on close by stop_positions with additional information 
       * log issue, yet still create transfer zone (without any osm modes) */
      LOGGER.fine(String.format("Salvaged: Transfer zone of type %s found for osm entity %d without osm mode support, likely tagging mistake",transferZoneType.name(), osmEntity.getId()));
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType);
    }else {  
      /* correctly tagged -> determine if any mapped planit modes are available and we should create the transfer zone at all */
      Set<Mode> planitModes = getNetworkToZoningData().getSettings().getMappedPlanitModes(eligibleOsmModes);
      if(planitModes != null && !planitModes.isEmpty()) {
        transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType);
        addOsmAccessModesToTransferZone(transferZone, eligibleOsmModes);
      }
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
      addOsmAccessModesToTransferZone(transferZone, eligibleOsmModes);
    }
    return transferZone;
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
  protected PlanitOsmTransferSettings getSettings() {
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
      final PlanitOsmTransferSettings transferSettings, 
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

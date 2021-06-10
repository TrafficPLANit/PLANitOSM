package org.planit.osm.converter.zoning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LinearLocation;
import org.planit.graph.listener.SyncDirectedEdgeXmlIdsToInternalIdOnBreakEdge;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkHandlerHelper;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderLayerData;
import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.graph.modifier.BreakEdgeListener;
import org.planit.utils.locale.DrivingDirectionDefaultByCountry;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.LinkSegment;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;
import org.planit.zoning.Zoning;
import org.planit.zoning.listener.UpdateConnectoidsOnBreakLink;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class to provide functionality for parsing PLANit connectoids from OSM entities
 * 
 * @author markr
 *
 */
public class PlanitOsmConnectoidParser {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmConnectoidParser.class.getCanonicalName());
  
  /** the zoning to work on */
  private final Zoning zoning;
  
  /** zoning reader data used to track created entities */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** settings to adhere to */
  private final PlanitOsmPublicTransportReaderSettings transferSettings;  
  
  /** information on parsed OSM network to utilise */
  private final PlanitOsmNetworkToZoningReaderData network2ZoningData;  
  
  /** track stats */
  private final PlanitOsmZoningHandlerProfiler profiler;
  
  /** utilities for geographic information */
  private final PlanitJtsCrsUtils geoUtils;   
  
  /** Verify if any valid access link segments exist for the given combination of link, on of its extreme nodes, and the access mode.
   * 
   * @param transferZone to check
   * @param accessLink nominated
   * @param node that is nominated
   * @param accessMode used
   * @return true when at least one valid access link segment exists, false otherwise
   * @throws PlanItException thrown if error
   */
  private boolean hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(TransferZone transferZone, Link accessLink, Node node, Mode accessMode) throws PlanItException {
    /* road based modes must stop with the waiting area in the driving direction, i.e., must avoid cross traffic, because otherwise they 
     * have no doors at the right side, e.g., travellers have to cross the road to get to the vehicle, which should not happen... */
    Long osmStopLocationId = node.getExternalId()!= null ? Long.valueOf(node.getExternalId()) : null;
    boolean mustAvoidCrossingTraffic = PlanitOsmZoningHandlerHelper.isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(accessMode, transferZone, osmStopLocationId, transferSettings);
    
    /* now collect the available access link segments (if any) - switch of logging of issues, since we are only interested in determining if this is feasible, we are not creating anything yet */    
    Collection<EdgeSegment> accessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(transferZone, accessLink, node, accessMode, mustAvoidCrossingTraffic, geoUtils);         
    
    return !accessLinkSegments.isEmpty();
  }


  /** Verify if the provided existing internal location of the link would be valid as access node with upstream access link segment if it were to
   * be used, i,e., if the link were to be broken at this point. Only in case the upstream link segment of this point is one-way and if extraoplated to the transfer zone
   * geometry would reside on the wrong side of it (for modes where this matters such as bus). Then this method will return false. In all other situation, e.g. two-way roads
   * or relative location of waitnig area is valid, or mode does not require a specific location relative to road (train), then it will return true
   * 
   * @param transferZone for which to check location
   * @param accessLink the location resides on
   * @param connectoidLocation to verify
   * @param accessMode for the location
   * @return true when deemed valid for the restrictions checked, false otherwise
   * @throws PlanItException thrown if error
   */
  private boolean hasStandAloneTransferZoneValidAccessLinkSegmentForLinkInternalLocationModeCombination(TransferZone transferZone, Link accessLink, Point connectoidLocation, Mode accessMode) throws PlanItException {
    
    MacroscopicPhysicalNetwork networkLayer = transferSettings.getReferenceNetwork().getInfrastructureLayerByMode(accessMode);
    OsmNode osmNode = network2ZoningData.getNetworkLayerData(networkLayer).getOsmNodeByLocation(connectoidLocation);
    Long osmStopLocationId = osmNode!= null ? osmNode.getId() : null;
    
    boolean mustAvoidCrossingTraffic = PlanitOsmZoningHandlerHelper.isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(accessMode, transferZone, osmStopLocationId, transferSettings);
    MacroscopicLinkSegment oneWayLinkSegment = PlanitOsmZoningHandlerHelper.getLinkSegmentIfLinkIsOneWayForMode(accessLink, accessMode);
    if(mustAvoidCrossingTraffic && oneWayLinkSegment != null) {
      /* special case: one way link and internal existing coordinate chosen. If the upstream geometry of this coordinate (when extrapolated to the waiting area)
       * is on the wrong side of the waiting area, it would be discarded, yet it might be that a projected location closest to the waiting area would be valid
       * due to a bend in the road in the downstream direction at this very coordinate. Hence, we only accept this existing coordinate when we are sure
       * it will not be discarded due to residing on the wrong side of the road infrastructure (when extrapolated) */
      Coordinate[] linkCoordinates = accessLink.getGeometry().getCoordinates();
      int coordinateIndex = PlanitJtsUtils.getCoordinateIndexOf(connectoidLocation.getCoordinate(), linkCoordinates);
      if(coordinateIndex <= 0) {
        throw new PlanItException("Unable to locate link internal location %s for osm way even though it is expected to exist for osm entity %s",accessLink.getExternalId(), transferZone.getExternalId());
      }
      
      LineSegment lineSegment = new LineSegment(linkCoordinates[coordinateIndex-1], linkCoordinates[coordinateIndex]);
      boolean reverseLinearLocationGeometry = oneWayLinkSegment.isDirectionAb()!=oneWayLinkSegment.getParentEdge().isGeometryInAbDirection();
      if(reverseLinearLocationGeometry) {
        lineSegment.reverse();
      }
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(zoningReaderData.getCountryName());
      return (isLeftHandDrive == PlanitOsmZoningHandlerHelper.isTransferZoneLeftOf(transferZone, lineSegment.p0, lineSegment.p1, geoUtils));
    }
    return true;
  }  
      

  /** Find the link segments that are accessible for the given acces link, node, mode combination taking into account the relative location of the transfer zone if needed and
   * mode compatibility.
   * 
   * @param transferZone these link segments pertain to
   * @param accessLink that is nominated
   * @param node extreme node of the link
   * @param accessMode eligible access mode
   * @param mustAvoidCrossingTraffic indicates of transfer zone must be on the logical side of the road or if it does not matter
   * @param geoUtils to use
   * @return found link segments that are deemed valid given the constraints
   * @throws PlanItException thrown if error
   */
  private Collection<EdgeSegment> findAccessLinkSegmentsForStandAloneTransferZone(
      TransferZone transferZone, Link accessLink, Node node, Mode accessMode, boolean mustAvoidCrossingTraffic, PlanitJtsCrsUtils geoUtils) throws PlanItException {
        
    Long osmWaitingAreaId = Long.valueOf(transferZone.getExternalId());
    Long osmNodeIdOfLinkExtremeNode = node.getExternalId()!= null ? Long.valueOf(node.getExternalId()) : null;
    EntityType osmWaitingAreaEntityType = PlanitOsmZoningHandlerHelper.getOsmEntityType(transferZone);
    
    /* potential link segments based on mode compatibility and access link restriction */ 
    Collection<EdgeSegment> accessLinkSegments = new ArrayList<EdgeSegment>(4);
    for(EdgeSegment linkSegment : node.getEntryEdgeSegments()) {
      if( ((MacroscopicLinkSegment)linkSegment).isModeAllowed(accessMode) && (linkSegment.getParentEdge().idEquals(accessLink))){      
        accessLinkSegments.add(linkSegment);
      }
    }  
    
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      return accessLinkSegments;
    }
        
    /* user overwrite checks and special treatment */
    boolean removeInvalidAccessLinkSegmentsIfNoMatchLeft = true;
    {
      /* in both cases: When a match, we must use the user overwrite value. We will still try to remove access link segments
       * that are invalid, but if due to this check no matches remain, we revert this and instead use all entry link segments on the osm way
       * since the user has indicated to explicitly use this combination which overrules the automatic filter we would ordinarily apply */
          
      /* stopLocation -> waiting area overwrite */
      if(node.getExternalId()!=null && transferSettings.isOverwriteStopLocationWaitingArea(osmNodeIdOfLinkExtremeNode)) {      
        Pair<EntityType, Long> result = transferSettings.getOverwrittenStopLocationWaitingArea(osmNodeIdOfLinkExtremeNode);      
        removeInvalidAccessLinkSegmentsIfNoMatchLeft = osmWaitingAreaId == result.second();
      }    
      /* waiting area -> osm way (stop_location) overwrite */
      else if (transferSettings.hasWaitingAreaNominatedOsmWayForStopLocation(osmWaitingAreaId, osmWaitingAreaEntityType)) {        
        long osmWayId = transferSettings.getWaitingAreaNominatedOsmWayForStopLocation(osmWaitingAreaId, osmWaitingAreaEntityType);
        removeInvalidAccessLinkSegmentsIfNoMatchLeft = !(Long.valueOf(accessLink.getExternalId()).equals(osmWayId));
      }
    }
  
    /* accessible link segments for planit node based on relative location of waiting area compared to infrastructure*/    
    if(mustAvoidCrossingTraffic) { 
                          
      boolean isLeftHandDrive = DrivingDirectionDefaultByCountry.isLeftHandDrive(zoningReaderData.getCountryName());
      Collection<EdgeSegment> toBeRemoveAccessLinkSegments = 
          PlanitOsmZoningHandlerHelper.identifyInvalidTransferZoneAccessLinkSegmentsBasedOnRelativeLocationToInfrastructure(accessLinkSegments, transferZone, accessMode, isLeftHandDrive, geoUtils);
      
      if(removeInvalidAccessLinkSegmentsIfNoMatchLeft || toBeRemoveAccessLinkSegments.size() < accessLinkSegments.size()) {
        /* filter because "normal" situation or there are still matches left even after filtering despite the explicit user override for this  combination */
        accessLinkSegments.removeAll(toBeRemoveAccessLinkSegments);
      }
      /* else  keep the access link segments to far */
    }
    return accessLinkSegments;
  }

  /** update an existing directed connectoid with new access zone and allowed modes. In case the link segment does not have any of the 
   * passed in modes listed as allowed, the connectoid is not updated with these modes for the given access zone as it would not be possible to utilise it. 
   * 
   * @param connectoidToUpdate to connectoid to update
   * @param accessZone to relate connectoids to
   * @param allowedModes to add to the connectoid for the given access zone
   */  
  private void updateDirectedConnectoid(DirectedConnectoid connectoidToUpdate, TransferZone accessZone, Set<Mode> allowedModes) {    
    final Set<Mode> realAllowedModes = ((MacroscopicLinkSegment)connectoidToUpdate.getAccessLinkSegment()).getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      if(!connectoidToUpdate.hasAccessZone(accessZone)) {
        connectoidToUpdate.addAccessZone(accessZone);
      }
      connectoidToUpdate.addAllowedModes(accessZone, realAllowedModes);   
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
  private void breakLinksAtPlanitNode(Node planitNode, MacroscopicPhysicalNetwork networkLayer, List<Link> linksToBreak) throws PlanItException {
    PlanitOsmNetworkReaderLayerData layerData = network2ZoningData.getNetworkLayerData(networkLayer);
  
    /* track original combinations of linksegment/downstream vertex for each connectoid possibly affected by the links we're about to break link (segments) 
     * if after breaking links this relation is modified, restore it by updating the connectoid to the correct access link segment directly upstream of the original 
     * downstream vertex identified */
    Map<Point, DirectedConnectoid> connectoidsAccessNodeLocationBeforeBreakLink = 
        PlanitOsmZoningHandlerHelper.collectConnectoidAccessNodeLocations(linksToBreak, zoningReaderData.getPlanitData().getDirectedConnectoidsByLocation(networkLayer));
    
    /* register additional actions on breaking link via callback listeners:
     * 1) connectoid update (see above)
     * 2) xml id update on links (and its link segments) by syncing to internal ids so they remain unique. 
     * 
     * 2) is needed when persisting based on planit xml ids which otherwise leads to problems due to duplicate xml ids when breaking links (only internal ids remain unique). 
     * Note that this syncing works because we create planit links such that initially xml ids are in sync with internal ids upon creation. If this is not the case we cannot 
     * guarantee uniqueness of xml ids using this method.
     */
    Set<BreakEdgeListener<Node, Link>> breakLinkListeners = 
        Set.of(
            new UpdateConnectoidsOnBreakLink<Node, Link, LinkSegment>(connectoidsAccessNodeLocationBeforeBreakLink),
            new SyncDirectedEdgeXmlIdsToInternalIdOnBreakEdge<Node, Link, LinkSegment>());
        
    /* LOCAL TRACKING DATA CONSISTENCY  - BEFORE */    
    {      
      /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links exist with smaller geometries... insert those after as replacements*/
      zoningReaderData.getPlanitData().removeLinksFromSpatialLinkIndex(linksToBreak); 
    }    
          
    /* break links */
    Map<Long, Set<Link>> newlyBrokenLinks = PlanitOsmNetworkHandlerHelper.breakLinksWithInternalNode(
        planitNode, linksToBreak, networkLayer, transferSettings.getReferenceNetwork().getCoordinateReferenceSystem(), breakLinkListeners);   
  
    /* TRACKING DATA CONSISTENCY - AFTER */
    {
      /* insert created/updated links and their geometries to spatial index instead */
      newlyBrokenLinks.forEach( (id, links) -> {zoningReaderData.getPlanitData().addLinksToSpatialLinkIndex(links);});
                    
      /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to be able to find the correct planit links for (internal) osm nodes */
      layerData.updateOsmWaysWithMultiplePlanitLinks(newlyBrokenLinks);                            
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
  private DirectedConnectoid createAndRegisterDirectedConnectoid(final TransferZone accessZone, final MacroscopicLinkSegment linkSegment, final Set<Mode> allowedModes) throws PlanItException {
    final Set<Mode> realAllowedModes = linkSegment.getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      DirectedConnectoid connectoid = zoning.transferConnectoids.registerNew(linkSegment,accessZone);
      
      /* xml id = internal id */
      connectoid.setXmlId(String.valueOf(connectoid.getId()));
      
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
  private Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(final TransferZone transferZone, final MacroscopicPhysicalNetwork networkLayer, final Collection<? extends EdgeSegment> linkSegments, final Set<Mode> allowedModes) throws PlanItException {
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

  private boolean extractDirectedConnectoidsForMode(TransferZone transferZone, Mode planitMode, Collection<EdgeSegment> eligibleLinkSegments, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(planitMode);
    
    for(EdgeSegment edgeSegment : eligibleLinkSegments) {
     
      /* update accessible link segments of already created connectoids (if any) */      
      Point proposedConnectoidLocation = edgeSegment.getDownstreamVertex().getPosition();
      boolean createConnectoidsForLinkSegment = true;
      
      if(zoningReaderData.getPlanitData().hasDirectedConnectoidForLocation(networkLayer, proposedConnectoidLocation)) {      
        /* existing connectoid: update model eligibility */
        Collection<DirectedConnectoid> connectoidsForNode = zoningReaderData.getPlanitData().getDirectedConnectoidsByLocation(proposedConnectoidLocation, networkLayer);        
        for(DirectedConnectoid connectoid : connectoidsForNode) {
          if(edgeSegment.idEquals(connectoid.getAccessLinkSegment())) {
            /* update mode eligibility */
            updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode));
            createConnectoidsForLinkSegment  = false;
            break;
          }
        }
      }
                    
      /* for remaining access link segments without connectoid -> create them */        
      if(createConnectoidsForLinkSegment) {
                
        /* create and register */
        Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, networkLayer, Collections.singleton(edgeSegment), Collections.singleton(planitMode));
        
        if(newConnectoids==null || newConnectoids.isEmpty()) {
          LOGGER.warning(String.format("Found eligible mode %s for stop_location of transferzone %s, but no access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
          return false;
        }
      }  
    }
    
    return true;
  }

  /** Identical to the same method with additional paramater containing link restrictions. Only by calling this method all entry link segments are considred whereas in the
   * more general version only link segments with a parent link in the provided link collection are considered. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  private boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    return extractDirectedConnectoidsForMode(location, transferZone, planitMode, null, geoUtils);
  }

  /** extract the connectoid access node based on the given location. Either it already exists as a planit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the planit node
   *
   * @param osmNodeLocation to collect/create planit node for 
   * @param networkLayer to extract node on
   * @return planit node collected/created
   * @throws PlanItException thrown if error
   */  
  private Node extractConnectoidAccessNodeByLocation(Point osmNodeLocation, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {
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
          planitNode = PlanitOsmZoningHandlerHelper.createPlanitNodeForConnectoidAccess(osmNode, layerData, networkLayer);
        }else {
          /* special cases whenever parser decided that location required planit node even though there exists no osm node at this location */ 
          planitNode = PlanitOsmZoningHandlerHelper.createPlanitNodeForConnectoidAccess(osmNodeLocation, layerData, networkLayer);
        }
        profiler.logConnectoidStatus(zoning.transferConnectoids.size());
                             
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
  private Node extractConnectoidAccessNodeByOsmNode(OsmNode osmNode, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {        
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);    
    return extractConnectoidAccessNodeByLocation(osmNodeLocation, networkLayer);
  }

  /** Constructor 
   * 
   * @param zoning to parse on
   * @param zoningReaderData to use
   * @param transferSettings to use
   * @param network2ZoningData to use
   * @param profiler to use
   */
  public PlanitOsmConnectoidParser(
      Zoning zoning, 
      PlanitOsmZoningReaderData zoningReaderData, 
      PlanitOsmPublicTransportReaderSettings transferSettings,
      PlanitOsmNetworkToZoningReaderData network2ZoningData, PlanitOsmZoningHandlerProfiler profiler) {

    this.zoning = zoning;
    this.zoningReaderData = zoningReaderData;
    this.transferSettings = transferSettings;
    this.network2ZoningData = network2ZoningData;
    this.profiler = profiler;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsCrsUtils(transferSettings.getReferenceNetwork().getCoordinateReferenceSystem());
  }
  
  /** find a suitable connectoid location on the given link based on the constraints that it must be able to reside on a linksegment that is in the correct relative position
   * to the transfer zone and supports the access mode on at least one of the designated link segment(s) that is eligible (if any). If not possible null is returned 
   *  
   * @param transferZone to find location for
   * @param accessLink to find location on
   * @param accessMode to be compatible with
   * @param maxAllowedStopToTransferZoneDistanceMeters the maximum allowed distance between stop and waiting area that we allow
   * @param networkLayer to use
   * @return found location either exisiting osm node or projected location that is nearest and does not exist as a shape point on the link yet, or null if no valid position could be found
   * @throws PlanItException thrown if error
   */
  public Point findConnectoidLocationForstandAloneTransferZoneOnLink(TransferZone transferZone, Link accessLink, Mode accessMode, double maxAllowedStopToTransferZoneDistanceMeters, MacroscopicPhysicalNetwork networkLayer) throws PlanItException {

    Coordinate closestExistingCoordinate = geoUtils.getClosestExistingLineStringCoordinateToGeometry(transferZone.getGeometry(), accessLink.getGeometry());
    double distanceToExistingCoordinateOnLinkInMeters = geoUtils.getClosestDistanceInMeters(PlanitJtsUtils.createPoint(closestExistingCoordinate), transferZone.getGeometry());        
    
    /* if close enough break at existing osm node to create stop_position/connectoid, otherwise create artificial non-osm node in closest projected location which
     * in most cases will be closer and within threshold */
    Point connectoidLocation = null;
    if(distanceToExistingCoordinateOnLinkInMeters < maxAllowedStopToTransferZoneDistanceMeters) {
      
      /* close enough, see if it can be reused: 
       * 1) node is an extreme node
       * 2) or node is internal to link
       * */
      
      /* 1) verify if extreme node */
      if(accessLink.getVertexA().isPositionEqual2D(closestExistingCoordinate)) {
        /* because it is an extreme node there is only one of the two directions accessible since an access link segments are assumed to be directly upstream of the node. This
         * can result in choosing a connectoid location that is not feasible when only considering the proximity and not the link segment specific information such as the mode
         * and relative location to the transfer zone (left or right of the road). Therefore we must check this here before accepting this pre-existing extreme node. If this is a problem,
         * we do not create the location on the existing location, but instead break the link so that we can use the access link segment in the opposite direction instead */
        if(hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(transferZone, accessLink, accessLink.getNodeA(), accessMode)) {       
          connectoidLocation = PlanitJtsUtils.createPoint(closestExistingCoordinate);
        }
      }else if(accessLink.getVertexB().isPositionEqual2D(closestExistingCoordinate)) {
          if(hasStandAloneTransferZoneValidAccessLinkSegmentForLinkNodeModeCombination(transferZone, accessLink, accessLink.getNodeB(), accessMode)){
            connectoidLocation = PlanitJtsUtils.createPoint(closestExistingCoordinate);  
          }        
      }else {
      
        /* 2) must be internal if not an extreme node */
        int coordinateIndex = PlanitJtsUtils.getCoordinateIndexOf(closestExistingCoordinate, accessLink.getGeometry().getCoordinates());
        if(coordinateIndex <= 0 || coordinateIndex==(accessLink.getGeometry().getCoordinates().length-1)) {
          throw new PlanItException("Unable to locate link internal osm node even though it is expected to exist when creating stop locations for osm entity %s",transferZone.getExternalId());
        }
        
        connectoidLocation = PlanitJtsUtils.createPoint(closestExistingCoordinate);
        if(!hasStandAloneTransferZoneValidAccessLinkSegmentForLinkInternalLocationModeCombination(transferZone, accessLink, connectoidLocation, accessMode)) {
          /* special case: if one way link and internal existing coordinate chosen results in waiting area on the wrong side of geometry (due to bend in the road directly
           * preceding the location (and mode is susceptible to waiting area location). Then we do not accept this existing coordinate and instead try
           * to use projected location not residing at this (possible) bend, but in between existing coordinates on straight section of road (hopefully), therefore
           * reset location and continue */
          connectoidLocation=null;
        }
      }
    }
     
    if(connectoidLocation == null) {
      /* too far, or identified existing location is not suitable, so we must break the existing link in appropriate location instead */
      LinearLocation projectedLinearLocationOnLink = PlanitOsmZoningHandlerHelper.getClosestProjectedLinearLocationOnEdgeForTransferZone(transferZone,accessLink, geoUtils);      
      
      /* verify projected location is valid */
      Coordinate closestProjectedCoordinate = projectedLinearLocationOnLink.getCoordinate(accessLink.getGeometry());
      if( closestExistingCoordinate.equals2D(closestProjectedCoordinate) || 
          geoUtils.getClosestDistanceInMeters(PlanitJtsUtils.createPoint(closestProjectedCoordinate), transferZone.getGeometry()) > maxAllowedStopToTransferZoneDistanceMeters) {
        /* no need to break link, the projected closest point is too far away or deemed not suitable */
      }else {
        connectoidLocation = PlanitJtsUtils.createPoint(closestProjectedCoordinate);
      }
    }
    
    return connectoidLocation;
  }   


  /** Create directed connectoids for transfer zones that reside on osw ways. For such transfer zones, we simply create connectoids in both directions for all eligible incoming 
   * link segments. This is a special case because due to residing on the osm way it is not possible to distinguish what intended direction of the osm way is serviced (it is neither
   * left nor right of the way). Therefore any attempt to extract this information is bypassed here.
   * 
   * @param transferZone residing on an osm way
   * @param networkLayer related to the mode
   * @param planitMode the connectoid is accessible for
   * @param geoUtils to use
   * @return created connectoids, null if it was not possible to create any due to some reason
   * @throws PlanItException thrown if error
   */
  public Collection<DirectedConnectoid> createAndRegisterDirectedConnectoidsOnTopOfTransferZone(
      TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* collect the osmNode for this transfer zone */
    OsmNode osmNode = network2ZoningData.getOsmNodes().get(Long.valueOf(transferZone.getExternalId()));
    
    Collection<? extends EdgeSegment> nominatedLinkSegments = null;
    if(transferSettings.hasWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node)) {
      /* user overwrite */
      
      long osmWayId = transferSettings.getWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node);
      Link nominatedLink = PlanitOsmZoningHandlerHelper.getClosestLinkWithOsmWayIdToGeometry( osmWayId, PlanitOsmNodeUtils.createPoint(osmNode), networkLayer, geoUtils);
      if(nominatedLink != null) {
        nominatedLinkSegments = nominatedLink.getEdgeSegments(); 
      }else {
        LOGGER.severe(String.format("User nominated osm way not available for waiting area on road infrastructure %d",osmWayId));
      }                      
      
    }else {
      /* regular approach */
      
      /* create/collect PLANit node with access link segment */
      Node planitNode = extractConnectoidAccessNodeByOsmNode(osmNode, networkLayer);
      if(planitNode == null) {
        LOGGER.warning(String.format("DISCARD: osm node (%d) could not be converted to access node for transfer zone osm entity %s at same location",osmNode.getId(), transferZone.getExternalId()));
        return null;
      }
      
      nominatedLinkSegments = planitNode.getEntryLinkSegments();
    }
    
    
    
    /* connectoid(s) */
        
    /* create connectoids on top of transfer zone */
    /* since located on osm way we cannot deduce direction of the stop, so create connectoid for both incoming directions (if present), so we can service any line using the way */        
    return createAndRegisterDirectedConnectoids(transferZone, networkLayer, nominatedLinkSegments, Collections.singleton(planitMode));    
  }


  /** create and/or update directed connectoids for the given mode and layer based on the passed in location where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone. Generally a single connectoid is created for the most likely link segment identified, i.e., if the transfer
   * zone is placed on the left of the infrastructure, the closest by incoming link segment to the given location is used. Since the geometry of a link applies to both link segments
   * we define closest based on the driving position of the country, so a left-hand drive country will use the incoming link segment where the transfer zone is placed on the left, etc. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param eligibleAccessLinks only links in this collection are considered when compatible with provided location
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  public boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, Mode planitMode, Collection<Link> eligibleAccessLinks, PlanitJtsCrsUtils geoUtils) throws PlanItException {    
    if(location == null || transferZone == null || planitMode == null || geoUtils == null) {
      return false;
    }
    
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(planitMode);
    OsmNode osmNode = network2ZoningData.getNetworkLayerData(networkLayer).getOsmNodeByLocation(location);                
    
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
    
    /* must avoid cross traffic when:
     * 1) stop position does not coincide with transfer zone, i.e., waiting area is not on the road/rail, and
     * 2) mode requires waiting area to be on a specific side of the road, e.g. buses can only open doors on one side, so it matters for them, but not for train
     */
    boolean mustAvoidCrossingTraffic = !planitNode.getPosition().equalsTopo(transferZone.getGeometry());
    if(mustAvoidCrossingTraffic) {
      mustAvoidCrossingTraffic = PlanitOsmZoningHandlerHelper.isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation( planitMode, transferZone, osmNode!= null ? osmNode.getId() : null, transferSettings);  
    }         
    
    /* find access link segments */
    Collection<EdgeSegment> accessLinkSegments = null;
    for(Link link : planitNode.<Link>getLinks()) {
      Collection<EdgeSegment> linkAccessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(transferZone,link, planitNode, planitMode, mustAvoidCrossingTraffic, geoUtils);
      if(linkAccessLinkSegments != null && !linkAccessLinkSegments.isEmpty()) {
        if(accessLinkSegments == null) {
          accessLinkSegments = linkAccessLinkSegments;
        }else {
          accessLinkSegments.addAll(linkAccessLinkSegments);
        }
      }
    }    
      
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      LOGGER.info(String.format("DICARD platform/pole/station %s its stop_location %s deemed invalid, no access link segment found due to incompatible modes or transfer zone on wrong side of road/rail", 
          transferZone.getExternalId(), planitNode.getExternalId()!= null ? planitNode.getExternalId(): ""));
      return false;
    }                           
    
    /* connectoids for link segments */
    return extractDirectedConnectoidsForMode(transferZone, planitMode, accessLinkSegments, geoUtils);
  }


  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node (location) where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used to determine location of transfer zone relative to infrastructure to identify which link segment(s) are eligible for connectoids placement
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  public boolean extractDirectedConnectoidsForMode(OsmNode osmNode, TransferZone transferZone, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    return extractDirectedConnectoidsForMode(osmNodeLocation, transferZone, planitMode, geoUtils);
  }

}

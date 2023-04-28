package org.goplanit.osm.converter.zoning.handler.helper;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.osm.converter.network.OsmNetworkHandlerHelper;
import org.goplanit.osm.converter.network.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.OsmZoningHandlerProfiler;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.OsmBoundingAreaUtils;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.osm.util.PlanitTransferZoneUtils;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.graph.modifier.event.GraphModifierListener;
import org.goplanit.utils.misc.IterableUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.ConnectoidUtils;
import org.goplanit.utils.zoning.DirectedConnectoid;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.TransferZoneGroup;
import org.goplanit.zoning.Zoning;
import org.goplanit.zoning.modifier.event.handler.UpdateDirectedConnectoidsOnBreakLinkSegmentHandler;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LinearLocation;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Class to provide functionality for parsing PLANit connectoids from OSM entities
 * 
 * @author markr
 *
 */
public class OsmConnectoidHelper extends OsmZoningHelperBase {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmConnectoidHelper.class.getCanonicalName());

  /** function used to identify overwritten mappings within generic PLANit core functionality */
  private final Function<Node,String> getOverwrittenWaitingAreaSourceIdForNode;

  /* the reference network to use */
  private final PlanitOsmNetwork referenceNetwork;

  /** the zoning to work on */
  private final Zoning zoning;
  
  /** zoning reader data used to track created entities */
  private final OsmZoningReaderData zoningReaderData;
  
    
  /** track stats */
  private final OsmZoningHandlerProfiler profiler;
  
  /** utilities for geographic information */
  private final PlanitJtsCrsUtils geoUtils; 
  

  /** Verify if the waiting area for a stop_position for the given mode must be on the logical relative location (left hand side for left hand drive) or not
   * 
   * @param accessMode to check
   * @param transferZone required in case of user overwrite
   * @param osmStopLocationNodeId may be null if not available
   * @param settings to see if user has provided any overwrite information
   * @return true when restricted for driving direction, false otherwise 
   */
  private static boolean isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(
      final Mode accessMode, final TransferZone transferZone, final Long osmStopLocationNodeId, final OsmPublicTransportReaderSettings settings) {
    
    boolean mustAvoidCrossingTraffic = true;
    if(accessMode.getPhysicalFeatures().getTrackType().equals(TrackModeType.RAIL)) {
      /* ... exception 1: train platforms because trains have doors on both sides */
      mustAvoidCrossingTraffic = false;
    }else if(osmStopLocationNodeId != null && settings.isOverwriteStopLocationWaitingArea(osmStopLocationNodeId)) {
      /* ... exception 2: user override with mapping to this zone for this node, in which case we allow crossing traffic regardless */
      mustAvoidCrossingTraffic = !Long.valueOf(transferZone.getExternalId()).equals(settings.getOverwrittenStopLocationWaitingArea(osmStopLocationNodeId).second());
    } 
    return mustAvoidCrossingTraffic;   
  }   
  
  /** log the given warning message but only when it is not too close to the bounding box, because then it is too likely that it is discarded due to missing
   * infrastructure or other missing assets that could not be parsed fully as they pass through the bounding box barrier. Therefore the resulting warning message is likely 
   * more confusing than helpful in those situation and is therefore ignored
   * 
   * @param message to log if not too close to bounding box
   * @param geometry to determine distance to bounding box to
   */
  private void logWarningIfNotNearBoundingBox(String message, Geometry geometry) {
    OsmBoundingAreaUtils.logWarningIfNotNearBoundingBox(message, geometry, getNetworkToZoningData().getNetworkBoundingBox(), geoUtils);
  }    

  /** Find the link segments that are accessible for the given access link, node, mode combination taking into account the relative location of the transfer zone if needed,
   * mode compatibility, and vertical plane compatibility. OSM layaer tagging is patch and inconsistent. So For the latter,
   * we ONLY enforce compatibility if ignoreOsmVerticalLayerCompatibility==false and the transfer zone has an explicit
   * layer tagged. If either is not the case, then in our experience there is too much risk enforcing this restriction.
   * 
   * @param transferZone these link segments pertain to
   * @param accessLink that is nominated
   * @param node extreme node of the link
   * @param accessMode eligible access mode
   * @param mustAvoidCrossingTraffic indicates of transfer zone must be on the logical side of the road or if it does not matter
   * @param ignoreOsmVerticalLayerCompatibility when true we do not filter the link (segments) based on matching OSM layer index, when false we do
   * @param geoUtils to use
   * @return found link segments that are deemed valid given the constraints, may be null if no match is found
   */
  private Collection<LinkSegment> findAccessLinkSegmentsForStandAloneTransferZone(
      TransferZone transferZone,
      MacroscopicLink accessLink,
      Node node,
      Mode accessMode,
      boolean mustAvoidCrossingTraffic,
      boolean ignoreOsmVerticalLayerCompatibility,
      PlanitJtsCrsUtils geoUtils) {

    /* transfer zone and link ought to be on same vertical plane ONLY IF the transfer zone has explicit layer registered AND we are not ignoring compatibility */
    if(!ignoreOsmVerticalLayerCompatibility) {
      var planitData = this.zoningReaderData.getPlanitData();
      var osmVerticalLayerIndex = planitData.getTransferZoneVerticalLayerIndex(transferZone);
      if (osmVerticalLayerIndex != null && osmVerticalLayerIndex != OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(accessLink)) {
        return null;
      }
    }

    Function<String, String> getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId = tzOsmId -> {
      EntityType osmWaitingAreaEntityType = PlanitTransferZoneUtils.transferZoneGeometryToOsmEntityType(transferZone.getGeometry(true));
      Long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(Long.valueOf(tzOsmId), osmWaitingAreaEntityType);
      return osmWayId!=null ? String.valueOf(osmWayId) : null;
    };

    return ZoningConverterUtils.findAccessLinkSegmentsForWaitingArea(
            transferZone.getExternalId(),
            transferZone.getGeometry(true),
            accessLink,
            accessLink.getExternalId(),
            node,
            accessMode,
            getSettings().getCountryName(),
            mustAvoidCrossingTraffic,
            getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId,
            this.getOverwrittenWaitingAreaSourceIdForNode,
            geoUtils);
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

  /** break a PLANit link at the PLANit node location while also updating all OSM related tracking indices and/or PLANit network link and link segment reference 
   * that might be affected by this process:
   * <ul>
   * <li>tracking of OSM ways with multiple PLANit links</li>
   * <li>connectoid access link segments affected by breaking of link (if any)</li>
   * </ul>
   * 
   * @param planitNode to break link at
   * @param networkLayer the node and link(s) reside on
   * @param linksToBreak the links to break 
   */
  private void breakLinksAtPlanitNode(Node planitNode, MacroscopicNetworkLayer networkLayer, List<MacroscopicLink> linksToBreak){
    OsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
  
    /* track original combinations of linksegment/downstream vertex for each connectoid possibly affected by the links we're about to break link (segments) 
     * if after breaking links this relation is modified, restore it by updating the connectoid to the correct access link segment directly upstream of the original 
     * downstream vertex identified */
    Map<Point, DirectedConnectoid> connectoidsAccessNodeLocationBeforeBreakLink =
        ConnectoidUtils.findDirectedConnectoidsReferencingLinks(linksToBreak, zoningReaderData.getPlanitData().getDirectedConnectoidsByLocation(networkLayer));
    
    /* register additional actions on breaking link via listener for connectoid update (see above)
     * TODO: refactor this so it does not require this whole preparing of data. Ideally this is handled more elegantly than now
     */
    GraphModifierListener listener = new UpdateDirectedConnectoidsOnBreakLinkSegmentHandler(connectoidsAccessNodeLocationBeforeBreakLink);
    networkLayer.getLayerModifier().addListener(listener);
        
    /* LOCAL TRACKING DATA CONSISTENCY  - BEFORE */    
    {      
      /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links exist with smaller geometries... insert those after as replacements*/
      zoningReaderData.getPlanitData().removeLinksFromSpatialLinkIndex(linksToBreak); 
    }    
          
    /* break links and group resulting new links by original link's OSM id*/
    Map<Long, Set<MacroscopicLink>> newlyBrokenLinks = networkLayer.getLayerModifier().breakAt(
        linksToBreak, planitNode,  referenceNetwork.getCoordinateReferenceSystem(), l -> Long.parseLong(l.getExternalId()));
  
    /* TRACKING DATA CONSISTENCY - AFTER */
    {
      /* insert created/updated links and their geometries to spatial index instead */
      newlyBrokenLinks.forEach( (id, links) -> zoningReaderData.getPlanitData().addLinksToSpatialLinkIndex(links));
                    
      /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to be able to find the correct planit links for (internal) osm nodes */
      layerData.updateOsmWaysWithMultiplePlanitLinks(newlyBrokenLinks);                            
    }
    
    networkLayer.getLayerModifier().removeListener(listener);          
  }

  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with access modes provided. connectoids are only created
   * when the access link segment has at least one of the allowed modes as an eligible mode
   * 
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @param ignoreOsmVerticalLayerCompatibility when true we do not report back if the link segments match the transfer zone's OSM layer index, when false we do
   * @return created connectoids
   */
  private Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(
      final TransferZone transferZone,
      final MacroscopicNetworkLayer networkLayer,
      final Iterable<? extends EdgeSegment> linkSegments,
      final Set<Mode> allowedModes,
      boolean ignoreOsmVerticalLayerCompatibility){

    if(!ignoreOsmVerticalLayerCompatibility) {
      var osmVerticalLayerIndex = this.zoningReaderData.getPlanitData().getTransferZoneVerticalLayerIndex(transferZone);
      if (osmVerticalLayerIndex != null &&
          !IterableUtils.asStream(linkSegments).allMatch(
              ls -> OsmNetworkHandlerHelper.getLinkVerticalLayerIndex((Link) ls.getParent()) == this.zoningReaderData.getPlanitData().getTransferZoneVerticalLayerIndex(transferZone))) {
        LOGGER.warning(String.format("OSM vertical layer index (layer=%d) of PLANit transfer zone (%s) not compatible with selected access link segments [%s] for its connectoids, this shouldn't happen, verify correctness",
                osmVerticalLayerIndex, transferZone.getIdsAsString(), IterableUtils.asStream(linkSegments).map(ls -> ls.getIdsAsString()).collect(Collectors.joining(","))));
      }
    }

    Collection<DirectedConnectoid> createdConnectoids = ZoningConverterUtils.createAndRegisterDirectedConnectoids(zoning, transferZone, (Iterable<MacroscopicLinkSegment>) linkSegments, allowedModes);
    for(var newConnectoid : createdConnectoids) {
      /* update planit data tracking information */
      /* 1) index by access link segment's downstream node location */
      zoningReaderData.getPlanitData().addDirectedConnectoidByLocation(networkLayer, newConnectoid.getAccessLinkSegment().getDownstreamVertex().getPosition() ,newConnectoid);
      /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
      zoningReaderData.getPlanitData().addConnectoidByTransferZone(transferZone, newConnectoid);
    }         
    
    return createdConnectoids;
  }

  private boolean extractDirectedConnectoidsForMode(TransferZone transferZone, Mode planitMode, Collection<LinkSegment> eligibleLinkSegments, boolean ignoreOsmVerticalLayerCompatibility,PlanitJtsCrsUtils geoUtils) {

    MacroscopicNetworkLayer networkLayer = referenceNetwork.getLayerByMode(planitMode);
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
        Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(
            transferZone, networkLayer, Collections.singleton(edgeSegment), Collections.singleton(planitMode), ignoreOsmVerticalLayerCompatibility);
        
        if(newConnectoids==null || newConnectoids.isEmpty()) {
          LOGGER.warning(String.format("Found eligible mode %s for stop_location of transferzone %s, but no access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
          return false;
        }
      }  
    }
    
    return true;
  }

  /** extract the connectoid access node based on the given location. Either it already exists as a PLANit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the PLANit node
   *
   * @param osmNodeLocation to collect/create PLANit node for
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not enforce filtering based on criteria that might not have been properly tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get best possible match based on available tagging, e.g., vertical layer information
   * @param networkLayer to extract node on
   * @param osmVerticalLayerIndex the vertical layer index indicating the vertical plane the connectoid is expected to reside on, may be null indicating it is not explicitly tagged
   * @return PLANit node collected/created
   */  
  private Node extractConnectoidAccessNodeByLocation(
      Point osmNodeLocation, boolean locationIsKnownOsmStopPosition, MacroscopicNetworkLayer networkLayer, Integer osmVerticalLayerIndex){
    final OsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);

    /* check if already exists */
    Node planitNode = layerData.getPlanitNodeByLocation(osmNodeLocation);
    if(planitNode == null) {
      /* does not exist yet...create */
      
      /* find the links with the location registered as internal */
      List<MacroscopicLink> linksToBreak = layerData.findPlanitLinksWithInternalLocation(osmNodeLocation);
      if(linksToBreak != null) {

        /* we would expect all links on this connectoids location to reside on the same vertical plane as their transfer zone's that is passed in, which we check since breaking links that are not
        * could be problematic and point to a tagging error, when known stop position, it is likely still correct, but we report it nevertheless */
        if(!linksToBreak.stream().allMatch( l ->
            (osmVerticalLayerIndex!=null && OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(l) == osmVerticalLayerIndex) ||
            (osmVerticalLayerIndex==null && OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(l) == 0))){
          var osmLayerIndices = linksToBreak.stream().map(l -> l.getIdsAsString() + " layer=" + OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(l)).collect(Collectors.joining(","));
          Level logLevel = locationIsKnownOsmStopPosition ? Level.INFO : Level.WARNING;
          String stopPositionContext = locationIsKnownOsmStopPosition ? "Explicit" : "Deduced";
          String followUpAdvice = locationIsKnownOsmStopPosition ? "possible tagging error" : "verify correctness";
          if(osmVerticalLayerIndex == null){
            LOGGER.log(logLevel, String.format("%s stop_position on location (%s) part of links [%s] differs from waiting area that has no OSM vertical layer defined, %s", stopPositionContext, osmNodeLocation, osmLayerIndices, followUpAdvice));
          }else{
              LOGGER.log(logLevel, String.format("%s stop_position on location (%s) part of links [%s] differs from waiting area's explicit OSM layer (layer=%d), %s", stopPositionContext, osmNodeLocation, osmLayerIndices, osmVerticalLayerIndex, followUpAdvice));
          }
        }

        /* location is internal to an existing link, create it based on OSM node if possible, otherwise base it solely on location provided*/
        OsmNode osmNode = layerData.getOsmNodeByLocation(osmNodeLocation);
        if(osmNode != null) {
          /* all regular cases */
          planitNode = OsmNetworkHandlerHelper.createPopulateAndRegisterNode(osmNode, networkLayer, layerData);
        }else {
          /* special cases whenever parser decided that location required planit node even though there exists no OSM node at this location */ 
          planitNode = OsmNetworkHandlerHelper.createPopulateAndRegisterNode(osmNodeLocation, networkLayer, layerData);
        }
        profiler.logConnectoidStatus(zoning.getTransferConnectoids().size());
                             
        /* now perform the breaking of links at the given node and update related tracking/reference information to broken link(segment)(s) where needed */
        breakLinksAtPlanitNode(planitNode, networkLayer, linksToBreak);
      }
    }
    return planitNode;
  }

  /** extract the connectoid access node. either it already exists as a PLANit node, or it is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the PLANit node
   * 
   * @param osmNode to collect PLANit node version for
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not enforce filtering based on criteria that might not have been properly tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get the best possible match based on available tagging, e.g., vertical layer information
   * @param networkLayer to extract node on
   * @param osmVerticalLayerIndex the vertical layer index indicating the vertical plane the connectoid is expected to reside on
   * @return PLANit node collected/created
   */
  private Node extractConnectoidAccessNodeByOsmNode(OsmNode osmNode, boolean locationIsKnownOsmStopPosition, MacroscopicNetworkLayer networkLayer, int osmVerticalLayerIndex){
    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    return extractConnectoidAccessNodeByLocation(osmNodeLocation, locationIsKnownOsmStopPosition, networkLayer, osmVerticalLayerIndex);
  }

  /** extract a connectoid location within the link based on an existing coordinate (osm node) or by inserting an additional coordinate in the location closest to the provided
   * waiting area geometry. A new location is only inserted into the link's geometry when all existing coordinates on the link's geometry fall outside the user specified distance between
   * waiting area and stop location.
   * 
   * @param transferZone transfer zone to use
   * @param accessLink to create connectoid location on on either one of its extreme or internal coordinates
   * @param planitAccessModeType to consider
   * @param maxAllowedStopToTransferZoneDistanceMeters the maximum allowed distance between stop and waiting area that we allow
   * @param networkLayer the link is registered on
   * @return connectoid location to use, may or may not be an existing osm node location, or not
   */
  private Point extractConnectoidLocationForstandAloneTransferZoneOnLink(
      TransferZone transferZone, MacroscopicLink accessLink, PredefinedModeType planitAccessModeType, double maxAllowedStopToTransferZoneDistanceMeters, MacroscopicNetworkLayer networkLayer) {
    
    /* determine distance to closest OSM node on existing planit link to create stop location (connectoid) for*/
    Point connectoidLocation =
        findConnectoidLocationForStandAloneTransferZoneOnLink(transferZone, accessLink, planitAccessModeType, maxAllowedStopToTransferZoneDistanceMeters);
    
    if(connectoidLocation !=null) {
      
      /* in case identified projected location is not identical to an existing shape point or extreme point of the link, insert it into the geometry */
      Coordinate closestExistingCoordinate = geoUtils.getClosestExistingLineStringCoordinateToGeometry(transferZone.getGeometry(), accessLink.getGeometry());
      if( !closestExistingCoordinate.equals2D(connectoidLocation.getCoordinate())) {
  
        /* add projected location to geometry of link */
        LinearLocation projectedLinearLocationOnLink = PlanitEntityGeoUtils.extractClosestProjectedLinearLocationToGeometryFromEdge(transferZone.getGeometry(true), accessLink, geoUtils);
        accessLink.updateGeometryInjectCoordinateAtProjectedLocation(projectedLinearLocationOnLink);
                
        /* new location must be marked as internal to link, otherwise the link will not be broken when extracting connectoids at this location*/
        getNetworkToZoningData().getNetworkLayerData(networkLayer).registerLocationAsInternalToPlanitLink(connectoidLocation, accessLink);
      }
    }
        
    return connectoidLocation;
  }  

  /** Constructor 
   *
   * @param referenceNetwork  to use
   * @param zoning to parse on
   * @param zoningReaderData to use
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @param transferSettings to use
   * @param profiler to use
   */
  public OsmConnectoidHelper(
      PlanitOsmNetwork referenceNetwork,
      Zoning zoning, 
      final OsmZoningReaderData zoningReaderData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      OsmPublicTransportReaderSettings transferSettings,
      OsmZoningHandlerProfiler profiler) {
    super(referenceNetwork, network2ZoningData, transferSettings);

    this.referenceNetwork = referenceNetwork;
    this.zoning = zoning;
    this.zoningReaderData = zoningReaderData;
    this.profiler = profiler;

    // functions to be passed in PLANit generic utils classes used during parsing of waiting areas (transfer zones)
    {
      /* function that takes a node and collects any overwritten waiting area that is pre-specified for it. Used to
       *  override default mapping between waiting area and stop location when needed */
      this.getOverwrittenWaitingAreaSourceIdForNode = n -> {
        var result = transferSettings.getOverwrittenStopLocationWaitingArea(n.getExternalId() != null ? Long.valueOf(n.getExternalId()) : null);
        return result!= null ? String.valueOf(result.second()) : null;
      };
    }
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsCrsUtils(referenceNetwork.getCoordinateReferenceSystem());
  }
  
  /** find a suitable connectoid location on the given link based on the constraints that it must be able to reside on a link segment that is in the correct relative position
   * to the transfer zone and supports the access mode on at least one of the designated link segment(s) that is eligible (if any). If not null is returned
   *  
   * @param transferZone to find location for
   * @param accessLink to find location on
   * @param planitModeType to be compatible with
   * @param maxAllowedDistanceMeters the maximum allowed distance between stop and waiting area that we allow
   * @return found location either existing node or projected location that is nearest and does not exist as a shape point on the link yet, or null if no valid position could be found
   */
  public Point findConnectoidLocationForStandAloneTransferZoneOnLink(
          final TransferZone transferZone, final MacroscopicLink accessLink, final PredefinedModeType planitModeType, double maxAllowedDistanceMeters) {

    final Mode planitMode = referenceNetwork.getModes().get(planitModeType);
    /* prep remaining functions that overwrite default behaviour of PLANit connectoid location finder based on user settings */
    Function<Point, String> getOverwrittenWaitingAreaSourceIdForPoint;
    Function<String, String> getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId;
    {
      /* transform point to waiting area source id if a specific waiting area is to be attached to it, overwrites default behaviour of finding
       * connectoid location in PLANit */
      getOverwrittenWaitingAreaSourceIdForPoint = p -> {
        final var networkLayer = referenceNetwork.getLayerByMode(planitMode);
        final var osmNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(p);
        if(osmNode == null){
          return null;
        }
        var result = getSettings().getOverwrittenStopLocationWaitingArea(osmNode.getId());
        return result!= null ? String.valueOf(result.second()) : null;
      };

      getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId = tzOsmId -> {
        EntityType osmWaitingAreaEntityType = PlanitTransferZoneUtils.transferZoneGeometryToOsmEntityType(transferZone.getGeometry());
        Long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(Long.valueOf(tzOsmId), osmWaitingAreaEntityType);
        return osmWayId!=null ? String.valueOf(osmWayId) : null;
      };
    }

    /* call PLANit connectoid location finder method with appropriate parameters */
    return ZoningConverterUtils.findConnectoidLocationForWaitingAreaOnLink(
            transferZone.getExternalId(),
            transferZone.getGeometry(true),
            accessLink,
            accessLink.getExternalId(),
            planitMode,
            maxAllowedDistanceMeters,
            getOverwrittenWaitingAreaSourceIdForNode,
            getOverwrittenWaitingAreaSourceIdForPoint,
            getOverwrittenAccessLinkSourceIdForWaitingAreaSourceId,
            getSettings().getCountryName(),
            geoUtils);
  }   


  /** Create directed connectoids for transfer zones that reside on OSM ways. For such transfer zones, we simply create connectoids in both directions for all eligible incoming 
   * link segments. This is a special case because due to residing on the OSM way it is not possible to distinguish what intended direction of the osm way is serviced (it is neither
   * left nor right of the way). Therefore any attempt to extract this information is bypassed here.
   * 
   * @param transferZone residing on an osm way
   * @param networkLayer related to the mode
   * @param planitModeType the connectoid is accessible for
   * @param geoUtils to use
   * @return created connectoids, null if it was not possible to create any due to some reason
   */
  public Collection<DirectedConnectoid> createAndRegisterDirectedConnectoidsOnTopOfTransferZone(
      TransferZone transferZone, MacroscopicNetworkLayer networkLayer, PredefinedModeType planitModeType, PlanitJtsCrsUtils geoUtils){

    /* collect the osmNode for this transfer zone */
    OsmNode osmNode = getNetworkToZoningData().getNetworkOsmNodes().get(Long.valueOf(transferZone.getExternalId()));
    
    Iterable<? extends EdgeSegment> nominatedLinkSegments = null;
    if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node)) {
      /* user overwrite */
      
      long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node);
      Link nominatedLink = PlanitLinkOsmUtils.getClosestLinkWithOsmWayIdToGeometry( osmWayId, OsmNodeUtils.createPoint(osmNode), networkLayer, geoUtils);
      if(nominatedLink != null) {
        nominatedLinkSegments = nominatedLink.getEdgeSegments(); 
      }else {
        LOGGER.severe(String.format("User nominated OSM way not available for waiting area on road infrastructure %d",osmWayId));
      }                      
      
    }else {
      /* regular approach */
      
      /* create/collect PLANit node with access link segment (no need to check layer on links here since we know transfer zone coincides with network) */
      var waitingAreaOsmVerticalLayerIndex = zoningReaderData.getPlanitData().getTransferZoneVerticalLayerIndex(transferZone);

      boolean locationIsKnownOsmStopPosition = true;
      Node planitNode = extractConnectoidAccessNodeByOsmNode(osmNode, locationIsKnownOsmStopPosition, networkLayer, waitingAreaOsmVerticalLayerIndex);
      if(planitNode == null) {
        LOGGER.warning(String.format("DISCARD: OSM node (%d) could not be converted to access node for transfer zone osm entity %s at same location",osmNode.getId(), transferZone.getExternalId()));
        return null;
      }
      
      nominatedLinkSegments = planitNode.getEntryLinkSegments();
    }
    
    /* connectoid(s) */
        
    /* create connectoids on top of transfer zone */
    /* since located on OSM way we cannot deduce direction of the stop, so create connectoid for both incoming directions (if present), so we can service any line using the way */
    boolean ignoreOsmVerticalLayerCompatibility = true;
    return createAndRegisterDirectedConnectoids(
        transferZone, networkLayer, nominatedLinkSegments, Collections.singleton(referenceNetwork.getModes().get(planitModeType)), ignoreOsmVerticalLayerCompatibility);
  }


  /** create and/or update directed connectoids for the given mode and layer based on the passed in location where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone. Generally a single connectoid is created for the most likely link segment identified, i.e., if the transfer
   * zone is placed on the left of the infrastructure, the closest by incoming link segment to the given location is used. Since the geometry of a link applies to both link segments
   * we define closest based on the driving position of the country, so a left-hand drive country will use the incoming link segment where the transfer zone is placed on the left, etc. 
   * 
   * @param location to create the access point for as PLANit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not enforce filtering based on criteria that might not have been properly tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get best possible match based on available tagging, e.g., vertical layer information
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitModeType mode type this connectoid is allowed access for
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   */
  public boolean extractDirectedConnectoidsForMode(
      Point location, boolean locationIsKnownOsmStopPosition, TransferZone transferZone, PredefinedModeType planitModeType, PlanitJtsCrsUtils geoUtils) {
    if(location == null || transferZone == null || planitModeType == null || geoUtils == null) {
      return false;
    }

    if(transferZone.getExternalId().equals("3516754930")){
      int bla = 4;
    }

    var planitMode = referenceNetwork.getModes().get(planitModeType);
    MacroscopicNetworkLayer networkLayer = referenceNetwork.getLayerByMode(planitMode);
    OsmNode osmNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(location);

    /* identify vertical plane the location resides on, but only use it if 1) explicit on the zone, or 2) unanimous on eligible links but only use that
    * if the stop position location is not explicitly known, otherwise it is too unreliable to use */
    var waitingAreaOsmVerticalLayerIndex = zoningReaderData.getPlanitData().getTransferZoneVerticalLayerIndex(transferZone);
    if(waitingAreaOsmVerticalLayerIndex == null && !locationIsKnownOsmStopPosition){
      var linkBasedResult = findOsmVerticalLayerIndexByStopPositionPlanitLinks(location, networkLayer);
      if(linkBasedResult != null && linkBasedResult.second()){
        /* unanimous result, so replace finding */
        waitingAreaOsmVerticalLayerIndex = linkBasedResult.first();
      }else{
        int bla = 4;
      }
    }

    /* planit access node */
    Node planitNode = extractConnectoidAccessNodeByLocation(
        location, locationIsKnownOsmStopPosition, networkLayer, waitingAreaOsmVerticalLayerIndex);
    if(planitNode==null) {
      if(osmNode != null) {
        LOGGER.warning(String.format("DISCARD: OSM node %d could not be converted to access node for transfer zone representation of OSM entity %s",osmNode.getId(), transferZone.getExternalId()));
      }else {
        LOGGER.warning(String.format("DISCARD: Location (%s) could not be converted to access node for transfer zone representation of OSM entity %s",location, transferZone.getExternalId()));
      }
      return false;
    }
    
    /* must avoid cross traffic when:
     * 1) stop position does not coincide with transfer zone, i.e., waiting area is not on the road/rail, and
     * 2) mode requires waiting area to be on a specific side of the road, e.g. buses can only open doors on one side, so it matters for them, but not for train
     */
    boolean mustAvoidCrossingTraffic = !planitNode.getPosition().equalsTopo(transferZone.getGeometry());
    if(mustAvoidCrossingTraffic) {
      mustAvoidCrossingTraffic = isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(planitMode, transferZone, osmNode!= null ? osmNode.getId() : null, getSettings());  
    }         
    
    /* find access link segments */
    Collection<LinkSegment> accessLinkSegments = null;
    boolean ignoreOsmVerticalLayerCompatibility = locationIsKnownOsmStopPosition;
    for(MacroscopicLink link : planitNode.<MacroscopicLink>getLinks()) {
      Collection<LinkSegment> linkAccessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(
          transferZone,link, planitNode, planitMode, mustAvoidCrossingTraffic, ignoreOsmVerticalLayerCompatibility, geoUtils);
      if(linkAccessLinkSegments != null && !linkAccessLinkSegments.isEmpty()) {
        if(accessLinkSegments == null) {
          accessLinkSegments = linkAccessLinkSegments;
        }else {
          accessLinkSegments.addAll(linkAccessLinkSegments);
        }
      }
    }    
      
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      LOGGER.warning(String.format("DISCARD platform/pole/station %s its stop_location %s deemed invalid, no access link segment found due to mode inaccessibility/exclusion, or on wrong side of road/rail, verify correctness",
          transferZone.getExternalId(), location));
      return false;
    }                           
    
    /* connectoids for link segments */
    return extractDirectedConnectoidsForMode(transferZone, planitMode, accessLinkSegments, ignoreOsmVerticalLayerCompatibility, geoUtils);
  }


  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node (location) where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not enforce filtering based on criteria that might not have been properly tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get the best possible match based on available tagging, e.g., vertical layer information
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitModeType mode type this connectoid is allowed access for
   * @param geoUtils used to determine location of transfer zone relative to infrastructure to identify which link segment(s) are eligible for connectoids placement
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   */
  public boolean extractDirectedConnectoidsForMode(OsmNode osmNode, boolean locationIsKnownOsmStopPosition, TransferZone transferZone, PredefinedModeType planitModeType, PlanitJtsCrsUtils geoUtils){
    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    return extractDirectedConnectoidsForMode(osmNodeLocation, locationIsKnownOsmStopPosition, transferZone, planitModeType, geoUtils);
  }
  
  /** create and/or update directed connectoids for the transfer zones and mode combinations when eligible, based on the passed in OSM node 
   * where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not enforce filtering based on criteria that might not have been properly tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get the best possible match based on available tagging, e.g., vertical layer information
   * @param transferZones connectoids are assumed to provide access to
   * @param planitModeTypes this connectoid is allowed access for
   * @param transferZoneGroup it belongs to, when zone is not yet in the group the zone is added to the group (group is allowed to be null)
   * @return true when at least connectoids where created for one of the transfer zones identified
   */
  public boolean extractDirectedConnectoids(OsmNode osmNode, boolean locationIsKnownOsmStopPosition, Collection<TransferZone> transferZones, Collection<PredefinedModeType> planitModeTypes, TransferZoneGroup transferZoneGroup){
    boolean success = false; 
    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    for(var modeType : planitModeTypes) {
      
      /* layer */
      MacroscopicNetworkLayer networkLayer = referenceNetwork.getLayerByPredefinedModeType(modeType);
      if(!getNetworkToZoningData().getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)) {
        logWarningIfNotNearBoundingBox(
            String.format("DISCARD: stop_position %d not present in network layer for %s (residing road type deactivated or node dangling)",osmNode.getId(), modeType), OsmNodeUtils.createPoint(osmNode));
        continue;
      }
      
      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) */
        success = extractDirectedConnectoidsForMode(osmNode, locationIsKnownOsmStopPosition, transferZone, modeType, geoUtils) || success;
        if(success && transferZoneGroup != null && !transferZone.isInTransferZoneGroup(transferZoneGroup)) {
          /* in some rare cases only the stop locations are part of the stop_area, but not the platforms next to the road/rail, only then this situation is triggered and we salve the situation */
          LOGGER.info(String.format("Platform/pole %s identified for stop_position %d, platform/pole not in stop_area %s of stop_position, added it",transferZone.getExternalId(), osmNode.getId(), transferZoneGroup.getExternalId()));
          transferZoneGroup.addTransferZone(transferZone);
        }
      }      
    }
    
    return success;
  }  
  
  /** create connectoids not based on OSM node location but based on auto-generated geographic location on the provided link's link segments by
   * finding either a close enough existing coordinate (OSM node), or if not close enough a newly created coordinate at the appropriate position.
   * then create connectoids accordingly by breaking the link in these locations
   * 
   * @param osmWaitingAreaId the waiting area pertains to
   * @param waitingAreaGeometry geometry of the waiting area
   * @param accessLink to create connectoids on by breaking it
   * @param transferZone to register connectoids on
   * @param planitAccessModeType eligible mode type for the station
   * @param maxAllowedStopToTransferZoneDistanceMeters the maximum allowed distance between stop and waiting area that we allow
   * @param networkLayer the modes relate to
   */
  public void extractDirectedConnectoidsForStandAloneTransferZoneByPlanitLink(
      long osmWaitingAreaId, Geometry waitingAreaGeometry , MacroscopicLink accessLink, TransferZone transferZone, PredefinedModeType planitAccessModeType, double maxAllowedStopToTransferZoneDistanceMeters, MacroscopicNetworkLayer networkLayer) {

    if(osmWaitingAreaId == 7012484909L){
      int bla = 4;
    }

    /* geo location on planit link, possibly inserted for this purpose by this method if no viable osm node/existing coordinate is present */
    Point connectoidLocation = extractConnectoidLocationForstandAloneTransferZoneOnLink(
        transferZone, accessLink, planitAccessModeType, maxAllowedStopToTransferZoneDistanceMeters, networkLayer);
    if(connectoidLocation == null) {
      logWarningIfNotNearBoundingBox(
          String.format("DISCARD: Unable to create stop_location on identified access link %s, identified location is likely too far from waiting area %s",accessLink.getExternalId(),transferZone.getExternalId()), transferZone.getGeometry());
    }
    
    /* special case - user overwrite verification */
    OsmNode osmStopLocationNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(connectoidLocation);
    if(osmStopLocationNode != null && getSettings().isOverwriteStopLocationWaitingArea(osmStopLocationNode.getId())) {
      /* user has chosen to overwrite waiting area for this connectoid (stop_location), so the transfer zone provided should correspond to the chosen waiting area id, otherwise
       * we simply ignore and return (when processing incomplete transfer zones, it might try to use a stop_location for a transfer zone that is incomplete but indicated by the user to
       * not be used for this connectoid, so there can be a valid reason why this method is invoked, as well as a valid reason to not create connectoids when checking for this situation */
      Pair<EntityType, Long>  overwriteResult = getSettings().getOverwrittenStopLocationWaitingArea(osmStopLocationNode.getId());
      /* when type match (point=node, otherwise=way)  and id match we can continue, otherwise not */
      if( !(waitingAreaGeometry instanceof Point && Long.valueOf(transferZone.getExternalId()) == overwriteResult.second())) {
        return;
      }else if( Long.valueOf(transferZone.getExternalId()) != overwriteResult.second()) {
        return;
      }
    }            
          
    /* create connectoids at identified location for mode and restricted to the accessLink identified (or update existing connectoid with mode access if valid) */
    boolean locationIsKnownOsmStopPosition = false;
    extractDirectedConnectoidsForMode(connectoidLocation, locationIsKnownOsmStopPosition, transferZone, planitAccessModeType, geoUtils);
  }  

}

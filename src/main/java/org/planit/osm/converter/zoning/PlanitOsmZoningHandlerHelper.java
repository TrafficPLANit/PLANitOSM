package org.planit.osm.converter.zoning;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LinearLocation;
import org.planit.network.InfrastructureLayer;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.osm.tags.OsmWaterModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitGraphGeoUtils;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.graph.Edge;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.TrackModeType;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Helper class for the OSM handlers, providing static helper methods that reflect common code across various
 * handlers that are not considered general enough to be part of a utility class.
 * 
 * @author markr
 *
 */
public class PlanitOsmZoningHandlerHelper {
  
  /** the logger to use */
  public static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningHandlerHelper.class.getCanonicalName());                  

  /** collect the one way edge segment for the mode if the link is in fact one way. If it is not (for the mode), null is returned
   * 
   * @param link to collect one way edge segment (for mode) from, if available
   * @param accessMode to check one-way characteristic
   * @return edge segment that is one way for the mode, i.e., the other edge segment (if any) does not support this mode, null if this is not the case
   */
  public static MacroscopicLinkSegment getLinkSegmentIfLinkIsOneWayForMode(Link link, Mode accessMode) {
    EdgeSegment edgeSegment = null;
    if(link.hasEdgeSegmentAb() != link.hasEdgeSegmentBa()) {
      /* link is one way across all modes, so transfer zones are identifiable to be one the wrong or right side of the road */
      edgeSegment = link.hasEdgeSegmentAb() ? link.getLinkSegmentAb() : link.getLinkSegmentBa();
    }else if(link.<MacroscopicLinkSegment>getLinkSegmentAb().isModeAllowed(accessMode) != link.<MacroscopicLinkSegment>getLinkSegmentBa().isModeAllowed(accessMode)) {
      /* link is one way for our mode, so transfer zones are identifiable to be one the wrong or right side of the road */
      edgeSegment = link.<MacroscopicLinkSegment>getLinkSegmentAb().isModeAllowed(accessMode) ? link.getLinkSegmentAb() : link.getLinkSegmentBa();
    }

    return (MacroscopicLinkSegment) edgeSegment;
  }  


  /** create a subset of links from the passed in ones, removing all links for which we can be certain that geometry is located on the wrong side of the road infrastructure geometry.
   * This is verified by checking if the link is one-way. If so, we can be sure (based on the driving direction of the country) if the geometry is located to the closest by (logical) 
   * driving direction given the placement of the geometry, i.e., on the left hand side for left hand drive countries, on the right hand side for right hand driving countries
   * 
   * @param waitingAreaGeometry representing the waiting area (station, platform, pole)
   * @param links to remove ineligible ones from
   * @param isLeftHandDrive flag
   * @param accessModes to consider
   * @param geoUtils to use
   * @return remaining links that are deemed eligible
   * @throws PlanItException thrown if error
   */   
  public static Collection<Link> excludeLinksOnWrongSideOf(Geometry waitingAreaGeometry, Collection<Link> links, boolean isLeftHandDrive, Collection<Mode> accessModes, PlanitJtsCrsUtils geoUtils) throws PlanItException{
    Collection<Link> matchedLinks = new HashSet<Link>(links);  
    for(Link link : links) {            
      for(Mode accessMode : accessModes){
        
        /* road based modes must stop with the waiting area in the driving direction, i.e., must avoid cross traffic, because otherwise they 
         * have no doors at the right side, e.g., travellers have to cross the road to get to the vehicle, which should not happen */
        boolean mustAvoidCrossingTraffic = true;
        if(accessMode.getPhysicalFeatures().getTrackType().equals(TrackModeType.RAIL)) {
          mustAvoidCrossingTraffic = false;
        }           
        
        MacroscopicLinkSegment oneWayLinkSegment = PlanitOsmZoningHandlerHelper.getLinkSegmentIfLinkIsOneWayForMode(link, accessMode);        
        if(oneWayLinkSegment != null && mustAvoidCrossingTraffic) {
          /* use line geometry closest to connectoid location */
          LineSegment finalLineSegment = PlanitOsmZoningHandlerHelper.extractClosestLineSegmentToGeometryFromLinkSegment(waitingAreaGeometry, oneWayLinkSegment, geoUtils);                    
          /* determine location relative to infrastructure */
          boolean isStationLeftOfOneWayLinkSegment = geoUtils.isGeometryLeftOf(waitingAreaGeometry, finalLineSegment.p0, finalLineSegment.p1);  
          if(isStationLeftOfOneWayLinkSegment != isLeftHandDrive) {
            /* travellers cannot reach doors of mode on this side of the road, so deemed not eligible */
            matchedLinks.remove(link);
            break; // from mode loop
          }
        }
      }             
    }
    return matchedLinks;
  }
  
  /** Verify if the passed on osm node that represents a stop_location is in fact also a Ptv1 stop, either a highway=tram_stop or
   * highway=bus_stop. This is effectively wrongly tagged, but does occur due to confusion regarding the tagging schemes. Therefore we
   * identify this situation allowing the parser to change its behaviour and if it is a Patv1 stop, process it as such if deemed necessary
   * 
   * @param osmNode that is classified as Ptv2 stop_location
   * @param tags of the stop_location
   * @return true when also a Ptv1 stop (bus or tram stop), false otherwise
   */
  public static boolean isPtv2StopPositionPtv1Stop(OsmNode osmNode, Map<String, String> tags) {
    
    /* Context: The parser assumed a valid Ptv2 tagging and ignored the Ptv1 tag. However, it was only a valid Ptv1 tag and incorrectly tagged Ptv2 stop_location.
     * We therefore identify this special situation */    
    if(OsmPtv1Tags.isTramStop(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 tram_stop (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isBusStop(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 bus_stop (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isHalt(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 halt (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isStation(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 station (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }
    return false;
  }   

  /** collect the transfer zone type based on the tags
   * 
   * @param osmNode node 
   * @param tags tags of the node
   * @return transfer zone type, unknown if not able to map 
   */
  public static TransferZoneType getPtv1TransferZoneType(OsmNode osmNode, Map<String, String> tags) {
    if(OsmPtv1Tags.isBusStop(tags)) {
      return TransferZoneType.POLE;
    }else if(OsmPtv1Tags.isTramStop(tags) ) {
      return TransferZoneType.PLATFORM;
    }else if(OsmPtv1Tags.isHalt(tags)) {
      return TransferZoneType.SMALL_STATION;
    }else if(OsmPtv1Tags.isStation(tags)) {
      return TransferZoneType.STATION;
    }else {
      LOGGER.severe(String.format("unable to map node %d to Ptv1 transferzone type", osmNode.getId()));
      return TransferZoneType.UNKNOWN;
    }
  }
      
  
  /** more readable check to see if pair with eligible modes contains any eligible osm mode
   * 
   * @param modeResult of collectEligibleModes on zoning base handler
   * @return true when has at least one mapped planit mode present
   */
  public static boolean hasEligibleOsmMode(Pair<Collection<String>, Collection<Mode>> modeResult) {
    if(modeResult!= null && modeResult.first()!=null && !modeResult.first().isEmpty()) {
      /* eligible modes available */
      return true;
    }else {
      return false;
    }
  }  

  /** more readable check to see if pair with eligible modes contains any mapped planit mode
   * 
   * @param modeResult of collectEligibleModes on zoning base handler
   * @return true when has at least one mapped planit mode present
   */
  public static boolean containsMappedPlanitMode(Pair<Collection<String>, Collection<Mode>> modeResult) {
    if(modeResult!= null && modeResult.second()!=null && !modeResult.second().isEmpty()) {
      /* eligible modes mapped to planit mode*/
      return true;
    }else {
      return false;
    }
  }

  /** collect the closest by link (with the given osm way id on the layer) to the provided geometry
   * @param osmWayId to filter links on
   * @param geometry to check closeness against
   * @param networkLayer the link must reside on
   * @param geoUtils to use for closeness computations
   * @return found link (null if none)
   * @throws PlanItException thrown if error
   */
  public static Link getClosestLinkWithOsmWayIdToGeometry(
      long osmWayId, Geometry geometry, MacroscopicPhysicalNetwork networkLayer, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* collect all planit links that match the osm way id (very slow, but it is rare and not worth the indexing generally) */
    Collection<? extends Link> nominatedLinks = networkLayer.links.getByExternalId(String.valueOf(osmWayId));
    /* in case osm way is broken, multiple planit links might exist with the same external id, find closest one and use it */
    return (Link) PlanitGraphGeoUtils.findEdgeClosest(geometry, nominatedLinks, geoUtils);
  }

  /** find the linear location reflecting the closest projected location between the transfer zone and link geometries. For the transfer zone geometry we use existing coordinates
   * rather than projected ones
   * 
   * @param transferZone to use
   * @param accessEdge to use
   * @param geoUtils to use
   * @return closest projected linear location on link geometry
   * @throws PlanItException thrown if error
   */
  public static LinearLocation getClosestProjectedLinearLocationOnEdgeForTransferZone(TransferZone transferZone, Edge accessEdge, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    LinearLocation projectedLinearLocationOnLink = null;
    EntityType transferZoneGeometryType = PlanitOsmZoningHandlerHelper.getOsmEntityType(transferZone);
    if(transferZoneGeometryType.equals(EntityType.Node)) {
      projectedLinearLocationOnLink = geoUtils.getClosestProjectedLinearLocationOnGeometry((Point)transferZone.getGeometry(),accessEdge.getGeometry());
    }else if (transferZoneGeometryType.equals(EntityType.Way)){
      projectedLinearLocationOnLink = geoUtils.getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(transferZone.getGeometry(),accessEdge.getGeometry());
    }else {
      throw new PlanItException("Unsupported osm entity type encountered for transfer zone (osm waiting area %s)",transferZone.getExternalId());
    }
    return projectedLinearLocationOnLink;
    
  }

  /** extract a JTS line segment based on the closest two coordinates on the link segment geometry in its intended direction to the reference geometry provided
   * 
   * @param referenceGeometry to find closest line segment to 
   * @param linkSegment to extract line segment from
   * @param geoUtils for distance calculations
   * @return line segment if found
   * @throws PlanItException  thrown if error
   */
  public static LineSegment extractClosestLineSegmentToGeometryFromLinkSegment(Geometry referenceGeometry, MacroscopicLinkSegment linkSegment, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    LineString linkSegmentGeometry = linkSegment.getParentEdge().getGeometry();
    if(linkSegmentGeometry == null) {
      throw new PlanItException("geometry not available on osm way %s, unable to determine if link (segment) is closest to reference geometry, this shouldn't happen", linkSegment.getParentEdge().getExternalId());
    }
    
    LinearLocation linearLocation = geoUtils.getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(referenceGeometry, linkSegmentGeometry);
    boolean reverseLinearLocationGeometry = linkSegment.isDirectionAb()!=linkSegment.getParentEdge().isGeometryInAbDirection();
    
    LineSegment lineSegment = linearLocation.getSegment(linkSegment.getParentEdge().getGeometry());
    if(reverseLinearLocationGeometry) {
      lineSegment.reverse();
    }
    return lineSegment;        
  }

  /** A stand alone station can either support a single platform when it is road based or two stop_locations for rail (on either side). This is 
   * reflected in the returned max matches. The search distance is based on the settings where a road based station utilises the stop to waiting
   * area search distance whereas a rail based one uses the station to waiting area search distance
   * 
   * @param osmStationId to use
   * @param settings to obtain search distance for
   * @param osmStationMode station modes supported
   * @return search distance and max stop_location matches pair, null if problem occurred
   */
  public static Pair<Double, Integer> determineSearchDistanceAndMaxStopLocationMatchesForStandAloneStation(
      long osmStationId, String osmStationMode, PlanitOsmPublicTransportReaderSettings settings) {
    
    Double searchDistance = null;
    Integer maxMatches = null;
    if(OsmRailModeTags.isRailModeTag(osmStationMode)) {
      /* rail based station -> match to nearby train tracks 
       * assumptions: small station would at most have two tracks with platforms and station might be a bit further away 
       * from tracks than a regular bus stop pole, so cast wider net */
      searchDistance = settings.getStationToWaitingAreaSearchRadiusMeters();
      maxMatches = 2;
    }else if(OsmRoadModeTags.isRoadModeTag(osmStationMode)) {
      /* road based station -> match to nearest road link 
       * likely bus stop, so only match to closest by road link which should be very close, so use
       * at most single match and small search radius, same as used for pole->stop_position search */
      searchDistance = settings.getStopToWaitingAreaSearchRadiusMeters();
      maxMatches = 1;
    }else if(OsmWaterModeTags.isWaterModeTag(osmStationMode)) {
      /* water based -> not supported yet */
      LOGGER.warning(String.format("DISCARD: water based stand-alone station detected %d, not supported yet, skip", osmStationId));
      return null;
    }
    
    return Pair.of(searchDistance, maxMatches);
  }

  /** Verify if the waiting area for a stop_position for the given mode must be on the logical relative location (left hadn side for left hand drive) or not
   * 
   * @param accessMode to check
   * @param transferZone required in case of user overwrite
   * @param osmStopLocationNodeId may be null if not available
   * @param settings to see if user has provided any overwrite information
   * @return true when restricted for driving direction, false otherwise 
   */
  public static boolean isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(
      final Mode accessMode, final TransferZone transferZone, final Long osmStopLocationNodeId, final PlanitOsmPublicTransportReaderSettings settings) {
    
    boolean mustAvoidCrossingTraffic = true;
    if(accessMode.getPhysicalFeatures().getTrackType().equals(TrackModeType.RAIL)) {
      /* ... exception 1: train platforms because trains have doors on both sides */
      mustAvoidCrossingTraffic = false;
    }else if(osmStopLocationNodeId != null && settings.isOverwriteStopLocationWaitingArea(osmStopLocationNodeId)) {
      /* ... exception 2: user override with mapping to this zone for this node, in which case we allow crossing traffic regardless */
      mustAvoidCrossingTraffic = Long.valueOf(transferZone.getExternalId()).equals(settings.getOverwrittenStopLocationWaitingArea(osmStopLocationNodeId).second());
    } 
    return mustAvoidCrossingTraffic;
    
  }
  
  /**
   * remove any dangling zones
   * 
   * @param zoning to remove them from
   */
  public static void removeDanglingZones(Zoning zoning) {
    /* delegate to zoning modifier */
    int originalNumberOfTransferZones = zoning.transferZones.size();
    zoning.getZoningModifier().removeDanglingZones();
    LOGGER.info(String.format("Removed dangling transfer zones, remaining number of zones %d (original: %d)", zoning.transferZones.size(), originalNumberOfTransferZones));
  }  
  
  /**
   * remove any dangling transfer zone groups
   * 
   * @param zoning to remove them from
   */  
  public static void removeDanglingTransferZoneGroups(Zoning zoning) {
    /* delegate to zoning modifier */
    int originalNumberOfTransferZoneGroups = zoning.transferZoneGroups.size();
    zoning.getZoningModifier().removeDanglingTransferZoneGroups();    
    LOGGER.info(String.format("Removed dangling transfer zone groups, remaining number of groups %d (original: %d)", zoning.transferZoneGroups.size(), originalNumberOfTransferZoneGroups));    
  }    

  /** extract the osm entity type from a planit Transfer zone
   * @param transferZone to identify entity type for
   * @return the entity type
   * @throws PlanItException thrown if error
   */
  public static EntityType getOsmEntityType(TransferZone transferZone) throws PlanItException {
    if( transferZone.getGeometry() instanceof Point) {
      return EntityType.Node;
    }else if(transferZone.getGeometry() instanceof Polygon || transferZone.getGeometry() instanceof LineString) {
      return EntityType.Way;
    }else {
      throw new PlanItException("unknown geometry type encountered for transfer zone (osm id %s)",transferZone.getExternalId());
    }
  }
  
  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @param osmNodes to consider
   * @param network to consider
   * @param networkToZoningData to extract layer specific data from
   * @return true when one or more layers are found, false otherwise
   * @throws PlanItException thrown if error
   */
  public static boolean hasNetworkLayersWithActiveOsmNode(
      long osmNodeId, Map<Long, OsmNode> osmNodes, InfrastructureNetwork<?, ?> network, PlanitOsmNetworkToZoningReaderData networkToZoningData) throws PlanItException {    
    OsmNode osmNode = osmNodes.get(osmNodeId);
    if(osmNode != null) {
      for(InfrastructureLayer networkLayer : network.infrastructureLayers) {        
        if(networkToZoningData.getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)){
          return true;
        }        
      }
    }
    return false;
  }

  
}

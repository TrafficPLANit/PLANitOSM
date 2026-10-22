package org.goplanit.osm.converter.zoning.handler.helper;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import org.goplanit.converter.zoning.VehicleStopConnectoidLocationOnLinkSegment;
import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.network.data.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.converter.zoning.handler.OsmZoningHandlerProfiler;
import org.goplanit.osm.util.*;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.graph.directed.DirectedVertex;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.graph.modifier.event.GraphModifierListener;
import org.goplanit.utils.id.ExternalIdAble;
import org.goplanit.utils.misc.IterableUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.utils.network.layer.physical.Node;
import org.goplanit.utils.zoning.*;
import org.goplanit.utils.zoning.connectoid.ConnectoidUtils;
import org.goplanit.utils.zoning.connectoid.DirectedConnectoidAccessZoneEntry;
import org.goplanit.utils.zoning.connectoid.TransferConnectoid;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.modifier.event.handler.UpdateDirectedConnectoidsOnBreakLinkSegmentHandler;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LinearLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Class to provide functionality for parsing PLANit transfer connectoids from OSM entities
 * 
 * @author markr
 *
 */
public class OsmTransferConnectoidHelper extends OsmZoningHelperBase {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmTransferConnectoidHelper.class.getCanonicalName());

  /** function used to identify overwritten mappings within generic PLANit core functionality */
  private final Function<Node,String> getOverwrittenWaitingAreaSourceIdForNode;

  /** track stats */
  private final OsmZoningHandlerProfiler profiler;


  /** Verify if the waiting area for a stop_position for the given mode must be on the logical relative location
   *  (left hand side for left hand drive) or not
   * 
   * @param accessMode to check
   * @param transferZone required in case of user overwrite
   * @param osmStopLocationNodeId may be null if not available
   * @param settings to see if user has provided any overwrite information
   * @return true when restricted for driving direction, false otherwise 
   */
  private static boolean isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(
      final Mode accessMode,
      final TransferZone transferZone,
      final Long osmStopLocationNodeId,
      final OsmPublicTransportReaderSettings settings) {

    /* ... exception 1: train/tram/ferry platforms because trains/trams/ferries have entrances on both sides */
    boolean mustAvoidCrossingTraffic = ZoningConverterUtils.isAvoidCrossTrafficForAccessMode(accessMode);
    if(osmStopLocationNodeId != null && settings.isOverwriteWaitingAreaOfStopLocation(osmStopLocationNodeId)) {
      /* ... exception 2: user override with mapping to this zone for this node, in which case we
      allow crossing traffic regardless */
      mustAvoidCrossingTraffic = !Long.valueOf(PlanitOsmUtils.parseOsmId(transferZone)).equals(
              settings.getOverwrittenWaitingAreaOfStopLocation(osmStopLocationNodeId).second());
    } 
    return mustAvoidCrossingTraffic;
  }

  /**
   * Log that no vehicle stop connectoid location could be created on an access link for a waiting area
   *
   * @param accessLink the connectoid location was looked for on
   * @param transferZone of the waiting area
   */
  private static void logVehicleStopConnectoidLocationNotCreated(
      MacroscopicLink accessLink, TransferZone transferZone) {
    LOGGER.warning(
        String.format("DISCARD: Unable to create stop_location on identified access link %s, identified location is " +
            "likely too far from waiting area %s",accessLink.getExternalId(),transferZone.getExternalId()));
  }

  /**
   * Create the mapping from a waiting area's OSM id to the OSM way id the user nominated as its access link, see
   * {@link OsmPublicTransportReaderSettings#getWaitingAreaNominatedOsmWayForStopLocation}
   *
   * @param transferZone of the waiting area, whose geometry tells which kind of OSM entity the waiting area is
   * @return mapping from waiting area OSM id to the nominated OSM way id, giving null when none is nominated
   */
  private Function<String, String> createNominatedAccessLinkSourceIdMapping(TransferZone transferZone) {
    return tzOsmId -> {
      EntityType osmWaitingAreaEntityType =
          PlanitTransferZoneUtils.transferZoneGeometryToOsmEntityType(transferZone.getGeometry(true));
      Long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(
          Long.valueOf(tzOsmId), osmWaitingAreaEntityType);
      return osmWayId!=null ? String.valueOf(osmWayId) : null;
    };
  }

  /**
   * Create the mapping from a location to the OSM id of the waiting area the user attached the stop location there to,
   * see {@link OsmPublicTransportReaderSettings#getOverwrittenWaitingAreaOfStopLocation}
   *
   * @param planitMode whose network layer holds the OSM nodes looked up by location
   * @return mapping from location to waiting area OSM id, giving null when the location is no OSM node, or when the
   * user attached it to no waiting area
   */
  private Function<Point, String> createOverwrittenWaitingAreaSourceIdForPointMapping(Mode planitMode) {
    var networkLayer = getZoningReaderData().getPlanitConverterData().getReferenceNetwork().getLayerByMode(planitMode);
    return point -> {
      final var osmNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(point);
      if(osmNode == null){
        return null;
      }
      var result = getSettings().getOverwrittenWaitingAreaOfStopLocation(osmNode.getId());
      return result!= null ? String.valueOf(result.second()) : null;
    };
  }

  /**
   * Find the link segments that are accessible for the given access link, node, mode combination taking into account
   * the relative location of the transfer zone if needed,
   * mode compatibility, and vertical plane compatibility. OSM layaer tagging is patch and inconsistent. So For the
   * latter,
   * we ONLY enforce compatibility if ignoreOsmVerticalLayerCompatibility==false and the transfer zone has an explicit
   * layer tagged. If either is not the case, then in our experience there is too much risk enforcing this restriction.
   *
   * @param transferZone                        these link segments pertain to
   * @param accessLink                          that is nominated
   * @param node                                extreme node of the link
   * @param accessMode                          eligible access mode
   * @param mustAvoidCrossingTraffic            indicates of transfer zone must be on the logical side of the road or
   *                                            if it does not matter
   * @param ignoreOsmVerticalLayerCompatibility when true we do not filter the link (segments) based on matching OSM
   *                                            layer index, when false we do
   * @return found link segments that are deemed valid given the constraints, may be null if no match is found
   */
  private Collection<LinkSegment> findAccessLinkSegmentsForStandAloneTransferZone(
      TransferZone transferZone,
      MacroscopicLink accessLink,
      Node node,
      Mode accessMode,
      boolean mustAvoidCrossingTraffic,
      boolean ignoreOsmVerticalLayerCompatibility) {

    var referenceNetwork = getZoningReaderData().getPlanitConverterData().getReferenceNetwork();
    /* transfer zone and link ought to be on same vertical plane ONLY IF the transfer zone has explicit layer
    registered AND we are not ignoring compatibility */
    if(!ignoreOsmVerticalLayerCompatibility) {
      var osmZoningPlanitData = getZoningReaderData().getPlanitConverterData();
      var osmNetworkLayerData =
          getNetworkToZoningData().getNetworkLayerData(referenceNetwork.getLayerByMode(accessMode));

      var osmVerticalLayerIndex = osmZoningPlanitData.getTransferZoneOsmVerticalLayerIndex(transferZone);
      if (osmVerticalLayerIndex != null &&
          osmVerticalLayerIndex != osmNetworkLayerData.getLinkVerticalLayerIndex(accessLink)) {
        return null;
      }
    }

    return ZoningConverterUtils.findAccessEntryLinkSegmentsForWaitingArea(
            transferZone.getExternalId(),
            transferZone.getGeometry(true),
            accessLink,
            accessLink.getExternalId(),
            node,
            accessMode,
            getSettings().getCountryName(),
            mustAvoidCrossingTraffic,
            createNominatedAccessLinkSourceIdMapping(transferZone),
            this.getOverwrittenWaitingAreaSourceIdForNode,
            getGeoUtils());
  }

  /**
   * update an existing directed connectoid with new access zone, segment and allowed modes (for given type). In case
   * the link segment does not have any of the passed in modes listed as allowed, the connectoid is not updated
   * with these modes for the given access zone as it would not be possible to utilise it.
   *
   * @param connectoidToUpdate to connectoid to update
   * @param accessZone         to relate connectoids to
   * @param accessSegment      to allow
   * @param allowedModes       to add to the connectoid for the given access zone
   * @param type type to use
   * @param forceAllowModes when true force allowed modes to be allowed on connectoid if it already exists but has not
   *                        all modes listed as allowed, when false, ignore and log warning if not compatible
   */  
  private void updateTransferConnectoidWithDirectedAccess(
      @Nonnull TransferConnectoid connectoidToUpdate,
      @Nonnull TransferZone accessZone,
      @Nonnull EdgeSegment accessSegment,
      @Nonnull Collection<Mode> allowedModes,
      ZoneConnectoidType type,
      boolean forceAllowModes) {

    // check mode compatibility first
    var availableModes = ((MacroscopicLinkSegment)accessSegment).getAllowedModesFrom(allowedModes);
    if(availableModes.isEmpty()) {
      return;
    }

    if(!connectoidToUpdate.hasAccessZoneEntry(accessZone, type)){
      connectoidToUpdate.createDirectedAccessZoneEntry(accessZone, type);
    }
    var entry = connectoidToUpdate.getAccessZoneEntry(accessZone, type);
    if(!entry.isAllModesAllowedFrom(availableModes) && !forceAllowModes){
      LOGGER.warning(String.format("Unable to update connectoid for access zone (%s) type (%s) and access " +
          "segment (%s), as modes are incompatible (now: %s, suggested: %s)", accessZone.getIdsAsString(),
          type, entry.getAllowedModesFrom(availableModes).stream().map(
              m -> m.getPredefinedModeType().toString()).collect(Collectors.joining(",")),
          availableModes.stream().map(
                  m -> m.getPredefinedModeType().toString()).collect(Collectors.joining(","))));
      return;
    }
    if(entry instanceof DirectedConnectoidAccessZoneEntry){
      // add if missing, if not directed already allowed
      var directedEntry = ((DirectedConnectoidAccessZoneEntry)entry);
      if(!directedEntry.hasAccessLinkSegment(accessSegment)){
        directedEntry.addAccessLinkSegment(accessSegment);
      }
    }else {
      LOGGER.severe("Cannot add access segment to connectoid entry that is not directed, this should not happen");
    }
    entry.addAllowedModes(availableModes);
  }

  /** break a PLANit link at the PLANit node location while also updating all OSM related tracking indices and/or
   * PLANit network link and link segment reference that might be affected by this process:
   * <ul>
   * <li>tracking of OSM ways with multiple PLANit links</li>
   * <li>connectoid access link segments affected by breaking of link (if any)</li>
   * </ul>
   * 
   * @param planitNode to break link at
   * @param networkLayer the node and link(s) reside on
   * @param linksToBreak the links to break
   */
  private void breakLinksAtPlanitNode(
      Node planitNode,
      MacroscopicNetworkLayer networkLayer,
      List<MacroscopicLink> linksToBreak){

    OsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
  
    /* track original combinations of linksegment/downstream vertex for each connectoid possibly affected by the links
    we're about to break link (segments) if after breaking links this relation is modified, restore it by updating the
    connectoid to the correct access link segment directly upstream of the original downstream vertex identified */
    Set<TransferConnectoid> connectoidsOverlappingWithBreakLinks =
        ConnectoidUtils.findDirectedConnectoidsReferencingLinks(
            linksToBreak,
            getZoningReaderData().getPlanitConverterData().getConnectoidData().getTransferConnectoidsByLocation(
                networkLayer).values().stream().flatMap(Collection::stream));
    
    /* register additional actions on breaking link via listener for connectoid update (see above)
     * TODO: refactor this so it does not require this whole preparing of data. Ideally this is handled more elegantly
     *  than now
     */
    GraphModifierListener listener = null;
    if(!connectoidsOverlappingWithBreakLinks.isEmpty()) {
      listener = new UpdateDirectedConnectoidsOnBreakLinkSegmentHandler(connectoidsOverlappingWithBreakLinks);
      networkLayer.getLayerModifier().addListener(listener);
    }
        
    try {
      /* LOCAL TRACKING DATA CONSISTENCY  - BEFORE */
      {
        /* remove links from spatial index when they are broken up and their geometry changes, after breaking more links
        exist with smaller geometries... insert those after as replacements*/
        getZoningReaderData().getPlanitConverterData().removeLinksFromSpatialLinkIndex(linksToBreak);
      }

      /* break links and group resulting new links by original link's OSM id*/
      var referenceNetwork = getZoningReaderData().getPlanitConverterData().getReferenceNetwork();

      var newlyBrokenLinks = PlanitNetworkLayerModifierUtils.convertBrokenLinksByGroupingThemWithCustomKey(
          networkLayer.getLayerModifier().breakAt(
              linksToBreak, planitNode, referenceNetwork.getCoordinateReferenceSystem()),
          PlanitOsmUtils::parseOsmId);

      /* TRACKING DATA CONSISTENCY - AFTER */
      {
        /* insert created/updated links and their geometries to spatial index instead */
        newlyBrokenLinks.forEach( (id, links) ->
                getZoningReaderData().getPlanitConverterData().addLinksToSpatialLinkIndex(networkLayer, links));

        /* update mapping since another osmWayId now has multiple planit links and this is needed in the layer data to
        be able to find the correct planit links for (internal) osm nodes */
        layerData.updateOsmWaysWithMultiplePlanitLinks(newlyBrokenLinks);
      }
    } finally {
      if(listener != null) {
        networkLayer.getLayerModifier().removeListener(listener);
      }
    }
  }

  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with
   * access modes provided. connectoids are only created
   * when the access link segment has at least one of the allowed modes as an eligible mode
   *
   * @param connectoidExternalId external id (allowed to be null)
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param accessNode of the connectoids
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @param type         the type of the zone connectoid combination reflecting how it is envisaged to be used
   * @param verifyOsmVerticalLayerCompatibility when true we do not report back if the link segments match the
   *                                            transfer zone's OSM layer index, when false we do
   * @return created connectoids
   */
  private Collection<TransferConnectoid> createAndRegisterTransferConnectoids(
      @Nullable String connectoidExternalId,
      final TransferZone transferZone,
      final MacroscopicNetworkLayer networkLayer,
      final DirectedVertex accessNode,
      final Iterable<? extends EdgeSegment> linkSegments,
      final Set<Mode> allowedModes,
      ZoneConnectoidType type,
      boolean verifyOsmVerticalLayerCompatibility){

    if(verifyOsmVerticalLayerCompatibility) {
      var osmZoningPlanitData = getZoningReaderData().getPlanitConverterData();
      var osmNetworkLayerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);

      var osmVerticalLayerIndex = osmZoningPlanitData.getTransferZoneOsmVerticalLayerIndex(transferZone);
      if (osmVerticalLayerIndex != null &&
          !IterableUtils.asStream(linkSegments).allMatch(
              ls -> osmNetworkLayerData.getLinkVerticalLayerIndex(
                  (Link) ls.getParent()) ==
                      getZoningReaderData().getPlanitConverterData().getTransferZoneOsmVerticalLayerIndex(transferZone))) {
        LOGGER.warning(String.format("OSM vertical layer index (layer=%d) of PLANit transfer zone (%s) not " +
                "compatible with selected access link segments [%s] for its connectoids, this shouldn't happen, " +
                "verify correctness", osmVerticalLayerIndex, transferZone.getIdsAsString(),
            IterableUtils.asStream(linkSegments).map(
                ExternalIdAble::getIdsAsString).collect(Collectors.joining(","))));
      }
    }

    Collection<TransferConnectoid> createdConnectoids = ZoningConverterUtils.createAndRegisterTransferConnectoids(
        connectoidExternalId,
        getZoningReaderData().getPlanitConverterData().getReferenceZoning(),
        transferZone,
        accessNode,
        (Iterable<MacroscopicLinkSegment>) linkSegments,
        allowedModes,
        type);

    /* update PLANit data tracking information */
    createdConnectoids.forEach(
        c -> getZoningReaderData().getPlanitConverterData().getConnectoidData().
            registerNewConnectoidOnPlanitTrackingData(c, transferZone, networkLayer));
    return createdConnectoids;
  }

  /** extract the connectoid access node based on the given location. Either it already exists as a PLANit node, or it
   * is internal to an existing link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the PLANit node
   *
   * @param osmNodeLocation to collect/create PLANit node for
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not
   *                                       enforce filtering based on criteria that might not have been properly
   *                                       tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get the best
   *                                       possible match based on available tagging, e.g., vertical layer information
   * @param networkLayer to extract node on
   * @param osmWaitingAreaVerticalLayerIndex the vertical layer index indicating the vertical plane the
   *                                         connectoid is expected to reside on, may be null indicating
   *                                         it is not explicitly tagged
   * @param osmWaitingAreaId reference id of OSM waiting area to be used in case user feedback is to be provided
   * @param suppressLogging when true suppress logging, false otherwise
   * @return PLANit node collected/created
   */  
  private Node extractTransferConnectoidAccessNodeByLocation(
      Point osmNodeLocation,
      boolean locationIsKnownOsmStopPosition,
      MacroscopicNetworkLayer networkLayer,
      Integer osmWaitingAreaVerticalLayerIndex,
      String osmWaitingAreaId,
      boolean suppressLogging){
    final OsmNetworkReaderLayerData layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);

    /* check if already exists */
    Node planitNode = layerData.getPlanitNodeByLocation(osmNodeLocation);
    if(planitNode == null) {
      /* does not exist yet...create */
      
      /* find the links with the location registered as internal */
      List<MacroscopicLink> linksToBreak = layerData.findPlanitLinksWithInternalLocation(osmNodeLocation);
      if(linksToBreak != null) {
        OsmNode osmNode = layerData.getOsmNodeByLocation(osmNodeLocation);

        // we would expect all links on this connectoids location to reside on the same vertical plane as their
        // transfer zone's that is passed in, which we check since breaking links that are not
        // could be problematic and point to a tagging error, when known stop position, it is likely still correct,
        // but we report it nevertheless
        if(!suppressLogging && !linksToBreak.stream().allMatch( l ->
            (osmWaitingAreaVerticalLayerIndex!=null &&
                layerData.getLinkVerticalLayerIndex(l) == osmWaitingAreaVerticalLayerIndex) ||
            (osmWaitingAreaVerticalLayerIndex==null && layerData.getLinkVerticalLayerIndex(l) == 0))){
          var osmLayerIndices = linksToBreak.stream().map(
              l -> l.getIdsAsString() + " layer=" +
                      layerData.getLinkVerticalLayerIndex(l)).collect(Collectors.joining(","));

          Level logLevel = locationIsKnownOsmStopPosition ? Level.INFO : Level.WARNING;
          String stopPositionContext = locationIsKnownOsmStopPosition ? "Explicit" : "Deduced";
          String followUpAdvice = locationIsKnownOsmStopPosition ? "possible tagging error" : "verify correctness";
          String osmNodeRef = osmNode!=null ? String.valueOf(osmNode.getId()) : "-";
          if(osmWaitingAreaVerticalLayerIndex == null){
            LOGGER.log(logLevel, String.format("%s stop_position (%s) on location (%s) part of links [%s] differs " +
                    "from waiting area (%s) that has no OSM vertical layer defined, %s",
                stopPositionContext, osmNodeRef, osmNodeLocation, osmLayerIndices, osmWaitingAreaId, followUpAdvice));
          }else{
              LOGGER.log(logLevel, String.format("%s stop_position (%s) on location (%s) part of links [%s] differs " +
                      "from OSM waiting area's (%s) explicit OSM layer (layer=%d), %s",
                  stopPositionContext, osmNodeRef, osmNodeLocation, osmLayerIndices, osmWaitingAreaId,
                  osmWaitingAreaVerticalLayerIndex, followUpAdvice));
          }
        }

        // location is internal to an existing link, create it based on OSM node if possible, otherwise base it solely
        // on location provided
        if(osmNode != null) {
          /* all regular cases */
          planitNode = PlanitOsmNetworkLayerUtils.createPopulateAndRegisterNode(osmNode, networkLayer, layerData);
        }else {
          // special cases whenever parser decided that location required planit node even though there exists no OSM
          //node at this location
          planitNode = PlanitOsmNetworkLayerUtils.createPopulateAndRegisterNode(
              osmNodeLocation, networkLayer, layerData);
        }
        profiler.logConnectoidStatus(
            getZoningReaderData().getPlanitConverterData().getReferenceZoning().getTransferConnectoids().size());
                             
        // now perform the breaking of links at the given node and update related tracking/reference information to
        // broken link(segment)(s) where needed
        breakLinksAtPlanitNode(
            planitNode,
            networkLayer,
            linksToBreak);
      }
    }
    return planitNode;
  }

  /** extract the connectoid access node. either it already exists as a PLANit node, or it is internal to an existing
   * link. In the latter case
   * a new node is created and the existing link is broken. In the former case, we simply collect the PLANit node
   * 
   * @param osmNode to collect PLANit node version for
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not
   *                                       enforce filtering based on criteria that might not have been properly
   *                                       tagged, when false, we
   *                                     proceed applying as many filter criteria as possible to get the best
   *                                       possible match based on available tagging, e.g., vertical layer information
   * @param networkLayer to extract node on
   * @param osmVerticalLayerIndex the vertical layer index indicating the vertical plane the connectoid is
   *                              expected to reside on (may be null)
   * @param osmWaitingAreaId reference id of OSM waiting area to be used in case user feedback is to be provided
   * @param suppressLogging when true suppress logging, false otherwise
   * @return PLANit node collected/created
   */
  private Node extractTransferConnectoidAccessNodeByOsmNode(
      OsmNode osmNode,
      boolean locationIsKnownOsmStopPosition,
      MacroscopicNetworkLayer networkLayer,
      Integer osmVerticalLayerIndex,
      String osmWaitingAreaId,
      boolean suppressLogging){

    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    return extractTransferConnectoidAccessNodeByLocation(
        osmNodeLocation,
            locationIsKnownOsmStopPosition,
            networkLayer,
            osmVerticalLayerIndex,
            osmWaitingAreaId,
            suppressLogging);
  }

  /** extract a connectoid location on the link of the given access link segments: the connectoid location of each
   * segment, see
   * {@link #findVehicleStopConnectoidLocationOnLinkSegment(TransferZone, MacroscopicLinkSegment, Mode, double)}, chosen
   * among them by the distance to the waiting area, see
   * {@link ZoningConverterUtils#chooseVehicleStopConnectoidLocationOnAccessLink}. A location that is not the existing
   * coordinate closest to the waiting area is inserted into the link's geometry, projected from the waiting area's
   * centre.
   *
   * @param transferZone transfer zone to use
   * @param accessLinkSegments of one access link, the segments the stop may be on
   * @param accessMode to consider
   * @param maxAllowedStopToTransferZoneDistanceMeters the maximum allowed distance between stop and waiting area
   *                                                   that we allow
   * @param networkLayer the link is registered on
   * @return connectoid location to use, may or may not be an existing osm node location, null when none is found
   */
  private Point extractTransferConnectoidLocationStandAloneTransferZoneOnAccessLinkSegments(
      TransferZone transferZone,
      SortedSet<MacroscopicLinkSegment> accessLinkSegments,
      Mode accessMode,
      double maxAllowedStopToTransferZoneDistanceMeters,
      MacroscopicNetworkLayer networkLayer) {

    var accessLink = accessLinkSegments.first().getParent();
    Point connectoidLocation = ZoningConverterUtils.chooseVehicleStopConnectoidLocationOnAccessLink(
        accessLinkSegments,
        accessLinkSegment -> {
          var locationOnSegment = findVehicleStopConnectoidLocationOnLinkSegment(
              transferZone, accessLinkSegment, accessMode, maxAllowedStopToTransferZoneDistanceMeters);
          return locationOnSegment == null ? null : locationOnSegment.getLocation();
        },
        location -> getGeoUtils().getClosestDistanceInMeters(location.getCoordinate(), transferZone.getGeometry(true)),
        (accessLinkSegment, location) -> false);

    if(connectoidLocation !=null) {

      /* in case identified projected location is not identical to an existing shape point or extreme point of the link,
      insert it into the geometry */
      Coordinate closestExistingCoordinate = getGeoUtils().getClosestExistingLineStringCoordinateToGeometry(
              transferZone.getGeometry(), accessLink.getGeometry());
      if( !closestExistingCoordinate.equals2D(connectoidLocation.getCoordinate())) {

        /* add projected location to geometry of link, projected from the waiting area's centre as the location is */
        LinearLocation projectedLinearLocationOnLink =
                PlanitEntityGeoUtils.extractClosestProjectedLinearLocationToGeometryFromEdge(
                        PlanitJtsUtils.extractCentre(transferZone.getGeometry(true)), accessLink, getGeoUtils());
        accessLink.updateGeometryInjectCoordinateAtProjectedLocation(projectedLinearLocationOnLink);

        /* new location must be marked as internal to link, otherwise the link will not be broken when extracting
        connectoids at this location*/
        getNetworkToZoningData().getNetworkLayerData(networkLayer).registerLocationAsInternalToPlanitLink(
                connectoidLocation, accessLink);
      }
    }

    return connectoidLocation;
  }

  /** Constructor 
   *
   * @param zoningReaderData to use
   * @param network2ZoningData data transferred from parsing network to be used by zoning reader.
   * @param transferSettings to use
   * @param profiler to use
   */
  public OsmTransferConnectoidHelper(
      final OsmZoningReaderData zoningReaderData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      OsmPublicTransportReaderSettings transferSettings,
      OsmZoningHandlerProfiler profiler) {
    super(zoningReaderData, network2ZoningData, transferSettings);

    this.profiler = profiler;

    // functions to be passed in PLANit generic utils classes used during parsing of waiting areas (transfer zones)
    {
      /* function that takes a node and collects any overwritten waiting area that is pre-specified for it. Used to
       *  override default mapping between waiting area and stop location when needed */
      this.getOverwrittenWaitingAreaSourceIdForNode = n -> {
        var result = transferSettings.getOverwrittenWaitingAreaOfStopLocation(
                n.getExternalId() != null ? PlanitOsmUtils.parseOsmId(n) : null);
        return result!= null ? String.valueOf(result.second()) : null;
      };
    }
  }

  /**
   * Find the access link segments of the given links for a stand-alone transfer zone, with the user's overwrites of
   * the default connectoid location applied, see
   * {@link ZoningConverterUtils#findAccessLinkSegmentsForWaitingAreaOnLinks}
   *
   * @param transferZone of the waiting area
   * @param links to find access link segments on
   * @param accessMode to find access link segments for
   * @return the access link segments found, in id order
   */
  public SortedSet<MacroscopicLinkSegment> findAccessLinkSegmentsForWaitingAreaOnLinks(
      TransferZone transferZone, Collection<MacroscopicLink> links, Mode accessMode) {
    return ZoningConverterUtils.findAccessLinkSegmentsForWaitingAreaOnLinks(
        transferZone.getExternalId(),
        transferZone.getGeometry(true),
        links,
        ExternalIdAble::getExternalId,
        List.of(accessMode),
        mode -> createNominatedAccessLinkSourceIdMapping(transferZone),
        getOverwrittenWaitingAreaSourceIdForNode,
        getSettings().getCountryName(),
        getGeoUtils());
  }

  /**
   * Find where the stop of a stand-alone transfer zone goes on an access link segment, with the user's overwrites of
   * the default connectoid location applied, see
   * {@link ZoningConverterUtils#findVehicleStopConnectoidLocationForWaitingAreaOnLinkSegment}
   *
   * @param transferZone of the waiting area
   * @param accessLinkSegment to find the location on
   * @param accessMode the stop serves
   * @param maxAllowedDistanceMeters the maximum allowed distance between stop and waiting area
   * @return the connectoid location, null when the segment gives none
   */
  public VehicleStopConnectoidLocationOnLinkSegment findVehicleStopConnectoidLocationOnLinkSegment(
      TransferZone transferZone, MacroscopicLinkSegment accessLinkSegment, Mode accessMode,
      double maxAllowedDistanceMeters) {
    return ZoningConverterUtils.findVehicleStopConnectoidLocationForWaitingAreaOnLinkSegment(
        transferZone.getExternalId(),
        transferZone.getGeometry(true),
        accessLinkSegment,
        accessLinkSegment.getParent().getExternalId(),
        accessMode,
        maxAllowedDistanceMeters,
        getSettings().getMaxEndNodeToIdealStopPointMeters(),
        getSettings().getMaxShapePointToIdealStopPointMeters(),
        getOverwrittenWaitingAreaSourceIdForNode,
        createOverwrittenWaitingAreaSourceIdForPointMapping(accessMode),
        createNominatedAccessLinkSourceIdMapping(transferZone),
        getSettings().getCountryName(),
        getGeoUtils());
  }

  /**
   * Create a connectoid or expand an existing connectoid with given mode if it exists for given transfer zone
   * and provided parameters.
   *
   * @param connectoidExternalId external id (allowed to be null)
   * @param transferZone to add connectoid for
   * @param planitMode mode to support
   * @param accessVertex access node to use
   * @param eligibleLinkSegments access link segments to create connectoids for
   * @param type         the type of the zone connectoid combination reflecting how it is envisaged to be used
   * @param ignoreOsmVerticalLayerCompatibilityCheck flag to ignore vertical layer compatibility
   * @param suppressLogging flag to suppress logging
   * @return true if success, false otherwise
   */
  public boolean extractTransferConnectoidsForModeLinkSegments(
      @Nullable String connectoidExternalId,
      TransferZone transferZone,
      Mode planitMode,
      DirectedVertex accessVertex,
      Collection<LinkSegment> eligibleLinkSegments,
      ZoneConnectoidType type,
      boolean ignoreOsmVerticalLayerCompatibilityCheck,
      boolean suppressLogging) {

    var referenceNetwork = getZoningReaderData().getPlanitConverterData().getReferenceNetwork();
    MacroscopicNetworkLayer networkLayer = referenceNetwork.getLayerByMode(planitMode);
    for(EdgeSegment edgeSegment : eligibleLinkSegments) {

      /* update accessible link segments of already created connectoids (if any) */
      Point proposedLocation = accessVertex.getPosition();
      boolean createConnectoidsForLinkSegment = true;

      if(getZoningReaderData().getPlanitConverterData().getConnectoidData().hasTransferConnectoidForLocation(
              networkLayer, proposedLocation)) {
        /* existing connectoid: update model eligibility */
        Collection<TransferConnectoid> connectoidsForNode =
            getZoningReaderData().getPlanitConverterData().getConnectoidData().getTransferConnectoidsByLocation(
                proposedLocation, networkLayer);
        for(TransferConnectoid connectoid : connectoidsForNode) {
          /* update zone-segment-mode eligibility */
          boolean forceAllowMode = true; // assumed we have verified this beforehand to be desirable
          updateTransferConnectoidWithDirectedAccess(
              connectoid, transferZone, edgeSegment, Collections.singleton(planitMode), type, forceAllowMode);
          createConnectoidsForLinkSegment  = false;
          break;
        }
      }

      /* for remaining access link segments without connectoid -> create them */
      if(createConnectoidsForLinkSegment) {

        /* create and register */
        Collection<TransferConnectoid> newConnectoids = createAndRegisterTransferConnectoids(
            connectoidExternalId,
            transferZone,
            networkLayer,
            accessVertex,
            Collections.singleton(edgeSegment),
            Collections.singleton(planitMode),
            type,
            ignoreOsmVerticalLayerCompatibilityCheck || suppressLogging);

        if(!suppressLogging && (newConnectoids==null || newConnectoids.isEmpty())) {
          LOGGER.warning(String.format("Found eligible mode %s for stop_location of transfer zone %s, but no " +
              "access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
          return false;
        }
      }
    }

    return true;
  }

  /** Create directed connectoids for transfer zones that reside on OSM ways. For such transfer zones, we simply
   * create connectoids in both directions for all eligible incoming link segments. This is a special case because
   * due to residing on the OSM way it is not possible to distinguish what intended direction of the OSM way is
   * serviced (it is neither left nor right of the way). Therefore, any attempt to extract this information
   * is bypassed here.
   *
   * @param connectoidExternalId external id (allowed to be null)
   * @param transferZone residing on an osm way
   * @param designatedOsmConnectoidNode the OSM node that we should use for the connectoid
   * @param networkLayer related to the mode
   * @param planitModeType the connectoid is accessible for
   * @param type         the type of the zone connectoid combination reflecting how it is envisaged to be used
   * @return created connectoids, null if it was not possible to create any due to some reason
   */
  public Collection<TransferConnectoid> createAndRegisterTransferConnectoidsOnTopOfTransferZone(
      @Nullable String connectoidExternalId,
      @Nonnull TransferZone transferZone,
      @Nonnull OsmNode designatedOsmConnectoidNode,
      @Nonnull MacroscopicNetworkLayer networkLayer,
      @Nonnull PredefinedModeType planitModeType,
      @Nonnull ZoneConnectoidType type){

    boolean suppressLogging = false;
    Node accessNode = null;
    Iterable<? extends EdgeSegment> nominatedLinkSegments = null;
    var referenceNetwork = getZoningReaderData().getPlanitConverterData().getReferenceNetwork();

    /* user overwrite */
    if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(
            designatedOsmConnectoidNode.getId(), EntityType.Node)) {

      long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(
              designatedOsmConnectoidNode.getId(), EntityType.Node);
      Link nominatedLink = PlanitLinkOsmUtils.getClosestLinkWithOsmWayIdToGeometry(
              osmWayId, OsmNodeUtils.createPoint(designatedOsmConnectoidNode), networkLayer, getGeoUtils());
      if(nominatedLink == null) {
        LOGGER.severe(String.format("IGNORE: User nominated OSM way not available for waiting area " +
                "on road infrastructure %d", osmWayId));
        return null;
      }

      nominatedLinkSegments = nominatedLink.getEdgeSegments();
      suppressLogging = true;

      /* choose closest access node */
      double nodeADistance = getGeoUtils().getDistanceInMetres(
              nominatedLink.getVertexA().getPosition().getCoordinate(),
              OsmNodeUtils.createCoordinate(designatedOsmConnectoidNode));
      double nodeBDistance = getGeoUtils().getDistanceInMetres(
              nominatedLink.getVertexB().getPosition().getCoordinate(),
              OsmNodeUtils.createCoordinate(designatedOsmConnectoidNode));
      accessNode = nodeADistance < nodeBDistance ? nominatedLink.getNodeA() : nominatedLink.getNodeB();
      
    }else { /* regular approach */
      
      /* create/collect PLANit node with access link segment (no need to check layer on links here since we know
      transfer zone coincides with network) */
      var waitingAreaOsmVerticalLayerIndex =
              getZoningReaderData().getPlanitConverterData().getTransferZoneOsmVerticalLayerIndex(transferZone);

      boolean locationIsKnownOsmStopPosition = true;
      accessNode = extractTransferConnectoidAccessNodeByOsmNode(
              designatedOsmConnectoidNode,
              locationIsKnownOsmStopPosition,
              networkLayer,
              waitingAreaOsmVerticalLayerIndex,
              transferZone.getExternalId(),
              suppressLogging);
      if(accessNode == null) {
        LOGGER.warning(String.format("DISCARD: OSM node (%d) could not be converted to access node for transfer " +
            "zone OSM entity %s at same location",designatedOsmConnectoidNode.getId(), transferZone.getExternalId()));
        return null;
      }
      
      nominatedLinkSegments = accessNode.getEntryEdgeSegments();
    }
    
    /* connectoid(s) */
        
    /* create connectoids on top of transfer zone */
    /* since located on OSM way we cannot deduce direction of the stop, so create connectoid for both incoming
    directions (if present), so we can service any line using the way */
    boolean ignoreOsmVerticalLayerCompatibility = suppressLogging;
    return createAndRegisterTransferConnectoids(
        connectoidExternalId,
        transferZone,
        networkLayer,
        accessNode,
        nominatedLinkSegments,
        Collections.singleton(referenceNetwork.getModes().get(planitModeType)),
        type,
        ignoreOsmVerticalLayerCompatibility);
  }


  /** create and/or update directed connectoids for the given mode and layer based on the passed in location where the
   * connectoids access link segments are extracted for. Each of the connectoids is related to the passed in
   * transfer zone. Generally a single connectoid is created for the most likely link segment identified, i.e., if
   * the transfer zone is placed on the left of the infrastructure, the closest by incoming link segment to the
   * given location is used. Since the geometry of a link applies to both link segments we define closest based on
   * the driving position of the country, so a left-hand drive country will use the incoming link segment where
   * the transfer zone is placed on the left, etc.
   *
   * @param connectoidExternalId external id (allowed to be null)
   * @param location to create the access point for as PLANit node (one or more upstream planit link segments will act
   *                 as access link segment for the created connectoid(s))
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not
   *                                       enforce filtering based on criteria that might not have been properly tagged,
   *                                       when false, we proceed applying as many filter criteria as possible to
   *                                       get best possible match based on available tagging, e.g., vertical layer
   *                                       information
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitModeType mode type this connectoid is allowed access for
   * @param type         the type of the zone connectoid combination reflecting how it is envisaged to be used
   * @param suppressLogging when true do not log anything, false otherwise
   * @return true when one or more connectoids have successfully been generated or existing connectoids have be
   * reused, false otherwise
   */
  public boolean extractTransferConnectoidsForMode(
      @Nullable String connectoidExternalId,
      Point location,
      boolean locationIsKnownOsmStopPosition,
      TransferZone transferZone,
      PredefinedModeType planitModeType,
      ZoneConnectoidType type,
      boolean suppressLogging) {
    if(location == null || transferZone == null || planitModeType == null) {
      return false;
    }

    var referenceNetwork = getZoningReaderData().getPlanitConverterData().getReferenceNetwork();
    var planitMode = referenceNetwork.getModes().get(planitModeType);
    MacroscopicNetworkLayer networkLayer = referenceNetwork.getLayerByMode(planitMode);
    var layerData = getNetworkToZoningData().getNetworkLayerData(networkLayer);
    OsmNode osmNode = layerData.getOsmNodeByLocation(location);

    /* identify vertical plane the location resides on, but only use it if 1) explicit on the zone, or 2) unanimous on
    eligible links but only use that if the stop position location is not explicitly known, otherwise it is
    too unreliable to use */
    var waitingAreaOsmVerticalLayerIndex =
            getZoningReaderData().getPlanitConverterData().getTransferZoneOsmVerticalLayerIndex(transferZone);
    if(waitingAreaOsmVerticalLayerIndex == null && !locationIsKnownOsmStopPosition){
      var linkBasedResult = findOsmVerticalLayerIndexByStopPositionPlanitLinks(location, networkLayer);
      if(linkBasedResult != null && linkBasedResult.second()){
        /* unanimous result, so replace finding */
        waitingAreaOsmVerticalLayerIndex = linkBasedResult.first();
      }
    }

    /* planit access node */
    Node planitAccessNode = extractTransferConnectoidAccessNodeByLocation(
            location,
            locationIsKnownOsmStopPosition,
            networkLayer,
            waitingAreaOsmVerticalLayerIndex,
            transferZone.getExternalId(),
            suppressLogging);
    if(planitAccessNode==null) {
      if(osmNode != null) {
        if(!suppressLogging)
          LOGGER.warning(String.format("DISCARD: OSM node %d could not be converted to access node for transfer zone " +
                  "representation of OSM entity %s",osmNode.getId(), transferZone.getExternalId()));
      }else {
        if(!suppressLogging)
          LOGGER.warning(String.format("DISCARD: Location (%s) could not be converted to access node for transfer " +
                  "zone representation of OSM entity %s",location, transferZone.getExternalId()));
      }
      return false;
    }
    
    /* must avoid cross traffic when:
     * 1) stop position does not coincide with transfer zone, i.e., waiting area is not on the road/rail, and
     * 2) mode requires waiting area to be on a specific side of the road, e.g. buses can only open doors on one side,
     * so it matters for them, but not for train
     */
    boolean mustAvoidCrossingTraffic = !planitAccessNode.getPosition().equalsTopo(transferZone.getGeometry());
    if(mustAvoidCrossingTraffic) {
      mustAvoidCrossingTraffic = isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation(
          planitMode, transferZone, osmNode!= null ? osmNode.getId() : null, getSettings());
    }

    /* find access link segments */
    Collection<LinkSegment> accessLinkSegments = null;
    boolean ignoreOsmVerticalLayerCompatibility = locationIsKnownOsmStopPosition;
    for(MacroscopicLink link : planitAccessNode.<MacroscopicLink>getLinks()) {
      Collection<LinkSegment> linkAccessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(
          transferZone,
          link,
          planitAccessNode,
          planitMode,
          mustAvoidCrossingTraffic,
          ignoreOsmVerticalLayerCompatibility);

      if(linkAccessLinkSegments != null && !linkAccessLinkSegments.isEmpty()) {
        if(accessLinkSegments == null) {
          accessLinkSegments = linkAccessLinkSegments;
        }else {
          accessLinkSegments.addAll(linkAccessLinkSegments);
        }
      }
    }    
      
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      if(!suppressLogging) LOGGER.warning(String.format(
          "DISCARD platform/pole/station %s its stop_location %s deemed invalid, no access link segment found due to " +
                  "mode inaccessibility/exclusion, or on wrong side of road/rail, verify correctness",
              transferZone.getExternalId(), location));
      return false;
    }                           
    
    /* connectoids for link segments */
    return extractTransferConnectoidsForModeLinkSegments(
        connectoidExternalId,
        transferZone,
        planitMode,
        planitAccessNode,
        accessLinkSegments,
        type,
        ignoreOsmVerticalLayerCompatibility,
        suppressLogging
    );
  }


  /** see {@link #extractTransferConnectoidsForMode(String, Point, boolean, TransferZone, PredefinedModeType,
   * ZoneConnectoidType, boolean)} converting node to point
   *
   * @param osmNode to create the access point for as PLANit node (one or more upstream planit link segments will act
   *                as access link segment for the created connectoid(s))
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not
   *                                       enforce filtering based on criteria that might not have been properly tagged,
   *                                       when false, we proceed applying as many filter criteria as possible to get
   *                                       the best possible match based on available tagging, e.g., vertical layer
   *                                       information
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitModeType mode type this connectoid is allowed access for
   * @param type         the type of the zone connectoid combination reflecting how it is envisaged to be used
   * @param suppressLogging when true do not log anything, false otherwise
   * @return true when one or more connectoids have successfully been generated or existing connectoids have been
   *  reused, false otherwise
   */
   public boolean extractTransferConnectoidsForMode(
      OsmNode osmNode,
      boolean locationIsKnownOsmStopPosition,
      TransferZone transferZone,
      PredefinedModeType planitModeType,
      ZoneConnectoidType type,
      boolean suppressLogging){
    Point osmNodeLocation = OsmNodeUtils.createPoint(osmNode);
    return extractTransferConnectoidsForMode(
        String.valueOf(osmNode.getId()),
        osmNodeLocation,
        locationIsKnownOsmStopPosition,
        transferZone,
        planitModeType,
        type,
        suppressLogging);
  }
  
  /** create and/or update directed connectoids for the transfer zones and mode combinations when eligible, based on
   * the passed in OSM node where the connectoids access link segments are extracted from
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param locationIsKnownOsmStopPosition when true the location provided is tagged explicitly, meaning we do not
   *                                       enforce filtering based on criteria that might not have been properly tagged,
   *                                       when false, we proceed applying as many filter criteria as possible to get
   *                                       the best possible match based on available tagging, e.g., vertical layer
   *                                       information
   * @param transferZones connectoids are assumed to provide access to
   * @param planitModeTypes this connectoid is allowed access for
   * @param transferZoneGroup it belongs to, when zone is not yet in the group the zone is added to the group
   *                          (group is allowed to be null)
   * @param suppressLogging when true, suppress logging, otherwise do not
   * @return true when at least connectoids where created for one of the transfer zones identified
   */
  public boolean extractTransferConnectoids(
      OsmNode osmNode,
      boolean locationIsKnownOsmStopPosition,
      Collection<TransferZone> transferZones,
      Collection<PredefinedModeType> planitModeTypes,
      TransferZoneGroup transferZoneGroup,
      boolean suppressLogging){
    boolean success = false; 

    /* for the given layer/mode combination, extract connectoids by linking them to the provided transfer zones */
    var referenceNetwork = getZoningReaderData().getPlanitConverterData().getReferenceNetwork();
    for(var modeType : planitModeTypes) {
      
      /* layer */
      MacroscopicNetworkLayer networkLayer = referenceNetwork.getLayerByPredefinedModeType(modeType);
      if(!getNetworkToZoningData().getNetworkLayerData(networkLayer).isOsmNodePresentInLayer(osmNode)
              && !suppressLogging) {
        LOGGER.warning(
            String.format("DISCARD: stop_position %d not present in network layer for %s (residing road type " +
                "deactivated or node dangling)",osmNode.getId(), modeType));
        continue;
      }
      
      /* transfer zone */
      for(TransferZone transferZone : transferZones) {
        
        /* connectoid(s) of type PT STOP*/
        success = extractTransferConnectoidsForMode(
            osmNode,
            locationIsKnownOsmStopPosition,
            transferZone,
            modeType,
            ZoneConnectoidType.PT_VEHICLE_STOP,
            suppressLogging) || success;
        if(success && transferZoneGroup != null && !transferZone.isInTransferZoneGroup(transferZoneGroup)) {
          /* in some rare cases only the stop locations are part of the stop_area, but not the platforms next to
          the road/rail, only then this situation is triggered and we salvage the situation */
          if(!suppressLogging && !transferZone.getExternalId().equals(String.valueOf(osmNode.getId()))){
            LOGGER.info(String.format("Platform/pole %s identified for stop_position %d, platform/pole not in " +
                    "stop_area %s of stop_position, added it",
                    transferZone.getExternalId(), osmNode.getId(), transferZoneGroup.getExternalId()));
          }
          transferZoneGroup.addTransferZone(transferZone);
        }
      }      
    }
    
    return success;
  }  
  
  /** create connectoids on an access link whose access link segments are not known yet, such as a link the user
   * nominated: its access link segments are found first, see
   * {@link #findAccessLinkSegmentsForWaitingAreaOnLinks(TransferZone, Collection, Mode)}, and the connectoids are
   * created on them, see {@link #extractTransferConnectoidsForStandAloneTransferZoneByAccessLinkSegments}.
   *
   * @param osmWaitingAreaId the waiting area pertains to
   * @param waitingAreaGeometry geometry of the waiting area
   * @param accessLink to create connectoids on by breaking it
   * @param transferZone to register connectoids on
   * @param planitAccessModeType eligible mode type for the station
   * @param maxAllowedStopToTransferZoneDistanceMeters the maximum allowed distance between stop and waiting area that
   *                                                   we allow
   * @param networkLayer the modes relate to
   * @param suppressLogging when true suppress logging, false otherwise
   */
  public void extractTransferConnectoidsForStandAloneTransferZoneByPlanitLink(
      long osmWaitingAreaId,
      Geometry waitingAreaGeometry ,
      MacroscopicLink accessLink,
      TransferZone transferZone,
      PredefinedModeType planitAccessModeType,
      double maxAllowedStopToTransferZoneDistanceMeters,
      MacroscopicNetworkLayer networkLayer,
      boolean suppressLogging) {
    var accessMode = getZoningReaderData().getPlanitConverterData().getReferenceNetwork().getModes().get(
        planitAccessModeType);
    var accessLinkSegments = findAccessLinkSegmentsForWaitingAreaOnLinks(transferZone, List.of(accessLink), accessMode);
    if(accessLinkSegments.isEmpty()) {
      if(!suppressLogging) {
        logVehicleStopConnectoidLocationNotCreated(accessLink, transferZone);
      }
      return;
    }
    extractTransferConnectoidsForStandAloneTransferZoneByAccessLinkSegments(osmWaitingAreaId, waitingAreaGeometry,
        accessLinkSegments, transferZone, planitAccessModeType, maxAllowedStopToTransferZoneDistanceMeters,
        networkLayer, suppressLogging);
  }

  /** create connectoids not based on OSM node location but based on auto-generated geographic location on the link of
   * the provided access link segments, by finding either a close enough existing coordinate (OSM node), or if not close
   * enough a newly created coordinate at the appropriate position. Then create connectoids accordingly by breaking the
   * link in these locations. This is assumed to become connectoids for a vehicle stop location of some sort.
   *
   * @param osmWaitingAreaId the waiting area pertains to
   * @param waitingAreaGeometry geometry of the waiting area
   * @param accessLinkSegments of one access link, the segments the stop may be on, not empty, see
   *                           {@link #findAccessLinkSegmentsForWaitingAreaOnLinks(TransferZone, Collection, Mode)}
   * @param transferZone to register connectoids on
   * @param planitAccessModeType eligible mode type for the station
   * @param maxAllowedStopToTransferZoneDistanceMeters the maximum allowed distance between stop and waiting area that
   *                                                   we allow
   * @param networkLayer the modes relate to
   * @param suppressLogging when true suppress logging, false otherwise
   */
  public void extractTransferConnectoidsForStandAloneTransferZoneByAccessLinkSegments(
      long osmWaitingAreaId,
      Geometry waitingAreaGeometry,
      SortedSet<MacroscopicLinkSegment> accessLinkSegments,
      TransferZone transferZone,
      PredefinedModeType planitAccessModeType,
      double maxAllowedStopToTransferZoneDistanceMeters,
      MacroscopicNetworkLayer networkLayer,
      boolean suppressLogging) {

    /* geolocation on planit link, possibly inserted for this purpose by this method if no viable osm node/existing
    coordinate is present */
    var accessMode = getZoningReaderData().getPlanitConverterData().getReferenceNetwork().getModes().get(
        planitAccessModeType);
    Point connectoidLocation = extractTransferConnectoidLocationStandAloneTransferZoneOnAccessLinkSegments(
        transferZone, accessLinkSegments, accessMode, maxAllowedStopToTransferZoneDistanceMeters, networkLayer);
    if(!suppressLogging && connectoidLocation == null) {
      logVehicleStopConnectoidLocationNotCreated(accessLinkSegments.first().getParent(), transferZone);
    }
    
    /* special case - user overwrite verification */
    OsmNode osmStopLocationNode =
            getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(connectoidLocation);
    if(osmStopLocationNode != null && getSettings().isOverwriteWaitingAreaOfStopLocation(osmStopLocationNode.getId())) {
      /* user has chosen to overwrite waiting area for this connectoid (stop_location), so the transfer zone provided
      should correspond to the chosen waiting area id, otherwise we simply ignore and return (when processing
      incomplete transfer zones, it might try to use a stop_location for a transfer zone that is incomplete but
      indicated by the user to not be used for this connectoid, so there can be a valid reason why this method is
      invoked, as well as a valid reason to not create connectoids when checking for this situation */
      Pair<EntityType, Long>  overwriteResult =
              getSettings().getOverwrittenWaitingAreaOfStopLocation(osmStopLocationNode.getId());
      /* when type match (point=node, otherwise=way)  and id match we can continue, otherwise not */
      if( !(waitingAreaGeometry instanceof Point &&
              PlanitOsmUtils.parseOsmId(transferZone) == overwriteResult.second())) {
        return;
      }else if( PlanitOsmUtils.parseOsmId(transferZone) != overwriteResult.second()) {
        return;
      }
      suppressLogging = true;
    }            
          
    /* create connectoids at identified location for mode and restricted to the accessLink identified (or update
    existing connectoid with mode access if valid) */
    boolean locationIsKnownOsmStopPosition = false;
    String ConnectoidExternalId = osmStopLocationNode!= null ? String.valueOf(osmStopLocationNode.getId()) : null;
    extractTransferConnectoidsForMode(
            ConnectoidExternalId,
            connectoidLocation,
            locationIsKnownOsmStopPosition,
            transferZone,
            planitAccessModeType,
            ZoneConnectoidType.PT_VEHICLE_STOP,
            suppressLogging);
  }

}

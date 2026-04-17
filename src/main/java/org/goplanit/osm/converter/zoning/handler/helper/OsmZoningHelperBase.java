package org.goplanit.osm.converter.zoning.handler.helper;

import org.goplanit.osm.converter.network.data.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReaderData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.PlanitNetworkLayerUtils;
import org.goplanit.utils.geo.PlanitEntityGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.directed.DirectedVertex;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Base class for all parser classes targeting support for parsing a specific PLANit zoning related entity (connectoid, transfer zone etc.)
 * This base class provides common funcionality to be made available to all parsers deriving from it
 *    
 * @author markr
 *
 */
class OsmZoningHelperBase {

  /** the logger  to use */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningHelperBase.class.getCanonicalName());
  
  /** settings to adhere to */
  private final OsmPublicTransportReaderSettings transferSettings;

  /** network data used by zoning reader/handler/helper */
  private final OsmNetworkToZoningReaderData network2ZoningData;

  /** reference network to use */
  private final PlanitOsmNetwork referenceNetwork;

  /** the zoning to work on */
  private final Zoning zoning;

  /** zoning reader data used to track created entities */
  private final OsmZoningReaderData zoningReaderData;

  /** utilities for geographic information */
  private final PlanitJtsCrsUtils geoUtils;

  /** Collect the pt settings
   * 
   * @return public transport settings
   */
  protected OsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }

  /**
   * Get reference network at hand
   * @return network
   */
  protected PlanitOsmNetwork getReferenceNetwork(){
    return referenceNetwork;
  }

  protected Zoning getZoning(){
    return zoning;
  }

  protected OsmZoningReaderData getZoningReaderData(){
    return zoningReaderData;
  }

  /** access to geo utils
   *
   * @return the utils
   */
  protected PlanitJtsCrsUtils getGeoUtils(){
    return geoUtils;
  }

  /**
   * Collect network to zoning data ported over to be made available
   * @return instance of OsmNetworkToZoningReaderData
   */
  protected OsmNetworkToZoningReaderData getNetworkToZoningData(){
    return network2ZoningData;
  }

  /** Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   * 
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) {
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(osmNodeId , referenceNetwork, network2ZoningData);
  }

  /**
   * Based on the location of the stop position, determine if the PLANit links that it resides on, or borders or
   * reside in a particular layer. If so, the OSM vertical layer index is retrieved and provided. If inconsistent
   * indices are found across the links the user is warned, if no matching links are known on the layer null is returned.
   *
   * @param stopPositionLocation  to find layer index for
   * @param layer to check
   * @return OSM vertical layer index found, and boolean indicating if the found layer index was the same across
   *  all eligible links (true), false otherwise
   */
  protected Pair<Integer,Boolean> findOsmVerticalLayerIndexByStopPositionPlanitLinks(
      Point stopPositionLocation, NetworkLayer layer) {
    var layerData = getNetworkToZoningData().getNetworkLayerData(layer);

    Collection<MacroscopicLink> planitLinks = layerData.findPlanitLinksWithInternalLocation(stopPositionLocation);
    if(planitLinks==null || planitLinks.isEmpty()) {
      var planitNode = layerData.getPlanitNodeByLocation(stopPositionLocation);
      if (planitNode != null && planitNode.hasLinks()) {
        planitLinks = planitNode.getLinks();
      }
    }

    if(planitLinks!=null && !planitLinks.isEmpty()) {
      final int verticalLayerIndex = layerData.getMostFrequentVerticalLayerIndex(planitLinks);
      final boolean consistent = planitLinks.stream().allMatch(
          l -> layerData.getLinkVerticalLayerIndex(l) == verticalLayerIndex);
      return Pair.of(verticalLayerIndex, consistent);
    }

    return  null;
  }

  /** Constructor 
   *
   * @param referenceNetwork to use
   * @param zoning to use
   * @param zoningReaderData to use
   * @param network2ZoningData to use
   * @param transferSettings to use
   */
  protected OsmZoningHelperBase(
      final PlanitOsmNetwork referenceNetwork,
      final Zoning zoning,
      final OsmZoningReaderData zoningReaderData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      final OsmPublicTransportReaderSettings transferSettings) {
    this.transferSettings = transferSettings;
    this.network2ZoningData = network2ZoningData;
    this.referenceNetwork = referenceNetwork;

    this.zoning = zoning;
    this.zoningReaderData = zoningReaderData;

    /* gis initialisation */
    this.geoUtils = new PlanitJtsCrsUtils(referenceNetwork.getCoordinateReferenceSystem());
  }

  /**
   * Find all nearby links within given search radius of geometry that do not have any banned modes but will have at
   * least one of the supported modes
   *
   * @param geometry geometry to use
   * @param bannedModes to consider
   * @param supportedModes to consider
   * @param maxSearchRadius to constrain by
   * @return found links
   */
  public Collection<MacroscopicLink> findNearbyModeCompatibleLinks(
          Geometry geometry,
          Collection<Mode> bannedModes,
          Collection<Mode> supportedModes,
          double maxSearchRadius){

    // assume single layer
    var networkLayer = this.getReferenceNetwork().getLayerByMode(supportedModes.iterator().next());
    var boundingBox = getGeoUtils().createBoundingBox(
            geometry.getEnvelopeInternal(), maxSearchRadius);

    Collection<MacroscopicLink> spatiallyMatchedLinks =
            getZoningReaderData().getPlanitData().findLinksSpatially(networkLayer, boundingBox);
    // reduce to any active mode supporting links that are not rail-based links
    spatiallyMatchedLinks.removeIf(l -> !l.isAnyModeAllowedOnAnySegment(supportedModes) ||
            l.isAnyModeAllowedOnAnySegment(bannedModes));

    return spatiallyMatchedLinks;
  }

  /**
   * Find closest mode compatible node (so any attached link with an entry segment supporting the mode constraints)
   * of provided links that do not have any banned modes but will have at
   * least one of the supported modes
   *
   * @param geometry reference centroid of geometry used
   * @param mode to check
   * @param eligibleLinks links to consider
   * @param maxSearchRadius only consider if within search radius
   * @param suppressSpatialWarnings flag for logging
   * @return pair of closest node and distance (null if not available)
   */
  public Pair<DirectedVertex, Double> findClosestModeCompatibleNode(
          Geometry geometry,
          Mode mode,
          Collection<MacroscopicLink> eligibleLinks,
          double maxSearchRadius,
          boolean suppressSpatialWarnings){

    var refCoord =  geometry.getCentroid().getCoordinate();
    var modeCompatibleNearbyLinks = eligibleLinks.stream().filter(
            l -> l.isModeAllowedOnAnySegment(mode)).collect(Collectors.toList());
    var closestLinkWithDistance = PlanitEntityGeoUtils.findPlanitEntityClosest(
            refCoord,
            modeCompatibleNearbyLinks, maxSearchRadius, suppressSpatialWarnings, getGeoUtils());
    if(closestLinkWithDistance == null){
      return null;
    }

    // determine preferred access node based on closeness
    return PlanitEntityGeoUtils.findPlanitEntityClosest(
            refCoord,
            Set.of(closestLinkWithDistance.first().getVertexA(),closestLinkWithDistance.first().getVertexB()),
            maxSearchRadius, suppressSpatialWarnings, getGeoUtils());
  }
    
}

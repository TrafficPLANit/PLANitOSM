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
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.zoning.Zoning;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for all parser classes targeting support for parsing a specific PLANit zoning related entity
 * (connectoid, transfer zone etc.) This base class provides common functionality to be made available to all
 * parsers deriving from it
 *    
 * @author markr
 *
 */
class OsmZoningHelperBase {

  /**
   * the logger  to use
   */
  private static final Logger LOGGER = Logger.getLogger(OsmZoningHelperBase.class.getCanonicalName());

  /**
   * settings to adhere to
   */
  private final OsmPublicTransportReaderSettings transferSettings;

  /**
   * network data used by zoning reader/handler/helper
   */
  private final OsmNetworkToZoningReaderData network2ZoningData;

  /**
   * zoning reader data used to track created entities
   */
  private final OsmZoningReaderData zoningReaderData;

  /**
   * Collect the pt settings
   *
   * @return public transport settings
   */
  protected OsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }


  protected OsmZoningReaderData getZoningReaderData() {
    return zoningReaderData;
  }

  /**
   * access to geo utils
   *
   * @return the utils
   */
  protected PlanitJtsCrsUtils getGeoUtils() {
    return getZoningReaderData().getPlanitConverterData().getGeoUtils();
  }

  /**
   * Collect network to zoning data ported over to be made available
   *
   * @return instance of OsmNetworkToZoningReaderData
   */
  protected OsmNetworkToZoningReaderData getNetworkToZoningData() {
    return network2ZoningData;
  }

  /**
   * Verify if there exist any layers where the node is active either as an extreme node or internal to a PLANit link
   *
   * @param osmNodeId to use
   * @return true when one or more layers are found, false otherwise
   */
  protected boolean hasNetworkLayersWithActiveOsmNode(long osmNodeId) {
    return PlanitNetworkLayerUtils.hasNetworkLayersWithActiveOsmNode(
        osmNodeId, getZoningReaderData().getPlanitConverterData().getReferenceNetwork(), network2ZoningData);
  }

  /**
   * Based on the location of the stop position, determine if the PLANit links that it resides on, or borders or
   * reside in a particular layer. If so, the OSM vertical layer index is retrieved and provided. If inconsistent
   * indices are found across the links the user is warned, if no matching links are known on the layer null is returned.
   *
   * @param stopPositionLocation to find layer index for
   * @param layer                to check
   * @return OSM vertical layer index found, and boolean indicating if the found layer index was the same across
   * all eligible links (true), false otherwise
   */
  protected Pair<Integer, Boolean> findOsmVerticalLayerIndexByStopPositionPlanitLinks(
      Point stopPositionLocation, NetworkLayer layer) {
    var layerData = getNetworkToZoningData().getNetworkLayerData(layer);

    Collection<MacroscopicLink> planitLinks = layerData.findPlanitLinksWithInternalLocation(stopPositionLocation);
    if (planitLinks == null || planitLinks.isEmpty()) {
      var planitNode = layerData.getPlanitNodeByLocation(stopPositionLocation);
      if (planitNode != null && planitNode.hasLinks()) {
        planitLinks = planitNode.getLinks();
      }
    }

    if (planitLinks != null && !planitLinks.isEmpty()) {
      final int verticalLayerIndex = layerData.getMostFrequentVerticalLayerIndex(planitLinks);
      final boolean consistent = planitLinks.stream().allMatch(
          l -> layerData.getLinkVerticalLayerIndex(l) == verticalLayerIndex);
      return Pair.of(verticalLayerIndex, consistent);
    }

    return null;
  }

  /**
   * Constructor
   *
   * @param zoningReaderData   to use
   * @param network2ZoningData to use
   * @param transferSettings   to use
   */
  protected OsmZoningHelperBase(
      final OsmZoningReaderData zoningReaderData,
      final OsmNetworkToZoningReaderData network2ZoningData,
      final OsmPublicTransportReaderSettings transferSettings) {
    this.transferSettings = transferSettings;
    this.network2ZoningData = network2ZoningData;
    this.zoningReaderData = zoningReaderData;
  }
}


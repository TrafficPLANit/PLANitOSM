package org.goplanit.osm.converter.zoning.handler.helper;

import org.goplanit.osm.converter.network.OsmNetworkHandlerHelper;
import org.goplanit.osm.converter.network.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.PlanitNetworkLayerUtils;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.locationtech.jts.geom.Point;

import java.util.Collection;
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
  
  /** Collect the pt settings
   * 
   * @return public transport settings
   */
  protected OsmPublicTransportReaderSettings getSettings() {
    return this.transferSettings;
  }

  protected PlanitOsmNetwork getReferenceNetwork(){
    return referenceNetwork;
  }

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
   * reside in a particular layer. If so, the OSM vertical layer index is retrieved and provided. If inconsistent indices are found
   * across the links the user is warned, if no matching links are known on the layer null is returned.
   *
   * @param stopPositionLocation  to find layer index for
   * @param layer to check
   * @return OSM vertical layer index found, null if no match on the layer exists
   */
  protected Integer findOsmVerticalLayerIndexByStopPositionPlanitLinks(Point stopPositionLocation, NetworkLayer layer) {
    var layerData = getNetworkToZoningData().getNetworkLayerData(layer);

    Collection<MacroscopicLink> planitLinks = layerData.findPlanitLinksWithInternalLocation(stopPositionLocation);
    if(planitLinks==null || planitLinks.isEmpty()) {
      var planitNode = layerData.getPlanitNodeByLocation(stopPositionLocation);
      if (planitNode != null && planitNode.hasLinks()) {
        planitLinks = planitNode.getLinks();
      }
    }

    if(planitLinks!=null && !planitLinks.isEmpty()) {
      final Integer verticalLayerIndex = OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(planitLinks.iterator().next());
      if (!planitLinks.stream().allMatch(l -> OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(l) == verticalLayerIndex)) {
        LOGGER.warning(String.format(
            "PLANit Link(s) [%s] connected to OSM stop position in location %s are not all on the expected vertical layer plane (layer=%d), verify correctness",
            planitLinks.stream().map(l -> l.getIdsAsString() + "layer: "+ OsmNetworkHandlerHelper.getLinkVerticalLayerIndex(l)).collect(Collectors.joining(",")),
            stopPositionLocation, verticalLayerIndex));
      }
      return verticalLayerIndex;
    }

    return  null;
  }

  /** Constructor 
   *
   * @param referenceNetwork to use
   * @param network2ZoningData to use
   * @param transferSettings to use
   */
  protected OsmZoningHelperBase(
      final PlanitOsmNetwork referenceNetwork,
      final OsmNetworkToZoningReaderData network2ZoningData,
      final OsmPublicTransportReaderSettings transferSettings) {
    this.transferSettings = transferSettings;
    this.network2ZoningData = network2ZoningData;
    this.referenceNetwork = referenceNetwork;
  }
    
}

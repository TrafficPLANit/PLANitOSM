package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: where a signal sits on the
 * PLANit layer now, on a PLANit node, or on a shape node inside one or more links. Always find it when it is needed,
 * see {@link #locateAndCreate(OsmNode, OsmNetworkReaderLayerData)}. Never keep it from one pass to the next: the third
 * pass breaks links, which changes the links a position lies inside. Where a vehicle signal stands is kept across
 * link breaks by {@link OsmVehicleSignalPositionsWithDirection} instead.
 *
 * @author markr
 */
final class SignalOnPlanitLayer {

  /**
   * Constructor
   *
   * @param signal the OSM node of the signal
   * @param node the signal is on, null when it lies inside links
   * @param links the signal lies inside, null when it is on a node
   */
  private SignalOnPlanitLayer(OsmNode signal, Node node, List<MacroscopicLink> links) {
    this.signal = signal;
    this.node = node;
    this.links = links;
  }

  /**
   * Find where a signal sits in the layer now: on a PLANit node, or on a shape node inside links, and create its
   * location
   *
   * @param signal the OSM node of the signal
   * @param layerData the parsing data of the layer, holding the PLANit node or links each OSM node ended up on
   * @return its location, null when it is not in the layer
   */
  public static SignalOnPlanitLayer locateAndCreate(OsmNode signal, OsmNetworkReaderLayerData layerData) {
    var node = layerData.getPlanitNodeByOsmNode(signal);
    if (node != null) {
      return new SignalOnPlanitLayer(signal, node, null);
    }
    // internal node of link, stop-line on osm way not on extreme node
    var links = layerData.findPlanitLinksWithInternalLocation(OsmNodeUtils.createPoint(signal));
    return links == null || links.isEmpty() ? null : new SignalOnPlanitLayer(signal, null, links);
  }

  /** the OSM node of the signal */
  public final OsmNode signal;

  /** the node the signal is on, null when it lies inside links */
  public final Node node;

  /** the links the signal lies inside, null when it is on a node */
  public final List<MacroscopicLink> links;

  /**
   * Verify if the signal is on a node
   *
   * @return true when on a node, false when inside links
   */
  public boolean isOnNode() {
    return node != null;
  }

  /**
   * Collect the links the signal lies inside, or that meet at its node
   *
   * @return links
   */
  public List<MacroscopicLink> getLinks() {
    return isOnNode() ? new ArrayList<>(node.<MacroscopicLink>getLinks()) : links;
  }

  /**
   * The segments carrying traffic past a signal that a look-ahead may travel, in each direction the signal applies to.
   * From a signal on a node, these are the segments starting at the node: they pass the signal and lie wholly ahead of
   * it; a segment ending at the node is behind it. From a signal inside links, these are the segments of those links,
   * since each passes it. Each link is part of an OSM way, and its A to B segment is the way's forward direction.
   *
   * @param direction the signal applies to, relative to the OSM way of each link, null for both, see
   *                  {@link PlanitLinkOsmUtils#isInOsmDirection(MacroscopicLinkSegment, String)}
   * @param travels the segments a look-ahead may travel
   * @return the segments passing the signal
   */
  public List<MacroscopicLinkSegment> findSegmentsPassing(String direction,
      Predicate<? super MacroscopicLinkSegment> travels) {
    var passing = new ArrayList<MacroscopicLinkSegment>(2);
    for (var link : getLinks()) {
      for (var segment : new MacroscopicLinkSegment[] {link.getLinkSegmentAb(), link.getLinkSegmentBa()}) {
        if (segment != null && travels.test(segment) && PlanitLinkOsmUtils.isInOsmDirection(segment, direction) &&
            (!isOnNode() || segment.getUpstreamVertex() == node)) {
          passing.add(segment);
        }
      }
    }
    return passing;
  }
}

package org.goplanit.osm.converter.network.trafficsignal;

import org.goplanit.osm.converter.helper.OsmProjectedBoundingAreaHelper;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.network.layer.physical.LinkUtils;
import org.goplanit.utils.network.layer.physical.Node;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the area the network was parsed
 * for, see {@link OsmProjectedBoundingAreaHelper}. It is the configured bounding area, or else, when none is
 * configured, the network's extent as the input file declares it. A road end outside it, or a signal near or beyond
 * its boundary, may belong to a junction the boundary cuts off.
 *
 * @author markr
 */
final class OsmProjectedBoundingAreaOrExtentHelper {

  /** the parsed area: the bounding area, or else the bounds the input file declares. Empty when there is neither */
  private final OsmProjectedBoundingAreaHelper parsedArea;

  /** the parsing data of the network, to find the OSM node of a PLANit node */
  private final OsmNetworkReaderData networkData;

  /**
   * Constructor
   *
   * @param parsedArea the bounding area, or else the bounds the input file declares, empty when there is neither
   * @param networkData the parsing data of the network
   */
  private OsmProjectedBoundingAreaOrExtentHelper(
      OsmProjectedBoundingAreaHelper parsedArea, OsmNetworkReaderData networkData) {
    this.parsedArea = parsedArea;
    this.networkData = networkData;
  }

  /**
   * Create the parsed area: the bounding area, or else the bounds the input file declares, empty when there is neither.
   * Only known once parsing has read them, so made when the integration is executed
   *
   * @param networkData the parsing data of the network, holding the bounding area and the input file's bounds
   * @param settings the settings, holding the source CRS, the country and the ferry leniency of the bounding area
   * @return the parsed area
   */
  public static OsmProjectedBoundingAreaOrExtentHelper of(
      OsmNetworkReaderData networkData, OsmNetworkReaderSettings settings) {
    var parsedAreaBoundary = networkData.getBoundingArea() != null ?
        networkData.getBoundingArea() : networkData.getInputFileBounds();
    var parsedArea = OsmProjectedBoundingAreaHelper.ofNullable(parsedAreaBoundary, settings.getSourceCRS(),
        settings.getCountryName(), settings.getMaximumDistanceFerryOutsideBoundingPolygonInMeters());
    return new OsmProjectedBoundingAreaOrExtentHelper(parsedArea, networkData);
  }

  /**
   * Verify if the road ends at a node outside the parsed area: only one link motorised road traffic can use meets
   * there, and the node lies outside the parsed area. The parsed road leads to a junction cut off by the boundary, so
   * the node where it ends stands in for that junction. Inside the parsed area such a node is a real dead end, no
   * junction.
   *
   * @param node to check
   * @return true when the road ends there outside the parsed area, false otherwise, also when the area is not bounded
   */
  public boolean isRoadEndOutside(Node node) {
    if (parsedArea.isEmpty() ||
        node.getNumberOfEdges(LinkUtils.permitsMotorisedRoadModeOnAnySegment()) != 1 ||
        !node.hasExternalId()) {
      return false;
    }
    var osmNode = networkData.getOsmNodeData().getRegisteredOsmNode(PlanitOsmUtils.parseOsmId(node));
    return osmNode != null &&
        !parsedArea.isWhollyWithinBoundaryArea(OsmNodeUtils.createPoint(osmNode), true);
  }

  /**
   * Verify if a signal is near or beyond the boundary of the parsed area. It is when it lies outside the area, on a way
   * running out of it. It also is when it lies inside, but no farther from the boundary than the reach of the signal to
   * its junction: the maximum distance from a vehicle signal to its junction, or from a crossing to its junction. A
   * junction it belongs to is then likely cut off.
   *
   * @param signal the OSM node of the signal
   * @param reach from the signal to its junction, in metres
   * @return true when near or beyond the boundary of the parsed area, false otherwise, also when the area is not
   *         bounded
   */
  public boolean isNearOrBeyondBoundary(OsmNode signal, double reach) {
    if (parsedArea.isEmpty()) {
      return false;
    }
    var position = OsmNodeUtils.createPoint(signal);
    return !parsedArea.isWhollyWithinBoundaryArea(position, true) ||
        parsedArea.calculateProjectedDistanceToBoundingPolygon(position, true) <= reach;
  }
}

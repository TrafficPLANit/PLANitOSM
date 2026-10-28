package org.goplanit.osm.converter.network.trafficsignal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.locationtech.jts.geom.Coordinate;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: where vehicle signals stand,
 * kept across link breaks. Each is kept as its coordinate and the direction of the traffic passing it along its link,
 * filed under the link's external id, the OSM way id. Breaking a link copies its external id to both links it is
 * broken into, so a new link segment made by breaking a link can still be asked which of them stand on it, see
 * {@link #findWhereVehicleSignalsStandOn(LinkSegment)}. Where a signal sits on the PLANit layer now is found anew each
 * time instead, see {@link SignalOnPlanitLayer}.
 *
 * @author markr
 */
final class OsmVehicleSignalPositionsWithDirection {

  /** Where a vehicle signal stands: its position, and the direction of the segment carrying traffic past it, relative
   * to its link. The new link segments made by breaking the link at a node later keep that direction, so it still
   * holds for them */
  private static final class OsmVehicleSignalPositionWithDirection {

    /** position of the signal, as in the geometry of the link it lies inside, or of the node it is on */
    private final Coordinate position;

    /** true when the traffic passing the signal runs from node A to node B of its link */
    private final boolean directionAb;

    /**
     * Constructor
     *
     * @param position of the signal
     * @param directionAb true when the traffic passing the signal runs from node A to node B of its link
     */
    private OsmVehicleSignalPositionWithDirection(Coordinate position, boolean directionAb) {
      this.position = position;
      this.directionAb = directionAb;
    }
  }

  /** per OSM way id, where the vehicle signals stand on its links */
  private final Map<String, List<OsmVehicleSignalPositionWithDirection>> positionsByOsmWayId = new HashMap<>();

  /**
   * Record where a vehicle signal stands: on the segment carrying traffic past it, which lies wholly ahead of a signal
   * on a node
   *
   * @param signal the OSM node of the signal
   * @param passing the segment carrying traffic past the signal
   */
  public void recordWhereVehicleSignalStands(OsmNode signal, MacroscopicLinkSegment passing) {
    positionsByOsmWayId.computeIfAbsent(passing.getParent().getExternalId(), id -> new ArrayList<>(1))
        .add(new OsmVehicleSignalPositionWithDirection(
            OsmNodeUtils.createCoordinate(signal), passing.isDirectionAb()));
  }

  /**
   * Find where the recorded vehicle signals standing on a link segment are: they lie inside the segment's link, or on
   * the node the segment starts at, and apply to the segment's direction. It holds too for the new link segments made
   * by breaking a link at a node after they were recorded
   *
   * @param segment to check
   * @return the positions of the signals standing on it
   */
  public Stream<Coordinate> findWhereVehicleSignalsStandOn(LinkSegment segment) {
    var positions = positionsByOsmWayId.get(segment.getParent().getExternalId());
    if (positions == null) {
      return Stream.empty();
    }
    var coordinates = segment.getParent().getGeometry().getCoordinates();
    var upstream = segment.getUpstreamNode().getPosition().getCoordinate();
    return positions.stream().filter(signal -> signal.directionAb == segment.isDirectionAb())
        .map(signal -> signal.position).filter(position -> position.equals2D(upstream) ||
            IntStream.range(1, coordinates.length - 1).anyMatch(index -> position.equals2D(coordinates[index])));
  }
}

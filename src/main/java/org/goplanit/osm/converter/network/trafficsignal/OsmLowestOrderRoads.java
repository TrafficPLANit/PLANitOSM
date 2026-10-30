package org.goplanit.osm.converter.network.trafficsignal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Link;

/**
 * Used only by the traffic signal integration, see {@link OsmTrafficSignalIntegrator}: the lowest order roads parsed,
 * service lanes, alleys among them, and living streets, remembered by their OSM way id, so the links a way is broken
 * into later are covered too. A vehicle signal passes over a junction where only these join a road of higher capacity,
 * to the next junction that counts, see {@link VehicleSignalJunctionResolver}.
 *
 * @author markr
 */
final class OsmLowestOrderRoads {

  /** OSM way ids of the lowest order roads parsed. The layer parser hands them over while it creates the links. Cleared
   * when the integration is reset, see {@link OsmTrafficSignalIntegrator#reset()} */
  private final Set<String> lowestOrderRoadOsmWayIds = new HashSet<>();

  /** the lowest order roads: service lanes, alleys among them, and living streets. A vehicle signal passes over a
   * junction where only these join a road of higher capacity */
  public static final Set<String> LOWEST_ORDER_ROAD_TYPES =
      Set.of(OsmHighwayTags.SERVICE, OsmHighwayTags.LIVING_STREET);

  /**
   * Remember a link as part of a lowest order road when the tags of its OSM way say so, see
   * {@link #LOWEST_ORDER_ROAD_TYPES}. It is kept by OSM way id, so the links the way is broken into later are covered
   * too.
   *
   * @param link created
   * @param tags of the OSM way it is created from
   */
  public void registerIfLowestOrderRoad(MacroscopicLink link, Map<String, String> tags) {
    var highwayValue = tags.get(OsmHighwayTags.HIGHWAY);
    if (highwayValue != null && LOWEST_ORDER_ROAD_TYPES.contains(highwayValue)) {
      lowestOrderRoadOsmWayIds.add(link.getExternalId());
    }
  }

  /**
   * Verify if a link is part of a lowest order road, see {@link #LOWEST_ORDER_ROAD_TYPES}
   *
   * @param link to verify
   * @return true when part of a lowest order road, false otherwise
   */
  public boolean isOnLowestOrderRoad(Link link) {
    return lowestOrderRoadOsmWayIds.contains(link.getExternalId());
  }

  /**
   * Verify if only lowest order roads of lower capacity join the road at a junction reached. The road arrived along is
   * not a lowest order road. Every other road there is one, see
   * {@link LookAhead.TargetReached#findCrossingRoadLinks()}. And their capacity is below that of the segment arriving.
   *
   * @param junctionAhead the junction reached and the segment arriving there
   * @return true when only lowest order roads of lower capacity join there, false otherwise
   */
  public boolean isJoinedOnlyByLowestOrderRoads(LookAhead.TargetReached junctionAhead) {
    if (isOnLowestOrderRoad(junctionAhead.arriving.getParent())) {
      return false;
    }
    var otherRoadLinks = junctionAhead.findCrossingRoadLinks();
    return !otherRoadLinks.isEmpty() && otherRoadLinks.stream().allMatch(this::isOnLowestOrderRoad) &&
        Precision.smaller(junctionAhead.computeCrossingRoadCapacity(),
            junctionAhead.arriving.getCapacityOrDefaultPcuH(), Precision.EPSILON_6);
  }

  /**
   * Forget the lowest order roads, when the integration is reset, see {@link OsmTrafficSignalIntegrator#reset()}
   */
  public void clear() {
    lowestOrderRoadOsmWayIds.clear();
  }
}

package org.goplanit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkUtils;
import org.goplanit.utils.network.layer.physical.Link;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;

/**
 * Utilities regarding PLANit links useful when parsing OSM entities 
 * 
 * @author markr
 *
 */
public class PlanitLinkOsmUtils {

  /** Collect the closest by link (with the given OSM way id on the layer) to the provided geometry. This is a very slow method
   * as we lookup the link via the un-indexed external id. Use cautiously.
   * 
   * @param osmWayId to filter links on
   * @param geometry to check closeness against
   * @param networkLayer the link must reside on
   * @param geoUtils to use for closeness computations
   * @return found link (null if none)
   */
  public static MacroscopicLink getClosestLinkWithOsmWayIdToGeometry(
      long osmWayId, Geometry geometry, MacroscopicNetworkLayer networkLayer, PlanitJtsCrsUtils geoUtils){

    /* collect all PLANit links that match the OSM way id (very slow, but it is rare and not worth
    the indexing generally) */
    Collection<? extends MacroscopicLink> nominatedLinks =
            networkLayer.getLinks().getByExternalId(String.valueOf(osmWayId));

    /* in case osm way is broken, multiple PLANit links might exist with the same external id, find closest
    one and use it */
    return (MacroscopicLink) PlanitGraphGeoUtils.findEdgeClosest(geometry, nominatedLinks, geoUtils);
  }

  /** Verify if a link segment runs in the given OSM direction of the OSM way its link was created from: a link's A to B
   * direction is its way's forward direction, B to A its backward direction, also after the link is broken
   *
   * @param segment to check
   * @param osmDirection an OSM direction value, e.g., forward, backward or both; null, both or an unrecognised value
   *                     covers both directions
   * @return true when the segment runs in the direction, false otherwise
   */
  public static boolean isInOsmDirection(MacroscopicLinkSegment segment, String osmDirection) {
    if (OsmDirectionTags.FORWARD.equals(osmDirection)) {
      return segment.isDirectionAb();
    }
    if (OsmDirectionTags.BACKWARD.equals(osmDirection)) {
      return !segment.isDirectionAb();
    }
    return true;
  }

  /**
   * Verify if the OSM ways of two links ending at a node disagree on their orientation there. A link's A to B direction
   * is its way's forward direction, so the ways disagree when both links run into the node, or both run out of it. A
   * direction tagged on the node then means opposite directions along them
   *
   * @param node the links end at
   * @param links pieces of the ways ending at the node
   * @return true when there are two links and their ways disagree, false otherwise
   */
  public static boolean waysDisagreeOnOrientation(Node node, List<MacroscopicLink> links) {
    long runningIn = links.stream().filter(link -> link.getVertexB() == node).count();
    return links.size() == 2 && runningIn != 1;
  }

  /**
   * Verify if two links are on the same OSM way, or on OSM ways with the same name. This still holds after PLANit broke
   * an OSM way into several links. It compares the external ids, which are OSM way ids, and the names, see
   * {@link MacroscopicLinkUtils#isSameExternalIdOrName(Link, Link)}.
   *
   * @param one link
   * @param other link
   * @return true when on the same OSM way or on OSM ways with the same name, false otherwise
   */
  public static boolean isSameOsmWayOrName(Link one, Link other) {
    return MacroscopicLinkUtils.isSameExternalIdOrName(one, other);
  }

  /**
   * The segment that continues an OSM way beyond the node a segment arrives at. It is the only allowed continuation,
   * see {@link MacroscopicLinkSegmentUtils#findOnlySegmentOnward(MacroscopicLinkSegment, boolean, Predicate)}. When
   * only the same OSM way or name may continue, it must also be on the same OSM way, or on a way with the same name,
   * see {@link #isSameOsmWayOrName(Link, Link)}. When more than one segment qualifies, the way does not continue
   * clearly, and nothing is returned
   *
   * @param arriving segment arriving at the node to continue beyond; its link is never a continuation
   * @param allowedContinuation the segments the way may continue on
   * @param sameOsmWayOrNameOnly true when only the same OSM way, or a way with the same name, may continue, as past a
   *                             junction. False when any allowed continuation may, as through a node the way simply
   *                             passes
   * @return the continuing segment, null when none qualifies, or when more than one does
   */
  public static MacroscopicLinkSegment findContinuingLinkSegment(MacroscopicLinkSegment arriving,
      Predicate<? super MacroscopicLinkSegment> allowedContinuation, boolean sameOsmWayOrNameOnly) {
    return MacroscopicLinkSegmentUtils.findOnlySegmentOnward(arriving, true,
        leaving -> allowedContinuation.test(leaving) && (!sameOsmWayOrNameOnly ||
            isSameOsmWayOrName(leaving.getParent(), arriving.getParent())));
  }
}

package org.goplanit.osm.util;

import java.util.Collection;
import java.util.HashSet;

import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.utils.geo.PlanitGraphGeoUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.TrackModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
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
}

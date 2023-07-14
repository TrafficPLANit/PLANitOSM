package org.goplanit.osm.util;

import java.util.Collection;
import java.util.HashSet;

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
    /* collect all PLANit links that match the OSM way id (very slow, but it is rare and not worth the indexing generally) */
    Collection<? extends MacroscopicLink> nominatedLinks = networkLayer.getLinks().getByExternalId(String.valueOf(osmWayId));
    /* in case osm way is broken, multiple planit links might exist with the same external id, find closest one and use it */
    return (MacroscopicLink) PlanitGraphGeoUtils.findEdgeClosest(geometry, nominatedLinks, geoUtils);
  }  
}

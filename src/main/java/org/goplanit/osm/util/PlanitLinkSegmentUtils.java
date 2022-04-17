package org.goplanit.osm.util;

import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LinearLocation;

/**
 * Utilities regarding PLANit link segments useful when parsing OSM entities and converting them into 
 * PLANit link segments or other PLANit entities that have something to do with link segments.
 * 
 * @author markr
 *
 */
public class PlanitLinkSegmentUtils {

  /** Collect the one way link segment for the mode if the link is in fact one way. If it is not (for the mode), null is returned
   * 
   * @param link to collect one way edge segment (for mode) from, if available
   * @param accessMode to check one-way characteristic
   * @return edge segment that is one way for the mode, i.e., the other edge segment (if any) does not support this mode, null if this is not the case
   */
  public static MacroscopicLinkSegment getLinkSegmentIfLinkIsOneWayForMode(Link link, Mode accessMode) {
    EdgeSegment edgeSegment = null;
    if(link.hasEdgeSegmentAb() != link.hasEdgeSegmentBa()) {
      /* link is one way across all modes */
      edgeSegment = link.hasEdgeSegmentAb() ? link.getLinkSegmentAb() : link.getLinkSegmentBa();
    }else if(link.<MacroscopicLinkSegment>getLinkSegmentAb().isModeAllowed(accessMode) != link.<MacroscopicLinkSegment>getLinkSegmentBa().isModeAllowed(accessMode)) {
      /* link is one way for our mode */
      edgeSegment = link.<MacroscopicLinkSegment>getLinkSegmentAb().isModeAllowed(accessMode) ? link.getLinkSegmentAb() : link.getLinkSegmentBa();
    }

    return (MacroscopicLinkSegment) edgeSegment;
  }
  
  /** Extract a JTS line segment based on the closest two coordinates on the link segment geometry in its intended direction to the reference geometry provided
   * 
   * @param referenceGeometry to find closest line segment to 
   * @param linkSegment to extract line segment from
   * @param geoUtils for distance calculations
   * @return line segment if found
   * @throws PlanItException  thrown if error
   */
  public static LineSegment extractClosestLineSegmentToGeometryFromLinkSegment(Geometry referenceGeometry, MacroscopicLinkSegment linkSegment, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    LineString linkSegmentGeometry = linkSegment.getParent().getGeometry();
    if(linkSegmentGeometry == null) {
      throw new PlanItException("geometry not available on osm way %s, unable to determine if link (segment) is closest to reference geometry, this shouldn't happen", linkSegment.getParent().getExternalId());
    }
    
    LinearLocation linearLocation = geoUtils.getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(referenceGeometry, linkSegmentGeometry);
    boolean reverseLinearLocationGeometry = linkSegment.isDirectionAb()!=linkSegment.getParent().isGeometryInAbDirection();
    
    LineSegment lineSegment = linearLocation.getSegment(linkSegment.getParent().getGeometry());
    if(reverseLinearLocationGeometry) {
      lineSegment.reverse();
    }
    return lineSegment;        
  }  
  
}

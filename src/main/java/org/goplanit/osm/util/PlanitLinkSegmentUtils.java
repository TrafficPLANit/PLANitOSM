package org.goplanit.osm.util;

import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.directed.EdgeSegment;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
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

  /** Extract a JTS line segment based on the closest two coordinates on the link segment geometry in its intended direction to the reference geometry provided
   * 
   * @param referenceGeometry to find closest line segment to 
   * @param linkSegment to extract line segment from
   * @param geoUtils for distance calculations
   * @return line segment if found
   */
  public static LineSegment extractClosestLineSegmentToGeometryFromLinkSegment(Geometry referenceGeometry, MacroscopicLinkSegment linkSegment, PlanitJtsCrsUtils geoUtils) {
    
    LineString linkSegmentGeometry = linkSegment.getParent().getGeometry();
    if(linkSegmentGeometry == null) {
      throw new PlanItRunTimeException("Geometry not available on OSM way %s, unable to determine if link (segment) is closest to reference geometry, this shouldn't happen", linkSegment.getParent().getExternalId());
    }
    
    LinearLocation linearLocation = geoUtils.getClosestGeometryExistingCoordinateToProjectedLinearLocationOnLineString(referenceGeometry, linkSegmentGeometry);
    LineSegment lineSegment = linearLocation.getSegment(linkSegment.getParent().getGeometry());
    if(linkSegment.isDirectionAb()!=linkSegment.getParent().isGeometryInAbDirection()) {
      lineSegment.reverse();
    }
    return lineSegment;        
  }  
  
}

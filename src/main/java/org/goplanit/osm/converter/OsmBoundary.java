package org.goplanit.osm.converter;

import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.StringUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

public class OsmBoundary {

  private final String adminLevel;

  /** boundary name to determine boundary, mutually exclusive to boundingPolygon */
  private final String boundaryName;

  /** Restrict to within the bounding polygon, mutually exclusive with boundaryName */
  private final Polygon boundingPolygon;

  /**
   * Constructor
   * @param boundaryName to use to determine boundary polygon from OSM data
   * @param adminLevel admin level to search for (in case multiple exist)
   */
  private OsmBoundary(String boundaryName, String adminLevel) {
    this.boundingPolygon = null;
    this.boundaryName = boundaryName;
    this.adminLevel = adminLevel;
  }

  /**
   * Constructor
   *
   * @param boundingPolygon to use
   */
  private OsmBoundary(final Polygon boundingPolygon) {
    this.boundingPolygon = boundingPolygon;
    boundaryName = null;
    adminLevel = null;
  }

  /**
   * Constructor
   *
   * @param boundaryName to use to determine boundaring polygon from OSM data
   * @param adminLevel admin level to search for (in case multiple exist)
   * @param boundingPolygon to use
   */
  private OsmBoundary(String boundaryName, String adminLevel, final Polygon boundingPolygon) {
    this.boundingPolygon = boundingPolygon;
    this.boundaryName = boundaryName;
    this.adminLevel = adminLevel;
  }

  /**
   * Create a polygon based bounding box to restrict parsing to
   *
   * @param boundingPolygon to restrict to
   * @return OsmBoundary to use in setting
   */
  public static OsmBoundary of(Polygon boundingPolygon) {
    return new OsmBoundary(boundingPolygon);
  }

  /** Set an additional (more restricting) square bounding box based on provided envelope
   *
   * @param x1, first x coordinate
   * @param y1, first y coordinate
   * @param x2, second x coordinate
   * @param y2, second y coordinate
   */
  public static OsmBoundary of(Number x1, Number x2, Number y1, Number y2){
    return of(new Envelope(PlanitJtsUtils.createPoint(x1, y1).getCoordinate(), PlanitJtsUtils.createPoint(x2, y2).getCoordinate()));
  }

  /** Set a square bounding box based on provided envelope
   * (which internally is converted to the bounding polygon that happens to be square)
   *
   * @param boundingBox to use
   */
  public static OsmBoundary of(Envelope boundingBox) {
    return of(PlanitJtsUtils.create2DPolygon(boundingBox));
  }

  public boolean hasBoundingPolygon(){
    return this.boundingPolygon != null;
  }

  public Polygon getBoundingPolygon(){
    return this.boundingPolygon;
  }

  public boolean hasBoundaryName(){
    return !StringUtils.isNullOrBlank(boundaryName);
  }

  public boolean hasBoundaryAdminLevel(){
    return !StringUtils.isNullOrBlank(adminLevel);
  }

  public String getBoundaryAdminLevel(){
    return adminLevel;
  }

  public String getBoundaryName(){
    return boundaryName;
  }

  /**
   * Deep clone
   * @return deep clone
   */
  public OsmBoundary deepClone() {
    return new OsmBoundary(boundaryName, adminLevel, boundingPolygon!= null ? (Polygon) boundingPolygon.copy() : null);
  }
}

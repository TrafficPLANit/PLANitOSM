package org.goplanit.osm.converter;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.goplanit.osm.tags.OsmBoundaryTags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.StringUtils;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

/**
 * OSMBoundary helper class to define a boundary to restrict parsing to. This can be a user defined bounding box or
 * polygon as well as a name based reference to an OSM relation via the boundary:_some_type_ key value combination.
 * When the _type_ chosen is administrative it is possible to provide the admin_level as an additional discriminator.
 *
 * @author markr
 */
public final class OsmBoundary {

  private final String adminLevel;

  private final String boundaryType;

  /** boundary name to determine boundary, mutually exclusive to boundingPolygon */
  private final String boundaryName;

  /** Restrict to within the bounding polygon, mutually exclusive with boundaryName */
  private final Polygon boundingPolygon;

  /**
   * Constructor
   *
   * @param boundaryName to use to determine boundary polygon from OSM data
   * @param boundaryType type (value) of boundary
   */
  private OsmBoundary(String boundaryName, String boundaryType) {
    this(boundaryName, boundaryType, null, null);
  }

  /**
   * Constructor
   *
   * @param boundingPolygon to use
   */
  private OsmBoundary(final Polygon boundingPolygon) {
    this(null, null, null, boundingPolygon);
  }

  /**
   * Constructor
   *
   * @param boundaryName to use to determine boundaring polygon from OSM data
   * @param boundaryType type (value) of boundary
   * @param boundingPolygon to use
   */
  private OsmBoundary(String boundaryName, String boundaryType, final Polygon boundingPolygon) {
    this(boundaryName, boundaryType, null, boundingPolygon);
  }

  /**
   * Constructor
   *
   * @param boundaryName to use to determine boundaring polygon from OSM data
   * @param boundaryType type (value) of boundary
   * @param boundingPolygon to use
   */
  private OsmBoundary(String boundaryName, String boundaryType, String adminLevel, final Polygon boundingPolygon) {
    this.boundingPolygon = boundingPolygon;
    this.boundaryName = boundaryName;
    this.boundaryType = boundaryType;
    this.adminLevel = adminLevel;
  }

  /**
   * Create a polygon based on name only to restrict parsing to. If name is not unique
   * then first match will be chosen
   *
   * @param boundaryName to restrict to
   * @return OsmBoundary to use in setting
   */
  public static OsmBoundary of(String boundaryName) {
    return OsmBoundary.of(boundaryName, null);
  }

  /**
   * Create a polygon based on type and name to restrict parsing to, if name and type are not unique first
   * match will be chosen
   *
   * @param boundaryName to restrict to
   * @param boundaryType type of boundary (optional)
   * @return OsmBoundary to use in setting
   */
  public static OsmBoundary of(String boundaryName, String boundaryType) {
    return new OsmBoundary(boundaryName, boundaryType);
  }

  /**
   * Create full OsM boundary with all members populated
   *
   * @param boundaryName to restrict to
   * @param boundaryType type of boundary (optional)
   * @param adminLevel admin level of the boundary (optional)
   * @param boundingPolygon to restrict to
   * @return OsmBoundary to use in setting
   */
  public static OsmBoundary of(String boundaryName, String boundaryType, String adminLevel, Polygon boundingPolygon) {
    return new OsmBoundary(boundaryName, boundaryType, adminLevel, boundingPolygon);
  }

  /**
   * Create OSM boundary of administrative type
   *
   * @param boundaryName to restrict to
   * @param adminLevel admin level of the boundary (optional)
   * @return OsmBoundary to use in setting
   */
  public static OsmBoundary ofAdminstrativeType(String boundaryName, String adminLevel) {
    return new OsmBoundary(boundaryName, OsmBoundaryTags.ADMINISTRATIVE, adminLevel, null);
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
   * @return created boundary
   */
  public static OsmBoundary of(Number x1, Number x2, Number y1, Number y2){
    return of(
        new Envelope(
            PlanitJtsUtils.createPoint(x1, y1).getCoordinate(), PlanitJtsUtils.createPoint(x2, y2).getCoordinate()));
  }

  /** Set a square bounding box based on provided envelope
   * (which internally is converted to the bounding polygon that happens to be square)
   *
   * @param boundingBox to use
   * @return created boundary
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

  public boolean hasBoundaryType(){ return boundaryType != null; }

  /** boundary value, e.g., administrative, forest etc.,
   *
   * @return boundary value type
   */
  public String getBoundaryType(){ return boundaryType; }

  public String getBoundaryName(){
    return boundaryName;
  }

  /**
   * Deep clone
   * @return deep clone
   */
  public OsmBoundary deepClone() {
    return new OsmBoundary(
        boundaryName, boundaryType, adminLevel, (boundingPolygon!= null ? (Polygon) boundingPolygon.copy() : null));
  }

  @Override
  public boolean equals(Object obj){
    if (obj == null){
      return false;
    }
    if (obj == this){
      return true;
    }
    if(obj.getClass() != getClass()) {
      return false;
    }

    OsmBoundary rhs = (OsmBoundary) obj;
    return new EqualsBuilder()
        .append(this.adminLevel, rhs.adminLevel)
        .append(this.boundaryName, rhs.boundaryName)
        .append(this.boundaryType, rhs.boundaryType)
        .append(this.boundingPolygon, rhs.boundingPolygon)
        .isEquals();
  }

  @Override
  public String toString(){
    StringBuilder sb = new StringBuilder();
    if(hasBoundaryName()){
      sb.append(OsmTags.NAME).append(": ").append(getBoundaryName());
    }
    if(hasBoundaryType()){
      sb.append(sb.length()>0? " " : "");
      sb.append(OsmBoundaryTags.getBoundaryKeyTag()).append(": ").append(getBoundaryType());
    }
    if(hasBoundingPolygon()) {
      sb.append(sb.length()>0? " " : "");
      sb.append("geometry: ").append(getBoundingPolygon().toString());
    }
    return sb.toString();
  }
}

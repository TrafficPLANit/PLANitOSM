package org.goplanit.osm.tags;

/** tags used by Osm Multipolygon relation
 * 
 * @author markr
 *
 */
public class OsmMultiPolygonTags {

  private OsmMultiPolygonTags(){}

  /** role of a member way on the outer boundary of a multipolygon */
  public static final String OUTER_ROLE = "outer";
  
  /** role of a member way on the boundary of a hole in a multipolygon */
  public static final String INNER_ROLE = "inner";
}

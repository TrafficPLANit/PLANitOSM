package org.goplanit.osm.tags;

/**
 * Relation roles that exist
 *
 * @author markr
 *
 */
public class OsmRelationMemberRoleTags {

  private OsmRelationMemberRoleTags(){}

  /* role values */
  
  /** role of the member a turn restriction starts from */
  public static final String FROM = "from";
  /** role of the member a turn restriction leads to */
  public static final String TO = "to";
  /** role of the node or way a turn restriction passes */
  public static final String VIA = "via";


  /** role of a way on the outer boundary of a multipolygon */
  public static final String OUTER = OsmMultiPolygonTags.OUTER_ROLE;
  /** role of a way on the boundary of a hole in a multipolygon */
  public static final String INNER = OsmMultiPolygonTags.INNER_ROLE;
  
}

package org.goplanit.osm.tags;

/**
 * Relation types that exist
 * 
 * 
 * @author markr
 *
 */
public class OsmRelationTypeTags {

  private OsmRelationTypeTags(){}

  /** key for relation types */
  public static final String TYPE = OsmTags.TYPE;  
  
  /* values */
  
  /** value of a relation forming an area from several ways, possibly with holes */
  public static final String MULTIPOLYGON = "multipolygon";
  /** value of a relation listing the ways and stops a public transport line or other route follows */
  public static final String ROUTE = "route";
  /** value of a relation grouping the route relations of one line, such as both directions */
  public static final String ROUTE_MASTER = "route_master";
  /** value of a relation restricting turns, see {@link OsmRelationRestrictionTags} */
  public static final String RESTRICTION = "restriction";
  /** value of a relation forming an administrative or other boundary */
  public static final String BOUNDARY = "boundary";
  /** value of a relation grouping public transport entities, such as a stop area */
  public static final String PUBLIC_TRANSPORT = OsmPtv2Tags.PUBLIC_TRANSPORT;
  /** value of a relation describing a destination sign at a junction */
  public static final String DESTINATION_SIGN = "destination_sign";
  /** value of a relation grouping the ways of a waterway, such as a river */
  public static final String WATERWAY = "waterway";
  /** value of a relation describing traffic enforcement, such as a speed camera */
  public static final String ENFORCEMENT = "enforcement";
  /** value of a relation saying which lanes connect across a junction */
  public static final String CONNECTIVITY = "connectivity";
  
  
}

package org.goplanit.osm.tags;

/**
 * common OSM one way tags, see {@link org.goplanit.osm.util.OsmWayUtils} for verifying if tags make a way one way
 *
 */
public class OsmOneWayTags {

  private OsmOneWayTags(){}
  
  /* key */
  /** key saying whether a way is one way, e.g. {@code oneway=yes} */
  public static final String ONEWAY = "oneway";
  
  /* values */
  
  /** value of a way that is one way in its own direction */
  public static final String YES = OsmTags.YES;
  
  /** value of a way that is one way against its own direction, {@code oneway=-1} */
  public static final String ONE_WAY_REVERSE_DIRECTION = "-1";
  
  /** value of a way that is not one way */
  public static final String NO = OsmTags.NO;
  
  /** value of a way that is one way in a direction that changes at times of day */
  public static final String REVERSIBLE = "reversible";
  
  /** value of a way travelled one direction at a time, in turns, such as a narrow passage */
  public static final String ALTERNATING = "alternating";

}

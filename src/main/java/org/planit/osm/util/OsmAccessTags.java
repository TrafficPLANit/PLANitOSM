package org.planit.osm.util;

/**
 * Access tags as described on https://wiki.openstreetmap.org/wiki/Key:access. 
 * 
 * @author markr
 *
 */
public class OsmAccessTags {
  
  protected static final String[] positiveAccessValueTags = 
    {OsmAccessTags.YES,OsmAccessTags.PERMISSIVE, OsmAccessTags.DESIGNATED};
  
  /** key: access tag */
  public static final String ACCESS = "access";
  
  /** value: yes tag */
  public static final String YES = OsmTags.YES;
  
  /** value: no tag */
  public static final String NO = OsmTags.NO;
  
  /** value: private tag */
  public static final String PRIVATE = "private";
  
  /** value: permissive tag */
  public static final String PERMISSIVE = "permissive";
  
  /** value: destination tag */
  public static final String DESTINATION = "destination";
  
  /** value: delivery tag */
  public static final String DELIVERY = "delivery";
  
  /** value: customers tag */
  public static final String CUSTOMERS = "customers";
    
  /** value: designated tag */
  public static final String DESIGNATED = "designated";
  
  /** value: use_sidepath tag mainly used by bicycle mode */
  public static final String USE_SIDEPATH = "use_sidepath";
  
  /** value: separately mapped tag mainly used by bicycle/pedestrian mode*/
  public static final String SEPARATE = "separate";  
  
  /** value: dismount tag mainly used by bicycle mode, possibly horse riders? */
  public static final String DISMOUNT = "dismount";  
  
    
  /** value: agricultural tag */
  public static final String AGRICULTURAL = "agricultural";
  
  /** value: forestry tag */
  public static final String FORESTRY = "forestry";
  
  /** value: discouraged tag */
  public static final String DISCOURAGED = "discouraged";
  
  /** value: unknown tag */
  public static final String UNKNOWN = "unknown";
  
  public static final String[] getPositiveAccessValueTags() {
    return positiveAccessValueTags;
  }


}

package org.planit.osm.tags;

/**
 * Access tags as described on https://wiki.openstreetmap.org/wiki/Key:access. And some related convenience methods related to these tags
 * 
 * @author markr
 *
 */
public class OsmAccessTags {
  
  /**
   * <ul>
   * <li>yes</li>
   * <li>permissive</li>
   * <li>designated</li>
   * </ul>
   */
  protected static final String[] POSTIVE_ACCESS_VALUE_TAGS = {OsmAccessTags.YES,OsmAccessTags.PERMISSIVE, OsmAccessTags.DESIGNATED};

  /**
   * <ul>
   * <li>no</li>
   * <li>none</li>
   * <li>private</li>
   * <li>destination</li>
   * <li>delivery</li>
   * <li>customers</li>
   * <li>use_sidepath</li>
   * <li>separate</li>
   * <li>dismount</li>
   * <li>discouraged</li>
   * </ul>
   */  
  protected static final String[] NEGATIVE_ACCESS_VALUE_TAGS = 
    {OsmAccessTags.NO, OsmAccessTags.NONE, OsmAccessTags.PRIVATE, OsmAccessTags.DESTINATION, OsmAccessTags.DELIVERY, OsmAccessTags.CUSTOMERS, OsmAccessTags.USE_SIDEPATH, OsmAccessTags.SEPARATE, OsmAccessTags.DISMOUNT, OsmAccessTags.DISCOURAGED};
  
  /** key: access tag */
  public static final String ACCESS = "access";
  
  /** value: yes tag */
  public static final String YES = OsmTags.YES;
  
  /** value: no tag */
  public static final String NO = OsmTags.NO;
  
  /** value: no tag */
  public static final String NONE = OsmTags.NONE;  
  
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
  
  /** collect all positive related access value tags indicating an affirmative access. Based on {@code positiveAccessValueTags}
   * @return postive access value tages
   */
  public static final String[] getPositiveAccessValueTags() {
    return POSTIVE_ACCESS_VALUE_TAGS;
  }
  
  /** collect all nagtive related access value tags indicating no (general) access. Based on {@code NEGATIVE_ACCESS_VALUE_TAGS}
   * @return postive access value tages
   */
  public static final String[] getNegativeAccessValueTags() {
    return NEGATIVE_ACCESS_VALUE_TAGS;
  }  


}

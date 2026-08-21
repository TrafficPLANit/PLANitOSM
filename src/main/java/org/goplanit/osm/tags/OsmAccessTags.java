package org.goplanit.osm.tags;

import java.util.Set;

/**
 * Access tags as described on https://wiki.openstreetmap.org/wiki/Key:access. And some related convenience methods related to these tags
 * 
 * @author markr
 *
 */
public class OsmAccessTags {

  private OsmAccessTags(){}
  
  /**
   * <ul>
   * <li>yes</li>
   * <li>permissive</li>
   * <li>designated</li>
   * </ul>
   */
  protected static final String[] DEFAULT_POSITIVE_ACCESS_VALUE_TAGS =
          {OsmAccessTags.YES,OsmAccessTags.PERMISSIVE, OsmAccessTags.DESIGNATED};

  /**
   * <ul>
   * <li>no</li>
   * <li>none</li>
   * <li>private</li>
   * <li>delivery</li>
   * <li>customers</li>
   * <li>discouraged</li>
   * </ul>
   * Note: removed destination as a negative access value as it does not forbid traffic (31/7/2026)
   * <p>
   * Note: removed use_sidepath and separate as negative access values (21/8/2026). Both are mode specific
   * redirections in OSM, used mainly for bicycle and pedestrian traffic to indicate the mode is mapped separately
   * or should use a parallel way. They do not constitute a general access ban, so treating them as one stripped
   * access for every mode. To be handled properly as part of refined active mode support.
   * </p>
   */
  protected static final String[] DEFAULT_NEGATIVE_ACCESS_VALUE_TAGS =
    {OsmAccessTags.NO, OsmAccessTags.NONE, OsmAccessTags.PRIVATE, OsmAccessTags.DELIVERY,
            OsmAccessTags.CUSTOMERS, OsmAccessTags.DISCOURAGED};

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
  
  /** collect the <b>default</b> positive related access value tags indicating an affirmative access.
   * <p>
   * These are the out-of-the-box defaults only. Application code should not consult these directly but instead use
   * the compiled classification on the network reader settings, which combines these defaults with any user
   * overrides and is what parsing actually applies.
   * </p>
   *
   * @return default positive access value tags
   */
  public static final String[] getDefaultPositiveAccessValueTags() {
    return DEFAULT_POSITIVE_ACCESS_VALUE_TAGS;
  }

  /** collect the <b>default</b> negative related access value tags indicating no (general) access.
   * <p>
   * These are the out-of-the-box defaults only. Application code should not consult these directly but instead use
   * the compiled classification on the network reader settings, which combines these defaults with any user
   * overrides and is what parsing actually applies.
   * </p>
   *
   * @return default negative access value tags
   */
  public static final String[] getDefaultNegativeAccessValueTags() {
    return DEFAULT_NEGATIVE_ACCESS_VALUE_TAGS;
  }

  /** collect all access value tags this class knows about, regardless of how they are classified. Used to warn a
   * user when they classify a value that is not a recognised OSM access value
   *
   * @return all known access value tags
   */
  public static final Set<String> getAllKnownAccessValueTags() {
    return Set.of(YES, NO, NONE, PRIVATE, PERMISSIVE, DESTINATION, DELIVERY, CUSTOMERS, DESIGNATED,
        USE_SIDEPATH, SEPARATE, DISMOUNT, AGRICULTURAL, FORESTRY, DISCOURAGED, UNKNOWN);
  }

}

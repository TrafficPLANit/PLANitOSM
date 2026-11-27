package org.goplanit.osm.converter.diagnostics;

/**
 * How far along the road the nearest junction is from a vehicle signal that found none within reach, looking ahead
 * in any direction up to {@link #MAX_DISTANCE_M}, grouped for reporting
 *
 * @author markr
 */
public enum OsmNearestJunctionBucket {

  /** nearer than 50 m, i.e. in a direction the signal does not apply to */
  UNDER_50_M(50, "under 50 m"),

  /** 50 to 75 m */
  FROM_50_TO_75_M(75, "50-75 m"),

  /** 75 to 100 m */
  FROM_75_TO_100_M(100, "75-100 m"),

  /** 100 to 150 m */
  FROM_100_TO_150_M(150, "100-150 m"),

  /** 150 to 200 m */
  FROM_150_TO_200_M(200, "150-200 m"),

  /** no junction within 200 m */
  NONE_WITHIN_REACH(Double.POSITIVE_INFINITY, "none within 200 m");

  /** how far along the road the nearest junction is looked for, in metres */
  public static final double MAX_DISTANCE_M = 200;

  /** upper bound of the bucket, in metres, exclusive */
  private final double upperBoundM;

  /** label used when logging */
  private final String label;

  /**
   * Constructor
   *
   * @param upperBoundM upper bound of the bucket, in metres, exclusive
   * @param label used when logging
   */
  OsmNearestJunctionBucket(double upperBoundM, String label) {
    this.upperBoundM = upperBoundM;
    this.label = label;
  }

  /**
   * Collect the label used when logging
   *
   * @return label
   */
  public String getLabel() {
    return label;
  }

  /**
   * Collect the bucket a distance to the nearest junction falls in
   *
   * @param distanceM to the nearest junction, in metres, null when none within {@link #MAX_DISTANCE_M}
   * @return bucket
   */
  public static OsmNearestJunctionBucket of(Double distanceM) {
    if (distanceM == null) {
      return NONE_WITHIN_REACH;
    }
    for (var bucket : values()) {
      if (distanceM < bucket.upperBoundM) {
        return bucket;
      }
    }
    return NONE_WITHIN_REACH;
  }
}

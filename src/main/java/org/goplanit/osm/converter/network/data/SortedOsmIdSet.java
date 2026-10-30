package org.goplanit.osm.converter.network.data;

import java.util.Arrays;
import java.util.stream.LongStream;

/**
 * A set of OSM ids, kept as a sorted array of primitive ids, since it can hold many, such as the nodes of every footway
 * or the ways the settings skip. Ids are collected as they come, and sorted without duplicates on the next query, so
 * ids may be added before and after querying
 *
 * @author markr
 */
final class SortedOsmIdSet {

  /** ids added since the last query, unsorted, null when there are none */
  private LongStream.Builder added = null;

  /** the ids sorted, without duplicates, as of the last query */
  private long[] sorted = new long[0];

  /**
   * Constructor, empty
   */
  public SortedOsmIdSet() {
  }

  /**
   * Add an id
   *
   * @param osmId to add
   */
  public void add(long osmId) {
    if (added == null) {
      added = LongStream.builder();
    }
    added.add(osmId);
  }

  /**
   * Verify if an id is in the set
   *
   * @param osmId to verify
   * @return true when in the set, false otherwise
   */
  public boolean contains(long osmId) {
    if (added != null) {
      sorted = LongStream.concat(Arrays.stream(sorted), added.build()).sorted().distinct().toArray();
      added = null;
    }
    return Arrays.binarySearch(sorted, osmId) >= 0;
  }

  /**
   * Remove every id
   */
  public void clear() {
    added = null;
    sorted = new long[0];
  }
}

package org.goplanit.osm.defaults;

import java.util.*;

/**
 * Interface with common functionality for configuration OSM ways (highway/rail/waterway)
 *
 * @author markr
 *
 */
public interface OsmInfrastructureConfiguration {
  void logDeactivatedTypes();

  /**
   * Verify if the passed in OSM way type is explicitly unsupported. Unsupported types will be ignored
   * when processing ways.
   *
   * @param osmValue, e.g. primary, road
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  boolean isDeactivated(String osmValue);

  /**
   * Verify if the passed in OSM way type is explicitly supported. Supported types will be processed
   * and converted into link(segments).
   *
   * @param osmValue, e.g. primary
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  boolean isActivated(String osmValue);

  /**
   * Choose to not parse the given combination of way subtype, e.g. highway=road
   *
   * @param osmValue to use
   */
  void deactivate(String osmValue);

  /**
   * Choose to add a given way value to be on top of the defaults, e.g. highway=road
   *
   * @param osmWayTypeValues to activate
   */
  void activate(String... osmWayTypeValues);

  /**
   * Choose to add a given way value to be on top of the defaults, e.g. highway=road
   *
   * @param osmWayTypeValues to activate
   */
  void activate(List<String> osmWayTypeValues);

  /** create a copy of the currently supported way types in set form
   *
   * @return map of supported types (by underlying key), any modifications on this set have no impact on the
   * instance internals
   */
  SortedMap<String, SortedSet<String>> getActivatedTypes();

  /** create a copy of the currently unsupported way types in set form
   *
   * @return deactivated way types (by underlying key), any modifications on this set have no impact on the
   * instance internals
   */
  SortedMap<String, SortedSet<String>> getDeactivatedTypes();

  /**
   * deactivate all types explicitly
   */
  void deactivateAll();

  /**
   * activate all types explicitly
   */
  void activateAll();

  /**
   * Deep clone instance
   *
   * @return deep clone
   */
  public OsmInfrastructureConfiguration deepClone();
}

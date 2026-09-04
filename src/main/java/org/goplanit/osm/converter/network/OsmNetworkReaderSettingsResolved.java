package org.goplanit.osm.converter.network;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Resolved network reader settings derived from the configured {@link OsmNetworkReaderSettings}.
 *
 * <p>
 * In this first pass we only resolve the effective activated OSM way types by excluding configured way types that
 * have no remaining activated OSM modes attached. The configured settings instance remains untouched.
 * </p>
 */
public final class OsmNetworkReaderSettingsResolved {

  /** logger */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderSettingsResolved.class.getCanonicalName());

  /** resolved active OSM way types by OSM key */
  private final SortedMap<String, SortedSet<String>> activatedOsmWayTypesByKey = new TreeMap<>();

  /** unmodifiable view on resolved active OSM way types by OSM key */
  private final SortedMap<String, SortedSet<String>> activatedOsmWayTypesByKeyView;

  /** configured OSM way types excluded during resolution by OSM key */
  private final SortedMap<String, SortedSet<String>> excludedOsmWayTypesByKey = new TreeMap<>();

  /**
   * Constructor.
   *
   * @param settings configured reader settings to resolve from
   */
  public OsmNetworkReaderSettingsResolved(final OsmNetworkReaderSettings settings) {
    populateResolvedWayTypes(settings);
    this.activatedOsmWayTypesByKeyView = createUnmodifiableWayTypesView(activatedOsmWayTypesByKey);
  }

  /**
   * Create an unmodifiable view on the provided resolved OSM way types by OSM key.
   *
   * @param osmWayTypesByKey to create the view for
   * @return unmodifiable view
   */
  private SortedMap<String, SortedSet<String>> createUnmodifiableWayTypesView(
      final SortedMap<String, SortedSet<String>> osmWayTypesByKey) {
    SortedMap<String, SortedSet<String>> view = new TreeMap<>();
    osmWayTypesByKey.forEach((osmWayKey, osmWayValues) ->
        view.put(osmWayKey, Collections.unmodifiableSortedSet(osmWayValues)));
    return Collections.unmodifiableSortedMap(view);
  }

  /**
   * Resolve activated highway, railway, and waterway way types without mutating the configured settings.
   *
   * @param settings to resolve from
   */
  private void populateResolvedWayTypes(final OsmNetworkReaderSettings settings) {
    if (settings.isHighwayParserActive()) {
      populateResolvedWayTypes(
          settings.getHighwaySettings().getSetOfActivatedOsmWayLikeTypes(),
          settings.getHighwaySettings().getAcivatedOsmModes(),
          settings.getHighwaySettings()::collectAllowedOsmHighwayModes);
    }
    if (settings.isRailwayParserActive()) {
      populateResolvedWayTypes(
          settings.getRailwaySettings().getSetOfActivatedOsmWayLikeTypes(),
          settings.getRailwaySettings().getAcivatedOsmModes(),
          settings.getRailwaySettings()::collectAllowedOsmRailwayModes);
    }
    if (settings.isWaterwayParserActive()) {
      populateResolvedWayTypes(
          settings.getWaterwaySettings().getSetOfActivatedOsmWayLikeTypes(),
          settings.getWaterwaySettings().getAcivatedOsmModes(),
          settings.getWaterwaySettings()::collectAllowedOsmWaterwayModes);
    }
  }

  /**
   * Resolve effective activated OSM way types for a single way category.
   *
   * @param configuredWayTypes configured active way types
   * @param activatedOsmModes activated OSM modes for the category
   * @param allowedModesProvider provides allowed OSM modes by OSM way value type
   */
  private void populateResolvedWayTypes(
      final SortedMap<String, SortedSet<String>> configuredWayTypes,
      final Collection<String> activatedOsmModes,
      final Function<String, Collection<String>> allowedModesProvider) {

    if (configuredWayTypes == null || activatedOsmModes == null) {
      return;
    }

    for (var entry : configuredWayTypes.entrySet()) {
      String osmWayKey = entry.getKey();
      for (String osmWayValue : entry.getValue()) {
        var allowedModes = new TreeSet<>(allowedModesProvider.apply(osmWayValue));
        allowedModes.retainAll(activatedOsmModes);
        if (allowedModes.isEmpty()) {
          excludedOsmWayTypesByKey.computeIfAbsent(osmWayKey, key -> new TreeSet<>()).add(osmWayValue);
        } else {
          activatedOsmWayTypesByKey.computeIfAbsent(osmWayKey, key -> new TreeSet<>()).add(osmWayValue);
        }
      }
    }
  }

  /**
   * Collect the resolved activated OSM way types by OSM key.
   *
   * @return unmodifiable view of resolved activated way types
   */
  public SortedMap<String, SortedSet<String>> getActivatedOsmWayTypesByKey() {
    return activatedOsmWayTypesByKeyView;
  }

  /**
   * Log configured OSM way types that were excluded during resolution.
   */
  public void logExcludedOsmWayTypes() {
    excludedOsmWayTypesByKey.forEach((osmWayKey, osmWayValues) ->
        osmWayValues.forEach(osmWayValue ->
            LOGGER.info(String.format("[DEACTIVATED] %s=%s", osmWayKey, osmWayValue))));
  }
}

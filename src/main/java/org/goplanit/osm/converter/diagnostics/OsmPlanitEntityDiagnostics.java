package org.goplanit.osm.converter.diagnostics;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.utils.misc.LogCollator;

/**
 * What became of the PLANit entities derived from OSM, for now the intersections from traffic signals: how many were
 * created, and the issues found with them, collated per issue with the entities' ids, so they are logged once with
 * their totals and a sample of ids, and can be queried per issue. What became of the OSM entities they derive from is
 * accounted for separately
 *
 * @author markr
 */
public final class OsmPlanitEntityDiagnostics {

  /** number of intersections created */
  private long intersectionsCreated = 0;

  /** number of approaches the intersections were created with */
  private long approachesCreated = 0;

  /** the entities per issue, collated by the issue's label, every entity retained */
  private final LogCollator issues = LogCollator.create();

  /**
   * Constructor, nothing recorded yet
   */
  public OsmPlanitEntityDiagnostics() {
  }

  /**
   * Record an intersection created
   */
  public void registerIntersectionCreated() {
    ++intersectionsCreated;
  }

  /**
   * Record an approach added to an intersection
   */
  public void registerApproachCreated() {
    ++approachesCreated;
  }

  /**
   * Collect the number of intersections created
   *
   * @return number created
   */
  public long getIntersectionsCreated() {
    return intersectionsCreated;
  }

  /**
   * Record an issue for an entity
   *
   * @param issue found
   * @param entityId id of the entity concerned, the OSM node id of an intersection, the OSM way id of an approach
   */
  public void registerIssue(OsmPlanitEntityIssue issue, String entityId) {
    registerIssue(issue, entityId, null);
  }

  /**
   * Record an issue for an entity, with context shown alongside it in the sample logged
   *
   * @param issue found
   * @param entityId id of the entity concerned
   * @param detail context, may be null
   */
  public void registerIssue(OsmPlanitEntityIssue issue, String entityId, String detail) {
    issues.increment(issue.getLabel(), entityId, detail);
  }

  /**
   * Collect the ids of the entities recorded for an issue, in the order recorded
   *
   * @param issue to collect for
   * @return entity ids, empty when none
   */
  public List<String> getEntityIds(OsmPlanitEntityIssue issue) {
    var template = issues.getTemplate(issue.getLabel());
    return template == null ? List.of() : template.getRetainedOccurrences().stream()
        .map(LogCollator.Occurrence::getEntityId).collect(Collectors.toList());
  }

  /**
   * Log the intersections and approaches created, then one line per issue that occurred, most frequent first, with a
   * sample of the entities' ids
   *
   * @param logger to log to
   * @param sectionTitle logged ahead of the issues
   */
  public void logSummary(Logger logger, String sectionTitle) {
    issues.logSummary(logger, String.format(
        "%s: %d intersections created with %d approaches", sectionTitle, intersectionsCreated, approachesCreated));
  }

  /**
   * Forget everything recorded
   */
  public void reset() {
    intersectionsCreated = 0;
    approachesCreated = 0;
    issues.reset();
  }
}

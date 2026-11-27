package org.goplanit.osm.converter.diagnostics;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.utils.misc.LogCollator;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * What became of the OSM entities the network reader met, for now the traffic signals, collated per outcome with their
 * OSM node ids, so the outcomes are logged once with their totals and a sample of signals, and can be queried per
 * outcome. What became of the PLANit entities derived from them is accounted for separately
 *
 * @author markr
 */
public final class OsmParseDiagnostics {

  /** per group of outcomes, the signals per outcome, collated by the outcome's label, every signal retained */
  private final Map<OsmTrafficSignalOutcomeGroup, LogCollator> collatorsByGroup =
      new EnumMap<>(OsmTrafficSignalOutcomeGroup.class);

  /** the vehicle signals without a junction in reach, per bucket of how far their nearest junction is, collated by
   * the bucket's label with the distance as detail, every signal retained */
  private final LogCollator nearestJunctions = LogCollator.create();

  /**
   * Constructor, nothing recorded yet
   */
  public OsmParseDiagnostics() {
    for (var group : OsmTrafficSignalOutcomeGroup.values()) {
      collatorsByGroup.put(group, LogCollator.create());
    }
  }

  /**
   * Record an outcome for a signal
   *
   * @param outcome of the signal
   * @param signal the OSM node of the signal
   */
  public void register(OsmTrafficSignalOutcome outcome, OsmNode signal) {
    collatorsByGroup.get(outcome.getGroup()).increment(outcome.getLabel(), String.valueOf(signal.getId()));
  }

  /**
   * Record how far along the road the nearest junction is from a vehicle signal that found none within reach
   *
   * @param signal the OSM node of the signal
   * @param distanceM to the nearest junction, in metres, null when none within {@link OsmNearestJunctionBucket#MAX_DISTANCE_M}
   */
  public void registerNearestJunction(OsmNode signal, Double distanceM) {
    nearestJunctions.increment(OsmNearestJunctionBucket.of(distanceM).getLabel(), String.valueOf(signal.getId()),
        distanceM == null ? null : String.format("%.0f m", distanceM));
  }

  /**
   * Collect the OSM node ids of the vehicle signals whose nearest junction falls in a bucket, in the order recorded
   *
   * @param bucket to collect for
   * @return signal ids, empty when none
   */
  public List<String> getSignalIds(OsmNearestJunctionBucket bucket) {
    var template = nearestJunctions.getTemplate(bucket.getLabel());
    return template == null ? List.of() : template.getRetainedOccurrences().stream()
        .map(LogCollator.Occurrence::getEntityId).collect(Collectors.toList());
  }

  /**
   * Collect the number of signals recorded for an outcome
   *
   * @param outcome to collect for
   * @return number of signals, 0 when none
   */
  public long getOccurrences(OsmTrafficSignalOutcome outcome) {
    var template = collatorsByGroup.get(outcome.getGroup()).getTemplate(outcome.getLabel());
    return template == null ? 0 : template.getOccurrences();
  }

  /**
   * Collect the number of outcomes recorded in a group: for a group of outcomes of a kind of signal, the number of
   * signals of that kind
   *
   * @param group to collect for
   * @return number of outcomes, 0 when none
   */
  public long getOccurrences(OsmTrafficSignalOutcomeGroup group) {
    return collatorsByGroup.get(group).getTemplatesByOccurrencesDescending().stream()
        .mapToLong(LogCollator.CollatedTemplate::getOccurrences).sum();
  }

  /**
   * Collect the OSM node ids of the signals recorded for an outcome, in the order recorded
   *
   * @param outcome to collect for
   * @return signal ids, empty when none
   */
  public List<String> getSignalIds(OsmTrafficSignalOutcome outcome) {
    var template = collatorsByGroup.get(outcome.getGroup()).getTemplate(outcome.getLabel());
    return template == null ? List.of() : template.getRetainedOccurrences().stream()
        .map(LogCollator.Occurrence::getEntityId).collect(Collectors.toList());
  }

  /**
   * Log the outcomes per group, one line per outcome that occurred, most frequent first, with a sample of their ids:
   * the signals ignored before their kind matters as a share of the signals recorded, the outcomes of a kind of signal
   * as a share of the signals of that kind, so that each adds up, and the notes alongside them as counts only
   *
   * @param logger to log to
   * @param sectionTitle logged ahead of the outcomes, with the number of signals recorded
   * @param signalsRecorded number of signals recorded
   */
  public void logSummary(Logger logger, String sectionTitle, long signalsRecorded) {
    logger.info(String.format("%s: %d recorded", sectionTitle, signalsRecorded));
    for (var group : OsmTrafficSignalOutcomeGroup.values()) {
      var collator = collatorsByGroup.get(group);
      if (group == OsmTrafficSignalOutcomeGroup.NOTE_ALONGSIDE_OUTCOME) {
        collator.logSummary(logger, String.format("  %s", group.getLabel()));
        continue;
      }
      long total = group == OsmTrafficSignalOutcomeGroup.SIGNAL_OF_ANY_KIND ? signalsRecorded : getOccurrences(group);
      collator.logSummary(logger, String.format("  %s: %d", group.getLabel(), getOccurrences(group)), total);
    }
    if (!nearestJunctions.isEmpty()) {
      nearestJunctions.logSummary(logger, "  nearest junction of the vehicle signals without a junction ahead");
    }
  }

  /**
   * Forget everything recorded
   */
  public void reset() {
    collatorsByGroup.values().forEach(LogCollator::reset);
    nearestJunctions.reset();
  }
}

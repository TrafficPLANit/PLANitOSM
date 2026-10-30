package org.goplanit.osm.converter.network;

import java.util.Set;
import java.util.function.Consumer;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.diagnostics.OsmTrafficSignalOutcome;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;

/**
 * Parsing the hand-made traffic signal scenarios in tests: the layer parsed, and what the reader recorded while
 * parsing it
 */
public final class OsmTrafficSignalScenarioTestUtils {

  /**
   * A traffic signal scenario parsed: its only layer, and what the reader recorded while parsing it
   */
  public static final class ParsedScenario {

    /** the reader that parsed the scenario */
    private final OsmNetworkReader reader;

    /** the only layer parsed */
    private final MacroscopicNetworkLayer layer;

    /**
     * Constructor
     *
     * @param reader that parsed the scenario
     * @param layer the only layer parsed
     */
    private ParsedScenario(OsmNetworkReader reader, MacroscopicNetworkLayer layer) {
      this.reader = reader;
      this.layer = layer;
    }

    /**
     * The only layer parsed
     *
     * @return the layer
     */
    public MacroscopicNetworkLayer layer() {
      return layer;
    }

    /**
     * The data the reader kept while parsing, holding what it recorded
     *
     * @return the reader's data
     */
    public OsmNetworkReaderData networkReaderData() {
      return reader.getNetworkReaderData();
    }

    /**
     * The ids of the signals recorded with an outcome
     *
     * @param outcome to get them for
     * @return the signal ids, in no particular order
     */
    public Set<String> signalIds(OsmTrafficSignalOutcome outcome) {
      return Set.copyOf(networkReaderData().getParseDiagnostics().getSignalIds(outcome));
    }

    /**
     * The ids of the entities recorded with an issue
     *
     * @param issue to get them for
     * @return the entity ids, in no particular order
     */
    public Set<String> entityIds(OsmPlanitEntityIssue issue) {
      return Set.copyOf(networkReaderData().getPlanitEntityDiagnostics().getEntityIds(issue));
    }
  }

  /**
   * Not instantiable
   */
  private OsmTrafficSignalScenarioTestUtils() {
  }

  /** the settings change breaking links at isolated signals, so isolated crossings and ramp meters get intersections */
  public static final Consumer<OsmNetworkReaderSettings> BREAK_LINKS_AT_ISOLATED_SIGNALS =
      settings -> settings.getIntersectionSettings().setBreakLinksAtIsolatedSignals(true);

  /**
   * The settings changes breaking links at isolated signals and parsing only within a bounding area
   *
   * @param boundingArea to parse within
   * @return the settings changes
   */
  public static Consumer<OsmNetworkReaderSettings> breakLinksAtIsolatedSignalsParsingOnlyWithin(
      OsmBoundary boundingArea) {
    return BREAK_LINKS_AT_ISOLATED_SIGNALS.andThen(settings -> settings.setBoundingArea(boundingArea));
  }

  /**
   * Create a reader of a traffic signal scenario in Australia, with settings changes. The ids are reset first, so what
   * it parses is numbered as in a first read
   *
   * @param scenario name of the scenario file, without its extension
   * @param configure the settings changes
   * @return the reader, not yet read
   */
  public static OsmNetworkReader createTrafficSignalScenarioReader(
      String scenario, Consumer<OsmNetworkReaderSettings> configure) {
    IdGenerator.reset();
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve(scenario + ".osm").toString(), CountryNames.AUSTRALIA);
    configure.accept(reader.getSettings());
    return reader;
  }

  /**
   * Parse a traffic signal scenario with settings changes, with a reader as
   * {@link #createTrafficSignalScenarioReader(String, Consumer)} creates it
   *
   * @param scenario name of the scenario file, without its extension
   * @param configure the settings changes
   * @return the scenario parsed, which has one layer
   */
  public static ParsedScenario parseTrafficSignalScenario(
      String scenario, Consumer<OsmNetworkReaderSettings> configure) {
    var reader = createTrafficSignalScenarioReader(scenario, configure);
    var network = reader.read();
    assertEquals(1, network.getTransportLayers().size());
    return new ParsedScenario(reader, network.getTransportLayers().getFirst());
  }

  /**
   * Parse a traffic signal scenario with default settings
   *
   * @param scenario name of the scenario file, without its extension
   * @return the scenario parsed, which has one layer
   */
  public static ParsedScenario parseTrafficSignalScenario(String scenario) {
    return parseTrafficSignalScenario(scenario, settings -> {});
  }
}

package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.bannedTurn;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A banned turn from an OSM turn restriction whose way is skipped by the settings, or whose way visits a node twice so
 * its links are only created after the relations are read
 * <pre>
 *             11
 *              |  way 20, primary
 *   13 ------- 1 ------- 14 ---- 15      way 24, residential: 1-14-15-16-14, ending on its own node 14
 *   way 25     |  \       \      |
 *   service,   |   18      ----- 16      relation 201: north arm onto way 24
 *   private    |   way 27, primary       relation 202: north arm onto way 25, no activated mode may use it
 *        17    |  way 21, primary        relation 203: south arm onto way 26, a footway, not activated by default
 *   way 26    12                         relation 204: south arm onto way 27
 *   footway
 * </pre>
 *
 * @author markr
 */
public class OsmTurnRestrictionOnSkippedOrCircularWayTest {

  /** the scenario */
  private static final String SCENARIO =
      TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("turn_restriction_onto_skipped_or_circular_way.osm").toString();

  /**
   * Reset the ids before each test
   */
  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  /**
   * Reset the ids after each test
   */
  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** A restriction onto a way that visits a node twice gives a banned turn, its links made after the relations */
  @Test
  public void restrictionOntoWayVisitingNodeTwiceIsBannedTurn() {
    var layer = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA).read().getTransportLayers().getFirst();

    assertNotNull(bannedTurn(layer, segment(layer, 11, 1), segment(layer, 1, 14)));
  }

  /** A restriction onto a way the settings skip is counted with the reason: no activated mode may use the way, or its
   * type is not activated. The restriction onto a way that is parsed gives a banned turn */
  @Test
  public void restrictionOntoWaySkippedBySettingsIsCountedWithItsReason() {
    var reader = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("202"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_SKIPPED_NO_ACTIVATED_MODE_ON_WAY));
    assertEquals(List.of("203"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_SKIPPED_WAY_TYPE_NOT_ACTIVATED));
    assertEquals(List.of(), diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_SKIPPED_WAY_EXCLUDED_BY_ID));
    assertNotNull(bannedTurn(layer, segment(layer, 12, 1), segment(layer, 1, 18)));
  }

  /** A restriction onto a way excluded by its id in the settings is counted with that reason, and gives no banned
   * turn */
  @Test
  public void restrictionOntoWayExcludedByIdIsCountedWithItsReason() {
    var reader = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA);
    reader.getSettings().excludeOsmWayFromParsing(27);
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("204"), diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_SKIPPED_WAY_EXCLUDED_BY_ID));
  }
}

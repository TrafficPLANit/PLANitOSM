package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.bannedTurn;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A banned turn from an OSM turn restriction whose infrastructure is dropped while parsing, or whose way visits a node
 * twice so its links are only created after the relations are read
 * <pre>
 *             11
 *              |  way 20, primary
 *   13 ------- 1 ------- 14 ---- 15      way 24, residential: 1-14-15-16-14, ending on its own node 14
 *   way 25     |  \       \      |
 *   service,   |   18      ----- 16      relation 201: north arm onto way 24
 *   private    |    \  way 27, primary   relation 202: north arm onto way 25, no activated mode may use it
 *        17    |     \__________ 61 ---- 62    relation 203: south arm onto way 26, a footway, not activated
 *   way 26    12    way 30,      :  way 31,    relation 204: south arm onto way 27
 *   footway   way 21, primary    :  primary    relation 205: way 28 onto way 29, both private, meeting only at 52
 *                                :             relation 206: way 30 onto way 31 at 61
 *   51 ---- 52 ---- 53           : edge of the area a test may set, 61 and 62 outside it
 *   way 28, way 29: service, private
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

  /**
   * A restriction whose infrastructure is dropped while parsing is counted: onto a way no activated mode may use, onto
   * a way of a type not activated, and between two ways no activated mode may use, which meet only each other so their
   * via node is dropped as well. The restrictions on parsed ways give banned turns
   */
  @Test
  public void restrictionOnDroppedInfrastructureIsCounted() {
    var reader = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("202", "203", "205"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_IGNORED_INFRASTRUCTURE_DROPPED));
    assertNotNull(bannedTurn(layer, segment(layer, 12, 1), segment(layer, 1, 18)));
    assertNotNull(bannedTurn(layer, segment(layer, 18, 61), segment(layer, 61, 62)));
  }

  /** A restriction onto a way excluded by its id in the settings is counted, and gives no banned turn */
  @Test
  public void restrictionOntoWayExcludedByIdIsCounted() {
    var reader = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA);
    reader.getSettings().excludeOsmWayFromParsing(27);
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("202", "203", "204", "205"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_IGNORED_INFRASTRUCTURE_DROPPED));
  }

  /** A restriction from a way in the area onto a way outside it is counted, and gives no banned turn */
  @Test
  public void restrictionOntoWayOutsideTheAreaIsCounted() {
    var reader = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA);
    reader.getSettings().setBoundingArea(OsmBoundary.of(151.2080, 151.2125, -33.8725, -33.8685));
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("202", "203", "205", "206"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.BANNED_TURN_IGNORED_INFRASTRUCTURE_DROPPED));
  }
}

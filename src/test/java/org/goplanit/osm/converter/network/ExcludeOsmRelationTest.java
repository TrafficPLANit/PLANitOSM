package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.bannedTurn;
import static org.goplanit.osm.test.OsmLayerTestUtils.createReader;
import static org.goplanit.osm.test.OsmLayerTestUtils.isModeAllowedOnWay;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.UserOverride;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OSM relations excluded from parsing by their id in the network reader settings: an excluded turn restriction makes no
 * banned turn, and an excluded id the OSM input does not have is recorded as not found.
 * <p>
 * A wrong turn restriction can trap a road, which then loses its modes when dangling subnetworks are removed. Excluding
 * the restriction is how a user leaves it out without editing the OSM data. The scenario is the on-ramp that three
 * restrictions leave with no allowed way on: a two-way road meets one-way motorway ramps at node 1, and the motorway is
 * reachable through a second on-ramp, way 25
 * </p>
 * <pre>
 *                  way 24, motorway
 *          13 &lt;------------------- 14
 *            \                    ^ ^
 *    way 22   \       way 23     /   \   way 25
 *    exit      v     on-ramp    /     \  second on-ramp
 *   11 ------- 1 ---------------------- 12
 *      way 20     way 21
 *
 *   relation 101: west arm (way 20) onto the on-ramp, restriction=no_left_turn
 *   relation 102: east arm (way 21) onto the on-ramp, restriction=no_right_turn
 *   relation 103: exit ramp (way 22) onto the west arm, restriction=only_right_turn
 * </pre>
 *
 * @author markr
 */
public class ExcludeOsmRelationTest {

  /** the logger, giving the log output PLANit's format */
  private static Logger LOGGER = null;

  /** path of the scenario */
  private static final String SCENARIO =
      TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("turn_restriction_trapping_on_ramp.osm").toString();

  /**
   * Create the logger once for all tests of the class
   *
   * @throws Exception when the log file cannot be opened
   */
  @BeforeAll
  public static void setUpLogger() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(ExcludeOsmRelationTest.class);
    }
  }

  /** Close the logger after all tests of the class */
  @AfterAll
  public static void tearDownLogger() {
    Logging.closeLogger(LOGGER);
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /**
   * With relation 101 excluded, the turn from the west arm onto the on-ramp is allowed, so the on-ramp keeps car access
   * when dangling subnetworks are removed, while relations 102 and 103 are still banned turns
   */
  @Test
  public void excludedRestrictionFreesTheOnRamp() {
    var network = createReader(SCENARIO, settings -> settings.excludeOsmRelationFromParsing(UserOverride.of(101L)))
        .read();
    var layer = network.getTransportLayers().getFirst();

    assertTrue(isModeAllowedOnWay(layer, network.getModes().get(PredefinedModeType.CAR), "23"));
    assertNull(bannedTurn(layer, segment(layer, 11, 1), segment(layer, 1, 14)));
    assertNotNull(bannedTurn(layer, segment(layer, 12, 1), segment(layer, 1, 14)));
    assertNotNull(bannedTurn(layer, segment(layer, 13, 1), segment(layer, 1, 12)));
  }

  /**
   * An excluded id the OSM input has no relation for is recorded once as not found, with its reason as the detail,
   * while the excluded relation that is there is not recorded
   */
  @Test
  public void excludedRelationNotInTheInputIsRecordedAsNotFound() {
    var reader = createReader(SCENARIO, settings -> {
      settings.excludeOsmRelationFromParsing(UserOverride.of(101L));
      settings.excludeOsmRelationFromParsing(UserOverride.of(999L, "checked on imagery"));
    });
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("999"), diagnostics.getEntityIds(OsmPlanitEntityIssue.OSM_RELATION_EXCLUDED_NOT_FOUND));
    assertEquals("checked on imagery",
        diagnostics.getDetail(OsmPlanitEntityIssue.OSM_RELATION_EXCLUDED_NOT_FOUND, "999"));
  }

  /** Without excluded relations nothing is recorded, and the on-ramp stays trapped, allowing no cars */
  @Test
  public void withoutExclusionsNothingIsRecorded() {
    var reader = createReader(SCENARIO, settings -> { });
    var network = reader.read();

    assertFalse(isModeAllowedOnWay(
        network.getTransportLayers().getFirst(), network.getModes().get(PredefinedModeType.CAR), "23"));
    assertEquals(List.of(), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.OSM_RELATION_EXCLUDED_NOT_FOUND));
  }
}

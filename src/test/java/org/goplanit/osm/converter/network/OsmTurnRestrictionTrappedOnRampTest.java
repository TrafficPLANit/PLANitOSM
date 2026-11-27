package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.bannedTurn;
import static org.goplanit.osm.test.OsmLayerTestUtils.createReaderKeepingDanglingSubnetworks;
import static org.goplanit.osm.test.OsmLayerTestUtils.isModeAllowedOnWay;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.PredefinedModeType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A motorway on-ramp that the OSM turn restrictions leave with no allowed way on is dangling for cars, so removing
 * dangling subnetworks takes cars off it, while the restrictions stay as banned turns. The scenario is a two-way road
 * meeting one-way motorway ramps at node 1, where three restrictions leave no allowed turn onto on-ramp way 23; the
 * motorway stays reachable through a second on-ramp, way 25
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
public class OsmTurnRestrictionTrappedOnRampTest {

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
      LOGGER = Logging.createLogger(OsmTurnRestrictionTrappedOnRampTest.class);
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
   * No turn onto the on-ramp is allowed, so it is dangling for cars and no link segment of it allows cars, while the
   * exit ramp, the motorway and the second on-ramp keep car access
   */
  @Test
  public void onRampWithNoAllowedWayOnIsDanglingForCars() {
    var network = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA).read();
    var layer = network.getTransportLayers().getFirst();
    var car = network.getModes().get(PredefinedModeType.CAR);

    assertFalse(isModeAllowedOnWay(layer, car, "23"));
    assertTrue(isModeAllowedOnWay(layer, car, "22"));
    assertTrue(isModeAllowedOnWay(layer, car, "24"));
    assertTrue(isModeAllowedOnWay(layer, car, "25"));
  }

  /**
   * The restrictions stay as banned turns wherever both their link segments remain: the only right turn from the exit
   * ramp still bans its turn onto the east arm
   */
  @Test
  public void restrictionsTrappingADanglingOnRampStayAsBannedTurns() {
    var layer = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA).read().getTransportLayers().getFirst();

    assertNotNull(bannedTurn(layer, segment(layer, 13, 1), segment(layer, 1, 12)));
  }

  /**
   * With dangling subnetworks kept, nothing is taken off: the on-ramp still allows cars
   */
  @Test
  public void onRampKeepsCarsWhenDanglingSubnetworksAreKept() {
    var network = createReaderKeepingDanglingSubnetworks(SCENARIO, settings -> { }).read();
    var car = network.getModes().get(PredefinedModeType.CAR);

    assertTrue(isModeAllowedOnWay(network.getTransportLayers().getFirst(), car, "23"));
  }
}

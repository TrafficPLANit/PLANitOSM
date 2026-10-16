package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.TRAFFIC_SIGNAL_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicBannedTurn;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The modes a banned turn gets from the tags of its OSM turn restriction. The scenario is a four-arm junction of
 * two-way roads with five restrictions via its centre node. The west arm has no pedestrians, the east arm no heavy
 * goods vehicles
 * <pre>
 *             11
 *              |  way 20
 *   13 ------- 1 ------- 14      relation 101: north arm onto east arm, restriction=no_left_turn, except=psv
 *     way 22   |   way 23        relation 102: north arm onto west arm, restriction:hgv=no_right_turn
 *     foot=no  |   hgv=no        relation 103: south arm back onto itself, restriction=no_u_turn
 *              |  way 21         relation 104: south arm onto east arm, restriction=no_right_turn
 *             12                 relation 105: west arm back onto itself, restriction=no_u_turn
 * </pre>
 *
 * @author markr
 */
public class OsmTurnRestrictionModesTest {

  /** the network read from the scenario */
  private MacroscopicNetwork network;

  /** its only layer */
  private MacroscopicNetworkLayer layer;

  /** the banned turn from one segment onto another */
  private MacroscopicBannedTurn bannedTurn(MacroscopicLinkSegment from, MacroscopicLinkSegment to) {
    return layer.getBannedTurns().getBySegment(from).stream()
        .filter(ban -> ban.getSegmentFrom() == from && ban.getSegmentTo() == to)
        .map(MacroscopicBannedTurn.class::cast).findFirst().orElse(null);
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    var reader = OsmNetworkReaderFactory.create(
        TRAFFIC_SIGNAL_SCENARIO_DIR.resolve("turn_restriction_modes.osm").toString(), CountryNames.AUSTRALIA);
    network = reader.read();
    layer = network.getTransportLayers().getFirst();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** A restriction tag with an except tag bans every mode of the layer but those excepted */
  @Test
  public void noLeftTurnExceptBuses() {
    var ban = bannedTurn(segment(layer, 11, 1), segment(layer, 1, 14));

    assertTrue(ban.hasExplicitlyBannedModes());
    assertTrue(ban.isModeBanned(network.getModes().get(PredefinedModeType.CAR)));
    assertFalse(ban.isModeBanned(network.getModes().get(PredefinedModeType.BUS)));
  }

  /** A restriction tag for one mode bans that mode only, where a plain restriction tag would be needed before */
  @Test
  public void restrictionForTrucksOnly() {
    var ban = bannedTurn(segment(layer, 11, 1), segment(layer, 1, 13));

    assertEquals(Set.of(network.getModes().get(PredefinedModeType.HEAVY_GOODS_VEHICLE)),
        ban.getExplicitlyBannedModes());
  }

  /** A restriction tag is for vehicles: it bans no mode that is not a vehicle, such as pedestrians */
  @Test
  public void restrictionLeavesOutModesThatAreNotVehicles() {
    var ban = bannedTurn(segment(layer, 12, 1), segment(layer, 1, 12));

    assertTrue(ban.isModeBanned(network.getModes().get(PredefinedModeType.CAR)));
    assertTrue(ban.isModeBanned(network.getModes().get(PredefinedModeType.BICYCLE)));
    assertFalse(ban.isModeBanned(network.getModes().get(PredefinedModeType.PEDESTRIAN)));
  }

  /** A restriction tag bans only modes that can make the turn: a mode not allowed on the segment it ends on is left
   * out */
  @Test
  public void restrictionForModesAllowedOnBothSegmentsOnly() {
    var ban = bannedTurn(segment(layer, 12, 1), segment(layer, 1, 14));

    assertTrue(ban.isModeBanned(network.getModes().get(PredefinedModeType.CAR)));
    assertFalse(ban.isModeBanned(network.getModes().get(PredefinedModeType.HEAVY_GOODS_VEHICLE)));
  }

  /** A restriction tag that bans every mode that can make the turn names none */
  @Test
  public void restrictionForEveryModeThatCanMakeTheTurn() {
    var ban = bannedTurn(segment(layer, 13, 1), segment(layer, 1, 13));

    assertFalse(ban.hasExplicitlyBannedModes());
    assertEquals(5, layer.getBannedTurns().size());
  }
}

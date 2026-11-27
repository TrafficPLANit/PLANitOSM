package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.LANES_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.createReaderKeepingDanglingSubnetworks;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.settings.override.OsmWayLaneOverride;
import org.goplanit.osm.tags.OsmRoadModeTags;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.settings.UserOverride;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The lanes of OSM ways overridden in the settings, on hand-made primary roads: an override takes the place of the
 * tags and the defaults in each direction it gives, on every link made from the way, and only where the way has
 * traffic; a way id no layer has is recorded as not found, with the reason as its detail; and an overridden direction
 * is neither counted as missing lanes nor rounded up
 * <pre>
 *   1 -------- 2     way 60, two-way, no lanes tag
 *   3 -------- 4     way 61, two-way, lanes=2
 *   5 -------- 6     way 62, two-way, lanes=4
 *   7 --- 8 -- 9     way 63, two-way, no lanes tag, two links: way 64 meets it at node 8
 *         |
 *        10          way 64, 8-10, two-way, no lanes tag
 *  11 -------> 12    way 65, one-way, no lanes tag
 *  13 -------- 14    way 66, two-way, lanes=3
 *  15 -------- 16    way 67, two-way, lanes=3
 *  17 ======== 18    way 68, railway
 * </pre>
 *
 * @author markr
 */
public class OsmWayLaneOverrideTest {

  /** the logger, giving the log output PLANit's format */
  private static Logger LOGGER = null;

  /** the scenario */
  private static final String SCENARIO = LANES_SCENARIO_DIR.resolve("way_lane_overrides.osm").toString();

  /**
   * A reader of the scenario, keeping every way, since its roads are mostly separate, with the settings changed
   *
   * @param settingsChange giving the overrides
   * @return the reader, not read yet
   */
  private static OsmNetworkReader createReader(Consumer<OsmNetworkReaderSettings> settingsChange) {
    return createReaderKeepingDanglingSubnetworks(SCENARIO, settingsChange);
  }

  /**
   * A lane override without a reason
   *
   * @param forward lanes forward along the way, null for none
   * @param backward lanes backward along the way, null for none
   * @return the override
   */
  private static UserOverride<OsmWayLaneOverride> lanes(Integer forward, Integer backward) {
    return UserOverride.of(new OsmWayLaneOverride(forward, backward));
  }

  /**
   * The number of directions a layer was given default lanes for, as no lanes were known
   *
   * @param reader that read the layer
   * @param layer read
   * @return the count
   */
  private static long missingLaneCount(OsmNetworkReader reader, MacroscopicNetworkLayer layer) {
    return reader.getNetworkReaderData().getLayerParser(layer).getLayerData().getProfiler().getMissingLaneCount();
  }

  /**
   * Create the logger once for all tests of the class
   *
   * @throws Exception when the log file cannot be opened
   */
  @BeforeAll
  public static void setUpLogger() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(OsmWayLaneOverrideTest.class);
    }
  }

  /** Close the logger after all tests of the class */
  @AfterAll
  public static void tearDownLogger() {
    Logging.closeLogger(LOGGER);
  }

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

  /** Both directions of an untagged two-way road overridden as 2 give its link segments 2 lanes, where the default
   * gives 1 */
  @Test
  public void bothDirectionsOverriddenOnAnUntaggedTwoWayRoad() {
    var layer = createReader(settings -> settings.overrideOsmWayLanes(60, lanes(2, 2)))
        .read().getTransportLayers().getFirst();

    assertEquals(2, segment(layer, 1, 2).getNumberOfLanes());
    assertEquals(2, segment(layer, 2, 1).getNumberOfLanes());
  }

  /** An override wins over the lanes tag: a way tagged lanes=2 overridden as 3 forward and 2 backward */
  @Test
  public void overrideWinsOverTheTags() {
    var layer = createReader(settings -> settings.overrideOsmWayLanes(61, lanes(3, 2)))
        .read().getTransportLayers().getFirst();

    assertEquals(3, segment(layer, 3, 4).getNumberOfLanes());
    assertEquals(2, segment(layer, 4, 3).getNumberOfLanes());
  }

  /** A way tagged lanes=4 with only its backward lanes overridden, as 1, keeps 2 lanes forward from the tag */
  @Test
  public void oneDirectionOverridden() {
    var layer = createReader(settings -> settings.overrideOsmWayLanes(62, lanes(null, 1)))
        .read().getTransportLayers().getFirst();

    assertEquals(2, segment(layer, 5, 6).getNumberOfLanes());
    assertEquals(1, segment(layer, 6, 5).getNumberOfLanes());
  }

  /** An untagged two-way road with only its forward lanes overridden, as 2, gets the default, 1, backward, and that
   * direction is still counted as missing lanes, as it was without the override */
  @Test
  public void oneDirectionOverriddenOnAnUntaggedRoad() {
    var withoutOverride = createReader(settings -> { });
    var missingWithout = missingLaneCount(withoutOverride, withoutOverride.read().getTransportLayers().getFirst());
    var withOverride = createReader(settings -> settings.overrideOsmWayLanes(60, lanes(2, null)));
    var layer = withOverride.read().getTransportLayers().getFirst();

    assertEquals(2, segment(layer, 1, 2).getNumberOfLanes());
    assertEquals(1, segment(layer, 2, 1).getNumberOfLanes());
    assertEquals(missingWithout, missingLaneCount(withOverride, layer));
  }

  /** Every link made from an overridden way has the lanes overridden: both links of way 63, broken at node 8 where
   * way 64 meets it. Way 64, not overridden, keeps its default */
  @Test
  public void everyLinkOfTheWay() {
    var layer = createReader(settings -> settings.overrideOsmWayLanes(63, lanes(2, 2)))
        .read().getTransportLayers().getFirst();

    assertEquals(2, segment(layer, 7, 8).getNumberOfLanes());
    assertEquals(2, segment(layer, 8, 7).getNumberOfLanes());
    assertEquals(2, segment(layer, 8, 9).getNumberOfLanes());
    assertEquals(2, segment(layer, 9, 8).getNumberOfLanes());
    assertEquals(1, segment(layer, 8, 10).getNumberOfLanes());
  }

  /** On a one-way way overridden as 3 forward and 2 backward, the backward lanes are ignored: no backward link segment
   * is made, and nothing is recorded. Walking is switched off, as one-way does not hold for pedestrians, whose
   * backward link segment would otherwise exist, and take the lanes overridden as any existing segment does */
  @Test
  public void backwardLanesOnAOneWayWay() {
    var reader = createReader(settings -> {
      settings.getHighwaySettings().deactivateOsmRoadMode(OsmRoadModeTags.FOOT);
      settings.overrideOsmWayLanes(65, lanes(3, 2));
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(3, segment(layer, 11, 12).getNumberOfLanes());
    assertThrows(NoSuchElementException.class, () -> segment(layer, 12, 11));
    assertEquals(List.of(), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.OSM_WAY_LANE_OVERRIDE_NOT_FOUND));
  }

  /** A way id the OSM data does not have, such as a mistyped one, is recorded once as not found, without a detail when
   * the override has no reason; a way that is found is not recorded */
  @Test
  public void mistypedWayIdIsRecordedAsNotFound() {
    var reader = createReader(settings -> {
      settings.overrideOsmWayLanes(60, lanes(2, 2));
      settings.overrideOsmWayLanes(999999999L, lanes(2, 2));
    });
    reader.read();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("999999999"), diagnostics.getEntityIds(OsmPlanitEntityIssue.OSM_WAY_LANE_OVERRIDE_NOT_FOUND));
    assertNull(diagnostics.getDetail(OsmPlanitEntityIssue.OSM_WAY_LANE_OVERRIDE_NOT_FOUND, "999999999"));
  }

  /** The reason of an override that is not found is the detail of its entry */
  @Test
  public void notFoundWithAReason() {
    var reader = createReader(settings -> settings.overrideOsmWayLanes(999999999L,
        UserOverride.of(new OsmWayLaneOverride(2, 2), "checked on imagery")));
    reader.read();

    assertEquals("checked on imagery", reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getDetail(OsmPlanitEntityIssue.OSM_WAY_LANE_OVERRIDE_NOT_FOUND, "999999999"));
  }

  /** An overridden road read with the railways is not recorded as not found. The reader puts every mode in one layer,
   * so this is as close as a test gets to a road that only the road layer turns into links */
  @Test
  public void overriddenRoadReadWithTheRailwaysIsFound() {
    var reader = createReader(settings -> {
      settings.activateRailwayParser(true);
      settings.overrideOsmWayLanes(60, lanes(2, 2));
    });
    reader.read();

    assertEquals(List.of(), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.OSM_WAY_LANE_OVERRIDE_NOT_FOUND));
  }

  /** Without overrides nothing about them is recorded, and the lanes are as tagged or by default */
  @Test
  public void noOverrides() {
    var reader = createReader(settings -> { });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(List.of(), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.OSM_WAY_LANE_OVERRIDE_NOT_FOUND));
    assertEquals(1, segment(layer, 1, 2).getNumberOfLanes());
    assertEquals(1, segment(layer, 3, 4).getNumberOfLanes());
  }

  /** Two-way ways tagged lanes=3: overridden in both directions, as 2 forward and 1 backward, the way is not recorded
   * as rounded up; with only its forward lanes overridden, as 1, its backward lanes are rounded up to 2 and it is */
  @Test
  public void oddTotalOverriddenInBothOrOneDirection() {
    var reader = createReader(settings -> {
      settings.overrideOsmWayLanes(66, lanes(2, 1));
      settings.overrideOsmWayLanes(67, lanes(1, null));
    });
    var layer = reader.read().getTransportLayers().getFirst();

    assertEquals(2, segment(layer, 13, 14).getNumberOfLanes());
    assertEquals(1, segment(layer, 14, 13).getNumberOfLanes());
    assertEquals(1, segment(layer, 15, 16).getNumberOfLanes());
    assertEquals(2, segment(layer, 16, 15).getNumberOfLanes());
    assertEquals(List.of("67"), reader.getNetworkReaderData().getPlanitEntityDiagnostics()
        .getEntityIds(OsmPlanitEntityIssue.LANES_ROUNDED_UP_FOR_ODD_TOTAL_ON_TWO_WAY_ROAD));
  }

  /** The directions of an untagged way overridden in both directions are no longer counted as missing lanes */
  @Test
  public void overriddenDirectionIsNotCountedAsMissingLanes() {
    var withoutOverride = createReader(settings -> { });
    var missingWithout = missingLaneCount(withoutOverride, withoutOverride.read().getTransportLayers().getFirst());
    var withOverride = createReader(settings -> settings.overrideOsmWayLanes(60, lanes(2, 2)));
    var missingWith = missingLaneCount(withOverride, withOverride.read().getTransportLayers().getFirst());

    assertEquals(missingWithout - 1, missingWith);
  }
}

package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.LANES_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A two-way road tagged with an odd total of lanes and no lanes per direction, rounded up in each direction and
 * recorded for checking, since the lanes in each direction may have been tagged as the total
 * <pre>
 *   6
 *   |  way 32, primary, one-way, lanes=3
 *   1 -------- 2 -------- 3 ---- 4      way 31, residential, lanes=3: 2-3-4-5-3, visiting node 3 twice
 *   |  way 30, primary,    \     |
 *   |  lanes=3              ---- 5
 *   7  way 33, primary, lanes=3, lanes:forward=2
 * </pre>
 *
 * @author markr
 */
public class OsmOddLanesTotalOnTwoWayRoadTest {

  /** the scenario */
  private static final String SCENARIO =
      LANES_SCENARIO_DIR.resolve("odd_lanes_total_on_two_way_roads.osm").toString();

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

  /** An odd total on a two-way road without lanes per direction is rounded up in each direction and recorded once per
   * way, also for a way visiting a node twice, which is parsed in parts. A one-way road, or a road with lanes per
   * direction, is not recorded */
  @Test
  public void oddTotalOnTwoWayRoadIsRoundedUpAndRecordedOncePerWay() {
    var reader = OsmNetworkReaderFactory.create(SCENARIO, CountryNames.AUSTRALIA);
    var layer = reader.read().getTransportLayers().getFirst();
    var diagnostics = reader.getNetworkReaderData().getPlanitEntityDiagnostics();

    assertEquals(List.of("30", "31"),
        diagnostics.getEntityIds(OsmPlanitEntityIssue.LANES_ROUNDED_UP_FOR_ODD_TOTAL_ON_TWO_WAY_ROAD));
    assertEquals(2, segment(layer, 1, 2).getNumberOfLanes());
    assertEquals(2, segment(layer, 2, 1).getNumberOfLanes());
  }
}

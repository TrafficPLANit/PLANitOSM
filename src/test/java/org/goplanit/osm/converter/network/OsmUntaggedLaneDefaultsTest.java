package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.LANES_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.readFirstLayerKeepingDanglingSubnetworks;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.goplanit.utils.id.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Roads without lane tags get the default lanes of their type, a total for the way, and the default capacity per lane
 * of their type
 * <pre>
 *   1 ------ 2    way 50, trunk, two-way
 *   3 -----> 4    way 51, trunk, one-way
 *   5 -----> 6    way 52, primary, one-way
 *   7 ------ 8    way 53, primary, two-way
 *   9 -----> 10   way 54, motorway_link, one-way
 *   11 ----- 12   way 55, secondary, two-way
 *   13 ----> 14   way 56, secondary, one-way
 * </pre>
 *
 * @author markr
 */
public class OsmUntaggedLaneDefaultsTest {

  /** the scenario */
  private static final String SCENARIO = LANES_SCENARIO_DIR.resolve("untagged_lane_defaults.osm").toString();

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

  /** The default of a trunk, primary or secondary road is 2 lanes for the way: 2 lanes when one-way, 1 in each
   * direction when two-way. A motorway_link has 1 lane */
  @Test
  public void untaggedRoadGetsDefaultLanesOfItsTypeForTheWay() {
    var layer = readFirstLayerKeepingDanglingSubnetworks(SCENARIO);

    assertEquals(1, segment(layer, 1, 2).getNumberOfLanes());
    assertEquals(1, segment(layer, 2, 1).getNumberOfLanes());
    assertEquals(2, segment(layer, 3, 4).getNumberOfLanes());
    assertEquals(2, segment(layer, 5, 6).getNumberOfLanes());
    assertEquals(1, segment(layer, 7, 8).getNumberOfLanes());
    assertEquals(1, segment(layer, 9, 10).getNumberOfLanes());
    assertEquals(1, segment(layer, 11, 12).getNumberOfLanes());
    assertEquals(2, segment(layer, 13, 14).getNumberOfLanes());
  }

  /** The capacity of an untagged road is its default lanes times the default capacity per lane of its type: trunk
   * 1,800, primary 1,700, motorway_link 1,800 and secondary 1,500 pcu/h per lane */
  @Test
  public void untaggedRoadGetsDefaultCapacityOfItsType() {
    var layer = readFirstLayerKeepingDanglingSubnetworks(SCENARIO);

    assertEquals(1800, segment(layer, 1, 2).getCapacityOrDefaultPcuH(), 1e-6);
    assertEquals(3600, segment(layer, 3, 4).getCapacityOrDefaultPcuH(), 1e-6);
    assertEquals(3400, segment(layer, 5, 6).getCapacityOrDefaultPcuH(), 1e-6);
    assertEquals(1700, segment(layer, 7, 8).getCapacityOrDefaultPcuH(), 1e-6);
    assertEquals(1800, segment(layer, 9, 10).getCapacityOrDefaultPcuH(), 1e-6);
    assertEquals(1500, segment(layer, 11, 12).getCapacityOrDefaultPcuH(), 1e-6);
    assertEquals(3000, segment(layer, 13, 14).getCapacityOrDefaultPcuH(), 1e-6);
  }
}

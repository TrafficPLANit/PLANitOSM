package org.goplanit.osm.converter.network;

import static org.goplanit.osm.test.OsmLayerTestUtils.LANES_SCENARIO_DIR;
import static org.goplanit.osm.test.OsmLayerTestUtils.readFirstLayerKeepingDanglingSubnetworks;
import static org.goplanit.osm.test.OsmLayerTestUtils.segment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;

import org.goplanit.utils.id.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Roads tagged oneway=-1, travelled against their geometry
 * <pre>
 *   1 <------- 2 <------- 3 ---> 4      way 40, residential, oneway=-1, lanes=2: travelled 2 to 1
 *       way 40      way 41  ^    |      way 41, residential, oneway=-1, lanes=2: 2-3-4-5-3, visiting node 3 twice,
 *                            \   v      its loop drawn clockwise, travelled anticlockwise 3-5-4-3, the arrows showing
 *                             -- 5      the drawn direction
 * </pre>
 *
 * @author markr
 */
public class OsmReversedOneWayRoadTest {

  /** the scenario */
  private static final String SCENARIO = LANES_SCENARIO_DIR.resolve("reversed_one_way_roads.osm").toString();

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

  /** A road tagged oneway=-1 has all its lanes in the direction it is travelled, against its geometry */
  @Test
  public void reversedOneWayRoadHasAllLanesInItsDirection() {
    var layer = readFirstLayerKeepingDanglingSubnetworks(SCENARIO);

    assertEquals(2, segment(layer, 2, 1).getNumberOfLanes());
  }

  /** A way tagged oneway=-1 that visits a node twice keeps the direction it is travelled, against its geometry, on
   * each of its links, its loop included: that direction is not replaced by the country's default direction for
   * circular ways */
  @Test
  public void reversedOneWayWayVisitingNodeTwiceKeepsItsDirection() {
    var layer = readFirstLayerKeepingDanglingSubnetworks(SCENARIO);

    var linksOfWay =
        layer.getLinks().stream().filter(link -> "41".equals(link.getExternalId())).collect(Collectors.toList());
    assertEquals(3, linksOfWay.size());
    assertTrue(linksOfWay.stream().allMatch(link -> link.hasEdgeSegmentBa()));
  }
}

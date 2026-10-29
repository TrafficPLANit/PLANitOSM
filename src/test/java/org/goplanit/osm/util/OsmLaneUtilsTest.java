package org.goplanit.osm.util;

import java.util.Map;

import com.slimjars.dist.gnu.trove.list.array.TLongArrayList;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.impl.Way;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.utils.misc.Pair;
import org.junit.jupiter.api.Test;

import static org.goplanit.osm.tags.OsmJunctionTags.JUNCTION;
import static org.goplanit.osm.tags.OsmJunctionTags.ROUNDABOUT;
import static org.goplanit.osm.tags.OsmLaneTags.LANES;
import static org.goplanit.osm.tags.OsmOneWayTags.ONEWAY;
import static org.goplanit.osm.tags.OsmOneWayTags.ONE_WAY_REVERSE_DIRECTION;
import static org.goplanit.osm.tags.OsmOneWayTags.YES;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the lanes parsed per direction from the lane tags of an OSM highway, see
 * {@link OsmLaneUtils#extractDirectionalHighwayLanes(OsmWay, Map)}
 *
 * @author markr
 */
public class OsmLaneUtilsTest {

  /** an OSM way to parse, its tags are passed separately */
  private static final OsmWay WAY = new Way(1, new TLongArrayList(new long[] {1, 2}));

  /** A two-way road tagged with an even total and no lanes per direction has half of them in each direction */
  @Test
  public void evenTotalOnTwoWayRoadIsSplitEvenly() {
    assertEquals(Pair.of(2, 2), OsmLaneUtils.extractDirectionalHighwayLanes(WAY, Map.of(LANES, "4")));
  }

  /** A two-way road tagged with an odd total and no lanes per direction has it rounded up in each direction */
  @Test
  public void oddTotalOnTwoWayRoadIsRoundedUpInEachDirection() {
    assertEquals(Pair.of(2, 2), OsmLaneUtils.extractDirectionalHighwayLanes(WAY, Map.of(LANES, "3")));
  }

  /** A roundabout or motorway without a oneway tag is one-way by implication, so all its lanes are in the direction
   * it is travelled, whichever that is */
  @Test
  public void totalOnImpliedOneWayIsAllInEitherDirection() {
    assertEquals(Pair.of(2, 2),
        OsmLaneUtils.extractDirectionalHighwayLanes(WAY, Map.of(LANES, "2", JUNCTION, ROUNDABOUT)));
    assertEquals(Pair.of(3, 3), OsmLaneUtils.extractDirectionalHighwayLanes(
        WAY, Map.of(LANES, "3", OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.MOTORWAY)));
  }

  /** A one-way road has all its lanes in its one direction, which is backward when it is reversed */
  @Test
  public void totalOnOneWayRoadIsAllInItsDirection() {
    assertEquals(Pair.of(4, null), OsmLaneUtils.extractDirectionalHighwayLanes(WAY, Map.of(LANES, "4", ONEWAY, YES)));
    assertEquals(Pair.of(null, 4),
        OsmLaneUtils.extractDirectionalHighwayLanes(WAY, Map.of(LANES, "4", ONEWAY, ONE_WAY_REVERSE_DIRECTION)));
  }
}

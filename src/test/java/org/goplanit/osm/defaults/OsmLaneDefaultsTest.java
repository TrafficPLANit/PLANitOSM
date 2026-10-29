package org.goplanit.osm.defaults;

import static org.goplanit.osm.tags.OsmHighwayTags.MOTORWAY;
import static org.goplanit.osm.tags.OsmHighwayTags.MOTORWAY_LINK;
import static org.goplanit.osm.tags.OsmHighwayTags.PRIMARY;
import static org.goplanit.osm.tags.OsmHighwayTags.RESIDENTIAL;
import static org.goplanit.osm.tags.OsmHighwayTags.SECONDARY;
import static org.goplanit.osm.tags.OsmHighwayTags.TRUNK;
import static org.goplanit.osm.tags.OsmHighwayTags.TRUNK_LINK;
import static org.goplanit.osm.tags.OsmJunctionTags.CIRCULAR;
import static org.goplanit.osm.tags.OsmJunctionTags.JUNCTION;
import static org.goplanit.osm.tags.OsmJunctionTags.ROUNDABOUT;
import static org.goplanit.osm.tags.OsmOneWayTags.ONEWAY;
import static org.goplanit.osm.tags.OsmOneWayTags.YES;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.junit.jupiter.api.Test;

/**
 * Tests the default lanes used when the tags of a way give none, see {@link OsmLaneDefaults}
 *
 * @author markr
 */
public class OsmLaneDefaultsTest {

  /** the highway key */
  private static final String HIGHWAY = OsmHighwayTags.getHighwayKeyTag();

  /**
   * The default lanes in each direction of a highway way with the given tags
   *
   * @param tags of the way
   * @return default lanes in each direction
   */
  private static Integer defaultLanesPerDirectionOfHighway(Map<String, String> tags) {
    return new OsmLaneDefaults().getDefaultLanesPerDirection(HIGHWAY, tags);
  }

  /** A highway default is a total for the way: all of it in the one direction of a one-way way, half of it, rounded
   * up, in each direction of a two-way way */
  @Test
  public void highwayDefaultIsTotalForTheWay() {
    assertEquals(2, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, TRUNK, ONEWAY, YES)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, TRUNK)));
    assertEquals(2, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, PRIMARY, ONEWAY, YES)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, PRIMARY)));
    assertEquals(2, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, SECONDARY, ONEWAY, YES)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, SECONDARY)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, RESIDENTIAL, ONEWAY, YES)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, RESIDENTIAL)));
    assertEquals(2, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, MOTORWAY)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, MOTORWAY_LINK, ONEWAY, YES)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, TRUNK_LINK, ONEWAY, YES)));
  }

  /** A roundabout gets half of the total, rounded up, as a two-way way does, whether or not it is tagged one-way: most
   * roundabouts have a single lane, also on roads whose one-way carriageways have two */
  @Test
  public void roundaboutDefaultIsHalfOfTheTotal() {
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, SECONDARY, JUNCTION, ROUNDABOUT)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, PRIMARY, JUNCTION, ROUNDABOUT, ONEWAY, YES)));
    assertEquals(1, defaultLanesPerDirectionOfHighway(Map.of(HIGHWAY, TRUNK, JUNCTION, CIRCULAR)));
  }

  /** Railway tracks are per direction, the same whether the way is one-way or not */
  @Test
  public void railwayDefaultIsPerDirection() {
    var defaults = new OsmLaneDefaults();
    var railway = OsmRailwayTags.getRailwayKeyTag();

    assertEquals(1, defaults.getDefaultLanesPerDirection(railway, Map.of(railway, OsmRailwayTags.RAIL, ONEWAY, YES)));
    assertEquals(1, defaults.getDefaultLanesPerDirection(railway, Map.of(railway, OsmRailwayTags.RAIL)));
  }
}

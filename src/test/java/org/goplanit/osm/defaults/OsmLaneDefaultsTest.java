package org.goplanit.osm.defaults;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.junit.jupiter.api.Test;

/**
 * Tests the default lanes used when the tags of a way give none, see {@link OsmLaneDefaults}
 *
 * @author markr
 */
public class OsmLaneDefaultsTest {

  /** A highway default is a total for the way: all of it in the one direction of a one-way way, half of it, rounded
   * up, in each direction of a two-way way */
  @Test
  public void highwayDefaultIsTotalForTheWay() {
    var defaults = new OsmLaneDefaults();
    var highway = OsmHighwayTags.getHighwayKeyTag();

    assertEquals(2, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.TRUNK, true));
    assertEquals(1, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.TRUNK, false));
    assertEquals(2, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.PRIMARY, true));
    assertEquals(1, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.PRIMARY, false));
    assertEquals(1, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.RESIDENTIAL, true));
    assertEquals(1, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.RESIDENTIAL, false));
    assertEquals(2, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.MOTORWAY, true));
    assertEquals(1, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.MOTORWAY_LINK, true));
    assertEquals(1, defaults.getDefaultLanesPerDirection(highway, OsmHighwayTags.TRUNK_LINK, true));
  }

  /** Railway tracks are per direction, the same whether the way is one-way or not */
  @Test
  public void railwayDefaultIsPerDirection() {
    var defaults = new OsmLaneDefaults();
    var railway = OsmRailwayTags.getRailwayKeyTag();

    assertEquals(1, defaults.getDefaultLanesPerDirection(railway, OsmRailwayTags.RAIL, true));
    assertEquals(1, defaults.getDefaultLanesPerDirection(railway, OsmRailwayTags.RAIL, false));
  }
}

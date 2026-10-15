package org.goplanit.osm.test;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.util.PlanitLinkOsmUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the OSM specific link utilities
 *
 * @author markr
 */
public class PlanitLinkOsmUtilsTest {

  private MacroscopicNetworkLayer layer;

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    layer = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken()).getTransportLayers().getFactory().registerNew();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** a link between two new nodes, created for the given OSM way, with the given name, none when null */
  private MacroscopicLink link(String osmWayId, String name) {
    var link = layer.getLinks().getFactory().registerNew(
        layer.getNodes().getFactory().registerNew(), layer.getNodes().getFactory().registerNew(), 1, true);
    link.setExternalId(osmWayId);
    link.setName(name);
    return link;
  }

  /**
   * Two links are on the same OSM way when their external ids, the OSM way ids, match, as when PLANit broke one OSM way
   * into several links. Links on different OSM ways count as the same when they have the same name. A blank name, or
   * none, never matches
   */
  @Test
  public void isSameOsmWayOrNameMatchesOnWayIdOrName() {
    assertTrue(PlanitLinkOsmUtils.isSameOsmWayOrName(link("100", null), link("100", null)));
    assertTrue(PlanitLinkOsmUtils.isSameOsmWayOrName(link("200", "George Street"), link("300", "George Street")));
    assertFalse(PlanitLinkOsmUtils.isSameOsmWayOrName(link("400", "George Street"), link("500", "Pitt Street")));
    assertFalse(PlanitLinkOsmUtils.isSameOsmWayOrName(link("600", null), link("700", null)));
    assertFalse(PlanitLinkOsmUtils.isSameOsmWayOrName(link("800", " "), link("900", " ")));
  }
}

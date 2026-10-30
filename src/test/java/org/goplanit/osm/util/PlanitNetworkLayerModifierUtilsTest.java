package org.goplanit.osm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the utilities for modifying PLANit network layers while parsing OSM: grouping broken links by the OSM way
 * they come from
 *
 * @author markr
 */
public class PlanitNetworkLayerModifierUtilsTest {

  /** the layer the links are on */
  private MacroscopicNetworkLayer layer;

  /** register a node at a position */
  private Node node(double x, double y) {
    var node = layer.getNodes().getFactory().registerNew();
    node.setPosition(PlanitJtsUtils.createPoint(x, y));
    return node;
  }

  /** register a link between two new nodes, with the OSM way id as external id, or none when null */
  private MacroscopicLink link(double x, String osmWayId) {
    var link = layer.getLinks().getFactory().registerNew(node(x, 0), node(x + 1, 0), 0.001, true);
    link.setExternalId(osmWayId);
    return link;
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
    var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    layer = network.getTransportLayers().getFactory().registerNew();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /**
   * Broken links are grouped by the OSM way they come from; a link the reader added itself, such as one connecting a
   * dangling ferry stop, comes from no OSM way and is left out
   */
  @Test
  public void brokenLinksGroupedByOsmWayLeaveOutLinksFromNoOsmWay() {
    var fromOsmWay = link(0, "12");
    var fromOsmWayBrokenOff = link(1, "12");
    var addedByReader = link(2, null);
    var addedByReaderBrokenOff = link(3, null);
    var brokenLinks = Map.of(
        fromOsmWay.getId(), Pair.of(fromOsmWay, fromOsmWayBrokenOff),
        addedByReader.getId(), Pair.of(addedByReader, addedByReaderBrokenOff));

    var grouped = PlanitNetworkLayerModifierUtils.convertBrokenLinksByGroupingThemWithCustomKey(
        brokenLinks, PlanitOsmUtils::parseOsmIdOrNull);

    assertEquals(Map.of(12L, Set.of(fromOsmWay, fromOsmWayBrokenOff)), grouped);
  }
}

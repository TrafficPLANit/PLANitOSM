package org.goplanit.osm.converter.network.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.logging.Logger;

import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

/**
 * Tests the data the OSM network reader keeps per layer: the PLANit links a location is internal to
 *
 * @author markr
 */
public class OsmNetworkReaderLayerDataTest {

  /** the logger, giving the log output PLANit's format */
  private static Logger LOGGER = null;

  /**
   * Create the logger once for all tests of the class
   *
   * @throws Exception when the log file cannot be opened
   */
  @BeforeAll
  public static void setUpLogger() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(OsmNetworkReaderLayerDataTest.class);
    }
  }

  /** Close the logger after all tests of the class */
  @AfterAll
  public static void tearDownLogger() {
    Logging.closeLogger(LOGGER);
  }

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /**
   * A link the reader added itself, such as one connecting a dangling ferry stop, comes from no OSM way and so has no
   * OSM way id; a location registered as internal to it is found on that link as it is
   */
  @Test
  public void locationInternalToLinkWithoutOsmWayIsFoundOnIt() {
    var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    var layer = network.getTransportLayers().getFactory().registerNew();
    var nodeA = layer.getNodes().getFactory().registerNew();
    nodeA.setPosition(PlanitJtsUtils.createPoint(0, 0));
    var nodeB = layer.getNodes().getFactory().registerNew();
    nodeB.setPosition(PlanitJtsUtils.createPoint(2, 0));
    var link = layer.getLinks().getFactory().registerNew(nodeA, nodeB, 0.002, true);
    link.setGeometry(PlanitJtsUtils.createLineString(new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(2, 0)));
    var location = PlanitJtsUtils.createPoint(1, 0);
    var layerData = new OsmNetworkReaderLayerData();
    layerData.registerLocationAsInternalToPlanitLink(location, link);

    assertEquals(List.of(link), layerData.findPlanitLinksWithInternalLocation(location));
  }
}

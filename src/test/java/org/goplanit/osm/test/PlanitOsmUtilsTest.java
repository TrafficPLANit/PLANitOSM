package org.goplanit.osm.test;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests reading the OSM id of a PLANit entity created from an OSM entity
 *
 * @author markr
 */
public class PlanitOsmUtilsTest {

  @BeforeEach
  public void setUp() {
    IdGenerator.reset();
  }

  @AfterEach
  public void tearDown() {
    IdGenerator.reset();
  }

  /** the OSM id is the entity's external id read as a number, also beyond the range of an int */
  @Test
  public void parseOsmIdReadsTheExternalIdAsNumber() {
    var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    var node = network.getTransportLayers().getFactory().registerNew().getNodes().getFactory().registerNew();
    node.setExternalId("6581009093");

    assertEquals(6581009093L, PlanitOsmUtils.parseOsmId(node));
  }
}

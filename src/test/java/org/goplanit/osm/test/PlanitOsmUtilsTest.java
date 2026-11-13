package org.goplanit.osm.test;

import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests reading the OSM id of a PLANit entity created from an OSM entity
 *
 * @author markr
 */
public class PlanitOsmUtilsTest {

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
      LOGGER = Logging.createLogger(PlanitOsmUtilsTest.class);
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

  /** the OSM id is the entity's external id read as a number, also beyond the range of an int */
  @Test
  public void parseOsmIdReadsTheExternalIdAsNumber() {
    var network = new MacroscopicNetwork(IdGroupingToken.collectGlobalToken());
    var node = network.getTransportLayers().getFactory().registerNew().getNodes().getFactory().registerNew();
    node.setExternalId("6581009093");

    assertEquals(6581009093L, PlanitOsmUtils.parseOsmId(node));
  }
}

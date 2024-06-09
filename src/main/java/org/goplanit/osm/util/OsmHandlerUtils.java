package org.goplanit.osm.util;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmReader;
import org.goplanit.utils.exceptions.PlanItRunTimeException;

import java.util.logging.Logger;

/**
 * Utilities for OSM4J handler classes
 */
public class OsmHandlerUtils {

  private static final Logger LOGGER = Logger.getLogger(OsmHandlerUtils.class.getCanonicalName());

  /** Read based on reader and handler where the reader performs a callback to the handler provided
   *
   * @param osmReader to use
   * @param osmHandler to use
   */
  public static void readWithHandler(OsmReader osmReader, DefaultOsmHandler osmHandler) {

    try {
      osmReader.setHandler(osmHandler);
      osmReader.read();
    } catch (OsmInputException e) {
      String cause = e.getCause()!=null ? e.getCause().getMessage() : "";
      LOGGER.severe(e.getMessage() + "cause:" + cause);
      throw new PlanItRunTimeException("Error during parsing of OSM file",e);
    }
  }
}

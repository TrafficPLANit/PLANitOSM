package org.goplanit.osm.util;

import java.util.Map;

import org.goplanit.osm.converter.network.OsmIntersectionSettings;
import org.goplanit.osm.converter.network.OsmTrafficSignalDirection;
import org.goplanit.osm.tags.OsmTrafficSignalTags;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Utilities for OSM traffic signals
 *
 * @author markr
 */
public final class OsmTrafficSignalUtils {

  /** Constructor to disallow instance creation */
  private OsmTrafficSignalUtils() {
  }

  /**
   * The direction along its OSM way a signal applies to: the one set in the settings, see
   * {@link OsmIntersectionSettings#overrideTrafficSignalDirection(long, OsmTrafficSignalDirection)}, otherwise the one
   * tagged, see {@link OsmTrafficSignalTags#getRecognisedDirection(Map)}
   *
   * @param signal the OSM node of the signal
   * @param tags of the signal
   * @param intersectionSettings holding the directions set
   * @return forward, backward or both, null when none is set and none recognised is tagged
   */
  public static String getDirectionOf(OsmNode signal, Map<String, String> tags,
      OsmIntersectionSettings intersectionSettings) {
    var set = intersectionSettings.getTrafficSignalDirectionOverride(signal.getId());
    return set != null ? set.getTagValue() : OsmTrafficSignalTags.getRecognisedDirection(tags);
  }
}

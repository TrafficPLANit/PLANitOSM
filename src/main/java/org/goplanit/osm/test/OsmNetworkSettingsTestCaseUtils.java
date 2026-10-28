package org.goplanit.osm.test;

import org.goplanit.osm.converter.network.OsmIntersectionSettings;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.OsmTrafficSignalDirection;
import org.goplanit.osm.converter.network.OsmTrafficSignalKind;
import org.goplanit.osm.tags.OsmRoadModeTags;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class OsmNetworkSettingsTestCaseUtils {


  /**
   * When applied to the 2023 PBF it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void sydney2023MinimiseVerifiedWarnings(OsmNetworkReaderSettings settings) {

    // none so far

  }

  /**
   * When applied to the 2023 Melbourne PBF it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void melbourneMinimiseVerifiedWarnings(OsmNetworkReaderSettings settings) {

    /* wrongly tagged in OSM as per 26/2/2020. Road has no access to motor vehicles, yet bus_stop exists */
    settings.overwriteModeAccessByOsmWayId(
            777286561L, OsmRoadModeTags.BUS, OsmRoadModeTags.FOOT, OsmRoadModeTags.BICYCLE);

  }

  /**
   * When applied to the 2023 Melbourne PBF it overrides the traffic signals whose tags read wrong, found when reviewing
   * junctions where only one approach has a signal against the official signal sites: signals that face the wrong way,
   * and a signal for trams only. No rule of the parser tells them apart from signals that are right, so they are
   * overridden here.
   *
   * @param intersectionSettings to apply to
   */
  public static void melbourneOverrideVerifiedTrafficSignals(OsmIntersectionSettings intersectionSettings) {

    /* Ingles Street: tagged backward, so it made Munro Street a signalised junction; its stop line faces forward */
    intersectionSettings.overrideTrafficSignalDirection(8986977483L, OsmTrafficSignalDirection.FORWARD);
    /* Cecil Street: tagged forward, so it made Dow Street a signalised junction; its stop line faces backward */
    intersectionSettings.overrideTrafficSignalDirection(9422335390L, OsmTrafficSignalDirection.BACKWARD);
    /* Freshwater Place: untagged, given to the nearest junction; it is a stop line of the Southbank Boulevard and
     * Riverside Quay junction, backward */
    intersectionSettings.overrideTrafficSignalDirection(8915393796L, OsmTrafficSignalDirection.BACKWARD);
    /* Canterbury Road at Mills Street: a plain vehicle signal that stops one carriageway only for trams crossing it
     * into the median, so it only switches when a tram passes. As a crossing signal it gets the crossing's higher green
     * ratio, rather than making Mills Street a signalised junction with Canterbury Road at 0.5 */
    intersectionSettings.overrideTrafficSignalKind(9422335232L, OsmTrafficSignalKind.CROSSING_SIGNAL);

  }

}

package org.goplanit.osm.test;

import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
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
    settings.overwriteModeAccessByOsmWayId(777286561L, OsmRoadModeTags.BUS, OsmRoadModeTags.FOOT, OsmRoadModeTags.BICYCLE);

  }

}

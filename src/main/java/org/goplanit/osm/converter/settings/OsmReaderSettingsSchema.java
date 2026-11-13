package org.goplanit.osm.converter.settings;

import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.utils.settings.SettingsSchema;

/**
 * The layout of the OSM reader settings sources: the country at the root, then the sections of the network settings
 * and of the public transport settings
 * <p>
 * For example, a source holding all of them:
 * </p>
 * <pre>
 * planitOsmConverterSettings: 1
 * country: Australia
 * network:
 *   osmWayLaneOverrides:
 *     60: {lanesForward: 2, lanesBackward: 2}
 * publicTransport:
 *   ...
 * </pre>
 * <p>
 * The header, the names of the sections and the country are on the settings classes: the header on
 * {@link OsmReaderSettings}, the sections on {@link OsmNetworkReaderSettings} and
 * {@link OsmPublicTransportReaderSettings}, and the country on the constructors that take it
 * ({@link OsmReaderSettings#COUNTRY_KEY}). One schema serves the network and the intermodal reader settings, so one
 * source serves both readers; the network reader settings take the network section only.
 * </p>
 *
 * @author markr
 */
public final class OsmReaderSettingsSchema {

  /** Not instantiable, everything is static */
  private OsmReaderSettingsSchema() {
  }

  /** the schema of every OSM reader settings source */
  public static final SettingsSchema SCHEMA =
      new SettingsSchema(OsmNetworkReaderSettings.class, OsmPublicTransportReaderSettings.class);
}

package org.goplanit.osm.converter.intermodal;

import org.goplanit.osm.converter.network.OsmNetworkReader;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.zoning.Zoning;

/**
 * The network reader of an intermodal read: an OSM network reader whose {@link #read()} only parses, leaving the
 * logging of the settings, the finalising of the parsed network and the logging of its diagnostics summary to
 * {@link OsmIntermodalReader}.
 * <p>
 * Finalising the network, which removes dangling subnetworks and then completes the intersections, needs the public
 * transport infrastructure parsed as well: removing a dangling subnetwork must also deal with the connectoids of the
 * stops on what it removes, and the intersections are completed once that has settled the network. Yet the public
 * transport infrastructure can only be parsed once the network is. So the intermodal reader parses the network with
 * this reader, parses the public transport infrastructure, and only then finalises the network, with that zoning,
 * through this class.
 * </p>
 * <p>
 * This class exists only to change when those steps run. It is not a network reader of a different kind: the settings
 * decide whether each step runs, as for any network reader. Subclassing keeps the steps off the network reader's
 * public API and leaves the settings as the user gave them, at the cost of a class whose only job is the order of the
 * calls; it is visible in this package only. A step added to {@link OsmNetworkReader#finaliseParsedNetwork(Zoning)}
 * needs no change here.
 * </p>
 *
 * @author markr
 */
final class OsmIntermodalNetworkReader extends OsmNetworkReader {

  /**
   * Constructor
   *
   * @param settings for populating the network
   * @param osmNetworkToPopulate network to populate
   */
  protected OsmIntermodalNetworkReader(OsmNetworkReaderSettings settings, PlanitOsmNetwork osmNetworkToPopulate) {
    super(settings, osmNetworkToPopulate);
  }

  /**
   * Leaves the settings out of {@link #read()}: the intermodal reader logs the settings of the whole read once
   */
  @Override
  protected void logSettingsAtStartOfRead() {
  }

  /**
   * Leaves finalising the network out of {@link #read()}: the intermodal reader finalises it once the public transport
   * infrastructure is parsed as well, see {@link #finaliseParsedNetwork(Zoning)}
   */
  @Override
  protected void finaliseAtEndOfRead() {
  }

  /**
   * Leaves the diagnostics summary out of {@link #read()}: the intermodal reader logs it once it has finalised the
   * network, see {@link #logDiagnosticsSummary()}
   */
  @Override
  protected void logDiagnosticsSummaryAtEndOfRead() {
  }

  /**
   * {@inheritDoc}
   * <p>
   * Declared here only so that {@link OsmIntermodalReader}, in this package, can call it: a protected method can be
   * called from anywhere in the package of the class declaring it. Removing it as redundant stops the intermodal reader
   * compiling.
   * </p>
   */
  @Override
  protected void finaliseParsedNetwork(Zoning zoning) {
    super.finaliseParsedNetwork(zoning);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Declared here only so that {@link OsmIntermodalReader}, in this package, can call it: a protected method can be
   * called from anywhere in the package of the class declaring it. Removing it as redundant stops the intermodal reader
   * compiling.
   * </p>
   */
  @Override
  protected void logDiagnosticsSummary() {
    super.logDiagnosticsSummary();
  }

}

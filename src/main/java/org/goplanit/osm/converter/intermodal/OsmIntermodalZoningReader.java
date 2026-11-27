package org.goplanit.osm.converter.intermodal;

import org.goplanit.osm.converter.network.data.OsmNetworkToZoningReaderData;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.converter.zoning.OsmZoningReader;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.zoning.Zoning;

/**
 * The zoning reader of an intermodal read: an OSM zoning reader whose {@link #read()} only parses the public transport
 * infrastructure, leaving the logging of the settings and the finalising of the parsed zoning to
 * {@link OsmIntermodalReader}.
 * <p>
 * Finalising the zoning removes dangling zones and transfer zone groups. In an intermodal read it must wait until the
 * network is finalised, since removing dangling subnetworks there takes away connectoids and can leave more zones
 * dangling. Yet the network can only be finalised once this zoning is parsed. So the intermodal reader parses the
 * zoning with this reader, finalises the network, and only then finalises the zoning through this class.
 * </p>
 * <p>
 * This class exists only to change when those steps run. It is not a zoning reader of a different kind: the settings
 * decide whether each step runs, as for any zoning reader. Subclassing keeps the steps off the zoning reader's public
 * API and leaves the settings as the user gave them, at the cost of a class whose only job is the order of the calls;
 * it is visible in this package only. A step added to {@link OsmZoningReader#finaliseParsedZoning()} needs no change
 * here.
 * </p>
 *
 * @author markr
 */
final class OsmIntermodalZoningReader extends OsmZoningReader {

  /**
   * Constructor
   *
   * @param settings to use
   * @param zoningToPopulate zoning to populate
   * @param referenceNetwork the network parsed before, which the zoning is built on
   * @param network2ZoningData the data relating the zoning to that network
   */
  protected OsmIntermodalZoningReader(
      OsmPublicTransportReaderSettings settings,
      Zoning zoningToPopulate,
      PlanitOsmNetwork referenceNetwork,
      OsmNetworkToZoningReaderData network2ZoningData) {
    super(settings, zoningToPopulate, referenceNetwork, network2ZoningData);
  }

  /**
   * Leaves the settings out of {@link #read()}: the intermodal reader logs the settings of the whole read once
   */
  @Override
  protected void logSettingsAtStartOfRead() {
  }

  /**
   * Leaves finalising the zoning out of {@link #read()}: the intermodal reader finalises it once it has finalised the
   * network, see {@link #finaliseParsedZoning()}
   */
  @Override
  protected void finaliseAtEndOfRead() {
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
  protected void finaliseParsedZoning() {
    super.finaliseParsedZoning();
  }

}

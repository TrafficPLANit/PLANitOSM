package org.goplanit.osm.converter.intermodal;

import java.net.URL;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.Logger;

import org.goplanit.converter.intermodal.IntermodalReader;
import org.goplanit.converter.zoning.ZoningConverterUtils;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.MacroscopicNetworkModifierUtils;
import org.goplanit.network.ServiceNetwork;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityDiagnostics;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityIssue;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.service.routed.RoutedServices;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Quadruple;
import org.goplanit.utils.zoning.TransferZone;
import org.goplanit.utils.zoning.connectoid.ZoneConnectoidType;
import org.goplanit.zoning.Zoning;
import org.goplanit.zoning.ZoningModifierUtils;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit intermodal network which includes the
 * transfer zones of a zoning instance. By default an intermodal reader will activate parsing transfer
 * infrastructure as well as the network infrastructure (including rail which for a "regular" network reader is
 * turned off by default, since we assume that more often than not, once desires to include rail when parsing
 * pt networks. One can manually change these defaults via the various settings made available.
 * <p>
 * The network and the public transport infrastructure are read from the same source by a network reader and a zoning
 * reader, but their last steps cannot run inside their own reads. Finalising the network removes dangling
 * subnetworks, which must also deal with the connectoids of the stops on what it removes, and then completes the
 * intersections once that has settled the network; finalising the zoning removes dangling zones, which must wait
 * until the network is finalised. Yet the zoning can only be parsed once the network is. So this reader has the
 * network parsed, then the zoning, and only then finalises the network, with the zoning, and then the zoning.
 * </p>
 * <p>
 * To do so it reads with {@link OsmIntermodalNetworkReader} and {@link OsmIntermodalZoningReader}, which leave those
 * steps out of their reads and offer them to this reader alone. They exist only to change when the steps run: the
 * settings still decide whether each runs, and this reader never changes the settings. A read is refused, with a
 * warning and no result, when the settings cannot be read with as given, such as with the public transport parser
 * switched off, which is what the network reader alone is for.
 * </p>
 *
 * @author markr
 *
 */
public class OsmIntermodalReader implements IntermodalReader<ServiceNetwork, RoutedServices> {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmIntermodalReader.class.getCanonicalName());
  
  /** the settings to use */
  private final OsmIntermodalReaderSettings settings;
  
  /** the zoning to populate if any */
  private final Zoning zoningToPopulate;

  /** the network to populate */
  private final PlanitOsmNetwork osmNetworkToPopulate;

  /** the diagnostics on the PLANit entities of the last read, null before the first read */
  private OsmPlanitEntityDiagnostics planitEntityDiagnostics = null;

  /** Check that the settings can be read with, changing none of them: the network and public transport settings name
   * the same country and the same input source, at least one network type is activated, and the public transport
   * parser is activated. The problem found is logged
   *
   * @return true when valid, false otherwise
   */
  private boolean isSettingsValid() {
    OsmNetworkReaderSettings networkSettings = getSettings().getNetworkSettings();
    OsmPublicTransportReaderSettings ptSettings = getSettings().getPublicTransportSettings();
    
    /* both source countries must be the same */
    if( networkSettings.getCountryName() == null ||
        !networkSettings.getCountryName().equals(ptSettings.getCountryName())){
        LOGGER.severe(String.format(
            "OSM intermodal reader requires both the network and zoning (pt) to utilise the same source country " +
                "upon parsing, found %s and %s respectively instead",
            networkSettings.getCountryName(), ptSettings.getCountryName()));
      return false;
    }
    
    /* both input files must be the same */
    if(networkSettings.getInputSource() == null ||
        !networkSettings.getInputSource().equals(ptSettings.getInputSource())) {
      LOGGER.warning(
          String.format("OSM intermodal reader requires both the network and zoning (pt) to utilise the same " +
              "OSM input file upon parsing, found %s and %s respectively instead",
              networkSettings.getInputSource(), ptSettings.getInputSource()));
      return false;
    }

    if(!(networkSettings.isHighwayParserActive() || networkSettings.isRailwayParserActive() ||
        networkSettings.isWaterwayParserActive())){
      LOGGER.warning("Not a single type of network is activated nor road, rail, or water");
      return false;
    }

    if(!ptSettings.isParserActive()) {
      LOGGER.warning("OSM intermodal reader parses public transport infrastructure, but its parser is not activated; " +
          "use the OSM network reader to parse a network alone");
      return false;
    }

    return true;
       
  }

  /**
   * Record, with the PLANit entity diagnostics, each transfer zone that reaches the network only in part once the read
   * has finished, by its external id: travellers can reach it but no vehicle stops there, or vehicles stop there but
   * travellers can no longer reach it. A zone left without any entry is not recorded, since it is removed as dangling,
   * nor is a stop that had no access or egress when the zoning was read, since giving stops access and egress already
   * reports each stop it cannot connect
   *
   * @param zoning read
   * @param stopsWithoutAccessOrEgressWhenZoningRead the transfer zones with a stop but no access or egress when the
   *                                                 zoning was read, by identity, since ids may be renumbered since
   */
  private void registerTransferZonesReachingNetworkInPart(
      Zoning zoning, Set<TransferZone> stopsWithoutAccessOrEgressWhenZoningRead) {
    var connectoidsByTransferZone = zoning.getTransferConnectoids().createIndexByAccessZone();
    ZoningConverterUtils.findTransferZonesWithAnyButNoneOfEntryTypes(
        connectoidsByTransferZone, ZoneConnectoidType.ACCESS_OR_EGRESS_TYPES, Set.of(ZoneConnectoidType.PT_VEHICLE_STOP))
        .forEach(zone -> planitEntityDiagnostics.registerIssue(
            OsmPlanitEntityIssue.TRANSFER_ZONE_ACCESS_EGRESS_WITHOUT_STOP, zone.getExternalId()));
    ZoningConverterUtils.findTransferZonesWithAnyButNoneOfEntryTypes(
        connectoidsByTransferZone, Set.of(ZoneConnectoidType.PT_VEHICLE_STOP), ZoneConnectoidType.ACCESS_OR_EGRESS_TYPES)
        .stream().filter(zone -> !stopsWithoutAccessOrEgressWhenZoningRead.contains(zone))
        .forEach(zone -> planitEntityDiagnostics.registerIssue(
            OsmPlanitEntityIssue.TRANSFER_ZONE_STOP_WITHOUT_ACCESS_EGRESS, zone.getExternalId()));
  }

  /**
   * Finalise the parsed network, with the zoning, then the parsed zoning, each as its settings ask, and sync the ids of
   * each once finalised. The network goes first, since removing its dangling subnetworks takes away the connectoids on
   * what it removes, which can leave zones dangling
   *
   * @param osmNetworkReader that parsed the network
   * @param network          parsed
   * @param osmZoningReader  that parsed the zoning
   * @param zoning           parsed
   */
  private void finaliseNetworkThenZoning(OsmIntermodalNetworkReader osmNetworkReader, PlanitOsmNetwork network,
      OsmIntermodalZoningReader osmZoningReader, Zoning zoning) {
    osmNetworkReader.finaliseParsedNetwork(zoning);
    MacroscopicNetworkModifierUtils.updateAndSyncManagedIdEntitiesContainerXmlIdsToIds(network);
    osmZoningReader.finaliseParsedZoning();
    ZoningModifierUtils.updateAndSyncManagedIdEntitiesContainerXmlIdsToIds(zoning);
  }

  /**
   * Constructor 
   * 
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   */
  protected OsmIntermodalReader(
      final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new OsmIntermodalReaderSettings(countryName), osmNetworkToPopulate, zoningToPopulate);
  }   
  
  /**
   * Constructor 
   * 
   * @param inputSource to use for all intermodal parsing
   * @param countryName to use for parsing the geometries in desired projection
   * @param osmNetworkToPopulate to populate
   * @param zoningToPopulate to populate
   */
  protected OsmIntermodalReader(
      final URL inputSource, final String countryName, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate) {
    this(new OsmIntermodalReaderSettings(inputSource, countryName), osmNetworkToPopulate, zoningToPopulate);
  }     

  /**
   * Constructor 
   * 
   * @param settings to use
   * @param zoningToPopulate to populate
   * @param osmNetworkToPopulate to populate
   */
  protected OsmIntermodalReader(
      OsmIntermodalReaderSettings settings, PlanitOsmNetwork osmNetworkToPopulate, Zoning zoningToPopulate){
    this.settings = settings;
    this.zoningToPopulate = zoningToPopulate;
    this.osmNetworkToPopulate = osmNetworkToPopulate;
  }
  
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network and zoning
   * given the configuration options that have been set
   * 
   * @return network and zoning that has been parsed, or null if detected problem has occurred and logged
   */  
  @Override
  public Pair<MacroscopicNetwork, Zoning> read() {
    
    /* only proceed when configuration is valid */
    if(!isSettingsValid()) {
      return null;
    }

    /* the configuration of the read as a whole, logged once: the network and zoning readers leave out their own */
    getSettings().logSettings();

    /* NETWORK READER, parsing only, finalised once the zoning is parsed as well */
    var osmNetworkReader = new OsmIntermodalNetworkReader(
        getSettings().getNetworkSettings(), new PlanitOsmNetwork(IdGroupingToken.collectGlobalToken()));
    PlanitOsmNetwork network = (PlanitOsmNetwork) osmNetworkReader.read();
    planitEntityDiagnostics = osmNetworkReader.getPlanitEntityDiagnostics();
    /* the zoning is made before the network is read, so it follows the network's layers once they exist */
    zoningToPopulate.attachTo(network);

    //TODO: ugly, should be done in a less ugly way
    /* ensure crs are compatible */
    zoningToPopulate.setCoordinateReferenceSystem(network.getCoordinateReferenceSystem());

    /* ZONING READER, parsing only, finalised once the network is */
    OsmPublicTransportReaderSettings ptSettings = getSettings().getPublicTransportSettings();
    var n2zData = osmNetworkReader.createNetworkToZoningReaderData();
    var osmZoningReader = new OsmIntermodalZoningReader(ptSettings, zoningToPopulate, network, n2zData);
    Zoning zoning = osmZoningReader.read();
    /* the stops without access or egress once the zoning is read, which giving stops access and egress reports itself;
     * by identity, since the zoning's ids may be renumbered before they are compared */
    Set<TransferZone> stopsWithoutAccessOrEgressWhenZoningRead = Collections.newSetFromMap(new IdentityHashMap<>());
    stopsWithoutAccessOrEgressWhenZoningRead.addAll(ZoningConverterUtils.findTransferZonesWithAnyButNoneOfEntryTypes(
        zoning.getTransferConnectoids().createIndexByAccessZone(), Set.of(ZoneConnectoidType.PT_VEHICLE_STOP),
        ZoneConnectoidType.ACCESS_OR_EGRESS_TYPES));

    finaliseNetworkThenZoning(osmNetworkReader, network, osmZoningReader, zoning);
    registerTransferZonesReachingNetworkInPart(zoning, stopsWithoutAccessOrEgressWhenZoningRead);
    /* once finalising and the checks on transfer zones have run */
    osmNetworkReader.logDiagnosticsSummary();

    /* return result */
    return Pair.of(network, zoning);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
  }      
  
  /**
   * {@inheritDoc}
   */
  @Override
  public OsmIntermodalReaderSettings getSettings() {
    return settings;
  }

  /**
   * The diagnostics on the PLANit entities of the last read, such as the connectoids pruning removed
   *
   * @return the diagnostics, null before the first read
   */
  public OsmPlanitEntityDiagnostics getPlanitEntityDiagnostics() {
    return planitEntityDiagnostics;
  }

  /**
   * Currently no support for this yet on the OSM side. To be implemented in the future. For now services
   * are to be sourced from GTFS and spliced into the OSM network
   *
   * @return false
   */
  @Override
  public boolean supportServiceConversion() {
    return false;
  }

  /**
   * Currently no support yet for this feature
   *
   * @return created network, zoning, service network and services
   */
  @Override
  public Quadruple<MacroscopicNetwork, Zoning, ServiceNetwork, RoutedServices> readWithServices() {

    /* only proceed when configuration is valid */
    if(!isSettingsValid()) {
      return null;
    }

    throw new PlanItRunTimeException("Support for service reader as part of Intermodal reader not yet supported" +
        " in OSMIntermodalReader");
  }
}

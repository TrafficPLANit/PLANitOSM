package org.goplanit.osm.defaults;

import org.goplanit.osm.tags.OsmWaterwayTags;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration options regarding the activation/deactivation of specific OSM waterway route types in the parser.
 * Note that we use two different keys for this, which is only possible because all values across both keys are unique
 * and the key itself is not used. If this chanegs this approach needs to be revisited.
 * 
 * @author markr
 *
 */
public class OsmWaterwayTypeConfiguration implements OsmInfrastructureConfiguration {

  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmWaterwayTypeConfiguration.class.getCanonicalName());

  /** default configuration for 'route=' configuration pertaining to ferries */
  protected static OsmInfrastructureConfiguration DEFAULT_ROUTEFERRY_CONFIGURATION = null;

  /** default configuration for 'ferry=_some_highway_type_' configuration pertaining to ferries */
  protected static OsmInfrastructureConfiguration DEFAULT_FERRYHIGHWAY_CONFIGURATION = null;

  protected OsmInfrastructureConfiguration routeFerryConfiguration = null;

  /** configuration for 'ferry=_some_highway_type_' configuration pertaining to ferries */
  protected OsmInfrastructureConfiguration ferryHighwayConfiguration = null;

  /**
   * conduct general initialisation for any instance of this class
   *
   * @param planitModes to populate based on (default) mapping
   */
  static {
    try {
      /* the waterway types that will be parsed by default, i.e., supported based on route=ferry key. */
      {
        final var defaults = new HashSet<String>();
        defaults.add(OsmWaterwayTags.FERRY);
        DEFAULT_ROUTEFERRY_CONFIGURATION = new OsmInfrastructureConfigurationImpl(
            OsmWaterwayTags.ROUTE, defaults, new HashSet<>());
      }

      /* the waterway types that will be parsed by default, i.e., supported based on ferry=_highway_type_ key. */
      {
        final var defaultSupported = new HashSet<String>();
        OsmWaterwayTags.getAllSupportedHighwayTypesAsWaterWayTypes().forEach( highwayType ->
            defaultSupported.add(highwayType));
        final var defaultUnSupported = new HashSet<String>();
        OsmWaterwayTags.getAllUnsupportedHighwayTypesAsWaterWayTypes().forEach( highwayType ->
            defaultUnSupported.add(highwayType));
        DEFAULT_FERRYHIGHWAY_CONFIGURATION = new OsmInfrastructureConfigurationImpl(
            OsmWaterwayTags.FERRY, defaultSupported, defaultUnSupported);
      }

    } catch (Exception e) {
      LOGGER.severe("Unable to create default supported and/or unsupported OSM water way types for this network");
    }
  }

  /**
   * copy constructor
   *
   * @param toCopy the one to copy
   */
  protected OsmWaterwayTypeConfiguration(OsmWaterwayTypeConfiguration toCopy){
    routeFerryConfiguration = toCopy.routeFerryConfiguration.deepClone();
    ferryHighwayConfiguration = toCopy.ferryHighwayConfiguration.deepClone();
  }

  /**
   * Default constructor
   */
  public OsmWaterwayTypeConfiguration(){
    routeFerryConfiguration = DEFAULT_ROUTEFERRY_CONFIGURATION.deepClone();
    ferryHighwayConfiguration = DEFAULT_FERRYHIGHWAY_CONFIGURATION.deepClone();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logDeactivatedTypes() {
    routeFerryConfiguration.logDeactivatedTypes();
    ferryHighwayConfiguration.logDeactivatedTypes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDeactivated(String osmValue) {
    return routeFerryConfiguration.isDeactivated(osmValue) || ferryHighwayConfiguration.isDeactivated(osmValue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isActivated(String osmValue) {
    return routeFerryConfiguration.isActivated(osmValue) || ferryHighwayConfiguration.isActivated(osmValue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deactivate(String osmValue) {
    if(routeFerryConfiguration.isActivated(osmValue)){
      routeFerryConfiguration.deactivate(osmValue);
    }
    if(ferryHighwayConfiguration.isActivated(osmValue)){
      ferryHighwayConfiguration.deactivate(osmValue);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate(String... osmWayTypeValues) {
    routeFerryConfiguration.activate(osmWayTypeValues);
    ferryHighwayConfiguration.activate(osmWayTypeValues);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate(List<String> osmWayTypeValues) {
    routeFerryConfiguration.activate(osmWayTypeValues);
    ferryHighwayConfiguration.activate(osmWayTypeValues);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String,Set<String>> getActivatedTypes() {
    var result = new HashMap<String, Set<String>>();
    result.putAll(routeFerryConfiguration.getActivatedTypes());
    result.putAll(ferryHighwayConfiguration.getActivatedTypes());
    return Collections.unmodifiableMap(result);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String,Set<String>> getDeactivatedTypes() {
    var result = new TreeMap<String, Set<String>>();
    result.putAll(routeFerryConfiguration.getDeactivatedTypes());
    result.putAll(ferryHighwayConfiguration.getDeactivatedTypes());
    return Collections.unmodifiableMap(result);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deactivateAll() {
    routeFerryConfiguration.deactivateAll();
    ferryHighwayConfiguration.deactivateAll();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activateAll() {
    routeFerryConfiguration.activateAll();
    ferryHighwayConfiguration.activateAll();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OsmInfrastructureConfiguration deepClone() {
    return new OsmWaterwayTypeConfiguration(this);
  }
}

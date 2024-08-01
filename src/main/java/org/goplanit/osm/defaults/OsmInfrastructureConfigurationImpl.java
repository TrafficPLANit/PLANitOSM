package org.goplanit.osm.defaults;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Base class with common functionality for configuration OSM ways (highway/rail/waterway)
 * 
 * @author markr
 *
 */
public class OsmInfrastructureConfigurationImpl implements OsmInfrastructureConfiguration {

  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmInfrastructureConfiguration.class.getCanonicalName());
    
  /**
   * the OSM types that are marked as supported OSM types, i.e., will be processed when parsing
   */
  protected final Set<String> activatedOsmTypes;
  
  /**
   * the OSM types that are marked as deactivate OSM types, i.e., will be ignored when parsing unless activated. This differs
   * from unactivatableOsmTypes because deactivatesOsmTypes can in fact be activated whereas unactivatableOsmTypes cannot
   */
  protected final Set<String> deactivatedOsmTypes;
   
  /** way key this configuration applies to */
  protected final String osmKey;
  
  /**
   * Log all de-activated OSM way types
   */  
  @Override
  public void logDeactivatedTypes() {
    deactivatedOsmTypes.forEach( 
        osmTag -> LOGGER.info(String.format("[DEACTIVATED] %s=%s", osmKey, osmTag)));
  }  
    
  /**
   * Default constructor
   * 
   * @param osmWayKey to apply
   */
  protected OsmInfrastructureConfigurationImpl(String osmWayKey) {
    this.osmKey = osmWayKey;
    this.activatedOsmTypes = new HashSet<>();
    this.deactivatedOsmTypes = new HashSet<>();
  }
  
  /** Construct with defaults being populated 
   * 
   * @param osmKey to apply
   * @param activatedOsmTypes to use
   * @param deactivatedOsmTypes to use
   */
  public OsmInfrastructureConfigurationImpl(String osmKey, Set<String> activatedOsmTypes, Set<String> deactivatedOsmTypes) {
    this.osmKey = osmKey;
    this.activatedOsmTypes = new HashSet<>(activatedOsmTypes);
    this.deactivatedOsmTypes = new HashSet<>(deactivatedOsmTypes);
  }

  /** Copy Constructor (deep copy)
   *
   * @param toCopy to copy
   */
  protected OsmInfrastructureConfigurationImpl(OsmInfrastructureConfigurationImpl toCopy) {
    //string is unmodifiable so b definition deep copy when replacing containiners
    this.osmKey = toCopy.osmKey;
    this.activatedOsmTypes = new HashSet<>(toCopy.activatedOsmTypes);
    this.deactivatedOsmTypes = new HashSet<>(toCopy.deactivatedOsmTypes);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDeactivated(String osmValue) {
    return deactivatedOsmTypes.contains(osmValue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isActivated(String osmValue) {
    return activatedOsmTypes.contains(osmValue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deactivate(String osmValue) {
    boolean removedFromActive = activatedOsmTypes.remove(osmValue);
    if(!removedFromActive) {
      return ;
    }
    deactivatedOsmTypes.add(osmValue);
    LOGGER.fine(String.format("Deactivating OSM type %s=%s", osmKey, osmValue));
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate(String... osmWayTypeValues) {
    activate(Arrays.asList(osmWayTypeValues));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate(List<String> osmWayTypeValues) {
    for(String osmWayTypeValue : osmWayTypeValues) {
      deactivatedOsmTypes.remove(osmWayTypeValue);
      boolean added = activatedOsmTypes.add(osmWayTypeValue);
      if(added) {
        LOGGER.fine(String.format("Activating OSM type %s=%s", osmKey, osmWayTypeValue));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SortedMap<String,SortedSet<String>> getActivatedTypes() {
    var map = new TreeMap<String,SortedSet<String>>();
    map.put(osmKey, new TreeSet<>(activatedOsmTypes));
    return map;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SortedMap<String,SortedSet<String>> getDeactivatedTypes() {
    var map = new TreeMap<String,SortedSet<String>>();
    map.put(osmKey, new TreeSet<>(deactivatedOsmTypes));
    return map;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deactivateAll() {
    getActivatedTypes().values().stream().flatMap(Collection::stream).forEach(this::deactivate);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activateAll() {
    getDeactivatedTypes().values().stream().flatMap(e -> e.stream()).forEach(deactivatedType -> activate(deactivatedType));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public OsmInfrastructureConfigurationImpl deepClone() {
    return new OsmInfrastructureConfigurationImpl(this);
  }
}

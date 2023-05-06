package org.goplanit.osm.defaults;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Base class with common functionality for configuration OSM ways (highway/rail)
 * 
 * @author markr
 *
 */
public class OsmInfrastructureConfiguration {

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
  public void logDeactivatedTypes() {
    deactivatedOsmTypes.forEach( 
        osmTag -> LOGGER.info(String.format("[DEACTIVATED] %s=%s", osmKey, osmTag)));
  }  
    
  /**
   * Default constructor
   * 
   * @param osmWayKey to apply
   */
  protected OsmInfrastructureConfiguration(String osmWayKey) {
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
  public OsmInfrastructureConfiguration(String osmKey, Set<String> activatedOsmTypes, Set<String> deactivatedOsmTypes) {
    this.osmKey = osmKey;
    this.activatedOsmTypes = new HashSet<>(activatedOsmTypes);
    this.deactivatedOsmTypes = new HashSet<>(deactivatedOsmTypes);
  }


  /**
   * Verify if the passed in OSM way type is explicitly unsupported. Unsupported types will be ignored
   * when processing ways.
   * 
   * @param osmValue, e.g. primary, road
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isDeactivated(String osmValue) {
    return deactivatedOsmTypes.contains(osmValue);
  }
  
  /**
   * Verify if the passed in OSM way type is explicitly supported. Supported types will be processed 
   * and converted into link(segments).
   * 
   * @param osmValue, e.g. primary
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isActivated(String osmValue) {
    return activatedOsmTypes.contains(osmValue);
  }  
  
  /**
   * Choose to not parse the given combination of way subtype, e.g. highway=road
   * 
   * @param osmValue to use
   */
  public void deactivate(String osmValue) {
    boolean removedFromActive = activatedOsmTypes.remove(osmValue);
    if(!removedFromActive) {
      LOGGER.warning(String.format("unable to deactivate OSM type %s=%s, because it is not currently activated", osmKey, osmValue));
      return;
    }
    deactivatedOsmTypes.add(osmValue);
    LOGGER.fine(String.format("deactivating OSM type %s=%s", osmKey, osmValue));
  }
  
  /**
   * Choose to add a given way value to be on top of the defaults, e.g. highway=road
   * 
   * @param osmWayValues to activate
   */
  public void activate(String... osmWayValues) {
    activate(Arrays.asList(osmWayValues));
  }   
    
  /**
   * Choose to add a given way value to be on top of the defaults, e.g. highway=road
   * 
   * @param osmWayValues to activate
   */
  public void activate(List<String> osmWayValues) {
    for(String osmWayValue : osmWayValues) {
      deactivatedOsmTypes.remove(osmWayValue);
      boolean added = activatedOsmTypes.add(osmWayValue);
      if(!added) {
        LOGGER.warning(String.format("OSM type %s=%s is already active, no need to activate again, ignored", osmKey, osmWayValue));
      }
      LOGGER.fine(String.format("activating OSM type %s=%s", osmKey, osmWayValue));
    }
  }  
  
  /** create a copy of the currently supported way types in set form
   * 
   * @return set of supported highway types, any modifications on this set have no impact on the instance internals
   */
  public Set<String> setOfActivatedTypes() {
    return new HashSet<String>(activatedOsmTypes);
  }   
  
  /** create a copy of the currently unsupported way types in set form
   * 
   * @return set of supported highway types, any modifications on this set have no impact on the instance internals
   */
  public Set<String> setOfDeactivatedTypes() {
    return new HashSet<String>(deactivatedOsmTypes);
  }    
  
  /**
   * deactivate all types explicitly
   */
  public void deactivateAll() {
    setOfActivatedTypes().forEach( activatedType -> deactivate(activatedType));
  }  
  
  /**
   * activate all types explicitly
   */
  public void activateAll() {
    setOfDeactivatedTypes().forEach( deactivatedType -> activate(deactivatedType));
  }   
    
}

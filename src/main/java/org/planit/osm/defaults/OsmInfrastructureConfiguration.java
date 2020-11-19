package org.planit.osm.defaults;

import java.util.HashSet;
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
  protected final String osmWayKey;
  
  /**
   * Log all de-activated OSM way types
   */  
  public void logDeactivatedTypes() {
    deactivatedOsmTypes.forEach( 
        osmTag -> LOGGER.info(String.format("[DEACTIVATED] %s=%s", osmWayKey, osmTag)));
  }  
    
  /**
   * default constructor
   * 
   * @param osmWayKey to apply
   */
  protected OsmInfrastructureConfiguration(String osmWayKey) {
    this.osmWayKey = osmWayKey;
    this.activatedOsmTypes = new HashSet<String>();
    this.deactivatedOsmTypes = new HashSet<String>();
  }
  
  /** construct with defaults being populated 
   * 
   * @param osmWayKey to apply
   * @param activatedOsmTypes to use
   * @param deactivatedOsmTypes to use
   * @param unactivatableOsmTypes to use
   */
  public OsmInfrastructureConfiguration(String osmWayKey, Set<String> activatedOsmTypes, Set<String> deactivatedOsmTypes) {
    this.osmWayKey = osmWayKey;
    this.activatedOsmTypes = new HashSet<String>(activatedOsmTypes);
    this.deactivatedOsmTypes = new HashSet<String>(deactivatedOsmTypes);
  }

  /**
   * Verify if the passed in OSM way type is explicitly unsupported. Unsupported types will be ignored
   * when processing ways.
   * 
   * @param osmWayValue, e.g. primary, road
   * @return true when unSupported, false if not (which means it is either supported, or not registered)
   */
  public boolean isDeactivated(String osmWayValue) {
    return deactivatedOsmTypes.contains(osmWayValue);
  }
  
  /**
   * Verify if the passed in OSM way type is explicitly supported. Supported types will be processed 
   * and converted into link(segments).
   * 
   * @param osmWayValue, e.g. primary
   * @return true when supported, false if not (which means it is unsupported, or not registered)
   */
  public boolean isActivated(String osmWayValue) {
    return activatedOsmTypes.contains(osmWayValue);
  }  
  
  /**
   * Choose to not parse the given combination of way subtype, e.g. highway=road
   * 
   * @param osmWayValue to use
   */
  public void deactivate(String osmWayValue) {
    boolean removedFromActive = activatedOsmTypes.remove(osmWayValue);
    if(!removedFromActive) {
      LOGGER.warning(String.format("unable to deactivate OSM type %s=%s, because it is not currently activated", osmWayKey, osmWayValue));
      return;
    }
    deactivatedOsmTypes.add(osmWayValue);
    LOGGER.fine(String.format("deactivating OSM type %s=%s", osmWayKey, osmWayValue));
  }
    
  /**
   * Choose to add a given way value to be on top of the defaults, e.g. highway=road
   * 
   * @param osmWayValues to activate
   */
  public void activate(String... osmWayValues) {
    for(int index=0;index<osmWayValues.length;++index) {
      String osmWayValue = osmWayValues[index];
      boolean removed = deactivatedOsmTypes.remove(osmWayValue);
      if(!removed) {
        LOGGER.warning(String.format("unable to activate OSM type %s=%s, either it is already active, or it is not yet supported by the parser",osmWayKey, osmWayValue));  
      }
      activatedOsmTypes.add(osmWayValue);      
      LOGGER.fine(String.format("activating OSM type %s=%s",osmWayKey, osmWayValue));
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

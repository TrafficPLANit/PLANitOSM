package org.planit.osm.converter.zoning;

import java.util.Collection;
import java.util.Map;

import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.util.PlanitOsmModeUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;

/**
 * class that provides functionality to parse/filter public transport modes from OSM entities based
 * on the user defined settings
 * 
 * @author markr
 *
 */
public class PlanitOsmPublicTransportModeParser {
  
  /** settings to use */
  private final PlanitOsmNetworkReaderSettings settings;

  /**Constructor
   *  
   * @param settings to use
   */
  public PlanitOsmPublicTransportModeParser(PlanitOsmNetworkReaderSettings settings) {
    this.settings = settings;
  }

  /** collect the pt modes both OSM and mapped PLANit modes for a pt entity. If no default mode is found based on the tags, a default mode may be 
   * provided explicitly by the user which will then be added to the OSM mode
   * 
   * @param osmPtEntityId to use
   * @param tags of the OSM entity
   * @param defaultMode to use
   * @return pair containing eligible OSM modes identified and their mapped PLANit counterparts
   */
  public Pair<Collection<String>, Collection<Mode>> collectPublicTransportModesFromPtEntity(long osmPtEntityId, Map<String, String> tags, String defaultMode) {    
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmPublicTransportModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = settings.getMappedPlanitModes(eligibleOsmModes);      
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
  
  /** collect the eligible modes both OSM and mapped PLANit modes for the given OSM entity representing pt infrastructure. If no default mode is found based on the tags, a default mode may be 
   * provided explicitly by the user which will then be added to the OSM mode
   * 
   * @param osmPtEntityId to use
   * @param tags of the OSM entity
   * @param defaultMode to use
   * @return pair containing eligible OSM modes identified and their mapped planit counterparts
   */
  public Pair<Collection<String>, Collection<Mode>> collectModesFromPtEntity(long osmPtEntityId, Map<String, String> tags, String defaultMode) {
    Collection<String> eligibleOsmModes = PlanitOsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = settings.getMappedPlanitModes(eligibleOsmModes);      
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
}

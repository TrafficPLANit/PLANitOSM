package org.goplanit.osm.converter.zoning.handler.helper;

import java.util.Collection;
import java.util.Map;

import org.goplanit.osm.converter.helper.OsmModeHelper;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.util.OsmModeUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;

/**
 * class that provides functionality to parse/filter public transport modes from OSM entities based
 * on the user defined settings
 * 
 * @author markr
 *
 */
public class OsmPublicTransportModeHelper extends OsmModeHelper {
  
  /**Constructor
   *  
   * @param settings to use
   */
  public OsmPublicTransportModeHelper(OsmNetworkReaderSettings settings) {
    super(settings);
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
    Collection<String> eligibleOsmModes = OsmModeUtils.collectEligibleOsmPublicTransportModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = getSettings().getMappedPlanitModes(eligibleOsmModes);      
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
    Collection<String> eligibleOsmModes = OsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<Mode> eligiblePlanitModes = getSettings().getMappedPlanitModes(eligibleOsmModes);      
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
}

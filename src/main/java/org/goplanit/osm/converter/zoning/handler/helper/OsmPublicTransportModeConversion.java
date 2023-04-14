package org.goplanit.osm.converter.zoning.handler.helper;

import java.util.*;

import org.goplanit.osm.converter.OsmModeConversionBase;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.util.OsmModeUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;

/**
 * class that provides functionality to parse/filter public transport modes from OSM entities based
 * on the user defined settings
 * 
 * @author markr
 *
 */
public class OsmPublicTransportModeConversion extends OsmModeConversionBase {
  
  /**Constructor
   *
   * @param settings to use
   * @param layerModes in use across the network we are populating
   */
  public OsmPublicTransportModeConversion(OsmNetworkReaderSettings settings, Iterable<Mode> layerModes) {
    super(settings, layerModes);
  }

  /** collect the pt modes both OSM and mapped PLANit modes for a pt entity. If no default mode is found based on the tags, a default mode may be 
   * provided explicitly by the user which will then be added to the OSM mode
   * 
   * @param osmPtEntityId to use
   * @param tags of the OSM entity
   * @param defaultMode to use
   * @return pair containing ordered eligible OSM modes identified and their mapped PLANit counterparts
   */
  public Pair<SortedSet<String>, Collection<PredefinedModeType>> collectPublicTransportModesFromPtEntity(long osmPtEntityId, Map<String, String> tags, String defaultMode) {
    SortedSet<String> eligibleOsmModes = OsmModeUtils.collectEligibleOsmPublicTransportModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Set<PredefinedModeType> eligiblePlanitModes = getSettings().getActivatedPlanitModeTypes(eligibleOsmModes);
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
  public Pair<Collection<String>, Collection<PredefinedModeType>> collectModesFromPtEntity(long osmPtEntityId, Map<String, String> tags, String defaultMode) {
    Collection<String> eligibleOsmModes = OsmModeUtils.collectEligibleOsmModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    
    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }    
    Collection<PredefinedModeType> eligiblePlanitModes = getSettings().getActivatedPlanitModeTypes(eligibleOsmModes);
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }  
}

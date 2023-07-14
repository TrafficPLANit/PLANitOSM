package org.goplanit.osm.converter.zoning.handler.helper;

import java.util.*;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.converter.OsmModeConversionBase;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.util.Osm4JUtils;
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

  private final  OsmPublicTransportReaderSettings ptSettings;
  
  /**Constructor
   *
   * @param networkSettings to use
   * @param ptSettings to use
   * @param layerModes in use across the network we are populating
   */
  public OsmPublicTransportModeConversion(
      OsmNetworkReaderSettings networkSettings, OsmPublicTransportReaderSettings ptSettings, Iterable<Mode> layerModes) {
    super(networkSettings, layerModes);
    this.ptSettings = ptSettings;
  }

  /** collect the pt modes both OSM and mapped PLANit modes for a pt entity. If no default mode is found based on the tags, a default mode may be
   * provided explicitly by the user which will then be added to the OSM mode
   * 
   * @param osmPtEntityId to use
   * @param osmEntityType of the id at hand
   * @param tags of the OSM entity
   * @param defaultMode to use
   * @return pair containing ordered eligible OSM modes identified and their mapped PLANit counterparts
   */
  public Pair<SortedSet<String>, SortedSet<PredefinedModeType>> collectPublicTransportModesFromPtEntity(
      long osmPtEntityId, EntityType osmEntityType, Map<String, String> tags, String defaultMode) {

    SortedSet<String> eligibleOsmModes;
    if(ptSettings.isOverwriteWaitingAreaModeAccess(osmPtEntityId, osmEntityType)){
      eligibleOsmModes = ptSettings.getOverwrittemWaitingAreaModeAccess(osmPtEntityId, osmEntityType);
    }else {
      eligibleOsmModes = OsmModeUtils.collectEligibleOsmPublicTransportModesOnPtOsmEntity(osmPtEntityId, tags, defaultMode);
    }

    if(eligibleOsmModes==null || eligibleOsmModes.isEmpty()) {
      return null;
    }

    SortedSet<PredefinedModeType> eligiblePlanitModes = getSettings().getActivatedPlanitModeTypes(eligibleOsmModes);
    return Pair.of(eligibleOsmModes, eligiblePlanitModes);
  }

  public Pair<SortedSet<String>, SortedSet<PredefinedModeType>> collectPublicTransportModesFromPtEntity(
      OsmEntity osmEntity, Map<String, String> tags, String defaultMode) {
    return collectPublicTransportModesFromPtEntity(osmEntity.getId(), Osm4JUtils.getEntityType(osmEntity), tags, defaultMode);
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

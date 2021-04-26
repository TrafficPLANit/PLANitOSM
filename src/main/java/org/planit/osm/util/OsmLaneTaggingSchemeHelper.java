package org.planit.osm.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.utils.mode.Mode;

import java.util.Set;
import java.util.Map.Entry;

/**
 * The tagging scheme helper is the base class for different lane based tagging schemes. Currently there exist two different type of mode specific lane based tagging schemes
 * namely a ModeLaneBased one and a LaneModeBased one. 
 * A good example and comparison of different schemes can be found for buses via https://wiki.openstreetmap.org/wiki/Bus_lanes
 * <p>
 * 
 * @author markr
 *
 */
public class OsmLaneTaggingSchemeHelper{
  
  /** track the modes that we support as part of public service vehicle (psv) lanes, currently only bus */
  protected static final Set<String> publicServiceVehicleModes = new HashSet<String>();
  
  static {
    publicServiceVehicleModes.add(OsmRoadModeTags.BUS);
  }
  
  /** track the osm modes that we are considering for the lanes:mode:*=* scheme */
  protected final Set<String> eligibleOsmModes;
  
  /** track the public service vehicle modes that are eligible for this instance */
  protected final Set<String> eligiblePublicServiceVehicleModes;  
  
  /** Verify if any modes that can be identified via a currently derived tagging scheme are activated via the settings making it worthwhile to utilise the (derived) tagging scheme. 
   * Currently we only consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * @param settings containing the activated and mapped Osm to PLANit modes 
   * @param networkLayer to identify subset of modes relevant for the layer at hand
   * @return yes, when these modes are activated, false otherwise
   */
  protected static boolean requireTaggingSchemeHelper(PlanitOsmNetworkReaderSettings settings, MacroscopicPhysicalNetwork networkLayer) {    
    if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BUS, OsmRoadModeTags.BICYCLE, OsmRoadModeTags.HEAVY_GOODS)) {
      List<String> modesRequiringTaggingScheme = Arrays.asList(OsmRoadModeTags.BUS, OsmRoadModeTags.BICYCLE, OsmRoadModeTags.HEAVY_GOODS);
      for(String osmMode : modesRequiringTaggingScheme) {
        Mode planitMode = settings.getMappedPlanitMode(osmMode);
        if(planitMode != null && networkLayer.supports(planitMode)) {
          return true;
        }
      }            
    }
    return false;
  }  
  
  /** collect activated modes that are utilised via all derived tagging schemes. currently we only consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * @param settings to filter for activated modes only
   * @param networkLayer to identify subset of modes relevant for the layer at hand 
   * @return list os OSM modes that would identify such modes */
  protected static Set<String> getEligibleTaggingSchemeHelperModes(PlanitOsmNetworkReaderSettings settings, MacroscopicPhysicalNetwork networkLayer) {
    Set<String> eligibleModes = new HashSet<>();
    if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BUS) && networkLayer.supports(settings.getMappedPlanitMode(OsmRoadModeTags.BUS))){
      eligibleModes.add(OsmRoadModeTags.BUS);
      eligibleModes.add(OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE);
    }else if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.BICYCLE) && networkLayer.supports(settings.getMappedPlanitMode(OsmRoadModeTags.BICYCLE))) {
      eligibleModes.add(OsmRoadModeTags.BICYCLE);
    }else if(settings.hasAnyMappedPlanitMode(OsmRoadModeTags.HEAVY_GOODS) && networkLayer.supports(settings.getMappedPlanitMode(OsmRoadModeTags.HEAVY_GOODS))) {
      eligibleModes.add(OsmRoadModeTags.HEAVY_GOODS);
    }
    return eligibleModes;
  }   
  
  /** Collect all the mapped OsmModes for keys that are found to be present in the passed in tags map
   * 
   * @param tags to use
   * @return osmModes for which the modeKeyMap is available (possibly extracted from road mode category when osmModeKeyMap contains a road mode category instead)
   */
  protected Set<String> getMappedModesForAvailableKeys(Map<String,String> tags, Map<String,String> osmModeKeyMap){
    Set<String> eligibleModes = new HashSet<>();
    for(Entry<String,String> entry: osmModeKeyMap.entrySet()) {
      if(tags.containsKey(entry.getValue())) {
        /* lanes:</mode/>=* entry found */

        if(!eligiblePublicServiceVehicleModes.isEmpty() && entry.getKey().equals(OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE)) {
          /* when psv category found, map all eligible modes for this category instead */
          eligibleModes.addAll(eligiblePublicServiceVehicleModes);  
        }else {
          eligibleModes.add(entry.getKey());
        }
      }
    }
    return eligibleModes;
  }

 
  /** Constructor
   * @param eligibleOsmModes (or road mode categories) to consider for the lane modes scheme
   */
  public OsmLaneTaggingSchemeHelper(final Set<String> theEligibleOsmModes) {   
    eligibleOsmModes = new HashSet<>(theEligibleOsmModes);
    /* track what supported psv modes are eligible and present, so we can map them when a psv based entry is encountered */
    eligiblePublicServiceVehicleModes = new HashSet<>(publicServiceVehicleModes);
    eligiblePublicServiceVehicleModes.retainAll(eligibleOsmModes);
  }
  
  /** Verify if any eligible modes are present
   * @return
   */
  public boolean hasEligibleModes() {
    return !eligibleOsmModes.isEmpty();
  }
   
  
}

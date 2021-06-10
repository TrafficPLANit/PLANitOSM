package org.planit.osm.util;

import java.util.HashMap;
import java.util.Map;

import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderSettings;
import org.planit.osm.tags.OsmDirectionTags;
import org.planit.osm.tags.OsmLaneTags;
import java.util.Set;

/**
 * The modeLanes tagging scheme is one of a number of tagging schemes used by OSM to identify dedicated lanes for specific modes. It allows
 * to identify which and how many lanes (and indirectly in what direction) are dedicated to a mode.
 * A good example and comparison of different schemes can be found for buses via https://wiki.openstreetmap.org/wiki/Bus_lanes
 * <p>
 * Methods that help identify modes specified using the {@code <mode>:lanes:*=*} scheme. since this scheme applies across a number of modes
 * it is useful to group the functionality in a separate class
 * 
 * @author markr
 *
 */
public class OsmModeLanesTaggingSchemeHelper extends OsmLaneTaggingSchemeHelper{
    
  /** {@code <mode>:lanes} tags */
  protected final Map<String,String> modeLanesKeyTags = new HashMap<String, String>();
  
  /** {@code <mode>:lanes:forward} tags */
  protected final Map<String,String> modeLanesForwardKeyTags = new HashMap<String, String>();
  
  /**  {@code <mode>:lanes:backward} tags */
  protected final Map<String,String> modeLanesBackwardKeyTags = new HashMap<String, String>();      
    
  /**
   * Initialise all the relevant keys for which to check that pertain to this scheme in combination with the chosen modes
   */
  private void initialise() {    
    if(hasEligibleModes()) {
      /* lanes:<mode> */
      eligibleOsmModes.forEach(osmMode -> modeLanesKeyTags.put(osmMode, OsmTagUtils.createCompositeOsmKey(osmMode, OsmLaneTags.LANES)));    
      /* lanes:<mode>:forward */
      modeLanesKeyTags.forEach( (osmMode, modeLaneTag) -> modeLanesForwardKeyTags.put(osmMode, OsmTagUtils.createCompositeOsmKey(modeLaneTag, OsmDirectionTags.FORWARD)));         
      /* lanes:<mode>:backward */
      modeLanesKeyTags.forEach((osmMode, modeLaneTag) -> modeLanesBackwardKeyTags.put(osmMode, OsmTagUtils.createCompositeOsmKey(modeLaneTag, OsmDirectionTags.BACKWARD)));                     
    }
  }
  
  /** Verify if any modes that can be identified via the {@code <mode>:lanes} tagging scheme are currently activated via the settings making it worthwhile to utilise this tagging scheme. 
   * Currently we only consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * 
   * @param settings containing the activated and mapped Osm to PLANit modes
   * @param networkLayer to identify supported modes on the layer, which is a subset of all mapped modes 
   * @return yes, when these modes are activated, false otherwise
   */
  public static boolean requireLanesModeSchemeHelper(PlanitOsmNetworkReaderSettings settings, MacroscopicPhysicalNetwork networkLayer) {
    return OsmLaneTaggingSchemeHelper.requireTaggingSchemeHelper(settings, networkLayer);
  }  
  
  /** collect activated modes(and their mode categories) that can be identified via the {@code <mode>:lanes} tagging scheme are currently supported. currently we only consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * @param settings to filter for activated modes only
   * @param networkLayer to identify supported modes on the layer, which is a subset of all mapped modes 
   * @return list of OSM modes that would identify such modes */
  public static Set<String> getEligibleModeLanesSchemeHelperModes(PlanitOsmNetworkReaderSettings settings, MacroscopicPhysicalNetwork networkLayer) {
    return OsmLaneTaggingSchemeHelper.getEligibleTaggingSchemeHelperModes(settings, networkLayer);
  }   
  
  /** Constructor
   * 
   * @param theEligibleOsmModes (or road mode categories) to consider for the lane modes scheme
   */
  public OsmModeLanesTaggingSchemeHelper(final Set<String> theEligibleOsmModes) {   
    super(theEligibleOsmModes);
    initialise();
  }
    
  /**
   * Collect the registered eligible OSM modes with lanes present without any further direction information via the {@code lanes:<mode>} mode tags
   * 
   * @param tags to use
   * @return modes found
   */
  public Set<String> getModesWithLanesWithoutDirection(Map<String,String> tags) {
    return getMappedModesForAvailableKeys(tags, modeLanesKeyTags);
  }
  
  /**
   * collect the registered eligible OSM modes with lanes present for a particular direction via the {@code lanes:<mode>} mode tags
   * 
   * @param tags to use
   * @param isForwardDirection when true explore only forward direction, when false only backward direction
   * @return modes in given direction
   */
  public Set<String> getModesWithLanesInDirection(Map<String,String> tags, boolean isForwardDirection) {
    Map<String,String> directionalKeyTags = isForwardDirection ? modeLanesForwardKeyTags : modeLanesBackwardKeyTags;
    return getMappedModesForAvailableKeys(tags, directionalKeyTags);
  }   
    
  
}

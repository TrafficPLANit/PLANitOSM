package org.goplanit.osm.converter.helper;

import java.util.HashMap;
import java.util.Map;

import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.tags.OsmDirectionTags;
import org.goplanit.osm.tags.OsmLaneTags;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;

import java.util.Set;

/**
 * The lanesMode tagging scheme is one of several OSM tagging schemes for lanes dedicated to specific modes. It does not
 * tell which lanes belong to a mode, but it does tell how many lanes (and possibly in what direction) are dedicated to
 * it. https://wiki.openstreetmap.org/wiki/Bus_lanes gives a good example and comparison of the schemes for buses.
 * <p>
 * Methods that help identify modes specified using the {@code lanes:<mode>:*=*} scheme. As this scheme applies to a
 * number of modes, they are grouped in a separate class
 * </p>
 *
 * @author markr
 */
public class OsmLanesModeTaggingSchemeHelper extends OsmLaneTaggingSchemeHelper{
      
  /** {@code lanes:<mode>} tags */
  protected Map<String,String> lanesModeKeyTags = new HashMap<String, String>();
  
  /** {@code lanes:<mode>:forward} tags */
  protected final Map<String,String> lanesModeForwardKeyTags = new HashMap<String, String>();
  
  /** {@code lanes:<mode>:backward} tags */
  protected final Map<String,String> lanesModeBackwardKeyTags = new HashMap<String, String>();  
   
    
  /**
   * Initialise all the relevant keys for which to check that pertain to this scheme in combination with the chosen modes
   */
  private void initialise() {    
    if(hasEligibleModes()) {
      /* lanes:<mode> */
      eligibleOsmModes.forEach(osmMode -> lanesModeKeyTags.put(osmMode, OsmTagUtils.createCompositeOsmKey(OsmLaneTags.LANES, osmMode)));    
      /* lanes:<mode>:forward */
      lanesModeKeyTags.forEach( (osmMode, lanesModeTag) -> lanesModeForwardKeyTags.put(osmMode, OsmTagUtils.createCompositeOsmKey(lanesModeTag, OsmDirectionTags.FORWARD)));         
      /* lanes:<mode>:backward */
      lanesModeKeyTags.forEach( (osmMode, lanesModeTag) -> lanesModeBackwardKeyTags.put(osmMode, OsmTagUtils.createCompositeOsmKey(lanesModeTag, OsmDirectionTags.BACKWARD)));                     
    }
  }
  
  /** Verify if any modes that can be identified via the {@code lanes:<mode>} tagging scheme are activated in the
   * settings, making it worthwhile to use this tagging scheme. If the passed in layer does not support the activated
   * PLANit mode, the helper is not required either, since the layer supports none of these modes. Currently we only
   * consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * 
   * @param settings containing the activated and mapped OSM to PLANit modes 
   * @param networkLayer to identify supported modes on the layer, which is a subset of all mapped modes
   * @return yes, when these modes are activated, false otherwise
   */
  public static boolean requireLanesModeSchemeHelper(OsmNetworkReaderSettings settings, MacroscopicNetworkLayer networkLayer) {
    return OsmLaneTaggingSchemeHelper.requireTaggingSchemeHelper(settings, networkLayer);
  }    
  
  /** collect the activated modes, supported by the layer, that can be identified via the {@code lanes:<mode>} tagging
   * scheme. Currently we only consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * 
   * @param settings to filter for activated modes only
   * @param networkLayer to restrict the eligible modes based on the modes supported by the layer
   * @return list os OSM modes that would identify such modes */
  public static Set<String> getEligibleLanesModeSchemeHelperModes(OsmNetworkReaderSettings settings, MacroscopicNetworkLayer networkLayer) {
    return OsmLaneTaggingSchemeHelper.getEligibleTaggingSchemeHelperModes(settings, networkLayer);
  } 
  
  /** Constructor
   * 
   * @param theEligibleOsmModes (or road mode categories) to consider for the lane modes scheme
   */
  public OsmLanesModeTaggingSchemeHelper(final Set<String> theEligibleOsmModes) {   
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
    return getMappedModesForAvailableKeys(tags, lanesModeKeyTags);
  }
  
  /**
   * Collect the registered eligible OSM modes with lanes present without any further direction information via the {@code lanes:<mode>} mode tags
   * 
   * @param tags to use
   * @param isForwardDirection when true explore only forward direction, when false only backward direction
   * @return modes with lanes in direction indicated
   */
  public Set<String> getModesWithLanesInDirection(Map<String,String> tags, boolean isForwardDirection) {
    Map<String,String> directionalKeyTags = isForwardDirection ? lanesModeForwardKeyTags : lanesModeBackwardKeyTags;
    return getMappedModesForAvailableKeys(tags, directionalKeyTags);
  }  
    
  
}

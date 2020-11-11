package org.planit.osm.util;

import java.util.HashMap;
import java.util.Map;
import org.planit.osm.physical.network.macroscopic.PlanitOsmSettings;
import org.planit.osm.tags.OsmDirectionTags;
import org.planit.osm.tags.OsmLaneTags;
import java.util.Set;

/**
 * The lanesMode tagging scheme is one of a number of tagging schemes used by OSM to identify dedicated lanes for specific modes. It does not allow
 * to identify which of the lanes belongs to a mode but it does allow one to identify how many lanes (and possibly in what direction) are dedicated to a mode.
 * A good example and comparison of different schemes can be found for buses via https://wiki.openstreetmap.org/wiki/Bus_lanes
 * <p>
 * Methods that help identify modes specified using the lanes:/<mode/>:*=* scheme. since this scheme applies across a number of modes
 * it is useful to group the functionality in a separate class
 * </p>
 * 
 * @author markr
 *
 */
public class OsmLanesModeTaggingSchemeHelper extends OsmLaneTaggingSchemeHelper{
      
  /** lanes:<mode> tags */
  protected Map<String,String> lanesModeKeyTags = new HashMap<String, String>();
  
  /** lanes:<mode>:forward tags */
  protected final Map<String,String> lanesModeForwardKeyTags = new HashMap<String, String>();
  
  /** lanes:<mode>:backward tags */
  protected final Map<String,String> lanesModeBackwardKeyTags = new HashMap<String, String>();  
   
    
  /**
   * initialise all the relevant keys for which to check that pertain to this scheme in combination with the chosen modes
   */
  private void initialise() {    
    if(hasEligibleModes()) {
      /* lanes:<mode> */
      eligibleOsmModes.forEach(osmMode -> lanesModeKeyTags.put(osmMode, PlanitOsmUtils.createCompositeOsmKey(OsmLaneTags.LANES, osmMode)));    
      /* lanes:<mode>:forward */
      lanesModeKeyTags.forEach( (osmMode, lanesModeTag) -> lanesModeForwardKeyTags.put(osmMode, PlanitOsmUtils.createCompositeOsmKey(lanesModeTag, OsmDirectionTags.FORWARD)));         
      /* lanes:<mode>:backward */
      lanesModeKeyTags.forEach( (osmMode, lanesModeTag) -> lanesModeBackwardKeyTags.put(osmMode, PlanitOsmUtils.createCompositeOsmKey(lanesModeTag, OsmDirectionTags.BACKWARD)));                     
    }
  }
  
  /** Verify if any modes that can be identified via the lanes:/<mode/> tagging scheme are currently activated via the settings making it worthwhile to utilise this tagging scheme.
   * Currently we only consider:
   * 
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * @param settings containing the activated and mapped Osm to PLANit modes 
   * @return yes, when these modes are activated, false otherwise
   */
  public static boolean requireLanesModeSchemeHelper(PlanitOsmSettings settings) {
    return OsmLaneTaggingSchemeHelper.requireTaggingSchemeHelper(settings);
  }    
  
  /** collect activated modes that can be identified via the lanes:/<mode/> tagging scheme are currently supported. currently we only consider:
   * <ul>
   * <li>bus (and therefore psv)</li>
   * <li>bicycle</li>
   * <li>hgv</li>
   * </ul>
   * @param settings to filter for activated modes only
   * @return list os OSM modes that would identify such modes */
  public static Set<String> getEligibleLanesModeSchemeHelperModes(PlanitOsmSettings settings) {
    return OsmLaneTaggingSchemeHelper.getEligibleTaggingSchemeHelperModes(settings);
  } 
  
  /** Constructor
   * @param eligibleOsmModes (or road mode categories) to consider for the lane modes scheme
   */
  public OsmLanesModeTaggingSchemeHelper(final Set<String> theEligibleOsmModes) {   
    super(theEligibleOsmModes);
    initialise();
  }
    
  /**
   * collect the registered eligible Osm modes with lanes present without any further direction information via the lanes:/<mode/> mode tags
   * 
   * @param tags to use
   */
  public Set<String> getModesWithLanesWithoutDirection(Map<String,String> tags) {
    return getMappedModesForAvailableKeys(tags, lanesModeKeyTags);
  }
  
  /**
   * collect the registered eligible Osm modes with lanes present without any further direction information via the lanes:/<mode/> mode tags
   * 
   * @param tags to use
   * @param isForwardDirection when true explore only forward direction, when false only backward direction
   */
  public Set<String> getModesWithLanesInDirection(Map<String,String> tags, boolean isForwardDirection) {
    Map<String,String> directionalKeyTags = isForwardDirection ? lanesModeForwardKeyTags : lanesModeBackwardKeyTags;
    return getMappedModesForAvailableKeys(tags, directionalKeyTags);
  }  
    
  
}
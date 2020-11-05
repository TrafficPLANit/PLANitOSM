package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Methods that help identify modes specified using the lanes:/<mode/>:*=* scheme. since this scheme applies across a number of modes
 * it is useful to group the functionality in a separate class
 * 
 * @author markr
 *
 */
public class LanesModeTaggingSchemeHelper{
  
  /** track the modes that we support as part of public service vehicle (psv) lanes, currently only bus */
  protected static final Set<String> publicServiceVehicleModes = new HashSet<String>();
  
  static {
    publicServiceVehicleModes.add(OsmRoadModeTags.BUS);
  }
  
  /** track the osm modes that we are considering for the lanes:mode:*=* scheme */
  protected final Set<String> eligibleOsmModes;
  
  /** track the public service vehicle modes that are eligible for this instance */
  protected final Set<String> eligiblePublicServiceVehicleModes;  
  
  /** lanes:<mode> tags */
  protected Map<String,String> laneModeKeyTags;
  
  /** lanes:<mode>:forward tags */
  protected Map<String,String> laneModeForwardKeyTags;
  
  /** lanes:<mode>:backward tags */
  protected Map<String,String> laneModeBackwardKeyTags;  
  
  /** lanes:<mode>:<direction> tags */
  protected Map<String,String> laneModeDirectionKeyTags;  
    
  /**
   * initialise all the relevant keys for which to check that pertain to this scheme in conbination with the chosen modes
   */
  private void initialise() {    
    if(hasEligibleModes()) {
      /* lanes:<mode> */
      eligibleOsmModes.forEach(osmMode -> laneModeKeyTags.put(osmMode, PlanitOsmUtils.createCompositeOsmKey(OsmLaneTags.LANES, osmMode)));    
      /* lanes:<mode>:forward */
      eligibleOsmModes.forEach(osmMode -> laneModeForwardKeyTags.put(osmMode, PlanitOsmUtils.createCompositeOsmKey(osmMode, OsmDirectionTags.FORWARD)));         
      /* lanes:<mode>:backward */
      eligibleOsmModes.forEach(osmMode -> laneModeBackwardKeyTags.put(osmMode, PlanitOsmUtils.createCompositeOsmKey(osmMode, OsmDirectionTags.BACKWARD)));                     
      /* lanes:<mode>:<direction> */
      laneModeDirectionKeyTags = Map.copyOf(laneModeForwardKeyTags);
      laneModeDirectionKeyTags.putAll(laneModeBackwardKeyTags);
    }
  }
  
  /** Constructor
   * @param eligibleOsmModes (or road mode categories) to consider for the lane modes scheme
   */
  public LanesModeTaggingSchemeHelper(final Set<String> theEligibleOsmModes) {   
    eligibleOsmModes = new HashSet<>(theEligibleOsmModes);
    /* track what supported psv modes are eligible and present, so we can map them when a psv based entry is encountered */
    eligiblePublicServiceVehicleModes = new HashSet<>(publicServiceVehicleModes);
    eligiblePublicServiceVehicleModes.retainAll(eligibleOsmModes);
    initialise();
  }
  
  /** Verify if any eligible modes are present
   * @return
   */
  public boolean hasEligibleModes() {
    return !eligibleOsmModes.isEmpty();
  }

  
  /**
   * collect the registered eligible Osm modes with lanes present without any further direction information via the lanes:/<mode/> mode tags
   * 
   * @param tags to use
   */
  public Set<String> getModesWithLanesWithoutDirection(Map<String,String> tags) {
    Set<String> eligibleModes = new HashSet<>();
    for(Entry<String,String> entry: laneModeKeyTags.entrySet()) {
      if(tags.containsKey(entry.getValue())) {
        if(!eligiblePublicServiceVehicleModes.isEmpty() && entry.getKey().equals(OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE)) {
          eligibleModes.addAll(eligiblePublicServiceVehicleModes);  
        }else {
          eligibleModes.add(entry.getKey());
        }
      }
    }
    return eligibleModes;
  }
    
  
}

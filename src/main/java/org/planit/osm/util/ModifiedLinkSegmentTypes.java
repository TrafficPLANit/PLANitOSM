package org.planit.osm.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.collections4.map.HashedMap;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

/**
 * Helper class that tracks all modified, i.e., custom link segment types, that differ from the default link segment types
 * created for open street map road and rail way types.
 * 
 * All custom link segment types are stored by their original link segment type (id) that they are based on. To make it efficient to look them up
 * when verifying if some potentential new custom link segment type already exists (or not)
 * 
 * @author markr
 *
 */
public class ModifiedLinkSegmentTypes {
  
  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(ModifiedLinkSegmentTypes.class.getCanonicalName());
  
  /**
   * Track link segment types where only modes are either added or removed compared to their parent link segment type.
   * To find the actual changes in modes one needs to combine the information on the added modes and removed modes which are stored separately
   * 
   * @author markr
   *
   */
  public class ModifiedLinkSegmentTypesModes{
    
    protected final MacroscopicLinkSegmentType original;
    
    /** the segment types with added modes compared to the original */
    protected final Map<Set<Mode>, Set<MacroscopicLinkSegmentType> > linkSegmentTypesWithAddedModes = new HashMap<Set<Mode>, Set<MacroscopicLinkSegmentType>>();
    
    /** the segment types with removed modes compared to the original */
    protected final Map<Set<Mode>, Set<MacroscopicLinkSegmentType>> linkSegmentTypesWithRemovedModes = new HashMap<Set<Mode>, Set<MacroscopicLinkSegmentType>>();
    
    /** constructor 
     * @param original original link segment type
     */
    ModifiedLinkSegmentTypesModes(MacroscopicLinkSegmentType original){
      this.original=original;
      /* populate empty entries with no added or removed modes */
      linkSegmentTypesWithAddedModes.put(null, new HashSet<MacroscopicLinkSegmentType>());
      linkSegmentTypesWithRemovedModes.put(null, new HashSet<MacroscopicLinkSegmentType>());
    }
    
    
    /** Verify if a modified link segment type with the provided added/removed modes exist
     * @param addedModes the added modes, can be null or empty in case no modes were added
     * @param removedModes the removed modes, can be null or empty in case no modes were added
     * @return true when a modified link segment type exists with these mode modifications
     */
    public boolean containsModifiedLinkSegmentType(final Set<Mode> addedModes, final Set<Mode> removedModes){
      return getModifiedLinkSegmentType(addedModes, removedModes)!= null;
    }
    
    /** collect a modified link segment type with the provided added/removed modes exist
     * @param addedModes the added modes, can be null or empty in case no modes were added
     * @param removedModes the removed modes, can be null or empty in case no modes were added
     * @return the link segment type when found, null otherwise
     */
    public MacroscopicLinkSegmentType getModifiedLinkSegmentType(final Set<Mode> addedModes, final Set<Mode> removedModes){
      Set<Mode> theAddedModes = (addedModes == null) ? new HashSet<Mode>() : addedModes;
      Set<Mode> theRemovedModes = (removedModes == null) ? new HashSet<Mode>() : removedModes;
      
      if(linkSegmentTypesWithAddedModes.containsKey(theAddedModes)) {
        Set<MacroscopicLinkSegmentType> candidateLinkSegmentTypes = linkSegmentTypesWithAddedModes.get(theAddedModes);
        if(!candidateLinkSegmentTypes.isEmpty()) {
          candidateLinkSegmentTypes  = new HashSet<MacroscopicLinkSegmentType>(candidateLinkSegmentTypes);
          Set<MacroscopicLinkSegmentType> otherCandidateLinkSegmentTypes = linkSegmentTypesWithRemovedModes.get(theRemovedModes);
          if(otherCandidateLinkSegmentTypes != null) {
            candidateLinkSegmentTypes.retainAll(otherCandidateLinkSegmentTypes);
            if(candidateLinkSegmentTypes.size() > 1) {
              LOGGER.warning(String.format("at most one (unique) modified link segment type expected based on added/removed modes compared to the original (id:%d), but multiple found",original));
            }else if(candidateLinkSegmentTypes.size() == 1) {
              return candidateLinkSegmentTypes.iterator().next();
            }
          }
        }
      }
      return null;
    } 
    
    /** Add a modified link segment type based on the changes in mode support. It is assumed the provided added and removed modes are consistent with the mode properties
     * in the link segment type, as this is not verified by by this method
     * 
     * @param modifiedLinkSegmentType the link segment type that is a modification of the original based on the passed in added and removed modes
     * @param addedModes the added modes
     * @param removedModes the removed modes
     * @return true when successfully added, false if not
     */
    public boolean addModifiedLinkSegmentType(MacroscopicLinkSegmentType modifiedLinkSegmentType, final Set<Mode> addedModes, final Set<Mode> removedModes) {
      Set<Mode> theAddedModes = (addedModes == null) ? new HashSet<Mode>() : addedModes;
      Set<Mode> theRemovedModes = (removedModes == null) ? new HashSet<Mode>() : removedModes;
      
      if(!containsModifiedLinkSegmentType(theAddedModes, theRemovedModes)) {
        linkSegmentTypesWithAddedModes.putIfAbsent(Collections.unmodifiableSet(addedModes), new HashSet<MacroscopicLinkSegmentType>());     
        linkSegmentTypesWithAddedModes.get(addedModes).add(modifiedLinkSegmentType);
        linkSegmentTypesWithRemovedModes.putIfAbsent(Collections.unmodifiableSet(theRemovedModes), new HashSet<MacroscopicLinkSegmentType>());        
        linkSegmentTypesWithRemovedModes.get(theRemovedModes).add(modifiedLinkSegmentType);
        return true;
      }
      return false;
    }
      
  }
  
  /** track all modified link segment types (value) that differ by the allowed modes from the original (key) */
  protected final Map<MacroscopicLinkSegmentType,ModifiedLinkSegmentTypesModes> modifiedLinkSegmentTypeModes = new HashedMap<MacroscopicLinkSegmentType,ModifiedLinkSegmentTypesModes>();

  /** Verify if a modified link segment type with the provided added/removed modes exist for the given original link segment type
   * 
   * @param original original type
   * @param addedModes the added modes, can be null or empty in case no modes were added
   * @param removedModes the removed modes, can be null or empty in case no modes were added
   * @return true when a modified link segment type exists with these mode modifications
   */
  public boolean containsModifiedLinkSegmentType(final MacroscopicLinkSegmentType original, final Set<Mode> addedModes, final Set<Mode> removedModes) {
    if(modifiedLinkSegmentTypeModes.containsKey(original)) {
      return modifiedLinkSegmentTypeModes.get(original).containsModifiedLinkSegmentType(addedModes, removedModes);
    }
    return false;
  }
  
  /** Collect a modified link segment type with the provided added/removed modes if it exists
   * 
   * @param original original type
   * @param addedModes the added modes, can be null or empty in case no modes were added
   * @param removedModes the removed modes, can be null or empty in case no modes were added
   * @return the modified link segment type if it exists, null otherwise
   */
  public MacroscopicLinkSegmentType getModifiedLinkSegmentType(final MacroscopicLinkSegmentType original, final Set<Mode> addedModes, final Set<Mode> removedModes) {
    if(modifiedLinkSegmentTypeModes.containsKey(original)) {
      return modifiedLinkSegmentTypeModes.get(original).getModifiedLinkSegmentType(addedModes, removedModes);
    }
    return null;
  }
  
  /** Add a modified link segment type based on the changes in mode support. It is assumed the provided added and removed modes are consistent with the mode properties
   * in the link segment type, as this is not verified by by this method
   * 
   * @param original original link segment type the modified type is a modification of
   * @param modifiedLinkSegmentType the link segment type that is a modification of the original based on the passed in added and removed modes
   * @param addedModes the added modes
   * @param removedModes the removed modes
   * @return true when successfully added, false if not
   */  
  public boolean addModifiedLinkSegmentType(final MacroscopicLinkSegmentType original, MacroscopicLinkSegmentType modifiedLinkSegmentType, final Set<Mode> addedModes, final Set<Mode> removedModes) {
    modifiedLinkSegmentTypeModes.putIfAbsent(original, new ModifiedLinkSegmentTypesModes(original));
    return modifiedLinkSegmentTypeModes.get(original).addModifiedLinkSegmentType(modifiedLinkSegmentType, addedModes, removedModes);
  }

  /**
   * remove all identified modified link segment types available
   */
  public void reset() {
    modifiedLinkSegmentTypeModes.clear();    
  }
}

package org.planit.osm.converter.helper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.planit.osm.converter.network.OsmNetworkReaderSettings;
import org.planit.osm.util.OsmModeUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;

/**
 * Class to support parsing and other functionality that depends on the configuration of the readers regarding OSM modes and their mappng to PLANit modes
 * 
 * @author markr
 *
 */
public class OsmModeHelper {
  
  /** settings relevant to this parser */
  private final OsmNetworkReaderSettings settings;
  
  /** Collect the settings containing the mapping between PLANit and OSM modes
   * 
   * @return settings used
   */
  protected OsmNetworkReaderSettings getSettings() {
    return this.settings;
  }
    
  /** Constructor 
   * 
   * @param settings to use
   */
  public OsmModeHelper(final OsmNetworkReaderSettings settings) {
    this.settings = settings;    
  }
  
  /** find out if link osmModesToCheck are compatible with the passed in reference osm modes. Mode compatible means at least one overlapping
   * mode that is mapped to a planit mode. When one allows for pseudo comaptibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param referenceOsmModes to map against (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  public boolean isModeCompatible(Collection<String> osmModesToCheck, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    /* collect compatible modes */
    Collection<String> overlappingModes = OsmModeUtils.extractCompatibleOsmModes(osmModesToCheck, referenceOsmModes, allowPseudoMatches);    
    
    /* only proceed when there is a valid mapping based on overlapping between reference modes and zone modes, while in absence
     * of reference osm modes, we trust any nearby zone with mapped mode */
    if(settings.hasAnyMappedPlanitMode(overlappingModes)) {
      /* no overlapping mapped modes while both have explicit osm modes available, not a match */
      return true;
    }
    return false;    
  }  
  
  /** Find out if PLANit link is mode compatible with the passed in reference OSM modes. Mode compatible means at least one overlapping
   * mode that is mapped to a PLANit mode. If the zone has no known modes, it is by definition not mode compatible. 
   * When one allows for pseudo compatibility we relax the restrictions such that any rail/road/water mode
   * is considered a match with any other rail/road/water mode. This can be useful when you do not want to make super strict matches but still want
   * to filter out definite non-matches.
   *  
   * @param link to verify
   * @param referenceOsmModes to map against (may be null)
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car, train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  public boolean isLinkModeCompatible(Link link, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {
    Collection<String> osmLinkModes = new HashSet<String>(); 
    if(link.hasEdgeSegmentAb()) {      
      Collection<Mode> planitModes = ((MacroscopicLinkSegment)link.getEdgeSegmentAb()).getLinkSegmentType().getAvailableModes();
      osmLinkModes.addAll(settings.getMappedOsmModes(planitModes));
    }
    if(link.hasEdgeSegmentBa()) {      
      Collection<Mode> planitModes = ((MacroscopicLinkSegment)link.getEdgeSegmentBa()).getLinkSegmentType().getAvailableModes();
      osmLinkModes.addAll(settings.getMappedOsmModes(planitModes));
    }
    if(osmLinkModes==null || osmLinkModes.isEmpty()) {
      return false;
    }
    
    /* check mode compatibility on extracted link supported modes*/
    return isModeCompatible(osmLinkModes, referenceOsmModes, allowPseudoMatches);
  }  
  
  /** Find all links with at least one compatible mode (and PLANit mode mapped) based on the passed in reference OSM modes and potential links
   * In case no eligible modes are provided (null), we allow any transfer zone with at least one valid mapped mode
   *  
   * @param referenceOsmModes to map against (may be null)
   * @param potentialLinks to extract mode compatible links from
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water mode, when false only exact matches are allowed
   * @return matched links that are deemed compatible
   */   
  public Collection<Link> filterModeCompatibleLinks(Collection<String> referenceOsmModes, Collection<Link> potentialLinks, boolean allowPseudoModeMatches) {
    Set<Link> modeCompatibleLinks = new HashSet<Link>();
    for(Link link : potentialLinks) {
      if(isLinkModeCompatible(link, referenceOsmModes, allowPseudoModeMatches)) {
        modeCompatibleLinks.add(link);
      }
    }    
    return modeCompatibleLinks;
  }  
  
  
}

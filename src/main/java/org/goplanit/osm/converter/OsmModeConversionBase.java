package org.goplanit.osm.converter;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.util.OsmModeUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.Link;

/**
 * Class to support parsing and other functionality that depends on the configuration of the readers regarding OSM
 * modes and their mapping to PLANit modes as well as the subset of modes provided so limit itself to, can be all
 * modes but also just modes for a given layer
 * 
 * @author markr
 *
 */
public class OsmModeConversionBase {

  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmModeConversionBase.class.getCanonicalName());
  
  /** settings relevant to this parser */
  private final OsmNetworkReaderSettings settings;

  /** used for temporary storage */
  private final Collection<String> osmLinkModes;

  private final Consumer<Mode> addMappedOsmLinkModesByPlanitMode;

  /** track mapping between predefined mode type and its instance */
  private Map<PredefinedModeType, Mode> predefinedModeTypeToModeMap;

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
   * @param layerModes in use across the network we are populating
   */
  public OsmModeConversionBase(final OsmNetworkReaderSettings settings, Iterable<Mode> layerModes) {
    this.settings = settings;
    this.predefinedModeTypeToModeMap = new HashMap<>();

    // prep lambda to be used in conjunction with container
    this.osmLinkModes = new HashSet<>();
    addMappedOsmLinkModesByPlanitMode = (planitMode) -> {
      if (planitMode.isPredefinedModeType()) {
        osmLinkModes.addAll(settings.getMappedOsmModes(planitMode.getPredefinedModeType()));
      }
    };

    /** create mode mapping */
    for(var mode : layerModes){
      if(mode.isPredefinedModeType()){
        var old = predefinedModeTypeToModeMap.put(mode.getPredefinedModeType(),mode);
        if(old != null){
          LOGGER.severe(String.format("found multiple modes with same predifined mode type %s, shouldn't happen",
              old.getPredefinedModeType()));
        }
      }
    }
  }

  /** Convenience method that collects the currently mapped PLANit modes (road or rail) for the given OSM modes
   *
   * @param osmModes to collect mapped mode for (if any)
   * @return mapped PLANit modes, if not available empty set is returned
   */
  public Set<Mode> getActivatedPlanitModes(final Collection<String> osmModes) {
    HashSet<Mode> mappedPlanitModes = new HashSet<>();

    if(osmModes == null) {
      return mappedPlanitModes;
    }

    for(String osmMode : osmModes) {
      var theModeType = settings.getMappedPlanitModeType(osmMode);
      if(theModeType == null) {
        continue;
      }

      var theMode = predefinedModeTypeToModeMap.get(theModeType);
      if(theMode == null) {
        continue;
      }
      mappedPlanitModes.add(theMode);
    }
    return mappedPlanitModes;
  }

  /** Convenience method that collects the currently mapped PLANit modes (road or rail) for the given OSM modes
   *
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getActivatedPlanitMode(final String osmMode) {
    if(osmMode == null) {
      return null;
    }

    var theModeType = settings.getMappedPlanitModeType(osmMode);
    if(theModeType == null) {
      return null;
    }

    var theMode = predefinedModeTypeToModeMap.get(theModeType);
    if(theMode == null) {
      return null;
    }
    return theMode;
  }
  
  /** find out if link osmModesToCheck are compatible with the passed in reference osm modes. Mode compatible means at
   * least one overlapping mode that is mapped to a planit mode. When one allows for pseudo compatibility we relax
   * the restrictions such that any rail/road/water mode is considered a match with any other rail/road/water mode.
   * This can be useful when you do not want to make super strict matches but still want to filter out definite
   * non-matches.
   *  
   * @param osmModesToCheck to check
   * @param referenceOsmModes to map against (may be null)
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car,
   *                           train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  public boolean isModeCompatible(
      final Collection<String> osmModesToCheck,
      final Collection<String> referenceOsmModes,
      boolean allowPseudoMatches) {

    /* collect compatible OSM modes */
    Collection<String> overlappingOsmModes =
        OsmModeUtils.extractCompatibleOsmModes(osmModesToCheck, referenceOsmModes, allowPseudoMatches);
    
    /* only proceed when there is a valid mapping based on overlapping between reference modes and zone modes, while
     * in absence of reference osm modes, we trust any nearby zone with mapped mode */
    if(settings.hasAnyMappedPlanitModeType(overlappingOsmModes)) {
      /* no overlapping mapped modes while both have explicit osm modes available, not a match */
      return true;
    }
    return false;    
  }  
  
  /** Find out if PLANit link is mode compatible with the passed in reference OSM modes. Mode compatible means at
   * least one overlapping mode that is mapped to a PLANit mode. If the zone has no known modes, it is by definition
   * not mode compatible. When one allows for pseudo compatibility we relax the restrictions such that any
   * rail/road/water mode is considered a match with any other rail/road/water mode. This can be useful when you do
   * not want to make super strict matches but still want to filter out definite non-matches.
   *  
   * @param link to verify
   * @param referenceOsmModes to map against (may be null)
   * @param allowPseudoMatches when true, we consider all road modes compatible, i.e., bus is compatible with car,
   *                           train is compatible with tram, etc., when false only exact matches are accepted
   * @return matched transfer zones
   */   
  public boolean isLinkModeCompatible(Link link, Collection<String> referenceOsmModes, boolean allowPseudoMatches) {

    osmLinkModes.clear(); // used by addMappedOsmLinkModesByPlanitMode consumer, so reset
    if(link.hasEdgeSegmentAb()) {
      Collection<Mode> planitModes =
          ((MacroscopicLinkSegment)link.getEdgeSegmentAb()).getLinkSegmentType().getAllowedModes();
      planitModes.forEach(addMappedOsmLinkModesByPlanitMode::accept);
    }
    if(link.hasEdgeSegmentBa()) {      
      Collection<Mode> planitModes =
          ((MacroscopicLinkSegment)link.getEdgeSegmentBa()).getLinkSegmentType().getAllowedModes();
      planitModes.forEach(addMappedOsmLinkModesByPlanitMode::accept);
    }

    if(osmLinkModes==null || osmLinkModes.isEmpty()) {
      return false;
    }
    
    /* check mode compatibility on extracted link supported modes*/
    return isModeCompatible(osmLinkModes, referenceOsmModes, allowPseudoMatches);
  }  
  
  /** Find all links with at least one compatible mode (and PLANit mode mapped) based on the passed in reference OSM
   * modes and potential links In case no eligible modes are provided (null), we allow any transfer zone with at least
   * one valid mapped mode
   *  
   * @param referenceOsmModes to map against (allowed null)
   * @param potentialLinksByLayer to extract mode compatible links from
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water
   *                                mode, when false only exact matches are allowed
   * @return matched links that are deemed compatible by layer, if a layer has no matches, it is excluded from the
   * map entirely
   */   
  public Map<MacroscopicNetworkLayer, Collection<MacroscopicLink>> filterModeCompatibleLinks(
      Collection<String> referenceOsmModes,
      Map<MacroscopicNetworkLayer, Collection<MacroscopicLink>> potentialLinksByLayer,
      boolean allowPseudoModeMatches) {

    var modeCompatibleLinksByLayer = new TreeMap<MacroscopicNetworkLayer, Collection<MacroscopicLink>>();
    for(var entry : potentialLinksByLayer.entrySet()) {
      Set<MacroscopicLink> modeCompatibleLinks = new HashSet<>();
      for (var link : entry.getValue()) {
        if (isLinkModeCompatible(link, referenceOsmModes, allowPseudoModeMatches)) {
          modeCompatibleLinks.add(link);
        }
      }
      if(!modeCompatibleLinks.isEmpty()){
        modeCompatibleLinksByLayer.put(entry.getKey(),modeCompatibleLinks);
      }
    }
    return modeCompatibleLinksByLayer;
  }

  
}

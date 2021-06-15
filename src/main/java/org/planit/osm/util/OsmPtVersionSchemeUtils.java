package org.planit.osm.util;

import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.tags.OsmPtv2Tags;
import org.planit.osm.tags.OsmRailwayTags;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Utilities regarding the tagging of OSM entities and the various PT version schemes that exist
 * 
 * @author markr
 *
 */
public class OsmPtVersionSchemeUtils {

  /** the logger */
  static final Logger LOGGER = Logger.getLogger(OsmPtVersionSchemeUtils.class.getCanonicalName());
  
  /**
   * Check if tags contain entries compatible with the provided Pt scheme given that we are verifying an OSM way/node that might reflect
   * a platform, stop, etc.
   *  
   * @param scheme to check against
   * @param tags to verify
   * @return true when present, false otherwise
   */
  public static boolean isCompatibleWith(OsmPtVersionScheme scheme, Map<String, String> tags) {
    if(scheme.equals(OsmPtVersionScheme.VERSION_1)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags) || OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return OsmPtv1Tags.hasPtv1ValueTag(tags);
      }
    }else if(scheme.equals(OsmPtVersionScheme.VERSION_2)) {
      return OsmPtv2Tags.hasPtv2ValueTag(tags);
    }else {
     LOGGER.severe(String.format("unknown OSM public transport scheme %s provided to check compatibility with, ignored",scheme.value()));

    }
    return false;
  }
  
  /** Verify if passed in tags reflect transfer based infrastructure that is eligible (and supported) to be parsed by this class, e.g.
   * tags related to original PT scheme stops ( railway=halt, railway=tram_stop, highway=bus_stop and highway=platform),
   * or the current v2 PT scheme (public_transport=stop_position, platform, station, stop_area)
   * 
   * @param tags
   * @return which scheme it is compatible with, NONE if none could be found
   */
  public static OsmPtVersionScheme isPublicTransportBasedInfrastructure(Map<String, String> tags) {
    if(isCompatibleWith(OsmPtVersionScheme.VERSION_2, tags)){
      return OsmPtVersionScheme.VERSION_2;
    }else if(isCompatibleWith(OsmPtVersionScheme.VERSION_1,tags)) {
      return OsmPtVersionScheme.VERSION_1;
    }
    return OsmPtVersionScheme.NONE;
  }    
  
  /** Verify if the passed on OSM node that is assumed to be recognised as a Ptv2 stop_location is in fact also a Ptv1 stop, either a highway=tram_stop or
   * highway=bus_stop. This is effectively wrongly tagged, but does occur due to confusion regarding the tagging schemes. Therefore we
   * identify this situation allowing the parser to change its behaviour and if it is a Patv1 stop, process it as such if deemed necessary
   * 
   * @param osmNode that is classified as Ptv2 stop_location
   * @param tags of the stop_location
   * @return true when also a Ptv1 stop (bus or tram stop), false otherwise
   */
  public static boolean isPtv2StopPositionPtv1Stop(OsmNode osmNode, Map<String, String> tags) {
    
    /* Context: The parser assumed a valid Ptv2 tagging and ignored the Ptv1 tag. However, it was only a valid Ptv1 tag and incorrectly tagged Ptv2 stop_location.
     * We therefore identify this special situation */    
    if(OsmPtv1Tags.isTramStop(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 tram_stop (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isBusStop(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 bus_stop (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isHalt(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 halt (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }else if(OsmPtv1Tags.isStation(tags)) {
      LOGGER.fine(String.format("Identified Ptv1 station (%d) that is also tagged as Ptv2 public_transport=stop_location", osmNode.getId()));
      return true;
    }
    return false;
  }   
}

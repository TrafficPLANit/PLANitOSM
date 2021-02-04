package org.planit.osm.physical.network.macroscopic;

import java.util.Map;
import java.util.logging.Logger;

import org.planit.geo.PlanitJtsUtils;
import org.planit.osm.settings.PlanitOsmSettings;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmPtv1Tags;
import org.planit.osm.tags.OsmPtv2Tags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.util.OsmPtVersionScheme;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Handler dedicated to parsing OSM tags related to OSM transfer infrastructure. We currently support
 * the parsing of public transport infrastructure based on the original scheme, and the version 2 scheme:
 * 
 * Original scheme tags
 * <ul>
 * <li>railway=halt</li>
 * <li>railway=tram_stop</li>
 * <li>highway=bus_stop</li>
 * <li>highway=platform</li>
 * </ul>
 * 
 * <p>
 * PT scheme v2 tags
 * <ul>
 * <li>public_transport=stop_position</li>
 * <li>public_transport=platform</li>
 * <li>public_transport=station</li>
 * <li>public_transport=stop_area</li>
 * </ul>
 * 
 * See also https://wiki.openstreetmap.org/wiki/Public_transport
 * 
 * @author markr
 *
 */
public class PlanitOsmInterModalHandler {
  
  /** the logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmInterModalHandler.class.getCanonicalName());
  
  // references
  
  /** geo utility instance based on network wide crs this layer is part of */
  private final PlanitJtsUtils geoUtils;   
  
  /** reference to parsed OSM nodes */
  private final Map<Long, OsmNode> osmNodes;
  
  /** settings relevant to this parser */
  private PlanitOsmSettings settings;
  
  /**
   * check if tags contain entries compatible with the provided Pt scheme
   * @param scheme to check against
   * @param tags to verify
   * @return true when present, false otherwise
   */
  private static boolean isCompatibleWith(OsmPtVersionScheme scheme, Map<String, String> tags) {
    if(scheme.equals(OsmPtVersionScheme.v1)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags) || OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return OsmPtv1Tags.hasPtv1ValueTag(tags);
      }
    }else if(scheme.equals(OsmPtVersionScheme.v2)) {
      return OsmPtv2Tags.hasPtv2ValueTag(tags);
    }else {
     LOGGER.severe(String.format("unknown OSM public transport scheme %s provided to check compatibility with, ignored",scheme.value()));

    }
    return false;
  }  

  /** Constructor
   * 
   * @param osmNodes reference to parsed osmNodes
   * @param settings used for this parser
   * @param geoUtils geometric utility class instance based on network wide crs
   */
  protected PlanitOsmInterModalHandler(Map<Long, OsmNode> osmNodes, PlanitOsmSettings settings, PlanitJtsUtils geoUtils) {
    this.osmNodes = osmNodes;
    this.geoUtils = geoUtils;
    this.settings = settings;    
  }

  /** Verify if passed in tags reflect transfer based infrastructure that is eligible (and supported) to be parsed by this class, e.g.
   * tags related to original PT scheme stops ( railway=halt, railway=tram_stop, highway=bus_stop and highway=platform),
   * or the current v2 PT scheme (public_transport=stop_position, platform, station, stop_area)
   * 
   * @param tags
   * @return true when eligible and identified as transfer based infrastructure, false otherwise
   */
  public static boolean isTransferBasedInfrastructure(Map<String, String> tags) {
    return isCompatibleWith(OsmPtVersionScheme.v1,tags) || isCompatibleWith(OsmPtVersionScheme.v2, tags);
  }
}

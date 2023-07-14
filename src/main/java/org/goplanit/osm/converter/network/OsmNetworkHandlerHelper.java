package org.goplanit.osm.converter.network;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Link;

/**
 * Helper class for the OSM network handlers, providing static helper methods that reflect common code across various
 * handlers that are not considered general enough to be part of a utility class.
 * 
 * @author markr
 *
 */
public class OsmNetworkHandlerHelper {
  
  /** the logger to use */
  public static final Logger LOGGER = Logger.getLogger(OsmNetworkHandlerHelper.class.getCanonicalName());
  
  /** to be able to retain the information on the osm way type to be able to identify the importance of an osm way compared to others we use the osm way type
   * and store it as an input property on the link using this key 
   */
  protected static final String LINK_OSMWAY_TYPE_PROPERTY_KEY = "osm_way_type";

  /** to be able to retain the information on the vertical layer index so we can map it to infrastructure at the same index level we
   *  store it as an input property on the link using this key
   */
  protected static final String LINK_OSM_LAYER_PROPERTY_KEY = "osm_vertical_layer_index";
  
  /** set the OSM way type
   * @param link to set for
   * @param osmWayType to use
   */
  public static void setLinkOsmWayType(Link link, String osmWayType) {
    link.addInputProperty(LINK_OSMWAY_TYPE_PROPERTY_KEY, osmWayType);
  }
  
  /** Collect the OSM way type of the link
   * @param link to collect from
   * @return osm way type, null if not present
   */
  public static String getLinkOsmWayType(Link link) {
    Object value = link.getInputProperty(LINK_OSMWAY_TYPE_PROPERTY_KEY);
    if(value != null) {
      return (String)value;
    }
    return null;
  }

  /** Collect the OSM vertical layer index for the link
   * @param link to collect from
   * @return vertical layer index, defaults to 0 if not explicitly registered
   */
  public static int getLinkVerticalLayerIndex(Link link) {
    Object value = link.getInputProperty(LINK_OSM_LAYER_PROPERTY_KEY);
    return value == null ? 0 : (Integer) value;
  }

  /** Collect the OSM vertical layer index across the given links that occurs most frequenctly
   *
   * @param links to base
   * @return vertical layer index chosen, defaults to 0 if not explicitly registered
   */
  public static int getMostFrequentVerticalLayerIndex(Collection<? extends Link> links) {
    Map<Integer,Long> valueCountPerLayerIndex =
        links.stream().collect(Collectors.groupingBy(l -> getLinkVerticalLayerIndex(l), Collectors.counting()));
    var layerIdWithHighestCount = valueCountPerLayerIndex.entrySet().stream().max((entry1, entry2) -> entry1.getValue() > entry2.getValue() ? 1 : -1).get().getKey();
    return layerIdWithHighestCount;
  }

  /** Set the OSM vertical layer index for the link based on its OSM tags
   * @param link to set index for
   * @param  tags to extract index from, if absent, OSM default of 0 is implicitly assumed
   */
  public static void setLinkVerticalLayerIndex(MacroscopicLink link, Map<String, String> tags) {
    if(!OsmTagUtils.containsAnyKey(tags, OsmTags.LAYER)){
      /* no layer tag, so default applies, which we do not explicitly store */
      return;
    }
    var layerValue = OsmTagUtils.getValueAsInt(tags, OsmTags.LAYER);
    link.addInputProperty(LINK_OSM_LAYER_PROPERTY_KEY, layerValue);
  }


  /** add addition to destination
   * @param addition to add
   * @param destination to add to
   */
  public static void addAllTo(Map<Long, Set<MacroscopicLink>> addition, Map<Long, Set<MacroscopicLink>> destination) {
    addition.forEach( (osmWayId, links) -> {
      destination.putIfAbsent(osmWayId, new HashSet<>());
      destination.get(osmWayId).addAll(links);
    });
  }

}

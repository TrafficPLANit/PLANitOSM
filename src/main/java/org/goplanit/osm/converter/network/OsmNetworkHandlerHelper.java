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


  /** add link entries in addition set to destination set
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

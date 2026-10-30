package org.goplanit.osm.util;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import org.goplanit.network.LayeredNetwork;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.network.OsmNetworkLayerParser;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderData;
import org.goplanit.osm.converter.network.data.OsmNetworkReaderLayerData;
import org.goplanit.osm.converter.network.data.OsmNetworkToZoningReaderData;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;
import org.goplanit.utils.network.layer.physical.Node;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Utilities regarding PLANit network layer modifier functionality with respect to parsing OSM entities for it
 * 
 * @author markr
 *
 */
public class PlanitNetworkLayerModifierUtils {

  private static final Logger LOGGER = Logger.getLogger(PlanitNetworkLayerModifierUtils.class.getCanonicalName());

  /**
   * Defautl behaviour of breaking edges returns in particular form that may not be ideal, this converts it to
   * different mapping using passed in function. A link the function gives no key for is left out, e.g. a link the
   * reader added itself when grouping by OSM way
   *
   * @param localBrokenLinks to change container for
   * @param mapToLinkKeyAsLong mapping, giving null for a link to leave out
   * @return newly mapped links
   */
  public static Map<Long, Set<MacroscopicLink>> convertBrokenLinksByGroupingThemWithCustomKey(
      Map<Long, Pair<MacroscopicLink, MacroscopicLink>> localBrokenLinks,
      Function<MacroscopicLink, Long> mapToLinkKeyAsLong){

    Map<Long, Set<MacroscopicLink>> groupNewLinksByOriginalLinkKey = new TreeMap<>();
    if(localBrokenLinks != null) {
      localBrokenLinks.values().stream().flatMap(pair -> Stream.of(pair.first(), pair.second())).forEach(link -> {
        var key = mapToLinkKeyAsLong.apply(link);
        if(key != null) {
          groupNewLinksByOriginalLinkKey.computeIfAbsent(key, k -> new HashSet<>()).add(link);
        }
      });
    }
    return groupNewLinksByOriginalLinkKey;
  }

  /**
   * whenever we find that internal nodes are used by more than one link OR a node is an extreme node
   * on an existing link but also an internal link on another node, we break the links where this node
   * is internal. the end result is a situations where all nodes used by more than one link are extreme
   * nodes, i.e., start/end nodes.
   * <p>
   * Osm ways with multiple PLANit links associated with them can cause problems because in the handler we only
   * register nodes internal to the original way to link mapping. If a link is broken we adjust the original link
   * and create an additional link causing the original mapping between internal nodes and PLANit link to be
   * potentially incorrect. We require the osmWaysWithMultiplePlanitLinks map to track these changes so that
   * we can always identify which of multiple PLANit links an internal node currently resides on.
   *
   * @param networkLayer the links are on
   * @param layerData of the layer, tracking which links locations are internal to
   * @param thePlanitNode to break links for where it is internal to them
   * @param crs to recompute the lengths of the broken links with
   * @return true when links were broken, false otherwise
   */
  public static boolean breakLinksWithInternalNode(
      MacroscopicNetworkLayer networkLayer, OsmNetworkReaderLayerData layerData, Node thePlanitNode,
      CoordinateReferenceSystem crs) {
    if(layerData.isLocationInternalToAnyLink(thePlanitNode.getPosition())) {
      /* links to break */
      List<MacroscopicLink> linksToBreak = layerData.findPlanitLinksWithInternalLocation(thePlanitNode.getPosition());

      /* break links */
      var brokenLinks = networkLayer.getLayerModifier().breakAt(linksToBreak, thePlanitNode, crs);

      var newOsmWaysWithMultipleLinks = convertBrokenLinksByGroupingThemWithCustomKey(
          brokenLinks,
          PlanitOsmUtils::parseOsmIdOrNull);

      /* update mapping since another osmWayId now has multiple PLANit links and this is needed in the layer
      data to be able to find the correct PLANit links for which osm nodes are internal */
      layerData.updateOsmWaysWithMultiplePlanitLinks(newOsmWaysWithMultipleLinks);

      return true;
    }
    return false;
  }
}

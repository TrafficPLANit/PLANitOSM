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

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

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
   * different mapping using passed in function
   *
   * @param localBrokenLinks to change container for
   * @param mapToLinkKeyAsLong mapping
   * @return newly mapped links
   */
  public static Map<Long, Set<MacroscopicLink>> convertBrokenLinksByGroupingThemWithCustomKey(
      Map<Long, Pair<MacroscopicLink, MacroscopicLink>> localBrokenLinks,
      Function<MacroscopicLink, Long> mapToLinkKeyAsLong){

    Map<Long, Set<MacroscopicLink>> groupNewLinksByOriginalLinkKey = new TreeMap<>();
    if(localBrokenLinks != null) {
      localBrokenLinks.forEach((id, pair) -> {
        // 1. Process the first link of the pair
        var firstLink = pair.first();
        groupNewLinksByOriginalLinkKey
            .computeIfAbsent(mapToLinkKeyAsLong.apply(firstLink), k -> new HashSet<>())
            .add(firstLink);

        // 2. Process the second link of the pair
        var secondLink = pair.second();
        groupNewLinksByOriginalLinkKey
            .computeIfAbsent(mapToLinkKeyAsLong.apply(secondLink), k -> new HashSet<>())
            .add(secondLink);
      });
    }
    return groupNewLinksByOriginalLinkKey;
  }
}

package org.goplanit.osm.util;

import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.goplanit.osm.tags.*;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.Triple;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * functionality regarding parsing of lane information
 *
 * @author markr
 */
public class OsmLaneUtils {

  private static final Logger LOGGER = Logger.getLogger(OsmLaneUtils.class.getCanonicalName());

  /**
   * Attempt to parse string as lane string either as iteger (expected) or fallback check on double changed to
   * int with warning
   *
   * @param osmWay to parse
   * @param laneString to parse
   * @return number of lanes found
   */
  public static Integer parseLaneString(OsmWay osmWay, String laneString) {
    Integer totalLanes = null;
    try {
      totalLanes = Integer.parseInt(laneString);
    }catch(Exception e){
      // could be a floating point, which would be a tagging error, but it happens, so we give it a go
      try {
        double decimalLanes = Double.parseDouble(laneString);
        totalLanes = (int) decimalLanes;
        LOGGER.info(String.format(
            "Tagging error (OSM way: %d) - invalid lanes tag containing fractional value %.2f, replaced with %d",
            osmWay.getId(), decimalLanes, totalLanes));

      }catch (Exception e2){
        // accept not possible
      }
    }
    return totalLanes;
  }

  /**
   * parse the number of lanes on the road-based on provided tags in forward and backward direction (if explicitly set), when not available defaults are used
   *
   * @param osmWay to parse
   * @param tags containing lane information
   * @return forward backwards lanes, null if not defined
   */
  public static Pair<Integer, Integer> extractDirectionalHighwayLanes(OsmWay osmWay, Map<String, String> tags) {
    Integer totalLanes = null;
    Integer lanesForward = null;
    Integer lanesBackward = null;

    if(tags.containsKey(OsmLaneTags.LANES)) {
      totalLanes = parseLaneString(osmWay, tags.get(OsmLaneTags.LANES));
    }
    if(tags.containsKey(OsmLaneTags.LANES_FORWARD)) {
      lanesForward = parseLaneString(osmWay, tags.get(OsmLaneTags.LANES_FORWARD));
    }
    if(tags.containsKey(OsmLaneTags.LANES_BACKWARD)) {
      lanesBackward = parseLaneString(osmWay, tags.get(OsmLaneTags.LANES_BACKWARD));
    }

    /* one way exceptions or implicit directional lanes */
    if(totalLanes!=null && (lanesForward==null || lanesBackward==null)) {
      if(OsmOneWayTags.isOneWay(tags)) {
        boolean isReversedOneWay = OsmOneWayTags.isReversedOneWay(tags);
        if(isReversedOneWay && lanesBackward==null) {
          lanesBackward = totalLanes;
        }else if(!isReversedOneWay && lanesForward==null) {
          lanesForward = totalLanes;
        }else if( (lanesForward==null && lanesBackward==null) && totalLanes%2==0) {
          /* two directions, with equal number of lanes does not require directional tags, simply split in two */
          lanesBackward = totalLanes/2;
          lanesForward = lanesBackward;
        }
      }
    }
    return Pair.of(lanesForward, lanesBackward);
  }

  /**
   * parse the number of lanes on the rail based on provided tags in forward and backward direction (if explicitly set), when not available defaults are used
   *
   * @param osmWay to parse
   * @param tags containing lane information
   * @return forward backwards lanes, null if not defined
   */
  public static Pair<Integer, Integer> extractDirectionalRailwayLanes(OsmWay osmWay, Map<String, String> tags) {
    Integer lanesForward = null;
    Integer lanesBackward = null;
    if(tags.containsKey(OsmRailFeatureTags.TRACKS)) {
      /* assumption is that same rail is used in both directions */
      lanesForward = parseLaneString(osmWay, tags.get(OsmRailFeatureTags.TRACKS));;
      lanesBackward = lanesForward;
    }
    return Pair.of(lanesForward, lanesBackward);
  }

  /** Waterways have no lanes, so we cannot obtain them from tagging, instead directly collect the default in
   * apply to both directions
   *
   * @param osmWay to parse
   * @param tags to use
   * @param waterwayDefaultSupplier default num lanes for waterways to use
   * @return lanes for waterways (Default always as long as tags are waterway supporting)
   */
  public static Pair<Integer, Integer> extractDirectionalWaterwayLanes(
      OsmWay osmWay, Map<String, String> tags, Supplier<Integer> waterwayDefaultSupplier) {
    var defaultLanes = waterwayDefaultSupplier.get();
    return Pair.of(defaultLanes,defaultLanes);
  }

  /** Determine lanes based on the provided tags. First determine what type of way we are dealing with and then
   * delegate to the appropriate lane identification sub method.
   *
   * @param osmWay to parse
   * @param tags to use
   * @param waterwayDefaultLanesFunction default num lanes for waterways to use for a given waterway key tag
   * @return OSM way key used to determine the number of lanes and the lanes (by direction) for tags given
   */
  public static Pair<String, Pair<Integer, Integer>> extractDirectionalLanes(
      OsmWay osmWay, Map<String, String> tags, Function<String, Integer> waterwayDefaultLanesFunction) {

    Pair<Integer, Integer> result = null;
    String osmWayKey = null;

    /* collect total and direction specific road-based lane information */
    if(tags.containsKey(OsmHighwayTags.getHighwayKeyTag())) {
      osmWayKey = OsmHighwayTags.getHighwayKeyTag();
      result = OsmLaneUtils.extractDirectionalHighwayLanes(osmWay, tags);

      /* convert number of tracks to lanes */
    }else if(tags.containsKey(OsmRailwayTags.getRailwayKeyTag())) {
      osmWayKey = OsmRailwayTags.getRailwayKeyTag();
      result =  OsmLaneUtils.extractDirectionalRailwayLanes(osmWay, tags);
    }else if(OsmWaterwayTags.isWaterBasedWay(tags)) {
      osmWayKey = OsmWaterwayTags.getUsedKeyTag(tags);
      result =  OsmLaneUtils.extractDirectionalWaterwayLanes(
          osmWay, tags, () -> waterwayDefaultLanesFunction.apply(OsmWaterwayTags.getUsedKeyTag(tags)));
    }else{
      LOGGER.warning(String.format("Unknown OSM way key tag for OSM way (%d) when extracting lanes", osmWay.getId()));
    }

    return Pair.of(osmWayKey, result);
  }

}

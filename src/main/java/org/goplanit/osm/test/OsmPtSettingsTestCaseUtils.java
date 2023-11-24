package org.goplanit.osm.test;

import de.topobyte.osm4j.core.model.iface.EntityType;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.osm.tags.OsmRailModeTags;
import org.goplanit.utils.misc.Triple;

/**
 * Dedicated class to minimise warnings that have been verified as ignorable and switch them off accordingly
 */
public class OsmPtSettingsTestCaseUtils {


  /**
   * When applied to the 2023 PBF it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void sydney2023MinimiseVerifiedWarnings(OsmPublicTransportReaderSettings settings) {

    /* stop area resides on edge of bounding box, it references entries outside bounding box yielding (valid but uncorrectable) warnings */
    settings.suppressOsmRelationStopAreaLogging(
        9697194L,
        10230850L,
        9724271L,
        9769474L /* new Martin Place metro station, not finished yet so yields warnings */
    );

    settings.excludeOsmNodesById(
        5425695836L       /* ferry stop position without a ferry terminal nor ptv2 platform, tagging error */,
        4928549541L                /* ferry platform without a ferry terminal nor disconnected from ferry routes, tagging error */
    );

    /* layer inconsistency between link of stop position and platform - tagging error  - remove log statement from output by explicit mapping */
    settings.overwriteWaitingAreaOfStopLocations(
        Triple.of(6553012228L, EntityType.Way, 142553711L)
    );

    /* platform has no mode support but PLANit correctly inferred stop location - tagging incomplete = suppress logging by explicit allocation mapping */
    settings.overwriteWaitingAreaOfStopLocations(
        Triple.of(6585559395L, EntityType.Way, 701216857L),
        Triple.of(6585559394L, EntityType.Way, 701216858L)
    );

  }

  /**
   * When applied to the Melbourne PBF it suppresses and addresses warnings deemed issues that are NOT to be fixed in the parser
   * but would detract from assessing the logs.
   *
   * @param settings to apply to
   */
  public static void melbourneMinimiseVerifiedWarnings(OsmPublicTransportReaderSettings settings) {

    /* layer inconsistency between link of stop position and platform - tagging error  - remove log statement from output by explicit mapping */
    settings.overwriteWaitingAreaOfStopLocations(
        Triple.of(9892925658L, EntityType.Way, 292444942L),
        Triple.of(9562190883L, EntityType.Way, 292446009L),
        Triple.of(9562190884L, EntityType.Way, 292446009L),
        Triple.of(2960408863L, EntityType.Way, 113689450L),
        Triple.of(9892925641L, EntityType.Way, 113689450L),
        Triple.of(294624270L, EntityType.Way, 113689449L),
        Triple.of(294623996L, EntityType.Way, 113689449L),
        Triple.of(9553987077L, EntityType.Way, 46330984L),
        Triple.of(9892925648L, EntityType.Way, 292492436L),
        Triple.of(9892925647L, EntityType.Way, 292492436L),
        Triple.of(4061053433L, EntityType.Way, 403795659L),
        Triple.of(4457663103L, EntityType.Way, 45683761L),
        Triple.of(8878473234L, EntityType.Way, 403795657L),
        Triple.of(4061053431L, EntityType.Way, 403795656L));

    /* salvaged stop position info - incomplete tag - correctly inferred by default - suppress logging */
    settings.overwriteWaitingAreaOfStopLocations(
        Triple.of(3967630092L, EntityType.Way, 185447226L),
        Triple.of(579489285L, EntityType.Way, 185447226L)
    );

    /* platform not in relation while stop position is - incomplete area error - correctly inferred by adding - suppress logging statement by explicit mapping */
    settings.overwriteWaitingAreaOfStopLocations(Triple.of(2207661036L, EntityType.Node, 9642392222L));

    /* Multiple platforms modelled for the same stop location - tagging error - correctly inferred by adding - suppress logging statement by explicit mapping */
    settings.overwriteWaitingAreaOfStopLocations(Triple.of(767584117L, EntityType.Node, 3937113689L));

    /* The platform to choose is missing ref information, stop location is attributed to other platform with same ref - tagging error - incorrectly inferred - suppress logging statement by explicit re-mapping */
    settings.overwriteWaitingAreaOfStopLocations(Triple.of(2271530408L, EntityType.Way, 661972253L));

    /* disused tram stop and stop location - tagging error - suppressing logging by exclusion */
    settings.excludeOsmNodesById(2189158040L, 3945796055L, 2189158011L, 2189158004L, 1281064369L, 4520380558L,
        4520380560L);

    /* dangling stop position (in currently used PBF) - tagging error - suppress warning by exclusion */
    settings.excludeOsmNodesById(3945796021L, 2189158004L, 4092065304L, 3954625856L);

    /* dangling bus stops (in currently used PBF) - around central station bus station - not connected to road - remove */
    settings.excludeOsmNodesById(5288512523L, 7246258953L, 7246258954L, 7246258955L, 7246258956L, 7246258957L);

    /* dangling tram platforms (in currently used PBF) - tagging error - suppress warning by exclusion */
    settings.excludeOsmNodesById(4092065307L);

    /* dangling ferry terminals - exclude to suppress logging */
    settings.excludeOsmNodesById(7257137819L, 7257137820L, 7257137821L, 7257137822L, 6176046755L, 6176046758L, 6176046760L);

    /* platforms without any mode information (in currently used PBF) - tagging error - suppress warning by exclusion */
    settings.excludeOsmNodesById(9449523499L, 9449523502L, 9794049217L, 9794049238L, 9794049245L, 9794049261L);

    /* Railway stations under construction */
    settings.excludeOsmWaysById(485842787L);

    /* tram platform that has internal node used as pole for stop location, to avoid unused duplicate remove those without ref to stop location */
    settings.excludeOsmWaysById(658631129L, 777609495L);

    /* ferry terminal that is not a location of boarding/alighting - tagging error - suppress warning by excluding */
    settings.excludeOsmWaysById(592081363L, 991682083L);

    /* platforms without any mode information (in currently used PBF) - tagging error - suppress warning by exclusion */
    settings.excludeOsmWaysById(135953167L);

    /* platforms without proper mode attribution - tagging error - suppress warning by ataching modes manually */
    settings.overwriteWaitingAreaModeAccess(208506996L, EntityType.Way, OsmRailModeTags.LIGHT_RAIL, OsmRailModeTags.TRAM);
    settings.overwriteWaitingAreaModeAccess(208507089L, EntityType.Way, OsmRailModeTags.LIGHT_RAIL, OsmRailModeTags.TRAM);
    settings.overwriteWaitingAreaModeAccess(970767685L, EntityType.Way, OsmRailModeTags.LIGHT_RAIL, OsmRailModeTags.TRAM);

    /* stop location link identified as ideal by PLANit lies too far from waiting area and triggers warning + discard -
     * override that this is correct - potential for improved tagging closer to OSM way */
    settings.overwriteWaitingAreaNominatedOsmWayForStopLocation(4273015511L, EntityType.Node, 428167259L);

  }

}

package org.goplanit.osm.converter;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.goplanit.osm.tags.OsmBoundaryTags;
import org.goplanit.osm.tags.OsmMultiPolygonTags;
import org.goplanit.osm.tags.OsmTags;
import org.goplanit.osm.util.OsmRelationUtils;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.osm.util.OsmWayUtils;
import org.goplanit.utils.geo.PlanitJtsUtils;
import org.goplanit.utils.misc.Pair;
import org.locationtech.jts.geom.Coordinate;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builder for OSM bounding boundaries. This class has a number of methods and internal state to be able to construct
 * an OsmBoundary instance with a polygon constructed from OSM ways based on the description provided in an OSMBoundary
 * without a polygon present but with the naming and other related information required to extract the polygon from
 * an OSM file. the builder can track this information and finally build a new OsmBoundary instance with the required information
 *
 * @author markr
 */
public class OsmBoundaryManager {

  private static final Logger LOGGER = Logger.getLogger(OsmBoundaryManager.class.getCanonicalName());

  /** original boundary with only description present, possibly without polygon (input) */
  private final OsmBoundary originalBoundary;

  /** final boundary including result polygon for boungin (final result) */
  private OsmBoundary finalBoundaryWithPolygon;

  /**
   * OSMWay ids that make up the outer polygon of the to be constructed osmBoundingArea (if any), contiguous nature of
   * these can be reconstructed by ordering them by their integer value which is incrementally created upon adding entries.
   */
  private final Map<Long, Pair<Integer, OsmWay>> osmBoundaryOsmWayTracker = new HashMap<>();

  /**
   * Register portions of the named OSM boundary to construct a single coherent polygon during regular pre-processing
   * stage but before main processing (as main processing relies on the boundary to be finalised to do the actual parsing)
   *
   * @param osmWayId to register as part of the future OSM boundary
   */
  private void registerBoundaryOsmWayOuterRoleSection(long osmWayId) {
    osmBoundaryOsmWayTracker.put(osmWayId,Pair.of(osmBoundaryOsmWayTracker.size(), null /* to be added in next stage */));
  }

  /**
   * Verify if OSM way is part of bounding area boundary
   *
   * @param osmWayId to verify
   * @return true when registered, false otherwise
   */
  private boolean isRegisteredBoundaryOsmWay(long osmWayId) {
    return osmBoundaryOsmWayTracker.containsKey(osmWayId);
  }

  /**
   * Register actual OsmWay  portion of the named OSM boundary to construct a single coherent polygon during regular pre-processing
   * stage but before main processing (as main processing relies on the boundary to be finalised to do the actual parsing).
   * <p> Requires the osmWayId to have been registered already in stage 1 of preprocessing </p>
   *
   * @param osmWay to update registered entry with as part of the future OSM boundary
   */
  private void updateBoundaryRegistrationWithOsmWay(OsmWay osmWay) {
    if(!isRegisteredBoundaryOsmWay(osmWay.getId())){
      LOGGER.severe("Should not update boundary with OSM way when it has not been identified as being part of boundary during " +
          "initial stage of preprocessing, ignored");
    }
    var valueWithoutOsmWay = osmBoundaryOsmWayTracker.get(osmWay.getId());
    osmBoundaryOsmWayTracker.put(osmWay.getId(),Pair.of(valueWithoutOsmWay.first(), osmWay /* update now available */));
  }

  /**
   * Check if any OSM ways have been registered for constructing the OSM bounding boundary polygon from
   *
   * @return true if present, false otherwise
   */
  private boolean hasRegisteredBoundaryOsmWay() {
    return !osmBoundaryOsmWayTracker.isEmpty();
  }

  /**
   * Extract an ordered list of the registered OSM ways for the bounding boundary in the order they were registered in
   *
   * @return ordered list of OSM ways
   */
  private List<OsmWay> getRegisteredBoundaryOsmWaysInOrder() {
    if(!hasRegisteredBoundaryOsmWay()){
      LOGGER.severe("Unable to constructed sorted list of OSM ways as none have been registered");
      return List.of();
    }
    if(osmBoundaryOsmWayTracker.values().iterator().next().second() == null){
      LOGGER.severe("Registered OSM ways do not have an OSM way object attached to them, unable to constructed sorted list of OSM ways");
      return List.of();
    }
    // sort by integer index and then collect the OSM ways as list
    return osmBoundaryOsmWayTracker.entrySet().stream().sorted(
        Comparator.comparingInt(e -> e.getValue().first())).map(e -> e.getValue().second()).collect(Collectors.toList());
  }

  /**
   * Constructor
   * @param originalBoundary as defined by user
   */
  public OsmBoundaryManager(OsmBoundary originalBoundary){
    this.originalBoundary = originalBoundary;

    /* when original already contains polygon then we are already done from the start and we copy it across
    * (no polygon extraction needed */
    if(originalBoundary != null && originalBoundary.hasBoundingPolygon()) {
      finalBoundaryWithPolygon = originalBoundary.deepClone();
    }
  }

  /**
   * Verify if relation defines a boundary area that was user configured to be the bounding area. If so
   * register its members reflecting the outer boundary for processing in the regular pre-processing stage
   *
   * @param osmRelation to check
   */
  public void stepOneIdentifyAndRegisterBoundingAreaRelationMembers(OsmRelation osmRelation) {
    /* only keep going when boundary is active and based on name */
    if(originalBoundary==null || !originalBoundary.hasBoundaryName()){
      return;
    }

    /* check for boundary tags on relation */
    var tags = OsmModelUtil.getTagsAsMap(osmRelation);
    if(!tags.containsKey(OsmBoundaryTags.getBoundaryKeyTag())){
      return;
    }

    if(!OsmTagUtils.matchesAnyValueTag(
        tags.get(OsmBoundaryTags.getBoundaryKeyTag()), OsmBoundaryTags.getBoundaryValues())){
      return;
    }

    /* boundary compatible relation - now check against settings  */
    if(OsmTagUtils.keyMatchesAnyValueTag(tags, OsmTags.NAME, originalBoundary.getBoundaryName())){

      // found, no see if more specific checks are required based on type and/or admin_level. Below flags switch to
      // true if the item is not used or it is used AND it is matched
      boolean boundaryTypeMatch =
          !originalBoundary.hasBoundaryType() || OsmBoundaryTags.hasBoundaryValueTag(tags, originalBoundary.getBoundaryType());
      boolean adminLevelMatch = !originalBoundary.hasBoundaryAdminLevel() ||
          OsmTagUtils.keyMatchesAnyValueTag(tags, OsmBoundaryTags.ADMIN_LEVEL, originalBoundary.getBoundaryAdminLevel());
      boolean boundaryAdministrativeMatch = !originalBoundary.getBoundaryType().equals(OsmBoundaryTags.ADMINISTRATIVE) ||
          originalBoundary.hasBoundaryAdminLevel() &&
              OsmTagUtils.keyMatchesAnyValueTag(tags, OsmBoundaryTags.ADMIN_LEVEL, originalBoundary.getBoundaryAdminLevel());

      if(boundaryTypeMatch && adminLevelMatch && boundaryAdministrativeMatch){

        LOGGER.info(String.format(
            "Found OSMRelation for bounding boundary: %s OsmRelationId:%d", originalBoundary.getBoundaryName(), osmRelation.getId()));

        // full match found -> register all members with correct roles to extract bounding area polygon from in next stage
        for(int memberIndex = 0; memberIndex < osmRelation.getNumberOfMembers(); ++memberIndex){
          var currMember = osmRelation.getMember(memberIndex);
          if(OsmRelationUtils.isMemberOfTypeAndRole(currMember, EntityType.Way, OsmMultiPolygonTags.OUTER_ROLE)){
            registerBoundaryOsmWayOuterRoleSection(currMember.getId());
          }
        }

      }
    }
  }

  /**
   * For all members identified in step one, we now attach the OSMway proper if the provided OSM way matches one
   * of the registered portions of the boundary
   *
   * @param osmWay to check and/or attach
   * @return true if matched, false otherwise
   */
  public boolean stepTwoAttachBoundaryRelationMemberOsmWays(OsmWay osmWay) {
    if(isRegisteredBoundaryOsmWay(osmWay.getId())){
      updateBoundaryRegistrationWithOsmWay(osmWay);
      return true;
    }
    return false;
  }

  /**
   * Construct bounding boundary based on registered OSM ways. It is assumed these OSM ways are contiguous based on the
   * numbering assigned to them. It is assumed this forms a complete polygon (will be checked) and it is assumed this
   * adheres to the bounding boundary configuration (without a polygon) configured by the user in the network settings.
   * The result is a new OsmBoundingBoundary that will be used in the actual processing
   *
   * @param osmNodes to use
   */
  public void stepThreeCompleteConstructionBoundingBoundary(Map<Long, OsmNode> osmNodes) {
    if(!isConfigured()){
      // no boundary configured, no need to complete
      return;
    }

    List<OsmWay> boundaryOsmWays = getRegisteredBoundaryOsmWaysInOrder();
    Deque<Coordinate> contiguousBoundaryCoords = new LinkedList<>() {
    };
    for(var osmWay : boundaryOsmWays){
      var coordArray = OsmWayUtils.createCoordinateArrayNoThrow(osmWay,osmNodes);
      if(!contiguousBoundaryCoords.isEmpty()){
        boolean success = PlanitJtsUtils.addCoordsContiguous(contiguousBoundaryCoords, coordArray);
        if(!success){
          LOGGER.severe(String.format("Bounding boundaries OSM way %d supposed to be contiguous with adjacent one, " +
              "but this was not found to be the case, this shouldn't happen, ignore", osmWay.getId()));
          contiguousBoundaryCoords.clear();
          break;
        }
      }else{
        contiguousBoundaryCoords.addAll(Arrays.asList(coordArray));
      }
    }
    if(contiguousBoundaryCoords.isEmpty()){
      LOGGER.severe("Unable to construct bounding boundary polygon");
      return;
    }

    // now convert to polygon
    var boundingBoundaryPolygon = PlanitJtsUtils.createPolygon(
        contiguousBoundaryCoords.toArray(new Coordinate[0]));
    if(boundingBoundaryPolygon == null){
      LOGGER.severe("Unable to construct bounding boundary polygon from OSM way coordinates listed under bounding boundary name");
      return;
    }

    // construct final OsmBoundary copying original + adding in polygon
    this.finalBoundaryWithPolygon =
        OsmBoundary.of(
            originalBoundary.getBoundaryName(),
            originalBoundary.getBoundaryType(),
            originalBoundary.getBoundaryAdminLevel(),
            boundingBoundaryPolygon);
  }

  public void overrideBoundingArea(OsmBoundary override){
    if(override == null || !override.hasBoundingPolygon()){
      LOGGER.warning("Unable to override bounding area on boundary manager, only allowed when it is fully configured and" +
          "contains bounding polygon.");
    }
    this.finalBoundaryWithPolygon = override;
  }

  /**
   * Check if bounding area is configured (may not yet be ready to be used)
   *
   * @return true if user configured a boundary initially (complete or not), false otherwise
   */
  public boolean isConfigured(){
    return originalBoundary != null;
  }

  /**
   * Check if bounding area is ready to be used
   *
   * @return true if final boundary with polygon is ready and available, false otherwise
   */
  public boolean isComplete(){
    return finalBoundaryWithPolygon != null;
  }

  /**
   * Provide the bounding area that was constructed, or null if not available or not ready
   *
   * @return OSM Bounding area
   */
  public OsmBoundary getCompleteBoundingArea(){
    if(isConfigured() && isComplete()){
      return finalBoundaryWithPolygon;
    }else{
      LOGGER.severe("Final boundary (with polygon) not available, either original boundary not configured, or final boundary not yet completed");
    }
    return null;
  }

  /**
   * Check if any OSMRelation members have been registered for OSMWay extraction in the next phase
   *
   * @return true if present, false otherwise
   */
  public boolean hasRegisteredRelationMembers(){
    return !this.osmBoundaryOsmWayTracker.isEmpty();
  }

}

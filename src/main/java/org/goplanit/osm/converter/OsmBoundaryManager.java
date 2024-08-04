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
   * Track OSM boundary nodes
   */
  private final OsmNodeData osmNodeData = new OsmNodeData();

  /**
   * Preregister the OSM way's nodes
   *
   * @param osmWay to preregister nodes for
   */
  private void preregisterOsmWayNodes(final OsmWay osmWay) {
    for(int index=0;index<osmWay.getNumberOfNodes();++index) {
      osmNodeData.preregisterEligibleOsmNode(osmWay.getNodeId(index));
    }
  }

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
  private void updateBoundaryRegistrationWithOsmWayAndNodes(OsmWay osmWay) {
    if(!isRegisteredBoundaryOsmWay(osmWay.getId())){
      LOGGER.severe("Should not update boundary with OSM way when it has not been identified as being part of boundary during " +
          "initial stage of preprocessing, ignored");
    }

    // way registration
    var valueWithoutOsmWay = osmBoundaryOsmWayTracker.get(osmWay.getId());
    osmBoundaryOsmWayTracker.put(osmWay.getId(),Pair.of(valueWithoutOsmWay.first(), osmWay /* update now available */));

    // nodes registration
    preregisterOsmWayNodes(osmWay);
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
   * Count number of currently registered OSM ways for boundary
   * @return num found
   */
  private int getNumberOfPreregisteredBoundaryOsmWays() {
    if(!hasRegisteredBoundaryOsmWay()){
      return 0;
    }
    return osmBoundaryOsmWayTracker.size();
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
      boolean boundaryAdministrativeMatch =
          !originalBoundary.hasBoundaryType() || !originalBoundary.getBoundaryType().equals(OsmBoundaryTags.ADMINISTRATIVE) ||
          originalBoundary.hasBoundaryAdminLevel() &&
              OsmTagUtils.keyMatchesAnyValueTag(tags, OsmBoundaryTags.ADMIN_LEVEL, originalBoundary.getBoundaryAdminLevel());

      if(boundaryTypeMatch && adminLevelMatch && boundaryAdministrativeMatch){

        LOGGER.info(String.format(
            "Boundary identification: Found OSMRelation \"%s\" OsmRelationId:%d", originalBoundary.getBoundaryName(), osmRelation.getId()));

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
  public boolean stepTwoAttachBoundaryOsmWaysAndPreregisterItsNodes(OsmWay osmWay) {
    if(isRegisteredBoundaryOsmWay(osmWay.getId())){
      updateBoundaryRegistrationWithOsmWayAndNodes(osmWay);
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
   */
  public void stepThreeCompleteConstructionBoundingBoundary() {
    if(!isConfigured()){
      // no boundary configured, no need to complete
      return;
    }
    if(isComplete()){
      // already done, no need to continue
      return;
    }

    List<OsmWay> boundaryOsmWays = getRegisteredBoundaryOsmWaysInOrder();
    LinkedList<Coordinate> contiguousBoundaryCoords = new LinkedList<>();

    for(var osmWay : boundaryOsmWays){
      var coordArray = OsmWayUtils.createCoordinateArrayNoThrow(osmWay, osmNodeData.getRegisteredOsmNodes());
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

    // close the contiguous coordinates by adding the last coordinate as the first if not done so already
    contiguousBoundaryCoords = PlanitJtsUtils.makeClosed2D(contiguousBoundaryCoords);

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

  /**
   * Verify if OSM node is a preregistered boundary node
   *
   * @param osmNodeId to verify
   * @return ture when preregistered false otherwise
   */
  public boolean isPreregisteredBoundaryOsmNode(long osmNodeId) {
    return this.osmNodeData.containsPreregisteredOsmNode(osmNodeId);
  }

  /**
   * Register OSM node as boundary node
   *
   * @param osmNode to register
   */
  public void registerBoundaryOsmNode(OsmNode osmNode) {
    osmNodeData.registerEligibleOsmNode(osmNode);
  }

  /**
   * Log the OSM boundary relation's members (OSM ways) that make up the boundary
   */
  public void logStepOneRegisteredRelationMemberStats() {
    if(isConfigured() && this.originalBoundary.hasBoundaryName()){
      boolean success = hasRegisteredRelationMembers();
      int numOsmWays = getNumberOfPreregisteredBoundaryOsmWays();
      LOGGER.info((success ?
          String.format("Boundary identification: registered %d relation member OSM ways for boundary: ",numOsmWays) :
          "Unable to identify bounding area for: ") + "\"" + this.originalBoundary.getBoundaryName() + "\"");
    }
  }

  /**
   * Log the OSM bounding boundary OSM nodes used to create the final boundary
   */
  public void logStepThreeCompletedBoundingBoundaryStats() {
    if(isComplete() && this.finalBoundaryWithPolygon.hasBoundaryName()){
      LOGGER.info(String.format(
          "Boundary identification: \"%s\" finalised using total of %d OSM nodes", finalBoundaryWithPolygon.getBoundaryName(), osmNodeData.getRegisteredOsmNodes().size()));
    }
  }

  /**
   * Free all temporary resources used during construction of bounding boundary after completion
   */
  public void freeResourcesAfterCompletion() {
    // free up temporary registered OSM entities to free up space
    this.osmNodeData.reset();
    osmBoundaryOsmWayTracker.clear();
  }
}

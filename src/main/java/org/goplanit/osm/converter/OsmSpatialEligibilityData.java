package org.goplanit.osm.converter;

import de.topobyte.osm4j.core.model.iface.OsmWay;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manages tracking of spatial eligibility of OSM entities based on, for example, boundary that is applied (if any)
 */
public class OsmSpatialEligibilityData {

  private static final Logger LOGGER = Logger.getLogger(OsmSpatialEligibilityData.class.getCanonicalName());

  /* temporary storage of tracking eligible osmWays by id (based on whether they fall partially within boundary of
  parsing */
  private final Set<Long> spatiallyEligibleOsmWays = new HashSet<>();

  /* temporary storage of tracking eligible osmNodes by id based on whether they fall partially within boundary of
  parsing*/
  private final Set<Long> spatiallyEligibleOsmNodes = new HashSet<>();

  /* temporary storage of tracking eligible osmRelations by id based on whether they fall partially within boundary*/
  private final Set<Long> spatiallyEligibleOsmRelations = new HashSet<>();

  /* temporary storage of tracking eligible osmWays by instance for special cases that do not qualify directly spatially
   * but for good reason should still be auto-included if meeting certain criteria. This takes multiple rounds of parsing
   * before we can decide, hence this tracker. If deemed eligible they will be removed from here and included in the
   * #spatiallyEligibleOsmWays and #spatiallyEligibleOsmNodes once verified
   */
  private final Set<Long> potentiallySpatiallyEligibleOsmWaysSpecialCases = new HashSet<>();

  private final Set<Long> potentiallySpatiallyEligibleOsmNodesSpecialCases = new HashSet<>();


  public boolean isOsmWaySpatiallyEligible(long osmWayId){
    return spatiallyEligibleOsmWays.contains(osmWayId);
  }

  public void markOsmWaySpatiallyEligible(long osmWayId){
    spatiallyEligibleOsmWays.add(osmWayId);
  }

  public boolean isOsmNodeSpatiallyEligible(long osmNodeId){
    return spatiallyEligibleOsmNodes.contains(osmNodeId);
  }

  public void markOsmNodeSpatiallyEligible(long osmNodeId){
    spatiallyEligibleOsmNodes.add(osmNodeId);
  }

  public void markOsmRelationSpatiallyEligible(long osmRelationId) {
    spatiallyEligibleOsmRelations.add(osmRelationId);
  }

  public boolean isOsmRelationSpatiallyEligible(long osmRelationId) {
    return spatiallyEligibleOsmRelations.contains(osmRelationId);
  }

  public int countSpatiallyEligibleNodes(){
    return spatiallyEligibleOsmNodes.size();
  }

  public int countSpatiallyEligibleWays(){
    return spatiallyEligibleOsmWays.size();
  }

  public int countSpatiallyEligibleRelations(){
    return spatiallyEligibleOsmRelations.size();
  }

  /**
   * Register OSM way as spatially eligible if it has at least one spatially eligible node
   *
   * @param osmWay to check
   * @return true when spatially eligible node found and OSM way marked as eligible
   */
  public boolean markOsmWaySpatiallyEligibleIfHasSpatiallyEligibleNode(OsmWay osmWay) {
    for(int index = 0; index < osmWay.getNumberOfNodes(); ++index){
      if(isOsmNodeSpatiallyEligible(osmWay.getNodeId(index))){
        markOsmWaySpatiallyEligible(osmWay.getId());
        return true;
      }
    }
    return false;
  }

  public void markOsmWayAndNodesPotentiallySpatiallyEligibleAsSpecialCase(OsmWay osmWay) {
    potentiallySpatiallyEligibleOsmWaysSpecialCases.add(osmWay.getId());
    for(int i=0; i<osmWay.getNumberOfNodes();i++) {
      potentiallySpatiallyEligibleOsmNodesSpecialCases.add(osmWay.getNodeId(i));
    }
  }

  public boolean isOsmWayPotentiallySpatiallyEligibleAsSpecialCase(long osmWayId){
    return potentiallySpatiallyEligibleOsmWaysSpecialCases.contains(osmWayId);
  }

  public boolean isOsmNodePartOfPotentiallySpatiallyEligibleWayAsSpecialCase(long osmNodeId){
    return potentiallySpatiallyEligibleOsmNodesSpecialCases.contains(osmNodeId);
  }

  public void reset(){
    spatiallyEligibleOsmWays.clear();
    spatiallyEligibleOsmNodes.clear();
    spatiallyEligibleOsmRelations.clear();
    potentiallySpatiallyEligibleOsmWaysSpecialCases.clear();
  }

}

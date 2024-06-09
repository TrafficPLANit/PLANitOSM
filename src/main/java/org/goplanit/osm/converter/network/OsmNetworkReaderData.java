package org.goplanit.osm.converter.network;

import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.converter.OsmSpatialEligibilityData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;

import java.util.*;
import java.util.logging.Logger;

/**
 * Data specifically required in the network reader while parsing OSM data
 * TODO: we should split this by processing step and stage so it is clear what data is populated when
 *
 * @author markr
 *
 */
public class OsmNetworkReaderData {
  
  /** the logger  */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderData.class.getCanonicalName());

  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays =new HashMap<>();

  /** the osmBoundary used during parsing.
   */
  private OsmBoundary osmBoundingArea = null;

  /**
   * Track OSM nodes to retain in memory during network parsing, which might or might not end up being used to construct links
   * or other entities
   */
  private final OsmNodeData osmNodeData = new OsmNodeData();

  /** Track OSM ways that are deemed eligible for parsing (after pre-processing). This is needed because we can't rely on nodes
   * being available as the way to do this since nodes may be shared between OSM ways and while on of the shared ways is
   * eligible another might not be. Hence the separate eligibility tracking
   */
  private final Set<Long> spatialInfrastructureEligibleOsmWays = new HashSet<>();

  /** track spatial eligibility of OSM entities, for example based on boundary used (if any) */
  private final OsmSpatialEligibilityData osmSpatialEligibilityData = new OsmSpatialEligibilityData();

  /** Track OSM ways that have been processed and identified as being unavailable/not used. This has been communicated if needed
   *  to users, so any subsequent dependencies on this OSM way can be safely ignored without issuing further warnings
   */
  private final Set<Long> discardedOsmWays = new HashSet<>();

  
  /** track layer specific information and handler to delegate processing the parts of osm ways assigned to a layer */
  private final Map<MacroscopicNetworkLayer, OsmNetworkLayerParser> osmLayerParsers = new HashMap<>();
  
  /** the distance that qualifies as being near to the network bounding area. USed to be used with
   * cruder version of bounding area when this was tracked as rectagnular gounding box and not a polygon */
  @Deprecated
  public static final double BOUNDINGAREA_NEARNESS_DISTANCE_METERS = 200;
  
  /**
   * initialise for each layer
   * 
   * @param network to use
   * @param settings to use
   * @param geoUtils to use
   */
  protected void initialiseLayerParsers(PlanitOsmNetwork network, OsmNetworkReaderSettings settings, PlanitJtsCrsUtils geoUtils) {
    /* for each layer initialise a handler */
    for(MacroscopicNetworkLayer macroNetworkLayer : network.getTransportLayers()) {
      OsmNetworkLayerParser layerHandler = new OsmNetworkLayerParser(macroNetworkLayer, this, settings, geoUtils);
      osmLayerParsers.put(macroNetworkLayer, layerHandler);
    }
  }

  /**
   * reset
   */
  public void reset() {
    clearOsmCircularWays();    
    osmNodeData.reset();
    
    /* reset layer handlers as well */
    osmLayerParsers.forEach( (layer, handler) -> handler.reset());
    osmLayerParsers.clear();

    osmBoundingArea = null;
    spatialInfrastructureEligibleOsmWays.clear();
    osmSpatialEligibilityData.reset();
  }  

  /**
   * Access to the bounding area to apply (if any). Will always have a polygon
   * to base the bounding area of as the naming has already been handled and converted into a polygon
   * during preprocessing
   *
   * @return bounding area
   */
  public OsmBoundary getBoundingAreaWithPolygon(){
    return this.osmBoundingArea;
  }

  /**
   * Set the bounding area. It is expected a polygon is available. If not an exception is thrown
   * @param osmBoundary to use
   */
  public void setBoundingAreaWithPolygon(OsmBoundary osmBoundary){
    if(!osmBoundary.hasBoundingPolygon()){
      throw new PlanItRunTimeException("Setting OSM bounding area for network without polygon, this should not happen");
    }
    this.osmBoundingArea = osmBoundary;
  }

  /**
   * Access to OSM node data
   * @return OSM node data
   */
  public OsmNodeData getOsmNodeData(){
  return osmNodeData;
  }

  /**
   * Access to OSM spatial eligibility data
   *
   * @return OSM boundary data
   */
  public OsmSpatialEligibilityData getOsmSpatialEligibilityData(){
    return osmSpatialEligibilityData;
  }

  /** collect the identified circular ways (unmodifiable)
   * 
   * @return osm circular ways
   */
  public Map<Long, OsmWay> getOsmCircularWays() {
    return Collections.unmodifiableMap(osmCircularWays);
  }

  /** add a circular way
   * @param osmWay to add
   */
  public void addOsmCircularWay(OsmWay osmWay) {
    osmCircularWays.put(osmWay.getId(), osmWay);
  }

  /**
   * remove all registered osm circular ways
   */
  public void clearOsmCircularWays() {
    osmCircularWays.clear();
  }

  /**
   * Register an OSM way as processed and identified as being unavailable. Any subsequent dependencies on this OSM way
   * can be safely ignored without issuing further warnings
   *
   * @param osmWayId to register
   */
  public void registerProcessedOsmWayAsUnavailable(long osmWayId){
    discardedOsmWays.add(osmWayId);
  }

  /**
   * Verify if an OSM way is processed but identified as unavailable. Any subsequent dependencies on this OSM way
   * can be safely ignored without issuing further warnings
   *
   * @param osmWayId to verify
   * @return true when processed and unavailable, false otherwise
   */
  public boolean isOsmWayProcessedAndUnavailable(long osmWayId){
    return discardedOsmWays.contains(osmWayId);
  }
  
  /** provide reference to a layer parser
   * 
   * @param networkLayer to collect parser for
   * @return layerParser, null if not present
   */
  public final OsmNetworkLayerParser getLayerParser(MacroscopicNetworkLayerImpl networkLayer) {
    return this.osmLayerParsers.get(networkLayer);
  }   
  
  /** provide reference to the used layer parsers for each of the identified layers
   * 
   * @return layerParsers used
   */
  public final Map<MacroscopicNetworkLayer, OsmNetworkLayerParser> getLayerParsers() {
    return this.osmLayerParsers;
  }

  /** check if bounding area is available
   *
   * @return true if present, false otherwise
   */
   public boolean hasBoundingArea(){
     return osmBoundingArea != null;
   }

  /** get the bounding area
   *
   * @return bounding area
   */
  public OsmBoundary getBoundingArea(){
    return osmBoundingArea;
  }

  /**
   * Set the bounding area to use
   *
   * @param osmBoundingArea to use
   */
  public void setBoundingArea(OsmBoundary osmBoundingArea){
    this.osmBoundingArea = osmBoundingArea;
  }

  /**
   * Track all OSMWays that have been identified as being spatially eligible infrastructure for parsing as part of
   * the network, i.e., they are infrastructure that is being parsed and they fall within the area configured to be
   * produced even if the input file or data spans a larger area
   *
   * @param osmWayId OSM way id to mark as eligible
   */
  public void registerSpatialInfraEligibleOsmWayId(long osmWayId) {
    spatialInfrastructureEligibleOsmWays.add(osmWayId);
  }

  /**
   * Verify if OSMWay is identified as being spatially eligible for parsing as part of the network, i.e.,
   * it falls within the area deemed suitable for the final result.
   *
   * @param osmWayId OSM way id to mark as eligible
   * @return true when eligible, false otherwise
   */
  public boolean isSpatialInfraEligibleOsmWay(long osmWayId) {
    return spatialInfrastructureEligibleOsmWays.contains(osmWayId);
  }

  /**
   * how many eligible OSM ways have been registered to date
   * @return count
   */
  public int getNumSpatialInfraEligibleOsmWays() {
    return spatialInfrastructureEligibleOsmWays.size();
  }
}

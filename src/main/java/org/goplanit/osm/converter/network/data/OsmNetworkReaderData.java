package org.goplanit.osm.converter.network.data;

import org.goplanit.osm.converter.diagnostics.OsmParseDiagnostics;
import org.goplanit.osm.converter.diagnostics.OsmPlanitEntityDiagnostics;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.converter.OsmSpatialEligibilityData;
import org.goplanit.osm.converter.network.OsmNetworkLayerParser;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLink;
import org.goplanit.utils.network.layer.physical.Node;

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

  /** OSM way ids of the roundabouts among the eligible OSM ways, those tagged junction=roundabout or circular, so that
   * a roundabout can be told apart once its links are created. Recorded only when intersections are parsed */
  private final Set<Long> osmWayIdsOfRoundabouts = new HashSet<>();

  /** OSM node ids of the ways that can cross a road, collected while pre-processing, parsed or not, see
   * {@link #registerOsmNodesOfWayThatCanCrossRoad(OsmWay)}; footways and cycleways have many nodes */
  private final SortedOsmIdSet osmNodeIdsOfWaysThatCanCrossRoad = new SortedOsmIdSet();

  /** OSM way ids of the road and rail ways the settings skip, by why they are skipped, so a turn restriction on such a
   * way can say why it gives no banned turn, see {@link #registerOsmWaySkippedBySettings(long, OsmWaySkipReason)} */
  private final Map<OsmWaySkipReason, SortedOsmIdSet> osmWayIdsSkippedBySettings =
      new EnumMap<>(OsmWaySkipReason.class);

  /** the distance that qualifies as being near to the network bounding area. USed to be used with
   * cruder version of bounding area when this was tracked as rectagnular gounding box and not a polygon */
  @Deprecated
  public static final double BOUNDINGAREA_NEARNESS_DISTANCE_METERS = 200;

  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the
   * roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays =new HashMap<>();

  /** the osmBoundary used during parsing.
   */
  private OsmBoundary osmBoundingArea = null;

  /** the bounds the input file declares, null when it declares none. Unlike the bounding area these restrict nothing
   * that is parsed */
  private OsmBoundary inputFileBounds = null;

  /**
   * Track OSM nodes to retain in memory during network parsing, which might or might not end up being used
   * to construct links or other entities
   */
  private final OsmNodeData osmNodeData = new OsmNodeData();

  /** track spatial eligibility of OSM entities, for example based on boundary used (if any) */
  private final OsmSpatialEligibilityData osmSpatialEligibilityData = new OsmSpatialEligibilityData();

  /** OSM node ids of the traffic signals among the eligible OSM nodes, vehicle or crossing, recorded only when
   * intersections are parsed, ordered so that integrating them does not depend on hash order */
  private final SortedSet<Long> trafficSignalOsmNodeIds = new TreeSet<>();

  /** per OSM node of a signalised crossing way, the id of that way, recorded only when intersections are parsed, for
   * any crossing way, parsed or not */
  private final Map<Long, Long> signalisedCrossingWayIdByOsmNodeId = new HashMap<>();

  /** OSM node ids of the eligible OSM nodes on a signalised crossing way without crossing signal tags of their own,
   * among them the node where the crossing way crosses a road, ordered so that integrating them does not depend on
   * hash order */
  private final SortedSet<Long> osmNodeIdsOnSignalisedCrossingWayOnly = new TreeSet<>();

  /** what became of the OSM entities read, for now the traffic signals */
  private final OsmParseDiagnostics parseDiagnostics = new OsmParseDiagnostics();

  /** what became of the PLANit entities derived from them, for now the intersections */
  private final OsmPlanitEntityDiagnostics planitEntityDiagnostics = new OsmPlanitEntityDiagnostics();

  /** Track OSM ways that have been processed and identified as being unavailable/not used. This has been communicated if needed
   *  to users, so any subsequent dependencies on this OSM way can be safely ignored without issuing further warnings
   */
  private final Set<Long> discardedOsmWays = new HashSet<>();

  
  /** track layer specific information and handler to delegate processing the parts of osm ways assigned to a layer */
  private final Map<MacroscopicNetworkLayer, OsmNetworkLayerParser> osmLayerParsers = new HashMap<>();
  
  /**
   * initialise for each layer
   * 
   * @param network to use
   * @param settings to use
   * @param geoUtils to use
   */
  public void initialiseLayerParsers(
      PlanitOsmNetwork network, OsmNetworkReaderSettings settings, PlanitJtsCrsUtils geoUtils) {
    /* for each layer initialise a handler */
    for(MacroscopicNetworkLayer macroNetworkLayer : network.getTransportLayers()) {
      OsmNetworkLayerParser layerHandler =
          new OsmNetworkLayerParser(macroNetworkLayer, this, settings, geoUtils);
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
    inputFileBounds = null;
    osmSpatialEligibilityData.reset();
    trafficSignalOsmNodeIds.clear();
    signalisedCrossingWayIdByOsmNodeId.clear();
    osmNodeIdsOnSignalisedCrossingWayOnly.clear();
    osmWayIdsOfRoundabouts.clear();
    osmNodeIdsOfWaysThatCanCrossRoad.clear();
    osmWayIdsSkippedBySettings.clear();
    parseDiagnostics.reset();
    planitEntityDiagnostics.reset();
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

  /**
   * Record an eligible OSM node as carrying a traffic signal, vehicle or crossing
   *
   * @param osmNodeId to record
   */
  public void registerTrafficSignalOsmNodeId(long osmNodeId){
    trafficSignalOsmNodeIds.add(osmNodeId);
  }

  /**
   * Collect the OSM node ids of the traffic signals among the eligible OSM nodes, ordered by id
   *
   * @return OSM node ids, unmodifiable
   */
  public SortedSet<Long> getTrafficSignalOsmNodeIds(){
    return Collections.unmodifiableSortedSet(trafficSignalOsmNodeIds);
  }

  /**
   * Record the OSM nodes of a signalised crossing way, each with the id of that way, unless it was recorded with
   * another signalised crossing way before
   *
   * @param osmWay the signalised crossing way
   */
  public void registerSignalisedCrossingWayNodes(OsmWay osmWay){
    for(int index = 0; index < osmWay.getNumberOfNodes(); ++index){
      signalisedCrossingWayIdByOsmNodeId.putIfAbsent(osmWay.getNodeId(index), osmWay.getId());
    }
  }

  /**
   * Collect the id of the signalised crossing way an OSM node is on
   *
   * @param osmNodeId of the node
   * @return id of the signalised crossing way, null when the node is on none
   */
  public Long getSignalisedCrossingWayId(long osmNodeId){
    return signalisedCrossingWayIdByOsmNodeId.get(osmNodeId);
  }

  /**
   * Record an eligible OSM node on a signalised crossing way that has no crossing signal tags of its own
   *
   * @param osmNodeId to record
   */
  public void registerOsmNodeOnSignalisedCrossingWayOnly(long osmNodeId){
    osmNodeIdsOnSignalisedCrossingWayOnly.add(osmNodeId);
  }

  /**
   * Collect the OSM node ids of the eligible OSM nodes on a signalised crossing way without crossing signal tags of
   * their own, ordered by id. Among them is the node where the crossing way crosses a road, but also, e.g., where it
   * meets the kerb
   *
   * @return OSM node ids, unmodifiable
   */
  public SortedSet<Long> getOsmNodeIdsOnSignalisedCrossingWayOnly(){
    return Collections.unmodifiableSortedSet(osmNodeIdsOnSignalisedCrossingWayOnly);
  }

  /**
   * Record an eligible OSM way as part of a roundabout, tagged junction=roundabout or circular
   *
   * @param osmWayId to record
   */
  public void registerOsmWayIdOfRoundabout(long osmWayId){
    osmWayIdsOfRoundabouts.add(osmWayId);
  }

  /**
   * Verify if an OSM way is part of a roundabout, see {@link #registerOsmWayIdOfRoundabout(long)}
   *
   * @param osmWayId to verify
   * @return true when part of a roundabout, false otherwise
   */
  public boolean isOsmWayIdOfRoundabout(long osmWayId){
    return osmWayIdsOfRoundabouts.contains(osmWayId);
  }

  /**
   * Record the OSM nodes of a way that can cross a road, a footway, cycleway or similar way that is not an area, parsed
   * or not, so that a node where it meets a road can be told apart once the road's links are created. Recorded while
   * pre-processing
   *
   * @param osmWay to record the nodes of
   */
  public void registerOsmNodesOfWayThatCanCrossRoad(OsmWay osmWay){
    for (int index = 0; index < osmWay.getNumberOfNodes(); ++index) {
      osmNodeIdsOfWaysThatCanCrossRoad.add(osmWay.getNodeId(index));
    }
  }

  /**
   * Verify if an OSM node lies on a way that can cross a road, see
   * {@link #registerOsmNodesOfWayThatCanCrossRoad(OsmWay)}
   *
   * @param osmNodeId to verify
   * @return true when on such a way, false otherwise
   */
  public boolean isOsmNodeOfWayThatCanCrossRoad(long osmNodeId){
    return osmNodeIdsOfWaysThatCanCrossRoad.contains(osmNodeId);
  }

  /**
   * Record a road or rail way the settings skip, so it gets no link, with why it is skipped
   *
   * @param osmWayId of the way
   * @param reason why the settings skip it
   */
  public void registerOsmWaySkippedBySettings(long osmWayId, OsmWaySkipReason reason){
    osmWayIdsSkippedBySettings.computeIfAbsent(reason, skipReason -> new SortedOsmIdSet()).add(osmWayId);
  }

  /**
   * Why the settings skip a way, see {@link #registerOsmWaySkippedBySettings(long, OsmWaySkipReason)}
   *
   * @param osmWayId of the way
   * @return why it is skipped, null when the settings do not skip it
   */
  public OsmWaySkipReason findWhyOsmWaySkippedBySettings(long osmWayId){
    return osmWayIdsSkippedBySettings.entrySet().stream().filter(entry -> entry.getValue().contains(osmWayId))
        .map(Map.Entry::getKey).findFirst().orElse(null);
  }

  /**
   * Access to what became of the OSM entities read, for now the traffic signals
   *
   * @return parse diagnostics
   */
  public OsmParseDiagnostics getParseDiagnostics(){
    return parseDiagnostics;
  }

  /**
   * Access to what became of the PLANit entities derived from the OSM entities read, for now the intersections
   *
   * @return PLANit entity diagnostics
   */
  public OsmPlanitEntityDiagnostics getPlanitEntityDiagnostics(){
    return planitEntityDiagnostics;
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
  public final OsmNetworkLayerParser getLayerParser(MacroscopicNetworkLayer networkLayer) {
    return this.osmLayerParsers.get(networkLayer);
  }   
  
  /** provide reference to the used layer parsers for each of the identified layers
   * 
   * @return layerParsers used
   */
  public final Map<MacroscopicNetworkLayer, OsmNetworkLayerParser> getLayerParsers() {
    return this.osmLayerParsers;
  }

  /**
   * Find across layer parsers
   * <p>
   *   Note that some OSM ways (circular) may not have been converted yet and are not findable through until after
   *   completion of main processing
   * </p>
   * @param osmWayId to find
   * @return PLANit link if available
   */
  public MacroscopicLink findPlanitLinkByOsmWayId(long osmWayId){
    return getLayerParsers().values().stream()
        .map(parser -> parser.getLayerData().findPlanitLinkByOsmWayId(osmWayId))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Find across layer parsers
   * @param osmNodeId to find
   * @return PLANit node if available
   */
  public Node findPlanitNodeByOsmNode(OsmNode osmNodeId) {
    return getLayerParsers().values().stream()
        .map(parser -> parser.getLayerData().getPlanitNodeByOsmNode(osmNodeId))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
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
   * Collect the bounds the input file declares, e.g. in its PBF header or as the bounds element of OSM XML
   *
   * @return the declared bounds, null when the input file declares none
   */
  public OsmBoundary getInputFileBounds(){
    return inputFileBounds;
  }

  /**
   * Set the bounds the input file declares
   *
   * @param inputFileBounds the declared bounds
   */
  public void setInputFileBounds(OsmBoundary inputFileBounds){
    this.inputFileBounds = inputFileBounds;
  }

}

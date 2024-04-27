package org.goplanit.osm.converter.network;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.network.layer.MacroscopicNetworkLayer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Data specifically required in the network reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class OsmNetworkReaderData {
  
  /** the logger  */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderData.class.getCanonicalName());

  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  private final Map<Long, OsmWay> osmCircularWays =new HashMap<>();
    
  /** on the fly tracking of network spanning (square) bounding box of all parsed nodes in the network */
  private Envelope networkBoundingBox;

  /** the osmBoundary derived from the user configuration, which in case the user configuration was based on a name only
   * will have been expanded with the polygon identified during network pre-processing. This polygon will then be used to
   * create this new bounding area that is used for main processing, instead of the one from user settings. If a polygon was
   * directly set, we create a copy instead
   */
  private OsmBoundary osmBoundingArea = null;

  /**
   * OSMWay ids that make up the outer polygon of the to be constructed osmBoundingArea (if any), contiguous nature of
   * these can be reconstructed by ordering them by their integer value which is incrementally created upon adding entries.
   */
  private Map<Long, Pair<Integer,OsmWay>> osmBoundaryOsmWayTracker = new HashMap<>();

  /**
   * Track OSM nodes to retain in memory during network parsing, which might or might not end up being used to construct links
   * or other entities
   */
  private OsmNodeData osmNodeData = new OsmNodeData();

  /** Track OSM ways that have been processed and identified as being unavailable/not used. This has been communicated if needed
   *  to users, so any subsequent dependencies on this OSM way can be safely ignored without issuing further warnings
   */
  private Set<Long> discardedOsmWays = new HashSet<>();

  
  /** track layer specific information and handler to delegate processing the parts of osm ways assigned to a layer */
  private final Map<MacroscopicNetworkLayer, OsmNetworkLayerParser> osmLayerParsers = new HashMap<>();
  
  /** the distance that qualifies as being near to the network bounding box. Used to suppress warnings of incomplete osm ways due to bounding box (which
   * is to be expected). when beyond this distance, warnings of missing nodes/ways will be generated as something else is going on */
  public static final double BOUNDINGBOX_NEARNESS_DISTANCE_METERS = 200;  
  
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
   * Initialise bounding area based on original bounding area set by user in settings. In case it has no polygon
   * but just a name, then we do nothing here, but instead await the pre-processing which will update the bounding area
   * at a later stage (with a polygon)
   *
   * @param settings to collect current bounding area information from
   */
  protected void initialiseBoundingArea(OsmNetworkReaderSettings settings) {
    boolean thisNoBoundaryNorPolygon = (this.osmBoundingArea==null || !getBoundingAreaWithPolygon().hasBoundingPolygon());
    boolean settingsHasBoundaryAndPolygon = settings.hasBoundingBoundary() && settings.getBoundingArea().hasBoundingPolygon();
    if(thisNoBoundaryNorPolygon && settingsHasBoundaryAndPolygon) {
      setBoundingAreaWithPolygon(settings.getBoundingArea().deepClone());
    }
  }

  /**
   * reset
   */
  public void reset() {
    clearOsmCircularWays();    
    osmNodeData.reset();
    
    /* reset layer handlers as well */
    osmLayerParsers.forEach( (layer, handler) -> {handler.reset();});
    osmLayerParsers.clear();    
  }  
  
  /** update bounding box to include OSM node
   *
   * @param osmNode to expand so that bounding box includes it
   */
  public void updateSpanningBoundingBox(OsmNode osmNode) {
    Coordinate coordinate = OsmNodeUtils.createCoordinate(osmNode);
    if(networkBoundingBox==null) {
      networkBoundingBox = new Envelope(coordinate);
    }else {
      networkBoundingBox.expandToInclude(coordinate);
    }
  }
  
  /** collect the network spanning bounding box so far (crude)
   * 
   * @return network spanning bounding box
   */
  public Envelope getNetworkSpanningBoundingBox() {
    return networkBoundingBox;
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

  /**
   * Register portions of the named OSM boundary to construct a single coherent polygon during regular pre-processing
   * stage but before main processing (as main processing relies on the boundary to be finalised to do the actual parsing)
   *
   * @param osmWayId to register as part of the future OSM boundary
   */
  public void registerBoundaryOsmWayOuterRoleSection(long osmWayId) {
    osmBoundaryOsmWayTracker.put(osmWayId,Pair.of(osmBoundaryOsmWayTracker.size(), null /* to be added in next stage */));
  }

  /**
   * Register actual OsmWay  portion of the named OSM boundary to construct a single coherent polygon during regular pre-processing
   * stage but before main processing (as main processing relies on the boundary to be finalised to do the actual parsing).
   * <p> Requires the osmWayId to have been registered already in stage 1 of preprocessing </p>
   *
   * @param osmWay to update registered entry with as part of the future OSM boundary
   */
  public void updateBoundaryRegistrationWithOsmWay(OsmWay osmWay) {
    if(!isRegisteredBoundaryOsmWay(osmWay.getId())){
      LOGGER.severe("Should not update boundary with OSM way when it has not been identified as being part of boundary during " +
          "initial stage of preprocessing, ignored");
    }
    var valueWithoutOsmWay = osmBoundaryOsmWayTracker.get(osmWay.getId());
    osmBoundaryOsmWayTracker.put(osmWay.getId(),Pair.of(valueWithoutOsmWay.first(), osmWay /* update now available */));
  }

  /**
   * Extract an ordered list of the registered OSM ways for the bounding boundary in the order they were registered in
   *
   * @return ordered list of OSM ways
   */
  public List<OsmWay> getRegisteredBoundaryOsmWaysInOrder() {
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
        Comparator.comparingInt( e -> e.getValue().first())).map( e -> e.getValue().second()).collect(Collectors.toList());
  }

  /**
   * Verify if OSM way is part of bounding area boundary
   *
   * @param osmWayId to verify
   * @return true when registered, false otherwise
   */
  public boolean isRegisteredBoundaryOsmWay(long osmWayId) {
    return osmBoundaryOsmWayTracker.containsKey(osmWayId);
  }

  /**
   * Check if any OSM ways have been registered for constructing the OSM bounding boundary polygon from
   *
   * @return true if present, false otherwise
   */
  public boolean hasRegisteredBoundaryOsmWay() {
    return !osmBoundaryOsmWayTracker.isEmpty();
  }

  /** check if bounding area is available
   *
   * @return true if present, false otherwise
   */
   public boolean hasBoundingBoundary(){
     return osmBoundingArea != null;
   }

  /** get the bounding area
   *
   * @return bounding area
   */
  public OsmBoundary getBoundingArea(){
    return osmBoundingArea;
  }

}

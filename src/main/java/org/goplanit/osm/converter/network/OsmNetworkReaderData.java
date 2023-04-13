package org.goplanit.osm.converter.network;

import java.util.*;
import java.util.logging.Logger;

import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.OsmNodeData;
import org.goplanit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.goplanit.osm.util.OsmNodeUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
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
    
  /** on the fly tracking of bounding box of all parsed nodes in the network */
  private Envelope networkBoundingBox;

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
   * reset
   */
  public void reset() {
    clearOsmCircularWays();    
    osmNodeData.reset();
    
    /* reset layer handlers as well */
    osmLayerParsers.forEach( (layer, handler) -> {handler.reset();});
    osmLayerParsers.clear();    
  }  
  
  /** update bounding box to include osm node
   * @param osmNode to expand so that bounding box includes it
   */
  public void updateBoundingBox(OsmNode osmNode) {
    Coordinate coordinate = OsmNodeUtils.createCoordinate(osmNode);
    if(networkBoundingBox==null) {
      networkBoundingBox = new Envelope(coordinate);
    }else {
      networkBoundingBox.expandToInclude(coordinate);
    }
  }
  
  /** collect the network bounding box so far
   * 
   * @return network bounding box
   */
  public Envelope getBoundingBox() {
    return networkBoundingBox;
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

 
}

package org.planit.osm.converter.network;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.planit.network.TransportLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.util.OsmNodeUtils;
import org.planit.utils.geo.PlanitJtsCrsUtils;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Data specifically required in the network reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class OsmNetworkReaderData {
    
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  protected final Map<Long, OsmNode> osmNodes;        

  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  protected final Map<Long, OsmWay> osmCircularWays;  
    
  /** on the fly tracking of bounding box of all parsed nodes in the network */
  private Envelope networkBoundingBox;
  
  /** track layer specific information and handler to delegate processing the parts of osm ways assigned to a layer */
  private final Map<MacroscopicPhysicalNetwork, OsmNetworkLayerParser> osmLayerParsers = new HashMap<MacroscopicPhysicalNetwork, OsmNetworkLayerParser>();  
  
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
    for(TransportLayer networkLayer : network.transportLayers) {
      MacroscopicPhysicalNetwork macroNetworkLayer = (MacroscopicPhysicalNetwork)networkLayer;
      OsmNetworkLayerParser layerHandler = new OsmNetworkLayerParser(macroNetworkLayer, this, settings, geoUtils);
      osmLayerParsers.put(macroNetworkLayer, layerHandler);
    }
  }    
      
  /** Default Constructor 
   * 
   */
  public OsmNetworkReaderData() {    
    this.osmNodes = new HashMap<Long, OsmNode>();
    this.osmCircularWays = new HashMap<Long, OsmWay>();
  }
  
  /**
   * reset
   */
  public void reset() {
    clearOsmCircularWays();    
    osmNodes.clear();
    
    /* reset layer handlers as well */
    osmLayerParsers.forEach( (layer, handler) -> {handler.reset();});
    osmLayerParsers.clear();    
  }  
  
  /** update bounding box to include osm node
   * @param osmNode to expand so that bounding box includes it
   */
  public void updateBoundingBox(OsmNode osmNode) {
    Coordinate coorinate = OsmNodeUtils.createCoordinate(osmNode);
    if(networkBoundingBox==null) {
      networkBoundingBox = new Envelope(coorinate);
    }else {
      networkBoundingBox.expandToInclude(coorinate);
    }
  }
  
  /** collect the network bounding box so far
   * 
   * @return network bounding box
   */
  public Envelope getBoundingBox() {
    return networkBoundingBox;
  }
  
  /** collect the osm nodes (unmoidifable)
   * 
   * @return osm nodes
   */
  public Map<Long, OsmNode> getOsmNodes() {
    return Collections.unmodifiableMap(osmNodes);
  }
  
  /** add an osm node
   * @param osmNode to add
   */
  public void addOsmNode(OsmNode osmNode) {
    osmNodes.put(osmNode.getId(), osmNode);
  } 
  
  /** collect an osm node
   * @param osmNodeId to collect
   * @return osm node, null if not present
   */
  public OsmNode getOsmNode(long osmNodeId) {
    return osmNodes.get(osmNodeId);
  }  
  
  /** Verify if osm node is available
   * @param osmNodeId to verify
   * @return true when available, false otherwise
   */
  public boolean hasOsmNode(long osmNodeId) {
    return getOsmNode(osmNodeId)!=null;
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
  
  /** provide reference to a layer parser
   * 
   * @param networkLayer to collect parser for
   * @return layerParser, null if not present
   */
  public final OsmNetworkLayerParser getLayerParser(MacroscopicPhysicalNetwork networkLayer) {
    return this.osmLayerParsers.get(networkLayer);
  }   
  
  /** provide reference to the used layer parsers for each of the identified layers
   * 
   * @return layerParsers used
   */
  public final Map<MacroscopicPhysicalNetwork, OsmNetworkLayerParser> getLayerParsers() {
    return this.osmLayerParsers;
  }

 
}

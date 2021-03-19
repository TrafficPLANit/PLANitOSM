package org.planit.osm.converter.reader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Data specifically required in the network reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkReaderData {

  /** the country we are importing for (if any) */
  private final String countryName;
  
  /** temporary storage of osmNodes before converting the useful ones to actual nodes */
  protected final Map<Long, OsmNode> osmNodes;        

  /** temporary storage of osmWays before extracting either a single node, or multiple links to reflect the roundabout/circular road */
  protected final Map<Long, OsmWay> osmCircularWays;  
  
  /** network to populate */
  private final PlanitOsmNetwork osmNetwork;
  
  /** Constructor 
   * @param countryName to use
   * @param osmNetwork to use
   */
  public PlanitOsmNetworkReaderData(String countryName, PlanitOsmNetwork osmNetwork) {
    this.countryName = countryName;
    this.osmNetwork = osmNetwork;
    
    this.osmNodes = new HashMap<Long, OsmNode>();
    this.osmCircularWays = new HashMap<Long, OsmWay>();    
  }
  
  /**
   * reset
   */
  public void reset() {
    clearOsmCircularWays();    
    osmNodes.clear();
  }  

  /** collect country name for this reader
   * @return country name
   */
  public String getCountryName() {
    return countryName;
  }

  /** collect osm network being popoulated for this reader
   * @return osm network
   */
  public PlanitOsmNetwork getOsmNetwork() {
    return osmNetwork;
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

 
}

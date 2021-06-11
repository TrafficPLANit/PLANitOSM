package org.planit.osm.converter.zoning.parser;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.geom.Point;
import org.planit.utils.network.physical.Link;
import org.planit.utils.zoning.DirectedConnectoid;

/**
 * Static helper methods to support parsing/functionality of PlanitOsmConnectoidParser
 * 
 * @author markr
 *
 */
public class PlanitOsmConnectoidParserHelper {

  /** find all already registered directed connectoids that reference a link segment part of the passed in link in the given network layer
   * 
   * @param link to find referencing directed connectoids for
   * @param knownConnectoidsByLocation all connectoids of the network layer indexed by their location
   * @return all identified directed connectoids
   */
  private static Collection<DirectedConnectoid> findDirectedConnectoidsRefencingLink(Link link, Map<Point, List<DirectedConnectoid>> knownConnectoidsByLocation) {
    Collection<DirectedConnectoid> referencingConnectoids = new HashSet<DirectedConnectoid>();
    /* find eligible locations for connectoids based on downstream locations of link segments on link */
    Set<Point> eligibleLocations = new HashSet<Point>();
    if(link.hasEdgeSegmentAb()) {      
      eligibleLocations.add(link.getEdgeSegmentAb().getDownstreamVertex().getPosition());
    }
    if(link.hasEdgeSegmentBa()) {
      eligibleLocations.add(link.getEdgeSegmentBa().getDownstreamVertex().getPosition());
    }
    
    /* find all directed connectoids with link segments that have downstream locations matching the eligible locations identified*/
    for(Point location : eligibleLocations) {
      Collection<DirectedConnectoid> knownConnectoidsForLink = knownConnectoidsByLocation.get(location);
      if(knownConnectoidsForLink != null && !knownConnectoidsForLink.isEmpty()) {
        for(DirectedConnectoid connectoid : knownConnectoidsForLink) {
          if(connectoid.getAccessLinkSegment().idEquals(link.getEdgeSegmentAb()) || connectoid.getAccessLinkSegment().idEquals(link.getEdgeSegmentBa()) ) {
            /* match */
            referencingConnectoids.add(connectoid);
          }
        }
      }
    }
    
    return referencingConnectoids;
  } 
  
  /**
   * Collect all connectoids and their access node's positions if their access link segments reside on the provided links. Can be useful to ensure
   * these positions remain correct after modifying the network.  
   * 
   * @param links to collect connectoid information for, i.e., only connectoids referencing link segments with a parent link in this collection
   * @param connectoidsByLocation all connectoids indexed by their location
   * @return found connectoids and their accessNode position 
   */
  public static Map<Point,DirectedConnectoid> collectConnectoidAccessNodeLocations(Collection<Link> links, Map<Point, List<DirectedConnectoid>> connectoidsByLocation) {    
    Map<Point, DirectedConnectoid> connectoidsDownstreamVerticesBeforeBreakLink = new HashMap<Point, DirectedConnectoid>();
    for(Link link : links) {
      Collection<DirectedConnectoid> connectoids = findDirectedConnectoidsRefencingLink(link,connectoidsByLocation);
      if(connectoids !=null && !connectoids.isEmpty()) {
        connectoids.forEach( connectoid -> connectoidsDownstreamVerticesBeforeBreakLink.put(connectoid.getAccessNode().getPosition(),connectoid));          
      }
    }
    return connectoidsDownstreamVerticesBeforeBreakLink;
  }   
 
}

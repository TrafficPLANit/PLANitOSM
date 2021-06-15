package org.planit.osm.util;

import java.util.Collection;
import java.util.HashSet;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitGraphGeoUtils;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.TrackModeType;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;

/**
 * Utilities regarding PLANit links useful when parsing OSM entities 
 * 
 * @author markr
 *
 */
public class PlanitLinkUtils {

  /** create a subset of links from the passed in ones, removing all links for which we can be certain that geometry is located on the wrong side of the road infrastructure geometry.
   * This is verified by checking if the link is one-way. If so, we can be sure (based on the driving direction of the country) if the geometry is located to the closest by (logical) 
   * driving direction given the placement of the geometry, i.e., on the left hand side for left hand drive countries, on the right hand side for right hand driving countries
   * 
   * @param waitingAreaGeometry representing the waiting area (station, platform, pole)
   * @param links to remove in-eligible ones from
   * @param isLeftHandDrive flag
   * @param accessModes to consider
   * @param geoUtils to use
   * @return remaining links that are deemed eligible
   * @throws PlanItException thrown if error
   */   
  public static Collection<Link> excludeLinksOnWrongSideOf(Geometry waitingAreaGeometry, Collection<Link> links, boolean isLeftHandDrive, Collection<Mode> accessModes, PlanitJtsCrsUtils geoUtils) throws PlanItException{
    Collection<Link> matchedLinks = new HashSet<Link>(links);  
    for(Link link : links) {            
      for(Mode accessMode : accessModes){
        
        /* road based modes must stop with the waiting area in the driving direction, i.e., must avoid cross traffic, because otherwise they 
         * have no doors at the right side, e.g., travellers have to cross the road to get to the vehicle, which should not happen */
        boolean mustAvoidCrossingTraffic = true;
        if(accessMode.getPhysicalFeatures().getTrackType().equals(TrackModeType.RAIL)) {
          mustAvoidCrossingTraffic = false;
        }           
        
        MacroscopicLinkSegment oneWayLinkSegment = PlanitLinkSegmentUtils.getLinkSegmentIfLinkIsOneWayForMode(link, accessMode);        
        if(oneWayLinkSegment != null && mustAvoidCrossingTraffic) {
          /* use line geometry closest to connectoid location */
          LineSegment finalLineSegment = PlanitLinkSegmentUtils.extractClosestLineSegmentToGeometryFromLinkSegment(waitingAreaGeometry, oneWayLinkSegment, geoUtils);                    
          /* determine location relative to infrastructure */
          boolean isStationLeftOfOneWayLinkSegment = geoUtils.isGeometryLeftOf(waitingAreaGeometry, finalLineSegment.p0, finalLineSegment.p1);  
          if(isStationLeftOfOneWayLinkSegment != isLeftHandDrive) {
            /* travellers cannot reach doors of mode on this side of the road, so deemed not eligible */
            matchedLinks.remove(link);
            break; // from mode loop
          }
        }
      }             
    }
    return matchedLinks;
  }
  
  /** Collect the closest by link (with the given OSM way id on the layer) to the provided geometry. This is a very slow method
   * as we lookup the link via the un-indexed external id. Use cautiously.
   * 
   * @param osmWayId to filter links on
   * @param geometry to check closeness against
   * @param networkLayer the link must reside on
   * @param geoUtils to use for closeness computations
   * @return found link (null if none)
   * @throws PlanItException thrown if error
   */
  public static Link getClosestLinkWithOsmWayIdToGeometry(
      long osmWayId, Geometry geometry, MacroscopicPhysicalNetwork networkLayer, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* collect all PLANit links that match the OSM way id (very slow, but it is rare and not worth the indexing generally) */
    Collection<? extends Link> nominatedLinks = networkLayer.links.getByExternalId(String.valueOf(osmWayId));
    /* in case osm way is broken, multiple planit links might exist with the same external id, find closest one and use it */
    return (Link) PlanitGraphGeoUtils.findEdgeClosest(geometry, nominatedLinks, geoUtils);
  }  
}

package org.planit.osm.converter.zoning;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Point;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkReaderLayerData;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.graph.EdgeSegment;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.zoning.DirectedConnectoid;
import org.planit.utils.zoning.TransferZone;

import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * Class to provide functionality for parsing PLANit connectoids from OSM entities
 * 
 * @author markr
 *
 */
public class PlanitOsmConnectoidParser {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmConnectoidParser.class.getCanonicalName());

  /** update an existing directed connectoid with new access zone and allowed modes. In case the link segment does not have any of the 
   * passed in modes listed as allowed, the connectoid is not updated with these modes for the given access zone as it would not be possible to utilise it. 
   * 
   * @param connectoidToUpdate to connectoid to update
   * @param accessZone to relate connectoids to
   * @param allowedModes to add to the connectoid for the given access zone
   */  
  protected void updateDirectedConnectoid(DirectedConnectoid connectoidToUpdate, TransferZone accessZone, Set<Mode> allowedModes) {    
    final Set<Mode> realAllowedModes = ((MacroscopicLinkSegment)connectoidToUpdate.getAccessLinkSegment()).getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      if(!connectoidToUpdate.hasAccessZone(accessZone)) {
        connectoidToUpdate.addAccessZone(accessZone);
      }
      connectoidToUpdate.addAllowedModes(accessZone, realAllowedModes);   
    }
  }

  protected boolean extractDirectedConnectoidsForMode(TransferZone transferZone, Mode planitMode, Collection<EdgeSegment> eligibleLinkSegments, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(planitMode);
    
    for(EdgeSegment edgeSegment : eligibleLinkSegments) {
     
      /* update accessible link segments of already created connectoids (if any) */      
      Point proposedConnectoidLocation = edgeSegment.getDownstreamVertex().getPosition();
      boolean createConnectoidsForLinkSegment = true;
      
      if(zoningReaderData.getPlanitData().hasDirectedConnectoidForLocation(networkLayer, proposedConnectoidLocation)) {      
        /* existing connectoid: update model eligibility */
        Collection<DirectedConnectoid> connectoidsForNode = zoningReaderData.getPlanitData().getDirectedConnectoidsByLocation(proposedConnectoidLocation, networkLayer);        
        for(DirectedConnectoid connectoid : connectoidsForNode) {
          if(edgeSegment.idEquals(connectoid.getAccessLinkSegment())) {
            /* update mode eligibility */
            updateDirectedConnectoid(connectoid, transferZone, Collections.singleton(planitMode));
            createConnectoidsForLinkSegment  = false;
            break;
          }
        }
      }
                    
      /* for remaining access link segments without connectoid -> create them */        
      if(createConnectoidsForLinkSegment) {
                
        /* create and register */
        Collection<DirectedConnectoid> newConnectoids = createAndRegisterDirectedConnectoids(transferZone, networkLayer, Collections.singleton(edgeSegment), Collections.singleton(planitMode));
        
        if(newConnectoids==null || newConnectoids.isEmpty()) {
          LOGGER.warning(String.format("Found eligible mode %s for stop_location of transferzone %s, but no access link segment supports this mode", planitMode.getExternalId(), transferZone.getExternalId()));
          return false;
        }
      }  
    }
    
    return true;
  }

  /** Identical to the same method with additional paramater containing link restrictions. Only by calling this method all entry link segments are considred whereas in the
   * more general version only link segments with a parent link in the provided link collection are considered. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    return extractDirectedConnectoidsForMode(location, transferZone, planitMode, null, geoUtils);
  }

  /** create and/or update directed connectoids for the given mode and layer based on the passed in location where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone. Generally a single connectoid is created for the most likely link segment identified, i.e., if the transfer
   * zone is placed on the left of the infrastructure, the closest by incoming link segment to the given location is used. Since the geometry of a link applies to both link segments
   * we define closest based on the driving position of the country, so a left-hand drive country will use the incoming link segment where the transfer zone is placed on the left, etc. 
   * 
   * @param location to create the access point for as planit node (one or more upstream planit link segments will act as access link segment for the created connectoid(s))
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param eligibleAccessLinks only links in this collection are considered when compatible with provided location
   * @param geoUtils used when location of transfer zone relative to infrastructure is to be determined 
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(Point location, TransferZone transferZone, Mode planitMode, Collection<Link> eligibleAccessLinks, PlanitJtsCrsUtils geoUtils) throws PlanItException {    
    if(location == null || transferZone == null || planitMode == null || geoUtils == null) {
      return false;
    }
    
    MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(planitMode);
    OsmNode osmNode = getNetworkToZoningData().getNetworkLayerData(networkLayer).getOsmNodeByLocation(location);                
    
    /* planit access node */
    Node planitNode = extractConnectoidAccessNodeByLocation(location, networkLayer);    
    if(planitNode==null) {
      if(osmNode != null) {
        LOGGER.warning(String.format("DISCARD: osm node %d could not be converted to access node for transfer zone representation of osm entity %s",osmNode.getId(), transferZone.getXmlId(), transferZone.getExternalId()));
      }else {
        LOGGER.warning(String.format("DISCARD: location (%s) could not be converted to access node for transfer zone representation of osm entity %s",location.toString(), transferZone.getXmlId(), transferZone.getExternalId()));
      }
      return false;
    }
    
    /* must avoid cross traffic when:
     * 1) stop position does not coincide with transfer zone, i.e., waiting area is not on the road/rail, and
     * 2) mode requires waiting area to be on a specific side of the road, e.g. buses can only open doors on one side, so it matters for them, but not for train
     */
    boolean mustAvoidCrossingTraffic = !planitNode.getPosition().equalsTopo(transferZone.getGeometry());
    if(mustAvoidCrossingTraffic) {
      mustAvoidCrossingTraffic = PlanitOsmZoningHandlerHelper.isWaitingAreaForPtModeRestrictedToDrivingDirectionLocation( planitMode, transferZone, osmNode!= null ? osmNode.getId() : null, getSettings());  
    }         
    
    /* find access link segments */
    Collection<EdgeSegment> accessLinkSegments = null;
    for(Link link : planitNode.<Link>getLinks()) {
      Collection<EdgeSegment> linkAccessLinkSegments = findAccessLinkSegmentsForStandAloneTransferZone(transferZone,link, planitNode, planitMode, mustAvoidCrossingTraffic, geoUtils);
      if(linkAccessLinkSegments != null && !linkAccessLinkSegments.isEmpty()) {
        if(accessLinkSegments == null) {
          accessLinkSegments = linkAccessLinkSegments;
        }else {
          accessLinkSegments.addAll(linkAccessLinkSegments);
        }
      }
    }    
      
    if(accessLinkSegments==null || accessLinkSegments.isEmpty()) {
      LOGGER.info(String.format("DICARD platform/pole/station %s its stop_location %s deemed invalid, no access link segment found due to incompatible modes or transfer zone on wrong side of road/rail", 
          transferZone.getExternalId(), planitNode.getExternalId()!= null ? planitNode.getExternalId(): ""));
      return false;
    }                           
    
    /* connectoids for link segments */
    return extractDirectedConnectoidsForMode(transferZone, planitMode, accessLinkSegments, geoUtils);
  }

  /** create and/or update directed connectoids for the given mode and layer based on the passed in osm node (location) where the connectoids access link segments are extracted for.
   * Each of the connectoids is related to the passed in transfer zone.  
   * 
   * @param osmNode to relate to planit network's incoming link segments as access points
   * @param transferZone this connectoid is assumed to provide access to
   * @param planitMode this connectoid is allowed access for
   * @param geoUtils used to determine location of transfer zone relative to infrastructure to identify which link segment(s) are eligible for connectoids placement
   * @return true when one or more connectoids have successfully been generated or existing connectoids have bee reused, false otherwise
   * @throws PlanItException thrown if error
   */
  protected boolean extractDirectedConnectoidsForMode(OsmNode osmNode, TransferZone transferZone, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    Point osmNodeLocation = PlanitOsmNodeUtils.createPoint(osmNode);
    return extractDirectedConnectoidsForMode(osmNodeLocation, transferZone, planitMode, geoUtils);
  }

  /** create directed connectoid for the link segment provided, all related to the given transfer zone and with access modes provided. When the link segment does not have any of the 
   * passed in modes listed as allowed, no connectoid is created and null is returned
   * 
   * @param accessZone to relate connectoids to
   * @param linkSegment to create connectoid for
   * @param allowedModes used for the connectoid
   * @return created connectoid when at least one of the allowed modes is also allowed on the link segment
   * @throws PlanItException thrown if error
   */
  protected DirectedConnectoid createAndRegisterDirectedConnectoid(final TransferZone accessZone, final MacroscopicLinkSegment linkSegment, final Set<Mode> allowedModes) throws PlanItException {
    final Set<Mode> realAllowedModes = linkSegment.getAllowedModesFrom(allowedModes);
    if(realAllowedModes!= null && !realAllowedModes.isEmpty()) {  
      DirectedConnectoid connectoid = zoning.transferConnectoids.registerNew(linkSegment,accessZone);
      
      /* xml id = internal id */
      connectoid.setXmlId(String.valueOf(connectoid.getId()));
      
      /* link connectoid to zone and register modes for access*/
      connectoid.addAllowedModes(accessZone, realAllowedModes);   
      return connectoid;
    }
    return null;
  }

  /** create directed connectoids, one per link segment provided, all related to the given transfer zone and with access modes provided. connectoids are only created
   * when the access link segment has at least one of the allowed modes as an eligible mode
   * 
   * @param transferZone to relate connectoids to
   * @param networkLayer of the modes and link segments used
   * @param linkSegments to create connectoids for (one per segment)
   * @param allowedModes used for each connectoid
   * @return created connectoids
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoids(final TransferZone transferZone, final MacroscopicPhysicalNetwork networkLayer, final Collection<? extends EdgeSegment> linkSegments, final Set<Mode> allowedModes) throws PlanItException {
    Set<DirectedConnectoid> createdConnectoids = new HashSet<DirectedConnectoid>();
    for(EdgeSegment linkSegment : linkSegments) {
      DirectedConnectoid newConnectoid = createAndRegisterDirectedConnectoid(transferZone, (MacroscopicLinkSegment)linkSegment, allowedModes);
      if(newConnectoid != null) {
        createdConnectoids.add(newConnectoid);
        
        /* update planit data tracking information */ 
        /* 1) index by access link segment's downstream node location */
        zoningReaderData.getPlanitData().addDirectedConnectoidByLocation(networkLayer, newConnectoid.getAccessLinkSegment().getDownstreamVertex().getPosition() ,newConnectoid);
        /* 2) index connectoids on transfer zone, so we can collect it by transfer zone as well */
        zoningReaderData.getPlanitData().addConnectoidByTransferZone(transferZone, newConnectoid);
      }
    }         
    
    return createdConnectoids;
  }

  /** Create directed connectoids for transfer zones that reside on osw ways. For such transfer zones, we simply create connectoids in both directions for all eligible incoming 
   * link segments. This is a special case because due to residing on the osm way it is not possible to distinguish what intended direction of the osm way is serviced (it is neither
   * left nor right of the way). Therefore any attempt to extract this information is bypassed here.
   * 
   * @param transferZone residing on an osm way
   * @param networkLayer related to the mode
   * @param planitMode the connectoid is accessible for
   * @param geoUtils to use
   * @return created connectoids, null if it was not possible to create any due to some reason
   * @throws PlanItException thrown if error
   */
  protected Collection<DirectedConnectoid> createAndRegisterDirectedConnectoidsOnTopOfTransferZone(
      TransferZone transferZone, MacroscopicPhysicalNetwork networkLayer, Mode planitMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* collect the osmNode for this transfer zone */
    OsmNode osmNode = getNetworkToZoningData().getOsmNodes().get(Long.valueOf(transferZone.getExternalId()));
    
    Collection<? extends EdgeSegment> nominatedLinkSegments = null;
    if(getSettings().hasWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node)) {
      /* user overwrite */
      
      long osmWayId = getSettings().getWaitingAreaNominatedOsmWayForStopLocation(osmNode.getId(), EntityType.Node);
      Link nominatedLink = PlanitOsmZoningHandlerHelper.getClosestLinkWithOsmWayIdToGeometry( osmWayId, PlanitOsmNodeUtils.createPoint(osmNode), networkLayer, geoUtils);
      if(nominatedLink != null) {
        nominatedLinkSegments = nominatedLink.getEdgeSegments(); 
      }else {
        LOGGER.severe(String.format("User nominated osm way not available for waiting area on road infrastructure %d",osmWayId));
      }                      
      
    }else {
      /* regular approach */
      
      /* create/collect planit node with access link segment */
      Node planitNode = extractConnectoidAccessNodeByOsmNode(osmNode, networkLayer);
      if(planitNode == null) {
        LOGGER.warning(String.format("DISCARD: osm node (%d) could not be converted to access node for transfer zone osm entity %s at same location",osmNode.getId(), transferZone.getExternalId()));
        return null;
      }
      
      nominatedLinkSegments = planitNode.getEntryLinkSegments();
    }
    
    
    
    /* connectoid(s) */
        
    /* create connectoids on top of transfer zone */
    /* since located on osm way we cannot deduce direction of the stop, so create connectoid for both incoming directions (if present), so we can service any line using the way */        
    return createAndRegisterDirectedConnectoids(transferZone, networkLayer, nominatedLinkSegments, Collections.singleton(planitMode));    
  }

}

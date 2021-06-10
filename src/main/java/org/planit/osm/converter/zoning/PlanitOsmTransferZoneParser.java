package org.planit.osm.converter.zoning;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkToZoningReaderData;
import org.planit.osm.tags.OsmTags;
import org.planit.osm.util.Osm4JUtils;
import org.planit.osm.util.OsmTagUtils;
import org.planit.osm.util.PlanitOsmBoundingBoxUtils;
import org.planit.osm.util.PlanitOsmNodeUtils;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.osm.util.PlanitOsmWayUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsCrsUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;

/**
 * Class to provide functionality for parsing transfer zones from OSM entities
 * 
 * @author markr
 *
 */
public class PlanitOsmTransferZoneParser {
  
  /** logger to use */ 
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmTransferZoneParser.class.getCanonicalName());
  
  /** the zoning to work on */
  private final Zoning zoning;
  
  /** zoning reader data used to track created entities */
  private final PlanitOsmZoningReaderData zoningReaderData;
  
  /** settings to adhere to */
  private final PlanitOsmPublicTransportReaderSettings transferSettings;  
  
  /** information on parsed OSM network to utilise */
  private final PlanitOsmNetworkToZoningReaderData network2ZoningData;  
  
  /** profiler to collect stats for */
  private final PlanitOsmZoningHandlerProfiler profiler;
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final PlanitOsmPublicTransportModeParser publicTransportModeParser;   
    

  /** create a new but unpopulated transfer zone
   * 
   * @param transferZoneType of the zone
   * @return created transfer zone
   */
  private TransferZone createEmptyTransferZone(TransferZoneType transferZoneType) {
    
    /* create */
    TransferZone transferZone = zoning.transferZones.createNew();
    /* type */
    transferZone.setType(transferZoneType);
    /* xml id = internal id */
    transferZone.setXmlId(String.valueOf(transferZone.getId()));
    
    profiler.logTransferZoneStatus(zoning.transferZones.size());
    return transferZone;
  }

  /** create a transfer zone based on the passed in osm entity, tags for feature extraction and access. Note that we attempt to also
   * parse its reference tags. Currently we look for keys:
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   *  to parse the reference for a transfer zone. If other keys are used, we are not (yet) able to pick them up.
   * 
   * @param osmEntity entity that is to be converted into a transfer zone
   * @param tags tags to extract features from
   * @param transferZoneType the type of the transfer zone 
   * @param geoUtils to use
   * @return transfer zone created
   * @throws PlanItException thrown if error
   */
  private TransferZone createAndPopulateTransferZone(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    TransferZone transferZone = null;
        
    /* Verify if there are nodes missing before extracting geometry, if so and we are near bounding box log this information to user, but avoid logging the
     * regular feedback when nodes are missing, because it lacks context regarding being close to bounding box and would confuse the user */
    Level geometryExtractionLogLevel = LOGGER.getLevel();
    if(Osm4JUtils.getEntityType(osmEntity).equals(EntityType.Way) && !PlanitOsmWayUtils.isAllOsmWayNodesAvailable((OsmWay)osmEntity, network2ZoningData.getOsmNodes())){
      Integer availableOsmNodeIndex = PlanitOsmWayUtils.findFirstAvailableOsmNodeIndexAfter(0,  (OsmWay) osmEntity, network2ZoningData.getOsmNodes());
      if(availableOsmNodeIndex!=null) {
        OsmNode referenceNode = network2ZoningData.getOsmNodes().get(((OsmWay) osmEntity).getNodeId(availableOsmNodeIndex));
        if(PlanitOsmBoundingBoxUtils.isNearNetworkBoundingBox(PlanitOsmNodeUtils.createPoint(referenceNode), network2ZoningData.getNetworkBoundingBox(), geoUtils)) {
          LOGGER.info(String.format("osm waiting area way (%d) geometry incomplete due to network bounding box cut-off, truncated to available nodes",osmEntity.getId()));
          geometryExtractionLogLevel = Level.OFF;
        }
      }/*else {
        not a single node present, this implies entire transfer zone is outside of accepted bounding box, something which we could not verify until now
        in this case, we do not report back to user as this is most likely intended behaviour since bounding box was set by user explicitly
      }*/
    }
    
    /* geometry, either centroid location or polygon circumference */
    Geometry theGeometry = PlanitOsmUtils.extractGeometry(osmEntity, network2ZoningData.getOsmNodes(), geometryExtractionLogLevel);
    if(theGeometry != null && !theGeometry.isEmpty()) {
    
      /* create */
      transferZone = createEmptyTransferZone(transferZoneType);
      transferZone.setGeometry(theGeometry); 
      if(theGeometry instanceof Point) {
        transferZone.getCentroid().setPosition((Point) theGeometry);
      }
      
      /* XML id = internal id*/
      transferZone.setXmlId(Long.toString(osmEntity.getId()));
      /* external id  = osm node id*/
      transferZone.setExternalId(transferZone.getXmlId());
      
      /* name */
      if(tags.containsKey(OsmTags.NAME)) {
        transferZone.setName(tags.get(OsmTags.NAME));
      }    
      
      String refValue = OsmTagUtils.getValueForSupportedRefKeys(tags);
      /* ref (to allow other entities to refer to this transfer zone locally) */
      if(refValue != null) {
        transferZone.addInputProperty(OsmTags.REF, refValue);
      }
      
    }else {
      LOGGER.warning(String.format("Transfer zone not created, geometry incomplete (polygon, line string) for osm way %s, possibly nodes outside bounding box, or invalid OSM entity",osmEntity.getId()));
    }
        
    return transferZone;
  }

  /** create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param geoUtils to use
   * @return transfer zone created, null if something happenned making it impossible to create the zone
   * @throws PlanItException thrown if error
   */
  private TransferZone createAndRegisterTransferZoneWithoutConnectoids(OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    /* create and register */
    TransferZone transferZone = createAndPopulateTransferZone(osmEntity,tags, transferZoneType, geoUtils);
    if(transferZone != null) {
      zoning.transferZones.register(transferZone);
      EntityType entityType = Osm4JUtils.getEntityType(osmEntity);
    
      /* register locally */
      zoningReaderData.getPlanitData().addTransferZoneByOsmId(entityType, osmEntity.getId(), transferZone);
    }
    return transferZone;
  }

  /** Constructor 
   * 
   * @param zoning to use
   * @param zoningReaderData to use
   * @param transferSettings to use
   * @param network2ZoningData to use
   * @param profiler to use
   */
  public PlanitOsmTransferZoneParser(
      Zoning zoning, 
      PlanitOsmZoningReaderData zoningReaderData, 
      PlanitOsmPublicTransportReaderSettings transferSettings, 
      PlanitOsmNetworkToZoningReaderData network2ZoningData, 
      PlanitOsmZoningHandlerProfiler profiler) {
    
    this.zoningReaderData = zoningReaderData;
    this.zoning = zoning;
    this.transferSettings = transferSettings;
    this.network2ZoningData = network2ZoningData;
    this.profiler = profiler;
    
    /* parser for identifying pt PLANit modes from OSM entities */
    this.publicTransportModeParser = new PlanitOsmPublicTransportModeParser(network2ZoningData.getNetworkSettings());
  }

  /** create a dummy transfer zone without access to underlying osmNode or way and without any geometry or populated
   * content other than its ids and type
   * 
   * @param osmId to use
   * @param transferZoneType to use
   * @return created transfer zone
   */
  protected TransferZone createDummyTransferZone(long osmId, TransferZoneType transferZoneType) {
    TransferZone transferZone = createEmptyTransferZone(transferZoneType);
    transferZone.setXmlId(String.valueOf(osmId));
    transferZone.setExternalId(transferZone.getXmlId());
    return transferZone;
  }

  /** attempt to create a new transfer zone and register it, do not yet create connectoids for it. This is postponed because likely at this point in time
   * it is not possible to best determine where they should reside. Find eligible access modes as input properties as well which can be used later
   * to map stop_positions more easily. Note that one can provide a default osm mode that is deemed eligible in case no tags are provided on the osm entity. In case no mode information
   * can be extracted a warning is issued but the transfer zone is still created because this is a tagging error and we might be able to salvage later on. If there are osm modes
   * but none of them are mapped, then we should not create the zone since it will not be of use.
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param defaultOsmMode to apply
   * @param geoUtils to use
   * @return transfer zone created, null if something happened making it impossible or not useful to create the zone
   * @throws PlanItException thrown if error
   */
  protected TransferZone createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(
      OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, String defaultOsmMode, PlanitJtsCrsUtils geoUtils) throws PlanItException {  
    
    TransferZone transferZone = null;
        
    /* tagged osm modes */        
    Pair<Collection<String>, Collection<Mode>> modeResult = publicTransportModeParser.collectPublicTransportModesFromPtEntity(osmEntity.getId(), tags, defaultOsmMode);
    if(!PlanitOsmZoningHandlerHelper.hasEligibleOsmMode(modeResult)) {
      /* no information on modes --> tagging issue, transfer zone might still be needed and could be salvaged based on close by stop_positions with additional information 
       * log issue, yet still create transfer zone (without any osm modes) */
      LOGGER.fine(String.format("SALVAGED: Transfer zone of type %s found for osm entity %d without osm mode support, likely tagging mistake",transferZoneType.name(), osmEntity.getId()));
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType, geoUtils);
    }else if(PlanitOsmZoningHandlerHelper.hasMappedPlanitMode(modeResult)){  
      /* mapped planit modes are available and we should create the transfer zone*/
      transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, transferZoneType, geoUtils);
      PlanitOsmZoningHandlerHelper.addOsmAccessModesToTransferZone(transferZone, modeResult.first());
    }else{
      /* waiting area with valid osm mode, but not mapped to planit mode, mark as such to avoid logging a warning when this transfer zone is part of stop_area 
       * and it cannot be found when we try to collect it */
      zoningReaderData.getOsmData().addWaitingAreaWithoutMappedPlanitMode(Osm4JUtils.getEntityType(osmEntity),osmEntity.getId());
    }
    return transferZone;    
  }

  /** attempt to create a new transfer zone and register it, do not create connectoids for it. Register the provided access modes as eligible by setting them on the input properties 
   * which can be used later to map stop_positions more easily.
   * 
   * @param osmEntity to extract transfer zone for
   * @param tags to use
   * @param transferZoneType to apply
   * @param eligibleOsmModes the eligible osm modes considered
   * @param geoUtils to use
   * @return transfer zone created, null if something happened making it impossible to create the zone
   * @throws PlanItException thrown if error
   */
  protected TransferZone createAndRegisterTransferZoneWithoutConnectoidsSetAccessModes(
      OsmEntity osmEntity, Map<String, String> tags, TransferZoneType transferZoneType, Collection<String> eligibleOsmModes, PlanitJtsCrsUtils geoUtils) throws PlanItException {
    TransferZone transferZone = createAndRegisterTransferZoneWithoutConnectoids(osmEntity, tags, TransferZoneType.PLATFORM, geoUtils);
    if(transferZone != null) {
      PlanitOsmZoningHandlerHelper.addOsmAccessModesToTransferZone(transferZone, eligibleOsmModes);
    }
    return transferZone;
  }

  /** Method that will attempt to create both a transfer zone and its connectoids at the location of the osm node. This is only relevant for very specific types
   * of osm pt nodes, such as tram_stop, some bus_stops that are tagged on the road, and potentially halts and/or stations. In case no existing transfer zone in this
   * location exists, we create one first using the default transfer zone type provided, otherwise we utilise the existing transfer zone
   * 
   * @param osmNode for the location to create both a transfer zone and connectoid(s)
   * @param tags of the node
   * @param defaultOsmMode that is to be expected here
   * @param defaultTransferZoneType in case a transfer zone needs to be created in this location
   * @param geoUtils to use
   * @return created transfer zone (if not already in existence)
   * @throws PlanItException thrown if error
   */  
  protected TransferZone createAndRegisterTransferZoneWithConnectoidsAtOsmNode(
      OsmNode osmNode, Map<String, String> tags, String defaultOsmMode, TransferZoneType defaultTransferZoneType, PlanitJtsCrsUtils geoUtils) throws PlanItException {        
        
    Pair<Collection<String>, Collection<Mode>> modeResult = publicTransportModeParser.collectPublicTransportModesFromPtEntity(osmNode.getId(), tags, defaultOsmMode);
    if(!PlanitOsmZoningHandlerHelper.hasMappedPlanitMode(modeResult)) {    
      throw new PlanItException("Should not attempt to parse osm node %d when no planit modes are activated for it", osmNode.getId());
    }
      
    /* transfer zone */
    TransferZone transferZone = zoningReaderData.getPlanitData().getTransferZoneByOsmId(EntityType.Node,osmNode.getId());
    if(transferZone == null) {
      /* not created for other layer; create and register transfer zone */
      transferZone = createAndRegisterTransferZoneWithoutConnectoidsFindAccessModes(osmNode, tags, defaultTransferZoneType, defaultOsmMode, geoUtils);
      if(transferZone == null) {
        throw new PlanItException("Unable to create transfer zone for osm node %d",osmNode.getId());
      }
    }
    
    /* connectoid(s) */
    for(Mode mode : modeResult.second()) {
      MacroscopicPhysicalNetwork networkLayer = (MacroscopicPhysicalNetwork) transferSettings.getReferenceNetwork().infrastructureLayers.get(mode);             
      
      /* we can immediately create connectoids since Ptv1 tram stop is placed on tracks and no Ptv2 tag is present */
      /* railway generally has no direction, so create connectoid for both incoming directions (if present), so we can service any tram line using the tracks */        
      createAndRegisterDirectedConnectoidsOnTopOfTransferZone(transferZone, networkLayer, mode, geoUtils);      
    }    
    
    return transferZone;
  }

  
  
}

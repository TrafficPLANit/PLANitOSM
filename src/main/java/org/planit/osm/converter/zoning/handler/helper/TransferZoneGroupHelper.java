package org.planit.osm.converter.zoning.handler.helper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.planit.osm.converter.zoning.OsmZoningReaderData;
import org.planit.osm.converter.zoning.handler.OsmZoningHandlerProfiler;
import org.planit.osm.tags.OsmTags;
import org.planit.osm.util.Osm4JUtils;
import org.planit.osm.util.OsmModeUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Class to provide functionality for parsing PLANit transfer zone groups from OSM entities
 * 
 * @author markr
 *
 */
public class TransferZoneGroupHelper extends ZoningHelperBase {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(TransferZoneGroupHelper.class.getCanonicalName());
  
  /** the zoning to work on */
  private final Zoning zoning;
  
  /** zoning reader data used to track created entities */
  private final OsmZoningReaderData zoningReaderData;
      
  /** profiler to collect stats for */
  private final OsmZoningHandlerProfiler profiler;
  
  /** transfer zone parser to access functionality related to transfer zones required fro transfer zone group parser functionality */
  private final TransferZoneHelper transferZoneParser;  
  
  /** parser functionality regarding the extraction of pt modes zones from OSM entities */  
  private final OsmPublicTransportModeHelper ptModeParser;
  
  /** Register a transfer zone on a group by providing the OSM id of the transfer zone and its type, if no transfer zone is available
   * for this combination, false is returned and it is not registered.
   *  
   * @param type of the OSM entity
   * @param osmId OSM id of the transfer zone
   * @param tags (maybe null if not available)
   * @param transferZoneGroup to register on
   * @return true when registered on the group, false otherwise
   */
  private boolean registerTransferZoneOnGroup(long osmId, EntityType type, Map<String, String> tags, TransferZoneGroup transferZoneGroup) {
    /* Should be parsed (with or without connectoids), connect to group and let stop_positions create connectoids */
    TransferZone transferZone = zoningReaderData.getPlanitData().getTransferZoneByOsmId(type, osmId);
    if( transferZone==null) {
      
      /* we do not issue warning when we have a bounding box, as it is possible this is the reason it is not available, not ideal but sufficient for now */
      boolean logDiscardWarning  = false;
      if(!getSettings().hasBoundingPolygon()) {
        /* tags available, use as is to extract mode compatibility for verification if it is rightly not available */
        if(tags!=null) {
          Pair<Collection<String>, Collection<Mode>> modeResult = ptModeParser.collectPublicTransportModesFromPtEntity(osmId, tags, OsmModeUtils.identifyPtv1DefaultMode(tags));
          if( OsmModeUtils.hasEligibleOsmMode(modeResult) && !getSettings().hasBoundingPolygon()) {      
            /* not parsed due to problems (or outside bounding box), discard */
            logDiscardWarning = true;
          }
        }else if(!zoningReaderData.getOsmData().isWaitingAreaWithoutMappedPlanitMode(type, osmId)){
          /* tags not available (because it is a way), it might have been discarded for valid reasons still, if so, it should be registered as a waiting area without mapped modes, if not 
           * issue warning */
          logDiscardWarning = true;
        }
        
        if(logDiscardWarning) {
          LOGGER.warning(String.format("DISCARD: waiting area OSM entity %d (type %s) not available, although referenced by stop_area %s and mode compatible, unable to register on transfer zone group",osmId, type.toString(), transferZoneGroup.getExternalId()));
        }
      }
      return false;      
    }    
        
    transferZoneGroup.addTransferZone(transferZone);
    return true;
  }  
  
  /** Process an OSM entity that is classified as a (train) station. For this to register on the group, we only see if we can utilise its name and use it for the group, but only
   * if the group does not already have a name
   *   
   * @param transferZoneGroup the osm station relates to 
   * @param osmEntityStation of the relation to process
   * @param tags of the osm entity representation a station
   */
  public static void updateTransferZoneGroupStationName(TransferZoneGroup transferZoneGroup, OsmEntity osmEntityStation, Map<String, String> tags) {
    
    if(!transferZoneGroup.hasName()) {
      String stationName = tags.get(OsmTags.NAME);
      if(stationName!=null) {
        transferZoneGroup.setName(stationName);
      }
    }      
  }  

  /** Constructor 
   * 
   * @param zoning to register transfer zone groups on
   * @param zoningReaderData to use
   * @param transferSettings to use 
   * @param profiler to track stats
   */
  public TransferZoneGroupHelper(
      Zoning zoning, 
      OsmZoningReaderData zoningReaderData, 
      OsmPublicTransportReaderSettings transferSettings, 
      OsmZoningHandlerProfiler profiler) {
    
    super(transferSettings);
    
    this.zoning = zoning;
    this.profiler = profiler;
    this.zoningReaderData = zoningReaderData;
    
    transferZoneParser = new TransferZoneHelper(zoning, zoningReaderData, transferSettings, profiler);
    ptModeParser = new OsmPublicTransportModeHelper(transferSettings.getNetworkDataForZoningReader().getNetworkSettings());
  }

  /** Create a transfer zone group based on the passed in OSM entity, tags for feature extraction and access
   * 
   * @param osmRelation the stop_area is based on 
   * @param tags tags to extract features from
   * @param transferZoneGroupType the type of the transfer zone group 
   * @return transfer zone group created
   */  
  public TransferZoneGroup createAndPopulateTransferZoneGroup(OsmRelation osmRelation, Map<String, String> tags) {
      /* create */
      TransferZoneGroup transferZoneGroup = zoning.transferZoneGroups.getFactory().createNew();
            
      /* XML id = internal id*/
      transferZoneGroup.setXmlId(String.valueOf(transferZoneGroup.getId()));
      /* external id  = osm node id*/
      transferZoneGroup.setExternalId(String.valueOf(osmRelation.getId()));
      
      /* name */
      if(tags.containsKey(OsmTags.NAME)) {
        transferZoneGroup.setName(tags.get(OsmTags.NAME));
      }    
      
      return transferZoneGroup;
  }  
  
  /** Create a transfer zone group based on the passed in OSM entity, tags for feature extraction and access and register it
   * 
   * @param osmRelation the stop_area is based on 
   * @param tags tags to extract features from
   * @param transferZoneGroupType the type of the transfer zone group 
   * @return transfer zone group created
   */  
  public TransferZoneGroup createPopulateAndRegisterTransferZoneGroup(OsmRelation osmRelation, Map<String, String> tags) {
      /* create */
      TransferZoneGroup transferZoneGroup = createAndPopulateTransferZoneGroup(osmRelation, tags);
            
      /* register */
      zoning.transferZoneGroups.register(transferZoneGroup);
      zoningReaderData.getPlanitData().addTransferZoneGroupByOsmId(osmRelation.getId(), transferZoneGroup);     
      
      profiler.logTransferZoneGroupStatus(zoning.transferZoneGroups.size());
      return transferZoneGroup;
  }   
  
  /** Find all transfer zone groups with at least one transfer zone that is mode compatible (and planit mode mapped)  with the passed in osm modes
   * In case no eligible modes are provided (null).
   *  
   * @param referenceOsmModes to map agains (may be null)
   * @param potentialTransferZones to extract transfer zone groups from
   * @param allowPseudoModeMatches, when true only broad category needs to match, i.e., both have a road/rail/water mode, when false only exact matches are allowed
   * @return matched transfer zone groups
   */
  public Set<TransferZoneGroup> findModeCompatibleTransferZoneGroups(Collection<String> referenceOsmModes, final Collection<TransferZone> potentialTransferZones, boolean allowPseudoModeMatches) {
    /* find potential matched transfer zones based on mode compatibility while tracking group memberships */
    Set<TransferZoneGroup> potentialTransferZoneGroups = new HashSet<TransferZoneGroup>();
    
    Collection<TransferZone> filteredTransferZones = transferZoneParser.filterModeCompatibleTransferZones(referenceOsmModes, potentialTransferZones, allowPseudoModeMatches);
    if(filteredTransferZones!=null && !filteredTransferZones.isEmpty()) {
      for(TransferZone transferZone : filteredTransferZones) {                     
        /* matched to group and/or zones*/        
        Set<TransferZoneGroup> transferZoneGroups = transferZone.getTransferZoneGroups();
        if(transferZoneGroups!=null && !transferZoneGroups.isEmpty()) {
          potentialTransferZoneGroups.addAll(transferZoneGroups);
        }               
      }
    }
    return potentialTransferZoneGroups;
  }
  
  /** Register a transfer zone on a group by providing the OSM id of the transfer zone and its type, if no transfer zone is available
   * for this combination, false is returned and it is not registered.
   *  
   * @param type of the OSM entity
   * @param osmId OSM id of the transfer zone
   * @param transferZoneGroup to register on
   * @return true when registered on the group, false otherwise
   */
  public boolean registerTransferZoneOnGroup(long osmId, EntityType type, TransferZoneGroup transferZoneGroup) {
    return registerTransferZoneOnGroup(osmId, type, null, transferZoneGroup);
  }

  /** Register a transfer zone on a group by providing the OSM entity, if no transfer zone is available for this combination, 
   * false is returned and it is not registered.
   *  
   * @param osmEntity to collect transfer zone for and register
   * @param transferZoneGroup to register on
   * @return true when registered on the group, false otherwise
   */  
  public boolean registerTransferZoneOnGroup(OsmEntity osmEntity, TransferZoneGroup transferZoneGroup) {
    return registerTransferZoneOnGroup(osmEntity.getId(), Osm4JUtils.getEntityType(osmEntity), OsmModelUtil.getTagsAsMap(osmEntity),transferZoneGroup);
  }  
  
  /** Register a transfer zone on a group by providing the OSM node, if no transfer zone is available for this combination, 
   * false is returned and it is not registered.
   *  
   * @param osmNode to collect transfer zone for and register
   * @param transferZoneGroup to register on
   * @return true when registered on the group, false otherwise
   */  
  public boolean registerTransferZoneOnGroup(OsmNode osmNode, Map<String, String> tags, TransferZoneGroup transferZoneGroup) {
    return registerTransferZoneOnGroup(osmNode.getId(),EntityType.Node, tags,transferZoneGroup);
  }  
}

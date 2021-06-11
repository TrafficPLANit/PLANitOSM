package org.planit.osm.converter.zoning.parser;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.converter.zoning.PlanitOsmPublicTransportReaderSettings;
import org.planit.osm.converter.zoning.PlanitOsmZoningHandlerProfiler;
import org.planit.osm.converter.zoning.PlanitOsmZoningReaderData;
import org.planit.osm.tags.OsmTags;
import org.planit.utils.zoning.TransferZone;
import org.planit.utils.zoning.TransferZoneGroup;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.model.iface.OsmEntity;
import de.topobyte.osm4j.core.model.iface.OsmRelation;

/**
 * Class to provide functionality for parsing PLANit transfer zone groups from OSM entities
 * 
 * @author markr
 *
 */
public class PlanitOsmTransferZoneGroupParser extends PlanitOsmZoningParserBase {
  
  /** logger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmTransferZoneGroupParser.class.getCanonicalName());
  
  /** the zoning to work on */
  private final Zoning zoning;
  
  /** zoning reader data used to track created entities */
  private final PlanitOsmZoningReaderData zoningReaderData;
      
  /** profiler to collect stats for */
  private final PlanitOsmZoningHandlerProfiler profiler;
  
  /** transfer zone parser to access functionality related to transfer zones required fro transfer zone group parser functionality */
  private final PlanitOsmTransferZoneParser transferZoneParser;  
  
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
  public PlanitOsmTransferZoneGroupParser(
      Zoning zoning, 
      PlanitOsmZoningReaderData zoningReaderData, 
      PlanitOsmPublicTransportReaderSettings transferSettings, 
      PlanitOsmZoningHandlerProfiler profiler) {
    
    super(transferSettings);
    
    this.zoning = zoning;
    this.profiler = profiler;
    this.zoningReaderData = zoningReaderData;
    
    transferZoneParser = new PlanitOsmTransferZoneParser(zoning, zoningReaderData, transferSettings, profiler);
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
      TransferZoneGroup transferZoneGroup = zoning.transferZoneGroups.createNew();
            
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
}

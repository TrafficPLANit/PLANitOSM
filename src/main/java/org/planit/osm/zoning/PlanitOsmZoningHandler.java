package org.planit.osm.zoning;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.osm.physical.network.macroscopic.PlanitOsmNetwork;
import org.planit.osm.settings.network.PlanitOsmNetworkSettings;
import org.planit.osm.tags.*;
import org.planit.osm.util.*;

import org.planit.geo.PlanitJtsUtils;
import org.planit.network.InfrastructureLayer;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;
import org.planit.zoning.Zoning;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations to the relevant transfer zones. 
 * 
 * @author markr
 * 
 *
 */
public class PlanitOsmZoningHandler extends DefaultOsmHandler {

  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmZoningHandler.class.getCanonicalName());
  
  /** the network assumed to have been populated already and compatible with the about to be populated zoning */
  private final PlanitOsmNetwork referenceNetwork;
  
  /**
   * the zoning to populate
   */
  private final Zoning zoning;

  /** utilities for geographic information */
  private final PlanitJtsUtils geoUtils;
      
  /** temporary storage of osmNodes before converting to nodes used in the zoning structure*/
  private final Map<Long, OsmNode> osmNodes;
  
  /**
   * check if tags contain entries compatible with the provided Pt scheme
   * @param scheme to check against
   * @param tags to verify
   * @return true when present, false otherwise
   */
  private static boolean isCompatibleWith(OsmPtVersionScheme scheme, Map<String, String> tags) {
    if(scheme.equals(OsmPtVersionScheme.v1)) {
      if(OsmHighwayTags.hasHighwayKeyTag(tags) || OsmRailwayTags.hasRailwayKeyTag(tags)) {
        return OsmPtv1Tags.hasPtv1ValueTag(tags);
      }
    }else if(scheme.equals(OsmPtVersionScheme.v2)) {
      return OsmPtv2Tags.hasPtv2ValueTag(tags);
    }else {
     LOGGER.severe(String.format("unknown OSM public transport scheme %s provided to check compatibility with, ignored",scheme.value()));

    }
    return false;
  }  
  
  /** Verify if passed in tags reflect transfer based infrastructure that is eligible (and supported) to be parsed by this class, e.g.
   * tags related to original PT scheme stops ( railway=halt, railway=tram_stop, highway=bus_stop and highway=platform),
   * or the current v2 PT scheme (public_transport=stop_position, platform, station, stop_area)
   * 
   * @param tags
   * @return true when eligible and identified as transfer based infrastructure, false otherwise
   */
  private static boolean isTransferBasedInfrastructure(Map<String, String> tags) {
    return isCompatibleWith(OsmPtVersionScheme.v1,tags) || isCompatibleWith(OsmPtVersionScheme.v2, tags);
  }  
                                                          
  
  /** verify if tags represent an infrastructure used for transfers between modes, for example PT platforms, stops, etc. 
   * and is also activated for parsing based on the related settings
   * 
   * @param tags to verify
   * @return true when activated and present, false otherwise 
   */  
  private boolean isActivatedTransferBasedInfrastructure(Map<String, String> tags) {
    if(settings.isTransferParserActive()) {
      return isTransferBasedInfrastructure(tags);
    }
    return false;
  }  
  

  /** extract the transfer infrastructure which will contribute to newly created transfer zones on the zoning instance
   * 
   * @param tags to use
   * @throws PlanItException thrown if error
   */
  private void extractOsmTransferInfrastructure(Map<String, String> tags) throws PlanItException{
    // TODO Auto-generated method stub
    
  }  

  /**
   * constructor
   * 
   * @param settings for the handler
   */
  public PlanitOsmZoningHandler(final PlanitOsmNetwork referenceNetwork, final Zoning zoningToPopulate) {
    this.referenceNetwork = referenceNetwork;
    this.zoning = zoningToPopulate;
    
    /* gis initialisation */
    this.geoUtils = new PlanitJtsUtils(referenceNetwork.getCoordinateReferenceSystem());
    
    /* prep */    
    this.osmNodes = new HashMap<Long, OsmNode>();
  }
  
  /**
   * Call this BEFORE we parse the OSM network to initialise the handler properly
   * @throws PlanItException 
   */
  public void initialiseBeforeParsing() throws PlanItException {
    PlanItException.throwIf(referenceNetwork.infrastructureLayers == null || referenceNetwork.infrastructureLayers.size()<=0,"network is expected to be populated at start of parsing OSM zoning");       
  }  


  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // not used
  }

  /**
   * construct PLANit nodes/connectoids/transferzones from OSM nodes when relevant
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {
    /* store for later processing */
    osmNodes.put(osmNode.getId(), osmNode);   
  }

  /**
   * parse an osm way to extract for example platforms, or other transfer zone related geometry
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
              
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);          
    try {              
      
      /* only parse ways that are potentially road infrastructure */
      if(isActivatedTransferBasedInfrastructure(tags)) {
        
        /* extract the (pt) transfer infrastructure to populate the PLANit memory model with */ 
        extractOsmTransferInfrastructure(tags);
      }
      
    } catch (PlanItException e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Error during parsing of OSM way (id:%d)", osmWay.getId())); 
    }      
            
  }

  /**
   * {@inheritDoc}
   */  
  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    // delegate
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void complete() throws IOException {
                
    LOGGER.info(" OSM (transfer) zone parsing...DONE");

  }

  /**
   * reset the contents, mainly to free up unused resources 
   */
  public void reset() {   
    osmNodes.clear();
  }

}

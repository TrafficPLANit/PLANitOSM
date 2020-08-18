package org.planit.osm.physical.network.macroscopic;

import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.collections4.map.HashedMap;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.util.PlanitOSMTags;
import org.planit.osm.util.PlanitOsmUtils;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

/**
 * Macroscopic network with additional OSM functionality
 * 
 * Disclaimer: The descriptions for the default OSM link segment types have been copied from the OSM Wiki
 *  
 * @author markr
 *
 */
public class PLANitOSMNetwork extends MacroscopicNetwork {

  /**
   * generated uid
   */
  private static final long serialVersionUID = -2227509715172627526L;
  
  /**
   * The logger
   */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PLANitOSMNetwork.class.getCanonicalName());
   
  /**
   *  Create an OSM default link segment type (no mode properties)
   * @param name name of the type
   * @param capacity capacity in pcu/h
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createDefaultLinkSegmentType(String name, double capacityPcuPerhour) throws PlanItException {
    return this.createAndRegisterNewMacroscopicLinkSegmentType(
        name, capacityPcuPerhour, PlanitOsmUtils.DEFAULT_MAX_DENSITY_LANE, -1);
  }
  
  /**
   * Create motorway type with defaults
   * 
   * restricted access major divided highway, normally with 2 or more running lanes 
   * plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc.. 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createMotorway() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.MOTORWAY, PlanitOsmUtils.MOTORWAY_CAPACITY);    
  }  
  
  /**
   * Create motorway link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a motorway from/to a motorway or lower class highway.
   *  Normally with the same motorway restrictions. 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createMotorwayLink() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.MOTORWAY_LINK, PlanitOsmUtils.MOTORWAY_LINK_CAPACITY);    
  }    
  
  /**
   * Create trunk type with defaults
   * 
   * The most important roads in a country's system that aren't motorways. 
   * (Need not necessarily be a divided highway.) 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createTrunk() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.TRUNK, PlanitOsmUtils.TRUNK_CAPACITY);    
  }  
  
  /**
   * Create trunk link type with defaults
   * 
   * restricted access major divided highway, normally with 2 or more running lanes 
   * plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc.. 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createTrunkLink() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.TRUNK_LINK, PlanitOsmUtils.TRUNK_LINK_CAPACITY);    
  }   
  
  /**
   * Create primary type with defaults
   * 
   * The next most important roads in a country's system (after trunk). (Often link larger towns.)  
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createPrimary() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.PRIMARY, PlanitOsmUtils.PRIMARY_CAPACITY);    
  }  
  
  /**
   * Create primary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or 
   * lower class highway. 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createPrimaryLink() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.PRIMARY_LINK, PlanitOsmUtils.PRIMARY_LINK_CAPACITY);    
  }   
  
  /**
   * Create secondary type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or 
   * lower class highway.
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createSecondary() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.SECONDARY, PlanitOsmUtils.SECONDARY_CAPACITY);    
  }  
  
  /**
   * Create secondary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a secondary road from/to a secondary road or lower class highway. 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createSecondaryLink() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.SECONDARY_LINK, PlanitOsmUtils.SECONDARY_LINK_CAPACITY);    
  }   
  
  /**
   * Create tertiary type with defaults
   * 
   * The next most important roads in a country's system (after secondary). (Often link smaller towns and villages) 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createTertiary() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.TERTIARY, PlanitOsmUtils.TERTIARY_CAPACITY);    
  }  
  
  /**
   * Create tertiary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a tertiary road from/to a tertiary road or lower class highway.  
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createTertiaryLink() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.TERTIARY_LINK, PlanitOsmUtils.TERTIARY_LINK_CAPACITY);    
  }   
  
  /**
   * Create unclassified type with defaults
   * 
   * The least important through roads in a country's system – i.e. minor roads of a lower classification than tertiary, but which serve a purpose other than access to properties. (Often link villages and hamlets.)   
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createUnclassified() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.UNCLASSIFIED, PlanitOsmUtils.UNCLASSIFIED_LINK_CAPACITY);    
  }   
  
  /**
   * Create residential type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createResidential() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.RESIDENTIAL, PlanitOsmUtils.RESIDENTIAL_LINK_CAPACITY);    
  }    
  
  /**
   * Create living street type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createLivingStreet() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.LIVING_STREET, PlanitOsmUtils.LIVING_STREET_LINK_CAPACITY);    
  }    
  
  /**
   * Create service type with defaults
   * 
   * For access roads to, or within an industrial estate, camp site, business park, car park, alleys, etc.     
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createService() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.SERVICE, PlanitOsmUtils.SERVICE_CAPACITY);    
  }      
  
  /**
   * Create pedestrian type with defaults
   * 
   * For roads used mainly/exclusively for pedestrians in shopping and some residential areas which may allow access by motorised vehicles only for very limited periods of the day.     
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createPedestrian() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.PEDESTRIAN, PlanitOsmUtils.PEDESTRIAN_CAPACITY);    
  }   
  
  /**
   * Create track type with defaults
   * 
   * Roads for mostly agricultural or forestry uses.    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createTrack() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.TRACK, PlanitOsmUtils.TRACK_CAPACITY);    
  }   
  
  /**
   * Create road type with defaults
   * 
   * A road/way/street/motorway/etc. of unknown type. It can stand for anything ranging from a footpath to a 
   * motorway. This tag should only be used temporarily until the road/way/etc. has been properly surveyed.     
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  private MacroscopicLinkSegmentType createRoad() throws PlanItException {
    return createDefaultLinkSegmentType(PlanitOSMTags.ROAD, PlanitOsmUtils.ROAD_CAPACITY);    
  }   
   
  
  /**
   * the link segment types that were created for all default OSM types
   */
  protected Map<String, MacroscopicLinkSegmentType> defaultOSMLinkSegmentTypes;  

  /**
   * Constructor
   */
  public PLANitOSMNetwork(IdGroupingToken groupId) {
    super(groupId);
    
    try {
      this.defaultOSMLinkSegmentTypes = createDefaultOSMLinkSegmentTypes();
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default OSM link segment types for this network");
    }
  }

  /**
   * Since we are building a macroscopic network based on OSM, we provide a mapping from
   * the common OSM highway types to macroscopic link segment types that go with it
   * 
   * @return the default created 
   * @throws PlanItException thrown when error
   */
  protected Map<String, MacroscopicLinkSegmentType> createDefaultOSMLinkSegmentTypes() throws PlanItException {
    Map<String, MacroscopicLinkSegmentType> types = new HashedMap<String, MacroscopicLinkSegmentType>();
    types.put(PlanitOSMTags.MOTORWAY, createMotorway());
    types.put(PlanitOSMTags.MOTORWAY_LINK, createMotorwayLink());
    types.put(PlanitOSMTags.TRUNK, createTrunk());
    types.put(PlanitOSMTags.TRUNK_LINK, createTrunkLink());
    types.put(PlanitOSMTags.PRIMARY, createPrimary());
    types.put(PlanitOSMTags.PRIMARY_LINK, createPrimaryLink());
    types.put(PlanitOSMTags.SECONDARY, createSecondary());
    types.put(PlanitOSMTags.SECONDARY_LINK, createSecondary());
    types.put(PlanitOSMTags.SECONDARY_LINK, createSecondaryLink());
    types.put(PlanitOSMTags.TERTIARY, createTertiary());
    types.put(PlanitOSMTags.TERTIARY_LINK, createTertiaryLink());
    types.put(PlanitOSMTags.UNCLASSIFIED, createUnclassified());
    types.put(PlanitOSMTags.RESIDENTIAL, createResidential());
    types.put(PlanitOSMTags.LIVING_STREET, createLivingStreet());
    types.put(PlanitOSMTags.SERVICE, createService());
    types.put(PlanitOSMTags.PEDESTRIAN, createPedestrian());
    types.put(PlanitOSMTags.TRACK, createTrack());
    types.put(PlanitOSMTags.ROAD, createRoad());
    return types;
  }

  /**
   * find the link segment type by the Highway=value, where we pass in the "value"
   *  
   * @param OSMHighwayTagValue the type of road to find
   * @return the link segment type that is registered
   */
  public MacroscopicLinkSegmentType getSegmentTypeByOSMTag(String osmHighwayTagValue) {
    return defaultOSMLinkSegmentTypes.get(osmHighwayTagValue);
  }

  
}

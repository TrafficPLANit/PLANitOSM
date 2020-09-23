package org.planit.osm.physical.network.macroscopic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.defaults.OsmModeAccessDefaults;
import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.PlanitOsmConstants;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

/**
 * Macroscopic network with additional OSM functionality
 * 
 * Disclaimer: The descriptions for the default OSM link segment types have been copied from the OSM Wiki
 *  
 * @author markr
 *
 */
public class PlanitOsmNetwork extends MacroscopicNetwork {

  /**
   * generated uid
   */
  private static final long serialVersionUID = -2227509715172627526L;
    
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetwork.class.getCanonicalName());
  
  /** Create a link segment type on the network based on the passed in OSM highway sub type tage
   * @param activatedType osm highway subtag
   * @return created link segment type if available
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmLinkSegmentType(String activatedType) throws PlanItException {
    /* create link segment type for the OSM type */
    switch (activatedType) {
    case OsmHighwayTags.MOTORWAY:
      return createMotorway();
    case OsmHighwayTags.MOTORWAY_LINK:
      return createMotorwayLink();
    case OsmHighwayTags.TRUNK:
      return createTrunk();
    case OsmHighwayTags.TRUNK_LINK:
      return createTrunkLink();
    case OsmHighwayTags.PRIMARY:
      return createPrimary();
    case OsmHighwayTags.PRIMARY_LINK:
      return createPrimaryLink();
    case OsmHighwayTags.SECONDARY:
      return createSecondary();
    case OsmHighwayTags.SECONDARY_LINK:
      return createSecondaryLink();
    case OsmHighwayTags.TERTIARY:
      return createTertiary();
    case OsmHighwayTags.TERTIARY_LINK:
      return createTertiaryLink();
    case OsmHighwayTags.UNCLASSIFIED:
      return createUnclassified();
    case OsmHighwayTags.RESIDENTIAL:
      return createResidential();
    case OsmHighwayTags.LIVING_STREET:
      return createLivingStreet();
    case OsmHighwayTags.SERVICE:
      return createService();
    case OsmHighwayTags.PEDESTRIAN:
      return createPedestrian();
    case OsmHighwayTags.TRACK:
      return createTrack();
    case OsmHighwayTags.ROAD:
      return createRoad();          
    default:
      throw new PlanItException(
          String.format("OSM type is supported but factory method is missing, unexpected for type highway:%s",activatedType));
    }   
  }     
  
  /**
   *  Create an OSM default link segment type (no mode properties)
   * @param name name of the type
   * @param capacityPcuPerhour capacity in pcu/h
   * @param maxDensityPcuPerKm max density
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmLinkSegmentType(String name, double capacityPcuPerhour, double maxDensityPcuPerKm) throws PlanItException {
    return this.createAndRegisterNewMacroscopicLinkSegmentType(name, capacityPcuPerhour, maxDensityPcuPerKm, name);
  }  
   
  /**
   *  Create an OSM default link segment type (no mode properties)
   *  
   * @param name name of the type
   * @param capacity capacity in pcu/h
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createDefaultOsmLinkSegmentType(String name, double capacityPcuPerhour) throws PlanItException {
    return createOsmLinkSegmentType(name, capacityPcuPerhour, PlanitOsmConstants.DEFAULT_MAX_DENSITY_LANE);
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
  protected MacroscopicLinkSegmentType createMotorway() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.MOTORWAY, PlanitOsmConstants.MOTORWAY_CAPACITY);    
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
  protected MacroscopicLinkSegmentType createMotorwayLink() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.MOTORWAY_LINK, PlanitOsmConstants.MOTORWAY_LINK_CAPACITY);    
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
  protected MacroscopicLinkSegmentType createTrunk() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TRUNK, PlanitOsmConstants.TRUNK_CAPACITY);    
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
  protected MacroscopicLinkSegmentType createTrunkLink() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TRUNK_LINK, PlanitOsmConstants.TRUNK_LINK_CAPACITY);    
  }   
  
  /**
   * Create primary type with defaults
   * 
   * The next most important roads in a country's system (after trunk). (Often link larger towns.)  
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createPrimary() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PRIMARY, PlanitOsmConstants.PRIMARY_CAPACITY);    
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
  protected MacroscopicLinkSegmentType createPrimaryLink() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PRIMARY_LINK, PlanitOsmConstants.PRIMARY_LINK_CAPACITY);    
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
  protected MacroscopicLinkSegmentType createSecondary() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.SECONDARY, PlanitOsmConstants.SECONDARY_CAPACITY);    
  }  
  
  /**
   * Create secondary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a secondary road from/to a secondary road or lower class highway. 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createSecondaryLink() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.SECONDARY_LINK, PlanitOsmConstants.SECONDARY_LINK_CAPACITY);    
  }   
  
  /**
   * Create tertiary type with defaults
   * 
   * The next most important roads in a country's system (after secondary). (Often link smaller towns and villages) 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createTertiary() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TERTIARY, PlanitOsmConstants.TERTIARY_CAPACITY);    
  }  
  
  /**
   * Create tertiary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a tertiary road from/to a tertiary road or lower class highway.  
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createTertiaryLink() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TERTIARY_LINK, PlanitOsmConstants.TERTIARY_LINK_CAPACITY);    
  }   
  
  /**
   * Create unclassified type with defaults
   * 
   * The least important through roads in a country's system – i.e. minor roads of a lower classification than tertiary, but which serve a purpose other than access to properties. (Often link villages and hamlets.)   
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createUnclassified() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.UNCLASSIFIED, PlanitOsmConstants.UNCLASSIFIED_LINK_CAPACITY);    
  }   
  
  /**
   * Create residential type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createResidential() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.RESIDENTIAL, PlanitOsmConstants.RESIDENTIAL_LINK_CAPACITY);    
  }    
  
  /**
   * Create living street type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createLivingStreet() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.LIVING_STREET, PlanitOsmConstants.LIVING_STREET_LINK_CAPACITY);    
  }    
  
  /**
   * Create service type with defaults
   * 
   * For access roads to, or within an industrial estate, camp site, business park, car park, alleys, etc.     
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createService() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.SERVICE, PlanitOsmConstants.SERVICE_CAPACITY);    
  }      
  
  /**
   * Create pedestrian type with defaults
   * 
   * For roads used mainly/exclusively for pedestrians in shopping and some residential areas which may allow access by motorised vehicles only for very limited periods of the day.     
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createPedestrian() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PEDESTRIAN, PlanitOsmConstants.PEDESTRIAN_CAPACITY);    
  }   
  
  /**
   * Create track type with defaults
   * 
   * Roads for mostly agricultural or forestry uses.    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createTrack() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TRACK, PlanitOsmConstants.TRACK_CAPACITY);    
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
  protected MacroscopicLinkSegmentType createRoad() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.ROAD, PlanitOsmConstants.ROAD_CAPACITY);    
  }
   
  /**
   * the list of types for which we have default link segment type mapping available out of the box
   * 
   **/
  protected static final Set<String> supportedOSMLinkSegmentTypes;
  
  /** the supported types for which we have default link segment type settings available */
  static {
    supportedOSMLinkSegmentTypes = new HashSet<String>();
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TRUNK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TRUNK_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.PRIMARY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.PRIMARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SECONDARY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TERTIARY);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TERTIARY_LINK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.UNCLASSIFIED);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.RESIDENTIAL);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.LIVING_STREET);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.SERVICE);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.PEDESTRIAN);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.TRACK);
    supportedOSMLinkSegmentTypes.add(OsmHighwayTags.ROAD);     
  }
     
  /**
   * the link segment types that are activated for this instance
   */
  protected final Map<String, MacroscopicLinkSegmentType> createdOSMLinkSegmentTypes;
    
  /**
   * Create the link segment types that are marked in the passed in settings. As long as they have defaults that
   * are supported, these will be created as indicated. If not available a warning is issued and a link segment type is created based on the default chosen in settings
   * 
   * @return the default created supported types 
   * @throws PlanItException thrown when error
   */
  void createOSMCompatibleLinkSegmentTypes(PlanitOsmSettings settings) throws PlanItException {
    for(String activatedType : settings.supportedOSMLinkSegmentTypes) {
      String osmHighwayTypeToUse = activatedType;
      
      /* find allowed OSM modes for this highway type, construct the mapped PLANit modes and properties for them */
      OsmModeAccessDefaults modeAccessconfiguration = settings.getModeAccessConfiguration();
      Collection<String> allowedOsmModes =  modeAccessconfiguration.collectAllowedModes(osmHighwayTypeToUse);
      Collection<Mode> allowedPlanitModes = settings.collectMappedPlanitModes(allowedOsmModes);
      CONTINUE TO CREAT THE MODE PROPERTIES
      
      MacroscopicLinkSegmentType linkSegmentType = null;  
      boolean isOverwrite = false;
      boolean isBackupDefault = false;
      if(settings.isOSMHighwayTypeDefaultOverwritten(activatedType)) {
        /* type is overwritten, so use overwritten data instead of defaults */
        final Pair<Double,Double> capacityDensityPair = settings.getOSMHighwayTypeOverwrite(activatedType);
        linkSegmentType = createOsmLinkSegmentType(activatedType, capacityDensityPair.getFirst(), capacityDensityPair.getSecond());
        isOverwrite = true;
      }else
      {      
        /* type is supposed to be included according to settings, continue...*/
        if(!supportedOSMLinkSegmentTypes.contains(activatedType)) {        
  
          if(!settings.hasOSMHighwayTypeWhenUnsupported()) {
            /* ... no replacement available skip type entirely*/
            LOGGER.warning(String.format(
                "Highway type (%s) chosen to be included in network, but not available as supported type by reader, exclude from processing %s", activatedType));          
            continue;
          }
          
          /* ...use replacement type instead of activate type to still be able to process OSM ways of this type */
          osmHighwayTypeToUse = settings.getOSMHighwayTypeWhenUnsupported();
          isBackupDefault = true;
          LOGGER.warning(String.format(
              "Highway type (%s) chosen to be included in network, but not available as supported type by reader, reverting to backup default %s", activatedType, osmHighwayTypeToUse));        
        }
        linkSegmentType = createOsmLinkSegmentType(osmHighwayTypeToUse);        
      }                
      
      /* create, register, and also store by osm tag */
      createdOSMLinkSegmentTypes.put(activatedType, linkSegmentType);
      
      LOGGER.info(String.format("%s %s highway:%s - capacity: %.2f (pcu/lane/h) max density %.2f (pcu/km/lane", 
          isOverwrite ? "[OVERWRITE]" : "[DEFAULT]", isBackupDefault ? "[BACKUP]" : "", activatedType, linkSegmentType.getCapacityPerLane(),linkSegmentType.getMaximumDensityPerLane()));      
    }
  }
  
  /**
   * Constructor
   */
  public PlanitOsmNetwork(IdGroupingToken groupId) {
    super(groupId);    
    this.createdOSMLinkSegmentTypes = new HashMap<String, MacroscopicLinkSegmentType>();
  }

  /**
   * find the link segment type by the Highway=value, where we pass in the "value"
   *  
   * @param OSMHighwayTagValue the type of road to find
   * @return the link segment type that is registered
   */
  public MacroscopicLinkSegmentType getSegmentTypeByOSMTag(String osmHighwayTagValue) {
    return this.createdOSMLinkSegmentTypes.get(osmHighwayTagValue);
  }

  
}

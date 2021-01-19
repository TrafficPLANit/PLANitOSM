package org.planit.osm.physical.network.macroscopic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.network.macroscopic.physical.MacroscopicModePropertiesFactory;
import org.planit.network.macroscopic.physical.MacroscopicPhysicalNetwork;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailWayTags;
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
    
  /** Create a link segment type on the network based on the passed in OSM highway value tags
   * 
   * @param highwayTypeValue of OSM way key
   * @return created link segment type if available
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmRoadWayLinkSegmentType(String highwayTypeValue) throws PlanItException {
    /* create link segment type for the OSM type */
    switch (highwayTypeValue) {
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
      case OsmHighwayTags.PATH:
        return createPath();
      case OsmHighwayTags.STEPS:
        return createSteps();
      case OsmHighwayTags.FOOTWAY:
        return createFootway();
      case OsmHighwayTags.CYCLEWAY:
        return createCycleway();        
      case OsmHighwayTags.TRACK:
        return createTrack();
      case OsmHighwayTags.ROAD:
        return createRoad();
      case OsmHighwayTags.BRIDLEWAY:
        return createBridleway();           
      default:
        throw new PlanItException(
            String.format("OSM type is supported but factory method is missing, unexpected for type highway:%s",highwayTypeValue));
      }  
    }  
  
  /** Create a link segment type on the network based on the passed in OSM railway value tags
   * 
   * @param railwayTypeValue of OSM way key
   * @return created link segment type if available
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmRailWayLinkSegmentType(String railwayTypeValue) throws PlanItException {
    /* create link segment type for the OSM type */
    switch (railwayTypeValue) {
      case OsmRailWayTags.FUNICULAR:
        return createFunicular();
      case OsmRailWayTags.LIGHT_RAIL:
        return createLightRail();
      case OsmRailWayTags.MONO_RAIL:
        return createMonoRail();
      case OsmRailWayTags.NARROW_GAUGE:
        return createNarrowGauge();
      case OsmRailWayTags.RAIL:
        return createRail();
      case OsmRailWayTags.SUBWAY:
        return createSubway();
      case OsmRailWayTags.TRAM:
        return createTram();        
      default:
        throw new PlanItException(
            String.format("OSM type is supported but factory method is missing, unexpected for type railway:%s",railwayTypeValue));
      }  
    }    
  
  /** Create a link segment type on the network based on the passed in OSM highway/railway value tags
   * 
   * @param wayTypeKey key for the way type (highway, railway)
   * @param wayTypeValue of OSM way key (road, rail)
   * @return created link segment type if available
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmLinkSegmentType(String wayTypeKey, String wayTypeValue) throws PlanItException {
    switch (wayTypeKey) {
      case OsmHighwayTags.HIGHWAY:
        return createOsmRoadWayLinkSegmentType(wayTypeValue);
      case OsmRailWayTags.RAILWAY:
        return createOsmRailWayLinkSegmentType(wayTypeValue);       
      default:
        throw new PlanItException(String.format("OSM way type %s is unknown supported, unable to create link segment type %s",wayTypeKey, wayTypeValue));
    } 
  }     
  
  /**
   *  Create an OSM default link segment type (no mode properties)
   * @param externalId of the type
   * @param capacityPcuPerhour capacity in pcu/h
   * @param maxDensityPcuPerKm max density
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmLinkSegmentType(String externalId, double capacityPcuPerhour, double maxDensityPcuPerKm) throws PlanItException {
    MacroscopicLinkSegmentType linkSegmentType = getDefaultNetworkLayer().linkSegmentTypes.createAndRegisterNew(externalId, capacityPcuPerhour, maxDensityPcuPerKm);
    /* XML id */
    linkSegmentType.setXmlId(Long.toString(linkSegmentType.getId()));
    /* external id */
    linkSegmentType.setExternalId(externalId);
    return linkSegmentType;
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
   * Create path type with defaults
   * 
   * A non-specific path either multi-use or unspecified usage, open to all non-motorized vehicles and not intended for motorized vehicles unless tagged so separately    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createPath() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PATH, PlanitOsmConstants.PATH_CAPACITY);    
  }    
  
  /**
   * Create step type with defaults
   * 
   * For flights of steps (stairs) on footways    
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createSteps() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.STEPS, PlanitOsmConstants.STEPS_CAPACITY);    
  }   
  
  /**
   * Create footway type with defaults
   * 
   * For designated footpaths; i.e., mainly/exclusively for pedestrians. This includes walking tracks and gravel paths.   
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createFootway() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.FOOTWAY, PlanitOsmConstants.FOOTWAY_CAPACITY);    
  }   
  
  /**
   * Create cycleway type with defaults
   * 
   * For designated cycleways   
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createCycleway() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.CYCLEWAY, PlanitOsmConstants.CYCLEWAY_CAPACITY);    
  }  
  
  /**
   * Create bridleway type with defaults
   * 
   * For horse riders.   
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createBridleway() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.BRIDLEWAY, PlanitOsmConstants.BRIDLEWAY_CAPACITY);    
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
   * Create funicular (rail) type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createFunicular() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.FUNICULAR, PlanitOsmConstants.RAILWAY_CAPACITY);    
  }   
  
  /**
   * Create light rail (rail) type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createLightRail() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.LIGHT_RAIL, PlanitOsmConstants.RAILWAY_CAPACITY);    
  }   
  
  /**
   * Create mono rail (rail) type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createMonoRail() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.MONO_RAIL, PlanitOsmConstants.RAILWAY_CAPACITY);    
  }    
  
  /**
   * Create narrow gauge(rail) type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createNarrowGauge() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.NARROW_GAUGE, PlanitOsmConstants.RAILWAY_CAPACITY);    
  }    
  
  /**
   * Create rail type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createRail() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.RAIL, PlanitOsmConstants.RAILWAY_CAPACITY);    
  }    
  
  /**
   * Create subway type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createSubway() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.SUBWAY, PlanitOsmConstants.RAILWAY_CAPACITY);    
  } 
  
  /**
   * Create tram type with defaults
   * 
   * 
   * @return created type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createTram() throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailWayTags.TRAM, PlanitOsmConstants.RAILWAY_CAPACITY);    
  }  
   
  /**
   * the list of road types for which we have default link segment type mapping available out of the box
   * 
   **/
  protected static final Set<String> supportedOsmRoadLinkSegmentTypes;

  /**
   * the list of rail types for which we have default link segment type mapping available out of the box
   * 
   **/  
  protected static final Set<String> supportedOsmRailLinkSegmentTypes;
  
  /** the supported types for which we have default road link segment type settings available */
  static {
    supportedOsmRoadLinkSegmentTypes = new HashSet<String>();
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY_LINK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.TRUNK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.TRUNK_LINK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.PRIMARY);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.PRIMARY_LINK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.SECONDARY);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.TERTIARY);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.TERTIARY_LINK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.UNCLASSIFIED);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.RESIDENTIAL);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.LIVING_STREET);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.SERVICE);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.PEDESTRIAN);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.PATH); 
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.STEPS); 
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.FOOTWAY);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.CYCLEWAY);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.TRACK);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.ROAD);
    supportedOsmRoadLinkSegmentTypes.add(OsmHighwayTags.BRIDLEWAY);
  }
  
  /** the supported types for which we have default rail link segment type settings available */
  static {
    supportedOsmRailLinkSegmentTypes = new HashSet<String>();
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.FUNICULAR);
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.LIGHT_RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.MONO_RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.NARROW_GAUGE);
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.SUBWAY);
    supportedOsmRailLinkSegmentTypes.add(OsmRailWayTags.TRAM);
  }  
     
  /**
   * the link segment types that are activated for this instance
   */
  protected final Map<String, MacroscopicLinkSegmentType> defaultPlanitOsmLinkSegmentTypes;
    
  /** collect the PLANit mode that are mapped, i.e., are marked to be activated in the final network.
   * @param osmWayKey to collect for
   * @param osmWayValue to collect for
   * @param settings to collect from
   * @return mappedPLANitModes, empty if no modes are mapped
   */
  protected Collection<Mode> collectMappedPlanitModes(String osmWayKey, String osmWayValue, PlanitOsmSettings settings) {
    Collection<String> allowedOsmModes =  settings.getModeAccessConfiguration().collectAllowedModes(osmWayKey, osmWayValue);
    return settings.collectMappedPlanitModes(allowedOsmModes);
  }     
  
  /**
   *  create the road based link segment type based on the setting
   * @param osmWayValue to use
   * @param settings to extract defaults from
   * @return created (or already existing) default link segment type for the given OSM highway type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmCompatibleRoadLinkSegmentType(final String osmWayValue, final PlanitOsmSettings settings) throws PlanItException {
    MacroscopicLinkSegmentType linkSegmentType = null; 
    
    /* only when way type is marked as supported in settings we parse it */
    if(settings.isOsmWayTypeActivated(OsmHighwayTags.HIGHWAY, osmWayValue)) {           
      
      boolean isOverwrite = settings.isOsmHighwayTypeDefaultOverwritten(osmWayValue);
      boolean isBackupDefault = false;          
      
        
      String osmWayValueToUse = osmWayValue;
      if(!supportedOsmRoadLinkSegmentTypes.contains(osmWayValue)){
        /* ...use replacement type instead of activate type to still be able to process OSM ways of this type, if no replacement is set, we revert to null to indicate we cannot support this way type */        
        osmWayValueToUse = settings.isApplyDefaultWhenOsmHighwayTypeDeactivated() ? settings.getDefaultOsmHighwayTypeWhenDeactivated() : null ;
        if(osmWayValueToUse != null) {
          isBackupDefault = true;
          LOGGER.info(String.format("Highway type %s chosen to be included in network, but not available as supported type by reader, reverting to backup default %s", osmWayValue, osmWayValueToUse));
        }else {
          LOGGER.info(String.format("Highway type %s chosen to be included in network, but not activated in reader nor is a default fallback activated, ignored", osmWayValue, osmWayValueToUse));          
        }
      }
      
      /* when valid osm value is found continue */
      if(osmWayValueToUse != null) {
        /* when way value has not been registered yet,duplicates may occur when way value is replaced with default when not supported */ 
        if(!defaultPlanitOsmLinkSegmentTypes.containsKey(osmWayValueToUse)) {
        
          /* Only when one or more OSM modes are mapped to PLANit modes, the osm way type will be used, otherwise it is ignored */
          Collection<Mode> activatedPlanitModes = collectMappedPlanitModes(OsmHighwayTags.HIGHWAY, osmWayValueToUse, settings);          
          if(!activatedPlanitModes.isEmpty()) {
  
            /* create the planit link segment type based on OSM tag */
            if(isOverwrite) {
              /* type is overwritten, so use overwritten data instead of defaults */
              final Pair<Double,Double> capacityDensityPair = settings.getOsmHighwayTypeOverwrite(osmWayValueToUse);
              linkSegmentType = createOsmLinkSegmentType(osmWayValue, capacityDensityPair.first(), capacityDensityPair.second());
            }else {
              /* use default link segment type values */
              linkSegmentType = createOsmLinkSegmentType(OsmHighwayTags.HIGHWAY, osmWayValueToUse);            
            }
            
            /* name based on OSM highway= <value> */
            linkSegmentType.setName(osmWayValueToUse);
            
            /* mode properties */
            double osmHighwayTypeMaxSpeed = settings.getDefaultSpeedLimitByOsmWayType(OsmHighwayTags.HIGHWAY, osmWayValueToUse);
            addLinkSegmentTypeModeProperties(linkSegmentType, activatedPlanitModes, osmHighwayTypeMaxSpeed);     
            
            /** convert to comma separated string by mode name */
            String csvModeString = String.join(",", linkSegmentType.getAvailableModes().stream().map( (mode) -> {return mode.getName();}).collect(Collectors.joining(",")));
            LOGGER.info(String.format("%s%s highway:%s - modes: %s speed: %.2f (km/h) capacity: %.2f (pcu/lane/h), max density %.2f (pcu/km/lane)", 
                isOverwrite ? "[OVERWRITE] " : "[DEFAULT]", isBackupDefault ? "[BACKUP]" : "", osmWayValueToUse, csvModeString, osmHighwayTypeMaxSpeed, linkSegmentType.getCapacityPerLane(),linkSegmentType.getMaximumDensityPerLane()));
          }else {
            linkSegmentType = defaultPlanitOsmLinkSegmentTypes.get(osmWayValueToUse);
          }
          
        }else {
          LOGGER.warning(String.format("highway:%s is supported but none of the default modes are mapped, type ignored", osmWayValueToUse));
        }
                 
      }else {
        /* ... not supported and no replacement available skip type entirely*/
        LOGGER.info(String.format(
            "Highway type (%s) chosen to be included in network, but not available as supported type by reader, exclude from processing", osmWayValue));
      }     
    }
    return linkSegmentType;
  }
 

  /**
   *  create the rail based link segment type based on the setting
   * @param osmWayValue to use
   * @param settings to extract defaults from
   * @return created (or already existing) default link segment type for the given OSM highway type
   * @throws PlanItException thrown if error
   */
  protected MacroscopicLinkSegmentType createOsmCompatibleRailLinkSegmentType(final String osmWayValue, final PlanitOsmSettings settings) throws PlanItException {
    MacroscopicLinkSegmentType linkSegmentType = null;
  
    /* only when way type is marked as supported in settings we parse it */
    if(settings.isOsmWayTypeActivated(OsmRailWayTags.RAILWAY, osmWayValue)) {
      
      Collection<Mode> activatedPlanitModes = collectMappedPlanitModes(OsmRailWayTags.RAILWAY, osmWayValue, settings);
      if(!activatedPlanitModes.isEmpty()) {
        
        /* create the PLANit link segment type based on OSM way tag */
        linkSegmentType = createOsmLinkSegmentType(OsmRailWayTags.RAILWAY, osmWayValue);
        
        /* name based on OSM railway= <value> */
        linkSegmentType.setName(osmWayValue);
        
        /* mode properties */
        double osmHighwayTypeMaxSpeed = settings.getDefaultSpeedLimitByOsmWayType(OsmRailWayTags.RAILWAY, osmWayValue);
        addLinkSegmentTypeModeProperties(linkSegmentType, activatedPlanitModes, osmHighwayTypeMaxSpeed);                              
          
        String csvModeString = String.join(",", linkSegmentType.getAvailableModes().stream().map( (mode) -> {return mode.getName();}).collect(Collectors.joining(",")));
        LOGGER.info(String.format("[DEFAULT] railway:%s - modes: %s speed: %s (km/h)", osmWayValue, csvModeString, osmHighwayTypeMaxSpeed));
        
      }else {
        LOGGER.warning(String.format("railway:%s is supported but none of the default modes are mapped, type ignored", osmWayValue));
      }
    }
    else {
        /* ... not supported and no replacement available skip type entirely*/
        LOGGER.info(String.format(
            "HighRailwayway type (%s) chosen to be included in network, but not available as supported type by reader, exclude from processing %s", osmWayValue));
    }     
    return linkSegmentType;
  }
    
  /**
   * Create the link segment types that are marked in the passed in settings. As long as they have defaults that
   * are supported, these will be created as indicated. If not available a warning is issued and a link segment type is created based on the default chosen in settings
   * 
   * @return the default created supported types 
   * @throws PlanItException thrown when error
   */
  protected void createOsmCompatibleLinkSegmentTypes(PlanitOsmSettings settings) throws PlanItException {
    
    /* combine rail and highway */
    Map<String,String> highwayKeyValueMap = 
        settings.highwayTypeConfiguration.setOfActivatedTypes().stream().collect(Collectors.toMap( value -> value, value -> OsmHighwayTags.HIGHWAY));
    Map<String,String> railwayKeyValueMap = 
        settings.railwayTypeConfiguration.setOfActivatedTypes().stream().collect(Collectors.toMap( value -> value, value -> OsmRailWayTags.RAILWAY));
    Map<String,String> combinedWayMap = new HashMap<String,String>();
    combinedWayMap.putAll(highwayKeyValueMap);
    combinedWayMap.putAll(railwayKeyValueMap);    
    
    /* ------------------ FOR EACH SUPPORTED OSM WAY TYPE ----------------------------------------- */   
    for(Entry<String,String> entry : combinedWayMap.entrySet()) {
      /* osmway key is the value in the map, hence the swap */
      String osmWayValueToUse = entry.getKey();
      String osmWayKey = entry.getValue();      
           
      /* ------------------ LINK SEGMENT TYPE ----------------------------------------------- */
      MacroscopicLinkSegmentType linkSegmentType = null;  

      /* only create type when there are one or more activated modes for it */
      Collection<Mode> activatedPlanitModes = collectMappedPlanitModes(osmWayKey, osmWayValueToUse, settings);
      if(activatedPlanitModes!=null && !activatedPlanitModes.isEmpty()) {
        
        if(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayValueToUse)) {         
          linkSegmentType = createOsmCompatibleRoadLinkSegmentType(osmWayValueToUse, settings);
        }else if(OsmRailWayTags.isRailwayKeyTag(osmWayKey) && OsmRailWayTags.isRailBasedRailway(osmWayValueToUse)) {             
          linkSegmentType = createOsmCompatibleRailLinkSegmentType(osmWayValueToUse, settings);
        }else {
          LOGGER.severe(String.format("osm way key:value combination is not recognised as a valid tag for (%s:%s), ignored when creating OSM compatible link segment types",osmWayKey, osmWayValueToUse));
        }
        /* ------------------ LINK SEGMENT TYPE ----------------------------------------------- */
        
        if(linkSegmentType == null) {
          LOGGER.warning(String.format("unable to create osm compatible PLANit link segment type for key:value combination %s:%s, ignored",osmWayKey, osmWayValueToUse));
        }else {
          /* create, register, and also store by osm tag */
          defaultPlanitOsmLinkSegmentTypes.put(osmWayValueToUse, linkSegmentType);        
        }
      }                        
    }
    /* ------------------ FOR EACH OSM HIGHWAY TYPE ----------------------------------------- */    
  }
  
  /**
   * Constructor
   */
  public PlanitOsmNetwork(final IdGroupingToken groupId) {
    super(groupId);    
    this.defaultPlanitOsmLinkSegmentTypes = new HashMap<String, MacroscopicLinkSegmentType>();
    /* for now an OSM network comprises but one layer for all modes */
    this.infrastructureLayers.registerNew();
  }

  /**
   * find the link segment type by the Highway=value, where we pass in the "value"
   *  
   * @param OSMHighwayTagValue the type of road to find
   * @return the link segment type that is registered
   */
  public MacroscopicLinkSegmentType getDefaultLinkSegmentTypeByOsmTag(final String osmHighwayTagValue) {
    return this.defaultPlanitOsmLinkSegmentTypes.get(osmHighwayTagValue);
  }
  
  /** add  mode properties for the passed in modes to the passed in link segment type where we cap the max and critical speed based on the minimum of the mode's
   * maximum speed and the osmway type's maximum speed
   * 
   * @param linkSegmentType to populate for
   * @param modesToAdd to add
   * @param osmWayTypeMaxSpeed maxSpeed to set 
   */
  public void addLinkSegmentTypeModeProperties(final MacroscopicLinkSegmentType linkSegmentType, final Collection<Mode> modesToAdd, double osmWayTypeMaxSpeed) {
    /* apply the way type's maximum speed to all modes, but for clarity already cap it to the mode's max speed if needed */
    for(Mode planitMode : modesToAdd) {       
      double cappedMaxSpeed = Math.min(osmWayTypeMaxSpeed, planitMode.getMaximumSpeedKmH());
      linkSegmentType.addModeProperties(planitMode, MacroscopicModePropertiesFactory.create(cappedMaxSpeed,cappedMaxSpeed));
    }
  }
  
  /** collect the only available layer for this network, a macroscopic physical network layer
   * 
   * @return network layer
   */
  public MacroscopicPhysicalNetwork getDefaultNetworkLayer() {
    return (MacroscopicPhysicalNetwork) this.infrastructureLayers.getFirst();
  }  
    
}

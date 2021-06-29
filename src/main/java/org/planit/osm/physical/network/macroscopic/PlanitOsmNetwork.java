package org.planit.osm.physical.network.macroscopic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.planit.network.layer.macroscopic.MacroscopicModePropertiesFactory;
import org.planit.network.layer.macroscopic.MacroscopicPhysicalLayer;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.osm.converter.network.OsmHighwaySettings;
import org.planit.osm.converter.network.OsmNetworkReaderSettings;
import org.planit.osm.converter.network.OsmRailwaySettings;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.util.OsmConstants;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.layer.TransportLayer;
import org.planit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;

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
    
  /** Create a link segment type on the network based on the passed in OSM highway value tags. In case PLANit modes
   * on different network layers exist, then we must register multiple link segment types, differentiated by their modes. For example
   * an OSM link that supports pedestrians and cars, could require mapping to an active network layer and an on-street layer requiring
   * a active layer link segment type version and one for on-street, the former supporting the active modes on that layer and the latter
   * the on-street modes.
   * 
   * @param highwayTypeValue of OSM way key
   * @param maxSpeed to utilise for this osm highway type
   * @param modes planit modes that determine what layer(s) the link segment type is to be registered on 
   * @return created link segment type per layer if available
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createOsmRoadWayLinkSegmentType(String highwayTypeValue, double maxSpeed, Collection<Mode> modes) throws PlanItException {
    
    /* create link segment type for the OSM type */
    switch (highwayTypeValue) {
      case OsmHighwayTags.MOTORWAY:
        return createMotorway(maxSpeed, modes);
      case OsmHighwayTags.MOTORWAY_LINK:
        return createMotorwayLink(maxSpeed, modes);
      case OsmHighwayTags.TRUNK:
        return createTrunk(maxSpeed, modes);
      case OsmHighwayTags.TRUNK_LINK:
        return createTrunkLink(maxSpeed, modes);
      case OsmHighwayTags.PRIMARY:
        return createPrimary(maxSpeed, modes);
      case OsmHighwayTags.PRIMARY_LINK:
        return createPrimaryLink(maxSpeed, modes);
      case OsmHighwayTags.SECONDARY:
        return createSecondary(maxSpeed, modes);
      case OsmHighwayTags.SECONDARY_LINK:
        return createSecondaryLink(maxSpeed, modes);
      case OsmHighwayTags.TERTIARY:
        return createTertiary(maxSpeed, modes);
      case OsmHighwayTags.TERTIARY_LINK:
        return createTertiaryLink(maxSpeed, modes);
      case OsmHighwayTags.UNCLASSIFIED:
        return createUnclassified(maxSpeed, modes);
      case OsmHighwayTags.RESIDENTIAL:
        return createResidential(maxSpeed, modes);
      case OsmHighwayTags.LIVING_STREET:
        return createLivingStreet(maxSpeed, modes);
      case OsmHighwayTags.SERVICE:
        return createService(maxSpeed, modes);
      case OsmHighwayTags.PEDESTRIAN:
        return createPedestrian(maxSpeed, modes);
      case OsmHighwayTags.PATH:
        return createPath(maxSpeed, modes);
      case OsmHighwayTags.STEPS:
        return createSteps(maxSpeed, modes);
      case OsmHighwayTags.FOOTWAY:
        return createFootway(maxSpeed, modes);
      case OsmHighwayTags.CYCLEWAY:
        return createCycleway(maxSpeed, modes);        
      case OsmHighwayTags.TRACK:
        return createTrack(maxSpeed, modes);
      case OsmHighwayTags.ROAD:
        return createRoad(maxSpeed, modes);
      case OsmHighwayTags.BRIDLEWAY:
        return createBridleway(maxSpeed, modes);           
      default:
        throw new PlanItException(
            String.format("OSM type is supported but factory method is missing, unexpected for type highway:%s",highwayTypeValue));
      }  
    }  
  
  /** Create a link segment type on the network based on the passed in OSM railway value tags
   * 
   * @param railwayTypeValue of OSM way key
   * @param maxSpeed to utilise for this osm highway type
   * @param modes planit modes that determine what layer(s) the link segment type is to be registered on 
   * @return created link segment type per layer if available
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createOsmRailWayLinkSegmentType(String railwayTypeValue, double maxSpeed, Collection<Mode> modes) throws PlanItException {
    /* create link segment type for the OSM type */
    switch (railwayTypeValue) {
      case OsmRailwayTags.FUNICULAR:
        return createFunicular(maxSpeed, modes);
      case OsmRailwayTags.LIGHT_RAIL:
        return createLightRail(maxSpeed, modes);
      case OsmRailwayTags.MONO_RAIL:
        return createMonoRail(maxSpeed, modes);
      case OsmRailwayTags.NARROW_GAUGE:
        return createNarrowGauge(maxSpeed, modes);
      case OsmRailwayTags.RAIL:
        return createRail(maxSpeed, modes);
      case OsmRailwayTags.SUBWAY:
        return createSubway(maxSpeed, modes);
      case OsmRailwayTags.TRAM:
        return createTram(maxSpeed, modes);        
      default:
        throw new PlanItException(
            String.format("OSM type is supported but factory method is missing, unexpected for type railway:%s",railwayTypeValue));
      }  
    }     
  
  /**
   *  Create OSM default link segment types with mode properties where we create multiple types if modes reside on different layers
   *  in which case only the modes on that layer will be added to the layer specific type
   *  
   * @param externalId of the type
   * @param capacityPcuPerhour capacity in pcu/h
   * @param maxDensityPcuPerKm max density
   * @param maxSpeed the max speed (km/h)
   * @param modes to identify layers to register link segment types on
   * @return link segment types per layer, if all modes are mapped to a single layer than the map only has a single entry, otherwise it might have more
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createOsmLinkSegmentType(String externalId, double capacityPcuPerhour, double maxDensityPcuPerKm, double maxSpeed, Collection<Mode> modes) throws PlanItException {
    
    /* per layer (via mode) check if type is to be created */
    Map<TransportLayer, MacroscopicLinkSegmentType> typesPerLayer = new HashMap<TransportLayer, MacroscopicLinkSegmentType>(); 
    for(Mode mode : modes) {
      MacroscopicLinkSegmentType linkSegmentType = null;
      MacroscopicPhysicalLayer networkLayer = (MacroscopicPhysicalLayer)getLayerByMode(mode);
      
      if(!typesPerLayer.containsKey(networkLayer)){
        /* new type */
        linkSegmentType = networkLayer.linkSegmentTypes.createAndRegisterNew(externalId, capacityPcuPerhour, maxDensityPcuPerKm);
        /* XML id */
        linkSegmentType.setXmlId(Long.toString(linkSegmentType.getId()));
        /* external id */
        linkSegmentType.setExternalId(externalId);
        /* name */
        linkSegmentType.setName(externalId);
        typesPerLayer.put(networkLayer, linkSegmentType);
      }
      
      /* collect and register mode properties */
      linkSegmentType = typesPerLayer.get(networkLayer);
      double cappedMaxSpeed = Math.min(maxSpeed, mode.getMaximumSpeedKmH());
      MacroscopicModePropertiesFactory.createOnLinkSegmentType(linkSegmentType, mode, cappedMaxSpeed);      
    }
    return typesPerLayer;
  }  
   
  /**
   *  Create an OSM default link segment type
   *  
   * @param name name of the type
   * @param capacityPcuPerhour capacity in pcu/h
   * @param maxSpeedKmh of this type
   * @param modes to identify layers to register link segment types on
   * @return link segment types per layer, if all modes are mapped to a single layer than the map only has a single entry, otherwise it might have more
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createDefaultOsmLinkSegmentType(
      String name, double capacityPcuPerhour, double maxSpeedKmh, Collection<Mode> modes) throws PlanItException {
    return createOsmLinkSegmentType(name, capacityPcuPerhour, OsmConstants.DEFAULT_MAX_DENSITY_LANE, maxSpeedKmh, modes);
  }
  
  
  /**
   * Create motorway type with defaults
   * 
   * restricted access major divided highway, normally with 2 or more running lanes 
   * plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc..
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createMotorway(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.MOTORWAY, OsmConstants.MOTORWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }  
  
  /**
   * Create motorway link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a motorway from/to a motorway or lower class highway.
   *  Normally with the same motorway restrictions. 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createMotorwayLink(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.MOTORWAY_LINK, OsmConstants.MOTORWAY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create trunk type with defaults
   * 
   * The most important roads in a country's system that aren't motorways. 
   * (Need not necessarily be a divided highway.) 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createTrunk(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TRUNK, OsmConstants.TRUNK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }  
  
  /**
   * Create trunk link type with defaults
   * 
   * restricted access major divided highway, normally with 2 or more running lanes 
   * plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc.. 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createTrunkLink(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TRUNK_LINK, OsmConstants.TRUNK_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create primary type with defaults
   * 
   * The next most important roads in a country's system (after trunk). (Often link larger towns.)  
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createPrimary(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PRIMARY, OsmConstants.PRIMARY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }  
  
  /**
   * Create primary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or 
   * lower class highway. 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createPrimaryLink(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PRIMARY_LINK, OsmConstants.PRIMARY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create secondary type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or 
   * lower class highway.
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createSecondary(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.SECONDARY, OsmConstants.SECONDARY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }  
  
  /**
   * Create secondary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a secondary road from/to a secondary road or lower class highway. 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createSecondaryLink(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.SECONDARY_LINK, OsmConstants.SECONDARY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create tertiary type with defaults
   * 
   * The next most important roads in a country's system (after secondary). (Often link smaller towns and villages) 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createTertiary(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TERTIARY, OsmConstants.TERTIARY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }  
  
  /**
   * Create tertiary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a tertiary road from/to a tertiary road or lower class highway.  
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createTertiaryLink(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TERTIARY_LINK, OsmConstants.TERTIARY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create unclassified type with defaults
   * 
   * The least important through roads in a country's system – i.e. minor roads of a lower classification than tertiary, but which serve a purpose other than access to properties. (Often link villages and hamlets.)   
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createUnclassified(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.UNCLASSIFIED, OsmConstants.UNCLASSIFIED_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create residential type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createResidential(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.RESIDENTIAL, OsmConstants.RESIDENTIAL_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create living street type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createLivingStreet(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.LIVING_STREET, OsmConstants.LIVING_STREET_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create service type with defaults
   * 
   * For access roads to, or within an industrial estate, camp site, business park, car park, alleys, etc.     
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createService(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.SERVICE, OsmConstants.SERVICE_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }      
  
  /**
   * Create pedestrian type with defaults
   * 
   * For roads used mainly/exclusively for pedestrians in shopping and some residential areas which may allow access by motorised vehicles only for very limited periods of the day.     
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createPedestrian(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PEDESTRIAN, OsmConstants.PEDESTRIAN_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create path type with defaults
   * 
   * A non-specific path either multi-use or unspecified usage, open to all non-motorized vehicles and not intended for motorized vehicles unless tagged so separately    
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createPath(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.PATH, OsmConstants.PATH_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create step type with defaults
   * 
   * For flights of steps (stairs) on footways    
   * 
    * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createSteps(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.STEPS, OsmConstants.STEPS_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create footway type with defaults
   * 
   * For designated footpaths; i.e., mainly/exclusively for pedestrians. This includes walking tracks and gravel paths.   
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createFootway(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.FOOTWAY, OsmConstants.FOOTWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create cycleway type with defaults
   * 
   * For designated cycleways   
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createCycleway(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.CYCLEWAY, OsmConstants.CYCLEWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }  
  
  /**
   * Create bridleway type with defaults
   * 
   * For horse riders.   
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createBridleway(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.BRIDLEWAY, OsmConstants.BRIDLEWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create track type with defaults
   * 
   * Roads for mostly agricultural or forestry uses.    
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createTrack(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.TRACK, OsmConstants.TRACK_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create road type with defaults
   * 
   * A road/way/street/motorway/etc. of unknown type. It can stand for anything ranging from a footpath to a 
   * motorway. This tag should only be used temporarily until the road/way/etc. has been properly surveyed.     
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createRoad(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmHighwayTags.ROAD, OsmConstants.ROAD_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }
  
  /**
   * Create funicular (rail) type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createFunicular(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.FUNICULAR, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create light rail (rail) type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createLightRail(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.LIGHT_RAIL, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }   
  
  /**
   * Create mono rail (rail) type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createMonoRail(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.MONO_RAIL, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create narrow gauge(rail) type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createNarrowGauge(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.NARROW_GAUGE, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create rail type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createRail(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.RAIL, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  }    
  
  /**
   * Create subway type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createSubway(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.SUBWAY, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
  } 
  
  /**
   * Create tram type with defaults
   * 
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createTram(double osmHighwayTypeMaxSpeed, Collection<Mode> modes) throws PlanItException {
    return createDefaultOsmLinkSegmentType(OsmRailwayTags.TRAM, OsmConstants.RAILWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);    
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
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.FUNICULAR);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.LIGHT_RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.MONO_RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.NARROW_GAUGE);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.SUBWAY);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.TRAM);
  }  
     
  /**
   * the PLANit link segment types per layer (value) that are activated for this osm way type (key)
   */
  protected final Map<String, Map<TransportLayer, MacroscopicLinkSegmentType>> defaultPlanitOsmLinkSegmentTypes;
    
  /** collect the PLANit mode that are mapped, i.e., are marked to be activated in the final network.
   * @param osmWayKey to collect for
   * @param osmWayValue to collect for
   * @param settings to collect from
   * @return mappedPLANitModes, empty if no modes are mapped
   */
  protected Collection<Mode> collectMappedPlanitModes(String osmWayKey, String osmWayValue, OsmNetworkReaderSettings settings) {
    Collection<String> allowedOsmModes = null;
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      allowedOsmModes =  settings.getHighwaySettings().collectAllowedOsmHighwayModes(osmWayValue);
    }else if(OsmRailwayTags.isRailwayKeyTag(osmWayKey) && settings.isRailwayParserActive()) {
      allowedOsmModes =  settings.getRailwaySettings().collectAllowedOsmRailwayModes(osmWayValue);
    }
    return settings.getMappedPlanitModes(allowedOsmModes);
  }     
  
  /**
   *  create the road based link segment type based on the setting
   * @param osmWayValue to use
   * @param settings to extract defaults from
   * @return created (or already existing) default link segment type for the given OSM highway type per layer
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createOsmCompatibleRoadLinkSegmentTypeByLayer(final String osmWayValue, final OsmNetworkReaderSettings settings) throws PlanItException {
    Map<TransportLayer, MacroscopicLinkSegmentType> linkSegmentTypes = null; 
    
    /* only when way type is marked as supported in settings we parse it */
    OsmHighwaySettings highwaySettings = settings.getHighwaySettings();
    if(highwaySettings.isOsmHighwayTypeActivated(osmWayValue)) {           
      
      boolean isOverwrite = highwaySettings.isDefaultCapacityOrMaxDensityOverwrittenByOsmHighwayType(osmWayValue);
      boolean isBackupDefault = false;          
      
        
      String osmWayValueToUse = osmWayValue;
      if(!supportedOsmRoadLinkSegmentTypes.contains(osmWayValue)){
        /* ...use replacement type instead of activate type to still be able to process OSM ways of this type, if no replacement is set, we revert to null to indicate we cannot support this way type */        
        osmWayValueToUse = highwaySettings.isApplyDefaultWhenOsmHighwayTypeDeactivated() ? highwaySettings.getDefaultOsmHighwayTypeWhenUnsupported() : null ;
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
          Collection<Mode> activatedPlanitModes = settings.getMappedPlanitModes(highwaySettings.collectAllowedOsmHighwayModes(osmWayValueToUse));          
          if(!activatedPlanitModes.isEmpty()) {            
            
            /* create the planit link segment type based on OSM tag */
            double osmHighwayTypeMaxSpeed = highwaySettings.getDefaultSpeedLimitByOsmHighwayType(osmWayValueToUse);
            if(isOverwrite) {
              /* type is overwritten, so use overwritten data instead of defaults */
              final Pair<Double,Double> capacityDensityPair = highwaySettings.getOverwrittenCapacityMaxDensityByOsmHighwayType(osmWayValueToUse);
              linkSegmentTypes = createOsmLinkSegmentType(osmWayValue, capacityDensityPair.first(), capacityDensityPair.second(), osmHighwayTypeMaxSpeed, activatedPlanitModes);
            }else {
              /* use default link segment type values */
              linkSegmentTypes = createOsmRoadWayLinkSegmentType(osmWayValueToUse, osmHighwayTypeMaxSpeed, activatedPlanitModes);            
            }
            
            /* log */
            for(Entry<TransportLayer, MacroscopicLinkSegmentType> entry: linkSegmentTypes.entrySet()) {
              TransportLayer layer = entry.getKey();
              MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
              
              /** convert to comma separated string by mode name */
              String csvModeString = String.join(",", linkSegmentType.getAvailableModes().stream().map( (mode) -> {return mode.getName();}).collect(Collectors.joining(",")));
              LOGGER.info(String.format("%s %s%s highway:%s - modes: %s speed: %.2f (km/h) capacity: %.2f (pcu/lane/h), max density: %.2f (pcu/km/lane)", 
                  TransportLayer.createLayerLogPrefix(layer),isOverwrite ? "[OVERWRITE] " : "[DEFAULT]", isBackupDefault ? "[BACKUP]" : "", osmWayValueToUse, csvModeString, osmHighwayTypeMaxSpeed, linkSegmentType.getCapacityPerLane(),linkSegmentType.getMaximumDensityPerLane()));              
            }            
          }else {
            linkSegmentTypes = defaultPlanitOsmLinkSegmentTypes.get(osmWayValueToUse);
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
    return linkSegmentTypes;
  }
 

  /**
   *  create the rail based link segment type based on the setting
   * @param osmWayValue to use
   * @param settings to extract defaults from
   * @return created (or already existing) default link segment type per layer for the given OSM highway type
   * @throws PlanItException thrown if error
   */
  protected Map<TransportLayer, MacroscopicLinkSegmentType> createOsmCompatibleRailLinkSegmentTypeByLayer(final String osmWayValue, final OsmNetworkReaderSettings settings) throws PlanItException {
    Map<TransportLayer, MacroscopicLinkSegmentType> linkSegmentTypes = null;     
    
    if(!settings.isRailwayParserActive()) {
      LOGGER.warning(String.format("railways are not activates, cannot create link segment types for railway=%s", osmWayValue));
      return linkSegmentTypes;
    }
  
    /* only when way type is marked as supported in settings we parse it */
    if(settings.isRailwayParserActive() && settings.getRailwaySettings().isOsmRailwayTypeActivated(osmWayValue)) {
      OsmRailwaySettings railwaySettings = settings.getRailwaySettings();
      boolean isOverwrite = railwaySettings.isDefaultCapacityOrMaxDensityOverwrittenByOsmRailwayType(osmWayValue);
      
      Collection<Mode> activatedPlanitModes = settings.getMappedPlanitModes(railwaySettings.collectAllowedOsmRailwayModes(osmWayValue));
      if(!activatedPlanitModes.isEmpty()) {
        
        /* create the PLANit link segment type based on OSM way tag and possibly overwritten defalt values*/
        double railwayMaxSpeed = railwaySettings.getDefaultSpeedLimitByOsmRailwayType(osmWayValue);
        if(isOverwrite) {
          /* type is overwritten, so use overwritten data instead of defaults */
          final Pair<Double,Double> capacityDensityPair = railwaySettings.getOverwrittenCapacityMaxDensityByOsmRailwayType(osmWayValue);
          linkSegmentTypes = createOsmLinkSegmentType(osmWayValue, capacityDensityPair.first(), capacityDensityPair.second(), railwayMaxSpeed, activatedPlanitModes);
        }else {
          /* use default link segment type values */
          linkSegmentTypes = createOsmRailWayLinkSegmentType(osmWayValue, railwayMaxSpeed, activatedPlanitModes);            
        }                                                                   
        
        /* log */
        for(Entry<TransportLayer, MacroscopicLinkSegmentType> entry: linkSegmentTypes.entrySet()) {
          TransportLayer layer = entry.getKey();
          MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
          
          String csvModeString = String.join(",", linkSegmentType.getAvailableModes().stream().map( (mode) -> {return mode.getName();}).collect(Collectors.joining(",")));
          LOGGER.info(String.format("%s %s railway:%s - modes: %s speed: %s (km/h)", TransportLayer.createLayerLogPrefix(layer), isOverwrite ? "[OVERWRITE] " : "[DEFAULT]", osmWayValue, csvModeString, railwayMaxSpeed));
        }
        
      }else {
        LOGGER.warning(String.format("railway:%s is supported but none of the default modes are mapped, type ignored", osmWayValue));
      }
    }
    else {
        /* ... not supported and no replacement available skip type entirely*/
        LOGGER.info(String.format(
            "Railwayway type (%s) chosen to be included in network, but not available as supported type by reader, exclude from processing %s", osmWayValue));
    }     
    return linkSegmentTypes;
  }  
  
  /**
   * Default Constructor
   */
  public PlanitOsmNetwork() {
    this(IdGroupingToken.collectGlobalToken());    
  }  
  
  /**
   * Constructor
   * 
   * @param groupId to use for id generation
   */
  public PlanitOsmNetwork(final IdGroupingToken groupId) {
    super(groupId);    
    this.defaultPlanitOsmLinkSegmentTypes = new HashMap<String, Map<TransportLayer, MacroscopicLinkSegmentType>>();
  }

  /**
   * Find the link segment type (per layer) by the Highway=value, where we pass in the "value"
   *  
   * @param osmHighwayTagValue the type of road to find
   * @return the link segment type that is registered per layer
   */
  public Map<TransportLayer, MacroscopicLinkSegmentType> getDefaultLinkSegmentTypeByOsmTag(final String osmHighwayTagValue) {
    return this.defaultPlanitOsmLinkSegmentTypes.get(osmHighwayTagValue);
  }
  
  /**
   * Create the link segment types that are marked in the passed in settings. As long as they have defaults that
   * are supported, these will be created as indicated. If not available a warning is issued and a link segment type is created based on the default chosen in settings
   * 
   * @param settings to use
   * @throws PlanItException thrown when error
   */
  public void createOsmCompatibleLinkSegmentTypes(OsmNetworkReaderSettings settings) throws PlanItException {
    
    /* combine rail and highway */
    Map<String,String> highwayKeyValueMap = 
        settings.getHighwaySettings().getSetOfActivatedOsmWayTypes().stream().collect(Collectors.toMap( value -> value, value -> OsmHighwayTags.HIGHWAY));
    Map<String,String> railwayKeyValueMap = null;
    if(settings.isRailwayParserActive()) {
      railwayKeyValueMap = settings.getRailwaySettings().getSetOfActivatedOsmWayTypes().stream().collect(Collectors.toMap( value -> value, value -> OsmRailwayTags.RAILWAY));
    }
    Map<String,String> combinedWayMap = new HashMap<String,String>();
    combinedWayMap.putAll(highwayKeyValueMap);
    if(railwayKeyValueMap != null) {
      combinedWayMap.putAll(railwayKeyValueMap);
    }
    
    /* ------------------ FOR EACH SUPPORTED OSM WAY TYPE ----------------------------------------- */   
    for(Entry<String,String> entry : combinedWayMap.entrySet()) {
      /* osmway key is the value in the map, hence the swap */
      String osmWayValueToUse = entry.getKey();
      String osmWayKey = entry.getValue();      
           
      /* ------------------ LINK SEGMENT TYPE ----------------------------------------------- */
      Map<TransportLayer, MacroscopicLinkSegmentType> linkSegmentTypesByLayer = null;  

      /* only create type when there are one or more activated modes for it */
      Collection<Mode> activatedPlanitModes = collectMappedPlanitModes(osmWayKey, osmWayValueToUse, settings);
      if(activatedPlanitModes!=null && !activatedPlanitModes.isEmpty()) {
        
        if(OsmHighwayTags.isHighwayKeyTag(osmWayKey) && OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayValueToUse)) {
          linkSegmentTypesByLayer = createOsmCompatibleRoadLinkSegmentTypeByLayer(osmWayValueToUse, settings);
        }else if(OsmRailwayTags.isRailwayKeyTag(osmWayKey) && OsmRailwayTags.isRailBasedRailway(osmWayValueToUse)) {             
          linkSegmentTypesByLayer = createOsmCompatibleRailLinkSegmentTypeByLayer(osmWayValueToUse, settings);
        }else {
          LOGGER.severe(String.format("osm way key:value combination is not recognised as a valid tag for (%s:%s), ignored when creating OSM compatible link segment types",osmWayKey, osmWayValueToUse));
        }
        /* ------------------ LINK SEGMENT TYPE ----------------------------------------------- */
        
        if(linkSegmentTypesByLayer == null || linkSegmentTypesByLayer.isEmpty()) {
          LOGGER.warning(String.format("unable to create osm compatible PLANit link segment type for key:value combination %s:%s, ignored",osmWayKey, osmWayValueToUse));
        }else {
          /* create, register, and also store by osm tag */
          defaultPlanitOsmLinkSegmentTypes.put(osmWayValueToUse, linkSegmentTypesByLayer);        
        }
      }                        
    }
    /* ------------------ FOR EACH OSM HIGHWAY TYPE ----------------------------------------- */    
  }
        
}

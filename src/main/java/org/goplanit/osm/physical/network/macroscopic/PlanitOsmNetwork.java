package org.goplanit.osm.physical.network.macroscopic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.network.layer.macroscopic.AccessGroupPropertiesFactory;
import org.goplanit.network.layer.macroscopic.MacroscopicNetworkLayerImpl;
import org.goplanit.osm.converter.network.OsmHighwaySettings;
import org.goplanit.osm.converter.network.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.network.OsmRailwaySettings;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.osm.util.OsmConstants;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.mode.PredefinedMode;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.network.layer.NetworkLayer;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegmentType;

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
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmRoadWayLinkSegmentType(String highwayTypeValue, double maxSpeed, Collection<? extends Mode> modes){
    
    /* create link segment type for the OSM type */
    var osmWayKey = OsmHighwayTags.getHighwayKeyTag();
    switch (highwayTypeValue) {
      case OsmHighwayTags.MOTORWAY:
        return createMotorway(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.MOTORWAY_LINK:
        return createMotorwayLink(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.TRUNK:
        return createTrunk(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.TRUNK_LINK:
        return createTrunkLink(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.PRIMARY:
        return createPrimary(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.PRIMARY_LINK:
        return createPrimaryLink(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.SECONDARY:
        return createSecondary(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.SECONDARY_LINK:
        return createSecondaryLink(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.TERTIARY:
        return createTertiary(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.TERTIARY_LINK:
        return createTertiaryLink(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.UNCLASSIFIED:
        return createUnclassified(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.RESIDENTIAL:
        return createResidential(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.LIVING_STREET:
        return createLivingStreet(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.SERVICE:
        return createService(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.PEDESTRIAN:
        return createPedestrian(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.PATH:
        return createPath(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.STEPS:
        return createSteps(maxSpeed, modes);
      case OsmHighwayTags.FOOTWAY:
        return createFootway(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.CYCLEWAY:
        return createCycleway(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.TRACK:
        return createTrack(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.ROAD:
        return createRoad(osmWayKey, maxSpeed, modes);
      case OsmHighwayTags.BRIDLEWAY:
        return createBridleway(osmWayKey, maxSpeed, modes);
      default:
        throw new PlanItRunTimeException(
            String.format("OSM type is supported but factory method is missing, unexpected for type highway:%s",highwayTypeValue));
      }  
    }  
  
  /** Create a link segment type on the network based on the passed in OSM railway value tags
   * 
   * @param railwayTypeValue of OSM way key
   * @param maxSpeed to utilise for this osm railway type
   * @param modes planit modes that determine what layer(s) the link segment type is to be registered on 
   * @return created link segment type per layer if available
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmRailWayLinkSegmentType(
      String railwayTypeValue, double maxSpeed, Collection<? extends Mode> modes){
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
        throw new PlanItRunTimeException(
            String.format("OSM type is supported but factory method is missing, unexpected for type railway:%s",railwayTypeValue));
      }  
    }

  /** Create a link segment type on the network based on the passed in OSM waterway route value tags
   *
   * @param waterwayValue of OSM way key
   * @param maxSpeed to utilise for this osm type
   * @param modes planit modes that determine what layer(s) the link segment type is to be registered on
   * @return created link segment type per layer if available
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmWaterWayLinkSegmentType(
      String waterwayValue, double maxSpeed, Collection<? extends Mode> modes){
    var osmWaterwayKey = OsmWaterwayTags.getKeyForValueType(waterwayValue);

    /* create link segment type for the OSM type */
    if(OsmWaterwayTags.ROUTE.equals(osmWaterwayKey)) {
      /* route=ferry */
      switch (waterwayValue) {
        case OsmWaterwayTags.FERRY:
          return createFerry(maxSpeed, modes);
        default:
          throw new PlanItRunTimeException(
              String.format("OSM type is supported for water way but factory method is missing, unexpected for type %s=%s", osmWaterwayKey, waterwayValue));
      }
    }else if(OsmWaterwayTags.FERRY.equals(osmWaterwayKey)){
      /* ferry= _highway_type_ */
      switch (waterwayValue) {
        case OsmHighwayTags.MOTORWAY:
          return createMotorway(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.MOTORWAY_LINK:
          return createMotorwayLink(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.TRUNK:
          return createTrunk(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.TRUNK_LINK:
          return createTrunkLink(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.PRIMARY:
          return createPrimary(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.PRIMARY_LINK:
          return createPrimaryLink(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.SECONDARY:
          return createSecondary(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.SECONDARY_LINK:
          return createSecondaryLink(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.TERTIARY:
          return createTertiary(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.TERTIARY_LINK:
          return createTertiaryLink(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.UNCLASSIFIED:
          return createUnclassified(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.RESIDENTIAL:
          return createResidential(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.LIVING_STREET:
          return createLivingStreet(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.SERVICE:
          return createService(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.PEDESTRIAN:
          return createPedestrian(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.PATH:
          return createPath(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.FOOTWAY:
          return createFootway(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.CYCLEWAY:
          return createCycleway(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.TRACK:
          return createTrack(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.ROAD:
          return createRoad(osmWaterwayKey, maxSpeed, modes);
        case OsmHighwayTags.BRIDLEWAY:
          return createBridleway(osmWaterwayKey, maxSpeed, modes);
        default:
          throw new PlanItRunTimeException("OSM type is supported for waterway but factory method is missing, unexpected for type %s=%s", osmWaterwayKey, waterwayValue);
      }
    }

    throw new PlanItRunTimeException("Unknown OSM way key (%s) for waterway, this should not happen", osmWaterwayKey);
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
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmLinkSegmentType(
      String externalId, double capacityPcuPerhour, double maxDensityPcuPerKm, double maxSpeed, Collection<? extends Mode> modes) {
    
    /* per layer (via mode) check if type is to be created */
    Map<NetworkLayer, MacroscopicLinkSegmentType> typesPerLayer = new HashMap<>();
    for(Mode mode : modes) {
      MacroscopicLinkSegmentType linkSegmentType = null;
      MacroscopicNetworkLayerImpl networkLayer = (MacroscopicNetworkLayerImpl) getLayerByMode(mode);
      
      if(!typesPerLayer.containsKey(networkLayer)){
        /* new type */
        linkSegmentType = networkLayer.linkSegmentTypes.getFactory().registerNew(externalId, capacityPcuPerhour, maxDensityPcuPerKm);
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

      var accessGroupProperties = AccessGroupPropertiesFactory.create(cappedMaxSpeed, mode);
      var matchedExistingAccessGroupProperties = linkSegmentType.findEqualAccessPropertiesForAnyMode(accessGroupProperties);
      if(matchedExistingAccessGroupProperties != null){
        linkSegmentType.registerModeOnAccessGroup(mode, matchedExistingAccessGroupProperties);
      }else {
        linkSegmentType.setAccessGroupProperties(accessGroupProperties);
      }
    }
    return typesPerLayer;
  }  
   
  /**
   *  Create an OSM default link segment type with name (namekey=)namevalue
   *
   * @param nameKey key component of name (may be null)
   * @param nameValue component of the type
   * @param capacityPcuPerhour capacity in pcu/h
   * @param maxSpeedKmh of this type
   * @param modes to identify layers to register link segment types on
   * @return link segment types per layer, if all modes are mapped to a single layer than the map only has a single entry, otherwise it might have more
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createDefaultOsmLinkSegmentType(
      String nameKey, String nameValue, double capacityPcuPerhour, double maxSpeedKmh, Collection<? extends Mode> modes) {
    String name = nameKey != null ? OsmTagUtils.toConcatEqualsString(nameKey,nameValue) : nameValue;
    return createOsmLinkSegmentType(name, capacityPcuPerhour, OsmConstants.DEFAULT_MAX_DENSITY_LANE, maxSpeedKmh, modes);
  }
  
  
  /**
   * Create motorway type with defaults
   * 
   * restricted access major divided highway, normally with 2 or more running lanes 
   * plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc..
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createMotorway(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes) {
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.MOTORWAY, OsmConstants.MOTORWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }  
  
  /**
   * Create motorway link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a motorway from/to a motorway or lower class highway.
   *  Normally with the same motorway restrictions. 
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createMotorwayLink(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.MOTORWAY_LINK, OsmConstants.MOTORWAY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create trunk type with defaults
   * 
   * The most important roads in a country's system that aren't motorways. 
   * (Need not necessarily be a divided highway.) 
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createTrunk(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.TRUNK, OsmConstants.TRUNK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }  
  
  /**
   * Create trunk link type with defaults
   * 
   * restricted access major divided highway, normally with 2 or more running lanes 
   * plus emergency hard shoulder. Equivalent to the Freeway, Autobahn, etc.. 
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createTrunkLink(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.TRUNK_LINK, OsmConstants.TRUNK_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create primary type with defaults
   * 
   * The next most important roads in a country's system (after trunk). (Often link larger towns.)  
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createPrimary(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes) {
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.PRIMARY, OsmConstants.PRIMARY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }  
  
  /**
   * Create primary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or 
   * lower class highway. 
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createPrimaryLink(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.PRIMARY_LINK, OsmConstants.PRIMARY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create secondary type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or 
   * lower class highway.
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createSecondary(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.SECONDARY, OsmConstants.SECONDARY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }  
  
  /**
   * Create secondary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a secondary road from/to a secondary road or lower class highway. 
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createSecondaryLink(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.SECONDARY_LINK, OsmConstants.SECONDARY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create tertiary type with defaults
   * 
   * The next most important roads in a country's system (after secondary). (Often link smaller towns and villages) 
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createTertiary(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.TERTIARY, OsmConstants.TERTIARY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }  
  
  /**
   * Create tertiary link type with defaults
   * 
   * The link roads (sliproads/ramps) leading to/from a tertiary road from/to a tertiary road or lower class highway.  
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createTertiaryLink(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes) {
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.TERTIARY_LINK, OsmConstants.TERTIARY_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create unclassified type with defaults
   * 
   * The least important through roads in a country's system – i.e. minor roads of a lower classification than tertiary, but which serve a purpose other than access to properties. (Often link villages and hamlets.)   
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createUnclassified(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.UNCLASSIFIED, OsmConstants.UNCLASSIFIED_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create residential type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createResidential(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.RESIDENTIAL, OsmConstants.RESIDENTIAL_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create living street type with defaults
   * 
   * Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing.    
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createLivingStreet(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.LIVING_STREET, OsmConstants.LIVING_STREET_LINK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create service type with defaults
   * 
   * For access roads to, or within an industrial estate, camp site, business park, car park, alleys, etc.     
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createService(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.SERVICE, OsmConstants.SERVICE_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }      
  
  /**
   * Create pedestrian type with defaults
   * 
   * For roads used mainly/exclusively for pedestrians in shopping and some residential areas which may allow access by motorised vehicles only for very limited periods of the day.     
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)

   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createPedestrian(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.PEDESTRIAN, OsmConstants.PEDESTRIAN_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create path type with defaults
   * 
   * A non-specific path either multi-use or unspecified usage, open to all non-motorized vehicles and not intended for motorized vehicles unless tagged so separately    
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)

   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createPath(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.PATH, OsmConstants.PATH_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create step type with defaults
   * 
   * For flights of steps (stairs) on footways    
   * 
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createSteps(
      double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes) {
    return createDefaultOsmLinkSegmentType(
        OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.STEPS, OsmConstants.STEPS_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create footway type with defaults
   * 
   * For designated footpaths; i.e., mainly/exclusively for pedestrians. This includes walking tracks and gravel paths.   
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createFootway(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.FOOTWAY, OsmConstants.FOOTWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create cycleway type with defaults
   * 
   * For designated cycleways   
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createCycleway(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.CYCLEWAY, OsmConstants.CYCLEWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }  
  
  /**
   * Create bridleway type with defaults
   * 
   * For horse riders.   
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createBridleway(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.BRIDLEWAY, OsmConstants.BRIDLEWAY_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create track type with defaults
   * 
   * Roads for mostly agricultural or forestry uses.    
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createTrack(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.TRACK, OsmConstants.TRACK_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create road type with defaults
   * 
   * A road/way/street/motorway/etc. of unknown type. It can stand for anything ranging from a footpath to a 
   * motorway. This tag should only be used temporarily until the road/way/etc. has been properly surveyed.     
   *
   * @param osmKey used to prefix name because the type itself might not be unique without it, e.g., ferry=x, highway=x
   * @param osmHighwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createRoad(
      String osmKey, double osmHighwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        osmKey, OsmHighwayTags.ROAD, OsmConstants.ROAD_CAPACITY, osmHighwayTypeMaxSpeed, modes);
  }
  
  /**
   * Create funicular (rail) type with defaults
   *
   * @param osmRailwayTypeMaxSpeed speed limit of highway type
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createFunicular(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes) {
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.FUNICULAR, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create light rail (rail) type with defaults
   * 
   * 
   * @param osmRailwayTypeMaxSpeed speed limit of highway type
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createLightRail(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.LIGHT_RAIL, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  }   
  
  /**
   * Create mono rail (rail) type with defaults
   * 
   * 
   * @param osmRailwayTypeMaxSpeed speed limit of highway type
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createMonoRail(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.MONO_RAIL, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create narrow gauge(rail) type with defaults
   * 
   * 
   * @param osmRailwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createNarrowGauge(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.NARROW_GAUGE, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create rail type with defaults
   * 
   * 
   * @param osmRailwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createRail(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.RAIL, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  }    
  
  /**
   * Create subway type with defaults
   * 
   * 
   * @param osmRailwayTypeMaxSpeed speed limit of highway type 
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createSubway(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.SUBWAY, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  } 
  
  /**
   * Create tram type with defaults
   * 
   * 
   * @param osmRailwayTypeMaxSpeed speed limit of highway type
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createTram(
      double osmRailwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.TRAM, OsmConstants.RAILWAY_CAPACITY, osmRailwayTypeMaxSpeed, modes);
  }

  /**
   * Create ferry way type with defaults
   *
   *
   * @param osmwayTypeMaxSpeed speed limit of type
   * @param modes to identify layers to register link segment types on
   * @return created types per layer (depending on how modes are mapped to layers)
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createFerry(
      double osmwayTypeMaxSpeed, Collection<? extends Mode> modes){
    return createDefaultOsmLinkSegmentType(
        OsmWaterwayTags.getKeyForValueType(OsmWaterwayTags.FERRY), OsmWaterwayTags.FERRY, OsmConstants.WATERWAY_CAPACITY, osmwayTypeMaxSpeed, modes);
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

  /**
   * the list of water way types for which we have default link segment type mapping available out of the box
   *
   **/
  protected static final Set<String> supportedOsmWaterLinkSegmentTypes;
  
  /** the supported types for which we have default road link segment type settings available */
  static {
    supportedOsmRoadLinkSegmentTypes = new HashSet<>();
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
    supportedOsmRailLinkSegmentTypes = new HashSet<>();
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.FUNICULAR);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.LIGHT_RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.MONO_RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.NARROW_GAUGE);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.RAIL);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.SUBWAY);
    supportedOsmRailLinkSegmentTypes.add(OsmRailwayTags.TRAM);
  }

  /** the supported types for which we have default waterway link segment type settings available */
  static {
    supportedOsmWaterLinkSegmentTypes = new HashSet<>();
    /* route=ferry */
    supportedOsmWaterLinkSegmentTypes.add(OsmWaterwayTags.FERRY);

    /* ferry= _highwaytype_ */
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.MOTORWAY_LINK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.TRUNK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.TRUNK_LINK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.PRIMARY);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.PRIMARY_LINK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.SECONDARY);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.SECONDARY_LINK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.TERTIARY);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.TERTIARY_LINK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.UNCLASSIFIED);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.RESIDENTIAL);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.LIVING_STREET);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.SERVICE);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.PEDESTRIAN);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.PATH);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.STEPS);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.FOOTWAY);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.CYCLEWAY);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.TRACK);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.ROAD);
    supportedOsmWaterLinkSegmentTypes.add(OsmHighwayTags.BRIDLEWAY);
  }
     
  /**
   * the PLANit link segment types per layer (value) that are activated for this osm way (key, value)
   */
  protected final Map<String, Map<String, Map<NetworkLayer, MacroscopicLinkSegmentType>>> defaultPlanitOsmLinkSegmentTypes;
    
  /**
   * collect the PLANit (predefined) mode types that are mapped, i.e., are marked to be activated in the final network.
   *
   * @param osmWayLikeKey   to collect for
   * @param osmWayLikeValue to collect for
   * @param settings    to collect from
   * @return mapped PLANit mode types, empty if no modes are mapped
   */
  protected Set<PredefinedModeType> collectMappedPlanitModeTypes(String osmWayLikeKey, String osmWayLikeValue, OsmNetworkReaderSettings settings) {
    Collection<String> allowedOsmModes = null;
    if(settings.isHighwayParserActive() && OsmHighwayTags.isHighwayKeyTag(osmWayLikeKey)) {
      allowedOsmModes =  settings.getHighwaySettings().collectAllowedOsmHighwayModes(osmWayLikeValue);
    }else if(settings.isRailwayParserActive() && OsmRailwayTags.isRailwayKeyTag(osmWayLikeKey)) {
      allowedOsmModes =  settings.getRailwaySettings().collectAllowedOsmRailwayModes(osmWayLikeValue);
    }else if(settings.isWaterwayParserActive() && OsmWaterwayTags.isWaterBasedWay(osmWayLikeKey, osmWayLikeValue)) {
      allowedOsmModes =  settings.getWaterwaySettings().collectAllowedOsmWaterwayModes(osmWayLikeValue);
    }
    return settings.getActivatedPlanitModeTypes(allowedOsmModes);
  }

  /**
   *  create the road based link segment type based on the setting
   * @param osmWayValue to use
   * @param settings to extract defaults from
   * @return created (or already existing) default link segment type for the given OSM highway type per layer
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmCompatibleRoadLinkSegmentTypeByLayer(final String osmWayValue, final OsmNetworkReaderSettings settings){
    Map<NetworkLayer, MacroscopicLinkSegmentType> linkSegmentTypes = null; 
    
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
        
          /* Only when one or more OSM modes are mapped to PLANit modes, the OSM way type will be used, otherwise it is ignored */
          Set<PredefinedMode> activatedPlanitModes = getAvailableModesFromModeTypes(settings.getActivatedPlanitModeTypes(highwaySettings.collectAllowedOsmHighwayModes(osmWayValueToUse)));
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
            for(Entry<NetworkLayer, MacroscopicLinkSegmentType> entry: linkSegmentTypes.entrySet()) {
              NetworkLayer layer = entry.getKey();
              MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
              
              /** convert to comma separated string by mode name */
              String csvModeString = String.join(",", linkSegmentType.getAllowedModes().stream().map( (mode) -> {return mode.getName();}).collect(Collectors.joining(",")));
              LOGGER.info(String.format("%s %s%s highway:%s - modes: %s speed: %.2f (km/h) capacity: %.2f (pcu/lane/h), max density: %.2f (pcu/km/lane)", 
                  NetworkLayer.createLayerLogPrefix(layer),isOverwrite ? "[OVERWRITE] " : "[DEFAULT]", isBackupDefault ? "[BACKUP]" : "", osmWayValueToUse, csvModeString, osmHighwayTypeMaxSpeed, linkSegmentType.getExplicitCapacityPerLaneOrDefault(),linkSegmentType.getExplicitMaximumDensityPerLaneOrDefault()));              
            }            
          }else {
            linkSegmentTypes = defaultPlanitOsmLinkSegmentTypes.get(OsmHighwayTags.getHighwayKeyTag()).get(osmWayValueToUse);
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
   * @return created (or already existing) default link segment type per layer for the given OSM railway type
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmCompatibleRailLinkSegmentTypeByLayer(final String osmWayValue, final OsmNetworkReaderSettings settings){
    Map<NetworkLayer, MacroscopicLinkSegmentType> linkSegmentTypes = null;     
    
    if(!settings.isRailwayParserActive()) {
      LOGGER.warning(String.format("Railways are not activated, cannot create link segment types for railway=%s", osmWayValue));
      return linkSegmentTypes;
    }
  
    /* only when way type is marked as supported in settings we parse it */
    if(settings.isRailwayParserActive() && settings.getRailwaySettings().isOsmRailwayTypeActivated(osmWayValue)) {
      OsmRailwaySettings railwaySettings = settings.getRailwaySettings();
      boolean isOverwrite = railwaySettings.isDefaultCapacityOrMaxDensityOverwrittenByOsmRailwayType(osmWayValue);
      
      Set<PredefinedMode> activatedPlanitModes = getAvailableModesFromModeTypes(settings.getActivatedPlanitModeTypes(railwaySettings.collectAllowedOsmRailwayModes(osmWayValue)));
      if(!activatedPlanitModes.isEmpty()) {
        
        /* create the PLANit link segment type based on OSM way tag and possibly overwritten default values*/
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
        for(Entry<NetworkLayer, MacroscopicLinkSegmentType> entry: linkSegmentTypes.entrySet()) {
          NetworkLayer layer = entry.getKey();
          MacroscopicLinkSegmentType linkSegmentType = entry.getValue();
          
          String csvModeString = String.join(",", linkSegmentType.getAllowedModes().stream().map( (mode) -> {return mode.getName();}).collect(Collectors.joining(",")));
          LOGGER.info(String.format("%s %s railway:%s - modes: %s speed: %s (km/h)", NetworkLayer.createLayerLogPrefix(layer), isOverwrite ? "[OVERWRITE] " : "[DEFAULT]", osmWayValue, csvModeString, railwayMaxSpeed));
        }
        
      }else {
        LOGGER.warning(String.format("Railway:%s is supported but none of the default modes are mapped, type ignored", osmWayValue));
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
   *  create the water based link segment type based on the setting
   * @param osmWayValue to use
   * @param settings to extract defaults from
   * @return created (or already existing) default link segment type per layer for the given OSM water (route) type
   */
  protected Map<NetworkLayer, MacroscopicLinkSegmentType> createOsmCompatibleWaterLinkSegmentTypeByLayer(
      final String osmWayValue, final OsmNetworkReaderSettings settings){
    if(!settings.isWaterwayParserActive()) {
      LOGGER.warning(String.format("Waterways are not activated, cannot create link segment types", osmWayValue));
      return null;
    }
    var osmWayKey = OsmWaterwayTags.getKeyForValueType(osmWayValue);
    if(StringUtils.isNullOrBlank(osmWayKey)){
      LOGGER.warning(String.format("OSM way value has no compatible waterway key, cannot create link segment types", osmWayValue));
      return null;
    }

    Map<NetworkLayer, MacroscopicLinkSegmentType> linkSegmentTypes = null;

    /* only when way type is marked as supported in settings we parse it */
    if(settings.getWaterwaySettings().isOsmWaterwayTypeActivated(osmWayValue)) {
      var waterwaySettings = settings.getWaterwaySettings();
      boolean isOverwrite = waterwaySettings.isDefaultCapacityOrMaxDensityOverwrittenByOsmWaterwayRouteType(osmWayValue);

      Set<PredefinedMode> activatedPlanitModes = getAvailableModesFromModeTypes(settings.getActivatedPlanitModeTypes(
          waterwaySettings.collectAllowedOsmWaterwayModes(osmWayValue)));
      if(!activatedPlanitModes.isEmpty()) {

        /* create the PLANit link segment type based on OSM tag and possibly overwritten default values*/
        double maxSpeedKmH = waterwaySettings.getDefaultSpeedLimitByOsmWaterwayType(osmWayValue);
        if(isOverwrite) {
          /* type is overwritten, so use overwritten data instead of defaults */
          final Pair<Double,Double> capacityDensityPair = waterwaySettings.getOverwrittenCapacityMaxDensityByOsmWaterwayRouteType(osmWayValue);
          linkSegmentTypes = createOsmLinkSegmentType(osmWayValue, capacityDensityPair.first(), capacityDensityPair.second(), maxSpeedKmH, activatedPlanitModes);
        }else {
          /* use default link segment type values */
          linkSegmentTypes = createOsmWaterWayLinkSegmentType(osmWayValue, maxSpeedKmH, activatedPlanitModes);
        }

        /* log */
        for(Entry<NetworkLayer, MacroscopicLinkSegmentType> entry: linkSegmentTypes.entrySet()) {
          NetworkLayer layer = entry.getKey();
          MacroscopicLinkSegmentType linkSegmentType = entry.getValue();

          String csvModeString = String.join(",", linkSegmentType.getAllowedModes().stream().map(
              (mode) -> mode.getName()).collect(Collectors.joining(",")));
          LOGGER.info(String.format("%s %s %s=%s - modes: %s speed: %s (km/h)",
              NetworkLayer.createLayerLogPrefix(layer), isOverwrite ? "[OVERWRITE] " : "[DEFAULT]",
              osmWayKey , osmWayValue, csvModeString, maxSpeedKmH));
        }

      }else {
        LOGGER.warning(String.format("IGNORE: %s=%s is supported but none of the default modes are mapped for the link segment type",
            osmWayKey , osmWayValue));
      }
    }
    else {
      /* ... not supported and no replacement available skip type entirely*/
      LOGGER.info(String.format(
          "Waterway (%s=%s) chosen to be included in network, but not available as supported type by reader, exclude from processing %s", osmWayKey, osmWayValue));
    }
    return linkSegmentTypes;
  }

  /** given predefined mode types, obtain the mode instances on the network that correspond to them
   *
   * @param predefinedModeTypes to collect modes for
   * @return predefined mode types found
   * */
  protected Set<PredefinedMode> getAvailableModesFromModeTypes(Collection<PredefinedModeType> predefinedModeTypes){
    return predefinedModeTypes.stream().map( mt -> getModes().get(mt)).filter( m -> m != null).collect(Collectors.toUnmodifiableSet());
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

    this.defaultPlanitOsmLinkSegmentTypes = new HashMap<>();
  }

  /**
   * Find the link segment type (per layer) by the Highway/railway/waterwayroute=value, where we pass in the "value"
   *
   * @param osmWayKey to use
   * @param osmWayTypeTagValue the type of road,rail,waterway to find
   * @return the link segment type that is registered per layer
   */
  public Map<NetworkLayer, MacroscopicLinkSegmentType> getDefaultLinkSegmentTypeByOsmTag(String osmWayKey, String osmWayTypeTagValue) {
    var availableTypes = this.defaultPlanitOsmLinkSegmentTypes.get(osmWayKey);
    if(availableTypes == null){
      return null;
    }
    return availableTypes.get(osmWayTypeTagValue);
  }
  
  /**
   * Create the link segment types that are marked in the passed in settings. As long as they have defaults that
   * are supported, these will be created as indicated. If not available a warning is issued and a link segment type is created based on the default chosen in settings
   * 
   * @param settings to use
   */
  public void createAndRegisterOsmCompatibleLinkSegmentTypes(OsmNetworkReaderSettings settings) {
    
    /* combine rail, highway, waterway */
    Map<String,Set<String>> combinedWayLikeTypeMap = new HashMap<>();
    if(settings.isHighwayParserActive()) {
      combinedWayLikeTypeMap = settings.getHighwaySettings().getSetOfActivatedOsmWayLikeTypes();
    }
    if(settings.isRailwayParserActive()) {
      var keyValueMap = settings.getRailwaySettings().getSetOfActivatedOsmWayLikeTypes();
      combinedWayLikeTypeMap.putAll(keyValueMap);
    }
    if(settings.isWaterwayParserActive()) {
      var keyValueMap = settings.getWaterwaySettings().getSetOfActivatedOsmWayLikeTypes();
      combinedWayLikeTypeMap.putAll(keyValueMap);
    }
    
    /* ------------------ FOR EACH SUPPORTED OSM WAY TYPE ----------------------------------------- */   
    for(Entry<String,Set<String>> entry : combinedWayLikeTypeMap.entrySet()) {
      String osmWayLikeKey = entry.getKey();
      defaultPlanitOsmLinkSegmentTypes.putIfAbsent(osmWayLikeKey, new HashMap<>());
      var defaultTypesToPopulate = defaultPlanitOsmLinkSegmentTypes.get(osmWayLikeKey);

      for(var osmWayLikeValueToUse : entry.getValue()) {

        /* ------------------ LINK SEGMENT TYPE ----------------------------------------------- */
        Map<NetworkLayer, MacroscopicLinkSegmentType> linkSegmentTypesByLayer = null;

        /* only create type when there are one or more activated modes for it */
        Set<PredefinedModeType> activatedPlanitModeTypes = collectMappedPlanitModeTypes(osmWayLikeKey, osmWayLikeValueToUse, settings);
        if (activatedPlanitModeTypes != null && !activatedPlanitModeTypes.isEmpty()) {

          if (OsmHighwayTags.isHighwayKeyTag(osmWayLikeKey) && OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayLikeValueToUse)) {
            linkSegmentTypesByLayer = createOsmCompatibleRoadLinkSegmentTypeByLayer(osmWayLikeValueToUse, settings);
          } else if (OsmRailwayTags.isRailwayKeyTag(osmWayLikeKey) && OsmRailwayTags.isRailBasedRailway(osmWayLikeValueToUse)) {
            linkSegmentTypesByLayer = createOsmCompatibleRailLinkSegmentTypeByLayer(osmWayLikeValueToUse, settings);
          } else if (OsmWaterwayTags.isWaterBasedWay(osmWayLikeKey, osmWayLikeValueToUse)) {
            linkSegmentTypesByLayer = createOsmCompatibleWaterLinkSegmentTypeByLayer(osmWayLikeValueToUse, settings);
          } else {
            LOGGER.severe(String.format("DISCARD: OSM %s=%s combination not recognised as valid when creating OSM compatible link segment types", osmWayLikeKey, osmWayLikeValueToUse));
          }
          /* ------------------ LINK SEGMENT TYPE ----------------------------------------------- */

          if (linkSegmentTypesByLayer == null || linkSegmentTypesByLayer.isEmpty()) {
            LOGGER.warning(String.format("DISCARD: Unable to create osm compatible PLANit link segment type for %s=%s", osmWayLikeKey, osmWayLikeValueToUse));
          } else {
            /* create, register, and also store by OSM tag */
            defaultTypesToPopulate.put(osmWayLikeValueToUse, linkSegmentTypesByLayer);
          }
        }
      }
    }
    /* ------------------ FOR EACH OSM WAY TYPE ----------------------------------------- */
  }

  /**
   * Based on the settings we create instances of the activated OSM to PLANit mode mappings,
   * for each created mode, append the OSM modes its represents as a list of semicolon separated entries
   *
   * @param settings to extract information from
   */
  public void createAndRegisterOsmCompatiblePlanitPredefinedModes(OsmNetworkReaderSettings settings) {
    if(!getModes().isEmpty()){
      LOGGER.severe("Initialising modes on OSM network, but found pre-existing modes on this supposedly empty network, shouldn't happen");
    }

    /* initialise road and rail modes on PLANit network as mode instances rather than the type placeholders */
    var mappedPlanitModes = settings.getActivatedPlanitModeTypes();
    for(var modeType : mappedPlanitModes){
      var newMode = getModes().getFactory().registerNew(modeType);
      newMode.appendExternalId(settings.getMappedOsmModes(modeType).stream().collect(Collectors.joining(";")), ';');
    }

  }

}

package org.planit.osm.settings.network;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitOpenGisUtils;
import org.planit.network.InfrastructureLayersConfigurator;
import org.planit.osm.defaults.OsmLaneDefaults;
import org.planit.osm.defaults.OsmModeAccessDefaults;
import org.planit.osm.defaults.OsmModeAccessDefaultsByCountry;
import org.planit.osm.defaults.OsmSpeedLimitDefaults;
import org.planit.osm.defaults.OsmSpeedLimitDefaultsByCountry;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.geo.PlanitJtsUtils;
import org.planit.utils.mode.Mode;
import org.planit.utils.mode.Modes;

/**
 * All general settings (and subsettings classes) for the OSM reader pertaining to parsing  network infrastructure.
 * contains additional settings for highway and railway (e.g., highway settings and reailway settings members, respectively).
 * 
 * @author markr
 *
 */
public class PlanitOsmNetworkSettings {
    
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmNetworkSettings.class.getCanonicalName());
    
  
  /** all settings specific to osm railway tags */
  protected PlanitOsmRailwaySettings osmRailwaySettings;
  
  /** all settings specific to osm highway tags*/
  protected PlanitOsmHighwaySettings osmHighwaySettings; 
  
  /** the country we are importing for (if any) */
  protected final String countryName;
           
  /** the default speed limits used in case no explicit information is available on the osmway's tags */
  protected final OsmSpeedLimitDefaults speedLimitConfiguration;
  
  /** the default mode access configuration used in case no explicit access information is available on the osmway's tags */
  protected final OsmModeAccessDefaults modeAccessConfiguration;  
  
  /** the default number of lanes used in case no explicit information is available on the osmway's tags */
  protected final OsmLaneDefaults laneConfiguration = new OsmLaneDefaults();  
      
  /** allow users to provide OSM way ids for ways that we are not to parse, for example when we know the original coding or tagging is problematic */
  protected final Set<Long>  excludedOsmWays = new HashSet<Long>();  
  
  /** mapping between PLANit modes and the layer their infrastructure will be mapped to. 
   * Default is based on {@link InfrastructureLayersConfigurator.createAllInOneConfiguration}, but the user can overwrite these settings if
   * desired */
  protected InfrastructureLayersConfigurator planitInfrastructureLayerConfiguration;
  
  /* SETTINGS */
  
  /** the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitOpenGisUtils.DEFAULT_GEOGRAPHIC_CRS;  
                  
  /**
   * option to track the geometry of an OSM way, i.e., extract the line string for link segments from the nodes
   * (default is false). When set to true parsing will be somewhat slower 
   */
  protected boolean parseOsmWayGeometry = DEFAULT_PARSE_OSMWAY_GEOMETRY;
    
  /** flag indicating if dangling subnetworks should be removed after parsing the network
   * OSM network often have small roads that appear to be connected to larger roads, but in fact are not. 
   * All subnetworks that are not part of the largest subnetwork that is parsed will be removed by default. 
   * */
  protected boolean removeDanglingSubNetworks = DEFAULT_REMOVE_DANGLING_SUBNETWORK;
  
  /**
   * When dangling subnetworks are marked for removal, this threshold determines the minimum subnetwork size for it NOT to be removed.
   * In other words, all subnetworks below this number will be removed
   */
  protected int discardSubNetworkBelowSize = DEFAULT_MINIMUM_SUBNETWORK_SIZE;
  
  /**
   * When dangling subnetworks are marked for removal, this threshold determines the maximum subnetwork size for it NOT to be removed.
   * In other words, all subnetworks above this number will be removed, including the largest one if it does not match the value
   */  
  protected int discardSubNetworkAbovesize = Integer.MAX_VALUE;
  
  /**
   * indicate whether or not to keep the largest subnetwork when {@code removeDanglingSubNetworks} is set to true even when it does
   * not adhere to the criteria of {@code discardSubNetworkBelowSize} and/or {@code discardSubNetworkAbovesize} 
   */
  protected boolean alwaysKeepLargestsubNetwork = DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK;
  
  /** add osmModeId to planit external id (in case multiple osm modes are mapped to the same planit mode)
   * @param planitMode to update external id for
   * @param osModeId to use
   */
  protected static void addToModeExternalId(Mode planitMode, String osModeId){
    if(planitMode != null) {
      if(planitMode.hasExternalId()) {
        planitMode.setExternalId(planitMode.getExternalId().concat("_").concat(osModeId));
      }else {
        planitMode.setExternalId(osModeId);
      }
    }
  }
  
  /** remove osmModeId to planit external id (in case multiple osm modes are mapped to the same planit mode)
   * @param planitMode to update external id for
   * @param osModeId to use
   */  
  protected static void removeFromModeExternalId(Mode planitMode, String osModeId){
    if(planitMode!= null && planitMode.hasExternalId()) {
      int startIndex = planitMode.getExternalId().indexOf(osModeId);
      if(startIndex == -1) {
        /* not present */
        return;
      }
      if(startIndex==0) {
        /* first */
        planitMode.setExternalId(planitMode.getExternalId().substring(startIndex+osModeId.length()));
      }else {
        /* not first, so preceded by underscore "*_<name>" */
        String before = planitMode.getExternalId().substring(0,startIndex-1);
        String after = planitMode.getExternalId().substring(startIndex+osModeId.length());
        planitMode.setExternalId(before.concat(after));
      }
    }
  }  
    
  /**
   * conduct general initialisation for any instance of this class
   * 
   * @param planitModes to populate based on (default) mapping
   */
  protected void initialise(Modes planitModes) {
    try {  
      /* the default mapping from OSM modes to PLANit modes */
      initialiseDefaultMappingFromOsmModes2PlanitModes(planitModes);
    } catch (PlanItException e) {
      LOGGER.severe("unable to create default supported and/or unsupported OSM link segment types for this network");
    }     
  }
    
    
  /**
   * Map both road and rail modes from OSM modes to PLANit modes
   * 
   * @param planitModes to populate based on (default) mapping
   * @throws PlanItException thrown if error
   */
  protected void initialiseDefaultMappingFromOsmModes2PlanitModes(Modes planitModes) throws PlanItException {
    osmHighwaySettings.initialiseDefaultMappingFromOsmRoadModes2PlanitModes(planitModes);
    osmRailwaySettings.initialiseDefaultMappingFromOsmRailModes2PlanitModes(planitModes);
  }

     
  
  /**
   * explicitly exclude all osmWay type:value in case none of the passed in osmModes is marked as mapped
   * 
   * @param osmWayKey to check
   * @param osmWayValue to check
   * @param osmModes of which at least one should be active on the key:value pair
   */
  protected void excludeOsmWayTypesWithoutModes(String osmWayKey, String osmWayValue, Collection<String> osmModes) {
    
    boolean hasMappedMode = false;
    if(osmModes != null) {
      for(String osmMode : osmModes) {
        if(hasMappedPlanitMode(osmMode)) {
          hasMappedMode = true;
          break;
        }
      } 
    }
    
    if(!hasMappedMode) {
      if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
        osmHighwaySettings.deactivateOsmHighwayType(osmWayValue);
      }else if(OsmRailwayTags.isRailwayKeyTag(osmWayKey)){
        osmRailwaySettings.deactivateOsmRailwayType(osmWayValue);
      }
    } 
  }     
    
  /** the default crs is set to {@code  PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS} */
  public static CoordinateReferenceSystem DEFAULT_SOURCE_CRS = PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS;
      
  /** default value for parsing OSM way geometry: false */
  public static boolean DEFAULT_PARSE_OSMWAY_GEOMETRY = false;  
  
  /** Default whether or not we are removing dangling subnetworks after parsing: true */
  public static boolean DEFAULT_REMOVE_DANGLING_SUBNETWORK = true;
  
  /** Default minimum size of subnetwork for it not to be removed when dangling subnetworks are removed, size indicates number of vertices: 20 */
  public static int DEFAULT_MINIMUM_SUBNETWORK_SIZE= 20;  
  
  /** by default we always keep the largest subnetwork */
  public static boolean DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK = true;
    
  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on, 
   * for various osm highway types in case maximum speed limit information is missing
   * 
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   * @param planitModes to populate based on (default) mapping
   */
  public PlanitOsmNetworkSettings(String countryName, Modes planitModes) {
    this.countryName = countryName;
    
    /* general */
    this.speedLimitConfiguration = OsmSpeedLimitDefaultsByCountry.create(countryName);
    this.modeAccessConfiguration = OsmModeAccessDefaultsByCountry.create(countryName);
    
    /* settings by sub-type */
    this.osmHighwaySettings = new PlanitOsmHighwaySettings(
        this.speedLimitConfiguration.getUrbanHighwayDefaults(), 
        this.speedLimitConfiguration.getNonUrbanHighwayDefaults(),
        this.modeAccessConfiguration.getHighwayModeAccessDefaults());
    this.osmRailwaySettings = new PlanitOsmRailwaySettings(
        this.speedLimitConfiguration.getRailwayDefaults(), 
        this.modeAccessConfiguration.getRailwayModeAccessDefaults());
    
    initialise(planitModes);   
    
    /* default will map all modes to a single layer */
    this.planitInfrastructureLayerConfiguration = InfrastructureLayersConfigurator.createAllInOneConfiguration(planitModes);
  }    

  /**
   * Default constructor. Here no specific locale is provided, meaning that all defaults will use global settings. This is especially relevant for
   * speed limits and mdoe access restrictions (unless manually adjusted by the user)
   * 
   * @param planitModes to populate based on (default) mapping
   * 
   */  
  public PlanitOsmNetworkSettings(Modes planitModes) {
    this( "", planitModes);
  }
  
  /** activate the parsing of railways
   * @param activate when true activate railway parsing, when false deactivate
   * @return railway settings that are activated, null when deactivated
   */
  public PlanitOsmRailwaySettings activateRailwayParser(boolean activate) {
    osmRailwaySettings.activateParser(activate);
    return getRailwaySettings();      
  }
  
  /** activate the parsing of highways
   * @param activate when true activate highway parsing, when false deactivate
   * @return highway settings that are activated, null when deactivated
   */
  public PlanitOsmHighwaySettings activateHighwayParser(boolean activate) {
    osmHighwaySettings.activateParser(activate);
    return getHighwaySettings();
  }     
  
  /** Verify if railway parser is active
   * 
   * @return true when active false otherwise
   */
  public boolean isRailwayParserActive() {
    return osmRailwaySettings.isParserActive();
  }
  
  /** Verify if railway parser is active
   * 
   * @return true when active false otherwise
   */
  public boolean isHighwayParserActive() {
    return osmHighwaySettings.isParserActive();
  }   

  /**
   * chosen crs, default is {@code PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS}
   * @return
   */
  public final CoordinateReferenceSystem getSourceCRS() {
    return sourceCRS;
  }

  /**
   * Override source CRS
   * 
   * @param sourceCRS
   */
  public void setSourceCRS(final CoordinateReferenceSystem sourceCRS) {
    this.sourceCRS = sourceCRS;
  }
    
  /**
   * explicitly exclude all osmWay types that are included but have no more activated modes due to deactivation of their default assigned modes.
   * Doing so avoids the reader to log warnings that supported way types cannot be injected in the network because they
   * have no viable modes attached
   * 
   * :TODO move somewhere else, not used from perspective of user
   */
  public void excludeOsmWayTypesWithoutActivatedModes() {
    Set<String> originallySupportedTypes = osmHighwaySettings.highwayTypeConfiguration.setOfActivatedTypes();
    for(String supportedHighWayType : originallySupportedTypes) {
      Collection<String> allowedOsmModes = osmHighwaySettings.collectAllowedOsmHighwayModes(supportedHighWayType);
      excludeOsmWayTypesWithoutModes(OsmHighwayTags.HIGHWAY, supportedHighWayType, allowedOsmModes);      
    }
    
    if(isRailwayParserActive()) {
      originallySupportedTypes = osmRailwaySettings.railwayTypeConfiguration.setOfActivatedTypes();
      for(String supportedRailWayType : originallySupportedTypes) {
        Collection<String> allowedOsmModes = osmRailwaySettings.collectAllowedOsmRailwayModes(supportedRailWayType);
        excludeOsmWayTypesWithoutModes(OsmRailwayTags.RAILWAY, supportedRailWayType, allowedOsmModes);      
      }       
    }   
  }      
     
  /**
   * indicate whether to remove dangling subnetworks or not
   * @param removeDanglingSubnetworks yes or no
   */
  public void setRemoveDanglingSubnetworks(boolean removeDanglingSubnetworks) {
    this.removeDanglingSubNetworks = removeDanglingSubnetworks;
  }
  
  /** verify if dangling subnetworks are removed from the final network
   * @return
   */
  public boolean isRemoveDanglingSubnetworks() {
    return this.removeDanglingSubNetworks;
  }  
       
  
  /** collect the current configuration setup for applying number of lanes in case the lanes tag is not available on the parsed osmway
   * @return lane configuration containing all defaults for various osm highway types
   */
  public OsmLaneDefaults getLaneConfiguration() {
    return this.laneConfiguration;
  }  
  
  /** Collect the default speed limit for a given highway or railway tag value, where we extract the key and value from the passed in tags, if available
   * 
   * @param tags to extract way key value pair from (highway,railway keys currently supported)
   * @return speedLimit in km/h (for highway types, the outside or inside urban area depending on the setting of the flag setSpeedLimitDefaultsBasedOnUrbanArea is collected)
   * @throws PlanItException thrown if error
   */    
  public Double getDefaultSpeedLimitByOsmWayType(Map<String, String> tags) throws PlanItException {
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      return osmHighwaySettings.getDefaultSpeedLimitByOsmHighwayType(tags.get(OsmHighwayTags.HIGHWAY));   
    }else if(tags.containsKey(OsmRailwayTags.RAILWAY)){
      return osmRailwaySettings.getDefaultSpeedLimitByOsmRailwayType(tags.get(OsmRailwayTags.RAILWAY));
    }else {
      throw new PlanItException("no default speed limit available, tags do not contain activated highway or railway key");
    }
  }   

  /** Collect the number of lanes/tracks for a given OSM way key/value for either direction (not total), 
   * e.g. highway=value, railway=value based on the defaults provided
   * 
   * @param type highway type to collect default lanes for
   * @return number of default lanes
   * @throws PlanItException thrown if error
   */
  public Integer getDefaultDirectionalLanesByWayType(String osmWayKey, String osmWayValue) {
    return this.laneConfiguration.getDefaultDirectionalLanesByWayType(osmWayKey, osmWayValue);    
  }
    
  /** convenience method that collects the currently mapped PLANit mode (road or rail) for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public Mode getMappedPlanitMode(final String osmMode) {
    Mode theMode = osmHighwaySettings.getMappedPlanitRoadMode(osmMode);
    if(theMode == null) {
      theMode = osmRailwaySettings.getMappedPlanitRailMode(osmMode);
    }
    return theMode;
  } 
  
  /** convenience method that collects the currently mapped PLANit modes (road or rail) for the given OSM modes
   * 
   * @param osmModes to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available empty set is returned
   */
  public Set<Mode> getMappedPlanitModes(final Collection<String> osmModes) {
    HashSet<Mode> mappedPlanitModes = new HashSet<Mode>();
    
    if(osmModes == null) {
      return mappedPlanitModes;
    } 
    
    for(String osmMode : osmModes) {
      Mode theMode = getMappedPlanitMode(osmMode);
      if(theMode != null) {
        mappedPlanitModes.add(theMode);
      }
    }    
    return mappedPlanitModes;  
  }   
    
  /** Verify if the passed in osmMode is mapped (either to road or rail mode), i.e., if it is actively included when reading the network
   * @param osmMode to verify
   * @return true if mapped, false otherwise
   */
  public boolean hasMappedPlanitMode(final String osmMode) {
    Mode mappedMode = osmHighwaySettings.getMappedPlanitRoadMode(osmMode);;
    if(mappedMode == null) {
      mappedMode = osmRailwaySettings.getMappedPlanitRailMode(osmMode);
    }
    return mappedMode != null;
  }
  
  /** Verify if any of the passed in osmModes are mapped, i.e., if it is actively included when reading the network
   * @param osmModes to verify
   * @return true if any is mapped, false otherwise
   */  
  public boolean hasAnyMappedPlanitMode(final String... osmModes) {
    for(int index=0;index<osmModes.length;++index) {
      if(hasMappedPlanitMode(osmModes[index])) {
        return true;
      }
    }
    return false;
  }  

  /** When country is set for settings this will return the chosen country
   * @return countryName
   */
  public final String getCountryName() {
    return this.countryName;
  }

  /** the minimum size an identified dangling network must have for it to NOT be removed when danlging networks are removed
   * 
   * @param discardBelow this number of vertices
   */
  public void setDiscardDanglingNetworksBelow(int discardBelow) {
    this.discardSubNetworkBelowSize = discardBelow;
  }
  
  /** allows you to set a maximum size for dangling subnetwork. Practically only useful for debugging purposes
   * 
   * @param discardAbove this number of vertices
   */
  public void setDiscardDanglingNetworksAbove(int discardAbove) {
    this.discardSubNetworkAbovesize = discardAbove;
  }  
  
  /** collect the size above which dangling networks are kept even if they are smaller than the largest connected network
   * @return danlging network size
   */
  public Integer getDiscardDanglingNetworkBelowSize() {
    return discardSubNetworkBelowSize;
  }  
  
  /** collect the size below which networks are removed 
   * @return dangling network size
   */
  public Integer getDiscardDanglingNetworkAboveSize() {
    return discardSubNetworkAbovesize;
  }    
  
  /** Verify if the largest subnetwork is always kept when we are removing dangling subnetworks
   * 
   * @return true when kept false otherwise
   */
  public boolean isAlwaysKeepLargestsubNetwork() {
    return alwaysKeepLargestsubNetwork;
  }

  /** indicate to keep the largest subnetwork always even when removing dangling subnetworks and the largest one
   * does not fit the set criteria
   * 
   * @param alwaysKeepLargestsubNetwork when true we always keep it, otherwise not
   */
  public void setAlwaysKeepLargestsubNetwork(boolean alwaysKeepLargestsubNetwork) {
    this.alwaysKeepLargestsubNetwork = alwaysKeepLargestsubNetwork;
  }  

  /**
   * deactivate all types for both rail and highway
   */
  public void deactivateAllOsmWayTypes() {    
    osmHighwaySettings.highwayTypeConfiguration.deactivateAll();
    osmRailwaySettings.deactivateAllOsmRailWayTypes();
  }

  /** deactive all osm way types except the ones indicated, meaning that if the ones passed in
   * are not already active, they will be marked as activate afterwards
   * @param osmWaytypes to mark as activated
   */
  public void deactivateAllOsmWayTypesExcept(String... osmWaytypes) {
    deactivateAllOsmWayTypes();
    for(String osmWayType : Arrays.asList(osmWaytypes)) {
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayType)) {
        osmHighwaySettings.activateOsmHighwayWayTypes(osmWayType);
      }else if(OsmRailwayTags.isRailBasedRailway(osmWayType)) {
        osmRailwaySettings.activateOsmRailwayType(osmWayType);
      }
    }
  }
  
  /**
   * exclude specific OSM ways from being parsed based on their id
   * 
   * @param osmWayId to mark as excluded
   */
  public void excludeOsmWayFromParsing(long osmWayId) {
    if(osmWayId <= 0) {
      LOGGER.warning(String.format("invalid OSM way id provided to be excluded, ignored", osmWayId));
      return;
    }
    excludedOsmWays.add(osmWayId);
  }
  
  /**
   * exclude specific OSM ways from being parsed based on their id
   * 
   * @param osmWayId to mark as excluded
   */
  public void excludeOsmWaysFromParsing(Long... osmWayIds) {
    if(osmWayIds==null) {
      LOGGER.warning(String.format("OSM way ids are null, ignored excluding them"));
      return;
    }    
    Stream.of(osmWayIds).forEach(osmWayId -> excludeOsmWayFromParsing(osmWayId));
  }  

  /** Verify if provided way id is excluded or not
   * 
   * @param osmWayId to verify
   * @return true if excluded, false otherwise
   */
  public boolean isOsmWayExcluded(Long osmWayId) {
    return excludedOsmWays.contains(osmWayId);
  }
  
  /**
   * Log all de-activated OSM way types
   */  
  public void logUnsupportedOsmWayTypes() {
    osmHighwaySettings.logUnsupportedOsmHighwayTypes();
    if(isRailwayParserActive()) {
      osmRailwaySettings.logUnsupportedOsmRailwayTypes();
    }
  }

  /** provide railway specific settings
   * @return railway settings , null when not activated
   */
  public PlanitOsmRailwaySettings getRailwaySettings() {
    return isRailwayParserActive() ? osmRailwaySettings : null ;
  }
  
  /** provide highway specific settings
   * @return highway settings , null when not activated
   */
  public PlanitOsmHighwaySettings getHighwaySettings() {
    return isHighwayParserActive() ? osmHighwaySettings : null ;
  } 
  
  /**
   * Allows access to the current planit infrastructure layer configuration which maps planit modes
   * to an infrastructure layer on the to be created PLANit network
   * 
   * @return infrastructure layer to mode configuration
   */
  public InfrastructureLayersConfigurator getPlanitInfrastructureLayerConfiguration() {
    return planitInfrastructureLayerConfiguration;
  }

  /** provide a new configuration other than the one provided by default
   * 
   * @param planitInfrastructureLayerConfiguration to use
   */
  public void setPlanitInfrastructureLayerConfiguration(
      InfrastructureLayersConfigurator planitInfrastructureLayerConfiguration) {
    this.planitInfrastructureLayerConfiguration = planitInfrastructureLayerConfiguration;
  }   
 
}

package org.goplanit.osm.converter.network;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.goplanit.osm.converter.OsmReaderSettings;
import org.goplanit.osm.defaults.OsmLaneDefaults;
import org.goplanit.osm.defaults.OsmModeAccessDefaults;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsByCountry;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaults;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsByCountry;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * All general settings (and sub-settings classes) for the OSM reader pertaining to parsing  network infrastructure.
 * contains additional settings for highway and railway (e.g., highway settings and railway settings members, respectively).
 * 
 * @author markr
 *
 */
public class OsmNetworkReaderSettings extends OsmReaderSettings{
    
  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderSettings.class.getCanonicalName());

  /** all settings specific to osm railway tags */
  protected OsmRailwaySettings osmRailwaySettings;
  
  /** all settings specific to osm highway tags*/
  protected OsmHighwaySettings osmHighwaySettings;

  /** all settings specific to OSM waterway (ferry) tags*/
  protected OsmWaterwaySettings osmWaterwaySettings;

  /** the default speed limits used in case no explicit information is available on the osmway's tags */
  protected final OsmSpeedLimitDefaults speedLimitConfiguration;
  
  /** the default mode access configuration used in case no explicit access information is available on the osmway's tags */
  protected final OsmModeAccessDefaults modeAccessConfiguration;  
  
  /** the default number of lanes used in case no explicit information is available on the osmway's tags */
  protected final OsmLaneDefaults laneConfiguration = new OsmLaneDefaults();  
      
  /** allow users to provide OSM way ids for ways that we are not to parse, for example when we know the original coding or tagging is problematic */
  protected final Set<Long>  excludedOsmWays = new HashSet<>();
  
  /** Allow users to provide OSM way ids for ways that we are to keep even if they fall (partially) outside a bounding polygon, 
   * for example when we know the OSM way meanders in and outside the polygon and we want to have a connected network and proper lengths for this way */
  protected final Set<Long>  includedOutsideBoundingPolygonOsmWays = new HashSet<>();
  
  /** Allow users to provide OSM node ids for nodes that we are not to keep even if they fall outside a bounding polygon */
  protected final Set<Long>  includedOutsideBoundingPolygonOsmNodes = new HashSet<>();
 
    
  /**
   * track overwritten mode access values for specific osm ways by osm id. Can be used in case the OSM file is incorrectly tagged which causes problems
   * in the memory model. Here one can be manually overwrite the allowable modes for this particular way.
   */
  protected final Map<Long, Set<String>> overwriteOsmWayModeAccess = new HashMap<>();

  /* SETTINGS */
  
  /** the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;  
                  
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
      
  /**
   * Conduct general initialisation for any instance of this class
   */
  protected void initialise() {
    /* the default mapping from OSM modes to PLANit modes */
    initialiseDefaultMappingFromOsmModes2PlanitModes();
  }
    
    
  /**
   * Map both road and rail modes from OSM modes to PLANit modes
   * 
   */
  protected void initialiseDefaultMappingFromOsmModes2PlanitModes() {
    osmHighwaySettings.initialiseDefaultMappingFromOsmRoadModes2PlanitModes();
    osmRailwaySettings.initialiseDefaultMappingFromOsmRailModes2PlanitModes();
  }

  /** the default crs is set to {@code  PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS} */
  public static CoordinateReferenceSystem DEFAULT_SOURCE_CRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;
      
  /** default value for parsing OSM way geometry: false */
  public static boolean DEFAULT_PARSE_OSMWAY_GEOMETRY = false;  
  
  /** Default whether we are removing dangling subnetworks after parsing: true */
  public static boolean DEFAULT_REMOVE_DANGLING_SUBNETWORK = true;
  
  /** Default minimum size of subnetwork for it not to be removed when dangling subnetworks are removed, size indicates number of vertices: 20 */
  public static int DEFAULT_MINIMUM_SUBNETWORK_SIZE= 20;  
  
  /** by default we always keep the largest subnetwork */
  public static boolean DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK = true;

  /**
   * Default constructor. Here no specific locale is provided, meaning that all defaults will use global settings. This is especially relevant for
   * speed limits and mdoe access restrictions (unless manually adjusted by the user)
   *
   */
  public OsmNetworkReaderSettings() {
    this(CountryNames.GLOBAL);
  }

  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on, 
   * for various OSM highway types in case maximum speed limit information is missing
   * 
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   */
  public OsmNetworkReaderSettings(String countryName) {
    this((URL) null, countryName);
  }

  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on,
   * for various OSM highway types in case maximum speed limit information is missing
   *
   * @param inputSource to use, expected local file location
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   */
  public OsmNetworkReaderSettings(String inputSource, String countryName) {
    this(UrlUtils.createFromPath(Path.of(inputSource)), countryName);
  }

  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on, 
   * for various OSM highway types in case maximum speed limit information is missing
   * 
   * @param inputSource to use
   * @param countryName the full country name to use speed limit data for, see also the OsmSpeedLimitDefaultsByCountry class
   */
  public OsmNetworkReaderSettings(URL inputSource, String countryName) {
    super(inputSource, countryName);
    
    /* general */
    this.speedLimitConfiguration = OsmSpeedLimitDefaultsByCountry.create(countryName);
    this.modeAccessConfiguration = OsmModeAccessDefaultsByCountry.create(countryName);
    
    /* settings by sub-type */
    this.osmHighwaySettings = new OsmHighwaySettings(
        this.speedLimitConfiguration.getUrbanHighwayDefaults(), 
        this.speedLimitConfiguration.getNonUrbanHighwayDefaults(),
        this.modeAccessConfiguration.getHighwayModeAccessDefaults());
    this.osmRailwaySettings = new OsmRailwaySettings(
        this.speedLimitConfiguration.getRailwayDefaults(), 
        this.modeAccessConfiguration.getRailwayModeAccessDefaults());
    this.osmWaterwaySettings = new OsmWaterwaySettings(
        this.speedLimitConfiguration.getWaterwayDefaults(),
        this.modeAccessConfiguration.getWaterwayModeAccessDefaults());
    
    initialise();
  }   
        
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // TODO    
  }
  
  /** activate the parsing of railways
   * @param activate when true activate railway parsing, when false deactivate
   * @return railway settings that are activated, null when deactivated
   */
  public OsmRailwaySettings activateRailwayParser(boolean activate) {
    osmRailwaySettings.activateParser(activate);
    return getRailwaySettings();      
  }

  /** activate the parsing of water ways
   * @param activate when true activate waterway parsing, when false deactivate
   * @return water way settings that are activated, null when deactivated
   */
  public OsmWaterwaySettings activateWaterwayParser(boolean activate) {
    osmWaterwaySettings.activateParser(activate);
    return getWaterwaySettings();
  }
  
  /** activate the parsing of highways
   * @param activate when true activate highway parsing, when false deactivate
   * @return highway settings that are activated, null when deactivated
   */
  public OsmHighwaySettings activateHighwayParser(boolean activate) {
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

  /** Verify if waterway parser is active
   *
   * @return true when active false otherwise
   */
  public boolean isWaterwayParserActive() {
    return osmWaterwaySettings.isParserActive();
  }

  /** Chosen crs, default is {@code PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS}
   * 
   * @return source CRS
   */
  public final CoordinateReferenceSystem getSourceCRS() {
    return sourceCRS;
  }

  /**
   * Override source CRS
   * 
   * @param sourceCRS to use
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
    osmHighwaySettings.excludeOsmWayTypesWithoutActivatedModes();
    osmRailwaySettings.excludeOsmWayTypesWithoutActivatedModes();
  }
     
  /**
   * Indicate whether to remove dangling subnetworks or not
   * 
   * @param removeDanglingSubnetworks yes or no
   */
  public void setRemoveDanglingSubnetworks(boolean removeDanglingSubnetworks) {
    this.removeDanglingSubNetworks = removeDanglingSubnetworks;
  }
  
  /** Verify if dangling subnetworks are removed from the final network
   * 
   * @return flag if dangling networks are removed
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
   */    
  public Double getDefaultSpeedLimitByOsmWayType(Map<String, String> tags){
    if(tags.containsKey(OsmHighwayTags.HIGHWAY)) {
      return osmHighwaySettings.getDefaultSpeedLimitByOsmHighwayType(tags.get(OsmHighwayTags.HIGHWAY));   
    }else if(tags.containsKey(OsmRailwayTags.RAILWAY)){
      return osmRailwaySettings.getDefaultSpeedLimitByOsmRailwayType(tags.get(OsmRailwayTags.RAILWAY));
    }else {
      throw new PlanItRunTimeException("no default speed limit available, tags do not contain activated highway or railway key");
    }
  }   

  /** Collect the number of lanes/tracks for a given OSM way key/value for either direction (not total), 
   * e.g. highway=value, railway=value based on the defaults provided
   * 
   * @param osmWayKey way key to collect default lanes for
   * @param osmWayValue way value to collect default lanes for
   * @return number of default lanes
   */
  public Integer getDefaultDirectionalLanesByWayType(String osmWayKey, String osmWayValue) {
    return this.laneConfiguration.getDefaultDirectionalLanesByWayType(osmWayKey, osmWayValue);    
  }
  
  /** Collect the mapped OSM modes based on the provided PLANit mode
   * 
   * @param planitModeType to get mapped PLANit modes for
   * @return mapped OSM modes, empty if no matches
   */  
  public Collection<String> getMappedOsmModes(PredefinedModeType planitModeType) {
    Collection<String> theRoadModes  = osmHighwaySettings.getMappedOsmRoadModes(planitModeType);
    Collection<String> theOsmRailModes = osmRailwaySettings.getMappedOsmRailModes(planitModeType);
    theRoadModes.addAll(theOsmRailModes);
    return theRoadModes;
  }  
  
  /** Collect the mapped OSM modes based on the provided PLANit mode types (if any)
   * 
   * @param planitModeTypes to get mapped OSM modes for
   * @return mapped OSM modes, empty if no matches
   */
  public Set<String> getMappedOsmModes(Collection<PredefinedModeType> planitModeTypes) {
    HashSet<String> mappedOsmModes = new HashSet<>();
    
    if(planitModeTypes == null) {
      return mappedOsmModes;
    } 
    
    for(var planitModeType : planitModeTypes) {
      Collection<String> theModes = getMappedOsmModes(planitModeType);
      if(theModes != null) {
        mappedOsmModes.addAll(theModes);
      }
    }    
    return mappedOsmModes; 
  }   
    
  /** convenience method that collects the currently mapped PLANit mode (road or rail) for the given OSM mode
   * 
   * @param osmMode to collect mapped mode for (if any)
   * @return mapped PLANit mode, if not available null is returned
   */
  public PredefinedModeType getMappedPlanitModeType(final String osmMode) {
    var theMode = osmHighwaySettings.getMappedPlanitRoadMode(osmMode);
    if(theMode == null) {
      theMode = osmRailwaySettings.getMappedPlanitRailMode(osmMode);
    }
    return theMode;
  } 
  
  /** convenience method that collects the currently mapped PLANit modes (road or rail) for the given OSM modes
   * 
   * @param osmModes to collect mapped mode for (if any)
   * @return mapped PLANit modes, if not available empty set is returned
   */
  public Set<PredefinedModeType> getActivatedPlanitModeTypes(final Collection<String> osmModes) {
    HashSet<PredefinedModeType> mappedPlanitModes = new HashSet<>();
    
    if(osmModes == null) {
      return mappedPlanitModes;
    } 
    
    for(String osmMode : osmModes) {
      var theMode = getMappedPlanitModeType(osmMode);
      if(theMode != null) {
        mappedPlanitModes.add(theMode);
      }
    }    
    return mappedPlanitModes;  
  }

  /**
   * convenience method that collects all currently mapped PLANit mode types (road or rail)
   *
   * @return mapped PLANit mode types, if not available empty set is returned
   */
  public Set<PredefinedModeType> getActivatedPlanitModeTypes() {
    return Stream.concat(
        getHighwaySettings().getActivatedPlanitModeTypesStream(),
        getRailwaySettings().getActivatedPlanitModeTypesStream()).collect(Collectors.toUnmodifiableSet());
  }
    
  /** Verify if the passed in osmMode is mapped (either to road or rail mode type), i.e., if it is actively included when reading the network
   * @param osmMode to verify
   * @return true if mapped, false otherwise
   */
  public boolean hasMappedPlanitModeType(final String osmMode) {
    var mappedMode = osmHighwaySettings.getMappedPlanitRoadMode(osmMode);;
    if(mappedMode == null) {
      mappedMode = osmRailwaySettings.getMappedPlanitRailMode(osmMode);
    }
    return mappedMode != null;
  }
  
  /** Verify if any of the passed in osmModes are mapped, i.e., if it is actively included when reading the network
   * @param osmModes to verify
   * @return true if any is mapped, false otherwise
   */  
  public boolean hasAnyMappedPlanitModeType(final String... osmModes) {
    for(int index=0;index<osmModes.length;++index) {
      if(hasMappedPlanitModeType(osmModes[index])) {
        return true;
      }
    }
    return false;
  }  
  
  /** Verify if any of the passed in osmModes are mapped, i.e., if it is actively included when reading the network
   * @param osmModes to verify
   * @return true if any is mapped, false otherwise
   */  
  public boolean hasAnyMappedPlanitModeType(final Collection<String> osmModes) {
    if(osmModes!=null) {
      for(String osmMode : osmModes) {
        if(hasMappedPlanitModeType(osmMode)) {
          return true;
        }
      }
    }
    return false;
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
   * @return dangling network size
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
  public boolean isAlwaysKeepLargestSubnetwork() {
    return alwaysKeepLargestsubNetwork;
  }

  /** indicate to keep the largest subnetwork always even when removing dangling subnetworks and the largest one
   * does not fit the set criteria
   * 
   * @param alwaysKeepLargestSubnetwork when true we always keep it, otherwise not
   */
  public void setAlwaysKeepLargestSubnetwork(boolean alwaysKeepLargestSubnetwork) {
    this.alwaysKeepLargestsubNetwork = alwaysKeepLargestSubnetwork;
  }  

  /**
   * deactivate all types for both rail and highway
   */
  public void deactivateAllOsmWayTypes() {    
    osmHighwaySettings.deactivateAllOsmHighwayTypes();
    osmRailwaySettings.deactivateAllOsmRailwayTypes();
  }

  /** deactivate all osm way types except the ones indicated, meaning that if the ones passed in
   * are not already active, they will be marked as activate afterwards. Note that this deactivates all types
   * across both railways and highways. If you want to do this within highways only, use the same method under highway settings.
   * 
   * @param osmWaytypes to mark as activated
   */
  public void deactivateAllOsmWayTypesExcept(String... osmWaytypes) {
    deactivateAllOsmWayTypesExcept(Arrays.asList(osmWaytypes));
  }
  
  /** deactivate all osm way types except the ones indicated, meaning that if the ones passed in
   * are not already active, they will be marked as activate afterwards. Note that this deactivates all types
   * across both railways and highways. If you want to do this within highways only, use the same method under highway settings.
   * 
   * @param osmWaytypes to mark as activated
   */
  public void deactivateAllOsmWayTypesExcept(List<String> osmWaytypes) {
    deactivateAllOsmWayTypes();
    for(String osmWayType : osmWaytypes) {
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayType)) {
        osmHighwaySettings.activateOsmHighwayTypes(osmWayType);
      }else if(OsmRailwayTags.isRailBasedRailway(osmWayType)) {
        osmRailwaySettings.activateOsmRailwayType(osmWayType);
      }
    }
  }  
  
  /**
   * exclude specific OSM ways from being parsed based on their id
   * 
   * @param osmWayId to mark as excluded (int or long)
   */
  public void excludeOsmWayFromParsing(Number osmWayId) {
    if(osmWayId.longValue() <= 0) {
      LOGGER.warning(String.format("invalid OSM way id provided to be excluded, ignored", osmWayId.longValue()));
      return;
    }
    excludedOsmWays.add(osmWayId.longValue());
  }
  
  /**
   * exclude specific OSM ways from being parsed based on their id
   * 
   * @param osmWayIds to mark as excluded (int or long)
   */
  public void excludeOsmWaysFromParsing(Number... osmWayIds) {
    excludeOsmWaysFromParsing(Arrays.asList(osmWayIds));
  }  

  /**
   * exclude specific OSM ways from being parsed based on their id. It is expected that the
   * way ids are either an integer or long
   * 
   * @param osmWayIds to mark as excluded
   */
  public void excludeOsmWaysFromParsing(List<Number> osmWayIds) {
    if(osmWayIds==null) {
      LOGGER.warning(String.format("OSM way ids are null, ignored excluding them"));
      return;
    }    
    osmWayIds.forEach(osmWayId -> excludeOsmWayFromParsing(osmWayId.longValue()));
  }
  

  /** Verify if provided way id is excluded or not
   * 
   * @param osmWayId to verify (int or long)
   * @return true if excluded, false otherwise
   */
  public boolean isOsmWayExcluded(Number osmWayId) {
    return excludedOsmWays.contains(osmWayId.longValue());
  }
  
  /** set the mode access for the given osm way id
   * 
   * @param osmWayId this mode access will be applied on (int or long)
   * @param allowedOsmModes to set as the only modes allowed
   */
  public void overwriteModeAccessByOsmWayId(Number osmWayId, String...allowedOsmModes) {
    overwriteModeAccessByOsmWayId(osmWayId, Arrays.asList(allowedOsmModes));    
  }  
  
  /** set the mode access for the given osm way id
   * 
   * @param osmWayId this mode access will be applied on (int or long)
   * @param allowedOsmModes to set as the only modes allowed
   */
  public void overwriteModeAccessByOsmWayId(Number osmWayId, List<String> allowedOsmModes) {
    this.overwriteOsmWayModeAccess.put(osmWayId.longValue(), Set.copyOf(allowedOsmModes));
  }   
  
  /**
   * check if defaults should be overwritten
   * 
   * @param osmWayId to check (int or long)
   * @return true when alternative mode access is provided, false otherwise
   */
  public boolean isModeAccessOverwrittenByOsmWayId(Number osmWayId) {
    return overwriteOsmWayModeAccess.containsKey(osmWayId.longValue());
  }

  /**
   * collect the overwrite type values that should be used
   * 
   * @param osmWayId to collect overwrite values for (int or long)
   * @return the osm modes with allowed access
   */
  public final Set<String> getModeAccessOverwrittenByOsmWayId(Number osmWayId) {
    return overwriteOsmWayModeAccess.get(osmWayId.longValue());
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
   * @return railway settings
   */
  public OsmRailwaySettings getRailwaySettings() {
    return osmRailwaySettings;
  }
  
  /** provide highway specific settings
   * @return highway settings
   */
  public OsmHighwaySettings getHighwaySettings() {
    return osmHighwaySettings;
  }

  /** provide waterway specific settings
   * @return waterway settings
   */
  public OsmWaterwaySettings getWaterwaySettings() {
    return osmWaterwaySettings;
  }

  /** When a bounding polygon is set, some ways might partially be in and/or outside this bounding box. For such OSM ways
   * the complete geometry is available, but this is not known to the parser since it only considers nodes within the bounding box (from which
   * the OSM ways are constructed). Hence without explicitly stating this OSM way needs to be preserved in full it is truncated for the portions
   * outside the bounding box. This method allows the user to explicitly state the full geometry needs to be retained.
   * 
   * @param osmWays to keep geometry even if it falls (partially) outside the bounding polygon (int or long)
   */
  public void setKeepOsmWaysOutsideBoundingPolygon(Number... osmWays) {
    setKeepOsmWaysOutsideBoundingPolygon(Arrays.asList(osmWays));
  }
  
  /** When a bounding polygon is set, some ways might partially be in and/or outside this bounding box. For such OSM ways
   * the complete geometry is available, but this is not known to the parser since it only considers nodes within the bounding box (from which
   * the OSM ways are constructed). Hence without explicitly stating this OSM way needs to be preserved in full it is truncated for the portions
   * outside the bounding box. This method allows the user to explicitly state the full geometry needs to be retained.
   * 
   * @param osmWays to keep geometry even if it falls (partially) outside the bounding polygon (int or long)
   */  
  public void setKeepOsmWaysOutsideBoundingPolygon(List<Number> osmWays) {
    includedOutsideBoundingPolygonOsmWays.addAll(osmWays.stream().map(Number::longValue).collect(Collectors.toList()));
  }  
  
  /** check if any OSM ways are marked for keeping outside bounding polygon
   * 
   * @return true when present, false otherwise
   */  
  public boolean hasKeepOsmWaysOutsideBoundingPolygon() {
    return includedOutsideBoundingPolygonOsmWays!=null && !includedOutsideBoundingPolygonOsmWays.isEmpty();
  }   
  
  /** check if OSM way is marked for keeping outside bounding polygon
   * 
   * @param osmWayId to verify (int or long)
   * @return true when present, false otherwise
   */  
  public boolean isKeepOsmWayOutsideBoundingPolygon(Number osmWayId) {
    return includedOutsideBoundingPolygonOsmWays.contains(osmWayId.longValue());
  }
  
  /** When a bounding polygon is set, some nodes might reside outside this bounding box but you want to make them available anyway for some reason. 
   * For such OSM nodes this method allows the user to explicitly include the OSM node even if it falls outside the bounding polygon
   * 
   * @param osmNodeId to keep
   */
  public void setKeepOsmNodeOutsideBoundingPolygon(Number osmNodeId) {
    includedOutsideBoundingPolygonOsmNodes.add(osmNodeId.longValue());
  }
  
  /** count number of marked OSM nodes to keep
   * 
   * @return count
   */
  public int getNumberOfKeepOsmNodesOutsideBoundingPolygon() {
    return includedOutsideBoundingPolygonOsmNodes.size();
  }
  
  /** count number of marked OSM ways to keep
   * 
   * @return count
   */
  public long getNumberOfKeepOsmWaysOutsideBoundingPolygon() {
    return includedOutsideBoundingPolygonOsmWays.size();
  }    
  
  /** check if any OSM nodes are marked for keeping outside bounding polygon
   * 
   * @return true when present, false otherwise
   */  
  public boolean hasKeepOsmNodesOutsideBoundingPolygon() {
    return includedOutsideBoundingPolygonOsmNodes!=null && !includedOutsideBoundingPolygonOsmNodes.isEmpty();
  }   
  
  /** check if OSM node is marked for keeping outside bounding polygon
   * 
   * @param osmNodeId to verify (int or long)
   * @return true when present, false otherwise
   */  
  public boolean isKeepOsmNodeOutsideBoundingPolygon(Number osmNodeId) {
    return includedOutsideBoundingPolygonOsmNodes.contains(osmNodeId.longValue());
  }  

}

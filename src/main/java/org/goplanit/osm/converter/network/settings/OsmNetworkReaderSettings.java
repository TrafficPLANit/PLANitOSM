package org.goplanit.osm.converter.network.settings;

import org.goplanit.osm.converter.settings.OsmReaderSettings;
import org.goplanit.osm.converter.network.settings.override.OsmWayLaneOverride;
import org.goplanit.osm.defaults.*;
import org.goplanit.osm.tags.OsmAccessTags;
import org.goplanit.osm.util.OsmTagUtils;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.directed.Connectivity;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.locale.LocaleUtils;
import org.goplanit.utils.misc.CollectionUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.NoSettingsKey;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsTopLevelValue;
import org.goplanit.utils.settings.annotations.SettingsSection;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * All general settings (and sub-settings classes) of the OSM reader for parsing network infrastructure, including the
 * highway settings and railway settings members. The keys a settings file can set are on
 * {@link OsmNetworkReaderYamlKeyedSettings}.
 * <p>
 * OSM holds far more than a transport network needs, tagged unevenly from place to place. These settings say what to
 * keep and how to read it: which ways and modes to parse, what to assume where tags are missing (speed limits, lanes
 * and access by country), how to fix single ways the map has wrong, and what to remove after parsing, such as the
 * dangling subnetworks a mode cannot leave
 * </p>
 *
 * @author markr
 *
 */
@SettingsSection(key = "network", level = SettingsKeyLevel.STANDARD, description =
    "The settings of parsing the network: which ways and modes, defaults where tags are missing, and corrections")
public class OsmNetworkReaderSettings extends OsmNetworkReaderYamlKeyedSettings {

  /**
   * The logger
   */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderSettings.class.getCanonicalName());

  /** compiled positive access value tags, defaults combined with user overrides, see
   * {@link #compileAccessValueTagsWhenClassificationChanged()} */
  private String[] compiledPositiveAccessValueTags = null;

  /** compiled negative access value tags, defaults combined with user overrides, see
   * {@link #compileAccessValueTagsWhenClassificationChanged()} */
  private String[] compiledNegativeAccessValueTags = null;

  /** the user classification the compiled access value tags were made from, null when not compiled yet */
  private Map<String, AccessValueClassification> compiledAccessValueClassification = null;

  /**
   * Compile the effective access value classification by starting from the defaults and applying the user's
   * overrides on top. Cached because it is consulted for every parsed OSM way carrying an access tag.
   *
   * @param classification to compile the value tags for
   * @return effective access value tags for that classification
   */
  private String[] compileAccessValueTags(AccessValueClassification classification) {
    var defaults = classification == AccessValueClassification.POSITIVE ?
        OsmAccessTags.getDefaultPositiveAccessValueTags() : OsmAccessTags.getDefaultNegativeAccessValueTags();

    var effective = new TreeSet<>(Arrays.asList(defaults));
    for(var entry : getAccessValueClassification().entrySet()){
      /* a value classified the other way by the user must no longer appear here, hence the removal irrespective
       * of whether it is subsequently added */
      effective.remove(entry.getKey());
      if(entry.getValue() == classification){
        effective.add(entry.getKey());
      }
    }
    return effective.toArray(new String[0]);
  }

  /**
   * Compile the access value tags again when the user classification differs from the one they were made from. The
   * classification is set on {@link OsmNetworkReaderYamlKeyedSettings}, which cannot tell this class it changed, so
   * the compiled tags check it each time they are asked for, a comparison of a few entries at most
   */
  private void compileAccessValueTagsWhenClassificationChanged(){
    var classification = getAccessValueClassification();
    if(!classification.equals(compiledAccessValueClassification)){
      compiledAccessValueClassification = new TreeMap<>(classification);
      compiledPositiveAccessValueTags = compileAccessValueTags(AccessValueClassification.POSITIVE);
      compiledNegativeAccessValueTags = compileAccessValueTags(AccessValueClassification.NEGATIVE);
    }
  }

  /**
   * The settings of the first way family knowing an OSM way type, in the order highway, railway, waterway
   *
   * @param osmWayType the type, such as primary
   * @return the settings of its family
   * @throws IllegalArgumentException naming the type, when no family knows it
   */
  private OsmWaySettings findOsmWaySettingsKnowing(String osmWayType) {
    return Stream.of(getHighwaySettings(), getRailwaySettings(), getWaterwaySettings())
        .filter(waySettings -> waySettings.isKnownOsmWayType(osmWayType)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            String.format("Not a known OSM highway, railway or waterway type: %s", osmWayType)));
  }

  /* SETTINGS */

  /** the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;

  /** the default crs is set to {@code  PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS} */
  public static CoordinateReferenceSystem DEFAULT_SOURCE_CRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;

  /**
   * Default constructor. Here no specific locale is provided, meaning that all defaults will use global settings.
   * This is especially relevant for speed limits and mode access restrictions (unless manually adjusted by the user)
   *
   */
  public OsmNetworkReaderSettings() {
    this(CountryNames.GLOBAL);
  }

  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on, 
   * for various OSM highway types in case maximum speed limit information is missing
   * 
   * @param countryName the full country name to use speed limit data for, see also the
   *                    OsmSpeedLimitDefaultsByCountry class
   */
  public OsmNetworkReaderSettings(
      @SettingsTopLevelValue(key = COUNTRY_KEY, description = COUNTRY_DESCRIPTION) String countryName) {
    this((URL) null, countryName);
  }

  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on,
   * for various OSM highway types in case maximum speed limit information is missing
   *
   * @param inputSource to use, expected local file location
   * @param countryName the full country name to use speed limit data for, see also
   *                    the OsmSpeedLimitDefaultsByCountry class
   */
  public OsmNetworkReaderSettings(String inputSource, String countryName) {
    this(UrlUtils.createFromLocalAbsoluteOrRelativePath(Path.of(inputSource)), countryName);
  }

  /**
   * Constructor with country to base (i) default speed limits and (ii) mode access on,
   * for various OSM highway types in case maximum speed limit information is missing
   * <p>
   * A country that is not a known country name, such as a misspelt one, is warned about, with the name given.
   * </p>
   *
   * @param inputSource to use
   * @param countryName the full country name to use speed limit data for, see also
   *                    the OsmSpeedLimitDefaultsByCountry class
   */
  public OsmNetworkReaderSettings(URL inputSource, String countryName) {
    super(inputSource, countryName);
    if (!CountryNames.GLOBAL.equals(countryName) && LocaleUtils.getIso2CountryCodeByName(countryName) == null) {
      LOGGER.warning(String.format("Country '%s' is not a known country name, check its spelling", countryName));
    }
  }
        
  /**
   * Put back the source coordinate reference system, besides every key the YAML keyed settings put back. The
   * compiled access value tags follow the classification put back when next asked for
   */
  @Override
  public void reset() {
    super.reset();
    this.sourceCRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("OSM Network Reader Settings"));
    super.logSettings(level);
    /* the effective classification is logged in full, defaults and user overrides combined, so that what is
     * reported is what parsing applies rather than requiring the reader to merge the two in their head */
    LOGGER.info(LoggingUtils.settingsValue("Access value tags granting access",
        String.join(", ", getPositiveAccessValueTags()), level));
    LOGGER.info(LoggingUtils.settingsValue("Access value tags denying access",
        String.join(", ", getNegativeAccessValueTags()), level));
    LOGGER.info(LoggingUtils.settingsValue("Source CRS", getSourceCRS().getName(), level));
    logNestedSettings(level);
  }

  /** activate the parsing of railways
   * @param activate when true activate railway parsing, when false deactivate
   * @return railway settings that are activated, null when deactivated
   */
  @CoveredBySettingsKey("railway.active")
  public OsmRailwaySettings activateRailwayParser(boolean activate) {
    getRailwaySettings().activateParser(activate);
    return getRailwaySettings();
  }

  /** activate the parsing of waterways
   * @param activate when true activate waterway parsing, when false deactivate
   * @return waterway settings that are activated, null when deactivated
   */
  @CoveredBySettingsKey("waterway.active")
  public OsmWaterwaySettings activateWaterwayParser(boolean activate) {
    getWaterwaySettings().activateParser(activate);
    return getWaterwaySettings();
  }
  
  /** activate the parsing of highways
   * @param activate when true activate highway parsing, when false deactivate
   * @return highway settings that are activated, null when deactivated
   */
  @CoveredBySettingsKey("highway.active")
  public OsmHighwaySettings activateHighwayParser(boolean activate) {
    getHighwaySettings().activateParser(activate);
    return getHighwaySettings();
  }

  /** activate the parsing of intersections from traffic signals
   * @param activate when true activate intersection parsing, when false deactivate
   * @return intersection settings, activated or deactivated
   */
  @CoveredBySettingsKey("intersections.active")
  public OsmIntersectionSettings activateIntersectionParser(boolean activate) {
    getIntersectionSettings().activateParser(activate);
    return getIntersectionSettings();
  }
  
  /** Verify if railway parser is active
   * 
   * @return true when active false otherwise
   */
  public boolean isRailwayParserActive() {
    return getRailwaySettings().isParserActive();
  }
  
  /** Verify if railway parser is active
   * 
   * @return true when active false otherwise
   */
  public boolean isHighwayParserActive() {
    return getHighwaySettings().isParserActive();
  }

  /** Verify if waterway parser is active
   *
   * @return true when active false otherwise
   */
  public boolean isWaterwayParserActive() {
    return getWaterwaySettings().isParserActive();
  }

  /** Verify if intersection parser is active
   *
   * @return true when active false otherwise
   */
  public boolean isIntersectionParserActive() {
    return getIntersectionSettings().isParserActive();
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
  @NoSettingsKey(reason = "OSM coordinates are always WGS84")
  public void setSourceCRS(final CoordinateReferenceSystem sourceCRS) {
    this.sourceCRS = sourceCRS;
  }

  /** Classify an OSM access value tag as granting access, see
   * {@link #classifyAccessValueTag(String, AccessValueClassification)}
   *
   * @param osmAccessValueTag to treat as granting access
   */
  @CoveredBySettingsKey("accessValueClassification")
  public void classifyAccessValueTagAsPositive(final String osmAccessValueTag) {
    classifyAccessValueTag(osmAccessValueTag, AccessValueClassification.POSITIVE);
  }

  /** Classify an OSM access value tag as denying access, see
   * {@link #classifyAccessValueTag(String, AccessValueClassification)}
   *
   * @param osmAccessValueTag to treat as denying access
   */
  @CoveredBySettingsKey("accessValueClassification")
  public void classifyAccessValueTagAsNegative(final String osmAccessValueTag) {
    classifyAccessValueTag(osmAccessValueTag, AccessValueClassification.NEGATIVE);
  }

  /** The access value tags that grant access during parsing, defaults combined with any user classification.
   * This is what parsing applies, as opposed to the defaults on {@code OsmAccessTags}
   *
   * @return effective positive access value tags
   */
  public String[] getPositiveAccessValueTags() {
    compileAccessValueTagsWhenClassificationChanged();
    return compiledPositiveAccessValueTags;
  }

  /** The access value tags that deny access during parsing, defaults combined with any user classification.
   * This is what parsing applies, as opposed to the defaults on {@code OsmAccessTags}
   *
   * @return effective negative access value tags
   */
  public String[] getNegativeAccessValueTags() {
    compileAccessValueTagsWhenClassificationChanged();
    return compiledNegativeAccessValueTags;
  }

  /**
   * Activate dealing with dangling subnetworks for a mode using the safest configuration, being a single
   * subnetwork the mode can route across in full, see {@link #DEFAULT_MINIMUM_SUBNETWORK_SIZE}
   *
   * @param modeType to activate for
   */
  @CoveredBySettingsKey("removeDanglingSubnetworks")
  public void activateRemoveDanglingSubnetworks(PredefinedModeType modeType) {
    activateRemoveDanglingSubnetworks(
        modeType, DEFAULT_MINIMUM_SUBNETWORK_SIZE, Integer.MAX_VALUE, DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY);
  }

  /**
   * Activate dealing with dangling subnetworks for a mode with an explicit configuration, keeping the other modes,
   * see {@link #setRemoveDanglingSubnetworks(Map)}
   *
   * @param modeType to activate for
   * @param belowSize withdraw the mode's access on subnetworks smaller than this, in vertices
   * @param aboveSize withdraw the mode's access on subnetworks larger than this, typically the maximum value
   * @param connectivity what constitutes a subnetwork of this mode
   */
  @CoveredBySettingsKey("removeDanglingSubnetworks")
  public void activateRemoveDanglingSubnetworks(
      PredefinedModeType modeType, int belowSize, int aboveSize, Connectivity connectivity) {
    var configByMode = new HashMap<>(getRemoveDanglingSubnetworkModes());
    configByMode.put(modeType, new DanglingSubnetworkConfig(belowSize, aboveSize, connectivity));
    setRemoveDanglingSubnetworks(configByMode);
  }

  /**
   * Deactivate dealing with dangling subnetworks for a single mode, leaving any other mode untouched
   *
   * @param modeType to deactivate for
   */
  @CoveredBySettingsKey("removeDanglingSubnetworks")
  public void deactivateRemoveDanglingSubnetworks(PredefinedModeType modeType) {
    var configByMode = new HashMap<>(getRemoveDanglingSubnetworkModes());
    configByMode.remove(modeType);
    setRemoveDanglingSubnetworks(configByMode);
  }

  /**
   * Deactivate dealing with dangling subnetworks for every mode.
   * <p>
   * There is no counterpart activating every mode at once. It would withdraw access from ferry and rail networks,
   * which are normally in disconnected pieces, and from bicycle networks, which are routinely fragmented. Each mode
   * has to be asked for.
   * </p>
   */
  @CoveredBySettingsKey("removeDanglingSubnetworks")
  public void deactivateRemoveDanglingSubnetworks() {
    setRemoveDanglingSubnetworks(Map.of());
  }

  /** Verify if dangling subnetworks are dealt with for at least one mode
   *
   * @return true when any mode is configured, false otherwise
   */
  public boolean isRemoveDanglingSubnetworks() {
    return !getRemoveDanglingSubnetworkModes().isEmpty();
  }

  /** Verify if dangling subnetworks are dealt with for the given mode
   *
   * @param modeType to verify
   * @return true when configured, false otherwise
   */
  public boolean isRemoveDanglingSubnetworks(PredefinedModeType modeType) {
    return getRemoveDanglingSubnetworkModes().containsKey(modeType);
  }

  /** Collect the configuration for dealing with a mode's dangling subnetworks
   *
   * @param modeType to collect for
   * @return configuration, null when not activated for this mode
   */
  public DanglingSubnetworkConfig getRemoveDanglingSubnetworksConfig(PredefinedModeType modeType) {
    return getRemoveDanglingSubnetworkModes().get(modeType);
  }

  /** Collect the default number of lanes/tracks in each direction of a way for its OSM way key,
   * e.g. highway=value, railway=value based on the defaults provided, see
   * {@link OsmLaneDefaults#getDefaultLanesPerDirection(String, Map)}
   *
   * @param osmWayKey way key to collect default lanes for
   * @param tags of the way, holding the value of the way key
   * @return number of default lanes in each direction
   */
  public Integer getDefaultLanesPerDirection(String osmWayKey, Map<String, String> tags) {
    return getLaneDefaults().getDefaultLanesPerDirection(osmWayKey, tags);
  }
  
  /** Collect the mapped OSM modes based on the provided PLANit mode
   * 
   * @param planitModeType to get mapped PLANit modes for
   * @return mapped OSM modes, empty if no matches
   */  
  public Set<String> getMappedOsmModes(PredefinedModeType planitModeType) {
    var theModes  = getHighwaySettings().getMappedOsmRoadModes(planitModeType);
    var theOsmRailModes = getRailwaySettings().getMappedOsmRailModes(planitModeType);
    theModes.addAll(theOsmRailModes);
    var theOsmWaterModes = getWaterwaySettings().getMappedOsmWaterModes(planitModeType);
    theModes.addAll(theOsmWaterModes);
    return theModes;
  }  
  
  /** Collect the mapped OSM modes based on the provided PLANit mode types (if any)
   * 
   * @param planitModeTypes to get mapped OSM modes for
   * @return mapped OSM modes, empty if no matches
   */
  public Set<String> getMappedOsmModes(Collection<PredefinedModeType> planitModeTypes) {
    Set<String> mappedOsmModes = new TreeSet<>();
    
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
    var theMode = getHighwaySettings().getMappedPlanitRoadMode(osmMode);
    if(theMode != null) {
      return theMode;
    }
    theMode = getRailwaySettings().getMappedPlanitRailMode(osmMode);
    if(theMode != null) {
      return theMode;
    }

    return getWaterwaySettings().getMappedPlanitWaterMode(osmMode);
  } 
  
  /** convenience method that collects the currently mapped PLANit modes (road or rail) for the given OSM modes
   * 
   * @param osmModes to collect mapped mode for (if any)
   * @return mapped PLANit modes, if not available empty set is returned
   */
  public SortedSet<PredefinedModeType> getActivatedPlanitModeTypes(final Collection<String> osmModes) {
    TreeSet<PredefinedModeType> mappedPlanitModes = new TreeSet<>();
    if(CollectionUtils.nullOrEmpty(osmModes)) {
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
   * Convenience method that collects all currently mapped PLANit mode types (road, rail, water)
   *
   * @return mapped PLANit mode types, if not available empty set is returned
   */
  public SortedSet<PredefinedModeType> getActivatedPlanitModeTypes() {
    Stream<PredefinedModeType> highWayModes =
        isHighwayParserActive() ? getHighwaySettings().getActivatedPlanitModeTypesStream() : Stream.empty();

    Stream<PredefinedModeType> railwayModes =
        isRailwayParserActive() ? getRailwaySettings().getActivatedPlanitModeTypesStream() : Stream.empty();

    Stream<PredefinedModeType> waterwayModes =
        isWaterwayParserActive() ? getWaterwaySettings().getActivatedPlanitModeTypesStream() : Stream.empty();

    return Stream.concat(Stream.concat(highWayModes, railwayModes), waterwayModes).collect(
        Collectors.toCollection(TreeSet::new));
  }

  /**
   * Check if any walk or cycle modes are activated
   *
   * @return true when active false otherwise
   */
  public boolean isAnyActivatedPlanitModeActiveMode() {
    return (isHighwayParserActive() ? getHighwaySettings().getActivatedPlanitModeTypesStream() :
            Stream.<PredefinedModeType>empty()).anyMatch(
            m -> m.equals(PredefinedModeType.BICYCLE) || m.equals(PredefinedModeType.PEDESTRIAN));
  }
    
  /** Verify if the passed in osmMode is mapped (either to road or rail mode type), i.e.,
   * if it is actively included when reading the network
   * @param osmMode to verify
   * @return true if mapped, false otherwise
   */
  public boolean hasMappedPlanitModeType(final String osmMode) {
    var mappedMode = getHighwaySettings().getMappedPlanitRoadMode(osmMode);
    if(mappedMode == null) {
      mappedMode = getRailwaySettings().getMappedPlanitRailMode(osmMode);
    }
    if(mappedMode == null) {
      mappedMode = getWaterwaySettings().getMappedPlanitWaterMode(osmMode);
    }
    return mappedMode != null;
  }
  
  /** Verify if any of the passed in osmModes are mapped, i.e., if it is actively included when reading the network
   * @param osmModes to verify
   * @return true if any is mapped, false otherwise
   */  
  public boolean hasAnyMappedPlanitModeType(final String... osmModes) {
    for (String osmMode : osmModes) {
      if (hasMappedPlanitModeType(osmMode)) {
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
    if(osmModes==null) {
      return false;
    }

    for(String osmMode : osmModes) {
      if(hasMappedPlanitModeType(osmMode)) {
        return true;
      }
    }
    return false;
  }  


  /**
   * deactivate all types of highways, railways and waterways
   */
  @CoveredBySettingsKey({"highway.types", "railway.types", "waterway.types"})
  public void deactivateAllOsmWayTypes() {
    getHighwaySettings().deactivateAllOsmHighwayTypes();
    getRailwaySettings().deactivateAllOsmRailwayTypes();
    getWaterwaySettings().deactivateAllOsmWaterwayTypes();
  }

  /** deactivate all osm way types except the ones indicated, which are activated if they are not already. This
   * deactivates all types across both railways and highways. To do this within highways only, use the same method
   * under highway settings.
   * 
   * @param osmWaytypes to mark as activated
   */
  @CoveredBySettingsKey({"highway.types", "railway.types", "waterway.types"})
  public void deactivateAllOsmWayTypesExcept(String... osmWaytypes) {
    deactivateAllOsmWayTypesExcept(Arrays.asList(osmWaytypes));
  }
  
  /** deactivate all osm way types except the ones indicated, which are activated if they are not already. This
   * deactivates all types across railways, waterways, and highways. To do this within highways only, use the similar
   * method under highway/railway/waterway settings.
   * 
   * @param osmWayTypes to mark as activated, each a known type of one family, the first family knowing it in the
   *     order highway, railway, waterway
   * @throws IllegalArgumentException naming a type no family knows, the types left as they were
   */
  @CoveredBySettingsKey({"highway.types", "railway.types", "waterway.types"})
  public void deactivateAllOsmWayTypesExcept(List<String> osmWayTypes) {
    var waySettingsByType = new LinkedHashMap<String, OsmWaySettings>();
    osmWayTypes.forEach(osmWayType -> waySettingsByType.put(osmWayType, findOsmWaySettingsKnowing(osmWayType)));
    deactivateAllOsmWayTypes();
    waySettingsByType.forEach((osmWayType, waySettings) -> waySettings.activateOsmWayType(osmWayType));
  }

  /** Same as {@link #registerNewOsmWayType(String, NewOsmWayTypeDefaults)}, with the type as its OSM key and value
   *
   * @param osmWayKey to use
   * @param osmWayTypeValue to use
   * @param numLanes default to use, for a highway the total lanes of a way, see {@link OsmLaneDefaults}, for a railway
   *                 or waterway per direction
   * @param speedLimitKmh default to use
   * @param capacityPerLanePcuH default to use
   * @param maxDensityPerLanePcuKm max density to apply
   * @param allowedOsmModes default allowed mode(s) to use
   */
  @CoveredBySettingsKey("newOsmWayTypes")
  public void registerNewOsmWayType(
          String osmWayKey,
          String osmWayTypeValue,
          int numLanes,
          double speedLimitKmh,
          double capacityPerLanePcuH,
          double maxDensityPerLanePcuKm,
          String... allowedOsmModes) {
    registerNewOsmWayType(OsmTagUtils.toConcatEqualsString(osmWayKey, osmWayTypeValue), new NewOsmWayTypeDefaults(
        numLanes, speedLimitKmh, capacityPerLanePcuH, maxDensityPerLanePcuKm,
        Set.copyOf(Arrays.asList(allowedOsmModes))));
  }
  
  /**
   * Same as {@link #excludeOsmWayFromParsing(UserOverride)}, without a reason
   *
   * @param osmWayId to exclude (int or long)
   */
  @CoveredBySettingsKey("osmWaysExcludedFromParsing")
  public void excludeOsmWayFromParsing(Number osmWayId) {
    excludeOsmWayFromParsing(UserOverride.of(osmWayId.longValue()));
  }

  /**
   * exclude specific OSM ways from being parsed based on their id
   *
   * @param osmWayIds to mark as excluded (int or long)
   */
  @CoveredBySettingsKey("osmWaysExcludedFromParsing")
  public void excludeOsmWaysFromParsing(Number... osmWayIds) {
    excludeOsmWaysFromParsing(Arrays.asList(osmWayIds));
  }  

  /**
   * exclude specific OSM ways from being parsed based on their id. It is expected that the
   * way ids are either an integer or long
   * 
   * @param osmWayIds to mark as excluded
   */
  @CoveredBySettingsKey("osmWaysExcludedFromParsing")
  public void excludeOsmWaysFromParsing(List<Number> osmWayIds) {
    if(osmWayIds==null) {
      LOGGER.warning("OSM way ids are null, ignored excluding them");
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
    return getOsmWaysExcludedFromParsing().containsKey(osmWayId.longValue());
  }

  /**
   * Same as {@link #overrideOsmWayModeAccess(long, UserOverride)}, without a reason
   *
   * @param osmWayId the OSM way id (int or long)
   * @param allowedOsmModes the only OSM modes allowed on it
   */
  @CoveredBySettingsKey("osmWayModeAccessOverrides")
  public void overrideOsmWayModeAccess(Number osmWayId, String... allowedOsmModes) {
    overrideOsmWayModeAccess(osmWayId, Arrays.asList(allowedOsmModes));
  }

  /**
   * Same as {@link #overrideOsmWayModeAccess(long, UserOverride)}, without a reason
   *
   * @param osmWayId the OSM way id (int or long)
   * @param allowedOsmModes the only OSM modes allowed on it
   */
  @CoveredBySettingsKey("osmWayModeAccessOverrides")
  public void overrideOsmWayModeAccess(Number osmWayId, List<String> allowedOsmModes) {
    overrideOsmWayModeAccess(osmWayId.longValue(), UserOverride.of(Set.copyOf(allowedOsmModes)));
  }

  /**
   * The OSM modes overridden as the only ones allowed on an OSM way, see
   * {@link #overrideOsmWayModeAccess(long, UserOverride)}
   *
   * @param osmWayId the OSM way id (int or long)
   * @return the OSM modes allowed, without the reason, null when not overridden
   */
  public Set<String> getOsmWayModeAccessOverride(Number osmWayId) {
    return UserOverride.getValueOrNull(getOsmWayModeAccessOverrides().get(osmWayId.longValue()));
  }

  /**
   * The lanes overridden for an OSM way, see {@link #overrideOsmWayLanes(long, UserOverride)}
   *
   * @param osmWayId the OSM way id
   * @return the lanes overridden, without the reason, null when not overridden
   */
  public OsmWayLaneOverride getOsmWayLaneOverride(long osmWayId) {
    return UserOverride.getValueOrNull(getOsmWayLaneOverrides().get(osmWayId));
  }

  /**
   * Log all de-activated OSM way types
   */
  @NoSettingsKey(reason = "logs the OSM way types not parsed")
  public void logUnsupportedOsmWayTypes() {
    if(isHighwayParserActive()) {
      getHighwaySettings().logUnsupportedOsmHighwayTypes();
    }
    if(isRailwayParserActive()) {
      getRailwaySettings().logUnsupportedOsmRailwayTypes();
    }
    if(isWaterwayParserActive()) {
      getWaterwaySettings().logUnsupportedOsmWayTypes();
    }
  }

  /** Same as {@link #setKeepOsmWayOutsideBoundingPolygon(UserOverride)} for each OSM way, without a reason
   *
   * @param osmWays to keep whole (int or long)
   */
  @CoveredBySettingsKey("osmWaysKeptOutsideBoundingPolygon")
  public void setKeepOsmWaysOutsideBoundingPolygon(Number... osmWays) {
    setKeepOsmWaysOutsideBoundingPolygon(Arrays.asList(osmWays));
  }

  /** Same as {@link #setKeepOsmWayOutsideBoundingPolygon(UserOverride)} for each OSM way, without a reason
   *
   * @param osmWays to keep whole (int or long)
   */
  @CoveredBySettingsKey("osmWaysKeptOutsideBoundingPolygon")
  public void setKeepOsmWaysOutsideBoundingPolygon(List<Number> osmWays) {
    osmWays.forEach(osmWayId -> setKeepOsmWayOutsideBoundingPolygon(UserOverride.of(osmWayId.longValue())));
  }
  
  /** check if any OSM ways are marked for keeping outside bounding polygon
   * 
   * @return true when present, false otherwise
   */  
  public boolean hasKeepOsmWaysOutsideBoundingPolygon() {
    return !getOsmWaysKeptOutsideBoundingPolygon().isEmpty();
  }   
  
  /** check if OSM way is marked for keeping outside bounding polygon
   * 
   * @param osmWayId to verify (int or long)
   * @return true when present, false otherwise
   */  
  public boolean isKeepOsmWayOutsideBoundingPolygon(Number osmWayId) {
    return getOsmWaysKeptOutsideBoundingPolygon().containsKey(osmWayId.longValue());
  }
  
  /** Same as {@link #setKeepOsmNodeOutsideBoundingPolygon(UserOverride)}, without a reason
   *
   * @param osmNodeId to keep (int or long)
   */
  @CoveredBySettingsKey("osmNodesKeptOutsideBoundingPolygon")
  public void setKeepOsmNodeOutsideBoundingPolygon(Number osmNodeId) {
    setKeepOsmNodeOutsideBoundingPolygon(UserOverride.of(osmNodeId.longValue()));
  }
  
  /** count number of marked OSM nodes to keep
   * 
   * @return count
   */
  public int getNumberOfKeepOsmNodesOutsideBoundingPolygon() {
    return getOsmNodesKeptOutsideBoundingPolygon().size();
  }
  
  /** count number of marked OSM ways to keep
   * 
   * @return count
   */
  public long getNumberOfKeepOsmWaysOutsideBoundingPolygon() {
    return getOsmWaysKeptOutsideBoundingPolygon().size();
  }    
  
  /** check if any OSM nodes are marked for keeping outside bounding polygon
   * 
   * @return true when present, false otherwise
   */  
  public boolean hasKeepOsmNodesOutsideBoundingPolygon() {
    return !getOsmNodesKeptOutsideBoundingPolygon().isEmpty();
  }   
  
  /** check if OSM node is marked for keeping outside bounding polygon
   * 
   * @param osmNodeId to verify (int or long)
   * @return true when present, false otherwise
   */  
  public boolean isKeepOsmNodeOutsideBoundingPolygon(Number osmNodeId) {
    return getOsmNodesKeptOutsideBoundingPolygon().containsKey(osmNodeId.longValue());
  }

}


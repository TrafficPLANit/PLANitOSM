package org.goplanit.osm.converter.network;

import org.goplanit.osm.converter.OsmReaderSettings;
import org.goplanit.osm.defaults.*;
import org.goplanit.osm.tags.OsmAccessTags;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.utils.geo.PlanitJtsCrsUtils;
import org.goplanit.utils.graph.directed.Connectivity;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.CollectionUtils;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.mode.TrackModeType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.goplanit.converter.utils.ProjectedBoundingAreaHelper.DEFAULT_MAX_FERRY_DISTANCE_OUTSIDE_BOUNDING_AREA_M;

/**
 * All general settings (and sub-settings classes) for the OSM reader pertaining to parsing  network infrastructure.
 * contains additional settings for highway and railway (e.g., highway settings and railway settings members,
 * respectively).
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

  /** the default speed limits used in case no explicit information is available on the OSM way's tags */
  protected final OsmSpeedLimitDefaults speedLimitConfiguration;
  
  /** the default mode access configuration used in case no explicit access information is available on the
   * OSM way's tags */
  protected final OsmModeAccessDefaults modeAccessConfiguration;  
  
  /** the default number of lanes used in case no explicit information is available on the OSM way's tags */
  protected final OsmLaneDefaults laneConfiguration = new OsmLaneDefaults();  
      
  /** allow users to provide OSM way ids for ways that we are not to parse, for example when we know the original
   * coding or tagging is problematic */
  protected final Set<Long>  excludedOsmWays = new HashSet<>();
  
  /** Allow users to provide OSM way ids for ways that we are to keep even if they fall (partially) outside a
   * bounding polygon, for example when we know the OSM way meanders in and outside the polygon, and we want to
   * have a connected network and proper lengths for this way */
  protected final Set<Long>  includedOutsideBoundingPolygonOsmWays = new HashSet<>();
  
  /** Allow users to provide OSM node ids for nodes that we are not to keep even if they fall outside
   * a bounding polygon */
  protected final Set<Long>  includedOutsideBoundingPolygonOsmNodes = new HashSet<>();
 
    
  /**
   * track overwritten mode access values for specific osm ways by osm id. Can be used in case the OSM file
   * is incorrectly tagged which causes problems in the memory model. Here one can be manually
   * overwrite the allowable modes for this particular way.
   */
  protected final Map<Long, Set<String>> overwriteOsmWayModeAccess = new HashMap<>();

  /**
   * How an OSM access value tag is to be interpreted during parsing
   */
  public enum AccessValueClassification {
    /** the value grants access */
    POSITIVE,
    /** the value denies access */
    NEGATIVE
  }

  /** user supplied classification of OSM access value tags, overriding the defaults. Kept as a single map keyed by
   * the access value so that a value can never end up classified both positively and negatively at once */
  protected final Map<String, AccessValueClassification> overriddenAccessValueTags = new TreeMap<>();

  /** compiled positive access value tags, defaults combined with user overrides. Null when it needs recompiling */
  private String[] compiledPositiveAccessValueTags = null;

  /** compiled negative access value tags, defaults combined with user overrides. Null when it needs recompiling */
  private String[] compiledNegativeAccessValueTags = null;

  /* SETTINGS */

  /** the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;

  private boolean consolidateLinkSegmentTypes = DEFAULT_CONSOLIDATE_LINK_SEGMENT_TYPES;

  /** flag per track type indicating if dangling subnetworks of that track type should be removed after parsing
   * the network. OSM networks often have small roads that appear to be connected to larger roads, but in fact are
   * not. All subnetworks that are not part of the largest subnetwork of their track type will be removed.
   * <p>
   * Pruning is per track type because road, rail and water infrastructure share a single PLANit layer while being
   * separate networks in practice. A rail line is not dangling merely because it is unreachable by car, so judging
   * connectivity across all track types at once would discard legitimate infrastructure.
   * </p>
   * */
  protected final Map<TrackModeType, Boolean> removeDanglingSubNetworkByTrackType =
      createDefaultRemoveDanglingSubNetworkFlags();
  
  /**
   * When dangling subnetworks are marked for removal, this threshold determines the minimum subnetwork size
   * for it NOT to be removed. In other words, all subnetworks below this number will be removed
   */
  protected int discardSubNetworkBelowSize = DEFAULT_MINIMUM_SUBNETWORK_SIZE;
  
  /**
   * When dangling subnetworks are marked for removal, this threshold determines the maximum subnetwork size for
   * it NOT to be removed. In other words, all subnetworks above this number will be removed, including the largest
   * one if it does not match the value
   */  
  protected int discardSubNetworkAboveSize = Integer.MAX_VALUE;
  
  /**
   * indicate whether to keep the largest subnetwork when {@code removeDanglingSubNetworks} even when it does
   * not adhere to the criteria of {@code discardSubNetworkBelowSize} and/or {@code discardSubNetworkAbovesize} 
   */
  protected boolean alwaysKeepLargestSubNetwork = DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK;

  /**
   * What counts as belonging to the same subnetwork when dangling subnetworks are removed, applied to every
   * activated track type alike
   */
  protected Connectivity danglingSubNetworkConnectivity = DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY;

  /** By default we allow ferry route OSM ways to be a fair way outside any bounding polygon and still be included.
   * We do so because often water bodies are not part of a zoning system and would therefore not include connecting
   * ferries. This is generally unwanted behaviour and therefore we automatically include all ferries within
   * the specified distance outside the bounding polygon and still be included. */
  private double maximumDistanceFerryOutsideBoundingPolygonInMeters =
      DEFAULT_MAX_FERRY_DISTANCE_OUTSIDE_BOUNDING_AREA_M;
      
  /** the default crs is set to {@code  PlanitJtsUtils.DEFAULT_GEOGRAPHIC_CRS} */
  public static CoordinateReferenceSystem DEFAULT_SOURCE_CRS = PlanitJtsCrsUtils.DEFAULT_GEOGRAPHIC_CRS;

  /** Default track types for which dangling subnetworks are removed after parsing: road only.
   * <p>
   * Rail and water networks are routinely disconnected from each other in an extract - a branch line leaving the
   * bounding area, a ferry route with no other ferry route to connect to - without that indicating a parsing
   * artefact. Road networks are where the spurious disconnected fragments typically occur, so that is the only
   * track type pruned unless the user opts in to more.
   * </p> */
  public static Set<TrackModeType> DEFAULT_REMOVE_DANGLING_SUBNETWORK_TRACK_TYPES = Set.of(TrackModeType.ROAD);
  
  /** Default minimum size of subnetwork for it not to be removed when dangling subnetworks are removed,
   * size indicates number of vertices: 20 */
  public static int DEFAULT_MINIMUM_SUBNETWORK_SIZE= 20;  
  
  /** by default we always keep the largest subnetwork */
  public static boolean DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK = true;

  /** Default notion of connectivity applied when removing dangling subnetworks: strong.
   * <p>
   * Weak connectivity asks only whether infrastructure is attached to the network, and so retains anything that
   * cannot be both entered and left, e.g. a car park served by a single one way road pointing outwards. Such
   * infrastructure cannot carry a route in either direction and is of no more use than a disconnected fragment,
   * which is why the stricter notion is the default.
   * </p>
   * <p>
   * Note that this does not by itself yield a network every part of which can serve as both origin and
   * destination. Being able to enter and leave is a per mode property, whereas pruning is per track type: a
   * pocket entered by a bus only segment and left by a car segment is strongly connected as road infrastructure
   * while remaining unusable for car. Measured on a metropolitan extract the stricter notion removes on the order
   * of a few hundred nodes, while an order of magnitude more remain unusable for an individual mode.
   * </p> */
  public static Connectivity DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY = Connectivity.STRONG;

  /** by default we always consolidate functionally equivalent OSM types into a single PLANit link segment type */
  public static boolean DEFAULT_CONSOLIDATE_LINK_SEGMENT_TYPES = true;

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
  public OsmNetworkReaderSettings(String countryName) {
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
   * 
   * @param inputSource to use
   * @param countryName the full country name to use speed limit data for, see also
   *                    the OsmSpeedLimitDefaultsByCountry class
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
  }   
        
  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    // TODO    
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("OSM Network Reader Settings"));
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue("Source CRS",
        getSourceCRS().getName(), level));
    LOGGER.info(LoggingUtils.settingsValue("Consolidate equivalent OSM link types",
        isConsolidateLinkSegmentTypes(), level));
    for(var trackType : TrackModeType.values()){
      LOGGER.info(LoggingUtils.settingsValue(
          String.format("Remove dangling subnetworks (%s)", trackType.name().toLowerCase()),
          isRemoveDanglingSubnetworks(trackType), level));
    }
    /* thresholds are logged unconditionally: they govern what a pruning pass does and are easily overlooked, and
     * the activation flags above can be temporarily off at the time of logging while pruning still takes place
     * later on, e.g. when an intermodal parse postpones removal until after the zoning is read */
    LOGGER.info(LoggingUtils.settingsValue("Discard dangling subnetworks below size",
        getDiscardDanglingNetworkBelowSize(), level));
    LOGGER.info(LoggingUtils.settingsValue("Discard dangling subnetworks above size",
        getDiscardDanglingNetworkAboveSize() != Integer.MAX_VALUE ?
            String.valueOf(getDiscardDanglingNetworkAboveSize()) : "infinite", level));
    LOGGER.info(LoggingUtils.settingsValue("Always keep largest subnetwork",
        isAlwaysKeepLargestSubnetwork(), level));
    /* logged next to the thresholds because it changes what they are applied to: under strong connectivity the
     * subnetworks identified are far more numerous and far smaller, so the same threshold prunes very differently */
    LOGGER.info(LoggingUtils.settingsValue("Dangling subnetwork connectivity",
        getDanglingSubnetworkConnectivity().name().toLowerCase(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum ferry distance outside bounding polygon (m)",
        getMaximumDistanceFerryOutsideBoundingPolygonInMeters(), level));

    /* the effective classification is logged in full, defaults and user overrides combined, so that what is
     * reported is what parsing applies rather than requiring the reader to merge the two in their head */
    LOGGER.info(LoggingUtils.settingsValue("Access value tags granting access",
        String.join(", ", getPositiveAccessValueTags()), level));
    LOGGER.info(LoggingUtils.settingsValue("Access value tags denying access",
        String.join(", ", getNegativeAccessValueTags()), level));
    if(!overriddenAccessValueTags.isEmpty()){
      LOGGER.info(LoggingUtils.settingsValue("Access value tags reclassified by user",
          overriddenAccessValueTags.entrySet().stream().map(
              e -> String.format("%s=%s", e.getKey(), e.getValue().name().toLowerCase())).collect(
                  Collectors.joining(", ")), level));
    }
    LOGGER.info(LoggingUtils.settingsSection("Highway", level + 1));
    getHighwaySettings().logSettings(level + 1);
    LOGGER.info(LoggingUtils.settingsSection("Railway", level + 1));
    getRailwaySettings().logSettings(level + 1);
    LOGGER.info(LoggingUtils.settingsSection("Waterway", level + 1));
    getWaterwaySettings().logSettings(level + 1);
  }

  /** activate the parsing of railways
   * @param activate when true activate railway parsing, when false deactivate
   * @return railway settings that are activated, null when deactivated
   */
  public OsmRailwaySettings activateRailwayParser(boolean activate) {
    osmRailwaySettings.activateParser(activate);
    return getRailwaySettings();      
  }

  /** activate the parsing of waterways
   * @param activate when true activate waterway parsing, when false deactivate
   * @return waterway settings that are activated, null when deactivated
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
   * explicitly exclude all osmWay types that are included but have no more activated modes due to
   * deactivation of their default assigned modes. Doing so avoids the reader to log warnings that supported
   * way types cannot be injected in the network because they have no viable modes attached
   * 
   * :TODO move somewhere else, not used from perspective of user
   */
  public void excludeOsmWayTypesWithoutActivatedModes() {
    osmHighwaySettings.excludeOsmWayTypesWithoutActivatedModes();
    osmRailwaySettings.excludeOsmWayTypesWithoutActivatedModes();
    osmWaterwaySettings.excludeOsmWayTypesWithoutActivatedModes();
  }
     
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
    for(var entry : overriddenAccessValueTags.entrySet()){
      /* a value classified the other way by the user must no longer appear here, hence the removal irrespective
       * of whether it is subsequently added */
      effective.remove(entry.getKey());
      if(entry.getValue() == classification){
        effective.add(entry.getKey());
      }
    }
    return effective.toArray(new String[0]);
  }

  /** discard the compiled access value classification so it is rebuilt on next use */
  private void invalidateCompiledAccessValueTags(){
    this.compiledPositiveAccessValueTags = null;
    this.compiledNegativeAccessValueTags = null;
  }

  /**
   * Classify an OSM access value tag, overriding how it would be treated by default. For example classifying
   * {@code private} as positive causes ways tagged {@code access=private} to retain their mode access rather than
   * having every mode stripped, which is often desirable since such ways are generally still traversable in
   * practice even though they are formally restricted.
   * <p>
   * A value can only carry one classification, so classifying it replaces any earlier choice for that value.
   * Values that are not recognised OSM access values are accepted, but a warning is logged since their effect
   * cannot be verified.
   * </p>
   *
   * @param osmAccessValueTag to classify, e.g. {@code private}
   * @param classification to apply
   */
  public void classifyAccessValueTag(final String osmAccessValueTag, final AccessValueClassification classification) {
    if(StringUtils.isNullOrBlank(osmAccessValueTag)){
      LOGGER.warning("IGNORE: null or blank OSM access value tag provided for classification");
      return;
    }
    if(!OsmAccessTags.getAllKnownAccessValueTags().contains(osmAccessValueTag)){
      LOGGER.warning(String.format(
          "OSM access value tag '%s' is not a recognised access value, classifying it as %s regardless, at the " +
              "user's own risk", osmAccessValueTag, classification));
    }
    overriddenAccessValueTags.put(osmAccessValueTag, classification);
    invalidateCompiledAccessValueTags();
  }

  /** Classify an OSM access value tag as granting access, see
   * {@link #classifyAccessValueTag(String, AccessValueClassification)}
   *
   * @param osmAccessValueTag to treat as granting access
   */
  public void classifyAccessValueTagAsPositive(final String osmAccessValueTag) {
    classifyAccessValueTag(osmAccessValueTag, AccessValueClassification.POSITIVE);
  }

  /** Classify an OSM access value tag as denying access, see
   * {@link #classifyAccessValueTag(String, AccessValueClassification)}
   *
   * @param osmAccessValueTag to treat as denying access
   */
  public void classifyAccessValueTagAsNegative(final String osmAccessValueTag) {
    classifyAccessValueTag(osmAccessValueTag, AccessValueClassification.NEGATIVE);
  }

  /** Remove any user classification for the given access value tag, restoring default behaviour for it
   *
   * @param osmAccessValueTag to restore to its default treatment
   */
  public void resetAccessValueTagClassification(final String osmAccessValueTag) {
    if(overriddenAccessValueTags.remove(osmAccessValueTag) != null){
      invalidateCompiledAccessValueTags();
    }
  }

  /** The access value tags that grant access during parsing, defaults combined with any user classification.
   * This is what parsing applies, as opposed to the defaults on {@code OsmAccessTags}
   *
   * @return effective positive access value tags
   */
  public String[] getPositiveAccessValueTags() {
    if(compiledPositiveAccessValueTags == null){
      compiledPositiveAccessValueTags = compileAccessValueTags(AccessValueClassification.POSITIVE);
    }
    return compiledPositiveAccessValueTags;
  }

  /** The access value tags that deny access during parsing, defaults combined with any user classification.
   * This is what parsing applies, as opposed to the defaults on {@code OsmAccessTags}
   *
   * @return effective negative access value tags
   */
  public String[] getNegativeAccessValueTags() {
    if(compiledNegativeAccessValueTags == null){
      compiledNegativeAccessValueTags = compileAccessValueTags(AccessValueClassification.NEGATIVE);
    }
    return compiledNegativeAccessValueTags;
  }

  /**
   * Construct the per track type defaults for removing dangling subnetworks
   *
   * @return flags for every track type, set according to {@code DEFAULT_REMOVE_DANGLING_SUBNETWORK_TRACK_TYPES}
   */
  private static Map<TrackModeType, Boolean> createDefaultRemoveDanglingSubNetworkFlags(){
    var flagsByTrackType = new EnumMap<TrackModeType, Boolean>(TrackModeType.class);
    for(var trackType : TrackModeType.values()){
      flagsByTrackType.put(trackType, DEFAULT_REMOVE_DANGLING_SUBNETWORK_TRACK_TYPES.contains(trackType));
    }
    return flagsByTrackType;
  }

  /**
   * Indicate whether to remove dangling subnetworks for a single track type. Each track type is pruned
   * independently, so deactivating road leaves any activated rail or water pruning untouched.
   *
   * @param trackType to configure
   * @param removeDanglingSubnetworks yes or no
   */
  public void setRemoveDanglingSubnetworks(TrackModeType trackType, boolean removeDanglingSubnetworks) {
    this.removeDanglingSubNetworkByTrackType.put(trackType, removeDanglingSubnetworks);
  }

  /** Verify if dangling subnetworks of the given track type are removed from the final network
   *
   * @param trackType to verify
   * @return flag if dangling networks of this track type are removed
   */
  public boolean isRemoveDanglingSubnetworks(TrackModeType trackType) {
    return this.removeDanglingSubNetworkByTrackType.getOrDefault(trackType, false);
  }

  /**
   * Set what counts as belonging to the same subnetwork when dangling subnetworks are removed, see
   * {@link #DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY}.
   * <p>
   * Applies to every activated track type alike rather than being configurable per track type. The pathology
   * strong connectivity addresses is one of one way roads, so it does not arise for rail or water, and a per
   * track type choice would double the configuration surface without a case that needs it.
   * </p>
   * <p>
   * Be aware that under {@link Connectivity#STRONG} the size thresholds lose most of their meaning, since the
   * overwhelming majority of the additional subnetworks it identifies consist of a single node and so fall below
   * any sensible threshold.
   * </p>
   *
   * @param connectivity to apply
   */
  public void setDanglingSubnetworkConnectivity(Connectivity connectivity) {
    this.danglingSubNetworkConnectivity = connectivity;
  }

  /**
   * Verify what counts as belonging to the same subnetwork when dangling subnetworks are removed
   *
   * @return connectivity applied
   */
  public Connectivity getDanglingSubnetworkConnectivity() {
    return this.danglingSubNetworkConnectivity;
  }

  /**
   * Deactivate the removal of dangling subnetworks for all track types.
   * <p>
   * There is deliberately no blanket counterpart that activates every track type at once. Water in particular
   * must be opted into explicitly: a ferry network legitimately consists of many small, mutually disconnected
   * crossings, so pruning all but the largest of them discards valid infrastructure rather than parsing artefacts.
   * </p>
   */
  public void deactivateRemoveDanglingSubnetworks() {
    for(var trackType : TrackModeType.values()){
      setRemoveDanglingSubnetworks(trackType, false);
    }
  }

  /** Verify if dangling subnetworks are removed from the final network for at least one track type
   *
   * @return true when any track type is configured for removal, false otherwise
   */
  public boolean isRemoveDanglingSubnetworks() {
    return this.removeDanglingSubNetworkByTrackType.values().stream().anyMatch(Boolean::booleanValue);
  }

  /** Collect the track types for which dangling subnetworks are to be removed
   *
   * @return activated track types, empty when none are activated
   */
  public Set<TrackModeType> getRemoveDanglingSubnetworkTrackTypes() {
    return this.removeDanglingSubNetworkByTrackType.entrySet().stream()
        .filter(Map.Entry::getValue).map(Map.Entry::getKey)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TrackModeType.class)));
  }

  /** Replace the track types for which dangling subnetworks are to be removed, any type not listed is deactivated
   *
   * @param trackTypes to activate removal for
   */
  public void setRemoveDanglingSubnetworkTrackTypes(Set<TrackModeType> trackTypes) {
    for(var trackType : TrackModeType.values()){
      setRemoveDanglingSubnetworks(trackType, trackTypes.contains(trackType));
    }
  }
       
  
  /** collect the current configuration setup for applying number of lanes in case the lanes tag is not
   * available on the parsed osmway
   * @return lane configuration containing all defaults for various osm highway types
   */
  public OsmLaneDefaults getLaneConfiguration() {
    return this.laneConfiguration;
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
  public Set<String> getMappedOsmModes(PredefinedModeType planitModeType) {
    var theModes  = osmHighwaySettings.getMappedOsmRoadModes(planitModeType);
    var theOsmRailModes = osmRailwaySettings.getMappedOsmRailModes(planitModeType);
    theModes.addAll(theOsmRailModes);
    var theOsmWaterModes = osmWaterwaySettings.getMappedOsmWaterModes(planitModeType);
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
    var theMode = osmHighwaySettings.getMappedPlanitRoadMode(osmMode);
    if(theMode != null) {
      return theMode;
    }
    theMode = osmRailwaySettings.getMappedPlanitRailMode(osmMode);
    if(theMode != null) {
      return theMode;
    }

    return osmWaterwaySettings.getMappedPlanitWaterMode(osmMode);
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
    var mappedMode = osmHighwaySettings.getMappedPlanitRoadMode(osmMode);
    if(mappedMode == null) {
      mappedMode = osmRailwaySettings.getMappedPlanitRailMode(osmMode);
    }
    if(mappedMode == null) {
      mappedMode = osmWaterwaySettings.getMappedPlanitWaterMode(osmMode);
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

  /** the minimum size an identified dangling network must have for it to NOT be removed when dangling
   * networks are removed
   * 
   * @param discardBelow this number of vertices
   */
  public void setDiscardDanglingNetworksBelow(int discardBelow) {
    this.discardSubNetworkBelowSize = discardBelow;
  }
  
  /** Allows you to set a maximum size for dangling subnetwork. Practically only useful for debugging purposes
   * 
   * @param discardAbove this number of vertices
   */
  public void setDiscardDanglingNetworksAbove(int discardAbove) {
    this.discardSubNetworkAboveSize = discardAbove;
  }  
  
  /** collect the size above which dangling networks are kept even if they are smaller than the largest
   * connected network
   * @return dangling network size
   */
  public Integer getDiscardDanglingNetworkBelowSize() {
    return discardSubNetworkBelowSize;
  }  
  
  /** collect the size below which networks are removed 
   * @return dangling network size
   */
  public Integer getDiscardDanglingNetworkAboveSize() {
    return discardSubNetworkAboveSize;
  }    
  
  /** Verify if the largest subnetwork is always kept when we are removing dangling subnetworks
   * 
   * @return true when kept false otherwise
   */
  public boolean isAlwaysKeepLargestSubnetwork() {
    return alwaysKeepLargestSubNetwork;
  }

  /** indicate to keep the largest subnetwork always even when removing dangling subnetworks and the largest one
   * does not fit the set criteria
   * 
   * @param alwaysKeepLargestSubnetwork when true we always keep it, otherwise not
   */
  public void setAlwaysKeepLargestSubnetwork(boolean alwaysKeepLargestSubnetwork) {
    this.alwaysKeepLargestSubNetwork = alwaysKeepLargestSubnetwork;
  }

  /** Get the maximum distance outside the bounding area PLANit will still include ferry routes
   *
   * @return distance set
   */
  public double getMaximumDistanceFerryOutsideBoundingPolygonInMeters() {
    return maximumDistanceFerryOutsideBoundingPolygonInMeters;
  }

  /** Set the maximum distance outside the bounding area PLANit will still include ferry routes
   *
   * @param distanceMeters distance to use
   */
  public void setMaximumDistanceFerryOutsideBoundingPolygonInMeters(double distanceMeters) {
    this.maximumDistanceFerryOutsideBoundingPolygonInMeters = distanceMeters;
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
   * across both railways and highways. If you want to do this within highways only, use the same method
   * under highway settings.
   * 
   * @param osmWaytypes to mark as activated
   */
  public void deactivateAllOsmWayTypesExcept(String... osmWaytypes) {
    deactivateAllOsmWayTypesExcept(Arrays.asList(osmWaytypes));
  }
  
  /** deactivate all osm way types except the ones indicated, meaning that if the ones passed in
   * are not already active, they will be marked as activate afterwards. Note that this deactivates all types
   * across railways, waterways, and highways. If you want to do this within highways only, use similar method
   * provided under highway/railway/waterway settings.
   * 
   * @param osmWayTypes to mark as activated
   */
  public void deactivateAllOsmWayTypesExcept(List<String> osmWayTypes) {
    deactivateAllOsmWayTypes();
    for(String osmWayType : osmWayTypes) {
      if(OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayType)) {
        osmHighwaySettings.activateOsmHighwayTypes(osmWayType);
      }else if(OsmRailwayTags.isRailBasedRailway(osmWayType)) {
        osmRailwaySettings.activateOsmRailwayType(osmWayType);
      }else if(OsmWaterwayTags.isWaterWayBasedValueTag(osmWayType)){
        osmWaterwaySettings.activateOsmWaterwayType(osmWayType);
      }
    }
  }

  /** register a custom new way key value tag with defaults to parse in addition to the default supported.configured
   * tags.
   *
   * @param osmWayKey to use
   * @param osmWayTypeValue to use
   * @param numLanes default to use
   * @param speedLimitKmh default to use
   * @param capacityPerLanePcuH default to use
   * @param maxDensityPerLanePcuH max density to apply
   * @param allowedOsmModes default allowed mode(s) to use
   */
  public void registerNewOsmWayType(
          String osmWayKey,
          String osmWayTypeValue,
          int numLanes,
          double speedLimitKmh,
          double capacityPerLanePcuH,
          double maxDensityPerLanePcuH,
          String... allowedOsmModes) {

    boolean success = true;
    OsmWaySettings settings = null;

    /* deal with LANE configuration */
    if(OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      settings = this.osmHighwaySettings;
      if(laneConfiguration.containsDefaultDirectionalLanes(osmWayKey,osmWayTypeValue)){
        LOGGER.warning(String.format("Not allowed to add an OSM type that already exists, ignored %s=%s",
            osmWayKey, osmWayTypeValue));
        return;
      }
      success = laneConfiguration.setDefaultDirectionalLanesByHighwayType(osmWayTypeValue, numLanes) == null;
    }else if(OsmRailwayTags.isRailwayKeyTag(osmWayKey)){
      settings = this.osmRailwaySettings;
      if(laneConfiguration.getDefaultDirectionalRailwayTracks() != numLanes){
        LOGGER.warning(String.format("Custom railway=%s number of tracks set to %d (instead of %d), " +
                "this is a global setting, overwrite globally to change it",
                osmWayTypeValue, laneConfiguration.getDefaultDirectionalRailwayTracks(), numLanes));
      }
    }else if(OsmWaterwayTags.isAnyWaterwayKeyTag(osmWayKey)) {
      settings = this.osmWaterwaySettings;
      if (laneConfiguration.getDefaultDirectionalWaterwayLanes() != numLanes) {
        LOGGER.warning(String.format("Custom waterway [%s=%s] waterway lanes set to %d (instead of %d)," +
                " this is a global setting, overwrite globally to change it",
                osmWayKey, osmWayTypeValue, laneConfiguration.getDefaultDirectionalRailwayTracks(), numLanes));
      }
    }else{
      LOGGER.warning(String.format("Custom way type for %s=%s is not allowed due to key not being a known" +
          " or supported OSM way type", osmWayKey, osmWayTypeValue));
      return;
    }

    if(!success){
      LOGGER.warning(String.format("Unable to register new OsmWay type %s=%s due to issue regarding lane " +
          "configuration", osmWayKey, osmWayTypeValue));
      return;
    }

    /* deal with remainder of WAY TYPE configuration */
    success = settings.registerNewSupportedOsmWayType(
            osmWayKey, osmWayTypeValue, speedLimitKmh, capacityPerLanePcuH, maxDensityPerLanePcuH, allowedOsmModes);
    if(!success){
      LOGGER.warning(String.format("Unable to register new OsmWaytype %s=%s due to issue regarding way " +
          "type configuration", osmWayKey, osmWayTypeValue));
    }
  }
  
  /**
   * exclude specific OSM ways from being parsed based on their id
   * 
   * @param osmWayId to mark as excluded (int or long)
   */
  public void excludeOsmWayFromParsing(Number osmWayId) {
    if(osmWayId.longValue() <= 0) {
      LOGGER.warning(String.format("invalid OSM way id (%d) provided to be excluded, ignored", osmWayId.longValue()));
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
    if(isHighwayParserActive()) {
      osmHighwaySettings.logUnsupportedOsmHighwayTypes();
    }
    if(isRailwayParserActive()) {
      osmRailwaySettings.logUnsupportedOsmRailwayTypes();
    }
    if(isWaterwayParserActive()) {
      osmWaterwaySettings.logUnsupportedOsmWayTypes();
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

  /** When a bounding polygon is set, some ways might partially be in and/or outside this bounding box. For such
   * OSM ways
   * the complete geometry is available, but this is not known to the parser since it only considers nodes within
   * the bounding box (from which
   * the OSM ways are constructed). Hence without explicitly stating this OSM way needs to be preserved in full it
   * is truncated for the portions
   * outside the bounding box. This method allows the user to explicitly state the full geometry needs to be retained.
   * 
   * @param osmWays to keep geometry even if it falls (partially) outside the bounding polygon (int or long)
   */
  public void setKeepOsmWaysOutsideBoundingPolygon(Number... osmWays) {
    setKeepOsmWaysOutsideBoundingPolygon(Arrays.asList(osmWays));
  }
  
  /** When a bounding polygon is set, some ways might partially be in and/or outside this bounding box. For such
   * OSM ways
   * the complete geometry is available, but this is not known to the parser since it only considers nodes within
   * the bounding box (from which
   * the OSM ways are constructed). Hence without explicitly stating this OSM way needs to be preserved in full it
   * is truncated for the portions
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
  
  /** When a bounding polygon is set, some nodes might reside outside this bounding box but you want to make
   * them available anyway for some reason.
   * For such OSM nodes this method allows the user to explicitly include the OSM node even if it falls outside
   * the bounding polygon
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

  /** Flag on whether to consolidate functionally equivalent link segment types for various OSM way types into a single
   * PLANit type
   *
   * @return flag set
   */
  public boolean isConsolidateLinkSegmentTypes() {
    return consolidateLinkSegmentTypes;
  }

  /** Flag on whether to consolidate functionally equivalent link segment types for various OSM way types into a single
   * PLANit type
   *
   * @param flag to set
   * */
  public void setConsolidateLinkSegmentTypes(boolean flag) {
    this.consolidateLinkSegmentTypes = flag;
  }

}


package org.goplanit.osm.converter.network.settings;

import static org.goplanit.converter.utils.ProjectedBoundingAreaHelper.DEFAULT_MAX_FERRY_DISTANCE_OUTSIDE_BOUNDING_AREA_M;

import java.net.URL;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.goplanit.osm.converter.network.settings.override.OsmWayLaneOverride;
import org.goplanit.osm.converter.settings.OsmReaderSettings;
import org.goplanit.osm.defaults.OsmLaneDefaults;
import org.goplanit.osm.defaults.OsmModeAccessDefaultsByCountry;
import org.goplanit.osm.defaults.OsmSpeedLimitDefaultsByCountry;
import org.goplanit.osm.tags.OsmAccessTags;
import org.goplanit.osm.tags.OsmHighwayTags;
import org.goplanit.osm.tags.OsmRailwayTags;
import org.goplanit.osm.tags.OsmWaterwayTags;
import org.goplanit.osm.util.PlanitOsmUtils;
import org.goplanit.utils.exceptions.ExceptionUtils;
import org.goplanit.utils.graph.directed.Connectivity;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.mode.PredefinedModeType;
import org.goplanit.utils.settings.UserOverride;
import org.goplanit.utils.settings.annotations.SettingsKeyEffect;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsSection;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The network reader settings a settings file can set: only the methods of its keys and sections, each write with the
 * read of the same key. {@link OsmNetworkReaderSettings} extends it with what code needs on top.
 * <p>
 * Keeping the keys in a class of their own shows in one place all a settings file can say in its network section,
 * while the methods made for code stay on the class code uses
 * </p>
 *
 * @author markr
 */
public abstract class OsmNetworkReaderYamlKeyedSettings extends OsmReaderSettings {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(OsmNetworkReaderYamlKeyedSettings.class.getCanonicalName());

  /**
   * Construct the per mode defaults for dealing with dangling subnetworks
   *
   * @return configuration for each mode in {@code DEFAULT_DANGLING_SUBNETWORK_MODES}
   */
  private static Map<PredefinedModeType, DanglingSubnetworkConfig> createDefaultDanglingSubnetworkConfig(){
    var configByMode = new EnumMap<PredefinedModeType, DanglingSubnetworkConfig>(PredefinedModeType.class);
    for(var modeType : DEFAULT_DANGLING_SUBNETWORK_MODES){
      configByMode.put(modeType, new DanglingSubnetworkConfig());
    }
    return configByMode;
  }

  /** all settings specific to parsing intersections from OSM traffic signals */
  private final OsmIntersectionSettings osmIntersectionSettings = new OsmIntersectionSettings();

  /** see {@link #getLaneDefaults()}, made again by a reset */
  private OsmLaneDefaults laneDefaults = new OsmLaneDefaults();

  /** all settings specific to osm highway tags, made on the defaults of the country, again by a reset */
  private OsmHighwaySettings osmHighwaySettings;

  /** all settings specific to osm railway tags, made on the defaults of the country, again by a reset */
  private OsmRailwaySettings osmRailwaySettings;

  /** all settings specific to OSM waterway (ferry) tags, made on the defaults of the country, again by a reset */
  private OsmWaterwaySettings osmWaterwaySettings;

  /** see {@link #registerNewOsmWayType(String, NewOsmWayTypeDefaults)}, by OSM tag key=value, none by default */
  private final Map<String, NewOsmWayTypeDefaults> newOsmWayTypes = new TreeMap<>();

  /** see {@link #overrideOsmWayLanes(long, UserOverride)}, by OSM way id, none by default */
  private final Map<Long, UserOverride<OsmWayLaneOverride>> osmWayLaneOverrides = new TreeMap<>();

  /** see {@link #excludeOsmWayFromParsing(UserOverride)}, by OSM way id, none by default */
  private final Map<Long, UserOverride<Long>> osmWaysExcludedFromParsing = new TreeMap<>();

  /** see {@link #excludeOsmRelationFromParsing(UserOverride)}, by OSM relation id, none by default */
  private final Map<Long, UserOverride<Long>> osmRelationsExcludedFromParsing = new TreeMap<>();

  /** see {@link #setKeepOsmWayOutsideBoundingPolygon(UserOverride)}, by OSM way id, none by default */
  private final Map<Long, UserOverride<Long>> osmWaysKeptOutsideBoundingPolygon = new TreeMap<>();

  /** see {@link #setKeepOsmNodeOutsideBoundingPolygon(UserOverride)}, by OSM node id, none by default */
  private final Map<Long, UserOverride<Long>> osmNodesKeptOutsideBoundingPolygon = new TreeMap<>();

  /** see {@link #overrideOsmWayModeAccess(long, UserOverride)}, by OSM way id, none by default */
  private final Map<Long, UserOverride<Set<String>>> osmWayModeAccessOverrides = new TreeMap<>();

  /** user supplied classification of OSM access value tags, overriding the defaults. Kept as a single map keyed by
   * the access value so that a value can never end up classified both positively and negatively at once */
  private final Map<String, AccessValueClassification> accessValueClassification = new TreeMap<>();

  /** see {@link #setRemoveDanglingSubnetworks(Map)}, by mode, car only by default */
  private final Map<PredefinedModeType, DanglingSubnetworkConfig> danglingSubnetworkConfigByMode =
      createDefaultDanglingSubnetworkConfig();

  /** see {@link #setConsolidateLinkSegmentTypes(boolean)} */
  private boolean consolidateLinkSegmentTypes = DEFAULT_CONSOLIDATE_LINK_SEGMENT_TYPES;

  /** see {@link #setAlwaysKeepLargestSubnetwork(boolean)} */
  private boolean alwaysKeepLargestSubnetwork = DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK;

  /** By default we allow ferry route OSM ways to be a fair way outside any bounding polygon and still be included.
   * We do so because often water bodies are not part of a zoning system and would therefore not include connecting
   * ferries. This is generally unwanted behaviour and therefore we automatically include all ferries within
   * the specified distance outside the bounding polygon and still be included. */
  private double maximumDistanceFerryOutsideBoundingPolygonMeters = DEFAULT_MAX_FERRY_DISTANCE_OUTSIDE_BOUNDING_AREA_M;

  /**
   * Make the highway, railway and waterway settings on the speed limit and mode access defaults of the country, as
   * the constructor does and a reset does again
   *
   * @param countryName whose defaults to use
   */
  private void createWaySettings(String countryName) {
    var speedLimitDefaults = OsmSpeedLimitDefaultsByCountry.create(countryName);
    var modeAccessDefaults = OsmModeAccessDefaultsByCountry.create(countryName);
    this.osmHighwaySettings = new OsmHighwaySettings(speedLimitDefaults.getUrbanHighwayDefaults(),
        speedLimitDefaults.getNonUrbanHighwayDefaults(), modeAccessDefaults.getHighwayModeAccessDefaults());
    this.osmRailwaySettings = new OsmRailwaySettings(
        speedLimitDefaults.getRailwayDefaults(), modeAccessDefaults.getRailwayModeAccessDefaults());
    this.osmWaterwaySettings = new OsmWaterwaySettings(
        speedLimitDefaults.getWaterwayDefaults(), modeAccessDefaults.getWaterwayModeAccessDefaults());
  }

  /**
   * Constructor
   *
   * @param inputSource to use, null when not known yet
   * @param countryName the full country name to base the defaults on
   */
  protected OsmNetworkReaderYamlKeyedSettings(URL inputSource, String countryName) {
    super(inputSource, countryName);
    createWaySettings(countryName);
  }

  /**
   * Log the highway, railway, waterway and intersection settings these settings hold, each in a section of its own,
   * so that settings extending these can log what they add before the sections
   *
   * @param level indentation level of these settings, the sections one deeper
   */
  protected void logNestedSettings(int level) {
    LOGGER.info(LoggingUtils.settingsSection("Highway", level + 1));
    getHighwaySettings().logSettings(level + 1);
    LOGGER.info(LoggingUtils.settingsSection("Railway", level + 1));
    getRailwaySettings().logSettings(level + 1);
    LOGGER.info(LoggingUtils.settingsSection("Waterway", level + 1));
    getWaterwaySettings().logSettings(level + 1);
    LOGGER.info(LoggingUtils.settingsSection("Intersections", level + 1));
    getIntersectionSettings().logSettings(level + 1);
  }

  /** by default we always consolidate functionally equivalent OSM types into a single PLANit link segment type */
  public static final boolean DEFAULT_CONSOLIDATE_LINK_SEGMENT_TYPES = true;

  /** by default we always keep the largest subnetwork */
  public static final boolean DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK = true;

  /** Default modes for which dangling subnetworks are dealt with after parsing: car only.
   * <p>
   * Being able to both reach and leave is a property of a single mode rather than of infrastructure, so this is
   * judged per mode. Car is the only mode treated by default, being the one whose network is routinely broken by
   * one way restrictions, and because withdrawing a mode's access is a modelling decision that should be asked
   * for rather than assumed for every mode present.
   * </p>
   * <p>
   * Rail and water are deliberately absent: their networks are routinely in disconnected pieces in an extract, a
   * branch line leaving the bounding area or a ferry crossing with nothing to connect to, without that indicating
   * a defect. Bicycle and pedestrian are absent for a related reason, their networks being fragmented rather than
   * directionally broken.
   * </p> */
  public static Set<PredefinedModeType> DEFAULT_DANGLING_SUBNETWORK_MODES = Set.of(PredefinedModeType.CAR);

  /** Default minimum size of subnetwork for a mode to keep its access, in vertices. At its maximum value every
   * subnetwork but the largest falls below it, which together with {@link #DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK}
   * and {@link #DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY} leaves a mode with a single network it can route across
   * in full. That is the safest default: anything less leaves subnetworks a trip can enter and then be unable to
   * complete */
  public static final int DEFAULT_MINIMUM_SUBNETWORK_SIZE = Integer.MAX_VALUE;

  /** Default notion of connectivity applied when removing dangling subnetworks: strong.
   * <p>
   * Weak connectivity asks only whether infrastructure is attached to the network, and so retains anything that
   * cannot be both entered and left, e.g. a car park served by a single one way road pointing outwards. Such
   * infrastructure cannot carry a route in either direction and is of no more use than a disconnected fragment,
   * which is why the stricter notion is the default.
   * </p>
   * <p>
   * Strong connectivity is judged per mode and honours the parsed turn restrictions: the mode moves from link segment
   * to link segment through the turns not banned for it, turning back onto the other direction of a way allowed unless
   * banned. A way the restrictions leave with no allowed turn onto it or off it, such as an on-ramp every turn onto
   * which is banned, loses the mode just as a way behind a one way road does, and the restrictions stay as banned
   * turns. The network that keeps the mode is then routable for it with every turn restriction in place.
   * </p> */
  public static Connectivity DEFAULT_DANGLING_SUBNETWORK_CONNECTIVITY = Connectivity.STRONG;

  /**
   * Override the lanes of an OSM way in each direction along it, in place of what its tags or the defaults give, such
   * as where its tags are missing or wrong. Forward runs from the first node of the way to its last, as OSM lists its
   * nodes, and backward the other way; they are not compass directions, see {@link OsmWayLaneOverride} for an example.
   * A direction the way has no traffic in keeps no link segment, so lanes given for it change nothing. Overriding the
   * way again replaces the override given before, its reason included. An OSM way id that no layer turns into links is
   * recorded as not found, with the reason as its detail
   *
   * @param osmWayId the OSM way id
   * @param lanes forward and backward along the way, at least one direction given, with the reason when there is one
   * @throws IllegalArgumentException naming the way, storing nothing, when neither direction is given or a direction
   *     has fewer than one lane
   */
  @SettingsWrite(key = "osmWayLaneOverrides", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "Lanes forward and backward along OSM ways, by way id, in place of their tags or the defaults")
  public void overrideOsmWayLanes(long osmWayId, UserOverride<OsmWayLaneOverride> lanes) {
    var forward = lanes.getValue().getLanesForward();
    var backward = lanes.getValue().getLanesBackward();
    ExceptionUtils.throwIllegalArgumentIf(forward == null && backward == null,
        "Lanes of OSM way %d overridden in neither direction", osmWayId);
    ExceptionUtils.throwIllegalArgumentIf((forward != null && forward < 1) || (backward != null && backward < 1),
        "Fewer than one lane overridden in a direction of OSM way %d: %s", osmWayId, lanes.getValue());
    osmWayLaneOverrides.put(osmWayId, lanes);
  }

  /**
   * Every lanes override with its reason, see {@link #overrideOsmWayLanes(long, UserOverride)}
   *
   * @return the overrides by OSM way id, in increasing way id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmWayLaneOverrides")
  public Map<Long, UserOverride<OsmWayLaneOverride>> getOsmWayLaneOverrides() {
    return Collections.unmodifiableMap(osmWayLaneOverrides);
  }

  /**
   * Exclude an OSM way from parsing, such as a way whose tags are wrong in a way no setting can fix. Excluding the way
   * again replaces the reason given before
   *
   * @param osmWayId the OSM way id, with the reason when there is one
   * @throws IllegalArgumentException naming the id, storing nothing, when it is zero or less, which no OSM way has
   */
  @SettingsWrite(key = "osmWaysExcludedFromParsing", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON, description = "OSM ways not to parse, by way id")
  public void excludeOsmWayFromParsing(UserOverride<Long> osmWayId) {
    PlanitOsmUtils.throwIfNotPositiveOsmId(osmWayId.getValue());
    osmWaysExcludedFromParsing.put(osmWayId.getValue(), osmWayId);
  }

  /**
   * Every OSM way excluded from parsing with its reason, see {@link #excludeOsmWayFromParsing(UserOverride)}
   *
   * @return the excluded ways by OSM way id, in increasing way id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmWaysExcludedFromParsing")
  public Map<Long, UserOverride<Long>> getOsmWaysExcludedFromParsing() {
    return Collections.unmodifiableMap(osmWaysExcludedFromParsing);
  }

  /**
   * Exclude an OSM relation from parsing, such as a turn restriction that is wrong in a way no setting can fix. An
   * excluded turn restriction makes no banned turn, so the turn it would ban is allowed, and a road it would trap is
   * judged without it when dangling subnetworks are removed. An id that no relation in the OSM input has is recorded as
   * not found, with the reason as its detail. Excluding the relation again replaces the reason given before
   *
   * @param osmRelationId the OSM relation id, with the reason when there is one
   * @throws IllegalArgumentException naming the id, storing nothing, when it is zero or less, which no OSM relation has
   */
  @SettingsWrite(key = "osmRelationsExcludedFromParsing", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "OSM relations not to parse, such as wrong turn restrictions, by relation id")
  public void excludeOsmRelationFromParsing(UserOverride<Long> osmRelationId) {
    PlanitOsmUtils.throwIfNotPositiveOsmId(osmRelationId.getValue());
    osmRelationsExcludedFromParsing.put(osmRelationId.getValue(), osmRelationId);
  }

  /**
   * Every OSM relation excluded from parsing with its reason, see {@link #excludeOsmRelationFromParsing(UserOverride)}
   *
   * @return the excluded relations by OSM relation id, in increasing relation id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmRelationsExcludedFromParsing")
  public Map<Long, UserOverride<Long>> getOsmRelationsExcludedFromParsing() {
    return Collections.unmodifiableMap(osmRelationsExcludedFromParsing);
  }

  /**
   * Keep an OSM way whole where it runs outside the bounding polygon. Parsing reads only the nodes inside the bounding
   * polygon, so a way running in and out of it is cut at its boundary. Kept whole, the way has its full length and the
   * network stays connected where the way crosses the boundary and back. Keeping the way again replaces the reason
   * given before
   *
   * @param osmWayId the OSM way id, with the reason when there is one
   */
  @SettingsWrite(key = "osmWaysKeptOutsideBoundingPolygon", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "OSM ways kept whole where they run outside the bounding polygon, by way id")
  public void setKeepOsmWayOutsideBoundingPolygon(UserOverride<Long> osmWayId) {
    osmWaysKeptOutsideBoundingPolygon.put(osmWayId.getValue(), osmWayId);
  }

  /**
   * Every OSM way kept whole outside the bounding polygon with its reason, see
   * {@link #setKeepOsmWayOutsideBoundingPolygon(UserOverride)}
   *
   * @return the kept ways by OSM way id, in increasing way id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmWaysKeptOutsideBoundingPolygon")
  public Map<Long, UserOverride<Long>> getOsmWaysKeptOutsideBoundingPolygon() {
    return Collections.unmodifiableMap(osmWaysKeptOutsideBoundingPolygon);
  }

  /**
   * Keep an OSM node that lies outside the bounding polygon, which parsing would otherwise leave out. Keeping the node
   * again replaces the reason given before
   *
   * @param osmNodeId the OSM node id, with the reason when there is one
   */
  @SettingsWrite(key = "osmNodesKeptOutsideBoundingPolygon", effect = SettingsKeyEffect.ADD_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "OSM nodes kept where they lie outside the bounding polygon, by node id")
  public void setKeepOsmNodeOutsideBoundingPolygon(UserOverride<Long> osmNodeId) {
    osmNodesKeptOutsideBoundingPolygon.put(osmNodeId.getValue(), osmNodeId);
  }

  /**
   * Every OSM node kept outside the bounding polygon with its reason, see
   * {@link #setKeepOsmNodeOutsideBoundingPolygon(UserOverride)}
   *
   * @return the kept nodes by OSM node id, in increasing node id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmNodesKeptOutsideBoundingPolygon")
  public Map<Long, UserOverride<Long>> getOsmNodesKeptOutsideBoundingPolygon() {
    return Collections.unmodifiableMap(osmNodesKeptOutsideBoundingPolygon);
  }

  /**
   * Override the modes allowed on an OSM way, in place of what its tags or the defaults give, such as where its access
   * tags are wrong. The OSM modes given are the only ones allowed on it, in both directions. Overriding the way again
   * replaces the override given before, its reason included
   *
   * @param osmWayId the OSM way id
   * @param allowedOsmModes the only OSM modes allowed on it, such as foot and bicycle, with the reason when there is
   *     one
   */
  @SettingsWrite(key = "osmWayModeAccessOverrides", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "The only OSM modes allowed on OSM ways, by way id, in place of their tags or the defaults")
  public void overrideOsmWayModeAccess(long osmWayId, UserOverride<Set<String>> allowedOsmModes) {
    osmWayModeAccessOverrides.put(osmWayId, allowedOsmModes);
  }

  /**
   * Every mode access override with its reason, see {@link #overrideOsmWayModeAccess(long, UserOverride)}
   *
   * @return the OSM modes allowed by OSM way id, in increasing way id order, unmodifiable, empty when none
   */
  @SettingsRead(key = "osmWayModeAccessOverrides")
  public Map<Long, UserOverride<Set<String>>> getOsmWayModeAccessOverrides() {
    return Collections.unmodifiableMap(osmWayModeAccessOverrides);
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
   * @throws IllegalArgumentException storing nothing, when the access value tag is null or blank
   */
  @SettingsWrite(key = "accessValueClassification", effect = SettingsKeyEffect.PUT_EACH,
      level = SettingsKeyLevel.UNCOMMON,
      description = "OSM access values read as granting (POSITIVE) or denying (NEGATIVE) access, in place of the " +
          "defaults")
  public void classifyAccessValueTag(final String osmAccessValueTag, final AccessValueClassification classification) {
    ExceptionUtils.throwIllegalArgumentIf(StringUtils.isNullOrBlank(osmAccessValueTag),
        "Null or blank OSM access value tag provided for classification");
    if(!OsmAccessTags.getAllKnownAccessValueTags().contains(osmAccessValueTag)){
      LOGGER.warning(String.format(
          "OSM access value tag '%s' is not a recognised access value, classifying it as %s regardless, at the " +
              "user's own risk", osmAccessValueTag, classification));
    }
    accessValueClassification.put(osmAccessValueTag, classification);
  }

  /**
   * Every access value classified by the user, see {@link #classifyAccessValueTag(String, AccessValueClassification)}
   *
   * @return the classification by access value, in alphabetical order, unmodifiable, empty when none
   */
  @SettingsRead(key = "accessValueClassification")
  public Map<String, AccessValueClassification> getAccessValueClassification() {
    return Collections.unmodifiableMap(accessValueClassification);
  }

  /**
   * Set the modes whose dangling subnetworks are dealt with after parsing, and how, replacing those set before. By
   * default only car's are, see {@link #DEFAULT_DANGLING_SUBNETWORK_MODES}.
   * <p>
   * Each mode is judged on its own network, and where that network does not meet the criteria the mode's access is
   * withdrawn rather than the infrastructure removed. Infrastructure only disappears once no mode is left that can
   * use it. This matters because a link one mode cannot use is typically carrying another perfectly well: a one
   * way street usually still permits pedestrians against the flow, which is also why judging this per track type
   * cannot work.
   * </p>
   * <p>
   * Under {@link Connectivity#STRONG} a subnetwork is what the mode can both reach and leave, which is what
   * routing requires. Under {@link Connectivity#WEAK} it is what hangs together when direction is ignored, which
   * reports a one way trap as perfectly healthy.
   * </p>
   *
   * @param configByMode how to deal with each mode's dangling subnetworks, stored as copies, empty to deal with none
   */
  @SettingsWrite(key = "removeDanglingSubnetworks", level = SettingsKeyLevel.COMMON,
      description = "The modes whose dangling subnetworks lose the mode's access, replacing the defaults (car only)")
  public void setRemoveDanglingSubnetworks(Map<PredefinedModeType, DanglingSubnetworkConfig> configByMode) {
    danglingSubnetworkConfigByMode.clear();
    configByMode.forEach((modeType, config) -> danglingSubnetworkConfigByMode.put(modeType,
        new DanglingSubnetworkConfig(config.getBelowSize(), config.getAboveSize(), config.getConnectivity())));
  }

  /** Collect the modes for which dangling subnetworks are dealt with, together with their configuration
   *
   * @return configuration by mode, unmodifiable, empty when none are activated
   */
  @SettingsRead(key = "removeDanglingSubnetworks")
  public Map<PredefinedModeType, DanglingSubnetworkConfig> getRemoveDanglingSubnetworkModes() {
    return Collections.unmodifiableMap(danglingSubnetworkConfigByMode);
  }

  /**
   * Register a new OSM way type to parse besides the types parsing supports, with the defaults for the ways of the
   * type whose tags give none: lanes, speed limit, capacity, maximum density and the OSM modes allowed.
   * <p>
   * Parsing only takes the way types it knows, as only those have defaults built in. The ways of any other type, such
   * as highway=escape, stay out of the network however they are tagged. Registered here, they become links like any
   * other. Railway tracks and waterway lanes are one setting for all their types, see {@link #getLaneDefaults()}, so
   * lanes for a railway or waterway type that differ from it are warned about and not used, as is a speed limit for a
   * type that has one by default
   * </p>
   *
   * @param osmWayTag the type as OSM key=value, such as highway=escape
   * @param defaults for the ways of the type, every value given, stored as a copy
   * @throws IllegalArgumentException naming the type, storing nothing, when it is not written as key=value, its key is
   *     not highway, railway or waterway, it is a highway type with default lanes already, such as one parsing
   *     supports or one registered before, a default is left out, or an allowed mode is not an OSM mode
   */
  @SettingsWrite(key = "newOsmWayTypes", effect = SettingsKeyEffect.PUT_EACH, level = SettingsKeyLevel.UNCOMMON,
      description = "OSM way types to parse besides the supported ones, by key=value such as highway=escape, with " +
          "the defaults for their ways")
  public void registerNewOsmWayType(String osmWayTag, NewOsmWayTypeDefaults defaults) {
    var keyAndValue = osmWayTag.split("=", 2);
    ExceptionUtils.throwIllegalArgumentIf(
        keyAndValue.length != 2 || keyAndValue[0].isBlank() || keyAndValue[1].isBlank(),
        "New OSM way type %s is not written as key=value, such as highway=escape", osmWayTag);
    ExceptionUtils.throwIllegalArgumentIf(defaults.getLanes() == null || defaults.getSpeedLimitKmh() == null ||
        defaults.getCapacityPerLanePcuH() == null || defaults.getMaxDensityPerLanePcuKm() == null ||
        defaults.getAllowedOsmModes().isEmpty(), "New OSM way type %s is missing a default: %s", osmWayTag, defaults);
    var osmWayKey = keyAndValue[0];
    var osmWayTypeValue = keyAndValue[1];
    OsmWaySettings waySettings;
    if (OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      ExceptionUtils.throwIllegalArgumentIf(laneDefaults.containsDefaultLanes(osmWayKey, osmWayTypeValue),
          "Not allowed to add an OSM type that already exists: %s", osmWayTag);
      waySettings = osmHighwaySettings;
    } else if (OsmRailwayTags.isRailwayKeyTag(osmWayKey)) {
      if (!defaults.getLanes().equals(laneDefaults.getRailwayTracksPerDirection())) {
        LOGGER.warning(String.format("Custom railway=%s number of tracks set to %d (instead of %d), " +
                "this is a global setting, overwrite globally to change it",
            osmWayTypeValue, laneDefaults.getRailwayTracksPerDirection(), defaults.getLanes()));
      }
      waySettings = osmRailwaySettings;
    } else if (OsmWaterwayTags.isAnyWaterwayKeyTag(osmWayKey)) {
      if (!defaults.getLanes().equals(laneDefaults.getWaterwayLanesPerDirection())) {
        LOGGER.warning(String.format("Custom waterway [%s=%s] waterway lanes set to %d (instead of %d)," +
                " this is a global setting, overwrite globally to change it",
            osmWayKey, osmWayTypeValue, laneDefaults.getWaterwayLanesPerDirection(), defaults.getLanes()));
      }
      waySettings = osmWaterwaySettings;
    } else {
      throw new IllegalArgumentException(String.format(
          "Custom way type for %s is not allowed due to key not being a known or supported OSM way type", osmWayTag));
    }

    boolean success = waySettings.registerNewSupportedOsmWayType(osmWayKey, osmWayTypeValue,
        defaults.getSpeedLimitKmh(), defaults.getCapacityPerLanePcuH(), defaults.getMaxDensityPerLanePcuKm(),
        defaults.getAllowedOsmModes().toArray(String[]::new));
    if (!success) {
      LOGGER.warning(String.format(
          "Unable to register new OsmWaytype %s due to issue regarding way type configuration", osmWayTag));
    }
    if (OsmHighwayTags.isHighwayKeyTag(osmWayKey)) {
      laneDefaults.setLanesByHighwayType(osmWayTypeValue, defaults.getLanes());
    }
    newOsmWayTypes.put(osmWayTag, new NewOsmWayTypeDefaults(defaults.getLanes(), defaults.getSpeedLimitKmh(),
        defaults.getCapacityPerLanePcuH(), defaults.getMaxDensityPerLanePcuKm(), defaults.getAllowedOsmModes()));
  }

  /**
   * Every new OSM way type registered with its defaults, see
   * {@link #registerNewOsmWayType(String, NewOsmWayTypeDefaults)}
   *
   * @return the defaults by OSM tag key=value, in alphabetical order, unmodifiable, empty when none
   */
  @SettingsRead(key = "newOsmWayTypes")
  public Map<String, NewOsmWayTypeDefaults> getNewOsmWayTypes() {
    return Collections.unmodifiableMap(newOsmWayTypes);
  }

  /** provide the settings of parsing intersections from traffic signals, consolidating and collapsing them
   * @return intersection settings
   */
  @SettingsSection(key = "intersections", level = SettingsKeyLevel.COMMON, description =
      "Parsing intersections: traffic signals, and joining the nodes of one junction into one intersection")
  public OsmIntersectionSettings getIntersectionSettings() {
    return osmIntersectionSettings;
  }

  /** The lane defaults, used where the tags of a way give no lanes
   *
   * @return the lanes by highway type, and per direction of railways and waterways
   */
  @SettingsSection(key = "laneDefaults", level = SettingsKeyLevel.UNCOMMON,
      description = "Lanes per direction of each way type, where the tags give none")
  public OsmLaneDefaults getLaneDefaults() {
    return laneDefaults;
  }

  /** provide highway specific settings
   * @return highway settings
   */
  @SettingsSection(key = "highway", level = SettingsKeyLevel.STANDARD,
      description = "The road ways parsed, with their modes, speeds and capacities")
  public OsmHighwaySettings getHighwaySettings() {
    return osmHighwaySettings;
  }

  /** provide railway specific settings
   * @return railway settings
   */
  @SettingsSection(key = "railway", level = SettingsKeyLevel.STANDARD,
      description = "The rail ways parsed, with their modes and speeds")
  public OsmRailwaySettings getRailwaySettings() {
    return osmRailwaySettings;
  }

  /** provide waterway specific settings
   * @return waterway settings
   */
  @SettingsSection(key = "waterway", level = SettingsKeyLevel.STANDARD,
      description = "The ferry routes parsed, with their modes and speeds")
  public OsmWaterwaySettings getWaterwaySettings() {
    return osmWaterwaySettings;
  }

  /** Flag on whether to consolidate functionally equivalent link segment types for various OSM way types into a single
   * PLANit type
   *
   * @return flag set
   */
  @SettingsRead(key = "consolidateLinkSegmentTypes")
  public boolean isConsolidateLinkSegmentTypes() {
    return consolidateLinkSegmentTypes;
  }

  /** Flag on whether to consolidate functionally equivalent link segment types for various OSM way types into a single
   * PLANit type
   *
   * @param flag to set
   * */
  @SettingsWrite(key = "consolidateLinkSegmentTypes", level = SettingsKeyLevel.STANDARD,
      description = "Give OSM way types that convert to the same link segment type one PLANit link segment type")
  public void setConsolidateLinkSegmentTypes(boolean flag) {
    this.consolidateLinkSegmentTypes = flag;
  }

  /** Verify if the largest subnetwork is always kept when we are removing dangling subnetworks
   *
   * @return true when kept false otherwise
   */
  @SettingsRead(key = "alwaysKeepLargestSubnetwork")
  public boolean isAlwaysKeepLargestSubnetwork() {
    return alwaysKeepLargestSubnetwork;
  }

  /** indicate to keep the largest subnetwork always even when removing dangling subnetworks and the largest one
   * does not fit the set criteria
   *
   * @param alwaysKeepLargestSubnetwork when true we always keep it, otherwise not
   */
  @SettingsWrite(key = "alwaysKeepLargestSubnetwork", level = SettingsKeyLevel.STANDARD,
      description = "Keep a mode's largest subnetwork when removing dangling subnetworks, whatever its size")
  public void setAlwaysKeepLargestSubnetwork(boolean alwaysKeepLargestSubnetwork) {
    this.alwaysKeepLargestSubnetwork = alwaysKeepLargestSubnetwork;
  }

  /** Get the maximum distance outside the bounding area PLANit will still include ferry routes
   *
   * @return distance set
   */
  @SettingsRead(key = "maximumDistanceFerryOutsideBoundingPolygonMeters")
  public double getMaximumDistanceFerryOutsideBoundingPolygonMeters() {
    return maximumDistanceFerryOutsideBoundingPolygonMeters;
  }

  /** Set the maximum distance outside the bounding area PLANit will still include ferry routes
   *
   * @param distanceMeters distance to use
   */
  @SettingsWrite(key = "maximumDistanceFerryOutsideBoundingPolygonMeters", level = SettingsKeyLevel.RARE,
      description = "Furthest a ferry route may run outside the bounding area and still be kept, in metres")
  public void setMaximumDistanceFerryOutsideBoundingPolygonMeters(double distanceMeters) {
    this.maximumDistanceFerryOutsideBoundingPolygonMeters = distanceMeters;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    super.logSettings(level);
    LOGGER.info(LoggingUtils.settingsValue("Consolidate equivalent OSM link types",
        isConsolidateLinkSegmentTypes(), level));
    /* every activated mode's configuration is logged in full rather than a flag alone, since the thresholds and
     * the notion of connectivity together decide what a pass actually does and are easily overlooked. The absence
     * of any activated mode is logged too, so that it is visible rather than merely unstated */
    if(getRemoveDanglingSubnetworkModes().isEmpty()){
      LOGGER.info(LoggingUtils.settingsValue("Remove dangling subnetworks", "deactivated for all modes", level));
    }else{
      for(var entry : getRemoveDanglingSubnetworkModes().entrySet()){
        LOGGER.info(LoggingUtils.settingsValue(
            String.format("Remove dangling subnetworks (%s)", entry.getKey().value()),
            entry.getValue().toString(), level));
      }
    }
    LOGGER.info(LoggingUtils.settingsValue("Always keep largest subnetwork",
        isAlwaysKeepLargestSubnetwork(), level));
    LOGGER.info(LoggingUtils.settingsValue("Maximum ferry distance outside bounding polygon (m)",
        getMaximumDistanceFerryOutsideBoundingPolygonMeters(), level));
    if(!getOsmWayLaneOverrides().isEmpty()){
      LOGGER.info(LoggingUtils.settingsValue("Lanes overridden by OSM way id", getOsmWayLaneOverrides(), level));
    }
    if(!getAccessValueClassification().isEmpty()){
      LOGGER.info(LoggingUtils.settingsValue("Access value tags reclassified by user",
          getAccessValueClassification().entrySet().stream().map(
              e -> String.format("%s=%s", e.getKey(), e.getValue().name().toLowerCase())).collect(
                  Collectors.joining(", ")), level));
    }
  }

  /**
   * Put every key back to its default for the country, which a reset keeps as only a constructor sets it. The highway,
   * railway and waterway settings and the lane defaults are made again, as the constructor makes them; the
   * intersection settings reset themselves
   */
  @Override
  public void reset() {
    super.reset();
    createWaySettings(getCountryName());
    this.laneDefaults = new OsmLaneDefaults();
    osmIntersectionSettings.reset();
    newOsmWayTypes.clear();
    osmWayLaneOverrides.clear();
    osmWaysExcludedFromParsing.clear();
    osmRelationsExcludedFromParsing.clear();
    osmWaysKeptOutsideBoundingPolygon.clear();
    osmNodesKeptOutsideBoundingPolygon.clear();
    osmWayModeAccessOverrides.clear();
    accessValueClassification.clear();
    danglingSubnetworkConfigByMode.clear();
    danglingSubnetworkConfigByMode.putAll(createDefaultDanglingSubnetworkConfig());
    this.consolidateLinkSegmentTypes = DEFAULT_CONSOLIDATE_LINK_SEGMENT_TYPES;
    this.alwaysKeepLargestSubnetwork = DEFAULT_ALWAYS_KEEP_LARGEST_SUBNETWORK;
    this.maximumDistanceFerryOutsideBoundingPolygonMeters = DEFAULT_MAX_FERRY_DISTANCE_OUTSIDE_BOUNDING_AREA_M;
  }
}

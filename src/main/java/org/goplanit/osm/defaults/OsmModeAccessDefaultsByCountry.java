package org.goplanit.osm.defaults;

import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVRecord;
import org.goplanit.osm.tags.*;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.locale.LocaleUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.misc.StringUtils;

/**
 * The defaults for the mode access in case no specific restrictions are indicated on the highway=type way.
 * Based on the information provided on <a href="https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions">access restrictions</a>
 * <p>
 * Note: not all highway types have defaults following the references wiki page. Those missing types are listed below with the assigned defaults based on
 * PLANit's own rules:
 * <ul>
 * <li>highway:service, same as road</li>
 * <li>track, same as road</li>
 * </ul>
 *
 * @author markr
 *
 */
public class OsmModeAccessDefaultsByCountry {
  
  /**
   * The logger for this class
   */
  private static final Logger LOGGER = Logger.getLogger(OsmModeAccessDefaultsByCountry.class.getCanonicalName());
  
  /** store all global defaults as fallback option **/
  private static final OsmModeAccessDefaults GLOBAL_MODE_ACCESS_DEFAULTS = new OsmModeAccessDefaults();
  
  /** store all defaults per country by ISO2 code **/
  private static final Map<String,OsmModeAccessDefaults> MODE_ACCESS_DEFAULTS_BY_COUNTRY = new HashMap<>();
  
  /** reference to the resource dir where we store the country specific mode access defaults */
  private static final String MODE_ACCESS_RESOURCE_DIR = "mode_access";
  
  /** store the global defaults as fall back option */
  protected static OsmModeAccessDefaults globalModeAccessDefaults;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmModeAccessDefaults> modeAccessDefaultsByCountry = new HashMap<>();
   
  /* initialise */
  static {    
    /* global */
    populateGlobalDefaultHighwayModeAccess();
    populateGlobalDefaultRailwayModeAccess();
    populateGlobalDefaultWaterwayModeAccess();
    
    /* country specific for supported countries */
    populateCountrySpecificDefaultModeAccess();        
  }
     
  /**
   * populate the global defaults for highway types, i.e., roads
   */
  protected static void populateGlobalDefaultHighwayModeAccess() {
    
    /* MOTORWAY and MOTORWAY_LINK --- the same setup with:
     * 
     * - allow all modes of category MOTOR_VEHICLE
     * - disallow slow motorcycles (MOPED, MOFA) and other exotic modes (ATV, GOLF_CART)  
     */
    
    /* MOTORWAY */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.MOTORWAY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultDisallowedModes(
          keyValue, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);
    }  
    
    /* MOTORWAY_LINK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.MOTORWAY_LINK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultDisallowedModes(
          keyValue, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);
    }  
    
    /* TRUNK ---- ROAD All the same setup with:
     * 
     * - allow all modes of category VEHICLE
     * - and allow pedestrian, i.e., FOOT  
     */
    
    /* TRUNK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.TRUNK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }      
    /* TRUNK_LINK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.TRUNK_LINK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }  
    /* PRIMARY */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.PRIMARY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }      
    /* PRIMARY_LINK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.PRIMARY_LINK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }   
    /* SECONDARY */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.SECONDARY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }      
    /* SECONDARY_LINK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.SECONDARY_LINK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }
    /* TERTIARY */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.TERTIARY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }      
    /* TERTIARY_LINK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.TERTIARY_LINK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }
    /* BUSWAY */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.BUSWAY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.PUBLIC_SERVICE_VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }
    /* UNCLASSIFIED */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.UNCLASSIFIED);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }      
    /* RESIDENTIAL */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.RESIDENTIAL);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }
    /* LIVING_STREET */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.LIVING_STREET);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }      
    /* ROAD */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.ROAD);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }    
    /* SERVICE */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.SERVICE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }     
    /* TRACK */
    {
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.TRACK);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(
          keyValue, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }     
    
    /* PEDESTRIAN */
    {
      /* pedestrian basically only allows foot based modes */
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.PEDESTRIAN);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG);
    }
    
    /* STEPS (same as PEDESTRIAN)*/
    {
      /* steps only allows foot based modes */
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.STEPS);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRoadModeTags.FOOT, OsmRoadModeTags.DOG);
    }    
    
    /* PATH */
    {
      /* a path only allows single track non_vehicular modes */
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.PATH);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue,
          OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG, OsmRoadModeTags.HORSE, OsmRoadModeTags.BICYCLE);    
    }  
    
    /* BRIDLEWAY */
    {
      /* a bridleway is for horses */
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.BRIDLEWAY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRoadModeTags.HORSE);
    }
    
    /* CYCLEWAY*/
    {
      /* a cycleway is for bicycles */
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.CYCLEWAY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRoadModeTags.BICYCLE);
    }
    
    /* FOOTWAY*/
    {
      /* same as pedestrian (only designated in SOM but we do not make this distinction */
      var keyValue = Pair.of(OsmHighwayTags.getHighwayKeyTag(), OsmHighwayTags.FOOTWAY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(keyValue, OsmRoadModeTags.FOOT);
    }         
  }
  

  /**
   * populate the global defaults for railway types
   */
  protected static void populateGlobalDefaultRailwayModeAccess() {
    
    /* FUNICULAR */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.FUNICULAR);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.FUNICULAR));
    }
    
    /* LIGHTRAIL */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.LIGHT_RAIL);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.LIGHT_RAIL));
      /* often mistakes are made where lightrail tracks are mapped to tram stations, and vice-versa, therefore
      by default we allow both light rail and tram on light rail tracks */
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.TRAM));
    }
    
    /* MONORAIL */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.MONO_RAIL);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.MONO_RAIL));
    }
    
    /* NARROW GAUGE */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.NARROW_GAUGE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.NARROW_GAUGE));
    }
    
    /* RAIL */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.RAIL);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.RAIL));
    }
    
    /* SUBWAY */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.SUBWAY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.SUBWAY));
    }        
    
    
    /* TRAM */
    {
      var keyValue = Pair.of(OsmRailwayTags.getRailwayKeyTag(), OsmRailwayTags.TRAM);
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.TRAM));
      /* often mistakes are made where lightrail tracks are mapped to tram stations, and vice-versa,
      therefore by default we allow both light rail and tram on light rail tracks */
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.LIGHT_RAIL));
    }                
    
  }

  /**
   * populate the global defaults for waterway types.
   */
  protected static void populateGlobalDefaultWaterwayModeAccess() {

    // complicated because we allow for 2 keys to indicate water mode mappings, i.e., route=ferry, but also
    // ferry= _a_highwaytype_. In all cases they mpa to the same mode though ferry. That's why the initialisation
    // takes ferry both as a key as well as the result

    /* route=ferry -> ferry */
    {
      var keyValue = Pair.of(OsmWaterwayTags.ROUTE, OsmWaterModeTags.FERRY);
      GLOBAL_MODE_ACCESS_DEFAULTS.getWaterwayModeAccessDefaults().addDefaultAllowedModes(
          keyValue, OsmWaterModeTags.FERRY);
    }

    /* ferry=_some_highway_type -> ferry */
    {
      OsmWaterwayTags.getAllSupportedHighwayTypesAsWaterWayTypes().forEach(highwayAsWaterWayType -> {
        var keyValue = Pair.of(OsmWaterwayTags.getKeyForValueType(
            highwayAsWaterWayType), highwayAsWaterWayType);
        GLOBAL_MODE_ACCESS_DEFAULTS.getWaterwayModeAccessDefaults().addDefaultAllowedModes(
            keyValue, OsmWaterModeTags.FERRY);}
      );
    }
  }
      
  /**
   * For all countries for which a dedicated CSV is available under the resources dir, we parse their mode
   * access defaults accordingly
   */
  protected static void populateCountrySpecificDefaultModeAccess(){
    CountrySpecificDefaultUtils.callForEachFileInResourceDir(
        MODE_ACCESS_RESOURCE_DIR, OsmModeAccessDefaultsByCountry::populateCountrySpecificDefaultModeAccess);    
  } 
  
  /** Each file should be named according to ISO366 alpha 2 country code. The mode access defaults are parsed as
   * CSV format and overwrite the global defaults for this country. If no explicit value is provided, we revert to
   * the global defaults instead.
   * 
   * @param inputReader to extract speed limit defaults from
   * @param fullCountryName these defaults relate to
   */
  protected static void populateCountrySpecificDefaultModeAccess(InputStreamReader inputReader, String fullCountryName){
                 
    try {
      /* OSM way key, first entry in record */
      Iterable<CSVRecord> records = CountrySpecificDefaultUtils.collectCsvRecordIterable(inputReader);            
      for(CSVRecord record : records) {
        String osmWayType = record.get(0).trim();
        boolean isOsmHighway = OsmHighwayTags.isRoadBasedHighwayValueTag(osmWayType);
        boolean isOsmRailwayTag = false, isOsmWaterwayTag = false;
        if(!isOsmHighway) {
          isOsmRailwayTag = OsmRailwayTags.isRailBasedRailway(osmWayType);
          isOsmWaterwayTag = OsmWaterwayTags.getKeyForValueType(osmWayType) != null;
        }

        if(!(isOsmHighway || isOsmRailwayTag || isOsmWaterwayTag)) {
          LOGGER.warning(String.format("IGNORED: OSM way type (%s) in country specific mode access defaults is not a " +
              "valid highway, railway, or waterway type",osmWayType));
        }
      
        /* allowed OSM modes */
        List<String> allowedOsmModes = new ArrayList<>(record.size() - 1);
        for(int columnIndex = 1; columnIndex< record.size() ; ++columnIndex) {
          final String osmMode = record.get(columnIndex).trim();
          if(!StringUtils.isNullOrBlank(osmMode)) {
            allowedOsmModes.add(osmMode);
          }
        }
        
        /* register on defaults */
        if(!allowedOsmModes.isEmpty()) {
          /* copy the global defaults and make adjustments */
          OsmModeAccessDefaults countryDefaults = GLOBAL_MODE_ACCESS_DEFAULTS.deepClone();
          countryDefaults.setCountry(fullCountryName);          

          if(isOsmHighway) {
            countryDefaults.getHighwayModeAccessDefaults().setAllowedModes(
                OsmHighwayTags.getHighwayKeyTag(), osmWayType, false /* no logging */, allowedOsmModes);
          }else if(isOsmRailwayTag) {
            countryDefaults.getRailwayModeAccessDefaults().setAllowedModes(
                OsmRailwayTags.getRailwayKeyTag(), osmWayType, false /* no logging */, allowedOsmModes);
          }else if(isOsmWaterwayTag){
            var osmWayKey = OsmWaterwayTags.getKeyForValueType(osmWayType);
            countryDefaults.getWaterwayModeAccessDefaults().setAllowedModes(
                osmWayKey, osmWayType, false /* no logging */, allowedOsmModes);
          }
      
          /* register country defaults */
          setDefaultsByCountry(countryDefaults);
        }
        
      }
      
    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format(
          "Parsing of CSV input stream with mode access defaults failed for %s", fullCountryName));
    }    
  }  
  
  /** Set defaults for a specific county
   * 
   * @param modeAccessDefaults defaults to use
   */
  protected static void setDefaultsByCountry(OsmModeAccessDefaults modeAccessDefaults) {
    if(!Objects.equals(modeAccessDefaults.getCountry(), CountryNames.GLOBAL)) {
      MODE_ACCESS_DEFAULTS_BY_COUNTRY.put(LocaleUtils.getIso2CountryCodeByName(
          modeAccessDefaults.getCountry()), modeAccessDefaults);
    }else {
      LOGGER.warning("setting OSM mode access defaults by country, then the defaults should have a country " +
          "specified, this is not the case, defaults, ignored");
    }
  }    
  
  /**
   * Default factory method for creating global defaults
   * 
   * @return created mode access defaults
   */
  public static OsmModeAccessDefaults create() {
    OsmModeAccessDefaults theDefaults = null;
    try {
      theDefaults = GLOBAL_MODE_ACCESS_DEFAULTS.deepClone();
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("Unable to initialise global mode access defaults");
    } 
    return theDefaults;
  }
  
  /**
   * Factory method to create defaults based on a given country. If country cannot be found or cannot be converted
   * into ISO2 country code, we revert to global defaults
   * 
   * @param countryName to base on
   * @return created mode access defaults
   * 
   */
  public static OsmModeAccessDefaults create(String countryName) {
    OsmModeAccessDefaults theDefaults = null;
    try {    
      String iso2CountryCode = LocaleUtils.getIso2CountryCodeByName(countryName);
      if(iso2CountryCode != null && MODE_ACCESS_DEFAULTS_BY_COUNTRY.containsKey(iso2CountryCode)) {
        theDefaults = MODE_ACCESS_DEFAULTS_BY_COUNTRY.get(iso2CountryCode).deepClone();
      }else {
        LOGGER.info("Reverting to global mode access defaults, rather than country specific ones");
        theDefaults = GLOBAL_MODE_ACCESS_DEFAULTS.deepClone();
      }
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("Unable to initialise global mode access defaults");
    }    
    return theDefaults;       
  }  

    
}

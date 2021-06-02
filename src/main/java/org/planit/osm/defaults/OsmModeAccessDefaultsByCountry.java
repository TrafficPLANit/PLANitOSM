package org.planit.osm.defaults;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVRecord;
import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;
import org.planit.utils.misc.StringUtils;
import org.planit.utils.locale.CountryNames;

/**
 * The defaults for the mode access in case no specific restrictions are indicated on the highway=type way.
 * Based on the information provided on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
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
  private static final Map<String,OsmModeAccessDefaults> MODE_ACCESS_DEFAULTS_BY_COUNTRY = new HashMap<String,OsmModeAccessDefaults>();
  
  /** reference to the resource dir where we store the country specific mode access defaults */
  private static final String MODE_ACCESS_RESOURCE_DIR = "mode_access";
  
  /** store the global defaults as fall back option */
  protected static OsmModeAccessDefaults globalModeAccessDefaults;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmModeAccessDefaults> modeAccessDefaultsByCountry = new HashMap<String,OsmModeAccessDefaults>();  
   
  /* initialise */
  static {    
    /* global */
    populateGlobalDefaultHighwayModeAccess();
    populateGlobalDefaultRailwayModeAccess();
    
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
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories( OsmHighwayTags.MOTORWAY, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultDisallowedModes(
          OsmHighwayTags.MOTORWAY, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);      
    }  
    
    /* MOTORWAY_LINK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories( OsmHighwayTags.MOTORWAY_LINK, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultDisallowedModes(
          OsmHighwayTags.MOTORWAY_LINK, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);      
    }  
    
    /* TRUNK ---- ROAD All the same setup with:
     * 
     * - allow all modes of category VEHICLE
     * - and allow pedestrian, i.e., FOOT  
     */
    
    /* TRUNK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TRUNK, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TRUNK, OsmRoadModeTags.FOOT);      
    }      
    /* TRUNK_LINK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TRUNK_LINK, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TRUNK_LINK, OsmRoadModeTags.FOOT);    
    }  
    /* PRIMARY */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.PRIMARY, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PRIMARY, OsmRoadModeTags.FOOT);      
    }      
    /* PRIMARY_LINK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.PRIMARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PRIMARY_LINK, OsmRoadModeTags.FOOT);    
    }   
    /* SECONDARY */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.SECONDARY, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.SECONDARY, OsmRoadModeTags.FOOT);      
    }      
    /* SECONDARY_LINK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.SECONDARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.SECONDARY_LINK, OsmRoadModeTags.FOOT);    
    }
    /* TERTIARY */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TERTIARY, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TERTIARY, OsmRoadModeTags.FOOT);      
    }      
    /* TERTIARY_LINK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TERTIARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TERTIARY_LINK, OsmRoadModeTags.FOOT);    
    }
    /* UNCLASSIFIED */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.UNCLASSIFIED, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.UNCLASSIFIED, OsmRoadModeTags.FOOT);      
    }      
    /* RESIDENTIAL */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.RESIDENTIAL, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.RESIDENTIAL, OsmRoadModeTags.FOOT);    
    }
    /* LIVING_STREET */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.LIVING_STREET, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.LIVING_STREET, OsmRoadModeTags.FOOT);      
    }      
    /* ROAD */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.ROAD, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.ROAD, OsmRoadModeTags.FOOT);    
    }    
    /* SERVICE */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.SERVICE, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.SERVICE, OsmRoadModeTags.FOOT);    
    }     
    /* TRACK */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TRACK, OsmRoadModeCategoryTags.VEHICLE);
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TRACK, OsmRoadModeTags.FOOT);    
    }     
    
    /* PEDESTRIAN */
    {
      /* pedestrian basically only allows foot based modes */
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG);    
    }
    
    /* STEPS (same as PEDESTRIAN)*/
    {
      /* steps only allows foot based modes */
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.STEPS, OsmRoadModeTags.FOOT, OsmRoadModeTags.DOG);    
    }    
    
    /* PATH */
    {
      /* a path only allows single track non_vehicular modes */
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PATH, 
          OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG, OsmRoadModeTags.HORSE, OsmRoadModeTags.BICYCLE);    
    }  
    
    /* BRIDLEWAY */
    {
      /* a bridleway is for horses */
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmRoadModeTags.HORSE);    
    }
    
    /* CYCLEWAY*/
    {
      /* a cycleway is for bicycles */
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.CYCLEWAY, OsmRoadModeTags.BICYCLE);    
    }
    
    /* FOOTWAY*/
    {
      /* same as pedestrian (only designated in SOM but we do not make this distinction */
      GLOBAL_MODE_ACCESS_DEFAULTS.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.FOOTWAY, OsmRoadModeTags.FOOT);    
    }         
  }
  

  /**
   * populate the global defaults for railway types
   */
  protected static void populateGlobalDefaultRailwayModeAccess() {
    
    /* FUNICULAR */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.FUNICULAR, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.FUNICULAR));
    }
    
    /* LIGHTRAIL */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.LIGHT_RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.LIGHT_RAIL));
      /* often mistakes are made where lightrail tracks are mapped to tram stations, and vice-versa, therefore by default we allow both light rail and tram on light rail tracks */
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.LIGHT_RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.TRAM));
    }
    
    /* MONORAIL */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.MONO_RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.MONO_RAIL));     
    }
    
    /* NARROW GAUGE */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.NARROW_GAUGE, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.NARROW_GAUGE));
    }
    
    /* RAIL */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.RAIL));
    }
    
    /* SUBWAY */
    {
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.SUBWAY, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.SUBWAY));     
    }        
    
    
    /* TRAM */
    {      
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.TRAM, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.TRAM));
      /* often mistakes are made where lightrail tracks are mapped to tram stations, and vice-versa, therefore by default we allow both light rail and tram on light rail tracks */
      GLOBAL_MODE_ACCESS_DEFAULTS.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.TRAM, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.LIGHT_RAIL));
    }                
    
  }
      
  /**
   * for all countries for which a dedicated CSV is available under the resources dir, we parse their mode access defaults
   * accordingly
   * @throws PlanItException thrown if error
   */
  protected static void populateCountrySpecificDefaultModeAccess(){
    CountrySpecificDefaultUtils.callForEachFileInResourceDir(
        MODE_ACCESS_RESOURCE_DIR, OsmModeAccessDefaultsByCountry::populateCountrySpecificDefaultModeAccess);    
  } 
  
  /** Each file should be named according to ISO366 alpha 2 country code. The mode access defaults are parsed as CSV format and overwrite the 
   * global defaults for this country. If no explicit value is provided, we revert to the global defaults instead.
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
        boolean isOsmRailwayTag = false;
        if(!isOsmHighway) {
          isOsmRailwayTag = OsmRailwayTags.isRailBasedRailway(osmWayType);
          if(!isOsmRailwayTag) {
            LOGGER.warning(String.format("IGNORED: OSM way type (%s) in country specific mode access defaults is not a valid highway or railway type",osmWayType));      
          }
        }
      
        /* allowed OSM modes */
        List<String> allowedOsmModes = new ArrayList<String>(record.size()-1);
        for(int columnIndex = 1; columnIndex< record.size() ; ++columnIndex) {
          final String osmMode = record.get(columnIndex).trim();
          if(!StringUtils.isNullOrBlank(osmMode)) {
            allowedOsmModes.add(osmMode);
          }
        }
        
        /* register on defaults */
        if(!allowedOsmModes.isEmpty()) {
          /* copy the global defaults and make adjustments */
          OsmModeAccessDefaults countryDefaults = GLOBAL_MODE_ACCESS_DEFAULTS.clone();
          countryDefaults.setCountry(fullCountryName);          

          if(isOsmHighway) {
            countryDefaults.getHighwayModeAccessDefaults().setAllowedModes(osmWayType, false /* no logging */, allowedOsmModes);    
          }else {
            countryDefaults.getRailwayModeAccessDefaults().setAllowedModes(osmWayType, false /* no logging */, allowedOsmModes);
          }
      
          /* register country defaults */
          setDefaultsByCountry(countryDefaults);
        }
        
      }
      
    } catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Parsing of csv input stream with mode access defaults failed for %s", fullCountryName));
    }    
  }  
  
  /** set defaults for a specific county
   * @param countryName to set
   * @param modeAccessDefaults defaults to use
   */
  protected static void setDefaultsByCountry(OsmModeAccessDefaults modeAccessDefaults) {
    if(modeAccessDefaults.getCountry() != CountryNames.GLOBAL) {
      MODE_ACCESS_DEFAULTS_BY_COUNTRY.put(LocaleUtils.getIso2CountryCodeByName(modeAccessDefaults.getCountry()), modeAccessDefaults);
    }else {
      LOGGER.warning("setting OSM mode access defaults by country, then the defaults should have a country specified, this is not the case, defaults, ignored");
    }
  }    
  
  /**
   * Default factory method for creating global defaults
   */
  public static OsmModeAccessDefaults create() {
    OsmModeAccessDefaults theDefaults = null;
    try {
      theDefaults = GLOBAL_MODE_ACCESS_DEFAULTS.clone();
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to initialise global mode access defaults");
    } 
    return theDefaults;
  }
  
  /**
   * Factory method to create defaults based on a given country. If country cannot be found or cannot be converted
   * into ISO2 country code, we revert to global defaults
   */
  public static OsmModeAccessDefaults create(String countryName) {
    OsmModeAccessDefaults theDefaults = null;
    try {    
      String iso2CountryCode = LocaleUtils.getIso2CountryCodeByName(countryName);
      if(iso2CountryCode != null && MODE_ACCESS_DEFAULTS_BY_COUNTRY.containsKey(iso2CountryCode)) {
        theDefaults = MODE_ACCESS_DEFAULTS_BY_COUNTRY.get(iso2CountryCode).clone();
      }else {
        LOGGER.info("Reverting to global mode access defaults, rather than country specific ones");
        theDefaults = GLOBAL_MODE_ACCESS_DEFAULTS.clone();
      }
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("Unable to initialise global mode access defaults");
    }    
    return theDefaults;       
  }  

    
}

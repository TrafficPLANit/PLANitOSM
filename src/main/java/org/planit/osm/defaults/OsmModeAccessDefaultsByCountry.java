package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.tags.OsmHighwayTags;
import org.planit.osm.tags.OsmRailModeTags;
import org.planit.osm.tags.OsmRailwayTags;
import org.planit.osm.tags.OsmRoadModeCategoryTags;
import org.planit.osm.tags.OsmRoadModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;
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
 * </ul>
 * </p>
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
  private static final OsmModeAccessDefaults globalAllowedModeAccessDefaults = new OsmModeAccessDefaults();
  
  /** store all defaults per country by ISO2 code **/
  private static final Map<String,OsmModeAccessDefaults> allowedModeAccessDefaultsByCountry = new HashMap<String,OsmModeAccessDefaults>();
  
  /** store the global defaults as fall back option */
  protected static OsmModeAccessDefaults globalModeAccessDefaults;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmModeAccessDefaults> modeAccessDefaultsByCountry = new HashMap<String,OsmModeAccessDefaults>();  
   
  /* initialise */
  static {    
    try {
      /* global */
      populateGlobalDefaultHighwayModeAccess();
      populateGlobalDefaultRailwayModeAccess();
  
      /* country specific */
      populateAustralianDefaultModeAccess();
    } catch (PlanItException e) {
      LOGGER.severe(String.format("unable to populate default mode access because: %s", e.getMessage()));
    }    
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
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories( OsmHighwayTags.MOTORWAY, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultDisallowedModes(
          OsmHighwayTags.MOTORWAY, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);      
    }  
    
    /* MOTORWAY_LINK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories( OsmHighwayTags.MOTORWAY_LINK, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultDisallowedModes(
          OsmHighwayTags.MOTORWAY_LINK, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);      
    }  
    
    /* TRUNK ---- ROAD All the same setup with:
     * 
     * - allow all modes of category VEHICLE
     * - and allow pedestrian, i.e., FOOT  
     */
    
    /* TRUNK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TRUNK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TRUNK, OsmRoadModeTags.FOOT);      
    }      
    /* TRUNK_LINK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TRUNK_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TRUNK_LINK, OsmRoadModeTags.FOOT);    
    }  
    /* PRIMARY */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.PRIMARY, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PRIMARY, OsmRoadModeTags.FOOT);      
    }      
    /* PRIMARY_LINK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.PRIMARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PRIMARY_LINK, OsmRoadModeTags.FOOT);    
    }   
    /* SECONDARY */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.SECONDARY, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.SECONDARY, OsmRoadModeTags.FOOT);      
    }      
    /* SECONDARY_LINK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.SECONDARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.SECONDARY_LINK, OsmRoadModeTags.FOOT);    
    }
    /* TERTIARY */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TERTIARY, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TERTIARY, OsmRoadModeTags.FOOT);      
    }      
    /* TERTIARY_LINK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TERTIARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TERTIARY_LINK, OsmRoadModeTags.FOOT);    
    }
    /* UNCLASSIFIED */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.UNCLASSIFIED, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.UNCLASSIFIED, OsmRoadModeTags.FOOT);      
    }      
    /* RESIDENTIAL */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.RESIDENTIAL, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.RESIDENTIAL, OsmRoadModeTags.FOOT);    
    }
    /* LIVING_STREET */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.LIVING_STREET, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.LIVING_STREET, OsmRoadModeTags.FOOT);      
    }      
    /* ROAD */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.ROAD, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.ROAD, OsmRoadModeTags.FOOT);    
    }    
    /* SERVICE */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.SERVICE, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.SERVICE, OsmRoadModeTags.FOOT);    
    }     
    /* TRACK */
    {
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModeCategories(OsmHighwayTags.TRACK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.TRACK, OsmRoadModeTags.FOOT);    
    }     
    
    /* PEDESTRIAN */
    {
      /* pedestrian basically only allows foot based modes */
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG);    
    }
    
    /* STEPS (same as PEDESTRIAN)*/
    {
      /* steps only allows foot based modes */
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.STEPS, OsmRoadModeTags.FOOT, OsmRoadModeTags.DOG);    
    }    
    
    /* PATH */
    {
      /* a path only allows single track non_vehicular modes */
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PATH, 
          OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG, OsmRoadModeTags.HORSE, OsmRoadModeTags.BICYCLE);    
    }  
    
    /* BRIDLEWAY */
    {
      /* a bridleway is for horses */
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmRoadModeTags.HORSE);    
    }
    
    /* CYCLEWAY*/
    {
      /* a cycleway is for bicycles */
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.CYCLEWAY, OsmRoadModeTags.BICYCLE);    
    }
    
    /* FOOTWAY*/
    {
      /* same as pedestrian (only designated in SOM but we do not make this distinction */
      globalAllowedModeAccessDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.FOOTWAY, OsmRoadModeTags.FOOT);    
    }         
  }
  
  /**
   * populate the global defaults for railway types
   */
  protected static void populateGlobalDefaultRailwayModeAccess() {
    
    /* FUNICULAR */
    {
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.FUNICULAR, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.FUNICULAR));
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.LIGHT_RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.LIGHT_RAIL));
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.MONO_RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.MONO_RAIL));
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.NARROW_GAUGE, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.NARROW_GAUGE));
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.RAIL, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.RAIL));
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.SUBWAY, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.SUBWAY));
      globalAllowedModeAccessDefaults.getRailwayModeAccessDefaults().addDefaultAllowedModes(OsmRailwayTags.TRAM, OsmRailModeTags.convertRailwayToMode(OsmRailwayTags.TRAM));
    }
    
    
  }
  
  /** set defaults for a specific county
   * @param countryName to set
   * @param modeAccessDefaults defaults to use
   */
  protected static void setDefaultsByCountry(OsmModeAccessDefaults modeAccessDefaults) {
    if(modeAccessDefaults.getCountry() != CountryNames.GLOBAL) {
      allowedModeAccessDefaultsByCountry.put(LocaleUtils.getIso2CountryCodeByName(modeAccessDefaults.getCountry()), modeAccessDefaults);
    }else {
      LOGGER.warning("setting OSM mode access defaults by country, then the defaults should have a country specified, this is not the case, defaults, ignored");
    }
  }    

  /**
   * populate the Australian mode access defaults
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianDefaultModeAccess() throws PlanItException {    
    try {    
      /* copy the global defaults and make adjustments */
      OsmModeAccessDefaults australiaDefaults = globalAllowedModeAccessDefaults.clone();
      australiaDefaults.setCountry(CountryNames.AUSTRALIA);
      
      /* differences:
       * - pedestrian also has access to bicycle
       * - bridleway has access to bicycle and foot
       * - cycleway has access to foot
       */    
      australiaDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmRoadModeTags.BICYCLE);
      australiaDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmRoadModeTags.BICYCLE, OsmRoadModeTags.FOOT);
      australiaDefaults.getHighwayModeAccessDefaults().addDefaultAllowedModes(OsmHighwayTags.CYCLEWAY, OsmRoadModeTags.FOOT);
      
      setDefaultsByCountry(australiaDefaults);    
    } catch (CloneNotSupportedException e) {
      LOGGER.severe("cloning of global mode access settings failed");
      throw new PlanItException("unable to populate Australian default mode access");
    }       
  }
  
  /**
   * Default factory method for creating global defaults
   */
  public static OsmModeAccessDefaults create() {
    OsmModeAccessDefaults theDefaults = null;
    try {
      theDefaults = globalAllowedModeAccessDefaults.clone();
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
      if(iso2CountryCode != null && allowedModeAccessDefaultsByCountry.containsKey(iso2CountryCode)) {
        theDefaults = allowedModeAccessDefaultsByCountry.get(iso2CountryCode).clone();
      }else {
        theDefaults = globalAllowedModeAccessDefaults.clone();
      }
    }catch (Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe("unable to initialise global mode access defaults");
    }    
    return theDefaults;       
  }  

    
}

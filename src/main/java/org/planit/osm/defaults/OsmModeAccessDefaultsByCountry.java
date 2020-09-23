package org.planit.osm.defaults;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmRoadModeCategoryTags;
import org.planit.osm.util.OsmRoadModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;
import org.planit.utils.mode.Mode;

/**
 * The defaults for the mode access in case no specific restrictions are indicated on the highway=type way.
 * Based on the information provided on https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access_restrictions
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
   
  /* initialise */
  static {    
    try {
      /* global */
      populateGlobalDefaultModeAccess();
  
      /* country specific */
      populateAustralianDefaultModeAccess();
    } catch (PlanItException e) {
      LOGGER.severe(String.format("unable to populate default mode access because: %s", e.getMessage()));
    }    
  }
  
  /** store the global defaults as fall back option */
  protected static OsmModeAccessDefaults globalModeAccessDefaults;
  
  /** store all defaults per country by ISO2 code **/
  protected static Map<String, OsmModeAccessDefaults> modeAccessDefaultsByCountry = new HashMap<String,OsmModeAccessDefaults>();
  
  /**
   * populate the global defaults
   */
  protected static void populateGlobalDefaultModeAccess() {
    
    /* MOTORWAY and MOTORWAY_LINK --- the same setup with:
     * 
     * - allow all modes of category MOTOR_VEHICLE
     * - disallow slow motorcycles (MOPED, MOFA) and other exotic modes (ATV, GOLF_CART)  
     */
    
    /* MOTORWAY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories( OsmHighwayTags.MOTORWAY, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      globalAllowedModeAccessDefaults.addDisallowedModes(
          OsmHighwayTags.MOTORWAY, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);      
    }  
    
    /* MOTORWAY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories( OsmHighwayTags.MOTORWAY_LINK, OsmRoadModeCategoryTags.MOTOR_VEHICLE);
      globalAllowedModeAccessDefaults.addDisallowedModes(
          OsmHighwayTags.MOTORWAY_LINK, OsmRoadModeTags.MOPED, OsmRoadModeTags.MOFA, OsmRoadModeTags.ATV, OsmRoadModeTags.GOLF_CART);      
    }  
    
    /* TRUNK ---- ROAD All the same setup with:
     * 
     * - allow all modes of category VEHICLE
     * - and allow pedestrian, i.e., FOOT  
     */
    
    /* TRUNK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TRUNK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TRUNK, OsmRoadModeTags.FOOT);      
    }      
    /* TRUNK_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TRUNK_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TRUNK_LINK, OsmRoadModeTags.FOOT);    
    }  
    /* PRIMARY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.PRIMARY, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PRIMARY, OsmRoadModeTags.FOOT);      
    }      
    /* PRIMARY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.PRIMARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PRIMARY_LINK, OsmRoadModeTags.FOOT);    
    }   
    /* SECONDARY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.SECONDARY, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.SECONDARY, OsmRoadModeTags.FOOT);      
    }      
    /* SECONDARY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.SECONDARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.SECONDARY_LINK, OsmRoadModeTags.FOOT);    
    }
    /* TERTIARY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TERTIARY, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TERTIARY, OsmRoadModeTags.FOOT);      
    }      
    /* TERTIARY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TERTIARY_LINK, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TERTIARY_LINK, OsmRoadModeTags.FOOT);    
    }
    /* UNCLASSIFIED */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.UNCLASSIFIED, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.UNCLASSIFIED, OsmRoadModeTags.FOOT);      
    }      
    /* RESIDENTIAL */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.RESIDENTIAL, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.RESIDENTIAL, OsmRoadModeTags.FOOT);    
    }
    /* LIVING_STREET */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.LIVING_STREET, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.LIVING_STREET, OsmRoadModeTags.FOOT);      
    }      
    /* ROAD */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.ROAD, OsmRoadModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.ROAD, OsmRoadModeTags.FOOT);    
    }    
    
    /* PEDESTRIAN */
    {
      /* pedestrian basically only allows foot based modes */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG);    
    }
    
    /* PATH */
    {
      /* a path only allows single track non_vehicular modes */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PATH, 
          OsmRoadModeTags.FOOT,OsmRoadModeTags.DOG, OsmRoadModeTags.HORSE, OsmRoadModeTags.BICYCLE);    
    }  
    
    /* BRIDLEWAY */
    {
      /* a bridleway is for horses */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmRoadModeTags.HORSE);    
    }
    
    /* CYCLEWAY*/
    {
      /* a cycleway is for bicycles */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.CYCLEWAY, OsmRoadModeTags.BICYCLE);    
    }
    
    /* FOOTWAY*/
    {
      /* same as pedestrian (only designated in SOM but we do not make this distinction */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.FOOTWAY, OsmRoadModeTags.FOOT);    
    }         
  }
  
  /** set defaults for a specific county
   * @param countryName to set
   * @param modeAccessDefaults defaults to use
   */
  protected static void setDefaultsByCountry(String countryName, OsmModeAccessDefaults modeAccessDefaults) {
    allowedModeAccessDefaultsByCountry.put(LocaleUtils.getIso2CountryCodeByName(countryName), modeAccessDefaults);
  }    

  /**
   * populate the Australian mode access defaults
   * @throws PlanItException thrown if error
   */
  protected static void populateAustralianDefaultModeAccess() throws PlanItException {    
    try {    
      /* copy the global defaults and make adjustments */
      OsmModeAccessDefaults australiaDefaults = globalAllowedModeAccessDefaults.clone();
      
      /* differences:
       * - pedestrian also has access to bicycle
       * - bridleway has access to bicycle and foot
       * - cycleway has access to foot
       */    
      australiaDefaults.addAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmRoadModeTags.BICYCLE);
      australiaDefaults.addAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmRoadModeTags.BICYCLE, OsmRoadModeTags.FOOT);
      australiaDefaults.addAllowedModes(OsmHighwayTags.CYCLEWAY, OsmRoadModeTags.FOOT);
      
      setDefaultsByCountry("Australia", australiaDefaults);    
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
   * Factory method to create defaults based on a given country. If country cannot be found or cannot be cnoverted
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

  
  /** collect all PLANit modes that are allowed for any highway type. Note this method creates
   * a new container every time it is invoked by searching the internal mode mapping settings.
   * 
   * TODO: 
   * @param osmHighwayTypeToUse
   * @return
   */
  public Map<String, Collection<Mode> > collectAllowedPlanitModesByHighwayType(String osmHighwayTypeToUse) {
    // TODO Auto-generated method stub
    return null;
  }
  
}

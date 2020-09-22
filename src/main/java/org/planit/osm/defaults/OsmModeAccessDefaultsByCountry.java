package org.planit.osm.defaults;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.planit.osm.util.OsmHighwayTags;
import org.planit.osm.util.OsmModeCategoryTags;
import org.planit.osm.util.OsmModeTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;

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
      globalAllowedModeAccessDefaults.addAllowedModeCategories( OsmHighwayTags.MOTORWAY, OsmModeCategoryTags.MOTOR_VEHICLE);
      globalAllowedModeAccessDefaults.addDisallowedModes(
          OsmHighwayTags.MOTORWAY, OsmModeTags.MOPED, OsmModeTags.MOFA, OsmModeTags.ATV, OsmModeTags.GOLF_CART);      
    }  
    
    /* MOTORWAY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories( OsmHighwayTags.MOTORWAY_LINK, OsmModeCategoryTags.MOTOR_VEHICLE);
      globalAllowedModeAccessDefaults.addDisallowedModes(
          OsmHighwayTags.MOTORWAY_LINK, OsmModeTags.MOPED, OsmModeTags.MOFA, OsmModeTags.ATV, OsmModeTags.GOLF_CART);      
    }  
    
    /* TRUNK ---- ROAD All the same setup with:
     * 
     * - allow all modes of category VEHICLE
     * - and allow pedestrian, i.e., FOOT  
     */
    
    /* TRUNK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TRUNK, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TRUNK, OsmModeTags.FOOT);      
    }      
    /* TRUNK_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TRUNK_LINK, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TRUNK_LINK, OsmModeTags.FOOT);    
    }  
    /* PRIMARY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.PRIMARY, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PRIMARY, OsmModeTags.FOOT);      
    }      
    /* PRIMARY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.PRIMARY_LINK, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PRIMARY_LINK, OsmModeTags.FOOT);    
    }   
    /* SECONDARY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.SECONDARY, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.SECONDARY, OsmModeTags.FOOT);      
    }      
    /* SECONDARY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.SECONDARY_LINK, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.SECONDARY_LINK, OsmModeTags.FOOT);    
    }
    /* TERTIARY */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TERTIARY, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TERTIARY, OsmModeTags.FOOT);      
    }      
    /* TERTIARY_LINK */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.TERTIARY_LINK, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.TERTIARY_LINK, OsmModeTags.FOOT);    
    }
    /* UNCLASSIFIED */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.UNCLASSIFIED, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.UNCLASSIFIED, OsmModeTags.FOOT);      
    }      
    /* RESIDENTIAL */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.RESIDENTIAL, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.RESIDENTIAL, OsmModeTags.FOOT);    
    }
    /* LIVING_STREET */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.LIVING_STREET, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.LIVING_STREET, OsmModeTags.FOOT);      
    }      
    /* ROAD */
    {
      globalAllowedModeAccessDefaults.addAllowedModeCategories(OsmHighwayTags.ROAD, OsmModeCategoryTags.VEHICLE);
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.ROAD, OsmModeTags.FOOT);    
    }    
    
    /* PEDESTRIAN */
    {
      /* pedestrian basically only allows foot based modes */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmModeTags.FOOT,OsmModeTags.DOG);    
    }
    
    /* PATH */
    {
      /* a path only allows single track non_vehicular modes */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.PATH, 
          OsmModeTags.FOOT,OsmModeTags.DOG, OsmModeTags.HORSE, OsmModeTags.BICYCLE);    
    }  
    
    /* BRIDLEWAY */
    {
      /* a bridleway is for horses */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmModeTags.HORSE);    
    }
    
    /* CYCLEWAY*/
    {
      /* a cycleway is for bicycles */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.CYCLEWAY, OsmModeTags.BICYCLE);    
    }
    
    /* FOOTWAY*/
    {
      /* same as pedestrian (only designated in SOM but we do not make this distinction */
      globalAllowedModeAccessDefaults.addAllowedModes(OsmHighwayTags.FOOTWAY, OsmModeTags.FOOT);    
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
      australiaDefaults.addAllowedModes(OsmHighwayTags.PEDESTRIAN, OsmModeTags.BICYCLE);
      australiaDefaults.addAllowedModes(OsmHighwayTags.BRIDLEWAY, OsmModeTags.BICYCLE, OsmModeTags.FOOT);
      australiaDefaults.addAllowedModes(OsmHighwayTags.CYCLEWAY, OsmModeTags.FOOT);
      
      setDefaultsByCountry("Australia", australiaDefaults);    
    } catch (CloneNotSupportedException e) {
      LOGGER.severe("cloning of global mode access settings failed");
      throw new PlanItException("unable to populate Australian default mode access");
    }       
  }

  
  
}

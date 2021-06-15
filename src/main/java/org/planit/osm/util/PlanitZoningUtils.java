package org.planit.osm.util;

import java.util.logging.Logger;
import org.planit.zoning.Zoning;

/**
 * Helper class for the OSM handlers with respect to the PLANit zoning, providing static helper methods to aid when populating the zoning
 * from an OSM data source 
 * 
 * @author markr
 *
 */
public class PlanitZoningUtils {
  
  /** the logger to use */
  public static final Logger LOGGER = Logger.getLogger(PlanitZoningUtils.class.getCanonicalName());                       
    
  /**
   * remove any dangling zones
   * 
   * @param zoning to remove them from
   */
  public static void removeDanglingZones(Zoning zoning) {
    /* delegate to zoning modifier */
    int originalNumberOfTransferZones = zoning.transferZones.size();
    zoning.getZoningModifier().removeDanglingZones();
    LOGGER.info(String.format("Removed dangling transfer zones, remaining number of zones %d (original: %d)", zoning.transferZones.size(), originalNumberOfTransferZones));
  }  
  
  /**
   * remove any dangling transfer zone groups
   * 
   * @param zoning to remove them from
   */  
  public static void removeDanglingTransferZoneGroups(Zoning zoning) {
    /* delegate to zoning modifier */
    int originalNumberOfTransferZoneGroups = zoning.transferZoneGroups.size();
    zoning.getZoningModifier().removeDanglingTransferZoneGroups();    
    LOGGER.info(String.format("Removed dangling transfer zone groups, remaining number of groups %d (original: %d)", zoning.transferZoneGroups.size(), originalNumberOfTransferZoneGroups));    
  }    
  
  
}

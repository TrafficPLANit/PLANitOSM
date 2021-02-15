package org.planit.osm.settings.zoning;

/**
 * Capture all the user configurable settings regarding how to
 * parse (if at all) (public transport) transfer infrastructure such as stations, poles, platforms, and other
 * stop and tranfer related infrastructure 
 * 
 * @author markr
 *
 */
public class PlanitOsmTransferSettings {
  
  /** flag indicating if the settings for this parser matter, by indicating if the parser for it is active or not */
  private boolean isParserActive = DEFAULT_TRANSFER_PARSER_ACTIVE;

  /** the maximum search radius used when trying to map stops/platforms to stop positions on the networks, when no explicit
   * relation is made known by OSM */
  protected double searchRadiusInMeters = DEFAULT_SEARCH_RADIUS_M;
  
  /** by default the transfer parser is deactivated */
  public static boolean DEFAULT_TRANSFER_PARSER_ACTIVE = false;
    
  /**
   * default search radius in meters for mapping stops/platforms to stop positions on the networks
   */
  public static double DEFAULT_SEARCH_RADIUS_M = 50;
  
  /** Constructor
  */
  public PlanitOsmTransferSettings() {
  }    
  
  /** set the flag whether or not the highways should be parsed or not
   * @param activate
   */
  public void activateParser(boolean activate) {
    this.isParserActive = activate;
  }  
  
  /** verifies if the parser for these settings is active or not
   * @return true if active false otherwise
   */
  public boolean isParserActive() {
    return this.isParserActive;
  }   
  
  /** Set the maximum search radius used when trying to map stops/platforms to stop positions on the networks, 
   * when no explicit relation is made known by OSM 
   * 
   * @param searchRadiusInMeters to use
   */
  public void setStopToWaitingAreaSearchRadiusMeters(double searchRadiusInMeters) {
    this.searchRadiusInMeters = searchRadiusInMeters;
  }
  
  /** Collect the maximum search radius set when trying to map stops/platforms to stop positions on the networks, 
   * when no explicit relation is made known by OSM 
   * 
   * @return search radius in meters
   */
  public double getStopToWaitingAreaSearchRadiusMeters() {
    return this.searchRadiusInMeters;
  }  

}

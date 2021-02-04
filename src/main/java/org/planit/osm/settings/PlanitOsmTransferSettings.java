package org.planit.osm.settings;

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
  
  /** by default the transfer parser is deactivated */
  public static boolean DEFAULT_TRANSFER_PARSER_ACTIVE = false;
  
  /** Constructor
  */
  protected PlanitOsmTransferSettings() {
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

}

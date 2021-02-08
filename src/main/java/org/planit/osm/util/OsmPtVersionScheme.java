package org.planit.osm.util;

/**
 * Enum highlighting the different (supported) public transport tagging schemes
 * 
 * @author markr
 *
 */
public enum OsmPtVersionScheme {

  VERSION_1("Ptv1"),
  VERSION_2("Ptv2"),
  NONE("none");
  
  
  /**
   * the value
   */
  private final String value;
  
  /** Colect the value
   * @return value
   */
  public String value() {
    return value;
  }
  
  /** Constructor
   * @param value representation of enum
   */
  OsmPtVersionScheme(String value){
    this.value = value;
  }
}

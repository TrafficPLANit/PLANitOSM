package org.planit.osm.util;

/**
 * Enum highlighting the different (supported) public transport tagging schemes
 * 
 * @author markr
 *
 */
public enum OsmPtVersionScheme {

  v1("Ptv1"),
  v2("Ptv2");
  
  
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

package org.goplanit.osm.util;

/**
 * Enum highlighting the different (supported) public transport tagging schemes
 * 
 * @author markr
 *
 */
public enum OsmPtVersionScheme {

  /** the first public transport tagging scheme, Ptv1 */
  VERSION_1("Ptv1"),
  /** the second public transport tagging scheme, Ptv2, of stop positions, platforms and stop areas */
  VERSION_2("Ptv2"),
  /** no public transport tagging scheme */
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

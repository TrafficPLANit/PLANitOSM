package org.planit.osm.reader;

import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.planit.geo.PlanitGeoUtils;
import org.planit.osm.util.PlanitOSMTags;

/**
 * Settings for the OSM reader
 * 
 * @author markr
 *
 */
public class PLANitOSMReaderSettings {
    
  /* the crs of the OSM source */
  protected CoordinateReferenceSystem sourceCRS = PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS;
  
  /**
   * When the parsed way has a type that is not supported, this alternative will be used, default is
   * set to PlanitOSMTags.TERTIARY
   */
  protected String defaultOSMHighwayValueWhenUnsupported = PlanitOSMTags.TERTIARY;
    
  public PLANitOSMReaderSettings() {
  }   

  /**
   * chosen crs, default is {@code PlanitGeoUtils.DEFAULT_GEOGRAPHIC_CRS}
   * @return
   */
  public final CoordinateReferenceSystem getSourceCRS() {
    return sourceCRS;
  }

  /**
   * Override source CRS
   * 
   * @param sourceCRS
   */
  public void setSourceCRS(final CoordinateReferenceSystem sourceCRS) {
    this.sourceCRS = sourceCRS;
  }
  
  /**
   * 
   * When the parsed way has a type that is not supported, this alternative will be used
   * @return chosen default
   * 
   **/
  public final String getDefaultOSMHighwayValueWhenUnsupported() {
    return defaultOSMHighwayValueWhenUnsupported;
  }

  /**
   * set the default to be used when we encounter an unsupported type.
   * 
   * @param defaultOSMHighwayValueWhenUnsupported the default to use, should be a type that is supported.
   */
  public void setDefaultOSMHighwayValueWhenUnsupported(String defaultOSMHighwayValueWhenUnsupported) {
    this.defaultOSMHighwayValueWhenUnsupported = defaultOSMHighwayValueWhenUnsupported;
  }  
    

}

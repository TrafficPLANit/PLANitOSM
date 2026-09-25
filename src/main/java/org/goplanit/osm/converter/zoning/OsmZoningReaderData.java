package org.goplanit.osm.converter.zoning;

import java.util.logging.Logger;

import org.goplanit.converter.zoning.AccessEgressInjectionSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.zoning.Zoning;

/**
 * Data specifically required in the zoning reader while parsing OSM data
 * 
 * @author markr
 *
 */
public class OsmZoningReaderData {
  
  /** logeger to use */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmZoningReaderData.class.getCanonicalName());
  
  /** the country name, used for geographic mapping that depends on driving direction on the infrastructure */
  private final String countryName;  
  
  /* UNPROCESSED OSM */
  
  /* PLANit entity related tracking during parsing */
  protected final OsmZoningReaderPlanitConverterData planitData;
  
  /* OSM entity related tracking during parsing */
  protected final OsmZoningReaderOsmData osmConverterData = new OsmZoningReaderOsmData();

  /** the osmBoundary used during parsing.
   */
  private OsmBoundary osmBoundingArea = null;

  /**
   * Constructor using country set to GLOBAL (right hand drive)
   *
   * @param network to use
   * @param zoning to use
   */
  public OsmZoningReaderData(MacroscopicNetwork network, Zoning zoning) {
    this(network, zoning, CountryNames.GLOBAL);
  }  
  
  /** Constructor
   *
   * @param network to use
   * @param zoning to use
   * @param countryName for this zoning
   */
  public OsmZoningReaderData(MacroscopicNetwork network, Zoning zoning, String countryName) {
    this.countryName = countryName;
    this.planitData = new OsmZoningReaderPlanitConverterData(network, zoning);
  }
  
  /** Collect the country name
   * 
   * @return country name
   */
  public String getCountryName() {
    return countryName;
  }  

  /**
   * reset the handler
   */
  public void reset() {
    planitData.reset();
    osmConverterData.reset();
    osmBoundingArea = null;
  }

  /** collect the planit related tracking data 
   * 
   * @return planit data
   */
  public OsmZoningReaderPlanitConverterData getPlanitConverterData() {
    return planitData;
  }
  
  /** collect the OSM related tracking data 
   * 
   * @return osm data
   */
  public OsmZoningReaderOsmData getOsmConverterData() {
    return osmConverterData;
  }

  /** get the bounding area
   *
   * @return bounding area
   */
  public OsmBoundary getBoundingArea(){
    return osmBoundingArea;
  }

  /**
   * Set the bounding area to use
   *
   * @param osmBoundingArea to use
   */
  public void setBoundingArea(OsmBoundary osmBoundingArea){
    this.osmBoundingArea = osmBoundingArea;
  }

  /**
   * Check if zoning has a bounding boundary area set
   *
   * @return true if present, false otherwise
   */
  public boolean hasBoundingArea() {
    return getBoundingArea() != null;
  }

  /** ugly way to get to internal settings based on OSMPtSettings
   *
   * @param ptSettings to get access to
   * @return access to internal accessEgressSettings
   */
  public static AccessEgressInjectionSettings getAccessEgressInjectionSettingsFrom(
      OsmPublicTransportReaderSettings ptSettings) {
    return ptSettings.accessEgressInjectionSettings;
  }
}

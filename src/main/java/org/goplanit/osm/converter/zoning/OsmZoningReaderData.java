package org.goplanit.osm.converter.zoning;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.utils.locale.CountryNames;

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
  OsmZoningReaderPlanitData planitData = new OsmZoningReaderPlanitData();
  
  /* OSM entity related tracking during parsing */
  OsmZoningReaderOsmData osmData = new OsmZoningReaderOsmData();

  /** the osmBoundary used during parsing.
   */
  private OsmBoundary osmBoundingArea = null;

  /** Track OSM ways that are deemed eligible for parsing (after pre-processing). This is needed because we can't rely on nodes
   * being available as the way to do this since nodes may be shared between OSM ways and while on of the shared ways is
   * eligible another might not be. Hence the separate eligibility tracking
   */
  private final Set<Long> spatialInfrastructureEligibleOsmWays = new HashSet<>();
  
  /**
   * Default constructor using country set to GLOBAL (right hand drive)
   */
  public OsmZoningReaderData() {
    this(CountryNames.GLOBAL);
  }  
  
  /** Constructor 
   * @param countryName for this zoning
   */
  public OsmZoningReaderData(String countryName) {
    this.countryName = countryName;
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
    osmData.reset();
    osmBoundingArea = null;
    spatialInfrastructureEligibleOsmWays.clear();
  }

  /** collect the planit related tracking data 
   * 
   * @return planit data
   */
  public OsmZoningReaderPlanitData getPlanitData() {
    return planitData;
  }
  
  /** collect the OSM related tracking data 
   * 
   * @return osm data
   */
  public OsmZoningReaderOsmData getOsmData() {
    return osmData;
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
   * @return ture if present, false otherwise
   */
  public boolean hasBoundingArea() {
    return getBoundingArea() != null;
  }

  /**
   * Track all OSMWays that have been identified as being spatially eligible PT infrastructure for parsing as part of
   * the zoning, i.e., they are infrastructure that is being parsed and they fall within the area configured to be
   * produced even if the input file or data spans a larger area
   *
   * @param osmWayId OSM way id to mark as eligible
   */
  public void registerSpatialInfraEligibleOsmWayId(long osmWayId) {
    spatialInfrastructureEligibleOsmWays.add(osmWayId);
  }

  /**
   * Verify if OSMWay is identified as being spatially eligible for parsing as part of the zoning, i.e.,
   * it falls within the area deemed suitable for the final result.
   *
   * @param osmWayId OSM way id to mark as eligible
   * @return true when eligible, false otherwise
   */
  public boolean isSpatialInfraEligibleOsmWay(long osmWayId) {
    return spatialInfrastructureEligibleOsmWays.contains(osmWayId);
  }

  /**
   * how many eligible OSM ways have been registered to date
   * @return count
   */
  public int getNumSpatialInfraEligibleOsmWays() {
    return spatialInfrastructureEligibleOsmWays.size();
  }
}

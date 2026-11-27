package org.goplanit.osm.converter.intermodal;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.osm.converter.network.settings.OsmNetworkReaderSettings;
import org.goplanit.osm.converter.settings.OsmReaderSettingsSchema;
import org.goplanit.osm.converter.settings.OsmReaderYamlKeyedSettings;
import org.goplanit.osm.converter.zoning.OsmPublicTransportReaderSettings;
import org.goplanit.utils.exceptions.PlanItRunTimeException;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.settings.SettingsSchema;
import org.goplanit.utils.settings.YamlWriteSettingsSupport;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;
import org.goplanit.utils.settings.annotations.SettingsTopLevelValue;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Capture all the user configurable settings regarding the OSM intermodal reader, which in turn has a network
 * and zoning reader. Hence, these settings provide access to OSM network and zoning reader settings
 * 
 * @author markr
 *
 */
public class OsmIntermodalReaderSettings implements ConverterReaderSettings, YamlWriteSettingsSupport {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(OsmIntermodalReaderSettings.class.getCanonicalName());

  /** Switch on the railway and waterway parsers, which intermodal reading uses for rail and ferry services */
  private void activateRailwayAndWaterwayParsers() {
    getNetworkSettings().getRailwaySettings().activateParser(true);
    getNetworkSettings().getWaterwaySettings().activateParser(true);
  }

  /** the network settings to use */
  protected final OsmNetworkReaderSettings networkSettings;
  
  /** the zoning PT settings to use */
  protected final OsmPublicTransportReaderSettings zoningPtSettings;

  /**
   * Constructor without a country, so the defaults are the global ones
   */
  public OsmIntermodalReaderSettings() {
    this(CountryNames.GLOBAL);
  }

  /**
   * Constructor
   *
   * @param countryName to use
   */
  public OsmIntermodalReaderSettings(
      @SettingsTopLevelValue(key = OsmReaderYamlKeyedSettings.COUNTRY_KEY,
          description = OsmReaderYamlKeyedSettings.COUNTRY_DESCRIPTION)
      final String countryName) {
    this((URL) null, countryName);
  }

  /**
   * Constructor
   *
   * @param inputSource to use
   * @param countryName to use
   */
  public OsmIntermodalReaderSettings(final String inputSource, final String countryName) {
    this(UrlUtils.createFrom(inputSource), countryName);
  }

  /**
   * Constructor
   * 
   * @param inputSource to use
   * @param countryName to use
   */
  public OsmIntermodalReaderSettings(final URL inputSource, final String countryName) {
    this(
        new OsmNetworkReaderSettings(inputSource, countryName),
        new OsmPublicTransportReaderSettings(inputSource, countryName));
    activateRailwayAndWaterwayParsers();
  }
         
  
  /**
   * Constructor
   * 
   * @param networkSettings to use
   * @param zoningPtSettings to use
   */
  public OsmIntermodalReaderSettings(
      final OsmNetworkReaderSettings networkSettings, final OsmPublicTransportReaderSettings zoningPtSettings) {
    this.networkSettings = networkSettings;
    this.zoningPtSettings = zoningPtSettings;
  } 
  

  /**
   * Reset each of the nested settings, then switch the railway and waterway parsers on again, as intermodal settings
   * made new have them
   */
  @Override
  public void reset() {
    networkSettings.reset();
    zoningPtSettings.reset();
    activateRailwayAndWaterwayParsers();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("OSM Intermodal Reader Settings"));
    networkSettings.logSettings(level+1);
    zoningPtSettings.logSettings(level+1);
  }

  /**
   * {@inheritDoc}
   *
   * @return the schema of every OSM reader settings source, whose network and public transport sections these settings
   *     hold
   */
  @Override
  public SettingsSchema getSettingsSchema() {
    return OsmReaderSettingsSchema.SCHEMA;
  }

  // GETTERS/SETTERS
  
  /** Provide access to the network reader settings
   * 
   * @return network reader settings
   */
  public OsmNetworkReaderSettings getNetworkSettings() {
    return networkSettings;
  }
  
  /** Provide access to the zoning (pt)  reader settings
   * 
   * @return zoning reader pt settings
   */
  public OsmPublicTransportReaderSettings getPublicTransportSettings() {
    return zoningPtSettings;
  }  
  
  /** Set the inputSource  to use including for both the network and public transport settings
   * (both should use the same source)
   * 
   * @param inputSource to use
   */
  @CoveredBySettingsKey("inputSource")
  public void setInputSource(final URL inputSource) {
    getNetworkSettings().setInputSource(inputSource);
    getPublicTransportSettings().setInputSource(inputSource);
  }  
  
  /** Set the input file to use, which is internally converted into a URL
   * @param inputFile to use
   */
  @CoveredBySettingsKey("inputSource")
  public void setInputFile(final String inputFile) {
    try{
      setInputSource(UrlUtils.createFromLocalAbsoluteOrRelativePath(inputFile));
    }catch(Exception e) {
      throw new PlanItRunTimeException("Unable to extract URL from input file location %s",inputFile);
    }
  }     

  /** Set a polygon based bounding box to restrict parsing to
   * @param osmBoundary to use
   */
  @CoveredBySettingsKey("boundingArea")
  public final void setBoundingArea(OsmBoundary osmBoundary) {
    getNetworkSettings().setBoundingArea(osmBoundary.deepClone());
    getPublicTransportSettings().setBoundingArea(osmBoundary.deepClone());
  }   
  
}

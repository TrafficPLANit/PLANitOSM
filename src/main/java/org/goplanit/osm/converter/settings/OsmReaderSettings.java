package org.goplanit.osm.converter.settings;

import java.net.URL;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.locale.CountryNames;
import org.goplanit.utils.misc.UrlUtils;
import org.goplanit.utils.settings.SettingsSchema;
import org.goplanit.utils.settings.YamlWriteSettingsSupport;
import org.goplanit.utils.settings.annotations.CoveredBySettingsKey;

/**
 * Settings every OSM reader takes: the input, the country its defaults come from, the area to read, and whether OSM
 * tags are kept
 * <p>
 * The settings of each OSM reader extend them, so these are given the same way for every reader. Those filling a
 * section of an OSM settings source ({@link OsmReaderSettingsSchema}) share the keys at its top level, which are on
 * {@link OsmReaderYamlKeyedSettings}.
 * </p>
 *
 * @author markr
 *
 */
public abstract class OsmReaderSettings extends OsmReaderYamlKeyedSettings
    implements ConverterReaderSettings, YamlWriteSettingsSupport {

  /**
   * Default constructor with default locale (Global)
   */
  public OsmReaderSettings() {
    this((URL)null, CountryNames.GLOBAL);
  }
  
  /**
   * Constructor
   * 
   *  @param countryName to use
   */
  public OsmReaderSettings(final String countryName) {
    this((URL) null, countryName);
  }

  /**
   * Constructor
   *
   *  @param inputSource to use
   *  @param countryName to use
   */
  public OsmReaderSettings(final String inputSource, final String countryName) {
    this((URL) null, countryName);
    setInputSource(inputSource);
  }

  /**
   * Constructor
   * 
   *  @param inputSource to use
   *  @param countryName to use
   */
  public OsmReaderSettings(final URL inputSource, final String countryName) {
    super(inputSource, countryName);
  }

  /** Set the input file to use, which is internally converted into a URL
   * 
   * @param inputFile to use
   * @throws PlanItException thrown if error
   */
  @CoveredBySettingsKey("inputSource")
  public void setInputFile(final String inputFile) throws PlanItException {
    try{
      setInputSource(UrlUtils.createFromLocalAbsoluteOrRelativePath(inputFile));
    }catch(Exception e) {
      throw new PlanItException("Unable to extract URL from input file location %s",inputFile);
    }
  }    
  
  /**
   * {@inheritDoc}
   *
   * @return the schema of every OSM reader settings source
   */
  @Override
  public SettingsSchema getSettingsSchema() {
    return OsmReaderSettingsSchema.SCHEMA;
  }

  /** Set a polygon based bounding box to restrict parsing to
   *
   * @return boundingPolygon used, can be null
   */
  public final boolean hasBoundingBoundary() {
    return getBoundingArea() != null;
  }
}


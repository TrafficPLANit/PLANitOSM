package org.goplanit.osm.converter.settings;

import java.net.URL;

import org.goplanit.osm.converter.OsmBoundary;
import org.goplanit.utils.settings.annotations.SettingsHeader;
import org.goplanit.utils.settings.annotations.SettingsKeyLevel;
import org.goplanit.utils.settings.annotations.SettingsRead;
import org.goplanit.utils.settings.annotations.SettingsWrite;

/**
 * The keys at the root of an OSM settings file, which every OSM reader takes, and the country the constructors of the
 * readers' settings take: only the methods of its keys, each write with the read of the same key, and the read of the
 * country. {@link OsmReaderSettings} extends it with what code needs on top.
 * <p>
 * The network and the public transport settings both extend {@link OsmReaderSettings}, so a key here is given once, at
 * the root of a file under the header this class gives, and applies to both: the input source, the area to read and
 * whether OSM tags are kept, which an intermodal reader needs the same for its network and its public transport
 * </p>
 *
 * @author markr
 */
@SettingsHeader(key = "planitOsmConverterSettings", version = 1)
public abstract class OsmReaderYamlKeyedSettings {

  /** OsmBoundary to apply, if null no restriction is applied */
  private OsmBoundary osmBoundary = null;

  /** country name to use to initialise OSM defaults for */
  private final String countryName;

  /** input source to use */
  private URL inputSource;

  /**
   * Flag to indicate if OSM tags are to be retained as is as part of parsing for relevant
   * entities, e.g., links, nodes.
   */
  private boolean retainOsmTags = DEFAULT_RETAIN_OSM_TAGS;

  /**
   * Constructor
   *
   * @param inputSource to use, null when not known yet
   * @param countryName whose defaults apply
   */
  protected OsmReaderYamlKeyedSettings(final URL inputSource, final String countryName) {
    this.inputSource = inputSource;
    this.countryName = countryName;
  }

  /** key at the root of a settings source giving the country, which the constructors of the readers' settings take */
  public static final String COUNTRY_KEY = "country";

  /** what the country key sets, written above it in a settings file, given by the constructors that take it */
  public static final String COUNTRY_DESCRIPTION =
      "The country whose defaults apply, by its full English name, such as Australia";

  /** the OSM tags are not kept on the PLANit entities unless asked for */
  public static final boolean DEFAULT_RETAIN_OSM_TAGS = false;

  /** The country name used to initialise OSM defaults for
   *
   * @return country name
   */
  @SettingsRead(key = COUNTRY_KEY)
  public final String getCountryName() {
    return this.countryName;
  }

  /**
   * boundary to restrict parsing to
   *
   * @param osmBoundary to apply
   */
  @SettingsWrite(key = "boundingArea", level = SettingsKeyLevel.STANDARD,
      description = "The area to read, exactly one of: box (west, east, south and north, in degrees), polygon (as " +
          "WKT, in degrees of longitude and latitude) or osmBoundary (the name, and optionally type and adminLevel, " +
          "of an OSM boundary)")
  public void setBoundingArea(final OsmBoundary osmBoundary){
    this.osmBoundary = osmBoundary;
  }

  /**
   * The OsmBoundary configured by the user
   *
   * @return osmBoundary
   */
  @SettingsRead(key = "boundingArea")
  public OsmBoundary getBoundingArea(){
    return this.osmBoundary;
  }

  /** The input source used
   *
   * @return input source used
   */
  @SettingsRead(key = "inputSource")
  public final URL getInputSource() {
    return this.inputSource;
  }

  /** Set the input source  to use
   *
   * @param inputSource to use
   */
  @SettingsWrite(key = "inputSource", level = SettingsKeyLevel.STANDARD,
      description = "The OSM file to read, such as data/sydney.osm.pbf, as a path relative to this settings file or " +
          "a URL")
  public void setInputSource(final URL inputSource) {
    this.inputSource = inputSource;
  }

  /**
   * Flag to indicate if OSM tags are retained as is as part of parsing for relevant
   * entities, e.g., links, nodes.
   *
   * @return flag
   */
  @SettingsRead(key = "retainOsmTags")
  public boolean isRetainOsmTags() {
    return retainOsmTags;
  }

  /**
   * Flag to indicate if OSM tags are to be retained as is as part of parsing for relevant
   * entities, e.g., links, nodes.
   *
   * @param retainOsmTags flag to set
   */
  @SettingsWrite(key = "retainOsmTags", level = SettingsKeyLevel.UNCOMMON,
      description = "Keep the OSM tags of each way and node on the links and nodes made from it")
  public void setRetainOsmTags(boolean retainOsmTags) {
    this.retainOsmTags = retainOsmTags;
  }
}

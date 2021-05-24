package org.planit.osm.defaults;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.locale.LocaleUtils;
import org.planit.utils.misc.FileUtils;
import org.planit.utils.misc.StringUtils;
import org.planit.utils.resource.ResourceUtils;

/** Class with some common functionality for defaults that are country specific and
 * stored in CSV files
 * 
 * @author markr
 *
 */
public class CountrySpecificDefaultUtils {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(CountrySpecificDefaultUtils.class.getCanonicalName());

  
  /** call consumer for each country specific file in resource dir
   * @param resourceDir to use
   * @param callBack to use
   */
  public static void callForEachFileInResourceDir(final String resourceDir, final Consumer<File> callBack) {
    /* country specific based on resource files*/
    URL url = ResourceUtils.getResourceUrl(resourceDir);
    FileUtils.callForEachFileIn(url.getPath().toString(), callBack);
  }  
  
  /** Validate if file is a valid resource file and its name is constructed based on a country code. 
   * Each file should be named according to ISO366 alpha 2 country code. If valid the full country name
   * is returned 
   * 
   * @param file to validate
   * @param descriptionOfDefaults to use if an exception if thrown
   * @return full country name, otherwise null
   * @throws PlanItException thrown if error
   */
  public static String extractCountryNameFromFile(File file, String descriptionOfDefaults) throws PlanItException {
    PlanItException.throwIfNull(file, "%s file provided is null", descriptionOfDefaults);    
    Path filePath = file.toPath();
    
    String countryCodeFileName = filePath.getFileName().toString();
    PlanItException.throwIfNull(countryCodeFileName, "%s file name not present", descriptionOfDefaults);    
    countryCodeFileName = FileUtils.getFileNameWithoutExtension(countryCodeFileName);
    
    return LocaleUtils.getCountryNameCodeByIso2Code(countryCodeFileName);    
  }

  /** collect an iterable given that each country specific file is in CSV format
   * @param file presumed compatible with country specific CSV format
   * @return CSVRecord iterable
   * @throws IOException thrown if error
   */
  public static Iterable<CSVRecord> collectCsvRecordIterable(File file) throws IOException {
    Reader in = new FileReader(file.toPath().toAbsolutePath().toString());
    return CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);    
  }  
}

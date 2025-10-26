package org.goplanit.osm.defaults;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.locale.LocaleUtils;
import org.goplanit.utils.misc.FileUtils;
import org.goplanit.utils.misc.StringUtils;
import org.goplanit.utils.misc.UriUtils;
import org.goplanit.utils.resource.ResourceUtils;

/** Class with some common functionality for defaults that are country specific and stored in CSV files
 * 
 * @author markr
 *
 */
public class CountrySpecificDefaultUtils {
  
  /** logger to use */
  private static final Logger LOGGER = Logger.getLogger(CountrySpecificDefaultUtils.class.getCanonicalName());

  
  /** Call consumer for each country specific file in resource dir
   * 
   * @param resourceDir to use
   * @param callBack to use
   */
  public static void callForEachFileInResourceDir(
      final String resourceDir, final BiConsumer<InputStreamReader, String> callBack) {
    try {
      /* country specific based on resource files*/
      URI uri = null;
      try {
        uri = ResourceUtils.getResourceUri(resourceDir);        
      }catch(Exception e) {
        LOGGER.warning(e.getMessage());
        e.printStackTrace();
        return;
      }
      
      /* when jar, we cannot utilise regular file based approach and instead we must use an alternate file system to
       * access the directory stream since a file system is closeable, we must have access to it */
      FileSystem fs = null;
      DirectoryStream<Path> directoryStream = null;
      if (UriUtils.isInJar(uri)) {
        fs = ResourceUtils.getJarFileSystem(uri);
        directoryStream = Files.newDirectoryStream(fs.getPath(resourceDir));        
      } else {
        directoryStream = Files.newDirectoryStream(Paths.get(uri));    
      }
      
      for(Path resourcePath: directoryStream){
        String fullCountryName = CountrySpecificDefaultUtils.extractCountryNameFromFile(resourcePath);
        if(StringUtils.isNullOrBlank(fullCountryName)) {
          LOGGER.warning(String.format("DISCARD: Unrecognised country code encountered (%s) when parsing default " +
              "OSM highway speed limit values", fullCountryName));
          continue;
        }
        
        /* construct relative file path, so we can let the uri sort out the actual location */
        String resourceInDirRelUri = resourceDir + "/" + resourcePath.getFileName().toString();        
        URI fileResourceUri = ResourceUtils.getResourceUri(resourceInDirRelUri);
        InputStreamReader inputReader = ResourceUtils.getResourceAsInputStreamReader(fileResourceUri);
        callBack.accept(inputReader, fullCountryName);
        inputReader.close();
      }
      
      /* close resources */
      directoryStream.close();
      if (UriUtils.isInJar(uri)) {
        fs.close();
      }
      
    }catch(Exception e) {
      LOGGER.severe(e.getMessage());
      LOGGER.severe(String.format("Unable to parse files in resource dir %s", resourceDir));
    }
  }  
  
  /** Validate if file is a valid resource file and its name is constructed based on a country code. 
   * Each file should be named according to ISO366 alpha 2 country code. If valid the full country name
   * is returned 
   * 
   * @param filePath to validate
   * @return full country name, otherwise null
   * @throws PlanItException thrown if error
   */
  public static String extractCountryNameFromFile(Path filePath) throws PlanItException {
    PlanItException.throwIfNull(filePath, "path provided is null");    
    
    String countryCodeFileName = filePath.getFileName().toString();
    PlanItException.throwIfNull(countryCodeFileName, "file name not present on path %s", filePath);    
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
    return collectCsvRecordIterable(in);    
  } 
  
  /** Collect an iterable for the given reader
   * @param readerToUse the reader to use
   * @return CSVRecord iterable
   * @throws IOException thrown if error
   */
  public static Iterable<CSVRecord> collectCsvRecordIterable(Reader readerToUse ) throws IOException {
    return CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(readerToUse);    
  }  
}

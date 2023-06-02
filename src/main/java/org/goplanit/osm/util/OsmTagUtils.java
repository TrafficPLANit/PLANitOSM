package org.goplanit.osm.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import org.goplanit.osm.tags.OsmTags;
import org.goplanit.utils.misc.StringUtils;

/**
 * Class with some general convenience methods for dealing with OSM tags 
 * 
 * @author markr
 *
 */
public class OsmTagUtils {

  /** logegr to use */
  private static final Logger LOGGER = Logger.getLogger(OsmTagUtils.class.getCanonicalName());
  
  /** regular expression used to identify non-word characters (a-z any case, 0-9 or _) or whitespace*/
  public static final String VALUETAG_SPECIALCHAR_STRIP_REGEX = "[^\\w\\s]";

  
  /** Verify if the passed in value tag is present in the list of value tags provided
   * 
   * @param valueTag to check
   * @param valueTags to check against
   * @return true when present, false otherwise
   */
  public static boolean matchesAnyValueTag(final String valueTag, final String... valueTags) {
    for(int index=0; index < valueTags.length;++ index) {
      if(valueTag.equals(valueTags[index])) {
        return true;
      }
    }
    return false;
  }
    
  /** Verify if the passed in key matches and of the passed in values in the tags provided, all value tags are filtered by applying {@link VALUETAG_SPECIALCHAR_STRIP_REGEX}
   * 
   * @param tags to check existence from
   * @param keyTag to check 
   * @param valueTags to check
   * @return true when match is present, false otherwise
   */    
  public static boolean keyMatchesAnyValueTag(Map<String, String> tags, String keyTag, String... valueTags) {
    return anyKeyMatchesAnyValueTag(tags, new String[] {keyTag}, valueTags);
  }   
  
  /** Verify if any of the passed in keys matches and of the passed in values in the tags provided, all value tags are filtered by applying {@link VALUETAG_SPECIALCHAR_STRIP_REGEX}
   * 
   * @param tags to check existence from
   * @param keyTags to check 
   * @param valueTags to check
   * @return true when match is present, false otherwise
   */  
  public static boolean anyKeyMatchesAnyValueTag(final Map<String,String> tags, final String[] keyTags, final String... valueTags) {
    return anyKeyMatchesAnyValueTag(tags, VALUETAG_SPECIALCHAR_STRIP_REGEX, keyTags, valueTags);
  }
  
  /** Verify if any of the passed in keys matches and of the passed in values in the tags provided
   * 
   * @param tags to check existence from
   * @param regEx filter each value tag in tags by applying this regular expressions and replace matches with "", can be used to strip whitespaces or unwanted characters that cause a mistmach
   * @param keyTags to check 
   * @param valueTags to check
   * @return true when match is present, false otherwise
   */  
  public static boolean anyKeyMatchesAnyValueTag(final Map<String,String> tags, String regEx, final String[] keyTags, final String... valueTags) {
    if(containsAnyKey(tags, keyTags)) {
      for(int index=0; index < keyTags.length;++ index) {
        String currentKey = keyTags[index];
        if(tags.containsKey(currentKey) && matchesAnyValueTag(tags.get(currentKey).replaceAll(regEx, ""), valueTags)) {
          return true;
        }
      }
    }
    return false;
  }       

  /** Construct composite key "currentKey:subTag1:subTag2:etc."
   * 
   * @param currentKey the currentKey
   * @param subTagConditions to add
   * @return composite version separated by colons
   */
  public static String createCompositeOsmKey(final String currentKey, final String... subTagConditions) {
    String compositeKey = (currentKey!=null && !currentKey.isBlank()) ? currentKey : "";
    if(subTagConditions != null) {    
      for(int index=0;index<subTagConditions.length;++index) {
        String subTag = subTagConditions[index];
        compositeKey  = (subTag!=null && !subTag.isBlank()) ? compositeKey.concat(":").concat(subTag) : compositeKey; 
      }
    }
    return compositeKey;
  }

  /** Determine if any of the potential keys is listed in the passed in tags
   * 
   * @param tags to check
   * @param potentialKeys to check
   * @return true when present, false otherwise
   */
  public static boolean containsAnyKey(final Map<String, String> tags, final String... potentialKeys) {
    for(int index=0;index<potentialKeys.length;++index) {
      String potentialKey = potentialKeys[index];
      if(tags.containsKey(potentialKey)) {
        return true;
      }
    }
    return false;
  }

  /** Collect the values for any "ref" related key tag that we support. We currently support the following ref key tags
   * 
   * <ul>
   * <li>ref</li>
   * <li>loc_ref</li>
   * <li>local_ref</li>
   * </ul>
   * 
   * @param tags to verify
   * @return found values, empty if present
   */
  public static List<String> getValuesForSupportedRefKeys(Map<String, String> tags) {
    final List<String> refs = new ArrayList<>(1);

    Consumer<String> addToRefsForTag =
        (osmRefTag) -> {
          if(tags.containsKey(osmRefTag)) {
            refs.addAll(Arrays.asList(StringUtils.splitByAnythingExceptAlphaNumeric(tags.get(osmRefTag))));
          }
        };

    addToRefsForTag.accept(OsmTags.REF);
    addToRefsForTag.accept(OsmTags.LOC_REF);
    addToRefsForTag.accept(OsmTags.LOCAL_REF);

    return refs;
  }

  /**
   * Parse value for key as Integer, return null and log warning if not possible to perform conversion
   *
   * @param tags to extract from
   * @param tagKey key to get value for
   * @return parsed integer
   */
  public static Integer getValueAsInt(Map<String, String> tags, String tagKey) {
    try {
      return Integer.parseInt(tags.get(tagKey));
    }catch(NumberFormatException nfe){
      LOGGER.warning(String.format("Value for tag %s is not integer, tagging error", tagKey));
    }
    return null;
  }

  /**
   * Combine two string and add '=' in between
   *
   * @param key to use
   * @param value to use
   * @return key=value
   */
  public static String toConcatEqualsString(String key, String value){
    return toConcatWithSep(key, value, "=");
  }

  /**
   * Combine two string and add separator, e.g, '=' in between
   *
   * @param key to use
   * @param value to use
   * @param sep separator to use
   * @return result e.g., a=b for ('a','b','=')
   */
  public static String toConcatWithSep(String key, String value,String sep){
    return String.format("%s%s%s",key, sep, value);
  }
}

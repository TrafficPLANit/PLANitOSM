package org.goplanit.osm.converter.network.settings;

/**
 * How an OSM access value tag is to be interpreted during parsing, see
 * {@link OsmNetworkReaderYamlKeyedSettings#classifyAccessValueTag(String, AccessValueClassification)}.
 * <p>
 * An access tag such as {@code access=private} only says something about mode access once its value is read as
 * granting or denying it. Most values have a fixed reading, but some, such as private, are read differently from one
 * study to the next, so the user can classify them
 * </p>
 *
 * @author markr
 */
public enum AccessValueClassification {
  /** the value grants access */
  POSITIVE,
  /** the value denies access */
  NEGATIVE
}

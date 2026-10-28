package org.goplanit.osm.converter.network;

import org.goplanit.osm.tags.OsmDirectionTags;

/**
 * The direction of travel along its OSM way that a traffic signal applies to, as the user sets it, see
 * {@link OsmIntersectionSettings#overrideTrafficSignalDirection(long, OsmTrafficSignalDirection)}: forward, in the
 * order of the way's nodes, or backward, against it
 *
 * @author markr
 */
public enum OsmTrafficSignalDirection {

  /** in the order of the nodes of the signal's OSM way */
  FORWARD(OsmDirectionTags.FORWARD),

  /** against the order of the nodes of the signal's OSM way */
  BACKWARD(OsmDirectionTags.BACKWARD);

  /** the value of traffic_signals:direction this direction stands for */
  private final String tagValue;

  /**
   * Constructor
   *
   * @param tagValue the value of traffic_signals:direction this direction stands for
   */
  private OsmTrafficSignalDirection(String tagValue) {
    this.tagValue = tagValue;
  }

  /**
   * The value of traffic_signals:direction this direction stands for
   *
   * @return forward or backward
   */
  public String getTagValue() {
    return tagValue;
  }
}

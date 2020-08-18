package org.planit.osm.reader;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import org.opengis.geometry.DirectPosition;
import org.planit.geo.PlanitGeoUtils;
import org.planit.network.physical.macroscopic.MacroscopicNetwork;
import org.planit.osm.physical.network.macroscopic.PLANitOSMNetwork;
import org.planit.osm.util.PlanitOSMTags;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.network.physical.Link;
import org.planit.utils.network.physical.Node;
import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

import de.topobyte.osm4j.core.access.DefaultOsmHandler;
import de.topobyte.osm4j.core.model.iface.OsmBounds;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;

/**
 * Handler that handles, i.e., converts, nodes, ways, and relations. We parse these entities in distinct order, first all nodes, then all ways, and then all relations. this allows
 * us to incrementally construct the network without backtracking or requiring the entire file to be in memory in addition to the memory model we're creating.
 * 
 * @author markr
 * 
 *
 */
public class PLANitOSMHandler extends DefaultOsmHandler {

  private static final Logger LOGGER = Logger.getLogger(PLANitOSMHandler.class.getCanonicalName());

  /** the network to populate */
  private final PLANitOSMNetwork network;

  /** the settings to adhere to */
  private final PLANitOSMReaderSettings settings;

  /** utilities for geographic information */
  private final PlanitGeoUtils geoUtils;  
  
  /**
   * @return the network
   */
  protected MacroscopicNetwork getNetwork() {
    return network;
  }
  
  /**
   * Collect the default settings for this way based on its highway type
   * 
   * @param way the way
   * @param tags the tags of this way
   * @return the link segment type if available, otherwise nullis returned
   */
  protected MacroscopicLinkSegmentType getDefaultLinkSegmentType(OsmWay osmWay, Map<String, String> tags) {
    MacroscopicLinkSegmentType linkSegmentType = null;
    if (tags.containsKey(PlanitOSMTags.HIGHWAY)) {
      linkSegmentType = network.getSegmentTypeByOSMTag(tags.get(PlanitOSMTags.HIGHWAY));
      if(linkSegmentType == null) {
        LOGGER.warning(String.format("unsupported value for OSM way encountered: highway:%s (id:%d), reverting to default: highway:%s", 
            tags.get(PlanitOSMTags.HIGHWAY), osmWay.getId(), settings.getDefaultOSMHighwayValueWhenUnsupported()));
        linkSegmentType = network.getSegmentTypeByOSMTag(settings.getDefaultOSMHighwayValueWhenUnsupported());
        if(linkSegmentType == null) {
          LOGGER.warning("supplied default is also not supported, reverting to known type TERTIARY");
          linkSegmentType = network.getSegmentTypeByOSMTag(PlanitOSMTags.TERTIARY);
        }
      }      
    }
    return linkSegmentType;
  }
  
  /** extract a link from the way
   * @param osmWay the way to process
   * @param tags tags that belong to the way
   * @return the link corresponding to this way
   */
  private Link extractLink(OsmWay osmWay, Map<String, String> tags) {
    return null;
  }  
  
  /** Extract one or two link segments from the way corresponding to the link
   * @param osmWay the way
   * @param tags tags that belong to the way
   * @param link the link corresponding to this way
   * @param defaultLinkSegmentType the default link segment type corresponding to this way  
   */
  private void extractMacroscopicLinkSegments(OsmWay osmWay, Map<String, String> tags, Link link, MacroscopicLinkSegmentType defaultLinkSegmentType) {
    // TODO Auto-generated method stub    
  }  
  
  /**
   * constructor
   * 
   * @param settings for the handler
   */
  public PLANitOSMHandler(PLANitOSMNetwork network, PLANitOSMReaderSettings settings) {
    this.network = network;
    this.settings = settings;
    this.geoUtils = new PlanitGeoUtils(settings.getSourceCRS());
  }

  @Override
  public void handle(OsmBounds bounds) throws IOException {
    // not used
  }

  /**
   * construct PLANit cnodes from OSM nodes
   * 
   * @param osmNode node to parse
   */
  @Override
  public void handle(OsmNode osmNode) throws IOException {
    /* location info */
    DirectPosition geometry = null;
    try {
      geometry = geoUtils.createDirectPosition(osmNode.getLongitude(), osmNode.getLatitude());
    } catch (PlanItException e) {
      LOGGER.severe(String.format("unable to construct location information for osm node (id:%d), node skipped", osmNode.getId()));
    }

    Node node = network.nodes.registerNewNode(osmNode.getId());
    node.setCentrePointGeometry(geometry);
    
    if(network.nodes.getNumberOfNodes() % 5000 == 0) {
      LOGGER.info(String.format("Created %d nodes out of OSM nodes",network.nodes.getNumberOfNodes()));
    }
  }

  /**
   * parse an osm way to extract link and link segments (including type). If insufficient information
   * is available the handler will try to infer the missing data by using defaults set by the user
   */
  @Override
  public void handle(OsmWay osmWay) throws IOException {
    
    Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);

    /* a default link segment type should be available as starting point*/
    MacroscopicLinkSegmentType linkSegmentType = getDefaultLinkSegmentType(osmWay, tags);
    if(linkSegmentType != null) {

      /* a link only consists of start and end node, no direction and has no model information */
      Link link = extractLink(osmWay, tags);
      
      /* a macroscopic link segment is directional and can have a shape, it also has model information */
      extractMacroscopicLinkSegments(osmWay, tags, link, linkSegmentType);
      
      if(network.links.getNumberOfLinks() % 5000 == 0) {
        LOGGER.info(String.format("Created %d links out of OSM ways",network.links.getNumberOfLinks()));
      }
      if(network.linkSegments.getNumberOfLinkSegments() % 5000 == 0) {
        LOGGER.info(String.format("Created %d links segments out of OSM ways",network.linkSegments.getNumberOfLinkSegments()));
      }      
    }
  }


  @Override
  public void handle(OsmRelation osmRelation) throws IOException {
    // delegate
  }

  @Override
  public void complete() throws IOException {
    // not used
    LOGGER.info("DONE");
  }

}

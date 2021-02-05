package org.planit.osm.converter.intermodal;

import java.util.logging.Logger;

import org.planit.converter.intermodal.IntermodalReader;
import org.planit.network.InfrastructureNetwork;
import org.planit.network.macroscopic.MacroscopicNetwork;
import org.planit.osm.converter.network.PlanitOsmNetworkReader;
import org.planit.osm.converter.zoning.PlanitOsmZoningReader;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.Pair;
import org.planit.zoning.Zoning;

/**
 * Parse OSM input in either *.osm or *.osm.pbf format and return PLANit intermodal network which includes the transfer zones
 * of a zoning instance.
 * 
 * @author markr
 *
 */
public class PlanitOsmIntermodalReader implements IntermodalReader {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(PlanitOsmIntermodalReader.class.getCanonicalName());
  
  /**
   * the network reader
   */
  protected final PlanitOsmNetworkReader osmNetworkReader;
  
  
  /**
   * the zoning reader 
   */
  protected final PlanitOsmZoningReader osmZoningReader;
    

  /**
   * Constructor 
   * 
   * @param inputFile
   * @param osmNetworkReader to use for parsing the (multi-layer) network
   * @param osmZoningReader to use for parsing the intermodal aspect regarding stops, platforms, etc.
   */
  protected PlanitOsmIntermodalReader(final PlanitOsmNetworkReader osmNetworkReader, final PlanitOsmZoningReader osmZoningReader){
    this.osmNetworkReader = osmNetworkReader;
    this.osmZoningReader = osmZoningReader;
  }
  
   
  /**
   * Parse a local *.osm or *.osm.pbf file and convert it into a Macroscopic network and zoning
   * given the configuration options that have been set
   * 
   * @return network and zoning that has been parsed
   * @throws PlanItException thrown if error
   */  
  @Override
  public Pair<InfrastructureNetwork, Zoning> read() throws PlanItException {

    /* first parse the network */
    MacroscopicNetwork network = osmNetworkReader.read();
    
    /* then parse the intermodal zoning aspect, i.e., transfer/od zones */
    Zoning zoning = osmZoningReader.read();
    
    /* return result */
    return Pair.create(network, zoning);
  }      

}

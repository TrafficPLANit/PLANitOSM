package org.planit.osm.util;

import org.planit.utils.network.physical.macroscopic.MacroscopicLinkSegmentType;

/**
 * Some constants used by PLANit OSM
 * 
 * @author markr
 *
 */
public class OsmConstants {

  /** pcu/km/lane */
  public static final double DEFAULT_MAX_DENSITY_LANE= MacroscopicLinkSegmentType.DEFAULT_MAX_DENSITY_LANE;
  
  /** pcu/h/lane */
  public static final double DEFAULT_MINIMUM_CAPACITY_LANE = 600;  
  
  /** default for this type in pcu/h/lane */
  public static final double MOTORWAY_CAPACITY = 2000;
  
  /** default for this type in pcu/h/lane */
  public static final double MOTORWAY_LINK_CAPACITY = 1800;

  /** default for this type in pcu/h/lane */
  public static final double TRUNK_CAPACITY = 2000;

  /** default for this type in pcu/h/lane */
  public static final double TRUNK_LINK_CAPACITY = 1700;

  /** default for this type in pcu/h/lane */
  public static final double PRIMARY_CAPACITY = 1600;

  /** default for this type in pcu/h/lane */
  public static final double PRIMARY_LINK_CAPACITY = 1400;  
  
  /** default for this type in pcu/h/lane */
  public static final double SECONDARY_CAPACITY = 1200;

  /** default for this type in pcu/h/lane */
  public static final double SECONDARY_LINK_CAPACITY = 1000;

  /** default for this type in pcu/h/lane */
  public static final double TERTIARY_CAPACITY = 1000;
  
  /** default for this type in pcu/h/lane */
  public static final double TERTIARY_LINK_CAPACITY = 800;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double UNCLASSIFIED_LINK_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double RESIDENTIAL_LINK_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double LIVING_STREET_LINK_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double SERVICE_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double PEDESTRIAN_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double TRACK_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane */
  public static final double ROAD_CAPACITY = 0;

  /** default capacity in pcu/h for railways, not that this is generally not used so merely here for consistency */
  public static final double RAILWAY_CAPACITY = 10000;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double PATH_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double STEPS_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double FOOTWAY_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double CYCLEWAY_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;

  /** default for this type in pcu/h/lane set to {@code DEFAULT_MINIMUM_CAPACITY_LANE}*/
  public static final double BRIDLEWAY_CAPACITY = DEFAULT_MINIMUM_CAPACITY_LANE;  
    
}

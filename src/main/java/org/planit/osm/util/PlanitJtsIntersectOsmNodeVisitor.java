package org.planit.osm.util;

import java.util.Collection;
import java.util.logging.Logger;

import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.ItemVisitor;
import org.planit.utils.geo.PlanitJtsUtils;
import de.topobyte.osm4j.core.model.iface.OsmNode;

/**
 * An item visitor for quadtree spatial index to filter out osm nodes in a quadtree that truly intersect with the
 * given bounding box provided in the query. Since spatial granularity of the "squares" in the quadtree might be too coarse 
 * it may select a too large a set of matches for any given bounding box. By providing this visitor we explicitly check the provided subset
 * if it indeed intersects with the given filer, i.e., bounding box that we are searching for.
 * 
 * @author markr
 *
 */
public class PlanitJtsIntersectOsmNodeVisitor implements ItemVisitor{
  
    /** the logger to use */
    private static final Logger LOGGER = Logger.getLogger(PlanitJtsIntersectOsmNodeVisitor.class.getCanonicalName());

    /** result to populate */  
    private Collection<OsmNode> filteredResultToPopulate;
    
    /** filter to apply, i.e., the area to filter intersection test on */
    private Polygon geometryFilter;
      
    /** Constructor
     * 
     * @param geometryFilter to use
     * @param filteredResultToPopulate to populate
     */
    public PlanitJtsIntersectOsmNodeVisitor(Polygon geometryFilter, Collection<OsmNode> filteredResultToPopulate) {
      this.geometryFilter = geometryFilter;
      this.filteredResultToPopulate = filteredResultToPopulate;
        
    }
  
    /**
     * {@inheritDoc}
     */
    @Override
    public void visitItem(Object osmNode) {
      try {
        if(geometryFilter.intersects(PlanitJtsUtils.createPoint(PlanitOsmNodeUtils.createCoordinate((OsmNode)osmNode)))){
          filteredResultToPopulate.add((OsmNode)osmNode); 
        }
      }catch(Exception e) {
        LOGGER.severe(e.getMessage());
      }
    }
  
    /** Collect the filtered result created by the visitor 
     * @return result
     */
    public Collection<OsmNode> getResult() {
      return filteredResultToPopulate;
    }
}

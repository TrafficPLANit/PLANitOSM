package org.planit.osm.util;

import java.util.logging.Logger;

import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;

/**
 * Utilities in relation to parsing OSM relations while constructing a PLANit model from it
 * 
 * @author markr
 *
 */
public class OsmRelationUtils {
  
  /** the logger */
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(OsmRelationUtils.class.getCanonicalName());

  /** Find the first relation member with the given role
   * 
   * @param osmRelation to check
   * @param role to find
   * @return member found, null if none is found
   */
  public static OsmRelationMember findFirstOsmRelationMemberWithRole(OsmRelation osmRelation, String role) {
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);         
      if(member.getRole().equals(role)) {
        return member;
      }
    }
    return null;
  }
  
  


}

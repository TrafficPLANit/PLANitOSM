package org.goplanit.osm.util;

import java.util.Map;
import java.util.logging.Logger;

import com.sun.istack.NotNull;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.model.iface.OsmRelationMember;
import org.goplanit.utils.functionalinterface.TriConsumer;

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

  /** Apply the provided consumer to all relation members with the given role
   *
   * @param tags of the relation
   * @param osmRelation to check*
   * @param role to find
   * @param consumer to apply
   */
  public static void applyToOsmRelationMemberWithRole(
      Map<String,String> tags,
      OsmRelation osmRelation,
      String role,
      TriConsumer<Map<String,String>, OsmRelation, OsmRelationMember> consumer) {
    for(int index = 0 ;index < osmRelation.getNumberOfMembers() ; ++index) {
      OsmRelationMember member = osmRelation.getMember(index);
      if(member.getRole().equals(role)) {
        consumer.accept(tags, osmRelation, member);
      }
    }
  }

  /**
   * Check if a member conforms to provided type and role
   * @param member to check
   * @param typeToCheck type
   * @param role role
   * @return true if match, false otherwise
   */
  public static boolean isMemberOfTypeAndRole(@NotNull OsmRelationMember member, @NotNull EntityType typeToCheck, @NotNull String role){
    assert member != null;
    assert typeToCheck != null;
    assert role != null;
    return typeToCheck.equals(member.getType()) && role.equals(member.getRole());
  }
  


}

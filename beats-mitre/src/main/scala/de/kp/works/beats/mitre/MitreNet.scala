package de.kp.works.beats.mitre
/**
 * Copyright (c) 2020 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 *
 */

import com.google.gson.JsonElement
import de.kp.works.beats.mitre.MitreDomains.MitreDomain

import scala.collection.mutable

class MitreNet extends MitreConnect {
  /**
   * The following object types are
   * not taken into account:
   *
   * - x-mitre-collection
   * - x-mitre-matrix
   */
  val NODE_TYPES = List(
    "attack-pattern",
    "course-of-action",
    /*
     * For nodes of the MITRE domain knowledge
     * basis, there is one `identity` object,
     * i.e., the MITRE organization.
     *
     * As this is no threat related information,
     * the `identity`object is ignored.
     *
     * - "identity",
     */
    "intrusion-set",
    "malware",
    /*
     * For nodes of the MITRE domain knowledge
     * basis, there is one `marking-definition`
     * object, that contains the copyright
     * statement for the respective entries.
     *
     * As this is no threat related information,
     * the `marking-definition`object is ignored.
     *
     * - "marking-definition",
     */
    "tool",
    "x-mitre-data-component",
    /*
     * Data sources represent the various subjects/topics
     * of information that can be collected by sensors/logs.
     *
     * Data sources also include data components, which identify
     * specific properties/values of a data source relevant to
     * detecting a given ATT&CK technique or sub-technique.
     *
     * MITRE recommends to monitor data components to detect
     * attack patterns.
     */
    "x-mitre-data-source"
    /*
     * A MITRE adversary tactic is considered a
     * node that is related to other MITRE objects,
     * and referenced via the kill chain phase name.
     *
     * Therefore, the tactic nodes are excluded from
     * the node extracted.
     *
     * - "x-mitre-tactic"
     */
  )

  val NODE_BASE_PROPERTIES = List(
    "created",
    "description",
    "id",
    "is_family",
    "modified",
    "name",
    "spec_version",
    "type",
    /*
     * CAPEC specific properties
     */
    "x_capec_abstraction",
    "x_capec_extended_description",
    "x_capec_execution_flow",
    "x_capec_likelihood_of_attack",
    "x_capec_status",
    "x_capec_typical_severity",
    "x_capec_version",
    /*
     * MITRE specific properties, referring
     * to the domains ENTERPRISE, ICS and
     * MOBILE
     */
    "x_mitre_attack_spec_version",
    "x_mitre_is_subtechnique",
    "x_mitre_tactic_type",
    "x_mitre_version")

  val NODE_LIST_PROPERTIES = List(
    "aliases",
    /*
     * CAPEC specific properties
     */
    "x_capec_alternate_terms",
    "x_capec_domains",
    "x_capec_example_instances",
    "x_capec_prerequisites",
    "x_capec_resources_required",
    /*
     * MITRE specific properties, referring
     * to the domains ENTERPRISE, ICS and
     * MOBILE
     */
    "x_mitre_aliases",
    "x_mitre_contributors",
    /*
     * List of defense measures that can be bypassed
     * e.g., by an attack-pattern
     */
    "x_mitre_defense_bypassed",
    "x_mitre_domains",
    /*
     * The list of platforms like Linux that can
     * be threatened by e.g., an attack-pattern
     */
    "x_mitre_platforms"
  )

  val NODE_OBJECT_PROPERTIES = List(
    /*
     * CAPEC specific properties
     */
    "x_capec_consequences",
    "x_capec_skills_required"
  )

  val NODE_RELATION_PROPERTIES = List(
    /*
     * For nodes of the MITRE domain knowledge
     * basis, there is one `identity` object,
     * i.e., the MITRE organization.
     *
     * As this is no threat related information,
     * the `identity`object is ignored.
     *
     * - "created_by_ref",
     */
    "external_references",
    "kill_chain_phases",
    /*
     * For nodes of the MITRE domain knowledge
     * basis, there is one `marking-definition`
     * object, that contains the copyright
     * statement for the respective entries.
     *
     * As this is no threat related information,
     * the `marking-definition`object is ignored.
     *
     * - "object_marking_refs",
     */
    /*
     * CAPEC specific properties
     */
    "x_capec_can_follow_refs",
    "x_capec_can_precede_refs",
    "x_capec_child_of_refs",
    "x_capec_parent_of_refs",
    "x_capec_peer_of_refs",
    /*
     * MITRE specific properties, referring
     * to the domains ENTERPRISE, ICS and
     * MOBILE
     */
    "x_mitre_data_source_ref", // Used to reference a data source in a data component
    "x_mitre_data_sources"
    /*
     * For nodes of the MITRE domain knowledge
     * basis, there is one `identity` object,
     * i.e., the MITRE organization.
     *
     * As this is no threat related information,
     * the `identity`object is ignored.
     *
     * - "x_mitre_modified_by_ref",
     */
  )

  val EDGES_TYPES = List(
    "relationship"
  )

  val EDGE_BASE_PROPERTIES = List(
    "created",
    /* Description is not supported by CAPEC */
    "description",
    "id",
    "modified",
    "relationship_type",
    "source_ref",
    "spec_version",
    "target_ref",
    "x_capec_version",
    /*
     * The `type` properties is ignored
     * as this is expressed via `edge`
     *
     * -"type"
     */
    "x_mitre_attack_spec_version",
    /*
     * For edges of the MITRE domain knowledge
     * basis, there is one `identity` object,
     * i.e., the MITRE organization.
     *
     * As this is no threat related information,
     * the `identity`object is ignored.
     *
     * - "x_mitre_modified_by_ref",
     */
    "x_mitre_version")

  /**
   * The edge list properties below are restricted
   * to the domains ENTERPRISE, ICS and MOBILE
   */
  val EDGE_LIST_PROPERTIES = List(
    "x_mitre_domains"
  )
  /**
   * This implementation does not support n-ary
   * relations, and therefore the properties
   * below are ignored.
   */
  val EDGE_RELATION_PROPERTIES = List(
    "created_by_ref",
    "external_references",
    "object_marking_refs"
  )
  /**
   * This method extracts the identifiers that
   * defines MITRE based STIX objects.
   *
   * This list is used as a lookup to determine
   * whether a certain referenced object is part
   * of the provided domain.
   */
  def extractNodeIds(domain:MitreDomain):Seq[String] = {

    val nodeIds = mutable.ArrayBuffer.empty[String]

    val objects = getObjects(domain)
    objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      if (NODE_TYPES.contains(objType)) {
        nodeIds += objJson.get("id").getAsString
      }

    })

    nodeIds

  }
  /**
   * This method supports the MITRE NGSI transformation
   * of updated domain objects.
   */
  def extractObjects(deltaObjects:Seq[JsonElement]):Seq[JsonElement] = {

    deltaObjects.filter(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      NODE_TYPES.contains(objType)

    })

  }
  /**
   * This method supports the MITRE NGSI transformation
   * of updated domain objects.
   */
  def extractNodeIds(deltaObjects:Seq[JsonElement]):Seq[String] = {

    val nodeIds = mutable.ArrayBuffer.empty[String]
    deltaObjects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      if (NODE_TYPES.contains(objType)) {
        nodeIds += objJson.get("id").getAsString
      }

    })

    nodeIds

  }

  /**
   * This method extracts the domain specific
   * relationships and validates them
   */
  def extractRelations(domain:MitreDomain):Seq[JsonElement] = {
   /*
    * Retrieve all node identifiers to validate
    * that the relationships refer to existing
    * objects
    */
    val lookup = extractNodeIds(domain)
    /*
     * Extract relations from domain specific knowledge
     * base and restrict to those that define valid
     * relationships
     */
    val relations = getObjects(domain, Some("relationship"))
    filterRelations(relations, lookup)

  }
  /**
   * This method supports the MITRE NGSI transformation
   * of updated domain objects.
   */
  def extractRelations(deltaObjects:Seq[JsonElement]):Seq[JsonElement] = {
    /*
     * Retrieve all node identifiers to validate
     * that the relationships refer to existing
     * objects
     */
    val lookup = extractNodeIds(deltaObjects)
    /*
     * Extract relations from the provided delta
     * objects and restrict to those that define
     * valid relationships
     */
    val relations = deltaObjects
      .filter(obj => {
        obj.getAsJsonObject.get("type").getAsString == "relationship"
      })

    filterRelations(relations, lookup)

  }

  private def filterRelations(relations:Seq[JsonElement], lookup:Seq[String]):Seq[JsonElement] = {

    relations
      .filter(obj => {

        val objJson = obj.getAsJsonObject
        val deprecated = isDeprecated(objJson)
        /*
         * Note, the current implementation of STIX CAPEC
         * is limited to the relationship_type = mitigates
         */
        val revokedBy = objJson.get("relationship_type").getAsString == "revoked-by"

        val source = objJson.get("source_ref").getAsString
        val hasSource = lookup.contains(source)

        val target = objJson.get("target_ref").getAsString
        val hasTarget = lookup.contains(target)

        hasSource && hasTarget && (!deprecated) && (!revokedBy)

      })

  }

}

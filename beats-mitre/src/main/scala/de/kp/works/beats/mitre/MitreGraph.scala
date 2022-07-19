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

import com.google.gson.{JsonArray, JsonElement, JsonNull, JsonObject}
import de.kp.works.beats.mitre.MitreDomains.MitreDomain

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable

object MitreGraph extends MitreConnect {
  /*
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
    "x-mitre-data-source",
    "x-mitre-tactic")

  val NODE_BASE_PROPERTIES = List(
    "created",
    "description",
    "id",
    "is_family",
    "modified",
    "name",
    "spec_version",
    "type",
    "x_mitre_attack_spec_version",
    "x_mitre_is_subtechnique",
    "x_mitre_tactic_type",
    "x_mitre_version")

  val NODE_LIST_PROPERTIES = List(
    "aliases",
    "kill_chain_phases",
    "x_mitre_aliases",
    "x_mitre_contributors",
    "x_mitre_data_sources",
    "x_mitre_defense_bypassed",
    "x_mitre_domains",
    "x_mitre_platforms"
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
    "external_references"
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
     *
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

  val EXTERNAL_PROPS = List(
    "description",
    "external_id",
    "source_name",
    "url"
  )

  def extractExternals(objects:Seq[JsonElement]):Seq[JsonObject] = {

    val externals = mutable.ArrayBuffer.empty[JsonObject]

    objects.foreach(obj => {
      val objJson = obj.getAsJsonObject
      if (objJson.has("external_references")) {

        val efs = objJson.get("external_references").getAsJsonArray
        efs.foreach(ef => {
          val efJson = ef.getAsJsonObject
          /*
           * An external reference is described as
           * a pseudo STIX object, as it is associated
           * to nodes via and edge.
           */
          val tokens = mutable.ArrayBuffer.empty[String]
          val externalJson = new JsonObject

          EXTERNAL_PROPS.foreach(prop => {
            if (efJson.has(prop)) {
              val value = efJson.get(prop).getAsString

              tokens += value
              externalJson.addProperty(prop, value)
            } else {
              tokens += ""
              externalJson.addProperty(prop, "")
            }
          })
          /*
           * Build unique identifier
           */
          val ident = java.util.UUID.fromString(tokens.mkString("|")).toString
          val id = s"external-reference--$ident"

          externalJson.addProperty("id", id)
          externals += externalJson

        })
      }
    })

    externals.distinct

  }
  /**
   * This method extracts the identifiers that
   * defines MITRE based STIX objects.
   *
   * This list is used as a lookup to determine
   * whether a certain referenced object is part
   * of the provided domain.
   */
  def extractNodeIds(objects:Seq[JsonElement]):Seq[String] = {

    val nodeIds = mutable.ArrayBuffer.empty[String]
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
   * Extracting nodes from a domain-specific knowledge
   * base is mapped onto nodes and edges, as certain
   * node properties are used to reference another node.
   */
  def extractNodes(domain:MitreDomain):(Seq[JsonObject], Seq[JsonObject]) = {

    val mustHaves = List("id", "name", "type")

    val nodes = mutable.ArrayBuffer.empty[JsonObject]
    val edges = mutable.ArrayBuffer.empty[JsonObject]

    val objects = getObjects(domain)
    /*
     * STEP #1: Extract the unique identifiers
     * of the nodes provided by the domain
     * specific knowledge base
     */
    val nodeIds = extractNodeIds(objects)
    /*
     * STEP #2: Determine all MITRE nodes
     */
     objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val nodeJson = new JsonObject
      /*
       * Assign base and list properties
       * to the nodeJSON
       */
      (NODE_BASE_PROPERTIES ++ NODE_LIST_PROPERTIES)
        .foreach(prop => {
          /*
           * Determine whether the MITRE domain
           * object contains one of must have
           * properties
           */
          if (mustHaves.contains(prop)) {

            if (!objJson.has(prop)) {
              val message = s"MITRE domain object detected that does not contain `$prop` field."
              error(message)

              throw new Exception(message)
            }

          }
          /*
           * The kill chain name of the MITRE kill chain
           * is always `mitre-attack`. It is introduced to
           * distinguish the respective phase from other
           * kill chains.
           *
           * Below the kill chain phases are flattened,
           * using an urn like format
           */
          if (objJson.has("kill_chain_phases")) {

            val formatted = new JsonArray
            objJson.remove("kill_chain_phases").getAsJsonArray
              .foreach(kcp => {
                val kcpJson = kcp.getAsJsonObject

                val kcn = kcpJson.get("kill_chain_name").getAsString
                val pn  = kcpJson.get("phase_name").getAsString

                formatted.add(s"$kcn:$pn")
              })

            objJson.add("kill_chain_phases", formatted)
          }

          val value = if (objJson.has(prop)) objJson.get(prop) else JsonNull.INSTANCE
          nodeJson.add(prop, value)

        })

      nodes += nodeJson
      /*
       * Build edges from relation properties
       */

    })

    // TODO

    (nodes, edges)

  }
}

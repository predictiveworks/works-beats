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
    "x_mitre_attack_spec_version",
    "x_mitre_is_subtechnique",
    "x_mitre_tactic_type",
    "x_mitre_version")

  val NODE_LIST_PROPERTIES = List(
    "aliases",
    "x_mitre_aliases",
    "x_mitre_contributors",
    "x_mitre_data_sources",
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
    "x_mitre_data_source_ref" // Used to reference a data source in a data component
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
    "description",
    "id",
    "modified",
    "relationship_type",
    "source_ref",
    "spec_version",
    "target_ref",
    "type",
    "x_mitre_attack_spec_version",
    "x_mitre_modified_by_ref",
    "x_mitre_version")

  val EDGE_LIST_PROPERTIES = List(
    "x_mitre_domains"
  )

  val EDGE_RELATION_PROPERTIES = List(
    "created_by_ref",
    "external_references",
    "object_marking_refs"
  )
  val EXTERNAL_PROPS = List(
    "description",
    "external_id",
    "source_name",
    "url"
  )
  /*
   *
   */

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
           * Build unique identifier `id` and
           * object `type`
           */
          val ident = java.util.UUID.fromString(tokens.mkString("|")).toString
          val id = s"external-reference--$ident"

          externalJson.addProperty("id", id)
          externalJson.addProperty("type", "external-reference")

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
     * STEP #1: Extract all external references
     * as pseudo STIX objects and add to nodes
     */
    val externals = extractExternals(objects)
    nodes ++= externals
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
       * Build edges from relation properties: the current
       * implementation is restricted to external references
       */
      if (objJson.has("external_references")) {

        val efs = objJson.get("external_references").getAsJsonArray
        efs.foreach(ef => {
          val efJson = ef.getAsJsonObject
          /*
           * An external reference is described as
           * a pseudo STIX object, as it is associated
           * to nodes via and edge.
           */
          val tokens = EXTERNAL_PROPS
            .map(prop => {
              if (efJson.has(prop)) efJson.get(prop).getAsString
              else ""
            })
          /*
           * Build unique identifier
           */
          val ident = java.util.UUID.fromString(tokens.mkString("|")).toString

          val source_ref = objJson.get("id").getAsString
          val target_ref = s"external-reference--$ident"

          val edgeJson = new JsonObject
          edgeJson.addProperty("src", source_ref)
          edgeJson.addProperty("dst", target_ref)

          edgeJson.addProperty("type", "has-reference")
          edges += edgeJson

        })
      }

    })

    (nodes, edges)

  }

  def extractEdges(domain:MitreDomain):Seq[JsonElement] = {

    val mustHaves = List("id", "relationship_type", "type")

    val edges = mutable.ArrayBuffer.empty[JsonObject]
    /*
     * Retrieve all node identifiers
     */
    val nodeIds = extractNodeIds(domain)
    val objects = getObjects(domain, Some("relationship"))
      .filter(obj => {

        val objJson = obj.getAsJsonObject
        val deprecated = isDeprecated(objJson)

        val source = objJson.get("source_ref").getAsString
        val hasSource = nodeIds.contains(source)

        val target = objJson.get("target_ref").getAsString
        val hasTarget = nodeIds.contains(target)

        hasSource && hasTarget && (!deprecated)

      })
    /*
     * STEP #2: Determine all MITRE edges
     */
    objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val edgeJson = new JsonObject
      /*
       * Assign base and list properties
       * to the nodeJSON
       */
      (EDGE_BASE_PROPERTIES ++ EDGE_LIST_PROPERTIES)
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

          val value = if (objJson.has(prop)) objJson.get(prop) else JsonNull.INSTANCE

          prop match {
            case "source_ref" =>
              edgeJson.add("src", value)

            case "target_ref" =>
              edgeJson.add("dst", value)

            case "relation_type" =>
              edgeJson.add("type", value)

            case _ =>
              edgeJson.add(prop, value)
          }

        })

      edges += edgeJson

    })

    edges

  }
}

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

import com.google.gson.{JsonElement, JsonNull, JsonObject}
import de.kp.works.beats.mitre.MitreDomains.MitreDomain
import de.kp.works.beats.mitre.model.{MitreExternals, MitreTactics}

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable

object MitreGraph extends MitreNet with MitreExternals {
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
    val externals = extractExternals(objects, format = MitreFormats.JSON)
    nodes ++= externals
    /*
     * STEP #2: Determine all MITRE nodes
     */
    objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val objType = objJson.get("type").getAsString

      if (NODE_TYPES.contains(objType)) {

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

            val value = if (objJson.has(prop)) objJson.get(prop) else JsonNull.INSTANCE
            nodeJson.add(prop, value)

          })

        nodes += nodeJson

        /** EXTERNAL REFERENCES **/

        /*
         * Build edges from relation properties: the current
         * implementation is restricted to external references
         * and kill chain phases (MITRE tactics)
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

        /** KILL CHAIN PHASES **/

        /*
         * The `phase_name` of a certain kill chain phase
         * is equal to the short name of a certain MITRE
         * tactics
         */
        if (objJson.has("kill_chain_phases")) {

          val kcps = objJson.get("kill_chain_phases").getAsJsonArray
          kcps.foreach(kcp => {

            val kcpJson = kcp.getAsJsonObject
            val phaseName = kcpJson.get("phase_name").getAsString
            /*
             * Retrieve MITRE tactic and build additional
             * relationship `has-technique` between tactic
             * and MITRE object (attack pattern)
             */
            val tactic = MitreTactics.getTacticByName(domain, phaseName)
            if (!tactic.isJsonNull) {

              val source_ref = tactic.getAsJsonObject.get("id").getAsString
              val target_ref = objJson.get("id").getAsString

              val edgeJson = new JsonObject
              edgeJson.addProperty("src", source_ref)
              edgeJson.addProperty("dst", target_ref)

              edgeJson.addProperty("type", "has-technique")
              edges += edgeJson

            }

          })

        }

        /** DATA SOURCES */

        /*
         * The MITRE domain knowledge bases defines a fixed set
         * of data sources (and their components) that can be
         * referenced by other objects.
         *
         * The MITRE Enterprise & Ics domain defines relationships
         * between `attack-pattern` and data components explicitly.
         *
         * For these domains no additional action must be taken
         * to extract the respective edges, and, other domains do
         * not support `x_mitre_data_sources`
         */
        if (objJson.has("x_mitre_data_source_ref")) {
          /*
           * The respective object is a data component and the
           * references specifies the associated data source
           */
          val source_ref = objJson.get("x_mitre_data_source_ref").getAsString
          val target_ref = objJson.get("id").getAsString

          /** x-mitre-data-source-[contains]->x-mitre-data-component **/

          val edgeJson = new JsonObject
          edgeJson.addProperty("src", source_ref)
          edgeJson.addProperty("dst", target_ref)

          edgeJson.addProperty("type", "contains")
          edges += edgeJson

        }

        /** CAPEC RELATIONSHIPS **/

        val capecRelations = Map(
          "x_capec_can_follow_refs"  -> "can-follow",
          "x_capec_can_precede_refs" -> "can-precede",
          "x_capec_child_of_refs"    -> "child-of",
          "x_capec_parent_of_refs"   -> "parent-of",
          "x_capec_peer_of_refs"     -> "peer-of")

        capecRelations.foreach{case(k, v) =>

          if (objJson.has(k)) {

            val idents = objJson.get(k).getAsJsonArray
            idents.foreach(ident => {

              val source_ref = objJson.get("id").getAsString
              val target_ref = ident.getAsString

              val edgeJson = new JsonObject
              edgeJson.addProperty("src", source_ref)
              edgeJson.addProperty("dst", target_ref)

              edgeJson.addProperty("type", v)
              edges += edgeJson

            })
          }
        }

      }

    })

    (nodes, edges)

  }
  /**
   * This method supports CAPEC, ENTERPRISE, ICS and
   * MOBILE relationships and extracts the following
   * edges:
   *
   * CAPEC:
   *
   * course-of-action-[mitigates]->attack-pattern
   *
   * ENTERPRISE:
   *
   * attack-pattern-[subtechnique-of]->attack-pattern
   * course-of-action-[mitigates]->attack-pattern
   * intrusion-set-[uses]->attack-pattern
   * intrusion-set-[uses]->malware
   * intrusion-set-[uses]->tool
   * malware-[uses]->attack-pattern
   * tool-[uses]->attack-pattern
   * x-mitre-data-component-[detects]->attack-pattern
   *
   * ICS:
   *
   * course-of-action-[mitigates]->attack-pattern
   * intrusion-set-[uses]->attack-pattern
   * intrusion-set-[uses]->malware
   * malware-[uses]->attack-pattern
   * x-mitre-data-component-[detects]->attack-pattern
   *
   * MOBILE:
   *
   * attack-pattern-[subtechnique-of]->attack-pattern
   * course-of-action-[mitigates]->attack-pattern
   * intrusion-set-[uses]->attack-pattern
   * intrusion-set-[uses]->malware
   * malware-[uses]->attack-pattern
   * tool-[uses]->attack-pattern
   *
   */
  def extractEdges(domain:MitreDomain):Seq[JsonElement] = {

    val mustHaves = List("id", "relationship_type", "type")

    val edges = mutable.ArrayBuffer.empty[JsonObject]
    val relations = extractRelations(domain)
    /*
     * STEP #2: Determine all MITRE edges
     */
    relations.foreach(obj => {

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
          /*
           * The property value can be NULL, if the relationship
           * refers to CAPEC and a certain `x_mitre` property is
           * requested and vice versa.
           */
          val value = if (objJson.has(prop)) objJson.get(prop) else JsonNull.INSTANCE
          prop match {
            case "source_ref" =>
              edgeJson.add("src", value)

            case "target_ref" =>
              edgeJson.add("dst", value)

            case "relation_type" =>
              edgeJson.add("type", value)

            case _ =>
              if (value.isJsonNull) {
                if (prop.startsWith("x_capec") && domain == MitreDomains.CAPEC)
                  edgeJson.add(prop, value)

                if (prop.startsWith("x_mitre") && domain != MitreDomains.CAPEC)
                  edgeJson.add(prop, value)

              } else
                edgeJson.add(prop, value)
          }

        })

      edges += edgeJson

    })

    edges

  }
}

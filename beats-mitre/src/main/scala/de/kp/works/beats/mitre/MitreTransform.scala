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

import com.google.gson.{JsonArray, JsonElement, JsonObject}
import de.kp.works.beats.mitre.MitreDomains.MitreDomain
import de.kp.works.beats.mitre.model.{MitreExternals, MitreTactics}

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable

object MitreTransform extends MitreNet with MitreExternals {

  val ACTION:String    = "action"
  val BASE_TYPE:String = "baseType"
  val ENTITIES:String  = "entities"
  val FORMAT:String    = "format"
  val ID:String        = "id"
  val METADATA:String  = "metadata"
  val RELATIONS:String = "relations"
  val TYPE:String      = "type"
  val VALUE:String     = "value"
  /**
   * Delta objects refer to those MITRE domain-specific objects
   * that have been created or modified since the last scan of
   * the local MITRE knowledge bases.
   *
   * {
   *  "format": "graph",
   *  "entities": [...],
   *  "relations: [...]
   * }
   */
  def transform(deltaObjects:Seq[JsonElement], domain:MitreDomain):Option[JsonObject] = {

    val entities  = new JsonArray
    val relations = new JsonArray

    val resultJson = new JsonObject
    resultJson.addProperty(FORMAT, "graph")
    /*
     * Extract nodes and associated edges
     * from delta objects
     */
    val (v, e) = extractNodes(deltaObjects, domain)
    v.foreach(entities.add)
    e.foreach(relations.add)
    /*
     * Extract edges from delta objects
     */
    extractEdges(deltaObjects).foreach(relations.add)

    resultJson.add(ENTITIES,  entities)
    resultJson.add(RELATIONS, relations)

    Some(resultJson)

  }

  def extractNodes(deltaObjects:Seq[JsonElement], domain:MitreDomain):(Seq[JsonElement], Seq[JsonElement]) = {

    val nodes = mutable.ArrayBuffer.empty[JsonObject]
    val edges = mutable.ArrayBuffer.empty[JsonObject]

    val objects = extractObjects(deltaObjects)
    /*
     * STEP #1: Extract all external references
     * as NGSI compliant entities and add to the
     * set of nodes
     */
    val externals = extractExternals(objects, format = MitreFormats.NGSI)
    nodes ++= externals
    /*
     * STEP #2: Transform all objects into NGSI
     * compliant entities
     */
    val baseProperties = NODE_BASE_PROPERTIES
      .filter(p => p != "id" && p != "type")

    val boolProperties = List(
      "is_family", "x_mitre_is_subtechnique")

    val listProperties = NODE_LIST_PROPERTIES

    objects.foreach(obj => {

      val objJson  = obj.getAsJsonObject
      val nodeJson = new JsonObject
      /*
       * When retrieving the delta objects, the respective
       * action (create | update) is inferred and added.
       */
      val action = if (objJson.has(ACTION))
        objJson.get(ACTION).getAsString else "create"

      nodeJson.addProperty(ACTION, action)
      /*
       * Assign NGSI compliant `id` and `type`
       */
      val (ngsiId, ngsiType) = toNGSI(objJson.get("id").getAsString)
      nodeJson.addProperty(ID, ngsiId)
      nodeJson.addProperty(TYPE, ngsiType)

      /** BASE PROPERTIES **/

      baseProperties.foreach(attrName => {

        if (objJson.has(attrName)) {

          val attrJson = new JsonObject
          attrJson.add(METADATA, new JsonObject)

          val attrType = if (boolProperties.contains(attrName))
            "Boolean" else "String"

          attrJson.addProperty(TYPE, attrType)
          attrJson.add(VALUE, objJson.get(attrName))

          nodeJson.add(attrName, attrJson)

        }

      })

      /** LIST PROPERTIES **/

      listProperties.foreach(attrName => {

        if (objJson.has(attrName)) {

          val metaJson = new JsonObject
          metaJson.addProperty(BASE_TYPE, "String")

          val attrJson = new JsonObject
          attrJson.add(METADATA, metaJson)

          attrJson.addProperty(TYPE, "List")
          attrJson.add(VALUE, objJson.get(attrName))

          nodeJson.add(attrName, attrJson)

        }

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

          val source_ref = objJson.get(ID).getAsString
          /*
           * Build unique NGSI compliant identifier
           * from the properties of the external
           * reference
           */
          val tokens = EXTERNAL_PROPS
            .map(prop => {
              if (efJson.has(prop)) efJson.get(prop).getAsString
              else ""
            })

          val ident = java.util.UUID.fromString(tokens.mkString("|")).toString
          val target_ref = s"external-reference--$ident"
          /*
           * Note, the edge format is compliant to
           * NGSI v2 (see `ref` attribute)
           */
          val edgeJson = buildEdge(source_ref, target_ref, action)
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
            /*
             * Note, the edge format is compliant to
             * NGSI v2 (see `ref` attribute)
             */
            val edgeJson = buildEdge(source_ref, target_ref, action)
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

        val edgeJson = buildEdge(source_ref, target_ref, action)
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

                  val edgeJson = buildEdge(source_ref, target_ref, action)
                  edges += edgeJson

                })
              }
            }

          })
        }
      }

    })

    (nodes, edges)

  }

  private def buildEdge(source_ref:String, target_ref:String, action:String):JsonObject = {

    val edgeJson = new JsonObject
    edgeJson.addProperty(ACTION, action)

    val (ngsiId, ngsiType) = toNGSI(source_ref)
    edgeJson.addProperty(ID, ngsiId)
    edgeJson.addProperty(TYPE, ngsiType)

    val refJson = new JsonObject
    refJson.addProperty(TYPE, "Relationship")

    val (refNgsiId, refNgsiType) = toNGSI(target_ref)
    refJson.addProperty(VALUE, refNgsiId)

    val refAttr = s"ref$refNgsiType"
    edgeJson.add(refAttr, refJson)

    edgeJson

  }
  /**
   * This method transforms the MITRE relations
   * that are part of the delta objects into an
   * NGSI compliant JSON format.
   *
   * NOTE: The NGSI relation format is minimalistic
   * and does not contain any further attributes.
   */
  def extractEdges(deltaObjects:Seq[JsonElement]):Seq[JsonElement] = {

    val edges = mutable.ArrayBuffer.empty[JsonObject]

    val relations = extractRelations(deltaObjects)
    relations.foreach(obj => {

      val objJson  = obj.getAsJsonObject
      /*
       * NGSI V2 (default)
       * -------
       * edge = {
       *    // PARENT
       *    "id": "...",
       *    "type": "...",
       *    // CHILD
       *    "ref<referenced-type>": {
       *      "type": "Relationship",
       *      "value": "..."
       *    }
       * }
       *
       * NGSI-LD
       * -------
       * edge = {
       *    // PARENT
       *    "id": "...",
       *    "type": "...",
       *    // CHILD
       *    "<relationship_type>": {
       *      "type": "Relationship",
       *      "object": "..."
       *    }
       * }
       */
      val edgeJson = new JsonObject
      /*
       * When retrieving the delta objects, the respective
       * action (create | update) is inferred and added.
       */
      val action = if (objJson.has(ACTION))
        objJson.get(ACTION).getAsString else "create"

      edgeJson.addProperty(ACTION, action)

      val source_ref = if (objJson.has("source_ref"))
        objJson.get("source_ref").getAsString else ""

      val target_ref = if (objJson.has("target_ref"))
        objJson.get("source_ref").getAsString else ""

      if (source_ref.nonEmpty && target_ref.nonEmpty) {

        val (ngsiId, ngsiType) = toNGSI(source_ref)
        edgeJson.addProperty(ID, ngsiId)
        edgeJson.addProperty(TYPE, ngsiType)

        val refJson = new JsonObject
        refJson.addProperty(TYPE, "Relationship")

        val (refNgsiId, refNgsiType) = toNGSI(target_ref)
        refJson.addProperty(VALUE, refNgsiId)

        val refAttr = s"ref$refNgsiType"
        edgeJson.add(refAttr, refJson)

        edges += edgeJson

      }

    })

    edges

  }

  private def toNGSI(id:String):(String, String) = {

    val tokens = id.split("--")

    val ngsiType = toCamelCase(tokens.head)
    val ngsiId = s"urn:ngsi-ld:$ngsiType:${tokens.last}"

    (ngsiId, ngsiType)

  }
  /**
   * A private helper method to transform the type
   * of a STIX V2.1 domain object or cyber observable
   * into an NGSI complaint representation.
   */
  private def toCamelCase(text:String, separator:String="-"):String = {

    val tokens = text.split(separator)
    tokens
      .map(token => token.head.toUpper + token.tail)
      .mkString

  }

}

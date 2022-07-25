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

import com.google.gson.{JsonElement, JsonObject}

import scala.collection.mutable

object MitreTransform extends MitreNet {

  val ACTION:String = "action"
  val ID:String     = "id"
  val TYPE:String   = "type"
  val VALUE:String  = "value"

  def transform(deltaObjects:Seq[JsonElement]):Option[JsonObject] = ???

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
      val action = if (objJson.has(ACTION)) objJson.get(ACTION).getAsString else "create"
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

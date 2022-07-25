package de.kp.works.beats.mitre.model
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
import de.kp.works.beats.mitre.MitreFormats
import de.kp.works.beats.mitre.MitreFormats.MitreFormat

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable

trait MitreExternals {

  val EXTERNAL_REFS = "external_references"

  val EXTERNAL_PROPS = List(
    "description",
    "external_id",
    "source_name",
    "url"
  )
  /**
   * This method transforms external references
   * into STIX like objects to build additional
   * relationships between MITRE objects and
   * these references.
   *
   * This method supports CAPEC, ENTERPRISE, ICS
   * and MOBILE and distinguishes between JSON
   * and NGSI data format.
   */
  def extractExternals(objects:Seq[JsonElement], format:MitreFormat = MitreFormats.JSON):Seq[JsonObject] = {

    val externals = mutable.ArrayBuffer.empty[JsonObject]

    objects.foreach(obj => {
      val objJson = obj.getAsJsonObject
      if (objJson.has(EXTERNAL_REFS)) {

        val efs = objJson.get(EXTERNAL_REFS).getAsJsonArray
        efs.foreach(ef => {

          val efJson = ef.getAsJsonObject
          /*
           * Extract attributes from the external
           * reference
           */
          val attributes = EXTERNAL_PROPS.map(prop => {
            val value = if (efJson.has(prop))
              efJson.get(prop).getAsString else ""

            (prop, value)

          }).toMap
          /*
           * Build identifier pattern
           */
          val tokens = attributes.values.toSeq
          val ident = java.util.UUID.fromString(tokens.mkString("|")).toString

          val externalJson = new JsonObject
          format match {
            case MitreFormats.JSON =>
              /*
               * Build unique identifier `id` and
               * object `type`
               */
              val id = s"external-reference--$ident"

              externalJson.addProperty("id", id)
              externalJson.addProperty("type", "external-reference")
              /*
               * Assign attributes
               */
              attributes.foreach{case(k,v) => externalJson.addProperty(k,v)}

            case MitreFormats.NGSI =>

              val ngsiType = "ExternalReference"
              val ngsiId = s"urn:ngsi-ld:$ngsiType:$ident"

              externalJson.addProperty("id", ngsiId)
              externalJson.addProperty("type", ngsiType)

              val action = if (objJson.has("action"))
                objJson.get("action").getAsString else "create"

              externalJson.addProperty("action", action)
              /*
               * Assign attributes
               */
              attributes.foreach{case(k,v) =>
                val attrJson = new JsonObject
                attrJson.add("metadata", new JsonObject)

                attrJson.addProperty("type", "String")
                attrJson.addProperty("value", v)

                externalJson.add(k, attrJson)
              }

          }

          externals += externalJson

        })
      }
    })

    externals.distinct

  }

}

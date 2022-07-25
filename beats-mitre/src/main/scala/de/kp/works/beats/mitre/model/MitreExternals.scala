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
   * and MOBILE
   */
  def extractExternals(objects:Seq[JsonElement]):Seq[JsonObject] = {

    val externals = mutable.ArrayBuffer.empty[JsonObject]

    objects.foreach(obj => {
      val objJson = obj.getAsJsonObject
      if (objJson.has(EXTERNAL_REFS)) {

        val efs = objJson.get(EXTERNAL_REFS).getAsJsonArray
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

}

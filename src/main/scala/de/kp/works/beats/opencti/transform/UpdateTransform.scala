package de.kp.works.beats.opencti.transform
/*
 * Copyright (c) 2020 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.JsonObject

object UpdateTransform extends BaseTransform {
  /*
   * SAMPLE UPDATE EVENT
   *
   *   data: {
   *     x_opencti_patch: {
   *       replace: { threat_actor_types: { current: ['competitor', 'crime-syndicate'], previous: ['competitor'] } },
   *     },
   *     id: 'threat-actor--b3486bf4-2cf8-527c-ab40-3fd2ff54de77',
   *     x_opencti_id: 'f499ceab-b3bf-4f39-827d-aea43beed391',
   *     type: 'threat-actor',
   *   }
   *
   */
  def transform(payload:Map[String,Any]):Option[String] = {
    /*
     * Extract patch information, determine patch operation
     * and extract the associated attributes and values
     */
    val patch = {
      if (payload.contains("x_opencti_patch")) {
        payload("x_opencti_patch").asInstanceOf[Map[String, Any]]
      }
      else
        Map.empty[String, Any]
    }

    if (patch.isEmpty) return None

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None
    /*
     * Build initial JSON output object; this object
     * represents an NGSI entity
     */
    val entityJson = new JsonObject
    entityJson.addProperty("id",   entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as attribute
     */
    val attrJson = new JsonObject
    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "create")

    entityJson.add("action", attrJson)
    /*
     * The keys are `replace`, `add` and `remove` and
     * multiple modes are supported
     */
    patch.keySet.foreach(mode => {
      if (patch.contains(mode)) {
        /*
         * Extract attribute names
         */
        val attrNames = patch(mode).asInstanceOf[Map[String, Any]].keySet
        /*
        * The update operation is added as metadata
        * to the transformed entity
        */
        val metaJson = new JsonObject
        metaJson.addProperty("mode", mode)

        attrNames.foreach(attrName => {
          mode match {
            case "add" | "remove" =>
              /*
               * The content of the patch message can be a list
               * of attribute values or a list of reference maps
               *
               * Reference maps contain an internal OpenCTI id
               * and a value. As the purpose of this Beat is to
               * publish data to an external system, references
               * to internal identifiers are skipped
               */
              val content = patch(mode)
                .asInstanceOf[Map[String, Any]](attrName).asInstanceOf[List[Any]]

              if (content.nonEmpty) {

                val attrJson = new JsonObject
                attrJson.add("metadata", metaJson)

                val values = content.map {
                  case map: Map[String, Any] => map("value")
                  case entry => entry
                }

                val head = values.head
                val basicType = getBasicType(head)

                if (values.size == 1) {
                  val attrType = basicType
                  attrJson.addProperty("type", attrType)

                  putValue(head, basicType, attrJson)

                }
                else {
                  val attrType = s"List[$basicType]"
                  attrJson.addProperty("type", attrType)

                  putValues(values, basicType, attrJson)
                }

                entityJson.add(attrName, attrJson)

              }
            case "replace" =>
              /*
               * { current, previous }
               */
              val content = patch(mode)
                .asInstanceOf[Map[String, Any]](attrName).asInstanceOf[Map[String, Any]]

              val attrJson = new JsonObject
              attrJson.add("metadata", metaJson)

              val current = content("current")
              current match {
                case values: List[Any] =>

                  val head = values.head
                  val basicType = getBasicType(head)

                  val attrType = s"List[$basicType]"
                  attrJson.addProperty("type", attrType)

                  putValues(values, basicType, attrJson)

                case _ =>
                  val basicType = getBasicType(current)

                  val attrType = basicType
                  attrJson.addProperty("type", attrType)

                  putValue(current, basicType, attrJson)
              }

              entityJson.add(attrName, attrJson)
            case _ =>
              val now = new java.util.Date().toString
              throw new Exception(s"[ERROR] $now - Patch operation `$mode` is not supported.")
          }

       })
      }
    })
    /*
     * Extract other attributes from the
     * provided payload
     */
    val filter = Seq("id", "type", "x_opencti_patch")
    val keys = payload.keySet.filter(key => !filter.contains(key))

    fillEntity(payload, keys, entityJson)
    Some(entityJson.toString)

  }
}

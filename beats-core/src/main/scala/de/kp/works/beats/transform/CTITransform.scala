package de.kp.works.beats.transform

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
import de.kp.works.beats.BeatsTransform
import de.kp.works.beats.events.SseEvent
/**
 * The [CTITransform] class retrieves SSE events from OpenCTI
 * and transforms them into an NGSI v2 compliant format.
 *
 * The current implementation focuses on the STIX data part
 * of the overall SSE message, and ignores other fields like
 * markings, origin, message or version.
 */
class CTITransform extends BeatsTransform {
  /*
   * Events format
   *
   * The events published by OpenCTI are based on the STIX format:
   *
   * id: {Event stream id} -> Like 1620249512318-0
   * event: {Event type} -> create / update / delete
   * data: { -> The complete event data
   *    markings: [] -> Array of markings IDS of the element
   *    origin: {Data Origin} -> Complex object with different information about the origin of the event
   *    data: {STIX data} -> The STIX representation of the data.
   *    message -> A simple string to easy understand the event
   *    version -> The version number of the event
   * }
   *
   *   {
    id: '1619436079034-0',
    topic: 'update',
    data: {
      markings: [],
      origin: {
        ip: '::1',
        user_id: '88ec0c6a-13ce-5e39-b486-354fe4a7084f',
        referer: 'http://localhost:4000/dashboard/threats/threat_actors/f499ceab-b3bf-4f39-827d-aea43beed391',
      },
      data: {
        x_opencti_patch: {
          replace: { threat_actor_types: { current: ['competitor', 'crime-syndicate'], previous: ['competitor'] } },
        },
        id: 'threat-actor--b3486bf4-2cf8-527c-ab40-3fd2ff54de77',
        x_opencti_id: 'f499ceab-b3bf-4f39-827d-aea43beed391',
        type: 'threat-actor',
      },
      message: 'replaces the `threat_actor_types` by `current: competitor,crime-syndicate, previous: competitor`',
      version: '2',
    },
  },
   *
   */
  def transform(sseEvent:SseEvent, namespace:String):Option[JsonObject] = {
    /*
     * This transform method supports the STIX data format version 0.2,
     * starting with version v4.5.1 of OpenCTI.
     */
    try {

      val event = sseEvent.eventType
      val data = mapper.readValue(sseEvent.data, classOf[Map[String, Any]])

      /**
       * STEP #1: Unpack the event data
       */
      val payload = {
        if (data.contains("data")) {
          data("data").asInstanceOf[Map[String, Any]]
        }
        else
          Map.empty[String, Any]
      }

      if (payload.isEmpty) return None
      /*
       * Build unified SSE event format that is
       * harmonized with all other Beat event output
       * formats.
       *
       * OpenCTI distinguishes `create`, `delete`,
       * `update` etc events
       */
      event match {
        case "create" =>
          val eventType = s"beat/$namespace/create"
          val eventData = transformCreate(payload)

          if (eventData.isDefined) {

            val sseEvent = new JsonObject
            sseEvent.addProperty("type", eventType)
            sseEvent.addProperty("event", eventData.get.toString)

            Some(sseEvent)

          } else None

        case "delete" =>
          val eventType = s"beat/$namespace/delete"
          val eventData = transformDelete(payload)

          if (eventData.isDefined) {

            val sseEvent = new JsonObject
            sseEvent.addProperty("type", eventType)
            sseEvent.addProperty("event", eventData.get.toString)

            Some(sseEvent)

          } else None

        case "merge" | "sync" => None
        case "update" =>
          val eventType = s"beat/$namespace/update"
         /*
           * This implementation is based on the OpenCTI
           * documentation for stream events (v4.5.1) and
           * STIX data version v02
           */
          val eventData = transformUpdate(payload)

          if (eventData.isDefined) {

            val sseEvent = new JsonObject
            sseEvent.addProperty("type", eventType)
            sseEvent.addProperty("event", eventData.get.toString)

            Some(sseEvent)

          } else None

        case _ =>
          val now = new java.util.Date().toString
          throw new Exception(s"[ERROR] $now - Unknown event type detected: $event")
      }

    } catch {
      case _:Throwable => None
    }

  }

  def transformCreate(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None
    /*
     * Build initial JSON output object, which
     * represents an NGSI entity
     */
    val entityJson = new JsonObject
    entityJson.addProperty("id", entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as NGSI compliant
     * attribute specification
     */
    val attrJson = new JsonObject
    attrJson.add("metadata", new JsonObject)

    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "create")

    entityJson.add("action", attrJson)

    val filter = Seq("id", "type")

    val keys = payload.keySet.filter(key => !filter.contains(key))
    fillEntity(payload, keys, entityJson)

    Some(entityJson)

  }

  def transformDelete(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None
    /*
     * Build initial JSON output object; this object
     * represents an NGSI entity
     */
    val entityJson = new JsonObject
    entityJson.addProperty("id", entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as attribute
     */
    val attrJson = new JsonObject
    attrJson.add("metadata", new JsonObject)

    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "delete")

    entityJson.add("action", attrJson)
    /*
     * Extract other attributes from the
     * provided payload
     */
    val filter = Seq("id", "type")
    val keys = payload.keySet.filter(key => !filter.contains(key))

    fillEntity(payload, keys, entityJson)
    Some(entityJson)

  }

  def transformUpdate(payload: Map[String, Any]): Option[JsonObject] = {
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
    entityJson.addProperty("id", entityId)
    entityJson.addProperty("type", entityType)
    /*
     * Add data operation as attribute
     */
    val attrJson = new JsonObject
    attrJson.add("metadata", new JsonObject)

    attrJson.addProperty("type", "String")
    attrJson.addProperty("value", "update")

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
                .asInstanceOf[Map[String, Any]](attrName)
                .asInstanceOf[List[Any]]

              if (content.nonEmpty) {

                val attrJson = new JsonObject
                attrJson.add("metadata", metaJson)
                /*
                 * Transform content into a list of plain values
                 */
                val values = content.map {
                  /*
                   * List values specify a plain value or a map,
                   * where the respective value is defined as `value`
                   * field.
                   */
                  case map: Map[_, Any] => map
                    .asInstanceOf[Map[String, Any]]("value")
                  case entry => entry
                }
                /*
                 * Determine data from head element
                 */
                val head = values.head
                val basicType = getBasicType(head)

                if (values.size == 1) {

                  val attrType = basicType
                  attrJson.addProperty("type", attrType)

                  val attrValu = mapper.writeValueAsString(head)
                  attrJson.addProperty("value", attrValu)

                }
                else {
                  val attrType = s"List[$basicType]"
                  attrJson.addProperty("type", attrType)

                  val attrValu = mapper.writeValueAsString(values)
                  attrJson.addProperty("value", attrValu)
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

                  val attrValu = mapper.writeValueAsString(values)
                  attrJson.addProperty("value", attrValu)

                case _ =>
                  val basicType = getBasicType(current)

                  val attrType = basicType
                  attrJson.addProperty("type", attrType)

                  val attrValu = mapper.writeValueAsString(current)
                  attrJson.addProperty("value", attrValu)

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
    Some(entityJson)

  }

}

package de.kp.works.beats.transform

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

import com.google.gson.JsonObject
import de.kp.works.beats.events.SseEvent
import de.kp.works.beats.transform.stix.STIXTransform
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
            sseEvent.addProperty(TYPE, eventType)
            sseEvent.addProperty(EVENT, eventData.get.toString)

            Some(sseEvent)

          } else None

        case "delete" =>
          val eventType = s"beat/$namespace/delete"
          val eventData = transformDelete(payload)

          if (eventData.isDefined) {

            val sseEvent = new JsonObject
            sseEvent.addProperty(TYPE, eventType)
            sseEvent.addProperty(EVENT, eventData.get.toString)

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
            sseEvent.addProperty(TYPE, eventType)
            sseEvent.addProperty(EVENT, eventData.get.toString)

            Some(sseEvent)

          } else None

        case _ =>
          throw new Exception(s"Unknown event type detected: $event")
      }

    } catch {
      case t:Throwable =>

        val message = s"OpenCTI event transformation failed: ${t.getLocalizedMessage}"
        error(message)

        None

    }

  }

  def transformCreate(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None

    val filter = Seq(ID, TYPE)
    val data = payload.filterKeys(k => !filter.contains(k))
    /*
     * The subsequent processing extracts `entities`
     * and `relationships` from the provided payload.
     *
     * Entities (v) and relations (e) are specified in
     * accordance with the NGSI specification.
     */
    val (v,e) = STIXTransform.transformCreate(entityId, entityType, data)

    val resultJson = new JsonObject
    resultJson.addProperty(FORMAT, "event")

    if (v.isDefined) resultJson.add(ENTITIES,  v.get)
    if (e.isDefined) resultJson.add(RELATIONS, e.get)

    Some(resultJson)

  }

  def transformDelete(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None

    val filter = Seq(ID, TYPE)
    val data = payload.filterKeys(k => !filter.contains(k))
    /*
     * The subsequent processing extracts `entities` and
     * `relationships from the provided payload
     *
     * Entities (v) and relations (e) are specified in
     * accordance with the NGSI specification.
     */
    val (v,e) = STIXTransform.transformDelete(entityId, entityType, data)

    val resultJson = new JsonObject
    resultJson.addProperty(FORMAT, "event")

    if (v.isDefined) resultJson.add(ENTITIES,  v.get)
    if (e.isDefined) resultJson.add(RELATIONS, e.get)

    Some(resultJson)

  }

  def transformUpdate(payload: Map[String, Any]): Option[JsonObject] = {

    val entityId = payload.getOrElse("id", "").asInstanceOf[String]
    val entityType = payload.getOrElse("type", "").asInstanceOf[String]

    if (entityId.isEmpty || entityType.isEmpty) return None

    val filter = Seq(ID, TYPE)
    val data = payload.filterKeys(k => !filter.contains(k))
    /*
     * The subsequent processing extracts `entities` and
     * `relationships from the provided payload
     *
     * Entities (v) and relations (e) are specified in
     * accordance with the NGSI specification.
     */
    val (v,e) = STIXTransform.transformUpdate(entityId, entityType, data)

    val resultJson = new JsonObject
    resultJson.addProperty(FORMAT, "event")

    if (v.isDefined) resultJson.add(ENTITIES,  v.get)
    if (e.isDefined) resultJson.add(RELATIONS, e.get)

    Some(resultJson)

  }

}

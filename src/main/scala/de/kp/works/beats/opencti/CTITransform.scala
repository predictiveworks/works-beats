package de.kp.works.beats.opencti
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
import de.kp.works.beats.opencti.transform._

/**
 * OpenCTI events do not respect the NGSI format.
 *
 * This transformer can be used to harmonize the
 * OpenCTI events to a standard format.
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

      val id = sseEvent.eventId
      val event = sseEvent.eventType

      val data = mapper.readValue(sseEvent.data, classOf[Map[String, Any]])

      /**
       * STEP #1: Unpack the event data
       */

      /** markings **/

      val markings = {
        if (data.contains("markings")) {
          data("markings").asInstanceOf[List[Map[String, Any]]]
        }
        else
          List.empty[Map[String, Any]]
      }

      /** origin **/

      val origin = {
        if (data.contains("origin")) {
          data("origin").asInstanceOf[Map[String, Any]]
        }
        else
          Map.empty[String, Any]
      }

      /** data **/

      val payload = {
        if (data.contains("data")) {
          data("data").asInstanceOf[Map[String, Any]]
        }
        else
          Map.empty[String, Any]
      }

      /** message **/

      val message = data.getOrElse("message", "").asInstanceOf[String]

      /** version **/

      val version = {
        if (data.contains("version")) {
          val value = data("version")
          value match {
            case i: Int => i.toString
            case _ => value.asInstanceOf[String]
          }
        }
        else "1"
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
          val eventData = CreateTransform.transform(payload)

          if (eventData.isDefined) {

            val sseEvent = new JsonObject
            sseEvent.addProperty("type", eventType)
            sseEvent.addProperty("event", eventData.get.toString)

            Some(sseEvent)

          } else None

        case "delete" =>
          val eventType = s"beat/$namespace/delete"
          val eventData = DeleteTransform.transform(payload)

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
          val eventData = UpdateTransform.transform(payload)

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
}

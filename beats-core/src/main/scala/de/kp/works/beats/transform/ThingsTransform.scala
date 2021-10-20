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
import de.kp.works.beats.events.MqttEvent

class ThingsTransform extends BeatsTransform {
  /*
   * Subscribing to topic v1/gateway/attributes results in messages of the format:
   *
   * Message: {"device": "Device A", "data": {"attribute1": "value1", "attribute2": 42}}
   */
  def transform(mqttEvent:MqttEvent, namespace:String):Option[JsonObject] = {

    try {

      val message = mapper.readValue(mqttEvent.json, classOf[Map[String, Any]])
      val device = message("device").asInstanceOf[String]

      val data = message("data").asInstanceOf[Map[String, Any]]
      /*
       * The payload of the [MqttEvent] is transformed into
       * an NGSI v2 compliant JSON object:
       *
       * {
       *   "id": ...
       *   "type": "device",
       *   "attribute1": {
       *     "metadata": {},
       *     "type": inferred data type
       *     "value": ...
       *   }
       * }
       */
      val entityJson = new JsonObject
      entityJson.addProperty("id", device)
      /*
       * The message received from ThingsBoard does not
       * define the `type` of the respective device.
       */
      entityJson.addProperty("type", "device")
      fillEntity(data, data.keySet, entityJson)
      /*
       * For compliance with other Works Beats, the SSE
       * is transformed into a unified format
       */
      val eventType = s"beat/$namespace/device"

      val sseEvent = new JsonObject
      sseEvent.addProperty("type", eventType)
      sseEvent.addProperty("event", entityJson.toString)

      Some(sseEvent)

    } catch {
      case _:Throwable => None
    }
  }

}

package de.kp.works.beats.thingsboard
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


import com.google.gson.{JsonObject, JsonParser}
import de.kp.works.beats.BeatsTransform

object ThingsTransform extends BeatsTransform {
  /*
   * Subscribing to topic v1/gateway/attributes results in messages of the format:
   *
   * Message: {"device": "Device A", "data": {"attribute1": "value1", "attribute2": 42}}
   */
  def transform(event:MqttEvent):String = {

    try {

      val message = mapper.readValue(event.json, classOf[Map[String, Any]])
      val device = message("device").asInstanceOf[String]

      val data = message("data").asInstanceOf[Map[String, Any]]
      // TODO

      null

    } catch {
      case t:Throwable => mapper.writeValueAsString(event)
    }
  }

}

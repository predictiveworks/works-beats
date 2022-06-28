package de.kp.works.beats.fiware.publishers

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
import de.kp.works.beats.BeatsLogging
import de.kp.works.beats.fiware.Fiware

abstract class BasePublisher extends Fiware with BeatsLogging {
  /**
   * `eventData` is a JSON object with the following
   * format:
   *
   * {
   *  "format": "...",
   *  "entity": {
   *    "id": "...",
   *    "type": "...",
   *    "timestamp": {...},
   *    "rows": [
   *      {
   *        "action": {...},
   *        "<column>": {...},
   *
   *      }
   *    ]
   *  }
   * }
   */
  def publish(eventData:JsonElement):Unit

  protected def entityCreate(entity:JsonObject):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityCreateUrl

    try {
      // TODO
      true

    } catch {
      case _:Throwable => false
    }

  }

  protected def entityDelete(entity:JsonObject):Boolean = {

    val entityId = entity.get("id").toString

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityDeleteUrl.replace("{id}", entityId)

    try {
      // TODO
      true

    } catch {
      case _:Throwable => false
    }

  }

  protected def entityUpdate(entity:JsonObject):Boolean = {

    val entityId = entity.get("id").toString

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityUpdateUrl.replace("{id}", entityId)

    try {
      // TODO
      true

    } catch {
      case _:Throwable => false
    }

  }

  protected def entityExists(entityId:String):Boolean = {

    val headers = Map.empty[String,String]
    val endpoint = getBrokerUrl + entityGetUrl.replace("{id}", entityId)

    try {

      val bytes = get(endpoint, headers, pooled = true)
      extractJsonBody(bytes)

      true

    } catch {
      case _:Throwable => false
    }

  }

}

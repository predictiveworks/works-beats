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

object ZEEK extends BasePublisher {
  /**
   * `eventData` is a JSON object with the following
   * NGSI compliant format:
   *
   * {
   * "entity": {
   * "id": "...",
   * "type": "...",
   * "<attribute>": {...},
   * ...
   * }
   */
  override def publish(eventData: JsonElement): Unit = {
    /*
     * The `eventData` element is either a JsonObject
     * or a JsonNull
     */
    if (eventData.isJsonNull) {
      val message = s"Zeek event is empty and will not be published."
      info(message)

    } else {

      val eventJson = eventData.getAsJsonObject
      /*
       * Check whether the provided entity already
       * exists
       */
      val entityId = eventJson.get(ID).getAsString
      if (entityExists(entityId))
        publishUpdate(eventJson)

      else
        publishCreate(eventJson)

    }

  }

  protected def publishCreate(entityJson: JsonObject): Unit = {
    entityCreate(entityJson)
  }

  protected def publishUpdate(eventJson:JsonObject):Unit = {
    /*
     * The respective Fleet entity (table) exists
     * and this request either updates or deletes
     * a certain set of attributes
     *
     * Extract entity identifier, type, action and
     * attributes
     */
    val entityId = eventJson.remove(ID).getAsString
    eventJson.remove(TYPE).getAsString
    /*
     * The remaining event JSON represents the attributes
     * associated with this event
     */
    val attrs = eventJson
    attributesUpdate(entityId, attrs)

  }

}

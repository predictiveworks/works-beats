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

import scala.collection.JavaConversions.iterableAsScalaIterable
/**
 * [FLEET] publishes Osquery events that refer to the
 * default result format. Each log line represents a
 * state change.
 *
 * This format works best for log aggregation systems
 * like Logstash or Splunk.
 */
object FLEET extends BasePublisher {
  /**
   * `eventData` is a JSON object with the following
   * format:
   *
   * {
   * "format": "...",
   * "entity": {
   * "id": "...",
   * "type": "...",
   * "timestamp": {...},
   * "rows": [
   * {
   * "action": {...},
   * "<column>": {...},
   *
   * }
   * ]
   * }
   * }
   */
  override def publish(eventData: JsonElement): Unit = {
    /*
     * The `eventData` element is either a JsonObject
     * or a JsonNull
     */
    if (eventData.isJsonNull) {
      val message = s"Fleet event is empty and will not be published."
      info(message)

    } else {

      val eventJson = eventData.getAsJsonObject
      val format = eventJson
        .remove("format").getAsString
      /*
       * Fleet events are provided in 3 different formats
       *
       * - event
       *   -----
       *   Event is the default result format. Each log line
       *   represents a state change. This format works best
       *   for log aggregation systems like Logstash or Splunk.
       *
       * - differential
       *   ------------
       *   Differential changes describe changes between the last
       *   (most recent) query execution and the current execution.
       *
       *   Each log line indicates what data has been added/removed
       *   by which query. The first time the query is executed (there
       *   is no "last" run), the last run is treated as having null
       *   results, so the differential consists entirely of log lines
       *   with the added indication.
       *
       * - snapshot
       *   --------
       *   Snapshot queries attempt to mimic the differential
       *   event format, but instead of specified `added` or
       *   `removed` rows, its a set of rows (per table) that
       *   describe the current state
       *
       * The format supported to send threat context to the FIWARE
       * context broker is restricted to `event'
       */
      if (format != "event") return
      /*
       * At this stage, it is unknown whether the respective entity
       * already exists.
       * {
       *  "id": "..",
       *  "type": ".."
       *  "timestamp": {
       *    "metadata": {},
       *    "type": "Long",
       *    "value": ...
       *  },
       *  "rows": [
       *    {...}
       *  ]
       * }
       *
       * As a first step, the provided rows with
       * a single row element are extracted and
       * added as NGSI attributes to the entity.
       */
      flattenRow(eventJson)
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

  protected def publishCreate(entityJson:JsonObject):Unit = {
    /*
     * Remove `action` field and evaluate whether
     * a `removed` action is specified
     */
    val action = entityJson.remove(ACTION).getAsString
    if (action == "added") {
      /*
       * As a second step, send post request to
       * FIWARE context broker create `entity`
       */
      entityCreate(entityJson)
    }
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

    val action = eventJson.remove(ACTION).getAsString
    /*
     * The remaining event JSON represents the attributes
     * associated with this event
     */
    val attrs = eventJson
    action match {
      case "added" =>
        attributesAppend(entityId, attrs)

      case "removed" =>
        attrs.keySet
          .foreach(attrName => attributeDelete(entityId, attrName))

      case _ => /* Do nothing */
    }

  }

  private def extractRow(eventJson:JsonObject):Option[JsonObject] = {

    val rows = eventJson.remove(ROWS)
    val row = {
      val rowsJson = rows.getAsJsonArray
      if (rowsJson.nonEmpty)
        Some(rowsJson.head.getAsJsonObject)

      else
        None
    }

    row

  }
  /**
   * Fleet event handling is synchronized with other
   * event sources, i.e. the set of attributes is defined
   * as a single row in rows.
   */
  private def flattenRow(entityJson:JsonObject):Unit = {

    val row = extractRow(entityJson)
    if (row.nonEmpty) {

      val rowJson = row.get
      rowJson.keySet
        .foreach(key => {
          /*
           * Extract the NGSI attribute from the
           * row and determine whether to remove
           * the `action` field
           */
          val attrJson = rowJson.get(key).getAsJsonObject
          if (attrJson.has(ACTION)) attrJson.remove(ACTION)
          /*
           * Assign optionally cleaned attribute
           * to the entity JSON representation
           */
          entityJson.add(key, attrJson)
        })
    }

  }

}

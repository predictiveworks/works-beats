package de.kp.works.beats.tls
/*
 * Copyright (c) 2021 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.{JsonArray, JsonObject}
import de.kp.works.beats.tls.TLSConstants._
import de.kp.works.beats.tls.redis.OsqueryNode

import java.text.SimpleDateFormat

object TLSTransform {
  /**
   * This method receives a node and its query result `data`
   * and converts the incoming log data into a series of fields,
   * normalizing and/or aggregating both batch and event format
   * into batch format, which is used throughout the rest of this
   * service.
   */
  def transform(node: OsqueryNode, data: JsonArray): JsonArray = {
    /*
     * Extract node meta information
     */
    val uuid = node.uuid
    val host = node.hostIdentifier

    val events = new JsonArray

    val iter = data.iterator
    while (iter.hasNext) {

      val item = iter.next.getAsJsonObject
      /*
       * STEP #1: Extract `name` (of the query) and normalize
       * `calendarTime`:
       *
       * "calendarTime": "Tue Sep 30 17:37:30 2014"
       *
       * - Weekday as locale’s abbreviated name
       *
       * - Month as locale’s abbreviated name
       *
       * - Day of the month as a zero-padded decimal number
       *
       * - H:M:S
       *
       * - Year with century as a decimal number
       *
       * UTC
       */
      val name = item.get(NAME).getAsString

      val calendarTime = item.get(CALENDAR_TIME).getAsString
      val datetime = transformCalTime(calendarTime)

      val timestamp = datetime.getTime

      if (item.get(COLUMNS) != null) {

        try {
          /*
           * In this case, `event format`, columns is a single
           * object that must be added to the overall output
           */
          val action = item.get(ACTION).getAsString
          val columns = item.get(COLUMNS).getAsJsonObject

          val field = buildField(uuid, host, timestamp, name, action, columns)
          events.add(field)

        } catch {
          case _: Throwable => /* Do nothing */
        }

      }
      else if (item.get(DIFF_RESULTS) != null) {

        val diffResults = item.get(DIFF_RESULTS).getAsJsonObject
        if (diffResults.get(ADDED) != null) {

          val added = diffResults.get(ADDED).getAsJsonArray
          (0 until added.size).foreach(i => {

            val columns = added.get(i)
            try {
              val field = buildField(uuid, host, timestamp, name, "added", columns.getAsJsonObject)
              events.add(field)

            } catch {
              case _: Throwable => /* Do nothing */
            }

          })
        }

        if (diffResults.get(REMOVED) != null) {

          val removed = diffResults.get(REMOVED).getAsJsonArray
          (0 until removed.size).foreach(i => {

            val columns = removed.get(i)
            try {
              val field = buildField(uuid, host, timestamp, name, "removed", columns.getAsJsonObject)
              events.add(field)

            } catch {
              case _: Throwable => /* Do nothing */
            }

          })
        }
      }
      else if (item.get(SNAPSHOT) != null) {

        val snapshot = item.get(SNAPSHOT).getAsJsonArray
        (0 until snapshot.size).foreach(i => {

          val columns = snapshot.get(i)
          try {
            val field = buildField(uuid, host, timestamp, name, "snapshot", columns.getAsJsonObject)
            events.add(field)

          } catch {
            case _: Throwable => /* Do nothing */
          }

        })
      }
      else {
        throw new Exception("Query result encountered that cannot be transformed.")
      }

    }

    events

  }

  private def buildField(node: String, host: String, timestamp: Long, name: String, action: String, columns: JsonObject): JsonObject = {

    val field = new JsonObject

    /* Node meta information */

    field.addProperty(NODE, node)
    field.addProperty(HOST, host)

    /* Entry information */

    field.addProperty(NAME, name)
    field.addProperty(ACTION, action)

    field.addProperty(TIMESTAMP, timestamp)
    field.add(COLUMNS, columns)

    field

  }

  private def transformCalTime(s: String): java.sql.Date = {

    try {

      /* Osquery use UTC to describe datetime */
      val pattern = if (s.endsWith("UTC")) {
        "EEE MMM dd hh:mm:ss yyyy z"
      }
      else
        "EEE MMM dd hh:mm:ss yyyy"


      val format = new SimpleDateFormat(pattern, java.util.Locale.US)
      format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))

      val date = format.parse(s)
      new java.sql.Date(date.getTime)

    } catch {
      case _: Throwable => null
    }

  }

}

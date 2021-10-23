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
import scala.collection.JavaConversions._
import scala.collection.mutable

object TLSTransform {

  private val X_WORKS_ACTION    = "x_works_action"
  private val X_WORKS_HOSTNAME  = "x_works_hostname"
  private val X_WORKS_NAME      = "x-works_name"
  private val X_WORKS_TIMESTAMP = "x_works_timestamp"

  /**
   * This method receives a node and its query result `data`
   * and converts the incoming log data into a series of fields,
   * normalizing and/or aggregating both batch and event format
   * into batch format, which is used throughout the rest of this
   * service.
   */
  def transform(node: OsqueryNode, data: JsonArray): Map[String, JsonArray] = {
    /*
     * Extract node meta information
     */
    val uuid = node.uuid
    val host = node.hostIdentifier

    val result = mutable.Map.empty[String, JsonArray]

    val iter = data.iterator
    while (iter.hasNext) {

      val oldObj = iter.next.getAsJsonObject
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
      val commonObj = new JsonObject

      val name = oldObj.get(NAME).getAsString
      commonObj.addProperty(X_WORKS_NAME, name)

      val calendarTime = oldObj.get(CALENDAR_TIME).getAsString
      val datetime = transformCalTime(calendarTime)

      val timestamp = datetime.getTime
      commonObj.addProperty(X_WORKS_TIMESTAMP, timestamp)

      commonObj.addProperty(X_WORKS_HOSTNAME, host)
      if (oldObj.get(COLUMNS) != null) {

        try {

          val batchObj = commonObj

          val action = oldObj.get(ACTION).getAsString
          batchObj.addProperty(X_WORKS_ACTION, action)
          /*
           * Extract log event specific format and thereby
           * assume that the columns provided are the result
           * of an offline configuration process.
           *
           * NOTE: the mechanism below does not work for adhoc
           * (distributed) queries.
           */
          val columns = oldObj.get("columns").getAsJsonObject
          /*
           * Extract the column names in ascending order to
           * enable a correct match with the schema definition
           */
          val colnames = columns.keySet.toSeq.sorted
          colnames.foreach(colName => batchObj.add(colName, columns.get(colName)))

          if (!result.contains(name))
            result += name -> new JsonArray

          result(name).add(batchObj)

        } catch {
          case _: Throwable => /* Do nothing */
        }

      }
      else if (oldObj.get(DIFF_RESULTS) != null) {

        val diffResults = oldObj.get(DIFF_RESULTS).getAsJsonObject

        val actions = diffResults.keySet().toSeq.sorted
        actions.foreach(action => {

          val data = diffResults.get(action).getAsJsonArray
          data.foreach(columns => {

            val batchObj = commonObj
            batchObj.addProperty(X_WORKS_ACTION, action)

            val colnames = columns.getAsJsonObject.keySet.toSeq.sorted
            colnames.foreach(colName => batchObj.add(colName, columns.getAsJsonObject.get(colName)))

            if (!result.contains(name))
              result += name -> new JsonArray

            result(name).add(batchObj)

          })

        })

      }
      else if (oldObj.get(SNAPSHOT) != null) {

        val snapshot = oldObj.get(SNAPSHOT).getAsJsonArray
        snapshot.foreach(columns => {

          val batchObj = commonObj
          batchObj.addProperty(X_WORKS_ACTION, "snapshot")

          val colnames = columns.getAsJsonObject.keySet.toSeq.sorted
          colnames.foreach(colName => batchObj.add(colName, columns.getAsJsonObject.get(colName)))

          if (!result.contains(name))
            result += name -> new JsonArray

          result(name).add(batchObj)

        })
      }
      else {
        throw new Exception("Query result encountered that cannot be transformed.")
      }

    }

    result.toMap

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

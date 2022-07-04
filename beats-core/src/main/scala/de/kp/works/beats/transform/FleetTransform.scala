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

import com.google.gson.{JsonArray, JsonObject, JsonParser}
import de.kp.works.beats.events.FileEvent
import de.kp.works.beats.transform.FleetFormats.{RESULT, STATUS}

import java.text.SimpleDateFormat
import scala.collection.JavaConversions._

object FleetFormats extends Enumeration {

  type FleetFormat = Value

  val RESULT: FleetFormats.Value = Value(1, "result.log")
  val STATUS: FleetFormats.Value = Value(2, "status.log")

}

object FleetFormatUtil {

  def fromFile(fileName:String):FleetFormats.Value = {

    val formats = FleetFormats.values.filter(format => {
      fileName.contains(format.toString)
    })

    if (formats.isEmpty) return null
    formats.head

  }
}
class FleetTransform extends FileTransform {

  override def transform(fileEvent:FileEvent, namespace:String):Option[JsonObject] = {
    /*
     * The Osquery (Fleet) Manager creates and updates 2 different
     * files, result.log and status.log; the file name is used as
     * event type.
     */
    val format = FleetFormatUtil.fromFile(fileEvent.eventType)

    format match {
      case RESULT =>
        /*
         * A log line of the Osquery (Fleet) Manager result.log
         * refers to a certain `query` name, the respective action
         * and the affected columns of the Osquery table(s).
         *
         * The log line transformed into a more meaningful format
         * and the query name is used as the event type
         */
        val log = JsonParser.parseString(fileEvent.eventData).getAsJsonObject
        val (table, batch) = transformLog(log)

        val json = new JsonObject

        json.addProperty(TYPE, s"beat/$namespace/$table")
        json.addProperty(EVENT, batch)

        Some(json)

      case STATUS =>
        /*
         * Osquery creates status logs of its own execution,
         * for log levels INFO, WARNING and ERROR.
         *
         * Note, this implementation expects a single log file
         * that contains status messages of all levels.
         */
        val json = new JsonObject

        json.addProperty(TYPE, s"beat/$namespace/osquery_status")
        json.addProperty(EVENT, fileEvent.eventData)

        Some(json)

      case _ =>

        val message = s"Fleet event transformation failed: Unknown format `$format.toString` detected."
        error(message)

        None
    }

  }

  /** HELPER METHODS **/

  /**
   * This method harmonizes the 3 different result log formats
   * into a common output format. The approach applied here, is
   * synchronized with the IgniteGraph project
   */
  private def transformLog(oldObject:JsonObject):(String, String) = {

    /*
     * Extract `name` (of the query) and normalize `calendarTime`:
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

    /*
     * The name of the JSON object refers to the Osquery
     * table and is interpreted as an NGSI entity.
     *
     * The combination of hostname and table is used as
     * a unique identifier of the respective NGSI entity.
     */
    val name = oldObject.get("name").getAsString
    val hostname = getHostname(oldObject)

    val entityJson = newNGSIEntity(hostname, name)

    val calendarTime = oldObject.get("calendarTime").getAsString
    val datetime = transformCalTime(calendarTime)

    val timestamp =
      if (datetime.isEmpty) System.currentTimeMillis else datetime.get.getTime

    /*
     * Add attribute as NGSI compliant specification
     */
    val timestampJson = new JsonObject
    timestampJson.add(METADATA, new JsonObject)

    timestampJson.addProperty(TYPE, "Long")
    timestampJson.addProperty(VALUE, timestamp)

    entityJson.add(TIMESTAMP, timestampJson)

    /*
     * __MOD__ The batch object representation is
     * harmonized to be NGSI compliant.
     */
    if (oldObject.get("columns") != null) {
      /*
       * Event is the default result format. Each log line represents
       * a state change. This format works best for log aggregation systems
       * like Logstash or Splunk.
       *
       * In this case, `event format`, columns is a single object that must
       * be added to the overall output.
       *
       * {
       *  "action": "added",
       *  "columns": {
       *    "name": "osqueryd",
       *    "path": "/opt/osquery/bin/osqueryd",
       *    "pid": "97830"
       *  },
       *  "name": "processes",
       *  "hostname": "hostname.local",
       *  "calendarTime": "Tue Sep 30 17:37:30 2014",
       *  "unixTime": "1412123850",
       *  "epoch": "314159265",
       *  "counter": "1",
       *  "numerics": false
       * }
       *
       * The data is generated by keeping a cache of previous query results
       * and logged only when the cache changes (added or removed).
       *
       * If no new processes (see above) are started or stopped, the query
       * won't log any results.
       */
      val rowsJson = new JsonArray
      val rowJson = new JsonObject
      /*
       * Extract log event specific columns and thereby
       * assume that the columns provided are the result
       * of an offline configuration process.
       *
       * NOTE: the mechanism below does not work for adhoc
       * (distributed) queries.
       */
      val columns = oldObject.get("columns").getAsJsonObject
      columns2Row(rowJson, columns)

      val action = oldObject.get("action").getAsString
      /*
       * The current implementation adds the data
       * operation associated with this relation as
       * a temporary non-NGSI compliant field `action`
       */
      rowJson.addProperty(ACTION, action)

      rowsJson.add(rowJson)
      entityJson.add(ROWS, rowsJson)
      /*
       * The result log format is explicitly specified
       * for subsequent data processing and publishing
       *
       * {
       *  "format" : "..."
       *  "entity": {
       *    "id": "...",
       *    "type": "...",
       *    "timestamp": {...},
       *    "rows": [
       *      {
       *        "action": {...},
       *        "<column>": {...},
       *      }
       *    ]
       *  }
       * }
       *
       */
      val resultJson = new JsonObject
      resultJson.addProperty(FORMAT, "event")
      resultJson.add(ENTITY, entityJson)

      (name, resultJson.toString)

    }
    /*
     * Differential changes describe changes between the last (most recent)
     * query execution and the current execution.
     *
     * Each log line indicates what data has been added/removed by which query.
     * The first time the query is executed (there is no "last" run), the last
     * run is treated as having null results, so the differential consists entirely
     * of log lines with the added indication.
     *
     * There are two format options, single, or event, and batched.
     */
    else if (oldObject.get("diffResults") != null) {
      /*
       * If a query identifies multiple state changes, the batched format
       * will include all results in a single log line.
       *
       * If you're programmatically parsing lines and loading them into a
       * backend datastore, this is probably the best solution.
       *  {
       *    "diffResults": {
       *       "added": [
       *         {
       *           "name": "osqueryd",
       *           "path": "/opt/osquery/bin/osqueryd",
       *           "pid": "97830"
       *         }
       *       ],
       *       "removed": [
       *         {
       *           "name": "osqueryd",
       *           "path": "/opt/osquery/bin/osqueryd",
       *           "pid": "97650"
       *         }
       *       ]
       *    },
       *    "name": "processes",
       *    "hostname": "hostname.local",
       *    "calendarTime": "Tue Sep 30 17:37:30 2014",
       *    "unixTime": "1412123850",
       *    "epoch": "314159265",
       *    "counter": "1",
       *    "numerics": false
       *  }
       */

      val diffResults = oldObject.get("diffResults").getAsJsonObject

      val rowsJson = new JsonArray

      val actions = diffResults.keySet().toSeq.sorted
      actions.foreach(action => {

        val data = diffResults.get(action).getAsJsonArray
        data.foreach(columns => {

          val rowJson = new JsonObject
          columns2Row(rowJson,columns.getAsJsonObject )

          rowJson.addProperty(ACTION, action)
          rowsJson.add(rowJson)

        })

      })

      entityJson.add(ROWS, rowsJson)

      /*
       * The result log format is explicitly specified
       * for subsequent data processing and publishing
       */
      val resultJson = new JsonObject
      resultJson.addProperty(FORMAT, "differential")
      resultJson.add(ENTITY, entityJson)

      (name, resultJson.toString)

    }
    /*
     * Snapshot logs are an alternate form of query result logging. A snapshot is
     * an 'exact point in time' set of results, with no differentials.
     *
     * For instance if you always want a list of mounts, not the added and removed
     * mounts, then use a snapshot. In the mounts case, where differential results
     * are seldom emitted (assuming hosts do not often mount and unmount), a complete
     * snapshot will log after every query execution.
     *
     * This will be a lot of data amortized across your fleet.
     */
    else if (oldObject.get("snapshot") != null) {
      /*
       * Snapshot queries attempt to mimic the differential event format,
       * instead of emitting "columns", the snapshot data is stored using
       * "snapshot".
       *
       * SAMPLE: Select parent, path, pid from processes
       *
       * The `snapshot` shows all rows of the processes table at a certain
       * point in time.
       *
       *  {
       *    "action": "snapshot",
       *    "snapshot": [
       *
       *      --- row ---
       *
       *      {
       *        "parent": "0",
       *        "path": "/sbin/launchd",
       *        "pid": "1"
       *      },
       *
       *      --- row ---
       *
       *      {
       *        "parent": "1",
       *        "path": "/usr/sbin/syslogd",
       *        "pid": "51"
       *      },
       *
       *      --- row ---
       *
       *      {
       *        "parent": "1",
       *        "path": "/usr/libexec/UserEventAgent",
       *        "pid": "52"
       *      },
       *
       *      --- row ---
       *
       *      {
       *        "parent": "1",
       *        "path": "/usr/libexec/kextd",
       *        "pid": "54"
       *      }
       *    ],
       *    "name": "process_snapshot",
       *    "hostIdentifier": "hostname.local",
       *    "calendarTime": "Mon May  2 22:27:32 2016 UTC",
       *    "unixTime": "1462228052",
       *    "epoch": "314159265",
       *    "counter": "1",
       *    "numerics": false
       *  }
       */
      val snapshot = oldObject.get("snapshot").getAsJsonArray

      val rowsJson = new JsonArray
      snapshot.foreach(columns => {

        val rowJson = new JsonObject
        columns2Row(rowJson,columns.getAsJsonObject )

        rowJson.addProperty(ACTION, "snapshot")
        rowsJson.add(rowJson)

      })

      entityJson.add(ROWS, rowsJson)
      /*
       * The result log format is explicitly specified
       * for subsequent data processing and publishing
       */
      val resultJson = new JsonObject
      resultJson.addProperty(FORMAT, "snapshot")
      resultJson.add(ENTITY, entityJson)

      (name, resultJson.toString)

    }
    else {
      throw new Exception("Query result encountered that cannot be transformed.")
    }

  }

  private def columns2Row(rowJson:JsonObject, columnsJson:JsonObject):Unit = {
    /*
      * Extract the column names in ascending order to
      * enable a correct match with the schema definition
      */
    val colnames = columnsJson.keySet.toSeq.sorted
    colnames.foreach(colName => {

      val columnValue = columnsJson.get(colName)
      if (columnValue.isJsonPrimitive) {
        /*
         * Add attribute as NGSI compliant specification
         */
        val attributeJson = new JsonObject
        attributeJson.add(METADATA, new JsonObject)

        val attributeValue = columnValue.getAsJsonPrimitive
        if (attributeValue.isBoolean) {

          attributeJson.addProperty(TYPE, "Boolean")
          attributeJson.addProperty(VALUE, attributeValue.getAsBoolean)

        }
        else if (attributeValue.isNumber) {

          attributeJson.addProperty(TYPE, "Number")
          attributeJson.addProperty(VALUE, attributeValue.getAsNumber)

        }
        else if (attributeValue.isString) {

          attributeJson.addProperty(TYPE, "String")
          attributeJson.addProperty(VALUE, attributeValue.getAsString)

        }
        else
          throw new Exception(s"Data type is not supported.")

        rowJson.add(colName, attributeJson)
      }

    })

  }

  private def getHostname(oldObject:JsonObject):String = {
    /*
     * The host name is represented by different
     * keys within the different result logs
     */
    val keys = Array(
      "host",
      "host_identifier",
      "hostname")

    try {

      val key = oldObject.keySet()
        .filter(k => keys.contains(k))
        .head

      oldObject.get(key)
        .getAsString

    } catch {
      case _:Throwable => ""
    }

  }
  /*
   * Method to transform Osquery's calendarTime into
   * java.sql.Date as this is compliant with Apache
   * Spark data types
   */
  private def transformCalTime(s: String): Option[java.sql.Date] = {

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
      Some(new java.sql.Date(date.getTime))

    } catch {
      case _: Throwable => None
    }

  }

}

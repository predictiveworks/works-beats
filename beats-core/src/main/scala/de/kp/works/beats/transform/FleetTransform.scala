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

  override def transform(event:FileEvent, namespace:String):JsonObject = {
    /*
     * The Osquery (Fleet) Manager creates and updates 2 different
     * files, result.log and status.log; the file name is used as
     * event type.
     */
    val format = FleetFormatUtil.fromFile(event.eventType)

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
        val log = JsonParser.parseString(event.eventData).getAsJsonObject
        val (query, payload) = transformLog(log)

        val json = new JsonObject

        json.addProperty("type", s"beat/$namespace/$query")
        json.addProperty("event", payload)

        json

      case STATUS =>
        /*
         * Osquery creates status logs of its own execution,
         * for log levels INFO, WARNING and ERROR.
         *
         * Note, this implementation expects a single log file
         * that contains status messages of all levels.
         */
        val json = new JsonObject

        json.addProperty("type", s"$namespace/status")
        json.addProperty("event", event.eventData)

        json

      case _ => throw new Exception(s"[FleetTransform] Unknown format `$format.toString` detected.")
    }

  }

  /** HELPER METHODS **/

  /**
   * This method harmonizes the 3 different result log formats
   * into a common output format:
   *
   * - name:         String
   * - calendarTime: java.sql.Date
   * - timestamp:    Long
   * - hostname:     String
   * - format:       String (event)
   * - <action>:     JsonArray ((List of) columns)
   */
  private def transformLog(oldObject:JsonObject):(String, String) = {

    val newObject = new JsonObject
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
    val name = oldObject.get("name").getAsString
    newObject.addProperty("name", name)

    val calendarTime = oldObject.get("calendarTime").getAsString
    val datetime = transformCalTime(calendarTime)

    val timestamp = datetime.getTime
    newObject.addProperty("timestamp", timestamp)

    val hostname = getHostname(oldObject)
    newObject.addProperty("hostname", hostname)

    if (oldObject.get("columns") != null) {
      /*
       * In this case, `event format`, columns is a single
       * object that must be added to the overall output
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
       *
       * Extract log event specific format and thereby
       * assume that the columns provided are the result
       * of an offline configuration process.
       *
       * NOTE: the mechanism below does not work for adhoc
       * (distributed) queries.
       */
      newObject.addProperty("format", "event")
      /*
       * Normalization of action & columns fields to be
       * in sync with batch and snapshot events
       */
      val action = oldObject.get("action").getAsString
      val columns = oldObject.get("columns").getAsJsonObject

      val batch = new JsonArray
      batch.add(columns)

      newObject.add(action, batch)
      /*
       * Output format:
       *
       * - name:         String
       * - calendarTime: java.sql.Date
       * - timestamp:    Long
       * - hostname:     String
       * - format:       String (event)
       * - <action>:     JsonArray (columns)
       */

      (name, newObject.toString)

    }
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
      newObject.addProperty("format", "batch")

      val diffResults = oldObject.get("diffResults").getAsJsonObject

      val actions = diffResults.keySet().toSeq.sorted
      actions.foreach(action => {

        val data = diffResults.get(action).getAsJsonArray
        newObject.add(action, data)

      })
      /*
       * Output format:
       *
       * - name:         String
       * - calendarTime: java.sql.Date
       * - timestamp:    Long
       * - hostname:     String
       * - format:       String (batch)
       * - <action>:     JsonArray (List of columns)
       */
      (name, newObject.toString)

    }
    else if (oldObject.get("snapshot") != null) {
      /*
       * Snapshot queries attempt to mimic the differential event format,
       * instead of emitting "columns", the snapshot data is stored using
       * "snapshot".
       *
       *  {
       *    "action": "snapshot",
       *    "snapshot": [
       *      {
       *        "parent": "0",
       *         "path": "/sbin/launchd",
       *        "pid": "1"
       *      },
       *      {
       *        "parent": "1",
       *        "path": "/usr/sbin/syslogd",
       *        "pid": "51"
       *      },
       *      {
       *        "parent": "1",
       *        "path": "/usr/libexec/UserEventAgent",
       *        "pid": "52"
       *      },
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
      newObject.addProperty("format", "snapshot")

      val data = oldObject.get("snapshot").getAsJsonArray
      newObject.add("snapshot", data)

      (name, newObject.toString)

    }
    else {
      throw new Exception("Query result encountered that cannot be transformed.")
    }

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
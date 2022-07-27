package de.kp.works.beats.mitre.osquery

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

import com.google.gson.JsonObject
import de.kp.works.beats.BeatsLogging

import scala.collection.JavaConversions.asScalaSet

object OsqueryActions extends Enumeration {
  type OsqueryAction = Value

  val ADDED:OsqueryAction   = Value(0, "added")
  val REMOVED:OsqueryAction = Value(1, "removed")

}

trait EventParser extends BeatsLogging{

  protected def parse(event:JsonObject):Option[GGraph]

  protected def getBigInt(event:JsonObject, field:String, remove:Boolean):Option[Long] = {

    try {

      val v = if (remove) event.remove(field) else event.get(field)
      if (v.isJsonPrimitive) {

        val p = v.getAsJsonPrimitive
        if (p.isNumber) {
          Some(p.getAsLong)

        } else {
          Some(p.getAsString.toLong)
        }

      } else None

    } catch {
      case _:Throwable => None
    }

  }

  protected def getHostname(event:JsonObject):Option[String] = {
    /*
     * The host name is represented by different
     * keys within the different result logs
     */
    val keys = Array(
      "host",
      "host_identifier",
      "hostname")

    try {

      val key = event.keySet()
        .filter(k => keys.contains(k))
        .head

      Some(event.get(key).getAsString)

    } catch {
      case _:Throwable => None
    }

  }

  protected def getUnixTime(event:JsonObject):Long = {

    try {

      val v = event.get("unixTime")
      if (v.isJsonPrimitive) {

        val p = v.getAsJsonPrimitive
        if (p.isNumber) {
          p.getAsLong * 1000

        } else {
          p.getAsString.toLong * 1000
        }

      } else Long.MinValue

    } catch {
      case _:Throwable => Long.MinValue
    }

  }
}

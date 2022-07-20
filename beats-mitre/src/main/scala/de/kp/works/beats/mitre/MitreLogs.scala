package de.kp.works.beats.mitre

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

import com.google.gson.JsonElement
import de.kp.works.beats.mitre.MitreDomains.MitreDomain

import java.time.{Instant, ZoneId}
import scala.collection.mutable

case class TimeLog(created:Long, modified:Long)

object MitreLogs {
  /**
   * The domain specific logs contain the most actual
   * time information of a certain MITRE domain object
   */
  private val CAPEC_LOGS = mutable.HashMap.empty[String, TimeLog]
  private val ENTERPRISE_LOGS = mutable.HashMap.empty[String, TimeLog]

  private val ICS_LOGS = mutable.HashMap.empty[String, TimeLog]
  private val MOBILE_LOGS = mutable.HashMap.empty[String, TimeLog]

  def date2Timestamp(date:String):Long = {
    val datetime = Instant.parse(date).atZone(ZoneId.of( "UTC" ))
    datetime.toInstant.toEpochMilli
  }
  /**
   * This method registers or updates the time log
   * entry of a specific MITRE domain objects.
   *
   * It also checks, whether the respective object
   * is more actual than the last object.
   *
   * If so, the object is part of the delta response.
   */
  def registerAndChanges(domain:MitreDomain, objects:Seq[JsonElement]):Seq[JsonElement] = {

    val deltaObjects = mutable.ArrayBuffer.empty[JsonElement]
    objects.foreach(obj => {

      val objJson = obj.getAsJsonObject
      val id = objJson.get("id").getAsString

      val created  = if (objJson.has("created")) {
        date2Timestamp(objJson.get("created").getAsString)
      } else 0L

      val modified = if (objJson.has("modified")) {
        date2Timestamp(objJson.get("modified").getAsString)
      } else 0L

      if (created > 0L) {

        val logs = getLogs(domain)
        if (logs.contains(id)) {

          val currentLog = logs(id)
          if (modified > currentLog) {
            /*
             * This case describes that the current version
             * of the object is modified after the last
             * registration. So, update time log and assign
             * object to the `deltaObjects`.
             */
            logs += id -> TimeLog(created, modified)
            deltaObjects += obj
          }

        } else {
          /*
           * Register the time log of the current
           * object; it is expected that this is
           * an initial release of the object
           */
          logs += id -> TimeLog(created, modified)
          /*
           * Also assign this object to the list of
           * `deltaObjects`
           */
          deltaObjects += obj
        }

      }

    })

    deltaObjects

  }

  private def getLogs(domain:MitreDomain):mutable.HashMap[String, TimeLog] = {

    domain match {
      case MitreDomains.CAPEC =>
        CAPEC_LOGS

      case MitreDomains.ENTERPRISE =>
        ENTERPRISE_LOGS

      case MitreDomains.ICS =>
        ICS_LOGS

      case MitreDomains.MOBILE =>
        MOBILE_LOGS

    }

  }
}

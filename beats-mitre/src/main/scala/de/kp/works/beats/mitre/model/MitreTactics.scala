package de.kp.works.beats.mitre.model

/**
 * Copyright (c) 2019 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
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

import com.google.gson.{JsonElement, JsonNull}
import de.kp.works.beats.mitre.MitreDomains.MitreDomain
import de.kp.works.beats.mitre.{MitreConnect, MitreDomains}

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.mutable

object MitreTactics extends MitreConnect {

  private val ENTERPRISE = mutable.HashMap.empty[String, JsonElement]
  private val ICS = mutable.HashMap.empty[String, JsonElement]
  private val MOBILE = mutable.HashMap.empty[String, JsonElement]

  load()

  def load(): Unit = {

    loadTactics(MitreDomains.ENTERPRISE, ENTERPRISE)
    loadTactics(MitreDomains.ICS, ICS)
    loadTactics(MitreDomains.MOBILE, MOBILE)

  }

  def getAllTactics(domain:MitreDomain):Seq[JsonElement] =
    getLogs(domain).values.toSeq

  def getTacticById(domain: MitreDomain, id: String): JsonElement = {

    val logs = getLogs(domain).values
    val filtered = logs.filter(obj => {
      obj.getAsJsonObject.get("id").getAsString == id
    })

    if (filtered.isEmpty) return JsonNull.INSTANCE
    filtered.head

  }

  def getTacticByName(domain: MitreDomain, shortName: String): JsonElement = {

    val logs = getLogs(domain)
    logs.getOrElse(shortName, JsonNull.INSTANCE)

  }
  /**
   * Method to retrieve all attack patterns that
   * refer to a certain tactic `shortName`.
   *
   * The `shortName` is equal to the `phase_name`
   * of an assigned kill chain phase.
   */
  def getTechniques(domain:MitreDomain, shortName:String):Seq[JsonElement] = {
    /*
     * STEP #1: Get attack patterns
     * (techniques)
     */
    val techniques = getObjects(domain, Some("attack-pattern"))
    /*
     * STEP #2: Filter techniques by
     * `shortName` which is the phase
     * name of a kill chain phase
     */
    techniques.filter(obj => {
      val objJson = obj.getAsJsonObject
      val kcps = objJson.get("kill_chain_phases").getAsJsonArray

      val filtered = kcps.filter(kcp => {
        val kcpJson = kcp.getAsJsonObject
        kcpJson.get("phase_name").getAsString == shortName
      })

      filtered.nonEmpty

    })

  }

  private def getLogs(domain:MitreDomain):mutable.HashMap[String, JsonElement] = {

    domain match {
      case MitreDomains.ENTERPRISE =>
        ENTERPRISE

      case MitreDomains.ICS =>
        ICS

      case MitreDomains.MOBILE =>
        MOBILE

      case _ =>
        mutable.HashMap.empty[String, JsonElement]
    }

  }

  private def loadTactics(domain: MitreDomain,
                  logs: mutable.HashMap[String, JsonElement]): Unit = {

    val tactics = getTactics(domain = domain)
    tactics.foreach(tactic => {

      val shortName = tactic.getAsJsonObject
        .get("x_mitre_shortname").getAsString

      logs += shortName -> tactic

    })

  }
}

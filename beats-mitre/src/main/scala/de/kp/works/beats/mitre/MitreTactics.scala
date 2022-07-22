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

import com.google.gson.{JsonElement, JsonNull}
import de.kp.works.beats.mitre.MitreDomains.MitreDomain

import scala.collection.mutable

object MitreTactics extends MitreConnect {

  private val ENTERPRISE = mutable.HashMap.empty[String, JsonElement]
  private val ICS = mutable.HashMap.empty[String, JsonElement]
  private val MOBILE = mutable.HashMap.empty[String, JsonElement]

  load()

  def load():Unit = {

    loadTactics(MitreDomains.ENTERPRISE, ENTERPRISE)
    loadTactics(MitreDomains.ICS, ICS)
    loadTactics(MitreDomains.MOBILE, MOBILE)

  }

  def getTactic(domain:MitreDomain, shortName:String):JsonElement = {

    val logs = domain match {
      case MitreDomains.ENTERPRISE =>
        ENTERPRISE

      case MitreDomains.ICS =>
        ICS

      case MitreDomains.MOBILE =>
        MOBILE

      case _ =>
        mutable.HashMap.empty[String, JsonElement]
    }

    logs.getOrElse(shortName, JsonNull.INSTANCE)

  }

  def loadTactics(domain:MitreDomain,
                  logs:mutable.HashMap[String, JsonElement]):Unit = {

    val tactics = getTactics(domain=domain)
    tactics.foreach(tactic => {

      val shortName = tactic.getAsJsonObject
        .get("x_mitre_shortname").getAsString

      logs += shortName -> tactic

    })

  }
}

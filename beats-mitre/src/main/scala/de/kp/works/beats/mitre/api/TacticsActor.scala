package de.kp.works.beats.mitre.api

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

import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.SourceQueueWithComplete
import com.google.gson.JsonArray
import de.kp.works.beats.BeatsConf
import de.kp.works.beats.actors.BeatsActor
import de.kp.works.beats.mitre.MitreDomains
import de.kp.works.beats.mitre.model.MitreTactics
/**
 * [TacticsActor] is a MitreBeat API actor that offers
 * domain specific tactics. Tactics represent one of the
 * top-level search dimensions of the Beat's API.
 */
class TacticsActor(queue: SourceQueueWithComplete[String])
  extends BeatsActor(BeatsConf.MITRE_CONF, queue) {

  private val emptyMessage = new JsonArray

  override def execute(request: HttpRequest): String = {

    val json = getBodyAsJson(request)
    if (json == null) {

      warn(ApiMessages.invalidRequest)
      return emptyMessage.toString

    }

    val req = mapper.readValue(json.toString, classOf[TacticsReq])
    if (req.domain.isEmpty) {

      warn(ApiMessages.invalidDomain)
      return emptyMessage.toString

    }
    /*
     * Supported requests either retrieve all MITRE tactics
     * of a certain domain, or, get a certain tactic by id,
     * or, short name
     */
    try {

      val result = new JsonArray

      val domain = MitreDomains.withName(req.domain)
      if (req.id.isEmpty) {

        if (req.shortName.isEmpty) {
          /*
           * Retrieve the list of all tactics of
           * a certain MITRE domain
           */
          val tactics = MitreTactics.getAllTactics(domain)
          tactics.foreach(result.add)

          result.toString

        } else {
          /*
           * Retrieve the tactic that is referenced
           * by its short name
           */
          val tactic = MitreTactics.getTacticByName(domain, req.shortName.get)
          if (tactic.isJsonNull) return result.toString

          result.add(tactic)
          result.toString

        }

      } else {
        /*
         * A request with a provided `id` always
         * returns the referenced tactic
         */
        val tactic = MitreTactics.getTacticById(domain, req.id.get)
        if (tactic.isJsonNull) return result.toString

        result.add(tactic)
        result.toString

      }

    } catch {
      case t:Throwable =>
        error(ApiMessages.failedTacticsReq(t))
        emptyMessage.toString
    }

  }

}

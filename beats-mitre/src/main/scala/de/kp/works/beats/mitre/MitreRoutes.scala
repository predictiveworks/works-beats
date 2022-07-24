package de.kp.works.beats.mitre

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

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import de.kp.works.beats.BeatsHttp

object MitreActors {

  val MITRE_DATA_ACTOR       = "mitre_data_actor"
  val MITRE_TACTICS_ACTOR    = "mitre_tactics_actor"
  val MITRE_TECHNIQUES_ACTOR = "mitre_techniques_actor"

}

class MitreRoutes(actors:Map[String,ActorRef],
                  source:Source[ServerSentEvent, NotUsed]) extends BeatsHttp(source) {

  import MitreActors._

  def getRoutes:Route = {

    getStream ~
    postData ~
    postTactics ~
    postTechniques

  }
  /**
   * This route provides access to domain specific
   * data sources and its components
   */
  private def postData:Route = routePost("mitre/v1/data", actors.get(MITRE_DATA_ACTOR))
  /**
   * This route provides access to domain specific
   * tactics
   */
  private def postTactics:Route = routePost("mitre/v1/tactics", actors.get(MITRE_TACTICS_ACTOR))
  /**
   * This route provides access to domain specific
   * techniques
   */
  private def postTechniques:Route = routePost("mitre/v1/techniques", actors.get(MITRE_TECHNIQUES_ACTOR))

}

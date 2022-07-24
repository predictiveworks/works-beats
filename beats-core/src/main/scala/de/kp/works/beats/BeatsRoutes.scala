package de.kp.works.beats

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

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Source

import scala.util.Try

case class Response(status: Try[_])

object BeatsActors {
  /*
   * This actor executes requests to determine
   * the health status of the Works Beat.
   */
  val BEATS_HEALTH_ACTOR = "beats_health_actor"
  /*
   * This actor executes requests to create or
   * update the semantic description assigned
   * to a certain Works Beat.
   */
  val BEATS_SEMANTICS_ACTOR = "beats_semantics_actor"
}

/**
 * [BeatsRoutes] supports the SSE route of the
 * WorksBeat service.
 */
class BeatsRoutes(actors:Map[String,ActorRef],source:Source[ServerSentEvent, NotUsed]) extends BeatsHttp {

  import BeatsActors._

  def this(source:Source[ServerSentEvent, NotUsed]) = {
    this(actors=Map.empty[String,ActorRef], source=source)
  }

  def getRoutes:Route = {

    getStream ~
    postHealth ~
    postSemantics

  }

  /**
   * This is the Server Sent Event route; the route
   * is harmonized with the Sensor Beat SSE route
   */
  protected def getStream:Route = {

    path("beat" / "stream") {
      options {
        extractOptions
      } ~
      Directives.get {
        addCors(
          complete {
            source
          }
        )
      }
    }
  }
  /**
   * This route provides access to the health status
   * of a certain Works Beat.
   */
  private def postHealth:Route = routePost("beat/v1/health", actors.get(BEATS_HEALTH_ACTOR))
  /**
   * This route provides access to the semantics actor
   * of a certain Works Beat.
   */
  private def postSemantics:Route = routePost("beat/v1/semantics", actors.get(BEATS_SEMANTICS_ACTOR))

}

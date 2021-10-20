package de.kp.works.beats.fiware
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

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.http.scaladsl.model.{HttpProtocols, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives.extractRequest
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.kp.works.beats.BeatsRoutes
import de.kp.works.beats.fiware.FiwareActor._

import scala.concurrent.Await
import scala.util.{Failure, Success}

class FiwareRoutes(actors:Map[String, ActorRef], source:Source[ServerSentEvent, NotUsed]) extends BeatsRoutes(source) {

  private val fiwareActor = actors(ACTOR_NAME)
  /**
   * This route defines the endpoint for the Fiware
   * Context Broker to send notifications to
   */
  def notifications:Route = {
    path("notifications") {
      post {
        /*
         * Extract (full) HTTP request from POST notification
         * of the Orion Context Broker
         */
        extractRequest { request =>
          complete {

            val future = fiwareActor ? request
            Await.result(future, timeout.duration) match {
              case Response(Failure(e)) =>
                /*
                 * A failure response is sent with 500 and
                 * the respective exception message
                 */
                val message = e.getMessage + "\n"
                val length = message.getBytes.length

                val headers = List(
                  `Content-Type`(`text/plain(UTF-8)`),
                  `Content-Length`(length)
                )

                HttpResponse(
                  status=StatusCodes.InternalServerError,
                  headers = headers,
                  entity = ByteString(message),
                  protocol = HttpProtocols.`HTTP/1.1`)
              case Response(Success(_)) =>

                val headers = List(
                  `Content-Type`(`text/plain(UTF-8)`),
                  `Content-Length`(0)
                )

                HttpResponse(
                  status=StatusCodes.OK,
                  headers = headers,
                  entity = ByteString(),
                  protocol = HttpProtocols.`HTTP/1.1`)
            }
          }
        }
      }
    }
  }

}

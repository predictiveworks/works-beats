package de.kp.works.beats
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
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpProtocols, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Source
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class BeatsRoutes(source:Source[ServerSentEvent, NotUsed]) extends CORS {
  /*
 	 * Common timeout for all Akka connections
   */
  implicit val timeout: Timeout = Timeout(5.seconds)

  /** EVENT **/

  /*
   * This is the Server Sent Event route
   */
  def event:Route = {

    path("event") {
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

  protected def extractOptions: RequestContext => Future[RouteResult] = {
    extractRequest { _ =>
      complete {
        baseResponse
      }
    }
  }

  protected def baseResponse: HttpResponse = {

    val response = HttpResponse(
      status=StatusCodes.OK,
      protocol = HttpProtocols.`HTTP/1.1`)

    addCorsHeaders(response)

  }

}

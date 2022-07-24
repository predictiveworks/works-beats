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
import akka.http.scaladsl.model.{HttpProtocols, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directives, RequestContext, Route, RouteResult}
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import com.google.gson.JsonObject

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

abstract class BeatsHttp(source:Source[ServerSentEvent, NotUsed]) extends CORS {
  /**
   * Common timeout for all Akka connections
   */
  val duration: FiniteDuration = 15.seconds
  implicit val timeout: Timeout = Timeout(duration)

  /**
   * This is the Server Sent Event route; the route
   * is harmonized with the Sensor Beat SSE route
   */
  def getStream:Route = {

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

  /*******************************
   *
   * HELPER METHODS
   *
   */
  def routePost(url:String, actor:Option[ActorRef]):Route = {
    val matcher = separateOnSlashes(url)
    path(matcher) {
      post {
        /*
         * The client sends sporadic [HttpEntity.Default]
         * requests; the [BaseActor] is not able to extract
         * the respective JSON body from.
         *
         * As a workaround, the (small) request is made
         * explicitly strict
         */
        toStrictEntity(duration) {
          extract(actor)
        }
      }
    }
  }

  protected def extract(actor:Option[ActorRef]): Route = {
    extractRequest { request =>
      complete {
        if (actor.isEmpty) {
          val response = new JsonObject
          response.addProperty("status", "not supported")
          jsonResponse(response.toString)

        } else {
          /*
           * The Http(s) request is sent to the respective
           * actor and the actor' response is sent to the
           * requester as response.
           */
          val future = actor.get ? request
          Await.result(future, timeout.duration) match {
            case Response(Failure(e)) =>
              val message = e.getMessage
              jsonResponse(message)
            case Response(Success(answer)) =>
              val message = answer.asInstanceOf[String]
              jsonResponse(message)
          }

        }
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

  protected def jsonResponse(message:String): HttpResponse = {

    HttpResponse(
      status=StatusCodes.OK,
      entity = ByteString(message),
      protocol = HttpProtocols.`HTTP/1.1`)

  }

}

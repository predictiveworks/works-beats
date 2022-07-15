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
import akka.pattern.ask
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpProtocols, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import com.google.gson.JsonObject

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success, Try}

case class Response(status: Try[_])

object BeatsActors {

  val BEATS_HEALTH_ACTOR = "beats_health_actor"
}

/**
 * [BeatsRoutes] supports the SSE route of the
 * WorksBeat service.
 */
class BeatsRoutes(actors:Map[String,ActorRef],source:Source[ServerSentEvent, NotUsed]) extends CORS {

  import BeatsActors._
  /**
   * Common timeout for all Akka connections
   */
  val duration: FiniteDuration = 15.seconds
  implicit val timeout: Timeout = Timeout(duration)

  def this(source:Source[ServerSentEvent, NotUsed]) = {
    this(actors=Map.empty[String,ActorRef], source=source)
  }

  def getRoutes:Route = {

    getStream ~
    postHealth

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

  def extract(actor:Option[ActorRef]): Route = {
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

  def jsonResponse(message:String): HttpResponse = {

    HttpResponse(
      status=StatusCodes.OK,
      entity = ByteString(message),
      protocol = HttpProtocols.`HTTP/1.1`)

  }

}

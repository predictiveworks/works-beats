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
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, DelayOverflowStrategy, OverflowStrategy}
import akka.util.Timeout
import com.typesafe.config.Config
import de.kp.works.beats.ssl.SslOptions

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

abstract class BeatsService(name:String) {

  private var server:Option[Future[Http.ServerBinding]] = None
  /**
   * Akka 2.6 provides a default materializer out of the box, i.e., for Scala
   * an implicit materializer is provided if there is an implicit ActorSystem
   * available. This avoids leaking materializers and simplifies most stream
   * use cases somewhat.
   */
  implicit val system: ActorSystem = ActorSystem(name)
  implicit lazy val context: ExecutionContextExecutor = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  /**
   * Common timeout for all Akka connection
   */
  implicit val timeout: Timeout = Timeout(5.seconds)
  /**
   * The configuration of the Beat output; the current implementation
   * supports the provisioning of events via SSE queue (default) and
   * the publishing via MQTT protocol to a certain MQTT broker.
   */
  private var outputCfg:Option[Config] = None

  def start(config:Option[String] = None):Unit = {

    BeatsConf.init(config)

    if (!BeatsConf.isInit) {

      val now = new java.util.Date().toString
      throw new Exception(s"[ERROR] $now - Loading configuration failed and beat service is not started.")

    }
    /*
     * Extract and assign the output configuration
     */
    outputCfg = Some(BeatsConf.getOutputCfg)
    /*
     * Retrieve configuration that refers to the provided
     * Beat (name)
     */
    val beatCfg = BeatsConf.getBeatCfg(name)

    /*
     * The queue is used as an internal buffer that receives
     * events published during internal longer lasting processes.
     *
     * These events can be continuously used leveraging an EventSource
     *
     * The overflow strategy specified for the queue is backpressure,
     * and is used to avoid dropping events if the buffer is full.
     *
     * Instead, the returned Future does not complete until there is
     * space in the buffer and offer should not be called again until
     * it completes.
     *
     */
    lazy val (queue, source) = Source.queue[String](Int.MaxValue, OverflowStrategy.backpressure)
      .delay(1.seconds, DelayOverflowStrategy.backpressure)
    /*
     * The message type, used to distinguish SSE from multiple source,
     * is set equal to the provided `name`
     */
      /* The message type is described as WorksEvent */
      .map(message => ServerSentEvent(message, Some(name)))
      .keepAlive(1.second, () => ServerSentEvent.heartbeat)
      .toMat(BroadcastHub.sink[ServerSentEvent])(Keep.both)
      .run()

    /*
     * Build beats specific routes; with respect to the current
     * implementation, [CTIBeat] and [ThingsBeat] have the same
     * route and differ from [FiwareBeat]
     */
    val routes = buildRoute(queue, source)
    val binding = beatCfg.getConfig("binding")

    val host = binding.getString("host")
    val port = binding.getInt("port")

    val security = beatCfg.getConfig("security")
    server =
      if (security.getString("ssl") == "false")
        Some(Http().bindAndHandle(routes , host, port))

      else {
        val context = SslOptions.buildServerConnectionContext(security)
        Some(Http().bindAndHandle(routes, host, port, connectionContext = context))
      }

    /* After start processing */

    onStart(queue, beatCfg)

  }
  /**
   * This method defines the base output `event` route
   * to retrieve the generated Server Sent Events (SSE).
   *
   * This route is always built independent of whether
   * the configured output channel is set to `sse`.
   */
  def buildRoute(
    queue: SourceQueueWithComplete[String],
    source: Source[ServerSentEvent, NotUsed]): Route = {

    val actors = buildApiActors(queue)

    val routes = new BeatsRoutes(actors, source)
    routes.getRoutes

  }

  def buildApiActors(queue: SourceQueueWithComplete[String]):Map[String,ActorRef] = {
    throw new Exception("not implemented")
  }
  /**
   * A public method to expose the output configuration
   * to the specific Works Beats.
   */
  def getOutputCfg:Config = outputCfg.get

  def onStart(queue: SourceQueueWithComplete[String], cfg:Config):Unit

  def stop():Unit = {

    if (server.isEmpty)
      throw new Exception("Beat service was not launched.")

    server.get
      /*
       * rigger unbinding from port
       */
      .flatMap(_.unbind())
      /*
       * Shut down application
       */
      .onComplete(_ => {
        system.terminate()
      })

  }
}

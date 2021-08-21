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
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, DelayOverflowStrategy, OverflowStrategy}
import akka.stream.scaladsl.{BroadcastHub, Keep, Source, SourceQueueWithComplete}
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

  def start(config:Option[String] = None):Unit = {

    BeatsConf.init(config)

    if (!BeatsConf.isInit) {

      val now = new java.util.Date().toString
      throw new Exception(s"[ERROR] $now - Loading configuration failed and beat service is not started.")

    }
    /*
     * Retrieve configuration that refers to the provided
     * `name`
     */
    val cfg = BeatsConf.getBeatCfg(name)
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
    val binding = cfg.getConfig("binding")

    val host = binding.getString("host")
    val port = binding.getInt("port")

    val security = cfg.getConfig("security")
    server =
      if (security.getString("ssl") == "false")
        Some(Http().bindAndHandle(routes , host, port))

      else {
        val context = SslOptions.buildServerConnectionContext(security)
        Some(Http().bindAndHandle(routes, host, port, connectionContext = context))
      }

    /* After start processing */

    onStart(queue, cfg)

  }

  def onStart(queue: SourceQueueWithComplete[String], cfg:Config):Unit

  def buildRoute(
    queue:SourceQueueWithComplete[String],
    source:Source[ServerSentEvent, NotUsed]):Route

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

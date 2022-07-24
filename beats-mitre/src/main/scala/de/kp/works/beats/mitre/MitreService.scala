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

import akka.NotUsed
import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import com.typesafe.config.Config
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.mitre.api.{DataActor, TacticsActor, TechniquesActor}
import de.kp.works.beats.{BeatsConf, BeatsService}

class MitreService extends BeatsService(BeatsConf.MITRE_CONF) {

  import MitreActors._

  override def buildRoute(queue: SourceQueueWithComplete[String],
                  source: Source[ServerSentEvent, NotUsed]): Route = {

    val actors = buildApiActors(queue)

    val routes = new MitreRoutes(actors, source)
    routes.getRoutes

  }
  /**
   * The [MitreService] supports the scheduled
   * retrieval of the MITRE knowledge bases on
   * the one hand (= receiver), and also enables
   * HTTP-based interaction with these bases.
   *
   * The API actors support specific HTTP routes
   * to access MITRE domains.
   */
  override def buildApiActors(queue: SourceQueueWithComplete[String]):Map[String,ActorRef] = {

    Map(
      MITRE_DATA_ACTOR ->
        system.actorOf(Props(new DataActor(queue)), MITRE_DATA_ACTOR),

      MITRE_TACTICS_ACTOR ->
        system.actorOf(Props(new TacticsActor(queue)), MITRE_TACTICS_ACTOR),

      MITRE_TECHNIQUES_ACTOR ->
        system.actorOf(Props(new TechniquesActor(queue)), MITRE_TECHNIQUES_ACTOR)
    )

  }

  override def onStart(queue: SourceQueueWithComplete[String], mitreCfg:Config):Unit = {

    val receiverCfg = mitreCfg.getConfig("receiver")

    val interval = receiverCfg.getInt("interval")
    val numThreads = receiverCfg.getInt("numThreads")

    val outputHandler:OutputHandler = buildOutputHandler(queue)

    val receiver = new MitreReceiver(outputHandler, interval, numThreads)
    receiver.start()

  }

  private def buildOutputHandler(queue: SourceQueueWithComplete[String]):OutputHandler = {

    val outputHandler:OutputHandler = new OutputHandler
    outputHandler.setNamespace(BeatsConf.MITRE_NAME)

    val channel = getOutputCfg.getString("channel")
    outputHandler.setChannel(channel)
    /*
     * The MITRE Beat implements a project specific
     * transformer [MitreTransform]. This is different
     * from other beats.
     */
    channel match {
      case "fiware" =>
      /*
       * Do nothing as the [OutputHandler] initiates the
       * [FiwarePublisher] when setting the respective channel
       */
      case "mqtt" =>
      /*
       * Do nothing as the [OutputHandler] initiates the
       * [MqttPublisher] when setting the respective channel
       */
      case "sse" =>
        /*
         * Configure the [OutputHandler] to write incoming
         * [MqttEvent]s to the SSE output queue
         */
        outputHandler.setSseQueue(queue)

      case _ =>
        throw new Exception(s"The configured output channel `$channel` is not supported.")
    }

    outputHandler

  }

}

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
import akka.actor.Props
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import com.typesafe.config.Config
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.subscriptions.FiwareSubscriptions
import de.kp.works.beats.transform.FiwareTransform
import de.kp.works.beats.{BeatsConf, BeatsService}

import scala.concurrent.Await

class FiwareService extends BeatsService(BeatsConf.FIWARE_CONF) {

  override def buildRoute(queue: SourceQueueWithComplete[String], source: Source[ServerSentEvent, NotUsed]): Route = {
    /*
     * In contrast to other Works Beats, the output handler
     * has to be constructed here, because it is delegated
     * to the respective actor.
     */
    val outputHandler:OutputHandler = buildOutputHandler(queue)

    lazy val fiwareActor = system
      .actorOf(Props(new FiwareActor(outputHandler)), FiwareActor.ACTOR_NAME)

    val actors = Map(FiwareActor.ACTOR_NAME -> fiwareActor)
    val routes = new FiwareRoutes(actors, source)
    /*
     * The [FiwareService] offers 2 different routes, one to
     * GET Context Broker notifications as SSE (event), and
     * another POST as endpoint to receive these notifications
     * from the Context Broker
     *
     * Context Broker --> POST [notifications] & SSE --> GET [notification]
     *
     */
    routes.event ~
    routes.notifications

  }

  override def onStart(queue: SourceQueueWithComplete[String], fiwareCfg:Config):Unit = {

    val subscriptions = FiwareSubscriptions.getSubscriptions
    subscriptions.foreach(subscription => {

      try {

        val future = FiwareClient.subscribe(subscription, system)
        val response = Await.result(future, timeout.duration)

        val sid = FiwareClient.getSubscriptionId(response)
        FiwareSubscriptions.register(sid, subscription)

      } catch {
        case _:Throwable =>
          /*
           * The current implementation of the Fiware
           * support is an optimistic approach that
           * focuses on those subscriptions that were
           * successfully registered.
           */
          println("[ERROR] ------------------------------------------------")
          println("[ERROR] Registration of subscription failed:")
          println(s"$subscription")
          println("[ERROR] ------------------------------------------------")
      }

    })

  }

  private def buildOutputHandler(queue: SourceQueueWithComplete[String]):OutputHandler = {

    val outputHandler:OutputHandler = new OutputHandler
    outputHandler.setNamespace(BeatsConf.FIWARE_NAME)

    val channel = getOutputCfg.getString("channel")
    outputHandler.setChannel(channel)
    /*
     * Configure the [OutputHandler] to transform incoming
     * [FiwareEvent]s prior to publishing with [FiwareTransform]
     */
    outputHandler.setFiwareTransform(new FiwareTransform)

    channel match {
      case "mqtt" =>
      /*
       * Do nothing as the [OutputHandler] initiates the
       * [MqttPublisher] when setting the respective channel
       */
      case "sse" =>
        /*
         * Configure the [OutputHandler] to write incoming
         * [FiwareEvent]s to the SSE output queue
         */
        outputHandler.setSseQueue(queue)

      case _ =>
        throw new Exception(s"The configured output channel `$channel` is not supported.")
    }

    outputHandler

  }

}

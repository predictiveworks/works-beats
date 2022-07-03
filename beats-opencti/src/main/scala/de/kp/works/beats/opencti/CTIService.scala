package de.kp.works.beats.opencti
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

import akka.stream.scaladsl.SourceQueueWithComplete
import com.typesafe.config.Config
import de.kp.works.beats.handler.OutputHandler
import de.kp.works.beats.ssl.SslOptions
import de.kp.works.beats.transform.CTITransform
import de.kp.works.beats.{BeatsConf, BeatsService}

class CTIService extends BeatsService(BeatsConf.OPENCTI_CONF) {

  override def onStart(queue: SourceQueueWithComplete[String], openCtiCfg:Config):Unit = {
    /*
     * After having started the Http(s) server,
     * the server is started that connects to
     * OpenCTI server and retrieves SSE
     *
     * OpenCTI streams (server) --> CTIReceiver
     *
     * The receiver is an SSE client that listens
     * to published threat intelligence events.
     */
    val receiverCfg = openCtiCfg.getConfig("receiver")
    val endpoint = receiverCfg.getString("endpoint")

    val authToken = {
      val value = receiverCfg.getString("authToken")
      if (value.isEmpty) None else Some(value)
    }
    /*
     * Transport security configuration used to
     * establish a Http(s) connection to the server.
     */
    val securityCfg = receiverCfg.getConfig("security")
    val sslOptions = SslOptions.getSslOptions(securityCfg)

    val numThreads = receiverCfg.getInt("numThreads")
    val outputHandler:OutputHandler = buildOutputHandler(queue)

    val receiver = new CTIReceiver(
      endpoint,
      authToken,
      Some(sslOptions),
      outputHandler,
      numThreads
    )

    receiver.start()

  }

  private def buildOutputHandler(queue: SourceQueueWithComplete[String]):OutputHandler = {

    val outputHandler:OutputHandler = new OutputHandler
    outputHandler.setNamespace(BeatsConf.OPENCTI_NAME)

    val channel = getOutputCfg.getString("channel")
    outputHandler.setChannel(channel)
    /*
     * Configure the [OutputHandler] to transform incoming
     * SseEvent]s prior to publishing with [CTITransform]
     */
    outputHandler.setCTITransform(new CTITransform)

    channel match {
      case "fiware" =>
      /*
       * The FIWARE publisher is used to send cyber threat
       * intelligence from OpenCTI to a FIWARE context broker.
       *
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

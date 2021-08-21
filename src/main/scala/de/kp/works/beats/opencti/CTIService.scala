package de.kp.works.beats.opencti
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
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import com.typesafe.config.Config
import de.kp.works.beats.ssl.SslOptions
import de.kp.works.beats.{BeatsConf, BeatsService}

class CTIService extends BeatsService(BeatsConf.OPENCTI_CONF) {

  override def buildRoute(queue: SourceQueueWithComplete[String], source: Source[ServerSentEvent, NotUsed]): Route = {

    val routes = new CTIRoutes(source)
    routes.event

  }

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
    val receiver = new CTIReceiver(
      endpoint,
      authToken,
      Some(sslOptions),
      Some(queue),
      numThreads
    )

    receiver.start()

  }
}

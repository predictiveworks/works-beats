package de.kp.works.beats.thingsboard
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

class ThingsService extends BeatsService(BeatsConf.THINGSBOARD_CONF) {

  override def onStart(queue: SourceQueueWithComplete[String], thingsCfg:Config):Unit = {
    /*
     * After having started the Http(s) server,
     * the server is started that connects to
     * ThingsBoard and retrieves notification
     * that refer to attribute change events
     */
    val mqttCfg = thingsCfg.getConfig("mqtt")
    val brokerUrl = mqttCfg.getString("brokerUrl")

    val mqttCreds = getMqttCreds(mqttCfg)

    val securityCfg = mqttCfg.getConfig("security")
    val sslOptions = SslOptions.getSslOptions(securityCfg)

    val numThreads = mqttCfg.getInt("numThreads")
    val receiver = new ThingsReceiver(
      brokerUrl,
      mqttCreds,
      Some(sslOptions),
      Some(queue),
      numThreads)

    receiver.start()

  }

  private def getMqttCreds(mqttCfg:Config):MqttCreds = {

    val authToken = {
      val value = mqttCfg.getString("authToken")
      if (value.isEmpty) None else Some(value)
    }

    val clientId = mqttCfg.getString("clientId")

    val userName = {
      val value = mqttCfg.getString("userName")
      if (value.isEmpty) None else Some(value)
    }

    val userPass = {
      val value = mqttCfg.getString("userPass")
      if (value.isEmpty) None else Some(value)
    }

    MqttCreds(clientId, authToken, userName, userPass)

  }
}

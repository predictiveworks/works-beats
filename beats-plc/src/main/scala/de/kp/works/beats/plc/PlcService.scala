package de.kp.works.beats.plc

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
import de.kp.works.beats.{BeatsConf, BeatsService}

class PlcService extends BeatsService(BeatsConf.PLC_CONF) {

  override def onStart(queue: SourceQueueWithComplete[String], plcCfg:Config):Unit = {

    val receiverCfg = plcCfg.getConfig("receiver")

    val interval = receiverCfg.getInt("interval")
    val numThreads = receiverCfg.getInt("numThreads")

    val outputHandler:OutputHandler = buildOutputHandler(queue)

    val receiver = new PlcReceiver(outputHandler, interval, numThreads)
    receiver.start()

  }

  private def buildOutputHandler(queue: SourceQueueWithComplete[String]):OutputHandler = {

    val outputHandler:OutputHandler = new OutputHandler
    outputHandler.setNamespace(BeatsConf.PLC_NAME)

    val channel = getOutputCfg.getString("channel")
    outputHandler.setChannel(channel)
    /*
     * The PLC Beat implements a project specific
     * transformer [PlcTransform]. This is different
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
